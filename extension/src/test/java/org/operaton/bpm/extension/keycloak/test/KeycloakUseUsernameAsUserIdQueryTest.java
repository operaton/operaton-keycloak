package org.operaton.bpm.extension.keycloak.test;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User query test for the Keycloak identity provider.
 * Flag useUsernameAsOperatonUserId enabled.
 */
class KeycloakUseUsernameAsUserIdQueryTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.useUsernameAsOperatonUserId.cfg.xml");

  static List<String> USER_IDS = new ArrayList<>();

  @BeforeAll
  static void setupSpecialTestUsers() throws JSONException {
    HttpHeaders headers = authenticateKeycloakAdmin();
    String realm = TEST_REALM;
    USER_IDS.add(createUser(headers, realm, "hans.wurst", null, null, null, null));
  }

  @AfterAll
  static void deleteSpecialTestUsers() throws JSONException {
    deleteUsersAndGroups(USER_IDS, List.of());
  }

  // ------------------------------------------------------------------------
  // Authorization tests
  // ------------------------------------------------------------------------

  @Test
  void keycloakLoginSuccess() {
    assertThat(identityService.checkPassword("operaton", "operaton1!")).isTrue();
  }

  // ------------------------------------------------------------------------
  // User Query tests
  // ------------------------------------------------------------------------

  @Test
  void userQueryFilterByUserId() {
    User user = identityService.createUserQuery().userId("hans.mustermann").singleResult();
    assertThat(user).isNotNull();

    user = identityService.createUserQuery().userId("operaton").singleResult();
    assertThat(user).isNotNull();

    // validate user
    assertThat(user.getId()).isEqualTo("operaton");
    assertThat(user.getFirstName()).isEqualTo("Admin");
    assertThat(user.getLastName()).isEqualTo("Operaton");
    assertThat(user.getEmail()).isEqualTo("operaton@accso.de");

    user = identityService.createUserQuery().userId("non-existing").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void userQueryFilterByUserIdIn() {
    List<User> users = identityService.createUserQuery().userIdIn("operaton", "hans.mustermann").list();
    assertThat(users).isNotNull();
    assertThat(users).hasSize(2);

    users = identityService.createUserQuery().userIdIn("operaton", "non-existing").list();
    assertThat(users).isNotNull();
    assertThat(users).hasSize(1);
  }

  @Test
  void userQueryFilterByEmail() {
    User user = identityService.createUserQuery().userEmail("operaton@accso.de").singleResult();
    assertThat(user).isNotNull();

    // validate user
    assertThat(user.getId()).isEqualTo("operaton");
    assertThat(user.getFirstName()).isEqualTo("Admin");
    assertThat(user.getLastName()).isEqualTo("Operaton");
    assertThat(user.getEmail()).isEqualTo("operaton@accso.de");

    user = identityService.createUserQuery().userEmail("non-exist*").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void userQueryFilterByNonExistingAttributeLike() {
    // hans.wurst has no other attributes than his username set
    User user = identityService.createUserQuery().userId("hans.wurst").userEmailLike("*").singleResult();
    assertThat(user).isNotNull();
    user = identityService.createUserQuery().userId("hans.wurst").userEmailLike("operaton*").singleResult();
    assertThat(user).isNull();
    user = identityService.createUserQuery().userId("hans.wurst").userFirstNameLike("*").singleResult();
    assertThat(user).isNotNull();
    user = identityService.createUserQuery().userId("hans.wurst").userFirstNameLike("operaton*").singleResult();
    assertThat(user).isNull();
    user = identityService.createUserQuery().userId("hans.wurst").userLastNameLike("*").singleResult();
    assertThat(user).isNotNull();
    user = identityService.createUserQuery().userId("hans.wurst").userLastNameLike("operaton*").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void userQueryFilterByGroupIdAndId() {
    List<User> result = identityService.createUserQuery().memberOfGroup(groupIdAdmin).userId("operaton").list();
    assertThat(result).hasSize(1);

    result = identityService.createUserQuery().memberOfGroup(groupIdAdmin).userId("non-exist").list();
    assertThat(result).hasSize(0);

    result = identityService.createUserQuery().memberOfGroup("non-exist").userId("operaton").list();
    assertThat(result).hasSize(0);

  }

  @Test
  void authenticatedUserSeesHimself() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);

      identityService.setAuthenticatedUserId("non-existing");
      assertThat(identityService.createUserQuery().count()).isZero();

      identityService.setAuthenticatedUserId("operaton");
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
    List<Group> result = identityService.createGroupQuery().groupMember("operaton").list();
    assertThat(result).hasSize(1);

    result = identityService.createGroupQuery().groupMember("non-exist").list();
    assertThat(result).hasSize(0);
  }

  @Test
  void filterByGroupIdAndUserId() {
    Group group = identityService.createGroupQuery().groupId(groupIdAdmin).groupMember("operaton").singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");

    group = identityService.createGroupQuery().groupId("non-exist").groupMember("operaton").singleResult();
    assertThat(group).isNull();

    group = identityService.createGroupQuery().groupId(groupIdAdmin).groupMember("non-exist").singleResult();
    assertThat(group).isNull();
  }

  @Test
  void filterByGroupIdInAndUserId() {
    Group group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdTeamlead)
        .groupMember("operaton")
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");

    group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdTeamlead)
        .groupMember("non-exist")
        .singleResult();
    assertThat(group).isNull();
  }

  @Test
  void groupQueryFilterByUserIdSimilarToClientName() {
    Group group = identityService.createGroupQuery().groupMember("operaton-identity-service").singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getId()).isEqualTo(groupIdSimilarClientName);
    assertThat(group.getName()).isEqualTo("operaton-identity-service");
  }
}
