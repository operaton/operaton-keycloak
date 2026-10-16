package org.operaton.bpm.extension.keycloak.test;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.identity.UserQuery;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.CacheableKeycloakUserQuery;
import org.operaton.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.operaton.bpm.extension.keycloak.test.util.CacheAwareKeycloakIdentityProviderPluginForTest;
import org.operaton.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;
import org.operaton.bpm.extension.keycloak.test.util.PredictableTicker;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * User query test for the Keycloak identity provider with caching enabled and max size configured
 */
class KeycloakUserQueryWithCachingAndMaxSizeTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.enableCachingAndConfigureMaxCacheSize.cfg.xml");

  @AfterEach
  void tearDown() {
    // delete all created authorizations
    processEngine.getAuthorizationService()
        .createAuthorizationQuery()
        .list()
        .forEach(a -> processEngine.getAuthorizationService().deleteAuthorization(a.getId()));
    this.clearCache();
    PredictableTicker.reset();
    CountingHttpRequestInterceptor.resetCount();
  }

  /**
   * clears the query cache so each test can start with a clean slate
   */
  private void clearCache() {
    processEngineConfiguration.getProcessEnginePlugins()
        .stream()
        .filter(KeycloakIdentityProviderPlugin.class::isInstance)
        .map(KeycloakIdentityProviderPlugin.class::cast)
        .forEach(KeycloakIdentityProviderPlugin::clearCache);
  }

  // ------------------------------------------------------------------------
  // Test configuration
  // ------------------------------------------------------------------------

  @Test
  void cacheEntriesEvictedWhenMaxSizeIsReached() {

    UserQuery query = identityService.createUserQuery();

    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    assertThat(countBefore).isZero();

    assertThat(queryUser(query, "Admin").getFirstName()).isEqualTo("Admin");

    // Admin has not been queried before so http call count should increase by 1
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // cache contains only Admin at this point
    assertThat(getCacheEntries()).containsExactlyElementsOf(singletonList("Admin"));

    assertThat(queryUser(query, "Identity").getFirstName()).isEqualTo("Identity");

    // Identity has not been queried before so http call count should increase by 1
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);

    // cache contains Identity and Admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("Admin", "Identity"));

    assertThat(queryUser(query, "Admin").getFirstName()).isEqualTo("Admin");

    // Admin has already been queried and is still in the cache so count stays same
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);

    // cache still contains Identity and Admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("Admin", "Identity"));

    assertThat(queryUser(query, "Gunnar").getFirstName()).isEqualTo("Gunnar");

    // Gunnar has not been queried before so http call count should increase by 1
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 3);

    // Identity was evicted because maxSize(2) was breached and it was used fewer times than Admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("Admin", "Gunnar"));

    // query Identity again
    assertThat(queryUser(query, "Identity").getFirstName()).isEqualTo("Identity");

    // count should increase because Identity was removed from cache before the query
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 4);

    // Gunnar was evicted because the name was used fewer times than Admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("Admin", "Identity"));
  }

  private static User queryUser(UserQuery query, String firstName) {
    User user = query.userFirstName(firstName).singleResult();
    CacheAwareKeycloakIdentityProviderPluginForTest.userQueryCache.cleanUp();
    return user;
  }

  private static List<String> getCacheEntries() {
    return CacheAwareKeycloakIdentityProviderPluginForTest.userQueryCache.asMap()
        .keySet()
        .stream()
        .map(CacheableKeycloakUserQuery::getFirstName)
        .sorted()
        .collect(Collectors.toList());
  }

}
