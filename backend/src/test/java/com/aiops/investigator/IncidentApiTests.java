package com.aiops.investigator;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiops.investigator.attempt.AttemptRepository;
import com.aiops.investigator.collection.CollectionResult;
import com.aiops.investigator.collection.CollectionResultRepository;
import com.aiops.investigator.incident.IncidentRepository;
import com.aiops.investigator.incident.IncidentStatus;
import com.aiops.investigator.worker.AttemptFinisher;
import com.aiops.investigator.worker.IncidentWorker;
import com.aiops.investigator.workload.WorkloadCatalogLoader;
import com.aiops.investigator.workload.WorkloadRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Exercises the incident API against PostgreSQL.
 *
 * <p>MockMvc sends HTTP inside the test process. The worker is not on a timer here. Tests call
 * {@link IncidentWorker#runOnce()} when they want a stored attempt to advance.
 */
class IncidentApiTests extends PostgresIntegrationTest {

  private static final String INVESTIGATE =
      """
      {"workloadId":"payment-reconciliation-demo","environment":"test","failureTime":"2026-03-16T08:30:00Z","lookbackMinutes":120,"autosysJobName":"DEMO_RECON_JOB","jobRunId":"run-recon-20260316-001","incidentType":"BATCH_FAILURE"}
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private IncidentWorker worker;
  @Autowired private AttemptFinisher finisher;
  @Autowired private IncidentRepository incidents;
  @Autowired private AttemptRepository attempts;
  @Autowired private WorkloadRepository workloads;
  @Autowired private WorkloadCatalogLoader loader;
  @Autowired private CollectionResultRepository collections;

  @Test
  void workloadCatalogLoadsOnce() throws Exception {
    assertEquals(4, workloads.count());
    loader.run(null);
    assertEquals(4, workloads.count());
  }

  @Test
  void createAndReadIncident() throws Exception {
    UUID id = create(INVESTIGATE, null);
    mockMvc
        .perform(get("/api/incidents/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CREATED"))
        .andExpect(jsonPath("$.createdBy").value("local-dev"))
        .andExpect(jsonPath("$.workloadId").value("payment-reconciliation-demo"));
    mockMvc
        .perform(get("/api/incidents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").exists());
    mockMvc
        .perform(get("/api/incidents/" + id + "/evidence"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mockMvc
        .perform(get("/api/incidents/" + id + "/report"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("No report is available for this incident."));
  }

  @Test
  void actorHeaderIsStored() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/incidents/investigate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Actor", "demo-engineer")
                    .content(INVESTIGATE))
            .andExpect(status().isAccepted())
            .andReturn();
    String id =
        com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.incidentId");
    mockMvc
        .perform(get("/api/incidents/" + id))
        .andExpect(jsonPath("$.createdBy").value("demo-engineer"));
  }

  @Test
  void unknownIncidentIs404() throws Exception {
    mockMvc
        .perform(get("/api/incidents/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Incident not found"));
  }

  @Test
  void missingWorkloadIdIsAValidationProblem() throws Exception {
    mockMvc
        .perform(
            post("/api/incidents/investigate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"environment\":\"test\",\"failureTime\":\"2026-03-16T08:30:00Z\",\"lookbackMinutes\":30}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("workloadId")));
  }

  @Test
  void unknownWorkloadIsRejectedBeforeInsert() throws Exception {
    long before = incidents.count();
    mockMvc
        .perform(
            post("/api/incidents/investigate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"workloadId\":\"missing-workload\",\"environment\":\"test\",\"failureTime\":\"2026-03-16T08:30:00Z\",\"lookbackMinutes\":30}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.title").value("Unknown workload"));
    assertEquals(before, incidents.count());
  }

  @Test
  void sameIdempotencyKeyAndBodyReturnsTheOriginalIncident() throws Exception {
    UUID first = create(INVESTIGATE, "phase2-same-body");
    UUID second = create(INVESTIGATE, "phase2-same-body");
    assertEquals(first, second);
    mockMvc
        .perform(
            post("/api/incidents/investigate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "phase2-same-body")
                .content(INVESTIGATE))
        .andExpect(jsonPath("$.replayed").value(true));
  }

  @Test
  void sameIdempotencyKeyAndDifferentBodyConflicts() throws Exception {
    create(INVESTIGATE, "phase2-different-body");
    mockMvc
        .perform(
            post("/api/incidents/investigate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "phase2-different-body")
                .content(INVESTIGATE.replace("\"lookbackMinutes\":120", "\"lookbackMinutes\":30")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Idempotency conflict"));
  }

  @Test
  void workerCollectsScenarioOneWithoutCallingTheAiService() throws Exception {
    UUID id = create(INVESTIGATE, null);
    collect(id);
    mockMvc
        .perform(get("/api/incidents/" + id))
        .andExpect(jsonPath("$.status").value("EVIDENCE_READY"))
        .andExpect(jsonPath("$.errorSummary").value(nullValue()));
    mockMvc
        .perform(get("/api/incidents/" + id + "/attempts"))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$[0].stage").value("EVIDENCE_READY"));
    mockMvc
        .perform(get("/api/incidents/" + id + "/evidence?size=100"))
        .andExpect(jsonPath("$.totalElements").value(13))
        .andExpect(jsonPath("$.items[*].evidenceKey", hasItem("ev-s1-run-failed")))
        .andExpect(jsonPath("$.items[*].evidenceKey", hasItem("ev-s1-log-validation")))
        .andExpect(jsonPath("$.items[*].provenance", hasItem("SIMULATED")));
    mockMvc.perform(get("/api/incidents/" + id + "/report")).andExpect(status().isNotFound());
    assertEquals(
        "UNCONFIGURED",
        collections.findByIncidentId(id).stream()
            .filter(result -> "WORKFLOW".equals(result.getSourceSystem()))
            .map(CollectionResult::getStatus)
            .findFirst()
            .orElseThrow());
    assertTrue(
        collections.findByIncidentId(id).stream()
            .filter(result -> "GITHUB".equals(result.getSourceSystem()))
            .anyMatch(result -> result.getDiagnosticMessage().contains("not a GitHub API call")));
  }

  @Test
  void shortLookbackDropsTheEarlierNegativeLog() throws Exception {
    UUID id = create(INVESTIGATE.replace("\"lookbackMinutes\":120", "\"lookbackMinutes\":1"), null);
    collect(id);
    mockMvc
        .perform(get("/api/incidents/" + id + "/evidence?size=100"))
        .andExpect(jsonPath("$.totalElements").value(12))
        .andExpect(jsonPath("$.items[*].evidenceKey", hasItem("ev-s1-run-failed")))
        .andExpect(jsonPath("$.items[*].evidenceKey", hasItem("ev-s1-run-success")))
        .andExpect(jsonPath("$.items[*].evidenceKey", not(hasItem("ev-s1-log-warning"))));
  }

  @Test
  void settlementCollectsWhileGithubStaysUnconfigured() throws Exception {
    UUID id =
        create(
            """
            {"workloadId":"settlement-pipeline-demo","environment":"test","failureTime":"2026-03-17T02:15:00Z","lookbackMinutes":60,"autosysJobName":"DEMO_SETTLE_JOB","jobRunId":"run-settle-20260317-001","incidentType":"BATCH_FAILURE"}
            """,
            null);
    collect(id);
    mockMvc
        .perform(get("/api/incidents/" + id))
        .andExpect(jsonPath("$.status").value("EVIDENCE_READY"));
    mockMvc
        .perform(get("/api/incidents/" + id + "/evidence?size=100"))
        .andExpect(jsonPath("$.totalElements").value(11))
        .andExpect(jsonPath("$.items[*].evidenceKey", hasItem("ev-s2-dep")));
    assertEquals(
        "UNCONFIGURED",
        collections.findByIncidentId(id).stream()
            .filter(result -> "GITHUB".equals(result.getSourceSystem()))
            .map(CollectionResult::getStatus)
            .findFirst()
            .orElseThrow());
  }

  @Test
  void caseReviewCollectsWorkflowWithoutAutosys() throws Exception {
    UUID id =
        create(
            """
            {"workloadId":"case-review-demo","environment":"test","failureTime":"2026-03-18T14:05:00Z","lookbackMinutes":60,"incidentType":"WORKFLOW_FAILURE"}
            """,
            null);
    collect(id);
    mockMvc
        .perform(get("/api/incidents/" + id))
        .andExpect(jsonPath("$.status").value("EVIDENCE_READY"));
    mockMvc
        .perform(get("/api/incidents/" + id + "/evidence?size=100"))
        .andExpect(jsonPath("$.totalElements").value(10))
        .andExpect(jsonPath("$.items[*].evidenceKey", hasItem("ev-s3-workflow")));
    assertEquals(
        "UNCONFIGURED",
        collections.findByIncidentId(id).stream()
            .filter(result -> "AUTOSYS".equals(result.getSourceSystem()))
            .map(CollectionResult::getStatus)
            .findFirst()
            .orElseThrow());
  }

  @Test
  void unknownFailureTimeDoesNotInventEvidence() throws Exception {
    UUID id = create(INVESTIGATE.replace("2026-03-16T08:30:00Z", "2020-01-01T00:00:00Z"), null);
    collect(id);
    mockMvc
        .perform(get("/api/incidents/" + id))
        .andExpect(jsonPath("$.status").value("PARTIAL"))
        .andExpect(jsonPath("$.errorSummary").value(containsString("No synthetic scenario")));
    mockMvc
        .perform(get("/api/incidents/" + id + "/evidence"))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void collectingAgainDoesNotDuplicateEvidence() throws Exception {
    UUID id = create(INVESTIGATE, null);
    collect(id);
    mockMvc
        .perform(
            post("/api/incidents/" + id + "/retry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rerunSuccessfulCollections\":true}"))
        .andExpect(status().isAccepted());
    collect(id);
    mockMvc
        .perform(get("/api/incidents/" + id + "/evidence?size=100"))
        .andExpect(jsonPath("$.totalElements").value(13));
    assertEquals(2, attempts.findByIncidentIdOrderByAttemptNumberAsc(id).size());
  }

  @Test
  void expiredLeaseCanBeClaimedAgain() throws Exception {
    UUID id = create(INVESTIGATE, null);
    var attempt = attempts.findByIncidentIdOrderByAttemptNumberAsc(id).getFirst();
    attempt.expireLease(Instant.now().minusSeconds(60));
    attempts.save(attempt);
    // The worker claims the oldest waiting attempts, and earlier tests left some behind.
    // Poll until this incident is included. One call is not enough once the table is shared.
    for (int poll = 0; poll < 30; poll++) {
      worker.runOnce();
    }
    mockMvc
        .perform(get("/api/incidents/" + id))
        .andExpect(jsonPath("$.status").value("EVIDENCE_READY"));
    assertEquals(1, attempts.findByIncidentIdOrderByAttemptNumberAsc(id).size());
  }

  @Test
  void failedAttemptStaysVisibleAfterRetry() throws Exception {
    UUID id = create(INVESTIGATE, null);
    var attempt = attempts.findByIncidentIdOrderByAttemptNumberAsc(id).getFirst();
    attempt.expireLease(Instant.now().minusSeconds(60));
    attempts.save(attempt);
    var incident = incidents.findById(id).orElseThrow();
    incident.applyStatus(IncidentStatus.COLLECTING_EVIDENCE, Instant.now());
    incidents.save(incident);
    finisher.fail(attempt.getId(), "database unavailable");
    mockMvc.perform(get("/api/incidents/" + id)).andExpect(jsonPath("$.status").value("FAILED"));
    mockMvc
        .perform(
            post("/api/incidents/" + id + "/retry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isAccepted());
    collect(id);
    mockMvc
        .perform(get("/api/incidents/" + id + "/attempts"))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].status").value("FAILED"))
        .andExpect(jsonPath("$[0].failureReason").value("database unavailable"))
        .andExpect(jsonPath("$[1].status").value("COMPLETED"));
    mockMvc
        .perform(get("/api/incidents/" + id))
        .andExpect(jsonPath("$.status").value("EVIDENCE_READY"));
  }

  @Test
  void finishedInvestigationIsNotRetriedUnlessRequested() throws Exception {
    UUID id = create(INVESTIGATE, null);
    var incident = incidents.findById(id).orElseThrow();
    incident.applyStatus(IncidentStatus.REPORT_READY, Instant.parse("2026-03-16T09:00:00Z"));
    incidents.save(incident);
    var attempt = attempts.findByIncidentIdOrderByAttemptNumberAsc(id).getFirst();
    attempt.complete(
        com.aiops.investigator.attempt.AttemptStage.REPORT_READY,
        Instant.parse("2026-03-16T09:00:00Z"));
    attempts.save(attempt);

    mockMvc
        .perform(
            post("/api/incidents/" + id + "/retry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Investigation already finished"));
    mockMvc
        .perform(
            post("/api/incidents/" + id + "/retry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rerunCompletedInvestigation\":true}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("INVESTIGATING"));
    assertEquals(2, attempts.findByIncidentIdOrderByAttemptNumberAsc(id).size());
  }

  @Test
  void pageSizeIsRejectedWhenItIsOutOfRange() throws Exception {
    mockMvc.perform(get("/api/incidents?page=0&size=0")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/incidents?page=-1&size=20")).andExpect(status().isBadRequest());
  }

  /**
   * Runs the worker until this incident leaves the queue.
   *
   * <p>Earlier tests leave pending attempts in the shared database. One call can claim those
   * instead of this incident.
   */
  private void collect(UUID id) throws Exception {
    for (int poll = 0; poll < 40; poll++) {
      worker.runOnce();
      String status =
          com.jayway.jsonpath.JsonPath.read(
              mockMvc
                  .perform(get("/api/incidents/" + id))
                  .andReturn()
                  .getResponse()
                  .getContentAsString(),
              "$.status");
      if (!"CREATED".equals(status) && !"COLLECTING_EVIDENCE".equals(status)) {
        return;
      }
    }
  }

  private UUID create(String json, String idempotencyKey) throws Exception {
    var request =
        post("/api/incidents/investigate").contentType(MediaType.APPLICATION_JSON).content(json);
    if (idempotencyKey != null) {
      request.header("Idempotency-Key", idempotencyKey);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isAccepted()).andReturn();
    String id =
        com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.incidentId");
    return UUID.fromString(id);
  }
}
