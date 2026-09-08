#!/usr/bin/env python3
"""Verify externally signed source approval and immutable-build attestation.

The trust anchor is deliberately supplied outside the repository: one absolute
public-key path plus an independently pinned SHA-256.  Neither a hash written
inside an attestation nor a repository-controlled key can establish trust.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import subprocess
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Mapping


RELEASE_ID = "onboard-contract-excel-20260718"
PREREQUISITE_RELEASE_ID = "contract-signing-release-a-20260716"
PREREQUISITE_RELEASE_REF = "refs/tags/contract-signing-release-a-20260716"

SOURCE_APPROVAL_ENV = "ONBOARD_CONTRACT_EXCEL_SOURCE_APPROVAL"
SOURCE_APPROVAL_SIGNATURE_ENV = (
    "ONBOARD_CONTRACT_EXCEL_SOURCE_APPROVAL_SIGNATURE"
)
BUILD_ATTESTATION_ENV = "ONBOARD_CONTRACT_EXCEL_BUILD_ATTESTATION"
BUILD_ATTESTATION_SIGNATURE_ENV = (
    "ONBOARD_CONTRACT_EXCEL_BUILD_ATTESTATION_SIGNATURE"
)
TRUSTED_SIGNER_PUBLIC_KEY_ENV = (
    "ONBOARD_CONTRACT_EXCEL_TRUSTED_SIGNER_PUBLIC_KEY"
)
TRUSTED_SIGNER_SHA256_ENV = "ONBOARD_CONTRACT_EXCEL_TRUSTED_SIGNER_SHA256"

EXPECTED_EXTERNAL_GATES = {
    "nativeMySqlRehearsal",
    "templateHrLegalApproval",
    "templateRegistrationAndPlanPublish",
    "isolatedThreePersonUat",
    "selected27WorkbookUat",
    "finalPdfHashAuditEvidence",
    "oneScopeGrayApproval",
}

SOURCE_APPROVAL_KEYS = {
    "schemaVersion",
    "kind",
    "status",
    "releaseId",
    "releaseARef",
    "releaseACommit",
    "releaseAManifestSha256",
    "candidateCommit",
    "approvedPatchSha256",
    "approvedSourceManifestSha256",
    "signerPublicKeySha256",
    "approvedBy",
    "approvedRole",
    "approvedAt",
    "containsPii",
}
BUILD_ATTESTATION_KEYS = {
    "schemaVersion",
    "kind",
    "status",
    "releaseId",
    "sourceApprovalSha256",
    "releaseARef",
    "releaseACommit",
    "releaseAManifestSha256",
    "candidateCommit",
    "approvedPatchSha256",
    "approvedSourceManifestSha256",
    "signerPublicKeySha256",
    "oaJar",
    "systemJar",
    "frontendDist",
    "externalGateEvidence",
    "externalGates",
    "buildToolchain",
    "builtAt",
    "approvedBy",
    "approvedRole",
    "approvedAt",
    "containsPii",
}
FILE_ARTIFACT_KEYS = {"path", "sha256"}
FRONTEND_ARTIFACT_KEYS = {"path", "treeSha256"}
TOOLCHAIN_KEYS = {"javaVersion", "mavenVersion", "nodeVersion", "npmVersion"}

SHA256 = re.compile(r"^[0-9a-f]{64}$")
GIT_COMMIT = re.compile(r"^[0-9a-f]{40}$")
PLACEHOLDER = re.compile(
    r"^(?:todo|tbd|pending|unknown|none|null|n/a|待定|未填写|示例|审批人|角色)$",
    re.IGNORECASE,
)
MAX_CLOCK_SKEW = timedelta(minutes=5)
MAX_JSON_BYTES = 1024 * 1024
MAX_SIGNATURE_BYTES = 16 * 1024
MAX_PUBLIC_KEY_BYTES = 1024 * 1024
MAX_ARTIFACT_BYTES = 4 * 1024 * 1024 * 1024
MAX_DIST_FILES = 100_000
MAX_DIST_BYTES = 4 * 1024 * 1024 * 1024


class AttestationError(ValueError):
    """A signed release attestation failed closed."""


def _fail(message: str) -> None:
    raise AttestationError(message)


def _sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def _require_hash(value: object, label: str) -> str:
    if not isinstance(value, str) or SHA256.fullmatch(value) is None:
        _fail(f"{label} must be a lowercase SHA-256")
    if len(set(value)) == 1:
        _fail(f"{label} must not be a placeholder SHA-256")
    return value


def _require_commit(value: object, label: str) -> str:
    if not isinstance(value, str) or GIT_COMMIT.fullmatch(value) is None:
        _fail(f"{label} must be a full lowercase Git commit")
    if len(set(value)) == 1:
        _fail(f"{label} must not be a placeholder commit")
    return value


def _require_text(value: object, label: str) -> str:
    if not isinstance(value, str):
        _fail(f"{label} must be non-placeholder text")
    result = value.strip()
    if (
        not result
        or len(result) > 512
        or PLACEHOLDER.fullmatch(result) is not None
        or any(ord(character) < 32 or ord(character) == 127 for character in result)
    ):
        _fail(f"{label} must be non-placeholder text")
    return result


def _require_time(value: object, label: str) -> datetime:
    text = _require_text(value, label)
    candidate = text[:-1] + "+00:00" if text.endswith("Z") else text
    try:
        parsed = datetime.fromisoformat(candidate)
    except ValueError as exc:
        raise AttestationError(f"{label} must be ISO-8601") from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        _fail(f"{label} must include a timezone")
    normalized = parsed.astimezone(timezone.utc)
    if normalized > datetime.now(timezone.utc) + MAX_CLOCK_SKEW:
        _fail(f"{label} must not be in the future")
    return normalized


def _safe_relative_path(value: object, label: str) -> str:
    if not isinstance(value, str) or not value or len(value) > 1024:
        _fail(f"{label} must be a safe relative path")
    path = PurePosixPath(value)
    if (
        path.is_absolute()
        or path.as_posix() != value
        or any(part in {"", ".", ".."} for part in path.parts)
        or "\\" in value
        or "\x00" in value
        or any(ord(character) < 32 or ord(character) == 127 for character in value)
    ):
        _fail(f"{label} must be a safe relative path")
    return value


def _require_exact_keys(value: object, expected: set[str], label: str) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) != expected:
        missing = sorted(expected - set(value)) if isinstance(value, dict) else sorted(expected)
        extra = sorted(set(value) - expected) if isinstance(value, dict) else []
        _fail(f"{label} keys are not exact (missing={missing}, extra={extra})")
    return value


def _stable_regular_bytes(path: Path, label: str, max_bytes: int) -> bytes:
    try:
        listed = path.lstat()
    except OSError as exc:
        raise AttestationError(f"{label} cannot be inspected: {path}") from exc
    if stat.S_ISLNK(listed.st_mode) or not stat.S_ISREG(listed.st_mode):
        _fail(f"{label} must be a non-symlink regular file")
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise AttestationError(f"{label} cannot be opened safely: {path}") from exc
    try:
        before = os.fstat(descriptor)
        if (
            not stat.S_ISREG(before.st_mode)
            or (before.st_dev, before.st_ino) != (listed.st_dev, listed.st_ino)
            or before.st_size <= 0
            or before.st_size > max_bytes
        ):
            _fail(f"{label} has an invalid type or size")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(descriptor, min(1024 * 1024, max_bytes - total + 1))
            if not chunk:
                break
            total += len(chunk)
            if total > max_bytes:
                _fail(f"{label} exceeds its byte budget")
            chunks.append(chunk)
        after = os.fstat(descriptor)
        identity_before = (
            before.st_dev,
            before.st_ino,
            before.st_size,
            before.st_mtime_ns,
            before.st_ctime_ns,
        )
        identity_after = (
            after.st_dev,
            after.st_ino,
            after.st_size,
            after.st_mtime_ns,
            after.st_ctime_ns,
        )
        if total != before.st_size or identity_before != identity_after:
            _fail(f"{label} changed while being read")
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def _external_path(
    root: Path,
    environ: Mapping[str, str],
    variable: str,
    label: str,
    max_bytes: int,
) -> tuple[Path, bytes]:
    value = environ.get(variable)
    if not isinstance(value, str) or not value.strip():
        _fail(f"{variable} is required")
    candidate = Path(value).expanduser()
    if not candidate.is_absolute():
        _fail(f"{label} path must be absolute")
    try:
        resolved = candidate.resolve(strict=True)
    except OSError as exc:
        raise AttestationError(f"{label} path does not exist") from exc
    try:
        resolved.relative_to(root)
    except ValueError:
        pass
    else:
        _fail(f"{label} must be stored outside the repository")
    return resolved, _stable_regular_bytes(candidate, label, max_bytes)


def _parse_json(payload: bytes, label: str) -> dict[str, Any]:
    def object_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                _fail(f"{label} contains duplicate JSON key {key}")
            result[key] = value
        return result

    try:
        value = json.loads(payload.decode("utf-8"), object_pairs_hook=object_pairs)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AttestationError(f"{label} must be valid UTF-8 JSON") from exc
    if not isinstance(value, dict):
        _fail(f"{label} must be a JSON object")
    return value


def _write_snapshot(path: Path, payload: bytes) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        offset = 0
        while offset < len(payload):
            offset += os.write(descriptor, payload[offset:])
    finally:
        os.close(descriptor)


def _verify_detached_signature(
    public_key: bytes,
    payload: bytes,
    signature: bytes,
    label: str,
) -> None:
    with tempfile.TemporaryDirectory(prefix="onboard-attestation-") as temporary:
        directory = Path(temporary)
        key_path = directory / "trusted-public-key.pem"
        payload_path = directory / "payload.json"
        signature_path = directory / "payload.sig"
        _write_snapshot(key_path, public_key)
        _write_snapshot(payload_path, payload)
        _write_snapshot(signature_path, signature)
        try:
            result = subprocess.run(
                [
                    "openssl",
                    "dgst",
                    "-sha256",
                    "-verify",
                    str(key_path),
                    "-signature",
                    str(signature_path),
                    str(payload_path),
                ],
                capture_output=True,
                text=True,
                check=False,
            )
        except OSError as exc:
            raise AttestationError("OpenSSL is required for attestation verification") from exc
    if result.returncode != 0:
        _fail(f"{label} detached signature is invalid")


def _artifact_path(root: Path, value: object, label: str) -> tuple[str, Path]:
    relative = _safe_relative_path(value, f"{label}.path")
    candidate = root.joinpath(*PurePosixPath(relative).parts)
    try:
        resolved = candidate.resolve(strict=True)
        resolved.relative_to(root)
    except (OSError, ValueError) as exc:
        raise AttestationError(f"{label}.path escapes or is missing from the repository") from exc
    return relative, candidate


def _file_sha(path: Path, label: str) -> str:
    try:
        listed = path.lstat()
    except OSError as exc:
        raise AttestationError(f"{label} cannot be inspected: {path}") from exc
    if stat.S_ISLNK(listed.st_mode) or not stat.S_ISREG(listed.st_mode):
        _fail(f"{label} must be a non-symlink regular file")
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as exc:
        raise AttestationError(f"{label} cannot be opened safely: {path}") from exc
    try:
        before = os.fstat(descriptor)
        if (
            not stat.S_ISREG(before.st_mode)
            or (before.st_dev, before.st_ino) != (listed.st_dev, listed.st_ino)
            or before.st_size <= 0
            or before.st_size > MAX_ARTIFACT_BYTES
        ):
            _fail(f"{label} has an invalid type or size")
        digest = hashlib.sha256()
        total = 0
        while True:
            block = os.read(
                descriptor,
                min(1024 * 1024, MAX_ARTIFACT_BYTES - total + 1),
            )
            if not block:
                break
            total += len(block)
            if total > MAX_ARTIFACT_BYTES:
                _fail(f"{label} exceeds its byte budget")
            digest.update(block)
        after = os.fstat(descriptor)
        identity_before = (
            before.st_dev,
            before.st_ino,
            before.st_size,
            before.st_mtime_ns,
            before.st_ctime_ns,
        )
        identity_after = (
            after.st_dev,
            after.st_ino,
            after.st_size,
            after.st_mtime_ns,
            after.st_ctime_ns,
        )
        if total != before.st_size or identity_before != identity_after:
            _fail(f"{label} changed while being hashed")
        return digest.hexdigest()
    finally:
        os.close(descriptor)


def _directory_tree_sha(path: Path, label: str) -> str:
    try:
        root_stat = path.lstat()
    except OSError as exc:
        raise AttestationError(f"{label} is missing") from exc
    if stat.S_ISLNK(root_stat.st_mode) or not stat.S_ISDIR(root_stat.st_mode):
        _fail(f"{label} must be a non-symlink directory")
    inventory: list[tuple[str, Path, int]] = []
    total = 0
    stack: list[tuple[Path, PurePosixPath]] = [(path, PurePosixPath())]
    while stack:
        directory, relative_directory = stack.pop()
        try:
            with os.scandir(directory) as entries:
                ordered = sorted(entries, key=lambda entry: entry.name)
        except OSError as exc:
            raise AttestationError(f"{label} cannot be scanned") from exc
        for entry in ordered:
            relative = relative_directory / entry.name
            metadata = entry.stat(follow_symlinks=False)
            if stat.S_ISLNK(metadata.st_mode):
                _fail(f"{label} contains a symlink: {relative.as_posix()}")
            if stat.S_ISDIR(metadata.st_mode):
                stack.append((Path(entry.path), relative))
            elif stat.S_ISREG(metadata.st_mode):
                inventory.append((relative.as_posix(), Path(entry.path), metadata.st_size))
                total += metadata.st_size
                if len(inventory) > MAX_DIST_FILES or total > MAX_DIST_BYTES:
                    _fail(f"{label} exceeds its file or byte budget")
            else:
                _fail(f"{label} contains a non-regular entry: {relative.as_posix()}")
    if not inventory:
        _fail(f"{label} must not be empty")
    digest = hashlib.sha256()
    for relative, file_path, _ in sorted(inventory):
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(bytes.fromhex(_file_sha(file_path, f"{label}/{relative}")))
    return digest.hexdigest()


def _verify_file_reference(root: Path, value: object, label: str) -> dict[str, str]:
    item = _require_exact_keys(value, FILE_ARTIFACT_KEYS, label)
    relative, path = _artifact_path(root, item["path"], label)
    expected = _require_hash(item["sha256"], f"{label}.sha256")
    if _file_sha(path, label) != expected:
        _fail(f"{label}.sha256 does not match the repository artifact")
    return {"path": relative, "sha256": expected}


def _verify_frontend_reference(root: Path, value: object) -> dict[str, str]:
    item = _require_exact_keys(value, FRONTEND_ARTIFACT_KEYS, "frontendDist")
    relative, path = _artifact_path(root, item["path"], "frontendDist")
    expected = _require_hash(item["treeSha256"], "frontendDist.treeSha256")
    if _directory_tree_sha(path, "frontendDist") != expected:
        _fail("frontendDist.treeSha256 does not match the repository artifact")
    return {"path": relative, "treeSha256": expected}


def _source_bindings(
    source: dict[str, Any],
    release_id: str,
    prerequisite: Mapping[str, object],
    candidate_commit: str,
    patch_hash: str,
    source_manifest_hash: str,
    signer_hash: str,
) -> datetime:
    _require_exact_keys(source, SOURCE_APPROVAL_KEYS, "source approval")
    if type(source["schemaVersion"]) is not int or source["schemaVersion"] != 1:
        _fail("source approval schemaVersion must be the integer 1")
    if source["containsPii"] is not False:
        _fail("source approval containsPii must be exactly false")
    expected = {
        "kind": "onboard-contract-excel-source-approval",
        "status": "approved",
        "releaseId": release_id,
        "releaseARef": prerequisite["releaseRef"],
        "releaseACommit": prerequisite["commit"],
        "releaseAManifestSha256": prerequisite["manifestSha256"],
        "candidateCommit": candidate_commit,
        "approvedPatchSha256": patch_hash,
        "approvedSourceManifestSha256": source_manifest_hash,
        "signerPublicKeySha256": signer_hash,
    }
    for key, value in expected.items():
        if source.get(key) != value:
            _fail(f"source approval {key} is not bound to the verified release")
    _require_text(source["approvedBy"], "source approval approvedBy")
    _require_text(source["approvedRole"], "source approval approvedRole")
    return _require_time(source["approvedAt"], "source approval approvedAt")


def _build_bindings(
    root: Path,
    build: dict[str, Any],
    source: dict[str, Any],
    source_approval_hash: str,
    signer_hash: str,
    source_approved_at: datetime,
) -> tuple[dict[str, Any], datetime, datetime]:
    _require_exact_keys(build, BUILD_ATTESTATION_KEYS, "build attestation")
    if type(build["schemaVersion"]) is not int or build["schemaVersion"] != 1:
        _fail("build attestation schemaVersion must be the integer 1")
    if build["containsPii"] is not False:
        _fail("build attestation containsPii must be exactly false")
    expected = {
        "kind": "onboard-contract-excel-build-attestation",
        "status": "approved",
        "releaseId": source["releaseId"],
        "sourceApprovalSha256": source_approval_hash,
        "releaseARef": source["releaseARef"],
        "releaseACommit": source["releaseACommit"],
        "releaseAManifestSha256": source["releaseAManifestSha256"],
        "candidateCommit": source["candidateCommit"],
        "approvedPatchSha256": source["approvedPatchSha256"],
        "approvedSourceManifestSha256": source["approvedSourceManifestSha256"],
        "signerPublicKeySha256": signer_hash,
    }
    for key, value in expected.items():
        if build.get(key) != value:
            _fail(f"build attestation {key} is inconsistent with source approval")

    oa = _verify_file_reference(root, build["oaJar"], "oaJar")
    system = _verify_file_reference(root, build["systemJar"], "systemJar")
    frontend = _verify_frontend_reference(root, build["frontendDist"])
    evidence = _verify_file_reference(
        root, build["externalGateEvidence"], "externalGateEvidence"
    )

    gates = _require_exact_keys(
        build["externalGates"], EXPECTED_EXTERNAL_GATES, "externalGates"
    )
    if any(value != "passed" for value in gates.values()):
        _fail("externalGates must contain exactly seven passed gates")

    toolchain = _require_exact_keys(
        build["buildToolchain"], TOOLCHAIN_KEYS, "buildToolchain"
    )
    for key in sorted(TOOLCHAIN_KEYS):
        _require_text(toolchain[key], f"buildToolchain.{key}")

    built_at = _require_time(build["builtAt"], "build attestation builtAt")
    approved_at = _require_time(build["approvedAt"], "build attestation approvedAt")
    _require_text(build["approvedBy"], "build attestation approvedBy")
    _require_text(build["approvedRole"], "build attestation approvedRole")
    if source_approved_at > built_at:
        _fail("build attestation builtAt precedes source approval")
    if built_at > approved_at:
        _fail("build attestation approvedAt precedes builtAt")

    return (
        {
            "status": "verified",
            "oaJar": oa,
            "systemJar": system,
            "frontendDist": frontend,
            "externalGateEvidence": evidence,
            "externalGates": dict(gates),
            "buildToolchain": dict(toolchain),
        },
        built_at,
        approved_at,
    )


def verify_attestations(
    root: Path | str,
    source_manifest_bytes: bytes,
    candidate_commit: str,
    prerequisite: Mapping[str, object],
    actual_patch_hash: str,
    environ: Mapping[str, str],
) -> dict[str, Any]:
    """Return a signature-verified release context or raise ``ValueError``.

    ``source_manifest_bytes`` must be the exact bytes whose independent hash was
    approved; callers must not re-serialize the manifest before invoking this API.
    """

    repository = Path(root).resolve(strict=True)
    if not repository.is_dir():
        _fail("release root must be a directory")
    if not isinstance(source_manifest_bytes, bytes) or not source_manifest_bytes:
        _fail("source manifest raw bytes are required")
    source_manifest = _parse_json(source_manifest_bytes, "source manifest")
    release_id = source_manifest.get("releaseId")
    if release_id != RELEASE_ID:
        _fail(f"source manifest releaseId must equal {RELEASE_ID}")
    source_manifest_hash = _sha256(source_manifest_bytes)
    candidate = _require_commit(candidate_commit, "candidateCommit")
    patch_hash = _require_hash(actual_patch_hash, "actual patch SHA-256")

    if not isinstance(prerequisite, Mapping):
        _fail("prerequisite release binding must be an object")
    if prerequisite.get("releaseId") != PREREQUISITE_RELEASE_ID:
        _fail("prerequisite releaseId is incorrect")
    if prerequisite.get("releaseRef") != PREREQUISITE_RELEASE_REF:
        _fail("prerequisite releaseRef is incorrect")
    release_a_commit = _require_commit(
        prerequisite.get("commit"), "prerequisite commit"
    )
    release_a_manifest_hash = _require_hash(
        prerequisite.get("manifestSha256"), "prerequisite manifest SHA-256"
    )
    prerequisite_binding: dict[str, object] = {
        "releaseId": PREREQUISITE_RELEASE_ID,
        "releaseRef": PREREQUISITE_RELEASE_REF,
        "commit": release_a_commit,
        "manifestSha256": release_a_manifest_hash,
    }

    key_path, public_key = _external_path(
        repository,
        environ,
        TRUSTED_SIGNER_PUBLIC_KEY_ENV,
        "trusted signer public key",
        MAX_PUBLIC_KEY_BYTES,
    )
    signer_hash = _require_hash(
        environ.get(TRUSTED_SIGNER_SHA256_ENV), "trusted signer SHA-256"
    )
    if _sha256(public_key) != signer_hash:
        _fail("trusted signer public key does not match the independently pinned SHA-256")

    source_path, source_bytes = _external_path(
        repository,
        environ,
        SOURCE_APPROVAL_ENV,
        "source approval",
        MAX_JSON_BYTES,
    )
    source_signature_path, source_signature = _external_path(
        repository,
        environ,
        SOURCE_APPROVAL_SIGNATURE_ENV,
        "source approval signature",
        MAX_SIGNATURE_BYTES,
    )
    build_path, build_bytes = _external_path(
        repository,
        environ,
        BUILD_ATTESTATION_ENV,
        "build attestation",
        MAX_JSON_BYTES,
    )
    build_signature_path, build_signature = _external_path(
        repository,
        environ,
        BUILD_ATTESTATION_SIGNATURE_ENV,
        "build attestation signature",
        MAX_SIGNATURE_BYTES,
    )

    _verify_detached_signature(
        public_key, source_bytes, source_signature, "source approval"
    )
    _verify_detached_signature(
        public_key, build_bytes, build_signature, "build attestation"
    )
    source = _parse_json(source_bytes, "source approval")
    build = _parse_json(build_bytes, "build attestation")
    source_approval_hash = _sha256(source_bytes)

    source_approved_at = _source_bindings(
        source,
        release_id,
        prerequisite_binding,
        candidate,
        patch_hash,
        source_manifest_hash,
        signer_hash,
    )
    verified_build, built_at, build_approved_at = _build_bindings(
        repository,
        build,
        source,
        source_approval_hash,
        signer_hash,
        source_approved_at,
    )

    return {
        "releaseId": release_id,
        "candidateCommit": candidate,
        "approvedPatchSha256": patch_hash,
        "approvedSourceManifestSha256": source_manifest_hash,
        "prerequisiteRelease": prerequisite_binding,
        "trustedSigner": {
            "publicKeyPath": str(key_path),
            "sha256": signer_hash,
        },
        "sourceApproval": {
            "path": str(source_path),
            "signaturePath": str(source_signature_path),
            "sha256": source_approval_hash,
            "approvedBy": source["approvedBy"],
            "approvedRole": source["approvedRole"],
            "approvedAt": source["approvedAt"],
        },
        "buildAttestation": {
            "path": str(build_path),
            "signaturePath": str(build_signature_path),
            "sha256": _sha256(build_bytes),
            "builtAt": build["builtAt"],
            "approvedBy": build["approvedBy"],
            "approvedRole": build["approvedRole"],
            "approvedAt": build["approvedAt"],
        },
        "artifactEvidence": {
            "status": "verified",
            "oaJar": verified_build["oaJar"],
            "systemJar": verified_build["systemJar"],
            "frontendDist": verified_build["frontendDist"],
        },
        "externalGateEvidence": verified_build["externalGateEvidence"],
        "externalGates": verified_build["externalGates"],
        "buildToolchain": verified_build["buildToolchain"],
        "verifiedTimes": {
            "sourceApprovedAt": source_approved_at.isoformat(),
            "builtAt": built_at.isoformat(),
            "buildApprovedAt": build_approved_at.isoformat(),
        },
    }


verify_signed_attestations = verify_attestations
