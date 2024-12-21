package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.test.PluggableProcessEngineTestCase;

/**
 * User query test for the Keycloak identity provider.
 * Flags useEmailAsOperatonUserId and useUsernameAsOperatonUserId disabled.
 */
public class KeycloakUseKeycloakIdAsUserIdQueryTest extends AbstractKeycloakIdentityProviderTest {

  public static Test suite() {
    return new TestSetup(new TestSuite(KeycloakUseKeycloakIdAsUserIdQueryTest.class)) {

      // @BeforeClass
      protected void setUp() throws Exception {
        ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createProcessEngineConfigurationFromResource(
            "operaton.useKeycloakIdAsOperatonUserId.cfg.xml");
        configureKeycloakIdentityProviderPlugin(config);
        PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
      }

      // @AfterClass
      protected void tearDown() throws Exception {
        PluggableProcessEngineTestCase.cachedProcessEngine.close();
        PluggableProcessEngineTestCase.cachedProcessEngine = null;
      }
    };
  }

  // ------------------------------------------------------------------------
  // Authorization tests
  // ------------------------------------------------------------------------

  public void testKeycloakLoginSuccess() {
    assertTrue(identityService.checkPassword(userIdOperatonAdmin, "operaton1!"));
  }

  // ------------------------------------------------------------------------
  // User Query tests
  // ------------------------------------------------------------------------

  public void testUserQueryFilterByUserId() {
    User user = identityService.createUserQuery().userId(userIdTeamlead).singleResult();
    assertNotNull(user);

    user = identityService.createUserQuery().userId(userIdOperatonAdmin).singleResult();
    assertNotNull(user);

    // validate user
    assertEquals(userIdOperatonAdmin, user.getId());
    assertEquals("Admin", user.getFirstName());
    assertEquals("Operaton", user.getLastName());
    assertEquals("operaton@accso.de", user.getEmail());

    user = identityService.createUserQuery().userId("non-existing").singleResult();
    assertNull(user);
  }

  public void testUserQueryFilterByUserIdIn() {
    List<User> users = identityService.createUserQuery().userIdIn(userIdOperatonAdmin, userIdTeamlead).list();
    assertNotNull(users);
    assertEquals(2, users.size());

    users = identityService.createUserQuery().userIdIn(userIdOperatonAdmin, "non-existing").list();
    assertNotNull(users);
    assertEquals(1, users.size());
  }

  public void testUserQueryFilterByEmail() {
    User user = identityService.createUserQuery().userEmail("operaton@accso.de").singleResult();
    assertNotNull(user);

    // validate user
    assertEquals(userIdOperatonAdmin, user.getId());
    assertEquals("Admin", user.getFirstName());
    assertEquals("Operaton", user.getLastName());
    assertEquals("operaton@accso.de", user.getEmail());

    user = identityService.createUserQuery().userEmail("non-exist*").singleResult();
    assertNull(user);
  }

  public void testUserQueryFilterByGroupIdAndId() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(groupIdAdmin)
        .userId(userIdOperatonAdmin)
        .list();
    assertEquals(1, result.size());

    result = identityService.createUserQuery().memberOfGroup(groupIdAdmin).userId("non-exist").list();
    assertEquals(0, result.size());

    result = identityService.createUserQuery().memberOfGroup("non-exist").userId(userIdOperatonAdmin).list();
    assertEquals(0, result.size());

  }

  public void testAuthenticatedUserSeesHimself() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);

      identityService.setAuthenticatedUserId("non-existing");
      assertEquals(0, identityService.createUserQuery().count());

      identityService.setAuthenticatedUserId(userIdOperatonAdmin);
      assertEquals(1, identityService.createUserQuery().count());

    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

  // ------------------------------------------------------------------------
  // Group query tests
  // ------------------------------------------------------------------------

  public void testGroupQueryFilterByUserId() {
    List<Group> result = identityService.createGroupQuery().groupMember(userIdOperatonAdmin).list();
    assertEquals(1, result.size());

    result = identityService.createGroupQuery().groupMember("non-exist").list();
    assertEquals(0, result.size());
  }

  public void testFilterByGroupIdAndUserId() {
    Group group = identityService.createGroupQuery()
        .groupId(groupIdAdmin)
        .groupMember(userIdOperatonAdmin)
        .singleResult();
    assertNotNull(group);
    assertEquals("operaton-admin", group.getName());

    group = identityService.createGroupQuery().groupId("non-exist").groupMember(userIdOperatonAdmin).singleResult();
    assertNull(group);

    group = identityService.createGroupQuery().groupId(groupIdAdmin).groupMember("non-exist").singleResult();
    assertNull(group);
  }

  public void testFilterByGroupIdInAndUserId() {
    Group group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdTeamlead)
        .groupMember(userIdOperatonAdmin)
        .singleResult();
    assertNotNull(group);
    assertEquals("operaton-admin", group.getName());

    group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdTeamlead)
        .groupMember("non-exist")
        .singleResult();
    assertNull(group);
  }

}
