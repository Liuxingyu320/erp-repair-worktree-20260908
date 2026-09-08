#!/usr/bin/env python3

import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("verify-audit-log-policy.py")
SPEC = importlib.util.spec_from_file_location("verify_audit_log_policy", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)
inspect_source = MODULE.inspect_source


class AuditLogPolicyScannerTest(unittest.TestCase):
    def test_metadata_only_annotation_is_inventory_safe(self):
        endpoints, errors = inspect_source(
            '''
            class DemoController {
                @Log(title = "demo", isSaveRequestData = false, isSaveResponseData = false)
                public void save(Long recordId) {}
            }
            ''',
            "DemoController.java",
        )
        self.assertEqual([], errors)
        self.assertEqual("metadata-only", endpoints[0].policy)
        self.assertEqual("save", endpoints[0].method)

    def test_true_payload_switch_is_rejected(self):
        _, errors = inspect_source(
            '''
            class DemoController {
                @Log(title = "demo", isSaveRequestData = true)
                public void save(String password) {}
            }
            ''',
            "DemoController.java",
        )
        self.assertTrue(any("cannot be enabled" in error for error in errors))

    def test_allowlist_is_classified(self):
        endpoints, errors = inspect_source(
            '''
            class DemoController {
                @Log(title = "demo", includeParamNames = { "recordId" })
                public void save(Long recordId) {}
            }
            ''',
            "DemoController.java",
        )
        self.assertEqual([], errors)
        self.assertEqual(("recordId",), endpoints[0].allowed_fields)
        self.assertEqual("allowed-fields", endpoints[0].policy)


if __name__ == "__main__":
    unittest.main()
