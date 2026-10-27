package com.aiops.investigator.api;

import com.aiops.investigator.api.IncidentResponses.AttemptResponse;
import com.aiops.investigator.api.IncidentResponses.EvidenceResponse;
import com.aiops.investigator.api.IncidentResponses.IncidentResponse;
import com.aiops.investigator.api.IncidentResponses.InvestigateResponse;
import com.aiops.investigator.api.IncidentResponses.PageResponse;
import com.aiops.investigator.api.IncidentResponses.ReportResponse;
import com.aiops.investigator.incident.IncidentService;
import com.aiops.investigator.incident.InvestigateCommand;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API for creating and reading investigations.
 *
 * <p>{@code @RestController} means each method's return value is the response body. {@code 202
 * Accepted} means the incident is stored and the investigation continues after this response. The
 * connection is not held open while a worker runs.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

  private final IncidentService incidents;

  public IncidentController(IncidentService incidents) {
    this.incidents = incidents;
  }

  /**
   * Accepts one investigation.
   *
   * @param idempotencyKey optional header. The same key and body return the original incident.
   * @param actor optional X-Actor header. Blank becomes local-dev.
   */
  @PostMapping("/investigate")
  public ResponseEntity<InvestigateResponse> investigate(
      @Valid @RequestBody InvestigateRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    InvestigateCommand command =
        InvestigateCommand.from(
            request.title(),
            request.workloadId().trim(),
            request.environment().trim(),
            request.failureTime(),
            request.lookbackMinutes(),
            request.autosysJobName(),
            request.jobRunId(),
            request.incidentType());
    return ResponseEntity.accepted()
        .body(InvestigateResponse.from(incidents.investigate(command, idempotencyKey, actor)));
  }

  /** Returns one page of incidents, newest first. */
  @GetMapping
  public PageResponse<IncidentResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return PageResponse.of(incidents.list(page, size).map(IncidentResponse::from));
  }

  /** Returns one incident. */
  @GetMapping("/{incidentId}")
  public IncidentResponse get(@PathVariable UUID incidentId) {
    return IncidentResponse.from(incidents.get(incidentId));
  }

  /** Returns evidence already stored for the incident. */
  @GetMapping("/{incidentId}/evidence")
  public PageResponse<EvidenceResponse> evidence(
      @PathVariable UUID incidentId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.of(incidents.evidence(incidentId, page, size).map(EvidenceResponse::from));
  }

  /** Returns the newest report, or 404 when none has been written. */
  @GetMapping("/{incidentId}/report")
  public ReportResponse report(@PathVariable UUID incidentId) {
    return ReportResponse.from(incidents.report(incidentId));
  }

  /** Returns every attempt from oldest to newest. */
  @GetMapping("/{incidentId}/attempts")
  public List<AttemptResponse> attempts(@PathVariable UUID incidentId) {
    return incidents.attempts(incidentId).stream().map(AttemptResponse::from).toList();
  }

  /**
   * Opens another attempt without deleting the earlier ones.
   *
   * @param actor optional X-Actor header
   */
  @PostMapping("/{incidentId}/retry")
  public ResponseEntity<IncidentResponse> retry(
      @PathVariable UUID incidentId,
      @RequestBody(required = false) RetryRequest request,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    RetryRequest body = request == null ? RetryRequest.defaults() : request;
    return ResponseEntity.accepted()
        .body(
            IncidentResponse.from(
                incidents.retry(
                    incidentId,
                    body.rerunCompletedInvestigation(),
                    body.rerunSuccessfulCollections(),
                    actor)));
  }
}
