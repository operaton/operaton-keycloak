package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Permissions;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.test.PluggableProcessEngineTestCase;

/**
 * Admin user configuration test for the Keycloak identity provider.
 * Use Keycloak internal ID as administratorUserId.
 */
public class KeycloakConfigureAdminUserIdTest extends AbstractKeycloakIdentityProviderTest {

  public static Test suite() {
    return new TestSetup(new TestSuite(KeycloakConfigureAdminUserIdTest.class)) {

      // @BeforeClass
      protected void setUp() throws Exception {
        ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createProcessEngineConfigurationFromResource(
            "operaton.configureAdminUserId.cfg.xml");
        configureKeycloakIdentityProviderPlugin(config).setAdministratorUserId(userIdOperatonAdmin);
        PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
      }

      // @AfterClass
      protected void tearDown() throws Exception {
        PluggableProcessEngineTestCase.cachedProcessEngine.close();
        PluggableProcessEngineTestCase.cachedProcessEngine = null;
      }
    };
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    // delete all created authorizations
    processEngine.getAuthorizationService()
        .createAuthorizationQuery()
        .list()
        .forEach(a -> processEngine.getAuthorizationService().deleteAuthorization(a.getId()));
  }

  // ------------------------------------------------------------------------
  // Test configuration
  // ------------------------------------------------------------------------

  public void testAdminUserConfiguration() {
    // check engine configuration
    List<String> operatonAdminUsers = ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getAdminUsers();
    assertEquals(1, operatonAdminUsers.size());
    String adminUserId = operatonAdminUsers.get(0);
    assertEquals(userIdOperatonAdmin, adminUserId);

    // check that authorizations have been created
    assertTrue(processEngine.getAuthorizationService().createAuthorizationQuery().userIdIn(adminUserId).count() > 0);

    // check sample authorization for applications
    assertEquals(1, processEngine.getAuthorizationService()
        .createAuthorizationQuery()
        .userIdIn(adminUserId)
        .resourceType(Resources.APPLICATION)
        .resourceId(Authorization.ANY)
        .hasPermission(Permissions.ALL)
        .count());

    // query user data
    User user = processEngine.getIdentityService().createUserQuery().userId(adminUserId).singleResult();
    assertNotNull(user);
    assertEquals("operaton@accso.de", user.getEmail());

    // query groups
    Group group = processEngine.getIdentityService().createGroupQuery().groupMember(adminUserId).singleResult();
    assertNotNull(group);
    assertEquals("operaton-admin", group.getName());
  }

}
