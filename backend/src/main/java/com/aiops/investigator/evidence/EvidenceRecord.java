package com.aiops.investigator.evidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One observation that belongs to an incident.
 *
 * <p>Phase 2 creates the table and the read API. Connectors insert rows in a later phase. {@code
 * evidence_key} is unique per incident so a retry cannot store the same observation twice.
 */
@Entity
@Table(name = "evidence_records")
public class EvidenceRecord {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "attempt_id", nullable = false)
  private UUID attemptId;

  @Column(name = "evidence_key", nullable = false, length = 200)
  private String evidenceKey;

  @Column(name = "source_system", nullable = false, length = 50)
  private String sourceSystem;

  @Column(name = "workload_id", nullable = false, length = 200)
  private String workloadId;

  @Column(name = "evidence_type", nullable = false, length = 50)
  private String evidenceType;

  @Column(name = "event_time")
  private Instant eventTime;

  @Column(name = "event_time_precision", nullable = false, length = 20)
  private String eventTimePrecision;

  @Column(name = "retrieval_time", nullable = false)
  private Instant retrievalTime;

  @Column(name = "source_reference", nullable = false)
  private String sourceReference;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private String attributes;

  @Column(name = "content_excerpt")
  private String contentExcerpt;

  @Column(name = "content_hash", length = 64)
  private String contentHash;

  @Column(nullable = false, length = 50)
  private String provenance;

  @Column(name = "redaction_status", nullable = false, length = 50)
  private String redactionStatus;

  @Column(length = 50)
  private String role;

  protected EvidenceRecord() {}

  public UUID getId() {
    return id;
  }

  public UUID getIncidentId() {
    return incidentId;
  }

  public String getEvidenceKey() {
    return evidenceKey;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

  public String getWorkloadId() {
    return workloadId;
  }

  public String getEvidenceType() {
    return evidenceType;
  }

  public Instant getEventTime() {
    return eventTime;
  }

  public String getEventTimePrecision() {
    return eventTimePrecision;
  }

  public Instant getRetrievalTime() {
    return retrievalTime;
  }

  public String getSourceReference() {
    return sourceReference;
  }

  public String getContentExcerpt() {
    return contentExcerpt;
  }

  public String getProvenance() {
    return provenance;
  }

  public String getRedactionStatus() {
    return redactionStatus;
  }

  public String getRole() {
    return role;
  }
}
