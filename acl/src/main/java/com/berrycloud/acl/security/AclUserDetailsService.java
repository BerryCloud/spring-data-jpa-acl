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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.berrycloud.acl.domain.AclRole;

/**
 * Extension of {@link UserDetailsService}. It creates an additional method {@link #createGrantedAuthority} what should
 * be used for creating a GrantedAuthorithy object from the role strings stored in the {@link AclRole} domain entities
 * 
 * @author István Rátkai (Selindek)
 *
 */
public interface AclUserDetailsService<A extends GrantedAuthority> extends UserDetailsService {

    /**
     * {@inheritDoc}
     */
    @Override
    AclUserDetails loadUserByUsername(String username) throws UsernameNotFoundException;

    /**
     * Create a GrantedAuthority object from an authority String. All acl methods use this method for creating
     * authorities, so by overriding this method in a derived AclUserDetailsService one can introduce an extended
     * GrantedAuthority object for the whole Acl/security/authentication framework
     */
    A createGrantedAuthority(String authority);
}
