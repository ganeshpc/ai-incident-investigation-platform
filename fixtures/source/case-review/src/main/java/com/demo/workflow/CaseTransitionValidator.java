package com.demo.workflow;

/** Applies one synthetic case-review transition rule. */
public final class CaseTransitionValidator {
  private CaseTransitionValidator() {}

  /**
   * Allows DRAFT to move to READY_FOR_REVIEW only when both preconditions hold.
   *
   * @param assignee operator id, required for review
   * @param kycStatus prerequisite status, required to equal COMPLETE
   */
  public static void requireReadyForReview(String assignee, String kycStatus) {
    boolean assigneePresent = assignee != null && !assignee.isBlank();
    boolean kycComplete = "COMPLETE".equals(kycStatus);
    if (!assigneePresent || !kycComplete) {
      throw new IllegalStateException(
          "TransitionRejected: READY_FOR_REVIEW requires assignee and kycStatus COMPLETE");
    }
  }
}
