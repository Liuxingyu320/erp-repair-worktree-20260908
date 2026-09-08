#!/usr/bin/env python3
"""Tests for the external signed-attestation trust boundary."""

from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).with_name("verify_onboard_contract_excel_attestations.py")
SPEC = importlib.util.spec_from_file_location("onboard_attestations", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def sha1(payload: bytes) -> str:
    return hashlib.sha1(payload).hexdigest()


def iso(minutes_ago: int) -> str:
    value = datetime.now(timezone.utc) - timedelta(minutes=minutes_ago)
    return value.replace(microsecond=0).isoformat().replace("+00:00", "Z")


@unittest.skipUnless(shutil.which("openssl"), "OpenSSL is required")
class SignedAttestationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        self.root = self.base / "repository"
        self.external = self.base / "external-approvals"
        self.root.mkdir()
        self.external.mkdir()

        self.private_key = self.external / "private.pem"
        self.public_key = self.external / "public.pem"
        subprocess.run(
            [
                "openssl",
                "genpkey",
                "-algorithm",
                "RSA",
                "-pkeyopt",
                "rsa_keygen_bits:2048",
                "-out",
                str(self.private_key),
            ],
            check=True,
            capture_output=True,
        )
        subprocess.run(
            [
                "openssl",
                "pkey",
                "-in",
                str(self.private_key),
                "-pubout",
                "-out",
                str(self.public_key),
            ],
            check=True,
            capture_output=True,
        )
        self.signer_hash = sha256(self.public_key.read_bytes())

        self.oa_path = self.root / "build/oa/oa.jar"
        self.system_path = self.root / "build/system/system.jar"
        self.dist_path = self.root / "build/frontend/dist"
        self.evidence_path = self.root / "evidence/external-gates.json"
        self.oa_path.parent.mkdir(parents=True)
        self.system_path.parent.mkdir(parents=True)
        self.dist_path.mkdir(parents=True)
        self.evidence_path.parent.mkdir(parents=True)
        self.oa_path.write_bytes(b"immutable OA jar\n")
        self.system_path.write_bytes(b"immutable System jar\n")
        (self.dist_path / "index.html").write_text(
            "<html><script src='app.js'></script></html>", encoding="utf-8"
        )
        (self.dist_path / "app.js").write_text("window.ready=true;", encoding="utf-8")
        self.evidence_path.write_text(
            json.dumps(
                {
                    "releaseId": MODULE.RELEASE_ID,
                    "status": "verified",
                    "gates": {key: "passed" for key in sorted(MODULE.EXPECTED_EXTERNAL_GATES)},
                    "containsPii": False,
                },
                sort_keys=True,
            ),
            encoding="utf-8",
        )

        self.source_manifest_bytes = json.dumps(
            {"schemaVersion": 1, "releaseId": MODULE.RELEASE_ID},
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
        self.candidate_commit = sha1(b"candidate commit")
        self.patch_hash = sha256(b"approved patch bytes")
        self.prerequisite = {
            "releaseId": MODULE.PREREQUISITE_RELEASE_ID,
            "releaseRef": MODULE.PREREQUISITE_RELEASE_REF,
            "commit": sha1(b"release A commit"),
            "manifestSha256": sha256(b"release A ready manifest"),
        }
        self.source_path = self.external / "source-approval.json"
        self.source_signature = self.external / "source-approval.json.sig"
        self.build_path = self.external / "build-attestation.json"
        self.build_signature = self.external / "build-attestation.json.sig"

        self.source = {
            "schemaVersion": 1,
            "kind": "onboard-contract-excel-source-approval",
            "status": "approved",
            "releaseId": MODULE.RELEASE_ID,
            "releaseARef": self.prerequisite["releaseRef"],
            "releaseACommit": self.prerequisite["commit"],
            "releaseAManifestSha256": self.prerequisite["manifestSha256"],
            "candidateCommit": self.candidate_commit,
            "approvedPatchSha256": self.patch_hash,
            "approvedSourceManifestSha256": sha256(self.source_manifest_bytes),
            "signerPublicKeySha256": self.signer_hash,
            "approvedBy": "Source Release Approver",
            "approvedRole": "Change Control",
            "approvedAt": iso(20),
            "containsPii": False,
        }
        self.build = self._new_build(self.source)
        self._write_and_sign_both()
        self.environ = {
            MODULE.SOURCE_APPROVAL_ENV: str(self.source_path),
            MODULE.SOURCE_APPROVAL_SIGNATURE_ENV: str(self.source_signature),
            MODULE.BUILD_ATTESTATION_ENV: str(self.build_path),
            MODULE.BUILD_ATTESTATION_SIGNATURE_ENV: str(self.build_signature),
            MODULE.TRUSTED_SIGNER_PUBLIC_KEY_ENV: str(self.public_key),
            MODULE.TRUSTED_SIGNER_SHA256_ENV: self.signer_hash,
        }

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _relative(self, path: Path) -> str:
        return path.relative_to(self.root).as_posix()

    def _new_build(self, source: dict) -> dict:
        source_bytes = json.dumps(
            source, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        return {
            "schemaVersion": 1,
            "kind": "onboard-contract-excel-build-attestation",
            "status": "approved",
            "releaseId": source["releaseId"],
            "sourceApprovalSha256": sha256(source_bytes),
            "releaseARef": source["releaseARef"],
            "releaseACommit": source["releaseACommit"],
            "releaseAManifestSha256": source["releaseAManifestSha256"],
            "candidateCommit": source["candidateCommit"],
            "approvedPatchSha256": source["approvedPatchSha256"],
            "approvedSourceManifestSha256": source["approvedSourceManifestSha256"],
            "signerPublicKeySha256": source["signerPublicKeySha256"],
            "oaJar": {
                "path": self._relative(self.oa_path),
                "sha256": sha256(self.oa_path.read_bytes()),
            },
            "systemJar": {
                "path": self._relative(self.system_path),
                "sha256": sha256(self.system_path.read_bytes()),
            },
            "frontendDist": {
                "path": self._relative(self.dist_path),
                "treeSha256": MODULE._directory_tree_sha(self.dist_path, "test dist"),
            },
            "externalGateEvidence": {
                "path": self._relative(self.evidence_path),
                "sha256": sha256(self.evidence_path.read_bytes()),
            },
            "externalGates": {
                key: "passed" for key in sorted(MODULE.EXPECTED_EXTERNAL_GATES)
            },
            "buildToolchain": {
                "javaVersion": "OpenJDK 17.0.12",
                "mavenVersion": "Apache Maven 3.9.9",
                "nodeVersion": "Node.js 22.17.0",
                "npmVersion": "npm 10.9.8",
            },
            "builtAt": iso(10),
            "approvedBy": "Immutable Build Approver",
            "approvedRole": "Release Engineering",
            "approvedAt": iso(5),
            "containsPii": False,
        }

    def _serialize(self, value: dict) -> bytes:
        return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")

    def _sign(self, source: Path, signature: Path, key: Path | None = None) -> None:
        subprocess.run(
            [
                "openssl",
                "dgst",
                "-sha256",
                "-sign",
                str(key or self.private_key),
                "-out",
                str(signature),
                str(source),
            ],
            check=True,
            capture_output=True,
        )

    def _write_source(self, *, sign: bool = True) -> None:
        self.source_path.write_bytes(self._serialize(self.source))
        if sign:
            self._sign(self.source_path, self.source_signature)

    def _write_build(self, *, sign: bool = True) -> None:
        self.build_path.write_bytes(self._serialize(self.build))
        if sign:
            self._sign(self.build_path, self.build_signature)

    def _write_and_sign_both(self) -> None:
        self._write_source()
        self.build["sourceApprovalSha256"] = sha256(self.source_path.read_bytes())
        self._write_build()

    def _verify(self, **overrides):
        values = {
            "root": self.root,
            "source_manifest_bytes": self.source_manifest_bytes,
            "candidate_commit": self.candidate_commit,
            "prerequisite": self.prerequisite,
            "actual_patch_hash": self.patch_hash,
            "environ": self.environ,
        }
        values.update(overrides)
        return MODULE.verify_attestations(**values)

    def test_positive_returns_signature_verified_context(self) -> None:
        result = self._verify()

        self.assertEqual(MODULE.RELEASE_ID, result["releaseId"])
        self.assertEqual(self.signer_hash, result["trustedSigner"]["sha256"])
        self.assertEqual("verified", result["artifactEvidence"]["status"])
        self.assertEqual(
            sha256(self.source_path.read_bytes()), result["sourceApproval"]["sha256"]
        )
        self.assertEqual(
            {key: "passed" for key in MODULE.EXPECTED_EXTERNAL_GATES},
            result["externalGates"],
        )

    def test_artifact_hashing_streams_without_materializing_whole_file(self) -> None:
        artifact = self.root / "streamed-artifact.jar"
        payload = (b"streamed-artifact-block\n" * 65536) + b"end"
        artifact.write_bytes(payload)
        with mock.patch.object(
            MODULE,
            "_stable_regular_bytes",
            side_effect=AssertionError("artifact hashing must not buffer the whole file"),
        ):
            actual = MODULE._file_sha(artifact, "streamed artifact")
        self.assertEqual(sha256(payload), actual)

    def test_tampered_json_and_signature_are_rejected(self) -> None:
        with self.subTest("tampered JSON"):
            self.source_path.write_bytes(self.source_path.read_bytes() + b"\n")
            with self.assertRaisesRegex(ValueError, "source approval detached signature"):
                self._verify()

        self._write_and_sign_both()
        with self.subTest("tampered signature"):
            signature = bytearray(self.build_signature.read_bytes())
            signature[0] ^= 0x01
            self.build_signature.write_bytes(signature)
            with self.assertRaisesRegex(ValueError, "build attestation detached signature"):
                self._verify()

    def test_wrong_key_and_independent_fingerprint_are_rejected(self) -> None:
        wrong_private = self.external / "wrong-private.pem"
        wrong_public = self.external / "wrong-public.pem"
        subprocess.run(
            [
                "openssl",
                "genpkey",
                "-algorithm",
                "RSA",
                "-pkeyopt",
                "rsa_keygen_bits:2048",
                "-out",
                str(wrong_private),
            ],
            check=True,
            capture_output=True,
        )
        subprocess.run(
            [
                "openssl",
                "pkey",
                "-in",
                str(wrong_private),
                "-pubout",
                "-out",
                str(wrong_public),
            ],
            check=True,
            capture_output=True,
        )

        wrong_key_environment = dict(self.environ)
        wrong_key_environment[MODULE.TRUSTED_SIGNER_PUBLIC_KEY_ENV] = str(wrong_public)
        wrong_key_environment[MODULE.TRUSTED_SIGNER_SHA256_ENV] = sha256(
            wrong_public.read_bytes()
        )
        with self.subTest("wrong key"):
            with self.assertRaisesRegex(ValueError, "source approval detached signature"):
                self._verify(environ=wrong_key_environment)

        wrong_fingerprint_environment = dict(self.environ)
        wrong_fingerprint_environment[MODULE.TRUSTED_SIGNER_SHA256_ENV] = sha256(
            b"independently wrong fingerprint"
        )
        with self.subTest("wrong fingerprint"):
            with self.assertRaisesRegex(ValueError, "independently pinned"):
                self._verify(environ=wrong_fingerprint_environment)

    def test_self_reported_hash_without_signature_is_rejected(self) -> None:
        self.source["approvedPatchSha256"] = sha256(b"self-reported patch")
        self.source_path.write_bytes(self._serialize(self.source))
        with self.assertRaisesRegex(ValueError, "source approval detached signature"):
            self._verify(actual_patch_hash=self.source["approvedPatchSha256"])

    def test_source_and_build_cross_binding_mismatch_is_rejected(self) -> None:
        self.build["candidateCommit"] = sha1(b"different candidate")
        self._write_build()
        with self.assertRaisesRegex(ValueError, "candidateCommit is inconsistent"):
            self._verify()

    def test_unsafe_artifact_and_evidence_paths_are_rejected(self) -> None:
        for field in ("oaJar", "externalGateEvidence"):
            with self.subTest(field=field):
                original = copy.deepcopy(self.build)
                self.build[field]["path"] = "../outside/reported.bin"
                self._write_build()
                with self.assertRaisesRegex(ValueError, "safe relative path"):
                    self._verify()
                self.build = original

    def test_pii_declarations_are_exactly_false(self) -> None:
        with self.subTest("source"):
            self.source["containsPii"] = True
            self._write_source()
            with self.assertRaisesRegex(ValueError, "containsPii"):
                self._verify()

        self.source["containsPii"] = False
        self._write_and_sign_both()
        with self.subTest("build"):
            self.build["containsPii"] = True
            self._write_build()
            with self.assertRaisesRegex(ValueError, "containsPii"):
                self._verify()

    def test_future_approval_and_build_times_are_rejected(self) -> None:
        future = (datetime.now(timezone.utc) + timedelta(hours=2)).isoformat()
        with self.subTest("source approval"):
            self.source["approvedAt"] = future
            self._write_source()
            with self.assertRaisesRegex(ValueError, "must not be in the future"):
                self._verify()

        self.source["approvedAt"] = iso(20)
        self._write_and_sign_both()
        with self.subTest("build time"):
            self.build["builtAt"] = future
            self._write_build()
            with self.assertRaisesRegex(ValueError, "must not be in the future"):
                self._verify()

    def test_exact_json_keys_are_required_even_when_resigned(self) -> None:
        self.source["unreviewed"] = "extra"
        self._write_source()
        with self.assertRaisesRegex(ValueError, "keys are not exact"):
            self._verify()

    def test_repository_controlled_key_is_rejected(self) -> None:
        repository_key = self.root / "trusted.pem"
        repository_key.write_bytes(self.public_key.read_bytes())
        environment = dict(self.environ)
        environment[MODULE.TRUSTED_SIGNER_PUBLIC_KEY_ENV] = str(repository_key)
        with self.assertRaisesRegex(ValueError, "outside the repository"):
            self._verify(environ=environment)

    def test_artifact_content_must_match_signed_hash(self) -> None:
        self.oa_path.write_bytes(b"artifact changed after attestation\n")
        with self.assertRaisesRegex(ValueError, "does not match the repository artifact"):
            self._verify()

    def test_external_files_require_absolute_paths(self) -> None:
        environment = dict(self.environ)
        environment[MODULE.SOURCE_APPROVAL_ENV] = "source-approval.json"
        with self.assertRaisesRegex(ValueError, "path must be absolute"):
            self._verify(environ=environment)


if __name__ == "__main__":
    unittest.main()
