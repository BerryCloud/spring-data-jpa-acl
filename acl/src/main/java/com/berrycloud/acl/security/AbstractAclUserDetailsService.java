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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import com.berrycloud.acl.AclLogic;
import com.berrycloud.acl.domain.AclRole;
import com.berrycloud.acl.domain.AclUser;

/**
 * Abstract superclass for {@link AclUserDetailsService} implementations. It uses the default
 * {@link AclLogic#loadUserByUsername} method for creting the default {@link AclUserDetails} object.
 *
 * @author István Rátkai (Selindek)
 */
public abstract class AbstractAclUserDetailsService<A extends GrantedAuthority> implements AclUserDetailsService<A> {
    // TODO extend it to a UserDetailsManager ???

    private final AclLogic aclLogic;

    protected AbstractAclUserDetailsService(AclLogic aclLogic) {
        this.aclLogic = aclLogic;
    }

    @Override
    @Transactional(readOnly = true)
    public AclUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AclUser aclUser = aclLogic.loadUserByUsername(username);
        return createUserDetails(aclUser, createAuthorities(aclLogic.getAllRoles(aclUser)));
    }

    /**
     * Subclasses should override this method for creating extended AclUserDetails objects.
     */
    protected AclUserDetails createUserDetails(AclUser aclUser, Collection<A> authorities) {
        return new SimpleAclUserDetails(aclLogic.getUserId(aclUser), aclUser.getUsername(), aclUser.getPassword(),
                authorities);
    }
    protected Collection<A> createAuthorities(AclUser aclUser) {
    	return createAuthorities(aclLogic.getAllRoles(aclUser));
    }

    protected Collection<A> createAuthorities(Set<AclRole> roleSet) {
        Set<A> grantedAuthorities = new HashSet<>();
        for (AclRole role : roleSet) {
            grantedAuthorities.add(createGrantedAuthority(role.getRoleName()));
        }
        return grantedAuthorities;
    }

    @Override
    public abstract A createGrantedAuthority(String authority);
}
