package org.operaton.bpm.extension.keycloak.showcase.sso;

import java.util.Collections;

import jakarta.inject.Inject;

import org.operaton.bpm.webapp.impl.security.auth.ContainerBasedAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.filter.ForwardedHeaderFilter;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Operaton Web application SSO configuration for usage with KeycloakIdentityProviderPlugin.
 */
@ConditionalOnMissingClass("org.springframework.test.context.junit.jupiter.SpringExtension")
@EnableWebSecurity
@Configuration
public class WebAppSecurityConfig {

  @Inject
  private KeycloakLogoutHandler keycloakLogoutHandler;

  @Bean
  @Order(2)
  public SecurityFilterChain httpSecurity(HttpSecurity http) throws Exception {
    PathPatternRequestMatcher.Builder pathPattern = PathPatternRequestMatcher.withDefaults();
    return http.csrf(csrf -> csrf.ignoringRequestMatchers(pathPattern.matcher("/api/**"), pathPattern.matcher("/engine-rest/**")))
        .authorizeHttpRequests(authorize -> authorize.requestMatchers(pathPattern.matcher("/assets/**"), pathPattern.matcher("/app/**"),
            pathPattern.matcher("/api/**"), pathPattern.matcher("/lib/**")).authenticated().anyRequest().permitAll())
        .oauth2Login(withDefaults())
        .logout(logout -> logout.logoutRequestMatcher(pathPattern.matcher("/app/**/logout"))
            .logoutSuccessHandler(keycloakLogoutHandler))
        .build();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Bean
  public FilterRegistrationBean containerBasedAuthenticationFilter() {

    FilterRegistrationBean filterRegistration = new FilterRegistrationBean();
    filterRegistration.setFilter(new ContainerBasedAuthenticationFilter());
    filterRegistration.setInitParameters(Collections.singletonMap("authentication-provider",
        "org.operaton.bpm.extension.keycloak.showcase.sso.KeycloakAuthenticationProvider"));
    filterRegistration.setOrder(201); // make sure the filter is registered after the Spring Security Filter Chain
    filterRegistration.addUrlPatterns("/app/*");
    return filterRegistration;
  }

  // The ForwardedHeaderFilter is required to correctly assemble the redirect URL for OAUth2 login.
  // Without the filter, Spring generates an HTTP URL even though the container route is accessed through HTTPS.
  @Bean
  public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
    FilterRegistrationBean<ForwardedHeaderFilter> filterRegistrationBean = new FilterRegistrationBean<>();
    filterRegistrationBean.setFilter(new ForwardedHeaderFilter());
    filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return filterRegistrationBean;
  }

  @Bean
  @Order(0)
  public RequestContextListener requestContextListener() {
    return new RequestContextListener();
  }

  // Modify firewall in order to allow request details for child groups
  @Bean
  public HttpFirewall getHttpFirewall() {
    StrictHttpFirewall strictHttpFirewall = new StrictHttpFirewall();
    strictHttpFirewall.setAllowUrlEncodedPercent(true);
    strictHttpFirewall.setAllowUrlEncodedSlash(true);
    return strictHttpFirewall;
  }
}