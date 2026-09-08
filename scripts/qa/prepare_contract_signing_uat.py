#!/usr/bin/env python3
"""Validate contract-signing UAT fixtures and emit a secret-free preflight artifact."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from contract_signing_uat_common import (
    HR_BROWSER_CHECKS,
    EMPLOYEE_BROWSER_CHECKS,
    SCENARIOS,
    UatConfigError,
    load_config,
    public_config,
)


def _output_path(value: str, root: Path) -> Path:
    candidate = Path(value).expanduser()
    if candidate.is_symlink():
        raise UatConfigError("preflight output must not be a symlink")
    output = candidate.resolve()
    allowed = (root / "output").resolve()
    try:
        output.relative_to(allowed)
    except ValueError as exc:
        raise UatConfigError("preflight output must be stored under output/") from exc
    return output


def build_preflight(config: dict) -> dict:
    legal_entity_count = sum(
        len(org["legalEntities"]) for org in config["organizations"].values()
    )
    browser_plan = [
        {
            "id": journey["id"],
            "scenario": journey["scenario"],
            "mode": journey["mode"],
            "actor": journey["actor"],
            "organization": journey["organization"],
            "viewport": journey["viewport"],
            "requiredCheckCount": len(
                HR_BROWSER_CHECKS
                if journey["mode"] == "HR_DESKTOP"
                else EMPLOYEE_BROWSER_CHECKS
            ),
        }
        for journey in config["browserJourneys"]
    ]
    api_plan = [
        {
            "id": probe["id"],
            "case": probe["case"],
            "scenario": probe.get("scenario"),
            "actor": probe.get("actor"),
            "organization": probe.get("organization"),
            "method": str(probe.get("method") or "GET").upper(),
            "expect": probe["expect"],
            "write": str(probe.get("method") or "GET").upper()
            in {"POST", "PUT", "PATCH", "DELETE"},
        }
        for probe in config["apiProbes"]
    ]
    gates = {
        "isolatedDatabase": True,
        "productionDataCopied": False,
        "mysql57RehearsalDeclared": str(config["databaseEngine"]["version"]).startswith("5.7."),
        "nonAdministratorActorCount": len(config["actors"]),
        "dedicatedHrCount": sum(
            actor["kind"] == "HR" for actor in config["actors"].values()
        ),
        "technicalEvidenceActorSeparated": True,
        "legacyPackageBoundaryDeclared": True,
        "environmentIdentityProbeDeclared": True,
        "trustedSignerPinned": True,
        "organizationCount": len(config["organizations"]),
        "legalEntityCount": legal_entity_count,
        "contractCoverageCount": len(config["contractCoverage"]),
        "syntheticEmployeeCount": len(config["employees"]),
        "scenarioCount": len(config["scenarios"]),
        "browserJourneyCount": len(browser_plan),
        "apiProbeCount": len(api_plan),
        "faultCaseCount": len(config["faultCases"]),
        "writesExecuted": False,
    }
    return {
        **public_config(config),
        "kind": "contract-signing-preflight",
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "status": "passed",
        "checkCount": len(gates),
        "gates": gates,
        "browserPlan": browser_plan,
        "apiPlan": api_plan,
        "environmentIdentityPlan": {
            "id": config["environmentIdentityProbe"]["id"],
            "actor": config["environmentIdentityProbe"]["actor"],
            "method": "GET",
            "path": config["environmentIdentityProbe"]["path"],
            "expectedEnvironmentId": config["expectedEnvironmentId"],
            "databaseFingerprintSha256": config["databaseFingerprintSha256"],
            "releaseId": config["releaseId"],
            "runId": config["runId"],
            "candidateCommit": config["candidateCommit"],
        },
        "legacyBoundaryPlan": dict(config["legacyMenuBoundary"]),
        "faultPlan": [
            {
                "id": row["id"],
                "executionMode": row["executionMode"],
                "writeRequired": row["writeRequired"],
                "recoveryRequired": bool(row.get("recoveryRequired")),
            }
            for row in config["faultCases"]
        ],
        "requiredScenarios": list(SCENARIOS),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        config = load_config(args.config, root)
        output = _output_path(args.output, root)
        preflight = build_preflight(config)
    except (UatConfigError, OSError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(preflight, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"[PASS] secret-free contract-signing UAT preflight: {output}")
    print(
        "[INFO] no business write was executed; use the API runner with explicit "
        "--allow-write approval only in the isolated UAT database"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
