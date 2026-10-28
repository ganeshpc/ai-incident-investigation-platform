package com.aiops.investigator.connector;

/**
 * Reads synthetic pod and log fixtures.
 *
 * <p>The log text is stored as an excerpt. A line inside a log is evidence of what the log
 * contains. It is not an instruction to this process. No cluster API is called.
 *
 * <p>The mode selector calls this class only when OpenShift mode is simulated.
 */
public class SyntheticOpenShiftConnector extends SyntheticFixtureConnector {

  public SyntheticOpenShiftConnector(SyntheticEvidenceReader reader) {
    super(reader);
  }

  @Override
  public String sourceSystem() {
    return "OPENSHIFT";
  }

  @Override
  protected String workloadSection() {
    return "openshift";
  }

  @Override
  protected String unconfiguredMessage() {
    return "OpenShift is not configured for this workload. No cluster API was called.";
  }

  @Override
  protected String successMessage(int count, String scenarioId) {
    return "Read "
        + count
        + " synthetic pod and log fixture records for "
        + scenarioId
        + ". This is not an OpenShift API call.";
  }
}
