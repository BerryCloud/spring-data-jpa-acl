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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor;

import com.berrycloud.acl.domain.AclRole;
import com.berrycloud.acl.domain.AclUser;
import com.berrycloud.acl.domain.SimpleAclRole;
import com.berrycloud.acl.domain.SimpleAclUser;

/**
 * PostProcessor for adding missing {@link AclUser} and {@link AclRole} domain classes to the Persistence MAnager.
 *
 * @author István Rátkai (Selindek)
 */
public class AclPersistenceUnitPostProcessor implements PersistenceUnitPostProcessor {

    private static Logger LOG = LoggerFactory.getLogger(AclPersistenceUnitPostProcessor.class);

    @Override
    public void postProcessPersistenceUnitInfo(MutablePersistenceUnitInfo pui) {
        List<Class<?>> entityClasses = createClasses(pui);

        if (missClass(entityClasses, AclUser.class)) {
            pui.addManagedClassName(SimpleAclUser.class.getName());
            LOG.info("{} was added to managed entities.", SimpleAclUser.class);
        }

        if (missClass(entityClasses, AclRole.class)) {
            pui.addManagedClassName(SimpleAclRole.class.getName());
            LOG.info("{} was added to managed entities.", SimpleAclRole.class);
        }
    }

    private static boolean missClass(List<Class<?>> entityClasses, Class<?> checkClass) {
        for (Class<?> entityClass : entityClasses) {
            if (checkClass.isAssignableFrom(entityClass)) {
                return false;
            }
        }
        return true;
    }

    private static List<Class<?>> createClasses(MutablePersistenceUnitInfo pui) {
        List<Class<?>> list = new ArrayList<>();
        for (String entityName : pui.getManagedClassNames()) {
            try {
                list.add(Class.forName(entityName));
            } catch (ClassNotFoundException e) {
                LOG.warn("Cannot create managed entity from className: {}", entityName);
            }
        }
        return list;
    }
}
