package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Permission;
import org.operaton.bpm.engine.authorization.Resource;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.Tenant;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.KeycloakTenantQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.operaton.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.operaton.bpm.engine.authorization.Permissions.READ;
import static org.operaton.bpm.extension.keycloak.CacheableKeycloakTenantQuery.of;

/**
 * Tests tenant queries with Keycloak organizations.
 */
class KeycloakTenantQueryTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.useOrganizationsAsTenants.cfg.xml");

  @Test
  void queryNoFilter() {
    List<Tenant> tenantList = identityService.createTenantQuery().list();
    assertThat(tenantList).hasSize(3);
  }

  @Test
  void queryPaging() {
    List<Tenant> firstPage = identityService.createTenantQuery().listPage(0, 2);
    assertThat(firstPage).hasSize(2);

    List<Tenant> secondPage = identityService.createTenantQuery().listPage(2, 10);
    assertThat(secondPage).hasSize(1);

    assertThat(firstPage.stream().filter(secondPage::contains).count()).isZero();
  }

  @Test
  void filterByTenantId() {
    Tenant tenant = identityService.createTenantQuery().tenantId(tenantIdAlpha).singleResult();
    assertThat(tenant).isNotNull();
    assertThat(tenant.getId()).isEqualTo(tenantIdAlpha);
    assertThat(tenant.getName()).isEqualTo("Alpha");

    tenant = identityService.createTenantQuery().tenantId("non-existing").singleResult();
    assertThat(tenant).isNull();
  }

  @Test
  void filterByTenantIdIn() {
    List<Tenant> tenants = identityService.createTenantQuery().tenantIdIn(tenantIdAlpha, tenantIdBeta).list();
    assertThat(tenants).hasSize(2);

    tenants = identityService.createTenantQuery().tenantIdIn(tenantIdAlpha, "non-existing").list();
    assertThat(tenants).hasSize(1);
  }

  @Test
  void filterByTenantName() {
    Tenant tenant = identityService.createTenantQuery().tenantName("Alpha").singleResult();
    assertThat(tenant).isNotNull();
    assertThat(tenant.getId()).isEqualTo(tenantIdAlpha);

    tenant = identityService.createTenantQuery().tenantName("non-existing").singleResult();
    assertThat(tenant).isNull();
  }

  @Test
  void filterByTenantNameLike() {
    Tenant tenant = identityService.createTenantQuery().tenantNameLike("Al*").singleResult();
    assertThat(tenant).isNotNull();
    assertThat(tenant.getId()).isEqualTo(tenantIdAlpha);

    tenant = identityService.createTenantQuery().tenantNameLike("non*").singleResult();
    assertThat(tenant).isNull();
  }

  @Test
  void filterByUserId() {
    List<Tenant> managerTenants = identityService.createTenantQuery().userMember(userIdManager).list();
    assertThat(managerTenants).hasSize(2);

    List<Tenant> adminTenants = identityService.createTenantQuery().userMember(userIdOperatonAdmin).list();
    assertThat(adminTenants).hasSize(1);

    List<Tenant> noTenants = identityService.createTenantQuery().userMember(userIdHierarchy).list();
    assertThat(noTenants).isEmpty();
  }

  @Test
  void userQueryMemberOfTenant() {
    List<User> alphaUsers = identityService.createUserQuery().memberOfTenant(tenantIdAlpha).list();
    assertThat(alphaUsers).hasSize(2);
    assertThat(alphaUsers.stream().anyMatch(u -> userIdOperatonAdmin.equals(u.getId()))).isTrue();
    assertThat(alphaUsers.stream().anyMatch(u -> userIdManager.equals(u.getId()))).isTrue();

    List<User> betaUsers = identityService.createUserQuery().memberOfTenant(tenantIdBeta).list();
    assertThat(betaUsers).hasSize(2);
    assertThat(betaUsers.stream().anyMatch(u -> userIdManager.equals(u.getId()))).isTrue();
    assertThat(betaUsers.stream().anyMatch(u -> userIdTeamlead.equals(u.getId()))).isTrue();
  }

  @Test
  void groupQueryMemberOfTenantNoOp() {
    List<Group> groups = identityService.createGroupQuery().memberOfTenant(tenantIdAlpha).list();
    assertThat(groups).hasSize(9);
  }

  @Test
  void orderByTenantId() {
    List<Tenant> tenants = identityService.createTenantQuery().orderByTenantId().desc().list();
    assertThat(tenants).hasSize(3);
    assertThat(tenants.get(0).getId().compareTo(tenants.get(1).getId())).isGreaterThan(0);
    assertThat(tenants.get(1).getId().compareTo(tenants.get(2).getId())).isGreaterThan(0);
  }

  @Test
  void orderByTenantName() {
    List<Tenant> tenants = identityService.createTenantQuery().orderByTenantName().list();
    assertThat(tenants).hasSize(3);
    assertThat(tenants.get(0).getName().compareTo(tenants.get(1).getName())).isLessThan(0);
    assertThat(tenants.get(1).getName().compareTo(tenants.get(2).getName())).isLessThan(0);
  }

  @Test
  void authenticatedUserCanQueryOwnTenants() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);
      identityService.setAuthenticatedUserId(userIdManager);

      assertThat(identityService.createTenantQuery().userMember(userIdManager).count()).isEqualTo(2);
      assertThat(identityService.createTenantQuery().userMember(userIdOperatonAdmin).count()).isZero();
    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

  @Test
  void authorizationForTenantRead() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);
      createGrantAuthorization(Resources.TENANT, tenantIdAlpha, userIdManager, READ);
      identityService.setAuthenticatedUserId(userIdManager);

      List<Tenant> tenants = identityService.createTenantQuery().list();
      assertThat(tenants).hasSize(1);
      assertThat(tenants.get(0).getId()).isEqualTo(tenantIdAlpha);
    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

  @Test
  void queryObjectEquality() {
    KeycloakTenantQuery q1 = (KeycloakTenantQuery) identityService.createTenantQuery();
    KeycloakTenantQuery q2 = (KeycloakTenantQuery) identityService.createTenantQuery();

    assertThat(q2)
            .isNotSameAs(q1)
            .isNotEqualTo(q1);

    assertThat(of(q2)).isNotSameAs(of(q1));
    assertThat(of(q2)).isEqualTo(of(q1));

    q1.tenantId(tenantIdAlpha);
    assertThat(of(q2)).isNotEqualTo(of(q1));

    q2.tenantId(tenantIdAlpha);
    assertThat(of(q2)).isEqualTo(of(q1));

    q1.userMember(userIdManager);
    assertThat(of(q2)).isNotEqualTo(of(q1));

    q2.userMember(userIdManager);
    assertThat(of(q2)).isEqualTo(of(q1));

    q1.tenantNameLike("Al*");
    assertThat(of(q2)).isNotEqualTo(of(q1));

    q2.tenantNameLike("Al*");
    assertThat(of(q2)).isEqualTo(of(q1));
  }

  protected void createGrantAuthorization(Resource resource,
                                          String resourceId,
                                          String userId,
                                          Permission... permissions) {
    Authorization authorization = createAuthorization(AUTH_TYPE_GRANT, resource, resourceId);
    authorization.setUserId(userId);
    for (Permission permission : permissions) {
      authorization.addPermission(permission);
    }
    authorizationService.saveAuthorization(authorization);
  }

  protected Authorization createAuthorization(int type, Resource resource, String resourceId) {
    Authorization authorization = authorizationService.createNewAuthorization(type);

    authorization.setResource(resource);
    if (resourceId != null) {
      authorization.setResourceId(resourceId);
    }

    return authorization;
  }
}
