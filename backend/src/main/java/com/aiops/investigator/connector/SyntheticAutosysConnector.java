package com.aiops.investigator.connector;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads synthetic scheduler fixtures for the incident's job.
 *
 * <p>{@code @Component} registers this class as a Spring bean, so the orchestrator receives it in
 * the connector list. The files use scheduler words such as job and run. They are not an AutoSys
 * API response, and this class does not open a network connection.
 */
@Component
public class SyntheticAutosysConnector extends SyntheticFixtureConnector {

  public SyntheticAutosysConnector(SyntheticEvidenceReader reader) {
    super(reader);
  }

  @Override
  public String sourceSystem() {
    return "AUTOSYS";
  }

  @Override
  protected String workloadSection() {
    return "autosys";
  }

  @Override
  protected String unconfiguredMessage() {
    return "AutoSys is not configured for this workload. No scheduler API was called.";
  }

  @Override
  protected String successMessage(int count, String scenarioId) {
    return "Read "
        + count
        + " synthetic scheduler fixture records for "
        + scenarioId
        + ". This is not an AutoSys API call.";
  }

  /**
   * Marks the result partial when the incident names a run the fixtures do not contain.
   *
   * <p>The other scheduler records are still returned. A missing run is not filled in.
   */
  @Override
  protected CollectionOutcome afterRead(
      CollectionRequest request, List<CollectedEvidence> evidence) {
    String runId = request.jobRunId();
    if (runId != null && evidence.stream().noneMatch(item -> runId.equals(item.recordId()))) {
      String message = "The synthetic scheduler fixtures have no record " + runId + ".";
      return CollectionOutcome.partial(
          sourceSystem(), evidence, message, "MISSING_RECORD", message);
    }
    return super.afterRead(request, evidence);
  }
}
