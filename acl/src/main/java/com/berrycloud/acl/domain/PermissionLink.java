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
package com.berrycloud.acl.domain;

import com.berrycloud.acl.annotation.AclOwner;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

/**
 * A superclass for defining permission-links between a permission-owner and a permission-target entity.
 * <p>
 * A permission link basically a manually handled many-to-many association between a Permission-Owner and a
 * Permission-target entity what also defines the type of the permission. The permission is a simple String
 * representation with two extra rules:
 * <ol>
 * <li>Any permission string automatically defines a {@code "read"} permission too. (If a user has any kind of
 * permission to an object that logically means that the user can also read that object.)</li>
 * <li>{@code "all"} permission automatically grants all of the possible permissions.</li>
 * </ol>
 *
 * @param <O> Permission-Owner: should be a class what implements the {@link AclUser} interface or any domain class what
 *            have a {@link AclUser} field or collection-like field containing {@link AclUser} entities.
 * @param <T> Permission-Target: could be any domain class.
 * @author István Rátkai (Selindek)
 */
@MappedSuperclass
public abstract class PermissionLink<O, T> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @AclOwner
    @ManyToOne()
    @JoinColumn(nullable = false, updatable = false)
    private O owner;

    @ManyToOne()
    @JoinColumn(nullable = false, updatable = false)
    private T target;

    @Column(nullable = false, updatable = false)
    private String permission;

    public PermissionLink() {
    }

    public PermissionLink(Long id) {
        this.id = id;
    }

    public PermissionLink(O owner, T target, String permission) {
        this.owner = owner;
        this.target = target;
        this.permission = permission;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public O getOwner() {
        return owner;
    }

    public void setOwner(final O owner) {
        this.owner = owner;
    }

    public T getTarget() {
        return target;
    }

    public void setTarget(final T target) {
        this.target = target;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(final String permission) {
        this.permission = permission;
    }
}
