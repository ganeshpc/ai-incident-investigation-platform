package com.aiops.investigator.connector;

/**
 * Reads synthetic release records.
 *
 * <p>The stored text is the fixture file. A deployed revision is present only when that file
 * contains one. This class does not add a revision the file does not state, and it does not call a
 * release-management API.
 *
 * <p>The mode selector calls this class only when release mode is simulated.
 */
public class SyntheticReleaseConnector extends SyntheticFixtureConnector {

  public SyntheticReleaseConnector(SyntheticEvidenceReader reader) {
    super(reader);
  }

  @Override
  public String sourceSystem() {
    return "RELEASE";
  }

  @Override
  protected String workloadSection() {
    return "release";
  }

  @Override
  protected String unconfiguredMessage() {
    return "Release management is not configured for this workload. No release API was called.";
  }

  @Override
  protected String successMessage(int count, String scenarioId) {
    return "Read "
        + count
        + " synthetic release fixture records for "
        + scenarioId
        + ". A deployed revision is stored only when the fixture file contains one.";
  }
}
