package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Groups;
import org.operaton.bpm.engine.authorization.Permissions;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin group configuration test for the Keycloak identity provider.
 * Use group path in configuration and flag useGroupPathAsOperatonGroupId enabled.
 */
class KeycloakConfigureAdminGroupAsPathAndUsePathAsIdTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.configureAdminGroupAsPathAndUsePathAsId.cfg.xml");

  // ------------------------------------------------------------------------
  // Test configuration
  // ------------------------------------------------------------------------

  @Test
  void adminGroupConfiguration() {
    // check engine configuration
    List<String> operatonAdminGroups = processEngineConfiguration.getAdminGroups();
    assertThat(operatonAdminGroups).hasSize(2); // Operaton always adds "operaton-admin" as admin group ID - we want the other ID
    String adminGroupId = operatonAdminGroups.stream().filter(g -> !Groups.OPERATON_ADMIN.equals(g)).findFirst().get();

    // check that authorizations have been created
    assertThat(authorizationService.createAuthorizationQuery().groupIdIn(adminGroupId).count()).isPositive();

    // check sample authorization for applications
    assertThat(authorizationService
        .createAuthorizationQuery()
        .groupIdIn(adminGroupId)
        .resourceType(Resources.APPLICATION)
        .resourceId(Authorization.ANY)
        .hasPermission(Permissions.ALL)
        .count()).isOne();

    // query user data
    User user = identityService.createUserQuery().memberOfGroup(adminGroupId).singleResult();
    assertThat(user).isNotNull();
    assertThat(user.getEmail()).isEqualTo("johnfoo@gmail.com");

    // query groups
    Group group = identityService.createGroupQuery().groupId(adminGroupId).singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getId()).isEqualTo("root/child2");
    assertThat(group.getName()).isEqualTo("child2");

    // query groups using group member
    List<Group> groups = identityService.createGroupQuery().groupMember(user.getId()).list();
    assertThat(groups).isNotNull();
    assertThat(groups).as("Wrong number of groups for admin").hasSize(2);

  }
}
