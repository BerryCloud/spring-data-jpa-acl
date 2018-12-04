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
import java.util.Optional;
import java.util.function.Function;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.CollectionFactory;
import org.springframework.data.domain.Pageable;
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
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.data.rest.webmvc.support.BackendId;
import org.springframework.data.rest.webmvc.support.DefaultedPageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
  private static final String COMPLEMENT = "Complement";

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

    Function<ReferencedProperty, ResourceSupport> handler = prop -> prop.mapValue(it -> {
      if (prop.property.isCollectionLike()) {
        return toResources((Iterable<?>) it, assembler, prop.propertyType, Optional.empty());

      } else if (prop.property.isMap()) {

        Map<Object, Resource<?>> resources = new HashMap<>();

        for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) it).entrySet()) {
          resources.put(entry.getKey(), assembler.toResource(entry.getValue()));
        }

        return new Resource<Object>(resources);

      } else {

        PersistentEntityResource resource = assembler.toResource(it);
        headers.set("Content-Location", resource.getId().getHref());
        return resource;
      }
    }).orElseThrow(() -> new ResourceNotFoundException());

    Optional<ResourceSupport> responseResource = doWithReferencedProperty(repoRequest, id, property, handler,
        HttpMethod.GET, pageable, null);

    return ControllerUtils.toResponseEntity(HttpStatus.OK, headers, responseResource);
  }

  @RequestMapping(value = BASE_MAPPING + COMPLEMENT, method = GET)
  public ResponseEntity<ResourceSupport> followPropertyComplementReference(final RootResourceInformation repoRequest,
      @BackendId Serializable id, final @PathVariable String property,
      final PersistentEntityResourceAssembler assembler, DefaultedPageable pageable) throws Exception {

    final HttpHeaders headers = new HttpHeaders();

    Function<ReferencedProperty, ResourceSupport> handler = prop -> prop.mapValue(it -> {
      return toResources((Iterable<?>) it, assembler, prop.propertyType, Optional.empty());
    }).orElseThrow(() -> new ResourceNotFoundException());

    Optional<ResourceSupport> responseResource = doWithReferencedProperty(repoRequest, id, property, handler,
        HttpMethod.GET, pageable, COMPLEMENT);

    return ControllerUtils.toResponseEntity(HttpStatus.OK, headers, responseResource);
  }

  @RequestMapping(value = BASE_MAPPING, method = DELETE)
  public ResponseEntity<? extends ResourceSupport> deletePropertyReference(final RootResourceInformation repoRequest,
      @BackendId Serializable id, @PathVariable String property) throws Exception {

    AclJpaRepository<Object, Object> aclRepository = getAclRepository(repoRequest.getDomainType());
    // final RepositoryInvoker repoMethodInvoker = repoRequest.getInvoker();

    Function<ReferencedProperty, ResourceSupport> handler = prop -> prop.mapValue(it -> {
      if (prop.property.isCollectionLike() || prop.property.isMap()) {
        throw HttpRequestMethodNotSupportedException.forRejectedMethod(HttpMethod.DELETE)
            .withAllowedMethods(HttpMethod.GET, HttpMethod.HEAD);
      } else {
        prop.wipeValue();
      }

      publisher.publishEvent(new BeforeLinkDeleteEvent(prop.accessor.getBean(), prop.propertyValue));
      Object result = aclRepository.saveWithoutPermissionCheck(prop.accessor.getBean());
      publisher.publishEvent(new AfterLinkDeleteEvent(result, prop.propertyValue));

      return (ResourceSupport) null;

    }).orElse(null);

    doWithReferencedProperty(repoRequest, id, property, handler, HttpMethod.DELETE, null, null);

    return ControllerUtils.toEmptyResponse(HttpStatus.NO_CONTENT);
  }

  @RequestMapping(value = BASE_MAPPING + "/{propertyId}", method = GET)
  public ResponseEntity<ResourceSupport> followPropertyReference(final RootResourceInformation repoRequest,
      @BackendId Serializable id, @PathVariable String property, final @PathVariable String propertyId,
      final PersistentEntityResourceAssembler assembler) throws Exception {

    final HttpHeaders headers = new HttpHeaders();

    Function<ReferencedProperty, ResourceSupport> handler = prop -> prop.mapValue(it -> {
      if (prop.property.isCollectionLike()) {
        PersistentEntityResource resource = assembler.toResource(prop.propertyValue);
        headers.set("Content-Location", resource.getId().getHref());
        return resource;
      } else if (prop.property.isMap()) {
        PersistentEntityResource resource = assembler.toResource(prop.propertyValue);
        headers.set("Content-Location", resource.getId().getHref());
        return resource;
      } else {
        return new Resource<>(prop.propertyValue);
      }

    }).orElseThrow(() -> new ResourceNotFoundException());

    Optional<ResourceSupport> responseResource = doWithReferencedProperty(repoRequest, id, property, handler,
        HttpMethod.GET, null, propertyId);
    return ControllerUtils.toResponseEntity(HttpStatus.OK, headers, responseResource);
  }

  @RequestMapping(value = BASE_MAPPING, method = GET, produces = { SPRING_DATA_COMPACT_JSON_VALUE,
      TEXT_URI_LIST_VALUE })
  public ResponseEntity<ResourceSupport> followPropertyReferenceCompact(RootResourceInformation repoRequest,
      @BackendId Serializable id, @PathVariable String property, PersistentEntityResourceAssembler assembler,
      DefaultedPageable pageable) throws Exception {

    return createCompactResponse(followPropertyReference(repoRequest, id, property, assembler, pageable));
  }

  private ResponseEntity<ResourceSupport> createCompactResponse(ResponseEntity<ResourceSupport> response) {

    if (response.getStatusCode() != HttpStatus.OK) {
      return response;
    }

    ResourceSupport resource = response.getBody();

    List<Link> links = new ArrayList<>();

    if (resource instanceof Resources) {
      ((Resources<?>) resource).getContent();
      for (Resource<?> res : ((Resources<Resource<?>>) resource).getContent()) {
        links.add(res.getLink("self"));
      }
      if (resource instanceof PagedResources) {
        return ControllerUtils.toResponseEntity(HttpStatus.OK, HttpHeaders.EMPTY,
            new PagedResources<>(Collections.emptyList(), ((PagedResources<?>) resource).getMetadata(), links));
      }
    } else {

      Object content = ((Resource<?>) resource).getContent();
      if (content instanceof Map) {

        Map<Object, Resource<?>> map = (Map<Object, Resource<?>>) content;

        for (Entry<Object, Resource<?>> entry : map.entrySet()) {
          Link l = new Link(entry.getValue().getLink("self").getHref(), entry.getKey().toString());
          links.add(l);
        }
      } else {
        links.add(resource.getLink("self"));
      }
    }
    return ControllerUtils.toResponseEntity(HttpStatus.OK, HttpHeaders.EMPTY,
        new Resources<>(EMPTY_RESOURCE_LIST, links));
  }

  @RequestMapping(value = BASE_MAPPING + COMPLEMENT, method = GET, produces = { SPRING_DATA_COMPACT_JSON_VALUE,
      TEXT_URI_LIST_VALUE })
  public ResponseEntity<ResourceSupport> followPropertyComplementReferenceCompact(RootResourceInformation repoRequest,
      @BackendId Serializable id, @PathVariable String property, PersistentEntityResourceAssembler assembler,
      DefaultedPageable pageable) throws Exception {

    return createCompactResponse(followPropertyComplementReference(repoRequest, id, property, assembler, pageable));
  }

  @RequestMapping(value = BASE_MAPPING, method = { PATCH, PUT, POST }, //
      consumes = { MediaType.APPLICATION_JSON_VALUE, SPRING_DATA_COMPACT_JSON_VALUE, TEXT_URI_LIST_VALUE })
  public ResponseEntity<? extends ResourceSupport> createPropertyReference(
      final RootResourceInformation resourceInformation, final HttpMethod requestMethod,
      final @RequestBody(required = false) Resources<Object> incoming, @BackendId Serializable id,
      @PathVariable String property) throws Exception {

    final Resources<Object> source = incoming == null ? new Resources<>(Collections.emptyList()) : incoming;

    AclJpaRepository<Object, Object> aclRepository = getAclRepository(resourceInformation.getDomainType());

    Function<ReferencedProperty, ResourceSupport> handler = prop -> {

      // Reload propertyValue - We need the original collection for modification
      Object propertyValue = prop.accessor.getProperty(prop.property);

      Class<?> propertyType = prop.property.getType();

      if (prop.property.isCollectionLike()) {
        Collection<Object> collection = AUGMENTING_METHODS.contains(requestMethod) ? (Collection<Object>) propertyValue
            : CollectionFactory.createCollection(propertyType, 0);

        // Add to the existing collection
        for (Link l : source.getLinks()) {
          Object value = loadPropertyValue(prop.propertyType, l);
          if (value != null) {
            collection.add(value);
          }
        }

        prop.accessor.setProperty(prop.property, collection);

      } else if (prop.property.isMap()) {

        Map<String, Object> map = AUGMENTING_METHODS.contains(requestMethod) ? (Map<String, Object>) propertyValue
            : CollectionFactory.<String, Object> createMap(propertyType, 0);

        // Add to the existing collection
        for (Link l : source.getLinks()) {
          Object value = loadPropertyValue(prop.propertyType, l);
          if (value != null) {
            map.put(l.getRel(), value);
          }
        }

        prop.accessor.setProperty(prop.property, map);

      } else {

        if (HttpMethod.PATCH.equals(requestMethod)) {
          throw HttpRequestMethodNotSupportedException.forRejectedMethod(HttpMethod.PATCH)//
              .withAllowedMethods(HttpMethod.PATCH)//
              .withMessage(
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
      Object result = aclRepository.saveWithoutPermissionCheck(prop.accessor.getBean());
      publisher.publishEvent(new AfterLinkSaveEvent(result, propertyValue));

      return null;

    };

    doWithReferencedProperty(resourceInformation, id, property, handler, requestMethod, null, null);

    return ControllerUtils.toEmptyResponse(HttpStatus.NO_CONTENT);
  }

  @RequestMapping(value = BASE_MAPPING + "/{propertyId}", method = DELETE)
  public ResponseEntity<ResourceSupport> deletePropertyReferenceId(final RootResourceInformation repoRequest,
      @BackendId Serializable backendId, @PathVariable String property, final @PathVariable String propertyId)
      throws Exception {

    // final RepositoryInvoker invoker = repoRequest.getInvoker();
    AclJpaRepository<Object, Object> aclRepository = getAclRepository(repoRequest.getDomainType());

    Function<ReferencedProperty, ResourceSupport> handler = prop -> prop.mapValue(it -> {

      // Reload propertyValue - We need the collection for deletion
      Object propertyValue = prop.accessor.getProperty(prop.property);

      if (prop.property.isCollectionLike()) {
        Collection<Object> coll = (Collection<Object>) propertyValue;
        Iterator<Object> itr = coll.iterator();
        while (itr.hasNext()) {
          Object obj = itr.next();

          Optional.ofNullable(prop.entity.getIdentifierAccessor(obj).getIdentifier())//
              .map(Object::toString)//
              .filter(propertyId::equals)//
              .ifPresent(__ -> itr.remove());
        }

      } else if (prop.property.isMap()) {
        Map<Object, Object> m = (Map<Object, Object>) propertyValue;
        Iterator<Entry<Object, Object>> itr = m.entrySet().iterator();

        while (itr.hasNext()) {

          Object key = itr.next().getKey();

          Optional.ofNullable(prop.entity.getIdentifierAccessor(m.get(key)).getIdentifier())//
              .map(Object::toString)//
              .filter(propertyId::equals)//
              .ifPresent(__ -> itr.remove());
        }
      } else {
        prop.wipeValue();
      }

      publisher.publishEvent(new BeforeLinkDeleteEvent(prop.accessor.getBean(), propertyValue));
      Object result = aclRepository.saveWithoutPermissionCheck(prop.accessor.getBean());
      publisher.publishEvent(new AfterLinkDeleteEvent(result, propertyValue));

      return (ResourceSupport) null;

    }).orElse(null);

    doWithReferencedProperty(repoRequest, backendId, property, handler, HttpMethod.DELETE, null, propertyId);

    return ControllerUtils.toEmptyResponse(HttpStatus.NO_CONTENT);
  }

  private Object loadPropertyValue(Class<?> type, Link link) {

    String href = link.expand().getHref();
    String id = href.substring(href.lastIndexOf('/') + 1);

    RepositoryInvoker invoker = repositoryInvokerFactory.getInvokerFor(type);

    return invoker.invokeFindById(id).orElse(null);
  }

  private Optional<ResourceSupport> doWithReferencedProperty(RootResourceInformation resourceInformation,
      Serializable id, String propertyPath, Function<ReferencedProperty, ResourceSupport> handler, HttpMethod method,
      DefaultedPageable pageable, String propertyId) throws Exception {

    ResourceMetadata metadata = resourceInformation.getResourceMetadata();
    PropertyAwareResourceMapping mapping = metadata.getProperty(propertyPath);

    if (mapping == null || !mapping.isExported()) {
      throw new ResourceNotFoundException();
    }

    PersistentProperty<?> property = mapping.getProperty();
    resourceInformation.verifySupportedMethod(method, property);

    AclJpaRepository<Object, Object> propertyRepository = getAclRepository(property.getOwner().getType());

    // We first load the domainObject and check its accessibility
    Object domainObj = propertyRepository.findById(id, HttpMethod.GET.equals(method) ? "read" : "update")
        .orElseThrow(() -> new ResourceNotFoundException());

    PersistentPropertyAccessor<Object> accessor = property.getOwner().getPropertyAccessor(domainObj);
    Object propertyValue;
    if (propertyId == COMPLEMENT) {
      if (property.isCollectionLike()) {
        propertyRepository.clear();
        // Load the complement-collection
        propertyValue = findPropertyComplement(pageable, property, accessor, propertyRepository);
      } else {
        throw new ResourceNotFoundException();
      }
    } else if (property.isMap()) {
      // If the property is a Map load it directly without pagination and ACL.
      propertyValue = accessor.getProperty(property);
    } else {
      // Must clear the JPA cache otherwise lazily-loaded properties of the domainObject may
      // appear in the property as proxy-classes and totally mess up the content
      if (HttpMethod.GET.equals(method)) {
        propertyRepository.clear();
      }
      // Then we load the property itself using ACl specification
      propertyValue = findProperty(pageable, property, accessor, propertyId, propertyRepository);
    }
    return Optional.ofNullable(handler.apply(new ReferencedProperty(property, propertyValue, accessor, repositories)));
  }

  protected AclJpaRepository<Object, Object> getAclRepository(Class<?> type) {
    Object repository = repositories.getRepositoryFor(type).orElseThrow(() -> new ResourceNotFoundException());
    if (!AclJpaRepository.class.isAssignableFrom(AopUtils.getTargetClass(repository))) {
      throw new ResourceNotFoundException();
    }
    return (AclJpaRepository<Object, Object>) repository;
  }

  protected Object findProperty(DefaultedPageable pageable, PersistentProperty<?> property,
      PersistentPropertyAccessor<Object> accessor, String propertyId,
      AclJpaRepository<Object, Object> propertyRepository) {

    Object ownerId = accessor.getProperty(property.getOwner().getIdProperty());
    if (propertyId != null) {
      // find the property as an object
      return propertyRepository.findProperty(ownerId, property, propertyId);
    }
    // find the property as a collection
    Pageable page = pageable == null ? Pageable.unpaged() : pageable.getPageable();
    return propertyRepository.findProperty(ownerId, property, page);
  }

  protected Object findPropertyComplement(DefaultedPageable pageable, PersistentProperty<?> property,
      PersistentPropertyAccessor<Object> accessor, AclJpaRepository<Object, Object> propertyRepository) {

    Object ownerId = accessor.getProperty(property.getOwner().getIdProperty());

    // find the property as a collection-complement
    Pageable page = pageable == null ? Pageable.unpaged() : pageable.getPageable();
    return propertyRepository.findPropertyComplement(ownerId, property, page);
  }

  private static class ReferencedProperty {

    final PersistentEntity<?, ?> entity;
    final PersistentProperty<?> property;
    final Class<?> propertyType;
    final Object propertyValue;
    final PersistentPropertyAccessor<Object> accessor;

    private ReferencedProperty(PersistentProperty<?> property, Object propertyValue,
        PersistentPropertyAccessor<Object> wrapper, Repositories repositories) {

      this.property = property;
      this.propertyValue = propertyValue;
      this.accessor = wrapper;
      this.propertyType = property.getActualType();
      this.entity = repositories.getPersistentEntity(propertyType);
    }

    public void wipeValue() {
      accessor.setProperty(property, null);
    }

    public <T> Optional<T> mapValue(Function<Object, T> function) {
      return Optional.ofNullable(propertyValue).map(function);
    }
  }

  @ExceptionHandler
  public ResponseEntity<Void> handle(HttpRequestMethodNotSupportedException exception) {
    return exception.toResponse();
  }

  static class HttpRequestMethodNotSupportedException extends RuntimeException {

    private static final long serialVersionUID = 3704212056962845475L;

    private final HttpMethod rejectedMethod;
    private final HttpMethod[] allowedMethods;
    private final String message;

    private HttpRequestMethodNotSupportedException(HttpMethod rejectedMethod, HttpMethod[] allowedMethods,
        String message) {
      this.rejectedMethod = rejectedMethod;
      this.allowedMethods = allowedMethods;
      this.message = message;
    }

    public static HttpRequestMethodNotSupportedException forRejectedMethod(HttpMethod method) {
      return new HttpRequestMethodNotSupportedException(method, new HttpMethod[0], null);
    }

    public HttpRequestMethodNotSupportedException withAllowedMethods(HttpMethod... methods) {
      return new HttpRequestMethodNotSupportedException(this.rejectedMethod, methods.clone(), null);
    }

    public HttpRequestMethodNotSupportedException withMessage(String message, Object... parameters) {
      return new HttpRequestMethodNotSupportedException(this.rejectedMethod, this.allowedMethods,
          String.format(message, parameters));
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
      return message;
    }

    public ResponseEntity<Void> toResponse() {
      return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(allowedMethods).build();
    }
  }
}
