package org.operaton.bpm.extension.keycloak.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.cockpit.Cockpit;
import org.operaton.bpm.cockpit.impl.DefaultCockpitRuntimeDelegate;
import org.operaton.bpm.engine.IdentityService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.impl.identity.Authentication;
import org.operaton.bpm.engine.rest.security.auth.AuthenticationProvider;
import org.operaton.bpm.engine.rest.security.auth.AuthenticationResult;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.auth.KeycloakJwtAuthenticationFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(ProcessEngineExtension.class)
class StatelessAuthenticationFilterTest {

    private final AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);

    ProcessEngine processEngine;

    private final KeycloakJwtAuthenticationFilter statelessAuthenticationFilter = new KeycloakJwtAuthenticationFilter("") {
        {
            authenticationProvider = StatelessAuthenticationFilterTest.this.authenticationProvider;
        }
    };

    @BeforeEach
    void setUp() {
        Cockpit.setCockpitRuntimeDelegate(new DefaultCockpitRuntimeDelegate());
        IdentityService identityService = processEngine.getIdentityService();
        identityService.saveUser(identityService.newUser("user"));
    }
    
    @AfterEach
    void tearDown() {
        processEngine.getIdentityService().deleteUser("user");
    }

    @Test
    void testDoFilterForProtectedResource() throws ServletException, IOException {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/test");
        when(httpServletRequest.getMethod()).thenReturn("GET");

        when(authenticationProvider.extractAuthenticatedUser(any(), any()))
                .thenReturn(new AuthenticationResult("user", true));

        FilterChain filterChain = spy(new FilterChain() {
            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
                Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
                assertNotNull(currentAuthentication);
                assertEquals("user", currentAuthentication.getUserId());
            }
        });

        statelessAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);

        Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
        assertNull(currentAuthentication);
    }

    @Test
    void testDoFilterForStaticResource() throws ServletException, IOException {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/cockpit/plugin/test/static/test");
        when(httpServletRequest.getMethod()).thenReturn("GET");

        when(authenticationProvider.extractAuthenticatedUser(any(), any()))
                .thenReturn(new AuthenticationResult("user", true));

        statelessAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse,
                (servletRequest, servletResponse) -> {
                    Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
                    assertNull(currentAuthentication);
                });

        Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
        assertNull(currentAuthentication);
    }

    @Test
    void testDoFilterForProtectedResourceWithoutUser() throws ServletException, IOException {
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/test");
        when(httpServletRequest.getMethod()).thenReturn("GET");

        when(authenticationProvider.extractAuthenticatedUser(any(), any()))
                .thenReturn(new AuthenticationResult(null, false));

        FilterChain filterChain = mock(FilterChain.class);

        statelessAuthenticationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verifyNoInteractions(filterChain);

        verify(httpServletResponse).setStatus(401);
        Authentication currentAuthentication = processEngine.getIdentityService().getCurrentAuthentication();
        assertNull(currentAuthentication);
    }

}