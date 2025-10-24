package org.operaton.bpm.extension.keycloak;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable wrapper over KeycloakTenantQuery that can be used as a cache key.
 * Note: keep equals/hashcode in sync with the list of fields
 */
public final class CacheableKeycloakTenantQuery {
  private final String id;
  private final String[] ids;
  private final String name;
  private final String nameLike;
  private final String userId;
  private final String groupId;

  private final boolean includingGroups;

  private CacheableKeycloakTenantQuery(KeycloakTenantQuery delegate) {
    this.id = delegate.getId();
    this.ids = delegate.getIds();
    this.name = delegate.getName();
    this.nameLike = delegate.getNameLike();
    this.userId = delegate.getUserId();
    this.groupId = delegate.getGroupId();
    this.includingGroups = delegate.isIncludingGroups();
  }

  public static CacheableKeycloakTenantQuery of(KeycloakTenantQuery tenantQuery) {
    return new CacheableKeycloakTenantQuery(tenantQuery);
  }

  public String getId() {
    return id;
  }

  public String[] getIds() {
    return ids;
  }

  public String getName() {
    return name;
  }

  public String getNameLike() {
    return nameLike;
  }

  public String getUserId() {
    return userId;
  }

  public String getGroupId() { return groupId; }

  public boolean isIncludingGroups() { return includingGroups; }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CacheableKeycloakTenantQuery that = (CacheableKeycloakTenantQuery) o;
    return Objects.equals(id, that.id) && Arrays.equals(ids, that.ids) && Objects.equals(name, that.name)
        && Objects.equals(nameLike, that.nameLike) && Objects.equals(userId, that.userId)
        && Objects.equals(groupId, that.groupId) && Objects.equals(includingGroups, that.includingGroups);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, name, nameLike, userId, groupId,includingGroups);
    result = 31 * result + Arrays.hashCode(ids);
    return result;
  }
}
