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
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * User query test for the Keycloak identity provider with caching enabled and cache duration configured
 */
class KeycloakUserQueryWithCachingAndCustomCacheExpiryTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.enableCachingAndConfigureCacheDuration.cfg.xml");

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
  void cacheEntriesEvictedWhenCacheTimeoutIsReached() {

    UserQuery query = identityService.createUserQuery();

    // query Admin at time = 0
    assertThat(queryUser(query, "Admin").getFirstName()).isEqualTo("Admin");

    // cache contains only Admin at this point
    assertThat(getCacheEntries()).containsExactlyElementsOf(singletonList("Admin"));

    // move clock by 2 minutes
    PredictableTicker.moveTimeForwardByMinutes(2);

    // query Identity after 2 minutes
    assertThat(queryUser(query, "Identity").getFirstName()).isEqualTo("Identity");

    // cache contains Identity and Admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("Admin", "Identity"));

    // move clock by another 2 minutes
    PredictableTicker.moveTimeForwardByMinutes(2);

    // cache still contains Identity and Admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("Admin", "Identity"));

    // move clock by another 2 minutes
    PredictableTicker.moveTimeForwardByMinutes(2);

    // Admin was evicted because eviction timeout (5 minutes) has been breached
    // it's been 6 minutes since Admin was inserted into cache
    assertThat(getCacheEntries()).containsExactlyElementsOf(singletonList("Identity"));

    // move clock by another 5 minutes
    PredictableTicker.moveTimeForwardByMinutes(5);

    // cache is empty. Identity has also been evicted
    assertThat(getCacheEntries()).containsExactlyElementsOf(emptyList());
  }

  private static User queryUser(UserQuery query, String firstName) {
    User user = query.userFirstName(firstName).singleResult();
    processPendingCacheEvictions();
    return user;
  }

  private static List<String> getCacheEntries() {
    processPendingCacheEvictions();
    return CacheAwareKeycloakIdentityProviderPluginForTest.userQueryCache.asMap()
        .keySet()
        .stream()
        .map(CacheableKeycloakUserQuery::getFirstName)
        .sorted()
        .collect(Collectors.toList());
  }

  private static void processPendingCacheEvictions() {
    CacheAwareKeycloakIdentityProviderPluginForTest.userQueryCache.cleanUp();
  }

}
