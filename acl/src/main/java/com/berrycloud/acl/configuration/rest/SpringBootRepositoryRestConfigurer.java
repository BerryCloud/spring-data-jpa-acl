package com.berrycloud.acl.configuration.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.rest.RepositoryRestProperties;
import org.springframework.core.annotation.Order;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurerAdapter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * A {@code RepositoryRestConfigurer} that applies configuration items from the
 * {@code spring.data.rest} namespace to Spring Data REST. Also, if a
 * {@link Jackson2ObjectMapperBuilder} is available, it is used to configure Spring Data
 * REST's {@link ObjectMapper ObjectMappers}.
 * <p>
 * This class was copied from its initial package to here because the original one is not public.
 *
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 */
@Order(0)
class SpringBootRepositoryRestConfigurer extends RepositoryRestConfigurerAdapter {

    @Autowired(required = false)
    private Jackson2ObjectMapperBuilder objectMapperBuilder;

    @Autowired
    private RepositoryRestProperties properties;

    @Override
    public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config) {
        this.properties.applyTo(config);
    }

    @Override
    public void configureJacksonObjectMapper(ObjectMapper objectMapper) {
        if (this.objectMapperBuilder != null) {
            this.objectMapperBuilder.configure(objectMapper);
        }
    }
}
