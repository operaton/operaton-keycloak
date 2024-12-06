package org.operaton.bpm.extension.keycloak.test;

import java.util.List;

import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.authorization.Authorization;
import org.operaton.bpm.engine.authorization.Groups;
import org.operaton.bpm.engine.authorization.Permissions;
import org.operaton.bpm.engine.authorization.Resources;
import org.operaton.bpm.engine.identity.Group;
import org.operaton.bpm.engine.identity.User;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.test.PluggableProcessEngineTestCase;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Admin group configuration test for the Keycloak identity provider.
 * Use group path in configuration.
 */
public class KeycloakConfigureAdminGroupAsPath extends AbstractKeycloakIdentityProviderTest {

	public static Test suite() {
	    return new TestSetup(new TestSuite(KeycloakConfigureAdminGroupAsPath.class)) {

	    	// @BeforeClass
	        protected void setUp() throws Exception {
	    		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
	    				.createProcessEngineConfigurationFromResource("operaton.configureAdminGroupAsPath.cfg.xml");
	    		configureKeycloakIdentityProviderPlugin(config);
	    		PluggableProcessEngineTestCase.cachedProcessEngine = config.buildProcessEngine();
	        }
	        
	        // @AfterClass
	        protected void tearDown() throws Exception {
	    		PluggableProcessEngineTestCase.cachedProcessEngine.close();
	    		PluggableProcessEngineTestCase.cachedProcessEngine = null;
	        }
	    };
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		// delete all created authorizations
		processEngine.getAuthorizationService().createAuthorizationQuery().list().forEach(a -> {
			processEngine.getAuthorizationService().deleteAuthorization(a.getId());
		});
	}

	// ------------------------------------------------------------------------
	// Test configuration
	// ------------------------------------------------------------------------

	public void testAdminGroupConfiguration() {
		// check engine configuration
		List<String> camundaAdminGroups = ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getAdminGroups();
		assertEquals(2, camundaAdminGroups.size()); // camunda always adds "camunda-admin" as admin group ID - we want the other ID
		String adminGroupId = camundaAdminGroups.stream().filter(g -> !Groups.OPERATON_ADMIN.equals(g)).findFirst().get();
		
		// check that authorizations have been created
		assertTrue(processEngine.getAuthorizationService().createAuthorizationQuery()
				.groupIdIn(adminGroupId).count() > 0);
		
		// check sample authorization for applications
		assertEquals(1, processEngine.getAuthorizationService().createAuthorizationQuery()
				.groupIdIn(adminGroupId)
				.resourceType(Resources.APPLICATION)
				.resourceId(Authorization.ANY)
				.hasPermission(Permissions.ALL)
				.count());

		// query user data
		User user = processEngine.getIdentityService().createUserQuery().memberOfGroup(adminGroupId).singleResult();
		assertNotNull(user);
		assertEquals("johnfoo@gmail.com", user.getEmail());
		
		// query groups
		Group group = processEngine.getIdentityService().createGroupQuery().groupId(adminGroupId).singleResult();
		assertNotNull(group);
		assertEquals("subchild1", group.getName());
	}

}