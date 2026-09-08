import datetime as dt
import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("verify-system-permission-alignment.py")
SPEC = importlib.util.spec_from_file_location("permission_alignment", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class PermissionAlignmentTest(unittest.TestCase):
    def test_extracts_java_and_frontend_and_permissions(self):
        managed = {"system:user:query", "system:user:pii:read"}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            java = root / "Controller.java"
            vue = root / "view.vue"
            java.write_text(
                '@RequiresPermissions(value = { "system:user:query", "system:user:pii:read" }, logical = Logical.AND)',
                encoding="utf-8",
            )
            vue.write_text(
                '<button v-if="$auth.hasPermiAnd([\'system:user:query\', \'system:user:pii:read\'])" />',
                encoding="utf-8",
            )
            self.assertEqual(MODULE.extract_backend_permissions([java], managed), managed)
            self.assertEqual(MODULE.extract_frontend_permissions([vue], managed), managed)

    def test_unexplained_and_expired_differences_fail(self):
        difference = MODULE.Difference("backend_without_frontend", "system:user:query", "1000")
        payload = {
            "schemaVersion": 1,
            "entries": [{
                "category": "backend_without_frontend",
                "permission": "system:user:query",
                "menu_id": "1000",
                "reason": "temporary API-only contract",
                "owner": "system owner",
                "expires_at": "2026-01-01",
            }],
        }
        remaining, errors = MODULE.validate_and_apply_allowlist(
            [difference], payload, {"system:user:query"}, dt.date(2026, 7, 14)
        )
        self.assertFalse(remaining)
        self.assertTrue(any("expired" in error for error in errors))

    def test_calculates_all_four_difference_classes(self):
        differences = MODULE.calculate_differences(
            {"system:a:list", "system:b:list"},
            {"system:b:list", "system:c:list"},
            {"system:c:list", "system:d:list"},
            {"system:a:list": "1", "system:b:list": "2"},
        )
        self.assertEqual({row.category for row in differences}, set(MODULE.CATEGORIES))


if __name__ == "__main__":
    unittest.main()
