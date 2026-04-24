package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Permission;
import org.operaton.bpm.engine.authorization.Resource;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.Tenant;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.operaton.bpm.extension.keycloak.CacheableKeycloakTenantQuery;
import org.operaton.bpm.extension.keycloak.KeycloakTenantQuery;

import static org.junit.Assert.assertNotEquals;
import static org.operaton.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.operaton.bpm.engine.authorization.Permissions.READ;

/**
 * Tests tenant queries with Keycloak organizations.
 */
public class KeycloakTenantQueryTest extends AbstractKeycloakIdentityProviderTest {

  public static Test suite() {
    return new TestSetup(new TestSuite(KeycloakTenantQueryTest.class)) {

      // @BeforeClass
      protected void setUp() {
        ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createProcessEngineConfigurationFromResource(
            "operaton.useOrganizationsAsTenants.cfg.xml");
        configureKeycloakIdentityProviderPlugin(config);
        PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
      }

      // @AfterClass
      protected void tearDown() {
        PluggableProcessEngineTestCase.cachedProcessEngine.close();
        PluggableProcessEngineTestCase.cachedProcessEngine = null;
      }
    };
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    processEngine.getAuthorizationService()
        .createAuthorizationQuery()
        .list()
        .forEach(a -> processEngine.getAuthorizationService().deleteAuthorization(a.getId()));
  }

  public void testQueryNoFilter() {
    List<Tenant> tenantList = identityService.createTenantQuery().list();
    assertEquals(3, tenantList.size());
  }

  public void testQueryPaging() {
    List<Tenant> firstPage = identityService.createTenantQuery().listPage(0, 2);
    assertEquals(2, firstPage.size());

    List<Tenant> secondPage = identityService.createTenantQuery().listPage(2, 10);
    assertEquals(1, secondPage.size());

    assertEquals(0, firstPage.stream().filter(secondPage::contains).count());
  }

  public void testFilterByTenantId() {
    Tenant tenant = identityService.createTenantQuery().tenantId(tenantIdAlpha).singleResult();
    assertNotNull(tenant);
    assertEquals(tenantIdAlpha, tenant.getId());
    assertEquals("Alpha", tenant.getName());

    tenant = identityService.createTenantQuery().tenantId("non-existing").singleResult();
    assertNull(tenant);
  }

  public void testFilterByTenantIdIn() {
    List<Tenant> tenants = identityService.createTenantQuery().tenantIdIn(tenantIdAlpha, tenantIdBeta).list();
    assertEquals(2, tenants.size());

    tenants = identityService.createTenantQuery().tenantIdIn(tenantIdAlpha, "non-existing").list();
    assertEquals(1, tenants.size());
  }

  public void testFilterByTenantName() {
    Tenant tenant = identityService.createTenantQuery().tenantName("Alpha").singleResult();
    assertNotNull(tenant);
    assertEquals(tenantIdAlpha, tenant.getId());

    tenant = identityService.createTenantQuery().tenantName("non-existing").singleResult();
    assertNull(tenant);
  }

  public void testFilterByTenantNameLike() {
    Tenant tenant = identityService.createTenantQuery().tenantNameLike("Al*").singleResult();
    assertNotNull(tenant);
    assertEquals(tenantIdAlpha, tenant.getId());

    tenant = identityService.createTenantQuery().tenantNameLike("non*").singleResult();
    assertNull(tenant);
  }

  public void testFilterByUserId() {
    List<Tenant> managerTenants = identityService.createTenantQuery().userMember(userIdManager).list();
    assertEquals(2, managerTenants.size());

    List<Tenant> adminTenants = identityService.createTenantQuery().userMember(userIdOperatonAdmin).list();
    assertEquals(1, adminTenants.size());

    List<Tenant> noTenants = identityService.createTenantQuery().userMember(userIdHierarchy).list();
    assertEquals(0, noTenants.size());
  }

  public void testUserQueryMemberOfTenant() {
    List<User> alphaUsers = identityService.createUserQuery().memberOfTenant(tenantIdAlpha).list();
    assertEquals(2, alphaUsers.size());
    assertTrue(alphaUsers.stream().anyMatch(u -> userIdOperatonAdmin.equals(u.getId())));
    assertTrue(alphaUsers.stream().anyMatch(u -> userIdManager.equals(u.getId())));

    List<User> betaUsers = identityService.createUserQuery().memberOfTenant(tenantIdBeta).list();
    assertEquals(2, betaUsers.size());
    assertTrue(betaUsers.stream().anyMatch(u -> userIdManager.equals(u.getId())));
    assertTrue(betaUsers.stream().anyMatch(u -> userIdTeamlead.equals(u.getId())));
  }

  public void testGroupQueryMemberOfTenantNoOp() {
    List<Group> groups = identityService.createGroupQuery().memberOfTenant(tenantIdAlpha).list();
    assertEquals(9, groups.size());
  }

  public void testOrderByTenantId() {
    List<Tenant> tenants = identityService.createTenantQuery().orderByTenantId().desc().list();
    assertEquals(3, tenants.size());
    assertTrue(tenants.get(0).getId().compareTo(tenants.get(1).getId()) > 0);
    assertTrue(tenants.get(1).getId().compareTo(tenants.get(2).getId()) > 0);
  }

  public void testOrderByTenantName() {
    List<Tenant> tenants = identityService.createTenantQuery().orderByTenantName().list();
    assertEquals(3, tenants.size());
    assertTrue(tenants.get(0).getName().compareTo(tenants.get(1).getName()) < 0);
    assertTrue(tenants.get(1).getName().compareTo(tenants.get(2).getName()) < 0);
  }

  public void testAuthenticatedUserCanQueryOwnTenants() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);
      identityService.setAuthenticatedUserId(userIdManager);

      assertEquals(2, identityService.createTenantQuery().userMember(userIdManager).count());
      assertEquals(0, identityService.createTenantQuery().userMember(userIdOperatonAdmin).count());
    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

  public void testAuthorizationForTenantRead() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);
      createGrantAuthorization(Resources.TENANT, tenantIdAlpha, userIdManager, READ);
      identityService.setAuthenticatedUserId(userIdManager);

      List<Tenant> tenants = identityService.createTenantQuery().list();
      assertEquals(1, tenants.size());
      assertEquals(tenantIdAlpha, tenants.get(0).getId());
    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

  public void testQueryObjectEquality() {
    KeycloakTenantQuery q1 = (KeycloakTenantQuery) identityService.createTenantQuery();
    KeycloakTenantQuery q2 = (KeycloakTenantQuery) identityService.createTenantQuery();

    assertNotSame(q1, q2);
    assertNotEquals(q1, q2);

    assertNotSame(CacheableKeycloakTenantQuery.of(q1), CacheableKeycloakTenantQuery.of(q2));
    assertEquals(CacheableKeycloakTenantQuery.of(q1), CacheableKeycloakTenantQuery.of(q2));

    q1.tenantId(tenantIdAlpha);
    assertNotEquals(CacheableKeycloakTenantQuery.of(q1), CacheableKeycloakTenantQuery.of(q2));

    q2.tenantId(tenantIdAlpha);
    assertEquals(CacheableKeycloakTenantQuery.of(q1), CacheableKeycloakTenantQuery.of(q2));

    q1.userMember(userIdManager);
    assertNotEquals(CacheableKeycloakTenantQuery.of(q1), CacheableKeycloakTenantQuery.of(q2));

    q2.userMember(userIdManager);
    assertEquals(CacheableKeycloakTenantQuery.of(q1), CacheableKeycloakTenantQuery.of(q2));

    q1.tenantNameLike("Al*");
    assertNotEquals(CacheableKeycloakTenantQuery.of(q1), CacheableKeycloakTenantQuery.of(q2));

    q2.tenantNameLike("Al*");
    assertEquals(CacheableKeycloakTenantQuery.of(q1), CacheableKeycloakTenantQuery.of(q2));
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
