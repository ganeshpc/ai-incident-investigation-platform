package com.aiops.investigator.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiops.investigator.config.InvestigatorProperties;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Checks that real mode does not read fixtures.
 *
 * <p>The connectors here are fakes. One counts calls. No HTTP server and no database are started.
 */
class ModeSelectingConnectorTest {

  @Test
  void simulatedModeReadsTheFixtureConnector() {
    AtomicInteger simulated = new AtomicInteger();
    AtomicInteger real = new AtomicInteger();
    ModeSelectingConnector connector =
        selector(ConnectorMode.SIMULATED, false, simulated, real, true);
    connector.collect(request());
    assertEquals(1, simulated.get());
    assertEquals(0, real.get());
  }

  @Test
  void realModeWithoutSettingsDoesNotReadFixtures() {
    AtomicInteger simulated = new AtomicInteger();
    AtomicInteger real = new AtomicInteger();
    ModeSelectingConnector connector = selector(ConnectorMode.REAL, false, simulated, real, true);
    CollectionOutcome outcome = connector.collect(request());
    assertEquals(0, simulated.get());
    assertEquals(0, real.get());
    assertEquals(CollectionStatus.UNCONFIGURED, outcome.status());
    assertTrue(outcome.diagnosticMessage().contains("Fixture data was not used."));
  }

  @Test
  void realModeDoesNotFallBackWhenTheApiFails() {
    AtomicInteger simulated = new AtomicInteger();
    AtomicInteger real = new AtomicInteger();
    ModeSelectingConnector connector = selector(ConnectorMode.REAL, true, simulated, real, true);
    CollectionOutcome outcome = connector.collect(request());
    assertEquals(0, simulated.get());
    assertEquals(1, real.get());
    assertEquals(CollectionStatus.FAILED, outcome.status());
    assertTrue(outcome.evidence().isEmpty());
  }

  @Test
  void releaseRealModeDoesNotCallAnApi() {
    InvestigatorProperties.Source settings = new InvestigatorProperties.Source();
    settings.setMode(ConnectorMode.REAL);
    settings.setBaseUrl("https://releases.example");
    AtomicInteger simulated = new AtomicInteger();
    ModeSelectingConnector connector =
        new ModeSelectingConnector(
            "RELEASE",
            "release",
            counter(
                simulated, CollectionOutcome.success("RELEASE", java.util.List.of(), "fixture")),
            request -> {
              throw new AssertionError("release API must not be called");
            },
            settings,
            false);
    CollectionOutcome outcome = connector.collect(request());
    assertEquals(0, simulated.get());
    assertEquals(CollectionStatus.UNCONFIGURED, outcome.status());
    assertTrue(outcome.diagnosticMessage().contains("not identified"));
  }

  /** A selector whose real call fails and whose simulated call only counts. */
  private static ModeSelectingConnector selector(
      ConnectorMode mode,
      boolean ready,
      AtomicInteger simulatedCalls,
      AtomicInteger realCalls,
      boolean productIdentified) {
    InvestigatorProperties.Source settings = new InvestigatorProperties.Source();
    settings.setMode(mode);
    if (ready) {
      settings.setBaseUrl("https://autosys.example");
      settings.setUsername("reader");
      settings.setPassword("secret");
      settings.setToken("token");
    }
    return new ModeSelectingConnector(
        "AUTOSYS",
        "autosys",
        counter(
            simulatedCalls, CollectionOutcome.success("AUTOSYS", java.util.List.of(), "fixture")),
        request -> {
          realCalls.incrementAndGet();
          return CollectionOutcome.failed(
              "AUTOSYS", "SERVER", true, "Remote read returned HTTP 500");
        },
        settings,
        productIdentified);
  }

  /** An evidence connector that counts calls and returns a fixed outcome. */
  private static EvidenceConnector counter(AtomicInteger calls, CollectionOutcome outcome) {
    return new EvidenceConnector() {
      @Override
      public String sourceSystem() {
        return "AUTOSYS";
      }

      @Override
      public CollectionOutcome collect(CollectionRequest request) {
        calls.incrementAndGet();
        return outcome;
      }
    };
  }

  /** A request whose workload names AutoSys and release, so the selector does not stop early. */
  private static CollectionRequest request() {
    return new CollectionRequest(
        "payment-reconciliation-demo",
        "test",
        Instant.parse("2026-03-16T08:30:00Z"),
        120,
        "DEMO_RECON_JOB",
        "run-recon-20260316-001",
        Map.of(
            "autosys", Map.of("jobName", "DEMO_RECON_JOB"),
            "release", Map.of("applicationId", "reconciliation-demo")),
        null,
        Path.of("."));
  }
}
