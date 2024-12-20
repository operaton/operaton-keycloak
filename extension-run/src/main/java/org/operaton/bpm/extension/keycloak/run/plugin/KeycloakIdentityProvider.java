package org.operaton.bpm.extension.keycloak.run.plugin;

import org.operaton.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
@ConfigurationProperties(prefix = "plugin.identity.keycloak")
public class KeycloakIdentityProvider extends KeycloakIdentityProviderPlugin {

}
