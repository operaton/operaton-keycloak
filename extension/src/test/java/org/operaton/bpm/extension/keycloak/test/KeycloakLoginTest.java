package org.operaton.bpm.extension.keycloak.test;

/**
 * Keycloak login test.
 */
public class KeycloakLoginTest extends AbstractKeycloakIdentityProviderTest {
  
	public void testKeycloakLoginSuccess() {
		assertTrue(identityService.checkPassword("camunda@accso.de", "camunda1!"));
	}

	public void testKeycloakLoginCapitalization() {
		assertTrue(identityService.checkPassword("Camunda@Accso.de", "camunda1!"));
	}

	public void testKeycloakLoginFailure() {
		assertFalse(identityService.checkPassword("camunda@accso.de", "c"));
		assertFalse(identityService.checkPassword("non-existing", "camunda1!"));
	}

	public void testKeycloakLoginNullValues() {
		assertFalse(identityService.checkPassword(null, "camunda1!"));
		assertFalse(identityService.checkPassword("camunda@accso.de", null));
		assertFalse(identityService.checkPassword(null, null));
	}

	public void testKeycloakLoginEmptyPassword() {
		assertFalse(identityService.checkPassword("camunda@accso.de", ""));
	}
	
	public void testKeycloakLoginSpecialCharacterPassword() {
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
	}

}
