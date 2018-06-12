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

import static com.berrycloud.acl.AclConstants.ALL_PERMISSION;
import static com.berrycloud.acl.AclConstants.PERMISSION_PREFIX_DELIMITER;
import static com.berrycloud.acl.AclConstants.READ_PERMISSION;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Common superclass for all ACL permission descriptor metadata.
 *
 * @author István Rátkai (Selindek)
 *
 */
public class PermissionData {

    private final Set<String> permissions;
    private final Set<String> allPrefixes;

    public PermissionData(String[] permissions) {
        this.permissions = new HashSet<>(Arrays.asList(permissions));
        this.allPrefixes = new HashSet<>();
        calculatePermissions();
    }

    private void calculatePermissions() {
        Set<String> extraPermissions = new HashSet<>();
        for (String permission : permissions) {
            int index = getPermissionIndex(permission);
            extraPermissions.add(permission.substring(0, index) + READ_PERMISSION);
            if (permission.substring(index).equals(ALL_PERMISSION)) {
                allPrefixes.add(permission.substring(0, index));
            }
        }
        permissions.addAll(extraPermissions);
    }

    protected int getPermissionIndex(String permission) {
        return permission.lastIndexOf(PERMISSION_PREFIX_DELIMITER) + 1;
    }

    public boolean hasPermission(String permission) {
        String prefix = permission.substring(0, getPermissionIndex(permission));
        return allPrefixes.contains(prefix) || permissions.contains(permission);
    }
}
