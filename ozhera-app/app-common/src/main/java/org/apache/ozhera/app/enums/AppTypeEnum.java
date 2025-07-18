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
package org.apache.ozhera.app.enums;

import lombok.Getter;

/**
 * @version 1.0
 * @description
 * @date 2023/5/25 10:54
 */
@Getter
public enum AppTypeEnum {

    BUSINESS_TYPE(0, "businessType"),
    HOST_TYPE(1, "hostType");
    private Integer code;
    private String message;

    AppTypeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static AppTypeEnum getEnum(Integer code) {
        if (code == null) {
            return null;
        }
        AppTypeEnum[] values = AppTypeEnum.values();
        for (AppTypeEnum value : values) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
