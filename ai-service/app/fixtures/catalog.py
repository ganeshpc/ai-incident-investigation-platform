"""Load the synthetic scenario fixtures into one catalog.

A catalog is a dictionary of evidence records keyed by evidence id. Building
it reads the files and checks that every ground-truth id points at a real
record. The dictionary is replaced on each load. Nothing is appended, so
running the load again cannot create a second copy of a record.

The database does not exist in this phase. This module is the file-level
loader. A later phase copies the same records into PostgreSQL.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path

import yaml

from app.fixtures.demo_repo import repo_root

ROLES = frozenset({"primary", "consequence", "negative", "context"})
TIME_PRECISIONS = frozenset({"EXACT", "APPROXIMATE", "UNKNOWN"})
GROUND_TRUTH_LISTS = (
    "supportingEvidenceIds",
    "consequenceEvidenceIds",
    "negativeEvidenceIds",
    "contextEvidenceIds",
)
ROLE_TO_GROUND_TRUTH = {
    "primary": "supportingEvidenceIds",
    "consequence": "consequenceEvidenceIds",
    "negative": "negativeEvidenceIds",
    "context": "contextEvidenceIds",
}


class FixtureError(ValueError):
    """The fixture files disagree with each other."""


@dataclass(frozen=True)
class Evidence:
    """One cited fixture record.

    ``frozen=True`` means the fields cannot be changed after construction.
    The loader therefore cannot quietly rewrite an id while it is checking
    the catalog.

    ``text`` is the file contents. The locator searches this string. It does
    not ask a model what the text means.
    """

    evidence_id: str
    scenario_id: str
    workload_id: str
    source_system: str
    evidence_type: str
    role: str
    event_time: datetime | None
    event_time_precision: str
    record_id: str
    content_file: str
    summary: str
    text: str


@dataclass(frozen=True)
class Scenario:
    """One seeded incident and the evidence files that belong to it."""

    scenario_id: str
    workload_id: str
    failure_time: datetime
    lookback_minutes: int
    evidence: tuple[Evidence, ...]
    ground_truth: dict[str, object]

    def by_role(self, role: str) -> set[str]:
        """Return the evidence ids the manifest gives this role."""

        return {item.evidence_id for item in self.evidence if item.role == role}


@dataclass(frozen=True)
class Catalog:
    """Every scenario loaded from one pass over the fixture tree."""

    scenarios: tuple[Scenario, ...]

    def scenario(self, scenario_id: str) -> Scenario:
        """Return one scenario or raise if the id is not in the catalog."""

        for item in self.scenarios:
            if item.scenario_id == scenario_id:
                return item
        raise FixtureError(f"unknown scenario {scenario_id}")


def load_catalog(root: Path | None = None) -> Catalog:
    """Read the scenario catalog, manifests, and ground-truth files.

    ``root`` defaults to the monorepo root. Tests pass a path only when they
    build a temporary tree. The returned catalog is a new object. The function
    does not update a global list.
    """

    base = repo_root() if root is None else root
    index = _read_mapping(base / "evaluation" / "scenario-catalog.yaml")
    entries = _require_list(index.get("scenarios"), "scenario catalog")
    scenarios: list[Scenario] = []
    seen_ids: set[str] = set()
    for entry in entries:
        scenario = _load_scenario(base, _require_mapping(entry, "scenario entry"))
        for item in scenario.evidence:
            if item.evidence_id in seen_ids:
                raise FixtureError(f"duplicate evidence id {item.evidence_id}")
            seen_ids.add(item.evidence_id)
        scenarios.append(scenario)
    if len(scenarios) != 4:
        raise FixtureError(f"expected 4 scenarios, found {len(scenarios)}")
    return Catalog(tuple(scenarios))


def canonical_summary(catalog: Catalog) -> dict[str, object]:
    """Return a JSON-ready summary whose bytes stay the same across loads.

    Keys are sorted and evidence is ordered by id. Two equal catalogs therefore
    produce the same document. Writing that document replaces the previous
    file instead of appending to it.
    """

    evidence: list[dict[str, str]] = []
    for scenario in catalog.scenarios:
        for item in scenario.evidence:
            evidence.append(
                {
                    "contentSha256": hashlib.sha256(item.text.encode("utf-8")).hexdigest(),
                    "evidenceId": item.evidence_id,
                    "evidenceType": item.evidence_type,
                    "role": item.role,
                    "scenarioId": item.scenario_id,
                }
            )
    evidence.sort(key=lambda row: row["evidenceId"])
    return {
        "evidence": evidence,
        "evidenceCount": len(evidence),
        "scenarioIds": [scenario.scenario_id for scenario in catalog.scenarios],
    }


def summary_json(catalog: Catalog) -> str:
    """Serialize :func:`canonical_summary` with a trailing newline."""

    return json.dumps(canonical_summary(catalog), indent=2, sort_keys=True) + "\n"


def _load_scenario(base: Path, entry: dict[str, object]) -> Scenario:
    """Load one manifest and check it against its ground-truth file."""

    manifest_path = _relative(base, _require_str(entry, "manifest"))
    truth_path = _relative(base, _require_str(entry, "groundTruth"))
    manifest = _read_mapping(manifest_path)
    truth = _read_mapping(truth_path)
    scenario_id = _require_str(manifest, "scenarioId")
    if scenario_id != _require_str(entry, "scenarioId"):
        raise FixtureError(f"{manifest_path} does not match the catalog id")
    if scenario_id != _require_str(truth, "scenarioId"):
        raise FixtureError(f"{truth_path} does not match {scenario_id}")
    workload_id = _require_str(manifest, "workloadId")
    _check_workload(base, workload_id)
    failure_time = _parse_utc(_require_str(manifest, "failureTime"), "failureTime")
    lookback = _require_int(manifest, "lookbackMinutes")
    evidence = tuple(
        _load_evidence(base, scenario_id, workload_id, item)
        for item in _require_list(manifest.get("evidence"), f"{scenario_id} evidence")
    )
    scenario = Scenario(scenario_id, workload_id, failure_time, lookback, evidence, truth)
    _check_ground_truth(scenario)
    _check_time_window(scenario)
    _check_missing_evidence(scenario)
    return scenario


def _load_evidence(
    base: Path,
    scenario_id: str,
    workload_id: str,
    raw: object,
) -> Evidence:
    """Turn one manifest entry into an evidence record and read its file."""

    item = _require_mapping(raw, f"{scenario_id} evidence entry")
    evidence_id = _require_str(item, "evidenceId")
    role = _require_str(item, "role")
    if role not in ROLES:
        raise FixtureError(f"{evidence_id} has unknown role {role}")
    precision = _require_str(item, "eventTimePrecision")
    if precision not in TIME_PRECISIONS:
        raise FixtureError(f"{evidence_id} has unknown time precision {precision}")
    event_raw = item.get("eventTime")
    event_time = None
    if event_raw is not None:
        event_time = _parse_utc(_require_text(event_raw, "eventTime"), evidence_id)
    elif precision != "UNKNOWN":
        raise FixtureError(f"{evidence_id} has no event time but precision is {precision}")
    content_file = _require_str(item, "contentFile")
    path = _relative(base, content_file)
    text = path.read_text(encoding="utf-8")
    record_id = _require_str(item, "recordId")
    evidence_type = _require_str(item, "evidenceType")
    if evidence_type == "CODE_DIFF":
        if not text.startswith("diff --git "):
            raise FixtureError(f"{evidence_id} is not a unified diff")
    elif not _record_is_present(record_id, text, content_file):
        raise FixtureError(f"{evidence_id} record id {record_id} is not in its content")
    return Evidence(
        evidence_id=evidence_id,
        scenario_id=scenario_id,
        workload_id=workload_id,
        source_system=_require_str(item, "sourceSystem"),
        evidence_type=evidence_type,
        role=role,
        event_time=event_time,
        event_time_precision=precision,
        record_id=record_id,
        content_file=content_file,
        summary=_require_str(item, "summary"),
        text=text,
    )


def _check_workload(base: Path, workload_id: str) -> None:
    """Require a workload registry file whose id matches the manifest."""

    path = base / "fixtures" / "workloads" / f"{workload_id}.yaml"
    document = _read_mapping(path)
    if document.get("workloadId") != workload_id:
        raise FixtureError(f"{path} does not declare workloadId {workload_id}")
    if document.get("provenance") != "SIMULATED":
        raise FixtureError(f"{workload_id} must say provenance SIMULATED")


def _check_ground_truth(scenario: Scenario) -> None:
    """Check that ground truth and manifest roles name the same ids."""

    truth = scenario.ground_truth
    seen: set[str] = set()
    for key in GROUND_TRUTH_LISTS:
        for evidence_id in _require_str_list(truth.get(key), f"{scenario.scenario_id} {key}"):
            if evidence_id in seen:
                raise FixtureError(f"{evidence_id} is listed twice in {scenario.scenario_id}")
            seen.add(evidence_id)
    manifest_ids = {item.evidence_id for item in scenario.evidence}
    if seen != manifest_ids:
        missing = manifest_ids - seen
        extra = seen - manifest_ids
        raise FixtureError(
            f"{scenario.scenario_id} ground truth does not match the manifest "
            f"missing={sorted(missing)} extra={sorted(extra)}"
        )
    for item in scenario.evidence:
        listed = set(_require_str_list(truth.get(ROLE_TO_GROUND_TRUTH[item.role]), item.role))
        if item.evidence_id not in listed:
            raise FixtureError(f"{item.evidence_id} role {item.role} disagrees with ground truth")
    if "primaryCauseCategory" not in truth:
        raise FixtureError(f"{scenario.scenario_id} has no primary cause category")
    if not _require_str_list(truth.get("requiredClaims"), "requiredClaims"):
        raise FixtureError(f"{scenario.scenario_id} has no required claim")
    if not _require_str_list(truth.get("forbiddenClaims"), "forbiddenClaims"):
        raise FixtureError(f"{scenario.scenario_id} has no forbidden claim")


def _check_time_window(scenario: Scenario) -> None:
    """Keep failure-window times inside the lookback, and leave context free.

    Context records, such as a successful run on an earlier day, may sit
    outside the window. A primary, consequence, or negative record that has
    a time must sit inside it. A time with no zone is rejected.
    """

    start = scenario.failure_time - timedelta(minutes=scenario.lookback_minutes)
    end = scenario.failure_time + timedelta(minutes=15)
    for item in scenario.evidence:
        if item.event_time is None or item.role == "context":
            continue
        if not start <= item.event_time <= end:
            raise FixtureError(
                f"{item.evidence_id} time {item.event_time.isoformat()} is outside the lookback"
            )


def _check_missing_evidence(scenario: Scenario) -> None:
    """A declared gap must not also be an evidence id."""

    raw = scenario.ground_truth.get("expectedMissingEvidence")
    if raw is None:
        raise FixtureError(f"{scenario.scenario_id} does not declare expectedMissingEvidence")
    if not isinstance(raw, list):
        raise FixtureError(f"{scenario.scenario_id} expectedMissingEvidence is not a list")
    known = {item.evidence_id for item in scenario.evidence}
    for entry in raw:
        document = _require_mapping(entry, "missing evidence")
        missing_id = _require_str(document, "id")
        _require_str(document, "reason")
        if missing_id in known:
            raise FixtureError(f"{missing_id} is both present and declared missing")


def _parse_utc(value: str, label: str) -> datetime:
    """Parse an instant that is explicitly UTC.

    A trailing ``Z`` is the UTC marker. A value without it is rejected instead
    of being guessed as local time.
    """

    if not value.endswith("Z"):
        raise FixtureError(f"{label} time {value} is not UTC")
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def _read_mapping(path: Path) -> dict[str, object]:
    """Read a YAML file and require its top level to be a mapping.

    PyYAML returns ordinary Python objects. The checks below narrow that
    result to a string-keyed dictionary before the rest of the loader uses it.
    """

    if not path.is_file():
        raise FixtureError(f"missing file {path}")
    loaded: object = yaml.safe_load(path.read_text(encoding="utf-8"))
    return _require_mapping(loaded, str(path))


def _require_mapping(value: object, label: str) -> dict[str, object]:
    """Return ``value`` when it is a dictionary with string keys."""

    if not isinstance(value, dict):
        raise FixtureError(f"{label} is not a mapping")
    result: dict[str, object] = {}
    for key, item in value.items():
        if not isinstance(key, str):
            raise FixtureError(f"{label} has a non-string key")
        result[key] = item
    return result


def _require_list(value: object, label: str) -> list[object]:
    """Return ``value`` when it is a list."""

    if not isinstance(value, list):
        raise FixtureError(f"{label} is not a list")
    return value


def _require_str(mapping: dict[str, object], key: str) -> str:
    """Read one required string field."""

    value = mapping.get(key)
    if not isinstance(value, str) or value == "":
        raise FixtureError(f"missing string field {key}")
    return value


def _require_text(value: object, label: str) -> str:
    """Require a non-empty string that is not nested in a mapping."""

    if not isinstance(value, str) or value == "":
        raise FixtureError(f"{label} is not a string")
    return value


def _require_int(mapping: dict[str, object], key: str) -> int:
    """Read one required integer field.

    YAML may read a whole number as ``int``. A boolean is a subclass of
    ``int`` in Python, so booleans are rejected separately.
    """

    value = mapping.get(key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise FixtureError(f"{key} is not an integer")
    return value


def _require_str_list(value: object, label: str) -> list[str]:
    """Return a list only when every item is a string."""

    items = _require_list(value, label)
    result: list[str] = []
    for item in items:
        if not isinstance(item, str) or item == "":
            raise FixtureError(f"{label} contains a non-string")
        result.append(item)
    return result


def _record_is_present(record_id: str, text: str, content_file: str) -> bool:
    """Return whether a record id can be found in its file.

    A runbook id may look like ``file.md#Section name``. The file name has to
    be in the path and the section name has to be in the text. Other ids have
    to appear either in the text or in the path.
    """

    if "#" in record_id:
        file_part, section = record_id.split("#", 1)
        return file_part in content_file and section in text
    return record_id in text or record_id in content_file


def _relative(base: Path, raw_path: str) -> Path:
    """Resolve a repo-relative path and reject paths that escape the repo."""

    path = (base / raw_path).resolve()
    if not path.is_relative_to(base.resolve()):
        raise FixtureError(f"{raw_path} escapes the repository")
    if not path.is_file():
        raise FixtureError(f"missing file {raw_path}")
    return path


def main() -> None:
    """Print the canonical summary. Used by the repeatable load script."""

    print(summary_json(load_catalog()), end="")


if __name__ == "__main__":
    main()
