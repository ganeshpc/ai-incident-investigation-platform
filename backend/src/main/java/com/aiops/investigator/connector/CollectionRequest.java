package com.aiops.investigator.connector;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The facts a connector is allowed to see for one incident.
 *
 * <p>The workload mapping is the YAML document from the registry file. A connector looks at its own
 * section, such as {@code autosys} or {@code github}. {@code scenario} is null when no manifest
 * matches this failure. The connector then returns no records rather than choosing a nearby
 * scenario.
 *
 * @param workloadId the incident's workload
 * @param environment the incident's environment
 * @param failureTime the failure instant from the request
 * @param lookbackMinutes how far before the failure time collection may reach
 * @param autosysJobName the job name on the incident, or null
 * @param jobRunId the run id on the incident, or null
 * @param workload the workload YAML as nested maps and lists
 * @param scenario the matching manifest, or null
 * @param repositoryRoot the directory that contains {@code fixtures} and {@code evaluation}
 */
public record CollectionRequest(
    String workloadId,
    String environment,
    Instant failureTime,
    int lookbackMinutes,
    String autosysJobName,
    String jobRunId,
    Map<String, Object> workload,
    SyntheticScenario scenario,
    Path repositoryRoot) {

  /**
   * Whether the workload YAML has a non-empty section with this name.
   *
   * <p>A missing section means the source is not configured. An empty map or list is treated the
   * same way.
   */
  public boolean hasSection(String name) {
    Object value = workload.get(name);
    if (value instanceof Map<?, ?> map) {
      return !map.isEmpty();
    }
    if (value instanceof List<?> list) {
      return !list.isEmpty();
    }
    if (value instanceof String text) {
      return !text.isBlank();
    }
    return value != null;
  }
}
