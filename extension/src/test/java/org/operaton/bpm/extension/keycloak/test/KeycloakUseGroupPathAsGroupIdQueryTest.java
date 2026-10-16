package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Group query test for the Keycloak identity provider.
 * Flag useGroupPathAsOperatonGroupId enabled.
 */
class KeycloakUseGroupPathAsGroupIdQueryTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.useGroupPathAsOperatonGroupId.cfg.xml");

  // ------------------------------------------------------------------------
  // Group Query tests
  // ------------------------------------------------------------------------

  @Test
  void filterByGroupId() {
    Group group = identityService.createGroupQuery().groupId("operaton-admin").singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo("operaton-admin");
    assertThat(group.getName()).isEqualTo("operaton-admin");
    assertThat(group.getType()).isEqualTo("SYSTEM");

    group = identityService.createGroupQuery().groupId("whatever").singleResult();
    assertThat(group).isNull();
  }

  @Test
  void filterByChildGroupId() {
    Group group = identityService.createGroupQuery().groupId("root/child1").singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo("root/child1");
    assertThat(group.getName()).isEqualTo("child1");
  }

  @Test
  void filterByUserId() {
    List<Group> result = identityService.createGroupQuery().groupMember("operaton@accso.de").list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterByUserIdMemberOfChildGroups() {
    List<Group> groups = identityService.createGroupQuery().groupMember("johnfoo@gmail.com").list();
    assertThat(groups).hasSize(2);

    for (Group group : groups) {
      if (!"root/child1/subchild1".equals(group.getId()) && !"root/child2".equals(group.getId())) {
        fail("");
      }
    }

  }

  @Test
  void filterByGroupTypeAndGroupId() {
    Group group = identityService.createGroupQuery().groupType("SYSTEM").groupId("cam-read-only").singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo("cam-read-only");
    assertThat(group.getName()).isEqualTo("cam-read-only");
    assertThat(group.getType()).isEqualTo("SYSTEM");
  }

  @Test
  void filterByGroupIdIn() {
    List<Group> groups = identityService.createGroupQuery().groupIdIn("operaton-admin", "manager").list();

    assertThat(groups).hasSize(2);
    for (Group group : groups) {
      if (!"operaton-admin".equals(group.getName()) && !"manager".equals(group.getName())) {
        fail("");
      }
    }
  }

  @Test
  void filterByChildGroupIdIn() {
    List<Group> groups = identityService.createGroupQuery().groupIdIn("root/child1/subchild1", "root/child2").list();

    assertThat(groups).hasSize(2);
    for (Group group : groups) {
      if (!"root/child1/subchild1".equals(group.getId()) && !"root/child2".equals(group.getId())) {
        fail("");
      }
    }
  }

  @Test
  void filterByGroupIdInAndType() {
    Group group = identityService.createGroupQuery()
        .groupIdIn("operaton-admin", "manager")
        .groupType("WORKFLOW")
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("manager");

    group = identityService.createGroupQuery()
        .groupIdIn("operaton-admin", "manager")
        .groupType("SYSTEM")
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");
  }

  @Test
  void filterByGroupIdInAndUserId() {
    Group group = identityService.createGroupQuery()
        .groupIdIn("operaton-admin", "manager")
        .groupMember("operaton@accso.de")
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");
  }

  @Test
  void filterByGroupName() {
    Group group = identityService.createGroupQuery().groupName("manager").singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo("manager");
    assertThat(group.getName()).isEqualTo("manager");

    group = identityService.createGroupQuery().groupName("whatever").singleResult();
    assertThat(group).isNull();
  }

  @Test
  void filterByGroupNameLike() {
    Group group = identityService.createGroupQuery().groupNameLike("manage*").singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo("manager");
    assertThat(group.getName()).isEqualTo("manager");

    group = identityService.createGroupQuery().groupNameLike("what*").singleResult();
    assertThat(group).isNull();
  }

  @Test
  void filterByGroupNameAndGroupNameLike() {
    Group group = identityService.createGroupQuery().groupNameLike("ma*").groupName("manager").singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo("manager");
    assertThat(group.getName()).isEqualTo("manager");
  }

  @Test
  void filterByGroupMember() {
    List<Group> list = identityService.createGroupQuery().groupMember("operaton@accso.de").list();
    assertThat(list).hasSize(1);
    list = identityService.createGroupQuery().groupMember("Gunnar.von-der-Beck@accso.de").list();
    assertThat(list).hasSize(2);
    list = identityService.createGroupQuery().groupMember("hans.mustermann@tradermail.info").list();
    assertThat(list).hasSize(1);
    list = identityService.createGroupQuery().groupMember("non-existing").list();
    assertThat(list).isEmpty();
  }

  @Test
  void orderByGroupId() {
    List<Group> groupList = identityService.createGroupQuery().orderByGroupId().desc().list();
    assertThat(groupList).hasSize(9);
    assertThat(groupList.get(0).getId().compareTo(groupList.get(1).getId())).isGreaterThan(0);
    assertThat(groupList.get(1).getId().compareTo(groupList.get(2).getId())).isGreaterThan(0);
    assertThat(groupList.get(2).getId().compareTo(groupList.get(3).getId())).isGreaterThan(0);
    assertThat(groupList.get(5).getId().compareTo(groupList.get(6).getId())).isGreaterThan(0);
    assertThat(groupList.get(6).getId().compareTo(groupList.get(7).getId())).isGreaterThan(0);
  }

  @Test
  void orderByGroupName() {
    List<Group> groupList = identityService.createGroupQuery().orderByGroupName().list();
    assertThat(groupList).hasSize(9);
    assertThat(groupList.get(0).getName().compareTo(groupList.get(1).getName())).isLessThan(0);
    assertThat(groupList.get(1).getName().compareTo(groupList.get(2).getName())).isLessThan(0);
    assertThat(groupList.get(2).getName().compareTo(groupList.get(3).getName())).isLessThan(0);
    assertThat(groupList.get(5).getName().compareTo(groupList.get(6).getName())).isLessThan(0);
    assertThat(groupList.get(6).getName().compareTo(groupList.get(7).getName())).isLessThan(0);
  }

  // ------------------------------------------------------------------------
  // User Query tests
  // ------------------------------------------------------------------------

  @Test
  void filterUserByGroupId() {
    List<User> result = identityService.createUserQuery().memberOfGroup("teamlead").list();
    assertThat(result).hasSize(2);

    result = identityService.createUserQuery().memberOfGroup("non-exist").list();
    assertThat(result).isEmpty();
  }

  @Test
  void filterUserByChildGroupId() {
    List<User> result = identityService.createUserQuery().memberOfGroup("root/child1/subchild1").list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterUserByGroupIdAndFirstname() {
    List<User> result = identityService.createUserQuery().memberOfGroup("teamlead").userFirstName("Gunnar").list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterUserByGroupIdAndId() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup("teamlead")
        .userId("gunnar.von-der-beck@accso.de")
        .list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterUserByChildGroupIdAndId() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup("root/child1/subchild1")
        .userId("johnfoo@gmail.com")
        .list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterUserByGroupIdAndLastname() {
    List<User> result = identityService.createUserQuery().memberOfGroup("teamlead").userLastName("von der Beck").list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterUserByGroupIdAndEmail() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup("teamlead")
        .userEmail("gunnar.von-der-beck@accso.de")
        .list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterUserByGroupIdAndEmailLike() {
    List<User> result = identityService.createUserQuery().memberOfGroup("teamlead").userEmailLike("*@accso.de").list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterUserByGroupIdSimilarToClientName() {
    User user = identityService.createUserQuery().memberOfGroup("operaton-identity-service").singleResult();
    assertThat(user).isNotNull();
    assertThat(user.getId()).isEqualTo("identity.service@test.de");
  }
}
