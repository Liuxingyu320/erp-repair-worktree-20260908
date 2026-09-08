#!/usr/bin/env python3

import unittest
from pathlib import Path

import verify_remote_system_management_release as remote


ROOT = Path(__file__).resolve().parent.parent


class RemoteSystemManagementReleaseContractTest(unittest.TestCase):
    def test_rejects_paths_outside_payload_root(self):
        with self.assertRaisesRegex(ValueError, "unsafe packaged"):
            remote.remote_artifact_path(Path("/tmp/payload"), "../secret.jar")
        with self.assertRaisesRegex(ValueError, "unsafe packaged"):
            remote.remote_artifact_path(Path("/tmp/payload"), "erp/system.jar")

    def test_development_manifest_is_never_accepted_as_remote_candidate(self):
        with self.assertRaisesRegex(ValueError, "not a ready candidate"):
            remote.verify(ROOT / "docker", ROOT / "scripts/system-management-release-20260714.json")

    def test_remote_wrappers_are_manifest_driven_and_do_not_probe_business_database(self):
        for relative in (
            "scripts/remote_deploy_verify.sh",
            "scripts/remote_erp_sha.sh",
            "scripts/remote_failed_jar_check.sh",
        ):
            source = (ROOT / relative).read_text(encoding="utf-8")
            self.assertIn("system-management-release-20260714.json", source)
            self.assertIn("verify_remote_system_management_release.py", source)
            self.assertNotIn("BossERP_NEW", source)
            self.assertNotIn("MYSQL_ROOT_PASS", source)


if __name__ == "__main__":
    unittest.main()
