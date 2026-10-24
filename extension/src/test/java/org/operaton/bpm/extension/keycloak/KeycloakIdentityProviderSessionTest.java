package org.operaton.bpm.extension.keycloak;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.identity.IdentityProviderException;
import org.operaton.bpm.extension.keycloak.cache.PassThroughCache;
import org.operaton.bpm.extension.keycloak.rest.KeycloakRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.throwable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeycloakIdentityProviderSessionTest {

  private static final String ISSUER_URL = "http://localhost:8080/realms/test";
  private static final String TOKEN_URL = ISSUER_URL + "/protocol/openid-connect/token";

  @Test
  void shouldReturnFalseForUnauthorizedInvalidCredentials() {
    KeycloakRestTemplate restTemplate = mock(KeycloakRestTemplate.class);
    when(restTemplate.postForEntity(eq(TOKEN_URL), any(HttpEntity.class), eq(String.class)))
        .thenThrow(clientError(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_grant\"}"));

    assertThat(createSession(restTemplate).checkPassword("operaton@accso.de", "wrong")).isFalse();
  }

  @Test
  void shouldReturnFalseForBadRequestInvalidGrant() {
    KeycloakRestTemplate restTemplate = mock(KeycloakRestTemplate.class);
    when(restTemplate.postForEntity(eq(TOKEN_URL), any(HttpEntity.class), eq(String.class)))
        .thenThrow(clientError(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_grant\"}"));

    assertThat(createSession(restTemplate).checkPassword("operaton@accso.de", "wrong")).isFalse();
  }

  @Test
  void shouldThrowForOtherBadRequestErrors() {
    KeycloakRestTemplate restTemplate = mock(KeycloakRestTemplate.class);
    when(restTemplate.postForEntity(eq(TOKEN_URL), any(HttpEntity.class), eq(String.class)))
        .thenThrow(clientError(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_client\"}"));

    assertThatThrownBy(() -> createSession(restTemplate).checkPassword("operaton@accso.de", "wrong")).asInstanceOf(throwable(IdentityProviderException.class));
  }

  private TestableKeycloakIdentityProviderSession createSession(KeycloakRestTemplate restTemplate) {
    KeycloakConfiguration configuration = new KeycloakConfiguration();
    configuration.setKeycloakIssuerUrl(ISSUER_URL);
    configuration.setClientId("operaton-identity-service");
    configuration.setClientSecret("secret");

    return new TestableKeycloakIdentityProviderSession(configuration, restTemplate);
  }

  private HttpClientErrorException clientError(HttpStatus status, String body) {
    return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY,
        body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
  }

  private static final class TestableKeycloakIdentityProviderSession extends KeycloakIdentityProviderSession {

    private TestableKeycloakIdentityProviderSession(KeycloakConfiguration keycloakConfiguration,
                                                    KeycloakRestTemplate restTemplate) {
      super(keycloakConfiguration, restTemplate, mock(KeycloakContextProvider.class), new PassThroughCache<>(),
          new PassThroughCache<CacheableKeycloakUserQuery, List<User>>(),
          new PassThroughCache<CacheableKeycloakGroupQuery, List<Group>>(),
          new PassThroughCache<CacheableKeycloakCheckPasswordCall, Boolean>());
    }

    @Override
    protected String getKeycloakUsername(String userId) {
      return userId;
    }
  }
}
