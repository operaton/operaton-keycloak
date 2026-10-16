package org.operaton.bpm.extension.keycloak.test;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.operaton.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests group queries with caching enabled.
 */
class KeycloakGroupQueryWithCachingTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.enableCaching.cfg.xml");

  @AfterEach
  void tearDown() {
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

  @Test
  void cacheEnabledQueryWithNoFilter() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<Group> groupList = identityService.createGroupQuery().list();
    assertThat(groupList).hasSize(9);

    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // run query again
    assertThat(identityService.createGroupQuery().list()).hasSize(9);

    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }

  @Test
  void cacheEnabledQueryFilterByGroupId() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    Group group = identityService.createGroupQuery().groupId(groupIdAdmin).singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo(groupIdAdmin);
    assertThat(group.getName()).isEqualTo("operaton-admin");
    assertThat(group.getType()).isEqualTo("SYSTEM");

    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // run query again
    assertThat(identityService.createGroupQuery().groupId(groupIdAdmin).singleResult()).isEqualTo(group);

    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    group = identityService.createGroupQuery().groupId("whatever").singleResult();
    assertThat(group).isNull();
  }

  @Test
  void cacheEnabledQueryWithPaging() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // First page
    List<Group> result = identityService.createGroupQuery().listPage(0, 3);
    assertThat(result).hasSize(3);

    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // run query again
    assertThat(identityService.createGroupQuery().listPage(0, 3)).hasSize(3);

    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // Next page
    List<Group> resultNext = identityService.createGroupQuery().listPage(3, 3);
    assertThat(resultNext).hasSize(3);

    // Next page
    List<Group> resultLast = identityService.createGroupQuery().listPage(6, 10);
    assertThat(resultLast).hasSize(3);

    // unique results
    assertThat(result.stream().filter(resultNext::contains).count()).isZero();
    assertThat(result.stream().filter(resultLast::contains).count()).isZero();
  }

  @Test
  void cacheEnabledQueryOrderByGroupId() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    List<Group> groupList = identityService.createGroupQuery().orderByGroupId().desc().list();
    assertThat(groupList).hasSize(9);
    assertThat(groupList.get(0).getId().compareTo(groupList.get(1).getId())).isGreaterThan(0);
    assertThat(groupList.get(1).getId().compareTo(groupList.get(2).getId())).isGreaterThan(0);
    assertThat(groupList.get(2).getId().compareTo(groupList.get(3).getId())).isGreaterThan(0);
    assertThat(groupList.get(5).getId().compareTo(groupList.get(6).getId())).isGreaterThan(0);
    assertThat(groupList.get(6).getId().compareTo(groupList.get(7).getId())).isGreaterThan(0);

    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // run query again
    assertThat(identityService.createGroupQuery().orderByGroupId().desc().list()).hasSize(9);

    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }

  @Test
  void authenticatedUserCanQueryOwnGroupsWhenCacheIsEnabled() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);
      identityService.setAuthenticatedUserId("johnfoo@gmail.com");

      assertThat(identityService.createGroupQuery().groupMember("operaton@accso.de").count()).isZero();
      assertThat(identityService.createGroupQuery().groupMember("johnfoo@gmail.com").count()).isEqualTo(2);

      // auth should not be cached and should be performed
      // on every query regardless of caching being enabled or not
      assertThat(identityService.createGroupQuery().groupMember("operaton@accso.de").count()).isZero();
    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

}
