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
package com.berrycloud.acl.repository;

import static javax.persistence.metamodel.Attribute.PersistentAttributeType.ELEMENT_COLLECTION;
import static javax.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_MANY;
import static javax.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_ONE;
import static javax.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_MANY;
import static javax.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_ONE;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Fetch;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.Attribute.PersistentAttributeType;
import javax.persistence.metamodel.Bindable;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.PluralAttribute;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.util.Assert;

/**
 * Mirror class of {@link QueryUtils}.
 *
 * @author Oliver Gierke
 * @author Kevin Raymond
 * @author Thomas Darimont
 * @author Komi Innocent
 * @author Christoph Strobl
 * @author István Rátkai (Selindek)
 */
class AclQueryUtils {

    private static final String IDENTIFIER = "[\\p{Lu}\\P{InBASIC_LATIN}\\p{Alnum}._$]+";
    private static final String IDENTIFIER_GROUP = String.format("(%s)", IDENTIFIER);

    private static final Map<PersistentAttributeType, Class<? extends Annotation>> ASSOCIATION_TYPES;

    static {
        StringBuilder builder = new StringBuilder();
        builder.append("(?<=from)"); // from as starting delimiter
        builder.append("(?:\\s)+"); // at least one space separating
        builder.append(IDENTIFIER_GROUP); // Entity name, can be qualified (any
        builder.append("(?:\\sas)*"); // exclude possible "as" keyword
        builder.append("(?:\\s)+"); // at least one space separating
        builder.append("(?!(?:where))(\\w*)"); // the actual alias

        builder = new StringBuilder();
        builder.append("(select\\s+((distinct )?(.+?)?)\\s+)?(from\\s+");
        builder.append(IDENTIFIER);
        builder.append("(?:\\s+as)?\\s+)");
        builder.append(IDENTIFIER_GROUP);
        builder.append("(.*)");

        Map<PersistentAttributeType, Class<? extends Annotation>> persistentAttributeTypes = new HashMap<>();
        persistentAttributeTypes.put(ONE_TO_ONE, OneToOne.class);
        persistentAttributeTypes.put(ONE_TO_MANY, null);
        persistentAttributeTypes.put(MANY_TO_ONE, ManyToOne.class);
        persistentAttributeTypes.put(MANY_TO_MANY, null);
        persistentAttributeTypes.put(ELEMENT_COLLECTION, null);

        ASSOCIATION_TYPES = Collections.unmodifiableMap(persistentAttributeTypes);

        builder = new StringBuilder();
        builder.append("select");
        builder.append("\\s+"); // at least one space separating
        builder.append("(.*\\s+)?"); // anything in between (e.g. distinct) at least one space separating
        builder.append("new");
        builder.append("\\s+"); // at least one space separating
        builder.append(IDENTIFIER);
        builder.append("\\s*"); // zero to unlimited space separating
        builder.append("\\(");
        builder.append(".*");
        builder.append("\\)");

        builder = new StringBuilder();
        builder.append("\\s+"); // at least one space
        builder.append("\\w+\\([0-9a-zA-z\\._,\\s']+\\)"); // any function call including parameters within the brackets
        builder.append("\\s+[as|AS]+\\s+(([\\w\\.]+))"); // the potential alias
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private AclQueryUtils() {

    }

    /**
     * Turns the given {@link Sort} into {@link javax.persistence.criteria.Order}s.
     *
     * @param sort
     *            the {@link Sort} instance to be transformed into JPA {@link javax.persistence.criteria.Order}s.
     * @param root
     *            must not be {@literal null}.
     * @param cb
     *            must not be {@literal null}.
     * @return
     */
    static List<javax.persistence.criteria.Order> toOrders(Sort sort, From<?, ?> root, CriteriaBuilder cb) {
        List<javax.persistence.criteria.Order> orders = new ArrayList<>();

        if (sort == null) {
            return orders;
        }

        Assert.notNull(root, "Root must not be null!");
        Assert.notNull(cb, "CriteriaBuilder must not be null!");

        for (org.springframework.data.domain.Sort.Order order : sort) {
            orders.add(toJpaOrder(order, root, cb));
        }

        return orders;
    }

    /**
     * Creates a criteria API {@link javax.persistence.criteria.Order} from the given {@link Order}.
     *
     * @param order
     *            the order to transform into a JPA {@link javax.persistence.criteria.Order}
     * @param root
     *            the {@link Root} the {@link Order} expression is based on
     * @param cb
     *            the {@link CriteriaBuilder} to build the {@link javax.persistence.criteria.Order} with
     * @return
     */
    @SuppressWarnings("unchecked")
    private static javax.persistence.criteria.Order toJpaOrder(Order order, From<?, ?> root, CriteriaBuilder cb) {
        PropertyPath property = PropertyPath.from(order.getProperty(), root.getJavaType());
        Expression<?> expression = toExpressionRecursively(root, property);

        if (order.isIgnoreCase() && String.class.equals(expression.getJavaType())) {
            Expression<String> lower = cb.lower((Expression<String>) expression);
            return order.isAscending() ? cb.asc(lower) : cb.desc(lower);
        } else {
            return order.isAscending() ? cb.asc(expression) : cb.desc(expression);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Expression<T> toExpressionRecursively(From<?, ?> from, PropertyPath property) {
        Bindable<?> propertyPathModel = null;
        Bindable<?> model = from.getModel();
        String segment = property.getSegment();

        if (model instanceof ManagedType) {

            /*
             * Required to keep support for EclipseLink 2.4.x. TODO: Remove once we drop that (probably Dijkstra M1)
             * See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=413892
             */
            propertyPathModel = (Bindable<?>) ((ManagedType<?>) model).getAttribute(segment);
        } else {
            propertyPathModel = from.get(segment).getModel();
        }

        if (requiresJoin(propertyPathModel, model instanceof PluralAttribute) && !isAlreadyFetched(from, segment)) {
            Join<?, ?> join = getOrCreateJoin(from, segment);
            return (Expression<T>) (property.hasNext() ? toExpressionRecursively(join, property.next()) : join);
        } else {
            Path<Object> path = from.get(segment);
            return (Expression<T>) (property.hasNext() ? toExpressionRecursively(path, property.next()) : path);
        }
    }

    /**
     * Returns whether the given {@code propertyPathModel} requires the creation of a join. This is the case if we find
     * a non-optional association.
     *
     * @param propertyPathModel
     *            must not be {@literal null}.
     * @param forPluralAttribute
     * @return
     */
    private static boolean requiresJoin(Bindable<?> propertyPathModel, boolean forPluralAttribute) {
        if (propertyPathModel == null && forPluralAttribute) {
            return true;
        }

        if (!(propertyPathModel instanceof Attribute)) {
            return false;
        }

        Attribute<?, ?> attribute = (Attribute<?, ?>) propertyPathModel;

        if (!ASSOCIATION_TYPES.containsKey(attribute.getPersistentAttributeType())) {
            return false;
        }

        Class<? extends Annotation> associationAnnotation = ASSOCIATION_TYPES
                .get(attribute.getPersistentAttributeType());

        if (associationAnnotation == null) {
            return true;
        }

        Member member = attribute.getJavaMember();

        if (!(member instanceof AnnotatedElement)) {
            return true;
        }

        Annotation annotation = AnnotationUtils.getAnnotation((AnnotatedElement) member, associationAnnotation);
        return annotation == null ? true : (Boolean) AnnotationUtils.getValue(annotation, "optional");
    }

    static Expression<Object> toExpressionRecursively(Path<Object> path, PropertyPath property) {
        Path<Object> result = path.get(property.getSegment());
        return property.hasNext() ? toExpressionRecursively(result, property.next()) : result;
    }

    /**
     * Returns an existing join for the given attribute if one already exists or creates a new one if not.
     *
     * @param from
     *            the {@link From} to get the current joins from.
     * @param attribute
     *            the {@link Attribute} to look for in the current joins.
     * @return will never be {@literal null}.
     */
    private static Join<?, ?> getOrCreateJoin(From<?, ?> from, String attribute) {
        for (Join<?, ?> join : from.getJoins()) {
            boolean sameName = join.getAttribute().getName().equals(attribute);
            if (sameName && join.getJoinType().equals(JoinType.LEFT)) {
                return join;
            }
        }

        return from.join(attribute, JoinType.LEFT);
    }

    /**
     * Return whether the given {@link From} contains a fetch declaration for the attribute with the given name.
     *
     * @param from
     *            the {@link From} to check for fetches.
     * @param attribute
     *            the attribute name to check.
     * @return
     */
    private static boolean isAlreadyFetched(From<?, ?> from, String attribute) {
        for (Fetch<?, ?> f : from.getFetches()) {
            boolean sameName = f.getAttribute().getName().equals(attribute);
            if (sameName && f.getJoinType().equals(JoinType.LEFT)) {
                return true;
            }
        }

        return false;
    }
}
