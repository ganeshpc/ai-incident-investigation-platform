package com.aiops.investigator.api;

import org.springframework.http.HttpStatus;

/**
 * A request failure with an HTTP status and a message that is safe to show.
 *
 * <p>The message is the problem-detail text. It names the workload or the rule that failed. It does
 * not include a stack trace, a password, or a file path from the server.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String title;

  /**
   * @param status the HTTP status to return
   * @param title a short label, such as "Unknown workload"
   * @param detail one sentence explaining the failure
   */
  public ApiException(HttpStatus status, String title, String detail) {
    super(detail);
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }
}
