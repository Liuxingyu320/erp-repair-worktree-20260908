#!/usr/bin/env python3
"""Verify release-blocking contract-signing UAT evidence and artifact hashes."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Any

from contract_signing_uat_common import (
    COMMIT,
    CONTRACT_TYPES,
    EMPLOYEE_BROWSER_CHECKS,
    HR_BROWSER_CHECKS,
    LEGACY_PACKAGE_MENU_IDS,
    LEGACY_PACKAGE_PERMISSIONS,
    OA_ROOT_MENU_ID,
    RELEASE_ID,
    REQUIRED_ACTORS,
    REQUIRED_API_CASES,
    REQUIRED_FAULT_CASES,
    SCENARIOS,
    SHA256,
    TASK_CENTER_ROOT_MENU_ID,
    TECHNICAL_PERMISSION,
)


CHAIN_STEPS = {
    "task-generated",
    "data-remediated-or-validated",
    "sent",
    "documents-loaded",
    "documents-read",
    "initial-signed",
    "company-and-seal-selected",
    "final-documents-read",
    "final-confirmed",
    "downloaded",
    "verified",
}
EXCEPTION_CASES = REQUIRED_FAULT_CASES
ARTIFACT_KINDS = {
    "preflight": "contract-signing-preflight",
    "api": "contract-signing-api-uat",
    "browser": "contract-signing-browser-uat",
    "mysql57": "contract-signing-mysql57-uat",
}
MAX_ATTESTATION_LOG_BYTES = 10 * 1024 * 1024
OBSERVATION_KEYS = {
    "httpStatus", "appCode", "contentType", "durationMs", "bodyBytes", "bodySha256"
}
LEGACY_BOUNDARY_CONTRACT = {
    "actor": "legacyPackageOnly",
    "oaRootMenuId": OA_ROOT_MENU_ID,
    "requiredMenuIds": list(LEGACY_PACKAGE_MENU_IDS),
    "forbiddenMenuIds": [TASK_CENTER_ROOT_MENU_ID],
    "packageRoute": "sign-package",
    "packageComponent": "oa/signPackage/index",
    "taskRoute": "sign-task",
    "packageBrowserPath": "/oa/sign-package",
    "taskBrowserPath": "/oa/sign-task",
}
PLACEHOLDER = re.compile(r"^(?:todo|tbd|pending|unknown|待定|未填写|示例|验收人)$", re.I)
SECRET_KEY = re.compile(
    r"(?:password|passwd|secret|access.?key|private.?key|bearer|token)$", re.I
)
SENSITIVE_KEY = re.compile(
    r"(?:signatureDataUrl|contract(?:Body|Text)|idCard|phone|bankAccount|homeAddress)$",
    re.I,
)


class EvidenceError(ValueError):
    pass


def _text(value: Any, label: str) -> str:
    result = str(value or "").strip()
    if not result or PLACEHOLDER.fullmatch(result):
        raise EvidenceError(f"{label} is required and must not be a placeholder")
    return result


def _timestamp(value: Any, label: str) -> datetime:
    text = _text(value, label)
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError as exc:
        raise EvidenceError(f"{label} must be ISO-8601") from exc
    if parsed.tzinfo is None:
        raise EvidenceError(f"{label} must include a timezone")
    return parsed


def _hash(value: Any, label: str) -> str:
    result = str(value or "")
    if not SHA256.fullmatch(result) or result == "0" * 64:
        raise EvidenceError(f"{label} must be a non-placeholder SHA-256")
    return result


def _reject_sensitive(value: Any, path: str = "evidence") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            key_text = str(key)
            if SECRET_KEY.search(key_text) or SENSITIVE_KEY.search(key_text):
                raise EvidenceError(f"{path}.{key_text} is a forbidden sensitive field")
            _reject_sensitive(child, f"{path}.{key_text}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _reject_sensitive(child, f"{path}[{index}]")
    elif isinstance(value, str):
        lowered = value.lower()
        if (
            len(value) > 2048
            or "data:image/" in lowered
            or "data:application/pdf" in lowered
            or "%pdf-" in lowered
            or lowered.startswith("bearer ")
        ):
            raise EvidenceError(f"{path} contains forbidden signing or credential content")


def _sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _output_file(
    value: Any,
    label: str,
    base_dir: Path,
    root: Path,
) -> Path:
    path_text = _text(value, label)
    candidate = Path(path_text).expanduser()
    if not candidate.is_absolute():
        candidate = base_dir / candidate
    if candidate.is_symlink():
        raise EvidenceError(f"{label} must not be a symlink")
    path = candidate.resolve()
    if not path.is_file():
        raise EvidenceError(f"{label} must be a regular file")
    try:
        path.relative_to((root / "output").resolve())
    except ValueError as exc:
        raise EvidenceError(f"{label} must be stored under output/") from exc
    return path


def _trusted_public_key(
    value: str | Path | None,
    fingerprint: str | None,
) -> tuple[Path, str]:
    if value is None or fingerprint is None:
        raise EvidenceError(
            "trusted signer public key and independently pinned SHA-256 are required"
        )
    expected = _hash(fingerprint, "trusted signer fingerprint")
    candidate = Path(value).expanduser()
    if not candidate.is_absolute():
        raise EvidenceError("trusted signer public key path must be absolute")
    if candidate.is_symlink():
        raise EvidenceError("trusted signer public key must not be a symlink")
    path = candidate.resolve()
    if not path.is_file():
        raise EvidenceError("trusted signer public key must be a regular file")
    if _sha(path) != expected:
        raise EvidenceError("trusted signer public key does not match the pinned fingerprint")
    return path, expected


def _verify_signature(
    data_path: Path,
    signature_value: Any,
    declared_fingerprint: Any,
    label: str,
    base_dir: Path,
    root: Path,
    public_key: Path,
    trusted_fingerprint: str,
) -> Path:
    if str(declared_fingerprint or "") != trusted_fingerprint:
        raise EvidenceError(f"{label}.signerPublicKeySha256 is not the trusted signer")
    signature = _output_file(signature_value, f"{label}.signaturePath", base_dir, root)
    if signature.stat().st_size <= 0 or signature.stat().st_size > 16 * 1024:
        raise EvidenceError(f"{label}.signaturePath has an invalid detached signature size")
    try:
        result = subprocess.run(
            [
                "openssl", "dgst", "-sha256", "-verify", str(public_key),
                "-signature", str(signature), str(data_path),
            ],
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError as exc:
        raise EvidenceError("OpenSSL is required for UAT attestation verification") from exc
    if result.returncode != 0:
        raise EvidenceError(f"{label} detached signature is invalid")
    return signature


def _verified_file_ref(
    value: Any,
    label: str,
    base_dir: Path,
    root: Path,
    max_bytes: int | None = None,
) -> Path:
    if not isinstance(value, dict) or set(value) != {"path", "sha256", "byteCount"}:
        raise EvidenceError(f"{label} must contain exactly path, sha256 and byteCount")
    path = _output_file(value["path"], f"{label}.path", base_dir, root)
    declared = _hash(value["sha256"], f"{label}.sha256")
    size = path.stat().st_size
    if not isinstance(value["byteCount"], int) or value["byteCount"] != size or size <= 0:
        raise EvidenceError(f"{label}.byteCount must match a non-empty file")
    if max_bytes is not None and size > max_bytes:
        raise EvidenceError(f"{label} exceeds the allowed size")
    if _sha(path) != declared:
        raise EvidenceError(f"{label}.sha256 does not match the file")
    return path


def _artifact(
    value: Any,
    label: str,
    expected_kind: str,
    evidence_dir: Path,
    root: Path,
    release_id: str,
    run_id: str,
    candidate_commit: str,
    public_key: Path,
    trusted_fingerprint: str,
) -> tuple[dict[str, Any], dict[str, Any]]:
    if not isinstance(value, dict):
        raise EvidenceError(f"{label} must be an object")
    if value.get("status") != "passed":
        raise EvidenceError(f"{label}.status must equal passed")
    path = _output_file(value.get("path"), f"{label}.path", evidence_dir, root)
    declared = _hash(value.get("sha256"), f"{label}.sha256")
    if _sha(path) != declared:
        raise EvidenceError(f"{label}.sha256 does not match the artifact")
    signature = _verify_signature(
        path,
        value.get("signaturePath"),
        value.get("signerPublicKeySha256"),
        label,
        evidence_dir,
        root,
        public_key,
        trusted_fingerprint,
    )
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"{label}.path must contain valid JSON") from exc
    if not isinstance(payload, dict):
        raise EvidenceError(f"{label} artifact must be a JSON object")
    _reject_sensitive(payload, f"artifact.{label}")
    if payload.get("kind") != expected_kind or payload.get("status") != "passed":
        raise EvidenceError(f"{label} artifact kind/status is invalid")
    if payload.get("releaseId") != release_id or payload.get("runId") != run_id:
        raise EvidenceError(f"{label} artifact releaseId/runId does not match evidence")
    if payload.get("candidateCommit") != candidate_commit:
        raise EvidenceError(f"{label} artifact candidateCommit does not match evidence")
    if payload.get("trustedSignerPublicKeySha256") != trusted_fingerprint:
        raise EvidenceError(f"{label} artifact is not bound to the trusted signer")
    _timestamp(payload.get("completedAt"), f"{label} artifact completedAt")
    declared_count = value.get("checkCount")
    artifact_count = payload.get("checkCount", payload.get("probeCount"))
    if not isinstance(declared_count, int) or declared_count <= 0:
        raise EvidenceError(f"{label}.checkCount must be a positive integer")
    if not isinstance(artifact_count, int) or declared_count != artifact_count:
        raise EvidenceError(f"{label}.checkCount must match the artifact")
    return (
        {
            "path": str(path),
            "sha256": declared,
            "checkCount": declared_count,
            "signaturePath": str(signature),
            "signerPublicKeySha256": trusted_fingerprint,
        },
        payload,
    )


def _verify_preflight(payload: dict[str, Any], environment: dict[str, Any]) -> None:
    gates = payload.get("gates")
    if not isinstance(gates, dict):
        raise EvidenceError("preflight artifact gates must be an object")
    expected = {
        "isolatedDatabase": True,
        "productionDataCopied": False,
        "mysql57RehearsalDeclared": True,
        "technicalEvidenceActorSeparated": True,
        "legacyPackageBoundaryDeclared": True,
        "environmentIdentityProbeDeclared": True,
        "trustedSignerPinned": True,
        "writesExecuted": False,
    }
    for key, value in expected.items():
        if gates.get(key) is not value:
            raise EvidenceError(f"preflight gate {key} must equal {value}")
    if gates.get("dedicatedHrCount", 0) < 2:
        raise EvidenceError("preflight must include dedicated non-admin HRs for both organizations")
    if gates.get("nonAdministratorActorCount") != len(REQUIRED_ACTORS):
        raise EvidenceError("preflight must include every exact non-administrator actor")
    if gates.get("organizationCount") != 2 or gates.get("scenarioCount") != 5:
        raise EvidenceError("preflight must cover two organizations and five scenarios")
    if gates.get("browserJourneyCount") != 10:
        raise EvidenceError("preflight must declare ten browser journeys")
    if gates.get("faultCaseCount") != len(EXCEPTION_CASES):
        raise EvidenceError("preflight must declare every required failure/recovery case")
    if payload.get("databaseFingerprintSha256") != environment["databaseFingerprintSha256"]:
        raise EvidenceError("preflight database fingerprint does not match final evidence")
    if payload.get("migrationBundleSha256") != environment["migrationBundleSha256"]:
        raise EvidenceError("preflight migration hash does not match final evidence")
    if payload.get("expectedEnvironmentId") != environment["environmentId"]:
        raise EvidenceError("preflight environment id does not match final evidence")
    if payload.get("database") != environment["database"]:
        raise EvidenceError("preflight database name does not match final evidence")
    if payload.get("legacyBoundaryPlan") != LEGACY_BOUNDARY_CONTRACT:
        raise EvidenceError("preflight legacy package/task menu boundary is not exact")
    identity = payload.get("environmentIdentityPlan")
    if not isinstance(identity, dict):
        raise EvidenceError("preflight environment identity plan is missing")
    expected_identity = {
        "expectedEnvironmentId": environment["environmentId"],
        "databaseFingerprintSha256": environment["databaseFingerprintSha256"],
        "releaseId": payload.get("releaseId"),
        "runId": payload.get("runId"),
        "candidateCommit": payload.get("candidateCommit"),
    }
    for key, expected_value in expected_identity.items():
        if identity.get(key) != expected_value:
            raise EvidenceError(f"preflight environment identity {key} is not pinned")
    if identity.get("method") != "GET" or not identity.get("actor"):
        raise EvidenceError("preflight environment identity probe must be authenticated read-only GET")


def _verify_http_observation(value: Any, label: str) -> None:
    if not isinstance(value, dict) or not OBSERVATION_KEYS <= set(value):
        raise EvidenceError(f"{label} lacks generated HTTP observation fields")
    status = value.get("httpStatus")
    if not isinstance(status, int) or 300 <= status < 400 or status >= 500:
        raise EvidenceError(f"{label} contains a forbidden redirect/server-error status")
    if not isinstance(value.get("durationMs"), int) or value["durationMs"] < 0:
        raise EvidenceError(f"{label}.durationMs must be a non-negative integer")
    if not isinstance(value.get("bodyBytes"), int) or value["bodyBytes"] < 0:
        raise EvidenceError(f"{label}.bodyBytes must be a non-negative integer")
    _hash(value.get("bodySha256"), f"{label}.bodySha256")
    if not isinstance(value.get("contentType"), str):
        raise EvidenceError(f"{label}.contentType must be a string")


def _verify_api(payload: dict[str, Any], environment: dict[str, Any]) -> None:
    if payload.get("writeMode") is not True or payload.get("skippedWriteCount") != 0:
        raise EvidenceError("release API artifact must be the explicitly approved full write run")
    if payload.get("failedCount") != 0:
        raise EvidenceError("release API artifact must have zero failed probes")
    if payload.get("environmentIdentityVerified") is not True:
        raise EvidenceError("release API artifact must verify environment identity before writes")
    identity = payload.get("environmentIdentity")
    if not isinstance(identity, dict) or identity.get("status") != "passed":
        raise EvidenceError("API environment identity observation is missing")
    _verify_http_observation(identity, "API environment identity")
    expected_identity = {
        "environmentId": environment["environmentId"],
        "database": environment["database"],
        "databaseFingerprintSha256": environment["databaseFingerprintSha256"],
        "releaseId": payload.get("releaseId"),
        "runId": payload.get("runId"),
        "candidateCommit": payload.get("candidateCommit"),
        "isolated": True,
        "productionDataCopied": False,
    }
    if identity.get("observed") != expected_identity or identity.get("method") != "GET":
        raise EvidenceError("API environment identity does not match the independently pinned target")
    if payload.get("databaseFingerprintSha256") != environment["databaseFingerprintSha256"]:
        raise EvidenceError("API artifact database fingerprint does not match final evidence")
    if payload.get("migrationBundleSha256") != environment["migrationBundleSha256"]:
        raise EvidenceError("API artifact migration hash does not match final evidence")
    covered = set(payload.get("coveredCases") or [])
    if not REQUIRED_API_CASES <= covered:
        raise EvidenceError(f"API artifact misses required cases: {sorted(REQUIRED_API_CASES - covered)}")
    results = payload.get("results")
    if not isinstance(results, list) or not results:
        raise EvidenceError("API artifact results must be non-empty")
    if payload.get("probeCount") != len(results) or not all(isinstance(item, dict) for item in results):
        raise EvidenceError("API artifact probeCount/results are inconsistent")
    for index, item in enumerate(results):
        if item.get("status") != "passed":
            raise EvidenceError("all API artifact results must be passed")
        executions = item.get("executions")
        if executions is None:
            _verify_http_observation(item, f"API result {index}")
        else:
            if not isinstance(executions, list) or len(executions) < 2:
                raise EvidenceError(f"API result {index} parallel executions are invalid")
            for execution_index, execution in enumerate(executions):
                _verify_http_observation(
                    execution, f"API result {index} execution {execution_index}"
                )
                if execution.get("assertionStatus") not in {
                    "success", "accepted-idempotent-failure"
                }:
                    raise EvidenceError("parallel execution assertion status is invalid")
    success_scenarios = {
        str(item.get("scenario") or "").upper()
        for item in results
        if isinstance(item, dict) and item.get("case") == "success-chain-state"
    }
    if success_scenarios != set(SCENARIOS):
        raise EvidenceError("API success-chain-state results must cover all five scenarios")
    hr_results = [
        item for item in results
        if isinstance(item, dict) and item.get("case") == "permissions-dedicated-hr"
    ]
    if not hr_results or any(
        item.get("actor") not in {"dedicatedHrOrgA", "dedicatedHrOrgB"}
        for item in hr_results
    ):
        raise EvidenceError("dedicated HR API permission checks must not use an administrator")
    legacy_package = [
        item for item in results
        if item.get("case") == "legacy-package-route-preserved"
    ]
    legacy_denied = [
        item for item in results
        if item.get("case") == "legacy-task-center-denied"
    ]
    if (
        len(legacy_package) != 1
        or legacy_package[0].get("actor") != "legacyPackageOnly"
        or legacy_package[0].get("expect") != "success"
        or not 200 <= legacy_package[0].get("httpStatus", 0) < 300
        or legacy_package[0].get("appCode") not in {None, 200, "200"}
        or len(legacy_denied) != 1
        or legacy_denied[0].get("actor") != "legacyPackageOnly"
        or legacy_denied[0].get("expect") != "failure"
        or (
            200 <= legacy_denied[0].get("httpStatus", 0) < 300
            and legacy_denied[0].get("appCode") in {None, 200, "200"}
        )
    ):
        raise EvidenceError("API evidence does not prove the exact legacy package/task boundary")
    duplicate_results = [item for item in results if item.get("case") == "duplicate-request"]
    if len(duplicate_results) != 1:
        raise EvidenceError("API artifact must contain one duplicate-request result")
    duplicate = duplicate_results[0]
    identity_hash = _hash(
        duplicate.get("stableIdentitySha256"),
        "duplicate-request stableIdentitySha256",
    )
    postcondition = duplicate.get("postcondition")
    if (
        not isinstance(postcondition, dict)
        or postcondition.get("status") != "passed"
        or postcondition.get("observedBusinessKeyCount") != 1
        or postcondition.get("stableIdentitySha256") != identity_hash
    ):
        raise EvidenceError("duplicate-request must prove one stable business-key row")
    _verify_http_observation(postcondition, "duplicate-request postcondition")


def _verify_screenshot_manifest(
    value: Any,
    journey_id: str,
    label: str,
    artifact_dir: Path,
    root: Path,
    payload: dict[str, Any],
) -> int:
    manifest_path = _verified_file_ref(
        value, f"{label}.screenshotManifest", artifact_dir, root, max_bytes=1024 * 1024
    )
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"{label} screenshot manifest must be UTF-8 JSON") from exc
    if not isinstance(manifest, dict):
        raise EvidenceError(f"{label} screenshot manifest must be an object")
    _reject_sensitive(manifest, f"{label}.screenshotManifest")
    bindings = {
        "kind": "contract-signing-screenshot-manifest",
        "releaseId": payload.get("releaseId"),
        "runId": payload.get("runId"),
        "candidateCommit": payload.get("candidateCommit"),
        "journeyId": journey_id,
    }
    for key, expected in bindings.items():
        if manifest.get(key) != expected:
            raise EvidenceError(f"{label} screenshot manifest {key} is not bound")
    files = manifest.get("files")
    if not isinstance(files, list) or not files:
        raise EvidenceError(f"{label} screenshot manifest must reference files")
    seen: set[Path] = set()
    for index, file_ref in enumerate(files):
        screenshot = _verified_file_ref(
            file_ref,
            f"{label}.screenshotManifest.files[{index}]",
            manifest_path.parent,
            root,
            max_bytes=25 * 1024 * 1024,
        )
        if screenshot in seen:
            raise EvidenceError(f"{label} screenshot manifest contains a duplicate file")
        seen.add(screenshot)
    return len(files)


def _verify_browser(
    payload: dict[str, Any],
    preflight: dict[str, Any],
    artifact_path: Path,
    root: Path,
) -> None:
    journeys = payload.get("journeys")
    if not isinstance(journeys, list) or len(journeys) != 10:
        raise EvidenceError("browser artifact must contain ten journeys")
    coverage: set[tuple[str, str]] = set()
    expected_plan = {
        row.get("id"): row
        for row in (preflight.get("browserPlan") or [])
        if isinstance(row, dict)
    }
    if len(expected_plan) != 10:
        raise EvidenceError("preflight browser plan must contain ten unique journeys")
    for index, journey in enumerate(journeys):
        if not isinstance(journey, dict) or journey.get("status") != "passed":
            raise EvidenceError(f"browser journey {index} must be passed")
        scenario = str(journey.get("scenario") or "").upper()
        mode = str(journey.get("mode") or "").upper()
        journey_id = str(journey.get("id") or "")
        planned = expected_plan.get(journey_id)
        if not isinstance(planned, dict):
            raise EvidenceError(f"browser journey {index} is absent from the signed preflight")
        if scenario not in SCENARIOS or mode not in {"HR_DESKTOP", "EMPLOYEE_MOBILE"}:
            raise EvidenceError(f"browser journey {index} has invalid scenario/mode")
        if mode == "EMPLOYEE_MOBILE" and journey.get("viewport") != {"width": 390, "height": 844}:
            raise EvidenceError("every employee browser journey must use the 390x844 viewport")
        if journey.get("consoleErrorCount") != 0:
            raise EvidenceError("browser journeys must have zero console errors")
        required_checks = HR_BROWSER_CHECKS if mode == "HR_DESKTOP" else EMPLOYEE_BROWSER_CHECKS
        if not required_checks <= set(journey.get("checks") or []):
            raise EvidenceError(f"browser journey {scenario}/{mode} misses required checks")
        if mode == "EMPLOYEE_MOBILE":
            for key in ("focusReturnPassed", "deepLinkRecoveryPassed", "loadFailureMessagePassed"):
                if journey.get(key) is not True:
                    raise EvidenceError(f"employee browser journey must pass {key}")
        for key in ("scenario", "mode", "actor", "organization"):
            if journey.get(key) != planned.get(key):
                raise EvidenceError(f"browser journey {journey_id} {key} differs from preflight")
        screenshot_count = _verify_screenshot_manifest(
            journey.get("screenshotManifest"),
            journey_id,
            f"browser journey {index}",
            artifact_path.parent,
            root,
            payload,
        )
        if journey.get("screenshotCount") != screenshot_count:
            raise EvidenceError(f"browser journey {journey_id} screenshotCount is incorrect")
        coverage.add((scenario, mode))
    expected = {(scenario, mode) for scenario in SCENARIOS for mode in ("HR_DESKTOP", "EMPLOYEE_MOBILE")}
    if coverage != expected:
        raise EvidenceError("browser artifact must cover HR and employee views for all five scenarios")
    legacy = payload.get("legacyMenuBoundary")
    if not isinstance(legacy, dict) or legacy.get("status") != "passed":
        raise EvidenceError("browser artifact must include the legacy package menu boundary")
    if (
        legacy.get("actor") != "legacyPackageOnly"
        or legacy.get("menuTreeIds") != [OA_ROOT_MENU_ID, *LEGACY_PACKAGE_MENU_IDS]
        or legacy.get("packageRouteAccessible") is not True
        or legacy.get("taskRouteAccessible") is not False
        or legacy.get("packageBrowserPath") != "/oa/sign-package"
        or legacy.get("taskBrowserPath") != "/oa/sign-task"
    ):
        raise EvidenceError("browser legacy role did not preserve package-only access exactly")
    legacy_count = _verify_screenshot_manifest(
        legacy.get("screenshotManifest"),
        "legacy-package-boundary",
        "browser legacy menu boundary",
        artifact_path.parent,
        root,
        payload,
    )
    if legacy.get("screenshotCount") != legacy_count:
        raise EvidenceError("browser legacy boundary screenshotCount is incorrect")
    if payload.get("checkCount") != len(journeys) + 1:
        raise EvidenceError("browser checkCount must include ten journeys and the legacy boundary")


def _verify_database_legacy_boundary(value: Any, label: str) -> None:
    if not isinstance(value, dict) or value.get("status") != "passed":
        raise EvidenceError(f"{label} must be a passed database assertion")
    if (
        value.get("actor") != "legacyPackageOnly"
        or value.get("oaRootMenuId") != OA_ROOT_MENU_ID
        or value.get("presentMenuIds") != list(LEGACY_PACKAGE_MENU_IDS)
        or value.get("absentMenuIds") != [TASK_CENTER_ROOT_MENU_ID]
        or value.get("packageRoute") != "sign-package"
        or value.get("packageComponent") != "oa/signPackage/index"
        or value.get("taskRoute") != "sign-task"
        or value.get("packageGrantCount") != len(LEGACY_PACKAGE_MENU_IDS)
        or value.get("taskCenterRootGrantCount") != 0
        or not isinstance(value.get("databaseCheckCount"), int)
        or value["databaseCheckCount"] < 2
    ):
        raise EvidenceError(f"{label} does not prove 4520-4526 present and 9650 absent")


def _verify_mysql57(
    payload: dict[str, Any],
    environment: dict[str, Any],
    artifact_path: Path,
    root: Path,
) -> None:
    if not str(payload.get("engineVersion") or "").startswith("5.7."):
        raise EvidenceError("mysql57 artifact must come from MySQL 5.7")
    if payload.get("virtualized") is not False:
        raise EvidenceError("mysql57 artifact must come from the authorized non-virtualized rehearsal database")
    if payload.get("migrationRunCount", 0) < 2:
        raise EvidenceError("mysql57 migrations must be repeated at least twice")
    if payload.get("migrationFailureCount") != 0 or payload.get("coreQueryFailureCount") != 0:
        raise EvidenceError("mysql57 migrations and core queries must have zero failures")
    if payload.get("coreQueryCount", 0) <= 0:
        raise EvidenceError("mysql57 artifact must include core signing queries")
    if payload.get("migrationBundleSha256") != environment["migrationBundleSha256"]:
        raise EvidenceError("mysql57 migration hash does not match final evidence")
    _text(payload.get("authorizedBy"), "mysql57 artifact authorizedBy")
    execution_log = _verified_file_ref(
        payload.get("executionLog"),
        "mysql57 executionLog",
        artifact_path.parent,
        root,
        max_bytes=MAX_ATTESTATION_LOG_BYTES,
    )
    try:
        log_text = execution_log.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise EvidenceError("mysql57 execution log must be UTF-8 text") from exc
    if re.search(r"(?i)(?:authorization:\s*bearer|password\s*=|passwd\s*=|data:image/)", log_text):
        raise EvidenceError("mysql57 execution log contains forbidden credential/signing content")
    attestation_path = _verified_file_ref(
        payload.get("executionAttestation"),
        "mysql57 executionAttestation",
        artifact_path.parent,
        root,
        max_bytes=1024 * 1024,
    )
    try:
        attestation = json.loads(attestation_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise EvidenceError("mysql57 execution attestation must be UTF-8 JSON") from exc
    if not isinstance(attestation, dict):
        raise EvidenceError("mysql57 execution attestation must be an object")
    _reject_sensitive(attestation, "mysql57 executionAttestation")
    expected = {
        "kind": "contract-signing-mysql57-execution-attestation",
        "releaseId": payload.get("releaseId"),
        "runId": payload.get("runId"),
        "candidateCommit": payload.get("candidateCommit"),
        "environmentId": environment["environmentId"],
        "database": environment["database"],
        "databaseFingerprintSha256": environment["databaseFingerprintSha256"],
        "migrationBundleSha256": environment["migrationBundleSha256"],
        "engineVersion": payload.get("engineVersion"),
        "virtualized": False,
        "migrationRunCount": payload.get("migrationRunCount"),
        "migrationFailureCount": 0,
        "coreQueryCount": payload.get("coreQueryCount"),
        "coreQueryFailureCount": 0,
        "authorizedBy": payload.get("authorizedBy"),
        "executionLogSha256": payload["executionLog"]["sha256"],
        "legacyMenuBoundary": payload.get("legacyMenuBoundary"),
    }
    for key, expected_value in expected.items():
        if attestation.get(key) != expected_value:
            raise EvidenceError(f"mysql57 execution attestation {key} does not match")
    _timestamp(attestation.get("completedAt"), "mysql57 execution attestation completedAt")
    _verify_database_legacy_boundary(
        payload.get("legacyMenuBoundary"), "mysql57 legacyMenuBoundary"
    )
    _verify_database_legacy_boundary(
        attestation.get("legacyMenuBoundary"),
        "mysql57 execution attestation legacyMenuBoundary",
    )


def _verify_actors(value: Any, preflight: dict[str, Any]) -> None:
    if not isinstance(value, list) or len(value) != len(REQUIRED_ACTORS):
        raise EvidenceError("actors must contain exactly the required UAT aliases")
    seen: set[str] = set()
    declared = preflight.get("actors")
    if not isinstance(declared, dict) or set(declared) != set(REQUIRED_ACTORS):
        raise EvidenceError("preflight actor declarations are incomplete")
    for actor in value:
        if not isinstance(actor, dict):
            raise EvidenceError("every actor must be an object")
        alias = str(actor.get("alias") or "")
        if alias not in REQUIRED_ACTORS or alias in seen:
            raise EvidenceError(f"unknown or duplicate actor: {alias!r}")
        seen.add(alias)
        if actor.get("kind") != REQUIRED_ACTORS[alias]:
            raise EvidenceError(f"actor {alias} kind is invalid")
        if actor.get("isAdministrator") is not False:
            raise EvidenceError(f"actor {alias} must be a non-administrator")
        if actor.get("permissionMatrixStatus") != "passed":
            raise EvidenceError(f"actor {alias} permission matrix is not passed")
        permissions = set(actor.get("permissions") or [])
        role_keys = set(actor.get("roleKeys") or [])
        expected = declared[alias]
        if list(actor.get("organizations") or []) != list(expected.get("organizations") or []):
            raise EvidenceError(f"actor {alias} organization scope does not match the preflight")
        if role_keys != set(expected.get("expectedRoleKeys") or []):
            raise EvidenceError(f"actor {alias} role keys do not match the preflight")
        if permissions != set(expected.get("expectedPermissions") or []):
            raise EvidenceError(f"actor {alias} permissions do not match the preflight")
        if actor["kind"] == "HR":
            if "sign_single_hr" not in role_keys or TECHNICAL_PERMISSION in permissions:
                raise EvidenceError(f"actor {alias} is not an isolated dedicated HR")
        elif actor["kind"] == "TECHNICAL_EVIDENCE":
            if TECHNICAL_PERMISSION not in permissions or "sign_single_hr" in role_keys:
                raise EvidenceError("technical evidence reviewer must remain independent")
        elif actor["kind"] == "LEGACY_PACKAGE_ONLY":
            if (
                permissions != LEGACY_PACKAGE_PERMISSIONS
                or "sign_single_hr" in role_keys
                or TECHNICAL_PERMISSION in permissions
            ):
                raise EvidenceError("legacy package actor received non-package signing authority")
            menu_ids = actor.get("menuIds")
            if menu_ids != [OA_ROOT_MENU_ID, *LEGACY_PACKAGE_MENU_IDS]:
                raise EvidenceError("legacy package actor must retain exactly 3000 and 4520-4526")
            if TASK_CENTER_ROOT_MENU_ID in menu_ids:
                raise EvidenceError("legacy package actor must not receive task-center root 9650")
            if menu_ids != list(expected.get("expectedMenuIds") or []):
                raise EvidenceError("legacy package actor menu tree differs from signed preflight")
            if list(expected.get("forbiddenMenuIds") or []) != [TASK_CENTER_ROOT_MENU_ID]:
                raise EvidenceError("signed preflight did not forbid task-center root 9650")
    if seen != set(REQUIRED_ACTORS):
        raise EvidenceError("one or more required actors are missing")


def _verify_fixtures(value: Any, preflight: dict[str, Any]) -> dict[str, dict[str, Any]]:
    if not isinstance(value, dict):
        raise EvidenceError("fixtures must be an object")
    entities = value.get("legalEntities")
    plans = value.get("plans")
    if not isinstance(entities, list) or not entities:
        raise EvidenceError("fixtures.legalEntities must be non-empty")
    expected_entities = {
        (organization, str(entity["legalEntityId"])): entity
        for organization, org in (preflight.get("organizations") or {}).items()
        for entity in org.get("legalEntities", [])
    }
    if not expected_entities:
        raise EvidenceError("preflight legal entity declarations are missing")
    covered_entities: set[tuple[str, str]] = set()
    for index, entity in enumerate(entities):
        if not isinstance(entity, dict) or entity.get("status") != "passed":
            raise EvidenceError(f"legal entity fixture {index} must be passed")
        organization = str(entity.get("organization") or "")
        if organization not in {"orgA", "orgB"}:
            raise EvidenceError(f"legal entity fixture {index} organization is invalid")
        entity_id = str(entity.get("legalEntityId") or "")
        key = (organization, entity_id)
        if key not in expected_entities or key in covered_entities:
            raise EvidenceError(f"legal entity fixture {index} is unknown or duplicated")
        covered_entities.add(key)
        if set(entity.get("contractTypes") or []) != CONTRACT_TYPES:
            raise EvidenceError("every legal entity must cover LABOR and SERVICE")
        _hash(entity.get("legalEntityFingerprintSha256"), f"legal entity fixture {index} fingerprint")
        seal_hash = _hash(entity.get("sealFingerprintSha256"), f"legal entity fixture {index} seal fingerprint")
        if str(entity.get("sealId") or "") != str(expected_entities[key]["sealId"]):
            raise EvidenceError(f"legal entity fixture {index} seal id does not match preflight")
        if seal_hash != expected_entities[key]["sealSha256"]:
            raise EvidenceError(f"legal entity fixture {index} seal hash does not match preflight")
    if covered_entities != set(expected_entities):
        raise EvidenceError("legal entity fixtures must match every preflight legal entity")

    if not isinstance(plans, list) or len(plans) != len(SCENARIOS):
        raise EvidenceError("fixtures.plans must contain exactly five published scenario plans")
    result: dict[str, dict[str, Any]] = {}
    for plan in plans:
        if not isinstance(plan, dict) or plan.get("status") != "published":
            raise EvidenceError("every fixture plan must be published")
        scenario = str(plan.get("scenario") or "").upper()
        if scenario not in SCENARIOS or scenario in result:
            raise EvidenceError(f"unknown or duplicate fixture plan: {scenario!r}")
        if str(plan.get("organization") or "") not in {"orgA", "orgB"}:
            raise EvidenceError(f"fixture plan {scenario} organization is invalid")
        if not str(plan.get("planVersionId") or "").isdigit():
            raise EvidenceError(f"fixture plan {scenario} planVersionId must be numeric")
        _hash(plan.get("planVersionSha256"), f"fixture plan {scenario} planVersionSha256")
        hashes = plan.get("templateHashes")
        if not isinstance(hashes, list) or not hashes:
            raise EvidenceError(f"fixture plan {scenario} templateHashes must be non-empty")
        for index, value_hash in enumerate(hashes):
            _hash(value_hash, f"fixture plan {scenario} templateHashes[{index}]")
        _text(plan.get("approvalRef"), f"fixture plan {scenario} approvalRef")
        expected_plan = (preflight.get("scenarios") or {}).get(scenario)
        if not isinstance(expected_plan, dict):
            raise EvidenceError(f"fixture plan {scenario} is absent from preflight")
        for key in ("organization", "planVersionId", "planVersionSha256", "approvalRef"):
            if str(plan.get(key)) != str(expected_plan.get(key)):
                raise EvidenceError(f"fixture plan {scenario} {key} does not match preflight")
        if list(plan.get("templateHashes") or []) != list(expected_plan.get("templateHashes") or []):
            raise EvidenceError(f"fixture plan {scenario} template hashes do not match preflight")
        result[scenario] = plan
    if set(result) != set(SCENARIOS):
        raise EvidenceError("one or more scenario fixture plans are missing")
    return result


def _verify_scenarios(value: Any, plans: dict[str, dict[str, Any]]) -> tuple[int, int, list[datetime]]:
    if not isinstance(value, list) or len(value) != len(SCENARIOS):
        raise EvidenceError("scenarios must contain exactly five cases")
    seen: set[str] = set()
    total_files = 0
    total_pages = 0
    review_times: list[datetime] = []
    for row in value:
        if not isinstance(row, dict):
            raise EvidenceError("every scenario must be an object")
        scenario = str(row.get("id") or "").upper()
        if scenario not in SCENARIOS or scenario in seen:
            raise EvidenceError(f"unknown or duplicate scenario: {scenario!r}")
        seen.add(scenario)
        if row.get("status") != "passed" or set(row.get("evidenceTypes") or []) != {"api", "browser"}:
            raise EvidenceError(f"scenario {scenario} must pass with API and browser evidence")
        if set(row.get("chainSteps") or []) != CHAIN_STEPS:
            raise EvidenceError(f"scenario {scenario} does not prove the complete signing chain")
        if str(row.get("organization") or "") != str(plans[scenario]["organization"]):
            raise EvidenceError(f"scenario {scenario} organization does not match its plan")
        if str(row.get("planVersionId") or "") != str(plans[scenario]["planVersionId"]):
            raise EvidenceError(f"scenario {scenario} plan version does not match its fixture")
        if row.get("contractType") not in CONTRACT_TYPES:
            raise EvidenceError(f"scenario {scenario} contractType is invalid")
        _hash(row.get("taskFingerprintSha256"), f"scenario {scenario} task fingerprint")
        _hash(row.get("packageFingerprintSha256"), f"scenario {scenario} package fingerprint")
        file_count = row.get("finalFileCount")
        verified_count = row.get("verifiedFileCount")
        page_count = row.get("placementReviewedPageCount")
        passed_pages = row.get("placementPassedPageCount")
        if not isinstance(file_count, int) or file_count <= 0 or verified_count != file_count:
            raise EvidenceError(f"scenario {scenario} must verify every final file")
        if not isinstance(page_count, int) or page_count <= 0 or passed_pages != page_count:
            raise EvidenceError(f"scenario {scenario} must pass signature/seal review on every page")
        if row.get("wrongContractCount") != 0:
            raise EvidenceError(f"scenario {scenario} has a wrong contract")
        _text(row.get("reviewer"), f"scenario {scenario}.reviewer")
        review_times.append(_timestamp(row.get("reviewedAt"), f"scenario {scenario}.reviewedAt"))
        total_files += file_count
        total_pages += page_count
    return total_files, total_pages, review_times


def _verify_exception_cases(value: Any) -> list[datetime]:
    if not isinstance(value, list) or len(value) != len(EXCEPTION_CASES):
        raise EvidenceError("exceptionCases must contain exactly all required failure/recovery cases")
    seen: set[str] = set()
    review_times: list[datetime] = []
    for row in value:
        if not isinstance(row, dict):
            raise EvidenceError("every exception case must be an object")
        case_id = str(row.get("id") or "")
        if case_id not in EXCEPTION_CASES or case_id in seen:
            raise EvidenceError(f"unknown or duplicate exception case: {case_id!r}")
        seen.add(case_id)
        if row.get("status") != "passed":
            raise EvidenceError(f"exception case {case_id} is not passed")
        evidence_types = set(row.get("evidenceTypes") or [])
        if not evidence_types or not evidence_types <= {"api", "browser", "mysql57", "fault-injection"}:
            raise EvidenceError(f"exception case {case_id} has invalid evidence types")
        if not isinstance(row.get("checkCount"), int) or row["checkCount"] <= 0:
            raise EvidenceError(f"exception case {case_id} checkCount must be positive")
        _text(row.get("reviewer"), f"exception case {case_id}.reviewer")
        review_times.append(_timestamp(row.get("reviewedAt"), f"exception case {case_id}.reviewedAt"))
    if seen != EXCEPTION_CASES:
        raise EvidenceError("one or more required exception cases are missing")
    return review_times


def _verify_metrics(value: Any, total_files: int, total_pages: int) -> None:
    if not isinstance(value, dict):
        raise EvidenceError("metrics must be an object")
    zero_metrics = {
        "wrongContractCount",
        "duplicateTaskCount",
        "duplicateNotificationCount",
        "technicalEvidenceCrossGrantCount",
        "crossOrganizationUnauthorizedSuccessCount",
    }
    for key in zero_metrics:
        if value.get(key) != 0:
            raise EvidenceError(f"metrics.{key} must equal 0")
    for key in (
        "newFileVerificationRate",
        "signatureSealPlacementPassRate",
        "hrBusinessPermissionPassRate",
    ):
        if value.get(key) != 100:
            raise EvidenceError(f"metrics.{key} must equal 100")
    if value.get("finalFileCount") != total_files or value.get("verifiedFileCount") != total_files:
        raise EvidenceError("metrics final/verified file counts must match scenario totals")
    if value.get("placementReviewedPageCount") != total_pages or value.get("placementPassedPageCount") != total_pages:
        raise EvidenceError("metrics placement page counts must match scenario totals")
    if not isinstance(value.get("notificationRecoveryCount"), int) or value["notificationRecoveryCount"] <= 0:
        raise EvidenceError("metrics.notificationRecoveryCount must be positive")


def verify(
    evidence_path: Path,
    root: Path,
    expected_commit: str | None = None,
    trusted_signer_public_key: str | Path | None = None,
    trusted_signer_fingerprint: str | None = None,
) -> dict[str, Any]:
    if evidence_path.is_symlink():
        raise EvidenceError("UAT evidence must not be a symlink")
    evidence_path = evidence_path.resolve()
    try:
        evidence_path.relative_to((root / "output").resolve())
    except ValueError as exc:
        raise EvidenceError("UAT evidence must be stored under output/") from exc
    try:
        value = json.loads(evidence_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"cannot read contract-signing UAT evidence: {evidence_path}") from exc
    if not isinstance(value, dict):
        raise EvidenceError("UAT evidence must be a JSON object")
    _reject_sensitive(value)
    public_key, trusted_fingerprint = _trusted_public_key(
        trusted_signer_public_key, trusted_signer_fingerprint
    )
    if value.get("trustedSignerPublicKeySha256") != trusted_fingerprint:
        raise EvidenceError("final evidence is not pinned to the independently trusted signer")
    final_attestation = value.get("attestation")
    if not isinstance(final_attestation, dict) or set(final_attestation) != {
        "signaturePath", "signerPublicKeySha256"
    }:
        raise EvidenceError("final evidence attestation must contain its detached signature")
    _verify_signature(
        evidence_path,
        final_attestation.get("signaturePath"),
        final_attestation.get("signerPublicKeySha256"),
        "final evidence attestation",
        evidence_path.parent,
        root,
        public_key,
        trusted_fingerprint,
    )
    if value.get("schemaVersion") != 1 or value.get("releaseId") != RELEASE_ID:
        raise EvidenceError(f"schemaVersion/releaseId must identify {RELEASE_ID}")
    candidate = str(value.get("candidateCommit") or "")
    if not COMMIT.fullmatch(candidate) or candidate == "0" * 40:
        raise EvidenceError("candidateCommit must be a non-placeholder full commit")
    if expected_commit and candidate != expected_commit:
        raise EvidenceError(f"candidateCommit {candidate} does not match release candidate {expected_commit}")
    run_id = _text(value.get("runId"), "runId")
    completed_at = _timestamp(value.get("completedAt"), "completedAt")

    environment = value.get("environment")
    if not isinstance(environment, dict):
        raise EvidenceError("environment must be an object")
    if environment.get("isolated") is not True or environment.get("productionDataCopied") is not False:
        raise EvidenceError("environment must be isolated and must not contain copied production data")
    if str(environment.get("databaseEngine") or "").lower() != "mysql":
        raise EvidenceError("environment.databaseEngine must equal mysql")
    if not str(environment.get("databaseVersion") or "").startswith("5.7."):
        raise EvidenceError("environment.databaseVersion must prove the MySQL 5.7 gate")
    environment["environmentId"] = _text(
        environment.get("environmentId"), "environment.environmentId"
    )
    environment["database"] = _text(environment.get("database"), "environment.database")
    environment["databaseFingerprintSha256"] = _hash(
        environment.get("databaseFingerprintSha256"), "environment.databaseFingerprintSha256"
    )
    environment["migrationBundleSha256"] = _hash(
        environment.get("migrationBundleSha256"), "environment.migrationBundleSha256"
    )
    if environment.get("trustedSignerPublicKeySha256") != trusted_fingerprint:
        raise EvidenceError("environment trusted signer fingerprint does not match")
    if environment.get("organizations") != ["orgA", "orgB"]:
        raise EvidenceError("environment.organizations must equal [orgA, orgB]")

    artifacts = value.get("artifacts")
    if not isinstance(artifacts, dict) or set(artifacts) != set(ARTIFACT_KINDS):
        raise EvidenceError("artifacts must contain exactly preflight, api, browser and mysql57")
    verified_artifacts: dict[str, dict[str, Any]] = {}
    artifact_payloads: dict[str, dict[str, Any]] = {}
    artifact_times: list[datetime] = []
    for name, kind in ARTIFACT_KINDS.items():
        verified, payload = _artifact(
            artifacts[name], f"artifacts.{name}", kind, evidence_path.parent,
            root, RELEASE_ID, run_id, candidate, public_key, trusted_fingerprint,
        )
        verified_artifacts[name] = verified
        artifact_payloads[name] = payload
        artifact_time = _timestamp(payload["completedAt"], f"artifacts.{name}.completedAt")
        artifact_times.append(artifact_time)
        if artifact_time > completed_at:
            raise EvidenceError(f"artifacts.{name} completes after the final evidence")
    _verify_preflight(artifact_payloads["preflight"], environment)
    _verify_api(artifact_payloads["api"], environment)
    _verify_browser(
        artifact_payloads["browser"], artifact_payloads["preflight"],
        Path(verified_artifacts["browser"]["path"]), root,
    )
    _verify_mysql57(
        artifact_payloads["mysql57"], environment,
        Path(verified_artifacts["mysql57"]["path"]), root,
    )

    _verify_actors(value.get("actors"), artifact_payloads["preflight"])
    plans = _verify_fixtures(value.get("fixtures"), artifact_payloads["preflight"])
    total_files, total_pages, scenario_review_times = _verify_scenarios(value.get("scenarios"), plans)
    exception_review_times = _verify_exception_cases(value.get("exceptionCases"))
    _verify_metrics(value.get("metrics"), total_files, total_pages)

    signoff = value.get("signoff")
    if not isinstance(signoff, dict):
        raise EvidenceError("signoff must be an object")
    owners = [
        _text(signoff.get(role), f"signoff.{role}")
        for role in ("hrOwner", "legalOwner", "qaOrBusinessOwner")
    ]
    if len(set(owners)) != 3:
        raise EvidenceError("HR, legal and QA/business signoff must be three distinct people")
    approved_at = _timestamp(signoff.get("approvedAt"), "signoff.approvedAt")
    if approved_at < max(artifact_times + scenario_review_times + exception_review_times):
        raise EvidenceError("signoff.approvedAt must be after all UAT artifacts and reviews")
    if approved_at > completed_at:
        raise EvidenceError("signoff.approvedAt must not be after completedAt")
    if signoff.get("decision") != "approved":
        raise EvidenceError("signoff.decision must equal approved")

    return {
        "releaseId": RELEASE_ID,
        "candidateCommit": candidate,
        "scenarioCount": len(SCENARIOS),
        "exceptionCaseCount": len(EXCEPTION_CASES),
        "finalFileCount": total_files,
        "placementReviewedPageCount": total_pages,
        "artifacts": verified_artifacts,
        "status": "passed",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--candidate-commit")
    parser.add_argument(
        "--trusted-signer-public-key",
        default=os.environ.get("ERP_CONTRACT_SIGN_UAT_TRUSTED_SIGNER_PUBLIC_KEY"),
    )
    parser.add_argument(
        "--trusted-signer-sha256",
        default=os.environ.get("ERP_CONTRACT_SIGN_UAT_TRUSTED_SIGNER_SHA256"),
    )
    args = parser.parse_args()
    root = Path(args.root).resolve()
    commit = args.candidate_commit
    if not commit:
        commit = subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
        ).strip()
    try:
        summary = verify(
            Path(args.evidence),
            root,
            commit,
            args.trusted_signer_public_key,
            args.trusted_signer_sha256,
        )
    except EvidenceError as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print("[PASS] contract-signing UAT evidence is complete, role-safe and artifact-backed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
