package com.aiops.investigator.workload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row in the workload registry.
 *
 * <p>An entity is a Java class whose fields are columns. {@code workload_id} is the primary key,
 * the value that identifies the row. The connector mapping is JSON because each workload names a
 * different set of sources. The job name is also its own column so a query can find it without
 * reading the JSON.
 */
@Entity
@Table(name = "workload_registry")
public class Workload {

  @Id
  @Column(name = "workload_id", nullable = false, length = 200)
  private String workloadId;

  @Column(name = "display_name", nullable = false, length = 300)
  private String displayName;

  @Column(nullable = false, length = 100)
  private String environment;

  @Column(nullable = false, length = 50)
  private String provenance;

  @Column(name = "autosys_job_name", length = 200)
  private String autosysJobName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private String definition;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Workload() {}

  /**
   * Creates a registry row from one fixture file.
   *
   * @param workloadId the id inside the fixture
   * @param displayName the human-readable name
   * @param environment the fixture environment, such as test
   * @param provenance SIMULATED for the committed fixtures
   * @param autosysJobName the scheduler job name, or null when the workload has no job
   * @param definition the fixture document as JSON
   * @param contentHash the hash of the raw fixture bytes
   * @param now the clock time used for both timestamps
   */
  public Workload(
      String workloadId,
      String displayName,
      String environment,
      String provenance,
      String autosysJobName,
      String definition,
      String contentHash,
      Instant now) {
    this.workloadId = workloadId;
    this.displayName = displayName;
    this.environment = environment;
    this.provenance = provenance;
    this.autosysJobName = autosysJobName;
    this.definition = definition;
    this.contentHash = contentHash;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * Replaces the stored document when the fixture file changed.
   *
   * @param now the clock time recorded as the update time
   */
  public void replace(
      String displayName,
      String environment,
      String provenance,
      String autosysJobName,
      String definition,
      String contentHash,
      Instant now) {
    this.displayName = displayName;
    this.environment = environment;
    this.provenance = provenance;
    this.autosysJobName = autosysJobName;
    this.definition = definition;
    this.contentHash = contentHash;
    this.updatedAt = now;
  }

  public String getWorkloadId() {
    return workloadId;
  }

  public String getContentHash() {
    return contentHash;
  }
}
