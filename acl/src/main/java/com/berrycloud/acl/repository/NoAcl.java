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
package com.berrycloud.acl.repository;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Signing interface for disabling the acl. This annotation can be used on {@code Repository} interfaces or on domain
 * classes. If it's used on a domain class then all entities of that domain class could be accessed by any user. The acl
 * will be completely turned off for that domain class. If it's used on a Repository then the acl will be turned of for
 * all of the methods of that Repository, but you can still access the entities using acl restrictions via an other
 * repository what is defined without this annotation.
 * <p>
 * You can create multiple repositories for the same domain class. A non-annotated one for public access (e.g. for API
 * methods) and an other one with {@link NoAcl} annotation for inner use. (e.g. for scheduled tasks or console commands)
 *
 * @author István Rátkai (Selindek)
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface NoAcl {
}
