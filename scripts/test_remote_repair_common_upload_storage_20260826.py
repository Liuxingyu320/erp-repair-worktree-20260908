#!/usr/bin/env python3
"""Isolated regression tests for the one-time common-upload repair script.

The harness replaces every external mutating integration with local stubs.  It
never connects to MySQL, systemd, HTTP, or production paths.
"""

from __future__ import annotations

import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import tempfile
import textwrap
import unittest
import urllib.parse


SCRIPT = pathlib.Path(__file__).with_name(
    "remote_repair_common_upload_storage_20260826.sh"
).resolve()
APPLY_TOKEN = "APPLY_COMMON_UPLOAD_STORAGE_20260826"
ROLLBACK_TOKEN = "ROLLBACK_COMMON_UPLOAD_STORAGE_20260826"


def write_executable(path: pathlib.Path, body: str) -> None:
    path.write_text(textwrap.dedent(body).lstrip(), encoding="utf-8")
    path.chmod(0o755)


class RepairFixture:
    public_bytes = b"public-contract-evidence\n"
    template_bytes = b"template-document-bytes\n"

    mandatory_tables = (
        "oa_sign_task",
        "oa_sign_package",
        "oa_sign_package_document",
        "oa_sign_file_evidence",
        "oa_sign_template",
        "oa_sign_plan",
        "oa_sign_plan_template",
        "oa_sign_plan_version",
        "oa_sign_plan_version_template",
        "oa_sign_final_confirmation",
        "oa_sign_final_confirmation_document",
        "oa_sign_event",
        "oa_sign_task_event",
        "oa_company_seal_config",
        "oa_sign_onboard_import_batch",
        "oa_sign_onboard_import_row",
        "oa_sign_file_cleanup",
        "oa_sign_notification_outbox",
        "oa_sign_task_hard_delete_operation",
    )

    def __init__(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="common-upload-repair-test-")
        self.root = pathlib.Path(self.temp.name).resolve()
        self.target = self.root / "target" / "uploadPath"
        self.release = self.root / "releases" / "r1"
        self.current = self.root / "current"
        self.backups = self.root / "backups"
        self.systemd = self.root / "systemd"
        self.lock = self.root / "run" / "repair.lock"
        self.candidate = self.root / "candidate" / "uploadPath"
        self.legacy_compat = self.root / "legacy" / "uploadPath"
        self.fakebin = self.root / "fakebin"
        self.command_log = self.root / "commands.log"
        self.fail_once_marker = self.root / "fail-once.marker"

        (self.target / "private" / "sign-package").mkdir(parents=True)
        (self.target / "private" / "sign-package" / "protected.bin").write_bytes(
            b"protected-sign-package-content\n"
        )
        (self.candidate / "public").mkdir(parents=True)
        (self.candidate / "sign-template").mkdir(parents=True)
        (self.candidate / "public" / "document.bin").write_bytes(self.public_bytes)
        (self.candidate / "sign-template" / "template.docx").write_bytes(
            self.template_bytes
        )
        (self.release / "erp").mkdir(parents=True)
        self.backups.mkdir()
        self.systemd.mkdir()
        self.lock.parent.mkdir()
        self.legacy_compat.parent.mkdir()
        self.current.symlink_to(self.release)
        (self.release / "uploadPath").symlink_to(self.candidate)
        self.raw_erp_link = "../../../candidate/uploadPath"
        (self.release / "erp" / "uploadPath").symlink_to(self.raw_erp_link)
        self.original_env = textwrap.dedent(
            f"""
            MYSQL_USERNAME=test_user
            MYSQL_PASSWORD=test_password
            MYSQL_DATABASE=test_db
            ERP_UPLOAD_ROOT={self.candidate}
            FILE_PATH={self.candidate}
            SIGN_PACKAGE_STORAGE_ROOT={self.target}/private/sign-package
            OA_SIGN_EXCEL_IMPORT_ENABLED=true
            VUE_APP_SIGN_EXCEL_IMPORT_ENABLED=true
            """
        ).lstrip()
        (self.release / ".env").write_text(self.original_env, encoding="utf-8")
        self.fakebin.mkdir()
        self._write_stubs()

        self.env = os.environ.copy()
        self.env.update(
            {
                "PATH": str(self.fakebin) + os.pathsep + self.env.get("PATH", ""),
                "ERP_COMMON_UPLOAD_REPAIR_TEST_MODE": "1",
                "ERP_COMMON_UPLOAD_REPAIR_TARGET_ROOT": str(self.target),
                "ERP_COMMON_UPLOAD_REPAIR_CURRENT_LINK": str(self.current),
                "ERP_COMMON_UPLOAD_REPAIR_BACKUP_BASE": str(self.backups),
                "ERP_COMMON_UPLOAD_REPAIR_SYSTEMD_ROOT": str(self.systemd),
                "ERP_COMMON_UPLOAD_REPAIR_LOCK_FILE": str(self.lock),
                "ERP_COMMON_UPLOAD_REPAIR_LEGACY_ROOT": str(self.legacy_compat),
                "ERP_COMMON_UPLOAD_REPAIR_PINNED_SEAL_SOURCE": str(
                    self.candidate / "public" / "unused-pinned.png"
                ),
                "ERP_COMMON_UPLOAD_REPAIR_FILE_HEALTH_URL": "http://fake/file-health",
                "ERP_COMMON_UPLOAD_REPAIR_OA_HEALTH_URL": "http://fake/oa-health",
                "ERP_COMMON_UPLOAD_REPAIR_FILE_PUBLIC_BASE_URL": "http://fake/file/public",
                "ERP_COMMON_UPLOAD_REPAIR_OA_BUSINESS_CHECK_URL": "http://fake/oa/capabilities",
                "ERP_COMMON_UPLOAD_REPAIR_OA_TEMPLATE_BASE_URL": "http://fake/oa/template",
                "ERP_REPAIR_OA_BEARER_TOKEN": "test-token.value",
                "ERP_REPAIR_DB_USER": "test_user",
                "ERP_REPAIR_DB_PASSWORD": "test_password",
                "ERP_REPAIR_DB_NAME": "test_db",
                "FAKE_TARGET_ROOT": str(self.target),
                "FAKE_PUBLIC_HASH": hashlib.sha256(self.public_bytes).hexdigest(),
                "FAKE_PUBLIC_SIZE": str(len(self.public_bytes)),
                "FAKE_TEMPLATE_HASH": hashlib.sha256(self.template_bytes).hexdigest(),
                "FAKE_TEMPLATE_SIZE": str(len(self.template_bytes)),
                "FAKE_COMMAND_LOG": str(self.command_log),
                "FAKE_FAIL_ONCE_MARKER": str(self.fail_once_marker),
                "FAKE_MANDATORY_TABLES": ",".join(self.mandatory_tables),
                "FAKE_CURRENT_LINK": str(self.current),
                "FAKE_ACTIVE_ENV": str(self.release / ".env"),
            }
        )

    def close(self) -> None:
        self.temp.cleanup()

    def _write_stubs(self) -> None:
        write_executable(
            self.fakebin / "mysql",
            r'''
            #!/usr/bin/env python3
            import os, sys
            if (
                os.environ.get("FAKE_ASSERT_NO_BEARER_ENV") == "1"
                and "ERP_REPAIR_OA_BEARER_TOKEN" in os.environ
            ):
                print("bearer token leaked to mysql child", file=sys.stderr)
                raise SystemExit(97)
            args = sys.argv[1:]
            sql = ""
            if "--execute" in args:
                sql = args[args.index("--execute") + 1]
            else:
                sql = sys.stdin.read()
            if "information_schema.COLUMNS" in sql and "COLUMN_NAME='status'" in sql:
                for table in (
                    "oa_sign_onboard_import_batch", "oa_sign_onboard_import_row",
                    "oa_sign_file_cleanup", "oa_sign_notification_outbox",
                    "oa_sign_task_hard_delete_operation",
                ):
                    print(f"{table}\tstatus")
            elif "information_schema.COLUMNS" in sql:
                for table, columns in {
                    "oa_sign_template": ("template_id", "file_url", "file_size", "file_hash"),
                    "oa_sign_file_evidence": ("evidence_id", "file_url", "file_size", "file_hash"),
                    "oa_labor_contract_template": ("template_id", "template_file_url"),
                }.items():
                    for column in columns:
                        print(f"{table}\t{column}")
            elif "information_schema.TABLES" in sql:
                for table in os.environ["FAKE_MANDATORY_TABLES"].split(","):
                    print(table)
            elif "oa_sign_onboard_import_batch.GENERATING" in sql:
                busy = int(os.environ.get("FAKE_INFLIGHT", "0"))
                print(f"oa_sign_onboard_import_batch.GENERATING\t{busy}")
                print("oa_sign_onboard_import_row.GENERATING\t0")
                print("oa_sign_file_cleanup.PROCESSING\t0")
                print("oa_sign_notification_outbox.PROCESSING_OR_SENDING\t0")
                print("oa_sign_task_hard_delete_operation.PROCESSING\t0")
            elif "oa_sign_template" in sql and "UNION ALL" in sql:
                def hx(value): return value.encode().hex().upper()
                print("\t".join((
                    "oa_sign_template", "7", hx("/profile/sign-template/template.docx"),
                    os.environ["FAKE_TEMPLATE_SIZE"], os.environ["FAKE_TEMPLATE_HASH"],
                )))
                print("\t".join((
                    "oa_sign_file_evidence", "9", hx("/profile/public/document.bin"),
                    os.environ["FAKE_PUBLIC_SIZE"], os.environ["FAKE_PUBLIC_HASH"],
                )))
                mode = os.environ.get("FAKE_DB_REFERENCE_MODE", "normal")
                if mode == "known_legacy":
                    print("\t".join((
                        "oa_labor_contract_template", "1",
                        hx("http://localhost:8080/file/public/2026/06/14/杭州劳动合同有社保版名田_20260614142819A005.docx"),
                        "", "",
                    )))
                    print("\t".join((
                        "oa_labor_contract_template", "2",
                        hx("http://localhost:8080/file/public/2026/06/14/杭州劳动合同(无社保版)名田_20260614142810A004.docx"),
                        "", "",
                    )))
                elif mode == "unknown_unhashed":
                    print("\t".join((
                        "oa_labor_contract_template", "99",
                        hx("http://localhost:8080/file/public/2026/06/14/unknown-unhashed.docx"),
                        "", "",
                    )))
            else:
                print("unhandled fake mysql query", file=sys.stderr)
                print(sql, file=sys.stderr)
                raise SystemExit(97)
            ''',
        )
        write_executable(
            self.fakebin / "mysqldump",
            r'''
            #!/usr/bin/env python3
            import os, sys
            if "--version" in sys.argv:
                if os.environ.get("FAKE_MARIADB") == "1":
                    print("mysqldump  Ver 10.19 Distrib 10.11.14-MariaDB")
                else:
                    print("mysqldump  Ver 8.0.40 for Linux on x86_64 (MySQL Community)")
            elif "--help" in sys.argv:
                if os.environ.get("FAKE_MARIADB") != "1":
                    print("--no-tablespaces")
                    print("--set-gtid-purged")
            else:
                print("-- isolated fake consistent signing database dump")
                print("START TRANSACTION;")
                print("COMMIT;")
            ''',
        )
        write_executable(
            self.fakebin / "systemctl",
            r'''
            #!/usr/bin/env python3
            import os, pathlib, sys
            args = sys.argv[1:]
            log = pathlib.Path(os.environ["FAKE_COMMAND_LOG"])
            with log.open("a", encoding="utf-8") as handle:
                handle.write("systemctl " + " ".join(args) + "\n")
            if args[:2] == ["is-active", "--quiet"]:
                raise SystemExit(0)
            if args and args[0] == "cat":
                print("[Service]")
                print("ExecStart=/bin/true")
                raise SystemExit(0)
            if args and args[0] == "show":
                if "MainPID" in args:
                    print("0")
                else:
                    print("FragmentPath=/fake/unit")
                    print("DropInPaths=")
                raise SystemExit(0)
            if args and args[0] in ("stop", "start", "daemon-reload"):
                fail = os.environ.get("FAKE_SYSTEMCTL_FAIL_ONCE", "")
                marker = pathlib.Path(os.environ["FAKE_FAIL_ONCE_MARKER"])
                signature = ":".join(args[:2])
                if fail == signature and not marker.exists():
                    marker.write_text("failed\n", encoding="utf-8")
                    raise SystemExit(int(os.environ.get("FAKE_SYSTEMCTL_FAILURE_RC", "23")))
                drift = os.environ.get("FAKE_RELEASE_DRIFT_ON", "")
                if drift == signature and not marker.exists():
                    marker.write_text("release-drifted\n", encoding="utf-8")
                    current = pathlib.Path(os.environ["FAKE_CURRENT_LINK"])
                    alternate = pathlib.Path(os.environ["FAKE_ALTERNATE_RELEASE"])
                    current.unlink()
                    current.symlink_to(alternate)
                if (
                    args[0] == "stop"
                    and os.environ.get("FAKE_CONFIG_DRIFT_ON_STOP") == "1"
                    and not marker.exists()
                ):
                    marker.write_text("config-drifted-during-stop\n", encoding="utf-8")
                    pathlib.Path(os.environ["FAKE_ACTIVE_ENV"]).write_text(
                        "drifted_during_rollback_stop=true\n", encoding="utf-8"
                    )
                raise SystemExit(0)
            raise SystemExit(0)
            ''',
        )
        write_executable(
            self.fakebin / "curl",
            r'''
            #!/usr/bin/env python3
            import json, os, pathlib, shutil, sys, urllib.parse
            args = sys.argv[1:]
            sys.stdin.read()
            output = pathlib.Path(args[args.index("-o") + 1])
            url = next((item for item in reversed(args) if item.startswith("http")), "")
            with pathlib.Path(os.environ["FAKE_COMMAND_LOG"]).open("a", encoding="utf-8") as handle:
                handle.write("curl " + url + "\n")
            target = pathlib.Path(os.environ["FAKE_TARGET_ROOT"])
            content_type = "application/json"
            if url.endswith("file-health") or url.endswith("oa-health"):
                body = b'{"status":"UP"}\n'
            elif "/file/public/" in url:
                if os.environ.get("FAKE_HTTP_JSON") == "1":
                    body = b'{"code":500,"msg":"fake JSON error"}\n'
                else:
                    relative = urllib.parse.unquote(url.split("/file/public/", 1)[1])
                    body = (target / "public" / relative).read_bytes()
                    content_type = "application/octet-stream"
            elif url.endswith("/oa/capabilities"):
                if os.environ.get("FAKE_CONFIG_DRIFT_THEN_FAIL") == "1":
                    pathlib.Path(os.environ["FAKE_ACTIVE_ENV"]).write_text(
                        "concurrent_operator_change=true\n", encoding="utf-8"
                    )
                    raise SystemExit(45)
                if os.environ.get("FAKE_CONFIG_DRIFT_BEFORE_COMPLETE") == "1":
                    pathlib.Path(os.environ["FAKE_ACTIVE_ENV"]).write_text(
                        "drifted_before_apply_complete=true\n", encoding="utf-8"
                    )
                if os.environ.get("FAKE_WRITE_THEN_FAIL") == "1":
                    (target / "public" / "new-service-write.bin").write_bytes(b"new write")
                    raise SystemExit(44)
                body = json.dumps({
                    "code": 200,
                    "data": {"coreEnabled": True, "excelImportEnabled": True},
                }).encode() + b"\n"
            elif "/oa/template/7/file" in url:
                body = (target / "sign-template" / "template.docx").read_bytes()
                content_type = "application/octet-stream"
            else:
                print(f"unhandled fake curl URL: {url}", file=sys.stderr)
                raise SystemExit(96)
            output.write_bytes(body)
            print(f"200\t{content_type}")
            ''',
        )
        write_executable(
            self.fakebin / "mv",
            r'''
            #!/usr/bin/env python3
            import os, subprocess, sys
            args = sys.argv[1:]
            if "--help" in args:
                print("  -T, --no-target-directory")
                raise SystemExit(0)
            atomic = any("T" in item for item in args if item.startswith("-") and item != "--")
            cleaned = []
            for item in args:
                if item == "--":
                    continue
                if item.startswith("-") and "T" in item:
                    flags = "-" + item[1:].replace("T", "")
                    if flags != "-":
                        cleaned.append(flags)
                    continue
                cleaned.append(item)
            if atomic:
                operands = [item for item in cleaned if not item.startswith("-")]
                if len(operands) != 2:
                    raise SystemExit("fake mv expected two operands")
                os.replace(operands[0], operands[1])
                raise SystemExit(0)
            raise SystemExit(subprocess.run(["/bin/mv", *cleaned]).returncode)
            ''',
        )
        write_executable(
            self.fakebin / "cp",
            r'''
            #!/usr/bin/env python3
            import subprocess, sys
            args = [item for item in sys.argv[1:] if item != "--reflink=auto" and item != "--"]
            raise SystemExit(subprocess.run(["/bin/cp", *args]).returncode)
            ''',
        )
        write_executable(
            self.fakebin / "readlink",
            r'''
            #!/usr/bin/env python3
            import os, pathlib, sys
            args = sys.argv[1:]
            if args and args[0] == "-f":
                print(pathlib.Path(args[1]).resolve())
            else:
                print(os.readlink(args[-1]))
            ''',
        )
        write_executable(
            self.fakebin / "stat",
            r'''
            #!/usr/bin/env python3
            import os, subprocess, sys
            args = sys.argv[1:]
            if len(args) >= 3 and args[0] == "-c":
                value = os.lstat(args[-1])
                if args[1] == "%s": print(value.st_size)
                elif args[1] == "%d:%i": print(f"{value.st_dev}:{value.st_ino}")
                else: raise SystemExit(f"unsupported fake stat format {args[1]}")
            else:
                raise SystemExit(subprocess.run(["/usr/bin/stat", *args]).returncode)
            ''',
        )
        write_executable(
            self.fakebin / "sha256sum",
            r'''
            #!/usr/bin/env python3
            import hashlib, pathlib, sys
            args = sys.argv[1:]
            if args and args[0] == "-c":
                ok = True
                for raw in pathlib.Path(args[1]).read_text(encoding="utf-8").splitlines():
                    digest, name = raw.split(None, 1)
                    name = name.lstrip(" *")
                    actual = hashlib.sha256(pathlib.Path(name).read_bytes()).hexdigest()
                    passed = actual == digest
                    print(f"{name}: {'OK' if passed else 'FAILED'}")
                    ok = ok and passed
                raise SystemExit(0 if ok else 1)
            if not args:
                data = sys.stdin.buffer.read()
                print(hashlib.sha256(data).hexdigest() + "  -")
            else:
                for name in args:
                    data = pathlib.Path(name).read_bytes()
                    print(hashlib.sha256(data).hexdigest() + "  " + name)
            ''',
        )
        write_executable(
            self.fakebin / "date",
            r'''
            #!/usr/bin/env python3
            import datetime, subprocess, sys
            if "--iso-8601=seconds" in sys.argv:
                print("2026-08-27T12:00:00+08:00")
            elif any(item.startswith("+%Y%m%dT%H%M%S") for item in sys.argv):
                print("20260827T120000")
            else:
                raise SystemExit(subprocess.run(["/bin/date", *sys.argv[1:]]).returncode)
            ''',
        )
        write_executable(
            self.fakebin / "chmod",
            r'''
            #!/usr/bin/env python3
            import os, stat, subprocess, sys
            args = sys.argv[1:]
            reference = next((item.split("=", 1)[1] for item in args if item.startswith("--reference=")), None)
            if reference is not None:
                target = args[-1]
                os.chmod(target, stat.S_IMODE(os.stat(reference).st_mode))
            else:
                raise SystemExit(subprocess.run(["/bin/chmod", *args]).returncode)
            ''',
        )
        write_executable(
            self.fakebin / "chown",
            r'''
            #!/usr/bin/env python3
            import subprocess, sys
            if any(item.startswith("--reference=") for item in sys.argv[1:]):
                raise SystemExit(0)
            raise SystemExit(subprocess.run(["/usr/sbin/chown", *sys.argv[1:]]).returncode)
            ''',
        )
        write_executable(
            self.fakebin / "sync",
            """
            #!/bin/sh
            /bin/sync
            """,
        )
        write_executable(
            self.fakebin / "flock",
            """
            #!/bin/sh
            exit 0
            """,
        )
        write_executable(
            self.fakebin / "rmdir",
            r'''
            #!/usr/bin/env python3
            import os, sys
            for item in sys.argv[1:]:
                if item != "--":
                    os.rmdir(item)
            ''',
        )

    def run(self, *args: str, extra_env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
        environment = self.env.copy()
        if extra_env:
            environment.update(extra_env)
        return subprocess.run(
            ["/bin/bash", str(SCRIPT), *args],
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=90,
        )

    def apply(self, extra_env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
        return self.run("--apply", "--confirm", APPLY_TOKEN, extra_env=extra_env)

    def only_bundle(self) -> pathlib.Path:
        bundles = list(self.backups.glob("common-upload-storage-20260826-*"))
        if len(bundles) != 1:
            raise AssertionError(f"expected one bundle, got {bundles}")
        return bundles[0]

    def assert_original_configuration(self, testcase: unittest.TestCase) -> None:
        testcase.assertEqual((self.release / ".env").read_text(encoding="utf-8"), self.original_env)
        testcase.assertTrue((self.release / "uploadPath").is_symlink())
        testcase.assertEqual(os.readlink(self.release / "uploadPath"), str(self.candidate))
        testcase.assertTrue((self.release / "erp" / "uploadPath").is_symlink())
        testcase.assertEqual(os.readlink(self.release / "erp" / "uploadPath"), self.raw_erp_link)
        testcase.assertFalse(os.path.lexists(self.legacy_compat))
        testcase.assertFalse((self.systemd / "erp-new@file.service.d").exists())
        testcase.assertFalse((self.systemd / "erp-new@oa.service.d").exists())
        testcase.assertEqual(
            (self.target / "private" / "sign-package" / "protected.bin").read_bytes(),
            b"protected-sign-package-content\n",
        )

    def assert_repair_artifacts_preserved(self, testcase: unittest.TestCase) -> None:
        for relative in (
            "public",
            "sign-template",
            "private/attendance",
            "private/reimbursement",
            "private/drive",
        ):
            testcase.assertTrue((self.target / relative).is_dir(), relative)
        testcase.assertEqual(
            (self.target / "public" / "document.bin").read_bytes(), self.public_bytes
        )
        testcase.assertEqual(
            (self.target / "sign-template" / "template.docx").read_bytes(),
            self.template_bytes,
        )


class RepairScriptTest(unittest.TestCase):
    def setUp(self) -> None:
        self.fx = RepairFixture()

    def tearDown(self) -> None:
        self.fx.close()

    def test_audit_is_read_only_for_systemd(self) -> None:
        report = self.fx.root / "audit"
        result = self.fx.run("--audit", "--output-dir", str(report))
        self.assertEqual(result.returncode, 0, result.stdout)
        log = self.fx.command_log.read_text(encoding="utf-8") if self.fx.command_log.exists() else ""
        for mutation in (" stop ", " start ", "daemon-reload"):
            self.assertNotIn(mutation, " " + log)
        self.assertTrue((report / "manifest.tsv").is_file())

    def test_wrong_token_and_mixed_modes_fail_before_mutation(self) -> None:
        bad = self.fx.run("--apply", "--confirm", "WRONG")
        self.assertEqual(bad.returncode, 1)
        mixed = self.fx.run("--audit", "--apply", "--confirm", APPLY_TOKEN)
        self.assertEqual(mixed.returncode, 1)
        self.assertFalse(any(self.fx.backups.iterdir()))

    def test_inflight_gate_fails_closed(self) -> None:
        result = self.fx.run(
            "--audit",
            "--output-dir",
            str(self.fx.root / "inflight-audit"),
            extra_env={"FAKE_INFLIGHT": "1"},
        )
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("in-flight signing work blocks", result.stdout)

    def test_success_then_manual_rollback_restores_config_without_deleting_target(self) -> None:
        applied = self.fx.apply()
        self.assertEqual(applied.returncode, 0, applied.stdout)
        bundle = self.fx.only_bundle()
        self.assertTrue((bundle / "APPLY_COMPLETE").is_file())
        self.assertTrue((self.fx.target / "private" / "attendance").is_dir())
        self.assertEqual(
            (self.fx.target / "public" / "document.bin").read_bytes(), self.fx.public_bytes
        )
        rolled_back = self.fx.run(
            "--rollback", str(bundle), "--confirm", ROLLBACK_TOKEN
        )
        self.assertEqual(rolled_back.returncode, 0, rolled_back.stdout)
        self.assertTrue((bundle / "ROLLED_BACK").is_file())
        self.fx.assert_original_configuration(self)
        self.fx.assert_repair_artifacts_preserved(self)

    def test_explicit_die_failpoints_each_trigger_one_exact_rollback(self) -> None:
        for failpoint in (
            "hash_verified_before_atomic_rename",
            "file_after_atomic_rename_before_applied_record",
            "env_after_update",
            "compat_after_switch",
            "runtime_after_services",
        ):
            with self.subTest(failpoint=failpoint):
                if failpoint != "hash_verified_before_atomic_rename":
                    self.fx.close()
                    self.fx = RepairFixture()
                result = self.fx.apply(
                    {"ERP_COMMON_UPLOAD_REPAIR_TEST_FAILPOINT": failpoint}
                )
                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertEqual(
                    result.stdout.count("attempting one controlled rollback"), 1, result.stdout
                )
                bundle = self.fx.only_bundle()
                self.assertTrue((bundle / "ROLLED_BACK").is_file(), result.stdout)
                self.fx.assert_original_configuration(self)

    def test_ordinary_command_failure_preserves_rc_and_rolls_back_once(self) -> None:
        result = self.fx.apply(
            {
                "FAKE_SYSTEMCTL_FAIL_ONCE": "start:erp-new@file.service",
                "FAKE_SYSTEMCTL_FAILURE_RC": "23",
            }
        )
        self.assertEqual(result.returncode, 23, result.stdout)
        self.assertEqual(result.stdout.count("attempting one controlled rollback"), 1)
        self.assertTrue((self.fx.only_bundle() / "ROLLED_BACK").is_file(), result.stdout)
        self.fx.assert_original_configuration(self)
        self.fx.assert_repair_artifacts_preserved(self)

    def test_http_200_json_fails_closed_and_rolls_back(self) -> None:
        result = self.fx.apply({"FAKE_HTTP_JSON": "1"})
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("expected binary file but received a JSON body", result.stdout)
        self.assertTrue((self.fx.only_bundle() / "ROLLED_BACK").is_file(), result.stdout)
        self.fx.assert_original_configuration(self)
        self.fx.assert_repair_artifacts_preserved(self)

    def test_service_write_after_start_preserves_unified_root_for_manual_intervention(self) -> None:
        result = self.fx.apply({"FAKE_WRITE_THEN_FAIL": "1"})
        self.assertEqual(result.returncode, 1, result.stdout)
        bundle = self.fx.only_bundle()
        self.assertTrue((bundle / "MANUAL_INTERVENTION").is_file(), result.stdout)
        self.assertFalse((bundle / "ROLLED_BACK").exists())
        self.assertTrue((self.fx.target / "public" / "new-service-write.bin").is_file())
        self.assertEqual((self.fx.release / "uploadPath").resolve(), self.fx.candidate)
        self.assertFalse((bundle / "scope.after-apply.fingerprint").exists())

    def test_tampered_pre_stop_backup_blocks_manual_rollback_before_stop(self) -> None:
        applied = self.fx.apply()
        self.assertEqual(applied.returncode, 0, applied.stdout)
        bundle = self.fx.only_bundle()
        (bundle / "config" / "env.before").write_text("tampered\n", encoding="utf-8")
        self.fx.command_log.write_text("", encoding="utf-8")
        result = self.fx.run("--rollback", str(bundle), "--confirm", ROLLBACK_TOKEN)
        self.assertEqual(result.returncode, 1, result.stdout)
        log = self.fx.command_log.read_text(encoding="utf-8")
        self.assertNotIn("systemctl stop", log)
        self.assertTrue((self.fx.target / "public" / "document.bin").is_file())

    def test_tampered_apply_journal_blocks_manual_rollback_before_stop(self) -> None:
        applied = self.fx.apply()
        self.assertEqual(applied.returncode, 0, applied.stdout)
        bundle = self.fx.only_bundle()
        journal = bundle / "file-intents.tsv"
        self.assertEqual(journal.stat().st_mode & 0o777, 0o400)
        journal.chmod(0o600)
        with journal.open("a", encoding="utf-8") as handle:
            handle.write("public/forged\t1\t" + "0" * 64 + "\tABSENT\n")
        self.fx.command_log.write_text("", encoding="utf-8")
        result = self.fx.run("--rollback", str(bundle), "--confirm", ROLLBACK_TOKEN)
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertTrue((bundle / "MANUAL_INTERVENTION").is_file())
        self.assertNotIn("systemctl stop", self.fx.command_log.read_text(encoding="utf-8"))
        self.assertTrue((self.fx.target / "public" / "document.bin").is_file())

    def test_identified_mariadb_client_uses_safe_gtid_option_omission(self) -> None:
        result = self.fx.apply({"FAKE_MARIADB": "1"})
        self.assertEqual(result.returncode, 0, result.stdout)
        bundle = self.fx.only_bundle()
        self.assertTrue((bundle / "database" / "signing-before.sql.gz.sha256").is_file())

    def test_known_legacy_unhashed_rows_are_residual_not_manifest(self) -> None:
        report = self.fx.root / "known-legacy-audit"
        result = self.fx.run(
            "--audit",
            "--output-dir",
            str(report),
            extra_env={"FAKE_DB_REFERENCE_MODE": "known_legacy"},
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        summary = json.loads((report / "summary.json").read_text(encoding="utf-8"))
        self.assertEqual(summary["known_legacy_unverified_residual"], 2)
        self.assertEqual(summary["blocking"], 0)
        manifest = (report / "manifest.tsv").read_text(encoding="utf-8")
        self.assertNotIn("杭州劳动合同", manifest)
        unresolved = (report / "unresolved.tsv").read_text(encoding="utf-8")
        self.assertEqual(unresolved.count("EXTERNAL_BACKUP_REQUIRED"), 2)

    def test_unknown_unhashed_reference_remains_blocking(self) -> None:
        report = self.fx.root / "unknown-unhashed-audit"
        result = self.fx.run(
            "--audit",
            "--output-dir",
            str(report),
            extra_env={"FAKE_DB_REFERENCE_MODE": "unknown_unhashed"},
        )
        self.assertEqual(result.returncode, 1, result.stdout)
        summary = json.loads((report / "summary.json").read_text(encoding="utf-8"))
        self.assertEqual(summary["known_legacy_unverified_residual"], 0)
        self.assertEqual(summary["blocking"], 1)
        self.assertIn(
            "UNVERIFIED_IN_SCOPE_NO_HASH",
            (report / "unresolved.tsv").read_text(encoding="utf-8"),
        )

    def test_apply_without_token_records_safari_deferred_postcheck(self) -> None:
        result = self.fx.apply({"ERP_REPAIR_OA_BEARER_TOKEN": ""})
        self.assertEqual(result.returncode, 0, result.stdout)
        bundle = self.fx.only_bundle()
        self.assertEqual(
            (bundle / "OA_BUSINESS_POSTCHECK").read_text(encoding="utf-8"),
            "DEFERRED_TO_SAFARI_SAME_ORIGIN\n",
        )
        self.assertIn("DEFERRED_TO_SAFARI_SAME_ORIGIN", result.stdout)
        log = self.fx.command_log.read_text(encoding="utf-8")
        self.assertNotIn("/oa/capabilities", log)
        self.assertNotIn("/oa/template/7/file", log)

    def test_apply_with_token_runs_authenticated_business_checks(self) -> None:
        result = self.fx.apply()
        self.assertEqual(result.returncode, 0, result.stdout)
        bundle = self.fx.only_bundle()
        self.assertEqual(
            (bundle / "OA_BUSINESS_POSTCHECK").read_text(encoding="utf-8"),
            "VERIFIED_WITH_TOKEN\n",
        )
        log = self.fx.command_log.read_text(encoding="utf-8")
        self.assertIn("curl http://fake/oa/capabilities", log)
        self.assertIn("curl http://fake/oa/template/7/file", log)

    def test_input_bearer_token_is_not_inherited_by_child_processes(self) -> None:
        result = self.fx.apply({"FAKE_ASSERT_NO_BEARER_ENV": "1"})
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertNotIn("bearer token leaked", result.stdout)

    def test_legacy_directory_contents_remain_reachable_and_unchanged(self) -> None:
        self.fx.legacy_compat.mkdir()
        files = {
            "public/unreferenced-template.docx": b"unreferenced template\n",
            "public/2026/06/14/杭州劳动合同有社保版名田_20260614142819A005.docx": b"legacy labor contract\n",
            "private/orphan.bin": b"private orphan\n",
        }
        before = {}
        for relative, payload in files.items():
            path = self.fx.legacy_compat / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(payload)
            before[relative] = hashlib.sha256(payload).hexdigest()
        applied = self.fx.apply()
        self.assertEqual(applied.returncode, 0, applied.stdout)
        self.assertIn("LEGACY_DIRECTORY_PRESERVED", applied.stdout)
        bundle = self.fx.only_bundle()
        rolled_back = self.fx.run(
            "--rollback", str(bundle), "--confirm", ROLLBACK_TOKEN
        )
        self.assertEqual(rolled_back.returncode, 0, rolled_back.stdout)
        for relative, expected in before.items():
            path = self.fx.legacy_compat / relative
            self.assertTrue(path.is_file(), relative)
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), expected)

    def test_release_link_drift_fails_closed_without_restoring_old_config(self) -> None:
        alternate = self.fx.root / "releases" / "r2"
        alternate.mkdir()
        (alternate / ".env").write_text("alternate=true\n", encoding="utf-8")
        result = self.fx.apply(
            {
                "FAKE_RELEASE_DRIFT_ON": "stop:erp-new@file.service",
                "FAKE_ALTERNATE_RELEASE": str(alternate),
            }
        )
        self.assertEqual(result.returncode, 1, result.stdout)
        bundle = self.fx.only_bundle()
        self.assertTrue((bundle / "MANUAL_INTERVENTION").is_file(), result.stdout)
        self.assertFalse((bundle / "ROLLED_BACK").exists())
        self.assertEqual(self.fx.current.resolve(), alternate)
        self.assertEqual((self.fx.release / ".env").read_text(encoding="utf-8"), self.fx.original_env)

    def test_concurrent_config_drift_is_not_overwritten_by_rollback(self) -> None:
        result = self.fx.apply({"FAKE_CONFIG_DRIFT_THEN_FAIL": "1"})
        self.assertEqual(result.returncode, 1, result.stdout)
        bundle = self.fx.only_bundle()
        self.assertTrue((bundle / "MANUAL_INTERVENTION").is_file(), result.stdout)
        self.assertFalse((bundle / "ROLLED_BACK").exists())
        self.assertEqual(
            (self.fx.release / ".env").read_text(encoding="utf-8"),
            "concurrent_operator_change=true\n",
        )

    def test_config_drift_while_stopping_is_caught_by_second_preflight(self) -> None:
        applied = self.fx.apply()
        self.assertEqual(applied.returncode, 0, applied.stdout)
        bundle = self.fx.only_bundle()
        result = self.fx.run(
            "--rollback",
            str(bundle),
            "--confirm",
            ROLLBACK_TOKEN,
            extra_env={"FAKE_CONFIG_DRIFT_ON_STOP": "1"},
        )
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertTrue((bundle / "MANUAL_INTERVENTION").is_file())
        self.assertFalse((bundle / "ROLLED_BACK").exists())
        self.assertEqual(
            (self.fx.release / ".env").read_text(encoding="utf-8"),
            "drifted_during_rollback_stop=true\n",
        )

    def test_apply_config_drift_while_stopping_is_never_overwritten(self) -> None:
        result = self.fx.apply({"FAKE_CONFIG_DRIFT_ON_STOP": "1"})
        self.assertEqual(result.returncode, 1, result.stdout)
        bundle = self.fx.only_bundle()
        self.assertTrue((bundle / "MANUAL_INTERVENTION").is_file(), result.stdout)
        self.assertFalse((bundle / "ROLLED_BACK").exists())
        self.assertEqual(
            (self.fx.release / ".env").read_text(encoding="utf-8"),
            "drifted_during_rollback_stop=true\n",
        )
        self.assertFalse((self.fx.systemd / "erp-new@file.service.d").exists())
        self.assertNotIn("daemon-reload", self.fx.command_log.read_text(encoding="utf-8"))

    def test_applied_config_is_rechecked_before_complete_publication(self) -> None:
        result = self.fx.apply({"FAKE_CONFIG_DRIFT_BEFORE_COMPLETE": "1"})
        self.assertEqual(result.returncode, 1, result.stdout)
        bundle = self.fx.only_bundle()
        self.assertTrue((bundle / "MANUAL_INTERVENTION").is_file(), result.stdout)
        self.assertFalse((bundle / "APPLY_COMPLETE").exists())
        self.assertFalse((bundle / "ROLLED_BACK").exists())
        self.assertEqual(
            (self.fx.release / ".env").read_text(encoding="utf-8"),
            "drifted_before_apply_complete=true\n",
        )

    def test_phase_downgrade_cannot_bypass_dynamic_phase_seal(self) -> None:
        applied = self.fx.apply()
        self.assertEqual(applied.returncode, 0, applied.stdout)
        bundle = self.fx.only_bundle()
        for name in (
            "APPLY_COMPLETE",
            "POST_APPLY_EXPECTED_PHASE",
            "POST_APPLY_ROLLBACK_STATE.sha256",
            "MUTATION_ROLLBACK.sha256",
            "DB_BACKUP.sha256",
        ):
            (bundle / name).unlink()
        (bundle / "PHASE").write_text("SERVICES_STOPPED\n", encoding="utf-8")
        self.fx.command_log.write_text("", encoding="utf-8")

        result = self.fx.run("--rollback", str(bundle), "--confirm", ROLLBACK_TOKEN)

        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertTrue((bundle / "MANUAL_INTERVENTION").is_file(), result.stdout)
        self.assertNotIn("systemctl stop", self.fx.command_log.read_text(encoding="utf-8"))
        self.assertIn(f"ERP_UPLOAD_ROOT={self.fx.target}", (self.fx.release / ".env").read_text())

    def test_phase_and_later_seal_inconsistency_fails_before_stop(self) -> None:
        applied = self.fx.apply()
        self.assertEqual(applied.returncode, 0, applied.stdout)
        bundle = self.fx.only_bundle()
        for name in (
            "APPLY_COMPLETE",
            "POST_APPLY_EXPECTED_PHASE",
            "POST_APPLY_ROLLBACK_STATE.sha256",
        ):
            (bundle / name).unlink()
        (bundle / "PHASE").write_text("SERVICES_STOPPED\n", encoding="utf-8")
        self.fx.command_log.write_text("", encoding="utf-8")

        result = self.fx.run("--rollback", str(bundle), "--confirm", ROLLBACK_TOKEN)

        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("phase and sealed checkpoints are inconsistent", result.stdout)
        self.assertNotIn("systemctl stop", self.fx.command_log.read_text(encoding="utf-8"))

    def test_symlinked_dropin_parent_never_mutates_external_target(self) -> None:
        applied = self.fx.apply()
        self.assertEqual(applied.returncode, 0, applied.stdout)
        bundle = self.fx.only_bundle()
        parent = self.fx.systemd / "erp-new@file.service.d"
        external = self.fx.root / "external-systemd-target"
        external.mkdir()
        external_dropin = external / "20-common-upload-root.conf"
        shutil.copy2(
            bundle / "systemd" / "erp-new@file.dropin.planned", external_dropin
        )
        canary = external / "do-not-touch.canary"
        canary.write_bytes(b"external-content-must-survive\n")
        shutil.rmtree(parent)
        parent.symlink_to(external, target_is_directory=True)
        applied_env = (self.fx.release / ".env").read_bytes()
        external_before = external_dropin.read_bytes()
        self.fx.command_log.write_text("", encoding="utf-8")

        result = self.fx.run("--rollback", str(bundle), "--confirm", ROLLBACK_TOKEN)

        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertTrue((bundle / "MANUAL_INTERVENTION").is_file(), result.stdout)
        self.assertFalse((bundle / "ROLLED_BACK").exists())
        self.assertEqual((self.fx.release / ".env").read_bytes(), applied_env)
        self.assertEqual(external_dropin.read_bytes(), external_before)
        self.assertEqual(canary.read_bytes(), b"external-content-must-survive\n")
        self.assertNotIn("systemctl stop", self.fx.command_log.read_text(encoding="utf-8"))

    def test_db_dump_checksum_gap_recovers_without_mutation(self) -> None:
        result = self.fx.apply(
            {"ERP_COMMON_UPLOAD_REPAIR_TEST_FAILPOINT": "db_after_dump_before_checksum"}
        )
        self.assertEqual(result.returncode, 1, result.stdout)
        bundle = self.fx.only_bundle()
        self.assertTrue((bundle / "database" / "signing-before.sql.gz").is_file())
        self.assertFalse((bundle / "database" / "signing-before.sql.gz.sha256").exists())
        self.assertTrue((bundle / "ROLLED_BACK").is_file(), result.stdout)
        self.fx.assert_original_configuration(self)

    def test_complete_bundle_marker_tamper_blocks_manual_rollback(self) -> None:
        applied = self.fx.apply()
        self.assertEqual(applied.returncode, 0, applied.stdout)
        bundle = self.fx.only_bundle()
        (bundle / "APPLY_COMPLETE").unlink()
        result = self.fx.run("--rollback", str(bundle), "--confirm", ROLLBACK_TOKEN)
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertFalse((bundle / "ROLLED_BACK").exists())

    def test_wrong_type_managed_child_and_manifest_ancestor_block(self) -> None:
        (self.fx.target / "private" / "attendance").write_text("wrong type", encoding="utf-8")
        result = self.fx.apply()
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("managed storage path has the wrong type", result.stdout)
        self.assertFalse(any(self.fx.backups.iterdir()))

        self.fx.close()
        self.fx = RepairFixture()
        (self.fx.target / "public").mkdir()
        (self.fx.target / "public" / "document.bin").mkdir()
        report = self.fx.root / "wrong-ancestor-audit"
        result = self.fx.run("--audit", "--output-dir", str(report))
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("blocking issues", result.stdout)
        unresolved = (report / "unresolved.tsv").read_text()
        self.assertTrue(
            "TARGET_TYPE_CONFLICT" in unresolved
            or "CANDIDATE_CONTENT_CONFLICT" in unresolved,
            unresolved,
        )

    def test_candidate_hash_conflict_blocks_even_with_a_match(self) -> None:
        conflict = self.fx.root / "conflict" / "uploadPath"
        (conflict / "public").mkdir(parents=True)
        (conflict / "sign-template").mkdir()
        (conflict / "public" / "document.bin").write_bytes(b"different")
        (conflict / "sign-template" / "template.docx").write_bytes(self.fx.template_bytes)
        report = self.fx.root / "candidate-conflict-audit"
        result = self.fx.run(
            "--audit", "--candidate", str(conflict), "--output-dir", str(report)
        )
        self.assertEqual(result.returncode, 1, result.stdout)
        self.assertIn("CANDIDATE_CONTENT_CONFLICT", (report / "unresolved.tsv").read_text())


if __name__ == "__main__":
    unittest.main(verbosity=2)
