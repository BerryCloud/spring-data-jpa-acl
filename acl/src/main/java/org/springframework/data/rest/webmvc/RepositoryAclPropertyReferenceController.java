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

import static org.springframework.data.rest.webmvc.ControllerUtils.EMPTY_RESOURCE_LIST;
import static org.springframework.data.rest.webmvc.RestMediaTypes.SPRING_DATA_COMPACT_JSON_VALUE;
import static org.springframework.data.rest.webmvc.RestMediaTypes.TEXT_URI_LIST_VALUE;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.PATCH;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.CollectionFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.IdentifierAccessor;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvoker;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.data.rest.core.event.AfterLinkDeleteEvent;
import org.springframework.data.rest.core.event.AfterLinkSaveEvent;
import org.springframework.data.rest.core.event.BeforeLinkDeleteEvent;
import org.springframework.data.rest.core.event.BeforeLinkSaveEvent;
import org.springframework.data.rest.core.mapping.PropertyAwareResourceMapping;
import org.springframework.data.rest.core.mapping.ResourceMapping;
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.data.rest.core.util.Function;
import org.springframework.data.rest.webmvc.support.BackendId;
import org.springframework.data.rest.webmvc.support.DefaultedPageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.berrycloud.acl.repository.AclJpaRepository;

/**
 * @author Jon Brisbin
 * @author Oliver Gierke
 * @author Greg Turnquist
 * @author István Rátkai (Selindek)
 */
@RepositoryRestController
@SuppressWarnings({ "unchecked" })
class RepositoryAclPropertyReferenceController extends AbstractRepositoryRestController
        implements ApplicationEventPublisherAware {

    private static final String BASE_MAPPING = "/{repository}/{id}/{property}";
    private static final Collection<HttpMethod> AUGMENTING_METHODS = Arrays.asList(HttpMethod.PATCH, HttpMethod.POST);

    private final Repositories repositories;
    private final RepositoryInvokerFactory repositoryInvokerFactory;

    private ApplicationEventPublisher publisher;

    @Autowired
    public RepositoryAclPropertyReferenceController(Repositories repositories,
            RepositoryInvokerFactory repositoryInvokerFactory, PagedResourcesAssembler<Object> assembler) {

        super(assembler);

        this.repositories = repositories;
        this.repositoryInvokerFactory = repositoryInvokerFactory;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.context.ApplicationEventPublisherAware#setApplicationEventPublisher(org.springframework.
     * context. ApplicationEventPublisher)
     */
    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }

    @RequestMapping(value = BASE_MAPPING, method = GET)
    public ResponseEntity<ResourceSupport> followPropertyReference(final RootResourceInformation repoRequest,
            @BackendId Serializable id, final @PathVariable String property,
            final PersistentEntityResourceAssembler assembler, DefaultedPageable pageable) throws Exception {

        final HttpHeaders headers = new HttpHeaders();

        Function<ReferencedProperty, ResourceSupport> handler = new Function<ReferencedProperty, ResourceSupport>() {

            @Override
            public ResourceSupport apply(ReferencedProperty prop) {

                if (null == prop.propertyValue) {
                    throw new ResourceNotFoundException();
                }

                if (prop.property.isCollectionLike()) {

                    return toResources((Iterable<?>) prop.propertyValue, assembler, prop.propertyType, null);

                } else if (prop.property.isMap()) {

                    Map<Object, Resource<?>> resources = new HashMap<Object, Resource<?>>();

                    for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) prop.propertyValue).entrySet()) {
                        resources.put(entry.getKey(), assembler.toResource(entry.getValue()));
                    }

                    return new Resource<Object>(resources);

                } else {

                    PersistentEntityResource resource = assembler.toResource(prop.propertyValue);
                    headers.set("Content-Location", resource.getId().getHref());
                    return resource;
                }
            }
        };

        ResourceSupport responseResource = doWithReferencedProperty(repoRequest, id, property, handler, HttpMethod.GET,
                pageable, null);
        return ControllerUtils.toResponseEntity(HttpStatus.OK, headers, responseResource);
    }

    @RequestMapping(value = BASE_MAPPING, method = DELETE)
    public ResponseEntity<? extends ResourceSupport> deletePropertyReference(final RootResourceInformation repoRequest,
            @BackendId Serializable id, @PathVariable String property) throws Exception {

        final AclJpaRepository<Object, Serializable> aclRepository = getAclRepository(repoRequest.getDomainType());
        // final RepositoryInvoker repoMethodInvoker = repoRequest.getInvoker();

        Function<ReferencedProperty, ResourceSupport> handler = new Function<ReferencedProperty, ResourceSupport>() {

            @Override
            public Resource<?> apply(ReferencedProperty prop) throws HttpRequestMethodNotSupportedException {

                if (null == prop.propertyValue) {
                    return null;
                }

                if (prop.property.isCollectionLike() || prop.property.isMap()) {
                    throw new HttpRequestMethodNotSupportedException("DELETE", new String[] { "DELETE" },
                            "Cannot DELETE map or collection-like properties.");
                } else {
                    prop.accessor.setProperty(prop.property, null);
                }

                publisher.publishEvent(new BeforeLinkDeleteEvent(prop.accessor.getBean(), prop.propertyValue));
                // Object result = repoMethodInvoker.invokeSave(prop.accessor.getBean());
                Object result = aclRepository.saveWithoutPermissionCheck(prop.accessor.getBean());
                publisher.publishEvent(new AfterLinkDeleteEvent(result, prop.propertyValue));

                return null;
            }
        };
        doWithReferencedProperty(repoRequest, id, property, handler, HttpMethod.DELETE, null, null);

        return ControllerUtils.toEmptyResponse(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(value = BASE_MAPPING + "/{propertyId}", method = GET)
    public ResponseEntity<ResourceSupport> followPropertyReference(final RootResourceInformation repoRequest,
            @BackendId Serializable id, @PathVariable String property, final @PathVariable String propertyId,
            final PersistentEntityResourceAssembler assembler) throws Exception {

        final HttpHeaders headers = new HttpHeaders();

        Function<ReferencedProperty, ResourceSupport> handler = new Function<ReferencedProperty, ResourceSupport>() {

            @Override
            public ResourceSupport apply(ReferencedProperty prop) {

                if (null == prop.propertyValue) {
                    throw new ResourceNotFoundException();
                }
                if (prop.property.isCollectionLike()) {
                    PersistentEntityResource resource = assembler.toResource(prop.propertyValue);
                    headers.set("Content-Location", resource.getId().getHref());
                    return resource;
                } else if (prop.property.isMap()) {
                    PersistentEntityResource resource = assembler.toResource(prop.propertyValue);
                    headers.set("Content-Location", resource.getId().getHref());
                    return resource;
                } else {
                    return new Resource<Object>(prop.propertyValue);
                }
            }
        };

        ResourceSupport responseResource = doWithReferencedProperty(repoRequest, id, property, handler, HttpMethod.GET,
                null, propertyId);
        return ControllerUtils.toResponseEntity(HttpStatus.OK, headers, responseResource);
    }

    @RequestMapping(value = BASE_MAPPING, method = GET, produces = { SPRING_DATA_COMPACT_JSON_VALUE,
            TEXT_URI_LIST_VALUE })
    public ResponseEntity<ResourceSupport> followPropertyReferenceCompact(RootResourceInformation repoRequest,
            @BackendId Serializable id, @PathVariable String property, PersistentEntityResourceAssembler assembler,
            DefaultedPageable pageable) throws Exception {

        ResponseEntity<ResourceSupport> response = followPropertyReference(repoRequest, id, property, assembler,
                pageable);

        if (response.getStatusCode() != HttpStatus.OK) {
            return response;
        }

        ResourceMetadata repoMapping = repoRequest.getResourceMetadata();
        PersistentProperty<?> persistentProp = repoRequest.getPersistentEntity().getPersistentProperty(property);
        ResourceMapping propertyMapping = repoMapping.getMappingFor(persistentProp);

        ResourceSupport resource = response.getBody();

        List<Link> links = new ArrayList<Link>();

        ControllerLinkBuilder linkBuilder = linkTo(methodOn(RepositoryAclPropertyReferenceController.class)
                .followPropertyReference(repoRequest, id, property, assembler, pageable));

        if (resource instanceof Resource) {

            Object content = ((Resource<?>) resource).getContent();
            if (content instanceof Iterable) {

                for (Resource<?> res : (Iterable<Resource<?>>) content) {
                    Link l = new Link(res.getLink("self").getHref(), res.getId().toString());
                    links.add(l);
                    // links.add(linkBuilder.withRel(propertyMapping.getRel()));
                }

            } else if (content instanceof Map) {

                Map<Object, Resource<?>> map = (Map<Object, Resource<?>>) content;

                for (Entry<Object, Resource<?>> entry : map.entrySet()) {
                    Link l = new Link(entry.getValue().getLink("self").getHref(), entry.getKey().toString());
                    links.add(l);
                }
            }

        } else {
            links.add(linkBuilder.withRel(propertyMapping.getRel()));
        }

        return ControllerUtils.toResponseEntity(HttpStatus.OK, null, new Resource<Object>(EMPTY_RESOURCE_LIST, links));
    }

    @RequestMapping(value = BASE_MAPPING, method = { PATCH, PUT, POST }, //
            consumes = { MediaType.APPLICATION_JSON_VALUE, SPRING_DATA_COMPACT_JSON_VALUE, TEXT_URI_LIST_VALUE })
    public ResponseEntity<? extends ResourceSupport> createPropertyReference(
            final RootResourceInformation resourceInformation, final HttpMethod requestMethod,
            final @RequestBody(required = false) Resources<Object> incoming, @BackendId Serializable id,
            @PathVariable String property) throws Exception {

        final Resources<Object> source = incoming == null ? new Resources<>(Collections.emptyList()) : incoming;
        // final RepositoryInvoker invoker = resourceInformation.getInvoker();
        final AclJpaRepository<Object, Serializable> aclRepository = getAclRepository(resourceInformation.getDomainType());

        Function<ReferencedProperty, ResourceSupport> handler = new Function<ReferencedProperty, ResourceSupport>() {

            @Override
            public ResourceSupport apply(ReferencedProperty prop) throws HttpRequestMethodNotSupportedException {

                // Reload propertyValue - We need the original collection for modification
                Object propertyValue = prop.accessor.getProperty(prop.property);

                Class<?> propertyType = prop.property.getType();

                if (prop.property.isCollectionLike()) {

                    Collection<Object> collection = AUGMENTING_METHODS.contains(requestMethod)
                            ? (Collection<Object>) propertyValue : CollectionFactory.createCollection(propertyType, 0);

                    // Add to the existing collection
                    for (Link l : source.getLinks()) {
                        Object property = loadPropertyValue(prop.propertyType, l);
                        if (property != null) {
                            collection.add(property);
                        }
                    }

                    prop.accessor.setProperty(prop.property, collection);

                } else if (prop.property.isMap()) {

                    Map<String, Object> map = AUGMENTING_METHODS.contains(requestMethod)
                            ? (Map<String, Object>) propertyValue
                            : CollectionFactory.<String, Object> createMap(propertyType, 0);

                    // Add to the existing collection
                    for (Link l : source.getLinks()) {
                        Object property = loadPropertyValue(prop.propertyType, l);
                        if (property != null) {
                            map.put(l.getRel(), property);
                        }
                    }

                    prop.accessor.setProperty(prop.property, map);

                } else {

                    if (HttpMethod.PATCH.equals(requestMethod)) {
                        throw new HttpRequestMethodNotSupportedException(HttpMethod.PATCH.name(),
                                new String[] { "PATCH" },
                                "Cannot PATCH a reference to this singular property since the property type is not a List or a Map.");
                    }

                    if (source.getLinks().size() != 1) {
                        throw new IllegalArgumentException(
                                "Must send only 1 link to update a property reference that isn't a List or a Map.");
                    }

                    Object propVal = loadPropertyValue(prop.propertyType, source.getLinks().get(0));
                    prop.accessor.setProperty(prop.property, propVal);
                }

                publisher.publishEvent(new BeforeLinkSaveEvent(prop.accessor.getBean(), propertyValue));
                // Object result = invoker.invokeSave(prop.accessor.getBean());
                Object result = aclRepository.saveWithoutPermissionCheck(prop.accessor.getBean());
                publisher.publishEvent(new AfterLinkSaveEvent(result, propertyValue));

                return null;
            }
        };

        doWithReferencedProperty(resourceInformation, id, property, handler, requestMethod, null, null);

        return ControllerUtils.toEmptyResponse(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(value = BASE_MAPPING + "/{propertyId}", method = DELETE)
    public ResponseEntity<ResourceSupport> deletePropertyReferenceId(final RootResourceInformation repoRequest,
            @BackendId Serializable id, @PathVariable String property, final @PathVariable String propertyId)
            throws Exception {

        // final RepositoryInvoker invoker = repoRequest.getInvoker();
        final AclJpaRepository<Object, Serializable> aclRepository = getAclRepository(repoRequest.getDomainType());

        Function<ReferencedProperty, ResourceSupport> handler = new Function<ReferencedProperty, ResourceSupport>() {

            @Override
            public ResourceSupport apply(ReferencedProperty prop) {

                if (null == prop.propertyValue) {
                    return null;
                }

                // Reload propertyValue - We need the collection for deletion
                Object propertyValue = prop.accessor.getProperty(prop.property);

                if (prop.property.isCollectionLike()) {
                    Collection<Object> coll = (Collection<Object>) propertyValue;
                    Iterator<Object> itr = coll.iterator();
                    while (itr.hasNext()) {
                        Object obj = itr.next();

                        IdentifierAccessor accessor = prop.entity.getIdentifierAccessor(obj);
                        String s = accessor.getIdentifier().toString();

                        if (propertyId.equals(s)) {
                            itr.remove();
                        }
                    }
                } else if (prop.property.isMap()) {

                    Map<Object, Object> m = (Map<Object, Object>) propertyValue;
                    Iterator<Entry<Object, Object>> itr = m.entrySet().iterator();

                    while (itr.hasNext()) {

                        Object key = itr.next().getKey();

                        IdentifierAccessor accessor = prop.entity.getIdentifierAccessor(m.get(key));
                        String s = accessor.getIdentifier().toString();

                        if (propertyId.equals(s)) {
                            itr.remove();
                        }
                    }

                } else {
                    prop.accessor.setProperty(prop.property, null);
                }

                publisher.publishEvent(new BeforeLinkDeleteEvent(prop.accessor.getBean(), propertyValue));
                // Object result = invoker.invokeSave(prop.accessor.getBean());
                Object result = aclRepository.saveWithoutPermissionCheck(prop.accessor.getBean());
                publisher.publishEvent(new AfterLinkDeleteEvent(result, propertyValue));

                return null;
            }
        };

        doWithReferencedProperty(repoRequest, id, property, handler, HttpMethod.DELETE, null, propertyId);

        return ControllerUtils.toEmptyResponse(HttpStatus.NO_CONTENT);
    }

    private Object loadPropertyValue(Class<?> type, Link link) {

        String href = link.expand().getHref();
        String id = href.substring(href.lastIndexOf('/') + 1);

        RepositoryInvoker invoker = repositoryInvokerFactory.getInvokerFor(type);

        return invoker.invokeFindOne(id);
    }

    private ResourceSupport doWithReferencedProperty(RootResourceInformation resourceInformation, Serializable id,
            String propertyPath, Function<ReferencedProperty, ResourceSupport> handler, HttpMethod method,
            DefaultedPageable pageable, String propertyId) throws Exception {

        ResourceMetadata metadata = resourceInformation.getResourceMetadata();
        PropertyAwareResourceMapping mapping = metadata.getProperty(propertyPath);

        if (mapping == null || !mapping.isExported()) {
            throw new ResourceNotFoundException();
        }

        PersistentProperty<?> property = mapping.getProperty();
        resourceInformation.verifySupportedMethod(method, property);

        AclJpaRepository<Object, Serializable> propertyRepository = getAclRepository(property.getOwner().getType());

        // We first load the domainObject and check its accessibility
        Object domainObj = propertyRepository.findOne(id, HttpMethod.GET.equals(method) ? "read" : "update");

        if (null == domainObj) {
            // Doesn't exist or the current user has no permission to it
            throw new ResourceNotFoundException();
        }
        // Must clear the JPA cache otherwise lazily-loaded properties of the domainObject may
        // appear in the property as proxy-classes and totally mess up the content
        if (HttpMethod.GET.equals(method)) {
            propertyRepository.clear();
        }

        PersistentPropertyAccessor accessor = property.getOwner().getPropertyAccessor(domainObj);
        // Then we load the property itself
        Object propertyValue = findProperty(pageable, property, accessor, propertyId, propertyRepository);

        return handler.apply(new ReferencedProperty(property, propertyValue, accessor));
    }

    protected AclJpaRepository<Object, Serializable> getAclRepository(Class<?> type) {
        Object repository = repositories.getRepositoryFor(type);
        if (!AclJpaRepository.class.isAssignableFrom(AopUtils.getTargetClass(repository))) {
            throw new ResourceNotFoundException();
        }
        return (AclJpaRepository<Object, Serializable>) repository;
    }

    protected Object findProperty(DefaultedPageable pageable, PersistentProperty<?> property,
            PersistentPropertyAccessor accessor, String propertyId,
            AclJpaRepository<Object, Serializable> propertyRepository) {

        Serializable ownerId = (Serializable) accessor.getProperty(property.getOwner().getIdProperty());
        if (propertyId != null) {
            // find the property as an object
            return propertyRepository.findProperty(ownerId, property, propertyId);
        }
        // find the property as a collection
        Pageable page = pageable == null ? null : pageable.getPageable();
        return propertyRepository.findProperty(ownerId, property, page);
    }

    private class ReferencedProperty {

        final PersistentEntity<?, ?> entity;
        final PersistentProperty<?> property;
        final Class<?> propertyType;
        final Object propertyValue;
        final PersistentPropertyAccessor accessor;

        private ReferencedProperty(PersistentProperty<?> property, Object propertyValue,
                PersistentPropertyAccessor wrapper) {

            this.property = property;
            this.propertyValue = propertyValue;
            this.accessor = wrapper;
            this.propertyType = property.getActualType();
            this.entity = repositories.getPersistentEntity(propertyType);
        }
    }
}
