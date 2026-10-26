# ADR 0003: PostgreSQL is the primary database

## Status

Accepted

## Date

2026-10-26

## Context

Incidents, attempts, evidence, hypotheses, reports, and audit events must survive a process restart. The plan requires PostgreSQL and Flyway.

## Decision

PostgreSQL 18 is the database. The official image tag for later Compose work is `postgres:18.6`. Flyway owns schema changes. Application code does not create tables at startup. JSON is allowed for source-specific attributes. Incidents, evidence, hypotheses, and their links stay in columns and foreign keys.

## Alternatives considered

- SQLite for the demo. Rejected because the plan requires PostgreSQL and the recovery tests need a server database.
- PostgreSQL 19. Rejected because it was a release candidate on 2026-10-26.
- One JSON document per investigation. Rejected because the plan requires queryable entities.

## Consequences

- Phase 2 adds the Flyway migrations and the Spring Data repositories.
- Phase 0 does not start PostgreSQL and does not add a JDBC driver, so the API can boot without a database.
- Local credentials stay in `.env`. The example file uses a placeholder password.
