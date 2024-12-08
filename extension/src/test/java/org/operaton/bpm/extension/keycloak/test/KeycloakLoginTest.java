package org.operaton.bpm.extension.keycloak.test;

/**
 * Keycloak login test.
 */
public class KeycloakLoginTest extends AbstractKeycloakIdentityProviderTest {
  
	public void testKeycloakLoginSuccess() {
		assertTrue(identityService.checkPassword("operaton@accso.de", "operaton1!"));
	}

	public void testKeycloakLoginCapitalization() {
		assertTrue(identityService.checkPassword("Operaton@Accso.de", "operaton1!"));
	}

	public void testKeycloakLoginFailure() {
		assertFalse(identityService.checkPassword("operaton@accso.de", "c"));
		assertFalse(identityService.checkPassword("non-existing", "operaton1!"));
	}

	public void testKeycloakLoginNullValues() {
		assertFalse(identityService.checkPassword(null, "operaton1!"));
		assertFalse(identityService.checkPassword("operaton@accso.de", null));
		assertFalse(identityService.checkPassword(null, null));
	}

	public void testKeycloakLoginEmptyPassword() {
		assertFalse(identityService.checkPassword("operaton@accso.de", ""));
	}
	
	public void testKeycloakLoginSpecialCharacterPassword() {
		assertTrue(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€"));
		assertTrue(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ"));
	}

}
