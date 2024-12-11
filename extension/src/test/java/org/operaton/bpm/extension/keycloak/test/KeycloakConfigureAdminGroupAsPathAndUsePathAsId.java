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
 * Use group path in configuration and flag useGroupPathAsOperatonGroupId enabled.
 */
public class KeycloakConfigureAdminGroupAsPathAndUsePathAsId extends AbstractKeycloakIdentityProviderTest {

	public static Test suite() {
	    return new TestSetup(new TestSuite(KeycloakConfigureAdminGroupAsPathAndUsePathAsId.class)) {

	    	// @BeforeClass
	        protected void setUp() throws Exception {
	    		ProcessEngineConfigurationImpl config = (ProcessEngineConfigurationImpl) ProcessEngineConfiguration
	    				.createProcessEngineConfigurationFromResource("operaton.configureAdminGroupAsPathAndUsePathAsId.cfg.xml");
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
		processEngine.getAuthorizationService().createAuthorizationQuery().list().forEach(a ->
			processEngine.getAuthorizationService().deleteAuthorization(a.getId()));
	}

	// ------------------------------------------------------------------------
	// Test configuration
	// ------------------------------------------------------------------------

	public void testAdminGroupConfiguration() {
		// check engine configuration
		List<String> operatonAdminGroups = ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getAdminGroups();
		assertEquals(2, operatonAdminGroups.size()); // Operaton always adds "operaton-admin" as admin group ID - we want the other ID
		String adminGroupId = operatonAdminGroups.stream().filter(g -> !Groups.OPERATON_ADMIN.equals(g)).findFirst().get();
		
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
		assertEquals("root/child2", group.getId());
		assertEquals("child2", group.getName());

		// query groups using group member
		List<Group> groups = processEngine.getIdentityService().createGroupQuery().groupMember(user.getId()).list();
		assertNotNull(groups);
		assertEquals("Wrong number of groups for admin", 2, groups.size());
		
	}

}
