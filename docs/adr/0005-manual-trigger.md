# ADR 0005: A person starts an investigation

## Status

Accepted

## Date

2026-10-26

## Context

The product investigates a failed batch job or workflow. Automatic detection would need a feed from a scheduler or a monitoring system that this project does not have.

## Decision

The first version starts an investigation only when a client calls `POST /api/incidents/investigate`. The call returns `202 Accepted` after the incident row is stored. It does not hold the HTTP connection open for collection or model calls. There is no polling of external systems and no event bus.

## Alternatives considered

- Kafka or another broker to receive failure events. Rejected for the first version. The plan lists it as out of scope.
- Running the whole investigation inside the POST request. Rejected because collection and model calls are slow and a dropped connection would hide a stored incident.

## Consequences

- The UI submits a form and then reads status with GET requests.
- Automatic detection is a later product decision, not a hidden Phase 0 feature.
- An in-process worker, added with the incident lifecycle, claims stored attempts. It uses a lease so a restart can continue an attempt that was left running.
