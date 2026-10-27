package com.aiops.investigator.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One recorded action for an incident.
 *
 * <p>The row stores the operation name, the result, and the time. It does not store request
 * headers, credentials, or evidence text.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(name = "incident_id")
  private UUID incidentId;

  @Column(name = "attempt_id")
  private UUID attemptId;

  @Column(nullable = false, length = 80)
  private String operation;

  @Column(nullable = false, length = 50)
  private String result;

  @Column private String message;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditEvent() {}

  /**
   * @param operation a stable name such as INCIDENT_CREATED
   * @param result a short outcome such as ACCEPTED or PARTIAL
   * @param message one sentence, or null when the operation name is enough
   */
  public AuditEvent(
      UUID incidentId,
      UUID attemptId,
      String operation,
      String result,
      String message,
      Instant occurredAt) {
    this.id = UUID.randomUUID();
    this.incidentId = incidentId;
    this.attemptId = attemptId;
    this.operation = operation;
    this.result = result;
    this.message = message;
    this.occurredAt = occurredAt;
  }

  public UUID getIncidentId() {
    return incidentId;
  }

  public UUID getAttemptId() {
    return attemptId;
  }

  public String getOperation() {
    return operation;
  }

  public String getResult() {
    return result;
  }

  public String getMessage() {
    return message;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
