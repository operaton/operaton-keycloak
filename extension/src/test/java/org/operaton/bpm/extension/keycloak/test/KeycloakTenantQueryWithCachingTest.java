package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.Tenant;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests tenant queries with caching enabled.
 */
class KeycloakTenantQueryWithCachingTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.enableCachingAndUseOrganizationsAsTenants.cfg.xml");

  @Test
  void cacheEnabledQueryWithNoFilter() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<Tenant> tenants = identityService.createTenantQuery().list();
    assertThat(tenants).hasSize(3);
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    assertThat(identityService.createTenantQuery().list()).hasSize(3);
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }

  @Test
  void cacheEnabledQueryFilterByTenantId() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    Tenant tenant = identityService.createTenantQuery().tenantId(tenantIdAlpha).singleResult();
    assertThat(tenant).isNotNull();
    assertThat(tenant.getId()).isEqualTo(tenantIdAlpha);

    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    Tenant cachedTenant = identityService.createTenantQuery().tenantId(tenantIdAlpha).singleResult();
    assertThat(cachedTenant).isNotNull();
    assertThat(cachedTenant.getId()).isEqualTo(tenantIdAlpha);
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }

  @Test
  void cacheEnabledQueryWithPaging() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<Tenant> firstPage = identityService.createTenantQuery().listPage(0, 2);
    assertThat(firstPage).hasSize(2);
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    assertThat(identityService.createTenantQuery().listPage(0, 2)).hasSize(2);
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    List<Tenant> secondPage = identityService.createTenantQuery().listPage(2, 10);
    assertThat(secondPage).hasSize(1);
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }

  @Test
  void cacheEnabledQueryFilterByUserId() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<Tenant> tenants = identityService.createTenantQuery().userMember(userIdManager).list();
    assertThat(tenants).hasSize(2);
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    assertThat(identityService.createTenantQuery().userMember(userIdManager).list()).hasSize(2);
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }
}
