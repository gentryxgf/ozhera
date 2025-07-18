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

package org.apache.ozhera.trace.etl.util.pool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Description
 * @Date 2022/4/28 3:40 pm
 */
@Component
@Slf4j
public class AsyncNotify {

    private ThreadPoolExecutor pool;

    private static final int QUEUE_SIZE = 30;

    @PostConstruct
    public void init(){
        BlockingQueue queue = new ArrayBlockingQueue(QUEUE_SIZE);
        pool = new ThreadPoolExecutor(2,2,1, TimeUnit.MINUTES,queue);
    }

    public void submit(Runnable runnable){
        pool.submit(runnable);
    }
}