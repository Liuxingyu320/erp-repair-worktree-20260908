#!/usr/bin/env python3

import unittest
from pathlib import Path

from finalize_system_management_release_manifest import build_candidate


ROOT = Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "scripts/system-management-release-20260714.json"


class FinalizeSystemManagementReleaseManifestTest(unittest.TestCase):
    def test_rejects_abbreviated_commit_before_reading_artifacts(self):
        with self.assertRaisesRegex(ValueError, "full lowercase Git SHA"):
            build_candidate(ROOT, TEMPLATE, "abc123", "2026-07-14T12:00:00Z", "150.0.0.0")

    def test_rejects_unversioned_browser_before_reading_artifacts(self):
        with self.assertRaisesRegex(ValueError, "four-part version"):
            build_candidate(
                ROOT,
                TEMPLATE,
                "0123456789abcdef0123456789abcdef01234567",
                "2026-07-14T12:00:00Z",
                "latest",
            )

    def test_template_remains_development_and_artifact_free(self):
        import json

        template = json.loads(TEMPLATE.read_text(encoding="utf-8"))
        self.assertEqual("development", template["status"])
        self.assertEqual("UNSET", template["build"]["commit"])
        self.assertEqual([], template["artifacts"])


if __name__ == "__main__":
    unittest.main()
