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
 * Test for checking a very low value of the maxResultSize parameter.
 */
class KeycloakMaxResultSizeTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.configureSmallMaxResultSize.cfg.xml",
      plugin -> plugin.setAdministratorUserId(userIdOperatonAdmin));

  static List<String> USER_IDS = new ArrayList<>();
  static List<String> GROUP_IDS = new ArrayList<>();

  @BeforeAll
  static void setupMassData() throws JSONException {
    HttpHeaders headers = authenticateKeycloakAdmin();
    String realm = TEST_REALM;
    for (int i = 0; i < 50; i++) {
      USER_IDS.add(
          createUser(headers, realm, "user.test" + i, "UTest" + i, "User Test" + i, "utest.user" + i + "@test.info",
              "test"));
    }
    USER_IDS.forEach(u -> assignUserGroup(headers, realm, u, groupIdManager));

    for (int i = 0; i < 50; i++) {
      GROUP_IDS.add(createGroup(headers, realm, "group.test" + i, false));
    }
    GROUP_IDS.forEach(g -> assignUserGroup(headers, realm, userIdTeamlead, g));
  }

  @AfterAll
  static void deleteMassData() throws JSONException {
    deleteUsersAndGroups(USER_IDS, GROUP_IDS);
  }

  @Test
  void userQueryNoFilter() {
    List<User> result = identityService.createUserQuery().list();
    assertThat(result).hasSize(25);
  }

  @Test
  void queryUnlimitedList() {
    List<User> result = identityService.createUserQuery().unlimitedList();
    assertThat(result).hasSize(25);
  }

  @Test
  void groupMemberQuery() {
    List<User> result = identityService.createUserQuery().memberOfGroup(groupIdManager).list();
    assertThat(result).hasSize(25);
  }

  @Test
  void userQueryUserFirstNameLike() {
    List<User> result = identityService.createUserQuery().userFirstNameLike("UT%").list();
    assertThat(result).hasSize(25);
  }

  @Test
  void userQueryUserLastNameLike() {
    List<User> result = identityService.createUserQuery().userLastNameLike("User%").orderByUserLastName().desc().list();
    assertThat(result).hasSize(25);
  }

  @Test
  void groupQueryNoFilter() {
    List<Group> result = identityService.createGroupQuery().list();
    assertThat(result).hasSize(25);
  }

  @Test
  void groupQueryGroupNameLike() {
    List<Group> result = identityService.createGroupQuery().groupNameLike("group%").list();
    assertThat(result).hasSize(25);
  }

  @Test
  void groupQueryGroupMember() {
    List<Group> result = identityService.createGroupQuery().groupMember("hans.mustermann@tradermail.info").list();
    assertThat(result).hasSize(25);
  }
}
