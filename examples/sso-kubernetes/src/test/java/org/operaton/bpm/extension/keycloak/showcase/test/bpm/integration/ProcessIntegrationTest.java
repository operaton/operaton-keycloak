package org.operaton.bpm.extension.keycloak.showcase.test.bpm.integration;

import org.apache.ibatis.logging.LogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.extension.keycloak.showcase.ProcessConstants.Variable;
import org.operaton.bpm.extension.keycloak.showcase.plugin.KeycloakIdentityProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.operaton.bpm.engine.test.assertions.bpmn.AbstractAssertions.init;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;
import static org.operaton.bpm.extension.keycloak.showcase.test.util.ProcessTestAssertions.waitUntil;

/**
 * Test case starting an in-memory database-backed Process Engine running
 * with the complete Spring Boot stack. The web front end is omitted.
 * <p>
 * With this type of test you integrate all your local services and identify
 * errors arising out of the combination of the service implementation with
 * the BPM process.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class ProcessIntegrationTest {

  private static final String PROCESS_DEFINITION_KEY = "operaton.showcase";

  @Autowired
  private ProcessEngine processEngine;

  @MockBean
  public KeycloakIdentityProvider disableKeycloak;

  static {
    LogFactory.useSlf4jLogging(); // MyBatis
  }

  @BeforeEach
  public void setup() {
    // init BPM assert
    init(processEngine);
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  /**
   * Test the happy (approved) path.
   */
  @Test
  public void testApprovedPath() {
    // start process
    ProcessInstance pi = runtimeService().startProcessInstanceByKey(PROCESS_DEFINITION_KEY,
        withVariables(Variable.NAME, "Demo"));
    assertThat(pi).isStarted();

    // check user task and approve user
    assertThat(pi).isWaitingAt("ApproveUser");
    Task task = task();
    assertNotNull(task, "User task expected");
    complete(task, withVariables("approved", Boolean.TRUE));

    // check service task (asynchronous continuation)
    waitUntil(pi).hasPassed("ServiceTask_Logger");

    // check corresponding process end
    assertThat(pi).hasPassed("END_APPROVED");
    assertThat(pi).isEnded();

    // TODO: insert assertions checking your local business logic impacts
  }

  /**
   * Test the not approved path.
   */
  @Test
  public void testNotApprovedPath() {
    // start process
    ProcessInstance pi = runtimeService().startProcessInstanceByKey(PROCESS_DEFINITION_KEY,
        withVariables(Variable.NAME, "Demo"));
    assertThat(pi).isStarted();

    // check user task and do not approve user
    assertThat(pi).isWaitingAt("ApproveUser");
    Task task = task();
    assertNotNull(task, "User task expected");
    complete(task, withVariables("approved", Boolean.FALSE));

    // check corresponding process end
    assertThat(pi).hasPassed("END_NOT_APPROVED");
    assertThat(pi).isEnded();

    // TODO: insert assertions checking your local business logic impacts
  }
}
