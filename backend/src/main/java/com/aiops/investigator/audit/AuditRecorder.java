package com.aiops.investigator.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Writes one audit row for an operation that already happened.
 *
 * <p>{@code @Service} marks this class as a Spring bean. Other beans receive it through their
 * constructor. The constructor is how Spring wires objects together without those objects calling
 * {@code new} on each other.
 */
@Service
public class AuditRecorder {

  private final AuditRepository audits;
  private final Clock clock;

  public AuditRecorder(AuditRepository audits, Clock clock) {
    this.audits = audits;
    this.clock = clock;
  }

  /**
   * Persists one audit event.
   *
   * @param operation a stable name such as INCIDENT_CREATED
   * @param result a short outcome such as ACCEPTED
   */
  public void record(
      UUID incidentId, UUID attemptId, String operation, String result, String message) {
    audits.save(new AuditEvent(incidentId, attemptId, operation, result, message, clock.instant()));
  }

  /** Returns whether this attempt already has one operation recorded. */
  public boolean alreadyRecorded(UUID attemptId, String operation) {
    return audits.existsByAttemptIdAndOperation(attemptId, operation);
  }

  /**
   * Writes an audit row in its own transaction.
   *
   * <p>A rejected retry throws after the audit is written. The caller's transaction then rolls
   * back. This method commits first, so the rejection remains visible.
   */
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void recordCommitted(
      UUID incidentId, UUID attemptId, String operation, String result, String message) {
    record(incidentId, attemptId, operation, result, message);
  }
}
