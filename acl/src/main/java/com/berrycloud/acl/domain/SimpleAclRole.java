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
package com.berrycloud.acl.domain;

import com.berrycloud.acl.AclConstants;
import com.berrycloud.acl.annotation.AclRolePermission;
import com.berrycloud.acl.annotation.AclRolePermissions;
import org.springframework.core.style.ToStringCreator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * Default implementation of the {@link AclRole} interface.
 *
 * @author István Rátkai (Selindek)
 */
@Entity
@AclRolePermissions(value = {
        @AclRolePermission(roles = {}, value = AclConstants.READ_PERMISSION),
        @AclRolePermission(roles = AclConstants.ROLE_ADMIN)
})
public class SimpleAclRole implements AclRole {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roleName;

    protected SimpleAclRole() {
    }

    public SimpleAclRole(String roleName) {
        this.roleName = roleName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    @Override
    public int hashCode() {
        return getRoleName() == null ? 0 : getRoleName().hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null || !object.getClass().equals(this.getClass())) {
            return false;
        }
        if (getRoleName() == null) {
            return ((AclRole) object).getRoleName() == null;
        }
        return getRoleName().equals(((AclRole) object).getRoleName());
    }

    @Override
    public String toString() {
        return new ToStringCreator(this).append("roleName", getRoleName()).toString();
    }
}
