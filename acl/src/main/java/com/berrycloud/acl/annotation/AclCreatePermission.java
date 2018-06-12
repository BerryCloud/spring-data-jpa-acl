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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Defines create permission for a list of roles for this domain class. All of the users who has at least one role from
 * the provided list will gain create permission to the annotated domain class.
 * <p>
 * An empty list in the {@code roles} field means: ANY role.
 * <p>
 * This annotation can be used multiple times.
 *
 * @author István Rátkai (Selindek)
 *
 */
@Target({ TYPE })
@Retention(RUNTIME)
@Documented
public @interface AclCreatePermission {

    /**
     * The list of roles (by string representation) which gains the create permission Empty array means ANY role.
     */
    String[] roles() default {};
}
