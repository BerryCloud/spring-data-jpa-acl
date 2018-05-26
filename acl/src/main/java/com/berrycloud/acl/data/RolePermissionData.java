/*
 * Copyright 2017 the original author or authors.
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
package com.berrycloud.acl.data;

import com.berrycloud.acl.annotation.AclRolePermission;

/**
 * Storage class for storing the processed data of {@link AclRolePermission} annotations.
 *
 * @author István Rátkai (Selindek)
 */
public class RolePermissionData extends PermissionData {

    private final String[] authorities;

    public RolePermissionData(String[] authorities, String[] permissions) {
        super(permissions);
        this.authorities = authorities;
    }

    public String[] getAuthorities() {
        return authorities;
    }
}
