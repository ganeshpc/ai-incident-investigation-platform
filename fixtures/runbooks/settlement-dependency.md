# Settlement dependency chain

Synthetic runbook for the settlement pipeline demo. It is not an employer procedure.

## Dependency chain

`DEMO_SETTLE_JOB` starts only after `DEMO_PREP_JOB` succeeds.
The relationship is the fixture dependency `dep-prep-to-settle`.
A settle record with status label `NOT_RUN_UPSTREAM_FAILED` is a consequence of the preparation failure.

There is no dependency between `DEMO_PREP_JOB` and `DEMO_UNRELATED_REPORT_JOB`.
A report job that fails at a similar time is a separate incident unless a dependency record links the jobs.

## Preparation failure

When the preparation log says the source directory is missing, repair that directory and run `DEMO_PREP_JOB` again before expecting `DEMO_SETTLE_JOB` to start.

## Missing scheduler detail

These fixtures do not include a condition-evaluation audit event. The dependency definition and the downstream status label are present. The scheduler's own evaluation record is not. Do not invent that record.
