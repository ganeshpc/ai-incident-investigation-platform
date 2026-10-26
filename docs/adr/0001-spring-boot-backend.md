# ADR 0001: Spring Boot is the system-of-record API

## Status

Accepted

## Date

2026-10-26

## Context

The investigation needs one process that accepts a request, stores the incident before slow work starts, and stays usable when the model service is down. The execution plan requires Java and Spring Boot.

## Decision

The HTTP API is a Spring Boot 4.1.2 application on Java 21. It owns incident lifecycle, validation, the workload registry, connector calls, evidence persistence, idempotency, audit, report retrieval, retries, and calls to the AI service. Maven builds it. springdoc-openapi 3.2.0 publishes the API document.

## Alternatives considered

- A second Java framework. Rejected because the plan fixes Spring Boot and Boot 4.1 is the current stable line.
- Spring Boot 4.2.0. Rejected because it was a release candidate on 2026-10-26.
- Putting the system of record in the Python service. Rejected because the API must work when the AI service is unavailable.

## Consequences

- Database migrations, incident state, and audit events live in this process.
- The backend calls the AI service over HTTP with a timeout. A failed call keeps the evidence already stored.
- Later phases add Flyway and Spring Data JPA. Phase 0 does not open a database connection.
