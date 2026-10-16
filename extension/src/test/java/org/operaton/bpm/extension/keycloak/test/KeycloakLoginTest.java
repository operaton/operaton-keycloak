package org.operaton.bpm.extension.keycloak.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keycloak login test.
 */
class KeycloakLoginTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.cfg.xml");

  @Test
  void keycloakLoginSuccess() {
    assertThat(identityService.checkPassword("operaton@accso.de", "operaton1!")).isTrue();
  }

  @Test
  void keycloakLoginCapitalization() {
    assertThat(identityService.checkPassword("Operaton@Accso.de", "operaton1!")).isTrue();
  }

  @Test
  void keycloakLoginFailure() {
    assertThat(identityService.checkPassword("operaton@accso.de", "c")).isFalse();
    assertThat(identityService.checkPassword("non-existing", "operaton1!")).isFalse();
  }

  @Test
  void keycloakLoginNullValues() {
    assertThat(identityService.checkPassword(null, "operaton1!")).isFalse();
    assertThat(identityService.checkPassword("operaton@accso.de", null)).isFalse();
    assertThat(identityService.checkPassword(null, null)).isFalse();
  }

  @Test
  void keycloakLoginEmptyPassword() {
    assertThat(identityService.checkPassword("operaton@accso.de", "")).isFalse();
  }

  @Test
  void keycloakLoginSpecialCharacterPassword() {
    assertThat(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€")).isTrue();
    assertThat(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ")).isTrue();
  }
}
