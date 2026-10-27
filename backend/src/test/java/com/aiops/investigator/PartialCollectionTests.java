package com.aiops.investigator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiops.investigator.connector.CollectionOutcome;
import com.aiops.investigator.connector.CollectionRequest;
import com.aiops.investigator.connector.EvidenceConnector;
import com.aiops.investigator.worker.IncidentWorker;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Checks that one unavailable source does not discard evidence from the others.
 *
 * <p>{@code @Import} adds one extra connector to this test's application only. The other test class
 * does not see it. The extra connector always reports an outage.
 */
@Import(PartialCollectionTests.OutageConfiguration.class)
class PartialCollectionTests extends PostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private IncidentWorker worker;

  @Test
  void oneUnavailableSourceKeepsTheSchedulerEvidence() throws Exception {
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
    mockMvc.perform(get("/api/incidents/" + id)).andExpect(jsonPath("$.status").value("PARTIAL"));
    mockMvc
        .perform(get("/api/incidents/" + id + "/evidence?size=100"))
        .andExpect(
            jsonPath("$.items[*].evidenceKey", org.hamcrest.Matchers.hasItem("ev-s1-run-failed")));
  }

  /**
   * A connector that always reports a source outage.
   *
   * <p>A static nested class can be imported as test configuration. Its bean is added to the
   * connector list for this test class.
   */
  @TestConfiguration
  static class OutageConfiguration {

    /** The extra source. Its name is not one of the fixture sources. */
    @Bean
    EvidenceConnector simulatedOutage() {
      return new SimulatedOutageConnector();
    }
  }

  /** Reports one source as unavailable and returns no evidence. */
  static class SimulatedOutageConnector implements EvidenceConnector {

    @Override
    public String sourceSystem() {
      return "SIMULATED_OUTAGE";
    }

    @Override
    public CollectionOutcome collect(CollectionRequest request) {
      return CollectionOutcome.unavailable(
          sourceSystem(), "SOURCE_OUTAGE", "Simulated source outage.");
    }
  }
}
