#!/usr/bin/env python3
"""Tests for the contract-signing release scope verifier."""

from __future__ import annotations

import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify_contract_signing_scope.py")
SPEC = importlib.util.spec_from_file_location("verify_contract_signing_scope", SCRIPT)
assert SPEC and SPEC.loader
scope_verifier = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(scope_verifier)


class ContractSigningScopeVerifierTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self._git("init", "-q")
        self._write(".gitignore", "* 2.*\n")
        self._write(
            "erp-modules/erp-oa/src/main/java/example/OaSignTracked.java",
            "class OaSignTracked { int value = 1; }\n",
        )
        self._write(
            "erp-modules/erp-oa/src/main/java/example/OaSignDeleted.java",
            "class OaSignDeleted {}\n",
        )
        self._git("add", ".gitignore", "erp-modules")
        self._git(
            "-c",
            "user.name=Scope Test",
            "-c",
            "user.email=scope@example.invalid",
            "commit",
            "-qm",
            "baseline",
        )

        self._write(
            "erp-modules/erp-oa/src/main/java/example/OaSignTracked.java",
            "class OaSignTracked { int value = 2; }\n",
        )
        (self.root / "erp-modules/erp-oa/src/main/java/example/OaSignDeleted.java").unlink()
        self._write("erp-ui/src/utils/signDictionary.js", "export const signDictionary = {}\n")
        for control in scope_verifier.REQUIRED_CONTROL_FILES:
            self._write(control, f"control:{control}\n")
        migration = "select 1;\n"
        self._write("sql/erp_oa_sign_example.sql", migration)
        self._write("docker/mysql/db/erp_oa_sign_example.sql", migration)
        self._write("erp-ui/test/newBusinessMigrationRelease.test 2.js", "legacy\n")
        self._write("erp-ui/test/newBusinessMigrationRelease.test.js", "canonical\n")

        self.paths = {
            "tracked": "erp-modules/erp-oa/src/main/java/example/OaSignTracked.java",
            "deleted": "erp-modules/erp-oa/src/main/java/example/OaSignDeleted.java",
            "utility": "erp-ui/src/utils/signDictionary.js",
            "source": "sql/erp_oa_sign_example.sql",
            "deploy": "docker/mysql/db/erp_oa_sign_example.sql",
        }
        self.scope = self._scope()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def _git(self, *args: str) -> subprocess.CompletedProcess[bytes]:
        return subprocess.run(
            ["git", "-C", str(self.root), *args],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    def _write(self, relative: str, text: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def _hash(self, relative: str) -> str:
        return scope_verifier.sha256_file(self.root / relative)

    def _head_hash(self, relative: str) -> str:
        blob = self._git("show", f"HEAD:{relative}").stdout
        return scope_verifier.sha256_bytes(blob)

    def _scope(self) -> dict:
        controls = list(scope_verifier.REQUIRED_CONTROL_FILES)
        control = scope_verifier.CANONICAL_SCOPE_PATH
        allowlist = [*self.paths.values(), *controls]
        legacy = "erp-ui/test/newBusinessMigrationRelease.test 2.js"
        canonical = "erp-ui/test/newBusinessMigrationRelease.test.js"
        return {
            "schemaVersion": 1,
            "releaseId": "contract-signing-release-a-20260716",
            "baseline": {
                "commit": self._git("rev-parse", "HEAD").stdout.decode().strip(),
                "branch": "test",
                "capturedAt": "2026-07-16T00:00:00+08:00",
            },
            "manifestControlFile": control,
            "controlFiles": controls,
            "controlFileHashes": {
                path: self._hash(path)
                for path in controls[1:]
            },
            "releaseAAllowlist": allowlist,
            "denylist": [
                {"category": "artifacts", "pattern": "*.jar", "reason": "build artifact"},
                {"category": "backups", "pattern": "* 2.*", "reason": "duplicate or backup"},
                {"category": "database-dumps", "pattern": "*dump*.sql", "reason": "database dump"},
                {"category": "employee-data", "pattern": "*employee-data*", "reason": "employee data"},
                {"category": "signatures-and-uploads", "pattern": "*.png", "reason": "binary evidence"},
                {"category": "test-credentials", "pattern": "*.auth.json", "reason": "test credential"},
            ],
            "discovery": {
                "pathRegexes": [
                    r"erp-modules/erp-oa/.*/OaSign[^/]+\.java",
                    r"erp-ui/src/utils/sign[^/]+\.js",
                    r"(?:sql|docker/mysql/db)/erp_oa_sign_[^/]+\.sql",
                ],
                "extraPaths": [],
            },
            "inventory": [
                {
                    "path": self.paths["tracked"],
                    "status": "M",
                    "sha256": self._hash(self.paths["tracked"]),
                    "hashSource": "WORKTREE",
                    "stage": "A0",
                    "releaseA": True,
                },
                {
                    "path": self.paths["deleted"],
                    "status": "D",
                    "sha256": self._head_hash(self.paths["deleted"]),
                    "hashSource": "HEAD",
                    "stage": "A0",
                    "releaseA": True,
                },
                {
                    "path": self.paths["utility"],
                    "status": "U",
                    "sha256": self._hash(self.paths["utility"]),
                    "hashSource": "WORKTREE",
                    "stage": "A5",
                    "releaseA": True,
                },
                {
                    "path": self.paths["source"],
                    "status": "U",
                    "sha256": self._hash(self.paths["source"]),
                    "hashSource": "WORKTREE",
                    "stage": "A1",
                    "releaseA": True,
                },
                {
                    "path": self.paths["deploy"],
                    "status": "U",
                    "sha256": self._hash(self.paths["deploy"]),
                    "hashSource": "WORKTREE",
                    "stage": "A1",
                    "releaseA": True,
                },
            ],
            "migrationPairs": [
                {"source": self.paths["source"], "deploy": self.paths["deploy"]}
            ],
            "duplicateTestReview": {
                "legacyPath": legacy,
                "canonicalPath": canonical,
                "legacySha256": self._hash(legacy),
                "canonicalSha256": self._hash(canonical),
                "allAssertionsSuperseded": True,
                "resolution": "REMOVE_BEFORE_PROJECT_WIDE_TEST",
                "supersessionEvidence": [
                    {"id": value, "canonicalEvidence": "stronger canonical assertion"}
                    for value in sorted(scope_verifier.REQUIRED_LEGACY_ASSERTIONS)
                ],
            },
        }

    def test_loads_only_marked_json_block(self) -> None:
        document = self.root / "scope.md"
        document.write_text(
            "# Scope\n"
            + scope_verifier.SCOPE_JSON_BEGIN
            + "\n"
            + json.dumps(self.scope)
            + "\n"
            + scope_verifier.SCOPE_JSON_END
            + "\n",
            encoding="utf-8",
        )
        self.assertEqual(scope_verifier.load_scope_document(document), self.scope)

    def test_staged_mode_uses_index_scope_not_unstaged_allowlist_edit(self) -> None:
        control = self.root / self.scope["controlFiles"][0]
        original = (
            scope_verifier.SCOPE_JSON_BEGIN
            + "\n"
            + json.dumps(self.scope)
            + "\n"
            + scope_verifier.SCOPE_JSON_END
            + "\n"
        )
        control.write_text(original, encoding="utf-8")
        self._git("add", self.scope["controlFiles"][0])

        outside = "erp-modules/erp-inventory/Unrelated.java"
        self._write(outside, "class Unrelated {}\n")
        self._git("add", outside)
        widened = json.loads(json.dumps(self.scope))
        widened["releaseAAllowlist"].append(outside)
        control.write_text(
            scope_verifier.SCOPE_JSON_BEGIN
            + "\n"
            + json.dumps(widened)
            + "\n"
            + scope_verifier.SCOPE_JSON_END
            + "\n",
            encoding="utf-8",
        )

        indexed = scope_verifier.load_staged_scope(self.root, control)
        errors = scope_verifier.verify_staged(indexed, self.root)

        self.assertNotIn(outside, indexed["releaseAAllowlist"])
        self.assertTrue(any("outside the frozen Release A allowlist" in error for error in errors), errors)

    def test_valid_baseline_accepts_modified_deleted_and_untracked_hashes(self) -> None:
        self.assertEqual(scope_verifier.verify_baseline(self.scope, self.root), [])

    def test_hash_drift_fails_closed(self) -> None:
        self._write(self.paths["tracked"], "class OaSignTracked { int value = 3; }\n")
        errors = scope_verifier.verify_baseline(self.scope, self.root)
        self.assertTrue(any("SHA-256 drift" in error for error in errors), errors)

    def test_non_manifest_control_file_is_content_anchored(self) -> None:
        control = self.scope["controlFiles"][1]

        self.assertEqual(scope_verifier.verify_static(self.scope, self.root), [])

        self._write(control, "drifted control\n")
        errors = scope_verifier.verify_static(self.scope, self.root)
        self.assertTrue(any("control file SHA-256 drift" in error for error in errors), errors)

    def test_non_manifest_control_requires_declared_hash(self) -> None:
        control = self.scope["controlFiles"][1]
        self.scope["controlFileHashes"].pop(control)

        errors, _, _ = scope_verifier.validate_scope_structure(self.scope)

        self.assertTrue(any("controlFileHashes keys" in error for error in errors), errors)

    def test_manifest_control_is_canonical_not_positional(self) -> None:
        self.scope["manifestControlFile"] = self.scope["controlFiles"][1]

        errors, _, _ = scope_verifier.validate_scope_structure(self.scope)

        self.assertTrue(any("manifestControlFile must equal" in error for error in errors), errors)

    def test_staged_mode_validates_control_hash_from_index(self) -> None:
        control = self.scope["controlFiles"][1]
        self._git("add", *self.scope["controlFiles"])
        self._write(control, "staged drift\n")
        self._git("add", control)
        self._write(control, f"control:{control}\n")

        errors = scope_verifier.verify_staged(self.scope, self.root)

        self.assertTrue(any("control file SHA-256 drift" in error for error in errors), errors)

    def test_staged_mode_validates_migration_pair_from_index(self) -> None:
        self._git("add", self.paths["source"], self.paths["deploy"], *self.scope["controlFiles"])
        self._write(self.paths["source"], "select 2;\n")
        self._git("add", self.paths["source"])
        self._write(self.paths["deploy"], "select 2;\n")

        errors = scope_verifier.verify_staged(self.scope, self.root)

        self.assertTrue(any("differs byte-for-byte" in error for error in errors), errors)

    def test_staged_rename_checks_source_and_destination(self) -> None:
        outside = "erp-modules/erp-inventory/Outside.java"
        destination = "erp-ui/src/utils/signRenamed.js"
        self._write(outside, "class Outside {}\n")
        self._git("add", outside)
        self._git(
            "-c",
            "user.name=Scope Test",
            "-c",
            "user.email=scope@example.invalid",
            "commit",
            "-qm",
            "outside baseline",
        )
        self.scope["releaseAAllowlist"].append(destination)
        self._git("mv", outside, destination)

        errors = scope_verifier.verify_staged(self.scope, self.root)

        self.assertTrue(any(outside in error and "outside" in error for error in errors), errors)

    def test_undeclared_signing_path_fails_closed(self) -> None:
        self._write("erp-ui/src/utils/signUndeclared.js", "export default {}\n")
        errors = scope_verifier.verify_baseline(self.scope, self.root)
        self.assertTrue(any("undeclared dirty signing paths" in error for error in errors), errors)

    def test_denied_artifact_cannot_be_allowlisted(self) -> None:
        artifact = "erp-modules/erp-oa/target/contract-signing.jar"
        self.scope["releaseAAllowlist"].append(artifact)
        errors, _, _ = scope_verifier.validate_scope_structure(self.scope)
        self.assertTrue(any("denied by artifacts" in error for error in errors), errors)

    def test_incomplete_migration_pair_fails_closed(self) -> None:
        (self.root / self.paths["deploy"]).unlink()
        errors = scope_verifier.verify_baseline(self.scope, self.root)
        self.assertTrue(any("migration pair is incomplete" in error for error in errors), errors)

    def test_duplicate_review_requires_every_legacy_assertion_group(self) -> None:
        self.scope["duplicateTestReview"]["supersessionEvidence"].pop()
        errors, _, _ = scope_verifier.validate_scope_structure(self.scope)
        self.assertTrue(any("does not cover every legacy assertion" in error for error in errors), errors)

    def test_completed_duplicate_removal_requires_the_legacy_file_to_be_absent(self) -> None:
        legacy = self.root / self.scope["duplicateTestReview"]["legacyPath"]
        legacy.unlink()
        self.scope["duplicateTestReview"]["resolution"] = "REMOVED_BEFORE_PROJECT_WIDE_TEST"
        self.assertEqual(scope_verifier.verify_baseline(self.scope, self.root), [])

        self._write(self.scope["duplicateTestReview"]["legacyPath"], "legacy\n")
        errors = scope_verifier.verify_baseline(self.scope, self.root)
        self.assertTrue(any("still exists after recorded removal" in error for error in errors), errors)

    def test_staged_mode_rejects_path_outside_release_allowlist(self) -> None:
        self._write("erp-modules/erp-inventory/Unrelated.java", "class Unrelated {}\n")
        self._git("add", "erp-modules/erp-inventory/Unrelated.java")
        errors = scope_verifier.verify_staged(self.scope, self.root)
        self.assertTrue(any("outside the frozen Release A allowlist" in error for error in errors), errors)


class RepositoryScopeIntegrationTest(unittest.TestCase):
    def test_repository_scope_static_contract_is_valid(self) -> None:
        root = SCRIPT.parents[2]
        document = root / "docs/releases/20260716-contract-signing-release-scope.md"
        if not document.exists():
            self.skipTest("release scope document has not been generated yet")
        scope = scope_verifier.load_scope_document(document)
        self.assertEqual(scope_verifier.verify_static(scope, root), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
