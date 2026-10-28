package com.aiops.investigator;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiops.investigator.collection.CollectionResult;
import com.aiops.investigator.collection.CollectionResultRepository;
import com.aiops.investigator.worker.IncidentWorker;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Checks a real mode that has no credentials.
 *
 * <p>AutoSys and release are set to real with empty settings. Their fixture records must be absent.
 * OpenShift stays simulated, so its log record is still stored.
 */
class RealModeGapTests extends PostgresIntegrationTest {

  @DynamicPropertySource
  static void realWithoutCredentials(DynamicPropertyRegistry registry) {
    registry.add("investigator.connectors.autosys.mode", () -> "real");
    registry.add("investigator.connectors.release.mode", () -> "real");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private IncidentWorker worker;
  @Autowired private CollectionResultRepository collections;

  @Test
  void missingRealSettingsDoNotUseFixturesForThoseSources() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/incidents/investigate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"workloadId":"payment-reconciliation-demo","environment":"test","failureTime":"2026-03-16T08:30:00Z","lookbackMinutes":120,"autosysJobName":"DEMO_RECON_JOB","jobRunId":"run-recon-20260316-001","incidentType":"BATCH_FAILURE"}
                        """))
            .andExpect(status().isAccepted())
            .andReturn();
    UUID id =
        UUID.fromString(
            com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.incidentId"));
    for (int poll = 0; poll < 40; poll++) {
      worker.runOnce();
      String incidentStatus =
          com.jayway.jsonpath.JsonPath.read(
              mockMvc
                  .perform(get("/api/incidents/" + id))
                  .andReturn()
                  .getResponse()
                  .getContentAsString(),
              "$.status");
      if (!"CREATED".equals(incidentStatus) && !"COLLECTING_EVIDENCE".equals(incidentStatus)) {
        break;
      }
    }
    mockMvc
        .perform(get("/api/incidents/" + id + "/evidence?size=100"))
        .andExpect(jsonPath("$.items[*].evidenceKey", not(hasItem("ev-s1-run-failed"))))
        .andExpect(jsonPath("$.items[*].evidenceKey", not(hasItem("ev-s1-release"))))
        .andExpect(jsonPath("$.items[*].evidenceKey", hasItem("ev-s1-log-validation")));
    assertStatus(id, "AUTOSYS", "UNCONFIGURED", "Fixture data was not used.");
    assertStatus(id, "RELEASE", "UNCONFIGURED", "not identified");
  }

  /** Finds one source result and checks its status and diagnostic. */
  private void assertStatus(UUID incidentId, String source, String expectedStatus, String message)
      throws Exception {
    CollectionResult result =
        collections.findByIncidentId(incidentId).stream()
            .filter(item -> source.equals(item.getSourceSystem()))
            .findFirst()
            .orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(expectedStatus, result.getStatus());
    org.junit.jupiter.api.Assertions.assertTrue(result.getDiagnosticMessage().contains(message));
  }
}
