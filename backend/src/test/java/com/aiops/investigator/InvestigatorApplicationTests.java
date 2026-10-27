package com.aiops.investigator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks that the backend process can start and answer its bootstrap routes.
 *
 * <p>JUnit is the test runner. A method marked {@link Test} is one test. The superclass starts
 * PostgreSQL and the Spring application context. {@link MockMvc} calls the HTTP handlers in memory
 * instead of opening a network port.
 */
class InvestigatorApplicationTests extends PostgresIntegrationTest {

  /** In-memory HTTP client. Spring fills this field before each test. */
  @Autowired private MockMvc mockMvc;

  /**
   * The context starts when this class is constructed, so an empty test still fails on a broken
   * bean.
   */
  @Test
  void contextLoads() {}

  /** The status route names this deployment as synthetic. */
  @Test
  void statusSaysTheEnvironmentIsSynthetic() throws Exception {
    mockMvc
        .perform(get("/api/platform/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("investigator-backend"))
        .andExpect(jsonPath("$.environmentLabel").value("synthetic-test"))
        .andExpect(jsonPath("$.phase").value("bootstrap"));
  }

  /** Actuator health is the process check a local orchestrator can call. */
  @Test
  void actuatorHealthIsUp() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}
