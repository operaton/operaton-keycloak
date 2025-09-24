package org.operaton.bpm.extension.keycloak;

import java.util.Collections;
import java.util.List;

import org.operaton.bpm.engine.identity.Tenant;
import org.operaton.bpm.engine.impl.Page;
import org.operaton.bpm.engine.impl.TenantQueryImpl;
import org.operaton.bpm.engine.impl.interceptor.CommandContext;
import org.operaton.bpm.engine.impl.interceptor.CommandExecutor;
import org.operaton.bpm.engine.impl.persistence.entity.TenantEntity;

/**
 * Keycloak specific tenant query implementation.
 */
public class KeycloakTenantQuery extends TenantQueryImpl {

  private static final long serialVersionUID = 1L;

  public KeycloakTenantQuery() {
    super();
  }

  public KeycloakTenantQuery(CommandExecutor commandExecutor) {
    super(commandExecutor);
  }

  @Override
  public long executeCount(CommandContext commandContext) {
    final KeycloakIdentityProviderSession provider = getKeycloakIdentityProvider(commandContext);

    if (provider.keycloakConfiguration.isUseOrganizationsAsTenants()) {
      return provider.findTenantsCountByQueryCriteria(this);
    } else {
      return 0;
    }
  }

  @Override
  public List<Tenant> executeList(CommandContext commandContext, Page page) {
    final KeycloakIdentityProviderSession provider = getKeycloakIdentityProvider(commandContext);

    if (provider.keycloakConfiguration.isUseOrganizationsAsTenants()) {
      return provider.findTenantsByQueryCriteria(this);
    } else {
      return Collections.emptyList();
    }
  }

  protected KeycloakIdentityProviderSession getKeycloakIdentityProvider(CommandContext commandContext) {
    return (KeycloakIdentityProviderSession) commandContext.getReadOnlyIdentityProvider();
  }
}
