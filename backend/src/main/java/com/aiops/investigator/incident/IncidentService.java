package com.aiops.investigator.incident;

import com.aiops.investigator.api.ApiException;
import com.aiops.investigator.attempt.AttemptRepository;
import com.aiops.investigator.attempt.AttemptStage;
import com.aiops.investigator.attempt.AttemptStatus;
import com.aiops.investigator.attempt.InvestigationAttempt;
import com.aiops.investigator.audit.AuditRecorder;
import com.aiops.investigator.evidence.EvidenceRecord;
import com.aiops.investigator.evidence.EvidenceRepository;
import com.aiops.investigator.report.InvestigationReport;
import com.aiops.investigator.report.ReportRepository;
import com.aiops.investigator.workload.WorkloadRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Accepts investigation requests and reads what has been stored.
 *
 * <p>The create and retry methods commit their rows before they return. They do not call a
 * connector or the AI service. {@link TransactionTemplate} starts a database transaction, runs the
 * work, and commits it. A transaction is the boundary that makes the incident row and its first
 * attempt appear together or not at all.
 */
@Service
public class IncidentService {

  private final IncidentRepository incidents;
  private final AttemptRepository attempts;
  private final EvidenceRepository evidence;
  private final ReportRepository reports;
  private final WorkloadRepository workloads;
  private final AuditRecorder audits;
  private final Clock clock;
  private final TransactionTemplate transactions;

  public IncidentService(
      IncidentRepository incidents,
      AttemptRepository attempts,
      EvidenceRepository evidence,
      ReportRepository reports,
      WorkloadRepository workloads,
      AuditRecorder audits,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.incidents = incidents;
    this.attempts = attempts;
    this.evidence = evidence;
    this.reports = reports;
    this.workloads = workloads;
    this.audits = audits;
    this.clock = clock;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  /**
   * Stores a new incident, or returns the original one when the idempotency key is reused.
   *
   * @param idempotencyKey the header value, or null when the caller did not send one
   * @param actor the X-Actor header, or null when the caller did not send one
   */
  public AcceptedInvestigation investigate(
      InvestigateCommand command, String idempotencyKey, String actor) {
    String key = blankToNull(idempotencyKey);
    String createdBy = actorOrDefault(actor);
    try {
      return transactions.execute(status -> create(command, key, createdBy));
    } catch (DataIntegrityViolationException ex) {
      return replayOrConflict(key, command.requestHash());
    }
  }

  /** Returns the incident or answers 404. */
  public Incident get(UUID incidentId) {
    return incidents.findById(incidentId).orElseThrow(() -> notFound(incidentId));
  }

  /** Returns one page of incidents, newest first. */
  public Page<Incident> list(int page, int size) {
    return incidents.findAllByOrderByCreatedAtDesc(pageOf(page, size));
  }

  /** Returns one page of evidence. An incident with no evidence is an empty page, not a 404. */
  public Page<EvidenceRecord> evidence(UUID incidentId, int page, int size) {
    get(incidentId);
    return evidence.findForIncident(incidentId, pageOf(page, size));
  }

  /** Returns the newest report, or 404 when no attempt has written one. */
  public InvestigationReport report(UUID incidentId) {
    get(incidentId);
    return reports
        .findFirstByIncidentIdOrderByCreatedAtDesc(incidentId)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Report not ready",
                    "No report is available for this incident."));
  }

  /** Returns every attempt, oldest first. */
  public List<InvestigationAttempt> attempts(UUID incidentId) {
    get(incidentId);
    return attempts.findByIncidentIdOrderByAttemptNumberAsc(incidentId);
  }

  /**
   * Opens a new attempt and leaves the earlier attempts in place.
   *
   * @param rerunCompletedInvestigation required when the incident is already REPORT_READY
   * @param rerunSuccessfulCollections starts again at collection even if collection finished
   */
  public Incident retry(
      UUID incidentId,
      boolean rerunCompletedInvestigation,
      boolean rerunSuccessfulCollections,
      String actor) {
    actorOrDefault(actor);
    return transactions.execute(
        status ->
            retryInTransaction(
                incidentId, rerunCompletedInvestigation, rerunSuccessfulCollections));
  }

  private AcceptedInvestigation create(InvestigateCommand command, String key, String createdBy) {
    if (key != null) {
      var existing = incidents.findByIdempotencyKey(key);
      if (existing.isPresent()) {
        return sameOrConflict(existing.get(), command.requestHash());
      }
    }
    if (!workloads.existsById(command.workloadId())) {
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "Unknown workload",
          "No workload is registered for " + command.workloadId() + ".");
    }
    Instant now = clock.instant();
    UUID incidentId = UUID.randomUUID();
    UUID attemptId = UUID.randomUUID();
    Incident incident =
        new Incident(
            incidentId,
            command.title(),
            command.workloadId(),
            command.environment(),
            command.incidentType(),
            command.failureTime(),
            command.lookbackMinutes(),
            command.autosysJobName(),
            command.jobRunId(),
            key,
            key == null ? null : command.requestHash(),
            createdBy,
            now);
    incidents.save(incident);
    attempts.save(
        new InvestigationAttempt(attemptId, incidentId, 1, AttemptStage.CREATED, null, false, now));
    audits.record(incidentId, attemptId, "INCIDENT_CREATED", "ACCEPTED", null);
    return new AcceptedInvestigation(incident, attemptId, 1, false);
  }

  private Incident retryInTransaction(
      UUID incidentId, boolean rerunCompletedInvestigation, boolean rerunSuccessfulCollections) {
    Incident incident = get(incidentId);
    InvestigationAttempt latest =
        attempts
            .findFirstByIncidentIdOrderByAttemptNumberDesc(incidentId)
            .orElseThrow(() -> notFound(incidentId));
    if (latest.getStatus() == AttemptStatus.PENDING
        || latest.getStatus() == AttemptStatus.RUNNING) {
      audits.recordCommitted(
          incidentId,
          latest.getId(),
          "RETRY_REJECTED",
          "REJECTED",
          "An attempt is still in progress.");
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Attempt in progress",
          "An investigation attempt is still in progress.");
    }
    if (incident.getStatus() == IncidentStatus.REPORT_READY && !rerunCompletedInvestigation) {
      audits.recordCommitted(
          incidentId,
          latest.getId(),
          "RETRY_REJECTED",
          "REJECTED",
          "The investigation is already finished.");
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Investigation already finished",
          "Retry a finished investigation only when rerunCompletedInvestigation is true.");
    }
    IncidentStatus target = retryTarget(incident, latest, rerunSuccessfulCollections);
    IncidentStateMachine.require(incident.getStatus(), target);
    Instant now = clock.instant();
    incident.applyStatus(target, now);
    incident.addAttempt(now);
    AttemptStage stage =
        target == IncidentStatus.INVESTIGATING ? AttemptStage.INVESTIGATING : AttemptStage.CREATED;
    UUID attemptId = UUID.randomUUID();
    attempts.save(
        new InvestigationAttempt(
            attemptId,
            incidentId,
            latest.getAttemptNumber() + 1,
            stage,
            latest.getId(),
            rerunSuccessfulCollections,
            now));
    audits.record(
        incidentId,
        attemptId,
        "RETRY_REQUESTED",
        "ACCEPTED",
        "Opened attempt " + (latest.getAttemptNumber() + 1));
    return incident;
  }

  /**
   * Chooses the status a retry moves to.
   *
   * <p>Evidence that is already collected stays put unless the caller sets {@code
   * rerunSuccessfulCollections}. That flag is the only reason to return from {@code EVIDENCE_READY}
   * to collection. Otherwise a finished collection moves on to investigation.
   */
  private static IncidentStatus retryTarget(
      Incident incident, InvestigationAttempt latest, boolean rerunSuccessfulCollections) {
    if (rerunSuccessfulCollections) {
      return IncidentStatus.COLLECTING_EVIDENCE;
    }
    if (incident.getStatus() == IncidentStatus.EVIDENCE_READY
        || latest.getStage() == AttemptStage.INVESTIGATING
        || incident.getStatus() == IncidentStatus.REPORT_READY) {
      return IncidentStatus.INVESTIGATING;
    }
    return IncidentStatus.COLLECTING_EVIDENCE;
  }

  private AcceptedInvestigation replayOrConflict(String key, String hash) {
    if (key == null) {
      throw new ApiException(HttpStatus.CONFLICT, "Conflict", "The incident could not be stored.");
    }
    Incident existing =
        incidents
            .findByIdempotencyKey(key)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.CONFLICT, "Conflict", "The incident could not be stored."));
    return sameOrConflict(existing, hash);
  }

  private AcceptedInvestigation sameOrConflict(Incident existing, String hash) {
    if (!hash.equals(existing.getRequestHash())) {
      audits.recordCommitted(
          existing.getId(),
          null,
          "IDEMPOTENCY_CONFLICT",
          "REJECTED",
          "Same key with a different request.");
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Idempotency conflict",
          "This Idempotency-Key was already used with a different request.");
    }
    InvestigationAttempt first =
        attempts.findByIncidentIdOrderByAttemptNumberAsc(existing.getId()).getFirst();
    audits.recordCommitted(existing.getId(), first.getId(), "IDEMPOTENCY_REPLAY", "REPLAYED", null);
    return new AcceptedInvestigation(existing, first.getId(), first.getAttemptNumber(), true);
  }

  private static PageRequest pageOf(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Invalid page",
          "page must be zero or greater and size must be from 1 to 100.");
    }
    return PageRequest.of(page, size);
  }

  private static ApiException notFound(UUID incidentId) {
    return new ApiException(
        HttpStatus.NOT_FOUND,
        "Incident not found",
        "No incident exists with id " + incidentId + ".");
  }

  private static String actorOrDefault(String actor) {
    String value = blankToNull(actor);
    if (value == null) {
      return "local-dev";
    }
    if (value.length() > 200) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "Invalid actor", "X-Actor must be 200 characters or fewer.");
    }
    return value;
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  /**
   * The incident that was accepted, and whether this call reused an earlier one.
   *
   * @param replayed true when the idempotency key matched a stored request
   */
  public record AcceptedInvestigation(
      Incident incident, UUID attemptId, int attemptNumber, boolean replayed) {}
}
