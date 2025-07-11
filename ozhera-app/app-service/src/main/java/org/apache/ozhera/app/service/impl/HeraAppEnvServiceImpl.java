/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ozhera.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ozhera.app.dao.mapper.HeraAppBaseInfoMapper;
import org.apache.ozhera.app.dao.mapper.HeraAppEnvMapper;
import org.apache.ozhera.app.enums.OperateEnum;
import org.apache.ozhera.app.enums.StatusEnum;
import org.apache.ozhera.app.exception.AppException;
import org.apache.ozhera.app.model.HeraAppBaseInfo;
import org.apache.ozhera.app.model.HeraAppEnv;
import org.apache.ozhera.app.model.vo.HeraAppEnvVo;
import org.apache.ozhera.app.model.vo.HeraAppOperateVo;
import org.apache.ozhera.app.model.vo.HeraEnvIpVo;
import org.apache.ozhera.app.service.HeraAppEnvService;
import org.apache.ozhera.app.service.env.DefaultEnvIpFetch;
import org.apache.ozhera.app.service.env.EnvIpFetch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.apache.ozhera.app.common.Constant.DEFAULT_OPERATOR;
import static org.apache.ozhera.app.common.Constant.GSON;

/**
 * @version 1.0
 * @description
 * @date 2022/11/9 17:46
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "service.selector.property", havingValue = "outer")
public class HeraAppEnvServiceImpl implements HeraAppEnvService {

    private final HeraAppBaseInfoMapper heraAppBaseInfoMapper;

    private final HeraAppEnvMapper heraAppEnvMapper;

    private final DefaultEnvIpFetch defaultEnvIpFetch;

    private final DefaultMQProducer defaultMQProducer;

    private EnvIpFetch envIpFetch;

    @Value("${rocket.mq.hera.ip.change.topic}")
    private String ipChangeTopic;

    @PostConstruct
    public void init() {
        envIpFetch = defaultEnvIpFetch.getEnvIpFetch();
    }

    public HeraAppEnvServiceImpl(HeraAppEnvMapper heraAppEnvMapper, HeraAppBaseInfoMapper heraAppBaseInfoMapper, DefaultEnvIpFetch defaultEnvIpFetch, DefaultMQProducer defaultMQProducer) {
        this.heraAppEnvMapper = heraAppEnvMapper;
        this.heraAppBaseInfoMapper = heraAppBaseInfoMapper;
        this.defaultEnvIpFetch = defaultEnvIpFetch;
        this.defaultMQProducer = defaultMQProducer;
    }

    @Override
    public HeraAppEnvVo queryAppEnvById(Long id) {
        HeraAppEnv heraAppEnv = heraAppEnvMapper.selectById(id);
        if (null != heraAppEnv) {
            return heraAppEnv.toHeraAppEnvVo();
        }
        return null;
    }

    @Override
    public Long addAppEnv(HeraAppOperateVo operateVo) {
        HeraAppEnv heraAppEnv = new HeraAppEnv();
        heraAppEnv.operateVoToHeraAppEnv(operateVo, OperateEnum.ADD_OPERATE);
        heraAppEnvMapper.insert(heraAppEnv);
        return heraAppEnv.getId();
    }

    @Override
    public Long updateAppEnv(HeraAppOperateVo operateVo) {
        if (null == heraAppEnvMapper.selectById(operateVo.getId())) {
            throw new AppException("当前记录不存在");
        }
        HeraAppEnv heraAppEnv = new HeraAppEnv();
        heraAppEnv.operateVoToHeraAppEnv(operateVo, OperateEnum.UPDATE_OPERATE);
        heraAppEnvMapper.updateById(heraAppEnv);
        return heraAppEnv.getId();
    }

    @Override
    public Boolean deleteAppEnv(Long id) {
        return heraAppEnvMapper.deleteById(id) == 1;
    }

    @Override
    public void fetchIpsOpByApp(String app) {
        /**
         * 分页获取app信息，查询环境信息
         */
        int pageNum = 1, pageSize = 100;

        while (true) {
            log.info("轮询应用,pageNum:{},pageSize:{}", pageNum, pageSize);
            Page<HeraAppBaseInfo> appBaseInfoPage = heraAppBaseInfoMapper.selectPage(new Page(pageNum, pageSize), new LambdaQueryWrapper<HeraAppBaseInfo>().eq(HeraAppBaseInfo::getStatus, StatusEnum.NOT_DELETED.getCode()));
            List<HeraAppBaseInfo> appBaseInfos = appBaseInfoPage.getRecords();
            if (CollectionUtils.isNotEmpty(appBaseInfos)) {
                appBaseInfos.stream().forEach(baseInfo -> {
                    Integer id = baseInfo.getId();
                    String bindId = baseInfo.getBindId();
                    String appName = baseInfo.getAppName();
                    try {
                        handleAppEnv(id, bindId, appName);
                    } catch (Exception e) {
                        log.error(String.format("fetchIpsOpByApp job env ip info error,id:%d,bindId:%s,appName:%s", id, bindId, appName), e);
                    }
                });
            }
            if (null == appBaseInfoPage || !appBaseInfoPage.hasNext()) {
                break;
            }
            pageNum++;
        }
    }

    public void handleAppEnv(Integer id, String bindId, String appName) throws Exception {
        envIpFetch = defaultEnvIpFetch.getEnvFetch(bindId);
        HeraAppEnvVo heraAppEnvVo = envIpFetch.fetch(id.longValue(), Long.valueOf(bindId), appName);
        log.debug("heraAppEnvVo,result:{}", GSON.toJson(heraAppEnvVo));
        for (HeraAppEnvVo.EnvVo envVo : heraAppEnvVo.getEnvVos()) {
            LambdaQueryWrapper<HeraAppEnv> queryWrapper = new LambdaQueryWrapper<HeraAppEnv>().eq(HeraAppEnv::getHeraAppId, id).eq(HeraAppEnv::getAppId, Long.valueOf(bindId)).eq(HeraAppEnv::getEnvId, envVo.getEnvId());
            HeraAppEnv heraAppEnv = heraAppEnvMapper.selectOne(queryWrapper);
            if (null == heraAppEnv) {
                addAppEnvNotExist(heraAppEnvVo, envVo);
                return;
            }
            if (!CollectionUtils.isEqualCollection(heraAppEnv.getIpList(), envVo.getIpList())) {
                heraAppEnvVo.setId(heraAppEnv.getId());
                updateAppEnv(buildHeraAppOperateVo(heraAppEnvVo, envVo));
                //发送变更消息
                publishIpChangeMq(buildHeraEnvIpVo(heraAppEnvVo, envVo));
            }
        }
    }


    private void publishIpChangeMq(HeraEnvIpVo heraAppEnvVo) {
        String mqMessage = GSON.toJson(heraAppEnvVo);
        log.info("mq data:{}", mqMessage);

        Message message = new Message();
        message.setTopic(this.ipChangeTopic);
        message.setBody(mqMessage.getBytes(StandardCharsets.UTF_8));
        try {
            defaultMQProducer.send(message);
            log.info("publish ip change MqMessage success");
        } catch (Exception e) {
            log.error("publish ip change MqMessage error,info:{}", mqMessage, e);
        }
    }


    @Override
    public void addAppEnvNotExist(HeraAppEnvVo heraAppEnvVo) {
        for (HeraAppEnvVo.EnvVo envVo : heraAppEnvVo.getEnvVos()) {
            addAppEnvNotExist(heraAppEnvVo, envVo);
        }
    }

    @Override
    public Boolean addAppEnvNotExist(HeraAppEnvVo heraAppEnvVo, HeraAppEnvVo.EnvVo envVo) {
        QueryWrapper<HeraAppEnv> queryWrapper = new QueryWrapper<HeraAppEnv>().eq("hera_app_id", heraAppEnvVo.getHeraAppId()).eq("app_id", heraAppEnvVo.getAppId()).eq("env_id", envVo.getEnvId());
        HeraAppEnv heraAppEnv = heraAppEnvMapper.selectOne(queryWrapper);
        if (null == heraAppEnv) {
            addAppEnv(buildHeraAppOperateVo(heraAppEnvVo, envVo));
            return true;
        }
        return false;
    }

    @Override
    public List<String> queryNonProbeAccessIPs() {
        List<String> ipList = new CopyOnWriteArrayList<>();
        Map<Long, Boolean> filterMap = new ConcurrentHashMap<>();
        long current = 1;
        long size = 100;

        while (true) {
            Page<HeraAppEnv> page = new Page<>(current, size);
            Page<HeraAppEnv> heraAppEnvPage = heraAppEnvMapper.selectPage(page, Wrappers.emptyWrapper());

            List<HeraAppEnv> pageRecords = heraAppEnvPage.getRecords();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (HeraAppEnv heraAppEnv : pageRecords) {
                futures.add(CompletableFuture.runAsync(() -> processHeraAppEnv(heraAppEnv, filterMap, ipList)));
            }
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.join();
            current++;

            if (!heraAppEnvPage.hasNext()) {
                break;
            }
        }
        return ipList.parallelStream().distinct().collect(Collectors.toList());
    }

    private void processHeraAppEnv(HeraAppEnv heraAppEnv, Map<Long, Boolean> filterMap, List<String> ipList) {
        if (filterMap.containsKey((heraAppEnv.getAppId())) && filterMap.get((heraAppEnv.getAppId()))) {
            ipList.addAll(heraAppEnv.getIpList());
        } else {
            EnvIpFetch envFetchFromRemote = defaultEnvIpFetch.getEnvFetchFromRemote(heraAppEnv.getAppId().toString());
            if (envFetchFromRemote != null) {
                ipList.addAll(heraAppEnv.getIpList());
            }
            filterMap.put(heraAppEnv.getAppId(), envFetchFromRemote != null);
        }
    }

    private HeraAppOperateVo buildHeraAppOperateVo(HeraAppEnvVo heraAppEnvVo, HeraAppEnvVo.EnvVo envVo) {
        HeraAppOperateVo heraAppOperateVo = new HeraAppOperateVo();
        BeanUtils.copyProperties(heraAppEnvVo, heraAppOperateVo);
        heraAppOperateVo.setEnvId(envVo.getEnvId());
        heraAppOperateVo.setEnvName(envVo.getEnvName());
        heraAppOperateVo.setIpList(envVo.getIpList());
        heraAppOperateVo.setOperator(DEFAULT_OPERATOR);
        return heraAppOperateVo;
    }

    private HeraEnvIpVo buildHeraEnvIpVo(HeraAppEnvVo heraAppEnvVo, HeraAppEnvVo.EnvVo envVo) {
        HeraEnvIpVo heraEnvIpVo = new HeraEnvIpVo();
        BeanUtils.copyProperties(heraAppEnvVo, heraEnvIpVo);
        BeanUtils.copyProperties(envVo, heraEnvIpVo);
        return heraEnvIpVo;
    }
}
