package org.operaton.bpm.extension.keycloak.test;

/**
 * Tests tenant queries when organizations are not enabled.
 */
public class KeycloakTenantQueryDisabledTest extends AbstractKeycloakIdentityProviderTest {

  public void testTenantQueriesDisabledReturnEmpty() {
    assertEquals(0, identityService.createTenantQuery().count());
    assertEquals(0, identityService.createTenantQuery().list().size());
  }
}
