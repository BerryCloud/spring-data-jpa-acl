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
package com.berrycloud.acl.security.access;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

import com.berrycloud.acl.AclSpecification;

/**
 * PermissionEvaluator for ACL managed entities. Permissions are checked via {@link AclSpecification}.
 *
 * @author István Rátkai (Selindek)
 */
public class AclPermissionEvaluator implements PermissionEvaluator {

    private static Logger LOG = LoggerFactory.getLogger(AclPermissionEvaluator.class);

    // Dynamically filled cache for entityInformation
    private final Map<Class<?>, JpaEntityInformation<?, ?>> entityInformationMap = new HashMap<>();

    private final EntityManager em;
    private final AclSpecification aclSpecification;

    public AclPermissionEvaluator(final EntityManager em, AclSpecification aclSpecification) {
        this.em = em;
        this.aclSpecification = aclSpecification;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (targetDomainObject == null) {
            return false;
        }
        try {
            Class<?> domainClass = targetDomainObject.getClass();
            return hasPermission(authentication, getId(targetDomainObject), domainClass, permission);
        } catch (Exception ex) {
            LOG.warn("Invalid target for AclPermissionEvaluator: {}", targetDomainObject);
            LOG.trace("Details: ", ex);
            return false;
        }
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
            Object permission) {
        try {
            return hasPermission(authentication, targetId, Class.forName(targetType), permission);
        } catch (ClassNotFoundException ex) {
            LOG.warn("Invalid target type for AclPermissionEvaluator: {}", targetType);
            LOG.trace("Details: ", ex);
            return false;
        }
    }

    /**
     * Check permission by directly creating a JPA count query with ACL support for the given permission
     */
    public <T> boolean hasPermission(Authentication authentication, Object targetId,
            Class<T> domainClass, Object permission) {
        String permissionString = getPermissionString(permission);

        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);

        Root<T> root = query.from(domainClass);
        query.select(builder.count(root));

        Predicate idPredicate = builder.equal(root.get(getEntityInformation(domainClass).getIdAttribute()), targetId);
        Predicate aclPredicate = aclSpecification.toPredicate(root, query, builder, permissionString);
        query.where(builder.and(idPredicate, aclPredicate));
        return em.createQuery(query).getSingleResult() != 0;
    }

    protected <T> Object getId(T object) {
        @SuppressWarnings("unchecked")
        Class<T> domainClass = (Class<T>) object.getClass();
        JpaEntityInformation<T, ?> entityInformation = getEntityInformation(domainClass);

        return entityInformation.getId(object);
    }

    protected <T> JpaEntityInformation<T, ?> getEntityInformation(Class<T> domainClass) {
        @SuppressWarnings("unchecked")
        JpaEntityInformation<T, ?> entityInformation = (JpaEntityInformation<T, ?>) entityInformationMap
                .get(domainClass);
        if (entityInformation == null) {
            entityInformation = JpaEntityInformationSupport.getEntityInformation(domainClass, em);
            entityInformationMap.put(domainClass, entityInformation);
        }
        return entityInformation;
    }

    protected String getPermissionString(Object permission) {
        return permission.toString();
    }
}
