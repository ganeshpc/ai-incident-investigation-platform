package com.aiops.investigator.attempt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One pass of work for an incident.
 *
 * <p>A retry inserts another row. It does not change the earlier row. {@code lease_until} is the
 * moment a claimed attempt becomes free again if the worker stops before it finishes.
 */
@Entity
@Table(name = "investigation_attempts")
public class InvestigationAttempt {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private AttemptStage stage;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private AttemptStatus status;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "retry_of_attempt_id")
  private UUID retryOfAttemptId;

  @Column(name = "rerun_successful_collections", nullable = false)
  private boolean rerunSuccessfulCollections;

  @Column(name = "model_name", length = 200)
  private String modelName;

  @Column(name = "prompt_version", length = 100)
  private String promptVersion;

  @Column(name = "lease_owner", length = 200)
  private String leaseOwner;

  @Column(name = "lease_until")
  private Instant leaseUntil;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected InvestigationAttempt() {}

  /**
   * Creates a waiting attempt.
   *
   * @param stage {@link AttemptStage#CREATED} for collection, or {@link AttemptStage#INVESTIGATING}
   *     when collection is being left as it is
   * @param retryOfAttemptId the previous attempt, or null for the first one
   */
  public InvestigationAttempt(
      UUID id,
      UUID incidentId,
      int attemptNumber,
      AttemptStage stage,
      UUID retryOfAttemptId,
      boolean rerunSuccessfulCollections,
      Instant createdAt) {
    this.id = id;
    this.incidentId = incidentId;
    this.attemptNumber = attemptNumber;
    this.stage = stage;
    this.status = AttemptStatus.PENDING;
    this.retryOfAttemptId = retryOfAttemptId;
    this.rerunSuccessfulCollections = rerunSuccessfulCollections;
    this.createdAt = createdAt;
  }

  /** Marks the attempt finished and releases the lease. */
  public void complete(AttemptStage stage, Instant completedAt) {
    this.stage = stage;
    this.status = AttemptStatus.COMPLETED;
    this.completedAt = completedAt;
    this.leaseOwner = null;
    this.leaseUntil = null;
  }

  /** Marks the attempt failed, keeps the reason, and releases the lease. */
  public void fail(String reason, Instant completedAt) {
    this.status = AttemptStatus.FAILED;
    this.failureReason = reason;
    this.completedAt = completedAt;
    this.leaseOwner = null;
    this.leaseUntil = null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getIncidentId() {
    return incidentId;
  }

  public int getAttemptNumber() {
    return attemptNumber;
  }

  public AttemptStage getStage() {
    return stage;
  }

  public AttemptStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public UUID getRetryOfAttemptId() {
    return retryOfAttemptId;
  }

  public boolean isRerunSuccessfulCollections() {
    return rerunSuccessfulCollections;
  }

  public String getModelName() {
    return modelName;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public String getLeaseOwner() {
    return leaseOwner;
  }

  public Instant getLeaseUntil() {
    return leaseUntil;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Used by the lease test to simulate a worker that stopped. */
  public void expireLease(Instant leaseUntil) {
    this.status = AttemptStatus.RUNNING;
    this.stage = AttemptStage.COLLECTING_EVIDENCE;
    this.leaseOwner = "expired-worker";
    this.leaseUntil = leaseUntil;
    this.startedAt = leaseUntil;
  }
}
