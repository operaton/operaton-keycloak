package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.GroupQuery;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.CacheableKeycloakGroupQuery;
import org.operaton.bpm.extension.keycloak.test.util.CacheAwareKeycloakIdentityProviderPluginForTest;
import org.operaton.bpm.extension.keycloak.test.util.PredictableTicker;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests group queries with caching enabled and cache duration configured
 */
class KeycloakGroupQueryWithCachingAndCustomCacheExpiryTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.enableCachingAndConfigureCacheDuration.cfg.xml");

  // ------------------------------------------------------------------------
  // Test configuration
  // ------------------------------------------------------------------------

  @Test
  void cacheEntriesEvictedWhenCacheTimeoutIsReached() {

    GroupQuery query = identityService.createGroupQuery();

    // query operaton-admin at time = 0
    assertThat(queryGroup(query, "operaton-admin").getName()).isEqualTo("operaton-admin");

    // cache contains only operaton-admin at this point
    assertThat(getCacheEntries()).containsExactlyElementsOf(singletonList("operaton-admin"));

    // move clock by 2 minutes
    PredictableTicker.moveTimeForwardByMinutes(2);

    // query cam-read-only after 2 minutes
    assertThat(queryGroup(query, "cam-read-only").getName()).isEqualTo("cam-read-only");

    // cache contains cam-read-only and operaton-admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("cam-read-only", "operaton-admin"));

    // move clock by another 2 minutes
    PredictableTicker.moveTimeForwardByMinutes(2);

    // cache still contains cam-read-only and operaton-admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("cam-read-only", "operaton-admin"));

    // move clock by another 2 minutes
    PredictableTicker.moveTimeForwardByMinutes(2);

    // operaton-admin was evicted because eviction timeout (5 minutes) has been breached
    // it's been 6 minutes since operaton-admin was inserted into cache
    assertThat(getCacheEntries()).containsExactlyElementsOf(singletonList("cam-read-only"));

    // move clock by another 5 minutes
    PredictableTicker.moveTimeForwardByMinutes(5);

    // cache is empty. cam-read-only has also been evicted
    assertThat(getCacheEntries()).containsExactlyElementsOf(emptyList());
  }

  private static Group queryGroup(GroupQuery query, String groupName) {
    Group group = query.groupName(groupName).singleResult();
    processPendingCacheEvictions();
    return group;
  }

  private static List<String> getCacheEntries() {
    processPendingCacheEvictions();
    return CacheAwareKeycloakIdentityProviderPluginForTest.groupQueryCache.asMap()
        .keySet()
        .stream()
        .map(CacheableKeycloakGroupQuery::getName)
        .sorted()
        .toList();
  }

  private static void processPendingCacheEvictions() {
    CacheAwareKeycloakIdentityProviderPluginForTest.groupQueryCache.cleanUp();
  }
}
