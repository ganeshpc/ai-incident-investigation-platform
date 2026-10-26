# ADR 0004: Connectors use one interface and ship first as simulations

## Status

Accepted

## Date

2026-10-26

## Context

Evidence comes from AutoSys-style scheduler data, OpenShift-style logs, GitHub history, release records, and runbooks. Real endpoints are not available in this project. The plan forbids inventing vendor APIs and forbids pretending fixture data is a live system.

## Decision

Each source gets a small Java connector interface. The first implementation of every source is a simulated adapter that reads fixtures and marks evidence as synthetic. A real adapter is a separate class, read-only, enabled only after the official API and the access requirements are verified. Configuration uses application modes such as `autosys.mode=simulated`. A real mode that cannot run returns `UNCONFIGURED` or a failure status. It does not silently read fixtures.

Real adapters are built after the synthetic investigation works, in the order in section 18 of the execution plan.

## Alternatives considered

- One connector class per vendor response shape, called directly by the investigator. Rejected because a vendor change would leak into the investigation rules.
- Implementing live AutoSys and OpenShift clients before the synthetic path works. Rejected because those systems are not available here and the plan says to finish the synthetic investigation first.

## Consequences

- Collection results use the statuses `SUCCESS`, `PARTIAL`, `UNAVAILABLE`, `UNCONFIGURED`, and `FAILED`.
- The UI must show synthetic provenance.
- The release connector stays on fixtures until a real release product and its documentation are identified.
