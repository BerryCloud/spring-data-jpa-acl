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

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;

import javax.persistence.EntityNotFoundException;
import java.io.Serializable;
import java.util.List;

/**
 * Repository interface extension for loading entities by non-default permission. Most ethods are used by
 * PermissionEvaluators.
 *
 * @author István Rátkai (Selindek)
 */
public interface AclJpaRepository<T, ID extends Serializable> extends PropertyRepository<T, ID>, JpaRepository<T, ID> {

    /**
     * Find an entity by id with testing the current user's permission against the given permission during
     * permission-check
     *
     * @param id         the id of the entity
     * @param permission the permission we check against
     * @return the entity with the given id or null if it's not exist or the current user has no proper permission to it
     */
    T findOne(ID id, String permission);

    /**
     * Find an entity by id with testing the current user's permission against the given permission during
     * permission-check
     *
     * @param id         the id of the entity
     * @param permission the permission we check against
     * @return the entity with the given id
     * @throws EntityNotFoundException if the entity cannot be found or the current user has no proper permission to it
     */
    T getOne(ID id, String permission);

    /**
     * Returns all instances of the type filtered by the given permission.
     *
     * @return all entities the current user has the given permission to
     */
    List<T> findAll(String permission);

    T findOne(Specification<T> spec, String permission);

    List<T> findAll(Iterable<ID> ids, String permission);

    /**
     * Delete the entity without permission check. This method should be used with extreme caution. The permission
     * should be checked manually before using this method. (I.e. methods protected by {@link PreAuthorize} annotation.)
     *
     * @param entity the entity
     */
    void deleteWithoutPermissionCheck(T entity);

    /**
     * Find an entity by id with without testing the current user's permission to it.
     *
     * @param id the id of the entity
     * @return the entity with the given id or null if it's not exist or the current user has no proper permission to it
     */
    T findOneWithoutPermissionCheck(ID id);
}
