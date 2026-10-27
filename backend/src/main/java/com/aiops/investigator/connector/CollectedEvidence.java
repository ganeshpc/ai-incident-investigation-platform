package com.aiops.investigator.connector;

import java.time.Instant;

/**
 * One normalized observation a connector is ready to store.
 *
 * <p>A record is a Java value with fixed fields. The connector fills those fields from a fixture
 * file. The database row is created later, on the worker thread, so a connector does not need a
 * database connection of its own.
 *
 * @param evidenceKey stable id for this observation, unique per incident
 * @param sourceSystem AUTOSYS, OPENSHIFT, GITHUB, RELEASE, RUNBOOK, or WORKFLOW
 * @param evidenceType kind of observation, such as JOB_RUN or APPLICATION_LOG
 * @param recordId id inside the fixture file, such as a run id or a commit id
 * @param eventTime when the observation happened, or null when the fixture has no time
 * @param eventTimePrecision EXACT, APPROXIMATE, or UNKNOWN
 * @param sourceReference repo-relative path of the fixture file
 * @param attributesJson small JSON object of ids and the file path, not the whole file
 * @param contentExcerpt bounded text from the file
 * @param contentHash SHA-256 of the full file bytes
 * @param role fixture label such as primary or context, not an investigation conclusion
 */
public record CollectedEvidence(
    String evidenceKey,
    String sourceSystem,
    String evidenceType,
    String recordId,
    Instant eventTime,
    String eventTimePrecision,
    String sourceReference,
    String attributesJson,
    String contentExcerpt,
    String contentHash,
    String role) {}
