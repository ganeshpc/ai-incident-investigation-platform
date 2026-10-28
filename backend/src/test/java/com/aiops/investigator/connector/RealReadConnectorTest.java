package com.aiops.investigator.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Checks the real clients against a local HTTP server.
 *
 * <p>The bodies are the fields from the vendor examples: AutoSys job, run, and dependency JSON, a
 * Kubernetes pod list, and a GitHub commit list plus compare file. The server is not those
 * products.
 */
class RealReadConnectorTest {

  @Test
  void autosysMapsTheDocumentedJobRunAndDependencyFields() throws Exception {
    HttpServer server =
        server(
            exchange -> {
              String path = exchange.getRequestURI().getPath();
              assertTrue(
                  exchange.getRequestHeaders().getFirst("Authorization").startsWith("Basic "));
              String body =
                  switch (path) {
                    case "/AEWS/job/test" ->
                        "{\"name\":\"test\",\"jobType\":\"CMD\",\"machine\":\"WSServer\",\"status\":\"4\"}";
                    case "/AEWS/job-run-info/test" ->
                        "{\"jobRun\":{\"name\":\"test\",\"runNum\":\"64035\",\"exitCode\":\"0\",\"runStartTime\":\"2012-05-21T15:36:27+0000\",\"runEndTime\":\"2012-05-21T15:36:27+0000\",\"status\":\"4\"}}";
                    case "/AEWS/job-dependencies/test" ->
                        "{\"predecessors\":{\"predecessor\":{\"name\":\"sleep1\",\"type\":\"job\"}},\"successors\":{\"successor\":{\"name\":\"sleep3\",\"type\":\"job\"}}}";
                    default -> throw new IllegalStateException(path);
                  };
              byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
              exchange.sendResponseHeaders(200, bytes.length);
              exchange.getResponseBody().write(bytes);
              exchange.close();
            });
    try {
      RealAutosysConnector connector =
          new RealAutosysConnector(ReadOnlyHttp.basic(base(server), "reader", "secret"));
      CollectionOutcome outcome =
          connector.collect(
              request(
                  "autosys",
                  Map.of("jobName", "test"),
                  Instant.parse("2012-05-21T16:00:00Z"),
                  "test"));
      assertEquals(CollectionStatus.PARTIAL, outcome.status());
      assertTrue(outcome.diagnosticMessage().contains("Job output was not read"));
      assertTrue(
          outcome.evidence().stream().anyMatch(item -> "JOB_RUN".equals(item.evidenceType())));
      assertTrue(
          outcome.evidence().stream()
              .anyMatch(item -> "JOB_DEFINITION".equals(item.evidenceType())));
      assertEquals(
          2,
          outcome.evidence().stream()
              .filter(item -> "DEPENDENCY".equals(item.evidenceType()))
              .count());
      assertTrue(outcome.evidence().stream().allMatch(item -> "REAL".equals(item.provenance())));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void autosysAuthenticationFailureDoesNotReturnEvidence() throws Exception {
    HttpServer server =
        server(
            exchange -> {
              exchange.sendResponseHeaders(401, -1);
              exchange.close();
            });
    try {
      CollectionOutcome outcome =
          new RealAutosysConnector(ReadOnlyHttp.basic(base(server), "reader", "secret"))
              .collect(
                  request(
                      "autosys",
                      Map.of("jobName", "test"),
                      Instant.parse("2012-05-21T16:00:00Z"),
                      "test"));
      assertEquals(CollectionStatus.UNAVAILABLE, outcome.status());
      assertEquals("AUTHENTICATION", outcome.errorCategory());
      assertTrue(outcome.evidence().isEmpty());
      assertTrue(outcome.diagnosticMessage().contains("Fixture data was not used."));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void openshiftReadsAPodAndItsLog() throws Exception {
    HttpServer server =
        server(
            exchange -> {
              String path = exchange.getRequestURI().getPath();
              String body =
                  path.endsWith("/log")
                      ? "2026-03-16T08:30:00Z ERROR record 2 missing required field transactionId\n"
                      : "{\"items\":[{\"metadata\":{\"name\":\"worker-a\",\"namespace\":\"operations-demo\"},\"spec\":{\"containers\":[{\"name\":\"worker\"}]},\"status\":{\"phase\":\"Running\"}}]}";
              byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
              exchange.sendResponseHeaders(200, bytes.length);
              exchange.getResponseBody().write(bytes);
              exchange.close();
            });
    try {
      CollectionOutcome outcome =
          new RealOpenShiftConnector(ReadOnlyHttp.bearer(base(server), "token"))
              .collect(
                  request(
                      "openshift",
                      Map.of(
                          "namespace",
                          "operations-demo",
                          "labels",
                          Map.of("app", "reconciliation-worker")),
                      Instant.parse("2026-03-16T08:30:00Z"),
                      null));
      assertEquals(CollectionStatus.SUCCESS, outcome.status());
      assertTrue(
          outcome.evidence().stream()
              .anyMatch(item -> item.contentExcerpt().contains("transactionId")));
      assertTrue(outcome.diagnosticMessage().contains("Fixture logs were not used."));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void githubComparesTheEarliestCommitWithTheLatest() throws Exception {
    AtomicReference<String> comparePath = new AtomicReference<>();
    HttpServer server =
        server(
            exchange -> {
              String path = exchange.getRequestURI().getPath();
              String body;
              if (path.contains("/compare/")) {
                comparePath.set(path);
                body =
                    "{\"files\":[{\"filename\":\"file1.txt\",\"status\":\"modified\",\"patch\":\"@@ -1 +1 @@\\n-old\\n+new\"}]}";
              } else {
                body =
                    "[{\"sha\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"commit\":{\"message\":\"later\",\"author\":{\"date\":\"2011-04-15T16:00:49Z\"}}},{\"sha\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"commit\":{\"message\":\"earlier\",\"author\":{\"date\":\"2011-04-14T16:00:49Z\"}}}]";
              }
              byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().add("Content-Type", "application/json");
              exchange.sendResponseHeaders(200, bytes.length);
              exchange.getResponseBody().write(bytes);
              exchange.close();
            });
    try {
      CollectionOutcome outcome =
          new RealGithubConnector(ReadOnlyHttp.bearer(base(server), "token"))
              .collect(
                  request(
                      "github",
                      Map.of("owner", "demo-org", "repository", "invoice-ingest"),
                      Instant.parse("2011-04-15T18:00:00Z"),
                      null));
      assertEquals(CollectionStatus.SUCCESS, outcome.status());
      assertTrue(outcome.diagnosticMessage().contains("does not show which commit was deployed"));
      assertTrue(
          comparePath
              .get()
              .contains(
                  "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa...bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
      assertTrue(
          outcome.evidence().stream().anyMatch(item -> "CODE_DIFF".equals(item.evidenceType())));
    } finally {
      server.stop(0);
    }
  }

  /** Starts a one-request server on loopback and returns it still running. */
  private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", handler);
    server.start();
    return server;
  }

  /** The base URL of a running test server. */
  private static String base(HttpServer server) {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** A collection request with one workload section. */
  private static CollectionRequest request(
      String section, Map<String, Object> value, Instant failureTime, String jobName) {
    return new CollectionRequest(
        "demo",
        "test",
        failureTime,
        180,
        jobName,
        null,
        Map.of(section, value),
        null,
        Path.of("."));
  }
}
