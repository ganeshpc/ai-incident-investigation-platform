package com.aiops.investigator.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * One investigation request stored before any slow work starts.
 *
 * <p>{@code @Version} is a counter Hibernate increments on each update. If two transactions change
 * the same row, the second one fails instead of overwriting the first. Times are {@link Instant}
 * values, which are UTC moments rather than local clock readings.
 */
@Entity
@Table(name = "incidents")
public class Incident {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(name = "workload_id", nullable = false, length = 200)
  private String workloadId;

  @Column(nullable = false, length = 100)
  private String environment;

  @Enumerated(EnumType.STRING)
  @Column(name = "incident_type", nullable = false, length = 50)
  private IncidentType incidentType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private IncidentStatus status;

  @Column(name = "failure_time", nullable = false)
  private Instant failureTime;

  @Column(name = "lookback_minutes", nullable = false)
  private int lookbackMinutes;

  @Column(name = "autosys_job_name", length = 200)
  private String autosysJobName;

  @Column(name = "job_run_id", length = 200)
  private String jobRunId;

  @Column(name = "error_summary")
  private String errorSummary;

  @Column(name = "investigation_attempt_count", nullable = false)
  private int investigationAttemptCount;

  @Column(name = "idempotency_key", length = 200)
  private String idempotencyKey;

  @Column(name = "request_hash", length = 64)
  private String requestHash;

  @Column(name = "created_by", nullable = false, length = 200)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected Incident() {}

  /**
   * Stores a newly accepted investigation in {@link IncidentStatus#CREATED}.
   *
   * @param id the id returned to the caller
   * @param now the clock time for both creation and update
   */
  public Incident(
      UUID id,
      String title,
      String workloadId,
      String environment,
      IncidentType incidentType,
      Instant failureTime,
      int lookbackMinutes,
      String autosysJobName,
      String jobRunId,
      String idempotencyKey,
      String requestHash,
      String createdBy,
      Instant now) {
    this.id = id;
    this.title = title;
    this.workloadId = workloadId;
    this.environment = environment;
    this.incidentType = incidentType;
    this.status = IncidentStatus.CREATED;
    this.failureTime = failureTime;
    this.lookbackMinutes = lookbackMinutes;
    this.autosysJobName = autosysJobName;
    this.jobRunId = jobRunId;
    this.investigationAttemptCount = 1;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.createdBy = createdBy;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * Writes a status that {@link IncidentStateMachine} has already allowed.
   *
   * @param status the next status
   * @param now the clock time stored as the update time
   */
  public void applyStatus(IncidentStatus status, Instant now) {
    this.status = status;
    this.updatedAt = now;
  }

  /** Records why the investigation is partial or failed. */
  public void setErrorSummary(String errorSummary, Instant now) {
    this.errorSummary = errorSummary;
    this.updatedAt = now;
  }

  /** Increases the stored attempt count after a retry creates a new attempt. */
  public void addAttempt(Instant now) {
    this.investigationAttemptCount = this.investigationAttemptCount + 1;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getWorkloadId() {
    return workloadId;
  }

  public String getEnvironment() {
    return environment;
  }

  public IncidentType getIncidentType() {
    return incidentType;
  }

  public IncidentStatus getStatus() {
    return status;
  }

  public Instant getFailureTime() {
    return failureTime;
  }

  public int getLookbackMinutes() {
    return lookbackMinutes;
  }

  public String getAutosysJobName() {
    return autosysJobName;
  }

  public String getJobRunId() {
    return jobRunId;
  }

  public String getErrorSummary() {
    return errorSummary;
  }

  public int getInvestigationAttemptCount() {
    return investigationAttemptCount;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
