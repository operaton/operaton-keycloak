package org.operaton.bpm.extension.keycloak.test;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.CacheableKeycloakCheckPasswordCall;
import org.operaton.bpm.extension.keycloak.test.util.CacheAwareKeycloakIdentityProviderPluginForTest;
import org.operaton.bpm.extension.keycloak.test.util.CountingHttpRequestInterceptor;
import org.operaton.bpm.extension.keycloak.test.util.PredictableTicker;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Keycloak login test with login cache enabled.
 */
class KeycloakLoginWithCachingTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.enableLoginCaching.cfg.xml");

  // ------------------------------------------------------------------------
  // Test methods
  // ------------------------------------------------------------------------

  @Test
  void cacheEnabledKeycloakLoginSuccess() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // first login
    assertThat(identityService.checkPassword("operaton@accso.de", "operaton1!")).isTrue();
    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // second login
    assertThat(identityService.checkPassword("operaton@accso.de", "operaton1!")).isTrue();
    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }

  @Test
  void cacheEnabledKeycloakLoginCapitalization() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // first login
    assertThat(identityService.checkPassword("Operaton@Accso.de", "operaton1!")).isTrue();
    // non cached query. http request count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);

    // second login
    assertThat(identityService.checkPassword("Operaton@Accso.de", "operaton1!")).isTrue();
    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 1);
  }

  @Test
  void cacheEnabledKeycloakLoginFailure() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // failing logins
    assertThat(identityService.checkPassword("operaton@accso.de", "c")).isFalse();
    assertThat(identityService.checkPassword("non-existing", "operaton1!")).isFalse();
    // non cached query. http requests count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);

    // second failing logins
    assertThat(identityService.checkPassword("operaton@accso.de", "c")).isFalse();
    assertThat(identityService.checkPassword("non-existing", "operaton1!")).isFalse();
    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);

    // third now successful login
    assertThat(identityService.checkPassword("operaton@accso.de", "operaton1!")).isTrue();
    // request count should have increased due to the different password
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 3);
  }

  @Test
  void cacheEnabledKeycloakLoginNullValues() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // failing logins
    assertThat(identityService.checkPassword(null, "operaton1!")).isFalse();
    assertThat(identityService.checkPassword("operaton@accso.de", null)).isFalse();
    assertThat(identityService.checkPassword(null, null)).isFalse();
    // no http requests: missing userId or password - we do not support anonymous logins
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore);
  }

  @Test
  void cacheEnabledKeycloakLoginEmptyValues() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // first login
    assertThat(identityService.checkPassword("", "operaton1!")).isFalse();
    assertThat(identityService.checkPassword("operaton@accso.de", "")).isFalse();
    assertThat(identityService.checkPassword("", "")).isFalse();
    // no http request: empty user id or passwords - we do not support anonymous logins
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore);
  }

  @Test
  void cacheEnabledKeycloakLoginSpecialCharacterPassword() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // first logins
    assertThat(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€")).isTrue();
    assertThat(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ")).isTrue();
    // non cached query. http requests count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);

    // second logins
    assertThat(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€")).isTrue();
    assertThat(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ")).isTrue();
    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);
  }

  @Test
  void loginCacheSize() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // first logins
    assertThat(identityService.checkPassword("operaton@accso.de", "operaton1!")).isTrue();
    assertThat(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€")).isTrue();
    // non cached query. http requests count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);
    // check cache entries
    assertThat(getCacheEntries()).containsExactlyElementsOf(asList("johnfoo@gmail.com", "operaton@accso.de"));

    // call cache entry
    assertThat(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€")).isTrue();
    // request count should be same as before, cache size as well
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);
    assertThat(getCacheEntries()).hasSize(2);

    // next login
    assertThat(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ")).isTrue();
    // non cached query. http requests count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 3);
    // check cache entries: one has been evicted, new one is part of the cache
    assertThat(getCacheEntries()).contains("hans.mustermann@tradermail.info");
    // cache may temporarily exceed the max entry limit while evicting, hence we need to wait
    await().atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> assertThat(getCacheEntries()).hasSize(2));

    // call cache entry
    assertThat(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ")).isTrue();
    // request count should be same as before, cache size as well
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 3);
    assertThat(getCacheEntries()).hasSize(2);
  }

  @Test
  void loginCustomCacheTimeout() {
    int countBefore = CountingHttpRequestInterceptor.getHttpRequestCount();

    // first logins
    assertThat(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€")).isTrue();
    assertThat(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ")).isTrue();
    // non cached query. http requests count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);

    // move clock by 2 minutes
    PredictableTicker.moveTimeForwardByMinutes(2);

    // next logins
    assertThat(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€")).isTrue();
    assertThat(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ")).isTrue();
    // request count should be same as before
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 2);

    // move clock by another 2 minutes
    PredictableTicker.moveTimeForwardByMinutes(2);

    // next logins
    assertThat(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€")).isTrue();
    assertThat(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ")).isTrue();
    assertThat(identityService.checkPassword("operaton@accso.de", "operaton1!")).isTrue();
    // request count should be increased by new login only
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 3);

    // move clock by another 2 minutes: now 6 minutes are gone, configured timeout has been 5 minutes
    PredictableTicker.moveTimeForwardByMinutes(2);

    // check cache: only youngest login (with age 2 minutes) should have survived
    assertThat(getCacheEntries()).hasSize(1);
    assertThat(getCacheEntries()).contains("operaton@accso.de");

    // logins after timeout
    assertThat(identityService.checkPassword("operaton@accso.de", "operaton1!")).isTrue();
    assertThat(identityService.checkPassword("johnfoo@gmail.com", "!§$%&/()=?#'-_.:,;+*~@€")).isTrue();
    assertThat(identityService.checkPassword("hans.mustermann@tradermail.info", "äöüÄÖÜ")).isTrue();
    // only youngest login cached, others are timed out. http requests count should have increased
    assertThat(CountingHttpRequestInterceptor.getHttpRequestCount()).isEqualTo(countBefore + 5);
  }

  // ------------------------------------------------------------------------
  // Helper methods
  // ------------------------------------------------------------------------

  private static List<String> getCacheEntries() {
    return CacheAwareKeycloakIdentityProviderPluginForTest.checkPasswordCache.asMap()
        .keySet()
        .stream()
        .map(CacheableKeycloakCheckPasswordCall::getUserId)
        .sorted()
        .toList();
  }
}
