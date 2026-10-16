package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Permission;
import org.operaton.bpm.engine.authorization.Resource;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.KeycloakGroupQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.operaton.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.operaton.bpm.extension.keycloak.CacheableKeycloakGroupQuery.of;

/**
 * Tests group queries.
 */
class KeycloakGroupQueryTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.cfg.xml");

  @Test
  void queryNoFilter() {
    List<Group> groupList = identityService.createGroupQuery().list();
    assertThat(groupList).hasSize(9);
  }

  @Test
  void queryUnlimitedList() {
    List<Group> groupList = identityService.createGroupQuery().unlimitedList();
    assertThat(groupList).hasSize(9);
  }

  @Test
  void queryPaging() {
    // First page
    List<Group> result = identityService.createGroupQuery().listPage(0, 3);
    assertThat(result).hasSize(3);

    // Next page
    List<Group> resultNext = identityService.createGroupQuery().listPage(3, 3);
    assertThat(resultNext).hasSize(3);

    // Next page
    List<Group> resultLast = identityService.createGroupQuery().listPage(6, 10);
    assertThat(resultLast).hasSize(3);

    // unique results
    assertThat(result.stream().filter(resultNext::contains).count()).isZero();
    assertThat(result.stream().filter(resultLast::contains).count()).isZero();
  }

  @Test
  void filterByGroupId() {
    Group group = identityService.createGroupQuery().groupId(groupIdAdmin).singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo(groupIdAdmin);
    assertThat(group.getName()).isEqualTo("operaton-admin");
    assertThat(group.getType()).isEqualTo("SYSTEM");

    group = identityService.createGroupQuery().groupId("whatever").singleResult();
    assertThat(group).isNull();
  }

  @Test
  void filterByUserId() {
    List<Group> result = identityService.createGroupQuery().groupMember("operaton@accso.de").list();
    assertThat(result).hasSize(1);
  }

  @Test
  void authenticatedUserCanQueryOwnGroups() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);
      identityService.setAuthenticatedUserId("johnfoo@gmail.com");

      assertThat(identityService.createGroupQuery().groupMember("operaton@accso.de").count()).isZero();
      assertThat(identityService.createGroupQuery().groupMember("johnfoo@gmail.com").count()).isEqualTo(2);

    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

  /* The REST API of Keycloak (get list(!) of groups) does not deliver group attributes :-(
  public void testFilterByGroupType() {
    List<Group> result = identityService.createGroupQuery().groupType("SYSTEM").list();
    assertEquals(2, result.size());
  }
  */

  @Test
  void filterByGroupTypeAndGroupId() {
    Group group = identityService.createGroupQuery()
        .groupType("SYSTEM")
        .groupId(groupIdSystemReadonly)
        .singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo(groupIdSystemReadonly);
    assertThat(group.getName()).isEqualTo("cam-read-only");
    assertThat(group.getType()).isEqualTo("SYSTEM");
  }

  @Test
  void filterByGroupIdIn() {
    List<Group> groups = identityService.createGroupQuery().groupIdIn(groupIdAdmin, groupIdManager).list();

    assertThat(groups).hasSize(2);
    for (Group group : groups) {
      if (!"operaton-admin".equals(group.getName()) && !"manager".equals(group.getName())) {
        fail("");
      }
    }

    groups = identityService.createGroupQuery().groupIdIn(groupIdManager, "non-existent").list();
    assertThat(groups).hasSize(1);
    assertThat(groups.get(0).getName()).isEqualTo("manager");

    groups = identityService.createGroupQuery().groupIdIn(groupIdManager).list();
    assertThat(groups).hasSize(1);
    assertThat(groups.get(0).getName()).isEqualTo("manager");

  }

  @Test
  void filterByGroupIdInAndType() {
    Group group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdManager)
        .groupType("WORKFLOW")
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("manager");

    group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdManager)
        .groupType("SYSTEM")
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");
  }

  @Test
  void filterByGroupIdInAndUserId() {
    Group group = identityService.createGroupQuery()
        .groupIdIn(groupIdAdmin, groupIdManager)
        .groupMember("operaton@accso.de")
        .singleResult();
    assertThat(group).isNotNull();
    assertThat(group.getName()).isEqualTo("operaton-admin");
  }

  @Test
  void filterByGroupName() {
    Group group = identityService.createGroupQuery().groupName("manager").singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo(groupIdManager);
    assertThat(group.getName()).isEqualTo("manager");

    group = identityService.createGroupQuery().groupName("whatever").singleResult();
    assertThat(group).isNull();
  }

  @Test
  void filterByGroupNameLike() {
    Group group = identityService.createGroupQuery().groupNameLike("manage*").singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo(groupIdManager);
    assertThat(group.getName()).isEqualTo("manager");

    group = identityService.createGroupQuery().groupNameLike("what*").singleResult();
    assertThat(group).isNull();
  }

  @Test
  void filterByGroupNameAndGroupNameLike() {
    Group group = identityService.createGroupQuery().groupNameLike("ma*").groupName("manager").singleResult();
    assertThat(group).isNotNull();

    // validate result
    assertThat(group.getId()).isEqualTo(groupIdManager);
    assertThat(group.getName()).isEqualTo("manager");
  }

  @Test
  void filterByGroupMember() {
    List<Group> list = identityService.createGroupQuery().groupMember("operaton@accso.de").list();
    assertThat(list).hasSize(1);
    list = identityService.createGroupQuery().groupMember("Gunnar.von-der-Beck@accso.de").list();
    assertThat(list).hasSize(2);
    list = identityService.createGroupQuery().groupMember("hans.mustermann@tradermail.info").list();
    assertThat(list).hasSize(1);
    list = identityService.createGroupQuery().groupMember("non-existing").list();
    assertThat(list).hasSize(0);
  }

  @Test
  void orderByGroupId() {
    List<Group> groupList = identityService.createGroupQuery().orderByGroupId().desc().list();
    assertThat(groupList).hasSize(9);
    assertThat(groupList.get(0).getId().compareTo(groupList.get(1).getId())).isGreaterThan(0);
    assertThat(groupList.get(1).getId().compareTo(groupList.get(2).getId())).isGreaterThan(0);
    assertThat(groupList.get(2).getId().compareTo(groupList.get(3).getId())).isGreaterThan(0);
    assertThat(groupList.get(5).getId().compareTo(groupList.get(6).getId())).isGreaterThan(0);
    assertThat(groupList.get(6).getId().compareTo(groupList.get(7).getId())).isGreaterThan(0);
  }

  @Test
  void orderByGroupName() {
    List<Group> groupList = identityService.createGroupQuery().orderByGroupName().list();
    assertThat(groupList).hasSize(9);
    assertThat(groupList.get(0).getName().compareTo(groupList.get(1).getName())).isLessThan(0);
    assertThat(groupList.get(1).getName().compareTo(groupList.get(2).getName())).isLessThan(0);
    assertThat(groupList.get(2).getName().compareTo(groupList.get(3).getName())).isLessThan(0);
    assertThat(groupList.get(5).getName().compareTo(groupList.get(6).getName())).isLessThan(0);
    assertThat(groupList.get(6).getName().compareTo(groupList.get(7).getName())).isLessThan(0);
  }

  @Test
  void orderByGroupType() {
    List<Group> groupList = identityService.createGroupQuery().orderByGroupType().desc().list();
    assertThat(groupList).hasSize(9);
    assertThat(groupList.get(0).getType().compareTo(groupList.get(1).getType())).isGreaterThanOrEqualTo(0);
    assertThat(groupList.get(1).getType().compareTo(groupList.get(2).getType())).isGreaterThanOrEqualTo(0);
    assertThat(groupList.get(2).getType().compareTo(groupList.get(3).getType())).isGreaterThanOrEqualTo(0);
    assertThat(groupList.get(5).getType().compareTo(groupList.get(6).getType())).isGreaterThanOrEqualTo(0);
    assertThat(groupList.get(6).getType().compareTo(groupList.get(7).getType())).isGreaterThanOrEqualTo(0);
  }

  @Test
  void queryObjectEquality() {

    KeycloakGroupQuery q1 = (KeycloakGroupQuery) identityService.createGroupQuery();
    KeycloakGroupQuery q2 = (KeycloakGroupQuery) identityService.createGroupQuery();

    assertThat(q2)
            .isNotSameAs(q1)
            // not the same object (by identity)
            .isNotEqualTo(q1); // not equal

    assertThat(of(q2)).isNotSameAs(of(q1)); // not the same object
    assertThat(of(q2)).isEqualTo(of(q1)); // but they are equal

    q1.groupId("id1");

    // not equal because first query has a filter
    assertThat(of(q2)).isNotEqualTo(of(q1));

    q2.groupId("id1");

    // equal now because second query also has same filter
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
