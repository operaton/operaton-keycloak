package org.operaton.bpm.extension.keycloak.showcase.task;

import java.util.logging.Logger;

import jakarta.inject.Named;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;

/**
 * This is an easy adapter implementation
 * illustrating how a Java Delegate can be used
 * from within a BPMN 2.0 Service Task.
 */
@Named("logger")
public class LoggerDelegate implements JavaDelegate {

  private static final Logger LOGGER = Logger.getLogger(LoggerDelegate.class.getName());

  public void execute(DelegateExecution execution) {

    LOGGER.info("\n\n  ... LoggerDelegate invoked by " + "processDefinitionId=" + execution.getProcessDefinitionId()
        + ", activityId=" + execution.getCurrentActivityId() + ", activityName='" + execution.getCurrentActivityName()
        + "'" + ", processInstanceId=" + execution.getProcessInstanceId() + ", businessKey="
        + execution.getProcessBusinessKey() + ", executionId=" + execution.getId() + " \n\n");
  }

}
