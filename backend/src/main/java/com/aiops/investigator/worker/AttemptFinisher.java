package com.aiops.investigator.worker;

import com.aiops.investigator.attempt.AttemptRepository;
import com.aiops.investigator.attempt.AttemptStage;
import com.aiops.investigator.attempt.AttemptStatus;
import com.aiops.investigator.attempt.InvestigationAttempt;
import com.aiops.investigator.audit.AuditRecorder;
import com.aiops.investigator.incident.Incident;
import com.aiops.investigator.incident.IncidentRepository;
import com.aiops.investigator.incident.IncidentStateMachine;
import com.aiops.investigator.incident.IncidentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finishes a claimed attempt.
 *
 * <p>Phase 2 has no connectors and does not call the AI service. Collection ends as partial, and an
 * attempt that was only waiting for analysis ends as partial too. The stored evidence, if a later
 * phase has written any, is left in place.
 */
@Component
public class AttemptFinisher {

  public static final String NO_CONNECTORS = "No evidence connectors are registered.";
  public static final String NO_AI = "AI analysis is not configured.";

  private final AttemptRepository attempts;
  private final IncidentRepository incidents;
  private final AuditRecorder audits;
  private final Clock clock;

  public AttemptFinisher(
      AttemptRepository attempts, IncidentRepository incidents, AuditRecorder audits, Clock clock) {
    this.attempts = attempts;
    this.incidents = incidents;
    this.audits = audits;
    this.clock = clock;
  }

  /** Completes one running attempt. A second call does nothing once the attempt is finished. */
  @Transactional
  public void finish(UUID attemptId) {
    InvestigationAttempt attempt = attempts.findById(attemptId).orElseThrow();
    if (attempt.getStatus() != AttemptStatus.RUNNING) {
      return;
    }
    Incident incident = incidents.findById(attempt.getIncidentId()).orElseThrow();
    Instant now = clock.instant();
    if (attempt.getStage() == AttemptStage.INVESTIGATING) {
      move(incident, IncidentStatus.PARTIAL, NO_AI, now);
      attempt.complete(AttemptStage.INVESTIGATING, now);
      audits.record(incident.getId(), attemptId, "AI_ANALYSIS_SKIPPED", "PARTIAL", NO_AI);
      return;
    }
    move(incident, IncidentStatus.PARTIAL, NO_CONNECTORS, now);
    attempt.complete(AttemptStage.EVIDENCE_READY, now);
    audits.record(incident.getId(), attemptId, "COLLECTION_COMPLETED", "PARTIAL", NO_CONNECTORS);
  }

  /**
   * Records a failed attempt without deleting earlier attempts or evidence.
   *
   * @param reason a short message safe to store and show
   */
  @Transactional
  public void fail(UUID attemptId, String reason) {
    InvestigationAttempt attempt = attempts.findById(attemptId).orElseThrow();
    if (attempt.getStatus() == AttemptStatus.COMPLETED
        || attempt.getStatus() == AttemptStatus.FAILED) {
      return;
    }
    Instant now = clock.instant();
    attempt.fail(reason, now);
    Incident incident = incidents.findById(attempt.getIncidentId()).orElseThrow();
    if (incident.getStatus() != IncidentStatus.FAILED) {
      IncidentStateMachine.require(incident.getStatus(), IncidentStatus.FAILED);
      incident.applyStatus(IncidentStatus.FAILED, now);
    }
    incident.setErrorSummary(reason, now);
    audits.record(incident.getId(), attemptId, "ATTEMPT_FAILED", "FAILED", reason);
  }

  private static void move(Incident incident, IncidentStatus status, String summary, Instant now) {
    if (incident.getStatus() != status) {
      IncidentStateMachine.require(incident.getStatus(), status);
      incident.applyStatus(status, now);
    }
    incident.setErrorSummary(summary, now);
  }
}
