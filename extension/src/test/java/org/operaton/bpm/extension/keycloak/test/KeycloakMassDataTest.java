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
 * Keycloak mass data test checking the default maxResultSize configuration parameter (which is currently 250).
 * Will detect errors in applying the max parameter to Keyclaok REST API search queries as the Keycloak default is only 100.
 */
public class KeycloakMassDataTest extends AbstractKeycloakIdentityProviderTest {

	static List<String> USER_IDS = new ArrayList<String>();
	static List<String> GROUP_IDS = new ArrayList<String>();
	
	public static Test suite() {
	    return new TestSetup(new TestSuite(KeycloakMassDataTest.class)) {

	    	// @BeforeClass
	        protected void setUp() throws Exception {
	    		// setup Keycloak mass data test users
	        	// -------------------------------------
	    		HttpHeaders headers = authenticateKeycloakAdmin();
	    		String realm = "test";
	    		for (int i = 0; i < 60; i++) {
	    			USER_IDS.add(createUser(headers, realm, "test.user" + i, "Test" + i, "User Test" + i, "test.user" + i + "@test.info", "test"));
	    		}
				headers = authenticateKeycloakAdmin();
	    		for (int i = 0; i < 100; i++) {
	    			USER_IDS.add(createUser(headers, realm, "user.test" + i, "UTest" + i, "User Test" + i, "utest.user" + i + "@test.info", "test"));
	    		}
				HttpHeaders finalHeaders = headers;
				USER_IDS.forEach(u -> assignUserGroup(finalHeaders, realm, u, GROUP_ID_MANAGER));

				headers = authenticateKeycloakAdmin();
	    		for (int i = 0; i < 60; i++) {
	    			GROUP_IDS.add(createGroup(headers, realm, "test.group" + i, false));
	    		}
				headers = authenticateKeycloakAdmin();
	    		for (int i = 0; i < 100; i++) {
	    			GROUP_IDS.add(createGroup(headers, realm, "group.test" + i, false));
	    		}
				HttpHeaders finalHeaders1 = headers;
				GROUP_IDS.forEach(g -> assignUserGroup(finalHeaders1, realm, USER_ID_TEAMLEAD, g));
	    		
	    		// setup process engine
	    		// -------------------------------------
	    		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
	    				.createProcessEngineConfigurationFromResource("operaton.configureHigherMaxResultSize.cfg.xml");
	    		configureKeycloakIdentityProviderPlugin(config).setAdministratorUserId(USER_ID_OPERATON_ADMIN);
	    		PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
	        }
	        
	        // @AfterClass
	        protected void tearDown() throws Exception {
	        	// tear down process engine
	    		PluggableProcessEngineTestCase.cachedProcessEngine.close();
	    		PluggableProcessEngineTestCase.cachedProcessEngine = null;
	    		
	    		// delete mass data test users
	    		HttpHeaders headers = authenticateKeycloakAdmin();
	    		String realm = "test";
	    		USER_IDS.forEach(u -> deleteUser(headers, realm, u));
	    		GROUP_IDS.forEach(g -> deleteGroup(headers, realm, g));
	        }
	    };
	}

	public void testUserQueryNoFilter() {
		List<User> result = identityService.createUserQuery().list();
		assertEquals(USER_IDS.size() + 5, result.size());
	}

	public void testQueryUnlimitedList() {
	    List<User> result = identityService.createUserQuery().unlimitedList();
	    assertEquals(USER_IDS.size() + 5, result.size());
	}

	public void testGroupMemberQuery() {
		List<User> result = identityService.createUserQuery().memberOfGroup(GROUP_ID_MANAGER).list();	
		assertEquals(USER_IDS.size() + 1, result.size());
	}

	public void testUserQueryUserFirstNameLike() {
		List<User> result = identityService.createUserQuery().userFirstNameLike("UT%").list();
		assertEquals(100, result.size());
	}

	public void testUserQueryUserLastNameLike() {
		List<User> result = identityService.createUserQuery().userLastNameLike("User%").list();
		assertEquals(USER_IDS.size(), result.size());
	}
	
	public void testGroupQueryNoFilter() {
		List<Group> result = identityService.createGroupQuery().list();
		assertEquals(GROUP_IDS.size() + 9, result.size());
	}

	public void testGroupQueryGroupNameLike() {
		List<Group> result = identityService.createGroupQuery().groupNameLike("group%").list();
		assertEquals(100, result.size());
	}

	public void testGroupQueryGroupMember() {
		List<Group> result = identityService.createGroupQuery().groupMember("hans.mustermann@tradermail.info").list();
		assertEquals(GROUP_IDS.size() + 1, result.size());
	}
}
