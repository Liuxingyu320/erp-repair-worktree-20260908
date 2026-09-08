#!/usr/bin/env python3

from __future__ import annotations

import copy
import io
import json
import os
import stat
import tempfile
import textwrap
import threading
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest import mock

from stage0_db_evidence import (
    EVIDENCE_KIND,
    META_SERVER_MARKER,
    PROFILES,
    SENTINEL_MARKER,
    SENTINEL_VALUE,
    EvidenceError,
    ProtocolError,
    build_contract,
    build_evidence,
    build_parser,
    check_passes,
    execute_mysql,
    git_commit,
    main,
    parse_mysql_output,
    prepare_output_destination,
    render_mysql_sql,
    require_clean_worktree,
    sha256_bytes,
    validate_defaults_file,
    validate_target_label,
    verify_evidence,
    write_evidence,
)


ROOT = Path(__file__).resolve().parents[1]
TARGET_LABEL = "prod-cn-primary"


def passing_metrics(contract, *, health_present: bool = True):
    metrics = {}
    for check in contract.checks:
        if check.check_id == "HEALTH_TABLE_COUNT":
            metrics[check.check_id] = 1 if health_present else 0
        elif check.check_id == "HEALTH_REQUIRED_COLUMNS":
            metrics[check.check_id] = 2 if health_present else 0
        elif isinstance(check.expected, tuple):
            metrics[check.check_id] = check.expected[-1]
        else:
            metrics[check.check_id] = check.expected
    return metrics


def mysql_output(contract, metrics, server_version="8.0.36"):
    lines = [f"{META_SERVER_MARKER}\t{server_version}"]
    for check in contract.checks:
        lines.append(f"{check.marker}\t{metrics[check.check_id]}")
    lines.append(f"{SENTINEL_MARKER}\t{SENTINEL_VALUE}")
    return "\n".join(lines) + "\n"


def secure_file(path: Path, text: str = "[client]\n") -> Path:
    path.write_text(text, encoding="utf-8")
    path.chmod(0o600)
    return path


class ContractTests(unittest.TestCase):
    def test_target_label_and_explicit_connection_are_required_by_cli(self):
        parser = build_parser()
        commands = (
            (
                "collect",
                "--profile",
                "pre-maintenance",
                "--defaults-file",
                "/tmp/client.cnf",
                "--database",
                "BossERP_NEW",
                "--host",
                "db.example",
                "--port",
                "3306",
                "--user",
                "evidence_reader",
                "--output",
                "/tmp/evidence.json",
            ),
            (
                "verify",
                "--evidence",
                "/tmp/evidence.json",
                "--profile",
                "pre-maintenance",
                "--database",
                "BossERP_NEW",
            ),
        )
        for command in commands:
            with self.subTest(command=command[0]), mock.patch(
                "sys.stderr", new=io.StringIO()
            ):
                with self.assertRaises(SystemExit):
                    parser.parse_args(command)

    def test_profiles_pin_expected_dynamic_manifest_counts(self):
        pre = build_contract("pre-maintenance")
        ready = build_contract("release-ready")

        self.assertEqual(PROFILES, ("pre-maintenance", "release-ready"))
        self.assertEqual(22, len(pre.checks))
        self.assertEqual(33, len(ready.checks))
        expected = {check.check_id: check.expected for check in pre.checks}
        self.assertEqual(17, expected["NEW_BUSINESS_BACKUP_TABLES"])
        self.assertEqual(9, expected["UNIFIED_REQUIRED_TABLES"])
        self.assertEqual(132, expected["UNIFIED_REQUIRED_COLUMNS"])
        self.assertEqual(10, expected["UNIFIED_REQUIRED_INDEXES"])
        self.assertEqual((0, 1), expected["HEALTH_TABLE_COUNT"])
        self.assertEqual(64, expected["SIGN_COLUMN_FINGERPRINTS"])
        ready_expected = {check.check_id: check.expected for check in ready.checks}
        self.assertEqual(26, ready_expected["NEW_BUSINESS_CREATED_TABLES"])
        self.assertEqual(7, ready_expected["NEW_BUSINESS_SAFE_FLAGS"])
        self.assertEqual(0, ready_expected["SIGN_SEAL_CODE_MISSING"])
        self.assertEqual(1, ready_expected["SIGN_ROLE_CONTRACT"])
        self.assertEqual(12, ready_expected["APPROVAL_TABLES"])
        self.assertEqual(232, ready_expected["APPROVAL_COLUMNS"])
        self.assertEqual(24, ready_expected["APPROVAL_UNIQUE_KEYS"])
        self.assertEqual(16, ready_expected["APPROVAL_ASSOCIATION_COLUMNS"])
        self.assertEqual(4, ready_expected["APPROVAL_ASSOCIATION_INDEXES"])
        self.assertEqual(2, ready_expected["INVENTORY_SAFE_FLAGS"])
        self.assertEqual(
            {"newBusiness", "approvalExpand", "signFinal", "inventorySafety"},
            set(ready.manifest_sha256),
        )
        approval_tables = next(
            check for check in ready.checks if check.check_id == "APPROVAL_TABLES"
        )
        self.assertEqual(1, approval_tables.expression.count("table_name IN ("))
        sign_role = next(
            check for check in ready.checks if check.check_id == "SIGN_ROLE_CONTRACT"
        )
        self.assertIn("HAVING COUNT(*)=1", sign_role.expression)
        self.assertIn("SUM(role_name='合同签约经办人'", sign_role.expression)
        oa_legacy = next(
            check for check in ready.checks if check.check_id == "OA_LEGACY_PENDING"
        )
        self.assertIn("COALESCE(LOWER(TRIM(status)),'')", oa_legacy.expression)
        by_id = {check.check_id: check for check in ready.checks}
        for check_id in ("SIGN_INDEXES", "APPROVAL_ASSOCIATION_INDEXES"):
            with self.subTest(check=check_id):
                expression = by_id[check_id].expression
                self.assertIn("non_unique", expression)
                self.assertIn("non_unique=1", expression)
                self.assertIn("GROUP BY table_name,index_name,non_unique", expression)

    def test_all_check_expressions_have_a_single_safe_select_shape(self):
        scalar_checks = {
            "MYSQL_MAJOR",
            "MYSQL_IS_MARIADB",
            "SESSION_REPEATABLE_READ",
            "SESSION_READ_ONLY",
        }
        for profile in PROFILES:
            for check in build_contract(profile).checks:
                with self.subTest(profile=profile, check=check.check_id):
                    expression = check.expression
                    self.assertTrue(expression.startswith("SELECT "))
                    self.assertNotIn(";", expression)
                    self.assertEqual(expression.count("("), expression.count(")"))
                    self.assertNotRegex(expression, r"\b(?:WHERE|AND|OR)\s+SELECT\b")
                    self.assertEqual(
                        0 if check.check_id in scalar_checks else 1,
                        expression.count("SELECT COUNT(*) FROM "),
                    )

        sign_created_columns = next(
            check
            for check in build_contract("release-ready").checks
            if check.check_id == "SIGN_CREATED_COLUMNS"
        )
        self.assertEqual(
            1,
            sign_created_columns.expression.count(
                "SELECT COUNT(*) FROM information_schema.columns WHERE "
            ),
        )

    def test_rendered_sql_is_one_fixed_read_only_snapshot(self):
        sql = render_mysql_sql(build_contract("release-ready"))

        self.assertEqual(
            1,
            sql.count("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;"),
        )
        self.assertEqual(1, sql.count("SET SESSION TRANSACTION READ ONLY;"))
        self.assertEqual(1, sql.count("START TRANSACTION WITH CONSISTENT SNAPSHOT;"))
        self.assertEqual(1, sql.count(SENTINEL_MARKER))
        self.assertTrue(sql.rstrip().endswith(f"'{SENTINEL_VALUE}';"))
        for line in sql.splitlines():
            self.assertNotRegex(
                line,
                r"(?i)^\s*(INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|"
                r"TRUNCATE|CALL|PREPARE|EXECUTE|LOCK|UNLOCK)\b",
            )


class ProtocolTests(unittest.TestCase):
    def setUp(self):
        self.contract = build_contract("pre-maintenance")
        self.metrics = passing_metrics(self.contract)
        self.output = mysql_output(self.contract, self.metrics)

    def test_valid_complete_output(self):
        version, values = parse_mysql_output(self.output, self.contract)
        self.assertEqual("8.0.36", version)
        self.assertEqual(self.metrics, values)

    def test_missing_marker_is_rejected(self):
        missing = self.output.replace(
            f"{self.contract.checks[3].marker}\t{self.metrics[self.contract.checks[3].check_id]}\n",
            "",
        )
        with self.assertRaisesRegex(ProtocolError, "missing check"):
            parse_mysql_output(missing, self.contract)

    def test_duplicate_marker_is_rejected(self):
        row = f"{self.contract.checks[0].marker}\t8\n"
        with self.assertRaisesRegex(ProtocolError, "duplicate check"):
            parse_mysql_output(row + self.output, self.contract)

    def test_unknown_marker_is_rejected(self):
        with self.assertRaisesRegex(ProtocolError, "unknown"):
            parse_mysql_output("UNEXPECTED\t1\n" + self.output, self.contract)

    def test_reordered_markers_are_rejected(self):
        lines = self.output.splitlines()
        lines[2], lines[3] = lines[3], lines[2]
        with self.assertRaisesRegex(ProtocolError, "order"):
            parse_mysql_output("\n".join(lines) + "\n", self.contract)

    def test_non_integer_is_rejected(self):
        check = self.contract.checks[0]
        damaged = self.output.replace(f"{check.marker}\t8", f"{check.marker}\t8 rows")
        with self.assertRaisesRegex(ProtocolError, "non-integer"):
            parse_mysql_output(damaged, self.contract)

    def test_missing_or_duplicate_sentinel_is_rejected(self):
        sentinel = f"{SENTINEL_MARKER}\t{SENTINEL_VALUE}\n"
        with self.assertRaisesRegex(ProtocolError, "missing completion"):
            parse_mysql_output(self.output.replace(sentinel, ""), self.contract)
        with self.assertRaisesRegex(ProtocolError, "duplicate"):
            parse_mysql_output(self.output + sentinel, self.contract)


class CheckEvaluationTests(unittest.TestCase):
    def test_premaintenance_allows_missing_health_but_not_partial_health(self):
        contract = build_contract("pre-maintenance")
        missing = passing_metrics(contract, health_present=False)
        by_id = {check.check_id: check for check in contract.checks}
        self.assertTrue(check_passes(by_id["HEALTH_TABLE_COUNT"], missing))
        self.assertTrue(check_passes(by_id["HEALTH_REQUIRED_COLUMNS"], missing))

        partial = dict(missing)
        partial["HEALTH_TABLE_COUNT"] = 1
        partial["HEALTH_REQUIRED_COLUMNS"] = 1
        self.assertFalse(check_passes(by_id["HEALTH_REQUIRED_COLUMNS"], partial))

    def test_release_ready_requires_created_tables_health_oa_and_exact_false_flags(self):
        contract = build_contract("release-ready")
        values = passing_metrics(contract)
        by_id = {check.check_id: check for check in contract.checks}

        values["NEW_BUSINESS_CREATED_TABLES"] = 28
        self.assertFalse(check_passes(by_id["NEW_BUSINESS_CREATED_TABLES"], values))
        values = passing_metrics(contract)
        values["NEW_BUSINESS_SAFE_FLAGS"] = 9
        self.assertFalse(check_passes(by_id["NEW_BUSINESS_SAFE_FLAGS"], values))
        values = passing_metrics(contract)
        values["HEALTH_TABLE_COUNT"] = 0
        values["HEALTH_REQUIRED_COLUMNS"] = 0
        self.assertFalse(check_passes(by_id["HEALTH_TABLE_COUNT"], values))
        self.assertFalse(check_passes(by_id["HEALTH_REQUIRED_COLUMNS"], values))
        values = passing_metrics(contract)
        values["OA_LEGACY_PENDING"] = 1
        self.assertFalse(check_passes(by_id["OA_LEGACY_PENDING"], values))
        values = passing_metrics(contract)
        values["INVENTORY_SAFE_FLAGS"] = 1
        self.assertFalse(check_passes(by_id["INVENTORY_SAFE_FLAGS"], values))
        values = passing_metrics(contract)
        values["SIGN_SEAL_CODE_MISSING"] = 1
        self.assertFalse(check_passes(by_id["SIGN_SEAL_CODE_MISSING"], values))
        values = passing_metrics(contract)
        values["SIGN_ROLE_CONTRACT"] = 0
        self.assertFalse(check_passes(by_id["SIGN_ROLE_CONTRACT"], values))

    def test_mariadb_major_version_never_satisfies_mysql_contract(self):
        contract = build_contract("pre-maintenance")
        values = passing_metrics(contract)
        by_id = {check.check_id: check for check in contract.checks}
        values["MYSQL_MAJOR"] = 10
        values["MYSQL_IS_MARIADB"] = 1
        self.assertTrue(check_passes(by_id["MYSQL_MAJOR"], values))
        self.assertFalse(check_passes(by_id["MYSQL_IS_MARIADB"], values))
        values = passing_metrics(contract)
        values["SESSION_REPEATABLE_READ"] = 0
        self.assertFalse(check_passes(by_id["SESSION_REPEATABLE_READ"], values))


class DefaultsFileTests(unittest.TestCase):
    def test_defaults_file_must_be_absolute_regular_owned_0600_not_symlink(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            good = secure_file(root / "client.cnf")
            self.assertEqual(good.resolve(), validate_defaults_file(str(good)))

            with self.assertRaisesRegex(EvidenceError, "absolute"):
                validate_defaults_file("client.cnf")

            weak = secure_file(root / "weak.cnf")
            weak.chmod(0o644)
            with self.assertRaisesRegex(EvidenceError, "0600"):
                validate_defaults_file(str(weak))

            link = root / "linked.cnf"
            link.symlink_to(good)
            with self.assertRaisesRegex(EvidenceError, "symlink"):
                validate_defaults_file(str(link))

    def test_defaults_file_rejects_execution_includes_and_ambiguous_groups(self):
        scenarios = {
            "execute": "[client]\nexecute=SELECT 1\n",
            "init-command": "[client]\ninit_command=DELETE FROM sys_config\n",
            "pager": "[client]\npager=/tmp/capture\n",
            "include": "[client]\n!include /tmp/other.cnf\n",
            "mysql-group": "[client]\nuser=readonly\n[mysql]\nexecute=SELECT 1\n",
            "duplicate": "[client]\nhost=db.example\nhost=other.example\n",
        }
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for scenario, source in scenarios.items():
                with self.subTest(scenario=scenario):
                    path = secure_file(root / f"{scenario}.cnf", source)
                    with self.assertRaises(EvidenceError):
                        validate_defaults_file(str(path))

            safe = secure_file(
                root / "safe.cnf",
                "[client]\nhost=db.example\nport=3306\nuser=readonly\n"
                "password=opaque-value\nssl-mode=VERIFY_IDENTITY\n"
                "ssl-ca=/secure/ca.pem\n",
            )
            self.assertEqual(safe.resolve(), validate_defaults_file(str(safe)))

    def test_existing_output_requires_explicit_overwrite(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = secure_file(Path(temporary) / "evidence.json", "{}\n")
            with self.assertRaisesRegex(EvidenceError, "--overwrite"):
                prepare_output_destination(path, overwrite=False)
            prepare_output_destination(path, overwrite=True)
            self.assertTrue(path.exists())

    def test_evidence_pair_refuses_late_output_or_active_lock(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "evidence.json"
            write_evidence(path, {"value": "old"})
            companion = Path(str(path) + ".sha256")
            original = (path.read_bytes(), companion.read_bytes())

            with self.assertRaisesRegex(EvidenceError, "--overwrite"):
                write_evidence(path, {"value": "new"})
            self.assertEqual(original, (path.read_bytes(), companion.read_bytes()))

            lock = secure_file(Path(str(path) + ".lock"), "")
            with self.assertRaisesRegex(EvidenceError, "locked"):
                write_evidence(path, {"value": "new"}, overwrite=True)
            self.assertEqual(original, (path.read_bytes(), companion.read_bytes()))
            lock.unlink()

    def test_overwrite_rolls_back_if_checksum_publish_fails(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "evidence.json"
            companion = Path(str(path) + ".sha256")
            write_evidence(path, {"value": "old"})
            original = (path.read_bytes(), companion.read_bytes())
            real_replace = os.replace
            failed = False

            def fail_checksum_once(source, destination):
                nonlocal failed
                if (
                    not failed
                    and Path(destination) == companion
                    and Path(source).suffix == ".tmp"
                ):
                    failed = True
                    raise OSError("injected checksum publish failure")
                return real_replace(source, destination)

            with mock.patch(
                "stage0_db_evidence.os.replace", side_effect=fail_checksum_once
            ):
                with self.assertRaisesRegex(OSError, "injected"):
                    write_evidence(path, {"value": "new"}, overwrite=True)
            self.assertEqual(original, (path.read_bytes(), companion.read_bytes()))
            self.assertFalse(Path(str(path) + ".lock").exists())
            self.assertFalse(list(root.glob("*.tmp")))
            self.assertFalse(list(root.glob(".*.backup")))

    def test_concurrent_no_overwrite_has_one_winner_and_consistent_pair(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "evidence.json"
            barrier = threading.Barrier(2)
            outcomes = []

            def publish(value):
                barrier.wait()
                try:
                    write_evidence(path, {"value": value})
                    outcomes.append(("passed", value))
                except (EvidenceError, OSError) as exc:
                    outcomes.append(("blocked", type(exc).__name__))

            threads = [
                threading.Thread(target=publish, args=(value,))
                for value in ("first", "second")
            ]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()

            self.assertEqual(1, sum(status == "passed" for status, _ in outcomes))
            self.assertEqual(1, sum(status == "blocked" for status, _ in outcomes))
            rendered = path.read_bytes()
            checksum = Path(str(path) + ".sha256").read_text(encoding="ascii")
            self.assertTrue(checksum.startswith(sha256_bytes(rendered)))

    def test_dirty_worktree_is_rejected(self):
        fake = mock.Mock(returncode=0, stdout=" M tracked.txt\n")
        with mock.patch("stage0_db_evidence.subprocess.run", return_value=fake):
            with self.assertRaisesRegex(EvidenceError, "clean Git worktree"):
                require_clean_worktree(ROOT)


class FakeMysqlTests(unittest.TestCase):
    def test_fake_mysql_receives_fixed_sql_without_password_cli_or_env(self):
        contract = build_contract("pre-maintenance")
        metrics = passing_metrics(contract, health_present=False)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            defaults = secure_file(root / "client.cnf", "[client]\npassword=fake\n")
            output_file = secure_file(
                root / "mysql-output.tsv", mysql_output(contract, metrics)
            )
            capture = root / "capture.json"
            fake = root / "fake-mysql"
            fake.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env python3
                    import json
                    import os
                    import sys
                    from pathlib import Path

                    sql = sys.stdin.read()
                    Path(os.environ["FAKE_MYSQL_CAPTURE"]).write_text(
                        json.dumps({
                            "argv": sys.argv[1:],
                            "sql": sql,
                            "mysqlPwdPresent": "MYSQL_PWD" in os.environ,
                            "mysqlLoginFile": os.environ.get("MYSQL_TEST_LOGIN_FILE"),
                            "passwordEnvPresent": any(
                                "PASSWORD" in key.upper() or "PASSWD" in key.upper()
                                for key in os.environ
                            ),
                        }),
                        encoding="utf-8",
                    )
                    sys.stdout.write(
                        Path(os.environ["FAKE_MYSQL_OUTPUT"]).read_text(encoding="utf-8")
                    )
                    """
                ),
                encoding="utf-8",
            )
            fake.chmod(0o700)
            environment = {
                "FAKE_MYSQL_CAPTURE": str(capture),
                "FAKE_MYSQL_OUTPUT": str(output_file),
                "MYSQL_PWD": "must-not-reach-child",
                "MYSQL_TEST_LOGIN_FILE": "/tmp/attacker-login.cnf",
                "ERP_TEST_PASSWORD": "must-not-reach-child",
            }
            with mock.patch.dict(os.environ, environment, clear=False):
                raw = execute_mysql(
                    mysql_bin=str(fake),
                    defaults_file=defaults,
                    database="BossERP_NEW",
                    sql=render_mysql_sql(contract),
                    host="127.0.0.1",
                    port=3306,
                    user="evidence_reader",
                )
            version, actual = parse_mysql_output(raw, contract)
            self.assertEqual("8.0.36", version)
            self.assertEqual(metrics, actual)
            captured = json.loads(capture.read_text(encoding="utf-8"))
            self.assertFalse(captured["mysqlPwdPresent"])
            self.assertFalse(captured["passwordEnvPresent"])
            self.assertEqual("/dev/null", captured["mysqlLoginFile"])
            self.assertFalse(
                any(argument.startswith("--password") for argument in captured["argv"])
            )
            self.assertEqual(
                f"--defaults-file={defaults.resolve()}", captured["argv"][0]
            )
            self.assertIn(
                "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ",
                captured["sql"],
            )
            self.assertIn("SET SESSION TRANSACTION READ ONLY", captured["sql"])
            self.assertIn("START TRANSACTION WITH CONSISTENT SNAPSHOT", captured["sql"])


class OfflineVerificationTests(unittest.TestCase):
    def make_evidence(
        self,
        directory: Path,
        *,
        profile: str = "release-ready",
        collected_at: datetime,
        commit: str,
        overrides=None,
    ):
        contract = build_contract(profile)
        metrics = passing_metrics(contract)
        metrics.update(overrides or {})
        evidence = build_evidence(
            contract=contract,
            database="BossERP_NEW",
            target_label=TARGET_LABEL,
            server_version="8.0.36",
            metrics=metrics,
            candidate_commit=commit,
            collected_at=collected_at,
            run_id="12345678-1234-5678-9234-567812345678",
        )
        path = directory / "evidence.json"
        write_evidence(
            path,
            evidence,
            overwrite=path.exists() or Path(str(path) + ".sha256").exists(),
        )
        return path, evidence

    def test_valid_evidence_recomputes_and_files_are_0600(self):
        now = datetime.now(timezone.utc)
        commit = git_commit(ROOT)
        with tempfile.TemporaryDirectory() as temporary:
            path, original = self.make_evidence(
                Path(temporary), collected_at=now, commit=commit
            )
            verified = verify_evidence(
                path,
                root=ROOT,
                expected_commit=commit,
                expected_profile="release-ready",
                expected_database="BossERP_NEW",
                expected_target_label=TARGET_LABEL,
                now=now,
            )
            self.assertEqual(EVIDENCE_KIND, verified["kind"])
            self.assertEqual("passed", verified["status"])
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            self.assertEqual(
                0o600, stat.S_IMODE(Path(str(path) + ".sha256").stat().st_mode)
            )
            rendered = json.dumps(original, ensure_ascii=False)
            self.assertNotIn("password", rendered.lower())
            self.assertNotIn("employee_id", rendered.lower())
            self.assertNotIn("user_name", rendered.lower())

    def test_non_ascii_checksum_is_cli_validation_error_not_blocked_evidence(self):
        now = datetime.now(timezone.utc)
        commit = git_commit(ROOT)
        with tempfile.TemporaryDirectory() as temporary:
            path, _ = self.make_evidence(
                Path(temporary), collected_at=now, commit=commit
            )
            Path(str(path) + ".sha256").write_bytes(b"\xff\xfe\n")
            stderr = io.StringIO()
            with mock.patch("sys.stderr", new=stderr):
                code = main(
                    [
                        "verify",
                        "--evidence",
                        str(path),
                        "--profile",
                        "release-ready",
                        "--database",
                        "BossERP_NEW",
                        "--target-label",
                        TARGET_LABEL,
                    ]
                )
            self.assertEqual(2, code)
            self.assertIn("[FAIL]", stderr.getvalue())
            self.assertNotIn("Traceback", stderr.getvalue())

    def test_expired_ready_and_future_premaintenance_are_rejected(self):
        now = datetime.now(timezone.utc)
        commit = git_commit(ROOT)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path, _ = self.make_evidence(
                root,
                collected_at=now - timedelta(hours=2, seconds=1),
                commit=commit,
            )
            with self.assertRaisesRegex(EvidenceError, "expired"):
                verify_evidence(
                    path,
                    root=ROOT,
                    expected_commit=commit,
                    expected_target_label=TARGET_LABEL,
                    now=now,
                )

            path, _ = self.make_evidence(
                root,
                profile="pre-maintenance",
                collected_at=now + timedelta(seconds=1),
                commit=commit,
            )
            with self.assertRaisesRegex(EvidenceError, "future"):
                verify_evidence(
                    path,
                    root=ROOT,
                    expected_commit=commit,
                    expected_target_label=TARGET_LABEL,
                    now=now,
                )

    def test_explicit_profile_and_database_must_match(self):
        now = datetime.now(timezone.utc)
        commit = git_commit(ROOT)
        with tempfile.TemporaryDirectory() as temporary:
            path, _ = self.make_evidence(
                Path(temporary), collected_at=now, commit=commit
            )
            with self.assertRaisesRegex(EvidenceError, "profile"):
                verify_evidence(
                    path,
                    root=ROOT,
                    expected_commit=commit,
                    expected_profile="pre-maintenance",
                    expected_database="BossERP_NEW",
                    expected_target_label=TARGET_LABEL,
                    now=now,
                )
            with self.assertRaisesRegex(EvidenceError, "database"):
                verify_evidence(
                    path,
                    root=ROOT,
                    expected_commit=commit,
                    expected_profile="release-ready",
                    expected_database="AnotherERP",
                    expected_target_label=TARGET_LABEL,
                    now=now,
                )

    def test_target_label_is_required_to_match_and_is_fingerprint_bound(self):
        now = datetime.now(timezone.utc)
        commit = git_commit(ROOT)
        with self.assertRaisesRegex(EvidenceError, "target label"):
            validate_target_label("prod primary")
        with tempfile.TemporaryDirectory() as temporary:
            path, original = self.make_evidence(
                Path(temporary), collected_at=now, commit=commit
            )
            with self.assertRaisesRegex(EvidenceError, "target label"):
                verify_evidence(
                    path,
                    root=ROOT,
                    expected_commit=commit,
                    expected_target_label="staging-cn-primary",
                    now=now,
                )

            tampered = copy.deepcopy(original)
            tampered["target"]["targetLabel"] = "staging-cn-primary"
            write_evidence(path, tampered, overwrite=True)
            with self.assertRaisesRegex(EvidenceError, "schemaFingerprintSha256"):
                verify_evidence(
                    path,
                    root=ROOT,
                    expected_commit=commit,
                    expected_target_label="staging-cn-primary",
                    now=now,
                )

    def test_commit_tampering_is_rejected_even_with_recomputed_file_hash(self):
        now = datetime.now(timezone.utc)
        commit = git_commit(ROOT)
        with tempfile.TemporaryDirectory() as temporary:
            path, original = self.make_evidence(
                Path(temporary), collected_at=now, commit=commit
            )
            tampered = copy.deepcopy(original)
            tampered["candidateCommit"] = "0" * 40
            write_evidence(path, tampered, overwrite=True)
            with self.assertRaisesRegex(EvidenceError, "candidateCommit"):
                verify_evidence(
                    path,
                    root=ROOT,
                    expected_commit=commit,
                    expected_target_label=TARGET_LABEL,
                    now=now,
                )

    def test_query_status_and_secret_field_tampering_are_rejected(self):
        now = datetime.now(timezone.utc)
        commit = git_commit(ROOT)
        with tempfile.TemporaryDirectory() as temporary:
            path, original = self.make_evidence(
                Path(temporary), collected_at=now, commit=commit
            )
            tampered = copy.deepcopy(original)
            tampered["checks"][0]["status"] = "blocked"
            write_evidence(path, tampered, overwrite=True)
            with self.assertRaisesRegex(EvidenceError, "altered"):
                verify_evidence(
                    path,
                    root=ROOT,
                    expected_commit=commit,
                    expected_target_label=TARGET_LABEL,
                    now=now,
                )

            tampered = copy.deepcopy(original)
            tampered["password"] = "forbidden"
            write_evidence(path, tampered, overwrite=True)
            with self.assertRaisesRegex(EvidenceError, "credential"):
                verify_evidence(
                    path,
                    root=ROOT,
                    expected_commit=commit,
                    expected_target_label=TARGET_LABEL,
                    now=now,
                )

    def test_blocked_oa_and_flag_metrics_are_preserved_but_not_passed(self):
        now = datetime.now(timezone.utc)
        commit = git_commit(ROOT)
        with tempfile.TemporaryDirectory() as temporary:
            path, original = self.make_evidence(
                Path(temporary),
                collected_at=now,
                commit=commit,
                overrides={"OA_LEGACY_PENDING": 2, "INVENTORY_SAFE_FLAGS": 1},
            )
            self.assertEqual("blocked", original["status"])
            verified = verify_evidence(
                path,
                root=ROOT,
                expected_commit=commit,
                expected_target_label=TARGET_LABEL,
                now=now,
            )
            self.assertEqual("blocked", verified["status"])


if __name__ == "__main__":
    unittest.main()
