#!/usr/bin/env python3

import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from release_migration_contract import (
    MigrationPlan,
    UNSAFE_AUTOMATIC_MIGRATIONS,
    load_and_validate_manifest,
    prepare_migration_plan,
    render_remote_migration_script,
)


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "scripts/new-business-release-20260714.json"


def create_test_manifest(root: Path, names=("one.sql", "two.sql")) -> Path:
    scripts = root / "scripts"
    source = root / "sql"
    deploy = root / "docker/mysql/releases/test-release"
    scripts.mkdir(parents=True)
    source.mkdir(parents=True)
    deploy.mkdir(parents=True)
    migrations = []
    for name in names:
        body = ("select '" + name + "';\n").encode()
        (source / name).write_bytes(body)
        (deploy / name).write_bytes(body)
        migrations.append({
            "file": name,
            "sha256": hashlib.sha256(body).hexdigest(),
            "creates": [],
            "backupTables": [],
        })
    (scripts / "test.list").write_text("\n".join(names) + "\n", encoding="utf-8")
    manifest_path = scripts / "test.json"
    manifest_path.write_text(json.dumps({
        "schemaVersion": 1,
        "releaseId": "test-release",
        "migrationCount": len(migrations),
        "sourceDirectory": "sql",
        "deployDirectory": "docker/mysql/releases/test-release",
        "migrationList": "scripts/test.list",
        "migrations": migrations,
    }), encoding="utf-8")
    return manifest_path


def configure_table_contract(
    manifest_path: Path,
    *,
    creates: list[str],
    backup_tables: list[str],
) -> None:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["migrations"][0]["creates"] = creates
    manifest["migrations"][0]["backupTables"] = backup_tables
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")


def run_rendered_remote_script(
    manifest_path: Path,
    *,
    existing_tables: set[str],
    bypass_host_validation: bool = False,
) -> tuple[subprocess.CompletedProcess[str], Path, Path]:
    root = manifest_path.parent.parent
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    password_file = root / "mysql-password"
    password_file.write_text("secret\n", encoding="utf-8")

    if bypass_host_validation:
        plan = MigrationPlan(
            enabled=True,
            release_id=manifest["releaseId"],
            manifest_name=manifest_path.name,
            manifest_sha256=hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
            database_name="erp_test",
            mysql_user="erp_release",
            mysql_password_file=str(password_file),
        )
    else:
        plan = prepare_migration_plan(
            apply_migrations=True,
            manifest_path=str(manifest_path),
            approval=manifest["releaseId"],
            database_name="erp_test",
            mysql_user="erp_release",
            mysql_password_file=str(password_file),
        )

    new_docker = root / "remote-new-docker"
    remote_manifest = new_docker / "release" / manifest_path.name
    remote_manifest.parent.mkdir(parents=True)
    remote_manifest.write_bytes(manifest_path.read_bytes())
    remote_sql = new_docker / "mysql" / "releases" / manifest["releaseId"]
    remote_sql.mkdir(parents=True)
    deploy_dir = root / manifest["deployDirectory"]
    for item in manifest["migrations"]:
        (remote_sql / item["file"]).write_bytes(
            (deploy_dir / item["file"]).read_bytes()
        )

    fake_bin = root / "fake-bin"
    fake_bin.mkdir()
    fake_mysql = fake_bin / "mysql"
    fake_mysql.write_text(
        """#!/usr/bin/env python3
import os
import re
import sys

query = sys.argv[sys.argv.index("-e") + 1] if "-e" in sys.argv else ""
if "@@version" in query:
    print("8.0.36|utf8mb4|erp_test")
elif "information_schema.tables" in query:
    match = re.search(r"table_name = '([a-z][a-z0-9_]*)'", query)
    if match is None:
        raise SystemExit("unrecognized table-existence query")
    existing = set(filter(None, os.environ.get("FAKE_EXISTING_TABLES", "").split(",")))
    print("1" if match.group(1) in existing else "0")
else:
    sys.stdin.read()
""",
        encoding="utf-8",
    )
    fake_mysql.chmod(0o755)
    fake_mysqldump = fake_bin / "mysqldump"
    fake_mysqldump.write_text(
        """#!/usr/bin/env python3
import json
import os
import sys
from pathlib import Path

Path(os.environ["FAKE_MYSQLDUMP_LOG"]).write_text(
    json.dumps(sys.argv[1:]), encoding="utf-8"
)
print("-- fake table backup")
""",
        encoding="utf-8",
    )
    fake_mysqldump.chmod(0o755)

    release_dir = root / "release-state"
    dump_log = root / "mysqldump-args.json"
    environment = os.environ.copy()
    environment.update({
        "NEW_DOCKER": str(new_docker),
        "RELEASE_DIR": str(release_dir),
        "FAKE_EXISTING_TABLES": ",".join(sorted(existing_tables)),
        "FAKE_MYSQLDUMP_LOG": str(dump_log),
        "PATH": str(fake_bin) + os.pathsep + environment["PATH"],
    })
    completed = subprocess.run(
        ["bash", "-c", "set -euo pipefail\n" + render_remote_migration_script(plan)],
        text=True,
        capture_output=True,
        env=environment,
    )
    record_dir = release_dir / "db-backup" / manifest["releaseId"]
    return completed, record_dir, dump_log


class ReleaseMigrationContractTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.manifest = create_test_manifest(Path(self.temporary_directory.name))

    def tearDown(self):
        self.temporary_directory.cleanup()

    def test_new_business_release_count_is_explicit_and_matches_entries(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        self.assertEqual(15, manifest["migrationCount"])
        self.assertEqual(manifest["migrationCount"], len(manifest["migrations"]))

    def test_new_business_manifest_satisfies_the_generic_contract(self):
        validated = load_and_validate_manifest(
            MANIFEST, require_deploy_mirror=False
        )

        self.assertEqual(15, validated["migrationCount"])

    def test_generic_contract_accepts_a_nonempty_manifest_declared_order(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            names = ["one.sql", "two.sql"]
            manifest_path = create_test_manifest(root, tuple(names))

            validated = load_and_validate_manifest(manifest_path)

            self.assertEqual(names, [item["file"] for item in validated["migrations"]])

    def test_source_only_validation_does_not_require_a_deploy_mirror(self):
        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        deploy = (
            self.manifest.parent.parent
            / manifest["deployDirectory"]
            / manifest["migrations"][0]["file"]
        )
        deploy.unlink()

        with self.assertRaisesRegex(ValueError, "source/deploy pair is incomplete"):
            load_and_validate_manifest(self.manifest)

        validated = load_and_validate_manifest(
            self.manifest, require_deploy_mirror=False
        )

        self.assertEqual("test-release", validated["releaseId"])

        source = (
            self.manifest.parent.parent
            / manifest["sourceDirectory"]
            / manifest["migrations"][0]["file"]
        )
        source.write_text("select 'tampered';\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "migration hash mismatch"):
            load_and_validate_manifest(
                self.manifest, require_deploy_mirror=False
            )

    def test_generic_contract_rejects_migration_count_drift(self):
        manifest = json.loads(self.manifest.read_text(encoding="utf-8"))
        manifest["migrationCount"] += 1
        self.manifest.write_text(json.dumps(manifest), encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "migrationCount"):
            load_and_validate_manifest(self.manifest)

    def test_generic_contract_rejects_manual_cutover_in_automatic_manifest(self):
        expected_unsafe = {
            "erp_unified_approval_center_20260714.sql",
            "erp_inventory_unified_approval_cutover_20260714.sql",
            "erp_oa_flowable_test_data_purge_20260714.sql",
        }
        self.assertEqual(expected_unsafe, set(UNSAFE_AUTOMATIC_MIGRATIONS))
        for migration in sorted(expected_unsafe):
            with self.subTest(migration=migration):
                with tempfile.TemporaryDirectory() as directory:
                    manifest = create_test_manifest(Path(directory), (migration,))

                    with self.assertRaisesRegex(ValueError, "unsafe manual migration"):
                        load_and_validate_manifest(manifest)

    def test_generic_contract_requires_approval_dependency_closure(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = create_test_manifest(
                Path(directory),
                ("erp_unified_approval_seed_20260716.sql",),
            )

            with self.assertRaisesRegex(ValueError, "requires dependency closure"):
                load_and_validate_manifest(manifest)

    def test_generic_contract_rejects_dependency_order_inversion(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = create_test_manifest(Path(directory), (
                "erp_hr_health_certificate_20260713.sql",
                "erp_unified_approval_seed_20260716.sql",
                "erp_hr_health_certificate_unified_approval_20260714.sql",
                "erp_inventory_unified_approval_expand_20260716.sql",
                "erp_oa_purchase_unified_approval_expand_20260716.sql",
                "erp_unified_approval_schema_20260716.sql",
            ))

            with self.assertRaisesRegex(ValueError, "must precede"):
                load_and_validate_manifest(manifest)

    def test_generic_contract_accepts_complete_approval_dependency_order(self):
        with tempfile.TemporaryDirectory() as directory:
            names = (
                "erp_hr_health_certificate_20260713.sql",
                "erp_hr_health_certificate_unified_approval_20260714.sql",
                "erp_inventory_unified_approval_expand_20260716.sql",
                "erp_oa_purchase_unified_approval_expand_20260716.sql",
                "erp_unified_approval_schema_20260716.sql",
                "erp_unified_approval_seed_20260716.sql",
                "erp_inventory_transfer_approval_start_outbox_20260716.sql",
                "erp_inventory_stock_check_approval_start_outbox_20260716.sql",
                "erp_oa_purchase_approval_start_outbox_20260716.sql",
                "erp_hr_health_certificate_approval_start_outbox_20260716.sql",
            )
            manifest = create_test_manifest(Path(directory), names)

            validated = load_and_validate_manifest(manifest)

            self.assertEqual(list(names), [
                item["file"] for item in validated["migrations"]
            ])

    def test_disabled_plan_never_writes_database(self):
        plan = prepare_migration_plan(
            apply_migrations=False,
            manifest_path=None,
            approval=None,
            database_name=None,
            mysql_user="root",
            mysql_password_file="/root/.erp-mysql-root-pass",
        )
        script = render_remote_migration_script(plan)
        self.assertIn("migrations skipped", script.lower())
        self.assertNotIn("mysqldump", script)

    def test_enabled_plan_requires_exact_release_approval(self):
        with self.assertRaisesRegex(ValueError, "exactly equal"):
            prepare_migration_plan(
                apply_migrations=True,
                manifest_path=str(self.manifest),
                approval="wrong-release",
                database_name="erp_test",
                mysql_user="root",
                mysql_password_file="/root/.erp-mysql-root-pass",
            )

    def test_enabled_plan_is_manifest_driven(self):
        plan = prepare_migration_plan(
            apply_migrations=True,
            manifest_path=str(self.manifest),
            approval="test-release",
            database_name="erp_test",
            mysql_user="erp_release",
            mysql_password_file="/run/secrets/mysql-release-password",
        )
        script = render_remote_migration_script(plan)
        self.assertIn("test-release", script)
        self.assertIn("erp_test", script)
        self.assertIn("migration-files.txt", script)
        self.assertIn("GET_LOCK", script)
        self.assertIn("mysqldump", script)
        self.assertIn("declared-creates-tables.txt", script)
        self.assertIn("table-presence-before.txt", script)
        self.assertIn("created-before=false", script)
        self.assertIn("sort -u", script)
        self.assertIn("unsafe manual migration in automatic manifest", script)
        for migration in UNSAFE_AUTOMATIC_MIGRATIONS:
            self.assertIn(migration, script)
        self.assertIn("automatic migration dependency closure is incomplete", script)
        self.assertNotIn("BossERP_NEW", script)
        self.assertNotIn("erp_inventory_transfer_reference_amount_20260701.sql", script)
        syntax = subprocess.run(
            ["bash", "-n"], input=script, text=True, capture_output=True
        )
        self.assertEqual(0, syntax.returncode, syntax.stderr)

    def test_remote_new_install_records_missing_creates_without_blocking(self):
        configure_table_contract(
            self.manifest,
            creates=["new_table"],
            backup_tables=[],
        )

        completed, record_dir, dump_log = run_rendered_remote_script(
            self.manifest,
            existing_tables=set(),
        )

        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual(
            [],
            (record_dir / "backup-tables.txt").read_text(
                encoding="utf-8"
            ).splitlines(),
        )
        self.assertEqual(
            [
                "creates\tnew_table\tcreated-before=false",
            ],
            (record_dir / "table-presence-before.txt").read_text(
                encoding="utf-8"
            ).splitlines(),
        )
        self.assertFalse(dump_log.exists())
        self.assertGreater((record_dir / "tables_before.sql.gz").stat().st_size, 0)

    def test_remote_rerun_backs_up_existing_creates_once(self):
        configure_table_contract(
            self.manifest,
            creates=["created_table", "created_table"],
            backup_tables=["required_table", "required_table"],
        )

        completed, record_dir, dump_log = run_rendered_remote_script(
            self.manifest,
            existing_tables={"created_table", "required_table"},
        )

        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual(
            ["created_table", "required_table"],
            (record_dir / "backup-tables.txt").read_text(
                encoding="utf-8"
            ).splitlines(),
        )
        self.assertEqual(
            [
                "backupTables\trequired_table\tcreated-before=true",
                "creates\tcreated_table\tcreated-before=true",
            ],
            (record_dir / "table-presence-before.txt").read_text(
                encoding="utf-8"
            ).splitlines(),
        )
        dump_arguments = json.loads(dump_log.read_text(encoding="utf-8"))
        self.assertEqual(1, dump_arguments.count("required_table"))
        self.assertEqual(1, dump_arguments.count("created_table"))

    def test_remote_still_fails_when_required_backup_table_is_missing(self):
        configure_table_contract(
            self.manifest,
            creates=["new_table"],
            backup_tables=["required_table"],
        )

        completed, record_dir, dump_log = run_rendered_remote_script(
            self.manifest,
            existing_tables=set(),
        )

        self.assertEqual(63, completed.returncode)
        self.assertIn("required backup table is missing", completed.stderr)
        self.assertEqual(
            "backupTables\trequired_table\tcreated-before=false\n",
            (record_dir / "table-presence-before.txt").read_text(encoding="utf-8"),
        )
        self.assertFalse(dump_log.exists())

    def test_remote_revalidates_creates_table_names(self):
        configure_table_contract(
            self.manifest,
            creates=["unsafe-table"],
            backup_tables=[],
        )

        completed, _record_dir, dump_log = run_rendered_remote_script(
            self.manifest,
            existing_tables=set(),
            bypass_host_validation=True,
        )

        self.assertNotEqual(0, completed.returncode)
        self.assertIn("unsafe creates table", completed.stderr)
        self.assertFalse(dump_log.exists())

    def test_database_name_is_restricted(self):
        with self.assertRaisesRegex(ValueError, "letters, numbers"):
            prepare_migration_plan(
                apply_migrations=True,
                manifest_path=str(self.manifest),
                approval="test-release",
                database_name="erp;drop database",
                mysql_user="root",
                mysql_password_file="/root/.erp-mysql-root-pass",
            )

    def test_deploy_helper_has_no_legacy_hardcoded_migration(self):
        helper = (ROOT / "scripts/aliyun_ecs_deploy_helper.py").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("BossERP_NEW", helper)
        self.assertNotIn("erp_inventory_transfer_reference_amount_20260701.sql", helper)
        self.assertIn("add_release_migration_arguments", helper)


if __name__ == "__main__":
    unittest.main()
