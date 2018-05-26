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

import com.berrycloud.acl.annotation.AclCreatePermission;
import com.berrycloud.acl.annotation.AclCreatePermissions;
import com.berrycloud.acl.annotation.AclOwner;
import com.berrycloud.acl.annotation.AclParent;
import com.berrycloud.acl.annotation.AclRoleCondition;
import com.berrycloud.acl.annotation.AclRoleConditions;
import com.berrycloud.acl.annotation.AclRolePermission;
import com.berrycloud.acl.annotation.AclRolePermissions;
import com.berrycloud.acl.annotation.AclRoleProvider;
import com.berrycloud.acl.annotation.AclSelf;
import com.berrycloud.acl.data.AclEntityMetaData;
import com.berrycloud.acl.data.AclMetaData;
import com.berrycloud.acl.data.CreatePermissionData;
import com.berrycloud.acl.data.OwnerData;
import com.berrycloud.acl.data.ParentData;
import com.berrycloud.acl.data.PermissionData;
import com.berrycloud.acl.data.PermissionLinkData;
import com.berrycloud.acl.data.RolePermissionData;
import com.berrycloud.acl.domain.AclRole;
import com.berrycloud.acl.domain.AclUser;
import com.berrycloud.acl.domain.PermissionLink;
import com.berrycloud.acl.domain.SimpleAclRole;
import com.berrycloud.acl.domain.SimpleAclUser;
import com.berrycloud.acl.repository.NoAcl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.OneToMany;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.IdentifiableType;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.SingularAttribute;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.berrycloud.acl.AclConstants.ALL_PERMISSION;
import static com.berrycloud.acl.AclConstants.PERMISSION_PREFIX_DELIMITER;
import static com.berrycloud.acl.AclConstants.ROLE_ADMIN;
import static com.berrycloud.acl.AclConstants.ROLE_USER;

/**
 * Default implementation of the {@link AclLogic}
 *
 * @author István Rátkai (Selindek)
 */
public class AclLogicImpl implements AclLogic {

    private static Logger LOG = LoggerFactory.getLogger(AclLogicImpl.class);

    private final EntityManager em;
    private String[] defaultSelfPermissions;

    private Class<AclUser> aclUserType;
    private JpaEntityInformation<AclUser, ?> userInformation;
    private Class<AclRole> aclRoleType;
    private Set<Class<?>> javaTypes;

    public AclLogicImpl(EntityManager em,
                        @Value("${spring.data.jpa.acl.self-permissions:" + ALL_PERMISSION + "}") String[] defaultSelfPermissions) {
        this.em = em;
        this.defaultSelfPermissions = defaultSelfPermissions;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public AclMetaData createAclMetaData() {
        createJavaTypeSet();

        aclUserType = (Class<AclUser>) searchEntityType(javaTypes, AclUser.class);
        userInformation = JpaEntityInformationSupport.getEntityInformation(aclUserType, em);
        aclRoleType = (Class<AclRole>) searchEntityType(javaTypes, AclRole.class);

        addDefaultUsersIfNeeded();

        return new AclMetaData(createMetaDataMap(), new PermissionData(defaultSelfPermissions));
    }

    private void createJavaTypeSet() {
        javaTypes = new HashSet<>();
        for (ManagedType<?> mt : em.getMetamodel().getManagedTypes()) {
            Class<?> type = mt.getJavaType();
            if (type.isAnnotationPresent(Entity.class) && !Modifier.isAbstract(type.getModifiers())) {
                javaTypes.add(type);
            }
        }
    }

    @Override
    public boolean isManagedType(Class<?> javaType) {
        return javaTypes != null && javaTypes.contains(javaType);
    }

    private Class<?> searchEntityType(Set<Class<?>> javaTypes, Class<?> checkType) {
        Class<?> foundType = null;
        for (Class<?> type : javaTypes) {
            if (checkType.isAssignableFrom(type)) {
                if (foundType != null) {
                    throw new IllegalStateException("Multiple managed entity of class " + checkType.getSimpleName()
                            + " found: " + foundType.getName() + " and " + type.getName());
                }
                foundType = type;
                LOG.debug(checkType.getSimpleName() + " found: " + foundType.getName());
            }
        }
        return foundType;
    }

    private Map<Class<?>, AclEntityMetaData> createMetaDataMap() {
        Map<Class<?>, AclEntityMetaData> metaDataMap = new HashMap<>();
        for (Class<?> javaType : javaTypes) {
            LOG.debug("Create metadata for {}", javaType);
            metaDataMap.put(javaType, createAclEntityMetaData(javaType));
        }

        return metaDataMap;
    }

    @SuppressWarnings("unchecked")
    private AclEntityMetaData createAclEntityMetaData(Class<?> javaType) {
        AclEntityMetaData metaData = new AclEntityMetaData();
        ManagedType<?> type = em.getMetamodel().managedType(javaType);

        if (!(type instanceof IdentifiableType)) {
            throw new IllegalArgumentException(javaType + " does not contain an id attribute!");
        }

        IdentifiableType<?> identifiableType = (IdentifiableType<?>) type;

        if (!identifiableType.hasSingleIdAttribute()) {
            throw new IllegalArgumentException(javaType + " has a non-single id attribute!");
        }
        metaData.setIdAttribute((SingularAttribute<? super Object, ?>) identifiableType
                .getId(identifiableType.getIdType().getJavaType()));

        try {
            // We use BeanWrapper for checking annotations on fields AND getters and setters too
            BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(javaType.getDeclaredConstructor().newInstance());
            for (final PropertyDescriptor propertyDescriptor : beanWrapper.getPropertyDescriptors()) {
                final String propertyName = propertyDescriptor.getName();
                final TypeDescriptor typeDescriptor = beanWrapper.getPropertyTypeDescriptor(propertyName);
                checkAclOwner(metaData, javaType, propertyName, typeDescriptor);
                checkAclParent(metaData, javaType, propertyName, typeDescriptor);
                checkAclPermissionLinks(metaData, propertyName, typeDescriptor);
            }
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            LOG.error("Cannot instantiate {} ", javaType);
        }
        checkSelfPermissions(javaType);
        checkAclCreatePermission(metaData, javaType);
        checkAclRolePermission(metaData, javaType);
        checkAclRoleCondition(metaData, javaType);
        // call this one last. It overrides the role annotations
        checkNoAcl(metaData, javaType);
        return metaData;
    }

    private void checkAclCreatePermission(AclEntityMetaData metaData, Class<?> javaType) {
        Set<AclCreatePermission> createPermissions = AnnotationUtils.getDeclaredRepeatableAnnotations(javaType,
                AclCreatePermission.class, AclCreatePermissions.class);
        for (AclCreatePermission createPermission : createPermissions) {
            metaData.getCreatePermissionList().add(new CreatePermissionData(createPermission.roles()));
        }
    }

    private void checkAclRolePermission(AclEntityMetaData metaData, Class<?> javaType) {
        Set<AclRolePermission> rolePermissions = AnnotationUtils.getDeclaredRepeatableAnnotations(javaType,
                AclRolePermission.class, AclRolePermissions.class);

        for (AclRolePermission rolePermission : rolePermissions) {
            metaData.getRolePermissionList()
                    .add(new RolePermissionData(rolePermission.roles(), rolePermission.value()));
        }

        if (metaData.getRolePermissionList().isEmpty()) {
            // Add default behaviour - users with ROLE_ADMIN role automatically gain all permissions
            metaData.getRolePermissionList()
                    .add(new RolePermissionData(new String[]{ROLE_ADMIN}, new String[]{ALL_PERMISSION}));
        }
    }

    private void checkAclRoleCondition(AclEntityMetaData metaData, Class<?> javaType) {
        Set<AclRoleCondition> roleConditions = AnnotationUtils.getDeclaredRepeatableAnnotations(javaType,
                AclRoleCondition.class, AclRoleConditions.class);

        for (AclRoleCondition roleCondition : roleConditions) {
            metaData.getRoleConditionList().add(new RolePermissionData(roleCondition.roles(), roleCondition.value()));
        }

        if (metaData.getRoleConditionList().isEmpty()) {
            // Add default behaviour - users with ANY roles could gain any permissions
            metaData.getRoleConditionList()
                    .add(new RolePermissionData(new String[]{}, new String[]{ALL_PERMISSION}));
        }
    }

    private void checkNoAcl(AclEntityMetaData metaData, Class<?> javaType) {
        NoAcl noAcl = AnnotationUtils.findAnnotation(javaType, NoAcl.class);
        if (noAcl != null) {
            metaData.getRoleConditionList().clear();
            metaData.getRolePermissionList().clear();
            // Turn off ACL - users with ANY roles automatically gain all permissions
            metaData.getRolePermissionList()
                    .add(new RolePermissionData(new String[]{}, new String[]{ALL_PERMISSION}));
        }
    }

    private void checkSelfPermissions(Class<?> javaType) {
        AclSelf aclSelf = AnnotationUtils.findAnnotation(javaType, AclSelf.class);
        if (aclSelf != null) {
            if (AclUser.class.isAssignableFrom(javaType)) {
                defaultSelfPermissions = aclSelf.value();
                LOG.trace("@AclSelf annotation was processed. Self permissions are {}",
                        Arrays.asList(defaultSelfPermissions));
            } else {
                LOG.warn("Non-AclUser type '{}' is annotated with @AclSelf ... ignored", javaType);
            }
        }
    }

    private void checkAclPermissionLinks(AclEntityMetaData metaData,
                                         String propertyName,
                                         TypeDescriptor typeDescriptor) {
        final OneToMany oneToMany = typeDescriptor.getAnnotation(OneToMany.class);
        if (oneToMany != null && (typeDescriptor.isCollection() || typeDescriptor.isArray())
                && PermissionLink.class.isAssignableFrom(typeDescriptor.getElementTypeDescriptor().getType())) {
            if ("target".equals(oneToMany.mappedBy())) {
                LOG.trace("PermissionLink owner: {}", propertyName);
                metaData.getPermissionLinkList().add(new PermissionLinkData(propertyName, "permission"));
            }
        }
    }

    private void checkAclOwner(AclEntityMetaData metaData, Class<?> javaType, final String propertyName,
                               final TypeDescriptor typeDescriptor) {
        final AclOwner aclOwner = typeDescriptor.getAnnotation(AclOwner.class);
        if (aclOwner != null) {
            if (AclUser.class.isAssignableFrom(typeDescriptor.getObjectType())) {
                // The owner is an AclUser
                metaData.getOwnerDataList()
                        .add(new OwnerData(propertyName, typeDescriptor.getObjectType(), false, aclOwner.value()));
            } else if ((typeDescriptor.isArray() || typeDescriptor.isCollection())
                    && typeDescriptor.getElementTypeDescriptor() != null
                    && AclUser.class.isAssignableFrom(typeDescriptor.getElementTypeDescriptor().getObjectType())) {
                // The owner is an AclUser collection
                metaData.getOwnerDataList().add(new OwnerData(propertyName,
                        typeDescriptor.getElementTypeDescriptor().getObjectType(), true, aclOwner.value()));
            } else if (isManagedType(typeDescriptor.getObjectType())) {
                // The owner is NOT an AclUser, but a managed type. We treat it as a group. (It could have AclUser
                // properties)
                metaData.getOwnerGroupDataList()
                        .add(new OwnerData(propertyName, typeDescriptor.getObjectType(), false, aclOwner.value()));
            } else if ((typeDescriptor.isArray() || typeDescriptor.isCollection())
                    && typeDescriptor.getElementTypeDescriptor() != null
                    && isManagedType(typeDescriptor.getElementTypeDescriptor().getObjectType())) {
                // The owner is NOT an AclUser, but a collection of a managed type. We treat it as a collection of
                // groups.
                metaData.getOwnerGroupDataList().add(new OwnerData(propertyName,
                        typeDescriptor.getElementTypeDescriptor().getObjectType(), true, aclOwner.value()));
            } else {
                LOG.warn("Non-managed entity property '{}.{} is annotated with @AclOwner ... ignored", javaType,
                        propertyName);
            }
        }
    }

    private void checkAclParent(AclEntityMetaData metaData, Class<?> javaType, final String propertyName,
                                final TypeDescriptor typeDescriptor) {
        final AclParent aclParent = typeDescriptor.getAnnotation(AclParent.class);
        if (aclParent != null) {
            if (isManagedType(typeDescriptor.getObjectType())
                    || ((typeDescriptor.isArray() || typeDescriptor.isCollection())
                    && typeDescriptor.getElementTypeDescriptor() != null
                    && isManagedType(typeDescriptor.getElementTypeDescriptor().getObjectType()))) {
                if (aclParent.prefix().indexOf(PERMISSION_PREFIX_DELIMITER) != -1) {
                    LOG.warn("@AclParent's prefix property contains illegal character at '{}.{}' ... ignored", javaType,
                            propertyName);
                } else {
                    metaData.getParentDataList()
                            .add(new ParentData(propertyName, aclParent.prefix(), aclParent.value()));
                }
            } else {
                LOG.warn("Non-managed entity property '{}.{}' is annotated by @AclParent ... ignored", javaType,
                        propertyName);
            }
        }
    }

    @Override
    public Set<AclRole> getAllRoles(AclUser aclUser) {
        Set<AclRole> roleSet = new HashSet<>(getRoles(aclUser));

        BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(aclUser);
        for (final PropertyDescriptor propertyDescriptor : beanWrapper.getPropertyDescriptors()) {
            final String propertyName = propertyDescriptor.getName();
            final TypeDescriptor typeDescriptor = beanWrapper.getPropertyTypeDescriptor(propertyName);
            AclRoleProvider roleProvider = typeDescriptor.getAnnotation(AclRoleProvider.class);
            if (roleProvider != null) {
                LOG.trace("Found AclRoleProvider: {}.{}", aclUser.getClass(), propertyName);
                @SuppressWarnings("unchecked")
                Collection<Object> values = beanWrapper.convertIfNecessary(beanWrapper.getPropertyValue(propertyName),
                        Collection.class);
                if (values == null) {
                    continue;
                }
                for (Object value : values) {
                    LOG.trace("Collecting roles from {}", value);
                    roleSet.addAll(getRoles(value));
                }
            }
        }

        return roleSet;
    }

    private Collection<AclRole> getRoles(Object entity) {
        Collection<AclRole> roleSet = new ArrayList<>();
        BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        for (final PropertyDescriptor propertyDescriptor : beanWrapper.getPropertyDescriptors()) {
            final String propertyName = propertyDescriptor.getName();
            final TypeDescriptor typeDescriptor = beanWrapper.getPropertyTypeDescriptor(propertyName);
            final TypeDescriptor elementTypeDescriptor = typeDescriptor.getElementTypeDescriptor();
            if (aclRoleType == typeDescriptor.getType()
                    || (elementTypeDescriptor != null && aclRoleType == elementTypeDescriptor.getType())) {
                @SuppressWarnings("unchecked")
                Collection<AclRole> roles = beanWrapper.convertIfNecessary(beanWrapper.getPropertyValue(propertyName),
                        Collection.class);
                if (roles != null) {
                    LOG.trace("Add roles from {}.{} : {}", entity.getClass(), propertyName, roles);
                    roleSet.addAll(roles);
                }
            }
        }

        return roleSet;
    }

    /**
     * Load AclUser by username without any permission checking. This method is for internal use only
     */
    @Override
    @Transactional(readOnly = true)
    public AclUser loadUserByUsername(String username) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<AclUser> query = cb.createQuery(aclUserType);
        Root<AclUser> root = query.from(aclUserType);
        query.select(root).where(cb.equal(root.get("username"), username));

        AclUser aclUser = em.createQuery(query).getSingleResult();

        if (aclUser == null) {
            throw (new UsernameNotFoundException("User with username'" + username + "' cannot be found."));
        }

        return aclUser;
    }

    @Override
    public Serializable getUserId(AclUser user) {
        return userInformation.getId(user);
    }

    private void addDefaultUsersIfNeeded() {
        // Check if we use default Acl types (Probably simple test application)
        if (SimpleAclRole.class.equals(aclRoleType) && SimpleAclUser.class.equals(aclUserType)) {
            SimpleAclRole adminRole = getOrCreateRoleByName(ROLE_ADMIN);
            SimpleAclRole userRole = getOrCreateRoleByName(ROLE_USER);

            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<AclUser> root = query.from(aclUserType);
            query.select(cb.count(root));

            if (em.createQuery(query).getSingleResult() == 0) {
                // Add default users if there are no users
                SimpleAclUser adminUser = new SimpleAclUser("admin", "password");
                SimpleAclUser userUser = new SimpleAclUser("user", "password");

                adminUser.setAclRoles(new HashSet<>(Arrays.asList(adminRole, userRole)));
                userUser.setAclRoles(new HashSet<>(Collections.singletonList(userRole)));

                em.persist(adminUser);
                em.persist(userUser);
            }
        }
    }

    private SimpleAclRole getOrCreateRoleByName(String roleName) {
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<SimpleAclRole> roleQuery = cb.createQuery(SimpleAclRole.class);
            Root<AclRole> roleRoot = roleQuery.from(aclRoleType);
            roleQuery.where(cb.equal(roleRoot.get("roleName"), roleName));
            return em.createQuery(roleQuery).getSingleResult();
        } catch (Exception ex) {
            LOG.trace("Cannot load role for '{}', creating default one...", roleName, ex);
            SimpleAclRole role = new SimpleAclRole(roleName);
            em.persist(role);
            return role;
        }
    }
}
