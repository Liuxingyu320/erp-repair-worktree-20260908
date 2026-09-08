#!/usr/bin/env python3
"""Verify that release-blocking UAT evidence is complete and artifact-backed."""

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
    "health-certificate",
    "fixed-asset-reporting",
    "transfer-return-discrepancy",
    "customer-service-card",
}
SHA256 = re.compile(r"^[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
PLACEHOLDER = re.compile(r"^(?:todo|tbd|pending|unknown|待定|未填写|示例)$", re.I)
SECRET_KEY = re.compile(
    r"(?:password|passwd|secret|access.?key|private.?key|bearer|token)$", re.I
)


class EvidenceError(ValueError):
    pass


def _text(value: Any, label: str) -> str:
    result = str(value or "").strip()
    if not result or PLACEHOLDER.fullmatch(result):
        raise EvidenceError(f"{label} is required and must not be a placeholder")
    return result


def _timestamp(value: Any, label: str) -> str:
    text = _text(value, label)
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError as exc:
        raise EvidenceError(f"{label} must be ISO-8601") from exc
    if parsed.tzinfo is None:
        raise EvidenceError(f"{label} must include a timezone")
    return text


def _reject_secrets(value: Any, path: str = "evidence") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if SECRET_KEY.search(str(key)):
                raise EvidenceError(f"{path}.{key} is a forbidden credential field")
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


def _artifact(
    value: Any, label: str, evidence_dir: Path, root: Path
) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise EvidenceError(f"{label} must be an object")
    if value.get("status") != "passed":
        raise EvidenceError(f"{label}.status must equal passed")
    path_text = _text(value.get("path"), f"{label}.path")
    candidate = Path(path_text).expanduser()
    if not candidate.is_absolute():
        candidate = evidence_dir / candidate
    if candidate.is_symlink():
        raise EvidenceError(f"{label}.path must not be a symlink")
    path = candidate.resolve()
    if not path.is_file():
        raise EvidenceError(f"{label}.path must be a regular evidence file")
    try:
        path.relative_to(root.resolve())
    except ValueError:
        raise EvidenceError(f"{label}.path must be stored under the workspace")
    declared = str(value.get("sha256") or "")
    if not SHA256.fullmatch(declared) or _sha(path) != declared:
        raise EvidenceError(f"{label}.sha256 does not match the artifact")
    count = value.get("checkCount")
    if not isinstance(count, int) or count <= 0:
        raise EvidenceError(f"{label}.checkCount must be a positive integer")
    return {"path": str(path), "sha256": declared, "checkCount": count}


def verify(
    evidence_path: Path, root: Path, expected_commit: str | None = None
) -> dict[str, Any]:
    try:
        value = json.loads(evidence_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"cannot read UAT evidence: {evidence_path}") from exc
    if not isinstance(value, dict):
        raise EvidenceError("UAT evidence must be a JSON object")
    _reject_secrets(value)
    if value.get("schemaVersion") != 1:
        raise EvidenceError("schemaVersion must equal 1")
    if value.get("releaseId") != RELEASE_ID:
        raise EvidenceError(f"releaseId must equal {RELEASE_ID}")
    candidate = str(value.get("candidateCommit") or "")
    if not COMMIT.fullmatch(candidate):
        raise EvidenceError("candidateCommit must be a full 40-character commit")
    if expected_commit and candidate != expected_commit:
        raise EvidenceError(
            f"candidateCommit {candidate} does not match release candidate {expected_commit}"
        )
    _text(value.get("runId"), "runId")
    _timestamp(value.get("completedAt"), "completedAt")

    environment = value.get("environment")
    if not isinstance(environment, dict):
        raise EvidenceError("environment must be an object")
    for label in ("databaseFingerprintSha256", "organizationFingerprintSha256"):
        if not SHA256.fullmatch(str(environment.get(label) or "")):
            raise EvidenceError(f"environment.{label} must be a SHA-256 fingerprint")
    if environment.get("stores") != ["storeA", "storeB"]:
        raise EvidenceError("environment.stores must equal [storeA, storeB]")
    if environment.get("warehouses") != ["warehouseA", "warehouseB"]:
        raise EvidenceError(
            "environment.warehouses must equal [warehouseA, warehouseB]"
        )

    artifacts = value.get("artifacts")
    if not isinstance(artifacts, dict):
        raise EvidenceError("artifacts must be an object")
    verified_artifacts = {
        "api": _artifact(
            artifacts.get("api"), "artifacts.api", evidence_path.parent, root
        ),
        "browser": _artifact(
            artifacts.get("browser"),
            "artifacts.browser",
            evidence_path.parent,
            root,
        ),
    }

    scenarios = value.get("scenarios")
    if not isinstance(scenarios, list) or len(scenarios) != len(SCENARIOS):
        raise EvidenceError("scenarios must contain exactly the four active required cases")
    seen = set()
    for scenario in scenarios:
        if not isinstance(scenario, dict):
            raise EvidenceError("every scenario must be an object")
        scenario_id = str(scenario.get("id") or "")
        if scenario_id not in SCENARIOS or scenario_id in seen:
            raise EvidenceError(f"unknown or duplicate scenario: {scenario_id!r}")
        seen.add(scenario_id)
        if scenario.get("status") != "passed":
            raise EvidenceError(f"scenario {scenario_id} is not passed")
        if set(scenario.get("evidenceTypes") or []) != {"api", "browser"}:
            raise EvidenceError(
                f"scenario {scenario_id} must have both API and browser evidence"
            )
        if not isinstance(scenario.get("negativeCaseCount"), int) or scenario[
            "negativeCaseCount"
        ] <= 0:
            raise EvidenceError(
                f"scenario {scenario_id} must include a passed negative case"
            )
        _text(scenario.get("reviewer"), f"scenario {scenario_id}.reviewer")
        _timestamp(
            scenario.get("reviewedAt"), f"scenario {scenario_id}.reviewedAt"
        )
    if seen != SCENARIOS:
        raise EvidenceError("one or more required scenarios are missing")

    signoff = value.get("signoff")
    if not isinstance(signoff, dict):
        raise EvidenceError("signoff must be an object")
    owners = [
        _text(signoff.get(role), f"signoff.{role}")
        for role in ("businessOwner", "qaOwner", "releaseOwner")
    ]
    if len(set(owners)) < 2:
        raise EvidenceError("signoff must contain at least two distinct people")
    _timestamp(signoff.get("approvedAt"), "signoff.approvedAt")
    if signoff.get("decision") != "approved":
        raise EvidenceError("signoff.decision must equal approved")

    return {
        "releaseId": RELEASE_ID,
        "candidateCommit": candidate,
        "scenarioCount": len(seen),
        "artifacts": verified_artifacts,
        "status": "passed",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--candidate-commit")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    commit = args.candidate_commit
    if not commit:
        commit = subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
        ).strip()
    try:
        summary = verify(Path(args.evidence).resolve(), root, commit)
    except EvidenceError as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print("[PASS] release-blocking UAT evidence is complete and artifact-backed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
