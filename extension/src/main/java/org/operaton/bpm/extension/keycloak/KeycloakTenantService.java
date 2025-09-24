package org.operaton.bpm.extension.keycloak;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.operaton.bpm.engine.identity.Tenant;
import org.operaton.bpm.engine.impl.Direction;
import org.operaton.bpm.engine.impl.QueryOrderingProperty;
import org.operaton.bpm.engine.impl.TenantQueryProperty;
import org.operaton.bpm.engine.impl.identity.IdentityProviderException;
import org.operaton.bpm.engine.impl.persistence.entity.TenantEntity;
import org.operaton.bpm.extension.keycloak.json.JsonException;
import org.operaton.bpm.extension.keycloak.rest.KeycloakRestTemplate;
import org.operaton.bpm.extension.keycloak.util.KeycloakPluginLogger;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.operaton.bpm.engine.authorization.Permissions.READ;
import static org.operaton.bpm.engine.authorization.Resources.TENANT;
import static org.operaton.bpm.extension.keycloak.json.JsonUtil.*;

/**
 * Implementation of Organization queries against Keycloak's REST API.
 */
public class KeycloakTenantService extends KeycloakServiceBase {

  /**
   * Default constructor.
   *
   * @param keycloakConfiguration   the Keycloak configuration
   * @param restTemplate            REST template
   * @param keycloakContextProvider Keycloak context provider
   */
  public KeycloakTenantService(KeycloakConfiguration keycloakConfiguration,
                               KeycloakRestTemplate restTemplate,
                               KeycloakContextProvider keycloakContextProvider) {
    super(keycloakConfiguration, restTemplate, keycloakContextProvider);
  }

  /**
   * Requests tenants.
   *
   * @param query the tenant query
   * @return list of matching tenants
   */
  public List<Tenant> requestTenants(CacheableKeycloakTenantQuery query) {
    List<Tenant> tenantList = new ArrayList<>();

    try {
      // get groups according to search criteria
      ResponseEntity<String> response;

      if (StringUtils.hasLength(query.getId())) {
        response = requestOrganizationById(query.getId());
      } else if (query.getIds() != null && query.getIds().length == 1) {
        response = requestOrganizationById(query.getIds()[0]);
      } else {
        String tenantFilter = createOrganizationSearchFilter(query); // only pre-filter of names possible

        // if a user filter is specified, use the users organizations endpoint.
        // No need to evaluate groups filters because all groups are in all organizations

        String url = StringUtils.hasLength(query.getUserId()) ?
                keycloakConfiguration.getKeycloakAdminUrl() + "/organizations/members/" + query.getUserId() + "/organizations" : keycloakConfiguration.getKeycloakAdminUrl() + "/organizations";

        response = restTemplate.exchange(url + tenantFilter, HttpMethod.GET, String.class);
      }
      if (!response.getStatusCode().equals(HttpStatus.OK)) {
        throw new IdentityProviderException(
            "Unable to read organizations from " + keycloakConfiguration.getKeycloakAdminUrl() + ": HTTP status code "
                + response.getStatusCode().value());
      }

      JsonArray searchResult = parseAsJsonArray(response.getBody());

      for (int i = 0; i < searchResult.size(); i++) {
        tenantList.add(transformOrganization(getJsonObjectAtIndex(searchResult, i)));
      }

    } catch (RestClientException | JsonException rce) {
      throw new IdentityProviderException("Unable to query organizations", rce);
    }

    return tenantList;
  }

  /**
   * Post processes a Keycloak query result.
   *
   * @param query        the original query
   * @param tenantList   the full list of results returned from Keycloak without client side filters
   * @param resultLogger the log accumulator
   * @return final result with client side filtered, sorted and paginated list of groups
   */
  public List<Tenant> postProcessResults(KeycloakTenantQuery query, List<Tenant> tenantList, StringBuilder resultLogger) {
    // apply client side filtering
    Stream<Tenant> processed = tenantList.stream().filter(tenant -> isValid(query, tenant, resultLogger));

    // sort tenants according to query criteria
    if (!query.getOrderingProperties().isEmpty()) {
      processed = processed.sorted(new TenantComparator(query.getOrderingProperties()));
    }

    // paging
    if ((query.getFirstResult() > 0) || (query.getMaxResults() < Integer.MAX_VALUE)) {
      processed = processed.skip(query.getFirstResult()).limit(query.getMaxResults());
    }

    // tenant queries in Keycloak do not consider the max attribute within the search request
    return processed.limit(keycloakConfiguration.getMaxResultSize()).toList();
  }

  /**
   * Post-processing query filter. Checks if a single group is valid.
   *
   * @param query        the original query
   * @param tenant       the group to validate
   * @param resultLogger the log accumulator
   * @return a boolean indicating if the group is valid for current query
   */
  private boolean isValid(KeycloakTenantQuery query, Tenant tenant, StringBuilder resultLogger) {
    // client side check of further query filters
    if (!matches(query.getId(), tenant.getId())) {
      return false;
    }
    if (!matches(query.getIds(), tenant.getId())) {
      return false;
    }
    if (!matches(query.getName(), tenant.getName())) {
      return false;
    }
    if (!matchesLike(query.getNameLike(), tenant.getName())) {
      return false;
    }

    // authenticated user is always allowed to query his own tenants
    // otherwise READ authentication is required
    boolean isAuthenticatedUser = isAuthenticatedUser(query.getUserId());
    if (isAuthenticatedUser || isAuthorized(READ, TENANT, tenant.getId())) {
      if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
        resultLogger.append(tenant);
        resultLogger.append(", ");
      }
      return true;
    }

    return false;
  }

  /**
   * Creates a Keycloak organization search filter query
   *
   * @param query the tenant query
   * @return request query
   */
  private String createOrganizationSearchFilter(CacheableKeycloakTenantQuery query) {
    StringBuilder filter = new StringBuilder();
    boolean hasSearch = false;
    if (StringUtils.hasLength(query.getName())) {
      // search = A String representing either an organization name or domain
      addArgument(filter, "search", query.getName());

      // exact = Boolean which defines whether the param 'search' must match exactly or not
      addArgument(filter, "exact", "true");
    }
    else if (StringUtils.hasLength(query.getNameLike())) {
      // search = A String representing either an organization name or domain
      addArgument(filter, "search", query.getNameLike().replaceAll("[%,\\*]", ""));
    }
    addArgument(filter, "max", getMaxQueryResultSize());

    if (!filter.isEmpty()) {
      filter.insert(0, "?");
      String result = filter.toString();
      KeycloakPluginLogger.INSTANCE.groupQueryFilter(result);
      return result;
    }
    return "";
  }

  /**
   * Requests data of single organization.
   *
   * @param organizationId the ID of the requested organization
   * @return response consisting of a list containing the one organization
   * @throws RestClientException
   */
  private ResponseEntity<String> requestOrganizationById(String organizationId) throws RestClientException {
    try {
      String organizationSearch = "/organizations/" + organizationId;

      ResponseEntity<String> response = restTemplate.exchange(keycloakConfiguration.getKeycloakAdminUrl() + organizationSearch,
          HttpMethod.GET, String.class);
      String result = "[" + response.getBody() + "]";
      return new ResponseEntity<>(result, response.getHeaders(), response.getStatusCode());
    } catch (HttpClientErrorException hcee) {
      if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
        String result = "[]";
        return new ResponseEntity<>(result, HttpStatus.OK);
      }
      throw hcee;
    }
  }

  /**
   * Maps a Keycloak JSON result to a Tenant object
   *
   * @param result the Keycloak JSON result
   * @return the Tenant object
   * @throws JsonException in case of errors
   */
  private TenantEntity transformOrganization(JsonObject result) throws JsonException {
    TenantEntity tenant = new TenantEntity();
    tenant.setId(getJsonString(result, "id"));
    tenant.setName(getJsonString(result, "name"));
    return tenant;
  }

  /**
   * Helper for client side group ordering.
   */
  private static class TenantComparator implements Comparator<Tenant> {
    private static final int TENANT_ID = 0;
    private static final int NAME = 1;

    private final int[] order;
    private final boolean[] desc;

    public TenantComparator(List<QueryOrderingProperty> orderList) {
      // Prepare query ordering
      this.order = new int[orderList.size()];
      this.desc = new boolean[orderList.size()];
      for (int i = 0; i < orderList.size(); i++) {
        QueryOrderingProperty qop = orderList.get(i);
        if (qop.getQueryProperty().equals(TenantQueryProperty.TENANT_ID)) {
          order[i] = TENANT_ID;
        } else if (qop.getQueryProperty().equals(TenantQueryProperty.NAME)) {
          order[i] = NAME;
        } else {
          order[i] = -1;
        }
        desc[i] = Direction.DESCENDING.equals(qop.getDirection());
      }
    }

    @Override
    public int compare(Tenant t1, Tenant t2) {
      int c = 0;
      for (int i = 0; i < order.length; i++) {
        switch (order[i]) {
        case TENANT_ID:
          c = KeycloakServiceBase.compare(t1.getId(), t2.getId());
          break;
        case NAME:
          c = KeycloakServiceBase.compare(t1.getName(), t2.getName());
          break;
        default:
          // do nothing
        }
        if (c != 0) {
          return desc[i] ? -c : c;
        }
      }
      return c;
    }
  }
}
