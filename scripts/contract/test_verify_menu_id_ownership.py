#!/usr/bin/env python3
"""Unit tests for the static sys_menu ID ownership release gate."""

from __future__ import annotations

import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import verify_menu_id_ownership as verifier  # noqa: E402


class MenuIdOwnershipScannerTest(unittest.TestCase):
    def test_multiline_multirow_insert_respects_column_order_and_comments(self) -> None:
        sql = """
        -- (9999, 'fake/component', 'fake:permission')
        INSERT IGNORE INTO `sys_menu` (`perms`, `menu_id`, `component`, `menu_name`)
        VALUES
          ('oa:signTask:list', 9650, 'oa/signTask/index', '合同签约中心'),
          ('oa:signTask:query', 9651, NULL, '签约任务查询')
        ON DUPLICATE KEY UPDATE perms = VALUES(perms);
        """

        definitions = verifier.scan_sql_text(sql, "menu.sql")
        observed = {(item.menu_id, item.field, item.value) for item in definitions}

        self.assertEqual(
            observed,
            {
                (9650, "component", "oa/signTask/index"),
                (9650, "perms", "oa:signTask:list"),
                (9651, "component", None),
                (9651, "perms", "oa:signTask:query"),
            },
        )
        self.assertTrue(all(item.line >= 4 for item in definitions))

    def test_known_hr_and_signing_collision_is_detected(self) -> None:
        hr_sql = """
        INSERT INTO sys_menu (menu_id, component, perms) VALUES
          (4600, NULL, ''),
          (4601, 'hr/employee/index', 'hr:employee:list'),
          (4602, 'hr/onboarding/index', 'hr:onboarding:list'),
          (4603, 'hr/completeness/index', 'hr:completeness:list'),
          (4604, 'hr/importExport/index', 'hr:import:preview'),
          (4611, NULL, 'hr:employee:query');
        """
        signing_sql = """
        INSERT INTO sys_menu (menu_id, component, perms) VALUES
          (4600, 'oa/signTask/index', 'oa:signTask:list'),
          (4601, NULL, 'oa:signTask:query'),
          (4602, NULL, 'oa:signTask:revalidate'),
          (4603, NULL, 'oa:signTask:confirm'),
          (4604, NULL, 'oa:signTask:send'),
          (4611, NULL, 'oa:signPackage:template');
        """

        definitions = verifier.scan_sql_text(hr_sql, "hr.sql")
        definitions += verifier.scan_sql_text(signing_sql, "sign.sql")
        conflicts = verifier.build_conflicts(definitions)

        self.assertEqual(list(conflicts), [4600, 4601, 4602, 4603, 4604, 4611])
        self.assertEqual(set(conflicts[4600]), {"component", "perms"})
        self.assertEqual(set(conflicts[4611]), {"perms"})

    def test_mirrored_identical_definitions_are_not_conflicts(self) -> None:
        sql = "INSERT INTO sys_menu (menu_id, component, perms) VALUES (9650, 'oa/signTask/index', 'oa:signTask:list');"
        definitions = verifier.scan_sql_text(sql, "sql/menu.sql")
        definitions += verifier.scan_sql_text(sql, "docker/mysql/db/menu.sql")

        self.assertEqual(verifier.build_conflicts(definitions), {})

    def test_insert_set_and_numeric_update_assignments_are_scanned(self) -> None:
        sql = """
        INSERT INTO sys_menu SET menu_id=77, component='old/page', perms='old:list';
        UPDATE sys_menu AS menu
           SET component = NULL, perms = 'new:list', update_time = NOW()
         WHERE menu.`menu_id` IN (77, 78);
        UPDATE sys_menu SET perms = 'range:list' WHERE menu_id BETWEEN 78 AND 79;
        UPDATE sys_menu SET perms = CONCAT('ignored', ':dynamic') WHERE menu_id = 77;
        """

        conflicts = verifier.build_conflicts(verifier.scan_sql_text(sql, "updates.sql"))

        self.assertEqual(set(conflicts[77]), {"component", "perms"})
        self.assertEqual(set(conflicts[78]), {"perms"})
        self.assertNotIn(79, conflicts)

    def test_dynamic_ids_and_computed_values_do_not_claim_static_ownership(self) -> None:
        sql = """
        INSERT INTO sys_menu (menu_id, component, perms)
        VALUES (@next_menu_id, 'dynamic/page', 'dynamic:list');
        UPDATE sys_menu SET perms = CONCAT('dynamic', ':list') WHERE menu_id = 42;
        UPDATE sys_menu SET component = 'page' WHERE menu_id = @menu_id;
        """

        self.assertEqual(verifier.scan_sql_text(sql, "dynamic.sql"), [])

    def test_complete_route_identity_fields_are_scanned(self) -> None:
        sql = """
        INSERT INTO sys_menu (menu_id, parent_id, path, component, route_name, menu_type, perms)
        VALUES (9650, 3000, 'sign-task', 'oa/signTask/index', 'OaSignTask', 'C', 'oa:signTask:list');
        """

        definitions = verifier.scan_sql_text(sql, "route.sql")
        observed = {item.field: item.value for item in definitions}

        self.assertEqual(
            observed,
            {
                "parent_id": 3000,
                "path": "sign-task",
                "component": "oa/signTask/index",
                "route_name": "OaSignTask",
                "menu_type": "C",
                "perms": "oa:signTask:list",
            },
        )

    def test_static_insert_select_claims_ownership(self) -> None:
        sql = """
        INSERT INTO sys_menu (menu_id, parent_id, path, component, route_name, menu_type, perms)
        SELECT 9650, 3000, 'sign-task', 'oa/signTask/index', 'OaSignTask', 'C', 'oa:signTask:list';
        """

        definitions = verifier.scan_sql_text(sql, "insert-select.sql")
        observed = {item.field: item.value for item in definitions}

        self.assertEqual(
            observed,
            {
                "parent_id": 3000,
                "path": "sign-task",
                "component": "oa/signTask/index",
                "route_name": "OaSignTask",
                "menu_type": "C",
                "perms": "oa:signTask:list",
            },
        )


class MenuIdOwnershipAllowlistTest(unittest.TestCase):
    @staticmethod
    def _conflicts(sql: str) -> tuple[dict, verifier.ConflictSpec]:
        conflicts = verifier.build_conflicts(verifier.scan_sql_text(sql, "fixture.sql"))
        return conflicts, verifier.conflict_spec(conflicts)

    def test_exact_allowlist_passes_but_a_new_conflict_fails(self) -> None:
        historical = """
        INSERT INTO sys_menu (menu_id, component, perms) VALUES
          (4600, NULL, ''),
          (4600, 'oa/signTask/index', 'oa:signTask:list');
        """
        _, actual = self._conflicts(historical)

        accepted = verifier.evaluate_gate(actual, "release", actual)
        self.assertTrue(accepted.passed)
        self.assertEqual(accepted.allowed_ids, (4600,))

        with_new_conflict = historical + """
        INSERT INTO sys_menu (menu_id, component, perms) VALUES
          (9650, 'other/page', 'other:list'),
          (9650, 'oa/signTask/index', 'oa:signTask:list');
        """
        _, changed = self._conflicts(with_new_conflict)
        rejected = verifier.evaluate_gate(changed, "release", actual)

        self.assertFalse(rejected.passed)
        self.assertEqual(rejected.allowed_ids, (4600,))
        self.assertEqual(rejected.failing_ids, (9650,))

    def test_third_owner_on_allowlisted_id_fails_exact_match(self) -> None:
        old_sql = """
        INSERT INTO sys_menu (menu_id, component, perms) VALUES
          (4600, NULL, ''),
          (4600, 'oa/signTask/index', 'oa:signTask:list');
        """
        _, allowed = self._conflicts(old_sql)
        new_sql = old_sql + """
        INSERT INTO sys_menu (menu_id, component, perms)
        VALUES (4600, 'unexpected/page', 'unexpected:list');
        """
        _, actual = self._conflicts(new_sql)

        result = verifier.evaluate_gate(actual, "release", allowed)

        self.assertFalse(result.passed)
        self.assertEqual(result.failing_ids, (4600,))
        self.assertEqual(result.allowed_ids, ())

    def test_stale_allowlist_entry_fails(self) -> None:
        allowed: verifier.ConflictSpec = {1: {"perms": ("first", "second")}}

        result = verifier.evaluate_gate({}, "release", allowed)

        self.assertFalse(result.passed)
        self.assertEqual(result.stale_allowlist_ids, (1,))

    def test_allowlist_loader_requires_canonical_exact_values(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            path = Path(temporary_directory) / "allowlist.json"
            path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "conflicts": {
                            "4600": {"component": [None, "oa/signTask/index"], "perms": ["", "oa:signTask:list"]}
                        },
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )

            loaded = verifier.load_allowlist(path)

        self.assertEqual(
            loaded,
            {
                4600: {
                    "component": (None, "oa/signTask/index"),
                    "perms": ("", "oa:signTask:list"),
                }
            },
        )


class MenuIdOwnershipCliTest(unittest.TestCase):
    def test_cli_strict_fails_and_release_reports_allowlisted_conflict(self) -> None:
        sql = """
        INSERT INTO sys_menu (menu_id, component, perms) VALUES
          (4600, NULL, ''),
          (4600, 'oa/signTask/index', 'oa:signTask:list');
        """
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            sql_path = root / "menu.sql"
            allowlist_path = root / "allowlist.json"
            sql_path.write_text(sql, encoding="utf-8")
            conflicts = verifier.build_conflicts(verifier.scan_sql_text(sql, str(sql_path)))
            allowlist_path.write_text(
                json.dumps(verifier.allowlist_document(verifier.conflict_spec(conflicts))),
                encoding="utf-8",
            )

            strict_output = io.StringIO()
            with contextlib.redirect_stdout(strict_output):
                strict_code = verifier.main([str(sql_path)])

            release_output = io.StringIO()
            with contextlib.redirect_stdout(release_output):
                release_code = verifier.main(
                    [str(sql_path), "--mode", "release", "--allowlist", str(allowlist_path)]
                )

        self.assertEqual(strict_code, 1)
        self.assertIn("ERROR menu_id 4600", strict_output.getvalue())
        self.assertEqual(release_code, 0)
        self.assertIn("ALLOWED menu_id 4600", release_output.getvalue())
        self.assertIn("PASS:", release_output.getvalue())

    def test_release_mode_requires_explicit_allowlist(self) -> None:
        errors = io.StringIO()
        with contextlib.redirect_stderr(errors):
            code = verifier.main(["--mode", "release"])

        self.assertEqual(code, 2)
        self.assertIn("requires --allowlist", errors.getvalue())


class RepositoryMenuIdOwnershipIntegrationTest(unittest.TestCase):
    def test_a1_reserved_range_has_the_exact_twenty_item_mapping(self) -> None:
        source = SCRIPT_DIR.parents[1] / "sql/erp_oa_sign_menu_permission_repair_20260716.sql"
        definitions = verifier.scan_sql_text(source.read_text(encoding="utf-8"), source.as_posix())
        actual: dict[int, dict[str, verifier.StaticValue]] = {}
        for definition in definitions:
            if 9650 <= definition.menu_id <= 9669:
                actual.setdefault(definition.menu_id, {})[definition.field] = definition.value

        permissions = (
            "oa:signTask:list",
            "oa:signTask:query",
            "oa:signTask:revalidate",
            "oa:signTask:send",
            "oa:signTask:retry",
            "oa:signTask:cancel",
            "oa:signTask:technicalEvidence",
            "oa:signPackage:list",
            "oa:signPackage:query",
            "oa:signPackage:add",
            "oa:signPackage:send",
            "oa:signPackage:void",
            "oa:signPackage:template",
            "oa:signTask:remind",
            "oa:signTask:resolveRefusal",
            "oa:signTask:resolveExpiry",
            "oa:signCompany:list",
            "oa:signCompany:edit",
            "oa:signSeal:list",
            "oa:signSeal:edit",
        )
        expected = {
            menu_id: {
                "parent_id": 3000 if menu_id == 9650 else 9650,
                "path": "sign-task" if menu_id == 9650 else "",
                "component": "oa/signTask/index" if menu_id == 9650 else None,
                "route_name": "OaSignTask" if menu_id == 9650 else "",
                "menu_type": "C" if menu_id == 9650 else "F",
                "perms": permissions[menu_id - 9650],
            }
            for menu_id in range(9650, 9670)
        }

        self.assertEqual(actual, expected)

    def test_repository_conflicts_match_the_frozen_release_allowlist(self) -> None:
        root = SCRIPT_DIR.parents[1]
        _, definitions = verifier.scan_paths([root / "sql", root / "docker/mysql/db"])
        actual = verifier.conflict_spec(verifier.build_conflicts(definitions))
        allowed = verifier.load_allowlist(SCRIPT_DIR / "menu_id_conflicts_20260716.json")

        result = verifier.evaluate_gate(actual, "release", allowed)

        self.assertTrue(result.passed, result)
        self.assertEqual(result.allowed_ids, tuple(sorted(allowed)))


if __name__ == "__main__":
    unittest.main(verbosity=2)
