/*
 * Copyright 2012-2017 the original author or authors.
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
package org.springframework.data.jpa.repository.query;

import com.berrycloud.acl.AclSpecification;
import com.berrycloud.acl.repository.NoAcl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.jpa.provider.PersistenceProvider;
import org.springframework.data.jpa.provider.QueryExtractor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.EvaluationContextProvider;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.util.Assert;

import javax.persistence.EntityManager;
import java.lang.reflect.Method;

/**
 * This class is an extension of the {@link JpaQueryLookupStrategy} class with some addition for the Acl. Unfortunately
 * the original class is a final class so I cannot extend it directly. The whole source was copy-pasted and the changes
 * was injected directly in the copied source.
 *
 * @author Oliver Gierke
 * @author Thomas Darimont
 * @author István Rátkai (Selindek)
 */
public final class AclJpaQueryLookupStrategy {

    private static Logger LOG = LoggerFactory.getLogger(AclJpaQueryLookupStrategy.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private AclJpaQueryLookupStrategy() {
    }

    /**
     * Base class for {@link QueryLookupStrategy} implementations that need access to an {@link EntityManager}.
     *
     * @author Oliver Gierke
     * @author Thomas Darimont
     */
    private abstract static class AbstractQueryLookupStrategy implements QueryLookupStrategy {

        private final EntityManager em;
        private final QueryExtractor provider;

        /**
         * Creates a new {@link AbstractQueryLookupStrategy}.
         *
         * @param em        the entity manager
         * @param extractor the query extractor
         */
        AbstractQueryLookupStrategy(EntityManager em, QueryExtractor extractor) {
            this.em = em;
            this.provider = extractor;
        }

        /*
         * (non-Javadoc)
         *
         * @see org.springframework.data.repository.query.QueryLookupStrategy#resolveQuery(java.lang.reflect.Method,
         * org.springframework.data.repository.core.RepositoryMetadata,
         * org.springframework.data.projection.ProjectionFactory, org.springframework.data.repository.core.NamedQueries)
         */
        @Override
        public final RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, ProjectionFactory factory,
                                                  NamedQueries namedQueries) {
            NoAcl noAclRepo = AnnotationUtils.findAnnotation(metadata.getRepositoryInterface(), NoAcl.class);
            NoAcl noAclMethod = AnnotationUtils.findAnnotation(method, NoAcl.class);
            boolean needAcl = noAclRepo == null && noAclMethod == null;

            RepositoryQuery query = resolveQuery(new JpaQueryMethod(method, metadata, factory, provider), em,
                    namedQueries, needAcl);

            if (needAcl && !(query instanceof PartTreeAclJpaQuery)) {
                LOG.error(
                        "Unsupported repository method '{}'. Acl was not activated for this method! Use @NoAcl annotation on the method for preventing this error message.",
                        method);
            }

            return query;
        }

        protected abstract RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em,
                                                        NamedQueries namedQueries, boolean needAcl);
    }

    /**
     * {@link QueryLookupStrategy} to create a query from the method name.
     *
     * @author Oliver Gierke
     * @author Thomas Darimont
     */
    private static class CreateQueryLookupStrategy extends AbstractQueryLookupStrategy {

        private final PersistenceProvider persistenceProvider;
        private final AclSpecification aclSpecification;

        CreateQueryLookupStrategy(EntityManager em, QueryExtractor extractor,
                                  AclSpecification aclSpecification) {
            super(em, extractor);
            this.persistenceProvider = PersistenceProvider.fromEntityManager(em);
            this.aclSpecification = aclSpecification;
        }

        @Override
        protected RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries,
                                               boolean needAcl) {

            try {
                if (needAcl) {
                    return new PartTreeAclJpaQuery(method, em, persistenceProvider, aclSpecification);
                } else {
                    return new PartTreeJpaQuery(method, em, persistenceProvider);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format("Could not create query metamodel for method %s!", method.toString()), e);
            }
        }
    }

    /**
     * {@link QueryLookupStrategy} that tries to detect a declared query declared via {@link Query} annotation followed
     * by a JPA named query lookup.
     *
     * @author Oliver Gierke
     * @author Thomas Darimont
     */
    private static class DeclaredQueryLookupStrategy extends AbstractQueryLookupStrategy {

        private final EvaluationContextProvider evaluationContextProvider;

        /**
         * Creates a new {@link DeclaredQueryLookupStrategy}.
         *
         * @param em                        the entity manager
         * @param extractor                 the query extractor
         * @param evaluationContextProvider the evaluation context provider
         */
        DeclaredQueryLookupStrategy(EntityManager em,
                                    QueryExtractor extractor,
                                    EvaluationContextProvider evaluationContextProvider) {

            super(em, extractor);
            this.evaluationContextProvider = evaluationContextProvider;
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * org.springframework.data.jpa.repository.query.JpaQueryLookupStrategy.AbstractQueryLookupStrategy#resolveQuery
         * (org.springframework .data.jpa.repository.query.JpaQueryMethod, javax.persistence.EntityManager,
         * org.springframework.data.repository.core.NamedQueries)
         */
        @Override
        protected RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries,
                                               boolean needAcl) {

            RepositoryQuery query = JpaQueryFactory.INSTANCE.fromQueryAnnotation(method, em, evaluationContextProvider);

            if (null != query) {
                return query;
            }

            query = JpaQueryFactory.INSTANCE.fromProcedureAnnotation(method, em);

            if (null != query) {
                return query;
            }

            String name = method.getNamedQueryName();
            if (namedQueries.hasQuery(name)) {
                return JpaQueryFactory.INSTANCE.fromMethodWithQueryString(method, em, namedQueries.getQuery(name),
                        evaluationContextProvider);
            }

            query = NamedQuery.lookupFrom(method, em);

            if (null != query) {
                return query;
            }

            throw new IllegalStateException(
                    String.format("Did neither find a NamedQuery nor an annotated query for method %s!", method));
        }
    }

    /**
     * {@link QueryLookupStrategy} to try to detect a declared query first (
     * {@link org.springframework.data.jpa.repository.Query}, JPA named query). In case none is found we fall back on
     * query creation.
     *
     * @author Oliver Gierke
     * @author Thomas Darimont
     */
    private static class CreateIfNotFoundQueryLookupStrategy extends AbstractQueryLookupStrategy {

        private final DeclaredQueryLookupStrategy lookupStrategy;
        private final CreateQueryLookupStrategy createStrategy;

        /**
         * Creates a new {@link CreateIfNotFoundQueryLookupStrategy}.
         *
         * @param em             the entity manager
         * @param extractor      the query extractor
         * @param createStrategy the create query lookup strategy
         * @param lookupStrategy the declared query lookup strategy
         */
        CreateIfNotFoundQueryLookupStrategy(EntityManager em,
                                            QueryExtractor extractor,
                                            CreateQueryLookupStrategy createStrategy,
                                            DeclaredQueryLookupStrategy lookupStrategy) {
            super(em, extractor);
            this.createStrategy = createStrategy;
            this.lookupStrategy = lookupStrategy;
        }

        /*
         * (non-Javadoc)
         *
         * @see
         * org.springframework.data.jpa.repository.query.JpaQueryLookupStrategy.AbstractQueryLookupStrategy#resolveQuery
         * (org.springframework .data.jpa.repository.query.JpaQueryMethod, javax.persistence.EntityManager,
         * org.springframework.data.repository.core.NamedQueries)
         */
        @Override
        protected RepositoryQuery resolveQuery(JpaQueryMethod method,
                                               EntityManager em,
                                               NamedQueries namedQueries,
                                               boolean needAcl) {

            try {
                return lookupStrategy.resolveQuery(method, em, namedQueries, needAcl);
            } catch (IllegalStateException e) {
                return createStrategy.resolveQuery(method, em, namedQueries, needAcl);
            }
        }
    }

    /**
     * Creates a {@link QueryLookupStrategy} for the given {@link EntityManager} and {@link Key}.
     *
     * @param em                        must not be {@literal null}.
     * @param key                       may be {@literal null}.
     * @param extractor                 must not be {@literal null}.
     * @param evaluationContextProvider must not be {@literal null}.
     * @return the query lookup strategy
     */
    public static QueryLookupStrategy create(EntityManager em, Key key, QueryExtractor extractor,
                                             EvaluationContextProvider evaluationContextProvider,
                                             AclSpecification aclSpecification) {

        Assert.notNull(em, "EntityManager must not be null!");
        Assert.notNull(extractor, "QueryExtractor must not be null!");
        Assert.notNull(evaluationContextProvider, "EvaluationContextProvider must not be null!");

        switch (key != null ? key : Key.CREATE_IF_NOT_FOUND) {
            case CREATE:
                return new CreateQueryLookupStrategy(em, extractor, aclSpecification);

            case USE_DECLARED_QUERY:
                return new DeclaredQueryLookupStrategy(em, extractor, evaluationContextProvider);

            case CREATE_IF_NOT_FOUND:
                return new CreateIfNotFoundQueryLookupStrategy(em, extractor,
                        new CreateQueryLookupStrategy(em, extractor, aclSpecification),
                        new DeclaredQueryLookupStrategy(em, extractor, evaluationContextProvider));

            default:
                throw new IllegalArgumentException(String.format("Unsupported query lookup strategy %s!", key));
        }
    }
}
