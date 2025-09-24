package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.identity.Tenant;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.operaton.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.operaton.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;

/**
 * Tests tenant queries with caching enabled.
 */
public class KeycloakTenantQueryTestWithCaching extends AbstractKeycloakIdentityProviderTest {

  public static Test suite() {
    return new TestSetup(new TestSuite(KeycloakTenantQueryTestWithCaching.class)) {

      // @BeforeClass
      protected void setUp() {
        ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createProcessEngineConfigurationFromResource(
            "operaton.enableCachingAndUseOrganizationsAsTenants.cfg.xml");
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

  public void testCacheEnabledQueryWithNoFilter() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<Tenant> tenants = identityService.createTenantQuery().list();
    assertEquals(3, tenants.size());
    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

    assertEquals(3, identityService.createTenantQuery().list().size());
    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
  }

  public void testCacheEnabledQueryFilterByTenantId() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    Tenant tenant = identityService.createTenantQuery().tenantId(tenantIdAlpha).singleResult();
    assertNotNull(tenant);
    assertEquals(tenantIdAlpha, tenant.getId());

    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

    Tenant cachedTenant = identityService.createTenantQuery().tenantId(tenantIdAlpha).singleResult();
    assertNotNull(cachedTenant);
    assertEquals(tenantIdAlpha, cachedTenant.getId());
    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
  }

  public void testCacheEnabledQueryWithPaging() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<Tenant> firstPage = identityService.createTenantQuery().listPage(0, 2);
    assertEquals(2, firstPage.size());
    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

    assertEquals(2, identityService.createTenantQuery().listPage(0, 2).size());
    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

    List<Tenant> secondPage = identityService.createTenantQuery().listPage(2, 10);
    assertEquals(1, secondPage.size());
    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
  }

  public void testCacheEnabledQueryFilterByUserId() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<Tenant> tenants = identityService.createTenantQuery().userMember(userIdManager).list();
    assertEquals(2, tenants.size());
    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());

    assertEquals(2, identityService.createTenantQuery().userMember(userIdManager).list().size());
    assertEquals(countBefore + 1, CountingHttpRequestInterceptor.getHttpRequestCount());
  }
}
