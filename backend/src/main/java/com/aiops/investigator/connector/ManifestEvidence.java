package com.aiops.investigator.connector;

import java.time.Instant;

/**
 * One entry in a scenario manifest, before its file is read.
 *
 * <p>{@code role} is a label the fixture author assigned, such as {@code primary} or {@code
 * context}. Collection stores it so the evidence bundle matches the manifest. It is not a
 * conclusion the worker computed.
 *
 * @param evidenceId stable id, also used as the evidence key
 * @param sourceSystem which connector owns this entry
 * @param evidenceType kind of observation
 * @param role fixture label, or null
 * @param eventTime observation time, or null
 * @param eventTimePrecision EXACT, APPROXIMATE, or UNKNOWN
 * @param recordId id that should appear in the file or its path
 * @param contentFile repo-relative path of the file
 * @param summary one sentence from the manifest
 */
public record ManifestEvidence(
    String evidenceId,
    String sourceSystem,
    String evidenceType,
    String role,
    Instant eventTime,
    String eventTimePrecision,
    String recordId,
    String contentFile,
    String summary) {}
