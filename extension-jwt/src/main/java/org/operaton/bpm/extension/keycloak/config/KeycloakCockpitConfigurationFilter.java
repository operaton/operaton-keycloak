package org.operaton.bpm.extension.keycloak.config;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

public class KeycloakCockpitConfigurationFilter implements Filter {
  public static String KEYCLOAK_OPTIONS_PATH = "/app/keycloak/keycloak-options.json";

  KeycloakCockpitConfiguration configuration;

  public KeycloakCockpitConfigurationFilter(KeycloakCockpitConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    PrintWriter out = response.getWriter();
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    out.print(configuration.toJSON());
    out.flush();
  }

}
