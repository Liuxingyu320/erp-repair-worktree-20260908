#!/usr/bin/env python3

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


class SystemNoticeWorkflowNativeMySqlContractTest(unittest.TestCase):
    def test_gate_is_loopback_random_schema_and_container_free(self):
        source = (ROOT / "scripts/verify-system-notice-workflow-native-mysql.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("127.0.0.1|localhost|::1", source)
        self.assertIn("erp_notice_workflow_test_", source)
        self.assertIn("secrets.token_hex(6)", source)
        self.assertIn("trap cleanup", source)
        self.assertIn("idempotent_runs=2", source)
        self.assertIn("guarded rollback unexpectedly succeeded", source)
        self.assertNotIn("docker ", source.lower())
        self.assertNotIn("testcontainers", source.lower())

    def test_fixture_is_synthetic_pre_workflow_schema(self):
        source = (ROOT / "scripts/fixtures/system-notice-workflow-baseline.sql").read_text(
            encoding="utf-8"
        )
        for table in (
            "sys_config",
            "sys_menu",
            "sys_role_menu",
            "sys_user",
            "sys_notice",
            "sys_notice_read",
        ):
            self.assertIn(f"CREATE TABLE {table}", source)
        self.assertNotIn("CREATE DATABASE", source.upper())
        self.assertNotIn("lifecycle_status", source.lower())
        self.assertIn("disabled-reader", source)


if __name__ == "__main__":
    unittest.main()
