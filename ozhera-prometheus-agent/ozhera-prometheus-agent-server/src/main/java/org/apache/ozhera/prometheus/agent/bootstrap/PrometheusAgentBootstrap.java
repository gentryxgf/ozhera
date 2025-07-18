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

package org.apache.ozhera.prometheus.agent.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.DubboComponentScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@EnableAutoConfiguration
@ComponentScan(basePackages = {"org.apache.ozhera.prometheus.agent", "com.xiaomi.youpin"})
@DubboComponentScan(basePackages = "org.apache.ozhera.prometheus.agent")
@Slf4j
public class PrometheusAgentBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(PrometheusAgentBootstrap.class);

    public static void main(String... args) {
        try {
            SpringApplication.run(PrometheusAgentBootstrap.class, args);
        } catch (Throwable throwable) {
            logger.error(throwable.getMessage(), throwable);
            System.exit(-1);
        }
    }
}