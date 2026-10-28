package com.aiops.investigator.connector;

/**
 * Reads synthetic case-workflow fixtures.
 *
 * <p>The case-review workload has a {@code workflow} section and no scheduler job. This connector
 * is unconfigured for a batch workload. It does not call a live case system.
 *
 * <p>The mode selector calls this class only in simulated mode. There is no remote workflow API.
 */
public class SyntheticWorkflowConnector extends SyntheticFixtureConnector {

  public SyntheticWorkflowConnector(SyntheticEvidenceReader reader) {
    super(reader);
  }

  @Override
  public String sourceSystem() {
    return "WORKFLOW";
  }

  @Override
  protected String workloadSection() {
    return "workflow";
  }

  @Override
  protected String unconfiguredMessage() {
    return "No workflow fixture is configured for this workload.";
  }

  @Override
  protected String successMessage(int count, String scenarioId) {
    return "Read "
        + count
        + " synthetic workflow fixture records for "
        + scenarioId
        + ". This is not a live case system.";
  }
}
