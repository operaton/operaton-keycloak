package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Permissions;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin user configuration test for the Keycloak identity provider.
 * Use email as administratorUserId and flag useEmailAsOperatonUserId enabled.
 */
class KeycloakConfigureAdminUserIdAsMailTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.configureAdminUserIdAsMail.cfg.xml");

  // ------------------------------------------------------------------------
  // Test configuration
  // ------------------------------------------------------------------------

  @Test
  void adminUserConfiguration() {
    // check engine configuration
    List<String> operatonAdminUsers = processEngineConfiguration.getAdminUsers();
    assertThat(operatonAdminUsers).hasSize(1);
    String adminUserId = operatonAdminUsers.get(0);
    assertThat(adminUserId).isEqualTo("operaton@accso.de");

    // check that authorizations have been created
    assertThat(authorizationService.createAuthorizationQuery().userIdIn(adminUserId).count()).isPositive();

    // check sample authorization for applications
    assertThat(authorizationService
        .createAuthorizationQuery()
        .userIdIn(adminUserId)
        .resourceType(Resources.APPLICATION)
        .resourceId(Authorization.ANY)
        .hasPermission(Permissions.ALL)
        .count()).isOne();

    // query user data
    User user = identityService.createUserQuery().userId(adminUserId).singleResult();
    assertThat(user).isNotNull();
    assertThat(user.getEmail()).isEqualTo("operaton@accso.de");

    // query groups
    Group group = identityService.createGroupQuery().groupMember(adminUserId).singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");
  }
}
