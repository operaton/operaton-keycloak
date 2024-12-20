package org.operaton.bpm.extension.keycloak.test.util;

import java.util.List;

import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.extension.keycloak.CacheableKeycloakCheckPasswordCall;
import org.operaton.bpm.extension.keycloak.CacheableKeycloakGroupQuery;
import org.operaton.bpm.extension.keycloak.CacheableKeycloakUserQuery;
import org.operaton.bpm.extension.keycloak.KeycloakIdentityProviderFactory;
import org.operaton.bpm.extension.keycloak.cache.CacheConfiguration;
import org.operaton.bpm.extension.keycloak.cache.CaffeineCache;
import org.operaton.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;

public class CacheAwareKeycloakIdentityProviderPluginForTest extends KeycloakIdentityProviderPlugin {

  public static CaffeineCache<CacheableKeycloakUserQuery, List<User>> userQueryCache;
  public static CaffeineCache<CacheableKeycloakGroupQuery, List<Group>> groupQueryCache;
  public static CaffeineCache<CacheableKeycloakCheckPasswordCall, Boolean> checkPasswordCache;

  @Override
  public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    super.preInit(processEngineConfiguration);
    KeycloakIdentityProviderFactory factory = (KeycloakIdentityProviderFactory) processEngineConfiguration.getIdentityProviderSessionFactory();

    PredictableTicker ticker = new PredictableTicker();
    CacheConfiguration cacheConfiguration = CacheConfiguration.from(this);
    CacheConfiguration loginCacheConfiguration = CacheConfiguration.fromLoginConfigOf(this);

    // instantiate with ticker that can be controlled in tests
    userQueryCache = new CaffeineCache<>(cacheConfiguration, ticker);
    groupQueryCache = new CaffeineCache<>(cacheConfiguration, ticker);
    checkPasswordCache = new CaffeineCache<>(loginCacheConfiguration, ticker);

    factory.setUserQueryCache(userQueryCache);
    factory.setGroupQueryCache(groupQueryCache);
    factory.setCheckPasswordCache(checkPasswordCache);
  }
}
