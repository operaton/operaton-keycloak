package org.operaton.bpm.extension.keycloak.showcase.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.operaton.bpm.engine.IdentityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

/**
 * Keycloak Authentication Filter - used for REST API Security.
 */
public class KeycloakAuthenticationFilter implements Filter {

	/** This class' logger. */
	private static final Logger LOG = LoggerFactory.getLogger(KeycloakAuthenticationFilter.class);
	
	/** Access to Camunda's IdentityService. */
	private IdentityService identityService;
	
	/** Access to the OAuth2 client service. */
	OAuth2AuthorizedClientService clientService;

	private String userNameAttribute;
	
	/**
	 * Creates a new KeycloakAuthenticationFilter.
	 * @param identityService access to Camunda's IdentityService
	 */
	public KeycloakAuthenticationFilter(IdentityService identityService, OAuth2AuthorizedClientService clientService, String userNameAttribute) {
		this.identityService = identityService;
		this.clientService = clientService;
		this.userNameAttribute = userNameAttribute;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

	    // Extract user-name-attribute of the JWT / OAuth2 token
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String userId = null;
		if (authentication instanceof JwtAuthenticationToken) {
			userId = ((JwtAuthenticationToken) authentication).getTokenAttributes().get(userNameAttribute).toString();
		} else if (authentication.getPrincipal() instanceof OidcUser) {
			userId = ((OidcUser)authentication.getPrincipal()).getName();
		} else {
			throw new AccessDeniedException("Invalid authentication request token");
		}
        if (!StringUtils.hasLength(userId)) {
        	throw new AccessDeniedException("Unable to extract user-name-attribute from token");
        }

        LOG.debug("Extracted userId from bearer token: {}", userId);

        try {
        	identityService.setAuthentication(userId, getUserGroups(userId));
        	chain.doFilter(request, response);
        } finally {
        	identityService.clearAuthentication();
        }
	}

    /**
     * Queries the groups of a given user.
     * @param userId the user's ID
     * @return list of groups the user belongs to
     */
    private List<String> getUserGroups(String userId){
        List<String> groupIds = new ArrayList<>();
        // query groups using KeycloakIdentityProvider plugin
        identityService.createGroupQuery().groupMember(userId).list()
        	.forEach( g -> groupIds.add(g.getId()));
        return groupIds;
    }
}
