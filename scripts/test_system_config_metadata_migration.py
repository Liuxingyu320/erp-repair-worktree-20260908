#!/usr/bin/env python3

import hashlib
import json
import unittest
from pathlib import Path

from release_migration_contract import load_and_validate_manifest


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "scripts/system-config-metadata-release-20260813.json"
SQL_NAME = "erp_system_config_metadata_20260813.sql"


class SystemConfigMetadataMigrationTest(unittest.TestCase):
    def test_manifest_is_automatic_minimal_and_backup_guarded(self):
        manifest = load_and_validate_manifest(
            MANIFEST, require_deploy_mirror=False
        )

        self.assertEqual("system-config-metadata-20260813", manifest["releaseId"])
        self.assertEqual("automatic", manifest["executionPolicy"])
        self.assertEqual(1, manifest["migrationCount"])
        self.assertEqual([SQL_NAME], [item["file"] for item in manifest["migrations"]])
        self.assertEqual([], manifest["migrations"][0]["creates"])
        self.assertEqual(
            ["sys_config", "sys_user"],
            manifest["migrations"][0]["backupTables"],
        )

    def test_source_sql_list_and_manifest_are_hash_bound(self):
        manifest_bytes = MANIFEST.read_bytes()
        list_path = ROOT / "scripts/system-config-metadata-migrations-20260813.list"
        source = ROOT / "sql" / SQL_NAME

        self.assertEqual(
            [SQL_NAME],
            [
                line.strip()
                for line in list_path.read_text(encoding="utf-8").splitlines()
                if line.strip() and not line.lstrip().startswith("#")
            ],
        )
        manifest = json.loads(manifest_bytes)
        self.assertEqual(
            hashlib.sha256(source.read_bytes()).hexdigest(),
            manifest["migrations"][0]["sha256"],
        )

    def test_sql_is_idempotent_expand_only_and_verifies_exact_contract(self):
        sql = (ROOT / "sql" / SQL_NAME).read_text(encoding="utf-8")
        upper = sql.upper()

        for fragment in (
            "ADD COLUMN group_code VARCHAR(32) NOT NULL DEFAULT 'custom'",
            "ADD COLUMN value_type VARCHAR(20) NOT NULL DEFAULT 'string'",
            "ADD COLUMN sensitive_flag CHAR(1) NOT NULL DEFAULT 'N'",
            "ADD COLUMN validation_rule VARCHAR(500) NULL DEFAULT NULL",
            "ADD COLUMN display_order INT NOT NULL DEFAULT 100",
            "ADD COLUMN version INT NOT NULL DEFAULT 1",
            "ADD COLUMN must_change_password CHAR(1) NOT NULL DEFAULT '0'",
            "ADD INDEX idx_sys_config_group_order (group_code, display_order, config_id)",
            "system config metadata column fingerprint verification failed",
            "system config metadata index fingerprint verification failed",
        ):
            self.assertIn(fragment, sql)

        self.assertGreaterEqual(upper.count("INFORMATION_SCHEMA.COLUMNS"), 10)
        self.assertIn("INFORMATION_SCHEMA.STATISTICS", upper)
        self.assertNotIn("UPDATE SYS_CONFIG", upper)
        self.assertNotIn("UPDATE SYS_USER", upper)
        self.assertNotIn("DELETE FROM", upper)
        self.assertNotIn("TRUNCATE", upper)
        self.assertNotIn("DROP TABLE", upper)

    def test_application_queries_are_covered_by_the_schema_expansion(self):
        config_mapper = (
            ROOT
            / "erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml"
        ).read_text(encoding="utf-8")
        user_mapper = (
            ROOT
            / "erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml"
        ).read_text(encoding="utf-8")
        sql = (ROOT / "sql" / SQL_NAME).read_text(encoding="utf-8")

        for column in (
            "group_code",
            "value_type",
            "sensitive_flag",
            "validation_rule",
            "display_order",
            "version",
        ):
            self.assertIn(column, config_mapper)
            self.assertIn("ADD COLUMN " + column, sql)
        self.assertIn("u.must_change_password", user_mapper)
        self.assertIn("ADD COLUMN must_change_password", sql)

    def test_runbook_binds_build_and_deploy_to_the_same_manifest(self):
        runbook = (
            ROOT / "docs/ALIYUN_ECS_DEPLOYMENT_RUNBOOK.md"
        ).read_text(encoding="utf-8")

        self.assertGreaterEqual(
            runbook.count(
                "--migration-manifest scripts/system-config-metadata-release-20260813.json"
            ),
            2,
        )
        self.assertIn(
            "--approve-migrations system-config-metadata-20260813", runbook
        )
        self.assertIn("--database-name bosserp_stock_state_75c59ee", runbook)
        self.assertIn("备份这两张表", runbook)


if __name__ == "__main__":
    unittest.main()
