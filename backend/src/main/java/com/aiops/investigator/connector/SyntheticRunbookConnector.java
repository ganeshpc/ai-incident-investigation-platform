package com.aiops.investigator.connector;

import org.springframework.stereotype.Component;

/**
 * Reads runbook Markdown that the workload names.
 *
 * <p>Lookup is the file path in the manifest. There is no vector search and no extra corpus. A
 * missing file becomes an unavailable collection result rather than an empty success.
 */
@Component
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
