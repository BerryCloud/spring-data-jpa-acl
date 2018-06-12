package com.berrycloud.acl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import com.berrycloud.acl.domain.SimpleAclRole;
import com.berrycloud.acl.domain.SimpleAclUser;
import com.berrycloud.acl.sample.all.AclAppAll;
import com.berrycloud.acl.sample.all.entity.Attachment;
import com.berrycloud.acl.sample.all.entity.Document;
import com.berrycloud.acl.sample.all.entity.Person;
import com.berrycloud.acl.sample.all.entity.PersonHasPersonPermission;
import com.berrycloud.acl.sample.all.entity.Project;
import com.berrycloud.acl.sample.all.entity.TestGroup;
import com.berrycloud.acl.sample.all.entity.Theme;
import com.berrycloud.acl.sample.all.repository.AttachmentRepository;
import com.berrycloud.acl.sample.all.repository.DocumentRepository;
import com.berrycloud.acl.sample.all.repository.GroupRepository;
import com.berrycloud.acl.sample.all.repository.PersonHasPersonRepository;
import com.berrycloud.acl.sample.all.repository.PersonRepository;
import com.berrycloud.acl.sample.all.repository.PersonRepositoryNoAcl;
import com.berrycloud.acl.sample.all.repository.ProjectRepository;
import com.berrycloud.acl.sample.all.repository.RoleRepository;
import com.berrycloud.acl.sample.all.repository.ThemeRepository;
import com.berrycloud.acl.sample.all.service.PersonService;
import com.berrycloud.acl.security.AclUserDetails;
import com.berrycloud.acl.security.AclUserDetailsService;

@SpringBootTest(classes = AclAppAll.class)
@RunWith(SpringRunner.class)
@Transactional
public class AclAllRepositoryIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private AclLogic aclLogic;

    @Autowired
    private AclUtils aclUtils;

    @Autowired
    private AclUserDetailsService<?> aclUserDetailsService;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    PersonHasPersonRepository personHasPersonRepository;

    @Autowired
    private PersonRepositoryNoAcl personRepositoryNoAcl;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private ThemeRepository themeRepository;

    @Autowired
    private PersonService personService;

    private SimpleAclRole adminRole, userRole, editorRole, manipulatorRole;

    private Person admin, user, user2, user3;

    @Before
    public void initTests() {
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
        personRepository.saveWithoutPermissionCheck(admin);

        user = new Person("user", "u", "u");
        user.getAclRoles().add(userRole);
        personRepository.saveWithoutPermissionCheck(user);

        user2 = new Person("user2", "u2", "u2");
        user2.getAclRoles().add(userRole);
        user2.setCreatedBy(user);
        personRepository.saveWithoutPermissionCheck(user2);

        user3 = new Person("user3", "u3", "u3");
        user3.getAclRoles().add(userRole);
        personRepository.saveWithoutPermissionCheck(user3);

    }

    public void setAuthentication(String username) {
        if (username == null) {
            SecurityContextHolder.getContext().setAuthentication(null);
            return;
        }
        AclUserDetails userDetails = aclUserDetailsService.loadUserByUsername(username);

        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, "",
                userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    public void testGivenContextWhenStartAppThenEntitiesAreManaged() {
        assertFalse(aclLogic.isManagedType(SimpleAclUser.class));
        assertTrue(aclLogic.isManagedType(SimpleAclRole.class));
        assertTrue(aclLogic.isManagedType(Person.class));
        assertTrue(aclLogic.isManagedType(Document.class));
        assertTrue(aclLogic.isManagedType(TestGroup.class));
    }

    @Test
    public void testGivenNullAuthenticationWhenCallIsAdminTheReturnFalse() {
        setAuthentication(null);
        assertFalse(aclUtils.isAdmin());
    }

    @Test
    public void testGivenUserAuthenticationWhenCallIsAdminTheReturnFalse() {
        setAuthentication("user");
        assertFalse(aclUtils.isAdmin());
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallIsAdminTheReturnTrue() {
        setAuthentication("admin");
        assertTrue(aclUtils.isAdmin());
    }

    @Test
    public void testGivenNullAuthenticationWhenCallGetUsernameTheReturnNull() {
        setAuthentication(null);
        assertNull(aclUtils.getUsername());
    }

    @Test
    public void testGivenAuthenticationWhenCallGetUsernameTheReturnProperUsername() {
        setAuthentication("user");
        assertThat(aclUtils.getUsername(), is("user"));
    }

    @Test
    public void testGivenNullValueWhenCallIsManagedTypeThenReturnFalse() {
        assertFalse(aclLogic.isManagedType(null));
    }

    @Test
    public void testGivenNullAuthenticationWhenCallExistOnSimpleAclRoleThenReturnTrue() {
        setAuthentication(null);
        assertTrue(roleRepository.exists(adminRole.getId()));
    }

    @Test(expected = JpaObjectRetrievalFailureException.class)
    public void testGivenNullAuthenticationWhenCallUpdateOnSimpleAclRoleThenThrowException() {
        setAuthentication(null);
        roleRepository.save(adminRole);
    }

    @Test(expected = JpaObjectRetrievalFailureException.class)
    public void testGivenUserRoleWhenCallUpdateOnSimpleAclRoleThenThrowException() {
        setAuthentication("user");
        roleRepository.save(adminRole);
    }

    @Test
    public void testGivenAdminRoleWhenCallUpdateOnSimpleAclRoleThenNoException() {
        setAuthentication("admin");
        roleRepository.save(adminRole);
    }

    @Test
    public void testGivenNoAuthenticationWhenCallCountThenReturnZero() {
        setAuthentication(null);
        assertThat(personRepository.count(), is(0l));
    }

    @Test
    public void testGivenNoAuthenticationWhenCallCountNoAclThenReturnNonZero() {
        setAuthentication(null);
        assertThat(personRepositoryNoAcl.count(), greaterThan(0l));
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallCountThenReturnNonZero() {
        setAuthentication("admin");
        assertThat(personRepository.count(), greaterThan(0l));
    }

    @Test(expected = JpaObjectRetrievalFailureException.class)
    public void testGivenNoAuthenticationWhenCallDeleteThenThrowException() {
        setAuthentication(null);
        personRepository.delete(admin);
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallDeleteUserThenUserIsDeleted() {
        setAuthentication("admin");
        Person deleteUser = new Person("delme", "d", "d");
        personRepository.save(deleteUser);
        personRepository.delete(deleteUser);
        assertFalse(personRepository.exists(deleteUser.getId()));
    }

    @Test(expected = JpaObjectRetrievalFailureException.class)
    public void testGivenNoAuthenticationWhenCallDeleteUserThenThrowException() {
        setAuthentication(null);
        personRepository.delete(admin.getId());
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallDeleteUserByIdThenUserIsDeleted() {
        setAuthentication("admin");
        Person deleteUser = new Person("delme", "d", "d");
        personRepository.save(deleteUser);
        personRepository.delete(deleteUser.getId());
        assertFalse(personRepository.exists(deleteUser.getId()));
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallDeleteDetachedUserThenUserIsDeleted() {
        setAuthentication("admin");
        Person deleteUser = new Person("delme", "d", "d");
        personRepository.save(deleteUser);
        em.detach(deleteUser);
        personRepository.delete(deleteUser.getId());
        assertFalse(personRepository.exists(deleteUser.getId()));
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallDeleteEmptyCollectionThenNothingIsDeleted() {
        setAuthentication("admin");
        long count = personRepository.count();
        personRepository.deleteInBatch(new HashSet<Person>());
        assertThat(personRepository.count(), is(count));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallDeleteMultipleUsersThenOnlyProperUsersAreDeleted() {
        setAuthentication("user");
        Person deleteUser = new Person("delme", "d", "d");
        deleteUser.setCreatedBy(user);
        personRepository.saveWithoutPermissionCheck(deleteUser);
        personRepository.deleteInBatch(Arrays.asList(admin, deleteUser));
        assertNull(personRepositoryNoAcl.findOne(deleteUser.getId()));
        assertNotNull(personRepositoryNoAcl.findOne(admin.getId()));
    }

    @Test
    public void testGivenNoAuthenticationWhenCallDeleteUserNoAclThenUserIsDeleted() {
        setAuthentication(null);
        Person deleteUser = new Person("delme", "d", "d");
        personRepository.saveWithoutPermissionCheck(deleteUser);
        personRepositoryNoAcl.deleteInBatch(Collections.singletonList(deleteUser));
        assertNull(personRepositoryNoAcl.findOne(deleteUser.getId()));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallDeleteAllThenOnlyPermittedUsersAreDeleted() {
        setAuthentication("user");
        Person deleteUser = new Person("delme", "d", "d");
        deleteUser.setCreatedBy(user);
        personRepository.saveWithoutPermissionCheck(deleteUser);
        personRepository.deleteAll();
        assertNull(personRepositoryNoAcl.findOne(deleteUser.getId()));
        assertNotNull(personRepositoryNoAcl.findOne(admin.getId()));

    }

    @Test
    public void testGivenUserAuthenticationWhenCallDeleteAllInBatchThenOnlyPermittedUsersAreDeleted() {
        setAuthentication("user");
        Person deleteUser = new Person("delme", "d", "d");
        deleteUser.setCreatedBy(user);
        personRepository.saveWithoutPermissionCheck(deleteUser);
        personRepository.deleteAllInBatch();
        assertNull(personRepositoryNoAcl.findOne(deleteUser.getId()));
        assertNotNull(personRepositoryNoAcl.findOne(admin.getId()));
    }

    @Test
    public void testGivenNoAuthenticationWhenCallDeleteAllInBatchThenOnlyAllUsersAreDeleted() {
        setAuthentication(null);
        Person deleteUser = new Person("delme", "d", "d");
        deleteUser.setCreatedBy(user);
        personRepositoryNoAcl.save(deleteUser);
        personRepositoryNoAcl.deleteAllInBatch();
        assertThat(personRepositoryNoAcl.count(), is(0L));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallFindOnePermittedUserThenReturnUser() {
        setAuthentication("user");
        assertNotNull(personRepository.findOne(user.getId()));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallFindOneNotPermittedUserThenReturnNull() {
        setAuthentication("user");
        assertNull(personRepository.findOne(admin.getId()));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallFindOneWithAllowedPermissionThenReturnUser() {
        setAuthentication("user");
        assertNotNull(personRepository.findOne(user.getId(), AclConstants.READ_PERMISSION));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallFindOneWithNotAllowedPermissionThenReturnNull() {
        setAuthentication("user");
        assertNull(personRepository.findOne(user.getId(), AclConstants.DELETE_PERMISSION));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallGetOnePermittedUserThenReturnUser() {
        setAuthentication("user");
        assertNotNull(personRepository.findOne(user.getId()));
    }

    @Test(expected = JpaObjectRetrievalFailureException.class)
    public void testGivenUserAuthenticationWhenCallGetOneNotPermittedUserThenThrowException() {
        setAuthentication("user");
        personRepository.getOne(admin.getId());
    }

    @Test(expected = JpaObjectRetrievalFailureException.class)
    public void testGivenUserAuthenticationWhenCallGetOneWithNotAllowedPermissionThenThrowException() {
        setAuthentication("user");
        personRepository.getOne(user.getId(), AclConstants.DELETE_PERMISSION);
    }

    @Test
    public void testGivenUserAuthenticationWhenCallGetOnePermittedUserWithAllowedPermissionThenReturnUser() {
        setAuthentication("user");
        personRepository.getOne(user.getId(), AclConstants.READ_PERMISSION);
    }

    @Test
    public void testGivenValidIdWhenCallExistThenReturnTrue() {
        assertTrue(personRepositoryNoAcl.exists(user.getId()));
    }

    @Test
    public void testGivenInvalidIdWhenCallExistThenReturnFalse() {
        assertFalse(personRepositoryNoAcl.exists(-1));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallExistWithNotPermittedUserIdThenReturnFalse() {
        setAuthentication("user");
        assertFalse(personRepository.exists(admin.getId()));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallFindAllTheReturnNumberOfPermittedUsers() {
        setAuthentication("user");
        assertThat(personRepository.findAll().size(), is(2));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallFindAllNoAclTheReturnNumberOfAllUsers() {
        setAuthentication("user");
        assertThat(personRepositoryNoAcl.findAll().size(), is(4));
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallFindAllTheReturnNumberOfAllUsers() {
        setAuthentication("admin");
        assertThat(personRepository.findAll().size(), is(4));
    }

    @Test
    public void testGivenNullCollectionWhenCallfindAllThenReturnEmptyArray() {
        assertThat(personRepositoryNoAcl.findAll(null, AclConstants.READ_PERMISSION).size(), is(0));
    }

    @Test
    public void testGivenEmptyCollectionWhenCallfindAllThenReturnEmptyArray() {
        assertThat(personRepositoryNoAcl.findAll(Collections.<Integer>emptyList(), AclConstants.READ_PERMISSION).size(), is(0));
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallFindOneWithSpecificationTheReturnObject() {
        Specification<Person> spec = new Specification<Person>() {

            @Override
            public Predicate toPredicate(Root<Person> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                return cb.equal(root.get("username"), "admin");
            }
        };
        setAuthentication("admin");
        assertNotNull(personRepository.findOne(spec));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallFindOneWithSpecificationWithoutPermissionTheReturnNull() {
        Specification<Person> spec = new Specification<Person>() {

            @Override
            public Predicate toPredicate(Root<Person> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                return cb.equal(root.get("username"), "admin");
            }
        };
        setAuthentication("user");
        assertNull(personRepository.findOne(spec));
    }

    @Test
    public void testGivenAdmiAuthenticationWhenCallUpdateThenObjectIsUpdated() {
        setAuthentication("admin");
        admin.setFirstName("changed");
        personRepository.save(admin);
        assertThat(personRepository.getOne(admin.getId()).getFirstName(), is("changed"));
    }

    @Test(expected = JpaObjectRetrievalFailureException.class)
    public void testGivenUserAuthenticationWhenCallUpdateWithoutPermissionThenThrowException() {
        setAuthentication("user");
        admin.setFirstName("changed");
        personRepository.save(admin);
    }

    @Test(expected = JpaObjectRetrievalFailureException.class)
    public void testGivenNoPermissionWhenCallUpdateWithModifiedPermissionPropertyThenThrowException() {
        setAuthentication("user");
        user3.setCreatedBy(user);
        personRepository.save(user3);
    }

    @Test
    public void testGivenUserAuthenticationWhenCallfindAllSortedThenReturnPermittedObjectsInProperOrder() {
        setAuthentication("user");
        List<Person> list = personRepository.findAll(new Sort(new Sort.Order(Sort.Direction.ASC, "username")));
        assertThat(list.size(), is(2));
        assertThat(list.get(0).getId(), is(user.getId()));
        assertThat(list.get(1).getId(), is(user2.getId()));
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallfindAllSortedThenReturnAllObjectsInProperOrder() {
        setAuthentication("admin");
        List<Person> list = personRepository.findAll(new Sort(new Sort.Order(Sort.Direction.DESC, "username")));
        assertThat(list.size(), is(4));
        assertThat(list.get(0).getId(), is(user3.getId()));
        assertThat(list.get(1).getId(), is(user2.getId()));
        assertThat(list.get(2).getId(), is(user.getId()));
        assertThat(list.get(3).getId(), is(admin.getId()));
    }

    @Test
    public void testGivenAdminAuthenticationWhenCallFindOneByExampleThenReturnObject() {
        setAuthentication("admin");
        assertNotNull(personRepository.findOne(Example.of(admin)));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallFindOneByExampleWithoutPermissionThenReturnNull() {
        setAuthentication("user");
        assertNull(personRepository.findOne(Example.of(admin)));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallAuthorizedMethodByIdWithPermissionThenMethodIsCalled() {
        setAuthentication("user");
        assertTrue(personService.loadPerson(user.getId(), AclConstants.READ_PERMISSION));
    }

    @Test(expected = AccessDeniedException.class)
    public void testGivenUserAuthenticationWhenCallAuthorizedMethodByIdWithoutPermissionThenThrowException() {
        setAuthentication("user");
        assertTrue(personService.loadPerson(user.getId(), AclConstants.UPDATE_PERMISSION));
    }

    @Test(expected = AccessDeniedException.class)
    public void testGivenUserAuthenticationWhenCallAuthorizedMethodWithInvalidTypeThenThrowException() {
        setAuthentication("user");
        assertTrue(personService.loadPersonInvalidType(user.getId(), AclConstants.READ_PERMISSION));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallAuthorizedMethodByObjectWithPermissionThenMethodIsCalled() {
        setAuthentication("user");
        assertTrue(personService.loadPerson(user, AclConstants.READ_PERMISSION));
    }

    @Test(expected = AccessDeniedException.class)
    public void testGivenUserAuthenticationWhenCallAuthorizedMethodByObjectWithoutPermissionThenThrowException() {
        setAuthentication("user");
        assertTrue(personService.loadPerson(user, AclConstants.UPDATE_PERMISSION));
    }

    @Test(expected = AccessDeniedException.class)
    public void testGivenUserAuthenticationWhenCallAuthorizedMethodByObjectWithInvalidObjectThenThrowException() {
        setAuthentication("user");
        assertTrue(personService.loadInvalidObject("user", AclConstants.READ_PERMISSION));
    }

    @Test(expected = AccessDeniedException.class)
    public void testGivenUserAuthenticationWhenCallAuthorizedMethodByObjectWithNullThenThrowException() {
        setAuthentication("user");
        assertTrue(personService.loadInvalidObject(null, AclConstants.READ_PERMISSION));
    }

    @Test
    public void testGivenUserAuthenticationWhenCallHasAuthorityThenReturnProperValues() {
        setAuthentication("user");
        assertTrue(aclUtils.hasAuthority(AclConstants.ROLE_USER));
        assertFalse(aclUtils.hasAuthority(AclConstants.ROLE_ADMIN));
    }

    @Test
    public void testGivenGroupRoleWhenCallHasAuthorityThenReturnTrueForMembers() {
        TestGroup editorGroup = new TestGroup("Editor's Group", admin);
        editorGroup.getMembers().add(user);
        editorGroup.setRole(editorRole);
        groupRepository.saveWithoutPermissionCheck(editorGroup);
        user.getGroups().add(editorGroup);
        personRepositoryNoAcl.save(user);
        setAuthentication("user");
        assertTrue(aclUtils.hasAuthority("ROLE_EDITOR"));
    }

    @Test
    public void testGivenGroupRoleWhenCallHasAuthorityThenReturnFalseForCreator() {
        TestGroup editorGroup = new TestGroup("Editor's Group", admin);
        editorGroup.getMembers().add(user);
        editorGroup.setRole(editorRole);
        groupRepository.saveWithoutPermissionCheck(editorGroup);
        user.getGroups().add(editorGroup);
        personRepositoryNoAcl.save(user);
        setAuthentication("admin");
        assertFalse(aclUtils.hasAuthority("ROLE_EDITOR"));
    }

    @Test
    public void testGivenGroupMembersWhenCallRepositoryMethodsThenReturnObjectAccordingToOwnerPermission() {
        setAuthentication("user");
        Person member = new Person("member", "m", "m");
        personRepository.saveWithoutPermissionCheck(member);
        assertNull(personRepository.findOne(member.getId()));

        TestGroup editorGroup = new TestGroup("Editor's Group", user);
        editorGroup.getMembers().add(member);
        groupRepository.saveWithoutPermissionCheck(editorGroup);
        member.getGroups().add(editorGroup);
        personRepositoryNoAcl.save(member);

        assertNotNull(personRepository.findOne(member.getId()));
    }

    @Test
    public void testGivenGroupMembersWhenCallProjectRepositoryMethodsThenReturnObjectAccordingToParentOwnerPermission() {
        setAuthentication("user");
        Person member = new Person("member", "m", "m");
        personRepository.saveWithoutPermissionCheck(member);

        TestGroup projectGroup = new TestGroup("Project Group", user);
        projectGroup.getMembers().add(member);
        groupRepository.saveWithoutPermissionCheck(projectGroup);
        member.getGroups().add(projectGroup);
        personRepositoryNoAcl.save(member);

        Project project = new Project();
        project.getGroups().add(projectGroup);
        projectRepository.saveWithoutPermissionCheck(project);
        projectGroup.getProjects().add(project);
        groupRepository.saveWithoutPermissionCheck(projectGroup);

        assertNotNull(projectRepository.findOne(project.getId()));
    }

    @Test
    public void testGivenCurrentUserHasPermissionInMultipleWaysWhenCallProjectRepositoryMethodsThenReturnListIsDistinct() {
        setAuthentication("user");
        Person member = new Person("member", "m", "m");
        personRepository.saveWithoutPermissionCheck(member);
        Person member2 = new Person("member2", "m2", "m2");
        personRepository.saveWithoutPermissionCheck(member2);

        TestGroup projectGroup = new TestGroup("Project Group", user);
        projectGroup.getMembers().add(member);
        projectGroup.getMembers().add(member2);
        groupRepository.saveWithoutPermissionCheck(projectGroup);
        member.getGroups().add(projectGroup);
        member2.getGroups().add(projectGroup);
        personRepositoryNoAcl.save(member);
        personRepositoryNoAcl.save(member2);

        Project project = new Project();
        project.getGroups().add(projectGroup);
        projectRepository.saveWithoutPermissionCheck(project);
        projectGroup.getProjects().add(project);
        groupRepository.saveWithoutPermissionCheck(projectGroup);

        assertNotNull(projectRepository.findOne(project.getId()));

        assertThat(projectRepository.findAll().size(), is(1));

    }

    @Test
    public void testGivenNoAclEntityWhenCallRepositoryMethodsThenReturnObject() {
        Theme theme = new Theme("theme", "content");
        themeRepository.save(theme);
        setAuthentication("user");
        assertNotNull(themeRepository.findOne(theme.getId()));
    }

    @Test
    public void testGivenFullOwnerPermissionsWhenCallDeleteThenObjectIsDeleted() {
        setAuthentication("user2");
        Document doc = new Document("doc", "content", user2);
        documentRepository.saveWithoutPermissionCheck(doc);
        assertTrue(documentRepository.exists(doc.getId()));
        documentRepository.delete(doc);
        assertFalse(documentRepository.exists(doc.getId()));
    }

    @Test
    public void testGivenRolePermissionsAndNoRoleWhenCallfindOneOnAttachmentThenReturnNull() {
        Attachment attachment = new Attachment("name", "content", null, null);
        attachmentRepository.saveWithoutPermissionCheck(attachment);
        setAuthentication("user");
        assertNull(attachmentRepository.findOne(attachment.getId()));
    }

    @Test
    public void testGivenRolePermissionsAndProperRoleWhenCallFindOneOnAttachmentThenReturnObject() {
        Attachment attachment = new Attachment("name", "content", null, null);
        attachmentRepository.saveWithoutPermissionCheck(attachment);
        user.getAclRoles().add(manipulatorRole);
        personRepositoryNoAcl.save(user);
        setAuthentication("user");
        assertNotNull(attachmentRepository.findOne(attachment.getId()));
    }

    @Test(expected = JpaObjectRetrievalFailureException.class)
    public void testGivenRolePermissionsAndProperRoleWhenCallDeleteOnAttachmentThenThrowException() {
        Attachment attachment = new Attachment("name", "content", null, null);
        attachmentRepository.save(attachment);
        user.getAclRoles().add(manipulatorRole);
        personRepositoryNoAcl.save(user);
        setAuthentication("user");
        attachmentRepository.delete(attachment);
    }

    @Test
    public void testGivenRolePermissionsAndProperRoleWhenCallSaveOnAttachmentThenObjectIsSaved() {
        Attachment attachment = new Attachment("name", "content", null, null);
        attachmentRepository.saveWithoutPermissionCheck(attachment);
        user.getAclRoles().add(manipulatorRole);
        personRepositoryNoAcl.save(user);
        setAuthentication("user");
        attachment.setContent("new content");
        attachmentRepository.save(attachment);

        assertThat(attachmentRepository.findOne(attachment.getId()).getContent(), is("new content"));
    }

    @Test
    public void testGivenRolePermissionsAndNoProperRoleAsAdminWhenCallfindOneOnAttachmentThenReturnNull() {
        Attachment attachment = new Attachment("name", "content", null, null);
        attachmentRepository.saveWithoutPermissionCheck(attachment);
        setAuthentication("admin");
        assertNull(attachmentRepository.findOne(attachment.getId()));
    }

    @Test
    public void testGivenDirectPermissionLinkToPersonWhenCallExistsThenReturnTrue() {
        setAuthentication("user2");
        assertFalse(personRepository.exists(user.getId()));
        PersonHasPersonPermission ppp = new PersonHasPersonPermission(user2, user, "read");
        personHasPersonRepository.save(ppp);

        assertTrue(personRepository.exists(user.getId()));
    }
}
