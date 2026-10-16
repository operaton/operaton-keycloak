package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.GroupQuery;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.CacheableKeycloakGroupQuery;
import org.operaton.bpm.extension.keycloak.test.util.CacheAwareKeycloakIdentityProviderPluginForTest;
import org.operaton.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests group queries with caching enabled and max size configured
 */
class KeycloakGroupQueryWithCachingAndMaxSizeTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.enableCachingAndConfigureMaxCacheSize.cfg.xml");

  // ------------------------------------------------------------------------
  // Test configuration
  // ------------------------------------------------------------------------

  @Test
  void cacheEntriesEvictedWhenMaxSizeIsReached() {

    GroupQuery query = identityService.createGroupQuery();

    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    assertThat(countBefore).isZero();

    assertThat(queryGroup(query, "operaton-admin").getName()).isEqualTo("operaton-admin");

    // operaton-admin has not been queried before so http call count should increase by 1
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // cache contains only operaton-admin at this point
    assertThat(getCacheEntries()).containsExactlyElementsOf(singletonList("operaton-admin"));

    assertThat(queryGroup(query, "cam-read-only").getName()).isEqualTo("cam-read-only");

    // cam-read-only has not been queried before so http call count should increase by 1
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);

    // cache contains cam-read-only and operaton-admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("cam-read-only", "operaton-admin"));

    assertThat(queryGroup(query, "operaton-admin").getName()).isEqualTo("operaton-admin");

    // operaton-admin has already been queried and is still in the cache so count stays same
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);

    // cache still contains cam-read-only and operaton-admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("cam-read-only", "operaton-admin"));

    assertThat(queryGroup(query, "manager").getName()).isEqualTo("manager");

    // manager has not been queried before so http call count should increase by 1
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 3);

    // cam-read-only was evicted because maxSize(2) was breached and it was used fewer times than operaton-admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("manager", "operaton-admin"));

    // query cam-read-only again
    assertThat(queryGroup(query, "cam-read-only").getName()).isEqualTo("cam-read-only");

    // count should increase because cam-read-only was removed from cache before the query
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 4);

    // manager was evicted because it was used fewer times than operaton-admin
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("cam-read-only", "operaton-admin"));
  }

  private static Group queryGroup(GroupQuery query, String groupName) {
    Group group = query.groupName(groupName).singleResult();
    CacheAwareKeycloakIdentityProviderPluginForTest.groupQueryCache.cleanUp();
    return group;
  }

  private static List<String> getCacheEntries() {
    return CacheAwareKeycloakIdentityProviderPluginForTest.groupQueryCache.asMap()
        .keySet()
        .stream()
        .map(CacheableKeycloakGroupQuery::getName)
        .sorted()
        .toList();
  }
}
