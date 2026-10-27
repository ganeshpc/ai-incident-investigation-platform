package com.aiops.investigator.workload;

import com.aiops.investigator.config.InvestigatorProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads workload fixture files into {@code workload_registry}.
 *
 * <p>{@link ApplicationRunner} runs after the application has started and Flyway has applied
 * migrations. Each file is inserted or, when its bytes changed, updated. A second start with the
 * same files does not insert another row.
 */
@Component
public class WorkloadCatalogLoader implements ApplicationRunner {

  private final WorkloadRepository workloads;
  private final InvestigatorProperties properties;
  private final Clock clock;

  public WorkloadCatalogLoader(
      WorkloadRepository workloads, InvestigatorProperties properties, Clock clock) {
    this.workloads = workloads;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Path directory = Path.of(properties.getFixturesRoot(), "workloads");
    if (!Files.isDirectory(directory)) {
      throw new IllegalStateException("Workload fixture directory does not exist: " + directory);
    }
    try (var files = Files.list(directory)) {
      files.filter(path -> path.getFileName().toString().endsWith(".yaml")).forEach(this::loadOne);
    } catch (IOException ex) {
      throw new IllegalStateException("Could not list workload fixtures", ex);
    }
  }

  /** Inserts or updates one workload file. The file name must match its workload id. */
  private void loadOne(Path path) {
    try {
      byte[] bytes = Files.readAllBytes(path);
      String hash = sha256(bytes);
      Map<String, Object> document = readYaml(path, bytes);
      String workloadId = requiredText(document, "workloadId", path);
      String expectedName = workloadId + ".yaml";
      if (!path.getFileName().toString().equals(expectedName)) {
        throw new IllegalStateException(path + " must be named " + expectedName);
      }
      String displayName = requiredText(document, "displayName", path);
      String environment = requiredText(document, "environment", path);
      String provenance = requiredText(document, "provenance", path);
      String jobName = autosysJobName(document);
      String definition = JsonValues.write(document);
      workloads
          .findById(workloadId)
          .ifPresentOrElse(
              existing -> {
                if (!hash.equals(existing.getContentHash())) {
                  existing.replace(
                      displayName,
                      environment,
                      provenance,
                      jobName,
                      definition,
                      hash,
                      clock.instant());
                }
              },
              () ->
                  workloads.save(
                      new Workload(
                          workloadId,
                          displayName,
                          environment,
                          provenance,
                          jobName,
                          definition,
                          hash,
                          clock.instant())));
    } catch (IOException ex) {
      throw new IllegalStateException("Could not load workload fixture " + path, ex);
    }
  }

  /**
   * Writes the YAML values as a JSON object.
   *
   * <p>The workload files only contain text, numbers, booleans, lists, and nested mappings. This
   * writer covers those values so the loader does not depend on a particular JSON library package.
   */
  static final class JsonValues {

    private JsonValues() {}

    static String write(Object value) {
      StringBuilder out = new StringBuilder();
      append(out, value);
      return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
      if (value == null) {
        out.append("null");
      } else if (value instanceof String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
          char current = text.charAt(i);
          switch (current) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            default -> out.append(current);
          }
        }
        out.append('"');
      } else if (value instanceof Number || value instanceof Boolean) {
        out.append(value);
      } else if (value instanceof Map<?, ?> map) {
        out.append('{');
        boolean first = true;
        for (var entry : map.entrySet()) {
          if (!first) {
            out.append(',');
          }
          first = false;
          append(out, String.valueOf(entry.getKey()));
          out.append(':');
          append(out, entry.getValue());
        }
        out.append('}');
      } else if (value instanceof Iterable<?> iterable) {
        out.append('[');
        boolean first = true;
        for (Object item : iterable) {
          if (!first) {
            out.append(',');
          }
          first = false;
          append(out, item);
        }
        out.append(']');
      } else {
        throw new IllegalStateException("Unsupported workload value " + value.getClass().getName());
      }
    }
  }

  private static Map<String, Object> readYaml(Path path, byte[] bytes) {
    Object loaded = new Yaml().load(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    if (!(loaded instanceof Map<?, ?> map)) {
      throw new IllegalStateException(path + " is not a YAML mapping");
    }
    java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
    for (var entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new IllegalStateException(path + " has a non-string key");
      }
      copy.put(key, entry.getValue());
    }
    return copy;
  }

  private static String requiredText(Map<String, Object> document, String key, Path path) {
    Object value = document.get(key);
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalStateException(path + " is missing " + key);
    }
    return text;
  }

  private static String autosysJobName(Map<String, Object> document) {
    Object autosys = document.get("autosys");
    if (!(autosys instanceof Map<?, ?> map)) {
      return null;
    }
    Object jobName = map.get("jobName");
    if (jobName instanceof String text && !text.isBlank()) {
      return text;
    }
    return null;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
