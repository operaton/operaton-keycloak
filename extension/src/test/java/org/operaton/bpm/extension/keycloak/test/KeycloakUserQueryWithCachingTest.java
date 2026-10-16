package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User query test for the Keycloak identity provider with caching enabled.
 */
class KeycloakUserQueryWithCachingTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.enableCaching.cfg.xml");

  // ------------------------------------------------------------------------
  // Test configuration
  // ------------------------------------------------------------------------

  @Test
  void cacheEnabledQueryWithNoFilter() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<User> result = identityService.createUserQuery().list();

    assertThat(result).hasSize(5);

    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // run query again
    assertThat(identityService.createUserQuery().list()).hasSize(5);

    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }

  @Test
  void cacheEnabledQueryFilterByUserId() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    User user = identityService.createUserQuery().userId("operaton@accso.de").singleResult();
    assertThat(user).isNotNull();

    // validate user
    assertThat(user.getId()).isEqualTo("operaton@accso.de");
    assertThat(user.getFirstName()).isEqualTo("Admin");
    assertThat(user.getLastName()).isEqualTo("Operaton");
    assertThat(user.getEmail()).isEqualTo("operaton@accso.de");

    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // run query again
    assertThat(identityService.createUserQuery().userId("operaton@accso.de").singleResult()).isEqualTo(user);

    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    user = identityService.createUserQuery().userId("non-existing").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void cacheEnabledQueryWithPaging() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // First page
    List<User> result = identityService.createUserQuery().listPage(0, 2);
    assertThat(result).hasSize(2);

    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // run query again
    assertThat(identityService.createUserQuery().listPage(0, 2)).hasSize(2);

    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // Next page
    List<User> resultNext = identityService.createUserQuery().listPage(2, 10);
    assertThat(resultNext).hasSize(3);

    // unique results
    assertThat(result.stream().filter(resultNext::contains).count()).isZero();
  }

  @Test
  void cacheEnabledQueryOrderByUserId() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<User> result = identityService.createUserQuery().orderByUserId().desc().list();
    assertThat(result).hasSize(5);
    assertThat(result.get(0).getId().compareTo(result.get(1).getId())).isGreaterThan(0);
    assertThat(result.get(1).getId().compareTo(result.get(2).getId())).isGreaterThan(0);

    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // run query again
    assertThat(identityService.createUserQuery().orderByUserId().desc().list()).hasSize(5);

    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }

  @Test
  void authenticatedUserCanQueryOwnGroupsWhenCacheIsEnabled() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);

      identityService.setAuthenticatedUserId("non-existing");
      assertThat(identityService.createUserQuery().count()).isZero();

      identityService.setAuthenticatedUserId("operaton@accso.de");
      assertThat(identityService.createUserQuery().count()).isOne();

      // auth should not be cached and should be performed
      // on every query regardless of caching being enabled or not
      identityService.setAuthenticatedUserId("non-existing");
      assertThat(identityService.createUserQuery().count()).isZero();

    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }
}
