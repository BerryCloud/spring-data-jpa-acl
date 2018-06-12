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
package com.berrycloud.acl.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.io.Serializable;
import java.util.Collection;

/**
 * Default implementation for the {@link AclUserDetails} interface.
 *
 * @author István Rátkai (Selindek)
 */
public class SimpleAclUserDetails extends User implements AclUserDetails {

    private static final long serialVersionUID = 5998681433255152586L;

    private final Serializable userId;

    public SimpleAclUserDetails(Serializable userId, String username, String password,
                                Collection<? extends GrantedAuthority> authorities) {
        this(userId, username, password, true, true, true, true, authorities);
    }

    public SimpleAclUserDetails(Serializable userId, String username, String password, boolean enabled,
                                boolean accountNonExpired, boolean credentialsNonExpired, boolean accountNonLocked,
                                Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
        this.userId = userId;
    }

    @Override
    public Serializable getUserId() {
        return userId;
    }
}
