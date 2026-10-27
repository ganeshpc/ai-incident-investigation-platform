package com.aiops.investigator.incident;

/**
 * The kind of failure the caller asked to investigate.
 *
 * <p>{@link #UNKNOWN} is the default when the request does not name a type. The service does not
 * infer a type from the workload.
 */
public enum IncidentType {
  BATCH_FAILURE,
  WORKFLOW_FAILURE,
  UNKNOWN
}
