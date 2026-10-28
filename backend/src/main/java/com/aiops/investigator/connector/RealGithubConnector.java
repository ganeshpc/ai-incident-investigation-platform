package com.aiops.investigator.connector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reads commits and one comparison from the GitHub REST API.
 *
 * <p>Commits use {@code GET /repos/{owner}/{repo}/commits} with the documented {@code since} and
 * {@code until} parameters. A comparison uses {@code GET /repos/{owner}/{repo}/compare/{basehead}}
 * where {@code basehead} is {@code BASE...HEAD}. The headers are the ones in the current commit
 * docs: {@code Accept: application/vnd.github+json}, {@code Authorization: Bearer}, and {@code
 * X-GitHub-Api-Version: 2026-03-10}. A comparison does not prove that a commit was deployed.
 */
public final class RealGithubConnector {

  private static final String SOURCE = "GITHUB";
  private static final Map<String, String> HEADERS =
      Map.of(
          "Accept", "application/vnd.github+json",
          "X-GitHub-Api-Version", "2026-03-10");

  private final ReadOnlyHttp http;

  public RealGithubConnector(ReadOnlyHttp http) {
    this.http = http;
  }

  /** Lists commits in the incident window and compares the earliest with the latest. */
  public CollectionOutcome collect(CollectionRequest request) {
    Object section = request.workload().get("github");
    if (!(section instanceof Map<?, ?> workload)) {
      return missing("The workload has no GitHub repository.");
    }
    String owner = JsonRecords.text(workload.get("owner"));
    String repo = JsonRecords.text(workload.get("repository"));
    if (!EvidenceTexts.safeSegment(owner) || !EvidenceTexts.safeSegment(repo)) {
      return missing("The GitHub owner or repository is missing or not a single path segment.");
    }
    Instant start = request.failureTime().minus(Duration.ofMinutes(request.lookbackMinutes()));
    Instant end = request.failureTime().plus(Duration.ofMinutes(15));
    try {
      String body =
          http.get(
              List.of("repos", owner, repo, "commits"),
              Map.of("since", start.toString(), "until", end.toString(), "per_page", "100"),
              HEADERS);
      List<Map<String, Object>> commits = commitList(body);
      commits.sort(Comparator.comparing(RealGithubConnector::authorDate));
      List<CollectedEvidence> evidence = new ArrayList<>();
      for (Map<String, Object> commit : commits) {
        CollectedEvidence row = commitRow(owner, repo, commit);
        if (row != null) {
          evidence.add(row);
        }
      }
      String message =
          "Read " + evidence.size() + " commits from the GitHub API. Fixture files were not used.";
      if (commits.size() >= 2) {
        String base = sha(commits.get(0));
        String head = sha(commits.get(commits.size() - 1));
        if (base != null && head != null && !base.equals(head)) {
          evidence.addAll(compare(owner, repo, base, head));
          message =
              "Compared "
                  + base
                  + "..."
                  + head
                  + ". This compare does not show which commit was deployed. Fixture files were not used.";
        }
      }
      return CollectionOutcome.success(SOURCE, evidence, message);
    } catch (RemoteCallException ex) {
      return RemoteOutcomes.from(SOURCE, ex);
    }
  }

  /** The list-commits response is a JSON array, unlike the compare response. */
  private static List<Map<String, Object>> commitList(String body) {
    try {
      List<Object> values =
          org.springframework.boot.json.JsonParserFactory.getJsonParser().parseList(body);
      List<Map<String, Object>> commits = new ArrayList<>();
      for (Object value : values) {
        if (value instanceof Map<?, ?> map) {
          java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
          for (var entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
              copy.put(key, entry.getValue());
            }
          }
          commits.add(copy);
        }
      }
      return commits;
    } catch (org.springframework.boot.json.JsonParseException ex) {
      throw new RemoteCallException(0, "GITHUB returned a document that is not JSON");
    }
  }

  /** One commit row. The time is {@code commit.author.date} from the documented example. */
  private static CollectedEvidence commitRow(
      String owner, String repo, Map<String, Object> commit) {
    String sha = sha(commit);
    if (sha == null || !EvidenceTexts.safeSegment(sha)) {
      return null;
    }
    String message = "";
    Instant when = null;
    Object nested = commit.get("commit");
    if (nested instanceof Map<?, ?> map) {
      message = JsonRecords.text(map.get("message"));
      Object author = map.get("author");
      if (author instanceof Map<?, ?> authorMap) {
        when = EvidenceTexts.instant(JsonRecords.text(authorMap.get("date")));
      }
    }
    String text = message == null ? sha : sha + "\n" + message;
    return new CollectedEvidence(
        "GITHUB:COMMIT:" + sha,
        SOURCE,
        "COMMIT",
        sha,
        when,
        when == null ? "UNKNOWN" : "EXACT",
        "/repos/" + owner + "/" + repo + "/commits/" + sha,
        EvidenceTexts.json(Map.of("sha", sha, "api", "GET /repos/{owner}/{repo}/commits")),
        FixtureDocuments.bounded(text),
        FixtureDocuments.sha256(text),
        "REAL",
        null);
  }

  /**
   * Changed files between two commits. A missing {@code patch} is kept as a file name only. Binary
   * files are documented to omit the patch.
   */
  private List<CollectedEvidence> compare(String owner, String repo, String base, String head) {
    String basehead = base + "..." + head;
    Map<String, Object> document =
        JsonRecords.object(
            http.get(List.of("repos", owner, repo, "compare", basehead), Map.of(), HEADERS),
            SOURCE);
    List<CollectedEvidence> rows = new ArrayList<>();
    for (Map<String, Object> file : JsonRecords.objects(document, "files")) {
      String filename = JsonRecords.text(file.get("filename"));
      if (filename == null) {
        continue;
      }
      String patch = JsonRecords.text(file.get("patch"));
      String status = textOr(file, "status");
      String body = patch == null ? filename + " " + status : patch;
      rows.add(
          new CollectedEvidence(
              "GITHUB:CODE_DIFF:" + base + "..." + head + ":" + filename,
              SOURCE,
              "CODE_DIFF",
              filename,
              null,
              "UNKNOWN",
              "/repos/" + owner + "/" + repo + "/compare/" + basehead,
              EvidenceTexts.json(
                  Map.of(
                      "base", base,
                      "head", head,
                      "filename", filename,
                      "status", status,
                      "api", "GET /repos/{owner}/{repo}/compare/{basehead}")),
              FixtureDocuments.bounded(body),
              FixtureDocuments.sha256(body),
              "REAL",
              null));
    }
    return rows;
  }

  /** The commit SHA, which the list and compare examples both name {@code sha}. */
  private static String sha(Map<String, Object> commit) {
    return JsonRecords.text(commit.get("sha"));
  }

  /** Author time used to order commits from earliest to latest. A missing time sorts last. */
  private static Instant authorDate(Map<String, Object> commit) {
    Object nested = commit.get("commit");
    if (nested instanceof Map<?, ?> map) {
      Object author = map.get("author");
      if (author instanceof Map<?, ?> authorMap) {
        Instant when = EvidenceTexts.instant(JsonRecords.text(authorMap.get("date")));
        if (when != null) {
          return when;
        }
      }
    }
    return Instant.MAX;
  }

  private static String textOr(Map<String, Object> document, String field) {
    String text = JsonRecords.text(document.get(field));
    return text == null ? "" : text;
  }

  private static CollectionOutcome missing(String message) {
    return CollectionOutcome.unconfigured(SOURCE, message + " Fixture data was not used.");
  }
}
