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
package com.berrycloud.acl;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import com.berrycloud.acl.security.AclUserDetails;

/**
 * Utility methods for ACL Security.
 *
 * @author István Rátkai (Selindek)
 */
public class AclUtils {

    /**
     * Get the Principal from the SecurityContext or null if there is no authentication
     */
    public Object getPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getPrincipal();
        }
        return null;
    }

    /**
     * Get the current user's AclUserDetails object from the securityContext or null if it's not an AclUserDetails
     */
    public AclUserDetails getAclUserDetails() {
        Object principal = getPrincipal();
        if (principal instanceof AclUserDetails) {
            return (AclUserDetails) principal;
        }
        return null;
    }

    /**
     * Get the current user's UserDetails object from the securityContext or null if it's not an UserDetails
     */
    public UserDetails getUserDetails() {
        Object principal = getPrincipal();
        if (principal instanceof UserDetails) {
            return (UserDetails) principal;
        }
        return null;
    }

    /**
     * Get the current user's username from the securityContext or null if it cannot be found
     */
    public String getUsername() {
        Object principal = getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else if (principal != null) {
            return principal.toString();
        }
        return null;
    }

    /**
     * Checks if the current user is an administrator (Has ROLE_ADMIN role)
     */
    public boolean isAdmin() {
        return hasAuthority(AclConstants.ROLE_ADMIN);
    }

    /**
     * Checks if the current user has the given authority
     *
     * @param authority
     */
    public boolean hasAuthority(String authority) {
        UserDetails currentUser = getUserDetails();
        if (authority != null && currentUser != null) {
            for (GrantedAuthority grantedAuthority : currentUser.getAuthorities()) {
                if (authority.equals(grantedAuthority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if any authority of the current principal is in the provided set. If the set is empty it automatically
     * returns true. (Empty set means: ANY authority)
     *
     * @param authorities
     */
    public boolean hasAnyAuthorities(String[] authorities) {
        if (authorities.length == 0) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
                for (String authority : authorities) {
                    if (authority.equals(grantedAuthority.getAuthority())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
