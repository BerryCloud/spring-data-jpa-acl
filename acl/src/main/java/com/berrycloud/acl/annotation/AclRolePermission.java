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
package com.berrycloud.acl.annotation;

import com.berrycloud.acl.repository.NoAcl;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static com.berrycloud.acl.AclConstants.ALL_PERMISSION;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines a list of permissions for a list of roles for this domain object. All of the users who has at least one role
 * from the provided list will gain all the permissions defined here for all of the entities of this domain class.
 * <p>
 * An empty list in the {@code roles} field means: ANY role.
 * <p>
 * If a domain class is NOT annotated with this annotation then the acl treats it as if it was annotated as
 *
 * <pre>
 * &#64;AclRolePermission(roles={"ROLE_ADMIN"}, value="all")
 * </pre>
 *
 * So a user with ADMIN_ROLE will gain all permissions to all objects. However if you add this annotation to a domain
 * class with any other settings, you have to manually set the privileges for the administrators. (Basically if you use
 * this annotation you override the default behaviour). This annotation can be used multiple times.
 * <p>
 * Another Example:
 *
 * <pre>
 * &#64;AclRolePermission(roles={}, value="all")
 * </pre>
 *
 * It means: users with ANY roles gain all permission to the entities of this domain class. By using these settings you
 * can basically turn off the acl for this domain class. (see also {@link NoAcl})
 * <p>
 * You can define multiple permissions in the {@link #value} field. If the list contains {@code "all"} then it provides
 * all possible permissions. If the list contains any permissions (not empty) it automatically provides {@code "read"}
 * permission too.
 *
 * @@author István Rátkai (Selindek)
 *
 */
@Target({ TYPE })
@Retention(RUNTIME)
@Documented
public @interface AclRolePermission {

    /**
     * The list of permissions the roles defined in the role field gain.
     */
    String[] value() default { ALL_PERMISSION };

    /**
     * The list of roles (by string representation) which gains the permission defined in the values field. Empty array
     * means ANY role.
     */
    String[] roles() default {};
}
