package com.aiops.investigator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiops.investigator.attempt.AttemptRepository;
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
  void workerMarksTheIncidentPartialWithoutTheAiService() throws Exception {
    UUID id = create(INVESTIGATE, null);
    worker.runOnce();
    mockMvc
        .perform(get("/api/incidents/" + id))
        .andExpect(jsonPath("$.status").value("PARTIAL"))
        .andExpect(jsonPath("$.errorSummary").value(AttemptFinisher.NO_CONNECTORS));
    mockMvc
        .perform(get("/api/incidents/" + id + "/attempts"))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$[0].stage").value("EVIDENCE_READY"));
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
    mockMvc.perform(get("/api/incidents/" + id)).andExpect(jsonPath("$.status").value("PARTIAL"));
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
    worker.runOnce();
    mockMvc
        .perform(get("/api/incidents/" + id + "/attempts"))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].status").value("FAILED"))
        .andExpect(jsonPath("$[0].failureReason").value("database unavailable"))
        .andExpect(jsonPath("$[1].status").value("COMPLETED"));
    mockMvc.perform(get("/api/incidents/" + id)).andExpect(jsonPath("$.status").value("PARTIAL"));
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
