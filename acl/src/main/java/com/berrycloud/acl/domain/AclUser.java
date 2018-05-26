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

import javax.persistence.Column;

import com.berrycloud.acl.annotation.AclOwner;
import com.berrycloud.acl.annotation.AclParent;
import com.berrycloud.acl.annotation.AclRoleCondition;
import com.berrycloud.acl.annotation.AclRolePermission;
import com.berrycloud.acl.annotation.AclSelf;

/**
 * Interface for User domain class. There should be exactly one domain class in the context what implements this
 * interface. Users are handled automatically by the AclUserdetailsService (or its subclasses) and can gain permissions
 * to other entities via {@link AclRole} entities and Acl role annotations like {@link AclRolePermission} or
 * {@link AclRoleCondition}, Acl permission annotations like {@link AclOwner}, {@link AclParent} or {@link AclSelf}, or
 * via {@link PermissionLink} entities.
 *
 * @author István Rátkai (Selindek)
 *
 */
public interface AclUser {

    @Column(nullable = false, unique = true)
    String getUsername();

    String getPassword();
}
