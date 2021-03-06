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

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import com.berrycloud.acl.search.Search;

public interface AclSpecification extends Specification<Object> {

  Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, String permission);

  /**
   * Checks whether this new entity can be created based on the Acl rules. (Does the current user have create permission
   * on this entity type or not.)
   * 
   * @param newEntity
   * @return
   */
  boolean canBeCreated(Object newEntity);

  void applySearch(CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder, From<?, ?> next, Search sort);

}
