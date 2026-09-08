#!/usr/bin/env python3

import importlib.util
import json
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("classify-system-oper-log.py")
SPEC = importlib.util.spec_from_file_location("classify_system_oper_log", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class OperLogClassifierTest(unittest.TestCase):
    def test_report_contains_counts_and_ids_but_no_payload(self):
        secret = "data:image/png;base64," + "A" * 160
        report = MODULE.summarize(
            [
                {"oper_id": 7, "oper_param": json.dumps({"signatureDataUrl": secret}), "json_result": "", "error_msg": ""},
                {"oper_id": 8, "oper_param": "{}", "json_result": "{}", "error_msg": ""},
            ]
        )

        rendered = json.dumps(report)
        self.assertEqual(2, report["scannedRows"])
        self.assertEqual(1, report["sensitiveRows"])
        self.assertEqual(7, report["minimumSensitiveOperId"])
        self.assertEqual(7, report["maximumSensitiveOperId"])
        self.assertNotIn(secret, rendered)

    def test_disguised_private_path_and_long_number_are_detected(self):
        categories = MODULE.classify_record(
            {"oper_param": "reference=/Users/example/private/a.pdf 6222020202020202", "json_result": "", "error_msg": ""}
        )
        self.assertIn("private_path", categories)
        self.assertIn("long_identity_or_bank_number", categories)


if __name__ == "__main__":
    unittest.main()
