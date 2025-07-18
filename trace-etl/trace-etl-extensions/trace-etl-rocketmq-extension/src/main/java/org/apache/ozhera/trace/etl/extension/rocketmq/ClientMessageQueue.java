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

package org.apache.ozhera.trace.etl.extension.rocketmq;

import org.apache.ozhera.trace.etl.common.HashUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Description
 * @Date 2022/11/6 4:27 pm
 */
@Slf4j
public class ClientMessageQueue {
    public List<ClientMessageQueueWrapper> clientMessageQueues = new CopyOnWriteArrayList<>();

    private volatile int rocketMQQueueSize;

    private RocketMQExtension producer;

    private static final int CLIENT_QUEUE_SIZE = 2000;

    private final static int FETCH_ROCKETMQ_QUEUE_GAP = 10;

    public ClientMessageQueue(RocketMQExtension producer) {
        this.producer = producer;
    }

    public void setRocketMQQueueSize(int size) {
        rocketMQQueueSize = size;
    }

    public void checkClientQueue(List<MessageQueue> queueList) {
        if (queueList == null || queueList.size() == 0) {
            return;
        }
        // If the local message queue is missing, add it
        queueList.stream()
                .filter(i -> !clientMessageQueues.stream().anyMatch(j -> j.getRocketMQMessageQueue().equals(i)))
                .forEach(queue -> clientMessageQueues.add(new ClientMessageQueueWrapper(queue, new ArrayBlockingQueue<>(CLIENT_QUEUE_SIZE), producer)));

        setRocketMQQueueSize(queueList.size());

        // If the local message queue is extra, it will be destroyed
        List<ClientMessageQueueWrapper> collect = clientMessageQueues.stream()
                .filter(i -> !queueList.contains(i.getRocketMQMessageQueue()))
                .collect(Collectors.toList());

        clientMessageQueues.stream()
                .filter(i -> !queueList.contains(i.getRocketMQMessageQueue()))
                .forEach(clientMessageQueues::remove);

        collect.forEach(ClientMessageQueueWrapper::stopExport);
    }

    public void enqueue(String traceId, MessageExt message) {
        try {
            // hash by traceId
            int i = HashUtil.consistentHash(traceId, rocketMQQueueSize);
            ClientMessageQueueWrapper clientMessageQueueWrapper = clientMessageQueues.get(i);
            clientMessageQueueWrapper.getClientMessageQueue().put(message);
        } catch (Throwable t) {
            log.error("client queue enqueue error : ", t);
        }
    }

    public void initFetchQueueTask() {
        new ScheduledThreadPoolExecutor(1).scheduleAtFixedRate(
                () -> {
                    List<MessageQueue> messageQueues = producer.fetchMessageQueue();
                    log.info("fetch message queue size : " + messageQueues.size());
                    if (messageQueues == null || messageQueues.size() == 0) {
                        return;
                    }
                    checkClientQueue(messageQueues);
                },
                0,
                FETCH_ROCKETMQ_QUEUE_GAP,
                TimeUnit.SECONDS);
    }
}