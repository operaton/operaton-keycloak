package org.operaton.bpm.extension.keycloak.test;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests queries with focus on nested group hierarchy.
 */
class KeycloakNestedGroupsQueryTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.cfg.xml");

  @Test
  void groupQueryFilterByGroupIdRoot() {
    Group group = identityService.createGroupQuery().groupId(groupIdHierarchyRoot).singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getId()).isEqualTo(groupIdHierarchyRoot);
    assertThat(group.getName()).isEqualTo("root");
  }

  @Test
  void groupQueryFilterByGroupIdChild() {
    Group group = identityService.createGroupQuery().groupId(groupIdHierarchyChild1).singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getId()).isEqualTo(groupIdHierarchyChild1);
    assertThat(group.getName()).isEqualTo("child1");
  }

  @Test
  void groupQueryFilterByGroupIdSubChild() {
    Group group = identityService.createGroupQuery().groupId(groupIdHierarchySubchild1).singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getId()).isEqualTo(groupIdHierarchySubchild1);
    assertThat(group.getName()).isEqualTo("subchild1");
  }

  @Test
  void groupQueryFilterByUserId() {
    List<Group> result = identityService.createGroupQuery().groupMember("johnfoo@gmail.com").list();
    assertThat(result).hasSize(2);
    assertThat(result.stream().filter(g -> "child2".equals(g.getName())).count()).as("expected johnfoo@gmail.com to member of group child2").isOne();
    assertThat(result.stream().filter(g -> "subchild1".equals(g.getName())).count()).as("expected johnfoo@gmail.com to member of group subchild1").isOne();
  }

  @Test
  void groupQueryFilterByGroupIdIn() {
    List<Group> groups = identityService.createGroupQuery().groupIdIn(groupIdAdmin, groupIdHierarchyChild1).list();
    assertThat(groups).hasSize(2);
    assertThat(groups.stream().filter(g -> "operaton-admin".equals(g.getName())).count()).as("operaton-admin not found").isOne();
    assertThat(groups.stream().filter(g -> "child1".equals(g.getName())).count()).as("child1 not found").isOne();
  }

  @Test
  void groupQueryFilterByGroupIdInAndUserId() {
    Group group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdManager, groupIdHierarchyChild2, groupIdHierarchyChild1)
        .groupMember("johnfoo@gmail.com")
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("child2");
  }

  @Test
  void groupQueryFilterByGroupName() {
    Group group = identityService.createGroupQuery().groupName("child1").singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getId()).isEqualTo(groupIdHierarchyChild1);
    assertThat(group.getName()).isEqualTo("child1");
  }

  @Test
  void groupQueryFilterByGroupNameLike() {
    List<Group> result = identityService.createGroupQuery().groupNameLike("child*").list();
    assertThat(result).hasSize(2);
    assertThat(result.stream().filter(g -> "child1".equals(g.getName())).count()).as("expected group child1 to be included").isOne();
    assertThat(result.stream().filter(g -> "child2".equals(g.getName())).count()).as("expected group child2 to be included").isOne();

    result = identityService.createGroupQuery().groupNameLike("*child*").list();
    assertThat(result).hasSize(3);
    assertThat(result.stream().filter(g -> "child1".equals(g.getName())).count()).as("expected group child1 to be included").isOne();
    assertThat(result.stream().filter(g -> "child2".equals(g.getName())).count()).as("expected group child2 to be included").isOne();
    assertThat(result.stream().filter(g -> "subchild1".equals(g.getName())).count()).as("expected group subchild1 to be included").isOne();
  }

  @Test
  void groupQueryFilterByGroupNameAndGroupNameLike() {
    Group group = identityService.createGroupQuery().groupNameLike("child*").groupName("child2").singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getId()).isEqualTo(groupIdHierarchyChild2);
    assertThat(group.getName()).isEqualTo("child2");
  }

  @Test
  void groupQueryFilterByGroupMember() {
    List<Group> result = identityService.createGroupQuery().groupMember("johnfoo@gmail.com").list();
    assertThat(result).hasSize(2);
    assertThat(result.stream().filter(g -> "child2".equals(g.getName())).count()).as("expected johnfoo@gmail.com to member of group child2").isOne();
    assertThat(result.stream().filter(g -> "subchild1".equals(g.getName())).count()).as("expected johnfoo@gmail.com to member of group subchild1").isOne();
  }

  @Test
  void userQueryFilterByMemberOfGroup() {
    User user = identityService.createUserQuery().memberOfGroup(groupIdHierarchySubchild1).singleResult();
    assertThat(user).isNotNull();
    assertThat(user.getId()).isEqualTo("johnfoo@gmail.com");

    user = identityService.createUserQuery().memberOfGroup(groupIdHierarchyChild2).singleResult();
    assertThat(user).isNotNull();
    assertThat(user.getId()).isEqualTo("johnfoo@gmail.com");

    user = identityService.createUserQuery().memberOfGroup(groupIdHierarchyChild1).singleResult();
    assertThat(user).isNull();
  }

}
