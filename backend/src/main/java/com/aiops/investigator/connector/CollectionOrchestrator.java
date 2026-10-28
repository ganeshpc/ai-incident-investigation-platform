package com.aiops.investigator.connector;

import com.aiops.investigator.attempt.InvestigationAttempt;
import com.aiops.investigator.collection.CollectionResult;
import com.aiops.investigator.collection.CollectionResultRepository;
import com.aiops.investigator.evidence.EvidenceRecord;
import com.aiops.investigator.evidence.EvidenceRepository;
import com.aiops.investigator.incident.Incident;
import com.aiops.investigator.incident.IncidentStatus;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs every connector for one attempt and stores the rows.
 *
 * <p>Connectors run on a pool of two threads because a later real connector may wait on the
 * network. Two is the bound. The whole batch has five seconds. A connector that does not finish is
 * cancelled and stored as failed, and its evidence is not written. These synthetic connectors only
 * read local files, so the wait is a file read. They do not touch the database. This method writes
 * the rows after they return, inside the attempt's transaction.
 */
@Component
public class CollectionOrchestrator {

  /** The sentence stored when the process has no connector beans. */
  public static final String NO_CONNECTORS = "No evidence connectors are registered.";

  private static final long BUDGET_SECONDS = 5;

  private final List<EvidenceConnector> connectors;
  private final SyntheticScenarioIndex scenarios;
  private final EvidenceRepository evidence;
  private final CollectionResultRepository results;
  private final Clock clock;
  private final ExecutorService reads =
      Executors.newFixedThreadPool(
          2,
          runnable -> {
            Thread thread = new Thread(runnable, "evidence-collection");
            thread.setDaemon(true);
            return thread;
          });

  public CollectionOrchestrator(
      List<EvidenceConnector> connectors,
      SyntheticScenarioIndex scenarios,
      EvidenceRepository evidence,
      CollectionResultRepository results,
      Clock clock) {
    this.connectors = connectors;
    this.scenarios = scenarios;
    this.evidence = evidence;
    this.results = results;
    this.clock = clock;
  }

  /** Stops the read threads when the application shuts down. */
  @PreDestroy
  public void stop() {
    reads.shutdownNow();
  }

  /**
   * Collects evidence and returns the incident status collection should move to.
   *
   * <p>{@code @Transactional} joins the worker transaction when the finisher already has one. A
   * source that is not configured does not make the incident partial. A failed or partial source
   * does. Evidence from the sources that succeeded is still stored.
   */
  @Transactional
  public CollectionSummary collect(Incident incident, InvestigationAttempt attempt) {
    List<EvidenceConnector> ordered =
        connectors.stream().sorted(Comparator.comparing(EvidenceConnector::sourceSystem)).toList();
    if (ordered.isEmpty()) {
      return new CollectionSummary(IncidentStatus.PARTIAL, NO_CONNECTORS, "PARTIAL");
    }
    CollectionRequest request = requestFor(incident);
    Instant started = clock.instant();
    List<Future<CollectionOutcome>> futures;
    try {
      List<Callable<CollectionOutcome>> tasks = new ArrayList<>();
      for (EvidenceConnector connector : ordered) {
        tasks.add(() -> connector.collect(request));
      }
      futures = reads.invokeAll(tasks, BUDGET_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Evidence collection was interrupted");
    }
    Instant ended = clock.instant();
    LinkedHashSet<String> problems = new LinkedHashSet<>();
    boolean anyEvidence = false;
    for (int index = 0; index < ordered.size(); index++) {
      CollectionOutcome outcome = outcomeOf(ordered.get(index), futures.get(index));
      anyEvidence = store(incident, attempt, outcome, started, ended) || anyEvidence;
      if (outcome.status() != CollectionStatus.SUCCESS
          && outcome.status() != CollectionStatus.UNCONFIGURED) {
        problems.add(outcome.diagnosticMessage());
      }
    }
    if (!problems.isEmpty()) {
      String message = String.join(" ", problems);
      return new CollectionSummary(IncidentStatus.PARTIAL, message, "PARTIAL");
    }
    if (!anyEvidence) {
      return new CollectionSummary(
          IncidentStatus.PARTIAL, "No synthetic evidence was collected.", "PARTIAL");
    }
    return new CollectionSummary(IncidentStatus.EVIDENCE_READY, null, "SUCCESS");
  }

  /** Builds the request from the incident, the workload file, and the matching scenario. */
  private CollectionRequest requestFor(Incident incident) {
    return new CollectionRequest(
        incident.getWorkloadId(),
        incident.getEnvironment(),
        incident.getFailureTime(),
        incident.getLookbackMinutes(),
        incident.getAutosysJobName(),
        incident.getJobRunId(),
        scenarios.workload(incident.getWorkloadId()),
        scenarios
            .match(
                incident.getWorkloadId(),
                incident.getEnvironment(),
                incident.getFailureTime(),
                incident.getAutosysJobName(),
                incident.getJobRunId())
            .orElse(null),
        scenarios.repositoryRoot());
  }

  /** Reads one future. A cancelled future is the five-second budget. */
  private static CollectionOutcome outcomeOf(
      EvidenceConnector connector, Future<CollectionOutcome> future) {
    try {
      return future.get();
    } catch (CancellationException ex) {
      return CollectionOutcome.failed(
          connector.sourceSystem(),
          "TIMEOUT",
          true,
          connector.sourceSystem() + " collection exceeded " + BUDGET_SECONDS + " seconds.");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return CollectionOutcome.failed(
          connector.sourceSystem(), "INTERRUPTED", true, "Evidence collection was interrupted.");
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause() == null ? ex : ex.getCause();
      return CollectionOutcome.failed(
          connector.sourceSystem(),
          "CONNECTOR_ERROR",
          false,
          connector.sourceSystem() + " collection failed: " + cause.getClass().getSimpleName());
    }
  }

  /**
   * Writes the result and any new evidence. An evidence key that is already stored is left as it
   * is.
   *
   * @return true when this outcome stored or already had at least one observation
   */
  private boolean store(
      Incident incident,
      InvestigationAttempt attempt,
      CollectionOutcome outcome,
      Instant started,
      Instant ended) {
    for (CollectedEvidence item : outcome.evidence()) {
      if (evidence.findByIncidentIdAndEvidenceKey(incident.getId(), item.evidenceKey()).isEmpty()) {
        evidence.save(
            new EvidenceRecord(
                UUID.randomUUID(),
                incident.getId(),
                attempt.getId(),
                item.evidenceKey(),
                item.sourceSystem(),
                incident.getWorkloadId(),
                item.evidenceType(),
                item.eventTime(),
                item.eventTimePrecision(),
                ended,
                item.sourceReference(),
                item.attributesJson(),
                item.contentExcerpt(),
                item.contentHash(),
                "SIMULATED",
                "NOT_REQUIRED",
                item.role()));
      }
    }
    results.save(
        new CollectionResult(
            UUID.randomUUID(),
            incident.getId(),
            attempt.getId(),
            outcome.sourceSystem(),
            outcome.status().name(),
            started,
            ended,
            outcome.warning(),
            outcome.errorCategory(),
            outcome.retryAppropriate(),
            outcome.diagnosticMessage()));
    return !outcome.evidence().isEmpty();
  }

  /**
   * What the worker should write on the incident after collection.
   *
   * @param status EVIDENCE_READY when every configured source succeeded and at least one record was
   *     stored, otherwise PARTIAL
   * @param errorSummary null when collection is complete
   * @param auditResult SUCCESS or PARTIAL
   */
  public record CollectionSummary(IncidentStatus status, String errorSummary, String auditResult) {}
}
