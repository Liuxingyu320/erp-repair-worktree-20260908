#!/usr/bin/env python3
"""Regression tests for the production Docker runtime contract."""

import json
import os
import re
import stat
import subprocess
import unittest
from pathlib import Path


DOCKER_DIR = Path(__file__).resolve().parents[1]
ECS_COMPOSE = (DOCKER_DIR / "docker-compose.ecs-host.yml").read_text(encoding="utf-8")
DEFAULT_COMPOSE = (DOCKER_DIR / "docker-compose.yml").read_text(encoding="utf-8")
DEPLOY = (DOCKER_DIR / "deploy-ecs.sh").read_text(encoding="utf-8")
PREFLIGHT = (DOCKER_DIR / "preflight-ecs.sh").read_text(encoding="utf-8")
HEALTHCHECK = (DOCKER_DIR / "healthcheck-ecs.sh").read_text(encoding="utf-8")
ENV_EXAMPLE = (DOCKER_DIR / ".env.example").read_text(encoding="utf-8")
NACOS_PREFLIGHT = (DOCKER_DIR / "nacos-production-preflight.py").read_text(
    encoding="utf-8"
)

PRODUCTION_SERVICES = {
    "erp-mysql",
    "erp-redis",
    "erp-gateway",
    "erp-auth",
    "erp-modules-system",
    "erp-modules-job",
    "erp-modules-oa",
    "erp-modules-inventory",
    "erp-modules-approval",
    "erp-modules-file",
    "erp-visual-monitor",
    "erp-nginx",
}
DEFAULT_SERVICES = PRODUCTION_SERVICES | {"erp-nacos"}
JAVA_SERVICES = {
    "erp-gateway",
    "erp-auth",
    "erp-modules-system",
    "erp-modules-job",
    "erp-modules-oa",
    "erp-modules-inventory",
    "erp-modules-approval",
    "erp-modules-file",
    "erp-visual-monitor",
}
RELEASE_ENV = {
    "NACOS_AUTH_TOKEN": "production-contract-token-with-more-than-32-bytes",
    "NACOS_AUTH_IDENTITY_KEY": "production-contract-key",
    "NACOS_AUTH_IDENTITY_VALUE": "production-contract-value",
    "NACOS_DB_USER": "nacos_app",
    "NACOS_DB_PASSWORD": "nacos-contract-password",
    "NACOS_CLIENT_USERNAME": "erp_prod_client",
    "NACOS_CLIENT_PASSWORD": "nacos-client-contract-password",
    "ECS_NACOS_SERVER_ADDR": "127.0.0.1:8848",
    "MYSQL_ROOT_PASSWORD": "mysql-root-contract-password",
    "MYSQL_DATABASE": "BossERP_NEW",
    "MYSQL_USERNAME": "erp_app",
    "MYSQL_PASSWORD": "mysql-app-contract-password",
    "SPRING_PROFILES_ACTIVE": "prod",
    "ERP_JWT_SECRET": "jwt-contract-secret-with-more-than-32-bytes",
    "REDIS_PASSWORD": "redis-contract-password",
    "APP_DATASOURCE_URL": (
        "jdbc:mysql://erp-mysql:3306/BossERP_NEW"
        "?useUnicode=true&characterEncoding=utf8&useSSL=false"
    ),
    "ECS_APP_DATASOURCE_URL": (
        "jdbc:mysql://127.0.0.1:3306/BossERP_NEW"
        "?useUnicode=true&characterEncoding=utf8&useSSL=false"
    ),
    "APP_DATASOURCE_USERNAME": "erp_app",
    "APP_DATASOURCE_PASSWORD": "mysql-app-contract-password",
    "ERP_UPLOAD_ROOT": "/data/erp-new-data/uploadPath",
    "FILE_PATH": "/data/erp-new-data/uploadPath",
    "SIGN_PACKAGE_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/sign-package",
    "OA_ATTENDANCE_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/attendance",
    "OA_REIMBURSEMENT_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/reimbursement",
    "DRIVE_LOCAL_PATH": "/data/erp-new-data/uploadPath/private/drive",
    "FILE_DOMAIN": "https://erp.example.invalid/prod-api",
    "REFERER_ENABLED": "true",
    "REFERER_ALLOWED_DOMAINS": "erp.example.invalid",
    "RELEASE_ID": "erp-20260728-contract",
    "GIT_COMMIT": "0123456789abcdef0123456789abcdef01234567",
    "BUILD_TIME": "2026-07-28T08:00:00Z",
}


def service_names(compose: str) -> set[str]:
    return set(re.findall(r"(?m)^  (erp-[a-z0-9-]+):\n", compose))


def service_block(compose: str, name: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(name)}:\n(?P<body>.*?)(?=^  erp-[a-z0-9-]+:\n|\Z)",
        compose,
    )
    if not match:
        raise AssertionError(f"missing service block: {name}")
    return match.group("body")


def resolved_compose(path: Path) -> dict:
    env = os.environ.copy()
    env.update(RELEASE_ENV)
    result = subprocess.run(
        [
            "docker",
            "compose",
            "--project-directory",
            str(DOCKER_DIR),
            "-f",
            str(path),
            "config",
            "--format",
            "json",
        ],
        cwd=DOCKER_DIR,
        env=env,
        check=True,
        capture_output=True,
        text=True,
    )
    return json.loads(result.stdout)


class ProductionRuntimeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.ecs_config = resolved_compose(DOCKER_DIR / "docker-compose.ecs-host.yml")
        cls.default_config = resolved_compose(DOCKER_DIR / "docker-compose.yml")

    def test_ecs_has_exactly_12_self_recovering_healthy_services(self):
        self.assertEqual(PRODUCTION_SERVICES, service_names(ECS_COMPOSE))
        for name in PRODUCTION_SERVICES:
            block = service_block(ECS_COMPOSE, name)
            self.assertIn("restart: unless-stopped", block, name)
            self.assertRegex(block, r"(?m)^    stop_grace_period: [1-9][0-9]*s$", name)
            self.assertIn("healthcheck:", block, name)
            self.assertIn("timeout:", block, name)
            self.assertIn("retries:", block, name)

    def test_default_compose_uses_the_same_runtime_safety_baseline(self):
        self.assertEqual(DEFAULT_SERVICES, service_names(DEFAULT_COMPOSE))
        for name in DEFAULT_SERVICES:
            block = service_block(DEFAULT_COMPOSE, name)
            self.assertIn("restart: unless-stopped", block, name)
            self.assertIn("stop_grace_period:", block, name)
            self.assertIn("healthcheck:", block, name)

    def test_dependency_order_waits_for_healthy_prerequisites(self):
        required = {
            "erp-auth": {"erp-redis", "erp-modules-system"},
            "erp-gateway": {"erp-redis"},
            "erp-modules-system": {"erp-mysql", "erp-redis"},
            "erp-modules-job": {"erp-mysql", "erp-redis"},
            "erp-modules-oa": {"erp-mysql", "erp-redis"},
            "erp-modules-inventory": {"erp-mysql", "erp-redis"},
            "erp-modules-approval": {"erp-mysql", "erp-redis"},
            "erp-modules-file": {"erp-mysql", "erp-redis"},
            "erp-nginx": {"erp-gateway"},
        }
        for name, dependencies in required.items():
            block = service_block(ECS_COMPOSE, name)
            for dependency in dependencies:
                self.assertRegex(
                    block,
                    rf"(?ms)^      {re.escape(dependency)}:\n        condition: service_healthy$",
                    f"{name} must wait for {dependency}",
                )

    def test_each_java_service_resolves_the_immutable_release_identity(self):
        expected = {
            "SPRING_PROFILES_ACTIVE": "prod",
            "RELEASE_ID": RELEASE_ENV["RELEASE_ID"],
            "GIT_COMMIT": RELEASE_ENV["GIT_COMMIT"],
            "BUILD_TIME": RELEASE_ENV["BUILD_TIME"],
        }
        for document in (self.ecs_config, self.default_config):
            for name in JAVA_SERVICES:
                environment = document["services"][name]["environment"]
                for key, value in expected.items():
                    self.assertEqual(value, environment.get(key), f"{name}: {key}")

    def test_production_profile_is_explicit_and_never_hardcoded_to_dev_or_local(self):
        expected_source = (
            "SPRING_PROFILES_ACTIVE: "
            "${SPRING_PROFILES_ACTIVE:?set SPRING_PROFILES_ACTIVE in .env}"
        )
        self.assertIn(expected_source, ECS_COMPOSE)
        self.assertIn(expected_source, DEFAULT_COMPOSE)
        self.assertNotRegex(
            ECS_COMPOSE + DEFAULT_COMPOSE,
            r"(?m)^\s*SPRING_PROFILES_ACTIVE:\s*(?:dev|local)\s*$",
        )
        self.assertRegex(ENV_EXAMPLE, r"(?m)^SPRING_PROFILES_ACTIVE=prod$")

    def test_ecs_services_without_nacos_have_complete_production_configuration(self):
        services = self.ecs_config["services"]
        for name in ("erp-modules-job", "erp-modules-file"):
            environment = services[name]["environment"]
            self.assertEqual(
                "false", environment.get("SPRING_CLOUD_NACOS_DISCOVERY_ENABLED"), name
            )
            self.assertEqual(
                "false", environment.get("SPRING_CLOUD_NACOS_CONFIG_ENABLED"), name
            )
            self.assertEqual("127.0.0.1", environment.get("SPRING_DATA_REDIS_HOST"), name)
            self.assertEqual(
                RELEASE_ENV["REDIS_PASSWORD"],
                environment.get("SPRING_DATA_REDIS_PASSWORD"),
                name,
            )

        for name, aliases in (("erp-modules-job", "com.erp.job.domain"),):
            environment = services[name]["environment"]
            self.assertEqual(
                RELEASE_ENV["ECS_APP_DATASOURCE_URL"],
                environment.get("SPRING_DATASOURCE_URL"),
                name,
            )
            self.assertEqual(
                RELEASE_ENV["APP_DATASOURCE_USERNAME"],
                environment.get("SPRING_DATASOURCE_USERNAME"),
                name,
            )
            self.assertEqual(
                RELEASE_ENV["APP_DATASOURCE_PASSWORD"],
                environment.get("SPRING_DATASOURCE_PASSWORD"),
                name,
            )
            self.assertEqual(aliases, environment.get("MYBATIS_TYPE_ALIASES_PACKAGE"), name)
            self.assertEqual(
                "classpath:mapper/**/*.xml",
                environment.get("MYBATIS_MAPPER_LOCATIONS"),
                name,
            )

        file_environment = services["erp-modules-file"]["environment"]
        self.assertEqual(
            RELEASE_ENV["ECS_APP_DATASOURCE_URL"],
            file_environment.get("SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL"),
        )
        self.assertEqual("/data/erp-new-data/uploadPath", file_environment.get("FILE_PATH"))
        self.assertEqual("/file", file_environment.get("FILE_PREFIX"))
        self.assertEqual(RELEASE_ENV["FILE_DOMAIN"], file_environment.get("FILE_DOMAIN"))
        self.assertEqual("true", file_environment.get("REFERER_ENABLED"))
        self.assertEqual("http://127.0.0.1:9000", file_environment.get("MINIO_URL"))
        self.assertEqual(
            RELEASE_ENV["REFERER_ALLOWED_DOMAINS"],
            file_environment.get("REFERER_ALLOWED_DOMAINS"),
        )

    def test_nacos_backed_services_require_exact_prod_data_ids(self):
        service_data_ids = {
            "erp-gateway": "erp-gateway-prod.yml",
            "erp-auth": "erp-auth-prod.yml",
            "erp-modules-system": "erp-system-prod.yml",
            "erp-modules-job": "erp-job-prod.yml",
            "erp-modules-oa": "erp-oa-prod.yml",
            "erp-modules-inventory": "erp-inventory-prod.yml",
            "erp-modules-approval": "erp-approval-prod.yml",
            "erp-modules-file": "erp-file-prod.yml",
            "erp-visual-monitor": "erp-monitor-prod.yml",
        }
        configurations = (
            (
                self.ecs_config,
                service_data_ids.keys()
                - {"erp-modules-job", "erp-modules-file"},
                RELEASE_ENV["ECS_NACOS_SERVER_ADDR"],
            ),
            (self.default_config, service_data_ids.keys(), "erp-nacos:8848"),
        )
        for document, names, expected_address in configurations:
            for name in names:
                environment = document["services"][name]["environment"]
                expected_import = (
                    "nacos:application-prod.yml,nacos:" + service_data_ids[name]
                )
                self.assertEqual(
                    expected_import, environment.get("SPRING_CONFIG_IMPORT"), name
                )
                self.assertNotIn("optional:", environment["SPRING_CONFIG_IMPORT"], name)
                self.assertEqual(
                    "true", environment.get("SPRING_CLOUD_NACOS_CONFIG_ENABLED"), name
                )
                self.assertEqual(
                    "true", environment.get("SPRING_CLOUD_NACOS_DISCOVERY_ENABLED"), name
                )
                self.assertEqual(
                    expected_address,
                    environment.get("SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR"),
                    name,
                )
                self.assertEqual(
                    RELEASE_ENV["NACOS_CLIENT_USERNAME"],
                    environment.get("SPRING_CLOUD_NACOS_CONFIG_USERNAME"),
                    name,
                )
                self.assertEqual(
                    RELEASE_ENV["NACOS_CLIENT_PASSWORD"],
                    environment.get("SPRING_CLOUD_NACOS_CONFIG_PASSWORD"),
                    name,
                )

    def test_application_services_never_receive_mysql_root_credentials(self):
        self.assertRegex(ENV_EXAMPLE, r"(?m)^MYSQL_USERNAME=erp_app$")
        self.assertNotRegex(ENV_EXAMPLE, r"(?m)^MYSQL_USERNAME=root$")
        for document, expected_url in (
            (self.ecs_config, RELEASE_ENV["ECS_APP_DATASOURCE_URL"]),
            (self.default_config, RELEASE_ENV["APP_DATASOURCE_URL"]),
        ):
            for name in JAVA_SERVICES:
                environment = document["services"][name]["environment"]
                self.assertEqual(
                    RELEASE_ENV["APP_DATASOURCE_USERNAME"],
                    environment.get("MYSQL_USERNAME"),
                    name,
                )
                self.assertNotEqual("root", environment.get("SPRING_DATASOURCE_USERNAME"))
                self.assertEqual(
                    expected_url, environment.get("SPRING_DATASOURCE_URL"), name
                )
                self.assertEqual(
                    RELEASE_ENV["APP_DATASOURCE_USERNAME"],
                    environment.get("SPRING_DATASOURCE_USERNAME"),
                    name,
                )
                self.assertEqual(
                    RELEASE_ENV["APP_DATASOURCE_USERNAME"],
                    environment.get(
                        "SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME"
                    ),
                    name,
                )

    def test_upload_storage_resolves_outside_both_release_directories(self):
        for document in (self.ecs_config, self.default_config):
            for service_name in ("erp-modules-oa", "erp-modules-file"):
                service = document["services"][service_name]
                upload = next(
                    item
                    for item in service["volumes"]
                    if item.get("target") == "/data/erp-new-data/uploadPath"
                )
                self.assertEqual(RELEASE_ENV["ERP_UPLOAD_ROOT"], upload.get("source"))
                self.assertEqual("bind", upload.get("type"))
                expected_environment = {
                    "ERP_UPLOAD_ROOT": "/data/erp-new-data/uploadPath",
                    "FILE_PATH": "/data/erp-new-data/uploadPath",
                    "SIGN_PACKAGE_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/sign-package",
                    "OA_ATTENDANCE_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/attendance",
                    "OA_REIMBURSEMENT_STORAGE_ROOT": "/data/erp-new-data/uploadPath/private/reimbursement",
                    "DRIVE_LOCAL_PATH": "/data/erp-new-data/uploadPath/private/drive",
                }
                for name, expected in expected_environment.items():
                    self.assertEqual(expected, service["environment"].get(name), f"{service_name}: {name}")

    def test_preflight_is_read_only_and_semantically_validates_compose(self):
        self.assertIn("config --quiet", PREFLIGHT)
        self.assertIn("config --format json", PREFLIGHT)
        self.assertIn("services=12 restart=12 healthcheck=12", PREFLIGHT)
        self.assertIn('python3 "$NACOS_PREFLIGHT" --env-file "$ENV_FILE"', PREFLIGHT)
        self.assertLess(
            PREFLIGHT.index('python3 "$NACOS_PREFLIGHT"'),
            PREFLIGHT.index("required_artifacts=("),
        )
        self.assertNotRegex(PREFLIGHT, r'"\$\{compose\[@\]\}"\s+(?:up|down|start|stop|restart|pull|build)\b')
        self.assertNotIn("docker restart", PREFLIGHT)
        self.assertNotIn("docker system prune", PREFLIGHT)
        self.assertIn("/nacos/v3/admin/core/state/readiness", NACOS_PREFLIGHT)
        self.assertIn("/nacos/v3/auth/user/login", NACOS_PREFLIGHT)
        self.assertIn("/nacos/v3/client/cs/config", NACOS_PREFLIGHT)
        for data_id in (
            "application-prod.yml",
            "erp-gateway-prod.yml",
            "erp-auth-prod.yml",
            "erp-system-prod.yml",
            "erp-job-prod.yml",
            "erp-oa-prod.yml",
            "erp-inventory-prod.yml",
            "erp-approval-prod.yml",
            "erp-file-prod.yml",
            "erp-monitor-prod.yml",
        ):
            self.assertIn(data_id, NACOS_PREFLIGHT)

    def test_deploy_dry_run_exits_before_any_mutation(self):
        preflight = DEPLOY.index('"$PREFLIGHT" --env-file')
        dry_run_exit = DEPLOY.index('if [[ "$DRY_RUN" == true ]]')
        docker_daemon = DEPLOY.index("docker info")
        create_upload = DEPLOY.index('mkdir -p "$ERP_UPLOAD_ROOT"')
        compose_up = DEPLOY.index('"${compose[@]}" up')
        self.assertLess(preflight, dry_run_exit)
        self.assertLess(dry_run_exit, docker_daemon)
        self.assertLess(dry_run_exit, create_upload)
        self.assertLess(dry_run_exit, compose_up)
        self.assertIn("--wait --wait-timeout", DEPLOY)
        self.assertIn('"$HEALTHCHECK"', DEPLOY)

    def test_healthcheck_is_observational_and_covers_edge_paths(self):
        self.assertIn("docker inspect", HEALTHCHECK)
        self.assertIn("/actuator/health", HEALTHCHECK)
        self.assertIn("/prod-api/code", HEALTHCHECK)
        self.assertIn("business-json", HEALTHCHECK)
        self.assertIn("code == 200", HEALTHCHECK)
        self.assertIn("erp-modules-oa erp-modules-file", HEALTHCHECK)
        self.assertNotRegex(HEALTHCHECK, r'"\$\{compose\[@\]\}"\s+(?:up|down|start|stop|restart|pull|build)\b')
        self.assertNotIn("docker restart", HEALTHCHECK)

    def test_shell_entrypoints_are_executable_and_parse(self):
        for name in (
            "deploy-ecs.sh",
            "healthcheck-ecs.sh",
            "preflight-ecs.sh",
            "run-erp-service.sh",
        ):
            path = DOCKER_DIR / name
            self.assertTrue(path.stat().st_mode & stat.S_IXUSR, name)
            subprocess.run(["bash", "-n", str(path)], check=True)


if __name__ == "__main__":
    unittest.main()
