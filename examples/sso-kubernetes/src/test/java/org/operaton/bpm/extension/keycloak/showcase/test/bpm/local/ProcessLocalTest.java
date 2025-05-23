package org.operaton.bpm.extension.keycloak.showcase.test.bpm.local;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.runtime.ProcessInstance;
import org.operaton.bpm.engine.task.Task;
import org.operaton.bpm.engine.test.Deployment;
import org.operaton.bpm.engine.test.junit5.ProcessEngineExtension;
import org.operaton.bpm.engine.test.mock.Mocks;
import org.operaton.bpm.extension.keycloak.showcase.ProcessConstants.Variable;
import org.operaton.bpm.extension.keycloak.showcase.task.LoggerDelegate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.operaton.bpm.engine.test.assertions.bpmn.AbstractAssertions.init;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.complete;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task;
import static org.operaton.bpm.engine.test.assertions.bpmn.BpmnAwareTests.withVariables;

/**
 * Sample process unit test covering exactly the process itself with absolutely everything else mocked.
 * <p>
 * With this type of test you can identify errors in the BPM process itself early and before
 * integrating with more complex business logic.
 */
class ProcessLocalTest {

  /**
   * BPMN 2 file to the process under test.
   */
  private static final String PROCESS_RESOURCE = "processes/process.bpmn";

  /**
   * The process definition key of the process under test.
   */
  private static final String PROCESS_DEFINITION_KEY = "operaton.showcase";

  /**
   * Access to the process engine.
   */
  @RegisterExtension
  static ProcessEngineExtension extension = ProcessEngineExtension.builder()
      .configurationResource("operaton.local.cfg.xml")
      .build();

  /**
   * Mock for the sample service task.
   */
  @Mock
  private LoggerDelegate loggerTask;

  /**
   * Set up the test case.
   */
  @BeforeEach
  public void setup() {
    // Initialize and register mocks
    MockitoAnnotations.openMocks(this);
    Mocks.register("logger", loggerTask);

    // Initialize BPM Assert
    init(extension.getProcessEngine());
  }

  /**
   * Tear down test case.
   */
  @AfterEach
  public void tearDown() {
    // Reset mocks
    reset(loggerTask);
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  /**
   * Just tests if the process definition is deployable.
   */
  @Test
  @Deployment(resources = PROCESS_RESOURCE)
  void testParsingAndDeployment() {
    // nothing is done here, as we just want to check for exceptions during
    // deployment
  }

  /**
   * Test the happy (approved) path.
   */
  @Test
  @Deployment(resources = PROCESS_RESOURCE)
  void testApprovedPath() throws Exception {
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
    execute(job());
    assertThat(pi).hasPassed("ServiceTask_Logger");

    // check corresponding process end
    assertThat(pi).hasPassed("END_APPROVED");
    assertThat(pi).isEnded();

    // verify mocks
    verify(loggerTask, times(1)).execute(any(DelegateExecution.class));
  }

  /**
   * Test the not approved path.
   */
  @Test
  @Deployment(resources = PROCESS_RESOURCE)
  void testNotApprovedPath() throws Exception {
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

    // verify mocks
    verify(loggerTask, times(0)).execute(any(DelegateExecution.class));
  }
}
