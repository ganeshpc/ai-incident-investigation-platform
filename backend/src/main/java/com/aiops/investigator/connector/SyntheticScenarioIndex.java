package com.aiops.investigator.connector;

import com.aiops.investigator.config.InvestigatorProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads scenario manifests once and finds the one that matches an incident.
 *
 * <p>A component is a Spring bean. The index is built the first time a collection needs it, then
 * reused. The files under {@code fixtures/scenarios} are the only input. Ground truth is not a file
 * this class opens.
 */
@Component
public class SyntheticScenarioIndex {

  private final Path scenariosDirectory;
  private List<SyntheticScenario> scenarios;

  public SyntheticScenarioIndex(InvestigatorProperties properties) {
    Path fixturesRoot = Path.of(properties.getFixturesRoot()).toAbsolutePath().normalize();
    this.scenariosDirectory = fixturesRoot.resolve("scenarios");
  }

  /**
   * Returns the scenario for this failure, or empty when none matches.
   *
   * <p>The workload, environment, and failure time have to be the same. A job name or run id
   * narrows the match only when both the incident and the manifest have that field. Two matches are
   * a broken fixture set, not a guess.
   */
  public Optional<SyntheticScenario> match(
      String workloadId,
      String environment,
      Instant failureTime,
      String autosysJobName,
      String jobRunId) {
    List<SyntheticScenario> found = new ArrayList<>();
    for (SyntheticScenario scenario : scenarios()) {
      if (!scenario.workloadId().equals(workloadId)) {
        continue;
      }
      if (!scenario.environment().equals(environment)) {
        continue;
      }
      if (!scenario.failureTime().equals(failureTime)) {
        continue;
      }
      if (!sameWhenBothPresent(scenario.autosysJobName(), autosysJobName)) {
        continue;
      }
      if (!sameWhenBothPresent(scenario.jobRunId(), jobRunId)) {
        continue;
      }
      found.add(scenario);
    }
    if (found.size() > 1) {
      throw new IllegalStateException(
          "More than one synthetic scenario matches " + workloadId + " at " + failureTime);
    }
    return found.stream().findFirst();
  }

  /**
   * Reads the workload registry file for one id.
   *
   * <p>Connectors use this mapping to decide whether their source is configured. The file is the
   * same document the startup loader stored.
   */
  public Map<String, Object> workload(String workloadId) {
    Path fixturesRoot = scenariosDirectory.getParent();
    if (fixturesRoot == null) {
      throw new IllegalStateException("Scenario directory has no parent: " + scenariosDirectory);
    }
    return mapping(fixturesRoot.resolve("workloads").resolve(workloadId + ".yaml"));
  }

  /** The repository root that contains the {@code fixtures} and {@code evaluation} directories. */
  public Path repositoryRoot() {
    Path fixturesRoot = scenariosDirectory.getParent();
    if (fixturesRoot == null || fixturesRoot.getParent() == null) {
      throw new IllegalStateException(
          "Fixture root has no repository parent: " + scenariosDirectory);
    }
    return fixturesRoot.getParent();
  }

  /**
   * Reads the manifests the first time and returns the same list after that.
   *
   * <p>{@code synchronized} means two collection threads cannot both build the list. The list is
   * replaced once. It is not appended to.
   */
  private synchronized List<SyntheticScenario> scenarios() {
    if (scenarios != null) {
      return scenarios;
    }
    if (!Files.isDirectory(scenariosDirectory)) {
      throw new IllegalStateException("Scenario directory does not exist: " + scenariosDirectory);
    }
    List<SyntheticScenario> loaded = new ArrayList<>();
    try (var directories = Files.list(scenariosDirectory)) {
      List<Path> manifests = new ArrayList<>();
      for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
        Path manifest = directory.resolve("manifest.yaml");
        if (Files.isRegularFile(manifest)) {
          manifests.add(manifest);
        }
      }
      for (Path manifest : manifests) {
        loaded.add(readManifest(manifest));
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Could not list scenario manifests", ex);
    }
    scenarios = List.copyOf(loaded);
    return scenarios;
  }

  /** Turns one manifest file into a scenario. A duplicate evidence id in that file is rejected. */
  private static SyntheticScenario readManifest(Path path) {
    Map<String, Object> document = mapping(path);
    String scenarioId = requiredText(document, "scenarioId", path);
    List<ManifestEvidence> evidence = new ArrayList<>();
    Object rawEvidence = document.get("evidence");
    if (!(rawEvidence instanceof List<?> entries)) {
      throw new IllegalStateException(path + " has no evidence list");
    }
    for (Object entry : entries) {
      if (!(entry instanceof Map<?, ?> raw)) {
        throw new IllegalStateException(path + " has an evidence entry that is not a mapping");
      }
      evidence.add(readEvidence(scenarioId, raw, path));
    }
    List<String> ids = evidence.stream().map(ManifestEvidence::evidenceId).toList();
    if (ids.stream().distinct().count() != ids.size()) {
      throw new IllegalStateException(path + " repeats an evidence id");
    }
    return new SyntheticScenario(
        scenarioId,
        requiredText(document, "workloadId", path),
        requiredText(document, "environment", path),
        Instant.parse(requiredText(document, "failureTime", path)),
        optionalText(document.get("autosysJobName")),
        optionalText(document.get("jobRunId")),
        evidence);
  }

  /** Reads one evidence mapping. A time without a trailing Z is rejected. */
  private static ManifestEvidence readEvidence(String scenarioId, Map<?, ?> raw, Path path) {
    Map<String, Object> entry = stringKeys(raw, path);
    String evidenceId = requiredText(entry, "evidenceId", path);
    String precision = requiredText(entry, "eventTimePrecision", path);
    Instant eventTime = null;
    Object rawTime = entry.get("eventTime");
    if (rawTime != null) {
      String text = optionalText(rawTime);
      if (text == null || !text.endsWith("Z")) {
        throw new IllegalStateException(evidenceId + " time is not UTC");
      }
      eventTime = Instant.parse(text);
    } else if (!"UNKNOWN".equals(precision)) {
      throw new IllegalStateException(evidenceId + " has no event time");
    }
    return new ManifestEvidence(
        evidenceId,
        requiredText(entry, "sourceSystem", path),
        requiredText(entry, "evidenceType", path),
        optionalText(entry.get("role")),
        eventTime,
        precision,
        requiredText(entry, "recordId", path),
        requiredText(entry, "contentFile", path),
        requiredText(entry, "summary", path));
  }

  /** Two job fields agree when either side omitted the field or both sides have the same text. */
  private static boolean sameWhenBothPresent(String scenarioValue, String requestValue) {
    if (scenarioValue == null || requestValue == null) {
      return true;
    }
    return scenarioValue.equals(requestValue);
  }

  /** Loads a YAML file and requires the top level to be a mapping with string keys. */
  private static Map<String, Object> mapping(Path path) {
    Object loaded;
    try {
      loaded = new Yaml().load(Files.readString(path));
    } catch (IOException ex) {
      throw new IllegalStateException("Could not read " + path, ex);
    }
    if (!(loaded instanceof Map<?, ?> raw)) {
      throw new IllegalStateException(path + " is not a YAML mapping");
    }
    return stringKeys(raw, path);
  }

  /** Copies a YAML mapping into a map whose keys are strings. */
  private static Map<String, Object> stringKeys(Map<?, ?> raw, Path path) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (var item : raw.entrySet()) {
      if (!(item.getKey() instanceof String key)) {
        throw new IllegalStateException(path + " has a non-string key");
      }
      copy.put(key, item.getValue());
    }
    return copy;
  }

  /** Reads a required non-blank string field. */
  private static String requiredText(Map<String, Object> document, String key, Path path) {
    String text = optionalText(document.get(key));
    if (text == null) {
      throw new IllegalStateException(path + " is missing " + key);
    }
    return text;
  }

  /** Returns a non-blank string, or null for any other YAML value. */
  private static String optionalText(Object value) {
    if (value instanceof String text && !text.isBlank()) {
      return text;
    }
    return null;
  }
}
