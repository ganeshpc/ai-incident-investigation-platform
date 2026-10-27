package com.aiops.investigator.worker;

import com.aiops.investigator.audit.AuditRecorder;
import com.aiops.investigator.config.InvestigatorProperties;
import com.aiops.investigator.incident.Incident;
import com.aiops.investigator.incident.IncidentRepository;
import com.aiops.investigator.incident.IncidentStateMachine;
import com.aiops.investigator.incident.IncidentStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves waiting attempts so one worker owns them for a short lease.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} locks the selected rows and skips rows another transaction has
 * already locked. Two workers can poll at the same time and will not claim the same attempt. The
 * lease time lets a later poll claim the row again if this process stops before it finishes.
 */
@Component
public class AttemptClaimer {

  private final NamedParameterJdbcTemplate jdbc;
  private final IncidentRepository incidents;
  private final AuditRecorder audits;
  private final InvestigatorProperties properties;
  private final Clock clock;
  private final String owner = UUID.randomUUID().toString();

  public AttemptClaimer(
      NamedParameterJdbcTemplate jdbc,
      IncidentRepository incidents,
      AuditRecorder audits,
      InvestigatorProperties properties,
      Clock clock) {
    this.jdbc = jdbc;
    this.incidents = incidents;
    this.audits = audits;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Claims up to the configured batch of attempts and commits that claim.
   *
   * @return the attempt ids this process now owns
   */
  @Transactional
  public List<UUID> claim() {
    Instant now = clock.instant();
    int limit = Math.clamp(properties.getWorker().getBatchSize(), 1, 10);
    Instant until = now.plus(properties.getWorker().getLease());
    List<UUID> ids =
        jdbc.query(
            """
            SELECT id FROM investigation_attempts
            WHERE status = 'PENDING'
               OR (status = 'RUNNING' AND lease_until < :now)
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """,
            Map.of("now", Timestamp.from(now), "limit", limit),
            (rs, row) -> rs.getObject("id", UUID.class));
    if (ids.isEmpty()) {
      return List.of();
    }
    jdbc.update(
        """
        UPDATE investigation_attempts
        SET status = 'RUNNING',
            stage = CASE WHEN stage = 'CREATED' THEN 'COLLECTING_EVIDENCE' ELSE stage END,
            lease_owner = :owner,
            lease_until = :until,
            started_at = COALESCE(started_at, :now)
        WHERE id IN (:ids)
        """,
        Map.of(
            "owner",
            owner,
            "until",
            Timestamp.from(until),
            "now",
            Timestamp.from(now),
            "ids",
            ids));
    List<Claimed> claimed =
        jdbc.query(
            """
            SELECT id, incident_id, stage
            FROM investigation_attempts
            WHERE id IN (:ids)
            """,
            Map.of("ids", ids),
            (rs, row) ->
                new Claimed(
                    rs.getObject("id", UUID.class),
                    rs.getObject("incident_id", UUID.class),
                    rs.getString("stage")));
    for (Claimed claimedAttempt : claimed) {
      Incident incident = incidents.findById(claimedAttempt.incidentId()).orElseThrow();
      if (incident.getStatus() == IncidentStatus.CREATED) {
        IncidentStateMachine.require(IncidentStatus.CREATED, IncidentStatus.COLLECTING_EVIDENCE);
        incident.applyStatus(IncidentStatus.COLLECTING_EVIDENCE, now);
      }
      if (!audits.alreadyRecorded(claimedAttempt.id(), "COLLECTION_STARTED")
          && !"INVESTIGATING".equals(claimedAttempt.stage())) {
        audits.record(
            incident.getId(), claimedAttempt.id(), "COLLECTION_STARTED", "ACCEPTED", null);
      }
    }
    return ids;
  }

  /** The columns read back after the lease update. */
  private record Claimed(UUID id, UUID incidentId, String stage) {}
}
