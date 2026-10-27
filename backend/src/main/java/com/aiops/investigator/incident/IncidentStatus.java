package com.aiops.investigator.incident;

/**
 * Lifecycle of one investigation.
 *
 * <p>An enum is a closed set of named constants. The database stores the name, such as {@code
 * CREATED}, and {@link IncidentStateMachine} is the only place that decides which name may follow
 * another.
 */
public enum IncidentStatus {
  CREATED,
  COLLECTING_EVIDENCE,
  EVIDENCE_READY,
  INVESTIGATING,
  REPORT_READY,
  PARTIAL,
  FAILED
}
