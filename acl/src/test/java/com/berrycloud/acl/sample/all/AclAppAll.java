package com.berrycloud.acl.sample.all;

import com.berrycloud.acl.configuration.EnableAclJpaRepositories;
import com.berrycloud.acl.sample.all.service.PersonService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableAclJpaRepositories
public class AclAppAll {

    @Bean
    public PersonService personService() {
        return new PersonService();
    }

    @Configuration
    public static class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.httpBasic().and().authorizeRequests().anyRequest().authenticated()
                    .and()
                    .csrf().disable();
        }
    }
}
  