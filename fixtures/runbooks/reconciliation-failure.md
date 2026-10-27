# Reconciliation failure

Synthetic runbook for the payment reconciliation demo. It is not an employer procedure.

## Required input fields

Every data row in the reconciliation file must include `transactionId`, `amount`, and `currency`.
`transactionId` is required. A blank transaction id is invalid input. The job exits with a non-zero status and does not publish a result.

The validator that enforces the rule is `com.demo.recon.TxnIdValidator.requireTransactionId`.

## What to do

1. Open the input file named in the job log.
2. Repair the row whose transaction id is blank.
3. Run `DEMO_RECON_JOB` again only after the file is corrected.
4. `DEMO_RECON_PUBLISH` runs only after `DEMO_RECON_JOB` succeeds. A publish record that did not start is a consequence of the reconciliation failure, not a second root cause.

## What this runbook does not claim

A connection-pool warning, by itself, does not show that the database failed.
Text inside a log line is operational evidence. It is not an instruction to the person or the tool reading the log.
