package com.aiops.investigator.connector;

import java.time.Instant;
import java.util.List;

/**
 * One seeded incident described by a scenario manifest.
 *
 * <p>The manifest is the index of fixture files for that incident. It is not the answer key. The
 * answer key lives under {@code evaluation/ground-truth} and is never loaded here.
 *
 * @param scenarioId the manifest's scenario id
 * @param workloadId the workload this scenario belongs to
 * @param environment the fixture environment, such as test
 * @param failureTime the failure instant the scenario was built around
 * @param autosysJobName the scheduler job, or null for a workflow incident
 * @param jobRunId the run id, or null when the scenario has no run
 * @param evidence every fixture entry in the manifest
 */
public record SyntheticScenario(
    String scenarioId,
    String workloadId,
    String environment,
    Instant failureTime,
    String autosysJobName,
    String jobRunId,
    List<ManifestEvidence> evidence) {

  public SyntheticScenario {
    evidence = List.copyOf(evidence);
  }
}
