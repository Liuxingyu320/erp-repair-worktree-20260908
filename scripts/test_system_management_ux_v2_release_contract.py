#!/usr/bin/env python3

import hashlib
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "scripts/system-management-ux-v2-release-20260714.json"


class SystemManagementUxV2ReleaseContractTest(unittest.TestCase):
    def test_manifest_and_sources_are_exact_and_rollback_is_guarded(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual("system-management-ux-v2-20260714", manifest["releaseId"])
        self.assertEqual(["system-management-20260714"], manifest["prerequisiteReleaseIds"])
        self.assertEqual("automatic", manifest["executionPolicy"])
        migration = manifest["migrations"][0]
        source = ROOT / manifest["sourceDirectory"] / migration["file"]
        self.assertEqual(migration["sha256"], hashlib.sha256(source.read_bytes()).hexdigest())
        rollback = manifest["rollback"]
        rollback_source = ROOT / "sql" / rollback["file"]
        self.assertEqual(rollback["sha256"], hashlib.sha256(rollback_source.read_bytes()).hexdigest())
        packaged_rollback = ROOT / manifest["deployDirectory"] / rollback["file"]
        self.assertTrue(packaged_rollback.is_file())
        self.assertEqual(rollback["sha256"], hashlib.sha256(packaged_rollback.read_bytes()).hexdigest())
        rollback_sql = rollback_source.read_text(encoding="utf-8").lower()
        self.assertIn("config_value = 'false'", rollback_sql)
        self.assertIn("create_by = 'system'", rollback_sql)

    def test_native_gate_is_loopback_random_schema_and_container_free(self):
        source = (ROOT / "scripts/verify-system-management-ux-v2-native-mysql.sh").read_text(encoding="utf-8")
        self.assertIn("127.0.0.1|localhost|::1", source)
        self.assertIn("secrets.token_hex(6)", source)
        self.assertIn("trap cleanup", source)
        self.assertNotIn("docker ", source.lower())
        self.assertNotIn("testcontainers", source.lower())


if __name__ == "__main__":
    unittest.main()
