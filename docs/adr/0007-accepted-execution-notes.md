# ADR 0007: Accepted execution notes

## Status

Accepted

## Date

2026-10-26

## Context

The execution plan leaves several contracts unnamed. The notes below were accepted before Phase 0 so later phases do not invent them differently.

## Decision

- The idempotency header is `Idempotency-Key`. The same key and the same body return the original incident. The same key and a different body return `409`.
- `POST /api/incidents/{id}/retry` creates a new attempt. `GET /api/incidents/{id}/attempts` returns history. Retry does not rerun a successful collection unless the request says so. A report that is already ready stays ready unless the caller explicitly asks to run again.
- Validation errors use RFC 9457 problem details.
- Phase 1 proves fixture consistency before any database load. The database seed is idempotent once Flyway tables exist.
- Scenario 4 builds a Git repository with a script that pins the author, committer, and dates. The monorepo does not contain a nested `.git` directory. The manifest stores the resulting commit SHAs.
- The local demo has no login screen. `createdBy` comes from the optional `X-Actor` header and defaults to `local-dev`. Services bind to localhost.
- Java code lives in `com.aiops.investigator`.

## Alternatives considered

- Leaving the header name and retry route until the incident API is written. Rejected because those choices affect the schema, the UI, and the tests together.

## Consequences

Phase 2 and later phases implement these contracts. Phase 0 only reserves them in this record and enables problem-detail responses in the backend configuration.
