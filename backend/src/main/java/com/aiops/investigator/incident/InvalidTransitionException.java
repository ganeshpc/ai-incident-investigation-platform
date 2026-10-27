package com.aiops.investigator.incident;

/**
 * Thrown when a status change is not in {@link IncidentStateMachine}.
 *
 * <p>This is a runtime exception, so callers are not forced to declare it. The API turns it into an
 * HTTP 409 response instead of writing the new status.
 */
public class InvalidTransitionException extends RuntimeException {

  private final IncidentStatus from;
  private final IncidentStatus to;

  /**
   * @param from the status that is stored now
   * @param to the status that was rejected
   */
  public InvalidTransitionException(IncidentStatus from, IncidentStatus to) {
    super("Cannot move an incident from " + from + " to " + to);
    this.from = from;
    this.to = to;
  }

  public IncidentStatus from() {
    return from;
  }

  public IncidentStatus to() {
    return to;
  }
}
