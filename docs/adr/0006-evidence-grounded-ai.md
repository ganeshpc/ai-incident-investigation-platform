# ADR 0006: The model reasons over collected evidence

## Status

Accepted

## Date

2026-10-26

## Context

A report that summarizes raw logs without references cannot be checked. Logs and runbooks can also contain text that looks like an instruction to the model.

## Decision

The model receives a bounded evidence bundle and returns structured hypotheses. Every evidence id in that response is checked against the incident's stored evidence. Unknown ids are removed or the report is rejected. The model may say the evidence is insufficient. Retrieved logs, code, and runbooks are untrusted evidence, not instructions. The model cannot call arbitrary URLs, shell commands, or source systems.

A deterministic report is produced before the model runs. If the model call fails, that report remains.

Hypothesis confidence is `LOW`, `MEDIUM`, or `HIGH` plus a written explanation. Those labels are not calibrated probabilities.

## Alternatives considered

- An agent with open-ended tools. Rejected. The plan forbids an agent that can run arbitrary commands or call arbitrary endpoints.
- Asking the model for a free-text root cause and storing that text as the report. Rejected because the report must cite evidence and state uncertainty.

## Consequences

- Prompt-injection text is part of the Phase 1 fixtures, and later tests check that it does not change the task.
- Report validation failures are audited.
- The deterministic pipeline is the baseline for evaluation. Model quality is measured only from real runs.
