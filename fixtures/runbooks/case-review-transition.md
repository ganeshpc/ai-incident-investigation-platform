# Case review transition

Synthetic runbook for a generic case-review workflow. It does not describe a proprietary case-management product.

## Permitted transition

`DRAFT` may move to `READY_FOR_REVIEW` only when both of these are true:

- `assignee` is present.
- `kycStatus` is `COMPLETE`.

`com.demo.workflow.CaseTransitionValidator.requireReadyForReview` enforces that rule.
A rejection with the text `TransitionRejected: READY_FOR_REVIEW requires assignee and kycStatus COMPLETE` is an application validation failure.

The workflow engine is still running when it records that rejection. A disk-usage warning in the same time window does not show that the engine is down.

## Comparison

A case that already has an assignee and `kycStatus` `COMPLETE` can complete the same transition. Compare the rejected case with that successful case before treating the service as unavailable.
