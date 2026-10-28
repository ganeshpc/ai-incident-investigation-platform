package com.aiops.investigator.connector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns manifest entries for one source into normalized evidence.
 *
 * <p>The reader applies the incident's lookback. A record with no time is kept. A record labeled
 * {@code context} is kept even when it sits outside the window, because the fixture uses that label
 * for an earlier successful run or an older release. Other timed records have to fall between the
 * lookback start and fifteen minutes after the failure time.
 */
@Component
public class SyntheticEvidenceReader {

  private static final Duration AFTER_FAILURE = Duration.ofMinutes(15);

  /**
   * Reads the matching scenario's entries for one source.
   *
   * @param sourceSystem the connector's source name
   * @param request the incident and the matched scenario
   * @return the records inside the window, in manifest order
   */
  public List<CollectedEvidence> read(String sourceSystem, CollectionRequest request) {
    SyntheticScenario scenario = request.scenario();
    if (scenario == null) {
      return List.of();
    }
    Instant start = request.failureTime().minus(Duration.ofMinutes(request.lookbackMinutes()));
    Instant end = request.failureTime().plus(AFTER_FAILURE);
    List<CollectedEvidence> collected = new ArrayList<>();
    for (ManifestEvidence entry : scenario.evidence()) {
      if (!sourceSystem.equals(entry.sourceSystem())) {
        continue;
      }
      if (!insideWindow(entry, start, end)) {
        continue;
      }
      FixtureDocuments.FixtureFile file =
          FixtureDocuments.read(request.repositoryRoot(), entry.contentFile());
      collected.add(
          new CollectedEvidence(
              entry.evidenceId(),
              entry.sourceSystem(),
              entry.evidenceType(),
              entry.recordId(),
              entry.eventTime(),
              entry.eventTimePrecision(),
              entry.contentFile(),
              attributes(entry, scenario.scenarioId()),
              file.excerpt(),
              file.sha256(),
              "SIMULATED",
              entry.role()));
    }
    return List.copyOf(collected);
  }

  /** Keeps undated records, context records, and timed records inside the window. */
  private static boolean insideWindow(ManifestEvidence entry, Instant start, Instant end) {
    if (entry.eventTime() == null || "context".equals(entry.role())) {
      return true;
    }
    return !entry.eventTime().isBefore(start) && !entry.eventTime().isAfter(end);
  }

  /**
   * A small JSON object of ids. The file body stays in the excerpt, not in this object.
   *
   * <p>The writer escapes quotes and control characters so the text is valid JSON.
   */
  private static String attributes(ManifestEvidence entry, String scenarioId) {
    return "{"
        + field("recordId", entry.recordId())
        + ","
        + field("summary", entry.summary())
        + ","
        + field("contentFile", entry.contentFile())
        + ","
        + field("scenarioId", scenarioId)
        + "}";
  }

  /** One JSON string field, including the quotes around the value. */
  private static String field(String name, String value) {
    return "\"" + escape(name) + "\":\"" + escape(value) + "\"";
  }

  /** Replaces characters that would break a JSON string. */
  private static String escape(String value) {
    StringBuilder out = new StringBuilder();
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '\\' -> out.append("\\\\");
        case '"' -> out.append("\\\"");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> out.append(character);
      }
    }
    return out.toString();
  }
}
