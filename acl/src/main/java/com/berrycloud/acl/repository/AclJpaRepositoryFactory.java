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

import com.berrycloud.acl.AclSpecification;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.jpa.provider.PersistenceProvider;
import org.springframework.data.jpa.provider.QueryExtractor;
import org.springframework.data.jpa.repository.query.AclJpaQueryLookupStrategy;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.EvaluationContextProvider;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;

import javax.persistence.EntityManager;

/**
 * JPA ACL repository factory.
 *
 * @author István Rátkai (Selindek)
 */
public class AclJpaRepositoryFactory extends JpaRepositoryFactory {

    private AclSpecification aclSpecification;
    private EntityManager entityManager;
    private final QueryExtractor extractor;

    public AclJpaRepositoryFactory(EntityManager entityManager, AclSpecification aclSpecification) {
        super(entityManager);
        this.aclSpecification = aclSpecification;
        this.entityManager = entityManager;
        this.extractor = PersistenceProvider.fromEntityManager(entityManager);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return SimpleAclJpaRepository.class;
    }

    protected boolean isAclRepository(RepositoryMetadata metadata) {
        NoAcl noAcl = AnnotationUtils.findAnnotation(metadata.getRepositoryInterface(), NoAcl.class);
        return noAcl == null;
    }

    @Override
    protected SimpleJpaRepository<?, ?> getTargetRepository(RepositoryInformation information,
                                                            EntityManager entityManager) {
        SimpleJpaRepository<?, ?> repository = super.getTargetRepository(information, entityManager);
        ((SimpleAclJpaRepository<?, ?>) repository)
                .setAclSpecification(isAclRepository(information) ? aclSpecification : null);
        return repository;
    }

    @Override
    protected QueryLookupStrategy getQueryLookupStrategy(Key key, EvaluationContextProvider evaluationContextProvider) {
        return AclJpaQueryLookupStrategy.create(entityManager, key, extractor, evaluationContextProvider,
                aclSpecification);
    }
}
