package org.operaton.bpm.extension.keycloak.plugin;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.operaton.bpm.engine.AuthorizationService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.authorization.Resource;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.operaton.bpm.engine.impl.persistence.entity.AuthorizationEntity;
import org.operaton.bpm.extension.keycloak.KeycloakConfiguration;
import org.operaton.bpm.extension.keycloak.KeycloakIdentityProviderFactory;
import org.operaton.bpm.extension.keycloak.KeycloakIdentityProviderSession;
import org.operaton.bpm.extension.keycloak.util.KeycloakPluginLogger;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.ObjectUtils;

import static org.operaton.bpm.engine.authorization.Authorization.ANY;
import static org.operaton.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.operaton.bpm.engine.authorization.Permissions.ALL;

/**
 * <p>{@link ProcessEnginePlugin} providing Keycloak Identity Provider support</p>
 *
 * <p>This class extends {@link KeycloakConfiguration} such that the configuration properties
 * can be set directly on this class via the <code>&lt;properties .../&gt;</code> element
 * in bpm-platform.xml / processes.xml</p>
 */
public class KeycloakIdentityProviderPlugin extends KeycloakConfiguration implements ProcessEnginePlugin {

  private static final KeycloakPluginLogger LOG = KeycloakPluginLogger.INSTANCE;

  private boolean authorizationEnabled;

  private KeycloakIdentityProviderFactory keycloakIdentityProviderFactory;

  /**
   * custom interceptors to modify behaviour of default KeycloakRestTemplate
   */
  private List<ClientHttpRequestInterceptor> customHttpRequestInterceptors = Collections.emptyList();

  /**
   * {@inheritDoc}
   */
  @Override
  public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    checkMandatoryConfigurationParameters(processEngineConfiguration);

    authorizationEnabled = processEngineConfiguration.isAuthorizationEnabled();

    if (!ObjectUtils.isEmpty(administratorGroupName)) {
      if (processEngineConfiguration.getAdminGroups() == null) {
        processEngineConfiguration.setAdminGroups(new ArrayList<>());
      }
      // add the configured administrator group to the engine configuration later: needs translation to group ID
    }
    if (!ObjectUtils.isEmpty(administratorUserId)) {
      if (processEngineConfiguration.getAdminUsers() == null) {
        processEngineConfiguration.setAdminUsers(new ArrayList<>());
      }
      // add the configured administrator to the engine configuration later: potentially needs translation to user ID
    }

    keycloakIdentityProviderFactory = new KeycloakIdentityProviderFactory(this, customHttpRequestInterceptors);
    processEngineConfiguration.setIdentityProviderSessionFactory(keycloakIdentityProviderFactory);

    LOG.pluginActivated(getClass().getSimpleName(), processEngineConfiguration.getProcessEngineName());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    // nothing to do
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void postProcessEngineBuild(ProcessEngine processEngine) {
    // always add the configured administrator group to the engine configuration
    String administratorGroupId = null;
    if (!ObjectUtils.isEmpty(administratorGroupName)) {
      // query the real group ID
      administratorGroupId = ((KeycloakIdentityProviderSession) keycloakIdentityProviderFactory.openSession()).getKeycloakAdminGroupId(
          administratorGroupName);
      ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getAdminGroups()
          .add(administratorGroupId);
    }

    // always add the configured administrator user to the engine configuration
    if (!ObjectUtils.isEmpty(administratorUserId)) {
      // query the real user ID
      administratorUserId = ((KeycloakIdentityProviderSession) keycloakIdentityProviderFactory.openSession()).getKeycloakAdminUserId(
          administratorUserId);
      ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getAdminUsers()
          .add(administratorUserId);
    }

    // need to prepare administrator authorizations only in case authorization has been enabled in the configuration
    if (!authorizationEnabled) {
      return;
    }

    final AuthorizationService authorizationService = processEngine.getAuthorizationService();

    if (!ObjectUtils.isEmpty(administratorGroupName)) {
      // create ADMIN authorizations on all built-in resources for configured admin group
      for (Resource resource : Resources.values()) {
        if (authorizationService.createAuthorizationQuery()
            .groupIdIn(administratorGroupId)
            .resourceType(resource)
            .resourceId(ANY)
            .count() == 0) {
          AuthorizationEntity adminGroupAuth = new AuthorizationEntity(AUTH_TYPE_GRANT);
          adminGroupAuth.setGroupId(administratorGroupId);
          adminGroupAuth.setResource(resource);
          adminGroupAuth.setResourceId(ANY);
          adminGroupAuth.addPermission(ALL);
          authorizationService.saveAuthorization(adminGroupAuth);
          LOG.grantGroupPermissions(administratorGroupName, administratorGroupId, resource.resourceName());
        }
      }
    }

    if (!ObjectUtils.isEmpty(administratorUserId)) {
      // create ADMIN authorizations on all built-in resources for configured admin user
      for (Resource resource : Resources.values()) {
        if (authorizationService.createAuthorizationQuery()
            .userIdIn(administratorUserId)
            .resourceType(resource)
            .resourceId(ANY)
            .count() == 0) {
          AuthorizationEntity adminUserAuth = new AuthorizationEntity(AUTH_TYPE_GRANT);
          adminUserAuth.setUserId(administratorUserId);
          adminUserAuth.setResource(resource);
          adminUserAuth.setResourceId(ANY);
          adminUserAuth.addPermission(ALL);
          authorizationService.saveAuthorization(adminUserAuth);
          LOG.grantUserPermissions(administratorUserId, resource.resourceName());
        }
      }
    }
  }

  /**
   * Checks mandatory configuration parameters.
   *
   * @param processEngineConfiguration the process engine configuration
   */
  private void checkMandatoryConfigurationParameters(ProcessEngineConfigurationImpl processEngineConfiguration) {
    List<String> missing = new ArrayList<>();
    if (ObjectUtils.isEmpty(keycloakIssuerUrl)) {
      LOG.missingConfigurationParameter("keycloakIssuerUrl");
      missing.add("keycloakIssuerUrl");
    }
    if (ObjectUtils.isEmpty(keycloakAdminUrl)) {
      LOG.missingConfigurationParameter("keycloakAdminUrl");
      missing.add("keycloakAdminUrl");
    }
    if (ObjectUtils.isEmpty(clientId)) {
      LOG.missingConfigurationParameter("clientId");
      missing.add("clientId");
    }
    if (ObjectUtils.isEmpty(clientSecret)) {
      LOG.missingConfigurationParameter("clientSecret");
      missing.add("clientSecret");
    }
    if (ObjectUtils.isEmpty(charset)) {
      LOG.missingConfigurationParameter("charset");
      missing.add("charset");
    }
    if (!missing.isEmpty()) {
      LOG.activationError(getClass().getSimpleName(), processEngineConfiguration.getProcessEngineName(),
          "missing mandatory configuration parameters " + missing);
      throw new IllegalStateException("Unable to initialize plugin " + getClass().getSimpleName()
          + ": - missing mandatory configuration parameters: " + missing);
    }
    if (isUseEmailAsOperatonUserId() && isUseUsernameAsOperatonUserId()) {
      LOG.activationError(getClass().getSimpleName(), processEngineConfiguration.getProcessEngineName(),
          "cannot use configuration parameters 'useUsernameAsOperatonUserId' AND 'useEmailAsOperatonUserId' at the same time");
      throw new IllegalStateException("Unable to initialize plugin " + getClass().getSimpleName()
          + ": - cannot use configuration parameters 'useUsernameAsOperatonUserId' AND 'useEmailAsOperatonUserId' at the same time");
    }
    if (!Charset.isSupported(charset)) {
      throw new IllegalStateException(
          "Unable to initialize plugin " + getClass().getSimpleName() + ": charset '" + charset
              + "' not supported in your JVM");
    }
  }

  /**
   * immediately clear entries from cache
   */
  public void clearCache() {
    this.keycloakIdentityProviderFactory.clearCache();
  }

  /**
   * @param customHttpRequestInterceptors the custom http request interceptors
   */
  public void setCustomHttpRequestInterceptors(List<ClientHttpRequestInterceptor> customHttpRequestInterceptors) {
    this.customHttpRequestInterceptors = customHttpRequestInterceptors;
  }
}
