"""Find fixture evidence by reading the files, without calling a model.

Each rule looks for a literal string that the synthetic record actually
contains. The evidence id is whatever record matched. The rules do not read
the ``role`` field, so they are not repeating the manifest's own answer key.
"""

from __future__ import annotations

from collections.abc import Callable

from app.fixtures.catalog import Catalog, Evidence

Predicate = Callable[[str], bool]


def locate_supporting(catalog: Catalog, scenario_id: str) -> set[str]:
    """Return the evidence ids that explain the scenario's primary failure."""

    items = catalog.scenario(scenario_id).evidence
    if scenario_id == "scenario-001-invalid-input":
        return _match_any(
            items,
            lambda text: "jobName: DEMO_RECON_JOB" in text and "reconciliation-worker.jar" in text,
            lambda text: (
                "dependencyId: dep-recon-to-publish" in text
                and "upstreamJob: DEMO_RECON_JOB" in text
            ),
            lambda text: "exitCode: 42" in text and "DEMO_RECON_JOB" in text,
            lambda text: "record 2 missing required field transactionId" in text,
            lambda text: ",25.50,USD" in text,
            lambda text: "class TxnIdValidator" in text,
            lambda text: "## Required input fields" in text,
        )
    if scenario_id == "scenario-002-upstream-dependency":
        return _match_any(
            items,
            lambda text: "jobName: DEMO_PREP_JOB" in text and "prepare-demo-input" in text,
            lambda text: (
                "dependencyId: dep-prep-to-settle" in text and "upstreamJob: DEMO_PREP_JOB" in text
            ),
            lambda text: "exitCode: 7" in text and "DEMO_PREP_JOB" in text,
            lambda text: "source directory missing /data/in/demo" in text,
            lambda text: "## Dependency chain" in text,
        )
    if scenario_id == "scenario-003-workflow-validation":
        return _match_any(
            items,
            lambda text: "transitionId: draft-to-ready" in text and "preconditions:" in text,
            lambda text: (
                "caseId: CASE-DEMO-100" in text and "state: DRAFT" in text and "asOf:" in text
            ),
            lambda text: "requestedTransition: draft-to-ready" in text,
            lambda text: (
                "TransitionRejected: READY_FOR_REVIEW requires assignee and kycStatus COMPLETE"
                in text
                and "case-review-demo-2" in text
            ),
            lambda text: "class CaseTransitionValidator" in text,
            lambda text: "## Permitted transition" in text,
        )
    if scenario_id == "scenario-004-code-regression":
        regression = "ae366f73578063be38807dcc08d861875eedbca0"
        return _match_any(
            items,
            lambda text: f"commitId: {regression}" in text and "Reject decimal amounts" in text,
            lambda text: "raw.indexOf('.')" in text and "decimal point is not accepted" in text,
            lambda text: (
                "releaseId: rel-invoice-20260320" in text
                and f"deployedCommit: {regression}" in text
            ),
            lambda text: "exitCode: 9" in text and f"deployedCommit: {regression}" in text,
            lambda text: (
                "decimal point is not accepted" in text and "run-invoice-20260320-001" in text
            ),
            lambda text: "INV-DEMO-1,10.00" in text,
            lambda text: "## Expected amount format" in text,
        )
    raise ValueError(f"no locator for {scenario_id}")


def locate_consequences(catalog: Catalog, scenario_id: str) -> set[str]:
    """Return records that describe a later job state caused by the primary failure."""

    items = catalog.scenario(scenario_id).evidence
    if scenario_id == "scenario-001-invalid-input":
        return _match_any(
            items,
            lambda text: (
                "statusLabel: NOT_RUN_UPSTREAM_FAILED" in text
                and "jobName: DEMO_RECON_PUBLISH" in text
            ),
        )
    if scenario_id == "scenario-002-upstream-dependency":
        return _match_any(
            items,
            lambda text: (
                "statusLabel: NOT_RUN_UPSTREAM_FAILED" in text
                and "jobName: DEMO_SETTLE_JOB" in text
            ),
        )
    return set()


def locate_negative(catalog: Catalog, scenario_id: str) -> set[str]:
    """Return nearby records that must not be treated as the cause."""

    items = catalog.scenario(scenario_id).evidence
    if scenario_id == "scenario-001-invalid-input":
        return _match_any(items, lambda text: "Ignore previous instructions" in text)
    if scenario_id == "scenario-002-upstream-dependency":
        return _match_any(
            items,
            lambda text: "report template DEMO_MONTHLY missing" in text,
        )
    if scenario_id == "scenario-003-workflow-validation":
        return _match_any(
            items, lambda text: "disk usage on node demo-worker-2 is 80 percent" in text
        )
    if scenario_id == "scenario-004-code-regression":
        unrelated = "503430c7d53a0ff5b1d0f1a2189f4f0d779b81af"
        return _match_any(items, lambda text: f"commitId: {unrelated}" in text)
    raise ValueError(f"no locator for {scenario_id}")


def _match_any(items: tuple[Evidence, ...], *predicates: Predicate) -> set[str]:
    """Collect evidence ids whose text satisfies at least one predicate."""

    found: set[str] = set()
    for item in items:
        if any(predicate(item.text) for predicate in predicates):
            found.add(item.evidence_id)
    return found
