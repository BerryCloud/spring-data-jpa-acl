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
package org.springframework.data.rest.webmvc;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.core.mapping.PropertyAwareResourceMapping;
import org.springframework.data.rest.core.mapping.ResourceMappings;
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.data.rest.webmvc.util.UriUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Resolves a domain class from a web request. Domain class resolution is only available for {@link NativeWebRequest web
 * requests} related to mapped and exported {@link Repositories}.
 *
 * @author Mark Paluch
 * @author Oliver Gierke
 * @author István Rátkai (Selindek)
 * @since 2.6
 */
public class DomainPropertyClassResolver {

    private final Repositories repositories;
    private final ResourceMappings mappings;
    private final BaseUri baseUri;

    public DomainPropertyClassResolver(Repositories repositories, ResourceMappings mappings, BaseUri baseUri) {
        Assert.notNull(repositories, "repositories cannot be null");
        Assert.notNull(mappings, "mappings cannot be null");
        Assert.notNull(baseUri, "baseUri cannot be null");

        this.repositories = repositories;
        this.mappings = mappings;
        this.baseUri = baseUri;
    }

    public static DomainPropertyClassResolver of(Repositories repositories, ResourceMappings mappings,
            BaseUri baseUri) {
        return new DomainPropertyClassResolver(repositories, mappings, baseUri);
    }

    /**
     * Resolves a domain class that is associated with the {@link NativeWebRequest} If this request was associated with
     * a property of an entity the the class of the property will be resolved.
     *
     * @param method
     *            must not be {@literal null}.
     * @param webRequest
     *            must not be {@literal null}.
     * @return domain type that is associated with this request or {@literal null} if no domain class can be resolved.
     */
    public Class<?> resolve(Method method, NativeWebRequest webRequest) {

        Assert.notNull(method, "Method must not be null!");
        Assert.notNull(webRequest, "NativeWebRequest must not be null!");

        String lookupPath = baseUri.getRepositoryLookupPath(webRequest);
        String repositoryKey = UriUtils.findMappingVariable("repository", method, lookupPath);

        if (!StringUtils.hasText(repositoryKey)) {
            List<String> pathSegments = UriUtils.getPathSegments(method);

            if (!pathSegments.isEmpty()) {
                repositoryKey = pathSegments.get(0);
            }
        }

        if (!StringUtils.hasText(repositoryKey)) {
            return null;
        }

        String propertyKey = UriUtils.findMappingVariable("property", method, lookupPath);

        if (!StringUtils.hasText(propertyKey)) {

            List<String> pathSegments = UriUtils.getPathSegments(method);

            if (pathSegments.size() >= 2) {
                propertyKey = pathSegments.get(2);
            }
        }

        for (Class<?> domainType : repositories) {

            ResourceMetadata mapping = mappings.getMetadataFor(domainType);

            if (mapping.getPath().matches(repositoryKey) && mapping.isExported()) {
                if (propertyKey != null) {
                    PropertyAwareResourceMapping propertyMapping = mapping.getProperty(propertyKey);
                    if (propertyMapping != null) {
                        return propertyMapping.getProperty().getActualType();
                    }
                }
                return domainType;
            }
        }

        return null;
    }
}
