#!/usr/bin/env python3
"""Single fail-closed readiness gate for the 20260718 onboarding release.

The repository manifest is an immutable *development source contract*.  Ready
state exists only in two repository-external, detached-signature attestations:
one approves the exact source patch and the other approves immutable build
artifacts plus the seven external gates.  This module composes source-boundary,
signature, evidence, artifact-semantic, and build-provenance verification.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Mapping, Sequence

from verify_onboard_contract_excel_artifacts import verify_artifacts
from verify_onboard_contract_excel_attestations import (
    BUILD_ATTESTATION_ENV,
    BUILD_ATTESTATION_SIGNATURE_ENV,
    SOURCE_APPROVAL_ENV,
    SOURCE_APPROVAL_SIGNATURE_ENV,
    TRUSTED_SIGNER_PUBLIC_KEY_ENV,
    TRUSTED_SIGNER_SHA256_ENV,
    verify_signed_attestations,
)
from verify_onboard_contract_excel_gate_evidence import verify_gate_evidence


EXPECTED_RELEASE_ID = "onboard-contract-excel-20260718"
EXPECTED_PREREQUISITE_ID = "contract-signing-release-a-20260716"
EXPECTED_PREREQUISITE_MANIFEST = "scripts/contract-signing-release-20260716.json"
EXPECTED_PREREQUISITE_REF = "refs/tags/contract-signing-release-a-20260716"
EXPECTED_SOURCE_MANIFEST = "scripts/onboard-contract-excel-release-20260718.json"
EXPECTED_RELEASE_FILE_LIST = "scripts/onboard-contract-excel-release-files-20260718.list"
EXPECTED_DELETED_PATH_LIST = "scripts/onboard-contract-excel-deleted-paths-20260718.list"
EXPECTED_APPROVAL_ANCHORS = {
    "sourceApprovalEnvironmentVariable": SOURCE_APPROVAL_ENV,
    "sourceApprovalSignatureEnvironmentVariable": SOURCE_APPROVAL_SIGNATURE_ENV,
    "buildAttestationEnvironmentVariable": BUILD_ATTESTATION_ENV,
    "buildAttestationSignatureEnvironmentVariable": BUILD_ATTESTATION_SIGNATURE_ENV,
    "trustedSignerPublicKeyEnvironmentVariable": TRUSTED_SIGNER_PUBLIC_KEY_ENV,
    "trustedSignerSha256EnvironmentVariable": TRUSTED_SIGNER_SHA256_ENV,
}
EXPECTED_GATES = {
    "nativeMySqlRehearsal",
    "templateHrLegalApproval",
    "templateRegistrationAndPlanPublish",
    "isolatedThreePersonUat",
    "selected27WorkbookUat",
    "finalPdfHashAuditEvidence",
    "oneScopeGrayApproval",
}
HEX_40 = re.compile(r"^[0-9a-f]{40}$")
HEX_64 = re.compile(r"^[0-9a-f]{64}$")
EVIDENCE_PATH = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._/-]{0,511}$")
MAX_APPROVAL_CLOCK_SKEW = timedelta(minutes=5)
MAX_READINESS_FILE_BYTES = 16 * 1024 * 1024
EVIDENCE_MATCH_FIELDS = (
    "subject",
    "approvedBy",
    "approvedRole",
    "approvedAt",
    "environment",
)
UNTRUSTED_GIT_ENVIRONMENT = {
    "GIT_ALTERNATE_OBJECT_DIRECTORIES",
    "GIT_CEILING_DIRECTORIES",
    "GIT_COMMON_DIR",
    "GIT_CONFIG_GLOBAL",
    "GIT_CONFIG_COUNT",
    "GIT_CONFIG_NOSYSTEM",
    "GIT_CONFIG_PARAMETERS",
    "GIT_CONFIG_SYSTEM",
    "GIT_DIR",
    "GIT_DISCOVERY_ACROSS_FILESYSTEM",
    "GIT_EXEC_PATH",
    "GIT_GLOB_PATHSPECS",
    "GIT_ICASE_PATHSPECS",
    "GIT_INDEX_FILE",
    "GIT_LITERAL_PATHSPECS",
    "GIT_NOGLOB_PATHSPECS",
    "GIT_OBJECT_DIRECTORY",
    "GIT_PREFIX",
    "GIT_REPLACE_REF_BASE",
    "GIT_SHALLOW_FILE",
    "GIT_SUPER_PREFIX",
    "GIT_WORK_TREE",
}


def _sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def _nonblank(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _safe_relative_path(value: object) -> bool:
    if not _nonblank(value):
        return False
    assert isinstance(value, str)
    path = PurePosixPath(value)
    return (
        len(value) <= 1024
        and not path.is_absolute()
        and ".." not in path.parts
        and "." not in path.parts
        and "" not in path.parts
        and "\x00" not in value
        and "\\" not in value
        and all(ord(character) >= 32 and ord(character) != 127 for character in value)
        and path.as_posix() == value
    )


def _safe_evidence_path(value: object) -> bool:
    return (
        _safe_relative_path(value)
        and isinstance(value, str)
        and EVIDENCE_PATH.fullmatch(value) is not None
    )


def _parse_approval_time(value: object) -> datetime | None:
    if not _nonblank(value):
        return None
    assert isinstance(value, str)
    candidate = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(candidate)
    except ValueError:
        return None
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        return None
    return parsed


def _git(root: Path, *arguments: str) -> subprocess.CompletedProcess[bytes]:
    environment = os.environ.copy()
    for key in list(environment):
        if (
            key in UNTRUSTED_GIT_ENVIRONMENT
            or key.startswith("GIT_CONFIG_KEY_")
            or key.startswith("GIT_CONFIG_VALUE_")
        ):
            environment.pop(key, None)
    environment.update(
        {
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_ATTR_NOSYSTEM": "1",
            "GIT_OPTIONAL_LOCKS": "0",
            "LC_ALL": "C",
        }
    )
    return subprocess.run(
        [
            "git",
            "-c",
            "core.fsmonitor=false",
            "-c",
            "core.untrackedCache=false",
            "-c",
            f"core.attributesFile={os.devnull}",
            "-c",
            "core.quotePath=true",
            *arguments,
        ],
        cwd=root,
        capture_output=True,
        check=False,
        env=environment,
    )


def _decode_git_output(payload: bytes) -> str:
    return payload.decode("utf-8", errors="replace").strip()


def _read_worktree_regular_file(
    path: Path,
    label: str,
    blockers: list[str],
    max_bytes: int = MAX_READINESS_FILE_BYTES,
) -> bytes:
    try:
        path_metadata = os.lstat(path)
    except OSError as exc:
        blockers.append(f"{label} cannot be inspected as a regular file: {exc}")
        return b""
    if not stat.S_ISREG(path_metadata.st_mode):
        blockers.append(f"{label} is not a regular non-symlink file")
        return b""
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        blockers.append(f"{label} cannot be opened as a regular file: {exc}")
        return b""
    try:
        metadata = os.fstat(descriptor)
        if (
            not stat.S_ISREG(metadata.st_mode)
            or metadata.st_dev != path_metadata.st_dev
            or metadata.st_ino != path_metadata.st_ino
        ):
            blockers.append(f"{label} changed while it was being opened")
            return b""
        if metadata.st_size < 0 or metadata.st_size > max_bytes:
            blockers.append(f"{label} exceeds the {max_bytes}-byte readiness limit")
            return b""
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(descriptor, min(1024 * 1024, max_bytes - total + 1))
            if not chunk:
                break
            total += len(chunk)
            if total > max_bytes:
                blockers.append(f"{label} exceeds the {max_bytes}-byte readiness limit")
                return b""
            chunks.append(chunk)
        after = os.fstat(descriptor)
        before_identity = (
            metadata.st_dev,
            metadata.st_ino,
            metadata.st_size,
            metadata.st_mtime_ns,
            metadata.st_ctime_ns,
        )
        after_identity = (
            after.st_dev,
            after.st_ino,
            after.st_size,
            after.st_mtime_ns,
            after.st_ctime_ns,
        )
        if total != metadata.st_size or before_identity != after_identity:
            blockers.append(f"{label} changed while it was being read")
            return b""
        return b"".join(chunks)
    except OSError as exc:
        blockers.append(f"{label} cannot be read: {exc}")
        return b""
    finally:
        os.close(descriptor)


def _read_repository_regular_file(
    root: Path,
    relative: str,
    label: str,
    blockers: list[str],
    max_bytes: int = MAX_READINESS_FILE_BYTES,
) -> bytes:
    """Read a repository-contained file without following any path symlink."""

    if not _safe_relative_path(relative):
        blockers.append(f"{label} path must be a safe repository-relative path")
        return b""
    candidate = root.joinpath(*PurePosixPath(relative).parts)
    current = root
    try:
        for part in PurePosixPath(relative).parts:
            current = current / part
            metadata = os.lstat(current)
            if stat.S_ISLNK(metadata.st_mode):
                blockers.append(
                    f"{label} is not a regular non-symlink file; "
                    f"path contains symlink {current}"
                )
                return b""
        resolved = candidate.resolve(strict=True)
        resolved.relative_to(root)
    except (OSError, ValueError) as exc:
        blockers.append(f"{label} cannot be inspected inside the repository: {exc}")
        return b""
    return _read_worktree_regular_file(
        candidate, label, blockers, max_bytes=max_bytes
    )


def _read_head_tracked_regular_blob(
    root: Path,
    relative: str,
    label: str,
    blockers: list[str],
) -> bytes:
    """Read a regular worktree file and bind its bytes to the HEAD blob."""

    worktree_bytes = _read_repository_regular_file(
        root, relative, label, blockers
    )
    literal_pathspec = f":(top,literal){relative}"
    tree_probe = _git(
        root,
        "ls-tree",
        "-z",
        "--full-tree",
        "HEAD",
        "--",
        literal_pathspec,
    )
    if tree_probe.returncode != 0:
        blockers.append(f"{label} HEAD tracking could not be checked")
        return worktree_bytes
    records = [record for record in tree_probe.stdout.split(b"\0") if record]
    if len(records) != 1 or b"\t" not in records[0]:
        blockers.append(f"{label} is not tracked in HEAD as a regular blob")
        return worktree_bytes
    metadata, tracked_path = records[0].split(b"\t", 1)
    metadata_fields = metadata.split()
    if tracked_path != os.fsencode(relative) or len(metadata_fields) != 3:
        blockers.append(f"{label} HEAD entry is not the exact requested path")
        return worktree_bytes
    mode, object_type, object_id = metadata_fields
    if mode not in {b"100644", b"100755"} or object_type != b"blob":
        blockers.append(f"{label} is not tracked in HEAD as a regular blob")
        return worktree_bytes
    blob_probe = _git(root, "cat-file", "blob", os.fsdecode(object_id))
    if blob_probe.returncode != 0:
        blockers.append(f"{label} HEAD blob cannot be read")
        return worktree_bytes
    if worktree_bytes != blob_probe.stdout:
        blockers.append(f"{label} working-tree bytes differ from HEAD")
    return worktree_bytes


def _parse_json_bytes(payload: bytes, label: str, blockers: list[str]) -> dict:
    duplicate_keys: list[str] = []

    def object_pairs(pairs: list[tuple[str, object]]) -> dict:
        result: dict[str, object] = {}
        for key, value in pairs:
            if key in result:
                duplicate_keys.append(key)
            result[key] = value
        return result

    try:
        value = json.loads(payload.decode("utf-8"), object_pairs_hook=object_pairs)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        blockers.append(f"{label} is not valid UTF-8 JSON: {exc}")
        return {}
    if duplicate_keys:
        blockers.append(
            f"{label} contains duplicate JSON keys: "
            + ", ".join(sorted(set(duplicate_keys)))
        )
    if not isinstance(value, dict):
        blockers.append(f"{label} must be a JSON object")
        return {}
    return value


def _read_path_list(
    root: Path,
    manifest: dict,
    field: str,
    blockers: list[str],
) -> set[str]:
    relative = manifest.get(field)
    if not _safe_relative_path(relative):
        blockers.append(f"manifest {field} must be a safe relative path")
        return set()
    assert isinstance(relative, str)
    payload = _read_head_tracked_regular_blob(
        root, relative, f"manifest {field}", blockers
    )
    try:
        lines = payload.decode("utf-8").splitlines()
    except UnicodeDecodeError as exc:
        blockers.append(f"manifest {field} is not UTF-8: {exc}")
        return set()
    result: set[str] = set()
    for line_number, raw in enumerate(lines, start=1):
        value = raw.strip()
        if not value or value.startswith("#"):
            continue
        if not _safe_relative_path(value):
            blockers.append(f"{relative}:{line_number} is not a safe relative path")
            continue
        if value in result:
            blockers.append(f"{relative}:{line_number} duplicates {value}")
        result.add(value)
    return result


def _validate_external_evidence(
    root: Path,
    manifest: dict,
    signed_context: dict,
    source_manifest: dict,
    blockers: list[str],
) -> None:
    pointer = manifest.get("externalGateEvidence")
    if not isinstance(pointer, dict):
        blockers.append("manifest externalGateEvidence must be an object")
        return
    relative = pointer.get("path")
    expected_hash = pointer.get("sha256")
    if not _safe_relative_path(relative):
        blockers.append("external evidence path must be a safe relative path")
        return
    if not isinstance(expected_hash, str) or not HEX_64.fullmatch(expected_hash):
        blockers.append("external evidence index SHA-256 is not pinned")
    assert isinstance(relative, str)
    evidence_bytes = _read_repository_regular_file(
        root, relative, "external evidence index", blockers
    )
    evidence = _parse_json_bytes(evidence_bytes, "external evidence index", blockers)
    if _sha256(evidence_bytes) != expected_hash:
        blockers.append("external evidence index does not match its manifest SHA-256")
    if not evidence:
        return
    if evidence.get("schemaVersion") != 2:
        blockers.append("external evidence index schemaVersion must be 2")
    if evidence.get("releaseId") != EXPECTED_RELEASE_ID:
        blockers.append("external evidence index releaseId is incorrect")
    if evidence.get("status") != "verified":
        blockers.append("external evidence index status is not verified")
    if evidence.get("containsPii") is not False:
        blockers.append("external evidence index must declare containsPii=false")
    gates = evidence.get("gates")
    if not isinstance(gates, dict):
        blockers.append("external evidence index gates must be an object")
        return
    actual_gate_names = set(gates)
    missing = sorted(EXPECTED_GATES - actual_gate_names)
    unexpected = sorted(actual_gate_names - EXPECTED_GATES)
    if missing:
        blockers.append("external evidence index is missing gates: " + ", ".join(missing))
    if unexpected:
        blockers.append(
            "external evidence index has unexpected gates: " + ", ".join(unexpected)
        )
    used_evidence_paths: set[str] = set()
    documents: dict[str, dict] = {}
    for gate_name in sorted(EXPECTED_GATES):
        item = gates.get(gate_name)
        if not isinstance(item, dict):
            blockers.append(f"external evidence {gate_name} must be an object")
            continue
        if item.get("status") != "passed":
            blockers.append(f"external evidence {gate_name} status is not passed")
        evidence_id = item.get("evidenceId")
        if not _safe_evidence_path(evidence_id):
            blockers.append(
                f"external evidence {gate_name} evidenceId must be a safe repository-relative path"
            )
            evidence_id = None
        elif evidence_id in used_evidence_paths:
            blockers.append(
                f"external evidence {gate_name} reuses evidenceId {evidence_id}"
            )
        else:
            assert isinstance(evidence_id, str)
            used_evidence_paths.add(evidence_id)
        evidence_hash = item.get("evidenceSha256")
        if not isinstance(evidence_hash, str) or not HEX_64.fullmatch(evidence_hash):
            blockers.append(f"external evidence {gate_name} evidenceSha256 is invalid")
            evidence_hash = None
        for field in EVIDENCE_MATCH_FIELDS:
            if not _nonblank(item.get(field)):
                blockers.append(f"external evidence {gate_name} {field} is empty")
        approved_at = _parse_approval_time(item.get("approvedAt"))
        if approved_at is None:
            blockers.append(f"external evidence {gate_name} approvedAt is invalid")
        elif approved_at.astimezone(timezone.utc) > (
            datetime.now(timezone.utc) + MAX_APPROVAL_CLOCK_SKEW
        ):
            blockers.append(
                f"external evidence {gate_name} approvedAt is unreasonably in the future"
            )

        if evidence_id is None:
            continue
        actual_label = f"external evidence file for {gate_name}"
        actual_bytes = _read_repository_regular_file(
            root, evidence_id, actual_label, blockers
        )
        actual_hash = _sha256(actual_bytes)
        if evidence_hash is not None and actual_hash != evidence_hash:
            blockers.append(
                f"external evidence {gate_name} evidenceSha256 does not match the evidence file"
            )
        actual = _parse_json_bytes(actual_bytes, actual_label, blockers)
        if not actual:
            continue
        if actual.get("schemaVersion") != 2:
            blockers.append(f"external evidence file {gate_name} schemaVersion must be 2")
        if actual.get("releaseId") != EXPECTED_RELEASE_ID:
            blockers.append(f"external evidence file {gate_name} releaseId is incorrect")
        if actual.get("gate") != gate_name:
            blockers.append(f"external evidence file {gate_name} gate is incorrect")
        if actual.get("status") != "passed":
            blockers.append(f"external evidence file {gate_name} status is not passed")
        if actual.get("containsPii") is not False:
            blockers.append(
                f"external evidence file {gate_name} must declare containsPii=false"
            )
        for field in EVIDENCE_MATCH_FIELDS:
            if actual.get(field) != item.get(field):
                blockers.append(
                    f"external evidence file {gate_name} {field} does not match the index"
                )
        documents[gate_name] = actual

    try:
        verify_gate_evidence(evidence, documents, signed_context, source_manifest)
    except Exception as exc:  # exact v2 business claims must fail closed
        blockers.append(f"external gate evidence v2 validation failed: {exc}")


def _validate_external_gates(manifest: dict, blockers: list[str]) -> None:
    gates = manifest.get("externalGates")
    if not isinstance(gates, dict):
        blockers.append("manifest externalGates must be an object")
        return
    actual_gate_names = set(gates)
    missing = sorted(EXPECTED_GATES - actual_gate_names)
    unexpected = sorted(actual_gate_names - EXPECTED_GATES)
    if missing:
        blockers.append("manifest is missing external gates: " + ", ".join(missing))
    if unexpected:
        blockers.append("manifest has unexpected external gates: " + ", ".join(unexpected))
    for gate_name in sorted(EXPECTED_GATES):
        if gates.get(gate_name) != "passed":
            blockers.append(f"external gate {gate_name} is not passed")


def _validate_source_contract(manifest: dict, blockers: list[str]) -> None:
    """Reject any attempt to turn the tracked source contract into ready state."""

    if manifest.get("schemaVersion") != 1:
        blockers.append("source manifest schemaVersion must be 1")
    if manifest.get("releaseId") != EXPECTED_RELEASE_ID:
        blockers.append("source manifest releaseId is incorrect")
    if manifest.get("status") != "development":
        blockers.append("tracked source manifest status must remain development")
    if manifest.get("approvalAnchors") != EXPECTED_APPROVAL_ANCHORS:
        blockers.append(
            "source manifest approvalAnchors do not name the six signed-attestation inputs"
        )

    gates = manifest.get("externalGates")
    if not isinstance(gates, dict) or set(gates) != EXPECTED_GATES:
        blockers.append("source manifest must contain exactly seven external gates")
    elif any(value != "external-pending" for value in gates.values()):
        blockers.append("tracked source manifest external gates must remain pending")

    prerequisite = manifest.get("prerequisiteRelease")
    if not isinstance(prerequisite, dict):
        blockers.append("source manifest prerequisiteRelease must be an object")
    else:
        expected = {
            "status": "verification-pending",
            "releaseId": EXPECTED_PREREQUISITE_ID,
            "manifestPath": EXPECTED_PREREQUISITE_MANIFEST,
            "releaseRef": EXPECTED_PREREQUISITE_REF,
            "commit": None,
            "manifestSha256": None,
        }
        for key, value in expected.items():
            if prerequisite.get(key) != value:
                blockers.append(
                    f"tracked source manifest prerequisiteRelease.{key} must remain {value!r}"
                )

    source = manifest.get("sourceCandidate")
    if not isinstance(source, dict):
        blockers.append("source manifest sourceCandidate must be an object")
    else:
        expected = {
            "status": "reassembly-pending",
            "baselineReleaseId": EXPECTED_PREREQUISITE_ID,
            "baselineCommit": None,
            "approvedPatchSha256": None,
            "approvedPatchExcludes": [],
            "candidateHeadMustBeVerifiedAtRuntime": True,
            "worktreeMustBeClean": True,
            "diffMustStayWithinReleaseAndDeletedPaths": True,
        }
        for key, value in expected.items():
            if source.get(key) != value:
                blockers.append(
                    f"tracked source manifest sourceCandidate.{key} must remain {value!r}"
                )
        required = source.get("requiredChangedPaths")
        if not isinstance(required, list) or not required:
            blockers.append("source candidate requiredChangedPaths must be non-empty")
        elif any(not _safe_relative_path(value) for value in required):
            blockers.append("source candidate requiredChangedPaths contains an unsafe path")
        elif required != sorted(required) or len(required) != len(set(required)):
            blockers.append("source candidate requiredChangedPaths must be sorted and unique")

    policy = manifest.get("buildPolicy")
    if not isinstance(policy, dict) or policy.get("externalSignedAttestationsRequired") is not True:
        blockers.append("source manifest must require external signed attestations")
    if (
        not isinstance(policy, dict)
        or policy.get("frontendExcelEntryCompiledEnabledRequired") is not True
    ):
        blockers.append(
            "source manifest must require the dedicated frontend Excel entry to be compiled enabled"
        )

    artifacts = manifest.get("artifactEvidence")
    expected_artifact_paths = {
        "oaJar": "docker/erp/modules/oa/jar/erp-modules-oa.jar",
        "systemJar": "docker/erp/modules/system/jar/erp-modules-system.jar",
        "frontendDist": "docker/nginx/html/dist",
    }
    if not isinstance(artifacts, dict) or artifacts.get("status") != "build-pending":
        blockers.append("tracked source manifest artifact evidence must remain build-pending")
    else:
        for artifact_id, expected_path in expected_artifact_paths.items():
            item = artifacts.get(artifact_id)
            hash_field = "treeSha256" if artifact_id == "frontendDist" else "sha256"
            if not isinstance(item, dict) or item.get("path") != expected_path:
                blockers.append(f"source manifest {artifact_id} path is incorrect")
            elif item.get(hash_field) is not None:
                blockers.append(f"tracked source manifest {artifact_id} hash must remain null")

    evidence = manifest.get("externalGateEvidence")
    if not isinstance(evidence, dict):
        blockers.append("source manifest externalGateEvidence must be an object")
    elif evidence.get("sha256") is not None:
        blockers.append("tracked source manifest external evidence hash must remain null")


def _collect_source_boundary(
    root: Path,
    manifest_relative: str,
    blockers: list[str],
) -> dict[str, Any] | None:
    """Collect one complete, HEAD-bound source snapshot without trusting ready data."""

    root = Path(root).resolve()
    manifest_bytes = _read_head_tracked_regular_blob(
        root, manifest_relative, "20260718 source manifest", blockers
    )
    manifest = _parse_json_bytes(
        manifest_bytes, "20260718 source manifest", blockers
    )
    if not manifest:
        return None
    _validate_source_contract(manifest, blockers)
    if manifest.get("releaseFileList") != EXPECTED_RELEASE_FILE_LIST:
        blockers.append("source manifest releaseFileList is incorrect")
    if manifest.get("deletedPathList") != EXPECTED_DELETED_PATH_LIST:
        blockers.append("source manifest deletedPathList is incorrect")

    release_paths = _read_path_list(root, manifest, "releaseFileList", blockers)
    deleted_paths = _read_path_list(root, manifest, "deletedPathList", blockers)
    allowed_paths = release_paths | deleted_paths
    if manifest_relative not in release_paths:
        blockers.append("source manifest is missing from the release boundary")
    for relative in sorted(deleted_paths):
        if (root / relative).exists() or (root / relative).is_symlink():
            blockers.append(f"superseded path still exists in candidate: {relative}")

    source = manifest.get("sourceCandidate")
    required_raw = source.get("requiredChangedPaths") if isinstance(source, dict) else []
    required = (
        {value for value in required_raw if _safe_relative_path(value)}
        if isinstance(required_raw, list)
        else set()
    )
    required_outside = sorted(required - allowed_paths)
    if required_outside:
        blockers.append(
            "requiredChangedPaths escape the release boundary: "
            + ", ".join(required_outside)
        )

    repo_probe = _git(root, "rev-parse", "--show-toplevel")
    if repo_probe.returncode != 0:
        blockers.append("candidate root is not a Git worktree")
        return None
    try:
        repository_root = Path(_decode_git_output(repo_probe.stdout)).resolve()
    except OSError as exc:
        blockers.append(f"candidate Git root cannot be resolved: {exc}")
        return None
    if repository_root != root:
        blockers.append("candidate root is not the Git worktree root")

    status = _git(root, "status", "--porcelain=v1", "--untracked-files=all")
    if status.returncode != 0:
        blockers.append("candidate worktree cleanliness could not be checked")
    elif status.stdout:
        blockers.append("candidate worktree is not clean")
    index_flags = _git(root, "ls-files", "-v", "-z")
    if index_flags.returncode != 0:
        blockers.append("candidate index flags could not be checked")
    else:
        hidden_paths: list[str] = []
        for record in index_flags.stdout.split(b"\0"):
            if not record:
                continue
            if len(record) < 3 or record[1:2] != b" ":
                hidden_paths.append("<malformed-index-record>")
                continue
            if record[:1] != b"H":
                hidden_paths.append(os.fsdecode(record[2:]))
        if hidden_paths:
            blockers.append(
                "candidate index uses skip-worktree/assume-unchanged or nonstandard flags: "
                + ", ".join(sorted(hidden_paths)[:20])
            )

    head_probe = _git(root, "rev-parse", "--verify", "HEAD^{commit}")
    head = _decode_git_output(head_probe.stdout) if head_probe.returncode == 0 else ""
    if HEX_40.fullmatch(head) is None:
        blockers.append("candidate HEAD cannot be resolved")
        return None

    ref_probe = _git(
        root,
        "rev-parse",
        "--verify",
        f"{EXPECTED_PREREQUISITE_REF}^{{commit}}",
    )
    baseline = _decode_git_output(ref_probe.stdout) if ref_probe.returncode == 0 else ""
    if HEX_40.fullmatch(baseline) is None:
        blockers.append("Release-A prerequisite ref cannot be resolved to a commit")
        return None
    if baseline == head:
        blockers.append("Release-A prerequisite commit must not equal candidate HEAD")
    ancestor = _git(root, "merge-base", "--is-ancestor", baseline, head)
    if ancestor.returncode != 0:
        blockers.append("Release-A prerequisite is not an ancestor of candidate HEAD")

    prerequisite_probe = _git(
        root, "show", f"{baseline}:{EXPECTED_PREREQUISITE_MANIFEST}"
    )
    prerequisite_bytes = prerequisite_probe.stdout if prerequisite_probe.returncode == 0 else b""
    if prerequisite_probe.returncode != 0:
        blockers.append("Release-A commit does not contain its prerequisite manifest")
    else:
        prerequisite_manifest = _parse_json_bytes(
            prerequisite_bytes, "Release-A prerequisite manifest", blockers
        )
        if prerequisite_manifest.get("releaseId") != EXPECTED_PREREQUISITE_ID:
            blockers.append("Release-A prerequisite manifest releaseId is incorrect")
        if prerequisite_manifest.get("status") != "ready":
            blockers.append("Release-A prerequisite manifest status is not ready")

    changed_probe = _git(
        root, "diff", "--no-renames", "--name-only", "-z", baseline, head, "--"
    )
    changed_paths: set[str] = set()
    if changed_probe.returncode != 0:
        blockers.append("candidate changed paths could not be calculated")
    else:
        changed_paths = {
            os.fsdecode(value) for value in changed_probe.stdout.split(b"\0") if value
        }
        if not changed_paths:
            blockers.append("candidate diff from Release-A is empty")
        outside = sorted(changed_paths - allowed_paths)
        if outside:
            blockers.append(
                "candidate diff escapes the release boundary: " + ", ".join(outside)
            )
        missing_required = sorted(required - changed_paths)
        if missing_required:
            blockers.append(
                "candidate diff is missing required changed paths: "
                + ", ".join(missing_required)
            )

    patch_probe = _git(
        root,
        "diff",
        "--no-renames",
        "--binary",
        "--full-index",
        "--no-ext-diff",
        "--no-textconv",
        "--no-color",
        "--src-prefix=a/",
        "--dst-prefix=b/",
        "--unified=3",
        "--diff-algorithm=myers",
        "--no-indent-heuristic",
        "--submodule=short",
        f"-O{os.devnull}",
        baseline,
        head,
        "--",
        ".",
    )
    if patch_probe.returncode != 0:
        blockers.append("candidate approved patch could not be calculated")
        return None

    prerequisite = {
        "releaseId": EXPECTED_PREREQUISITE_ID,
        "releaseRef": EXPECTED_PREREQUISITE_REF,
        "commit": baseline,
        "manifestSha256": _sha256(prerequisite_bytes),
    }
    return {
        "manifestBytes": manifest_bytes,
        "manifest": manifest,
        "candidateCommit": head,
        "actualPatchSha256": _sha256(patch_probe.stdout),
        "prerequisiteRelease": prerequisite,
        "releasePaths": sorted(release_paths),
        "deletedPaths": sorted(deleted_paths),
        "changedPaths": sorted(changed_paths),
    }


def _validate_signed_context(
    source: dict[str, Any],
    signed: dict[str, Any],
    blockers: list[str],
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]] | None:
    """Bind signed output paths back to the immutable source contract."""

    manifest = source["manifest"]
    if signed.get("releaseId") != EXPECTED_RELEASE_ID:
        blockers.append("signed release context releaseId is incorrect")
    if signed.get("candidateCommit") != source["candidateCommit"]:
        blockers.append("signed release context candidate commit changed")
    if signed.get("approvedPatchSha256") != source["actualPatchSha256"]:
        blockers.append("signed release context patch hash changed")
    if signed.get("approvedSourceManifestSha256") != _sha256(source["manifestBytes"]):
        blockers.append("signed release context source manifest hash changed")
    if signed.get("prerequisiteRelease") != source["prerequisiteRelease"]:
        blockers.append("signed Release-A prerequisite binding changed")

    artifacts = signed.get("artifactEvidence")
    source_artifacts = manifest.get("artifactEvidence")
    if not isinstance(artifacts, dict) or artifacts.get("status") != "verified":
        blockers.append("signed artifact evidence is not verified")
        return None
    if not isinstance(source_artifacts, dict):
        blockers.append("source artifact path contract is missing")
        return None
    result: dict[str, dict[str, str]] = {}
    for artifact_id, hash_field in (
        ("oaJar", "sha256"),
        ("systemJar", "sha256"),
        ("frontendDist", "treeSha256"),
    ):
        item = artifacts.get(artifact_id)
        source_item = source_artifacts.get(artifact_id)
        if not isinstance(item, dict) or not isinstance(source_item, dict):
            blockers.append(f"signed {artifact_id} evidence is missing")
            continue
        if item.get("path") != source_item.get("path"):
            blockers.append(f"signed {artifact_id} path differs from the source contract")
        value = item.get(hash_field)
        if not isinstance(value, str) or HEX_64.fullmatch(value) is None:
            blockers.append(f"signed {artifact_id} hash is invalid")
        result[artifact_id] = item

    pointer = signed.get("externalGateEvidence")
    if not isinstance(pointer, dict):
        blockers.append("signed external evidence pointer is missing")
    elif not _safe_relative_path(pointer.get("path")):
        blockers.append("signed external evidence path is unsafe")
    elif not isinstance(pointer.get("sha256"), str) or HEX_64.fullmatch(
        pointer["sha256"]
    ) is None:
        blockers.append("signed external evidence hash is invalid")

    _validate_external_gates(signed, blockers)
    if len(result) != 3 or not isinstance(pointer, dict):
        return None
    return result["oaJar"], result["systemJar"], result["frontendDist"]


def verify_readiness(
    root: Path,
    manifest_path: Path,
    environ: Mapping[str, str] | None = None,
) -> list[str]:
    """Return all readiness blockers; an empty list means the entire gate passed."""

    root = Path(root).resolve()
    blockers: list[str] = []
    candidate_manifest = Path(manifest_path)
    if not candidate_manifest.is_absolute():
        candidate_manifest = root / candidate_manifest
    try:
        normalized_manifest = candidate_manifest.resolve()
    except OSError as exc:
        blockers.append(f"20260718 source manifest path cannot be resolved: {exc}")
        return blockers
    expected_manifest = (root / EXPECTED_SOURCE_MANIFEST).resolve()
    if normalized_manifest != expected_manifest:
        blockers.append(
            "readiness must use the exact HEAD-tracked 20260718 source manifest"
        )
        return blockers

    environment = os.environ if environ is None else environ
    source_stage_start = len(blockers)
    source = _collect_source_boundary(root, EXPECTED_SOURCE_MANIFEST, blockers)
    if source is None or len(blockers) != source_stage_start:
        return blockers

    try:
        signed = verify_signed_attestations(
            root,
            source["manifestBytes"],
            source["candidateCommit"],
            source["prerequisiteRelease"],
            source["actualPatchSha256"],
            environment,
        )
    except Exception as exc:  # fail closed on malformed external trust inputs
        blockers.append(f"signed release attestations failed: {exc}")
        return blockers

    signed_artifacts = _validate_signed_context(source, signed, blockers)
    _validate_external_evidence(
        root,
        {"externalGateEvidence": signed.get("externalGateEvidence")},
        signed,
        source["manifest"],
        blockers,
    )
    if signed_artifacts is None or blockers:
        return blockers

    oa, system, frontend = signed_artifacts
    try:
        verify_artifacts(
            root / oa["path"],
            root / system["path"],
            root / frontend["path"],
            oa["sha256"],
            system["sha256"],
            frontend["treeSha256"],
            expected_candidate_commit=source["candidateCommit"],
            expected_patch_sha256=source["actualPatchSha256"],
            expected_source_manifest_sha256=_sha256(source["manifestBytes"]),
        )
    except Exception as exc:  # artifact parsing must also fail closed
        blockers.append(f"immutable artifact verification failed: {exc}")
        return blockers

    # Re-read every source binding and signed artifact hash after semantic
    # verification.  A path swap during the expensive scan cannot inherit the
    # result produced for the earlier bytes.
    second_stage_start = len(blockers)
    second_source = _collect_source_boundary(
        root, EXPECTED_SOURCE_MANIFEST, blockers
    )
    if second_source is None or len(blockers) != second_stage_start:
        return blockers
    if second_source != source:
        blockers.append("source boundary changed during readiness verification")
        return blockers
    try:
        second_signed = verify_signed_attestations(
            root,
            second_source["manifestBytes"],
            second_source["candidateCommit"],
            second_source["prerequisiteRelease"],
            second_source["actualPatchSha256"],
            environment,
        )
    except Exception as exc:
        blockers.append(f"signed release attestations changed or failed on recheck: {exc}")
        return blockers
    if second_signed != signed:
        blockers.append("signed release context changed during readiness verification")
        return blockers
    _validate_external_evidence(
        root,
        {"externalGateEvidence": second_signed.get("externalGateEvidence")},
        second_signed,
        second_source["manifest"],
        blockers,
    )
    return blockers


def _parse_args(argv: Sequence[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify signed source, evidence, and immutable artifacts for 20260718"
    )
    default_root = Path(__file__).resolve().parent.parent
    parser.add_argument("--root", type=Path, default=default_root)
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("scripts/onboard-contract-excel-release-20260718.json"),
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = _parse_args(argv)
    blockers = verify_readiness(args.root, args.manifest)
    if blockers:
        for blocker in blockers:
            print(f"[BLOCKED] {blocker}", file=sys.stderr)
        return 5
    print("ONBOARD_CONTRACT_EXCEL_SIGNED_READINESS_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
