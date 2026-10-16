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
 * Keycloak mass data test checking the default maxResultSize configuration parameter (which is currently 250).
 * Will detect errors in applying the max parameter to Keyclaok REST API search queries as the Keycloak default is only 100.
 */
class KeycloakMassDataTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.configureHigherMaxResultSize.cfg.xml",
      plugin -> plugin.setAdministratorUserId(userIdOperatonAdmin));

  static List<String> USER_IDS = new ArrayList<>();
  static List<String> GROUP_IDS = new ArrayList<>();

  @BeforeAll
  static void setupMassData() throws JSONException {
    HttpHeaders headers = authenticateKeycloakAdmin();
    String realm = "test";
    for (int i = 0; i < 60; i++) {
      USER_IDS.add(
          createUser(headers, realm, "test.user" + i, "Test" + i, "User Test" + i, "test.user" + i + "@test.info",
              "test"));
    }
    headers = authenticateKeycloakAdmin();
    for (int i = 0; i < 100; i++) {
      USER_IDS.add(
          createUser(headers, realm, "user.test" + i, "UTest" + i, "User Test" + i, "utest.user" + i + "@test.info",
              "test"));
    }
    HttpHeaders finalHeaders = headers;
    USER_IDS.forEach(u -> assignUserGroup(finalHeaders, realm, u, groupIdManager));

    headers = authenticateKeycloakAdmin();
    for (int i = 0; i < 60; i++) {
      GROUP_IDS.add(createGroup(headers, realm, "test.group" + i, false));
    }
    headers = authenticateKeycloakAdmin();
    for (int i = 0; i < 100; i++) {
      GROUP_IDS.add(createGroup(headers, realm, "group.test" + i, false));
    }
    HttpHeaders finalHeaders1 = headers;
    GROUP_IDS.forEach(g -> assignUserGroup(finalHeaders1, realm, userIdTeamlead, g));
  }

  @AfterAll
  static void deleteMassData() throws JSONException {
    HttpHeaders headers = authenticateKeycloakAdmin();
    String realm = "test";
    USER_IDS.forEach(u -> deleteUser(headers, realm, u));
    GROUP_IDS.forEach(g -> deleteGroup(headers, realm, g));
  }

  @Test
  void userQueryNoFilter() {
    List<User> result = identityService.createUserQuery().list();
    assertThat(result).hasSize(USER_IDS.size() + 5);
  }

  @Test
  void queryUnlimitedList() {
    List<User> result = identityService.createUserQuery().unlimitedList();
    assertThat(result).hasSize(USER_IDS.size() + 5);
  }

  @Test
  void groupMemberQuery() {
    List<User> result = identityService.createUserQuery().memberOfGroup(groupIdManager).list();
    assertThat(result).hasSize(USER_IDS.size() + 1);
  }

  @Test
  void userQueryUserFirstNameLike() {
    List<User> result = identityService.createUserQuery().userFirstNameLike("UT%").list();
    assertThat(result).hasSize(100);
  }

  @Test
  void userQueryUserLastNameLike() {
    List<User> result = identityService.createUserQuery().userLastNameLike("User%").list();
    assertThat(result).hasSize(USER_IDS.size());
  }

  @Test
  void groupQueryNoFilter() {
    List<Group> result = identityService.createGroupQuery().list();
    assertThat(result).hasSize(GROUP_IDS.size() + 9);
  }

  @Test
  void groupQueryGroupNameLike() {
    List<Group> result = identityService.createGroupQuery().groupNameLike("group%").list();
    assertThat(result).hasSize(100);
  }

  @Test
  void groupQueryGroupMember() {
    List<Group> result = identityService.createGroupQuery().groupMember("hans.mustermann@tradermail.info").list();
    assertThat(result).hasSize(GROUP_IDS.size() + 1);
  }
}
