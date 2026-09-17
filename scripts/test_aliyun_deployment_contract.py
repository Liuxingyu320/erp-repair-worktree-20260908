#!/usr/bin/env python3
"""Static regression tests for the Aliyun deployment safety contract."""

import ast
import hashlib
import json
import re
import os
import stat
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HELPER = (ROOT / "scripts/aliyun_ecs_deploy_helper.py").read_text(encoding="utf-8")
REMOTE_VERIFY = (ROOT / "scripts/remote_deploy_verify.sh").read_text(encoding="utf-8")
ECS_COMPOSE = (ROOT / "docker/docker-compose.ecs-host.yml").read_text(encoding="utf-8")
STANDARD_COMPOSE = (ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
ECS_DEPLOY = (ROOT / "docker/deploy-ecs.sh").read_text(encoding="utf-8")
DOCKER_DEPLOY = (ROOT / "docker/deploy.sh").read_text(encoding="utf-8")
HOST_RUNNER = (ROOT / "docker/run-erp-service.sh").read_text(encoding="utf-8")
ENV_EXAMPLE = (ROOT / "docker/.env.example").read_text(encoding="utf-8")
RUNBOOK = (ROOT / "docs/ALIYUN_ECS_DEPLOYMENT_RUNBOOK.md").read_text(encoding="utf-8")


SERVICES = {
    "gateway": 8080,
    "auth": 9200,
    "monitor": 9100,
    "system": 9201,
    "job": 9203,
    "oa": 9204,
    "inventory": 9205,
    "file": 9300,
    "approval": 9206,
}

RUNTIME_DB_CONFIGS = (
    "erp-modules/erp-system/src/main/resources/bootstrap.yml",
    "erp-modules/erp-system/src/main/resources/application-dev.yml",
    "erp-modules/erp-oa/src/main/resources/bootstrap.yml",
    "erp-modules/erp-inventory/src/main/resources/bootstrap.yml",
    "erp-modules/erp-file/src/main/resources/application-local.yml",
    "erp-modules/erp-file/src/main/resources/application-dev.yml",
    "erp-modules/erp-job/src/main/resources/application-dev.yml",
    "erp-modules/erp-approval/src/main/resources/bootstrap.yml",
)


def compose_service_block(name: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(name)}:\n(?P<body>.*?)(?=^  [a-zA-Z0-9_-]+:\n|\Z)",
        ECS_COMPOSE,
    )
    if not match:
        raise AssertionError(f"missing compose service: {name}")
    return match.group("body")


def host_runtime_contract() -> str:
    tree = ast.parse(HELPER, filename="aliyun_ecs_deploy_helper.py")
    function = next(
        node
        for node in tree.body
        if isinstance(node, ast.FunctionDef)
        and node.name == "render_host_runtime_contract"
    )
    namespace = {}
    exec(
        compile(ast.Module(body=[function], type_ignores=[]), "runtime-contract", "exec"),
        namespace,
    )
    return namespace["render_host_runtime_contract"]()


def release_archive_verifier():
    tree = ast.parse(HELPER, filename="aliyun_ecs_deploy_helper.py")
    function = next(
        node
        for node in tree.body
        if isinstance(node, ast.FunctionDef)
        and node.name == "verify_release_archive"
    )
    namespace = {
        "json": json,
        "Path": Path,
        "RELEASE_VERIFY_TOOL": ROOT / "scripts/release/release_tool.py",
        "re": re,
        "subprocess": subprocess,
        "sys": sys,
    }
    exec(
        compile(ast.Module(body=[function], type_ignores=[]), "release-verifier", "exec"),
        namespace,
    )
    return namespace["verify_release_archive"]


def rendered_helper(name: str) -> str:
    tree = ast.parse(HELPER, filename="aliyun_ecs_deploy_helper.py")
    function = next(
        node
        for node in tree.body
        if isinstance(node, ast.FunctionDef) and node.name == name
    )
    namespace = {}
    exec(
        compile(ast.Module(body=[function], type_ignores=[]), name, "exec"),
        namespace,
    )
    return namespace[name]()


class AliyunDeploymentContractTest(unittest.TestCase):
    RELEASE_IDENTITY = {
        "releaseId": "release-test-20260813",
        "gitCommit": "a" * 40,
        "buildTime": "2026-08-13T02:10:00Z",
    }

    def make_host_runner_fixture(self, root: Path) -> Path:
        runner = root / "run-erp-service.sh"
        # The host runner has no production bypass. Substitute only filesystem
        # paths in the fixture; execute the real path/mount guards against fake findmnt.
        data = root.resolve() / "data"
        fixture = HOST_RUNNER.replace("/data", str(data))
        fixture = fixture.replace('require_data_path "$BASE_DIR"', 'require_data_path "' + str(data / "erp-new/releases") + '"')
        for relative in ["erp-new/releases", "erp-new/packages", "erp-new/backups", "erp-new-data/uploadPath"]:
            (data / relative).mkdir(parents=True, exist_ok=True)
        for kind in ["logs", "tmp", "cache"]:
            for service in SERVICES:
                (data / "erp-new-data" / kind / service).mkdir(parents=True, exist_ok=True)
        for name in ["attendance", "reimbursement"]:
            (data / "erp-new-data/tmp" / name).mkdir(parents=True, exist_ok=True)
        for name in ["sign-package", "sign-package-temp", "attendance", "reimbursement", "drive"]:
            (data / "erp-new-data/uploadPath/private" / name).mkdir(parents=True, exist_ok=True)
        (root / "logs").symlink_to(data / "erp-new-data/logs")
        findmnt = root / "findmnt-fixture"
        findmnt.write_text("#!/usr/bin/env bash\nprintf '%s\\n' \"${TEST_MOUNT_TARGET:-" + str(data) + "}\"\n")
        findmnt.chmod(0o700)
        fixture = fixture.replace("findmnt -n", '"' + str(findmnt) + '" -n')
        runner.write_text(fixture, encoding="utf-8")
        runner.chmod(runner.stat().st_mode | stat.S_IXUSR)
        return runner

    def test_host_release_targets_every_packaged_java_service(self):
        deploy_host_source = HELPER[
            HELPER.index("def deploy_host(args):") : HELPER.index("\ndef deploy_host_patch(args):")
        ]
        for service, port in SERVICES.items():
            self.assertRegex(HELPER, rf'["\']{service}["\']\s*:\s*{port}\b')
        self.assertIn('services = " ".join(HOST_SERVICE_PORTS)', deploy_host_source)
        self.assertIn("HOST_SERVICE_START_ORDER", HELPER)
        self.assertIn("HOST_REQUIRED_BASELINE_SERVICES", HELPER)
        self.assertIn("required previous ERP services are not active", HELPER)
        self.assertNotIn("start_services $SERVICES", deploy_host_source)
        self.assertIn("start_services $PREVIOUS_ACTIVE_SERVICES", deploy_host_source)
        self.assertIn(
            'wait_for_host_readiness "$PREVIOUS_ACTIVE_SERVICES"',
            deploy_host_source,
        )
        self.assertIn("wait_for_host_readiness", HELPER)
        self.assertIn("reload_nginx", deploy_host_source)
        self.assertIn("/etc/init.d/nginx reload", HELPER)
        self.assertIn('kill -HUP "$nginx_master_pid"', HELPER)
        self.assertNotIn("systemctl reload nginx\n", HELPER)
        self.assertNotIn(
            'if [ "$svc" = monitor ]; then\n    curl',
            HELPER,
        )
        self.assertIn('--legacy-preflight is restricted to remote_deploy_verify.sh', HELPER)
        self.assertIn("--legacy-preflight requires a command name containing 'predeploy'", HELPER)
        self.assertNotIn(
            "http://127.0.0.1/prod-api/code >/dev/null || true",
            HELPER,
        )
        self.assertNotIn("restart_services || true", HELPER)
        self.assertNotIn("systemctl daemon-reload || true", HELPER)
        self.assertIn("previous_services=$PREVIOUS_ACTIVE_SERVICES", deploy_host_source)
        self.assertIn(
                'verify_host_service_contract "$NEW_DOCKER"\n'
                "trap 'rollback \"$?\"' ERR\n"
                "trap 'rollback_on_exit \"$?\"' EXIT",
            deploy_host_source,
        )
        self.assertIn("trap - ERR EXIT", HELPER)
        self.assertIn("run-erp-service-legacy.sh", HELPER)
        self.assertIn('LEGACY_RUNNER_SOURCE="$PREV_TARGET/run-erp-service-legacy.sh"', HELPER)
        self.assertIn("def put_object_with_retry(bucket, key, data, attempts=12):", HELPER)
        self.assertGreaterEqual(
            HELPER.count("part_size = 1 * 1024 * 1024"),
            2,
            "both multipart and resumable chunk uploads must use small parts",
        )

    def test_host_release_preserves_optional_service_state(self):
        tree = ast.parse(HELPER, filename="aliyun_ecs_deploy_helper.py")
        assignment = next(
            node
            for node in tree.body
            if isinstance(node, ast.Assign)
            and any(
                isinstance(target, ast.Name)
                and target.id == "HOST_REQUIRED_BASELINE_SERVICES"
                for target in node.targets
            )
        )
        required = set(ast.literal_eval(assignment.value))
        self.assertEqual(
            required,
            {"gateway", "auth", "system", "oa", "inventory", "file", "approval"},
        )
        self.assertNotIn("job", required)
        self.assertNotIn("monitor", required)

    def test_full_host_deploy_requires_verified_canonical_release_archive(self):
        deploy_host_source = HELPER[
            HELPER.index("def deploy_host(args):") : HELPER.index("\ndef deploy_host_patch(args):")
        ]
        self.assertLess(
            deploy_host_source.index("verified_release = verify_release_archive(package)"),
            deploy_host_source.index("ak, secret = read_credentials()"),
            "a raw package must be rejected before credentials or external writes are used",
        )
        self.assertLess(
            deploy_host_source.index("packaged_migration_release ="),
            deploy_host_source.index("ak, secret = read_credentials()"),
            "migration/archive pairing must fail before credentials or remote writes",
        )
        self.assertIn(
            "candidate migration identity differs from the explicitly approved",
            deploy_host_source,
        )
        self.assertIn("packaged_migration_manifest_sha =", deploy_host_source)
        self.assertIn("approved_migration_manifest_sha =", deploy_host_source)
        self.assertLess(
            deploy_host_source.index("approved_migration_manifest_sha ="),
            deploy_host_source.index("ak, secret = read_credentials()"),
            "the exact manifest hash must be paired before credentials or remote writes",
        )
        self.assertIn('PACKAGE_ROOT="$RELEASE_DIR/$RELEASE_ID"', deploy_host_source)
        self.assertIn('NEW_DOCKER="$PACKAGE_ROOT/docker"', deploy_host_source)
        self.assertIn("remote_release_verifier = render_remote_release_verifier()", deploy_host_source)
        self.assertIn("remote_release_env_stamper = render_remote_release_env_stamper()", deploy_host_source)
        self.assertIn('grep -Fxc "RELEASE_ID=$RELEASE_ID"', deploy_host_source)
        self.assertIn('grep -Fxc "GIT_COMMIT=$EXPECTED_GIT_COMMIT"', deploy_host_source)
        self.assertIn('grep -Fxc "BUILD_TIME=$EXPECTED_BUILD_TIME"', deploy_host_source)

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw_docker = root / "docker"
            raw_docker.mkdir()
            (raw_docker / "docker-compose.yml").write_text(
                "services: {}\n", encoding="utf-8"
            )
            raw_archive = root / "raw-docker.tar.gz"
            with tarfile.open(raw_archive, "w:gz") as archive:
                archive.add(raw_docker, arcname="docker")
            with self.assertRaisesRegex(
                SystemExit, "release archive verification failed"
            ):
                release_archive_verifier()(raw_archive)

    def test_release_archive_verifier_returns_hash_bound_migration_identity(self):
        verified = {
            "status": "verified",
            "releaseId": "release-test-20260813",
            "gitCommit": "a" * 40,
            "buildTime": "2026-08-13T13:43:31Z",
            "migrationReleaseId": "system-config-metadata-20260813",
            "migrationManifestSha256": "b" * 64,
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "candidate.tar.gz"
            archive.write_bytes(b"fixture")
            verifier = root / "verifier.py"
            verifier.write_text(
                "import json\nprint(json.loads(" + repr(json.dumps(json.dumps(verified))) + "))\n",
                encoding="utf-8",
            )
            function = release_archive_verifier()
            function.__globals__["RELEASE_VERIFY_TOOL"] = verifier

            identity = function(archive)

            self.assertEqual(
                "system-config-metadata-20260813",
                identity["migrationReleaseId"],
            )
            self.assertEqual("b" * 64, identity["migrationManifestSha256"])

    def test_remote_release_helpers_compile_with_production_python_36_grammar(self):
        for helper in (
            "render_remote_release_verifier",
            "render_remote_release_env_stamper",
        ):
            source = rendered_helper(helper)
            result = subprocess.run(
                [sys.executable, "-c", "import ast,sys; ast.parse(sys.stdin.read(), feature_version=(3,6))"],
                input=source,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertNotIn("from __future__ import annotations", source)
            self.assertNotRegex(source, r"\bf[\"']")

    def write_remote_release_fixture(self, root: Path):
        identity = self.RELEASE_IDENTITY
        frontend = root / "docker/erp/ui"
        jar = root / "docker/erp/system/jar/system.jar"
        provenance = root / "provenance"
        release_tools = root / "release-tools"
        frontend.mkdir(parents=True)
        jar.parent.mkdir(parents=True)
        provenance.mkdir(parents=True)
        release_tools.mkdir(parents=True)

        (provenance / "release-manifest.json").write_text(
            json.dumps(identity), encoding="utf-8"
        )
        (release_tools / "release-contract.json").write_text(
            json.dumps(
                {
                    "frontend": {
                        "destination": "docker/erp/ui",
                        "releaseInfo": "release-info.json",
                    },
                    "artifactMap": [
                        {"destination": "docker/erp/system/jar/system.jar"}
                    ],
                }
            ),
            encoding="utf-8",
        )
        (frontend / "release-info.json").write_text(
            json.dumps(identity), encoding="utf-8"
        )
        manifest = (
            "Manifest-Version: 1.0\n"
            f"X-ERP-Release-Id: {identity['releaseId']}\n"
            f"X-ERP-Git-Commit: {identity['gitCommit']}\n"
            f"X-ERP-Build-Time: {identity['buildTime']}\n\n"
        )
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", manifest)
            archive.writestr("META-INF/erp-release.json", json.dumps(identity))
        self.write_fixture_checksums(root)

    @staticmethod
    def write_fixture_checksums(root: Path):
        checksum = root / "provenance/SHA256SUMS"
        records = []
        for path in sorted(item for item in root.rglob("*") if item.is_file()):
            relative = path.relative_to(root).as_posix()
            if relative != "provenance/SHA256SUMS":
                records.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {relative}\n")
        checksum.write_text("".join(records), encoding="utf-8")

    def run_rendered_helper(self, name: str, *arguments):
        return subprocess.run(
            [sys.executable, "-c", rendered_helper(name), *map(str, arguments)],
            text=True,
            capture_output=True,
        )

    def test_remote_release_verifier_executes_canonical_checksum_and_identity_contract(self):
        identity = self.RELEASE_IDENTITY
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write_remote_release_fixture(root)
            result = self.run_rendered_helper(
                "render_remote_release_verifier",
                root,
                identity["releaseId"],
                identity["gitCommit"],
                identity["buildTime"],
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("release identity and checksums verified", result.stdout)

            frontend_info = root / "docker/erp/ui/release-info.json"
            frontend_info.write_text("{}", encoding="utf-8")
            tampered = self.run_rendered_helper(
                "render_remote_release_verifier",
                root,
                identity["releaseId"],
                identity["gitCommit"],
                identity["buildTime"],
            )
            self.assertNotEqual(0, tampered.returncode)
            self.assertIn("checksum mismatch", tampered.stderr)

            self.write_fixture_checksums(root)
            forged = self.run_rendered_helper(
                "render_remote_release_verifier",
                root,
                identity["releaseId"],
                identity["gitCommit"],
                identity["buildTime"],
            )
            self.assertNotEqual(0, forged.returncode)
            self.assertIn("remote frontend identity differs", forged.stderr)

    def test_remote_env_stamper_handles_missing_final_newline_and_rejects_duplicates(self):
        identity = self.RELEASE_IDENTITY
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            dotenv = root / ".env"
            dotenv.write_text("KEEP=value", encoding="utf-8")
            arguments = (
                dotenv,
                identity["releaseId"],
                identity["gitCommit"],
                identity["buildTime"],
            )
            first = self.run_rendered_helper(
                "render_remote_release_env_stamper", *arguments
            )
            self.assertEqual(0, first.returncode, first.stderr)
            expected_lines = [
                "KEEP=value",
                f"RELEASE_ID={identity['releaseId']}",
                f"GIT_COMMIT={identity['gitCommit']}",
                f"BUILD_TIME={identity['buildTime']}",
            ]
            self.assertEqual(expected_lines, dotenv.read_text(encoding="utf-8").splitlines())

            second = self.run_rendered_helper(
                "render_remote_release_env_stamper", *arguments
            )
            self.assertEqual(0, second.returncode, second.stderr)
            lines = dotenv.read_text(encoding="utf-8").splitlines()
            for key in ("RELEASE_ID", "GIT_COMMIT", "BUILD_TIME"):
                self.assertEqual(1, sum(line.startswith(key + "=") for line in lines))

            duplicate = root / "duplicate.env"
            original = "RELEASE_ID=first\nRELEASE_ID=second\n"
            duplicate.write_text(original, encoding="utf-8")
            rejected = self.run_rendered_helper(
                "render_remote_release_env_stamper",
                duplicate,
                identity["releaseId"],
                identity["gitCommit"],
                identity["buildTime"],
            )
            self.assertNotEqual(0, rejected.returncode)
            self.assertIn("duplicate release identity key", rejected.stderr)
            self.assertEqual(original, duplicate.read_text(encoding="utf-8"))

    def test_host_patch_is_retired_before_credentials_or_external_writes(self):
        tree = ast.parse(HELPER, filename="aliyun_ecs_deploy_helper.py")
        function = next(
            node
            for node in tree.body
            if isinstance(node, ast.FunctionDef)
            and node.name == "deploy_host_patch"
        )
        self.assertIsInstance(
            function.body[0],
            ast.Raise,
            "the retired patch entrypoint must fail before any credential read or write",
        )
        message = ast.unparse(function.body[0])
        self.assertIn("deploy-host-patch is retired", message)
        self.assertIn("use deploy-host", message)
        self.assertEqual(1, len(function.body), "retired patch code must not remain reachable or dead")
        parser_source = HELPER[HELPER.index("def main():") :]
        retired_parser = parser_source[
            parser_source.index('sub.add_parser(\n        "deploy-host-patch"') :
            parser_source.index("p_deploy_host_patch.set_defaults", parser_source.index('sub.add_parser(\n        "deploy-host-patch"'))
        ]
        self.assertNotIn("--frontend-only", retired_parser)
        self.assertNotIn("add_release_migration_arguments", retired_parser)

    def test_versioned_host_runner_extends_only_new_services(self):
        self.assertIn("erp/visual/monitor/jar/erp-visual-monitor.jar", HOST_RUNNER)
        self.assertIn("erp/modules/approval/jar/erp-modules-approval.jar", HOST_RUNNER)
        self.assertIn('exec "$LEGACY_RUNNER" "$@"', HOST_RUNNER)
        self.assertNotRegex(HOST_RUNNER, r"(?m)^\s*(?:source|\.)\s+.*\.env")
        self.assertIn('export "$key=$value"', HOST_RUNNER)
        self.assertIn(
            'ERP_JAVA_BIN="${ERP_JAVA_BIN:-/www/server/java/jdk-17.0.8/bin/java}"',
            HOST_RUNNER,
        )
        self.assertIn('COMMAND=("$ERP_JAVA_BIN")', HOST_RUNNER)

    def test_runtime_database_name_is_host_configurable(self):
        placeholder_count = 0
        for relative_path in RUNTIME_DB_CONFIGS:
            source = (ROOT / relative_path).read_text(encoding="utf-8")
            self.assertIn(
                "${MYSQL_DATABASE:BossERP_NEW}",
                source,
                relative_path,
            )
            self.assertNotRegex(source, r"/BossERP_NEW\?", relative_path)
            placeholder_count += source.count("${MYSQL_DATABASE:BossERP_NEW}")
        self.assertEqual(10, placeholder_count)
        self.assertIn("MYSQL_DATABASE=BossERP_NEW", ENV_EXAMPLE)
        self.assertIn(
            "MYSQL_DATABASE: ${MYSQL_DATABASE:-BossERP_NEW}",
            ECS_COMPOSE,
        )
        self.assertIn(
            "host .env must contain exactly one safe MYSQL_DATABASE value",
            HELPER,
        )

    def test_host_runner_delegates_existing_services_unchanged(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            runner = self.make_host_runner_fixture(root)
            legacy = root / "run-erp-service-legacy.sh"
            legacy.write_text(
                "#!/usr/bin/env bash\nprintf 'legacy:%s\\n' \"$*\"\n",
                encoding="utf-8",
            )
            legacy.chmod(legacy.stat().st_mode | stat.S_IXUSR)
            (root / ".env").write_text("SPRING_PROFILE=local\n", encoding="utf-8")
            result = subprocess.run(
                [str(runner), "gateway", "--sample"],
                check=True,
                capture_output=True,
                text=True,
            )
            self.assertEqual("legacy:gateway --sample", (root.resolve()/"data/erp-new-data/logs/gateway/stdout.log").read_text().strip())

    def test_host_runner_does_not_evaluate_dotenv_shell_syntax(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            runner = self.make_host_runner_fixture(root)
            marker = root / "dotenv-command-ran"
            (root / ".env").write_text(
                "SPRING_PROFILE=local\n"
                f"UNTRUSTED=$(touch {marker})\n",
                encoding="utf-8",
            )
            jar = root / "erp/modules/approval/jar/erp-modules-approval.jar"
            jar.parent.mkdir(parents=True)
            jar.write_bytes(b"jar")
            java = root / "java"
            java.write_text(
                "#!/usr/bin/env bash\nprintf 'java:%s\\n' \"$*\"\nprintf 'env:%s\\n' \"$UNTRUSTED\"\n",
                encoding="utf-8",
            )
            java.chmod(java.stat().st_mode | stat.S_IXUSR)
            env = os.environ.copy()
            env["PATH"] = f"{root}:{env['PATH']}"
            result = subprocess.run(
                [str(runner), "approval"],
                check=True,
                capture_output=True,
                text=True,
                env=env,
            )
            output = (root.resolve()/"data/erp-new-data/logs/approval/stdout.log").read_text()
            self.assertIn("--spring.profiles.active=local", output)
            self.assertIn(f"env:$(touch {marker})", output)
            self.assertFalse(marker.exists())

    def test_host_runner_rejects_storage_service_with_wrong_common_root(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            runner = self.make_host_runner_fixture(root)
            legacy = root / "run-erp-service-legacy.sh"
            legacy.write_text("#!/usr/bin/env bash\nexit 99\n", encoding="utf-8")
            legacy.chmod(legacy.stat().st_mode | stat.S_IXUSR)
            (root / ".env").write_text(
                "ERP_UPLOAD_ROOT=/opt/erp-new-data/uploadPath\n"
                "FILE_PATH=/opt/erp-new-data/uploadPath\n",
                encoding="utf-8",
            )
            result = subprocess.run(
                [str(runner), "oa"],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(65, result.returncode)
            self.assertIn("ERP_UPLOAD_ROOT must equal", result.stderr)

    def test_host_runner_missing_data_mount_never_starts_or_creates_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            runner = self.make_host_runner_fixture(root)
            (root / ".env").write_text("SPRING_PROFILE=local\n")
            result = subprocess.run([str(runner), "gateway"], env={**os.environ, "TEST_MOUNT_TARGET": "/"}, capture_output=True, text=True)
            self.assertEqual(73, result.returncode)
            self.assertIn("required project path", result.stderr)
            self.assertFalse((root / "data/erp-new-data/logs/gateway/stdout.log").exists())

    def test_host_runner_rejects_cache_symlink_outside_data(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            runner = self.make_host_runner_fixture(root)
            (root / ".env").write_text("SPRING_PROFILE=local\n")
            outside = root / "outside-cache"
            outside.mkdir()
            cache = root / "data/erp-new-data/cache/gateway"
            cache.rmdir()
            cache.symlink_to(outside)
            result = subprocess.run([str(runner), "gateway"], capture_output=True, text=True)
            self.assertEqual(73, result.returncode)
            self.assertEqual([], list(outside.iterdir()))

    def test_host_runner_rejects_missing_temp_directory_without_fallback(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            runner = self.make_host_runner_fixture(root)
            (root / ".env").write_text("SPRING_PROFILE=local\n")
            target = root / "data/erp-new-data/tmp/gateway"
            target.rmdir()
            result = subprocess.run([str(runner), "gateway"], capture_output=True, text=True)
            self.assertEqual(73, result.returncode)
            self.assertFalse(target.exists())

    def test_host_release_uses_persistent_upload_root(self):
        self.assertIn('HOST_SHARED_UPLOAD_ROOT = "/data/erp-new-data/uploadPath"', HELPER)
        self.assertIn(
            'HOST_SIGN_PACKAGE_STORAGE_ROOT = f"{HOST_SHARED_UPLOAD_ROOT}/private/sign-package"',
            HELPER,
        )
        self.assertIn("HOST_ATTENDANCE_STORAGE_ROOT", HELPER)
        self.assertIn("HOST_REIMBURSEMENT_STORAGE_ROOT", HELPER)
        self.assertIn("HOST_DRIVE_LOCAL_PATH", HELPER)
        self.assertIn("ensure_shared_upload_source", HELPER)
        self.assertIn("link_shared_upload_target", HELPER)
        self.assertIn("repair_previous_upload_link", HELPER)
        self.assertIn("upload=$upload_status", HELPER)
        self.assertIn("uploadPath-migration-backup", HELPER)
        self.assertIn('diff -qr "$legacy" "$SHARED_UPLOAD_ROOT"', HELPER)
        self.assertNotIn(
            'cp -a "$PREV_TARGET/erp/uploadPath" "$NEW_DOCKER/erp/uploadPath"',
            HELPER,
        )
        contract = host_runtime_contract()
        self.assertIn("host .env must pin $name to $expected", contract)
        self.assertIn("unit_property_has_word", contract)
        self.assertIn('--property="$property"', contract)
        self.assertIn(
            'unit_property_has_word "$unit" RequiresMountsFor "$root"', contract
        )
        self.assertIn("--property=ExecStartPre", contract)
        self.assertIn('split($0, fields, ";")', contract)
        self.assertIn("if (field == expected) found = 1", contract)
        self.assertNotIn(
            'grep -Fq "argv[]=/usr/bin/test $mode $path"', contract
        )
        self.assertNotRegex(
            contract,
            r"systemctl cat[^\n]*\|\s*grep[^\n]*(?:RequiresMountsFor|ExecStartPre)",
        )
        self.assertIn("verify_host_storage_runtime", contract)
        self.assertIn('for relative in uploadPath erp/uploadPath', contract)
        commented_only_contract = (
            "set -euo pipefail\n"
            + contract
            + "\n"
            + 'systemctl() {\n'
            + '  if [[ "$1" == cat ]]; then\n'
            + '    printf \x27# RequiresMountsFor=%s\\n# ExecStartPre=/usr/bin/test -d %s\\n# ExecStartPre=/usr/bin/test -w %s\\n\x27 "$TEST_ROOT" "$TEST_ROOT" "$TEST_ROOT"\n'
            + '    return 0\n'
            + '  fi\n'
            + '  case "$3" in\n'
            + '    --property=RequiresMountsFor) printf \x27%s\\n\x27 "${EFFECTIVE_REQUIRES:-}" ;;\n'
            + '    --property=ExecStartPre) printf \x27%s\\n\x27 "${EFFECTIVE_PRE:-}" ;;\n'
            + '  esac\n'
            + '}\n'
            + 'if verify_storage_unit_contract oa "$TEST_ROOT"; then exit 91; fi\n'
            + 'EFFECTIVE_REQUIRES="$TEST_ROOT"\n'
            + 'EFFECTIVE_PRE="{ path=/usr/bin/test ; argv[]=/usr/bin/test -d $TEST_ROOT-wrong ; } { path=/usr/bin/test ; argv[]=/usr/bin/test -w $TEST_ROOT-wrong ; }"\n'
            + 'if verify_storage_unit_contract oa "$TEST_ROOT"; then exit 92; fi\n'
            + 'EFFECTIVE_PRE="{ path=/usr/bin/test ; argv[]=/usr/bin/test    -d   $TEST_ROOT ; } { path=/usr/bin/test ; argv[]=/usr/bin/test  -w    $TEST_ROOT ; }"\n'
            + 'verify_storage_unit_contract oa "$TEST_ROOT"\n'
        )
        env = os.environ.copy()
        env["TEST_ROOT"] = "/data/erp-new-data/uploadPath"
        subprocess.run(["bash", "-c", commented_only_contract], env=env, check=True)

    def test_rollback_repairs_partial_upload_migration(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            previous = root / "previous"
            (previous / "erp").mkdir(parents=True)
            shared = root / "shared"
            shared.mkdir()
            (shared / "after-copy.txt").write_text("durable", encoding="utf-8")
            backup = root / "backup"
            backup.mkdir()
            candidate = previous / "erp/uploadPath.shared-link-test"
            candidate.symlink_to(shared, target_is_directory=True)
            script = (
                "set -euo pipefail\n"
                'PREV_TARGET="$TEST_PREVIOUS"\n'
                'SHARED_UPLOAD_ROOT="$TEST_SHARED"\n'
                + host_runtime_contract()
                + "\n"
                + 'UPLOAD_MIGRATION_BACKUP="$TEST_BACKUP"\n'
                + 'UPLOAD_MIGRATION_LINK_CANDIDATE="$TEST_CANDIDATE"\n'
                + "repair_previous_upload_link\n"
                + 'test "$(readlink -f "$PREV_TARGET/erp/uploadPath")" = "$(readlink -f "$SHARED_UPLOAD_ROOT")"\n'
                + 'test ! -e "$UPLOAD_MIGRATION_LINK_CANDIDATE"\n'
            )
            env = os.environ.copy()
            env.update(
                {
                    "TEST_PREVIOUS": str(previous),
                    "TEST_SHARED": str(shared),
                    "TEST_BACKUP": str(backup),
                    "TEST_CANDIDATE": str(candidate),
                }
            )
            subprocess.run(["bash", "-c", script], check=True, env=env)

    def test_explicit_exit_runs_rollback_guard(self):
        with tempfile.TemporaryDirectory() as tmp:
            marker = Path(tmp) / "rollback-code"
            contract = host_runtime_contract()
            rollback_start = contract.index("rollback() {")
            rollback_end = contract.index("\n'''", rollback_start) if "\n'''" in contract else len(contract)
            rollback_functions = contract[rollback_start:rollback_end]
            script = (
                "set -euo pipefail\n"
                + rollback_functions
                + "\n"
                + 'rollback() { local code="${1:-$?}"; trap - ERR EXIT; printf "%s" "$code" > "$ROLLBACK_MARKER"; exit "$code"; }\n'
                + "trap 'rollback \"$?\"' ERR\n"
                + "trap 'rollback_on_exit \"$?\"' EXIT\n"
                + "exit 63\n"
            )
            env = os.environ.copy()
            env["ROLLBACK_MARKER"] = str(marker)
            result = subprocess.run(["bash", "-c", script], env=env)
            self.assertEqual(63, result.returncode)
            self.assertEqual("63", marker.read_text(encoding="utf-8"))

    def test_rollback_reloads_nginx_and_verifies_public_endpoints(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            previous = root / "previous"
            previous.mkdir()
            current = root / "current"
            marker = root / "rollback-steps"
            script = (
                "set -u\n"
                + host_runtime_contract()
                + "\n"
                + 'PREV_TARGET="$TEST_PREVIOUS"\n'
                + 'CURRENT_LINK="$TEST_CURRENT"\n'
                + 'SHARED_UPLOAD_ROOT="$TEST_SHARED"\n'
                + 'SERVICES="gateway"\n'
                + 'PREVIOUS_ACTIVE_SERVICES="gateway"\n'
                + 'START_SERVICES="gateway"\n'
                + 'SERVICE_PORTS="gateway:8080"\n'
                + 'change_service_state() { return 0; }\n'
                + 'repair_previous_upload_link() { return 0; }\n'
                + 'systemctl() { return 0; }\n'
                + 'start_services() { printf "restart\\n" >>"$TEST_MARKER"; return 0; }\n'
                + 'wait_for_host_readiness() { printf "ready\\n" >>"$TEST_MARKER"; return 0; }\n'
                + 'verify_host_storage_runtime() { printf "storage\\n" >>"$TEST_MARKER"; return 0; }\n'
                + 'reload_nginx() { printf "nginx\\n" >>"$TEST_MARKER"; return 0; }\n'
                + 'curl() { printf "curl:%s\\n" "${@: -1}" >>"$TEST_MARKER"; printf \x27{"code":200}\x27; return 0; }\n'
                + 'rollback 57\n'
            )
            env = os.environ.copy()
            env.update(
                {
                    "TEST_PREVIOUS": str(previous),
                    "TEST_CURRENT": str(current),
                    "TEST_SHARED": str(root / "shared"),
                    "TEST_MARKER": str(marker),
                }
            )
            result = subprocess.run(
                ["bash", "-c", script],
                env=env,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            self.assertEqual(57, result.returncode)
            self.assertEqual(
                [
                    "restart",
                    "ready",
                    "storage",
                    "nginx",
                    "curl:http://127.0.0.1/",
                    "curl:http://127.0.0.1/prod-api/code",
                ],
                marker.read_text(encoding="utf-8").splitlines(),
            )
            self.assertIn("ROLLBACK_OK", result.stderr)
            self.assertNotIn("ROLLBACK_INCOMPLETE", result.stderr)

    def test_nginx_reload_fails_closed_when_config_validation_fails(self):
        script = (
            "set +e\n"
            + host_runtime_contract()
            + "\n"
            + 'nginx() { test "${1:-}" != "-t"; }\n'
            + 'systemctl() { return 0; }\n'
            + 'kill() { return 0; }\n'
            + 'reload_nginx\n'
        )
        result = subprocess.run(
            ["bash", "-c", script],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        self.assertNotEqual(0, result.returncode)

    def test_business_health_rejects_http_200_with_code_500(self):
        contract = host_runtime_contract()
        script = (
            "set -euo pipefail\n"
            + contract
            + "\n"
            + 'curl() { printf "%s" "$HTTP_BODY"; }\n'
            + 'business_json_ready http://example.invalid example.invalid\n'
        )
        env = os.environ.copy()
        env["HTTP_BODY"] = '{"code":200,"data":{}}'
        subprocess.run(["bash", "-c", script], env=env, check=True)
        env["HTTP_BODY"] = '{"code":500,"msg":"missing file"}'
        result = subprocess.run(["bash", "-c", script], env=env, check=False)
        self.assertNotEqual(0, result.returncode)

    def test_deployment_refuses_degraded_previous_baseline(self):
        baseline = "gateway auth system job oa inventory file"
        script = (
            "set -euo pipefail\n"
            'SERVICES="gateway auth monitor system job oa inventory file approval"\n'
            f'REQUIRED_PREVIOUS_SERVICES="{baseline}"\n'
            + host_runtime_contract()
            + "\n"
            + 'systemctl() {\n'
            + '  local unit="${@: -1}"\n'
            + '  local service="${unit#erp-new@}"\n'
            + '  service="${service%.service}"\n'
            + '  [[ " $ACTIVE_SERVICES " == *" $service "* ]]\n'
            + '}\n'
            + 'ACTIVE_SERVICES="gateway auth system job oa inventory"\n'
            + 'if collect_previous_active_services; then exit 70; fi\n'
            + f'ACTIVE_SERVICES="{baseline}"\n'
            + 'collect_previous_active_services\n'
            + f'test "$PREVIOUS_ACTIVE_SERVICES" = "{baseline}"\n'
        )
        subprocess.run(["bash", "-c", script], check=True)

    def test_ecs_compose_has_healthchecks_and_complete_services(self):
        compose_names = {
            "gateway": "erp-gateway",
            "auth": "erp-auth",
            "monitor": "erp-visual-monitor",
            "system": "erp-modules-system",
            "job": "erp-modules-job",
            "oa": "erp-modules-oa",
            "inventory": "erp-modules-inventory",
            "file": "erp-modules-file",
            "approval": "erp-modules-approval",
        }
        for service, compose_name in compose_names.items():
            block = compose_service_block(compose_name)
            self.assertIn("healthcheck:", block, service)
            self.assertIn(str(SERVICES[service]), block, service)
        file_block = compose_service_block("erp-modules-file")
        self.assertIn("${ERP_UPLOAD_ROOT:?set ERP_UPLOAD_ROOT in .env}", file_block)
        self.assertIn("ERP_UPLOAD_ROOT=/data/erp-new-data/uploadPath", ENV_EXAMPLE)
        expected_container_root = "/data/erp-new-data/uploadPath"
        for compose_source in (ECS_COMPOSE, STANDARD_COMPOSE):
            for name, suffix in (
                ("ERP_UPLOAD_ROOT", ""),
                ("FILE_PATH", ""),
                ("SIGN_PACKAGE_STORAGE_ROOT", "/private/sign-package"),
                ("OA_ATTENDANCE_STORAGE_ROOT", "/private/attendance"),
                ("OA_REIMBURSEMENT_STORAGE_ROOT", "/private/reimbursement"),
                ("DRIVE_LOCAL_PATH", "/private/drive"),
            ):
                self.assertIn(f"{name}: {expected_container_root}{suffix}", compose_source)
            self.assertEqual(
                2,
                compose_source.count(
                    "${ERP_UPLOAD_ROOT:?set ERP_UPLOAD_ROOT in .env}:/data/erp-new-data/uploadPath"
                ),
            )
        self.assertIn(
            "SIGN_PACKAGE_STORAGE_ROOT=/data/erp-new-data/uploadPath/private/sign-package",
            ENV_EXAMPLE,
        )

    def test_compose_deploy_waits_and_checks_real_http_endpoints(self):
        self.assertIn("config --quiet", ECS_DEPLOY)
        self.assertIn("--wait --wait-timeout", ECS_DEPLOY)
        self.assertIn("ERP_UPLOAD_ROOT", ECS_DEPLOY)
        self.assertIn("ERP_UPLOAD_ROOT is not mounted on /data", ECS_DEPLOY)
        self.assertIn("/actuator/health", ECS_DEPLOY)
        self.assertIn("/prod-api/code", ECS_DEPLOY)
        self.assertNotRegex(ECS_DEPLOY, r"curl[^\n]*\|\|\s*true")
        for service in (
            "erp-gateway",
            "erp-auth",
            "erp-visual-monitor",
            "erp-modules-system",
            "erp-modules-job",
            "erp-modules-oa",
            "erp-modules-inventory",
            "erp-modules-file",
            "erp-modules-approval",
        ):
            self.assertIn(service, DOCKER_DEPLOY)
        for service, port in SERVICES.items():
            self.assertIn(f"{service}:{port}", ECS_DEPLOY)

    def test_remote_verify_checks_process_port_health_and_shared_upload(self):
        for service, port in SERVICES.items():
            self.assertIn(f"{service}:{port}", REMOTE_VERIFY)
            self.assertIn(f'"{service}": ("jar",', REMOTE_VERIFY)
        self.assertIn('"frontend": ("directory",', REMOTE_VERIFY)
        self.assertIn('set(by_id) != set(expected_artifacts)', REMOTE_VERIFY)
        self.assertIn('release/new-business-release-20260714.json', REMOTE_VERIFY)
        self.assertIn("/actuator/health", REMOTE_VERIFY)
        self.assertNotIn('if [[ "$service" == monitor ]]', REMOTE_VERIFY)
        self.assertIn("/data/erp-new-data/uploadPath", REMOTE_VERIFY)
        self.assertIn("/proc/$pid/environ", REMOTE_VERIFY)
        self.assertIn("code == 200", REMOTE_VERIFY)
        self.assertIn('--property="$property"', REMOTE_VERIFY)
        self.assertIn(
            'unit_property_has_word "$unit" RequiresMountsFor "$root"', REMOTE_VERIFY
        )
        self.assertIn("--property=ExecStartPre", REMOTE_VERIFY)
        self.assertIn('split($0, fields, ";")', REMOTE_VERIFY)
        self.assertIn("if (field == expected) found = 1", REMOTE_VERIFY)
        self.assertNotIn(
            'grep -Fq "argv[]=/usr/bin/test $mode $path"', REMOTE_VERIFY
        )
        self.assertNotRegex(
            REMOTE_VERIFY,
            r"systemctl cat[^\n]*\|\s*grep[^\n]*(?:RequiresMountsFor|ExecStartPre)",
        )
        self.assertIn('cmp -s "$ACTIVE_FRONTEND_INDEX" "$SERVED_FRONTEND_INDEX"', REMOTE_VERIFY)
        self.assertIn(
            "frontend artifact mismatch: served index.html differs from active release",
            REMOTE_VERIFY,
        )
        self.assertIn("HTTP success with a failed business code", REMOTE_VERIFY)

        storage_contract = REMOTE_VERIFY[
            REMOTE_VERIFY.index("unit_property_has_word() {") : REMOTE_VERIFY.index(
                "\nfor entry in $SERVICE_PORTS;"
            )
        ]
        wrong_suffix_contract = (
            "set -euo pipefail\n"
            + storage_contract
            + "\n"
            + 'systemctl() {\n'
            + '  case "$3" in\n'
            + '    --property=RequiresMountsFor) printf \x27%s\\n\x27 "$TEST_ROOT" ;;\n'
            + '    --property=ExecStartPre) printf \x27%s\\n\x27 "$EFFECTIVE_PRE" ;;\n'
            + '  esac\n'
            + '}\n'
            + 'EFFECTIVE_PRE="{ path=/usr/bin/test ; argv[]=/usr/bin/test -d $TEST_ROOT-wrong ; } { path=/usr/bin/test ; argv[]=/usr/bin/test -w $TEST_ROOT-wrong ; }"\n'
            + 'if storage_unit_contract_ready oa "$TEST_ROOT"; then exit 93; fi\n'
            + 'EFFECTIVE_PRE="{ path=/usr/bin/test ; argv[]=/usr/bin/test   -d   $TEST_ROOT ; } { path=/usr/bin/test ; argv[]=/usr/bin/test -w  $TEST_ROOT ; }"\n'
            + 'storage_unit_contract_ready oa "$TEST_ROOT"\n'
        )
        env = os.environ.copy()
        env["TEST_ROOT"] = "/data/erp-new-data/uploadPath"
        subprocess.run(["bash", "-c", wrong_suffix_contract], env=env, check=True)

    def test_runbook_documents_the_fail_closed_contract(self):
        self.assertIn("9 个 Java 服务", RUNBOOK)
        self.assertIn("/data/erp-new-data/uploadPath", RUNBOOK)
        self.assertIn("逐服务 readiness", RUNBOOK)
        self.assertIn("ERP_UPLOAD_ROOT=/data/erp-new-data/uploadPath", RUNBOOK)
        self.assertIn("HTTP 200 携带 `code=500` 必须判定失败", RUNBOOK)
        self.assertIn("`deploy-host` 不负责合并两棵分叉文件树", RUNBOOK)
        self.assertIn("remote_repair_common_upload_storage_20260826.sh", RUNBOOK)


if __name__ == "__main__":
    unittest.main()
