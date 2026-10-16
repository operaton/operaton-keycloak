package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User query test for the Keycloak identity provider.
 * Flags useEmailAsOperatonUserId and useUsernameAsOperatonUserId disabled.
 */
class KeycloakUseKeycloakIdAsUserIdQueryTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.useKeycloakIdAsOperatonUserId.cfg.xml");

  // ------------------------------------------------------------------------
  // Authorization tests
  // ------------------------------------------------------------------------

  @Test
  void keycloakLoginSuccess() {
    assertThat(identityService.checkPassword(userIdOperatonAdmin, "operaton1!")).isTrue();
  }

  // ------------------------------------------------------------------------
  // User Query tests
  // ------------------------------------------------------------------------

  @Test
  void userQueryFilterByUserId() {
    User user = identityService.createUserQuery().userId(userIdTeamlead).singleResult();
    assertThat(user).isNotNull();

    user = identityService.createUserQuery().userId(userIdOperatonAdmin).singleResult();
    assertThat(user).isNotNull();

    // validate user
    assertThat(user.getId()).isEqualTo(userIdOperatonAdmin);
    assertThat(user.getFirstName()).isEqualTo("Admin");
    assertThat(user.getLastName()).isEqualTo("Operaton");
    assertThat(user.getEmail()).isEqualTo("operaton@accso.de");

    user = identityService.createUserQuery().userId("non-existing").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void userQueryFilterByUserIdIn() {
    List<User> users = identityService.createUserQuery().userIdIn(userIdOperatonAdmin, userIdTeamlead).list();
    assertThat(users).isNotNull();
    assertThat(users).hasSize(2);

    users = identityService.createUserQuery().userIdIn(userIdOperatonAdmin, "non-existing").list();
    assertThat(users).isNotNull();
    assertThat(users).hasSize(1);
  }

  @Test
  void userQueryFilterByEmail() {
    User user = identityService.createUserQuery().userEmail("operaton@accso.de").singleResult();
    assertThat(user).isNotNull();

    // validate user
    assertThat(user.getId()).isEqualTo(userIdOperatonAdmin);
    assertThat(user.getFirstName()).isEqualTo("Admin");
    assertThat(user.getLastName()).isEqualTo("Operaton");
    assertThat(user.getEmail()).isEqualTo("operaton@accso.de");

    user = identityService.createUserQuery().userEmail("non-exist*").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void userQueryFilterByGroupIdAndId() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(groupIdAdmin)
        .userId(userIdOperatonAdmin)
        .list();
    assertThat(result).hasSize(1);

    result = identityService.createUserQuery().memberOfGroup(groupIdAdmin).userId("non-exist").list();
    assertThat(result).hasSize(0);

    result = identityService.createUserQuery().memberOfGroup("non-exist").userId(userIdOperatonAdmin).list();
    assertThat(result).hasSize(0);

  }

  @Test
  void authenticatedUserSeesHimself() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);

      identityService.setAuthenticatedUserId("non-existing");
      assertThat(identityService.createUserQuery().count()).isZero();

      identityService.setAuthenticatedUserId(userIdOperatonAdmin);
      assertThat(identityService.createUserQuery().count()).isOne();

    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

  // ------------------------------------------------------------------------
  // Group query tests
  // ------------------------------------------------------------------------

  @Test
  void groupQueryFilterByUserId() {
    List<Group> result = identityService.createGroupQuery().groupMember(userIdOperatonAdmin).list();
    assertThat(result).hasSize(1);

    result = identityService.createGroupQuery().groupMember("non-exist").list();
    assertThat(result).hasSize(0);
  }

  @Test
  void filterByGroupIdAndUserId() {
    Group group = identityService.createGroupQuery()
        .groupId(groupIdAdmin)
        .groupMember(userIdOperatonAdmin)
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");

    group = identityService.createGroupQuery().groupId("non-exist").groupMember(userIdOperatonAdmin).singleResult();
    assertThat(group).isNull();

    group = identityService.createGroupQuery().groupId(groupIdAdmin).groupMember("non-exist").singleResult();
    assertThat(group).isNull();
  }

  @Test
  void filterByGroupIdInAndUserId() {
    Group group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdTeamlead)
        .groupMember(userIdOperatonAdmin)
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");

    group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdTeamlead)
        .groupMember("non-exist")
        .singleResult();
    assertThat(group).isNull();
  }
}
