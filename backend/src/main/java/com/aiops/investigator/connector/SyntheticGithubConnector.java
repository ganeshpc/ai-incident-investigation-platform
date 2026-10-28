package com.aiops.investigator.connector;

/**
 * Reads the local Git fixture named by the workload.
 *
 * <p>A workload with no {@code github} section is unconfigured. That is not a failed GitHub call,
 * and this class does not substitute a different repository. Commit ids and diffs come from the
 * fixture files.
 *
 * <p>The mode selector calls this class only when GitHub mode is simulated.
 */
public class SyntheticGithubConnector extends SyntheticFixtureConnector {

  public SyntheticGithubConnector(SyntheticEvidenceReader reader) {
    super(reader);
  }

  @Override
  public String sourceSystem() {
    return "GITHUB";
  }

  @Override
  protected String workloadSection() {
    return "github";
  }

  @Override
  protected String unconfiguredMessage() {
    return "GitHub is not configured for this workload. No GitHub API was called.";
  }

  @Override
  protected String successMessage(int count, String scenarioId) {
    return "Read "
        + count
        + " local Git fixture records for "
        + scenarioId
        + ". This is not a GitHub API call.";
  }
}
