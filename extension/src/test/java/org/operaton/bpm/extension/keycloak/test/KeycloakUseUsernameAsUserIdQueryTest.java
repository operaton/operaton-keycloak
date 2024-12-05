package org.operaton.bpm.extension.keycloak.test;

import java.util.ArrayList;
import java.util.List;

import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.springframework.http.HttpHeaders;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * User query test for the Keycloak identity provider.
 * Flag useUsernameAsOperatonUserId enabled.
 */
public class KeycloakUseUsernameAsUserIdQueryTest extends AbstractKeycloakIdentityProviderTest {

	static List<String> USER_IDS = new ArrayList<String>();

	public static Test suite() {
	    return new TestSetup(new TestSuite(KeycloakUseUsernameAsUserIdQueryTest.class)) {

	    	// @BeforeClass
	        protected void setUp() throws Exception {
	    		// setup Keycloak special test users
	        	// -------------------------------------
	    		HttpHeaders headers = authenticateKeycloakAdmin();
	    		String realm = "test";
	    		USER_IDS.add(createUser(headers, realm, "hans.wurst", null, null, null, null));

	    		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
	    				.createProcessEngineConfigurationFromResource("operaton.useUsernameAsOperatonUserId.cfg.xml");
	    		configureKeycloakIdentityProviderPlugin(config);
	    		PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
	        }
	        
	        // @AfterClass
	        protected void tearDown() throws Exception {
	    		PluggableProcessEngineTestCase.cachedProcessEngine.close();
	    		PluggableProcessEngineTestCase.cachedProcessEngine = null;

	    		// delete special test users
	    		HttpHeaders headers = authenticateKeycloakAdmin();
	    		String realm = "test";
	    		USER_IDS.forEach(u -> deleteUser(headers, realm, u));
	        }
	    };
	}
	
	// ------------------------------------------------------------------------
	// Authorization tests
	// ------------------------------------------------------------------------
	
	public void testKeycloakLoginSuccess() {
		assertTrue(identityService.checkPassword("operaton", "operaton1!"));
	}

	// ------------------------------------------------------------------------
	// User Query tests
	// ------------------------------------------------------------------------
	
	public void testUserQueryFilterByUserId() {
		User user = identityService.createUserQuery().userId("hans.mustermann").singleResult();
		assertNotNull(user);

		user = identityService.createUserQuery().userId("operaton").singleResult();
		assertNotNull(user);

		// validate user
		assertEquals("operaton", user.getId());
		assertEquals("Admin", user.getFirstName());
		assertEquals("Operaton", user.getLastName());
		assertEquals("operaton@accso.de", user.getEmail());

		user = identityService.createUserQuery().userId("non-existing").singleResult();
		assertNull(user);
	}

	public void testUserQueryFilterByUserIdIn() {
		List<User> users = identityService.createUserQuery().userIdIn("operaton", "hans.mustermann").list();
		assertNotNull(users);
		assertEquals(2, users.size());

		users = identityService.createUserQuery().userIdIn("operaton", "non-existing").list();
		assertNotNull(users);
		assertEquals(1, users.size());
	}

	public void testUserQueryFilterByEmail() {
		User user = identityService.createUserQuery().userEmail("operaton@accso.de").singleResult();
		assertNotNull(user);

		// validate user
		assertEquals("operaton", user.getId());
		assertEquals("Admin", user.getFirstName());
		assertEquals("Operaton", user.getLastName());
		assertEquals("operaton@accso.de", user.getEmail());

		user = identityService.createUserQuery().userEmail("non-exist*").singleResult();
		assertNull(user);
	}
	
	public void testUserQueryFilterByNonExistingAttributeLike() {
		// hans.wurst has no other attributes than his username set
		User user = identityService.createUserQuery().userId("hans.wurst").userEmailLike("*").singleResult();
		assertNotNull(user);
		user = identityService.createUserQuery().userId("hans.wurst").userEmailLike("operaton*").singleResult();
		assertNull(user);
		user = identityService.createUserQuery().userId("hans.wurst").userFirstNameLike("*").singleResult();
		assertNotNull(user);
		user = identityService.createUserQuery().userId("hans.wurst").userFirstNameLike("operaton*").singleResult();
		assertNull(user);
		user = identityService.createUserQuery().userId("hans.wurst").userLastNameLike("*").singleResult();
		assertNotNull(user);
		user = identityService.createUserQuery().userId("hans.wurst").userLastNameLike("operaton*").singleResult();
		assertNull(user);
	}

	public void testUserQueryFilterByGroupIdAndId() {
		List<User> result = identityService.createUserQuery()
				.memberOfGroup(GROUP_ID_ADMIN)
				.userId("operaton")
				.list();
		assertEquals(1, result.size());

		result = identityService.createUserQuery()
				.memberOfGroup(GROUP_ID_ADMIN)
				.userId("non-exist")
				.list();
		assertEquals(0, result.size());

		result = identityService.createUserQuery()
				.memberOfGroup("non-exist")
				.userId("operaton")
				.list();
		assertEquals(0, result.size());
		
	}

	public void testAuthenticatedUserSeesHimself() {
		try {
			processEngineConfiguration.setAuthorizationEnabled(true);

			identityService.setAuthenticatedUserId("non-existing");
			assertEquals(0, identityService.createUserQuery().count());

			identityService.setAuthenticatedUserId("operaton");
			assertEquals(1, identityService.createUserQuery().count());

		} finally {
			processEngineConfiguration.setAuthorizationEnabled(false);
			identityService.clearAuthentication();
		}
	}

	// ------------------------------------------------------------------------
	// Group query tests
	// ------------------------------------------------------------------------

	public void testGroupQueryFilterByUserId() {
		List<Group> result = identityService.createGroupQuery().groupMember("operaton").list();
		assertEquals(1, result.size());

		result = identityService.createGroupQuery().groupMember("non-exist").list();
		assertEquals(0, result.size());
	}

	public void testFilterByGroupIdAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupId(GROUP_ID_ADMIN)
				.groupMember("operaton")
				.singleResult();
		assertNotNull(group);
		assertEquals("operaton-admin", group.getName());

		group = identityService.createGroupQuery()
				.groupId("non-exist")
				.groupMember("operaton")
				.singleResult();
		assertNull(group);

		group = identityService.createGroupQuery()
				.groupId(GROUP_ID_ADMIN)
				.groupMember("non-exist")
				.singleResult();
		assertNull(group);
	}
	
	public void testFilterByGroupIdInAndUserId() {
		Group group = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_TEAMLEAD)
				.groupMember("operaton")
				.singleResult();
		assertNotNull(group);
		assertEquals("operaton-admin", group.getName());

		group = identityService.createGroupQuery()
				.groupIdIn(GROUP_ID_ADMIN, GROUP_ID_TEAMLEAD)
				.groupMember("non-exist")
				.singleResult();
		assertNull(group);
	}
	
	public void testGroupQueryFilterByUserIdSimilarToClientName() {
		Group group = identityService.createGroupQuery().groupMember("operaton-identity-service").singleResult();
		assertNotNull(group);
		assertEquals(GROUP_ID_SIMILAR_CLIENT_NAME, group.getId());
		assertEquals("operaton-identity-service", group.getName());
	}
}
