#!/usr/bin/env python3

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


class SystemManagementNativeMySqlContractTest(unittest.TestCase):
    def test_gate_is_loopback_random_schema_and_container_free(self):
        source = (ROOT / "scripts/verify-system-management-native-mysql.sh").read_text(encoding="utf-8")
        self.assertIn("127.0.0.1|localhost|::1", source)
        self.assertIn("erp_system_mgmt_test_", source)
        self.assertIn("secrets.token_hex(6)", source)
        self.assertIn("trap cleanup", source)
        self.assertNotIn("docker ", source.lower())
        self.assertNotIn("testcontainers", source.lower())

    def test_fixture_contains_only_minimal_system_management_tables(self):
        source = (ROOT / "scripts/fixtures/system-management-baseline.sql").read_text(encoding="utf-8")
        for table in ("sys_config", "sys_menu", "sys_role_menu", "sys_user"):
            self.assertIn(f"CREATE TABLE {table}", source)
        self.assertNotIn("CREATE DATABASE", source.upper())
        self.assertIn("fixture-only-not-a-real-password", source)


if __name__ == "__main__":
    unittest.main()
