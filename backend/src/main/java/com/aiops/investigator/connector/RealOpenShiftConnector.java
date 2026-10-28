package com.aiops.investigator.connector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads pod identity and container logs from the Kubernetes pod API.
 *
 * <p>The list call is {@code GET /api/v1/namespaces/{namespace}/pods} with a {@code labelSelector}.
 * The log call is {@code GET /api/v1/namespaces/{namespace}/pods/{name}/log} with {@code
 * timestamps}, {@code sinceTime}, and {@code limitBytes}. Those query names are the documented pod
 * log parameters. The token is a bearer token. This class does not create, delete, or exec into a
 * pod. Logs of pods that no longer exist are not available from this API, and fixture logs are not
 * substituted.
 */
public final class RealOpenShiftConnector {

  private static final String SOURCE = "OPENSHIFT";
  private static final int POD_LIMIT = 5;
  private static final int LOG_BYTES = 32768;

  private final ReadOnlyHttp http;

  public RealOpenShiftConnector(ReadOnlyHttp http) {
    this.http = http;
  }

  /** Lists matching pods and reads one bounded log for each, up to five pods. */
  public CollectionOutcome collect(CollectionRequest request) {
    Object section = request.workload().get("openshift");
    if (!(section instanceof Map<?, ?> workload)) {
      return missing("The workload has no OpenShift namespace.");
    }
    String namespace = JsonRecords.text(workload.get("namespace"));
    if (!EvidenceTexts.safeSegment(namespace)) {
      return missing("The OpenShift namespace is missing or not a single path segment.");
    }
    String selector = selector(workload.get("labels"));
    if (selector == null) {
      return missing("The OpenShift labels are missing or contain a reserved character.");
    }
    try {
      Map<String, Object> list =
          JsonRecords.object(
              http.get(
                  List.of("api", "v1", "namespaces", namespace, "pods"),
                  Map.of("labelSelector", selector),
                  Map.of()),
              SOURCE);
      List<Map<String, Object>> pods = JsonRecords.objects(list, "items");
      if (pods.isEmpty()) {
        String message =
            "No pods matched the labels. The pod log API does not return logs for pods that no longer exist. Fixture logs were not used.";
        return CollectionOutcome.partial(SOURCE, List.of(), message, "NO_PODS", message);
      }
      boolean truncated = pods.size() > POD_LIMIT;
      List<CollectedEvidence> evidence = new ArrayList<>();
      int kept = 0;
      for (Map<String, Object> pod : pods) {
        if (kept == POD_LIMIT) {
          break;
        }
        addPod(evidence, request, namespace, pod);
        kept = kept + 1;
      }
      String warning =
          truncated
              ? "Only the first " + POD_LIMIT + " pods were read. Fixture logs were not used."
              : null;
      if (warning != null) {
        return CollectionOutcome.partial(SOURCE, evidence, warning, "POD_LIMIT", warning);
      }
      return CollectionOutcome.success(
          SOURCE,
          evidence,
          "Read "
              + evidence.size()
              + " pod records from the Kubernetes API. Fixture logs were not used.");
    } catch (RemoteCallException ex) {
      return RemoteOutcomes.from(SOURCE, ex);
    }
  }

  /**
   * Stores the pod and its current log. An empty current log is followed by one previous-log read.
   */
  private void addPod(
      List<CollectedEvidence> evidence,
      CollectionRequest request,
      String namespace,
      Map<String, Object> pod) {
    Object metadata = pod.get("metadata");
    if (!(metadata instanceof Map<?, ?> meta)) {
      return;
    }
    String name = JsonRecords.text(meta.get("name"));
    if (!EvidenceTexts.safeSegment(name)) {
      return;
    }
    String phase = phase(pod.get("status"));
    String container = containerName(pod.get("spec"));
    evidence.add(
        row(
            "OPENSHIFT:POD:" + namespace + ":" + name,
            "POD",
            name,
            null,
            "UNKNOWN",
            "/api/v1/namespaces/" + namespace + "/pods/" + name,
            Map.of("namespace", namespace, "name", name, "phase", phase == null ? "" : phase)));
    String since =
        request
            .failureTime()
            .minus(java.time.Duration.ofMinutes(request.lookbackMinutes()))
            .toString();
    Map<String, String> query = new java.util.LinkedHashMap<>();
    query.put("timestamps", "true");
    query.put("sinceTime", since);
    query.put("limitBytes", Integer.toString(LOG_BYTES));
    if (container != null) {
      query.put("container", container);
    }
    String log = readLog(namespace, name, query);
    if (log.isBlank()) {
      query.put("previous", "true");
      log = readLog(namespace, name, query);
    }
    evidence.add(
        row(
            "OPENSHIFT:APPLICATION_LOG:" + namespace + ":" + name,
            "APPLICATION_LOG",
            name,
            null,
            "APPROXIMATE",
            "/api/v1/namespaces/" + namespace + "/pods/" + name + "/log",
            Map.of(
                "namespace",
                namespace,
                "pod",
                name,
                "container",
                container == null ? "" : container),
            log));
  }

  /** Reads one log. An empty body is returned as an empty string rather than a fixture. */
  private String readLog(String namespace, String name, Map<String, String> query) {
    try {
      String body =
          http.get(
              List.of("api", "v1", "namespaces", namespace, "pods", name, "log"), query, Map.of());
      return body == null ? "" : body;
    } catch (RemoteCallException ex) {
      if (ex.status() == 404) {
        return "";
      }
      throw ex;
    }
  }

  /** Builds {@code key=value,key2=value2}. A comma or equals sign inside a label is rejected. */
  private static String selector(Object labels) {
    if (!(labels instanceof Map<?, ?> map) || map.isEmpty()) {
      return null;
    }
    List<String> parts = new ArrayList<>();
    for (var entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        return null;
      }
      String value = JsonRecords.text(entry.getValue());
      if (value == null
          || key.contains(",")
          || key.contains("=")
          || value.contains(",")
          || value.contains("=")) {
        return null;
      }
      parts.add(key + "=" + value);
    }
    return String.join(",", parts);
  }

  /** The pod phase, such as Running, when the status object has one. */
  private static String phase(Object status) {
    if (status instanceof Map<?, ?> map) {
      return JsonRecords.text(map.get("phase"));
    }
    return null;
  }

  /** The container name when the pod spec lists exactly one container. */
  private static String containerName(Object spec) {
    if (!(spec instanceof Map<?, ?> map)) {
      return null;
    }
    Object containers = map.get("containers");
    if (!(containers instanceof List<?> list)
        || list.size() != 1
        || !(list.getFirst() instanceof Map<?, ?> container)) {
      return null;
    }
    String name = JsonRecords.text(container.get("name"));
    return EvidenceTexts.safeSegment(name) ? name : null;
  }

  /** A configuration gap. No API call is implied by the caller when this is returned first. */
  private static CollectionOutcome missing(String message) {
    return CollectionOutcome.unconfigured(SOURCE, message + " Fixture data was not used.");
  }

  private static CollectedEvidence row(
      String key,
      String type,
      String recordId,
      Instant eventTime,
      String precision,
      String reference,
      Map<String, String> attributes) {
    return row(
        key,
        type,
        recordId,
        eventTime,
        precision,
        reference,
        attributes,
        EvidenceTexts.json(attributes));
  }

  private static CollectedEvidence row(
      String key,
      String type,
      String recordId,
      Instant eventTime,
      String precision,
      String reference,
      Map<String, String> attributes,
      String body) {
    return new CollectedEvidence(
        key,
        SOURCE,
        type,
        recordId,
        eventTime,
        precision,
        reference,
        EvidenceTexts.json(attributes),
        FixtureDocuments.bounded(body),
        FixtureDocuments.sha256(body),
        "REAL",
        null);
  }
}
