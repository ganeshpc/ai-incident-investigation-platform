package com.aiops.investigator.incident;

import java.time.Instant;

/**
 * The investigate request after blank values have been trimmed and defaults applied.
 *
 * <p>A record keeps these fields together so the hash and the insert use one copy. Callers cannot
 * change a field after the record is created.
 */
public record InvestigateCommand(
    String title,
    String workloadId,
    String environment,
    Instant failureTime,
    int lookbackMinutes,
    String autosysJobName,
    String jobRunId,
    IncidentType incidentType,
    String requestHash) {

  /** Builds a command from the raw request fields. */
  public static InvestigateCommand from(
      String title,
      String workloadId,
      String environment,
      Instant failureTime,
      int lookbackMinutes,
      String autosysJobName,
      String jobRunId,
      IncidentType incidentType) {
    String resolvedTitle =
        title == null || title.isBlank() ? workloadId + " at " + failureTime : title.trim();
    IncidentType resolvedType = incidentType == null ? IncidentType.UNKNOWN : incidentType;
    String resolvedJob = blankToNull(autosysJobName);
    String resolvedRun = blankToNull(jobRunId);
    String hash =
        RequestFingerprint.hash(
            resolvedTitle,
            workloadId,
            environment,
            failureTime,
            lookbackMinutes,
            resolvedJob,
            resolvedRun,
            resolvedType);
    return new InvestigateCommand(
        resolvedTitle,
        workloadId,
        environment,
        failureTime,
        lookbackMinutes,
        resolvedJob,
        resolvedRun,
        resolvedType,
        hash);
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
