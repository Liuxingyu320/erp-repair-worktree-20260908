#!/usr/bin/env python3
"""Verify production-scale query evidence without accepting self-declared timings."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Any


RELEASE_ID = "new-business-20260714"
SCENARIOS = {
    "health-certificate-expiry-list": ("healthCertificates", 1200),
    "customer-service-card-search": ("customerServiceCards", 1200),
    "transfer-workbench": ("transferOrders", 1500),
    "transfer-discrepancy-list": ("transferDiscrepancies", 1200),
}
SHA256 = re.compile(r"^[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
SAFE_OBJECT = re.compile(r"^[a-z][a-z0-9_.]{1,127}$")
PLACEHOLDER = re.compile(r"^(?:todo|tbd|pending|unknown|待定|未填写|示例)$", re.I)
SECRET_KEY = re.compile(
    r"(?:password|passwd|secret|access.?key|private.?key|bearer|token)$", re.I
)
MAX_ARTIFACT_BYTES = 5 * 1024 * 1024


class PerformanceEvidenceError(ValueError):
    pass


def _text(value: Any, label: str) -> str:
    result = str(value or "").strip()
    if not result or PLACEHOLDER.fullmatch(result):
        raise PerformanceEvidenceError(
            f"{label} is required and cannot be a placeholder"
        )
    return result


def _time(value: Any, label: str) -> str:
    result = _text(value, label)
    try:
        parsed = datetime.fromisoformat(result.replace("Z", "+00:00"))
    except ValueError as exc:
        raise PerformanceEvidenceError(f"{label} must be ISO-8601") from exc
    if parsed.tzinfo is None:
        raise PerformanceEvidenceError(f"{label} must include a timezone")
    return result


def _positive_number(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or value <= 0:
        raise PerformanceEvidenceError(f"{label} must be a positive number")
    return float(value)


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise PerformanceEvidenceError(f"{label} must be a positive integer")
    return value


def _reject_secrets(value: Any, path: str = "evidence") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if SECRET_KEY.search(str(key)):
                raise PerformanceEvidenceError(
                    f"{path}.{key} is a forbidden credential field"
                )
            _reject_secrets(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _reject_secrets(child, f"{path}[{index}]")


def _sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _artifact(value: Any, label: str, evidence_dir: Path, root: Path) -> Path:
    if (
        not isinstance(value, dict)
        or value.get("status") != "passed"
        or value.get("sanitized") is not True
    ):
        raise PerformanceEvidenceError(
            f"{label} must be a passed, explicitly sanitized artifact"
        )
    candidate = Path(_text(value.get("path"), f"{label}.path")).expanduser()
    if not candidate.is_absolute():
        candidate = evidence_dir / candidate
    if candidate.is_symlink():
        raise PerformanceEvidenceError(f"{label}.path must not be a symlink")
    path = candidate.resolve()
    if not path.is_file():
        raise PerformanceEvidenceError(f"{label}.path must be a regular file")
    try:
        path.relative_to(root.resolve())
    except ValueError as exc:
        raise PerformanceEvidenceError(
            f"{label}.path must be under the workspace"
        ) from exc
    if path.stat().st_size > MAX_ARTIFACT_BYTES:
        raise PerformanceEvidenceError(f"{label} exceeds the 5 MiB evidence limit")
    declared = str(value.get("sha256") or "")
    if not SHA256.fullmatch(declared) or _sha(path) != declared:
        raise PerformanceEvidenceError(f"{label}.sha256 does not match its artifact")
    return path


def _load_probe(
    path: Path,
    label: str,
    phase: str,
    commit: str,
    scenario_id: str,
    expected_p95: float,
) -> int:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PerformanceEvidenceError(f"{label} is not valid JSON") from exc
    if (
        value.get("schemaVersion") != 1
        or value.get("releaseId") != RELEASE_ID
        or value.get("kind") != "api-performance"
        or value.get("phase") != phase
        or value.get("gitCommit") != commit
        or value.get("status") != "passed"
    ):
        raise PerformanceEvidenceError(
            f"{label} does not match the {phase} commit or passed probe contract"
        )
    matches = [
        item
        for item in value.get("scenarios", [])
        if isinstance(item, dict) and item.get("id") == scenario_id
    ]
    if len(matches) != 1:
        raise PerformanceEvidenceError(
            f"{label} must contain scenario {scenario_id} exactly once"
        )
    result = matches[0]
    count = _positive_int(result.get("sampleCount"), f"{label}.sampleCount")
    measured = _positive_number(result.get("p95Ms"), f"{label}.p95Ms")
    if count < 30 or result.get("errorCount") != 0 or result.get("status") != "passed":
        raise PerformanceEvidenceError(
            f"{label} must contain at least 30 successful, error-free samples"
        )
    if abs(measured - expected_p95) > 0.11:
        raise PerformanceEvidenceError(
            f"{label}.p95Ms differs from the declared scenario value"
        )
    return count


def verify(path: Path, root: Path, expected_commit: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PerformanceEvidenceError(
            f"cannot read performance evidence: {path}"
        ) from exc
    if not isinstance(value, dict):
        raise PerformanceEvidenceError("performance evidence must be a JSON object")
    _reject_secrets(value)
    if value.get("schemaVersion") != 1 or value.get("releaseId") != RELEASE_ID:
        raise PerformanceEvidenceError("performance evidence schema/releaseId is invalid")
    candidate = str(value.get("candidateCommit") or "")
    baseline = str(value.get("baselineCommit") or "")
    if not COMMIT.fullmatch(candidate) or candidate != expected_commit:
        raise PerformanceEvidenceError(
            "candidateCommit does not match the release candidate"
        )
    if not COMMIT.fullmatch(baseline):
        raise PerformanceEvidenceError("baselineCommit must be a full Git commit")
    _text(value.get("runId"), "runId")
    _time(value.get("completedAt"), "completedAt")

    handling = value.get("dataHandling")
    if not isinstance(handling, dict) or any(
        handling.get(field) is not expected
        for field, expected in (
            ("syntheticOrAnonymized", True),
            ("containsProductionPersonalData", False),
            ("responseBodiesPersisted", False),
        )
    ):
        raise PerformanceEvidenceError(
            "dataHandling must prove synthetic/anonymized, response-free evidence"
        )

    datasets = value.get("datasets")
    if not isinstance(datasets, dict) or set(datasets) != {
        dataset for dataset, _ in SCENARIOS.values()
    }:
        raise PerformanceEvidenceError("datasets must cover the five exact data domains")
    for name, dataset in datasets.items():
        if not isinstance(dataset, dict):
            raise PerformanceEvidenceError(f"datasets.{name} must be an object")
        fixture = _positive_int(dataset.get("fixtureRows"), f"datasets.{name}.fixtureRows")
        reference = _positive_int(
            dataset.get("productionReferenceRows"),
            f"datasets.{name}.productionReferenceRows",
        )
        ratio = fixture / reference
        if not 0.8 <= ratio <= 1.25:
            raise PerformanceEvidenceError(
                f"datasets.{name} fixture volume must be within 80%-125% of reference"
            )
        _time(dataset.get("referenceCapturedAt"), f"datasets.{name}.referenceCapturedAt")
        _artifact(
            dataset.get("rowCountEvidence"),
            f"datasets.{name}.rowCountEvidence",
            path.parent,
            root,
        )

    scenarios = value.get("scenarios")
    if not isinstance(scenarios, list) or [item.get("id") for item in scenarios] != list(
        SCENARIOS
    ):
        raise PerformanceEvidenceError(
            "scenarios must contain the five critical queries in the required order"
        )
    for index, scenario in enumerate(scenarios):
        scenario_id = scenario["id"]
        dataset_name, maximum_budget = SCENARIOS[scenario_id]
        label = f"scenarios[{index}]"
        if scenario.get("status") != "passed" or scenario.get("dataset") != dataset_name:
            raise PerformanceEvidenceError(f"{label} is incomplete or uses the wrong dataset")
        endpoint = _text(scenario.get("endpoint"), f"{label}.endpoint")
        if not endpoint.startswith("GET /") or any(value in endpoint for value in ("?", "#", "://")):
            raise PerformanceEvidenceError(
                f"{label}.endpoint must be a query-free same-origin GET route"
            )
        digest = str(scenario.get("queryDigestSha256") or "")
        if not SHA256.fullmatch(digest):
            raise PerformanceEvidenceError(f"{label}.queryDigestSha256 is invalid")
        before = _positive_number(scenario.get("p95BeforeMs"), f"{label}.p95BeforeMs")
        after = _positive_number(scenario.get("p95AfterMs"), f"{label}.p95AfterMs")
        budget = _positive_number(scenario.get("budgetMs"), f"{label}.budgetMs")
        if budget > maximum_budget or after > budget:
            raise PerformanceEvidenceError(
                f"{label} exceeds its accepted P95 budget ({maximum_budget} ms max)"
            )
        if after > max(before * 1.10, before + 50):
            raise PerformanceEvidenceError(f"{label} has an unexplained P95 regression")

        artifacts = scenario.get("artifacts")
        if not isinstance(artifacts, dict):
            raise PerformanceEvidenceError(f"{label}.artifacts is required")
        explain_before = _artifact(
            artifacts.get("explainBefore"),
            f"{label}.artifacts.explainBefore",
            path.parent,
            root,
        )
        _artifact(
            artifacts.get("explainAfter"),
            f"{label}.artifacts.explainAfter",
            path.parent,
            root,
        )
        slow_before = _artifact(
            artifacts.get("slowQueryBefore"),
            f"{label}.artifacts.slowQueryBefore",
            path.parent,
            root,
        )
        _artifact(
            artifacts.get("slowQueryAfter"),
            f"{label}.artifacts.slowQueryAfter",
            path.parent,
            root,
        )
        load_before = _artifact(
            artifacts.get("loadBefore"),
            f"{label}.artifacts.loadBefore",
            path.parent,
            root,
        )
        load_after = _artifact(
            artifacts.get("loadAfter"),
            f"{label}.artifacts.loadAfter",
            path.parent,
            root,
        )
        before_count = _load_probe(
            load_before,
            f"{label}.artifacts.loadBefore",
            "before",
            baseline,
            scenario_id,
            before,
        )
        after_count = _load_probe(
            load_after,
            f"{label}.artifacts.loadAfter",
            "after",
            candidate,
            scenario_id,
            after,
        )
        if scenario.get("sampleCountBefore") != before_count or scenario.get(
            "sampleCountAfter"
        ) != after_count:
            raise PerformanceEvidenceError(
                f"{label} sample counts differ from the load-probe artifacts"
            )

        changes = scenario.get("indexChanges")
        if not isinstance(changes, list):
            raise PerformanceEvidenceError(f"{label}.indexChanges must be an array")
        allowed_evidence = {_sha(explain_before), _sha(slow_before)}
        for change_index, change in enumerate(changes):
            change_label = f"{label}.indexChanges[{change_index}]"
            if not isinstance(change, dict) or change.get("action") not in {
                "create",
                "modify",
                "drop",
            }:
                raise PerformanceEvidenceError(f"{change_label}.action is invalid")
            if not SAFE_OBJECT.fullmatch(
                _text(change.get("databaseObject"), f"{change_label}.databaseObject")
            ):
                raise PerformanceEvidenceError(f"{change_label}.databaseObject is invalid")
            _text(change.get("rationale"), f"{change_label}.rationale")
            _text(change.get("reviewer"), f"{change_label}.reviewer")
            if change.get("basedOnArtifactSha256") not in allowed_evidence:
                raise PerformanceEvidenceError(
                    f"{change_label} is not tied to before EXPLAIN/slow-query evidence"
                )
        _text(scenario.get("reviewer"), f"{label}.reviewer")
        _time(scenario.get("completedAt"), f"{label}.completedAt")

    signoff = value.get("signoff")
    if not isinstance(signoff, dict) or signoff.get("decision") != "approved":
        raise PerformanceEvidenceError("performance signoff decision must be approved")
    owners = [
        _text(signoff.get(role), f"signoff.{role}")
        for role in ("backendOwner", "databaseOwner", "qaOwner")
    ]
    if len(set(owners)) < 2:
        raise PerformanceEvidenceError(
            "performance signoff requires at least two distinct people"
        )
    _time(signoff.get("approvedAt"), "signoff.approvedAt")
    return {
        "releaseId": RELEASE_ID,
        "candidateCommit": candidate,
        "scenarioCount": len(SCENARIOS),
        "status": "passed",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--candidate-commit")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    commit = args.candidate_commit or subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
    ).strip()
    try:
        result = verify(Path(args.evidence).resolve(), root, commit)
    except (OSError, PerformanceEvidenceError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print("[PASS] production-scale performance evidence is complete")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
