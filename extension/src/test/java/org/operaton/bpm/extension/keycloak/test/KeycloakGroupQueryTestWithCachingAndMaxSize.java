package org.operaton.bpm.extension.keycloak.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.GroupQuery;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.operaton.bpm.extension.keycloak.CacheableKeycloakGroupQuery;
import org.operaton.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.operaton.bpm.extension.keycloak.test.util.CacheAwareKeycloakIdentityProviderPluginForTest;
import org.operaton.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;

/**
 * Tests group queries with caching enabled and max size configured
 */
public class KeycloakGroupQueryTestWithCachingAndMaxSize extends AbstractKeycloakIdentityProviderTest {

  public static Test suite() {
    return new TestSetup(new TestSuite(KeycloakGroupQueryTestWithCachingAndMaxSize.class)) {

      // @BeforeClass
      protected void setUp() {
        ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createProcessEngineConfigurationFromResource(
            "operaton.enableCachingAndConfigureMaxCacheSize.cfg.xml");
        configureKeycloakIdentityProviderPlugin(config);
        PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
      }

      // @AfterClass
      protected void tearDown() {
        PluggableProcessEngineTestCase.cachedProcessEngine.close();
        PluggableProcessEngineTestCase.cachedProcessEngine = null;
      }
    };
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    // delete all created authorizations
    processEngine.getAuthorizationService()
        .createAuthorizationQuery()
        .list()
        .forEach(a -> processEngine.getAuthorizationService().deleteAuthorization(a.getId()));
    this.clearCache();
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

  public void testCacheEntriesEvictedWhenMaxSizeIsReached() {

    GroupQuery query = identityService.createGroupQuery();

    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    assertEquals(0, countBefore);

    assertEquals("operaton-admin", queryGroup(query, "operaton-admin").getName());

    // operaton-admin has not been queried before so http call count should increase by 1
    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

    // cache contains only operaton-admin at this point
    assertEquals(Collections.singletonList("operaton-admin"), getCacheEntries());

    assertEquals("cam-read-only", queryGroup(query, "cam-read-only").getName());

    // cam-read-only has not been queried before so http call count should increase by 1
    assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());

    // cache contains cam-read-only and operaton-admin
    assertEquals(Arrays.asList("cam-read-only", "operaton-admin"), getCacheEntries());

    assertEquals("operaton-admin", queryGroup(query, "operaton-admin").getName());

    // operaton-admin has already been queried and is still in the cache so count stays same
    assertEquals(countBefore + 2, CountingHttpRequestInterceptor.getHttpRequestCount());

    // cache still contains cam-read-only and operaton-admin
    assertEquals(Arrays.asList("cam-read-only", "operaton-admin"), getCacheEntries());

    assertEquals("manager", queryGroup(query, "manager").getName());

    // manager has not been queried before so http call count should increase by 1
    assertEquals(countBefore + 3, CountingHttpRequestInterceptor.getHttpRequestCount());

    // cam-read-only was evicted because maxSize(2) was breached and it was used fewer times than operaton-admin
    assertEquals(Arrays.asList("manager", "operaton-admin"), getCacheEntries());

    // query cam-read-only again
    assertEquals("cam-read-only", queryGroup(query, "cam-read-only").getName());

    // count should increase because cam-read-only was removed from cache before the query
    assertEquals(countBefore + 4, CountingHttpRequestInterceptor.getHttpRequestCount());

    // manager was evicted because it was used fewer times than operaton-admin
    assertEquals(Arrays.asList("cam-read-only", "operaton-admin"), getCacheEntries());
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
