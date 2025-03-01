package org.operaton.bpm.extension.keycloak.test;

import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;

import javax.net.ssl.SSLContext;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.operaton.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.operaton.bpm.engine.impl.test.TestLogger;
import org.operaton.bpm.extension.keycloak.plugin.KeycloakIdentityProviderPlugin;
import org.operaton.commons.logging.BaseLogger;
import org.slf4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Super class for all Identity Provider Tests.
 */
@Testcontainers
public abstract class AbstractKeycloakIdentityProviderTest extends PluggableProcessEngineTestCase {

  // Keycloak configuration
  // - in your maven build set as environment variables in order to override defaults
  // - if not available defaults will be taken from keycloak-default.properties
  private static String keycloakUrl; // expected format "https://<myhost:myport>/auth
  private static String keycloakAdminUser;
  private static String keycloakAdminPassword;
  private static Boolean keycloakEnforceSubgroupsInGroupQuery;

  // ------------------------------------------------------------------------

  private static final Logger LOG = BaseLogger.createLogger(TestLogger.class, "KEYCLOAK",
      "org.operaton.bpm.extension.keycloak", "42").getLogger();

  protected static String groupIdTeamlead;
  protected static String groupIdManager;
  protected static String groupIdAdmin;
  protected static String groupIdSystemReadonly;

  protected static String userIdOperatonAdmin;
  protected static String userIdTeamlead;
  protected static String userIdManager;

  protected static String groupIdHierarchyRoot;
  protected static String groupIdHierarchyChild1;
  protected static String groupIdHierarchyChild2;
  protected static String groupIdHierarchySubchild1;

  protected static String userIdHierarchy;

  protected static String groupIdSimilarClientName;
  protected static String userIdSimilarClientName;

  private static final RestTemplate REST_TEMPLATE = new RestTemplate();

  protected static String clientSecret;

  @Container
  static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.7");

  // creates Keycloak setup only once per test run
  @BeforeClass
  public static void initialize() {
    // read keycloak configuration
    ResourceBundle defaults = ResourceBundle.getBundle("keycloak-default");
    keycloakAdminUser = getConfigValue(defaults, "keycloak.admin.user");
    keycloakAdminPassword = getConfigValue(defaults, "keycloak.admin.password");
    keycloakEnforceSubgroupsInGroupQuery = Boolean.valueOf(
        getConfigValue(defaults, "keycloak.enforce.subgroups.in.group.query"));
    keycloak.withAdminUsername(keycloakAdminUser);
    keycloak.withAdminPassword(keycloakAdminPassword);

    keycloak.start();
    keycloakUrl = keycloak.getAuthServerUrl();

    // setup
    try {
      setupRestTemplate();
      setupKeycloak();
    } catch (Exception e) {
      throw new RuntimeException("Unable to setup keycloak test realm", e);
    }
  }

  /**
   * Helper class for reading configuration values from environment variables and system properties.
   *
   * @param defaults the default configuration
   * @param key      the key
   * @return the value of the key - falls back to default in case environment variable hasn't been set
   */
  private static String getConfigValue(ResourceBundle defaults, String key) {
    String val = null;
    boolean useDefault = false;
    String envVarName = key.toUpperCase().replace('.', '_');
    // 1.) check for environment variable
    val = System.getenv(envVarName);
    if (ObjectUtils.isEmpty(val)) {
      // 2.) check for system property
      val = System.getProperty(envVarName);
      if (ObjectUtils.isEmpty(val)) {
        // 3.) fall back to keycloak-default.properties
        useDefault = true;
        val = defaults.getString(key);
      }
    }
    LOG.info("Configuration {}: '{}' {}", envVarName, val,
        useDefault ? "[Environment variable not set - using default]" : "");
    return val;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void initializeProcessEngine() {
    processEngine = getOrInitializeCachedProcessEngine();
  }

  /**
   * Initializes the process engine using standard configuration operaton.cfg.xml, but
   * replaces the KeyCloakProvider's client secret with the actual test setup.
   * Furthermore, it uses a new database for each test class.
   *
   * @return the process engine
   */
  private ProcessEngine getOrInitializeCachedProcessEngine() {
    if (cachedProcessEngine == null) {
      ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration.createProcessEngineConfigurationFromResource(
          "operaton.cfg.xml");
      configureKeycloakIdentityProviderPlugin(config);
      config.setJdbcUrl(config.getJdbcUrl().replace("KeycloakIdentityServiceTest", getClass().getSimpleName()));
      cachedProcessEngine = config.buildProcessEngine();
    }
    return cachedProcessEngine;
  }

  /**
   * Helper for configuring the KeycloakIdentityProviderPlugin with the test setup configuration.
   *
   * @param config the process engine configuration
   * @return the configured KeycloakIdentityProviderPlugin
   */
  protected static KeycloakIdentityProviderPlugin configureKeycloakIdentityProviderPlugin(ProcessEngineConfigurationImpl config) {
    for (ProcessEnginePlugin p : config.getProcessEnginePlugins()) {
      if (p instanceof KeycloakIdentityProviderPlugin kcp) {
        if (!keycloak.isCreated()) {
          initialize();
        }
        kcp.setKeycloakAdminUrl(kcp.getKeycloakAdminUrl().replace("http://localhost:9000", keycloakUrl));
        kcp.setKeycloakIssuerUrl(kcp.getKeycloakIssuerUrl().replace("http://localhost:9000", keycloakUrl));
        kcp.setEnforceSubgroupsInGroupQuery(keycloakEnforceSubgroupsInGroupQuery);
        kcp.setClientSecret(clientSecret);
        return kcp;
      }
    }
    throw new IllegalStateException("KeycloakIdentityProviderPlugin not found in process engine plugins.");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  // ------------------------------------------------------------------------
  // Test setup
  // ------------------------------------------------------------------------

  /**
   * Setup Keycloak test realm, client, users and groups.
   *
   * @throws JSONException in case of errors
   */
  private static void setupKeycloak() throws JSONException {
    LOG.info("Setting up Keycloak Test Realm");

    // Authenticate Admin
    HttpHeaders headers = authenticateKeycloakAdmin();

    // Create test realm
    String realm = "test";
    createRealm(headers, realm);

    // Create Client
    clientSecret = createClient(headers, realm, "operaton-identity-service", "http://localhost:8080/login");

    // Create groups
    groupIdAdmin = createGroup(headers, realm, "operaton-admin", true);
    groupIdTeamlead = createGroup(headers, realm, "teamlead", false);
    groupIdManager = createGroup(headers, realm, "manager", false);
    groupIdSystemReadonly = createGroup(headers, realm, "cam-read-only", true);

    // Create Users
    userIdOperatonAdmin = createUser(headers, realm, "operaton", "Admin", "Operaton", "operaton@accso.de",
        "operaton1!");
    assignUserGroup(headers, realm, userIdOperatonAdmin, groupIdAdmin);

    userIdTeamlead = createUser(headers, realm, "hans.mustermann", "Hans", "Mustermann",
        "hans.mustermann@tradermail.info", "äöüÄÖÜ");
    assignUserGroup(headers, realm, userIdTeamlead, groupIdTeamlead);

    userIdManager = createUser(headers, realm, "gunnar.von-der-beck@accso.de", "Gunnar", "von der Beck",
        "gunnar.von-der-beck@accso.de", null);
    assignUserGroup(headers, realm, userIdManager, groupIdManager);
    assignUserGroup(headers, realm, userIdManager, groupIdTeamlead);

    // Create additional group hierarchy
    groupIdHierarchyRoot = createGroup(headers, realm, "root", false);
    groupIdHierarchyChild1 = createGroup(headers, realm, "child1", false, groupIdHierarchyRoot);
    groupIdHierarchyChild2 = createGroup(headers, realm, "child2", false, groupIdHierarchyRoot);
    groupIdHierarchySubchild1 = createGroup(headers, realm, "subchild1", false, groupIdHierarchyChild1);

    // Create user with access to parts of hierarchy
    userIdHierarchy = createUser(headers, realm, "johnfoo", "John", "Foo", "johnfoo@gmail.com",
        "!§$%&/()=?#'-_.:,;+*~@€");
    assignUserGroup(headers, realm, userIdHierarchy, groupIdHierarchyChild2);
    assignUserGroup(headers, realm, userIdHierarchy, groupIdHierarchySubchild1);

    // Create user and group named similar to the name of the Keycloak Client
    groupIdSimilarClientName = createGroup(headers, realm, "operaton-identity-service", false);
    userIdSimilarClientName = createUser(headers, realm, "operaton-identity-service", "Identity", "Service",
        "identity.service@test.de", null);
    assignUserGroup(headers, realm, userIdSimilarClientName, groupIdSimilarClientName);

    // --------------------------------------------------------------------

    // Create Client roles
    String clientInternalId = getClientInternalId(headers, realm, "operaton-identity-service");
    createClientRole(headers, realm, clientInternalId, "role-admin");
    createClientRole(headers, realm, clientInternalId, "role-readonly");
    createClientRole(headers, realm, clientInternalId, "role-teamlead");
    createClientRole(headers, realm, clientInternalId, "role-manager");

    // Add Client level roles to groups and users
    addGroupClientRoleMapping(headers, realm, groupIdAdmin, clientInternalId, "role-admin");
    addGroupClientRoleMapping(headers, realm, groupIdTeamlead, clientInternalId, "role-teamlead");
    addGroupClientRoleMapping(headers, realm, groupIdManager, clientInternalId, "role-manager");
    addUserClientRoleMapping(headers, realm, userIdTeamlead, clientInternalId, "role-readonly");
    addUserClientRoleMapping(headers, realm, userIdManager, clientInternalId, "role-readonly");
  }

  /**
   * Deletes Keycloak test realm
   *
   * @throws JSONException in case of errors
   */
  @AfterClass
  public static void tearDownKeycloak() throws JSONException {
    LOG.info("Cleaning up Keycloak Test Realm");

    // Delete test realm
    HttpHeaders headers = authenticateKeycloakAdmin();
    deleteRealm(headers, "test");

    keycloak.stop();
  }

  // ------------------------------------------------------------------------
  // Helper methods
  // ------------------------------------------------------------------------

  /**
   * Rest template setup including a disabled SSL certificate validation.
   *
   * @throws Exception in case of errors
   */
  private static void setupRestTemplate() throws Exception {

    PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
    SSLContext sslContext = SSLContextBuilder.create().loadTrustMaterial(new TrustAllStrategy()).build();
    SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
        NoopHostnameVerifier.INSTANCE);
    connectionManagerBuilder.setSSLSocketFactory(sslConnectionSocketFactory);
    final HttpClient httpClient = HttpClientBuilder.create()
        .setConnectionManager(connectionManagerBuilder.build())
        .build();
    final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
    REST_TEMPLATE.setRequestFactory(factory);

    for (int i = 0; i < REST_TEMPLATE.getMessageConverters().size(); i++) {
      if (REST_TEMPLATE.getMessageConverters().get(i) instanceof StringHttpMessageConverter) {
        REST_TEMPLATE.getMessageConverters().set(i, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        break;
      }
    }
  }

  /**
   * Authenticates towards the admin REST interface as Keycloak Admin using username/password.
   *
   * @return HttpHeaders including the Authorization header / access token
   * @throws JSONException in case of errors
   */
  protected static HttpHeaders authenticateKeycloakAdmin() throws JSONException {
    // Authenticate Admin
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    HttpEntity<String> request = new HttpEntity<>(
        "client_id=admin-cli" + "&username=" + keycloakAdminUser + "&password=" + keycloakAdminPassword
            + "&grant_type=password", headers);
    ResponseEntity<String> response = REST_TEMPLATE.postForEntity(
        keycloakUrl + "/realms/master/protocol/openid-connect/token", request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JSONObject json = new JSONObject(response.getBody());
    String accessToken = json.getString("access_token");
    String tokenType = json.getString("token_type");

    // Create REST request header
    headers = new HttpHeaders();
    headers.add(HttpHeaders.CONTENT_TYPE,
        MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name());
    headers.add(HttpHeaders.AUTHORIZATION, tokenType + " " + accessToken);

    return headers;
  }

  /**
   * Creates a new Keycloak realm.
   *
   * @param headers HttpHeaders including the Authorization header / access token
   * @param realm   the realm name
   */
  private static void createRealm(HttpHeaders headers, String realm) {
    String realmData = "{\"id\":null,\"realm\":\"" + realm
        + "\",\"notBefore\":0,\"revokeRefreshToken\":false,\"refreshTokenMaxReuse\":0,\"accessTokenLifespan\":300,\"accessTokenLifespanForImplicitFlow\":900,\"ssoSessionIdleTimeout\":1800,\"ssoSessionMaxLifespan\":36000,\"ssoSessionIdleTimeoutRememberMe\":0,\"ssoSessionMaxLifespanRememberMe\":0,\"offlineSessionIdleTimeout\":2592000,\"offlineSessionMaxLifespanEnabled\":false,\"offlineSessionMaxLifespan\":5184000,\"accessCodeLifespan\":60,\"accessCodeLifespanUserAction\":300,\"accessCodeLifespanLogin\":1800,\"actionTokenGeneratedByAdminLifespan\":43200,\"actionTokenGeneratedByUserLifespan\":300,\"enabled\":true,\"sslRequired\":\"external\",\"registrationAllowed\":false,\"registrationEmailAsUsername\":false,\"rememberMe\":false,\"verifyEmail\":false,\"loginWithEmailAllowed\":true,\"duplicateEmailsAllowed\":false,\"resetPasswordAllowed\":false,\"editUsernameAllowed\":false,\"bruteForceProtected\":false,\"permanentLockout\":false,\"maxFailureWaitSeconds\":900,\"minimumQuickLoginWaitSeconds\":60,\"waitIncrementSeconds\":60,\"quickLoginCheckMilliSeconds\":1000,\"maxDeltaTimeSeconds\":43200,\"failureFactor\":30,\"defaultRoles\":[\"uma_authorization\",\"offline_access\"],\"requiredCredentials\":[\"password\"],\"otpPolicyType\":\"totp\",\"otpPolicyAlgorithm\":\"HmacSHA1\",\"otpPolicyInitialCounter\":0,\"otpPolicyDigits\":6,\"otpPolicyLookAheadWindow\":1,\"otpPolicyPeriod\":30,\"otpSupportedApplications\":[\"FreeOTP\",\"Google Authenticator\"],\"browserSecurityHeaders\":{\"contentSecurityPolicyReportOnly\":\"\",\"xContentTypeOptions\":\"nosniff\",\"xRobotsTag\":\"none\",\"xFrameOptions\":\"SAMEORIGIN\",\"xXSSProtection\":\"1; mode=block\",\"contentSecurityPolicy\":\"frame-src 'self'; frame-ancestors 'self'; object-src 'none';\",\"strictTransportSecurity\":\"max-age=31536000; includeSubDomains\"},\"smtpServer\":{},\"eventsEnabled\":false,\"eventsListeners\":[\"jboss-logging\"],\"enabledEventTypes\":[],\"adminEventsEnabled\":false,\"adminEventsDetailsEnabled\":false,\"internationalizationEnabled\":false,\"supportedLocales\":[],\"browserFlow\":\"browser\",\"registrationFlow\":\"registration\",\"directGrantFlow\":\"direct grant\",\"resetCredentialsFlow\":\"reset credentials\",\"clientAuthenticationFlow\":\"clients\",\"dockerAuthenticationFlow\":\"docker auth\",\"attributes\":{\"_browser_header.xXSSProtection\":\"1; mode=block\",\"_browser_header.xFrameOptions\":\"SAMEORIGIN\",\"_browser_header.strictTransportSecurity\":\"max-age=31536000; includeSubDomains\",\"permanentLockout\":\"false\",\"quickLoginCheckMilliSeconds\":\"1000\",\"_browser_header.xRobotsTag\":\"none\",\"maxFailureWaitSeconds\":\"900\",\"minimumQuickLoginWaitSeconds\":\"60\",\"failureFactor\":\"30\",\"actionTokenGeneratedByUserLifespan\":\"300\",\"maxDeltaTimeSeconds\":\"43200\",\"_browser_header.xContentTypeOptions\":\"nosniff\",\"offlineSessionMaxLifespan\":\"5184000\",\"actionTokenGeneratedByAdminLifespan\":\"43200\",\"_browser_header.contentSecurityPolicyReportOnly\":\"\",\"bruteForceProtected\":\"false\",\"_browser_header.contentSecurityPolicy\":\"frame-src 'self'; frame-ancestors 'self'; object-src 'none';\",\"waitIncrementSeconds\":\"60\",\"offlineSessionMaxLifespanEnabled\":\"false\"},\"userManagedAccessAllowed\":false}";
    HttpEntity<String> request = new HttpEntity<>(realmData, headers);
    ResponseEntity<String> response = REST_TEMPLATE.postForEntity(keycloakUrl + "/admin/realms", request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    LOG.info("Created realm " + realm);
  }

  /**
   * Deletes a Keycloak realm.
   *
   * @param headers HttpHeaders including the Authorization header / access token
   * @param realm   the realm name
   */
  private static void deleteRealm(HttpHeaders headers, String realm) {
    ResponseEntity<String> response = REST_TEMPLATE.exchange(keycloakUrl + "/admin/realms/" + realm, HttpMethod.DELETE,
        new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    LOG.info("Deleted realm " + realm);
  }

  /**
   * Creates a new Keycloak client including access rights for querying users and groups using the REST API.
   *
   * @param headers     HttpHeaders including the Authorization header / access token
   * @param realm       the realm name
   * @param clientId    the client ID
   * @param redirectUri valid redirect URI
   * @return the client secret required for authentication
   * @throws JSONException in case of errors
   */
  private static String createClient(HttpHeaders headers, String realm, String clientId, String redirectUri)
      throws JSONException {
    // Create Client
    String clientData = "{\"id\":null,\"clientId\":\"" + clientId
        + "\",\"surrogateAuthRequired\":false,\"enabled\":true,\"clientAuthenticatorType\":\"client-secret\",\"redirectUris\":[\""
        + redirectUri
        + "\"],\"webOrigins\":[],\"notBefore\":0,\"bearerOnly\":false,\"consentRequired\":false,\"standardFlowEnabled\":true,\"implicitFlowEnabled\":false,\"directAccessGrantsEnabled\":true,\"serviceAccountsEnabled\":true,\"publicClient\":false,\"frontchannelLogout\":false,\"protocol\":\"openid-connect\",\"attributes\":{\"client_credentials.use_refresh_token\":\"true\",\"saml.assertion.signature\":\"false\",\"saml.force.post.binding\":\"false\",\"saml.multivalued.roles\":\"false\",\"saml.encrypt\":\"false\",\"saml.server.signature\":\"false\",\"saml.server.signature.keyinfo.ext\":\"false\",\"exclude.session.state.from.auth.response\":\"false\",\"saml_force_name_id_format\":\"false\",\"saml.client.signature\":\"false\",\"tls.client.certificate.bound.access.tokens\":\"false\",\"saml.authnstatement\":\"false\",\"display.on.consent.screen\":\"false\",\"saml.onetimeuse.condition\":\"false\"},\"authenticationFlowBindingOverrides\":{},\"fullScopeAllowed\":true,\"nodeReRegistrationTimeout\":-1,\"protocolMappers\":[{\"id\":null,\"name\":\"Client Host\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-usersessionmodel-note-mapper\",\"consentRequired\":false,\"config\":{\"user.session.note\":\"clientHost\",\"userinfo.token.claim\":\"true\",\"id.token.claim\":\"true\",\"access.token.claim\":\"true\",\"claim.name\":\"clientHost\",\"jsonType.label\":\"String\"}},{\"id\":null,\"name\":\"Client IP Address\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-usersessionmodel-note-mapper\",\"consentRequired\":false,\"config\":{\"user.session.note\":\"clientAddress\",\"userinfo.token.claim\":\"true\",\"id.token.claim\":\"true\",\"access.token.claim\":\"true\",\"claim.name\":\"clientAddress\",\"jsonType.label\":\"String\"}},{\"id\":null,\"name\":\"Client ID\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"oidc-usersessionmodel-note-mapper\",\"consentRequired\":false,\"config\":{\"user.session.note\":\"clientId\",\"userinfo.token.claim\":\"true\",\"id.token.claim\":\"true\",\"access.token.claim\":\"true\",\"claim.name\":\"clientId\",\"jsonType.label\":\"String\"}}],\"defaultClientScopes\":[\"web-origins\",\"role_list\",\"profile\",\"roles\",\"email\"],\"optionalClientScopes\":[\"address\",\"phone\",\"offline_access\"],\"access\":{\"view\":true,\"configure\":true,\"manage\":true}}";
    HttpEntity<String> request = new HttpEntity<>(clientData, headers);
    ResponseEntity<String> response = REST_TEMPLATE.postForEntity(keycloakUrl + "/admin/realms/" + realm + "/clients",
        request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Get the Client secret
    String clientSecret = null;
    response = REST_TEMPLATE.exchange(keycloakUrl + "/admin/realms/" + realm + "/clients?clientId=" + clientId,
        HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String internalClientId = new JSONArray(response.getBody()).getJSONObject(0).getString("id");
    response = REST_TEMPLATE.exchange(
        keycloakUrl + "/admin/realms/" + realm + "/clients/" + internalClientId + "/client-secret", HttpMethod.GET,
        new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    clientSecret = new JSONObject(response.getBody()).getString("value");
    assertThat(clientSecret).isNotNull();

    // Get the Client's service account
    response = REST_TEMPLATE.exchange(
        keycloakUrl + "/admin/realms/" + realm + "/users?username=service-account-" + clientId, HttpMethod.GET,
        new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String serviceAccountId = new JSONArray(response.getBody()).getJSONObject(0).getString("id");

    // Get user and group roles of realm-management client
    String queryUserRoleId = null;
    String queryGroupRoleId = null;
    String viewUserRoleId = null;
    String viewClientsRoleId = null;
    response = REST_TEMPLATE.exchange(keycloakUrl + "/admin/realms/" + realm + "/clients?clientId=realm-management",
        HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String realmManagementId = new JSONArray(response.getBody()).getJSONObject(0).getString("id");
    response = REST_TEMPLATE.exchange(
        keycloakUrl + "/admin/realms/" + realm + "/users/" + serviceAccountId + "/role-mappings/clients/"
            + realmManagementId + "/available", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JSONArray roleList = new JSONArray(response.getBody());
    for (int i = 0; i < roleList.length(); i++) {
      JSONObject role = roleList.getJSONObject(i);
      String roleName = role.getString("name");
      if ("query-users".equals(roleName)) {
        queryUserRoleId = role.getString("id");
      } else if ("query-groups".equals(roleName)) {
        queryGroupRoleId = role.getString("id");
      } else if ("view-users".equals(roleName)) {
        viewUserRoleId = role.getString("id");
      } else if ("view-clients".equals(roleName)) {
        viewClientsRoleId = role.getString("id");
      }
      if (queryUserRoleId != null && queryGroupRoleId != null && viewUserRoleId != null && viewClientsRoleId != null) {
        break;
      }
    }
    assertThat(queryUserRoleId).isNotNull();
    assertThat(queryGroupRoleId).isNotNull();
    assertThat(viewUserRoleId).isNotNull();

    // Add service account client roles query-user, query-group, view-user, view-clients for realm management: this allows using the management REST API via this newly created client
    String roleMapping = "[" + "{\"clientRole\":true,\"composite\":false,\"containerId\":\"" + realmManagementId
        + "\",\"description\":\"${role_query-groups}\",\"id\":\"" + queryGroupRoleId + "\",\"name\":\"query-groups\"},"
        + "{\"clientRole\":true,\"composite\":false,\"containerId\":\"" + realmManagementId
        + "\",\"description\":\"${role_query-users}\",\"id\":\"" + queryUserRoleId + "\",\"name\":\"query-users\"},"
        + "{\"clientRole\":true,\"composite\":true,\"containerId\":\"" + realmManagementId
        + "\",\"description\":\"${role_view-users}\",\"id\":\"" + viewUserRoleId + "\",\"name\":\"view-users\"},"
        + "{\"clientRole\":true,\"composite\":true,\"containerId\":\"" + realmManagementId
        + "\",\"description\":\"${role_view-clients}\",\"id\":\"" + viewClientsRoleId + "\",\"name\":\"view-clients\"}"
        + "]";
    request = new HttpEntity<>(roleMapping, headers);
    response = REST_TEMPLATE.postForEntity(
        keycloakUrl + "/admin/realms/" + realm + "/users/" + serviceAccountId + "/role-mappings/clients/"
            + realmManagementId, request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    LOG.info("Created client " + clientId);
    return clientSecret;
  }

  /**
   * Creates a user.
   *
   * @param headers   HttpHeaders including the Authorization header / access token
   * @param realm     the realm name
   * @param userName  the username
   * @param firstName the first name
   * @param lastName  the last name
   * @param email     the email
   * @param password  the password (optional, can be {@code null} in case no authentication is planned/required)
   * @return the user ID
   * @throws JSONException in case of errors
   */
  static String createUser(HttpHeaders headers,
                           String realm,
                           String userName,
                           String firstName,
                           String lastName,
                           String email,
                           String password) throws JSONException {
    // create user
    StringBuffer userData = new StringBuffer(
        "{\"id\":null,\"username\":\"" + userName + "\",\"enabled\":true,\"totp\":false,\"emailVerified\":false,");
    if (firstName != null) {
      userData.append("\"firstName\":\"" + firstName + "\",");
    }
    if (lastName != null) {
      userData.append("\"lastName\":\"" + lastName + "\",");
    }
    if (email != null) {
      userData.append("\"email\":\"" + email + "\",");
    }
    userData.append(
        "\"disableableCredentialTypes\":[\"password\"],\"requiredActions\":[],\"federatedIdentities\":[],\"notBefore\":0,\"access\":{\"manageGroupMembership\":true,\"view\":true,\"mapRoles\":true,\"impersonate\":true,\"manage\":true}}");
    HttpEntity<String> request = new HttpEntity<>(userData.toString(), headers);
    ResponseEntity<String> response = REST_TEMPLATE.postForEntity(keycloakUrl + "/admin/realms/" + realm + "/users",
        request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    // get the user ID
    response = REST_TEMPLATE.exchange(keycloakUrl + "/admin/realms/" + realm + "/users?username=" + userName,
        HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String userId = new JSONArray(response.getBody()).getJSONObject(0).getString("id");
    // set optional password
    if (!ObjectUtils.isEmpty(password)) {
      String pwd = "{\"type\":\"password\",\"value\":\"" + password + "\"}";
      response = REST_TEMPLATE.exchange(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/reset-password",
          HttpMethod.PUT, new HttpEntity<>(pwd, headers), String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
    LOG.info("Created user {}", userName);
    return userId;
  }

  /**
   * Deletes a user.
   *
   * @param headers HttpHeaders including the Authorization header / access token
   * @param realm   the realm name
   * @param userId  the user ID
   */
  static void deleteUser(HttpHeaders headers, String realm, String userId) {
    ResponseEntity<String> response = REST_TEMPLATE.exchange(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId,
        HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    LOG.info("Deleted user with ID {}", userId);
  }

  /**
   * Creates a group.
   *
   * @param headers       HttpHeaders including the Authorization header / access token
   * @param realm         the realm name
   * @param groupName     the name of the group
   * @param isSystemGroup {@code true} in case of system groups, {@code false} for workflow groups
   * @return the group ID
   * @throws JSONException in case of errors
   */
  static String createGroup(HttpHeaders headers, String realm, String groupName, boolean isSystemGroup)
      throws JSONException {
    // create group without parent
    return createGroup(headers, realm, groupName, isSystemGroup, null);
  }

  /**
   * Creates a child group
   *
   * @param headers       HttpHeaders including the Authorization header / access token
   * @param realm         the realm name
   * @param groupName     the name of the group
   * @param isSystemGroup {@code true} in case of system groups, {@code false} for workflow groups
   * @param parentGroupId the group ID of the parent group
   * @return the group ID
   * @throws JSONException in case of errors
   */
  static String createGroup(HttpHeaders headers,
                            String realm,
                            String groupName,
                            boolean isSystemGroup,
                            String parentGroupId) throws JSONException {
    // create group
    String operatonAdmin =
        "{\"id\":null,\"name\":\"" + groupName + "\",\"attributes\":{" + (isSystemGroup ? "\"type\":[\"SYSTEM\"]" : "")
            + "},\"realmRoles\":[],\"clientRoles\":{},\"subGroups\":[],\"access\":{\"view\":true,\"manage\":true,\"manageMembership\":true}}";
    HttpEntity<String> request = new HttpEntity<>(operatonAdmin, headers);
    ResponseEntity<String> response = REST_TEMPLATE.postForEntity(keycloakUrl + "/admin/realms/" + realm + "/groups" + (
        parentGroupId != null ?
            "/" + parentGroupId + "/children" :
            ""), request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    // get the group id
    response = REST_TEMPLATE.exchange(keycloakUrl + "/admin/realms/" + realm + "/groups?search=" + groupName,
        HttpMethod.GET, new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JSONArray groups = new JSONArray(response.getBody());
    JSONObject group = findGroupInHierarchy(groups, groupName);
    if (group != null) {
      return group.getString("id");
    }
    throw new IllegalStateException("Error creating group " + groupName);
  }

  /**
   * Deletes a group.
   *
   * @param headers HttpHeaders including the Authorization header / access token
   * @param realm   the realm name
   * @param groupId the group ID
   */
  static void deleteGroup(HttpHeaders headers, String realm, String groupId) {
    ResponseEntity<String> response = REST_TEMPLATE.exchange(
        keycloakUrl + "/admin/realms/" + realm + "/groups/" + groupId, HttpMethod.DELETE, new HttpEntity<>(headers),
        String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    LOG.info("Deleted group with ID {}", groupId);
  }

  private static JSONObject findGroupInHierarchy(JSONArray groups, String groupName) throws JSONException {
    for (int i = 0; i < groups.length(); i++) {
      JSONObject group = groups.getJSONObject(i);
      if (group.getString("name").equals(groupName)) {
        LOG.info("Created group {}", groupName);
        return group;
      }
      JSONObject subGroup = findGroupInHierarchy(group.getJSONArray("subGroups"), groupName);
      if (subGroup != null) {
        return subGroup;
      }
    }
    return null;
  }

  /**
   * Assigns a user to a group.
   *
   * @param headers HttpHeaders including the Authorization header / access token
   * @param realm   the realm name
   * @param userId  the user ID
   * @param groupId the group ID
   */
  static void assignUserGroup(HttpHeaders headers, String realm, String userId, String groupId) {
    ResponseEntity<String> response = REST_TEMPLATE.exchange(
        keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/groups/" + groupId, HttpMethod.PUT,
        new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  // ------------------------------------------------------------------------
  // Client role specific functions
  // ------------------------------------------------------------------------

  /**
   * Returns the Keycloak internal ID of the service client
   *
   * @param headers  HttpHeaders including the Authorization header / access token
   * @param realm    the realm name
   * @param clientId the client ID
   * @return the internal ID of the client
   * @throws JSONException in case of any errors
   */
  static String getClientInternalId(HttpHeaders headers, String realm, String clientId) throws JSONException {
    ResponseEntity<String> response = REST_TEMPLATE.exchange(
        keycloakUrl + "/admin/realms/" + realm + "/clients?clientId=" + clientId, HttpMethod.GET,
        new HttpEntity<>(null, headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JSONArray json = new JSONArray(response.getBody());
    return json.getJSONObject(0).getString("id");
  }

  /**
   * Creates a client role.
   *
   * @param headers          HttpHeaders including the Authorization header / access token
   * @param realm            the realm name
   * @param clientInternalId the Keycloak internal ID of the client
   * @param roleName         the name of the client role to create
   */
  static void createClientRole(HttpHeaders headers, String realm, String clientInternalId, String roleName) {
    String role = "{\"name\":\"" + roleName + "\"}";
    HttpEntity<String> request = new HttpEntity<>(role, headers);
    ResponseEntity<String> response = REST_TEMPLATE.postForEntity(
        keycloakUrl + "/admin/realms/" + realm + "/clients/" + clientInternalId + "/roles", request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    LOG.info("Created client role {}", roleName);
  }

  /**
   * Add client-level roles to the user role mapping
   *
   * @param headers          HttpHeaders including the Authorization header / access token
   * @param realm            the realm name
   * @param userId           the Keycloak ID of the user
   * @param clientInternalId the Keycloak internal ID of the client
   * @param roleName         the name of the client role to create the mapping for
   * @throws JSONException in case of any errors
   */
  static void addUserClientRoleMapping(HttpHeaders headers,
                                       String realm,
                                       String userId,
                                       String clientInternalId,
                                       String roleName) throws JSONException {
    // get internal ID of client role
    ResponseEntity<String> response = REST_TEMPLATE.exchange(
        keycloakUrl + "/admin/realms/" + realm + "/clients/" + clientInternalId + "/roles/" + roleName, HttpMethod.GET,
        new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String roleId = new JSONObject(response.getBody()).getString("id");

    // create role mapping
    String mapping = "[{\"id\":\"" + roleId + "\",\"name\":\"" + roleName
        + "\",\"composite\":false,\"clientRole\":true,\"containerId\":\"" + clientInternalId + "\"}]";
    HttpEntity<String> request = new HttpEntity<>(mapping, headers);
    response = REST_TEMPLATE.postForEntity(
        keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/clients/" + clientInternalId,
        request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  /**
   * Add client-level roles to the group role mapping
   *
   * @param headers          HttpHeaders including the Authorization header / access token
   * @param realm            the realm name
   * @param groupId          the Keycloak ID of the group
   * @param clientInternalId the Keycloak internal ID of the client
   * @param roleName         the name of the client role to create the mapping for
   * @throws JSONException in case of any errors
   */
  static void addGroupClientRoleMapping(HttpHeaders headers,
                                        String realm,
                                        String groupId,
                                        String clientInternalId,
                                        String roleName) throws JSONException {
    // get internal ID of client role
    ResponseEntity<String> response = REST_TEMPLATE.exchange(
        keycloakUrl + "/admin/realms/" + realm + "/clients/" + clientInternalId + "/roles/" + roleName, HttpMethod.GET,
        new HttpEntity<>(headers), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String roleId = new JSONObject(response.getBody()).getString("id");

    // create role mapping
    String mapping = "[{\"id\":\"" + roleId + "\",\"name\":\"" + roleName
        + "\",\"composite\":false,\"clientRole\":true,\"containerId\":\"" + clientInternalId + "\"}]";
    HttpEntity<String> request = new HttpEntity<>(mapping, headers);
    response = REST_TEMPLATE.postForEntity(
        keycloakUrl + "/admin/realms/" + realm + "/groups/" + groupId + "/role-mappings/clients/" + clientInternalId,
        request, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

}
