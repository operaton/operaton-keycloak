package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;

/**
 * Tests queries with focus on nested group hierarchy.
 */
public class KeycloakNestedGroupsQueryTest extends AbstractKeycloakIdentityProviderTest {

  public void testGroupQueryFilterByGroupIdRoot() {
    Group group = identityService.createGroupQuery().groupId(groupIdHierarchyRoot).singleResult();
    assertNotNull(group);
    assertEquals(groupIdHierarchyRoot, group.getId());
    assertEquals("root", group.getName());
  }

  public void testGroupQueryFilterByGroupIdChild() {
    Group group = identityService.createGroupQuery().groupId(groupIdHierarchyChild1).singleResult();
    assertNotNull(group);
    assertEquals(groupIdHierarchyChild1, group.getId());
    assertEquals("child1", group.getName());
  }

  public void testGroupQueryFilterByGroupIdSubChild() {
    Group group = identityService.createGroupQuery().groupId(groupIdHierarchySubchild1).singleResult();
    assertNotNull(group);
    assertEquals(groupIdHierarchySubchild1, group.getId());
    assertEquals("subchild1", group.getName());
  }

  public void testGroupQueryFilterByUserId() {
    List<Group> result = identityService.createGroupQuery().groupMember("johnfoo@gmail.com").list();
    assertEquals(2, result.size());
    assertEquals("expected johnfoo@gmail.com to member of group child2", 1,
        result.stream().filter(g -> "child2".equals(g.getName())).count());
    assertEquals("expected johnfoo@gmail.com to member of group subchild1", 1,
        result.stream().filter(g -> "subchild1".equals(g.getName())).count());
  }

  public void testGroupQueryFilterByGroupIdIn() {
    List<Group> groups = identityService.createGroupQuery().groupIdIn(groupIdAdmin, groupIdHierarchyChild1).list();
    assertEquals(2, groups.size());
    assertEquals("operaton-admin not found", 1,
        groups.stream().filter(g -> "operaton-admin".equals(g.getName())).count());
    assertEquals("child1 not found", 1, groups.stream().filter(g -> "child1".equals(g.getName())).count());
  }

  public void testGroupQueryFilterByGroupIdInAndUserId() {
    Group group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdManager, groupIdHierarchyChild2, groupIdHierarchyChild1)
        .groupMember("johnfoo@gmail.com")
        .singleResult();
    assertNotNull(group);
    assertEquals("child2", group.getName());
  }

  public void testGroupQueryFilterByGroupName() {
    Group group = identityService.createGroupQuery().groupName("child1").singleResult();
    assertNotNull(group);
    assertEquals(groupIdHierarchyChild1, group.getId());
    assertEquals("child1", group.getName());
  }

  public void testGroupQueryFilterByGroupNameLike() {
    List<Group> result = identityService.createGroupQuery().groupNameLike("child*").list();
    assertEquals(2, result.size());
    assertEquals("expected group child1 to be included", 1,
        result.stream().filter(g -> "child1".equals(g.getName())).count());
    assertEquals("expected group child2 to be included", 1,
        result.stream().filter(g -> "child2".equals(g.getName())).count());

    result = identityService.createGroupQuery().groupNameLike("*child*").list();
    assertEquals(3, result.size());
    assertEquals("expected group child1 to be included", 1,
        result.stream().filter(g -> "child1".equals(g.getName())).count());
    assertEquals("expected group child2 to be included", 1,
        result.stream().filter(g -> "child2".equals(g.getName())).count());
    assertEquals("expected group subchild1 to be included", 1,
        result.stream().filter(g -> "subchild1".equals(g.getName())).count());
  }

  public void testGroupQueryFilterByGroupNameAndGroupNameLike() {
    Group group = identityService.createGroupQuery().groupNameLike("child*").groupName("child2").singleResult();
    assertNotNull(group);
    assertEquals(groupIdHierarchyChild2, group.getId());
    assertEquals("child2", group.getName());
  }

  public void testGroupQueryFilterByGroupMember() {
    List<Group> result = identityService.createGroupQuery().groupMember("johnfoo@gmail.com").list();
    assertEquals(2, result.size());
    assertEquals("expected johnfoo@gmail.com to member of group child2", 1,
        result.stream().filter(g -> "child2".equals(g.getName())).count());
    assertEquals("expected johnfoo@gmail.com to member of group subchild1", 1,
        result.stream().filter(g -> "subchild1".equals(g.getName())).count());
  }

  public void testUserQueryFilterByMemberOfGroup() {
    User user = identityService.createUserQuery().memberOfGroup(groupIdHierarchySubchild1).singleResult();
    assertNotNull(user);
    assertEquals("johnfoo@gmail.com", user.getId());

    user = identityService.createUserQuery().memberOfGroup(groupIdHierarchyChild2).singleResult();
    assertNotNull(user);
    assertEquals("johnfoo@gmail.com", user.getId());

    user = identityService.createUserQuery().memberOfGroup(groupIdHierarchyChild1).singleResult();
    assertNull(user);
  }

}
