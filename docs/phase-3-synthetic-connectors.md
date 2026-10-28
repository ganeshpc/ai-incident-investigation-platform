# Phase 3: synthetic connectors

This guide explains evidence collection. It assumes [`phase-2-incident-api.md`](phase-2-incident-api.md). That file walks the incident row, the worker lease, and the API. This file walks what the worker does after it claims an attempt.

Collection reads fixture files while each source mode is `simulated`, which is the default. It does not call the model. The answer key under `evaluation/ground-truth` is not opened. Phase 4 adds a `real` mode for the documented read APIs. That mode does not read these fixtures. It is explained in [`phase-4-real-adapters.md`](phase-4-real-adapters.md).

## 1. A connector is a boundary

An interface is a list of methods. `EvidenceConnector` has two: the source name, and `collect`. The orchestrator asks every connector, then writes the rows. A connector does not open the database. It returns observations. The worker thread stores them.

The request a connector receives is the incident's workload, environment, failure time, lookback, optional job name, and optional run id. It also receives the workload YAML. It does not receive a URL, a shell command, or a path chosen by the caller.

```text
claimed attempt
  → load the workload file
  → find the scenario manifest for this failure, if one matches
  → each connector reads its own fixture files
  → store new evidence rows and one collection result per source
  → EVIDENCE_READY when every configured source succeeded
  → PARTIAL when one configured source did not
```

Six connectors are registered: scheduler, pods and logs, local Git, releases, runbooks, and case workflow. A batch workload has no workflow section, so the workflow connector records `UNCONFIGURED`. The settlement workload has no GitHub section, so GitHub records `UNCONFIGURED`. That is not a failed call, and nothing is substituted from another workload.

## 2. The manifest is an index, not the answer

`SyntheticScenarioIndex` reads `fixtures/scenarios/*/manifest.yaml`. Each manifest lists evidence ids, types, times, roles, and file paths. The class never opens `evaluation/`.

A scenario matches an incident when the workload, environment, and failure time are the same. If both sides name a job or a run, those have to match too. If either side omitted the field, it does not reject the match. Two matches stop collection with an error. Zero matches means every configured connector returns `PARTIAL` with `No synthetic scenario matches this incident. No records were invented.` The incident becomes `PARTIAL` and the evidence list stays empty.

`FixtureDocuments` resolves a repo-relative path and rejects two kinds of escape. `../` that leaves the repository is `UNSAFE_PATH`. A path under `evaluation/` is also `UNSAFE_PATH`, even though that directory is inside the repository, because that directory is the answer key. A missing file is `MISSING_FIXTURE` and the connector result is `UNAVAILABLE`. The other connectors still store what they read.

## 3. What each connector stores

The shared parent is `SyntheticFixtureConnector`. It checks the workload section, then asks `SyntheticEvidenceReader` for that source's manifest entries.

The reader keeps three kinds of entries:

- An entry with no event time.
- An entry whose fixture role is `context`, even when the time is outside the window. The earlier successful run and the older release use that label.
- Any other timed entry between the lookback start and fifteen minutes after the failure time.

A one-minute lookback on scenario 1 therefore drops `ev-s1-log-warning` at 08:15 and still keeps the failed run and the previous day's context run.

Each stored row uses the manifest evidence id as `evidence_key`. The key is unique per incident. A second collection sees the key and does not insert another row. The excerpt is at most 2000 characters. The SHA-256 hash is of the full file. `provenance` is `SIMULATED`. `redaction_status` is `NOT_REQUIRED` because these fixtures contain no secret to remove. `role` is the fixture author's label. It is not a conclusion the worker computed.

The diagnostic sentence names the fixture and says which API was not called. The GitHub sentence says `This is not a GitHub API call.` The release connector copies the fixture file and does not add a deployed revision the file does not contain.

The scheduler connector adds one check. If the incident names a run id and none of the returned records use that id, the result is `PARTIAL` with `MISSING_RECORD`. The runs that were found are still stored. The missing run is not invented.

## 4. The orchestrator

`CollectionOrchestrator.collect` sorts the connectors by source name so the results are stable. It runs them on a pool of two threads. Two is the bound. The whole batch has five seconds. A connector that does not finish is cancelled, stored as `FAILED` with category `TIMEOUT`, and its evidence is discarded. `retry_appropriate` is true for that timeout because the same read might finish later. A missing fixture is not retried: the file will still be missing.

The pool threads only read files. The database writes happen afterward on the worker thread, in the same transaction as the attempt update.

One collection result row is stored per source per attempt. The status is one of `SUCCESS`, `PARTIAL`, `UNAVAILABLE`, `UNCONFIGURED`, or `FAILED`. A source that is not on the workload is `UNCONFIGURED` and does not make the incident partial. Any other status besides `SUCCESS` does.

When at least one record was stored and every configured source succeeded, the incident becomes `EVIDENCE_READY` and `errorSummary` stays null. The attempt completes at stage `EVIDENCE_READY`. There is still no report row.

An attempt whose stage is already `INVESTIGATING` does not collect. It finishes `PARTIAL` with `AI analysis is not configured.`

## 5. Retry

`EVIDENCE_READY` can move to `INVESTIGATING` or back to `COLLECTING_EVIDENCE`.

A retry without `rerunSuccessfulCollections` moves a collected incident to `INVESTIGATING`. Collection is not repeated. A retry with that flag moves it back to `COLLECTING_EVIDENCE`. The new attempt collects again. Evidence keys that already exist stay as one row.

## 6. What you can see

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run
```

```bash
curl -s -H 'Content-Type: application/json' \
  -d '{"workloadId":"payment-reconciliation-demo","environment":"test","failureTime":"2026-03-16T08:30:00Z","lookbackMinutes":120,"autosysJobName":"DEMO_RECON_JOB","jobRunId":"run-recon-20260316-001","incidentType":"BATCH_FAILURE"}' \
  http://127.0.0.1:8080/api/incidents/investigate
```

The response is 202. After the worker runs, `GET /api/incidents/{id}` is `EVIDENCE_READY` and `GET /api/incidents/{id}/evidence` contains 13 records, including `ev-s1-run-failed` and `ev-s1-log-validation`. The log excerpt contains the validation failure. `GET /api/incidents/{id}/report` is 404.

`cd backend && ./mvnw -B verify` runs these checks against PostgreSQL in Docker. It does not need `OPENAI_API_KEY`.

## 7. What this phase does not do

The deterministic timeline and hypotheses are Phase 5. The model call is Phase 6. A real read-only adapter is Phase 4, and only after its API is verified. Until then a real mode stays unconfigured. These connectors do not silently switch from a failed real call to a fixture.
