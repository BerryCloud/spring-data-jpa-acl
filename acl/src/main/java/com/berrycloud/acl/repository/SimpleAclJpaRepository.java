/*
 * Copyright 2008-2017 the original author or authors.
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

import static com.berrycloud.acl.AclConstants.DELETE_PERMISSION;
import static com.berrycloud.acl.AclConstants.READ_PERMISSION;
import static com.berrycloud.acl.AclConstants.UPDATE_PERMISSION;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.Parameter;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.CrudMethodMetadata;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.berrycloud.acl.AclSpecification;

/**
 * Default implementation of the {@link AclJpaRepository} interface. This class uses the default SimpleJpaRepository
 * methods and logic and extends it with the ACL support
 *
 * @author Oliver Gierke
 * @author Eberhard Wolff
 * @author Thomas Darimont
 * @author Mark Paluch
 * @author István Rátkai (Selindek)
 *
 * @param <T>
 *            the type of the entity to handle
 * @param <ID>
 *            the type of the entity's identifier
 */
@Repository
@Transactional(readOnly = true)
public class SimpleAclJpaRepository<T, ID extends Serializable> extends SimpleJpaRepository<T, ID>
        implements AclJpaRepository<T, ID> {

    private static final String ID_MUST_NOT_BE_NULL = "The given id must not be null!";

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager em;

    private CrudMethodMetadata metadata;
    private AclSpecification aclSpecification;

    /**
     * Creates a new {@link SimpleAclJpaRepository} to manage objects of the given {@link JpaEntityInformation}.
     *
     * @param entityInformation
     *            must not be {@literal null}.
     * @param entityManager
     *            must not be {@literal null}.
     */
    public SimpleAclJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);

        this.entityInformation = entityInformation;
        this.em = entityManager;
    }

    /**
     * Creates a new {@link SimpleJpaRepository} to manage objects of the given domain type.
     *
     * @param domainClass
     *            must not be {@literal null}.
     * @param em
     *            must not be {@literal null}.
     */
    public SimpleAclJpaRepository(Class<T> domainClass, EntityManager em) {
        this(JpaEntityInformationSupport.getEntityInformation(domainClass, em), em);
    }

    /**
     * Configures a custom {@link CrudMethodMetadata} to be used to detect {@link LockModeType}s and query hints to be
     * applied to queries.
     *
     * @param crudMethodMetadata
     */
    @Override
    public void setRepositoryMethodMetadata(CrudMethodMetadata crudMethodMetadata) {
        super.setRepositoryMethodMetadata(crudMethodMetadata);
        this.metadata = crudMethodMetadata;
    }

    public void setAclSpecification(AclSpecification aclSpecification) {
        this.aclSpecification = aclSpecification;
    }

    @Override
    protected CrudMethodMetadata getRepositoryMethodMetadata() {
        return metadata;
    }

    @Override
    protected Class<T> getDomainClass() {
        return entityInformation.getJavaType();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.repository.CrudRepository#delete(java.io. Serializable)
     */
    @Override
    @Transactional
    public void delete(ID id) {
        T entity = findOne(id, DELETE_PERMISSION);

        if (entity == null) {
            throw new EntityNotFoundException("Entity cannot be deleted");
        }
        deleteWithoutPermissionCheck(entity);

    }

    @Override
    @Transactional
    public void deleteWithoutPermissionCheck(T entity) {
        em.remove(em.contains(entity) ? entity : em.merge(entity));
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.repository.CrudRepository#delete(java.lang. Object)
     */
    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public void delete(T entity) {

        Assert.notNull(entity, "The entity must not be null!");
        delete((ID) (entityInformation.getId(entity)));

    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.jpa.repository.JpaRepository#deleteInBatch(java. lang.Iterable)
     */
    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public void deleteInBatch(Iterable<T> entities) {
        Assert.notNull(entities, "The given Iterable of entities not be null!");

        Iterator<T> iterator = entities.iterator();
        if (!iterator.hasNext()) {
            return;
        }

        List<T> list;

        if (aclSpecification != null) {
            List<ID> ids = new ArrayList<>();
            for (T e : entities) {
                ids.add((ID) entityInformation.getId(e));
            }
            list = findAll(ids, DELETE_PERMISSION);
        } else {
            list = new ArrayList<>();
            for (T e : entities) {
                list.add(e);
            }
        }

        doDelete(list);
    }

    protected void doDelete(List<T> list) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(getDomainClass());
        Root<T> root = delete.from(getDomainClass());

        Predicate inPredicate = root.in(list);

        delete.where(inPredicate);

        em.createQuery(delete).executeUpdate();
    }

    @Override
    public List<T> findAll(Iterable<ID> ids, String permission) {

        if (ids == null || !ids.iterator().hasNext()) {
            return Collections.emptyList();
        }

        if (entityInformation.hasCompositeId()) {

            List<T> results = new ArrayList<>();

            for (ID id : ids) {
                results.add(findOne(id));
            }

            return results;
        }

        ByIdsSpecification<T> specification = new ByIdsSpecification<>(entityInformation);
        TypedQuery<T> query = getQuery(specification, (Sort) null, permission);

        return query.setParameter(specification.parameter, ids).getResultList();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.repository.Repository#deleteAll()
     */
    @Override
    @Transactional
    public void deleteAll() {
        doDelete(findAll(DELETE_PERMISSION));
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.jpa.repository.JpaRepository#deleteAllInBatch()
     */
    @Override
    @Transactional
    public void deleteAllInBatch() {
        if (aclSpecification != null) {
            deleteAll();
            return;
        }
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<T> delete = cb.createCriteriaDelete(getDomainClass());
        delete.from(getDomainClass());
        em.createQuery(delete).executeUpdate();
    }

    @Override
    public T findOneWithoutPermissionCheck(ID id) {
        return findOne(id, null);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.repository.CrudRepository#findOne(java.io. Serializable)
     */
    @Override
    public T findOne(ID id) {
        return findOne(id, READ_PERMISSION);
    }

    @Override
    public T findOne(final ID id, String permission) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        return findOne(new Specification<T>() {

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                return cb.equal(root.get(entityInformation.getIdAttribute()), id);
            }

        }, permission);

    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.jpa.repository.JpaRepository#getOne(java.io. Serializable)
     */
    @Override
    public T getOne(ID id) {
        return getOne(id, READ_PERMISSION);
    }

    @Override
    public T getOne(ID id, String permission) {

        T entity = this.findOne(id, permission);
        if (entity == null) {
            throw new EntityNotFoundException("Unable to find " + getDomainClass().getName() + " with id " + id);
        }
        return entity;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.repository.CrudRepository#exists(java.io. Serializable)
     */
    @Override
    public boolean exists(ID id) {
        return findOne(id) != null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.jpa.repository.JpaRepository#findAll()
     */
    @Override
    public List<T> findAll() {
        return findAll(READ_PERMISSION);
    }

    @Override
    public List<T> findAll(String permission) {
        return getQuery(null, (Sort) null, permission).getResultList();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.jpa.repository.JpaSpecificationExecutor#findOne(
     * org.springframework.data.jpa.domain.Specification)
     */
    @Override
    public T findOne(Specification<T> spec) {
        return findOne(spec, READ_PERMISSION);
    }

    @Override
    public T findOne(Specification<T> spec, String permission) {

        try {
            return getQuery(spec, (Sort) null, permission).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.repository.CrudRepository#count()
     */
    @Override
    public long count() {
        return count((Specification<T>) null);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.springframework.data.repository.CrudRepository#save(java.lang.Object)
     */
    @SuppressWarnings("unchecked")
    @Override
    @Transactional
    public <S extends T> S save(S entity) {
        if (aclSpecification == null) {
            return saveWithoutPermissionCheck(entity);
        }
        if (entityInformation.isNew(entity)) {
            if (!aclSpecification.canBeCreated(entity)) {
                throw new EntityNotFoundException("New entity cannot be created.");
            }
            em.persist(entity);
            return entity;
        } else {
            FlushModeType oldMode = em.getFlushMode();
            em.setFlushMode(FlushModeType.COMMIT);
            getOne((ID) (entityInformation.getId(entity)), UPDATE_PERMISSION);
            em.setFlushMode(oldMode);
            return em.merge(entity);
        }
    }

    @Override
    @Transactional
    public <S extends T> S saveWithoutPermissionCheck(S entity) {
        if (entityInformation.isNew(entity)) {
            em.persist(entity);
            return entity;
        } else {
            return em.merge(entity);
        }
    }

    /**
     * Creates a {@link TypedQuery} for the given {@link Specification} and {@link Sort}.
     *
     * @param spec
     *            can be {@literal null}.
     * @param sort
     *            can be {@literal null}.
     * @return
     */
    @Override
    protected TypedQuery<T> getQuery(Specification<T> spec, Sort sort) {
        return getQuery(spec, sort, READ_PERMISSION);
    }

    protected TypedQuery<T> getQuery(Specification<T> spec, Sort sort, String permission) {
        return getQuery(spec, getDomainClass(), sort, permission);
    }

    /**
     * Creates a {@link TypedQuery} for the given {@link Specification} and {@link Sort}.
     *
     * @param spec
     *            can be {@literal null}.
     * @param domainClass
     *            must not be {@literal null}.
     * @param sort
     *            can be {@literal null}.
     * @return
     */
    @Override
    protected <S extends T> TypedQuery<S> getQuery(Specification<S> spec, Class<S> domainClass, Sort sort) {
        return getQuery(spec, domainClass, sort, READ_PERMISSION);
    }

    protected <S extends T> TypedQuery<S> getQuery(Specification<S> spec, Class<S> domainClass, Sort sort,
            String permission) {

        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<S> query = builder.createQuery(domainClass);

        Root<S> root = applySpecificationToCriteria(spec, domainClass, query, permission);
        if (query.getSelection() == null) {
            query.select(root);
        }
        if (sort != null && query.getSelection() instanceof From) {
            query.orderBy(AclQueryUtils.toOrders(sort, (From<?, ?>) query.getSelection(), builder));
        }

        return applyRepositoryMethodMetadata(em.createQuery(query));
    }

    /**
     * Creates a new count query for the given {@link Specification}.
     *
     * @param spec
     *            can be {@literal null}.
     * @param domainClass
     *            must not be {@literal null}.
     * @return
     */
    @Override
    protected <S extends T> TypedQuery<Long> getCountQuery(Specification<S> spec, Class<S> domainClass) {

        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);

        Root<S> root = applySpecificationToCriteria(spec, domainClass, query, READ_PERMISSION);

        if (query.isDistinct()) {
            query.select(builder.countDistinct(root));
        } else {
            query.select(builder.count(root));
        }

        // Remove all Orders the Specifications might have applied
        query.orderBy(Collections.<Order> emptyList());

        return em.createQuery(query);
    }

    /**
     * Applies the given {@link Specification} to the given {@link CriteriaQuery}.
     *
     * @param spec
     *            can be {@literal null}.
     * @param domainClass
     *            must not be {@literal null}.
     * @param query
     *            must not be {@literal null}.
     * @return
     */
    private <S, U extends T> Root<U> applySpecificationToCriteria(Specification<U> spec, Class<U> domainClass,
            CriteriaQuery<S> query, String permission) {

        Assert.notNull(query, "query cannot be null");
        Assert.notNull(domainClass, "domainClass cannot be null");
        Root<U> root = query.from(domainClass);

        CriteriaBuilder builder = em.getCriteriaBuilder();

        Predicate predicate = spec == null ? null : spec.toPredicate(root, query, builder);

        // Permission specification must be executed AFTER all of the other specifications
        if (aclSpecification != null && permission != null) {
            Predicate permissionPredicate = aclSpecification.toPredicate(root, query, builder, permission);
            predicate = predicate == null ? permissionPredicate : builder.and(predicate, permissionPredicate);
        }

        if (predicate != null) {
            query.where(predicate);
        }

        return root;
    }

    private <S> TypedQuery<S> applyRepositoryMethodMetadata(TypedQuery<S> query) {

        if (metadata == null) {
            return query;
        }

        LockModeType type = metadata.getLockModeType();
        TypedQuery<S> toReturn = type == null ? query : query.setLockMode(type);

        applyQueryHints(toReturn);

        return toReturn;
    }

    private void applyQueryHints(Query query) {

        for (Entry<String, Object> hint : getQueryHints().entrySet()) {
            query.setHint(hint.getKey(), hint.getValue());
        }
    }

    @Override
    @Transactional
    public void clear() {
        em.clear();
    }

    @Override
    @Transactional
    public Object findProperty(ID id, PersistentProperty<? extends PersistentProperty<?>> property, Pageable pageable) {
        if (property.isCollectionLike()) {
            return findAll(new PropertySpecification<T>(id, property), pageable);
        }
        return findOne(new PropertySpecification<T>(id, property));
    }

    @Override
    @Transactional
    public Object findProperty(ID id, PersistentProperty<? extends PersistentProperty<?>> property,
            Serializable propertyId) {
        if (property.isCollectionLike()) {
            return findOne(new PropertySpecification<T>(id, property, propertyId));
        }
        return findOne(new PropertySpecification<T>(id, property));
    }

    private class PropertySpecification<S> implements Specification<S> {

        private String propertyName;
        private ID ownerId;
        private String ownerIdName;
        private Serializable propertyId = null;

        public PropertySpecification(ID id, PersistentProperty<? extends PersistentProperty<?>> property) {
            this.propertyName = property.getName();
            this.ownerId = id;
            this.ownerIdName = property.getOwner().getIdProperty().getName();
        }

        public PropertySpecification(ID id, PersistentProperty<? extends PersistentProperty<?>> property,
                Serializable propertyId) {
            this(id, property);
            this.propertyId = propertyId;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public Predicate toPredicate(Root<S> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
            Join<?, ?> join = root.join(propertyName);
            query.select((Selection) (join));

            Predicate predicate = cb.equal(root.get(ownerIdName), ownerId);
            if (propertyId != null) {
                EntityType<?> et = em.getMetamodel().entity(join.getJavaType());
                SingularAttribute<?, ?> id = et.getId(et.getIdType().getJavaType());
                predicate = cb.and(predicate, cb.equal(join.get((SingularAttribute) id), propertyId));
            }

            return predicate;
        }

    }

    /**
     * Specification that gives access to the {@link Parameter} instance used to bind the ids for
     * {@link SimpleJpaRepository#findAll(Iterable)}. Workaround for OpenJPA not binding collections to in-clauses
     * correctly when using by-name binding.
     * 
     * @see <a href="https://issues.apache.org/jira/browse/OPENJPA-2018?focusedCommentId=13924055">OPENJPA-2018</a>
     * @author Oliver Gierke
     */
    @SuppressWarnings("rawtypes")
    private static final class ByIdsSpecification<T> implements Specification<T> {

        private final JpaEntityInformation<T, ?> entityInformation;

        ParameterExpression<Iterable> parameter;

        public ByIdsSpecification(JpaEntityInformation<T, ?> entityInformation) {
            this.entityInformation = entityInformation;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.springframework.data.jpa.domain.Specification#toPredicate(javax.persistence.criteria.Root,
         * javax.persistence.criteria.CriteriaQuery, javax.persistence.criteria.CriteriaBuilder)
         */
        @Override
        public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {

            Path<?> path = root.get(entityInformation.getIdAttribute());
            parameter = cb.parameter(Iterable.class);
            return path.in(parameter);
        }
    }
}
