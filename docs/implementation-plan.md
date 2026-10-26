# Saved specification

Saved on 2026-10-26. The specification below is the execution plan as provided. The accepted decisions and execution notes in this header are part of the plan.

Repository facts at save time:

- The monorepo root is this repository, `ai-incident-investigation-platform`. Section 4's `ai-operations-investigator/` tree is relative to that root.
- There are no commits. `origin` is `https://github.com/ganeshpc/ai-incident-investigation-platform` and has no branches.
- Already present, so later work extends these files instead of replacing them: `.gitignore`, `.gitattributes`, `.editorconfig`, `.env.example`, `.envrc`, `AGENTS.md`, `CLAUDE.md`, and a stub `README.md`.
- Application code, ADRs, fixtures, Compose, and tests are not present.
- Local toolchain observed on 2026-10-26: Java 21.0.11 (Temurin), Maven 3.9.11, Python 3.13.3, uv 0.9.24, Node.js 24.12.0, npm 11.6.2, pnpm 10.33.2, Docker 29.1.4, Docker Compose v5.0.1, Git 2.49.0.

## Accepted decisions (2026-10-26)

- Backend line: Spring Boot 4.1.x on Java 21, Maven, and springdoc-openapi 3.x when that springdoc line matches the chosen Boot patch.
- The AI service keeps a replaceable LLM provider. Configuration selects the provider. Automated tests use a fake provider and do not call a live model.
- The provider for real investigations is OpenAI, using ChatGPT models through the official API. Source: [OpenAI developer quickstart](https://developers.openai.com/api/docs/quickstart) and [models](https://developers.openai.com/api/docs/models), read on 2026-10-26.
  - Credential: `OPENAI_API_KEY`, sent only by the Python service as `Authorization: Bearer`. The browser never receives it.
  - Endpoint used by the current quickstart: `POST https://api.openai.com/v1/responses` through the official OpenAI Python SDK (`client.responses.create`).
  - Model id: `gpt-6-luna`. On the [pricing page](https://developers.openai.com/api/docs/pricing) read on 2026-10-26, its standard rate is $0.10 per million input tokens and $0.50 per million output tokens. That is the lowest rate among text models that are still available. `gpt-5-nano` is listed cheaper and is marked deprecated. `gpt-4.1-nano` is listed cheaper on output and is marked shut down. Override with `OPENAI_MODEL` if needed.
  - Reasoning effort is `none`. The [GPT-6 Luna page](https://developers.openai.com/api/docs/models/gpt-6-luna) says the default is `medium`, and reasoning tokens are billed as output. `none` is the lowest-cost setting that page documents. Processing stays on the standard tier.
  - The model does not receive OpenAI built-in tools such as web search, file search, or computer use. It only reasons over the bounded evidence bundle.
  - xAI remains an alternate OpenAI-compatible provider (`XAI_API_KEY`, `https://api.x.ai/v1`). It is not the selected provider.
- New code follows the teaching-comment standard in `AGENTS.md`.
- Git: the existing scaffold and this plan are committed on `master`. Each implementation phase gets its own branch and pull request.

## Accepted execution notes (2026-10-26)

These notes change structure and sequencing. They do not remove a functional requirement.

1. This repository is the monorepo root. Section 4's tree is created here, not inside a nested `ai-operations-investigator/` directory.
2. Build in the order in section 18. Real AutoSys, OpenShift, GitHub, and release adapters come after the synthetic product works. Phase numbers in the body stay as written. Until a real endpoint is verified, that connector's real mode stays `UNCONFIGURED`. A failed real connector does not fall back to fixture data.
3. PostgreSQL, through the Spring Boot backend, is the only system of record. The AI service handles one bounded request at a time. A LangGraph checkpoint database waits until a graph must resume after a process restart.
4. `POST /api/incidents/investigate` returns `202` after the incident row commits. The idempotency header is `Idempotency-Key`. The same key and body return the original incident. The same key with a different body returns `409`. `POST /api/incidents/{id}/retry` opens a new attempt. `GET /api/incidents/{id}/attempts` shows history. A finished investigation stays finished unless the retry explicitly asks to run again. Successful connectors run again only when that request says so. Validation errors use RFC 9457 problem details. An in-process worker claims persisted attempts and uses a lease so a restart can pick up an attempt left in progress.
5. Phase 1 checks that scenario files, timestamps, identifiers, and ground-truth references agree. The idempotent database load arrives once the Flyway tables exist.
6. Scenario 4 history is built by a script with pinned author, committer, and dates. The manifest records the SHAs. The monorepo does not contain a nested `.git` directory.
7. Phase 1 includes one prompt-injection line in a negative log fixture.
8. The local demo has no login. `createdBy` comes from an optional `X-Actor` header and defaults to `local-dev`. Compose binds services to localhost. Hypothesis confidence is `LOW`, `MEDIUM`, or `HIGH`, plus a written explanation.
9. Java packages live under `com.aiops.investigator`. The LLM port is selected with `LLM_PROVIDER`, base URL, model, and API key. Versions are pinned in `docs/toolchain.md` from the latest stable releases that were current when Phase 0 was built. Release candidates are not used.

---

# AI Operations Investigator
## Phase-by-Phase Engineering Implementation Plan

**Document type:** Engineering execution specification for an autonomous coding agent
**Project type:** Independent engineering project with a local UAT-style environment
**Primary goal:** Build an AI-assisted incident investigation platform for enterprise batch-processing and workflow systems
**Initial invocation:** Manual REST API request
**Initial data sources:** Synthetic AutoSys job executions, OpenShift-style application logs, GitHub repository history, release metadata, and operational runbooks
**Primary backend:** Java and Spring Boot
**AI service:** Python, FastAPI, LangGraph, and an LLM provider
**Persistence:** PostgreSQL
**Local runtime:** Docker Compose
**External integrations:** Read-only, connector-based, enabled only after their real interfaces and access requirements have been verified

---

# 1. Instructions to the coding agent

You are acting as a senior software engineer responsible for designing, implementing, testing, documenting, and delivering this project.

Do not treat this document as a list of suggestions. Execute it as a phased implementation plan. Complete one phase at a time, verify the result, and preserve a working application throughout development.

## 1.1 Product objective

Build an AI-assisted investigation platform that helps an engineer investigate a failed batch job or workflow by assembling evidence from multiple systems.

The platform should answer questions such as:

- Which job or workflow failed?
- What happened immediately before the failure?
- Did an upstream dependency fail?
- What does the application log show?
- Is there a relevant exception or validation failure?
- Did the relevant source code change between the last known-good version and the affected version?
- Is there reliable evidence connecting a deployment or release to the failure?
- Which hypotheses are supported, contradicted, or still unverified?
- What should an engineer inspect next?

The system must produce an investigation report containing evidence references and uncertainty, rather than simply asking an LLM to summarize raw logs.

## 1.2 Truthfulness and integration constraints

The project is independent. Use synthetic data and your own demonstration repository.

Follow these rules throughout development:

1. Do not access, reproduce, or claim to use confidential employer code, logs, credentials, release records, or production systems.
2. Do not represent synthetic adapters as real vendor integrations.
3. Do not invent API endpoints, request parameters, response fields, authentication mechanisms, or vendor capabilities.
4. For every real connector, consult official documentation for the relevant product and version.
5. Do not assume that AutoSys job output, historical logs, release records, or pod logs are available just because an API exists.
6. Do not claim that an application has achieved production readiness merely because it runs locally or passes a happy-path test.
7. Do not fabricate benchmark results, reliability percentages, performance numbers, user adoption, or incident-resolution improvements.
8. Do not make production changes, trigger jobs, modify case states, or execute remediation actions.
9. Keep all external integrations read-only.
10. If a required API or capability cannot be verified, document the limitation and implement a simulated adapter with an explicit status such as `UNCONFIGURED` or `SIMULATED`.

## 1.3 How to work through the plan

Before writing code:

- Inspect the existing repository.
- Identify existing language versions, build tools, code conventions, and tests.
- Do not overwrite working code or introduce a new framework without justification.
- Create a short implementation checklist for the current phase.
- Implement the smallest complete increment.
- Run the relevant tests and report the actual result.
- Fix failures before moving forward.
- Update documentation when an implementation decision changes.
- Record architectural decisions in an Architecture Decision Record (ADR) when the choice materially affects the design.

After each phase, provide:

1. What was implemented.
2. Files added or changed.
3. Commands executed.
4. Tests run and their actual results.
5. Known limitations.
6. The next phase's prerequisites.

Never report a command or test as successful unless it was actually executed and its result observed.

---

# 2. Product scope and non-goals

## 2.1 In scope

The first complete version must provide:

- Manual incident submission through a REST API.
- Incident lifecycle management.
- Persistent incident and evidence records.
- A configurable workload registry.
- Connector interfaces for each evidence source.
- Synthetic adapters for every source.
- A deterministic evidence collection pipeline.
- An AI-assisted investigation pipeline.
- Evidence-grounded hypotheses.
- A human-readable investigation report.
- Handling of partial data, timeouts, failed connectors, and missing evidence.
- A small web interface or equivalent report viewer.
- Automated tests, a reproducible synthetic dataset, and local Docker Compose execution.
- A benchmark using seeded incidents with known expected causes.

## 2.2 Explicitly out of scope for the first version

Do not implement these unless a later phase explicitly requires them:

- Automatic detection of incidents.
- AutoSys job execution, restart, force-start, or other control operations.
- Autonomous production remediation.
- Direct modification of Actimize-like case states.
- Kafka or other event streaming infrastructure.
- Kubernetes deployment of the investigator itself.
- A distributed microservice architecture for every connector.
- A vector database before there is a demonstrated retrieval need.
- A generic agent that can execute arbitrary shell commands.
- An autonomous agent that can call any endpoint or access any file.
- An assumption that an unknown RLM product has a particular API.
- Production deployment to an employer's infrastructure.

The objective is to build a bounded, testable investigation workflow, not to build an unnecessarily complex agent platform.

---

# 3. Architecture and technology decisions

## 3.1 Component responsibilities

### A. Spring Boot backend

Spring Boot is the primary application backend.

It owns:

- Incident creation and lifecycle.
- Request validation.
- Workload registry management.
- Investigation orchestration at the application level.
- Connector invocation.
- Evidence normalization and persistence.
- Authorization boundaries for the application.
- Idempotency.
- Audit history.
- Report retrieval.
- Retry requests.
- Communication with the AI service.

The backend must remain usable even if the AI service is unavailable. Users should still be able to create an incident, inspect collection results, and view evidence already gathered.

### B. Python FastAPI AI service

The Python service owns:

- AI investigation orchestration.
- Structured hypothesis generation.
- Evidence-based hypothesis assessment.
- Selection of additional investigation steps from a predefined, bounded set.
- Report generation.
- Validation of the LLM response against a defined schema.

Use LangGraph for a bounded workflow with explicit nodes and transitions.

Do not let the LLM directly access arbitrary vendor systems. It receives a normalized evidence bundle and may request only explicitly supported additional analysis operations.

### C. PostgreSQL

PostgreSQL stores:

- Incidents.
- Investigation attempts.
- Workload registry entries.
- Evidence records.
- Connector collection results.
- Hypotheses.
- Evidence-to-hypothesis relationships.
- Final reports.
- Audit events.
- Evaluation results.

Persist the investigation state before starting long-running operations.

### D. Connectors

Implement connectors behind stable interfaces.

Initial implementations:

- `AutoSysConnector`
- `OpenShiftLogConnector`
- `GitHubConnector`
- `ReleaseConnector`
- `RunbookConnector`

Each source must support a simulated implementation. Real implementations are optional, separately configured adapters.

### E. Frontend

A simple web UI is sufficient.

It should provide:

- Incident submission.
- Incident list and status.
- Investigation progress.
- Evidence inspection.
- Hypothesis review.
- Final report.
- Failed or unavailable connector details.

Do not spend time building an elaborate dashboard before the end-to-end investigation works.

## 3.2 Technology choices

Use the following unless the existing repository or compatibility requirements justify a documented alternative.

| Area | Technology |
|---|---|
| Main backend | Java and Spring Boot |
| Java build | Maven |
| REST API documentation | Springdoc OpenAPI, if compatible with the selected Spring Boot version |
| Persistence | PostgreSQL |
| Database migrations | Flyway |
| Java database access | Spring Data JPA or JDBC where simpler |
| AI service | Python and FastAPI |
| AI workflow | LangGraph |
| Request validation | Pydantic |
| Python dependencies | A lockfile managed by the selected Python dependency tool |
| Testing | JUnit 5, Spring Boot Test, pytest |
| Integration testing | Testcontainers where practical |
| Frontend | React with TypeScript and Vite |
| Local environment | Docker Compose |
| Logs | Structured application logs |
| Version control | Git |
| Continuous integration | GitHub Actions, when repository hosting is configured |

Choose mutually compatible, stable dependency versions at implementation time. Pin versions and commit dependency lockfiles. Do not blindly use the latest major version without checking compatibility.

Useful documentation:

- Spring Boot: https://docs.spring.io/spring-boot/reference/
- Spring Framework: https://docs.spring.io/spring/reference/
- FastAPI: https://fastapi.tiangolo.com/
- LangGraph: https://docs.langchain.com/oss/python/langgraph/overview
- PostgreSQL: https://www.postgresql.org/docs/
- Flyway: https://documentation.red-gate.com/flyway
- Testcontainers: https://testcontainers.com/
- Docker Compose: https://docs.docker.com/compose/
- React: https://react.dev/
- Vite: https://vite.dev/guide/
- GitHub Actions: https://docs.github.com/en/actions

---

# 4. Repository structure

Create a monorepo unless an existing repository already establishes another convention.

Use this structure as the starting point:

```text
ai-operations-investigator/
├── README.md
├── CONTRIBUTING.md
├── SECURITY.md
├── .gitignore
├── .env.example
├── compose.yaml
│
├── docs/
│   ├── architecture.md
│   ├── data-model.md
│   ├── api-contracts.md
│   ├── integration-matrix.md
│   ├── threat-model.md
│   ├── evaluation-methodology.md
│   ├── runbook.md
│   └── adr/
│
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/
│       └── test/
│
├── ai-service/
│   ├── pyproject.toml
│   ├── lockfile
│   ├── app/
│   │   ├── main.py
│   │   ├── api/
│   │   ├── schemas/
│   │   ├── graph/
│   │   ├── prompts/
│   │   ├── evidence/
│   │   └── evaluation/
│   └── tests/
│
├── frontend/
│   ├── package.json
│   ├── src/
│   └── tests/
│
├── fixtures/
│   ├── scenarios/
│   ├── autosys/
│   ├── openshift/
│   ├── github/
│   ├── releases/
│   └── runbooks/
│
├── scripts/
│   ├── seed-demo-data.sh
│   ├── reset-demo-data.sh
│   └── run-evaluation.sh
│
└── evaluation/
    ├── scenario-catalog.yaml
    ├── ground-truth/
    └── results/
```

This is a proposed structure, not a requirement to create empty files immediately. Add files as the corresponding phase begins.

Do not commit:

- API keys.
- Personal access tokens.
- Database passwords.
- Local `.env` files.
- Production logs.
- Confidential employer information.
- Real customer or account data.

Use synthetic fixtures only.

---

# 5. Core domain model

Implement the domain model before building the AI workflow. The investigation must have stable identifiers and clear ownership of its state.

## 5.1 Incident

An incident represents one investigation request.

Required fields:

- `id`: UUID.
- `title`: human-readable description.
- `workloadId`: reference to the workload registry.
- `environment`: for example, `test` or `uat`.
- `incidentType`: for example, `BATCH_FAILURE`, `WORKFLOW_FAILURE`, or `UNKNOWN`.
- `status`.
- `failureTime`: instant in UTC.
- `lookbackMinutes`.
- `autosysJobName`, if relevant.
- `jobRunId`, if available and verified.
- `createdAt`.
- `updatedAt`.
- `createdBy`, where authentication is implemented.
- `idempotencyKey`, where applicable.
- `errorSummary`, optional.
- `investigationAttemptCount`.

Use an `Instant` or an equivalent UTC-aware type in Java. Never interpret timestamps as local time without an explicit rule.

Suggested incident statuses:

- `CREATED`
- `COLLECTING_EVIDENCE`
- `EVIDENCE_READY`
- `INVESTIGATING`
- `REPORT_READY`
- `PARTIAL`
- `FAILED`

Define the permitted state transitions explicitly. A retry must not silently move a completed incident into an unrelated state.

## 5.2 Investigation attempt

An incident can have more than one attempt.

Store:

- Attempt ID.
- Incident ID.
- Attempt number.
- Start and completion times.
- Current stage.
- Status.
- Failure reason, if applicable.
- Retry relationship.
- Model and prompt version used for AI analysis, when applicable.

This allows an engineer to understand whether the original investigation failed and whether a later attempt succeeded.

## 5.3 Workload registry

The workload registry maps a logical workload to the external systems where its evidence resides.

Example synthetic record:

```yaml
workloadId: payment-reconciliation-demo
displayName: Payment Reconciliation Demo
environment: test

autosys:
  jobName: DEMO_RECON_JOB

openshift:
  namespace: operations-demo
  workloadName: reconciliation-worker
  labels:
    app: reconciliation-worker

github:
  owner: demo-org
  repository: reconciliation-worker

release:
  applicationId: reconciliation-demo

runbooks:
  - runbooks/reconciliation-failure.md
```

All values above are fictional.

Support incomplete mappings. A missing GitHub repository should not prevent AutoSys and OpenShift evidence collection.

## 5.4 Evidence record

An evidence record represents an observation retrieved from a source.

Required fields:

- Evidence ID.
- Incident ID.
- Source system.
- Logical workload ID.
- Evidence type.
- Event time, if known.
- Retrieval time.
- Source reference.
- Structured attributes.
- Sanitized content or a reference to stored content.
- Content hash, where useful.
- Collection attempt ID.
- Synthetic/real provenance.
- Redaction status.

Example source systems:

- `AUTOSYS`
- `OPENSHIFT`
- `GITHUB`
- `RELEASE`
- `RUNBOOK`

Example evidence types:

- `JOB_RUN`
- `JOB_DEFINITION`
- `DEPENDENCY`
- `APPLICATION_LOG`
- `COMMIT`
- `CODE_DIFF`
- `RELEASE_RECORD`
- `RUNBOOK_EXCERPT`

Keep large logs out of ordinary API responses. Store the required sanitized excerpt or a file/object reference and expose a bounded excerpt for the report.

## 5.5 Collection result

Each connector invocation must produce a result containing:

- Source system.
- Status.
- Start and end time.
- Evidence IDs produced.
- Warnings.
- Error category, if applicable.
- Whether retry is appropriate.
- Safe diagnostic message.

Use these collection statuses:

- `SUCCESS`
- `PARTIAL`
- `UNAVAILABLE`
- `UNCONFIGURED`
- `FAILED`

The distinction between `UNAVAILABLE` and `UNCONFIGURED` is important. A real integration may be configured incorrectly, or a source may simply not be configured yet.

## 5.6 Hypothesis

A hypothesis is a candidate explanation, not a proven root cause.

Fields:

- Hypothesis ID.
- Incident ID.
- Summary.
- Category.
- Supporting evidence IDs.
- Contradicting evidence IDs.
- Missing evidence.
- Confidence level.
- Explanation.
- Recommended next check.
- Validation status.

Suggested validation statuses:

- `SUPPORTED`
- `CONTRADICTED`
- `INCONCLUSIVE`
- `NEEDS_MORE_EVIDENCE`

Do not represent a model-generated confidence score as a calibrated probability unless calibration has actually been evaluated.

---

# 6. Phase 0 — Project bootstrap and architecture decisions

## Objective

Establish a clean, reproducible development environment and agree on the boundaries before implementation becomes difficult to change.

## Step 1: Inspect the environment

Check:

- Java version.
- Maven version.
- Python version.
- Node.js and package manager versions.
- Docker and Docker Compose availability.
- Git version.

Document the versions used for development.

Do not assume that a local executable or service is available just because the plan mentions it.

## Step 2: Initialize the repository

Create the monorepo structure, `.gitignore`, `.env.example`, and initial README.

The README must explain:

- What the project does.
- What it does not do.
- Why synthetic data is used.
- The major components.
- The planned local setup.
- How to run tests.

## Step 3: Write the initial ADRs

Create concise Architecture Decision Records for:

1. Spring Boot as the main API backend.
2. Python as the separate AI orchestration service.
3. PostgreSQL as the primary database.
4. Connector interfaces and synthetic adapters.
5. Manual triggering before automatic incident detection.
6. Evidence-grounded AI rather than unconstrained agent execution.

Each ADR should state the context, decision, alternatives considered, and consequences.

## Step 4: Define development standards

Set up:

- Java formatting and static analysis.
- Python linting and type checking.
- Frontend formatting and linting.
- Test commands.
- Configuration conventions.
- Logging conventions.
- Secret-management conventions.

## Exit criteria

- Repository builds or has a documented initial build state.
- The project structure is committed.
- Architecture decisions are documented.
- No real credentials or employer data exist in the repository.
- Another developer can understand how the repository is intended to work.

---

# 7. Phase 1 — Build realistic synthetic operational data

## Objective

Create reproducible synthetic incidents that resemble actual enterprise batch and workflow failures. The AI investigation must not begin until the deterministic data collection and expected answers are well defined.

Create four primary scenarios. Each scenario must include source records, timestamps, identifiers, a known causal explanation, and evidence that supports the explanation.

## Scenario 1: Batch fails because an input record is invalid

### Situation

A scheduled reconciliation batch processes a synthetic input file. One record is missing a required transaction identifier. The parser rejects the record, and the job exits unsuccessfully.

### Data to create

1. An AutoSys-style job definition fixture for `DEMO_RECON_JOB`.
2. A successful run from a previous day.
3. A failed run with a unique run identifier and a non-zero exit status.
4. Application logs containing:
   - Job startup.
   - Input file selection.
   - Record processing progress.
   - A validation exception.
   - A stack trace.
   - Job termination.
5. A synthetic input file with a malformed record.
6. Java source code for the parser and validation logic.
7. A runbook explaining the required input fields and expected remediation.
8. Release metadata identifying the deployed application revision.

Use consistent timestamps and job/run identifiers across fixtures.

### Expected diagnosis

The likely cause is invalid input data that violates a required field constraint.

The report must cite:

- The failed job execution.
- The validation error in the logs.
- The malformed synthetic input record.
- The relevant parser or validation code.
- The runbook rule.

It should distinguish the immediate failure from any downstream jobs that subsequently fail.

### Negative test

Include another log that contains a generic warning but is unrelated to the failure. The investigator should not select the warning as the root cause merely because it is easy to find.

## Scenario 2: Downstream job blocked by an upstream failure

### Situation

A synthetic batch pipeline contains an upstream data-preparation job and a downstream reconciliation job. The upstream job fails, preventing the downstream job from completing its expected processing.

### Data to create

- Definitions for both jobs.
- An explicit dependency relationship in the synthetic scheduler data.
- An upstream failed execution.
- A downstream execution or scheduler record showing the resulting blocked, skipped, or unsuccessful state, depending on the scenario being modeled.
- Logs explaining the upstream failure.
- A runbook describing the dependency chain.

Do not invent a universal AutoSys status value. Use status labels and semantics supported by the fixture's documented scenario. If implementing a real connector, map actual vendor responses to the internal model.

### Expected diagnosis

The upstream failure is the primary cause. The downstream state is a consequence.

The report must distinguish:

- The original failure.
- The dependent job's resulting state.
- The evidence linking the two jobs.
- Any uncertainty caused by missing scheduler metadata.

### Negative test

Provide a downstream failure that occurs at approximately the same time but has no dependency relationship. The investigator must not infer causality from timestamps alone.

## Scenario 3: Workflow or case transition rejected by a validation rule

### Situation

A synthetic case-management workflow rejects a transition because a required field or prerequisite is missing.

This scenario models a generic enterprise workflow or an Actimize-like process. It is not a recreation of a proprietary internal system.

### Data to create

- A synthetic workflow definition.
- Initial case state.
- Requested transition.
- Required field or transition precondition.
- Application logs recording the rejected transition.
- Java plugin or service code implementing the validation.
- A runbook describing the required fields and permitted transition.
- A successful example for comparison.

### Expected diagnosis

The transition is rejected because a required validation condition is not satisfied.

The report must cite the specific validation failure and relevant rule. It must not infer that the entire workflow engine is unavailable when the evidence shows an application-level rejection.

### Negative test

Include an unrelated infrastructure warning in the same log window. It must not override the more specific validation evidence.

## Scenario 4: Regression associated with a code change

### Situation

A code change modifies input parsing or validation behavior. The affected application revision produces a failure that was not present in the previous version.

### Data to create

- A small Git repository containing the synthetic application.
- A known-good commit.
- A subsequent commit containing the seeded regression.
- A reproducible failing input.
- Logs from the failing run.
- Release metadata identifying the revision used in the synthetic deployment.
- A successful execution using the known-good revision.
- A runbook with expected behavior.

### Expected diagnosis

The changed code path is a plausible cause of the failure when the deployed revision, diff, logs, and reproducible test support that conclusion.

### Critical requirement

A commit timestamp alone does not prove that a change was deployed or caused the failure. The fixture must include explicit synthetic deployment metadata connecting the deployed revision to the failing workload.

### Negative test

Create another commit close to the incident time that changes an unrelated file. The report must not identify it as causal without supporting evidence.

## Scenario fixture format

Each scenario should have a manifest, source fixtures, and ground truth.

Example:

```yaml
scenarioId: scenario-001-invalid-input
description: Missing required transaction identifier
workloadId: payment-reconciliation-demo
failureTime: "2026-10-26T08:30:00Z"

expected:
  primaryCauseCategory: INVALID_INPUT
  requiredEvidenceTypes:
    - JOB_RUN
    - APPLICATION_LOG
    - RUNBOOK_EXCERPT
  requiredClaims:
    - input_record_violates_required_field
  forbiddenClaims:
    - database_outage_proven
```

The fixture must refer to actual records present in the scenario. Do not let the ground-truth manifest refer to evidence that does not exist.

## Exit criteria

- Four complete scenarios exist.
- Every scenario has a known explanation and expected evidence.
- Timestamps and identifiers are internally consistent.
- Each scenario has at least one negative test.
- A script can load the fixtures repeatedly without creating duplicate records.
- A deterministic test can locate the evidence needed to explain each scenario without calling an LLM.

---

# 8. Phase 2 — Implement the Spring Boot backend

## Objective

Build the application's reliable system-of-record layer before introducing AI.

## Step 1: Initialize the Spring Boot service

Create the Maven project with the dependencies required for:

- Spring Web.
- Bean validation.
- Database access.
- PostgreSQL.
- Flyway.
- Health checks.
- Testing.
- OpenAPI documentation, if compatible.

Avoid adding messaging, caching, or distributed tracing infrastructure until there is a concrete requirement.

## Step 2: Implement database migrations

Create versioned Flyway migrations for:

- `incidents`
- `investigation_attempts`
- `workload_registry`
- `evidence_records`
- `collection_results`
- `hypotheses`
- `hypothesis_evidence`
- `investigation_reports`
- `audit_events`

Add primary keys, foreign keys, appropriate uniqueness constraints, and indexes.

At minimum, index incident lookup and evidence retrieval by incident ID. Add workload and event-time indexes if the query patterns justify them.

Do not store the entire investigation in one opaque JSON field. JSON can be useful for source-specific attributes, but important entities and relationships should remain queryable.

## Step 3: Implement incident creation

Create:

`POST /api/incidents/investigate`

The request should include:

- Workload ID.
- Environment.
- Failure time.
- Lookback duration.
- Optional scheduler job name.
- Optional job-run ID.
- Optional incident type.
- Optional idempotency key through a documented request header.

Validate the workload and request before persisting the incident.

Return an incident ID and the accepted state. Do not keep the HTTP connection open for the entire investigation.

For the first version, a persistent database-backed state machine with a bounded worker pool is sufficient. Do not add Kafka merely to dispatch local investigation tasks.

## Step 4: Implement retrieval endpoints

Create:

- `GET /api/incidents`
- `GET /api/incidents/{incidentId}`
- `GET /api/incidents/{incidentId}/evidence`
- `GET /api/incidents/{incidentId}/report`

Add pagination for lists. Return clear `404` responses for unknown IDs and appropriate validation errors for malformed requests.

## Step 5: Implement state transitions and retries

Define allowed transitions in one place. Reject invalid transitions rather than silently overwriting status.

For retries:

- Create a new attempt.
- Preserve the earlier attempt's history.
- Avoid duplicate evidence records.
- Retry only failed or eligible stages.
- Do not re-run successful collection stages unless explicitly requested.

Implement idempotency so repeated requests do not create duplicate active investigations.

## Step 6: Add auditing

Record events such as:

- Incident created.
- Collection started.
- Collection completed.
- Collection partially failed.
- AI analysis started.
- Report generated.
- Retry requested.
- Report rejected due to invalid evidence references.

Audit events should identify the operation, timestamp, incident, and result without storing credentials or unnecessary sensitive data.

## Exit criteria

- Database migrations execute from a clean database.
- Incident creation and retrieval work.
- Validation errors are meaningful.
- Duplicate requests are handled predictably.
- State transitions are tested.
- A failed attempt can be inspected and retried without erasing history.
- The service can run without the AI service being available.

---

# 9. Phase 3 — Implement connector interfaces and synthetic adapters

## Objective

Create a stable evidence collection architecture that can support multiple systems without coupling the investigation logic to vendor response formats.

## Step 1: Define connector contracts

Each connector should expose a small, typed interface.

A conceptual Java interface is:

```java
public interface EvidenceConnector {

    SourceSystem sourceSystem();

    CollectionResult collect(
        CollectionRequest request
    );
}
```

Use asynchronous execution where justified by network I/O, but keep concurrency bounded.

The collection request should contain:

- Incident ID.
- Workload configuration.
- Environment.
- Failure time.
- Lookback window.
- Optional job/run identifiers.
- Connector-specific safe configuration.

Do not pass arbitrary user-controlled URLs, shell commands, or unrestricted file paths through the request.

## Step 2: Implement synthetic AutoSys connector

Read the synthetic scheduler fixtures.

Support:

- Job definition lookup.
- Run history lookup.
- Failed run identification.
- Dependency relationship lookup.
- Explicit missing-record behavior.

The adapter must emit normalized evidence rather than raw fixture-specific objects.

It must not claim to call AutoSys.

## Step 3: Implement synthetic OpenShift connector

Read synthetic pod metadata and timestamped logs.

Support:

- Workload-to-pod mapping.
- Container identification.
- Time-window filtering.
- Log excerpt generation.
- Timestamp normalization.
- Missing or truncated log handling.

Simulate unavailable pods and expired logs through fixtures.

## Step 4: Implement synthetic GitHub connector

Use a dedicated local demo repository or committed fixture files.

Support:

- Repository identification.
- Known-good and affected revision metadata.
- Commit metadata.
- File-change metadata.
- Relevant diff retrieval.
- Missing revision handling.

The adapter must distinguish a local Git fixture from a real GitHub API call.

## Step 5: Implement synthetic release connector

Load release records from fixtures.

A release record should contain only fields actually represented in the synthetic dataset, such as:

- Application identifier.
- Environment.
- Release identifier.
- Deployment status.
- Artifact or commit reference.
- Deployment timestamp.

If the fixture does not establish the deployed revision, the connector must not manufacture one.

## Step 6: Implement runbook connector

Start with Markdown files in the repository.

Support:

- Runbook lookup by workload and incident category.
- Retrieval of relevant sections.
- Source path and section metadata.
- Missing runbook behavior.

Use straightforward file lookup and text search initially. Do not introduce a vector database before the runbook corpus demonstrates a need for semantic retrieval.

## Step 7: Implement collection orchestration

For a single incident:

1. Load the workload registry entry.
2. Determine which connectors are configured.
3. Invoke the applicable connectors.
4. Enforce timeouts.
5. Persist results and evidence.
6. Record warnings and unavailable sources.
7. Continue if an independent connector fails.
8. Mark the incident as partial if evidence collection is incomplete.

Do not treat every connector failure as fatal. A GitHub outage should not erase the AutoSys run information already retrieved.

## Exit criteria

- All connectors have a simulated implementation.
- The same normalized evidence model is used across sources.
- Connector failures are represented explicitly.
- The system can collect evidence from one source while another source is unavailable.
- Repeated collection does not create uncontrolled duplicate records.
- The application can produce a deterministic evidence bundle without AI.

---

# 10. Phase 4 — Add real integration adapters only where verifiable

## Objective

Introduce real, read-only integration adapters without weakening the synthetic demonstration or assuming that every source exposes every required capability.

The application must continue to work when real integrations are disabled.

## 10.1 AutoSys Workload Automation 12.1

### Supported integration approach

Use the AutoSys Web Server (AEWS) REST interface documented by Broadcom.

Official documentation:

- AutoSys 12.1 REST API and Swagger documentation: https://knowledge.broadcom.com/external/article/445384/rest-api-and-swagger-documentation-for.html
- AutoSys 12.1 open REST API reference: https://techdocs.broadcom.com/us/en/ca-enterprise-software/intelligent-automation/autosys-workload-automation/12-1-01/reference/ae-web-services/autosys-workload-automation-open-rest-apis.html

Broadcom documents the AEWS endpoint family and an interactive Swagger interface. The default port commonly documented is `9443`, but the actual host, port, path, TLS setup, and authentication must be verified for the deployment. See the official reference above.

### Implementation procedure

1. Read the version-specific Swagger/OpenAPI documentation.
2. Record the actual base URL and authentication mechanism in local development documentation.
3. Identify the supported read operations needed for job definitions and job-run information.
4. Inspect actual response schemas and filtering syntax.
5. Implement a typed HTTP client.
6. Add connection and read timeouts.
7. Handle authentication failures, rate or server errors, pagination if supported, and malformed responses.
8. Map actual response fields into the normalized internal evidence model.
9. Test with a mock HTTP server using response examples derived from official documentation.
10. Enable the adapter only when the necessary configuration and authorization are present.

### Known documented endpoint family

Broadcom support material documents querying job-run information through the AEWS resource:

`GET /AEWS/job-run-info`

For example, the official Broadcom article on querying jobs by execution time demonstrates filters using `runStartTime`. The precise supported filters and response fields must be taken from the version-specific documentation.

Reference: https://knowledge.broadcom.com/external/article/451923/how-to-query-autosys-to-find-all-jobs-th.html

Do not assume this example covers every historical run, job definition, dependency, or job-output use case.

### What to verify before implementing more

- Whether the deployed AutoSys 12.1 installation has AEWS enabled.
- Whether the account has permission to read the required job and run information.
- Whether the required run-history retention window is available.
- Which supported API operations expose dependency details.
- Whether any needed job output or spool information is accessible through a documented interface.
- Whether the response contains a reliable run identifier.
- How timestamp filters and pagination work in the installed version.

If the API does not expose a required field, do not invent an endpoint for it. Record the gap, check the official product documentation for another supported mechanism, and keep the connector partial until a verified solution exists.

**Read-only requirement:** do not implement AutoSys operations that hold, release, force-start, or otherwise modify jobs.

## 10.2 OpenShift pod logs

### Supported integration approach

Use the Kubernetes/OpenShift API to retrieve pod logs when the application writes to container stdout/stderr and the necessary permissions and retention conditions are satisfied.

Official references:

- OpenShift pod API documentation: https://docs.redhat.com/en/documentation/openshift_container_platform/4.20/html/workloads_apis/pod-v1
- Kubernetes pod API reference: https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/
- Kubernetes RBAC documentation: https://kubernetes.io/docs/reference/access-authn-authz/rbac/

The documented pod-log subresource follows this path pattern:

`GET /api/v1/namespaces/{namespace}/pods/{name}/log`

The actual base URL is the cluster's API endpoint. Use the version-appropriate documentation to verify supported query parameters and behavior.

### Implementation procedure

1. Determine the target OpenShift version.
2. Verify the API server endpoint and certificate trust.
3. Establish the supported authentication mechanism.
4. Obtain a read-only identity.
5. Verify the minimum RBAC permissions required to list the relevant pods and read their logs.
6. Implement pod discovery using configured namespaces and workload labels.
7. Retrieve logs for a bounded time window using supported query parameters.
8. Parse timestamps and retain the pod/container source identity.
9. Handle restarts, deleted pods, empty logs, truncation, and authorization errors.
10. Persist normalized excerpts and retrieval metadata.
11. Test with a mock server and synthetic logs before enabling a real endpoint.

### Important limitations

The pod-log API does not guarantee access to every historical log.

Verify:

- Whether logs are available only for currently existing pods.
- Whether previous-container logs are accessible after a restart.
- Whether the application writes logs to stdout/stderr or only to internal files.
- Whether a centralized logging system retains the historical records required for investigation.

If a configured centralized logging platform is the authoritative source for historical logs, evaluate its documented search API as a separate connector. Do not assume that a particular logging platform is present.

## 10.3 GitHub

### Supported integration approach

Use the GitHub REST API for repository metadata, commits, and relevant code changes.

Official documentation:

- Commit REST API: https://docs.github.com/en/rest/commits/commits
- Repository contents API: https://docs.github.com/en/rest/repos/contents
- Authentication: https://docs.github.com/en/rest/authentication/authenticating-to-the-rest-api

The documented commit-comparison operation is:

`GET /repos/{owner}/{repo}/compare/{basehead}`

Use the exact documented path and request parameters from the current GitHub API documentation. Do not add undocumented query parameters.

### Implementation procedure

1. Create a synthetic demo repository.
2. Add a known-good commit and a commit containing the seeded regression.
3. Record the commit SHA and affected file in the scenario fixture.
4. Implement a read-only GitHub REST client.
5. Use a narrowly scoped token or GitHub App configuration appropriate to the repository and required read operations.
6. Retrieve commit comparison data for the relevant base and affected revisions.
7. Parse the returned changed-file metadata and available patch information.
8. Enforce response-size limits and handle binary files or missing patches.
9. Persist the source reference, commit SHAs, file paths, and relevant excerpts.
10. Verify that the report cites the correct diff.

A GitHub compare response does not prove that a particular commit was deployed. Obtain the deployed revision from a trustworthy release/deployment record or explicitly mark the deployed revision as unknown.

## 10.4 Release management (RLM)

The RLM product has not been identified. Do not implement a purported RLM API in this phase.

Instead:

1. Keep `ReleaseConnector` as a stable interface.
2. Continue using synthetic release records.
3. Document which release fields the investigation needs.
4. When the product is identified, locate its official documentation.
5. Verify whether it provides a supported REST API, webhook, CLI, export, or another authorized read-only mechanism.
6. Implement only the documented capabilities that are actually needed.
7. Record unsupported fields as unavailable rather than inferring them.

Minimum useful release information includes the application/environment, release identifier, deployment status, deployment timestamp, and exact artifact or commit revision when available.

## 10.5 Configuration policy

Use explicit configuration flags, for example:

- `autosys.mode=simulated`
- `openshift.mode=simulated`
- `github.mode=simulated`
- `release.mode=simulated`

These are application-defined configuration keys, not vendor API parameters.

When a connector is configured for real access, require its verified connection settings and credentials through environment variables or an approved secret-management mechanism.

Do not silently fall back from a failed real integration to synthetic data. That would create a misleading investigation report.

## Exit criteria

- Every enabled real connector has an official documentation reference.
- Its authentication and required permissions are documented.
- Its response mapping is tested.
- The connector performs only read operations.
- An unconfigured connector returns a clear status.
- No connector claims success when it returns fixture data instead of real data.
- The application still runs fully in simulated mode.

---

# 11. Phase 5 — Build the deterministic investigation pipeline

## Objective

Build a reliable evidence-based investigation pipeline before adding LLM reasoning.

The pipeline should already organize the facts and show the relevant timeline. AI should improve interpretation, not compensate for poor data collection.

## Step 1: Construct the investigation context

For every incident, create an internal context object containing:

- Incident metadata.
- Workload configuration.
- Scheduler run records.
- Dependency records.
- Application log excerpts.
- Source-code changes.
- Release records.
- Runbook excerpts.
- Collection statuses and warnings.

Every evidence item must have a stable evidence ID.

## Step 2: Normalize timestamps

Use UTC internally.

When a source returns a local timestamp without a timezone, do not guess the timezone. Preserve the original timestamp and mark the normalized event time as unknown until the source's timezone convention is configured.

## Step 3: Build the evidence timeline

Sort events by normalized event time when available.

Display:

- Event time.
- Source.
- Workload.
- Event type.
- Short summary.
- Evidence ID.
- Whether the time is exact, approximate, or unknown.

Do not make causal conclusions based only on timeline proximity.

## Step 4: Extract deterministic signals

Implement bounded parsers for:

- Error and exception markers.
- Java stack traces.
- Job exit status.
- Failed dependency references.
- Input validation errors.
- Workflow transition rejections.
- Release and commit identifiers.

Preserve the original evidence. Parser output should reference the original record instead of replacing it.

## Step 5: Build correlation rules

Use explicit rules such as:

- Exact workload registry mapping.
- Matching job-run identifiers.
- Explicit dependency relationships.
- Matching release/deployed commit identifiers.
- Matching application/container identity.
- Relevant time windows.

Do not treat identical names or close timestamps as sufficient proof of a relationship.

## Step 6: Generate a deterministic preliminary report

Before invoking the LLM, generate a basic report with:

- Observed failure.
- Evidence collected.
- Important timeline events.
- Missing evidence.
- Candidate failure categories based on deterministic rules.
- Reasons why each category may or may not apply.

This report is the baseline against which AI-assisted investigation will be evaluated.

## Exit criteria

- All four synthetic scenarios produce a useful preliminary report.
- Correlation rules are tested.
- The report clearly separates observations from inferred relationships.
- Missing or conflicting timestamps do not crash the pipeline.
- No LLM is required to create the preliminary report.

---

# 12. Phase 6 — Implement the AI investigation service

## Objective

Use an LLM to reason over collected evidence, compare hypotheses, identify gaps, and produce an evidence-backed report.

The LLM must not invent missing source data or independently execute unrestricted tool calls.

## Step 1: Implement the FastAPI service

Create a typed request schema containing:

- Incident ID.
- Workload ID.
- Incident summary.
- Bounded evidence bundle.
- Collection status summary.
- Investigation limits.

Create a typed response schema containing:

- Report summary.
- Observed facts.
- Hypotheses.
- Supporting evidence IDs.
- Contradicting evidence IDs.
- Missing evidence.
- Confidence explanation.
- Recommended next checks.
- Limitations.

Validate requests and responses with Pydantic.

Do not allow arbitrary user input to choose a filesystem path or remote endpoint.

## Step 2: Design the LangGraph workflow

Use a bounded graph with nodes similar to:

1. `load_context`
2. `summarize_evidence`
3. `generate_hypotheses`
4. `validate_evidence_references`
5. `assess_hypotheses`
6. `identify_gaps`
7. `generate_report`
8. `validate_report`
9. `finish`

These are internal workflow node names, not vendor APIs.

Each node should have a narrow responsibility and a typed input/output contract.

## Step 3: Implement hypothesis generation

Ask the model to generate a small, bounded number of candidate explanations.

Each hypothesis must contain:

- A concise explanation.
- The expected evidence that would support it.
- Evidence that contradicts it, if any.
- Missing evidence needed to confirm it.
- A suggested next check.

Examples of candidate categories include:

- Invalid input.
- Upstream dependency failure.
- Application validation failure.
- Database or external dependency failure, if evidence supports it.
- Code regression.
- Insufficient evidence.

The model must be permitted to return “insufficient evidence.”

## Step 4: Validate all evidence references

Before storing a hypothesis:

1. Extract the evidence IDs it references.
2. Check every ID against the evidence records belonging to the incident.
3. Reject or remove references that do not exist.
4. Ensure that supporting and contradicting evidence are not silently conflated.
5. Mark unsupported claims as invalid or request a bounded regeneration.
6. Fail safely if the report cannot be validated.

Do not let the LLM fabricate IDs or cite a different incident's evidence.

## Step 5: Assess hypotheses

Use deterministic checks alongside model reasoning.

Examples:

- A dependency-failure hypothesis requires evidence of a relevant dependency relationship and an upstream failure.
- A code-regression hypothesis requires a relevant source change and evidence about the deployed revision, or an explicit statement that deployment mapping is unknown.
- A validation-failure hypothesis should reference a specific validation message or rule.
- An infrastructure-failure hypothesis should not be accepted merely because a generic timeout appears in an unrelated log.

The system should be able to say that a hypothesis is plausible but unconfirmed.

## Step 6: Handle untrusted log content

Logs, source code, commit messages, runbooks, and external records may contain instructions that look like prompts.

Treat all retrieved content as untrusted evidence, not instructions to the model.

The model must not obey text inside a log that asks it to reveal secrets, alter its task, call arbitrary URLs, or execute commands.

Do not pass credentials or unnecessary environment configuration into the model context.

## Step 7: Enforce budgets and timeouts

Configure:

- Maximum evidence count.
- Maximum text size per evidence item.
- Maximum total prompt size.
- Maximum number of hypotheses.
- Maximum model calls per investigation.
- Request timeout.
- Retry limit.
- Maximum investigation duration.

If a limit is reached, produce a partial report and explain the limitation.

## Step 8: Persist investigation state

Use LangGraph checkpointing when it provides practical value for resumable multi-step work. Do not assume in-memory checkpointing is durable across restarts.

Official references:

- LangGraph persistence: https://docs.langchain.com/oss/python/langgraph/persistence
- LangGraph checkpoint reference: https://reference.langchain.com/python/langgraph/checkpoints

For the initial local implementation, a bounded workflow with durable application-level stage state may be sufficient. Add a persistent LangGraph checkpointer if it is needed for resuming graph execution after a process interruption.

Do not store huge raw logs or secrets in checkpoints.

## Exit criteria

- The AI service returns schema-valid output.
- Every cited evidence ID exists.
- Unsupported claims are rejected or marked uncertain.
- The model can abstain.
- Prompt injection in synthetic logs does not change the task or trigger external actions.
- Investigation limits are enforced.
- A failed AI request does not erase collected evidence.

---

# 13. Phase 7 — Integrate the backend, AI service, and report UI

## Objective

Provide a complete user journey from a manually submitted incident to an inspectable report.

## Step 1: Integrate the services

The Spring Boot backend should:

1. Accept the incident request.
2. Persist the incident and attempt.
3. Run evidence collection.
4. Persist evidence and collection results.
5. Build a bounded evidence bundle.
6. Call the AI service.
7. Validate and persist the returned report.
8. Update the incident state.

Use connection timeouts and clear error handling for AI service calls.

If the AI service fails, preserve the deterministic preliminary report.

## Step 2: Implement the report API

The report should expose structured sections rather than a single unstructured text field.

Include:

- Incident overview.
- Investigation status.
- Observed failure.
- Timeline.
- Root-cause hypotheses.
- Supporting evidence.
- Contradicting evidence.
- Missing evidence.
- Recommended next checks.
- Connector status summary.
- Report limitations.

## Step 3: Build the UI

Implement the minimum screens:

### Incident submission

Fields:

- Workload.
- Environment.
- Failure timestamp.
- Lookback window.
- Optional job name.
- Optional job-run ID.

Validate required fields and show understandable errors.

### Incident list

Display:

- Incident ID.
- Workload.
- Failure time.
- Current status.
- Creation time.
- Whether the report is complete or partial.

### Incident detail

Display the investigation stages and connector outcomes. Avoid showing a misleading success indicator while collection is still in progress.

### Evidence view

Show source, timestamp, evidence ID, source reference, excerpt, and collection status.

### Report view

Show the leading hypothesis, alternatives, supporting and contradictory evidence, and next checks. Allow a reviewer to inspect each evidence item.

## Step 4: Add operator-friendly errors

Examples:

- “AutoSys connector is not configured.”
- “OpenShift log retrieval failed due to an authorization error.”
- “No relevant GitHub changes were found for the configured revisions.”
- “The release record did not identify the deployed commit.”
- “The report is partial because historical logs were unavailable.”
- “AI analysis failed; deterministic evidence remains available.”

Never display fake success messages.

## Exit criteria

- A user can submit an incident and retrieve its report.
- The UI distinguishes collection from analysis.
- Evidence links work.
- Partial results are visible.
- The application remains useful if the AI service is unavailable.
- The interface does not imply that synthetic evidence came from a real enterprise system.

---

# 14. Phase 8 — Reliability, security, and failure recovery

## Objective

Demonstrate that the system behaves predictably when dependencies fail, data is incomplete, and requests are repeated.

## 14.1 Connector reliability

Implement and test:

- Connection timeouts.
- Read timeouts.
- Bounded retries for transient failures.
- No retries for permanent validation or authorization errors unless configuration changes.
- Pagination where the documented source API supports it.
- Response-size limits.
- Deduplication.
- Partial collection.
- Clear failure categories.

Do not retry indefinitely. Use bounded backoff and record each attempt.

## 14.2 Idempotency and recovery

Test the following:

- The same incident request is submitted twice.
- The backend restarts during collection.
- A connector succeeds but the AI service fails.
- The AI service succeeds but report persistence fails.
- One connector times out while another succeeds.
- A retry is submitted for an already completed investigation.
- The database is temporarily unavailable.

Define the expected state and recovery behavior for each case.

Use transactions for database consistency. Do not attempt to hold one database transaction open across long-running external calls.

## 14.3 Security

Implement:

- Secret configuration outside source control.
- Read-only integration credentials.
- Least-privilege access.
- Input validation.
- Safe error messages.
- Log and prompt redaction.
- Bounded evidence payloads.
- Restriction of connector targets to configured systems.
- Protection against server-side request forgery when real HTTP integrations are enabled.
- Authentication and authorization for non-local deployments.

Do not add complex authentication solely for a local demo if it delays core functionality, but do not expose an unauthenticated service to a public network.

## 14.4 Data handling

Use synthetic data by default.

For future real integrations, document:

- Data classification.
- Retention requirements.
- Sensitive fields to redact.
- Who can access evidence and reports.
- Whether evidence is sent to an external LLM provider.
- How reports and checkpoints are deleted.
- What is written to application logs.

## 14.5 Observability of the investigator

Instrument the investigator itself with structured logs and basic operational metrics.

Useful metrics include:

- Investigation duration.
- Collection duration by source.
- Connector success/partial/failure count.
- AI request duration.
- AI request failure count.
- Evidence count per investigation.
- Report validation failures.
- Retry count.

These metrics describe the investigator, not the original enterprise workload. Do not claim to have reconstructed production metrics from text logs.

## Exit criteria

- All failure scenarios have automated tests.
- Retries are bounded.
- No secrets are written to source control or ordinary logs.
- Partial evidence survives an AI failure.
- A restarted process can recover according to the documented recovery design.
- Connector failure status is visible to the user.

---

# 15. Phase 9 — Evaluation and regression testing

## Objective

Measure whether the investigator reaches correct conclusions from reproducible evidence. Do not use a handful of successful demos as proof of reliability.

## Step 1: Define the benchmark

Use the four seeded scenarios from Phase 1.

For each scenario, record:

- Expected primary cause category.
- Expected evidence types.
- Required evidence references.
- Acceptable alternative hypotheses.
- Forbidden unsupported claims.
- Expected missing-evidence behavior.

Keep the ground-truth labels separate from the prompt context supplied to the model.

## Step 2: Define metrics

### Top-1 root-cause accuracy

The fraction of evaluated scenarios in which the leading hypothesis matches the expected cause category.

### Top-3 root-cause accuracy

The fraction in which the expected cause appears among the first three hypotheses.

### Evidence-reference validity

The fraction of cited evidence references that exist and belong to the correct incident.

### Unsupported-claim rate

The proportion of factual report claims that lack adequate evidence.

Define the claim-extraction and review method before reporting a number.

### Abstention quality

Check whether the system correctly reports insufficient evidence when essential source data is missing.

### Recovery behavior

Measure whether the system returns a useful partial report when a connector or the AI service fails.

### Latency and cost

Measure actual wall-clock investigation duration and LLM usage/cost where available. Report the test environment and number of runs.

## Step 3: Test multiple conditions

For each scenario, test:

- All connectors available.
- One connector unavailable.
- Required evidence missing.
- Conflicting evidence.
- Irrelevant warning in logs.
- A misleading commit near the failure time.
- Incorrect workload mapping.
- Invalid evidence ID returned by the model.
- Prompt-injection-like content in a log.
- LLM timeout or malformed response.

## Step 4: Record results

Store machine-readable results in the evaluation directory.

Include:

- Test timestamp.
- Application version or commit SHA.
- Model/provider and model version, if known.
- Prompt version.
- Scenario ID.
- Expected and actual diagnosis.
- Evidence validity.
- Latency.
- Failure reason, if applicable.

Do not store API keys or full sensitive prompts in benchmark output.

## Step 5: Establish a baseline

Run the deterministic pipeline without AI and compare it with the AI-assisted pipeline.

The evaluation should answer:

- Does the AI identify causes that deterministic rules miss?
- Does it add unsupported claims?
- Does it improve ranking of plausible causes?
- Does it correctly express uncertainty?
- What is the additional latency and cost?

Do not assume the AI version is better. Use the results to decide where it adds value.

## Exit criteria

- Every seeded scenario has automated evaluation.
- Evidence-reference validity is checked automatically.
- At least one missing-evidence and one conflicting-evidence case is evaluated.
- The report includes actual measured results and their limitations.
- Regression tests prevent previously fixed hallucination and correlation errors from returning.

---

# 16. Phase 10 — Local UAT-style environment and delivery

## Objective

Make the project easy to run, inspect, evaluate, and demonstrate from a clean checkout.

This phase establishes a repeatable test environment. It does not establish that the system has passed an organization's production-readiness process.

## Step 1: Docker Compose

Provide local services for:

- PostgreSQL.
- Spring Boot backend.
- Python AI service.
- Frontend.

Use health checks and service readiness conditions where supported. Persist database data using a named volume for ordinary development, and provide an explicit reset procedure.

Do not place real credentials in the Compose file.

## Step 2: Demo seeding

Create a deterministic seeding command that:

1. Loads the workload registry.
2. Loads the four scenarios.
3. Loads their evidence fixtures.
4. Creates the required synthetic repository history or references it.
5. Creates the associated runbooks.
6. Validates that all referenced files and evidence IDs exist.
7. Reports how many records were inserted.

The seeding command must be safe to rerun. Provide a separate explicit reset command rather than silently deleting user data.

## Step 3: UAT test cases

Create a test checklist covering:

- Successful invalid-input investigation.
- Upstream dependency failure.
- Workflow validation failure.
- Code regression.
- Missing source data.
- Connector timeout.
- AI service unavailable.
- Duplicate submission.
- Retry after partial failure.
- Report evidence inspection.
- Evidence reference validation.

Record actual outcomes and defects.

## Step 4: Demo script

Prepare a reproducible demo using the invalid-input scenario:

1. Start the local environment.
2. Seed the synthetic data.
3. Submit an investigation request.
4. Inspect the incident status.
5. Inspect the collected scheduler and application evidence.
6. Inspect the relevant source code and runbook evidence.
7. Open the AI-generated report.
8. Show why the leading hypothesis is supported.
9. Disable or simulate the failure of one connector.
10. Repeat the investigation and show the partial-result behavior.

The demo must clearly identify the environment as a synthetic test environment.

## Step 5: Documentation

The README must include:

- Problem statement.
- Scope and non-goals.
- Architecture diagram.
- Component responsibilities.
- Technology stack.
- Setup instructions.
- Configuration reference.
- Synthetic scenario descriptions.
- API documentation link for the running backend.
- How to run tests.
- How to run evaluation.
- Known limitations.
- Integration status matrix.
- Security considerations.
- Screenshots or a short demo recording, if useful.

## Exit criteria

- A clean checkout can be started using the documented commands.
- Synthetic data can be seeded reproducibly.
- All four scenarios can be investigated.
- Failure and partial-result demonstrations work.
- Tests and evaluation can be run by another developer.
- All real and simulated integrations are clearly distinguished.
- Documentation does not claim an unperformed production deployment.

---

# 17. Definition of done for the overall project

The project is complete when all of the following are true:

1. A manual REST request can create an investigation.
2. The incident is persisted before long-running work begins.
3. The system collects evidence through a consistent connector contract.
4. Synthetic AutoSys, OpenShift, GitHub, release, and runbook adapters work.
5. The four core scenarios are reproducible.
6. The deterministic evidence timeline is available without AI.
7. The AI produces structured hypotheses and an evidence-backed report.
8. Every cited evidence ID is validated.
9. Missing data and uncertain conclusions are clearly reported.
10. A connector failure does not erase successful evidence from other sources.
11. Retries are bounded and previous attempts remain inspectable.
12. The project can be started locally from a clean checkout.
13. The benchmark reports measured results without fabricated performance claims.
14. The README explains the architecture, limitations, setup, and evaluation.
15. Real integration adapters, if present, use documented endpoints and verified permissions.
16. No production changes or unauthorized access are required to demonstrate the project.

---

# 18. Suggested execution order and priorities

Implement in this order:

1. Project bootstrap.
2. Synthetic scenarios and ground truth.
3. Domain model and database migrations.
4. Incident API and lifecycle.
5. Connector contracts and synthetic adapters.
6. Deterministic collection and evidence timeline.
7. AI service and evidence validation.
8. Backend/AI integration.
9. Report UI.
10. Reliability and security tests.
11. Evaluation benchmark.
12. Docker Compose, UAT-style testing, and documentation.
13. Real read-only adapters, one at a time, only after their access and capabilities have been verified.

If time is constrained, finish a complete end-to-end synthetic investigation before adding real integrations. A small system that can explain a seeded incident correctly and demonstrate its limitations is more valuable than four half-finished vendor connectors.

---

# 19. Additional reference material

Use these official references when implementing the corresponding phase.

## AutoSys Workload Automation 12.1

- [Broadcom: AutoSys 12.1 and R24.1 REST API/Swagger documentation](https://knowledge.broadcom.com/external/article/445384/rest-api-and-swagger-documentation-for.html)
- [Broadcom: AutoSys 12.1 open REST API reference](https://techdocs.broadcom.com/us/en/ca-enterprise-software/intelligent-automation/autosys-workload-automation/12-1-01/reference/ae-web-services/autosys-workload-automation-open-rest-apis.html)
- [Broadcom: Querying AutoSys jobs by run time](https://knowledge.broadcom.com/external/article/451923/how-to-query-autosys-to-find-all-jobs-th.html)

Use the version-specific Swagger/OpenAPI contract as the source of truth for actual operations, fields, filters, and supported behavior.

## GitHub

- [GitHub REST API: Commits and commit comparison](https://docs.github.com/en/rest/commits/commits)
- [GitHub REST API: Repository contents](https://docs.github.com/en/rest/repos/contents)
- [GitHub REST API: Authentication](https://docs.github.com/en/rest/authentication/authenticating-to-the-rest-api)

## OpenShift and Kubernetes

- [OpenShift pod API reference](https://docs.redhat.com/en/documentation/openshift_container_platform/4.20/html/workloads_apis/pod-v1)
- [Kubernetes pod API reference](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/)
- [Kubernetes RBAC](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)

## AI orchestration

- [LangGraph overview](https://docs.langchain.com/oss/python/langgraph/overview)
- [LangGraph persistence](https://docs.langchain.com/oss/python/langgraph/persistence)
- [LangGraph checkpoint reference](https://reference.langchain.com/python/langgraph/checkpoints)
- [FastAPI documentation](https://fastapi.tiangolo.com/)

## Testing and local runtime

- [Spring Boot reference](https://docs.spring.io/spring-boot/reference/)
- [Testcontainers](https://testcontainers.com/)
- [Docker Compose](https://docs.docker.com/compose/)
- [PostgreSQL documentation](https://www.postgresql.org/docs/)

---

# Final instruction to the coding agent

Implement this as a coherent engineering product, not as a collection of disconnected demos.

Prioritize explicit contracts, deterministic evidence collection, traceability, bounded AI behavior, and reproducible tests. Keep the system operational at every milestone. When documentation or access does not establish that an integration is available, do not guess; document the gap and continue with the simulated adapter.

The final deliverable should be a working, independently reproducible UAT-style AI Operations Investigator with realistic synthetic incidents, evidence-backed reports, measured evaluation results, and a clear path to verified read-only integrations. It should be possible for another engineer to clone the repository, run the documented commands, reproduce the incidents, and understand exactly which parts are implemented, simulated, or still unverified.
