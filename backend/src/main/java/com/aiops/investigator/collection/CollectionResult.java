package com.aiops.investigator.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One connector invocation stored for an attempt.
 *
 * <p>An entity is a Java class whose fields are columns. {@code source_system} plus the attempt is
 * unique, so the same attempt cannot store two results for AutoSys. A later attempt gets its own
 * rows. Evidence rows are separate and are not deleted when this result is written.
 */
@Entity
@Table(name = "collection_results")
public class CollectionResult {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "attempt_id", nullable = false)
  private UUID attemptId;

  @Column(name = "source_system", nullable = false, length = 50)
  private String sourceSystem;

  @Column(nullable = false, length = 50)
  private String status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at", nullable = false)
  private Instant endedAt;

  @Column private String warning;

  @Column(name = "error_category", length = 100)
  private String errorCategory;

  @Column(name = "retry_appropriate", nullable = false)
  private boolean retryAppropriate;

  @Column(name = "diagnostic_message", nullable = false)
  private String diagnosticMessage;

  protected CollectionResult() {}

  /**
   * Creates the row for one finished connector call.
   *
   * @param status one of the collection status names
   * @param retryAppropriate whether another attempt might succeed for a transient failure
   */
  public CollectionResult(
      UUID id,
      UUID incidentId,
      UUID attemptId,
      String sourceSystem,
      String status,
      Instant startedAt,
      Instant endedAt,
      String warning,
      String errorCategory,
      boolean retryAppropriate,
      String diagnosticMessage) {
    this.id = id;
    this.incidentId = incidentId;
    this.attemptId = attemptId;
    this.sourceSystem = sourceSystem;
    this.status = status;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.warning = warning;
    this.errorCategory = errorCategory;
    this.retryAppropriate = retryAppropriate;
    this.diagnosticMessage = diagnosticMessage;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

  public String getStatus() {
    return status;
  }

  public String getDiagnosticMessage() {
    return diagnosticMessage;
  }

  public String getErrorCategory() {
    return errorCategory;
  }
}
