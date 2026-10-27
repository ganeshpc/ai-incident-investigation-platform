# Phase 1: synthetic scenarios

This guide explains the fixture data added in Phase 1. It assumes the language introductions in [`python-and-the-ai-service.md`](python-and-the-ai-service.md) and [`java-and-the-backend.md`](java-and-the-backend.md).

Phase 1 does not investigate anything. It stores four fictional incidents whose cause is already known, and it checks that a program can find the cited records by reading those files. No model is called. No database is written.

## 1. A fixture is data, not a running system

The files under `fixtures/` describe a made-up operations environment. A YAML file is a text file of nested keys. The loader reads it into dictionaries. Nothing in those files starts a job or connects to AutoSys, OpenShift, or GitHub.

Every workload file says `provenance: SIMULATED`. That word means the record was written for this demo. A later connector must keep that label when it reads these files, and must not describe them as a live vendor response.

Status labels such as `FAILURE` and `NOT_RUN_UPSTREAM_FAILED` are defined only in `fixtures/autosys/status-labels.yaml`. They are sentences about these fixtures. They are not AutoSys status codes.

## 2. How the files are split

```text
fixtures/
├── workloads/          one registry file per fictional workload
├── scenarios/          one manifest per incident, listing evidence ids
├── autosys/            scheduler definitions, dependencies, and runs
├── openshift/          pod identity and application logs
├── workflow/           generic case-review definition and case records
├── inputs/             the files the batch jobs read
├── source/             Java source cited by scenarios 1 and 3
├── github/invoice-ingest/
│   ├── revisions/      snapshot trees used to build commits
│   ├── expected-commits.yaml
│   └── regression-amount-parser.diff
├── releases/
└── runbooks/
evaluation/
├── scenario-catalog.yaml
└── ground-truth/       the known cause, kept apart from the evidence
```

The manifest is the index an investigator is allowed to see: ids, types, times, and file paths. The ground-truth file is the answer key: the cause category, the ids that support it, the ids that must not be treated as the cause, and any evidence that was deliberately left out. A later prompt to a model must not include the ground-truth directory.

Log files end in `.log.txt`. A plain `.log` suffix is gitignored in this repository because it is the usual name for local process logs. The fixture logs have to be committed, so they use the longer suffix.

## 3. The four incidents

| Scenario | Workload | What happened | What must not be chosen |
|---|---|---|---|
| `scenario-001-invalid-input` | payment reconciliation | Record 2 has an empty `transactionId`. `DEMO_RECON_JOB` exits 42. | A pool warning that also contains a prompt-injection sentence. |
| `scenario-002-upstream-dependency` | settlement pipeline | `DEMO_PREP_JOB` cannot find its input directory, so `DEMO_SETTLE_JOB` does not start. | `DEMO_UNRELATED_REPORT_JOB`, which fails at a similar time and has no dependency edge. |
| `scenario-003-workflow-validation` | case review | Case 100 cannot move to review because assignee and KYC are missing. | A disk-usage warning. The service was still writing the rejection. |
| `scenario-004-code-regression` | invoice ingest | The deployed parser rejects `10.00`. The previous revision accepted it. | A README-only commit made ten minutes earlier. |

Scenario 1 also has a downstream publish job that does not start. That record is a consequence. The invalid input remains the cause. Scenario 2 is the opposite shape: the upstream failure is the cause, and the downstream job is the consequence.

Scenario 2 names a gap on purpose. The dependency definition and the downstream status label exist. A scheduler condition-evaluation audit event does not. The ground-truth file lists that missing id, and the catalog rejects the load if a file is later added under the same id. The gap stays visible instead of being filled in.

Scenario 4's release record has `deployedCommit` set to the parser commit `ae366f73578063be38807dcc08d861875eedbca0`. The README commit `503430c7d53a0ff5b1d0f1a2189f4f0d779b81af` is close in time and is not that field. A commit time by itself is not deployment evidence.

## 4. One manifest entry

This entry from scenario 1 points at the failed run:

```yaml
- evidenceId: ev-s1-run-failed
  sourceSystem: AUTOSYS
  evidenceType: JOB_RUN
  role: primary
  eventTime: "2026-03-16T08:30:01Z"
  eventTimePrecision: EXACT
  recordId: run-recon-20260316-001
  contentFile: fixtures/autosys/scenario-001/run-recon-failed.yaml
```

`evidenceId` is the stable name other files use. `role` is the author's classification: `primary` supports the cause, `consequence` is a later effect, `negative` is a distractor, and `context` is comparison material such as an earlier successful run. The locator tested below does not read `role`. It has to find the same ids from the file text.

`eventTime` ends in `Z`, which means UTC. The loader rejects a timestamp that has no zone. A primary, consequence, or negative time must fall inside the scenario's lookback. Context is allowed to sit outside it, because the previous good run is part of the story and is not part of the failure window.

## 5. How the loader works

`ai-service/app/fixtures/catalog.py` reads `evaluation/scenario-catalog.yaml`, then each manifest and ground-truth file. `load_catalog` builds a new `Catalog` object every time. It does not keep a global list and it does not append. A second call therefore returns the same evidence ids and the same file hashes.

`canonical_summary` turns that catalog into a dictionary with sorted keys. `scripts/load-fixtures.sh` runs the loader twice and compares the two JSON documents with `cmp`. They match, and the script prints the count. The current fixtures load as 47 evidence records across 4 scenarios.

The loader also checks:

- Every evidence id is unique.
- Every ground-truth id exists, and every manifest id is classified once.
- The workload file exists and says `provenance: SIMULATED`.
- The record id appears in the cited file, or, for a runbook, the section name after `#` appears in the text.
- A declared missing-evidence id is not also a stored record.

There is no PostgreSQL write in this phase. The same records are what a later migration will insert. Repeating that insert has to be an update-or-skip, not a second copy. The file loader is the pattern for that later step.

## 6. Finding evidence without a model

`app/fixtures/locate.py` searches the text of each evidence file for literal phrases. For the invalid-input scenario, one rule is "the text contains `exitCode: 42` and `DEMO_RECON_JOB`". The only file that matches is the failed run. Another rule looks for `Ignore previous instructions`, which matches only the warning log.

`_match_any` applies several such tests and returns the evidence ids that matched:

```python
def _match_any(items: tuple[Evidence, ...], *predicates: Predicate) -> set[str]:
    found: set[str] = set()
    for item in items:
        if any(predicate(item.text) for predicate in predicates):
            found.add(item.evidence_id)
    return found
```

A predicate is a function that takes the file text and returns true or false. `*predicates` gathers every rule passed to the function. The test `test_each_scenario_is_explained_by_file_contents` compares those ids with the ground-truth lists. If a rule accidentally matches a distractor, the sets differ and the test fails.

This locator is a fixture check. It is not the investigation pipeline. Phase 5 replaces these hand-written phrases with the product's correlation rules. The phrases are still useful now because they prove the cited strings are actually in the files.

## 7. The invoice Git history

Scenario 4 needs commit ids, and a commit id changes if the author, timestamp, or file bytes change. `app/fixtures/demo_repo.py` rebuilds a repository in a temporary directory:

1. `git init` a new repository. This is not the monorepo's `.git`.
2. Copy `revisions/01-known-good/` and commit it at `2026-03-18T10:00:00+00:00`.
3. Copy the README from `02-unrelated-readme/` and commit it at `2026-03-20T09:00:00+00:00`. The parser file stays as it was.
4. Copy the parser from `03-regression/` and commit it at `2026-03-20T09:10:00+00:00`.

The author and committer are `Demo Engineer <demo-engineer@example.invalid>`. Signing is turned off so a local GPG setting cannot change the hash. The test builds the repository twice and compares the ids with `fixtures/github/invoice-ingest/expected-commits.yaml`. It also compares `git diff` of the parser with `regression-amount-parser.diff`.

The known-good id is `a383f65cb8a8b10604b8b0ebbb4d371814feb11c`. The README-only id is `503430c7d53a0ff5b1d0f1a2189f4f0d779b81af`. The regression id is `ae366f73578063be38807dcc08d861875eedbca0`.

## 8. Checks

From the repository root:

```bash
./scripts/load-fixtures.sh
cd ai-service && uv run pytest tests/test_scenario_catalog.py
```

`pytest` also compiles the cited Java files with `javac --release 21`. Those files are evidence. They are not part of the Spring Boot module, so Spotless does not format them.

## 9. What this phase does not do

- It does not create an incident through the API.
- It does not store the records in PostgreSQL.
- It does not decide a cause for a new incident. The locator only checks these four seeded files.
- It does not call OpenAI, and the injection line in the scenario 1 warning is evidence, not an instruction the loader obeys.
