package com.aiops.investigator.attempt;

/**
 * Whether one attempt is waiting, claimed by a worker, finished, or failed.
 *
 * <p>This is not the incident status. An incident can be {@code PARTIAL} while its latest attempt
 * is {@code COMPLETED}, because the attempt finished and the evidence is incomplete.
 */
public enum AttemptStatus {
  PENDING,
  RUNNING,
  COMPLETED,
  FAILED
}
