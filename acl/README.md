# Spring Boot Data JPA ACL (for Spring Boot 2 - See SB1 branch for Spring Boot 1)

This package is an extension for Spring Data JPA. It provides a fast an easily customizable access-control system for projects using Spring Repositories. (Plus some useful extra features)

ACL usually means 'Access Control List', but in this case it stands for 'Access Control Logic'. Although it could still use lists for permission control in some special cases, most of the access decisions are made by predefined access-logic rules.

Let's see the main differences between traditional AC-List implementations and this package:

1. This package uses only standard JPA methods and queries, so in theory it's compatible with any Relational DB that has JPA support.
2. There are no additional tables in your DB with weak-references to your objects.
3. All the access control logics are defined in the domain objects via annotations, so you can see the data-structure and the access rules in the same place
4. The permission decisions are happened in the DB side, so there is much less traffic between your application and the DB, and you can even use paginated queries on permission-filtered entities.
5. It's compatible with Spring Data Jpa Repositories so the ACL is automatically applied for all standard repository methods.
6. It's also compatible with Spring Data Rest. All of your restful endpoints are automatically secured by the ACL
7. As a side-effect, you can use paginated queries on all property-collection endpoints under Spring Data Rest. (Property collections are also filtered by the ACL)

On top of the above advantages, the ACL also has the following in-built features:

1. Search feature for searching objects by text patterns. The result will contain all objects where all of the words of the search-pattern appear in any of the searchable properties of the domain object. (It could be also a paginated result-set.) The search rules are also defined via annotations in the entity classes.
2. Endpoints for complement-collections for collection-like properties. (Very handy feature for creating convenient UI for adding elements for collections.) Simply append the "Complement" at the end of any default Data Rest RepositoryProperty endpoint.

## Basics

The basic idea behind this ACL package is that most of the time when you want to grant access for a user to a given object in the DB, there is already some kind of relation between them.

Let's see some example:

- A user has full control to his documents (probably there is an author field in the document table). 
- Users in the same work-group can see (but cannot modify) each-others. (There must be a workgroup table with a ManyToMany relationship with users.)
- Administrators have full access to all objects. (They must have a special role attached to them somehow. Either directly or they are members of an admin group)
- If a user is a moderator in a topic he can edit all messages sent to that topic (Must be a relation between messages and topics) and also the replies to these messages (There must be a field in the message table what stores the id of the original message if the current one is a reply.)

So instead of creating a new permission entry (or a bunch of entries) every time we are creating a new object, we simply use the existing relationships for access control.
Sometimes it means that you have to add a few extra relations to your tables when you design your DB structure, but hey, that's why it's called 'relational DB'!

## Usage

Add the following dependency to your pom.xml:

	<dependency>
    	<groupId>com.github.BerryCloud.spring-data-jpa-acl</groupId>
		<artifactId>spring-data-jpa-acl</artifactId>
		<version>LATEST</version>
	</dependency>

... and the following repository:

	<repository>
		<id>jitpack.io</id>
		<url>https://jitpack.io</url>
	</repository>

Thanks to Spring's auto-configuration all necessary beans are initialized automatically. All you have to do is add the following annotation to one of your configuration classes:
	
	@EnableAclJpaRepositories
	
The ACL extension is basically a security feature, so it automatically turns on Spring Security too. For ACL security we need at least the following basic components:
- AclUserDetailsService (With an AclUserDetails implementation)
- AclUser entity
- AclRole entity

If any of these beans or domain classes are missing the ACl automatically creates them:
- SimpleAclUserDetailsService (using SimpleAclUserDetails)
- SimpleAclUser
- SimpleAclRole

You can use all Spring Data Repository features exactly the same way as it's described in Spring Data JPA documentation, but you have to extend the AclJpaRepository interface instead of JpaRepository:

	public interface UserRepository extends AclJpaRepository<SimpleAclUser, Integer>{
	    
	    List<SimpleAclUser> findByLastName(@Param("name") String name);
	
	    SimpleAclUser findByUsername(@Param("username") String username);
	    
	    List<SimpleAclUser> findAllByOrderByLastNameDesc();
	    List<SimpleAclUser> findAllByOrderByIdAsc();
	    
	    Long countByIdGreaterThan(@Param("id") Integer id);
	}

The results of all query methods will be filtered by the ACL rules and all save and delete methods will be executed only on entities what the current user has permission to based on the ACL rules.

Unfortunately the query methods where you manually define the native or JPA query cannot be processed by the ACL, so if any of your repositories contains such methods you will get a warning during startup:

	    @Query("select p from SimpleAclUser p")
	    List<SimpleAclUser> mySelect();

#_These methods WON'T BE secured by the ACL at all even if you don't add the @NoAcl annotation!_

You can annotate such methods with the @NoAcl annotation to prevent these warnings.

@NoAcl annotation is also useful if you want to create methods (or repositories) without permission checks. (E.g. you want a repository with full ACL support for your API and an other one without permission checks for inner use for your scheduled tasks.)

If you also want to use Spring Data Rest module, you can include it the usual way:

    <dependency>
      <groupId>org.springframework.data</groupId>
      <artifactId>spring-data-rest-webmvc</artifactId>
    </dependency>
    
If this package is in the classpath the ACL module automatically creates the necessary beans and modification for the Rest API and it will use the ACL secured repository methods. There is also a slight improvement in the rest API that allows using paginated queries on properties.

You should also consider adding the following dependency if you use Hibernate, otherwise lazily loaded properties could appear in an inconsistent way:
    
	<dependency>
      <groupId>com.fasterxml.jackson.datatype</groupId>
      <artifactId>jackson-datatype-hibernate5</artifactId>
      <optional>true</optional>
    </dependency>
    
## ACL rules

You can build up your ACL rules using a few simple annotations. All of the following annotations have a String[] field where you can set a list of permissions. Permissions are represented by strings. The ACl uses the following default permissions (CRUD):
create, read, update, delete, all

'all' permission provides all of the other permissions. Not only the ones listed here but any other permissions you use in your project.
'read' permission is automatically provided if any other permission is present. (It's quite logical: If you have any kind of permission to an object i.e. you can 'do' something with that object then we can assume you can 'see' that object.)

### @AclSelf

This annotation defines the permissions the users will gain on their own domain object. By default any user can read, update or delete her own domain-object but sometimes you want to limit their access. (Maybe you want to create special protocol for deletion, or you want to prevent modifying usernames or passwords using the default API) 

A simple example for a User domain class:

	@Entity
	@AclSelf({ "read" })
	public class Person implements AclUser<SimpleAclRole> {
	
	    @Id
	    @GeneratedValue(strategy = GenerationType.AUTO)
	    private Integer id;
	
	    @Column(unique = true, nullable = false)
	    private String username;
	    @JsonIgnore
	    private String password = "password";
	
	    @ManyToMany(fetch = FetchType.LAZY)
	    private Set<SimpleAclRole> aclRoles;
	
	    private String firstName;
	    private String lastName;
	    
	    // ... other properties, getters/setters
	}
   
It implements the AclUser interface, so when a user logs in, the AclUserDetailsService automatically tries to locate the user by username in this table. The `@AclSelf` annotation grants "read" permission for users to their own user-object, so every user can see but cannot modify or delete their user-object. 

If you create the following repository

	@RepositoryRestResource(collectionResourceRel = "people", path = "people")
	public interface PersonRepository extends AclJpaRepository<Person, Integer>{  
	}

... and have spring-data-rest-webmvc dependency in your pom.xml then if a user tries to access the /people endpoint she could see only her own user object there.


### @AclOwner

With this annotation we can provide some permission to a user or group of users via a property. Let's see the following simple document entity: 

	@Entity
	public class Document {
	
	    @Id
	    @GeneratedValue(strategy = GenerationType.AUTO)
	    private Integer id;
	
	    private String title;
	    private String content;

	    @AclOwner
	    @ManyToOne(fetch = FetchType.LAZY)
	    private Person creator;
	}

Most of the time you create properties like 'creator' or 'createdBy' or 'owner' anyway so adding this association to your domain class won't waste any extra resources. But by adding only this one extra annotation here you can easily set a permission for all of the users to all of their documents. (The default permission is 'all')

If the users of this system commonly share documents with each others (and can modify each-other's documents) then you can add the following association:

	@AclOwner("update")
	@ManyToMany
	private List<Person> editors;
	
All the users who are in relation with a document via this association will gain the 'update' permission to that document. You can freely combine any number of annotations in any of your domain classes. So using the annotations above together will grant the users full access to their own documents and read/update permission to all documents which were shared with them.

Maintaining ManyToMany relationships between all the users and objects is quite messy and needs a lot of DB resources, so most of the systems are using some kind of group-hierarchy instead.
Let's say we have WorkGroups where the users can work on documents together.

	@Entity
	public class WorkGroup {
		@Id
	    @GeneratedValue(strategy = GenerationType.AUTO)
	    private Integer id;
	    
	    private String groupName;
	    
	    @AclOwner
	    Person groupOwner;
	    
		@AclOwner("read")
		@ManyToMany
		private List<Person> members;    
	}
	
As you can see the groupOwner has full permission to the group (She can rename or delete it) and the members has "read" permission, so they can see this group in the group list. (But they still cannot see each-others! All `@AclOwner` annotation applies to the actual entity, but not to the associated entities.)

Let's allow the users to share their documents with any WorkGroups:

	@AclOwner("update")
	@ManyToMany
	private List<WorkGroup> workGroups;
	
(Of course you have to define the other side of these associations, but there is no need to add any extra annotation to the other side, so ignore it now.)

When you add an `@AclOwner` annotation to a field what is not an AclUser or collection of AclUsers the ACL assumes that you want to grant permission to the AclUsers who are owners of that object. (I.e. all AclUser or collection of AclUsers who are annotated with `@AclOwner` in that domain class.) In this case it means that the owner of the groups and all members of the groups which are connected with the given document will gain "update" permission to the document. Notice that the members also gain the "update" permission even though they got only "read" permission to the group itself. When they gain permission to the WorkGroup object they get it via the `@AclOwner` annotation in the WorkGroup.class, but when they gain the permission to the document they get it by the `@AclOwner` annotation in the Document class.

(The current ACl implementation has some limitation here. You cannot grant different permissions to the members and the owner in the example above. However I will probably solve this issue in a later revision)

## @AclParent

Let's go one step further!
We want to add attachments to our documents and want to grant the very same permissions to these attachment objects that the users have to their parent-document. `@AclOwner` won't work here because the evaluation of this annotation is not recursive. So if we add the following lines:

	@Entity
	public class Attachment {
		// ...
		
		@AclOwner
		@ManyToOne
		private Document document;
		
		/ ...
	}
	
... that will grant permission to the attachments of a document for the owner of the document (Because the owner is an AclUser annotated with `@AclOwner` in the Document.class), but it won't grant any permissions to the members of the WorkGroups which are linked to the document.

In this cases we can use the `@AclParent` annotation. We can define the permissions in the same way as for the `@AclOwner`:

		@AclParent({"update", "delete", "execute"})
		@ManyToOne
		private Document document;

`@AclParent` declares that any user who has the listed permissions to the annotated object or collection of objects gain that permission to this object too.
So the owner of the parent document will gain all the permissions (Even the "execute" one because the default `@AclOwner` annotation grants "all" permissions, even user-defined ones), and the members of the WorkGroups associated with the documents will gain "update" permission (Their `@AclOwner` annotation allows only "update")

Sometimes you need further refining the permissions. You want to grant different permissions to an object than to its parent. In this case you can define a prefix for the `@AclParent` annotation:

	@AclParent(value={"update","delete"}, prefix="attachments")
	
In this case if a user has permission to the parent object only the permissions with the proper prefix will take place. If we added the previous annotation to the document field in the Attachment.class then we have to grant "attachments-update" and "attachments-delete" permissions to the owner and the workGroups field of the documents if want to allow the owner and the members of the linked WorkGroups to modify the attachments.

`@AclParent` annotations are evaluated recursively. You can even stack the prefixes like "documents-attachments-read"

The main purpose of `@AclParent` annotations is that you don't have to set permissions to all of the objects. You can easily build up a "permission tree". If a user has permission to the root she has permissions to all the leafs too. 

In a complex DB where you have to define dozens of inherited permissions there is a chance that you accidentally create a inheritance-loop. to avoid infinite-loops during permission evaluating you can set the maximum depth of permission inheritance via `@AclParent` annotations. Set the following property in your application.properties file (the default value is 2):

	spring.data.jpa.acl.max-depth = 2

## @AclRolePermission
	
An other common permission control is the role-based permission system. With the `@AclRolePermission` annotation you can easily grant permissions to a given domain class by roles. Because it grants permissions by role and not by relations it should be used on the class itself, not on properties. The value field of the annotation is the same as in the other annotations: It contains an array of strings which represents the permissions. The other field of the annotation is 'roles()'. It is also an array of string where each string represents a role (i.e. a authority name for a GrantedAuthority object). An empty role array means that ANY user with ANY role will gain the listed permissions. So annotating a domain class with the following annotation means that everybody will gain "read" access to these domain objects (The default value of the roles field is an empty array):

	@AclRolePermission("read")
	@Entity
	public class Theme {
		/ ...
	}
	
`@AclRolePermission` annotations are in an 'OR' relation with the `@AclOwner` and `@AclParent` annotations.

So a user gain permission either if she has the proper role OR if she has a relation with the object via a properly annotated property.

If a domain object is not annotated with `@AclRolePermission` the ACL treats it as there would be the following annotation on it:

	@AclRolePermission(value="all", roles="ROLE_ADMIN")
	
So by default any user with "ROLE_ADMIN" role will gain all possible permissions to all domain objects. This default behavior grants the full access for administrators to all of the domain objects. So if you add this annotation to an entity class don't forget to manually add the appropriate admin permission too!


`@AclRolePermission` is a `@Repeatable` annotation. You can set different permission-sets for different roles. If your java version is 1.7 or smaller, you can use the `@AclRolePermissions` container annotation for it.


## @AclRoleCondition

This annotation is an other tool for role-based permission control. While the previous role annotation has an 'OR' relation with the permission-annotations, this one has an 'AND' relation. By using this annotation you can set a precondition for the `@AclOwner` and `@AclParent` annotations. Adding the following annotation to the document.class means that only users with "ROLE_EDITOR" could gain "update" permissions to these domain objects:

	@AclRoleCondition(value="update", roles="ROLE_EDITOR")
	@AclRoleCondition(value="read")
	@Entity
	public class Document {
		// ...
	}

The users gain "update" if they have "ROLE_EDITOR" role AND they also have some relationship with the object via an annotated property (`@AclOwner` or `@AclParent`)
The second annotation states that any user CAN gain "read" permission if they have an annotated relationship with an entity.
Empty roles() array means ANY role. So the following annotation means that only users with "ROLE_POWER" could gain any kind of permissions to these objects, but only if they have the proper relationship with the object via `@AclOwner` and `@AclParent` annotation:

	@AclRoleCondition(value="all", roles="ROLE_POWER")
	
If you add the following annotation to a domain class (and this is the only role annotation on it) then nobody can gain any permission on the entities of this class via `@AclOwner` and `@AclParent` annotations (However they can be accessed with roles defined with an @AclRolePermission):

	@AclRoleCondition(value={}, roles={})
	@Entity
	public class SystemData {
		// ...
	}


The upper annotation affects all users (users with ANY roles) and it has no permissions defined, so the precondition will fail for any permissions.

This annotation is also a `@Repeatable` annotation. You can also use the `@AclRoleConditions` container annotation.

If no `@AclRoleCondition` annotation is present on a domain class the ACL treats it as there would be the following precondition there:
	
	@AclRoleCondition(value="all", roles{})
	
Means: ANY user could gain all type of permissions via `@AclOwner` or `@AclParent` annotations.


## PermissionLink

Most of the cases a well-designed DB structure and the above annotations would be enough for handling any permission/restriction requirements.

But sometimes you really have to assign permissions dynamically. (Like in a traditional Access-Control-List)

Traditional ACL allows you to create permissions to any object. However you probably don't need such a flexibility because you can handle most of the uses-cases using the above annotations. So you probably need to allow dynamic permissions to only a few domain classes. (Other permissions are covered by `@AclOwner` and `@AclParent` annotations)

In this case you can use the PermissionLink superclass for creating dynamic permission-links.

Let's assume we need some extra feature for our shared-document system. There is a requirement that some 3rd party auditors or freelancers could have specific permissions to some of the documents or only to their attachments.
The provided permissions could be anything: "read", "update" or even some specific ones like "audit". Of course we don't want to add extra relationships for all of these permissions (Because `@AclOwner` annotation could have only predefined permissions). So we can create the following class:

	@AclRoleCondition(value="all", roles="ROLE_ADMIN")
	@Entity
	public class PersonHasDocumentPermission extends PermissionLink<Person, Document> {
	}
	
As you can see all you have to do is to create a class extending the PermissionLink abstract class and provides the generic types for it. PermissionLink is annotated with `@MappedSuperclass` and contains all permission annotation the ACl needs, so all tables and JPA managedTypes will be created automatically. (If you want to link it to a specific DB table or want to use different column names then you can override the JPA annotations in the subclass) I also added an `@AclRoleCondition` annotation to it, so only administrators can create and delete these permission entities. 
Note, that the PermissionLink superclass creates ManyToOne associations to the owner and the target classes, so we have to define the other side of these associations manually in the appropriate classes.
In the Person class:

    @OneToMany(mappedBy = "owner", fetch = FetchType.LAZY)
    private List<PersonHasDocumentPermission> documentPermissionLink;

And in the Document class:

    @OneToMany(mappedBy = "target", fetch = FetchType.LAZY)
    private List<PersonHasDocumentPermission> personPermissionLink;


Now, we can create a User for a 3rd party auditor and the administrator of the system can create the proper permissionLinks for that user to the documents he could interested in. We can set any kind of permission in the permission field of the PermissionLink Entity. But if we need to add multiple permissions, we have to create separate PermissionLink objects for each permissions. However the rules for "all" and "read" permissions are also applies here. (Creating PermissionLink with "all" permission automatically grants every possible permissions for the owner to the target and creating a PermissionLink with any kind of permission will automatically grants "read" permission to the target.)

We can also use prefixed permissions. So if want to hire a freelancer artist to do some photoshop magic on the attachments of some documents, we can grant him "read" and "attachments-update" permissions. This way he can see the document but cannot modify it, but he can modify all the attachments of the document. And we can grant these permissions one by one to any document.


And that's all. using the above annotations and maybe a few PermissionLink objects you can easily define almost any permission-schema. And all of your permission rules will be defined right in the domain-classes, you don't have to create special methods and don't have to use `@PreAuthorize` annotations on your logic or repository methods.

## @AclCreatePermission

Granting permissions for object-creation is usually a weak-point of any ACL, because you cannot define permission to an object what is about to create, simply because it's not exist at the time when the permission should be checked. 
Using the `@AclCreatePermission` annotation we can control the object creation in a similar way as any other permissions.

The following annotation grants 'create' permission for managers:

	@AclCreatePermission(roles="ROLE_MANAGER")
	@Entity
	public class UserNote

This annotation also works like all the other annotations: the empty roles property means: any roles.

When the Acl checks for 'create' permissions it also checks the `@AclRolePermission` annotations, so by default all users with ROLE_ADMIN role can create any kind of objects.
You can add a 'create' permission to certain roles by a `@AclRolePermission` annotation too, but don't forget that in this case those roles also automatically gain 'read' permission too! 

	
## @PreAuthorize

The ACL module automatically defines a AclPermissionEvaluator bean, so you can use all its functionality without any further settings.
It could be useful if you need some custom endpoints which should be accessed only with a special permission. Let's say we have runnable scripts on the server and these scripts and they results can be seen by any users but they can be executed only be they owners.
You can create a controller like this:

	@PreAuthorize(script, 'execute')
	@PutMapping("/scripts/{script}/launch")
	public ResponseEntity<String> execute(@PathVariable Script script) {
	...
	}
	
The AcPermissionEvaluator check whether the current authentication has 'execute' permission to the given script object. You can also use this new permission in any of the Acl annotations. If you use 'all' in any of the annotations that will automatically grant this new custom permission too. 

## Special methods

AS it was mentioned above you have to extend the AclJpaRepository interface instead of the JpaRepository when you create your own repositories. This new interface defines a few extra methods:

	<S extends T> S saveWithoutPermissionCheck(S entity);
	void deleteWithoutPermissionCheck(T entity);
	T findOneWithoutPermissionCheck(ID id);
	
These methods work exactly the same as the appropriate original JpaRepository methods. So if you need to access/save/delete an object without permission you can use these methods. saveWithoutPermissionCheck() works like the original save() in the JpaRepository: It creates a new object if it was not persisted yet or updates the object otherwise. 	

There are a few extra methods for getting object by custom permission. By default all 'get' and 'find' methods are using the 'read' permission.

	T findOne(ID id, String permission);
	T getOne(ID id, String permission);
	List<T> findAll(String permission);
	T findOne(Specification<T> spec, String permission);
	List<T> findAll(Iterable<ID> ids, String permission);


E.g. you can create a special controller for getting all the scripts which the current user has 'execute' permission to.
Unfortunately these kind of queries cannot be created automatically via the Spring Repositories. 

## Spring Data Rest Webmvc

If you want to create a restful API using Spring's data-rest module all you have to do is include the proper spring package. All of the rest endpoints will be automatically secured by the ACL.
Even when you try to create or modify an object with associations, only the links that points to object you have permission to will be created. So if somebody tries to manually create a http request for adding a link to a document then it will be added only if the given user has at least read permission to the given document, because each objects are loaded via the proper repository method and each of these methods are secured by the ACL.

The same rules apply to all properties. So even if you have "read" access to one of your fellow group-members, if you try to access all of his documents using

	GET /people/23/documents
	
You will see only those of his documents what you have permission to via some way. (Eg. you can see the documents he shared with your WorkGroups, but you cannot see the documents he shared with other WorkGroups where you are not a member)

## Failed permission checks

If a permission check fails during a 'get', 'save' or 'delete' method the result is a EntityNotFoundException. If it happens during accessing a Spring Data-Rest endpoint the result will be a 404 ('not found') status code. It's 404 and NOT 'forbidden' because we don't want to reveal even the existence of those objects which the current user has no access to.

If a permission check fails during a 'find' method the result is `null`.

If a permission check fails during a 'findAll' method only the elements with appropriate permissions will be returned. The elements without proper permissions will be silently skipped.  

The same rules are true for properties and property-collections but only if you try to access them via Spring Data Rest endpoints.

	/users
	
... return all users you have read permission to.

	/users/12

... return user with id '12' if it exists AND you have read permission to that user otherwise 404.

	/users/12/documents
	
... return all of the documents of user 12 you have permission to them IF you also have read permission to user 12.

	/users/12/documents/123
	
... return document 123 if you have permission to document 123 AND to user 12.

WARNING!

These automatic permission checks for properties and property-collections are only performed during accessing the Data-Rest endpoints. If you call a 

	userRepository.findOne(12) 

repository method directly from your code then the Acl will check the current user's permission to user 12 but if you try to access any properties of that object later NO ANY PERMISSION CHECK will be performed. ACL affects only repository methods but not the getters and setters of the potentially protected properties and collections of that object.

So if you have to create a specific controller to manipulate protected properties of an object, use repository methods to access those properties.
Let's say you want to create an endpoint where you can send a message to all your friends who are members of a certain group. (The members of that group you have 'read' permission to ) You should implement it like this:

	@PostMapping("/groups/{group}/message")
	public ResponseEntity<String> sendMessageToMembers(@PathVariable Group group @RequestBody Message message) {
	
		sendMessage(message, userRepository.findByGroups(group));
	}

If you send the message this way:

		sendMessage(message, group.getMembers());
	 	
... then all the members of that group will get the message not only your friends.

# Other features

## @AclSearchable

A flexible search system is a common requirement in modern clients. So if a user types in a text into the search user field he expects to find all the users which have that pattern in their first-name, last-name or email-address or even in their address.
Such features are usually implemented by using external indexes like elasticsearch. It can be used together with the ACL too: When the actual objects are loaded from the DB by id, all the objects without proper permissions will be filtered out.

However if you want to create a paginated search result using ACl - that's impossible.
Also, if you run the server on multiple instances you need some extra caution if you want to use external indexes.
And sometimes integrating the search system itself is a very complicated and painful process.

_... and that's exactly the reason why..._   

The ACl has an inbuilt - simple but quite effective - search functionality. It's not suitable for excessive usage, so if you want to provide a full-scale text search for hundreds of thousands of users, then you should consider to implement some 3rd party indexed search system. But if you want to provide the search feature for only administrators or power users or you have only a few hundreds of users or you simply doesn't expect more than a couple of searches per second then you can turn on the full-text-search feature within seconds. Literally.

All you have to do is annotating all of the searchable properties of your entities with the `@AclSeachable` annotation. That's all.
(Collections, numbers, associations or any properties which are not stored as characters are cannot be annotated)

All the collection and properties endpoints and also all the exported repository-methods which have `Sort` or `Pageable` arguments will also accept a `search` parameter from now, like

	/groups/12/users?search=foo bar
	
The result will contain only the users where all the words in the search pattern (`foo` and `bar`) appear at least once in any of the annotated properties of the user domain class. It doesn't only filter the result but also order the result set by relevance. Objects have higher precedence if

- A word appears closer to the start of the property. (`bart` has priority over `qbar`)
- A word appears in multiple properties. (eg. in first-name and email address)

On top of that the longer words and the words in the front of the list also get some extra priority.

Even if those rules sound confusing just test it: It has a surprisingly good result.

If there are both `search` and `sort` parameters in the URL then the `search` parameter has precedence. (Because the the search feature not only filters the results but also automatically sort them by relevance.) 

Although this functionality is present primary for the REST endpoints, you can also use it internally. You can pass a `Search` object for any repository methods which accept `Sort` or `Pageable` objects. The `Search` class is a subclass of `Sort` and `Pageable` also contains a `Sort` property.

Because of performance reasons the search logic handles only the first three words of the pattern. You can change it with the following property in your application.properties file:
	
	spring.data.jpa.acl.max-search-words=3

## Complement endpoints

Let's assume we have a Group entity with a members property. It has a 'members' property which is a collection of Users annotated with `@ManyToMany` annotation.
The following request returns all the users who are members of a given group:
`GET groups/12/members`
If we want to create a state-of-the-art client for this API, then when a user want to add more users to a given group via the client, it would be nice if we could show all the available users who are NOT members of the given group, so the user can choose the new members from this shortened list instead of all the users.
The following request returns with these users:
`GET groups/12/membersComplement`

This request could be also paginated and searchable, so all the extra parameters can be used just like on a normal property endpoint.

`GET groups/12/membersComplement?page=2&size=5&search=mike`

And the result set also will be filtered by the ACl - just like any other endpoints -, so it will contain only objects which the current user has permission to.

This feature can be used on any collection-like property of any ACL-managed entity without any additional settings.
 
## Missing features

Unfortunately the `@DataJpaTest` annotation which can be used for testing the JpaRepositories cannot be used together with this extension.
If you have test classes in an existing project where this annotation is in use, then either turn off those tests by adding @Ignore or test the repository methods in a full context. 

## Limitations

There are some limitations if you want to use the ACL:

- All domain entities must have a singular id attribute. If you really need composite primary key somewhere, then a possible workaround is using @EmbeddedId annotation. (Theoretically this issue could be resolved, but I rather spend my resources to add other functionalities and improvements.)
- All ACl managed queries are forced to be distinct. At the moment I have no idea how can I resolve this issue. 
- Search functionality doesn't work with unique queries (defined by `@Query` annotation).
- ACL and pagination is not working on maps (They are treated as common properties, not as collections).

## Conclusion

Maybe this document is quite long and complex but creating a safe permission-system for your application what covers all of the use cases is still much more difficult than understanding the proper usage of these annotations. And your final code will be much more clear if you can handle everything by including an extra dependency and adding a few annotations here or there.


