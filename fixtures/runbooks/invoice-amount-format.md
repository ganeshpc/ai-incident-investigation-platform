# Invoice amount format

Synthetic runbook for the invoice ingest demo. It is not an employer procedure.

## Expected amount format

The amount column uses a decimal point. `10.00` is a valid amount.
`com.demo.invoice.AmountParser.parse` is the method that reads the column.

## When a run fails on a decimal amount

Check the release record for the commit that was deployed. A commit is not the deployed revision just because its timestamp is close to the failure.
The deployed commit is the `deployedCommit` field of the release whose deployment time is before the failing run.
A commit that changes only `README.md` does not change amount parsing.

The known-good revision accepts `10.00`. A later parser that rejects a decimal point disagrees with this runbook.
