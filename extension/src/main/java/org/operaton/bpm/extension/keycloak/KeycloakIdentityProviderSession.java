package org.operaton.bpm.extension.keycloak;

import static org.operaton.bpm.extension.keycloak.json.JsonUtil.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import org.operaton.bpm.engine.BadUserRequestException;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.GroupQuery;
import org.operaton.bpm.engine.identity.NativeUserQuery;
import org.operaton.bpm.engine.identity.Tenant;
import org.operaton.bpm.engine.identity.TenantQuery;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.identity.UserQuery;
import org.operaton.bpm.engine.impl.UserQueryImpl;
import org.operaton.bpm.engine.impl.identity.IdentityProviderException;
import org.operaton.bpm.engine.impl.identity.ReadOnlyIdentityProvider;
import org.operaton.bpm.engine.impl.interceptor.CommandContext;
import org.operaton.bpm.extension.keycloak.cache.QueryCache;
import org.operaton.bpm.extension.keycloak.json.JsonException;
import org.operaton.bpm.extension.keycloak.rest.KeycloakRestTemplate;
import org.operaton.bpm.extension.keycloak.util.ContentType;
import org.operaton.bpm.extension.keycloak.util.KeycloakPluginLogger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import com.google.gson.JsonObject;

/**
 * Keycloak {@link ReadOnlyIdentityProvider}.
 */
public class KeycloakIdentityProviderSession implements ReadOnlyIdentityProvider {

	protected KeycloakConfiguration keycloakConfiguration;
	protected KeycloakRestTemplate restTemplate;
	protected KeycloakContextProvider keycloakContextProvider;
	
	protected KeycloakUserService userService;
	protected KeycloakGroupService groupService;

	protected QueryCache<CacheableKeycloakUserQuery, List<User>> userQueryCache;
	protected QueryCache<CacheableKeycloakGroupQuery, List<Group>> groupQueryCache;
	protected QueryCache<CacheableKeycloakCheckPasswordCall, Boolean> checkPasswordCache;

	/**
	 * Creates a new session.
	 * @param keycloakConfiguration the Keycloak configuration
	 * @param restTemplate REST template
	 * @param keycloakContextProvider Keycloak context provider
	 */
	public KeycloakIdentityProviderSession(
					KeycloakConfiguration keycloakConfiguration, KeycloakRestTemplate restTemplate, KeycloakContextProvider keycloakContextProvider,
					QueryCache<CacheableKeycloakUserQuery, List<User>> userQueryCache, QueryCache<CacheableKeycloakGroupQuery, List<Group>> groupQueryCache,
					QueryCache<CacheableKeycloakCheckPasswordCall, Boolean> checkPasswordCache) {
		this.keycloakConfiguration = keycloakConfiguration;
		this.restTemplate = restTemplate;
		this.keycloakContextProvider = keycloakContextProvider;
		
		this.userService = new KeycloakUserService(keycloakConfiguration, restTemplate, keycloakContextProvider);
		this.groupService = new  KeycloakGroupService(keycloakConfiguration, restTemplate, keycloakContextProvider);

		this.userQueryCache = userQueryCache;
		this.groupQueryCache = groupQueryCache;
		this.checkPasswordCache = checkPasswordCache;
	}
	
	@Override
	public void flush() {
		// nothing to do
	}

	@Override
	public void close() {
		// nothing to do
	}

	//-------------------------------------------------------------------------
	// Users
	//-------------------------------------------------------------------------
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public User findUserById(String userId) {
		return createUserQuery(org.operaton.bpm.engine.impl.context.Context.getCommandContext()).userId(userId)
				.singleResult();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UserQuery createUserQuery() {
		return new KeycloakUserQuery(org.operaton.bpm.engine.impl.context.Context.getProcessEngineConfiguration()
				.getCommandExecutorTxRequired());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public UserQueryImpl createUserQuery(CommandContext commandContext) {
		return new KeycloakUserQuery();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NativeUserQuery createNativeUserQuery() {
		throw new BadUserRequestException("Native user queries are not supported for Keycloak identity service provider.");
	}

	/**
	 * find the number of users meeting given user query criteria.
	 * @param userQuery the user query
	 * @return number of matching users
	 */
	protected long findUserCountByQueryCriteria(KeycloakUserQuery userQuery) {
		return findUserByQueryCriteria(userQuery).size();
	}

	/**
	 * find users meeting given user query criteria (with cache lookup).
	 * @param userQuery the user query
	 * @return list of matching users
	 */
	protected List<User> findUserByQueryCriteria(KeycloakUserQuery userQuery) {
		StringBuilder resultLogger = new StringBuilder();

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("Keycloak group query results: [");
		}

		List<User> allMatchingUsers = userQueryCache
						.getOrCompute(CacheableKeycloakUserQuery.of(userQuery), this::doFindUserByQueryCriteria);

		List<User> processedUsers = userService.postProcessResults(userQuery, allMatchingUsers, resultLogger);

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("]");
			KeycloakPluginLogger.INSTANCE.groupQueryResult(resultLogger.toString());
		}

		return processedUsers;
	}

	/**
	 * find users meeting given user query criteria (without cache lookup).
	 * @param userQuery the user query
	 * @return list of matching users
	 */
	private List<User> doFindUserByQueryCriteria(CacheableKeycloakUserQuery userQuery) {
		if (StringUtils.hasLength(userQuery.getGroupId())) {
			// search within the members of a single group
			return userService.requestUsersByGroupId(userQuery);
		} else {
			return userService.requestUsersWithoutGroupId(userQuery);
		}
	}

	
	/**
	 * Get the user ID of the configured admin user. Enable configuration using username / email as well.
	 * This prevents common configuration pitfalls and makes it consistent to other configuration options
	 * like the flags 'useUsernameAsOperatonUserId' and 'useEmailAsOperatonUserId'.
	 * 
	 * @param configuredAdminUserId the originally configured admin user ID
	 * @return the corresponding keycloak user ID to use: either internal keycloak ID, username or email, depending on config
	 * 
	 * @see org.operaton.bpm.extension.keycloak.KeycloakUserService#getKeycloakAdminUserId(java.lang.String)
	 */
	public String getKeycloakAdminUserId(String configuredAdminUserId) {
		return userService.getKeycloakAdminUserId(configuredAdminUserId);
	}
	
	//-------------------------------------------------------------------------
	// Login / Password check
	//-------------------------------------------------------------------------
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean checkPassword(String userId, String password) {
		return checkPasswordCache.getOrCompute(new CacheableKeycloakCheckPasswordCall(userId, password), 
				(c) -> this.doCheckPassword(c.getUserId(), password));
	}

	/**
	 * Checks the password of a user without using the cache.
	 * @param userId the user ID
	 * @param password the password
	 * @return {@code true}, if user is allowed to login
	 */
	private boolean doCheckPassword(String userId, String password) {

		// engine can't work without users
		if (!StringUtils.hasLength(userId)) {
			return false;
		}

		// prevent missing/empty passwords - we do not support anonymous login
		if (!StringUtils.hasLength(password)) {
			return false;
		}
		
		// Get Keycloak username for authentication
		String userName;
		try {
			userName = getKeycloakUsername(userId);
		} catch (KeycloakUserNotFoundException aunfe) {
			KeycloakPluginLogger.INSTANCE.userNotFound(userId, aunfe);
			return false;
		}
			
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED + ";charset=" + keycloakConfiguration.getCharset());
			HttpEntity<String> request = new HttpEntity<>(
		    		"client_id=" + keycloakConfiguration.getClientId()
    	    		+ "&client_secret=" + keycloakConfiguration.getClientSecret()
    	    		+ "&username=" + userName
    	    		+ "&password=" + URLEncoder.encode(password, keycloakConfiguration.getCharset())
    	    		+ "&grant_type=password",
    	    		headers);
			restTemplate.postForEntity(keycloakConfiguration.getKeycloakIssuerUrl() + "/protocol/openid-connect/token", request, String.class);
			return true;
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
				return false;
			}
			throw new IdentityProviderException("Unable to authenticate user at " + keycloakConfiguration.getKeycloakIssuerUrl(),
					hcee);
		} catch (RestClientException | UnsupportedEncodingException rce) {
			throw new IdentityProviderException("Unable to authenticate user at " + keycloakConfiguration.getKeycloakIssuerUrl(),
					rce);
		}

	}

	/**
	 * Gets the Keycloak internal username of an user.
	 * @param userId the userId as sent by the client (when checking password)
	 * @return the Keycloak internal username
	 * @throws KeycloakUserNotFoundException in case the user cannot be found
	 * @throws RestClientException in case of technical errors
	 */
	protected String getKeycloakUsername(String userId) throws KeycloakUserNotFoundException, RestClientException {
		if (keycloakConfiguration.isUseUsernameAsOperatonUserId()) {
			return userId;
		}
		try {
			if (keycloakConfiguration.isUseEmailAsOperatonUserId()) {
				ResponseEntity<String> response = restTemplate.exchange(
					keycloakConfiguration.getKeycloakAdminUrl() + "/users?exact=true&email=" + userId, HttpMethod.GET, String.class);
				JsonObject result = findFirst(parseAsJsonArray(response.getBody()), "email", userId);
				if (result != null) {
					return getJsonString(result, "username");
				}
				throw new KeycloakUserNotFoundException(userId + " not found - email unknown");
			} else {
				ResponseEntity<String> response = restTemplate.exchange(
						keycloakConfiguration.getKeycloakAdminUrl() + "/users/" + userId, HttpMethod.GET, String.class);
				return parseAsJsonObjectAndGetMemberAsString(response.getBody(), "username");
			}
		} catch (JsonException je) {
			throw new KeycloakUserNotFoundException(userId + 
					(keycloakConfiguration.isUseEmailAsOperatonUserId()
					? " not found - email unknown" 
					: " not found - ID unknown"), je);
		} catch (HttpClientErrorException hcee) {
			if (hcee.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				throw new KeycloakUserNotFoundException(userId + " not found", hcee);
			}
			throw hcee;
		}
	}
	
	//-------------------------------------------------------------------------
	// Groups
	//-------------------------------------------------------------------------
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Group findGroupById(String groupId) {
		return createGroupQuery(org.operaton.bpm.engine.impl.context.Context.getCommandContext()).groupId(groupId)
				.singleResult();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public GroupQuery createGroupQuery() {
		return new KeycloakGroupQuery(org.operaton.bpm.engine.impl.context.Context.getProcessEngineConfiguration()
				.getCommandExecutorTxRequired());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public GroupQuery createGroupQuery(CommandContext commandContext) {
		return new KeycloakGroupQuery();
	}

	/**
	 * find the number of groups meeting given group query criteria.
	 * @param groupQuery the group query
	 * @return number of matching groups
	 */
	protected long findGroupCountByQueryCriteria(KeycloakGroupQuery groupQuery) {
		return findGroupByQueryCriteria(groupQuery).size();
	}

	/**
	 * find groups meeting given group query criteria (with cache lookup and post processing).
	 * @param groupQuery the group query
	 * @return list of matching groups
	 */
	protected List<Group> findGroupByQueryCriteria(KeycloakGroupQuery groupQuery) {
		StringBuilder resultLogger = new StringBuilder();

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("Keycloak group query results: [");
		}

		List<Group> allMatchingGroups = groupQueryCache
						.getOrCompute(CacheableKeycloakGroupQuery.of(groupQuery), this::doFindGroupByQueryCriteria);

		List<Group> processedGroups = groupService.postProcessResults(groupQuery, allMatchingGroups, resultLogger);

		if (KeycloakPluginLogger.INSTANCE.isDebugEnabled()) {
			resultLogger.append("]");
			KeycloakPluginLogger.INSTANCE.groupQueryResult(resultLogger.toString());
		}

		return processedGroups;
	}

	/**
	 * find all groups meeting given group query criteria (without cache lookup or post processing).
	 * @param groupQuery the group query
	 * @return list of matching groups
	 */
	private List<Group> doFindGroupByQueryCriteria(CacheableKeycloakGroupQuery groupQuery) {
		if (StringUtils.hasLength(groupQuery.getUserId())) {
			// if restriction on userId is provided, we're searching within the groups of a single user
			return groupService.requestGroupsByUserId(groupQuery);
		} else {
			return groupService.requestGroupsWithoutUserId(groupQuery);
		}
	}

	/**
	 * Get the group ID of the configured admin group. Enable configuration using group path as well.
	 * This prevents common configuration pitfalls and makes it consistent to other configuration options
	 * like the flag 'useGroupPathAsOperatonGroupId'.
	 * 
	 * @param configuredAdminGroupName the originally configured admin group name
	 * @return the corresponding keycloak group ID to use: either internal keycloak ID or path, depending on config
	 * 
	 * @see org.operaton.bpm.extension.keycloak.KeycloakGroupService#getKeycloakAdminGroupId(java.lang.String)
	 */
	public String getKeycloakAdminGroupId(String configuredAdminGroupName) {
		return groupService.getKeycloakAdminGroupId(configuredAdminGroupName);
	}

	//-------------------------------------------------------------------------
	// Tenants
	//-------------------------------------------------------------------------
	
	@Override
	public TenantQuery createTenantQuery() {
		return new KeycloakTenantQuery(org.operaton.bpm.engine.impl.context.Context.getProcessEngineConfiguration()
				.getCommandExecutorTxRequired());
	}

	@Override
	public TenantQuery createTenantQuery(CommandContext commandContext) {
		return new KeycloakTenantQuery();
	}

	@Override
	public Tenant findTenantById(String id) {
		// since multi-tenancy is currently not supported for the Keycloak plugin, always return null
		return null;
	}

}
