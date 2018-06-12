/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.berrycloud.acl;

/**
 * Constant for the ACl
 *
 * @author István Rátkai (Selindek)
 */
public class AclConstants {

    public static final String ALL_PERMISSION = "all";
    public static final String READ_PERMISSION = "read";
    public static final String DELETE_PERMISSION = "delete";
    public static final String UPDATE_PERMISSION = "update";
    public static final String CREATE_PERMISSION = "create";

    public static final char PERMISSION_PREFIX_DELIMITER = '-';

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_USER = "ROLE_USER";
}
