package com.berrycloud.acl;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.util.Locale;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.rest.webmvc.RestMediaTypes;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.berrycloud.acl.domain.SimpleAclRole;
import com.berrycloud.acl.sample.all.AclAppAll;
import com.berrycloud.acl.sample.all.entity.Person;
import com.berrycloud.acl.sample.all.repository.PersonHasPersonRepository;
import com.berrycloud.acl.sample.all.repository.PersonRepositoryNoAcl;
import com.berrycloud.acl.sample.all.repository.RoleRepository;

@SpringBootTest(classes = AclAppAll.class)
@RunWith(SpringRunner.class)
@Transactional
public class AclAllControllerIntegrationTest {

    private static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(),
            MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));

    @Autowired
    PersonHasPersonRepository personHasPersonRepository;

    @Autowired
    private PersonRepositoryNoAcl personRepositoryNoAcl;

    @Autowired
    private RoleRepository roleRepository;

    private SimpleAclRole adminRole, userRole, editorRole, manipulatorRole;

    private Person admin, user, user2, user3, user4;

    @Autowired
    WebApplicationContext ctx;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @Before
    public void initTests() {
        mockMvc = MockMvcBuilders.webAppContextSetup(ctx).addFilters(springSecurityFilterChain).build();

        adminRole = new SimpleAclRole(AclConstants.ROLE_ADMIN);
        userRole = new SimpleAclRole(AclConstants.ROLE_USER);
        editorRole = new SimpleAclRole("ROLE_EDITOR");
        manipulatorRole = new SimpleAclRole("ROLE_MANIPULATOR");
        roleRepository.saveWithoutPermissionCheck(adminRole);
        roleRepository.saveWithoutPermissionCheck(userRole);
        roleRepository.saveWithoutPermissionCheck(editorRole);
        roleRepository.saveWithoutPermissionCheck(manipulatorRole);

        admin = new Person("admin", "a", "a");
        admin.getAclRoles().add(adminRole);
        personRepositoryNoAcl.saveWithoutPermissionCheck(admin);

        user = new Person("user", "u", "u");
        user.getAclRoles().add(userRole);
        user.setCreatedBy(admin);
        personRepositoryNoAcl.saveWithoutPermissionCheck(user);

        user2 = new Person("user2", "u2", "u2");
        user2.getAclRoles().add(userRole);
        user2.setCreatedBy(user);
        user2.getSupervisors().add(user);
        user2.getSupervisors().add(admin);
        personRepositoryNoAcl.saveWithoutPermissionCheck(user2);

        admin.setControlled(user2);
        personRepositoryNoAcl.saveWithoutPermissionCheck(admin);
        user.setControlled(user2);
        personRepositoryNoAcl.saveWithoutPermissionCheck(user);

        user3 = new Person("user3", "u3", "u3");
        user3.getAclRoles().add(userRole);
        user3.setCreatedBy(user);
        personRepositoryNoAcl.saveWithoutPermissionCheck(user3);

        user4 = new Person("user4", "u4", "u4");
        user4.getAclRoles().add(userRole);
        personRepositoryNoAcl.saveWithoutPermissionCheck(user4);

    }

    @Test
    public void testGivenProperPermissionWhenCallGetPropertyOnInvalidPropertyThenReturnNotFound() throws Exception {
        // @formatter:off
        mockMvc.perform(
                get("/persons/" + user2.getId() + "/invalid").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==")
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNotFound()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null));
        // @formatter:on
    }

    @Test
    public void testGivenProperPermissionWhenCallGetPropertyObjectThenReturnJsonObject() throws Exception {
        // @formatter:off
        mockMvc.perform(
                get("/persons/" + user2.getId() + "/createdBy").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==")
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk()).andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

                .andExpect(jsonPath("$.username", is("user")))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenProperPermissionWhenCallGetPropertyObjectByIdThenReturnJsonObject() throws Exception {
        // @formatter:off
        mockMvc.perform(get("/persons/" + user2.getId() + "/createdBy/" + user.getId())
                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==").contentType(MediaType.APPLICATION_JSON)
                .locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk()).andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

                .andExpect(jsonPath("$.username", is("user")))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenNoPermissionWhenCallGetPropertyObjectThenReturnJsonObject() throws Exception {
        // @formatter:off
        mockMvc.perform(
                get("/persons/" + user.getId() + "/createdBy").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==")
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNotFound()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenProperPermissionWhenCallGetPropertyCollectionThenReturnOnlyPermittedObjects()
            throws Exception {
        // @formatter:off
        mockMvc.perform(
                get("/persons/" + user2.getId() + "/supervisors").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==")
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk()).andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

                .andExpect(jsonPath("$._embedded.persons", hasSize(1)))
                .andExpect(jsonPath("$._embedded.persons[0].username", is("user")))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenNoProperPermissionWhenCallGetPropertyCollectionThenReturnEmptyCollection() throws Exception {
        // @formatter:off
        mockMvc.perform(
                get("/persons/" + user2.getId() + "/supervisors").header("Authorization", "Basic dXNlcjI6cGFzc3dvcmQ=") // user2
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk()).andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

                .andExpect(jsonPath("$._embedded.persons", hasSize(0)))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenNoProperPermissionOnObjectWhenCallGetPropertyCollectionThenReturnNotFound() throws Exception {
        // @formatter:off
        mockMvc.perform(
                get("/persons/" + user.getId() + "/supervisors").header("Authorization", "Basic dXNlcjI6cGFzc3dvcmQ=") // user2
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNotFound()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenProperPermissionWhenCallGetPropertyCollectionElementThenReturnObject() throws Exception {
        // @formatter:off
        mockMvc.perform(get("/persons/" + user2.getId() + "/supervisors/" + user.getId())
                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==").contentType(MediaType.APPLICATION_JSON)
                .locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk()).andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

                .andExpect(jsonPath("$.username", is("user")))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenAdminPermissionWhenCallGetPropertyCollectionThenReturnOnlyAllObjects() throws Exception {
        // @formatter:off
        mockMvc.perform(
                get("/persons/" + user2.getId() + "/supervisors").header("Authorization", "Basic YWRtaW46cGFzc3dvcmQ=")
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk()).andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

                .andExpect(jsonPath("$._embedded.persons", hasSize(2)))
                .andExpect(jsonPath("$._embedded.persons[0].username", is("admin")))
                .andExpect(jsonPath("$._embedded.persons[1].username", is("user")))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenAdminPermissionWhenCallGetPropertyCollectionWithPaginationThenReturnOnlyAllObjects()
            throws Exception {
        // @formatter:off
        mockMvc.perform(get("/persons/" + user2.getId() + "/supervisors?page=1&size=1")
                .header("Authorization", "Basic YWRtaW46cGFzc3dvcmQ=").contentType(MediaType.APPLICATION_JSON)
                .locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk()).andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

                .andExpect(jsonPath("$._embedded.persons", hasSize(1)))
                .andExpect(jsonPath("$._embedded.persons[0].username", is("user")))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenAdminPermissionWhenCallGetPropertyCollectionWithPaginationAndSortThenReturnOnlyAllObjects()
            throws Exception {
        // @formatter:off
        mockMvc.perform(get("/persons/" + user2.getId() + "/supervisors?page=1&size=1&sort=username,desc")
                .header("Authorization", "Basic YWRtaW46cGFzc3dvcmQ=").contentType(MediaType.APPLICATION_JSON)
                .locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk()).andExpect(content().contentType(APPLICATION_JSON_UTF8))
                .andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

                .andExpect(jsonPath("$._embedded.persons", hasSize(1)))
                .andExpect(jsonPath("$._embedded.persons[0].username", is("admin")))

        ;
        // @formatter:on
    }

    @Test
    public void testGivenProperPermissionWhenDeletePropertyObjectThenObjectIsDeleted() throws Exception {
        // @formatter:off
        mockMvc.perform(
                delete("/persons/" + user2.getId() + "/createdBy").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==")
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertNull(personRepositoryNoAcl.findOne(user2.getId()).getCreatedBy());
    }

    @Test
    public void testGivenProperPermissionWhenDeletePropertyObjectByIdThenPropertyIsDeleted() throws Exception {
        // @formatter:off
        mockMvc.perform(delete("/persons/" + user2.getId() + "/createdBy/" + user.getId())
                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==").contentType(MediaType.APPLICATION_JSON)
                .locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertNull(personRepositoryNoAcl.findOne(user2.getId()).getCreatedBy());
    }

    @Test
    public void testGivenNoPermissionWhenDeletePropertyObjectThenPropertyIsNotDeleted() throws Exception {
        user2.setCreatedBy(admin);
        personRepositoryNoAcl.save(user2);
        // @formatter:off
        mockMvc.perform(
                delete("/persons/" + user2.getId() + "/createdBy").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==")
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertNotNull(personRepositoryNoAcl.findOne(user2.getId()).getCreatedBy());
    }

    @Test
    public void testGivenProperPermissionWhenDeletePropertyCollectionThenReturnMethodNotAllowed() throws Exception {
        // @formatter:off
        mockMvc.perform(delete("/persons/" + user2.getId() + "/supervisors")
                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==").contentType(MediaType.APPLICATION_JSON)
                .locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isMethodNotAllowed()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

    }

    @Test
    public void testGivenProperPermissionWhenDeletePropertyCollectionElementThenElementIsDeleted() throws Exception {
        // @formatter:off
        mockMvc.perform(delete("/persons/" + user2.getId() + "/supervisors/" + user.getId())
                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==").contentType(MediaType.APPLICATION_JSON)
                .locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertThat(personRepositoryNoAcl.findOne(user2.getId()).getSupervisors(),
                not(CoreMatchers.<Person>hasItem(hasProperty("username", is("user")))));
    }

    @Test
    public void testGivenNoPermissionWhenDeletePropertyCollectionElementThenElementIsNotDeleted() throws Exception {

        // @formatter:off
        mockMvc.perform(delete("/persons/" + user2.getId() + "/supervisors/" + admin.getId())
                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==").contentType(MediaType.APPLICATION_JSON)
                .locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertThat(personRepositoryNoAcl.findOne(user2.getId()).getSupervisors(),
                CoreMatchers.<Person>hasItem(hasProperty("username", is("admin"))));
    }

    @Test
    public void testGivenNoPermissionOnObjectWhenDeletePropertyObjectThenPropertyIsNotDeleted() throws Exception {
        // User has only read permission on himself
        // @formatter:off
        mockMvc.perform(
                delete("/persons/" + user.getId() + "/controlled").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                        .contentType(MediaType.APPLICATION_JSON).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNotFound()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertNotNull(personRepositoryNoAcl.findOne(user.getId()).getControlled());
    }

    @Test
    public void testGivenPermissionOnObjectWhenPutPropertyObjectThenPropertyIsAdded() throws Exception {
        // @formatter:off
        mockMvc.perform(
                put("/persons/" + user2.getId() + "/controlled").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                        .contentType(RestMediaTypes.TEXT_URI_LIST).locale(Locale.UK).content("/persons/" + user.getId())
                        .accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertNotNull(personRepositoryNoAcl.findOne(user2.getId()).getControlled());
        assertThat(personRepositoryNoAcl.findOne(user2.getId()).getControlled().getUsername(), is("user"));
    }

    @Test
    public void testGivenPermissionOnObjectWhenPutPropertyObjectWithEmptyContentThenReturn500() throws Exception {
        // @formatter:off
        mockMvc.perform(
                put("/persons/" + user2.getId() + "/controlled").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                        .contentType(RestMediaTypes.TEXT_URI_LIST).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isInternalServerError()).andExpect(redirectedUrl(null))
                .andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertNull(personRepositoryNoAcl.findOne(user2.getId()).getControlled());
    }

    @Test
    public void testGivenPermissionOnObjectWhenPutPropertyObjectWithMultipleContentThenReturn500() throws Exception {
        // @formatter:off
        mockMvc.perform(put("/persons/" + user2.getId() + "/controlled")
                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                .contentType(RestMediaTypes.TEXT_URI_LIST).locale(Locale.UK)
                .content("/persons/" + user.getId() + "\n/persons/" + user.getId()).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isInternalServerError()).andExpect(redirectedUrl(null))
                .andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertNull(personRepositoryNoAcl.findOne(user2.getId()).getControlled());
    }

    @Test
    public void testGivenPermissionOnObjectWhenPatchPropertyObjectThenReturnNotAllowed() throws Exception {
        // @formatter:off
        mockMvc.perform(
                patch("/persons/" + user2.getId() + "/controlled").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                        .contentType(RestMediaTypes.TEXT_URI_LIST).locale(Locale.UK).content("/persons/" + user.getId())
                        .accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isMethodNotAllowed()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertNull(personRepositoryNoAcl.findOne(user2.getId()).getControlled());
    }

    @Test
    public void testGivenPermissionOnObjectWhenPutPropertyObjectWithoutPermissionThenPropertyIsNotAdded()
            throws Exception {
        // @formatter:off
        mockMvc.perform(
                put("/persons/" + user2.getId() + "/controlled").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                        .contentType(RestMediaTypes.TEXT_URI_LIST).locale(Locale.UK)
                        .content("/persons/" + admin.getId()).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertNull(personRepositoryNoAcl.findOne(user2.getId()).getControlled());
    }

    @Test
    public void testGivenPermissionOnObjectWhenPutPropertyCollectionThenOnlyPropertiesWithPermissionAreAdded()
            throws Exception {
        // @formatter:off
        mockMvc.perform(put("/persons/" + user3.getId() + "/supervisors")
                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                .contentType(RestMediaTypes.TEXT_URI_LIST).locale(Locale.UK)
                .content("/persons/" + admin.getId() + "\n/persons/" + user.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null));
        // @formatter:on

        assertThat(personRepositoryNoAcl.findOne(user3.getId()).getSupervisors(),
                CoreMatchers.<Person>hasItem(hasProperty("username", is("user"))));
        assertThat(personRepositoryNoAcl.findOne(user3.getId()).getSupervisors(),
                not(CoreMatchers.<Person>hasItem(hasProperty("username", is("admin")))));
    }

    @Test
    public void testGivenPermissionOnObjectWhenPostPropertyCollectionThenOnlyPropertiesWithPermissionAreAdded()
            throws Exception {
        // @formatter:off
        mockMvc.perform(post("/persons/" + user3.getId() + "/supervisors")
                .header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                .contentType(RestMediaTypes.TEXT_URI_LIST).locale(Locale.UK)
                .content("/persons/" + admin.getId() + "\n/persons/" + user.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null));
        // @formatter:on

        assertThat(personRepositoryNoAcl.findOne(user3.getId()).getSupervisors(),
                CoreMatchers.<Person>hasItem(hasProperty("username", is("user"))));
        assertThat(personRepositoryNoAcl.findOne(user3.getId()).getSupervisors(),
                not(CoreMatchers.<Person>hasItem(hasProperty("username", is("admin")))));
    }

    @Test
    public void testGivenPermissionOnObjectWhenPostPropertyCollectionWithEmptyContentThenDoNothing() throws Exception {
        // @formatter:off
        mockMvc.perform(
                post("/persons/" + user3.getId() + "/supervisors").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                        .contentType(RestMediaTypes.TEXT_URI_LIST).locale(Locale.UK).accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertThat(personRepositoryNoAcl.findOne(user3.getId()).getSupervisors(), empty());

    }

    @Test
    public void testGivenPermissionOnObjectWhenPostPropertyCollectionThenOnlyPropertiesWithPermissionAreAddedToTheCollection()
            throws Exception {
        // @formatter:off
        mockMvc.perform(
                post("/persons/" + user2.getId() + "/supervisors").header("Authorization", "Basic dXNlcjpwYXNzd29yZA==") // user
                        .contentType(RestMediaTypes.TEXT_URI_LIST).locale(Locale.UK)
                        .content("/persons/" + user4.getId() + "\n/persons/" + user3.getId())
                        .accept(MediaType.APPLICATION_JSON))

                .andExpect(status().isNoContent()).andExpect(redirectedUrl(null)).andExpect(forwardedUrl(null))

        ;
        // @formatter:on

        assertThat(personRepositoryNoAcl.findOne(user2.getId()).getSupervisors(),
                CoreMatchers.<Person>hasItem(hasProperty("username", is("user"))));
        assertThat(personRepositoryNoAcl.findOne(user2.getId()).getSupervisors(),
                CoreMatchers.<Person>hasItem(hasProperty("username", is("admin"))));
        assertThat(personRepositoryNoAcl.findOne(user2.getId()).getSupervisors(),
                CoreMatchers.<Person>hasItem(hasProperty("username", is("user3"))));
        assertThat(personRepositoryNoAcl.findOne(user2.getId()).getSupervisors(),
                not(CoreMatchers.<Person>hasItem(hasProperty("username", is("user4")))));
    }
}
