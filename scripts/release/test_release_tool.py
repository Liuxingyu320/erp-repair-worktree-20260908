#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
import json
import subprocess
import tarfile
import tempfile
import unittest
import zipfile
from argparse import Namespace
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).resolve().with_name("release_tool.py")
SPEC = importlib.util.spec_from_file_location("erp_release_tool", MODULE_PATH)
assert SPEC and SPEC.loader
release_tool = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release_tool)


BUILD_TIME = "2026-07-28T12:00:00Z"


class ReleaseFixture:
    def __init__(self, root: Path, contract: dict):
        self.root = root
        self.contract = contract
        (root / ".gitignore").write_text(
            "target/\n**/target/\ndist/\n**/dist/\noutput/\n", encoding="utf-8"
        )
        (root / "docker").mkdir()
        (root / "docker/.env.example").write_text("TEMPLATE=true\n", encoding="utf-8")
        (root / "docker/docker-compose.yml").write_text(
            "services: {}\n", encoding="utf-8"
        )
        (root / "mvnw").write_text(
            "#!/usr/bin/env sh\n"
            "echo 'Apache Maven 3.9.16 (fixture)'\n"
            "echo 'Maven home: /fixture/maven'\n"
            "echo 'Java version: 17.0.18, vendor: Test, runtime: /fixture/java'\n",
            encoding="utf-8",
        )
        (root / "mvnw").chmod(0o755)
        for item in contract["artifactMap"]:
            source = root / item["source"]
            destination = root / item["destination"]
            source.parent.mkdir(parents=True, exist_ok=True)
            destination.parent.mkdir(parents=True, exist_ok=True)
            (destination.parent / "readme.txt").write_text(
                f"Place {destination.name} here.\n", encoding="utf-8"
            )
        subprocess.run(["git", "init", "-q"], cwd=root, check=True)
        subprocess.run(
            ["git", "config", "user.email", "release-test@example.invalid"],
            cwd=root,
            check=True,
        )
        subprocess.run(
            ["git", "config", "user.name", "Release Test"], cwd=root, check=True
        )
        subprocess.run(["git", "add", "."], cwd=root, check=True)
        subprocess.run(
            ["git", "commit", "-qm", "fixture"], cwd=root, check=True
        )
        self.commit = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True
        ).strip()
        self.build_outputs()

    def build_outputs(self) -> None:
        for item in self.contract["artifactMap"]:
            source = self.root / item["source"]
            source.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(source, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr(
                    "META-INF/MANIFEST.MF",
                    "Manifest-Version: 1.0\r\nMain-Class: example.Main\r\n\r\n",
                )
                archive.writestr("BOOT-INF/classes/example.txt", item["component"])
        frontend = self.contract["frontend"]
        dist = self.root / frontend["source"]
        dist.mkdir(parents=True, exist_ok=True)
        (dist / "index.html").write_text("<!doctype html>\n", encoding="utf-8")
        (dist / "app.js").write_text("console.log('fixture')\n", encoding="utf-8")
        (dist / frontend["releaseInfo"]).write_text(
            json.dumps({"commit": self.commit, "buildTime": BUILD_TIME}) + "\n",
            encoding="utf-8",
        )


class ReleaseToolTest(unittest.TestCase):
    def setUp(self) -> None:
        self.contract = release_tool.load_contract(release_tool.DEFAULT_CONTRACT)

    def production_environment_values(self) -> dict[str, str]:
        values = {
            item["name"]: "valid-secret-value"
            for item in self.contract["requiredEnvironment"]
        }
        values.update(
            {
                "NACOS_DB_USER": "nacos_app",
                "NACOS_CLIENT_USERNAME": "erp_prod_client",
                "NACOS_CLIENT_PASSWORD": "nacos-client-contract-password",
                "ECS_NACOS_SERVER_ADDR": "127.0.0.1:8848",
                "MYSQL_DATABASE": "BossERP_NEW",
                "MYSQL_USERNAME": "erp_app",
                "SPRING_PROFILES_ACTIVE": "prod",
                "OA_SIGN_EXCEL_IMPORT_ENABLED": "true",
                "APP_DATASOURCE_URL": "jdbc:mysql://db/erp",
                "ECS_APP_DATASOURCE_URL": (
                    "jdbc:mysql://127.0.0.1:3306/BossERP_NEW"
                    "?useSSL=false&allowPublicKeyRetrieval=false"
                ),
                "APP_DATASOURCE_USERNAME": "erp_app",
                "ERP_UPLOAD_ROOT": "/data/erp-new-data/uploadPath",
                "FILE_PATH": "/data/erp-new-data/uploadPath",
                "SIGN_PACKAGE_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/sign-package",
                "OA_ATTENDANCE_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/attendance",
                "OA_REIMBURSEMENT_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/reimbursement",
                "DRIVE_LOCAL_PATH": "/data/erp-new-data/uploadPath/private/drive",
                "FILE_DOMAIN": "https://erp.acme.cn/prod-api",
                "REFERER_ENABLED": "true",
                "REFERER_ALLOWED_DOMAINS": "erp.acme.cn,admin.acme.cn",
                "RELEASE_ID": "erp-20260728-test",
                "GIT_COMMIT": "a" * 40,
                "BUILD_TIME": BUILD_TIME,
            }
        )
        return values

    def test_environment_template_is_complete_without_emitting_values(self) -> None:
        report = release_tool.validate_environment(
            self.contract,
            Path(__file__).resolve().parents[2] / "docker/.env.example",
            Path(__file__).resolve().parents[2],
            allow_placeholders=True,
        )
        self.assertTrue(report["checks"])
        self.assertEqual(len(report["checks"]), 32)
        self.assertFalse(report["secretValuesEmitted"])
        self.assertTrue(all("value" not in item for item in report["checks"]))

    def test_production_environment_requires_external_upload_and_identity(self) -> None:
        values = self.production_environment_values()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            env_file = root / ".env"
            env_file.write_text(
                "".join(f"{key}={value}\n" for key, value in values.items()),
                encoding="utf-8",
            )
            report = release_tool.validate_environment(
                self.contract, env_file, root, allow_placeholders=False
            )
            self.assertEqual(report["mode"], "production")
            values["ERP_UPLOAD_ROOT"] = str(root / "uploads")
            env_file.write_text(
                "".join(f"{key}={value}\n" for key, value in values.items()),
                encoding="utf-8",
            )
            with self.assertRaises(release_tool.ReleaseError):
                release_tool.validate_environment(
                    self.contract, env_file, root, allow_placeholders=False
                )

    def test_production_endpoints_and_referer_policy_fail_closed(self) -> None:
        invalid_cases = {
            "nacos_remote_address": (
                "ECS_NACOS_SERVER_ADDR",
                "10.0.0.8:8848",
            ),
            "nacos_wrong_port": (
                "ECS_NACOS_SERVER_ADDR",
                "127.0.0.1:18848",
            ),
            "nacos_username_placeholder": (
                "NACOS_CLIENT_USERNAME",
                "replace-with-production-nacos-client",
            ),
            "nacos_password_placeholder": (
                "NACOS_CLIENT_PASSWORD",
                "change-me",
            ),
            "ecs_remote_database": (
                "ECS_APP_DATASOURCE_URL",
                "jdbc:mysql://10.0.0.8:3306/BossERP_NEW"
                "?allowPublicKeyRetrieval=false",
            ),
            "ecs_embedded_credentials": (
                "ECS_APP_DATASOURCE_URL",
                "jdbc:mysql://user:password@127.0.0.1:3306/BossERP_NEW"
                "?allowPublicKeyRetrieval=false",
            ),
            "ecs_public_key_retrieval": (
                "ECS_APP_DATASOURCE_URL",
                "jdbc:mysql://127.0.0.1:3306/BossERP_NEW"
                "?allowPublicKeyRetrieval=true",
            ),
            "ecs_unsupported_option": (
                "ECS_APP_DATASOURCE_URL",
                "jdbc:mysql://127.0.0.1:3306/BossERP_NEW"
                "?allowPublicKeyRetrieval=false&allowLoadLocalInfile=true",
            ),
            "file_domain_http": ("FILE_DOMAIN", "http://erp.acme.cn/prod-api"),
            "file_domain_localhost": (
                "FILE_DOMAIN",
                "https://localhost/prod-api",
            ),
            "file_domain_query": (
                "FILE_DOMAIN",
                "https://erp.acme.cn/prod-api?token=value",
            ),
            "referer_disabled": ("REFERER_ENABLED", "false"),
            "referer_placeholder": (
                "REFERER_ALLOWED_DOMAINS",
                "replace-with-production-hostname",
            ),
            "referer_url_instead_of_domain": (
                "REFERER_ALLOWED_DOMAINS",
                "https://erp.acme.cn",
            ),
        }
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            env_file = root / ".env"
            for label, (name, invalid_value) in invalid_cases.items():
                with self.subTest(label=label):
                    values = self.production_environment_values()
                    values[name] = invalid_value
                    env_file.write_text(
                        "".join(
                            f"{key}={value}\n" for key, value in values.items()
                        ),
                        encoding="utf-8",
                    )
                    with self.assertRaises(release_tool.ReleaseError):
                        release_tool.validate_environment(
                            self.contract,
                            env_file,
                            root,
                            allow_placeholders=False,
                        )

    def test_package_verify_and_rollback_dry_run(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            root.mkdir()
            fixture = ReleaseFixture(root, self.contract)
            output = Path(temporary) / "releases"
            source_state = release_tool.require_clean_commit(
                root, fixture.commit, allow_dirty=False
            )
            first_identity = {
                "releaseId": "erp-previous-20260728",
                "gitCommit": fixture.commit,
                "buildTime": BUILD_TIME,
            }
            _, epoch = release_tool.normalized_build_time(BUILD_TIME)
            previous = release_tool.create_release_package(
                root,
                output,
                self.contract,
                first_identity,
                epoch,
                source_state,
                previous_archive=None,
            )
            current_identity = {
                "releaseId": "erp-current-20260728",
                "gitCommit": fixture.commit,
                "buildTime": BUILD_TIME,
            }
            current = release_tool.create_release_package(
                root,
                output,
                self.contract,
                current_identity,
                epoch,
                source_state,
                previous_archive=Path(previous["archive"]),
            )
            verified = release_tool.verify_archive(
                Path(current["archive"]), self.contract
            )
            self.assertEqual(verified["releaseId"], current_identity["releaseId"])
            self.assertEqual(verified["artifactCount"], len(self.contract["artifactMap"]))
            evidence_path = output / "rollback-dry-run.json"
            evidence = release_tool.rollback_dry_run(
                Path(current["archive"]),
                Path(previous["archive"]),
                self.contract,
                evidence_path,
            )
            self.assertEqual(evidence["status"], "passed")
            self.assertFalse(evidence["businessDataTouched"])
            self.assertEqual(
                [item["step"] for item in evidence["steps"]],
                ["activate-current", "switch-to-previous", "restore-current"],
            )
            self.assertTrue(evidence_path.is_file())

    def test_package_embeds_and_verifies_only_the_explicit_migration_plan(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            root.mkdir()
            fixture = ReleaseFixture(root, self.contract)
            source_contract = MODULE_PATH.parents[1] / "release_migration_contract.py"
            scripts = root / "scripts"
            sql = root / "sql"
            scripts.mkdir(exist_ok=True)
            sql.mkdir()
            (scripts / "release_migration_contract.py").write_bytes(
                source_contract.read_bytes()
            )
            sql_name = "erp_fixture_expand_20260813.sql"
            sql_bytes = b"SELECT 'fixture migration';\n"
            (sql / sql_name).write_bytes(sql_bytes)
            list_path = scripts / "fixture-migrations.list"
            list_path.write_text(sql_name + "\n", encoding="utf-8")
            manifest_path = scripts / "fixture-release.json"
            manifest_path.write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "releaseId": "fixture-migration-20260813",
                        "migrationList": "scripts/fixture-migrations.list",
                        "migrationCount": 1,
                        "sourceDirectory": "sql",
                        "deployDirectory": (
                            "docker/mysql/releases/fixture-migration-20260813"
                        ),
                        "executionPolicy": "automatic",
                        "preconditions": {"tables": [], "columns": [], "indexes": []},
                        "migrations": [
                            {
                                "file": sql_name,
                                "sha256": release_tool.hashlib.sha256(sql_bytes).hexdigest(),
                                "creates": [],
                                "backupTables": [],
                            }
                        ],
                    }
                )
                + "\n",
                encoding="utf-8",
            )
            subprocess.run(
                ["git", "add", "scripts", "sql"], cwd=root, check=True
            )
            subprocess.run(
                ["git", "commit", "-qm", "add fixture migration"],
                cwd=root,
                check=True,
            )
            fixture.commit = subprocess.check_output(
                ["git", "rev-parse", "HEAD"], cwd=root, text=True
            ).strip()
            fixture.build_outputs()
            source_state = release_tool.require_clean_commit(
                root, fixture.commit, allow_dirty=False
            )
            identity = {
                "releaseId": "erp-with-migration-20260813",
                "gitCommit": fixture.commit,
                "buildTime": BUILD_TIME,
            }
            _, epoch = release_tool.normalized_build_time(BUILD_TIME)
            result = release_tool.create_release_package(
                root,
                Path(temporary) / "releases",
                self.contract,
                identity,
                epoch,
                source_state,
                previous_archive=None,
                migration_manifest=manifest_path,
            )

            verified = release_tool.verify_archive(
                Path(result["archive"]), self.contract
            )
            self.assertEqual(
                "fixture-migration-20260813", verified["migrationReleaseId"]
            )
            self.assertEqual(
                release_tool.sha256_file(manifest_path),
                verified["migrationManifestSha256"],
            )
            extracted = Path(temporary) / "extracted"
            package_root = release_tool.safe_extract_archive(
                Path(result["archive"]), extracted
            )
            release_manifest = release_tool.read_json(
                package_root / release_tool.RELEASE_MANIFEST
            )
            plan = release_manifest["migrationPlan"]
            self.assertEqual(1, plan["migrationCount"])
            self.assertEqual(
                sql_bytes,
                (
                    package_root
                    / "docker/mysql/releases/fixture-migration-20260813"
                    / sql_name
                ).read_bytes(),
            )
            self.assertTrue(
                (package_root / "docker/release/fixture-release.json").is_file()
            )

    def test_package_rejects_an_untracked_migration_input(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            root.mkdir()
            fixture = ReleaseFixture(root, self.contract)
            scripts = root / "scripts"
            sql = root / "sql"
            scripts.mkdir()
            sql.mkdir()
            (scripts / "release_migration_contract.py").write_bytes(
                (MODULE_PATH.parents[1] / "release_migration_contract.py").read_bytes()
            )
            (scripts / "fixture.list").write_text("one.sql\n", encoding="utf-8")
            sql_bytes = b"select 1;\n"
            (sql / "one.sql").write_bytes(sql_bytes)
            manifest_path = scripts / "fixture.json"
            manifest_path.write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "releaseId": "fixture-release",
                        "migrationList": "scripts/fixture.list",
                        "migrationCount": 1,
                        "sourceDirectory": "sql",
                        "deployDirectory": "docker/mysql/releases/fixture-release",
                        "executionPolicy": "automatic",
                        "preconditions": {"tables": [], "columns": [], "indexes": []},
                        "migrations": [
                            {
                                "file": "one.sql",
                                "sha256": release_tool.hashlib.sha256(sql_bytes).hexdigest(),
                                "creates": [],
                                "backupTables": [],
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                release_tool.ReleaseError, "must be tracked by Git"
            ):
                release_tool.package_migration_manifest(
                    root, Path(temporary) / "package", manifest_path
                )

    def test_safe_extract_does_not_require_python_312_filter_api(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "source"
            source.mkdir()
            (source / "manifest.txt").write_text("release\n", encoding="utf-8")
            archive_path = root / "release.tar.gz"
            with tarfile.open(archive_path, "w:gz") as archive:
                archive.add(source, arcname="erp-release")

            destination = root / "extracted"
            with mock.patch.object(
                tarfile.TarFile,
                "extractall",
                side_effect=AssertionError("extractall must not be used"),
            ):
                extracted = release_tool.safe_extract_archive(
                    archive_path, destination
                )

            self.assertEqual(extracted, destination / "erp-release")
            self.assertEqual(
                (extracted / "manifest.txt").read_text(encoding="utf-8"),
                "release\n",
            )

    def test_empty_spring_boot_marker_is_not_treated_as_a_jar_signature(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            jar = Path(temporary) / "spring-boot.jar"
            with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr(
                    "META-INF/MANIFEST.MF",
                    "Manifest-Version: 1.0\r\nMain-Class: example.Main\r\n\r\n",
                )
                archive.writestr("META-INF/BOOT.SF", b"")
                archive.writestr("BOOT-INF/classes/example.txt", "fixture")
            identity = {
                "releaseId": "erp-boot-marker-test",
                "gitCommit": "b" * 40,
                "buildTime": BUILD_TIME,
            }

            release_tool.stamp_jar(jar, identity)
            release_tool.verify_stamped_jar(jar, identity)

            with zipfile.ZipFile(jar, "r") as archive:
                self.assertEqual(archive.read("META-INF/BOOT.SF"), b"")

    def test_real_jar_signature_entries_are_rejected(self) -> None:
        identity = {
            "releaseId": "erp-signed-jar-test",
            "gitCommit": "c" * 40,
            "buildTime": BUILD_TIME,
        }
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            cases = (
                ("named-signer.jar", "META-INF/SIGNER.SF", b"signature", None),
                ("boot-signer.jar", "META-INF/BOOT.SF", b"", "META-INF/BOOT.RSA"),
            )
            for filename, signature_file, signature_content, signature_block in cases:
                jar = root / filename
                with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
                    archive.writestr(
                        "META-INF/MANIFEST.MF",
                        "Manifest-Version: 1.0\r\nMain-Class: example.Main\r\n\r\n",
                    )
                    archive.writestr(signature_file, signature_content)
                    if signature_block:
                        archive.writestr(signature_block, b"certificate")
                with self.subTest(filename=filename):
                    with self.assertRaises(release_tool.ReleaseError):
                        release_tool.stamp_jar(jar, identity)

    def test_toolchain_prefers_wrapper_and_rejects_system_maven_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = ReleaseFixture(root, self.contract)
            toolchain = release_tool.collect_toolchain(root)
            self.assertEqual(toolchain["maven"]["command"], ["./mvnw", "--version"])
            self.assertIn(
                "Apache Maven 3.9.16",
                "\n".join(toolchain["maven"]["output"]),
            )

            package_root = root / "package"
            (package_root / "provenance").mkdir(parents=True)
            toolchain["maven"]["command"] = ["mvn", "--version"]
            release_tool.write_json(
                package_root / release_tool.TOOLCHAIN_MANIFEST,
                toolchain,
            )
            with self.assertRaises(release_tool.ReleaseError):
                release_tool.validate_toolchain_manifest(
                    package_root, self.contract
                )

    def test_unexpected_jar_name_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fixture = ReleaseFixture(root, self.contract)
            first = self.contract["artifactMap"][0]
            extra = (root / first["source"]).parent / "gateway copy.jar"
            extra.write_bytes((root / first["source"]).read_bytes())
            with self.assertRaises(release_tool.ReleaseError):
                release_tool.validate_artifact_inputs(root, self.contract)
            self.assertEqual(
                subprocess.check_output(
                    ["git", "status", "--porcelain"], cwd=root, text=True
                ).strip(),
                "",
            )

    def test_old_deployment_dist_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ReleaseFixture(root, self.contract)
            old_dist = root / self.contract["frontend"]["destination"]
            old_dist.mkdir(parents=True)
            (old_dist / "index.html").write_text("old", encoding="utf-8")
            with self.assertRaises(release_tool.ReleaseError):
                release_tool.check_clean_deployment_sources(root, self.contract)

    def test_compose_contract_rejects_nonproduction_profile(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            compose = Path(temporary) / "compose.yml"
            compose.write_text(
                """
x-env: &env
  SPRING_PROFILES_ACTIVE: local
  OA_SIGN_EXCEL_IMPORT_ENABLED: ${OA_SIGN_EXCEL_IMPORT_ENABLED:-true}
  RELEASE_ID: ${RELEASE_ID:?required}
  GIT_COMMIT: ${GIT_COMMIT:?required}
  BUILD_TIME: ${BUILD_TIME:?required}
  ERP_UPLOAD_ROOT: /data/erp-new-data/uploadPath
  FILE_PATH: /data/erp-new-data/uploadPath
  SIGN_PACKAGE_STORAGE_ROOT: /data/erp-new-data/uploadPath/private/sign-package
  OA_ATTENDANCE_STORAGE_ROOT: /data/erp-new-data/uploadPath/private/attendance
  OA_REIMBURSEMENT_STORAGE_ROOT: /data/erp-new-data/uploadPath/private/reimbursement
  DRIVE_LOCAL_PATH: /data/erp-new-data/uploadPath/private/drive
services:
  file:
    environment: *env
    volumes:
      - ${ERP_UPLOAD_ROOT:?required}:/data/erp-new-data/uploadPath
""".lstrip(),
                encoding="utf-8",
            )
            with self.assertRaises(release_tool.ReleaseError):
                release_tool.validate_compose_source(compose)

    def test_compose_contract_accepts_interpolated_release_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            compose = Path(temporary) / "compose.yml"
            compose.write_text(
                """
x-env: &env
  SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:?required}
  OA_SIGN_EXCEL_IMPORT_ENABLED: ${OA_SIGN_EXCEL_IMPORT_ENABLED:-true}
  RELEASE_ID: ${RELEASE_ID:?required}
  GIT_COMMIT: ${GIT_COMMIT:?required}
  BUILD_TIME: ${BUILD_TIME:?required}
  ERP_UPLOAD_ROOT: /data/erp-new-data/uploadPath
  FILE_PATH: /data/erp-new-data/uploadPath
  SIGN_PACKAGE_STORAGE_ROOT: /data/erp-new-data/uploadPath/private/sign-package
  OA_ATTENDANCE_STORAGE_ROOT: /data/erp-new-data/uploadPath/private/attendance
  OA_REIMBURSEMENT_STORAGE_ROOT: /data/erp-new-data/uploadPath/private/reimbursement
  DRIVE_LOCAL_PATH: /data/erp-new-data/uploadPath/private/drive
services:
  file:
    environment: *env
    volumes:
      - ${ERP_UPLOAD_ROOT:?required}:/data/erp-new-data/uploadPath
""".lstrip(),
                encoding="utf-8",
            )
            report = release_tool.validate_compose_source(compose)
            self.assertEqual(report["status"], "valid")

    def test_preflight_validates_every_requested_compose_file(self) -> None:
        args = Namespace(
            contract=release_tool.DEFAULT_CONTRACT,
            repo_root=Path("/fixture/repo"),
            env_file=Path("/fixture/repo/docker/.env"),
            compose_file=[
                Path("/fixture/repo/docker/docker-compose.yml"),
                Path("/fixture/repo/docker/docker-compose.ecs-host.yml"),
            ],
            archive=Path("/fixture/release.tar.gz"),
        )
        package = {
            "releaseId": "erp-release-test",
            "gitCommit": "a" * 40,
            "buildTime": BUILD_TIME,
        }
        environment_values = {
            "RELEASE_ID": package["releaseId"],
            "GIT_COMMIT": package["gitCommit"],
            "BUILD_TIME": package["buildTime"],
        }
        with (
            mock.patch.object(
                release_tool, "load_contract", return_value=self.contract
            ),
            mock.patch.object(
                release_tool,
                "validate_environment",
                return_value={"status": "valid"},
            ),
            mock.patch.object(
                release_tool,
                "validate_compose_source",
                side_effect=lambda path: {
                    "composeFile": str(path),
                    "status": "valid",
                },
            ) as compose_validator,
            mock.patch.object(release_tool, "verify_archive", return_value=package),
            mock.patch.object(
                release_tool, "parse_env", return_value=environment_values
            ),
        ):
            result = release_tool.command_preflight(args)
        self.assertEqual(result["status"], "passed")
        self.assertEqual(len(result["composes"]), 2)
        self.assertEqual(compose_validator.call_count, 2)


if __name__ == "__main__":
    unittest.main()
