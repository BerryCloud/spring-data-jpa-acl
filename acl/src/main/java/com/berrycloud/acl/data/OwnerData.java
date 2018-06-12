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

import com.berrycloud.acl.annotation.AclOwner;

/**
 * Storage class for storing the processed data of {@link AclOwner} annotations.
 *
 * @author István Rátkai (Selindek)
 */
public class OwnerData extends PropertyPermissionData {

    private final Class<?> propertyType;
    private final boolean collection;

    public OwnerData(String propertyName, Class<?> propertyType, boolean collection, String[] permissions) {
        super(propertyName, permissions);
        this.propertyType = propertyType;
        this.collection = collection;
    }

    public boolean isCollection() {
        return collection;
    }

    public Class<?> getPropertyType() {
        return propertyType;
    }
}
