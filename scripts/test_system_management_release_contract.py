#!/usr/bin/env python3

import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import verify_system_management_release_contract as contract


ROOT = Path(__file__).resolve().parent.parent


class SystemManagementReleaseContractTest(unittest.TestCase):
    def test_current_source_and_packaged_sql_contract_passes(self):
        result = contract.verify(ROOT)
        self.assertEqual(3, result["migrationCount"])
        self.assertEqual("development", result["status"])

    def test_release_stays_manual_phased(self):
        manifest = json.loads(
            (ROOT / "scripts/system-management-release-20260714.json").read_text(encoding="utf-8")
        )
        self.assertEqual("manual-phased", manifest["executionPolicy"])
        self.assertEqual(contract.EXPECTED_PHASES, [item["phase"] for item in manifest["migrations"]])

    def test_rejects_undeclared_packaged_sql(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "repo"
            for relative in (
                "scripts/system-management-release-20260714.json",
                "scripts/system-management-migrations-20260714.list",
                "scripts/new-business-release-20260714.json",
            ):
                destination = root / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / relative, destination)
            for migration in json.loads(
                (ROOT / "scripts/system-management-release-20260714.json").read_text(encoding="utf-8")
            )["migrations"]:
                for parent in ("sql", "docker/mysql/releases/system-management-20260714"):
                    destination = root / parent / migration["file"]
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(ROOT / parent / migration["file"], destination)
            extra = root / "docker/mysql/releases/system-management-20260714/undeclared.sql"
            extra.write_text("select 1;\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "undeclared SQL"):
                contract.verify(root)

    def test_candidate_rejects_unfinalized_build_metadata(self):
        with patch.object(contract, "validate_migrations", return_value=3), \
                patch.object(contract, "validate_critical_classes", return_value=13):
            with self.assertRaisesRegex(ValueError, "status must be ready"):
                contract.verify(ROOT, candidate=True)

    def test_artifact_inventory_is_complete_and_fixed(self):
        self.assertEqual(
            {"gateway", "auth", "monitor", "system", "file", "job", "oa", "inventory", "approval", "frontend"},
            set(contract.EXPECTED_ARTIFACTS),
        )


if __name__ == "__main__":
    unittest.main()
