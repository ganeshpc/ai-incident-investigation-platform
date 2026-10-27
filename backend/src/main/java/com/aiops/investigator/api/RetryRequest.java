package com.aiops.investigator.api;

/**
 * Options for {@code POST /api/incidents/{incidentId}/retry}.
 *
 * <p>This is a plain class rather than a record so an empty JSON object {@code {}} is valid.
 * Jackson fills a record through its constructor and rejects a missing primitive. A class field
 * starts as false and stays false when the JSON omits it. A finished investigation is rejected
 * unless {@code rerunCompletedInvestigation} is true. {@code rerunSuccessfulCollections} asks the
 * next attempt to collect again even when collection already finished.
 */
public class RetryRequest {

  private boolean rerunCompletedInvestigation;
  private boolean rerunSuccessfulCollections;

  /** The request used when the caller sends no JSON body. */
  public static RetryRequest defaults() {
    return new RetryRequest();
  }

  /** True when a finished investigation may be opened again. */
  public boolean rerunCompletedInvestigation() {
    return rerunCompletedInvestigation;
  }

  /** Jackson calls this when the JSON includes {@code rerunCompletedInvestigation}. */
  public void setRerunCompletedInvestigation(boolean rerunCompletedInvestigation) {
    this.rerunCompletedInvestigation = rerunCompletedInvestigation;
  }

  /** True when collection should run again instead of skipping successful sources. */
  public boolean rerunSuccessfulCollections() {
    return rerunSuccessfulCollections;
  }

  /** Jackson calls this when the JSON includes {@code rerunSuccessfulCollections}. */
  public void setRerunSuccessfulCollections(boolean rerunSuccessfulCollections) {
    this.rerunSuccessfulCollections = rerunSuccessfulCollections;
  }
}
