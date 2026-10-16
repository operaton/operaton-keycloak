package org.operaton.bpm.extension.keycloak.test;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.operaton.bpm.engine.BadUserRequestException;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Permission;
import org.operaton.bpm.engine.authorization.Resource;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.extension.keycloak.KeycloakUserQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.operaton.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.operaton.bpm.extension.keycloak.CacheableKeycloakUserQuery.of;

/**
 * User query test for the Keycloak identity provider.
 */
class KeycloakUserQueryTest extends AbstractKeycloakIdentityProviderTest {

  @RegisterExtension
  static ProcessEngineExtension engineExtension = keycloakEngine("operaton.cfg.xml");

  @Test
  void queryNoFilter() {
    List<User> result = identityService.createUserQuery().list();
    assertThat(result).hasSize(5);
  }

  @Test
  void queryUnlimitedList() {
    List<User> result = identityService.createUserQuery().unlimitedList();
    assertThat(result).hasSize(5);
  }

  @Test
  void queryPaging() {
    // First page
    List<User> result = identityService.createUserQuery().listPage(0, 2);
    assertThat(result).hasSize(2);

    // Next page
    List<User> resultNext = identityService.createUserQuery().listPage(2, 10);
    assertThat(resultNext).hasSize(3);

    // unique results
    assertThat(result.stream().filter(resultNext::contains).count()).isZero();
  }

  @Test
  void filterByUserId() {
    User user = identityService.createUserQuery().userId("operaton@accso.de").singleResult();
    assertThat(user).isNotNull();

    // validate user
    assertThat(user.getId()).isEqualTo("operaton@accso.de");
    assertThat(user.getFirstName()).isEqualTo("Admin");
    assertThat(user.getLastName()).isEqualTo("Operaton");
    assertThat(user.getEmail()).isEqualTo("operaton@accso.de");

    user = identityService.createUserQuery().userId("non-existing").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void filterByUserIdIn() {
    List<User> users = identityService.createUserQuery()
        .userIdIn("operaton@accso.de", "gunnar.von-der-beck@accso.de")
        .list();
    assertThat(users).isNotNull();
    assertThat(users).hasSize(2);

    users = identityService.createUserQuery().userIdIn("operaton@accso.de", "non-existing").list();
    assertThat(users).isNotNull();
    assertThat(users).hasSize(1);

    users = identityService.createUserQuery().userIdIn("operaton@accso.de").list();
    assertThat(users).isNotNull();
    assertThat(users).hasSize(1);
  }

  @Test
  void filterByFirstname() {
    User user = identityService.createUserQuery().userFirstName("Gunnar").singleResult();
    assertThat(user).isNotNull();

    user = identityService.createUserQuery().userFirstName("non-existing").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void filterByFirstnameLike() {
    User user = identityService.createUserQuery().userFirstNameLike("Gun*").singleResult();
    assertThat(user).isNotNull();

    user = identityService.createUserQuery().userFirstNameLike("non-exist*").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void filterByLastname() {
    User user = identityService.createUserQuery().userLastName("von der Beck").singleResult();
    assertThat(user).isNotNull();

    user = identityService.createUserQuery().userLastName("non-existing").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void filterByLastnameLike() {
    User user = identityService.createUserQuery().userLastNameLike("von*").singleResult();
    assertThat(user).isNotNull();

    user = identityService.createUserQuery().userLastNameLike("non-exist*").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void filterByFirstnameLikeAndLastnameLike() {
    User user = identityService.createUserQuery().userFirstNameLike("*n%").userLastNameLike("von*").singleResult();
    assertThat(user).isNotNull();

    user = identityService.createUserQuery().userFirstNameLike("nox-exist*").userLastNameLike("von*").singleResult();
    assertThat(user).isNull();

    user = identityService.createUserQuery().userFirstNameLike("*n%").userLastNameLike("non-exist*").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void filterByFirstLastNameEmailUsingPartsOfNames() {
    User user = identityService.createUserQuery()
        .userFirstNameLike("Gun*")
        .userLastNameLike("von*")
        .userEmailLike("*accso.de")
        .singleResult();
    assertThat(user).isNotNull();

    user = identityService.createUserQuery().userFirstName("Gun").singleResult();
    assertThat(user).isNull();

    user = identityService.createUserQuery().userLastName("von").singleResult();
    assertThat(user).isNull();

    user = identityService.createUserQuery().userEmail("accso.de").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void filterByFirstNameExactEmailLike() {
    assertThat(identityService.createUserQuery().userEmailLike("%accso.de").count()).isEqualTo(2);

    // Must deliver a single result
    User user = identityService.createUserQuery().userFirstName("Gunnar").userEmailLike("%accso.de").singleResult();
    assertThat(user).isNotNull();
  }

  @Test
  void filterByEmail() {
    User user = identityService.createUserQuery().userEmail("operaton@accso.de").singleResult();
    assertThat(user).isNotNull();

    user = identityService.createUserQuery().userEmail("non-exist*").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void filterByEmailLike() {
    User user = identityService.createUserQuery().userEmailLike("operaton@*").singleResult();
    assertThat(user).isNotNull();
    user = identityService.createUserQuery().userEmailLike("operaton@%").singleResult();
    assertThat(user).isNotNull();

    List<User> users = identityService.createUserQuery().userEmailLike("%@accso.de").list();
    assertThat(users).isNotNull();
    assertThat(users).hasSize(2);

    user = identityService.createUserQuery().userEmailLike("non-exist*").singleResult();
    assertThat(user).isNull();
  }

  @Test
  void filterByGroupId() {
    List<User> result = identityService.createUserQuery().memberOfGroup(groupIdTeamlead).list();
    assertThat(result).hasSize(2);

    result = identityService.createUserQuery().memberOfGroup("non-exist").list();
    assertThat(result).hasSize(0);
  }

  @Test
  void filterByGroupIdAndFirstname() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(groupIdTeamlead)
        .userFirstName("Gunnar")
        .list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterByGroupIdAndId() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(groupIdTeamlead)
        .userId("gunnar.von-der-beck@accso.de")
        .list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterByGroupIdAndLastname() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(groupIdTeamlead)
        .userLastName("von der Beck")
        .list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterByGroupIdAndEmail() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(groupIdTeamlead)
        .userEmail("gunnar.von-der-beck@accso.de")
        .list();
    assertThat(result).hasSize(1);
  }

  @Test
  void filterByGroupIdAndEmailLike() {
    List<User> result = identityService.createUserQuery()
        .memberOfGroup(groupIdTeamlead)
        .userEmailLike("*@accso.de")
        .list();
    assertThat(result).hasSize(1);
  }

  @Test
  void orderByUserId() {
    List<User> result = identityService.createUserQuery().orderByUserId().desc().list();
    assertThat(result).hasSize(5);
    assertThat(result.get(0).getId().compareTo(result.get(1).getId())).isGreaterThan(0);
    assertThat(result.get(1).getId().compareTo(result.get(2).getId())).isGreaterThan(0);
  }

  @Test
  void orderByUserEmail() {
    List<User> result = identityService.createUserQuery().orderByUserEmail().list();
    assertThat(result).hasSize(5);
    assertThat(result.get(0).getEmail().compareTo(result.get(1).getEmail())).isLessThan(0);
    assertThat(result.get(1).getEmail().compareTo(result.get(2).getEmail())).isLessThan(0);
  }

  @Test
  void orderByUserFirstName() {
    List<User> result = identityService.createUserQuery().orderByUserFirstName().desc().list();
    assertThat(result).hasSize(5);
    assertThat(result.get(0).getFirstName().compareTo(result.get(1).getFirstName())).isGreaterThan(0);
    assertThat(result.get(1).getFirstName().compareTo(result.get(2).getFirstName())).isGreaterThan(0);
  }

  @Test
  void orderByUserLastName() {
    List<User> result = identityService.createUserQuery().orderByUserLastName().list();
    assertThat(result).hasSize(5);
    assertThat(result.get(0).getLastName().compareTo(result.get(1).getLastName())).isLessThan(0);
    assertThat(result.get(1).getLastName().compareTo(result.get(2).getLastName())).isLessThan(0);
  }

  @Test
  void authenticatedUserSeesHimself() {
    try {
      processEngineConfiguration.setAuthorizationEnabled(true);

      identityService.setAuthenticatedUserId("non-existing");
      assertThat(identityService.createUserQuery().count()).isZero();

      identityService.setAuthenticatedUserId("operaton@accso.de");
      assertThat(identityService.createUserQuery().count()).isOne();

    } finally {
      processEngineConfiguration.setAuthorizationEnabled(false);
      identityService.clearAuthentication();
    }
  }

  @Test
  void nativeQueryFail() {
    try {
      identityService.createNativeUserQuery();
      fail("Native queries are not supported in Keycloak case.");
    } catch (BadUserRequestException ex) {
      assertThat(ex.getMessage()).withFailMessage("Wrong exception").contains("Native user queries are not supported for Keycloak");
    }

  }

  @Test
  void queryObjectEquality() {

    KeycloakUserQuery q1 = (KeycloakUserQuery) identityService.createUserQuery();
    KeycloakUserQuery q2 = (KeycloakUserQuery) identityService.createUserQuery();

    assertThat(q2)
            .isNotSameAs(q1)
            // not the same object (identity)
            .isNotEqualTo(q1); // not equal

    assertThat(of(q2)).isNotSameAs(of(q1)); // not the same object
    assertThat(of(q2)).isEqualTo(of(q1)); // but are equal

    q1.userId("id1");

    // not equal because first query has a filter
    assertThat(of(q2)).isNotEqualTo(of(q1));

    q2.userId("id1");

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
