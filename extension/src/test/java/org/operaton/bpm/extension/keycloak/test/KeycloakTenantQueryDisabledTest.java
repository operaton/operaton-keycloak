package org.operaton.bpm.extension.keycloak.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests tenant queries when organizations are not enabled.
 */
class KeycloakTenantQueryDisabledTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.cfg.xml");

  @Test
  void tenantQueriesDisabledReturnEmpty() {
    assertThat(identityService.createTenantQuery().count()).isZero();
    assertThat(identityService.createTenantQuery().list()).isEmpty();
  }
}
