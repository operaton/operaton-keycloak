package org.operaton.bpm.extension.keycloak.showcase.sso;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.rest.security.auth.AuthenticationResult;
import org.operaton.bpm.engine.rest.security.auth.impl.ContainerBasedAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.StringUtils;

/**
 * OAuth2 Authentication Provider for usage with Keycloak and KeycloakIdentityProviderPlugin.
 */
public class KeycloakAuthenticationProvider extends ContainerBasedAuthenticationProvider {

  @Override
  public AuthenticationResult extractAuthenticatedUser(HttpServletRequest request, ProcessEngine engine) {

    // Extract user-name-attribute of the OAuth2 token
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof OAuth2AuthenticationToken)
        || !(authentication.getPrincipal() instanceof OidcUser)) {
      return AuthenticationResult.unsuccessful();
    }
    String userId = ((OidcUser) authentication.getPrincipal()).getName();
    if (!StringUtils.hasLength(userId)) {
      return AuthenticationResult.unsuccessful();
    }

    // Authentication successful
    AuthenticationResult authenticationResult = new AuthenticationResult(userId, true);
    authenticationResult.setGroups(getUserGroups(userId, engine));

    return authenticationResult;
  }

  private List<String> getUserGroups(String userId, ProcessEngine engine) {
    List<String> groupIds = new ArrayList<>();
    // query groups using KeycloakIdentityProvider plugin
    engine.getIdentityService().createGroupQuery().groupMember(userId).list().forEach(g -> groupIds.add(g.getId()));
    return groupIds;
  }

}