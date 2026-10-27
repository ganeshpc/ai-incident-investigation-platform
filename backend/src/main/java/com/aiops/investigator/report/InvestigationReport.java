package com.aiops.investigator.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The report produced by one attempt.
 *
 * <p>The row is absent until a later phase writes it. The read API uses that absence as "no report
 * yet" rather than inventing a successful report.
 */
@Entity
@Table(name = "investigation_reports")
public class InvestigationReport {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "attempt_id", nullable = false)
  private UUID attemptId;

  @Column(nullable = false, length = 50)
  private String status;

  @Column(name = "observed_failure")
  private String observedFailure;

  @Column private String summary;

  @Column(name = "missing_evidence")
  private String missingEvidence;

  @Column(name = "recommended_next_checks")
  private String recommendedNextChecks;

  @Column private String limitations;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected InvestigationReport() {}

  public UUID getId() {
    return id;
  }

  public UUID getAttemptId() {
    return attemptId;
  }

  public String getStatus() {
    return status;
  }

  public String getObservedFailure() {
    return observedFailure;
  }

  public String getSummary() {
    return summary;
  }

  public String getMissingEvidence() {
    return missingEvidence;
  }

  public String getRecommendedNextChecks() {
    return recommendedNextChecks;
  }

  public String getLimitations() {
    return limitations;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
