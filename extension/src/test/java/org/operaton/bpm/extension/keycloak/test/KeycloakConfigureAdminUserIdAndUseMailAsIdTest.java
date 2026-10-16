package org.operaton.bpm.extension.keycloak.test;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Permissions;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin user configuration test for the Keycloak identity provider.
 * Use Keycloak internal ID as administratorUserId and flag useEmailAsOperatonUserId enabled.
 */
class KeycloakConfigureAdminUserIdAndUseMailAsIdTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.configureAdminUserIdAndUseMailAsId.cfg.xml",
      plugin -> plugin.setAdministratorUserId(userIdOperatonAdmin));

  @AfterEach
  void tearDown() {
    // delete all created authorizations
    processEngine.getAuthorizationService()
        .createAuthorizationQuery()
        .list()
        .forEach(a -> processEngine.getAuthorizationService().deleteAuthorization(a.getId()));
  }

  // ------------------------------------------------------------------------
  // Test configuration
  // ------------------------------------------------------------------------

  @Test
  void adminUserConfiguration() {
    // check engine configuration
    List<String> operatonAdminUsers = ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getAdminUsers();
    assertThat(operatonAdminUsers).hasSize(1);
    String adminUserId = operatonAdminUsers.get(0);
    assertThat(adminUserId).isEqualTo("operaton@accso.de");

    // check that authorizations have been created
    assertThat(processEngine.getAuthorizationService().createAuthorizationQuery().userIdIn(adminUserId).count()).isGreaterThan(0);

    // check sample authorization for applications
    assertThat(processEngine.getAuthorizationService()
        .createAuthorizationQuery()
        .userIdIn(adminUserId)
        .resourceType(Resources.APPLICATION)
        .resourceId(Authorization.ANY)
        .hasPermission(Permissions.ALL)
        .count()).isOne();

    // query user data
    User user = processEngine.getIdentityService().createUserQuery().userId(adminUserId).singleResult();
    assertThat(user).isNotNull();
    assertThat(user.getId()).isEqualTo("operaton@accso.de");
    assertThat(user.getEmail()).isEqualTo("operaton@accso.de");

    // query groups
    Group group = processEngine.getIdentityService().createGroupQuery().groupMember(adminUserId).singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");
  }

}
