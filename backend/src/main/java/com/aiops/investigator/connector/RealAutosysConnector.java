package com.aiops.investigator.connector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads AutoSys 12.1 job, run, and dependency records through AEWS.
 *
 * <p>The calls are the documented GET operations {@code /AEWS/job/{name}}, {@code
 * /AEWS/job-run-info/{name}}, and {@code /AEWS/job-dependencies/{name}}. Authentication is HTTP
 * Basic, which the 12.1 web-services page describes as the security of an AEWS transaction. Cancel,
 * hold, and force-start are not called. Job output is not read, because no spool read was verified,
 * so a successful call is still {@code PARTIAL}.
 */
public final class RealAutosysConnector {

  private static final String SOURCE = "AUTOSYS";
  private static final Duration AFTER_FAILURE = Duration.ofMinutes(15);
  private static final Map<String, String> JSON = Map.of("Accept", "application/json");

  private final ReadOnlyHttp http;

  public RealAutosysConnector(ReadOnlyHttp http) {
    this.http = http;
  }

  /** Reads the three documented resources for the workload's job. */
  public CollectionOutcome collect(CollectionRequest request) {
    String job = jobName(request);
    if (!EvidenceTexts.safeSegment(job)) {
      return CollectionOutcome.failed(
          SOURCE, "UNSAFE_NAME", false, "The AutoSys job name is not a single path segment.");
    }
    try {
      Map<String, Object> definition =
          JsonRecords.object(http.get(List.of("AEWS", "job", job), Map.of(), JSON), SOURCE);
      Map<String, Object> runs =
          JsonRecords.object(
              http.get(List.of("AEWS", "job-run-info", job), Map.of(), JSON), SOURCE);
      Map<String, Object> dependencies =
          JsonRecords.object(
              http.get(List.of("AEWS", "job-dependencies", job), Map.of(), JSON), SOURCE);
      List<CollectedEvidence> evidence = new ArrayList<>();
      evidence.add(definition(definition, job));
      evidence.addAll(runs(runs, request));
      evidence.addAll(dependencies(dependencies, job));
      String warning =
          "Job output was not read. No verified read-only spool operation was used. Fixture data was not used.";
      return CollectionOutcome.partial(SOURCE, evidence, warning, "NO_SPOOL", warning);
    } catch (RemoteCallException ex) {
      return RemoteOutcomes.from(SOURCE, ex);
    }
  }

  /** The incident job name, or the name on the workload when the incident omitted it. */
  private static String jobName(CollectionRequest request) {
    if (request.autosysJobName() != null && !request.autosysJobName().isBlank()) {
      return request.autosysJobName();
    }
    Object section = request.workload().get("autosys");
    if (section instanceof Map<?, ?> map) {
      return JsonRecords.text(map.get("jobName"));
    }
    return null;
  }

  /** One job-definition row from the documented job fields only. */
  private static CollectedEvidence definition(Map<String, Object> document, String job) {
    String name = textOr(document, "name", job);
    return row(
        "AUTOSYS:JOB_DEFINITION:" + name,
        "JOB_DEFINITION",
        name,
        null,
        "UNKNOWN",
        "/AEWS/job/" + name,
        Map.of(
            "name", name,
            "jobType", textOr(document, "jobType", ""),
            "machine", textOr(document, "machine", ""),
            "status", textOr(document, "status", ""),
            "api", "GET /AEWS/job/{name}"));
  }

  /** Run rows whose start or end falls in the incident window. */
  private static List<CollectedEvidence> runs(
      Map<String, Object> document, CollectionRequest request) {
    Instant start = request.failureTime().minus(Duration.ofMinutes(request.lookbackMinutes()));
    Instant end = request.failureTime().plus(AFTER_FAILURE);
    List<CollectedEvidence> rows = new ArrayList<>();
    for (Map<String, Object> run : JsonRecords.objects(document, "jobRun")) {
      Instant started = EvidenceTexts.instant(JsonRecords.text(run.get("runStartTime")));
      Instant ended = EvidenceTexts.instant(JsonRecords.text(run.get("runEndTime")));
      if (!inWindow(started, start, end)
          && !inWindow(ended, start, end)
          && (started != null || ended != null)) {
        continue;
      }
      String name = textOr(run, "name", "");
      String runNum = textOr(run, "runNum", "");
      String status = JsonRecords.text(run.get("strStatus"));
      if (status == null) {
        status = textOr(run, "status", "");
      }
      rows.add(
          row(
              "AUTOSYS:JOB_RUN:" + name + ":" + runNum,
              "JOB_RUN",
              runNum,
              started != null ? started : ended,
              started == null && ended == null ? "UNKNOWN" : "EXACT",
              "/AEWS/job-run-info/" + name,
              Map.of(
                  "name",
                  name,
                  "runNum",
                  runNum,
                  "exitCode",
                  textOr(run, "exitCode", ""),
                  "status",
                  status,
                  "runMachine",
                  textOr(run, "runMachine", ""),
                  "api",
                  "GET /AEWS/job-run-info/{name}")));
    }
    return rows;
  }

  /** One dependency row for each predecessor and successor name in the documented document. */
  private static List<CollectedEvidence> dependencies(Map<String, Object> document, String job) {
    List<CollectedEvidence> rows = new ArrayList<>();
    addDependencies(rows, document.get("predecessors"), "predecessor", job, "PREDECESSOR");
    addDependencies(rows, document.get("successors"), "successor", job, "SUCCESSOR");
    return rows;
  }

  /** Reads a predecessor or successor field, which the examples show as one object. */
  private static void addDependencies(
      List<CollectedEvidence> rows, Object wrapper, String field, String job, String direction) {
    if (!(wrapper instanceof Map<?, ?> map)) {
      return;
    }
    for (Map<String, Object> item : JsonRecords.objects(stringKeyMap(map), field)) {
      String name = JsonRecords.text(item.get("name"));
      if (name == null) {
        continue;
      }
      rows.add(
          row(
              "AUTOSYS:DEPENDENCY:" + job + ":" + direction + ":" + name,
              "DEPENDENCY",
              name,
              null,
              "UNKNOWN",
              "/AEWS/job-dependencies/" + job,
              Map.of(
                  "job", job,
                  "direction", direction,
                  "name", name,
                  "type", textOr(item, "type", ""),
                  "api", "GET /AEWS/job-dependencies/{name}")));
    }
  }

  /** True when a time is inside the closed window. A null time is not inside. */
  private static boolean inWindow(Instant value, Instant start, Instant end) {
    return value != null && !value.isBefore(start) && !value.isAfter(end);
  }

  /** A string field, or the fallback when the field is absent. */
  private static String textOr(Map<String, Object> document, String field, String fallback) {
    String text = JsonRecords.text(document.get(field));
    return text == null ? fallback : text;
  }

  /** Copies a raw map to a string-key map so {@link JsonRecords#objects} can read it. */
  private static Map<String, Object> stringKeyMap(Map<?, ?> raw) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (var entry : raw.entrySet()) {
      if (entry.getKey() instanceof String key) {
        copy.put(key, entry.getValue());
      }
    }
    return copy;
  }

  /** One real observation. Provenance is filled in later as REAL. */
  private static CollectedEvidence row(
      String key,
      String type,
      String recordId,
      Instant eventTime,
      String precision,
      String reference,
      Map<String, String> attributes) {
    String json = EvidenceTexts.json(attributes);
    return new CollectedEvidence(
        key,
        SOURCE,
        type,
        recordId,
        eventTime,
        precision,
        reference,
        json,
        FixtureDocuments.bounded(json),
        FixtureDocuments.sha256(json),
        "REAL",
        null);
  }
}
