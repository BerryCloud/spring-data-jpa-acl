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
package com.berrycloud.acl.configuration.rest;

import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.rest.RepositoryRestProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.rest.webmvc.BasePathAwareHandlerMapping;
import org.springframework.data.rest.webmvc.DomainPropertyClassResolver;
import org.springframework.data.rest.webmvc.RepositoryRestHandlerMapping;
import org.springframework.data.rest.webmvc.config.RepositoryRestMvcConfiguration;
import org.springframework.data.rest.webmvc.json.JacksonMappingAwarePropertySortTranslator;
import org.springframework.data.rest.webmvc.json.MappingAwareDefaultedPageableArgumentResolver;
import org.springframework.data.rest.webmvc.json.MappingAwareDefaultedPropertyPageableArgumentResolver;
import org.springframework.data.rest.webmvc.support.DelegatingHandlerMapping;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This configuration class is activated only if spring-data-rest-webmvc is in the classpath. It overrides some of the
 * original beans of the the data-rest module to make it compatible with the ACL package.
 *
 * @author Will Faithfull
 * @author István Rátkai (Selindek)
 */
@Configuration
@ConditionalOnClass(value = RepositoryRestMvcConfiguration.class)
@EnableConfigurationProperties(RepositoryRestProperties.class)
@Import({ SpringBootRepositoryRestConfigurer.class })
public class AclRepositoryRestConfiguration extends RepositoryRestMvcConfiguration implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * The PageableResolver what is constructed in the super method is not handling the domain-properties, so we have to
     * change it to a proper one here.
     */
    @Override
    protected List<HandlerMethodArgumentResolver> defaultMethodArgumentResolvers() {
        List<HandlerMethodArgumentResolver> originalList = super.defaultMethodArgumentResolvers();
        List<HandlerMethodArgumentResolver> newList = new ArrayList<>();

        JacksonMappingAwarePropertySortTranslator sortTranslator = new JacksonMappingAwarePropertySortTranslator(
                objectMapper(), repositories(),
                DomainPropertyClassResolver.of(repositories(), resourceMappings(), baseUri()), persistentEntities(),
                associationLinks());

        HandlerMethodArgumentResolver defaultedPageableResolver = new MappingAwareDefaultedPropertyPageableArgumentResolver(
                sortTranslator, pageableResolver());

        for (HandlerMethodArgumentResolver element : originalList) {
            if (element instanceof MappingAwareDefaultedPageableArgumentResolver) {
                newList.add(defaultedPageableResolver);
            } else {
                newList.add(element);
            }
        }

        return newList;
    }

    /**
     * By overriding this method we can exclude the original
     * {@link org.springframework.data.rest.webmvc.RepositoryPropertyReferenceController} from the handler-mapping.
     */
    @Override
    public DelegatingHandlerMapping restHandlerMapping() {
        Map<String, CorsConfiguration> corsConfigurations = config().getCorsRegistry().getCorsConfigurations();

        RepositoryRestHandlerMapping repositoryMapping = new RepositoryRestHandlerMapping(resourceMappings(), config(),
                repositories()) {
            @Override
            protected boolean isHandler(Class<?> beanType) {
                return super.isHandler(beanType)
                        && !beanType.getSimpleName().equals("RepositoryPropertyReferenceController")
                        && !beanType.getSimpleName().equals("RepositoryEntityController");
            }
        };

        repositoryMapping.setJpaHelper(jpaHelper());
        repositoryMapping.setApplicationContext(applicationContext);
        repositoryMapping.setCorsConfigurations(corsConfigurations);
        repositoryMapping.afterPropertiesSet();

        BasePathAwareHandlerMapping basePathMapping = new BasePathAwareHandlerMapping(config());
        basePathMapping.setApplicationContext(applicationContext);
        basePathMapping.setCorsConfigurations(corsConfigurations);
        basePathMapping.afterPropertiesSet();

        List<HandlerMapping> mappings = new ArrayList<>();
        mappings.add(basePathMapping);
        mappings.add(repositoryMapping);

        return new DelegatingHandlerMapping(mappings);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
