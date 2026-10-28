package com.aiops.investigator.connector;

/**
 * A read-only HTTP call did not return a usable document.
 *
 * <p>{@code status} is the HTTP status, or {@code 0} when the call did not reach a response. The
 * message names the status and the path. It does not include the response body or a credential.
 */
public final class RemoteCallException extends RuntimeException {

  private final int status;

  /**
   * @param status the HTTP status, or 0 for a transport failure
   * @param message a short sentence safe to store
   */
  public RemoteCallException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
