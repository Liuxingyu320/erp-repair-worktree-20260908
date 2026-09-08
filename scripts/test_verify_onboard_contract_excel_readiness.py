#!/usr/bin/env python3
"""Tests for the single signed 20260718 readiness orchestrator."""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import verify_onboard_contract_excel_readiness as verifier


def _sha256(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


class CandidateRepository:
    manifest_relative = Path(verifier.EXPECTED_SOURCE_MANIFEST)
    release_list_relative = Path(verifier.EXPECTED_RELEASE_FILE_LIST)
    deleted_list_relative = Path(verifier.EXPECTED_DELETED_PATH_LIST)
    evidence_template_relative = Path(
        "docs/releases/evidence/20260718-onboard-contract-excel-evidence.json"
    )
    evidence_output_relative = Path("output/readiness-evidence-index.json")

    def __init__(self, test: unittest.TestCase) -> None:
        temporary = tempfile.TemporaryDirectory()
        test.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self._git("init", "-q")
        self._git("config", "user.name", "Readiness Test")
        self._git("config", "user.email", "readiness@example.invalid")
        (self.root / ".git/info/exclude").write_text("output/\n", encoding="utf-8")

        prerequisite = {
            "schemaVersion": 1,
            "releaseId": verifier.EXPECTED_PREREQUISITE_ID,
            "status": "ready",
        }
        self._write_json(Path(verifier.EXPECTED_PREREQUISITE_MANIFEST), prerequisite)
        self._write(Path("legacy.txt"), "Release-A legacy file\n")
        self._git("add", ".")
        self._git("commit", "-q", "-m", "Release A")
        self.baseline = self.head
        self._git("tag", verifier.EXPECTED_PREREQUISITE_REF.removeprefix("refs/tags/"))

        (self.root / "legacy.txt").unlink()
        self._write(Path("feature.txt"), "approved onboarding implementation\n")
        release_paths = sorted(
            {
                self.evidence_template_relative.as_posix(),
                "feature.txt",
                self.deleted_list_relative.as_posix(),
                self.manifest_relative.as_posix(),
                self.release_list_relative.as_posix(),
            }
        )
        self._write(self.release_list_relative, "\n".join(release_paths) + "\n")
        self._write(self.deleted_list_relative, "legacy.txt\n")
        self._write_json(self.evidence_template_relative, self.pending_evidence())
        self._write_json(self.evidence_output_relative, self.valid_evidence())
        self._write_json(self.manifest_relative, self.source_manifest())
        self._git("add", "-A")
        self._git("commit", "-q", "-m", "20260718 candidate")
        self.finalize()

    @property
    def head(self) -> str:
        return self._git("rev-parse", "HEAD").stdout.strip()

    @property
    def manifest_path(self) -> Path:
        return self.root / self.manifest_relative

    @property
    def environment(self) -> dict[str, str]:
        return {
            verifier.SOURCE_APPROVAL_ENV: "/release/source-approval.json",
            verifier.SOURCE_APPROVAL_SIGNATURE_ENV: "/release/source-approval.sig",
            verifier.BUILD_ATTESTATION_ENV: "/release/build-attestation.json",
            verifier.BUILD_ATTESTATION_SIGNATURE_ENV: "/release/build-attestation.sig",
            verifier.TRUSTED_SIGNER_PUBLIC_KEY_ENV: "/release/trusted-public-key.pem",
            verifier.TRUSTED_SIGNER_SHA256_ENV: "1" * 64,
        }

    def source_manifest(self) -> dict:
        return {
            "schemaVersion": 1,
            "releaseId": verifier.EXPECTED_RELEASE_ID,
            "status": "development",
            "releaseFileList": self.release_list_relative.as_posix(),
            "deletedPathList": self.deleted_list_relative.as_posix(),
            "prerequisiteRelease": {
                "status": "verification-pending",
                "releaseId": verifier.EXPECTED_PREREQUISITE_ID,
                "manifestPath": verifier.EXPECTED_PREREQUISITE_MANIFEST,
                "releaseRef": verifier.EXPECTED_PREREQUISITE_REF,
                "commit": None,
                "manifestSha256": None,
            },
            "sourceCandidate": {
                "status": "reassembly-pending",
                "baselineReleaseId": verifier.EXPECTED_PREREQUISITE_ID,
                "baselineCommit": None,
                "approvedPatchSha256": None,
                "approvedPatchExcludes": [],
                "requiredChangedPaths": ["feature.txt"],
                "candidateHeadMustBeVerifiedAtRuntime": True,
                "worktreeMustBeClean": True,
                "diffMustStayWithinReleaseAndDeletedPaths": True,
            },
            "approvalAnchors": dict(verifier.EXPECTED_APPROVAL_ANCHORS),
            "externalGates": {
                gate: "external-pending" for gate in sorted(verifier.EXPECTED_GATES)
            },
            "buildPolicy": {
                "externalSignedAttestationsRequired": True,
                "frontendExcelEntryCompiledEnabledRequired": True,
            },
            "artifactEvidence": {
                "status": "build-pending",
                "oaJar": {
                    "path": "docker/erp/modules/oa/jar/erp-modules-oa.jar",
                    "sha256": None,
                },
                "systemJar": {
                    "path": "docker/erp/modules/system/jar/erp-modules-system.jar",
                    "sha256": None,
                },
                "frontendDist": {
                    "path": "docker/nginx/html/dist",
                    "treeSha256": None,
                },
            },
            "externalGateEvidence": {
                "path": self.evidence_template_relative.as_posix(),
                "sha256": None,
            },
        }

    def pending_evidence(self) -> dict:
        return {
            "schemaVersion": 2,
            "releaseId": verifier.EXPECTED_RELEASE_ID,
            "status": "evidence-pending",
            "containsPii": False,
            "binding": {
                "candidateCommit": None,
                "approvedPatchSha256": None,
                "approvedSourceManifestSha256": None,
                "sourceApprovalSha256": None,
                "oaJarSha256": None,
                "systemJarSha256": None,
                "frontendTreeSha256": None,
            },
            "gates": {
                gate: {
                    "status": "pending",
                    "evidenceId": None,
                    "evidenceSha256": None,
                    "subject": None,
                    "approvedBy": None,
                    "approvedRole": None,
                    "approvedAt": None,
                    "environment": None,
                }
                for gate in sorted(verifier.EXPECTED_GATES)
            },
        }

    def valid_evidence(self) -> dict:
        gates: dict[str, dict] = {}
        for gate in sorted(verifier.EXPECTED_GATES):
            subject = f"Approval evidence for {gate}"
            approved_by = "hr-legal-approver@example.invalid"
            approved_role = "HR and legal release approver"
            approved_at = "2026-07-18T10:00:00+08:00"
            environment = "isolated-uat"
            actual = {
                "schemaVersion": 2,
                "releaseId": verifier.EXPECTED_RELEASE_ID,
                "gate": gate,
                "status": "passed",
                "containsPii": False,
                "subject": subject,
                "approvedBy": approved_by,
                "approvedRole": approved_role,
                "approvedAt": approved_at,
                "environment": environment,
                "binding": {
                    "candidateCommit": "a" * 40,
                    "approvedPatchSha256": "b" * 64,
                    "approvedSourceManifestSha256": "c" * 64,
                    "sourceApprovalSha256": "d" * 64,
                    "oaJarSha256": "e" * 64,
                    "systemJarSha256": "f" * 64,
                    "frontendTreeSha256": "1" * 64,
                },
                "result": {"summary": "verified without personal data"},
            }
            relative = self.gate_evidence_relative(gate)
            actual_bytes = self._write_json(relative, actual)
            gates[gate] = {
                "status": "passed",
                "evidenceId": relative.as_posix(),
                "evidenceSha256": _sha256(actual_bytes),
                "subject": subject,
                "approvedBy": approved_by,
                "approvedRole": approved_role,
                "approvedAt": approved_at,
                "environment": environment,
            }
        return {
            "schemaVersion": 2,
            "releaseId": verifier.EXPECTED_RELEASE_ID,
            "status": "verified",
            "containsPii": False,
            "binding": {
                "candidateCommit": "a" * 40,
                "approvedPatchSha256": "b" * 64,
                "approvedSourceManifestSha256": "c" * 64,
                "sourceApprovalSha256": "d" * 64,
                "oaJarSha256": "e" * 64,
                "systemJarSha256": "f" * 64,
                "frontendTreeSha256": "1" * 64,
            },
            "gates": gates,
        }

    @staticmethod
    def gate_evidence_relative(gate: str) -> Path:
        return Path("output/readiness-evidence") / f"{gate}.json"

    def finalize(self) -> None:
        self._git("add", "-A")
        staged = self._git("diff", "--cached", "--quiet", check=False)
        if staged.returncode == 1:
            self._git("commit", "-q", "--amend", "--no-edit")
        elif staged.returncode != 0:
            raise AssertionError(staged.stderr)
        self.approved_candidate = self.head
        self.signed_evidence_hash = _sha256(
            (self.root / self.evidence_output_relative).read_bytes()
        )

    def signed_context(
        self,
        root: Path,
        source_manifest_bytes: bytes,
        candidate_commit: str,
        prerequisite: dict,
        actual_patch_hash: str,
        environ: dict[str, str],
    ) -> dict:
        for variable in verifier.EXPECTED_APPROVAL_ANCHORS.values():
            if not environ.get(variable):
                raise ValueError(f"{variable} is required")
        if Path(root).resolve() != self.root.resolve():
            raise ValueError("unexpected repository")
        if source_manifest_bytes != self.manifest_path.read_bytes():
            raise ValueError("source manifest bytes changed")
        if candidate_commit != self.approved_candidate:
            raise ValueError("candidate commit is not source-approved")
        if prerequisite.get("commit") != self.baseline:
            raise ValueError("Release-A commit is not source-approved")
        manifest_hash = _sha256(source_manifest_bytes)
        return {
            "releaseId": verifier.EXPECTED_RELEASE_ID,
            "candidateCommit": candidate_commit,
            "approvedPatchSha256": actual_patch_hash,
            "approvedSourceManifestSha256": manifest_hash,
            "prerequisiteRelease": prerequisite,
            "trustedSigner": {"publicKeyPath": "/release/key.pem", "sha256": "1" * 64},
            "sourceApproval": {"sha256": "2" * 64, "approvedBy": "source-owner"},
            "buildAttestation": {"sha256": "3" * 64, "approvedBy": "release-owner"},
            "artifactEvidence": {
                "status": "verified",
                "oaJar": {
                    "path": "docker/erp/modules/oa/jar/erp-modules-oa.jar",
                    "sha256": "4" * 64,
                },
                "systemJar": {
                    "path": "docker/erp/modules/system/jar/erp-modules-system.jar",
                    "sha256": "5" * 64,
                },
                "frontendDist": {
                    "path": "docker/nginx/html/dist",
                    "treeSha256": "6" * 64,
                },
            },
            "externalGateEvidence": {
                "path": self.evidence_output_relative.as_posix(),
                "sha256": self.signed_evidence_hash,
            },
            "externalGates": {
                gate: "passed" for gate in sorted(verifier.EXPECTED_GATES)
            },
            "buildToolchain": {
                "javaVersion": "21",
                "mavenVersion": "3.9",
                "nodeVersion": "22",
                "npmVersion": "10",
            },
        }

    def read_manifest(self) -> dict:
        return json.loads(self.manifest_path.read_text(encoding="utf-8"))

    def write_manifest(self, value: dict) -> None:
        self._write_json(self.manifest_relative, value)

    def read_evidence(self) -> dict:
        return json.loads(
            (self.root / self.evidence_output_relative).read_text(encoding="utf-8")
        )

    def write_evidence(self, value: dict) -> None:
        self._write_json(self.evidence_output_relative, value)

    def read_gate_evidence(self, gate: str) -> dict:
        return json.loads(
            (self.root / self.gate_evidence_relative(gate)).read_text(encoding="utf-8")
        )

    def write_gate_evidence_and_pin(self, gate: str, value: dict) -> None:
        actual_bytes = self._write_json(self.gate_evidence_relative(gate), value)
        evidence = self.read_evidence()
        evidence["gates"][gate]["evidenceSha256"] = _sha256(actual_bytes)
        self.write_evidence(evidence)

    def _write(self, relative: Path, contents: str) -> bytes:
        target = self.root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        payload = contents.encode("utf-8")
        target.write_bytes(payload)
        return payload

    def _write_json(self, relative: Path, value: dict) -> bytes:
        return self._write(relative, json.dumps(value, ensure_ascii=False, indent=2) + "\n")

    def _git(
        self,
        *arguments: str,
        check: bool = True,
        text: bool = True,
    ) -> subprocess.CompletedProcess:
        result = subprocess.run(
            ["git", *arguments],
            cwd=self.root,
            check=False,
            capture_output=True,
            text=text,
        )
        if check and result.returncode != 0:
            stderr = result.stderr if text else result.stderr.decode(errors="replace")
            raise AssertionError(f"git {' '.join(arguments)} failed: {stderr}")
        return result


class OnboardContractExcelReadinessVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.candidate = CandidateRepository(self)

    def verify(
        self,
        environment: dict[str, str] | None = None,
        *,
        signed_side_effect=None,
        artifact_side_effect=None,
    ) -> tuple[list[str], mock.Mock, mock.Mock]:
        signed_effect = signed_side_effect or self.candidate.signed_context
        artifact_effect = artifact_side_effect or ({"status": "verified"})
        with mock.patch.object(
            verifier, "verify_signed_attestations", side_effect=signed_effect
        ) as signed_mock, mock.patch.object(
            verifier, "verify_artifacts", side_effect=artifact_effect
        ) as artifact_mock, mock.patch.object(
            verifier, "verify_gate_evidence"
        ) as gate_evidence_mock:
            blockers = verifier.verify_readiness(
                self.candidate.root,
                self.candidate.manifest_path,
                self.candidate.environment if environment is None else environment,
            )
        self.last_gate_evidence_mock = gate_evidence_mock
        return blockers, signed_mock, artifact_mock

    def test_verified_repository_uses_one_orchestrator_and_rechecks_bindings(self) -> None:
        blockers, signed, artifacts = self.verify()
        self.assertEqual([], blockers)
        self.assertEqual(2, signed.call_count)
        self.assertEqual(1, artifacts.call_count)
        self.assertEqual(2, self.last_gate_evidence_mock.call_count)
        gate_call = self.last_gate_evidence_mock.call_args_list[0]
        self.assertEqual(2, gate_call.args[0]["schemaVersion"])
        self.assertEqual(verifier.EXPECTED_GATES, set(gate_call.args[1]))
        self.assertEqual(
            self.candidate.approved_candidate,
            gate_call.args[2]["candidateCommit"],
        )
        self.assertEqual(
            verifier.EXPECTED_RELEASE_ID,
            gate_call.args[3]["releaseId"],
        )
        call = artifacts.call_args
        self.assertEqual(self.candidate.approved_candidate, call.kwargs["expected_candidate_commit"])
        self.assertRegex(call.kwargs["expected_patch_sha256"], r"^[0-9a-f]{64}$")
        self.assertEqual(
            _sha256(self.candidate.manifest_path.read_bytes()),
            call.kwargs["expected_source_manifest_sha256"],
        )

    def test_gate_evidence_business_schema_failure_stops_before_artifacts(self) -> None:
        def reject_gate_evidence(*_args, **_kwargs):
            raise ValueError("selected27WorkbookUat sentCount must be 27")

        with mock.patch.object(
            verifier,
            "verify_gate_evidence",
            side_effect=reject_gate_evidence,
        ), mock.patch.object(
            verifier,
            "verify_signed_attestations",
            side_effect=self.candidate.signed_context,
        ), mock.patch.object(verifier, "verify_artifacts") as artifacts:
            blockers = verifier.verify_readiness(
                self.candidate.root,
                self.candidate.manifest_path,
                self.candidate.environment,
            )
        self.assertIn(
            "external gate evidence v2 validation failed: "
            "selected27WorkbookUat sentCount must be 27",
            blockers,
        )
        artifacts.assert_not_called()

    def test_source_manifest_worktree_bytes_must_match_head(self) -> None:
        manifest = self.candidate.read_manifest()
        manifest["unexpectedLocalEdit"] = True
        self.candidate.write_manifest(manifest)
        blockers, signed, artifacts = self.verify()
        self.assertIn("20260718 source manifest working-tree bytes differ from HEAD", blockers)
        signed.assert_not_called()
        artifacts.assert_not_called()

    def test_detached_or_substitute_manifest_is_rejected(self) -> None:
        detached = self.candidate.root / "output/source-manifest.json"
        detached.parent.mkdir(parents=True, exist_ok=True)
        detached.write_bytes(self.candidate.manifest_path.read_bytes())
        blockers = verifier.verify_readiness(
            self.candidate.root, detached, self.candidate.environment
        )
        self.assertIn(
            "readiness must use the exact HEAD-tracked 20260718 source manifest",
            blockers,
        )

    def test_release_and_deleted_lists_must_match_head_blobs(self) -> None:
        for field, relative in (
            ("releaseFileList", self.candidate.release_list_relative),
            ("deletedPathList", self.candidate.deleted_list_relative),
        ):
            target = self.candidate.root / relative
            target.write_text(
                target.read_text(encoding="utf-8") + "# local replacement\n",
                encoding="utf-8",
            )
            blockers, _, _ = self.verify()
            self.assertIn(f"manifest {field} working-tree bytes differ from HEAD", blockers)
            self.candidate._git("restore", relative.as_posix())

    def test_ignored_untracked_release_list_replacement_is_blocked(self) -> None:
        relative = self.candidate.release_list_relative
        target = self.candidate.root / relative
        replacement = target.read_bytes()
        self.candidate._git("rm", relative.as_posix())
        self.candidate._git("commit", "-q", "--amend", "--no-edit")
        with (self.candidate.root / ".git/info/exclude").open("a", encoding="utf-8") as stream:
            stream.write(relative.as_posix() + "\n")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(replacement)
        blockers, _, _ = self.verify()
        self.assertIn(
            "manifest releaseFileList is not tracked in HEAD as a regular blob", blockers
        )

    def test_tracked_manifest_cannot_self_certify_ready(self) -> None:
        manifest = self.candidate.read_manifest()
        manifest["status"] = "ready"
        self.candidate.write_manifest(manifest)
        self.candidate.finalize()
        blockers, signed, _ = self.verify()
        self.assertIn("tracked source manifest status must remain development", blockers)
        signed.assert_not_called()

    def test_tracked_external_gates_must_remain_pending(self) -> None:
        manifest = self.candidate.read_manifest()
        manifest["externalGates"]["nativeMySqlRehearsal"] = "passed"
        self.candidate.write_manifest(manifest)
        self.candidate.finalize()
        blockers, _, _ = self.verify()
        self.assertIn("tracked source manifest external gates must remain pending", blockers)

    def test_source_contract_requires_dedicated_frontend_entry_enabled_artifact(self) -> None:
        manifest = self.candidate.read_manifest()
        manifest["buildPolicy"]["frontendExcelEntryCompiledEnabledRequired"] = False
        self.candidate.write_manifest(manifest)
        self.candidate.finalize()
        blockers, signed, artifacts = self.verify()
        self.assertIn(
            "source manifest must require the dedicated frontend Excel entry to be compiled enabled",
            blockers,
        )
        signed.assert_not_called()
        artifacts.assert_not_called()

    def test_missing_signed_attestation_environment_is_blocked(self) -> None:
        blockers, signed, artifacts = self.verify({})
        self.assertTrue(any(verifier.SOURCE_APPROVAL_ENV in item for item in blockers))
        self.assertEqual(1, signed.call_count)
        artifacts.assert_not_called()

    def test_signature_failure_stops_before_artifact_scan(self) -> None:
        blockers, _, artifacts = self.verify(
            signed_side_effect=ValueError("detached signature is invalid")
        )
        self.assertIn(
            "signed release attestations failed: detached signature is invalid", blockers
        )
        artifacts.assert_not_called()

    def test_moved_release_a_ref_is_blocked(self) -> None:
        tag = verifier.EXPECTED_PREREQUISITE_REF.removeprefix("refs/tags/")
        self.candidate._git("tag", "-f", tag, "HEAD")
        blockers, signed, _ = self.verify()
        self.assertTrue(any("must not equal candidate HEAD" in item for item in blockers))
        signed.assert_not_called()

    def test_git_replace_refs_cannot_change_candidate_semantics(self) -> None:
        self.candidate._git("replace", self.candidate.approved_candidate, self.candidate.baseline)
        blockers, signed, artifacts = self.verify()
        self.assertEqual([], blockers)
        self.assertEqual(2, signed.call_count)
        self.assertEqual(1, artifacts.call_count)

    def test_inherited_git_repository_environment_is_ignored(self) -> None:
        hostile = {
            "GIT_DIR": str(self.candidate.root / "missing-git-dir"),
            "GIT_WORK_TREE": str(self.candidate.root),
            "GIT_INDEX_FILE": str(self.candidate.root / "missing-index"),
            "GIT_REPLACE_REF_BASE": "refs/hostile/replace/",
        }
        with mock.patch.dict(os.environ, hostile, clear=False):
            blockers, _, _ = self.verify()
        self.assertEqual([], blockers)

    def test_skip_worktree_or_assume_unchanged_flags_are_blocked(self) -> None:
        self.candidate._git("update-index", "--skip-worktree", "feature.txt")
        blockers, signed, artifacts = self.verify()
        self.assertTrue(any("skip-worktree/assume-unchanged" in item for item in blockers))
        signed.assert_not_called()
        artifacts.assert_not_called()

    def test_patch_hash_is_independent_of_local_diff_config(self) -> None:
        first_blockers: list[str] = []
        first = verifier._collect_source_boundary(
            self.candidate.root, verifier.EXPECTED_SOURCE_MANIFEST, first_blockers
        )
        self.assertEqual([], first_blockers)
        self.assertIsNotNone(first)
        self.candidate._git("config", "diff.noprefix", "true")
        self.candidate._git("config", "diff.context", "27")
        self.candidate._git("config", "diff.algorithm", "patience")
        second_blockers: list[str] = []
        second = verifier._collect_source_boundary(
            self.candidate.root, verifier.EXPECTED_SOURCE_MANIFEST, second_blockers
        )
        self.assertEqual([], second_blockers)
        self.assertIsNotNone(second)
        self.assertEqual(first["actualPatchSha256"], second["actualPatchSha256"])

    def test_empty_git_diff_is_blocked(self) -> None:
        real_git = verifier._git

        def fake_git(root: Path, *arguments: str):
            if arguments[:3] == ("diff", "--no-renames", "--name-only"):
                return subprocess.CompletedProcess(["git", *arguments], 0, b"", b"")
            return real_git(root, *arguments)

        with mock.patch.object(verifier, "_git", side_effect=fake_git):
            blockers, _, _ = self.verify()
        self.assertIn("candidate diff from Release-A is empty", blockers)

    def test_no_renames_boundary_blocks_path_outside_allowlist(self) -> None:
        self.candidate._write(Path("outside.txt"), "outside\n")
        self.candidate.finalize()
        blockers, _, _ = self.verify()
        self.assertTrue(any("outside.txt" in item and "escapes" in item for item in blockers))

    def test_dirty_worktree_stops_before_external_trust(self) -> None:
        with (self.candidate.root / "feature.txt").open("a", encoding="utf-8") as stream:
            stream.write("dirty edit\n")
        blockers, signed, _ = self.verify()
        self.assertIn("candidate worktree is not clean", blockers)
        signed.assert_not_called()

    def test_signed_evidence_index_hash_is_enforced(self) -> None:
        target = self.candidate.root / self.candidate.evidence_output_relative
        target.write_bytes(target.read_bytes() + b" \n")
        blockers, _, artifacts = self.verify()
        self.assertIn(
            "external evidence index does not match its manifest SHA-256", blockers
        )
        artifacts.assert_not_called()

    def test_duplicate_json_keys_in_signed_evidence_are_blocked(self) -> None:
        target = self.candidate.root / self.candidate.evidence_output_relative
        payload = target.read_text(encoding="utf-8")
        target.write_text(
            payload.replace(
                '"schemaVersion": 2,',
                '"schemaVersion": 2,\n  "schemaVersion": 2,',
                1,
            ),
            encoding="utf-8",
        )
        self.candidate.signed_evidence_hash = _sha256(target.read_bytes())
        blockers, _, artifacts = self.verify()
        self.assertIn(
            "external evidence index contains duplicate JSON keys: schemaVersion",
            blockers,
        )
        artifacts.assert_not_called()

    def test_oversized_evidence_file_is_blocked_before_json_parse(self) -> None:
        gate = "nativeMySqlRehearsal"
        target = self.candidate.root / self.candidate.gate_evidence_relative(gate)
        with target.open("wb") as stream:
            stream.truncate(verifier.MAX_READINESS_FILE_BYTES + 1)
        blockers, _, artifacts = self.verify()
        self.assertTrue(any("readiness limit" in item for item in blockers))
        artifacts.assert_not_called()

    def test_missing_actual_evidence_file_is_blocked(self) -> None:
        gate = "nativeMySqlRehearsal"
        (self.candidate.root / self.candidate.gate_evidence_relative(gate)).unlink()
        blockers, _, _ = self.verify()
        self.assertTrue(
            any(f"external evidence file for {gate} cannot be inspected" in item for item in blockers)
        )

    def test_tampered_actual_evidence_bytes_are_blocked(self) -> None:
        gate = "nativeMySqlRehearsal"
        target = self.candidate.root / self.candidate.gate_evidence_relative(gate)
        target.write_text('{"tampered": true}\n', encoding="utf-8")
        blockers, _, _ = self.verify()
        self.assertIn(
            f"external evidence {gate} evidenceSha256 does not match the evidence file",
            blockers,
        )

    def test_actual_evidence_metadata_must_match_index(self) -> None:
        gate = "nativeMySqlRehearsal"
        actual = self.candidate.read_gate_evidence(gate)
        actual["approvedRole"] = "unapproved-role"
        self.candidate.write_gate_evidence_and_pin(gate, actual)
        self.candidate.finalize()
        blockers, _, _ = self.verify()
        self.assertIn(
            f"external evidence file {gate} approvedRole does not match the index", blockers
        )

    def test_actual_evidence_must_not_be_symlink(self) -> None:
        gate = "nativeMySqlRehearsal"
        target = self.candidate.root / self.candidate.gate_evidence_relative(gate)
        replacement = target.with_suffix(".replacement.json")
        replacement.write_bytes(target.read_bytes())
        target.unlink()
        target.symlink_to(replacement.name)
        blockers, _, _ = self.verify()
        self.assertTrue(any("is not a regular non-symlink file" in item for item in blockers))

    def test_evidence_id_must_be_safe_repository_relative_path(self) -> None:
        gate = "nativeMySqlRehearsal"
        evidence = self.candidate.read_evidence()
        evidence["gates"][gate]["evidenceId"] = "../outside.json"
        self.candidate.write_evidence(evidence)
        self.candidate.finalize()
        blockers, _, _ = self.verify()
        self.assertTrue(any(f"external evidence {gate} evidenceId must be a safe" in item for item in blockers))

    def test_evidence_parent_symlink_cannot_escape_repository(self) -> None:
        gate = "nativeMySqlRehearsal"
        external = tempfile.TemporaryDirectory()
        self.addCleanup(external.cleanup)
        external_root = Path(external.name)
        external_file = external_root / f"{gate}.json"
        source = self.candidate.root / self.candidate.gate_evidence_relative(gate)
        external_file.write_bytes(source.read_bytes())
        escaped_parent = self.candidate.root / "output/escaped"
        escaped_parent.symlink_to(external_root, target_is_directory=True)
        evidence = self.candidate.read_evidence()
        evidence["gates"][gate]["evidenceId"] = f"output/escaped/{gate}.json"
        evidence["gates"][gate]["evidenceSha256"] = _sha256(external_file.read_bytes())
        self.candidate.write_evidence(evidence)
        self.candidate.finalize()
        blockers, _, artifacts = self.verify()
        self.assertTrue(
            any("path contains symlink" in item and "external evidence file" in item for item in blockers)
        )
        artifacts.assert_not_called()

    def test_evidence_approval_time_in_future_is_blocked(self) -> None:
        gate = "nativeMySqlRehearsal"
        future = "2999-01-01T00:00:00+00:00"
        actual = self.candidate.read_gate_evidence(gate)
        actual["approvedAt"] = future
        actual_bytes = self.candidate._write_json(
            self.candidate.gate_evidence_relative(gate), actual
        )
        evidence = self.candidate.read_evidence()
        evidence["gates"][gate]["approvedAt"] = future
        evidence["gates"][gate]["evidenceSha256"] = _sha256(actual_bytes)
        self.candidate.write_evidence(evidence)
        self.candidate.finalize()
        blockers, _, _ = self.verify()
        self.assertIn(
            f"external evidence {gate} approvedAt is unreasonably in the future", blockers
        )

    def test_missing_evidence_gate_is_blocked(self) -> None:
        evidence = self.candidate.read_evidence()
        evidence["gates"].pop("oneScopeGrayApproval")
        self.candidate.write_evidence(evidence)
        self.candidate.finalize()
        blockers, _, _ = self.verify()
        self.assertTrue(any("evidence index is missing gates" in item for item in blockers))

    def test_artifact_semantic_failure_is_blocked(self) -> None:
        blockers, signed, artifacts = self.verify(
            artifact_side_effect=ValueError("required controller missing")
        )
        self.assertIn(
            "immutable artifact verification failed: required controller missing", blockers
        )
        self.assertEqual(1, signed.call_count)
        self.assertEqual(1, artifacts.call_count)

    def test_signed_artifact_path_must_match_source_contract(self) -> None:
        def wrong_path(*args, **kwargs):
            context = self.candidate.signed_context(*args, **kwargs)
            context["artifactEvidence"]["oaJar"]["path"] = "output/other-oa.jar"
            return context

        blockers, _, artifacts = self.verify(signed_side_effect=wrong_path)
        self.assertIn("signed oaJar path differs from the source contract", blockers)
        artifacts.assert_not_called()

    def test_source_change_during_artifact_scan_is_blocked(self) -> None:
        def mutate_source(*args, **kwargs):
            with self.candidate.manifest_path.open("a", encoding="utf-8") as stream:
                stream.write(" \n")
            return {"status": "verified"}

        blockers, _, _ = self.verify(artifact_side_effect=mutate_source)
        self.assertTrue(any("working-tree bytes differ from HEAD" in item for item in blockers))

    def test_evidence_change_during_artifact_scan_is_blocked_on_recheck(self) -> None:
        gate = "nativeMySqlRehearsal"

        def mutate_evidence(*args, **kwargs):
            target = self.candidate.root / self.candidate.gate_evidence_relative(gate)
            target.write_text('{"changed": true}\n', encoding="utf-8")
            return {"status": "verified"}

        blockers, _, _ = self.verify(artifact_side_effect=mutate_evidence)
        self.assertIn(
            f"external evidence {gate} evidenceSha256 does not match the evidence file",
            blockers,
        )

    def test_signed_context_change_during_scan_is_blocked(self) -> None:
        calls = 0

        def changing_context(*args, **kwargs):
            nonlocal calls
            calls += 1
            context = self.candidate.signed_context(*args, **kwargs)
            if calls == 2:
                context["buildAttestation"]["approvedBy"] = "different-release-owner"
            return context

        blockers, _, _ = self.verify(signed_side_effect=changing_context)
        self.assertIn("signed release context changed during readiness verification", blockers)

    def test_missing_source_contract_sections_fail_closed(self) -> None:
        manifest = self.candidate.read_manifest()
        for key in ("approvalAnchors", "sourceCandidate", "artifactEvidence"):
            manifest.pop(key)
        self.candidate.write_manifest(manifest)
        self.candidate.finalize()
        blockers, signed, _ = self.verify()
        self.assertTrue(any("approvalAnchors" in item for item in blockers))
        self.assertTrue(any("sourceCandidate" in item for item in blockers))
        self.assertTrue(any("artifact evidence" in item for item in blockers))
        signed.assert_not_called()

    def test_malformed_required_path_fails_closed_without_traceback(self) -> None:
        manifest = self.candidate.read_manifest()
        manifest["sourceCandidate"]["requiredChangedPaths"].append({"bad": "type"})
        self.candidate.write_manifest(manifest)
        self.candidate.finalize()
        blockers, signed, artifacts = self.verify()
        self.assertIn(
            "source candidate requiredChangedPaths contains an unsafe path", blockers
        )
        signed.assert_not_called()
        artifacts.assert_not_called()

        result = subprocess.run(
            [
                sys.executable,
                str(Path(verifier.__file__)),
                "--root",
                str(self.candidate.root),
                "--manifest",
                str(self.candidate.manifest_path),
            ],
            check=False,
            capture_output=True,
            text=True,
            env={},
        )
        self.assertEqual(5, result.returncode)
        self.assertNotIn("Traceback", result.stderr)

    def test_cli_aggregates_blockers_and_exits_five(self) -> None:
        environment = os.environ.copy()
        for variable in verifier.EXPECTED_APPROVAL_ANCHORS.values():
            environment.pop(variable, None)
        result = subprocess.run(
            [
                sys.executable,
                str(Path(verifier.__file__)),
                "--root",
                str(self.candidate.root),
                "--manifest",
                str(self.candidate.manifest_path),
            ],
            check=False,
            capture_output=True,
            text=True,
            env=environment,
        )
        self.assertEqual(5, result.returncode)
        blocked_lines = [line for line in result.stderr.splitlines() if line]
        self.assertGreaterEqual(len(blocked_lines), 1)
        self.assertTrue(all(line.startswith("[BLOCKED] ") for line in blocked_lines))

    def test_current_workspace_fails_closed_without_exception(self) -> None:
        workspace = Path(__file__).resolve().parent.parent
        blockers = verifier.verify_readiness(
            workspace, workspace / verifier.EXPECTED_SOURCE_MANIFEST, {}
        )
        self.assertTrue(blockers)


if __name__ == "__main__":
    unittest.main()
