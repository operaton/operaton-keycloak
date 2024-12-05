package org.operaton.bpm.extension.keycloak.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.ProcessEngines;
import org.operaton.bpm.engine.rest.security.auth.AuthenticationResult;
import org.operaton.bpm.webapp.impl.security.SecurityActions;
import org.operaton.bpm.webapp.impl.security.auth.Authentications;
import org.operaton.bpm.webapp.impl.security.auth.ContainerBasedAuthenticationFilter;
import org.operaton.bpm.webapp.impl.security.auth.UserAuthentication;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Combination of ContainerBasedAuthenticationFilter and AuthenticationFilter with removed dependency on HttpSession
 *
 * @see org.operaton.bpm.webapp.impl.security.auth.AuthenticationFilter
 * @see org.operaton.bpm.webapp.impl.security.auth.ContainerBasedAuthenticationFilter
 */
public class KeycloakJwtAuthenticationFilter extends ContainerBasedAuthenticationFilter {

    private static final int HTTP_STATUS_NOT_AUTHENTICATED = 401;
    private static final int HTTP_STATUS_NOT_FOUND = 404;
    private static final String GET_METHOD = "GET";
    private String processEngineName = ProcessEngines.NAME_DEFAULT;

    private String operatonApplicationPath = "";

    public KeycloakJwtAuthenticationFilter(String operatonApplicationPath) {
        if (StringUtils.hasLength(operatonApplicationPath)) {
            this.operatonApplicationPath = operatonApplicationPath;
        }
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse resp = (HttpServletResponse) response;

        String engineName = extractEngineName(req);

        if (engineName == null) {
            chain.doFilter(request, response);
            return;
        }

        ProcessEngine engine = getAddressedEngine(engineName);

        if (engine == null) {
            resp.sendError(HTTP_STATUS_NOT_FOUND, "Process engine " + engineName + " not available");
            return;
        }

        AuthenticationResult authenticationResult = this.authenticationProvider.extractAuthenticatedUser(req, engine);
        if (authenticationResult.isAuthenticated()) {
            Authentications authentications = new Authentications();
            String authenticatedUser = authenticationResult.getAuthenticatedUser();
            if (!this.existisAuthentication(authentications, engineName, authenticatedUser)) {
                List<String> groups = authenticationResult.getGroups();
                List<String> tenants = authenticationResult.getTenants();
                UserAuthentication authentication = this.createAuthentication(engine, authenticatedUser, groups, tenants);
                if (authentication != null) authentications.addOrReplace(authentication);
            }

            Authentications.setCurrent(authentications);
            try {
                SecurityActions.runWithAuthentications((SecurityActions.SecurityAction<Void>) () -> {
                    chain.doFilter(request, response);
                    return null;
                }, authentications);
            } finally {
                Authentications.clearCurrent();
            }
        } else {
            resp.setStatus(HTTP_STATUS_NOT_AUTHENTICATED);
            this.authenticationProvider.augmentResponseByAuthenticationChallenge(resp, engine);
        }
    }

    public void setProcessEngineName(String processEngineName) {
        this.processEngineName = processEngineName;
    }

    @Override
    protected String extractEngineName(HttpServletRequest request) {
        String requestUri = request.getRequestURI().substring(operatonApplicationPath.length());
        Matcher apiStaticPluginPattern = API_STATIC_PLUGIN_PATTERN.matcher(requestUri);
        if (request.getMethod().equals(GET_METHOD) && apiStaticPluginPattern.matches()) {
            return null;
        } else {
            return processEngineName;
        }
    }

}
