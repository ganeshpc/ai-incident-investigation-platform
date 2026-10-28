package com.aiops.investigator.connector;

import java.util.List;

/**
 * Shared behavior for a connector that reads one section of the workload YAML.
 *
 * <p>An abstract class is a partial implementation. Each source subclasses it and supplies the
 * section name and the sentences stored on the collection result. The sentences say the read was a
 * local fixture, not a vendor API call.
 */
public abstract class SyntheticFixtureConnector implements EvidenceConnector {

  private final SyntheticEvidenceReader reader;

  protected SyntheticFixtureConnector(SyntheticEvidenceReader reader) {
    this.reader = reader;
  }

  /** The workload YAML key that means this source is configured, such as {@code github}. */
  protected abstract String workloadSection();

  /** The sentence stored when the workload has no section for this source. */
  protected abstract String unconfiguredMessage();

  /**
   * The sentence stored when fixture records were read.
   *
   * @param count how many records were returned
   * @param scenarioId the matched scenario
   */
  protected abstract String successMessage(int count, String scenarioId);

  /** The sentence stored when the incident matches no scenario. */
  protected String noScenarioMessage() {
    return "No synthetic scenario matches this incident. No records were invented.";
  }

  @Override
  public CollectionOutcome collect(CollectionRequest request) {
    if (!request.hasSection(workloadSection())) {
      return CollectionOutcome.unconfigured(sourceSystem(), unconfiguredMessage());
    }
    if (request.scenario() == null) {
      return CollectionOutcome.partial(
          sourceSystem(), List.of(), noScenarioMessage(), "NO_SCENARIO", noScenarioMessage());
    }
    try {
      List<CollectedEvidence> evidence = reader.read(sourceSystem(), request);
      return afterRead(request, evidence);
    } catch (FixtureReadException ex) {
      if ("UNSAFE_PATH".equals(ex.category())) {
        return CollectionOutcome.failed(sourceSystem(), ex.category(), false, ex.getMessage());
      }
      return CollectionOutcome.unavailable(sourceSystem(), ex.category(), ex.getMessage());
    }
  }

  /**
   * Builds the outcome after the files have been read. Autosys overrides this to notice a missing
   * run id.
   */
  protected CollectionOutcome afterRead(
      CollectionRequest request, List<CollectedEvidence> evidence) {
    String scenarioId = request.scenario().scenarioId();
    return CollectionOutcome.success(
        sourceSystem(), evidence, successMessage(evidence.size(), scenarioId));
  }
}
