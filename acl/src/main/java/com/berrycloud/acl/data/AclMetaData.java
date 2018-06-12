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

import java.util.Collections;
import java.util.Map;

/**
 * A storage class containing all ACL metadata for all the managed entities and for the logic itself. The data is
 * constructed during startup and its used during permission-evaluation.
 *
 * @author István Rátkai (Selindek)
 */
public class AclMetaData {

    private final Map<Class<?>, AclEntityMetaData> metaDataMap;
    private final PermissionData selfPermissions;

    public AclMetaData(Map<Class<?>, AclEntityMetaData> metaDataMap, PermissionData selfPermissions) {
        this.metaDataMap = Collections.unmodifiableMap(metaDataMap);
        this.selfPermissions = selfPermissions;
    }

    public PermissionData getSelfPermissions() {
        return selfPermissions;
    }

    public AclEntityMetaData getAclEntityMetaData(Class<?> entityClass) {
        return metaDataMap.get(entityClass);
    }
}
