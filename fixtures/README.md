# Synthetic fixtures

Four fictional incidents live here. The teaching guide is [`docs/phase-1-synthetic-scenarios.md`](../docs/phase-1-synthetic-scenarios.md).

| Scenario | Directory |
|---|---|
| Invalid reconciliation input | `scenarios/scenario-001-invalid-input/` |
| Upstream preparation failure | `scenarios/scenario-002-upstream-dependency/` |
| Rejected case transition | `scenarios/scenario-003-workflow-validation/` |
| Deployed parser regression | `scenarios/scenario-004-code-regression/` |

Evidence files are under `autosys/`, `openshift/`, `workflow/`, `inputs/`, `source/`, `github/`, `releases/`, and `runbooks/`. The answer key is `evaluation/ground-truth/`, not in these evidence files.

`./scripts/load-fixtures.sh` reads the catalog twice and requires the same result.
