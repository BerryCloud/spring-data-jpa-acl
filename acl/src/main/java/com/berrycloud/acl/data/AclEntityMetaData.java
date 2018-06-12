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

import java.util.ArrayList;
import java.util.List;

import javax.persistence.metamodel.SingularAttribute;

/**
 * A storage class containing all ACL metadata for a managed entities. The data is constructed during startup and its
 * used during permission-evaluation.
 *
 * @author István Rátkai (Selindek)
 */
public class AclEntityMetaData {

    private SingularAttribute<? super Object, ?> idAttribute;
    private List<OwnerData> ownerDataList = new ArrayList<>();
    private List<OwnerData> ownerGroupDataList = new ArrayList<>();
    private List<ParentData> parentDataList = new ArrayList<>();
    private List<PermissionLinkData> permissionLinkList = new ArrayList<>();
    private List<RolePermissionData> rolePermissionList = new ArrayList<>();
    private List<RolePermissionData> roleConditionList = new ArrayList<>();
    private List<CreatePermissionData> createPermissionList = new ArrayList<>();

    public List<OwnerData> getOwnerDataList() {
        return ownerDataList;
    }

    public List<ParentData> getParentDataList() {
        return parentDataList;
    }

    public List<OwnerData> getOwnerGroupDataList() {
        return ownerGroupDataList;
    }

    public List<PermissionLinkData> getPermissionLinkList() {
        return permissionLinkList;
    }

    public SingularAttribute<? super Object, ?> getIdAttribute() {
        return idAttribute;
    }

    public void setIdAttribute(SingularAttribute<? super Object, ?> idAttribute) {
        this.idAttribute = idAttribute;
    }

    public List<RolePermissionData> getRolePermissionList() {
        return rolePermissionList;
    }

    public List<RolePermissionData> getRoleConditionList() {
        return roleConditionList;
    }

    public List<CreatePermissionData> getCreatePermissionList() {
        return createPermissionList;
    }

    public void setCreatePermissionList(List<CreatePermissionData> createPermissionList) {
        this.createPermissionList = createPermissionList;
    }
}
