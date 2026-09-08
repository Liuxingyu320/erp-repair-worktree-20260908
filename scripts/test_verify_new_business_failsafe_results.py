#!/usr/bin/env python3

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify_new_business_failsafe_results.py")
SPEC = importlib.util.spec_from_file_location("failsafe_results", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class FailsafeResultGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.classes = [f"com.erp.example.ReleaseCase{index}IT" for index in range(6)]
        report_dir = self.root / "erp-modules/example/target/failsafe-reports"
        report_dir.mkdir(parents=True)
        for class_name in self.classes:
            (report_dir / f"TEST-{class_name}.xml").write_text(
                f'<testsuite name="{class_name}" tests="2" failures="0" '
                'errors="0" skipped="0"></testsuite>\n',
                encoding="utf-8",
            )

    def tearDown(self):
        self.temp.cleanup()

    def test_accepts_all_fresh_green_reports(self):
        result = MODULE.collect_results(self.root, self.classes, 0)
        self.assertEqual(6, result["executedCount"])
        self.assertEqual(12, result["tests"])

    def test_rejects_skipped_report(self):
        report = next(
            self.root.glob("erp-modules/*/target/failsafe-reports/TEST-*.xml")
        )
        report.write_text(
            '<testsuite name="com.erp.example.ReleaseCase0IT" tests="2" '
            'failures="0" errors="0" skipped="1"></testsuite>\n',
            encoding="utf-8",
        )
        with self.assertRaisesRegex(RuntimeError, "invalid="):
            MODULE.collect_results(self.root, self.classes, 0)

    def test_rejects_missing_report(self):
        next(
            self.root.glob("erp-modules/*/target/failsafe-reports/TEST-*.xml")
        ).unlink()
        with self.assertRaisesRegex(RuntimeError, "missing="):
            MODULE.collect_results(self.root, self.classes, 0)

    def test_manifest_paths_resolve_to_classes(self):
        manifest = {
            "integrationTests": [
                "erp-modules/example/src/test/java/" + name.replace(".", "/") + ".java"
                for name in self.classes
            ]
        }
        self.assertEqual(self.classes, MODULE.expected_classes(manifest))


if __name__ == "__main__":
    unittest.main()
