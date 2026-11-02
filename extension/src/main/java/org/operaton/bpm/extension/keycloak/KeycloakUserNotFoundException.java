package org.operaton.bpm.extension.keycloak;

import java.io.Serial;

/**
 * Thrown in case a query for a unique user fails.
 */
public class KeycloakUserNotFoundException extends Exception {

  /**
   * This class' serial version UID.
   */
  @Serial
  private static final long serialVersionUID = -160645252403548564L;

  /**
   * Creates a new KeycloakUserNotFoundException.
   *
   * @param message the message
   */
  public KeycloakUserNotFoundException(String message) {
    super(message);
  }

  /**
   * Creates a new KeycloakUserNotFoundException.
   *
   * @param message the message
   * @param cause   the original cause
   */
  public KeycloakUserNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

}
