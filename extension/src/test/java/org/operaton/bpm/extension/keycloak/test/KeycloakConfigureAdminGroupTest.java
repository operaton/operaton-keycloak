package org.operaton.bpm.extension.keycloak.test;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Groups;
import org.operaton.bpm.engine.authorization.Permissions;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin group configuration test for the Keycloak identity provider.
 */
class KeycloakConfigureAdminGroupTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.configureAdminGroup.cfg.xml");

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
  void adminGroupConfiguration() {
    // check engine configuration
    List<String> operatonAdminGroups = ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getAdminGroups();
    assertThat(operatonAdminGroups).hasSize(2); // Operaton always adds "operaton-admin" as admin group ID - we want the other ID
    String adminGroupId = operatonAdminGroups.stream().filter(g -> !Groups.OPERATON_ADMIN.equals(g)).findFirst().get();

    // check that authorizations have been created
    assertThat(processEngine.getAuthorizationService().createAuthorizationQuery().groupIdIn(adminGroupId).count()).isGreaterThan(0);

    // check sample authorization for applications
    assertThat(processEngine.getAuthorizationService()
        .createAuthorizationQuery()
        .groupIdIn(adminGroupId)
        .resourceType(Resources.APPLICATION)
        .resourceId(Authorization.ANY)
        .hasPermission(Permissions.ALL)
        .count()).isOne();

    // query user data
    User user = processEngine.getIdentityService().createUserQuery().memberOfGroup(adminGroupId).singleResult();
    assertThat(user).isNotNull();
    assertThat(user.getEmail()).isEqualTo("operaton@accso.de");

    // query groups
    Group group = processEngine.getIdentityService().createGroupQuery().groupId(adminGroupId).singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");
  }

}
