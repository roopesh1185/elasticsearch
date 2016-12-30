/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.authz;

import org.elasticsearch.Version;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.security.authz.RoleDescriptor.IndicesPrivileges;
import org.elasticsearch.xpack.security.authz.permission.FieldPermissionsCache;
import org.elasticsearch.xpack.security.authz.permission.Role;
import org.elasticsearch.xpack.security.authz.privilege.IndexPrivilege;
import org.elasticsearch.xpack.security.authz.store.CompositeRolesStore;
import org.elasticsearch.xpack.security.user.User;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;

public class AuthorizedIndicesTests extends ESTestCase {

    public void testAuthorizedIndicesUserWithoutRoles() {
        User user = new User("test user");
        AuthorizedIndices authorizedIndices = new AuthorizedIndices(user, Role.EMPTY, "",
                MetaData.EMPTY_META_DATA);
        List<String> list = authorizedIndices.get();
        assertTrue(list.isEmpty());
    }

    public void testAuthorizedIndicesUserWithSomeRoles() {
        User user = new User("test user", "a_star", "b");
        RoleDescriptor aStarRole = new RoleDescriptor("a_star", null,
                new IndicesPrivileges[] { IndicesPrivileges.builder().indices("a*").privileges("all").build() }, null);
        RoleDescriptor bRole = new RoleDescriptor("b", null,
                new IndicesPrivileges[] { IndicesPrivileges.builder().indices("b").privileges("READ").build() }, null);
        Settings indexSettings = Settings.builder().put("index.version.created", Version.CURRENT).build();
        MetaData metaData = MetaData.builder()
                .put(new IndexMetaData.Builder("a1").settings(indexSettings).numberOfShards(1).numberOfReplicas(0).build(), true)
                .put(new IndexMetaData.Builder("a2").settings(indexSettings).numberOfShards(1).numberOfReplicas(0).build(), true)
                .put(new IndexMetaData.Builder("aaaaaa").settings(indexSettings).numberOfShards(1).numberOfReplicas(0).build(), true)
                .put(new IndexMetaData.Builder("bbbbb").settings(indexSettings).numberOfShards(1).numberOfReplicas(0).build(), true)
                .put(new IndexMetaData.Builder("b")
                        .settings(indexSettings)
                        .numberOfShards(1)
                        .numberOfReplicas(0)
                        .putAlias(new AliasMetaData.Builder("ab").build())
                        .putAlias(new AliasMetaData.Builder("ba").build())
                        .build(), true)
                .build();
        Role roles =
                CompositeRolesStore.buildRoleFromDescriptors(Sets.newHashSet(aStarRole, bRole), new FieldPermissionsCache(Settings.EMPTY));
        AuthorizedIndices authorizedIndices = new AuthorizedIndices(user, roles, SearchAction.NAME, metaData);
        List<String> list = authorizedIndices.get();
        assertThat(list, containsInAnyOrder("a1", "a2", "aaaaaa", "b", "ab"));
        assertFalse(list.contains("bbbbb"));
        assertFalse(list.contains("ba"));
    }

    public void testAuthorizedIndicesUserWithSomeRolesEmptyMetaData() {
        User user = new User("test user", "role");
        Role role = Role.builder("role").add(IndexPrivilege.ALL, "*").build();
        AuthorizedIndices authorizedIndices = new AuthorizedIndices(user, role, SearchAction.NAME, MetaData.EMPTY_META_DATA);
        List<String> list = authorizedIndices.get();
        assertTrue(list.isEmpty());
    }
}
