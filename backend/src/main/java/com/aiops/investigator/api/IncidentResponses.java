package com.aiops.investigator.api;

import com.aiops.investigator.attempt.InvestigationAttempt;
import com.aiops.investigator.evidence.EvidenceRecord;
import com.aiops.investigator.incident.Incident;
import com.aiops.investigator.incident.IncidentService.AcceptedInvestigation;
import com.aiops.investigator.report.InvestigationReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/**
 * JSON shapes returned by the incident API.
 *
 * <p>These records are separate from the database entities. The response names are the JSON keys. A
 * page record is the list plus the page numbers, instead of Spring's own page object.
 */
public final class IncidentResponses {

  private IncidentResponses() {}

  /** The body of a 202 response. */
  public record InvestigateResponse(
      UUID incidentId, String status, int attemptNumber, Instant createdAt, boolean replayed) {

    static InvestigateResponse from(AcceptedInvestigation accepted) {
      Incident incident = accepted.incident();
      return new InvestigateResponse(
          incident.getId(),
          incident.getStatus().name(),
          accepted.attemptNumber(),
          incident.getCreatedAt(),
          accepted.replayed());
    }
  }

  /** One incident as returned by the read endpoints. */
  public record IncidentResponse(
      UUID id,
      String title,
      String workloadId,
      String environment,
      String incidentType,
      String status,
      Instant failureTime,
      int lookbackMinutes,
      String autosysJobName,
      String jobRunId,
      String errorSummary,
      int investigationAttemptCount,
      String createdBy,
      Instant createdAt,
      Instant updatedAt) {

    static IncidentResponse from(Incident incident) {
      return new IncidentResponse(
          incident.getId(),
          incident.getTitle(),
          incident.getWorkloadId(),
          incident.getEnvironment(),
          incident.getIncidentType().name(),
          incident.getStatus().name(),
          incident.getFailureTime(),
          incident.getLookbackMinutes(),
          incident.getAutosysJobName(),
          incident.getJobRunId(),
          incident.getErrorSummary(),
          incident.getInvestigationAttemptCount(),
          incident.getCreatedBy(),
          incident.getCreatedAt(),
          incident.getUpdatedAt());
    }
  }

  /** One attempt as returned by the attempt list. */
  public record AttemptResponse(
      UUID id,
      int attemptNumber,
      String stage,
      String status,
      String failureReason,
      UUID retryOfAttemptId,
      boolean rerunSuccessfulCollections,
      String modelName,
      String promptVersion,
      Instant startedAt,
      Instant completedAt,
      Instant createdAt) {

    static AttemptResponse from(InvestigationAttempt attempt) {
      return new AttemptResponse(
          attempt.getId(),
          attempt.getAttemptNumber(),
          attempt.getStage().name(),
          attempt.getStatus().name(),
          attempt.getFailureReason(),
          attempt.getRetryOfAttemptId(),
          attempt.isRerunSuccessfulCollections(),
          attempt.getModelName(),
          attempt.getPromptVersion(),
          attempt.getStartedAt(),
          attempt.getCompletedAt(),
          attempt.getCreatedAt());
    }
  }

  /** One evidence row. The list is empty until a connector writes evidence. */
  public record EvidenceResponse(
      UUID id,
      String evidenceKey,
      String sourceSystem,
      String workloadId,
      String evidenceType,
      Instant eventTime,
      String eventTimePrecision,
      Instant retrievalTime,
      String sourceReference,
      String contentExcerpt,
      String provenance,
      String redactionStatus,
      String role) {

    static EvidenceResponse from(EvidenceRecord evidence) {
      return new EvidenceResponse(
          evidence.getId(),
          evidence.getEvidenceKey(),
          evidence.getSourceSystem(),
          evidence.getWorkloadId(),
          evidence.getEvidenceType(),
          evidence.getEventTime(),
          evidence.getEventTimePrecision(),
          evidence.getRetrievalTime(),
          evidence.getSourceReference(),
          evidence.getContentExcerpt(),
          evidence.getProvenance(),
          evidence.getRedactionStatus(),
          evidence.getRole());
    }
  }

  /** A stored report. This phase does not create one. */
  public record ReportResponse(
      UUID id,
      UUID attemptId,
      String status,
      String observedFailure,
      String summary,
      String missingEvidence,
      String recommendedNextChecks,
      String limitations,
      Instant createdAt) {

    static ReportResponse from(InvestigationReport report) {
      return new ReportResponse(
          report.getId(),
          report.getAttemptId(),
          report.getStatus(),
          report.getObservedFailure(),
          report.getSummary(),
          report.getMissingEvidence(),
          report.getRecommendedNextChecks(),
          report.getLimitations(),
          report.getCreatedAt());
    }
  }

  /** A page of items plus the numbers a client needs to ask for the next page. */
  public record PageResponse<T>(
      List<T> items, int page, int size, long totalElements, int totalPages) {

    static <T> PageResponse<T> of(Page<T> page) {
      return new PageResponse<>(
          page.getContent(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements(),
          page.getTotalPages());
    }
  }
}
