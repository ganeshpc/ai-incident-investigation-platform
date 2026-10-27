# Phase 2: the incident API

This guide explains the system of record added in Phase 2. It assumes [`java-and-the-backend.md`](java-and-the-backend.md). That file walks Java, Maven, and the status route. This file walks what happens after someone asks the backend to investigate a failure.

Phase 2 does not collect evidence and does not call a model. The AI service can be down. The backend still accepts an incident, stores it, and moves it to a finished partial state that says why it stopped.

## 1. One request, then a separate worker

`POST /api/incidents/investigate` does not hold the HTTP connection open while it thinks. The handler inserts two rows and returns `202 Accepted`. A worker in the same process later claims the attempt and finishes it.

```text
HTTP thread
  validate the body
  reject an unknown workload before any insert
  commit the incident and attempt 1
  return 202

worker, after that commit
  claim a waiting attempt with a short lease
  find that no connector is registered
  mark the incident PARTIAL
  mark the attempt COMPLETED
  write an audit row
```

The worker is not inside the HTTP transaction. If the process stops after the `202` and before the worker finishes, the attempt is still in the database. The next poll can claim it again when its lease expires.

There is no Kafka topic. One process both accepts the request and runs the attempt.

## 2. Flyway creates the tables, Hibernate only checks them

A migration is a SQL file Spring runs once. The file is `backend/src/main/resources/db/migration/V1__create_investigation_schema.sql`. Flyway records that name in `flyway_schema_history`. A second start does not run it again.

Hibernate is the library that turns a Java object into `INSERT` and `SELECT`. `spring.jpa.hibernate.ddl-auto` is `validate`. Hibernate compares its mapped classes to the tables and refuses to start if a column disagrees. It does not create or alter tables. The SQL file is the only place that does that.

`spring.jpa.open-in-view` is `false`. A web request does not keep a database session open after the service method returns. The JSON is built from values already loaded.

Times are stored as `timestamptz`. The pool runs `SET TIME ZONE 'UTC'` on each connection, and Hibernate is told `hibernate.jdbc.time_zone: UTC`. A timestamp written by JDBC and a timestamp written by Hibernate are the same UTC instant.

## 3. The tables

| Table | Written in Phase 2 | What a row is |
|---|---|---|
| `workload_registry` | yes, at startup | One fixture workload. The document is JSON. The job name is also its own column. |
| `incidents` | yes | One accepted investigation. |
| `investigation_attempts` | yes | One pass of work. A retry adds a row. It does not change the earlier row. |
| `audit_events` | yes | One recorded operation, such as `INCIDENT_CREATED` or `RETRY_REJECTED`. |
| `evidence_records` | no | Empty until a connector stores evidence. The list endpoint returns an empty page. |
| `collection_results` | no | Not used. Phase 2 does not invent a per-source `UNCONFIGURED` row. |
| `hypotheses` and `hypothesis_evidence` | no | Present so a later phase can store a conclusion. Confidence, when it exists, is `LOW`, `MEDIUM`, or `HIGH`. |
| `investigation_reports` | no | Absent on purpose. `GET .../report` returns 404. |

Queryable facts are columns: status, workload id, failure time, attempt number. The workload document and, later, evidence attributes are `jsonb`. The whole investigation is not one JSON blob.

Hashes are `varchar(64)`. A `char(64)` column would pad with spaces and break an equality check.

`idempotency_key` is unique. PostgreSQL allows many nulls in a unique column, so requests that omit the header can all insert.

## 4. Which Java types are entities

An entity is a class Hibernate loads and saves. Phase 2 maps the tables it actually reads or writes:

- `Workload`
- `Incident`, with `@Version` so two updates cannot silently overwrite each other
- `InvestigationAttempt`
- `EvidenceRecord`, read-only for now
- `InvestigationReport`
- `AuditEvent`

`collection_results`, `hypotheses`, and `hypothesis_evidence` are SQL only. Hibernate's validate check does not require a Java class for every table.

Packages under `com.aiops.investigator`:

```text
api/        HTTP and problem JSON
incident/   the row, the state machine, create and retry
attempt/    one pass of work
workload/   fixture load
evidence/   read an empty page
report/     read, and 404 when none exists
audit/      append-only events
worker/     claim and finish
config/     clock, scheduling, settings
```

## 5. Starting PostgreSQL

`compose.yaml` at the repository root starts only the database. The image is `postgres:18.6`. The port is `127.0.0.1:5432`. The volume path is `/var/lib/postgresql`, which is the path that image expects. The user, password, and database name default to `investigator`, matching `.env.example`. Those values are local placeholders.

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run
```

The datasource URL is `jdbc:postgresql://127.0.0.1:5432/investigator`. On this Mac, `localhost` resolves to IPv6 first, and a published Docker port often refuses that address. The URL uses `127.0.0.1` directly.

Tests do not use this Compose database. `PostgresIntegrationTest` starts one `postgres:18.6` container for the whole test JVM and throws it away when the JVM exits. `./mvnw verify` needs a running Docker daemon.

Two local details are easy to miss:

- Rancher Desktop keeps its socket at `~/.rd/docker.sock` and does not create `/var/run/docker.sock` unless administrative access is on. The Docker CLI reads its context and finds the socket. Testcontainers does not. The Maven profile `rancher-desktop-docker` turns on only when `/var/run/docker.sock` is missing, and it sets `DOCKER_HOST` for the test JVM. Inside the Linux VM the socket path Ryuk must mount is still `/var/run/docker.sock`.
- The published test port accepts connections about a second after Docker says the container has started. The test waits until `127.0.0.1` accepts TCP before Spring opens a pool. Hikari tries once at startup and would otherwise stop.

`src/test/resources/application-test.yml` is a profile, not a second `application.yml`. A second file of that name would hide the main settings. `@ActiveProfiles("test")` turns the worker timer off so a test can arrange rows before it calls `worker.runOnce()`.

## 6. The workload catalog is loaded once per start

`WorkloadCatalogLoader` is an `ApplicationRunner`. Spring calls it after Flyway has migrated. It reads `investigator.fixtures-root`, default `../fixtures`, then `workloads/*.yaml`. Maven's working directory is `backend/`, so `../fixtures` is the repository's fixture directory.

The file name must be `{workloadId}.yaml`. The loader hashes the raw bytes with SHA-256. The same hash skips the update. A changed file updates that one row. It does not insert a second row. Evidence fixtures and ground truth are not loaded. Those files stay on disk until a connector reads them.

The definition column stores JSON, not YAML. The loader writes the YAML map with a small writer of its own, `JsonValues`, so the column does not depend on which Jackson package Boot 4 put on the classpath.

A missing `workloads` directory stops startup. The tests assert that the four Phase 1 workloads are present, and that a second call of the loader still leaves four rows.

## 7. Accepting an investigation

The body is a Java record, `InvestigateRequest`. Validation runs before the controller method. A missing `workloadId` is HTTP 400 problem JSON whose detail contains `workloadId`.

An unknown workload is HTTP 422, title `Unknown workload`. Nothing is inserted. 422 is `HttpStatus.UNPROCESSABLE_CONTENT`.

`InvestigateCommand.from` trims the fields and fills defaults before anything is hashed or stored:

- A blank title becomes `workloadId + " at " + failureTime`.
- A missing incident type becomes `UNKNOWN`. Allowed names are `BATCH_FAILURE`, `WORKFLOW_FAILURE`, and `UNKNOWN`.
- A blank job name or run id becomes null.
- Lookback must be from 1 to 10080 minutes.

`createdBy` comes from the `X-Actor` header. A missing header stores `local-dev`. A value longer than 200 characters is HTTP 400.

The response is 202 and a small JSON object: `incidentId`, `status` (`CREATED`), `attemptNumber` (1), `createdAt`, and `replayed` (false on the first call).

### Idempotency

The header is `Idempotency-Key`. A blank header means "this is a new incident." The key is not part of the hash.

The hash is SHA-256 of one canonical string: title, workload id, environment, `Instant.toString()` of the failure time, lookback, job name, run id, and incident type, joined by newlines. Nulls are empty strings. The hash is stored only when a key was sent.

The same key and the same body return the original incident id, still 202, with `replayed: true`, and an `IDEMPOTENCY_REPLAY` audit row.

The same key and a different body return 409, title `Idempotency conflict`.

Two requests with the same new key can pass the "does this key exist?" read at the same moment. The unique constraint then rejects one insert. That failure is caught outside the transaction, the row is read again, and the caller gets the replay or the 409. The catch has to be outside the transaction. Inside it, the failed statement has already marked the transaction unusable.

## 8. The state machine

`IncidentStateMachine` is the only list of legal status changes. A move that is not in the list throws. The API turns that into 409, title `Invalid status change`. Calling code does not assign `incident.status = ...` on its own. `applyStatus` writes a move the machine already allowed.

```text
CREATED              → COLLECTING_EVIDENCE
COLLECTING_EVIDENCE  → EVIDENCE_READY, PARTIAL, FAILED
EVIDENCE_READY       → INVESTIGATING
INVESTIGATING        → REPORT_READY, PARTIAL, FAILED
PARTIAL              → COLLECTING_EVIDENCE, INVESTIGATING
FAILED               → COLLECTING_EVIDENCE, INVESTIGATING
REPORT_READY         → COLLECTING_EVIDENCE, INVESTIGATING
```

`CREATED` cannot jump to `FAILED`. The claim step moves `CREATED` to `COLLECTING_EVIDENCE` before a later failure is allowed to record `FAILED`.

Attempt status is separate: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`. Attempt stage is `CREATED`, `COLLECTING_EVIDENCE`, `EVIDENCE_READY`, `INVESTIGATING`, or `REPORT_READY`.

## 9. Reading what was stored

| Request | Result |
|---|---|
| `GET /api/incidents?page=0&size=20` | One page, newest `createdAt` first. Default size 20, maximum 100. |
| `GET /api/incidents/{id}` | The incident, or 404 title `Incident not found`. |
| `GET /api/incidents/{id}/evidence` | An empty page when the incident exists and nothing has been collected. That is 200, not 404. |
| `GET /api/incidents/{id}/attempts` | Every attempt, oldest first. |
| `GET /api/incidents/{id}/report` | 404 detail `No report is available for this incident.` |

The page JSON is a small object, not Spring's `Page` type: `items`, `page`, `size`, `totalElements`, `totalPages`. `page` below 0, or `size` outside 1 to 100, is HTTP 400.

JSON field names are the Java names in camelCase. The Python service uses snake_case. These are different documents.

## 10. Retry

`POST /api/incidents/{id}/retry` opens attempt N+1. It does not delete the old attempt or any evidence.

The body is optional. `{}` means both flags are false. `RetryRequest` is a class with boolean fields, not a record. Jackson builds a record through its constructor and rejects a missing primitive, so an empty object would be HTTP 400. A class field starts as false and stays false when the JSON omits it.

Rules:

- The latest attempt is `PENDING` or `RUNNING`: 409, title `Attempt in progress`. The rejection is audited in a new transaction (`REQUIRES_NEW`) so the audit row survives the rollback of the 409.
- The incident is `REPORT_READY` and `rerunCompletedInvestigation` is false: 409, title `Investigation already finished`.
- `rerunSuccessfulCollections` true: the incident moves to `COLLECTING_EVIDENCE` and the new attempt stage is `CREATED`.
- Otherwise, if the latest stage is `INVESTIGATING` or the incident is `REPORT_READY`: the incident moves to `INVESTIGATING` and the new attempt stage is `INVESTIGATING`. Collection is not repeated.
- Otherwise the new attempt collects again.

A finished investigation stays finished until a retry explicitly asks to run again.

## 11. The worker

`IncidentWorker.scheduled` runs after the previous poll finishes. The delay is `investigator.worker.poll-delay-ms`, one second in the main settings. `schedule-enabled` is false in tests. Tests call `runOnce()`.

`AttemptClaimer.claim` runs one transaction:

```sql
SELECT id FROM investigation_attempts
WHERE status = 'PENDING'
   OR (status = 'RUNNING' AND lease_until < :now)
ORDER BY created_at
FOR UPDATE SKIP LOCKED
LIMIT :limit
```

`FOR UPDATE SKIP LOCKED` locks the chosen rows and skips rows another transaction already locked. Two polls will not claim the same attempt. The batch size is clamped to 1 through 10. The running process then sets status `RUNNING`, sets a lease owner and `lease_until`, and fills `started_at` only when it was null. A stage of `CREATED` becomes `COLLECTING_EVIDENCE`. Any other stage is left as it is.

The claim does not load the attempt with Hibernate inside that transaction. A later flush could write the old in-memory object back over the SQL update. The attempt id is read back with JDBC.

If the incident is still `CREATED`, the claim moves it to `COLLECTING_EVIDENCE` and writes `COLLECTION_STARTED`, unless that audit row is already there or the stage is `INVESTIGATING`.

`AttemptFinisher` runs after the claim commits:

- Stage `INVESTIGATING`: incident `PARTIAL`, error summary `AI analysis is not configured.`, attempt `COMPLETED` at stage `INVESTIGATING`, audit `AI_ANALYSIS_SKIPPED`. No model is called.
- Any other stage: incident `PARTIAL`, error summary `No evidence connectors are registered.`, attempt `COMPLETED` at stage `EVIDENCE_READY`, audit `COLLECTION_COMPLETED`. No report row is inserted.
- A second finish of an attempt that is no longer `RUNNING` does nothing.
- If finish throws, the worker records the attempt as `FAILED`, moves the incident to `FAILED` when it is not already, and stores `The attempt failed before it could finish.` The log line contains the exception class name, not the SQL text.

An expired lease is just a `RUNNING` row whose `lease_until` is in the past. The next claim takes it. The attempt number does not increase. A crash is not a retry.

## 12. What you can see with the AI service stopped

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run
```

```bash
curl -s -D - -o /tmp/incident.json \
  -H 'Content-Type: application/json' \
  -H 'X-Actor: local-dev' \
  -d '{"workloadId":"payment-reconciliation-demo","environment":"test","failureTime":"2026-03-16T08:30:00Z","lookbackMinutes":120,"incidentType":"BATCH_FAILURE"}' \
  http://127.0.0.1:8080/api/incidents/investigate
```

The status line is `202`. Within about a second the same incident's `GET` shows `PARTIAL` and the error summary `No evidence connectors are registered.` `GET .../evidence` is an empty page. `GET .../report` is 404. The OpenAPI page is `http://127.0.0.1:8080/swagger-ui.html`.

`./mvnw -B verify` is the check for this module. It needs Docker, and it does not need `OPENAI_API_KEY`.
