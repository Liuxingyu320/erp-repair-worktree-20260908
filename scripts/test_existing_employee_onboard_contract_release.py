#!/usr/bin/env python3
"""Fail-closed source contract for the non-Docker 20260717 feature release."""

from __future__ import annotations

import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "scripts/existing-employee-onboard-contract-release-20260717.json"
MIGRATIONS = ROOT / "scripts/existing-employee-onboard-contract-migrations-20260717.list"
FILES = ROOT / "scripts/existing-employee-onboard-contract-release-files-20260717.list"
MIGRATION_NAME = "erp_oa_sign_onboard_open_guard_20260717.sql"


def ordered_list(path: Path) -> list[str]:
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


class ExistingEmployeeOnboardReleaseContractTest(unittest.TestCase):
    def test_manifest_is_source_only_hash_pinned_and_ordered(self) -> None:
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual(manifest["schemaVersion"], 1)
        self.assertEqual(manifest["releaseId"], "existing-employee-onboard-contract-20260717")
        self.assertEqual(manifest["executionPolicy"], "manual-source-only")
        self.assertFalse(manifest["dockerBootstrapIncluded"])
        self.assertNotIn("deployDirectory", manifest)
        self.assertEqual(manifest["migrationCount"], 1)
        self.assertEqual(ordered_list(MIGRATIONS), [MIGRATION_NAME])
        self.assertEqual([item["file"] for item in manifest["migrations"]], [MIGRATION_NAME])
        migration = ROOT / "sql" / MIGRATION_NAME
        digest = hashlib.sha256(migration.read_bytes()).hexdigest()
        self.assertEqual(manifest["migrations"][0]["sha256"], digest)
        self.assertEqual(
            manifest["migrations"][0]["phase"],
            "01-open-onboard-employee-guard-before-backend",
        )

    def test_release_file_boundary_is_explicit_existing_and_non_docker(self) -> None:
        files = ordered_list(FILES)
        self.assertEqual(files, sorted(files))
        self.assertEqual(len(files), len(set(files)))
        self.assertFalse(any(path.startswith("docker/") for path in files))
        self.assertIn("sql/" + MIGRATION_NAME, files)
        self.assertIn("scripts/verify-existing-employee-onboard-contract-release.sh", files)
        self.assertTrue(
            {
                "erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeListVo.java",
                "erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/HrEmployeeProfileVo.java",
                "erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysUserListVo.java",
                "erp-ui/src/utils/signDictionary.js",
                "erp-ui/src/views/hr/components/HrEmployeeList.vue",
                "erp-ui/src/views/hr/components/HrOnboardContractBatchDialog.vue",
                "erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue",
            }.issubset(files),
            "release boundary must include the entry UI, state dictionary and Long-id contracts",
        )
        for relative in files:
            self.assertTrue((ROOT / relative).is_file(), relative)

    def test_migration_fails_closed_before_generated_unique_guard(self) -> None:
        source = (ROOT / "sql" / MIGRATION_NAME).read_text(encoding="utf-8")
        normalized = " ".join(source.split()).upper()
        duplicate_error = "DUPLICATE OPEN ONBOARD TASKS MUST BE RESOLVED"
        add_column = "ADD COLUMN OPEN_ONBOARD_EMPLOYEE_ID"
        self.assertIn(duplicate_error, normalized)
        self.assertIn(add_column, normalized)
        self.assertLess(normalized.index(duplicate_error), normalized.index(add_column))
        self.assertIn("SIGNAL SQLSTATE '45000'", normalized)
        self.assertIn("GENERATED ALWAYS AS", normalized)
        self.assertIn("STORED COMMENT", normalized)
        self.assertIn("UPPER(TRIM(SCENARIO)) = ''ONBOARD''", normalized)
        self.assertIn(
            "STATUS NOT IN (''SIGNED'', ''REFUSED'', ''EXPIRED'', ''CANCELLED'', ''NO_ACTION'')",
            normalized,
        )
        self.assertIn("ADD UNIQUE INDEX UK_OA_SIGN_TASK_OPEN_ONBOARD_EMPLOYEE", normalized)
        self.assertNotIn("UPDATE OA_SIGN_TASK", normalized)
        self.assertNotIn("DELETE FROM OA_SIGN_TASK", normalized)

    def test_frozen_release_a_lists_remain_unmodified_by_feature_migration(self) -> None:
        frozen = (
            "scripts/contract-signing-migrations-20260716.list",
            "scripts/contract-signing-release-files-20260716.list",
            "scripts/contract-signing-release-20260716.json",
        )
        for relative in frozen:
            self.assertNotIn(MIGRATION_NAME, (ROOT / relative).read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
