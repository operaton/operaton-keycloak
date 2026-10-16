package org.operaton.bpm.extension.keycloak.test;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.KeycloakContext;
import org.operaton.bpm.extension.keycloak.KeycloakContextProvider;
import org.operaton.bpm.extension.keycloak.KeycloakIdentityProviderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh access token test.
 */
class KeycloakRefreshTokenTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.cfg.xml");

  /**
   * Tests refreshing access token.
   *
   * @throws Exception in case of errors
   */
  @Test
  void refreshToken() throws Exception {
    // access Keycloak
    assertThat(identityService.createUserQuery().count()).isEqualTo(5);

    // expire current token (the dirty way)
    KeycloakIdentityProviderFactory sessionFactory = (KeycloakIdentityProviderFactory) ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getIdentityProviderSessionFactory();
    KeycloakContextProvider keycloakContextProvider = getProtectedField(sessionFactory, "keycloakContextProvider");
    KeycloakContext ctx = getProtectedField(keycloakContextProvider, "context");
    Field expiresField = KeycloakContext.class.getDeclaredField("expiresAt");
    expiresField.setAccessible(true);
    expiresField.set(ctx, 0);
    assertThat(ctx.needsRefresh()).isTrue();

    // access Keycloak again
    assertThat(identityService.createUserQuery().count()).isEqualTo(5);

  }

  /**
   * Helper for accessing protected fields.
   *
   * @param obj       the parent object
   * @param fieldName the name of the declared field in the parent object to retrieve
   * @return the value of the field
   * @throws Exception in case of errors
   */
  @SuppressWarnings("unchecked")
  private <T> T getProtectedField(Object obj, String fieldName) throws Exception {
    Field field = obj.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return (T) field.get(obj);
  }

}
