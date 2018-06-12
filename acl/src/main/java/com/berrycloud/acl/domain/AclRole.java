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

/**
 * Interface for role Entity. (Role is basically a string what becomes the 'authority' property of a GrantedAuthority
 * object what will be assigned to the associated Users.) There should be exactly one domain class in the context what
 * implements this interface. If there is no such a class in the context the {@link SimpleAclRole} class is used as
 * default implementation.
 * <p>
 * {@code AclRole} entities will be automatically picked up when a user object is loaded by the AclUserDetailsService.
 *
 * @author István Rátkai (Selindek)
 *
 */
public interface AclRole {

    @Column(nullable = false, unique = true)
    String getRoleName();
}
