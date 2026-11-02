package org.operaton.bpm.extension.keycloak.json;

import java.io.Serial;

/**
 * Exception thrown in case of any JSON errors.
 */
public class JsonException extends Exception {

  /**
   * This class' serial version UID.
   */
  @Serial
  private static final long serialVersionUID = -3207252773395186866L;

  /**
   * Constructs a new JsonException.
   *
   * @param message the message
   * @param cause   the original cause
   */
  public JsonException(String message, Throwable cause) {
    super(message, cause);
  }

}
