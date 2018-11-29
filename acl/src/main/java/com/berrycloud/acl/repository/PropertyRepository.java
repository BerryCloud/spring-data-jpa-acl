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

import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.security.access.prepost.PreAuthorize;

public interface PropertyRepository<T, ID> {

    Object findProperty(ID id, PersistentProperty<? extends PersistentProperty<?>> property, Pageable pageable);

    Object findProperty(ID id, PersistentProperty<? extends PersistentProperty<?>> property, Object propertyId);

    Object findPropertyComplement(ID id, PersistentProperty<? extends PersistentProperty<?>> property, Pageable pageable);

    /**
     * Helper method for calling clear on the EntityManager.
     *
     */
    void clear();

    /**
     * Update the entity without permission check. This method should be used with extreme caution. The permission
     * should be checked manually before using this method. (I.e. methods protected by {@link PreAuthorize} annotation.)
     * This method is used by {@link RepositoryAclPropertyReferenceController} where the permissions are checked for the
     * entity and all the modified properties manually before update.
     * 
     * @param entity
     * @return merged entity
     */
    <S extends T> S saveWithoutPermissionCheck(S entity);

}
