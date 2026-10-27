package com.aiops.investigator.attempt;

/**
 * The step a worker is allowed to run for one attempt.
 *
 * <p>{@link #CREATED} means the attempt is stored and no worker has claimed it. {@link
 * #EVIDENCE_READY} means the collection step has finished, even when the incident itself is only
 * partial.
 */
public enum AttemptStage {
  CREATED,
  COLLECTING_EVIDENCE,
  EVIDENCE_READY,
  INVESTIGATING,
  REPORT_READY
}
