package com.aiops.investigator.connector;

/**
 * Reads runbook Markdown that the workload names.
 *
 * <p>Lookup is the file path in the manifest. There is no vector search and no extra corpus. A
 * missing file becomes an unavailable collection result rather than an empty success.
 *
 * <p>Runbooks stay on local files. There is no remote runbook API in this phase.
 */
public class SyntheticRunbookConnector extends SyntheticFixtureConnector {

  public SyntheticRunbookConnector(SyntheticEvidenceReader reader) {
    super(reader);
  }

  @Override
  public String sourceSystem() {
    return "RUNBOOK";
  }

  @Override
  protected String workloadSection() {
    return "runbooks";
  }

  @Override
  protected String unconfiguredMessage() {
    return "No runbook path is configured for this workload.";
  }

  @Override
  protected String successMessage(int count, String scenarioId) {
    return "Read " + count + " runbook fixture records for " + scenarioId + ".";
  }
}
