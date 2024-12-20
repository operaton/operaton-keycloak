package org.operaton.bpm.extension.keycloak.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

public class KeycloakConfigurationFilterRegistrationBean extends FilterRegistrationBean {

  public KeycloakConfigurationFilterRegistrationBean(KeycloakCockpitConfiguration keycloakCockpitConfiguration,
                                                     String operatonWebappApplicationPath) {
    setFilter(new KeycloakCockpitConfigurationFilter(keycloakCockpitConfiguration));
    setOrder(Ordered.HIGHEST_PRECEDENCE);
    addUrlPatterns(operatonWebappApplicationPath + KeycloakCockpitConfigurationFilter.KEYCLOAK_OPTIONS_PATH);
  }
}
