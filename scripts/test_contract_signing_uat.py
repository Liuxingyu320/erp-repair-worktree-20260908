#!/usr/bin/env python3

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts" / "qa"))

from contract_signing_uat_common import (  # noqa: E402
    LEGACY_PACKAGE_MENU_IDS,
    OA_ROOT_MENU_ID,
    RELEASE_ID,
    REQUIRED_API_CASES,
    REQUIRED_FAULT_CASES,
    SCENARIOS,
    TASK_CENTER_ROOT_MENU_ID,
    UatConfigError,
    load_config,
    public_config,
)
from prepare_contract_signing_uat import (  # noqa: E402
    _output_path as preflight_output_path,
    build_preflight,
)
from run_contract_signing_api_uat import (  # noqa: E402
    _assert_one,
    _assert_parallel_failure,
    _output_path as api_output_path,
    _perform,
    run,
)
from verify_contract_signing_evidence import (  # noqa: E402
    CHAIN_STEPS,
    EvidenceError,
    verify,
)


NOW = "2026-07-17T10:00:00+08:00"
LATER = "2026-07-17T12:00:00+08:00"


def _sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _execution(
    parsed,
    *,
    http_status: int = 200,
    content_type: str = "application/json",
    body_sha256: str | None = None,
) -> dict:
    body = b"" if parsed is None else json.dumps(
        parsed, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")
    return {
        "httpStatus": http_status,
        "appCode": parsed.get("code") if isinstance(parsed, dict) else None,
        "contentType": content_type,
        "durationMs": 1,
        "bodyBytes": len(body),
        "bodySha256": body_sha256 or hashlib.sha256(body).hexdigest(),
        "_parsed": parsed,
    }


def _identity_response(config: dict, *, environment_id: str | None = None) -> dict:
    return _execution(
        {
            "code": 200,
            "data": {
                "environmentId": environment_id or config["expectedEnvironmentId"],
                "database": config["database"],
                "databaseFingerprintSha256": config["databaseFingerprintSha256"],
                "releaseId": config["releaseId"],
                "runId": config["runId"],
                "candidateCommit": config["candidateCommit"],
                "isolated": True,
                "productionDataCopied": False,
            },
        }
    )


def _generated_http_observation(config, probe, captures, body_override=None):
    probe_id = str(probe.get("id") or "")
    case = probe.get("case")
    if probe_id == "contract-signing-environment-identity":
        return _identity_response(config)
    if probe_id == "duplicate-notification-postcondition":
        return _execution(
            {"code": 200, "data": {"count": 1, "notificationId": "notification-stable-1"}}
        )
    if case == "duplicate-request":
        return _execution(
            {"code": 200, "data": {"notificationId": "notification-stable-1"}}
        )
    if case == "cross-organization-denied":
        return _execution({"code": 403, "msg": "无权访问其他组织"}, http_status=403)
    if case == "legacy-task-center-denied":
        return _execution({"code": 403, "msg": "未授权任务中心"}, http_status=403)
    if case in {"permissions-dedicated-hr", "legacy-package-route-preserved"}:
        return _execution({"code": 200, "rows": []})
    if case == "success-chain-state":
        return _execution({"code": 200, "data": {"task": {"status": "SIGNED"}}})
    if case == "refusal-terminal":
        return _execution(
            {"code": 200, "data": {"task": {"status": "REFUSED", "resolutionStatus": "OPEN"}}}
        )
    if case == "expiry-terminal":
        return _execution(
            {"code": 200, "data": {"task": {"status": "EXPIRED", "resolutionStatus": "OPEN"}}}
        )
    if case == "replacement-version":
        return _execution({"code": 200, "data": {"replacementTaskId": "960001"}})
    if case == "notification-failure":
        return _execution(
            {
                "code": 200,
                "data": {
                    "latestNotificationStatus": "DEAD",
                    "latestNotificationBusinessKey": "SIGN_SENT:950003:uat-v1",
                },
            }
        )
    if case == "notification-recovery":
        return _execution({"code": 200, "data": {"requeued": True}})
    if case == "file-verification":
        return _execution(
            None,
            content_type="application/pdf",
            body_sha256=config["faultFixtures"]["completedFinalFileSha256"],
        )
    return _execution({"code": 200})


class ConfigFixture:
    def __init__(self, directory: Path, signer_fingerprint: str = "f" * 64):
        self.directory = directory
        self.value = json.loads(
            (ROOT / "scripts" / "qa" / "contract-signing-uat.example.json").read_text(
                encoding="utf-8"
            )
        )
        self.value["candidateCommit"] = "a" * 40
        self.value["databaseEngine"]["version"] = "5.7.44"
        self.value["baseUrl"] = "http://localhost:8080"
        self.value["apiBaseUrl"] = "http://localhost:8080/prod-api"
        self._replace_placeholder_hashes(self.value)
        self.value["trustedSignerPublicKeySha256"] = signer_fingerprint
        for index, (alias, actor) in enumerate(self.value["actors"].items(), 1):
            state = directory / f"{alias}.json"
            state.write_text(
                json.dumps(
                    {
                        "cookies": [
                            {
                                "name": "Admin-Token",
                                "value": f"contract-uat-token-{index}",
                                "domain": "localhost",
                                "path": "/",
                            }
                        ],
                        "origins": [],
                    }
                ),
                encoding="utf-8",
            )
            state.chmod(0o600)
            actor["storageState"] = str(state)
        self.path = directory / "contract-signing-uat.json"

    def _replace_placeholder_hashes(self, value):
        if isinstance(value, dict):
            for key, child in list(value.items()):
                if isinstance(child, str) and child == "0" * 64:
                    value[key] = "b" * 64
                else:
                    self._replace_placeholder_hashes(child)
        elif isinstance(value, list):
            for index, child in enumerate(value):
                if isinstance(child, str) and child == "0" * 64:
                    value[index] = "b" * 64
                else:
                    self._replace_placeholder_hashes(child)

    def save(self) -> Path:
        self.path.write_text(json.dumps(self.value), encoding="utf-8")
        return self.path

    def environment(self) -> dict[str, str]:
        return {
            "ERP_CONTRACT_SIGN_UAT_RUN_ID": self.value["runId"],
            "ERP_CONTRACT_SIGN_UAT_DATABASE": self.value["database"],
            "ERP_CONTRACT_SIGN_UAT_ENVIRONMENT_ID": self.value["expectedEnvironmentId"],
            "ERP_CONTRACT_SIGN_UAT_DATABASE_FINGERPRINT_SHA256": self.value[
                "databaseFingerprintSha256"
            ],
            "ERP_CONTRACT_SIGN_UAT_TRUSTED_SIGNER_SHA256": self.value[
                "trustedSignerPublicKeySha256"
            ],
            "ERP_CONTRACT_SIGN_UAT_ALLOWED_ORG_IDS": "910001,920001",
            "ERP_CONTRACT_SIGN_UAT_COMMIT": self.value["candidateCommit"],
            "ERP_UAT_APPROVE_BASE_URL": self.value["baseUrl"],
        }

    def load(self):
        self.save()
        with patch.dict(os.environ, self.environment(), clear=False):
            return load_config(self.path, ROOT)


class ContractSigningConfigTests(unittest.TestCase):
    def test_loads_complete_role_boundary_fixture_without_public_secrets(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = ConfigFixture(Path(temporary))
            loaded = fixture.load()
            self.assertEqual(set(loaded["scenarios"]), set(SCENARIOS))
            self.assertEqual(len(loaded["actors"]), 9)
            legacy = loaded["actors"]["legacyPackageOnly"]
            self.assertEqual(
                legacy["expectedMenuIds"], [OA_ROOT_MENU_ID, *LEGACY_PACKAGE_MENU_IDS]
            )
            self.assertEqual(legacy["forbiddenMenuIds"], [TASK_CENTER_ROOT_MENU_ID])
            public = json.dumps(public_config(loaded), ensure_ascii=False)
            self.assertNotIn("storageState", public)
            self.assertNotIn("_token", public)
            self.assertNotIn("employeeId", public)
            self.assertNotIn("faultFixtures", public)

    def test_rejects_admin_technical_cross_grant_and_legacy_task_root(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = ConfigFixture(Path(temporary))
            fixture.value["actors"]["dedicatedHrOrgA"]["isAdministrator"] = True
            with self.assertRaises(UatConfigError):
                fixture.load()
            fixture.value["actors"]["dedicatedHrOrgA"]["isAdministrator"] = False
            fixture.value["actors"]["dedicatedHrOrgA"]["expectedPermissions"].append(
                "oa:signTask:technicalEvidence"
            )
            with self.assertRaises(UatConfigError):
                fixture.load()
            fixture.value["actors"]["dedicatedHrOrgA"]["expectedPermissions"].pop()
            fixture.value["actors"]["legacyPackageOnly"]["expectedMenuIds"].append(9650)
            with self.assertRaises(UatConfigError):
                fixture.load()

    def test_rejects_embedded_confirmation_token_and_missing_fault_case(self):
        with tempfile.TemporaryDirectory() as temporary:
            fixture = ConfigFixture(Path(temporary))
            fixture.value["apiProbes"][0]["body"] = {"confirmationToken": "forbidden"}
            with self.assertRaises(UatConfigError):
                fixture.load()
            del fixture.value["apiProbes"][0]["body"]
            fixture.value["faultCases"].pop()
            with self.assertRaises(UatConfigError):
                fixture.load()

    def test_preflight_pins_identity_signer_and_legacy_boundary(self):
        with tempfile.TemporaryDirectory() as temporary:
            preflight = build_preflight(ConfigFixture(Path(temporary)).load())
            serialized = json.dumps(preflight)
            self.assertFalse(preflight["gates"]["writesExecuted"])
            self.assertTrue(preflight["gates"]["environmentIdentityProbeDeclared"])
            self.assertTrue(preflight["gates"]["legacyPackageBoundaryDeclared"])
            self.assertEqual(preflight["legacyBoundaryPlan"]["forbiddenMenuIds"], [9650])
            self.assertNotIn("storageState", serialized)
            self.assertNotIn("contract-uat-token", serialized)

    def test_local_evidence_outputs_reject_symlinks(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            target = directory / "target.json"
            target.write_text("{}", encoding="utf-8")
            link = directory / "evidence.json"
            link.symlink_to(target)
            with self.assertRaises(UatConfigError):
                preflight_output_path(str(link), ROOT)
            with self.assertRaises(UatConfigError):
                api_output_path(str(link), ROOT)


class ContractSigningApiRunnerTests(unittest.TestCase):
    def test_default_mode_skips_every_write_probe(self):
        with tempfile.TemporaryDirectory() as temporary:
            loaded = ConfigFixture(Path(temporary)).load()
            loaded["apiProbes"] = [
                {
                    "id": "read-probe-001",
                    "case": "permissions-dedicated-hr",
                    "actor": "dedicatedHrOrgA",
                    "organization": "orgA",
                    "method": "GET",
                    "path": "/signTask/list",
                    "expect": "success",
                },
                {
                    "id": "write-probe-001",
                    "case": "notification-recovery",
                    "actor": "dedicatedHrOrgA",
                    "organization": "orgA",
                    "method": "POST",
                    "path": "/signTask/1/notification/retry",
                    "writePurpose": "isolated test",
                    "body": {"requestId": "uat-request-001", "businessKey": "key"},
                    "expect": "success",
                },
            ]
            with patch(
                "run_contract_signing_api_uat._perform",
                return_value=_execution({"code": 200}),
            ) as perform:
                evidence = run(loaded, allow_write=False)
            self.assertEqual(perform.call_count, 1)
            self.assertFalse(evidence["writeMode"])
            self.assertFalse(evidence["environmentIdentityVerified"])
            self.assertEqual(evidence["skippedWriteCount"], 1)

    def test_write_mode_requires_run_specific_environment_approval(self):
        with tempfile.TemporaryDirectory() as temporary:
            loaded = ConfigFixture(Path(temporary)).load()
            with patch.dict(os.environ, {"ERP_CONTRACT_SIGN_UAT_WRITE_APPROVAL": "wrong"}):
                with self.assertRaises(UatConfigError):
                    run(loaded, allow_write=True)

    def test_identity_mismatch_blocks_before_any_write(self):
        with tempfile.TemporaryDirectory() as temporary:
            loaded = ConfigFixture(Path(temporary)).load()
            loaded["apiProbes"] = [
                {
                    "id": "write-probe-001",
                    "case": "notification-recovery",
                    "actor": "dedicatedHrOrgA",
                    "organization": "orgA",
                    "method": "POST",
                    "path": "/signTask/1/notification/retry",
                    "writePurpose": "must never execute",
                    "body": {"requestId": "uat-request-001"},
                    "expect": "success",
                }
            ]
            with patch.dict(
                os.environ,
                {"ERP_CONTRACT_SIGN_UAT_WRITE_APPROVAL": loaded["runId"]},
            ), patch(
                "run_contract_signing_api_uat._perform",
                return_value=_identity_response(loaded, environment_id="wrong-environment"),
            ) as perform:
                with self.assertRaises(AssertionError):
                    run(loaded, allow_write=True)
            self.assertEqual(perform.call_count, 1, "write probe ran after identity mismatch")

    def test_redirect_does_not_deliver_bearer_to_cross_origin_target(self):
        delivered = []

        class SinkHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                delivered.append(self.headers.get("Authorization"))
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b"{}")

            def log_message(self, *_):
                pass

        sink = ThreadingHTTPServer(("127.0.0.1", 0), SinkHandler)

        class RedirectHandler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(302)
                self.send_header("Location", f"http://127.0.0.1:{sink.server_port}/sink")
                self.end_headers()

            def log_message(self, *_):
                pass

        source = ThreadingHTTPServer(("127.0.0.1", 0), RedirectHandler)
        threads = [
            threading.Thread(target=server.serve_forever, daemon=True)
            for server in (sink, source)
        ]
        for thread in threads:
            thread.start()
        try:
            with tempfile.TemporaryDirectory() as temporary:
                loaded = ConfigFixture(Path(temporary)).load()
                loaded["apiBaseUrl"] = f"http://127.0.0.1:{source.server_port}"
                probe = {
                    "id": "redirect-probe",
                    "actor": "dedicatedHrOrgA",
                    "method": "GET",
                    "path": "/start",
                    "expect": "success",
                }
                with patch.dict(
                    os.environ,
                    {"NO_PROXY": "127.0.0.1,localhost", "no_proxy": "127.0.0.1,localhost"},
                    clear=False,
                ):
                    with self.assertRaises(AssertionError):
                        _perform(loaded, probe, {})
            self.assertEqual(delivered, [])
        finally:
            source.shutdown()
            sink.shutdown()
            source.server_close()
            sink.server_close()

    def test_every_http_5xx_is_rejected_in_normal_and_parallel_paths(self):
        result = _execution(
            {"code": 500, "msg": "重复请求"},
            http_status=500,
        )
        with self.assertRaises(AssertionError):
            _assert_one(
                {
                    "id": "negative",
                    "expect": "failure",
                    "allowedHttpStatuses": [403],
                    "allowedAppCodes": [500],
                    "messageIncludes": ["重复"],
                },
                result,
            )
        with self.assertRaises(AssertionError):
            _assert_parallel_failure(
                {
                    "id": "duplicate-request",
                    "parallelFailureAllowedHttpStatuses": [409],
                    "parallelFailureAllowedAppCodes": [500],
                    "parallelFailureMessageIncludes": ["重复"],
                },
                result,
            )

    def test_duplicate_probe_requires_stable_identity_and_exact_count_one(self):
        with tempfile.TemporaryDirectory() as temporary:
            loaded = ConfigFixture(Path(temporary)).load()
            duplicate = next(
                probe for probe in loaded["apiProbes"] if probe["case"] == "duplicate-request"
            )
            loaded["apiProbes"] = [duplicate]
            with patch.dict(
                os.environ,
                {"ERP_CONTRACT_SIGN_UAT_WRITE_APPROVAL": loaded["runId"]},
            ), patch(
                "run_contract_signing_api_uat._perform",
                side_effect=_generated_http_observation,
            ):
                evidence = run(loaded, allow_write=True)
            self.assertEqual(evidence["status"], "passed")
            result = evidence["results"][0]
            self.assertEqual(result["postcondition"]["observedBusinessKeyCount"], 1)
            self.assertEqual(
                result["stableIdentitySha256"],
                result["postcondition"]["stableIdentitySha256"],
            )

    def test_duplicate_probe_fails_when_postcondition_finds_two_rows(self):
        def duplicate_row_observation(config, probe, captures, body_override=None):
            if probe.get("id") == "duplicate-notification-postcondition":
                return _execution(
                    {
                        "code": 200,
                        "data": {"count": 2, "notificationId": "notification-stable-1"},
                    }
                )
            return _generated_http_observation(config, probe, captures, body_override)

        with tempfile.TemporaryDirectory() as temporary:
            loaded = ConfigFixture(Path(temporary)).load()
            loaded["apiProbes"] = [
                next(
                    probe
                    for probe in loaded["apiProbes"]
                    if probe["case"] == "duplicate-request"
                )
            ]
            with patch.dict(
                os.environ,
                {"ERP_CONTRACT_SIGN_UAT_WRITE_APPROVAL": loaded["runId"]},
            ), patch(
                "run_contract_signing_api_uat._perform",
                side_effect=duplicate_row_observation,
            ):
                evidence = run(loaded, allow_write=True)
            self.assertEqual(evidence["status"], "failed")
            self.assertEqual(evidence["failedCount"], 1)


class ContractSigningEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.key_directory = Path(tempfile.mkdtemp(prefix="contract-uat-signer-"))
        cls.private_key = cls.key_directory / "private.pem"
        cls.public_key = cls.key_directory / "public.pem"
        subprocess.run(
            [
                "openssl", "genpkey", "-algorithm", "RSA",
                "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(cls.private_key),
            ],
            check=True,
            capture_output=True,
        )
        subprocess.run(
            ["openssl", "pkey", "-in", str(cls.private_key), "-pubout", "-out", str(cls.public_key)],
            check=True,
            capture_output=True,
        )
        cls.signer_fingerprint = _sha(cls.public_key)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.key_directory, ignore_errors=True)

    def _sign(self, path: Path) -> Path:
        signature = Path(str(path) + ".sig")
        subprocess.run(
            [
                "openssl", "dgst", "-sha256", "-sign", str(self.private_key),
                "-out", str(signature), str(path),
            ],
            check=True,
            capture_output=True,
        )
        return signature

    def _file_ref(self, path: Path) -> dict:
        return {"path": str(path), "sha256": _sha(path), "byteCount": path.stat().st_size}

    def _artifact_ref(self, path: Path, payload: dict) -> dict:
        signature = self._sign(path)
        return {
            "path": str(path),
            "sha256": _sha(path),
            "checkCount": payload.get("checkCount", payload.get("probeCount")),
            "status": "passed",
            "signaturePath": str(signature),
            "signerPublicKeySha256": self.signer_fingerprint,
        }

    def _manifest_ref(
        self,
        directory: Path,
        loaded: dict,
        journey_id: str,
    ) -> tuple[dict, int]:
        screenshot = directory / "screenshots" / f"{journey_id}.png"
        screenshot.parent.mkdir(parents=True, exist_ok=True)
        screenshot.write_bytes(f"synthetic screenshot for {journey_id}".encode("utf-8"))
        manifest = {
            "kind": "contract-signing-screenshot-manifest",
            "releaseId": RELEASE_ID,
            "runId": loaded["runId"],
            "candidateCommit": loaded["candidateCommit"],
            "journeyId": journey_id,
            "files": [self._file_ref(screenshot)],
        }
        manifest_path = directory / "manifests" / f"{journey_id}.json"
        _write_json(manifest_path, manifest)
        return self._file_ref(manifest_path), 1

    def _generated_api(self, loaded: dict) -> dict:
        with patch.dict(
            os.environ,
            {"ERP_CONTRACT_SIGN_UAT_WRITE_APPROVAL": loaded["runId"]},
        ), patch(
            "run_contract_signing_api_uat._perform",
            side_effect=_generated_http_observation,
        ):
            api = run(loaded, allow_write=True)
        self.assertEqual(api["status"], "passed")
        api["completedAt"] = NOW
        return api

    def _valid_evidence(self, directory: Path) -> tuple[Path, dict]:
        states = Path(tempfile.mkdtemp(prefix="contract-signing-uat-states-"))
        self.addCleanup(shutil.rmtree, states, True)
        fixture = ConfigFixture(states, self.signer_fingerprint)
        loaded = fixture.load()

        preflight = build_preflight(loaded)
        preflight["completedAt"] = NOW
        preflight_path = directory / "preflight.json"
        _write_json(preflight_path, preflight)

        api = self._generated_api(loaded)
        api_path = directory / "api.json"
        _write_json(api_path, api)

        browser_journeys = []
        for journey in loaded["browserJourneys"]:
            manifest_ref, screenshot_count = self._manifest_ref(
                directory, loaded, journey["id"]
            )
            row = {
                "id": journey["id"],
                "scenario": journey["scenario"],
                "mode": journey["mode"],
                "actor": journey["actor"],
                "organization": journey["organization"],
                "status": "passed",
                "consoleErrorCount": 0,
                "checks": journey["checks"],
                "screenshotManifest": manifest_ref,
                "screenshotCount": screenshot_count,
            }
            if journey["mode"] == "EMPLOYEE_MOBILE":
                row.update(
                    {
                        "viewport": {"width": 390, "height": 844},
                        "focusReturnPassed": True,
                        "deepLinkRecoveryPassed": True,
                        "loadFailureMessagePassed": True,
                    }
                )
            browser_journeys.append(row)
        legacy_manifest, legacy_screenshot_count = self._manifest_ref(
            directory, loaded, "legacy-package-boundary"
        )
        browser = {
            "schemaVersion": 1,
            "releaseId": RELEASE_ID,
            "runId": loaded["runId"],
            "candidateCommit": loaded["candidateCommit"],
            "trustedSignerPublicKeySha256": self.signer_fingerprint,
            "kind": "contract-signing-browser-uat",
            "completedAt": NOW,
            "status": "passed",
            "checkCount": 11,
            "journeys": browser_journeys,
            "legacyMenuBoundary": {
                "status": "passed",
                "actor": "legacyPackageOnly",
                "menuTreeIds": [OA_ROOT_MENU_ID, *LEGACY_PACKAGE_MENU_IDS],
                "packageRouteAccessible": True,
                "taskRouteAccessible": False,
                "packageBrowserPath": "/oa/sign-package",
                "taskBrowserPath": "/oa/sign-task",
                "screenshotManifest": legacy_manifest,
                "screenshotCount": legacy_screenshot_count,
            },
        }
        browser_path = directory / "browser.json"
        _write_json(browser_path, browser)

        execution_log_path = directory / "mysql57-execution.log"
        execution_log_path.write_text(
            "mysql 5.7 migration run 1: passed\n"
            "mysql 5.7 migration run 2: passed\n"
            "legacy menu 4520-4526 present, 9650 absent: passed\n",
            encoding="utf-8",
        )
        legacy_database_boundary = {
            "status": "passed",
            "actor": "legacyPackageOnly",
            "oaRootMenuId": OA_ROOT_MENU_ID,
            "presentMenuIds": list(LEGACY_PACKAGE_MENU_IDS),
            "absentMenuIds": [TASK_CENTER_ROOT_MENU_ID],
            "packageRoute": "sign-package",
            "packageComponent": "oa/signPackage/index",
            "taskRoute": "sign-task",
            "packageGrantCount": len(LEGACY_PACKAGE_MENU_IDS),
            "taskCenterRootGrantCount": 0,
            "databaseCheckCount": 2,
        }
        mysql_attestation = {
            "kind": "contract-signing-mysql57-execution-attestation",
            "releaseId": RELEASE_ID,
            "runId": loaded["runId"],
            "candidateCommit": loaded["candidateCommit"],
            "completedAt": NOW,
            "environmentId": loaded["expectedEnvironmentId"],
            "database": loaded["database"],
            "databaseFingerprintSha256": loaded["databaseFingerprintSha256"],
            "migrationBundleSha256": loaded["migrationBundleSha256"],
            "engineVersion": "5.7.44",
            "virtualized": False,
            "migrationRunCount": 2,
            "migrationFailureCount": 0,
            "coreQueryCount": 12,
            "coreQueryFailureCount": 0,
            "authorizedBy": "Database Owner",
            "executionLogSha256": _sha(execution_log_path),
            "legacyMenuBoundary": legacy_database_boundary,
        }
        mysql_attestation_path = directory / "mysql57-attestation.json"
        _write_json(mysql_attestation_path, mysql_attestation)
        mysql57 = {
            "schemaVersion": 1,
            "releaseId": RELEASE_ID,
            "runId": loaded["runId"],
            "candidateCommit": loaded["candidateCommit"],
            "trustedSignerPublicKeySha256": self.signer_fingerprint,
            "kind": "contract-signing-mysql57-uat",
            "completedAt": NOW,
            "status": "passed",
            "checkCount": 8,
            "engineVersion": "5.7.44",
            "virtualized": False,
            "migrationRunCount": 2,
            "migrationFailureCount": 0,
            "coreQueryCount": 12,
            "coreQueryFailureCount": 0,
            "migrationBundleSha256": loaded["migrationBundleSha256"],
            "authorizedBy": "Database Owner",
            "executionLog": self._file_ref(execution_log_path),
            "executionAttestation": self._file_ref(mysql_attestation_path),
            "legacyMenuBoundary": legacy_database_boundary,
        }
        mysql57_path = directory / "mysql57.json"
        _write_json(mysql57_path, mysql57)

        artifacts = {
            "preflight": self._artifact_ref(preflight_path, preflight),
            "api": self._artifact_ref(api_path, api),
            "browser": self._artifact_ref(browser_path, browser),
            "mysql57": self._artifact_ref(mysql57_path, mysql57),
        }
        actors = []
        for alias, actor in loaded["actors"].items():
            row = {
                "alias": alias,
                "kind": actor["kind"],
                "isAdministrator": False,
                "organizations": actor["organizations"],
                "roleKeys": actor["expectedRoleKeys"],
                "permissions": actor["expectedPermissions"],
                "permissionMatrixStatus": "passed",
            }
            if alias == "legacyPackageOnly":
                row["menuIds"] = [OA_ROOT_MENU_ID, *LEGACY_PACKAGE_MENU_IDS]
            actors.append(row)
        entities = []
        for organization, org in loaded["organizations"].items():
            for entity in org["legalEntities"]:
                entities.append(
                    {
                        "organization": organization,
                        "legalEntityId": entity["legalEntityId"],
                        "sealId": entity["seal"]["sealId"],
                        "contractTypes": ["LABOR", "SERVICE"],
                        "legalEntityFingerprintSha256": "e" * 64,
                        "sealFingerprintSha256": entity["seal"]["sha256"],
                        "status": "passed",
                    }
                )
        plans = [
            {
                "scenario": scenario,
                "organization": row["organization"],
                "planVersionId": row["planVersionId"],
                "planVersionSha256": row["planVersionSha256"],
                "templateHashes": row["templateHashes"],
                "approvalRef": row["approvalRef"],
                "status": "published",
            }
            for scenario, row in loaded["scenarios"].items()
        ]
        scenarios = [
            {
                "id": scenario,
                "status": "passed",
                "evidenceTypes": ["api", "browser"],
                "chainSteps": sorted(CHAIN_STEPS),
                "organization": row["organization"],
                "planVersionId": row["planVersionId"],
                "contractType": row["contractType"],
                "taskFingerprintSha256": "1" * 64,
                "packageFingerprintSha256": "2" * 64,
                "finalFileCount": 1,
                "verifiedFileCount": 1,
                "placementReviewedPageCount": 2,
                "placementPassedPageCount": 2,
                "wrongContractCount": 0,
                "reviewer": f"Reviewer {scenario}",
                "reviewedAt": NOW,
            }
            for scenario, row in loaded["scenarios"].items()
        ]
        exception_cases = [
            {
                "id": case,
                "status": "passed",
                "evidenceTypes": ["api"] if case in REQUIRED_API_CASES else ["fault-injection"],
                "checkCount": 1,
                "reviewer": f"Reviewer {case}",
                "reviewedAt": NOW,
            }
            for case in sorted(REQUIRED_FAULT_CASES)
        ]
        evidence_path = directory / "evidence.json"
        signature_path = Path(str(evidence_path) + ".sig")
        value = {
            "schemaVersion": 1,
            "releaseId": RELEASE_ID,
            "candidateCommit": loaded["candidateCommit"],
            "runId": loaded["runId"],
            "completedAt": LATER,
            "trustedSignerPublicKeySha256": self.signer_fingerprint,
            "attestation": {
                "signaturePath": str(signature_path),
                "signerPublicKeySha256": self.signer_fingerprint,
            },
            "environment": {
                "environmentId": loaded["expectedEnvironmentId"],
                "database": loaded["database"],
                "databaseEngine": "mysql",
                "databaseVersion": "5.7.44",
                "databaseFingerprintSha256": loaded["databaseFingerprintSha256"],
                "migrationBundleSha256": loaded["migrationBundleSha256"],
                "trustedSignerPublicKeySha256": self.signer_fingerprint,
                "organizations": ["orgA", "orgB"],
                "isolated": True,
                "productionDataCopied": False,
            },
            "artifacts": artifacts,
            "actors": actors,
            "fixtures": {"legalEntities": entities, "plans": plans},
            "scenarios": scenarios,
            "exceptionCases": exception_cases,
            "metrics": {
                "wrongContractCount": 0,
                "duplicateTaskCount": 0,
                "duplicateNotificationCount": 0,
                "technicalEvidenceCrossGrantCount": 0,
                "crossOrganizationUnauthorizedSuccessCount": 0,
                "newFileVerificationRate": 100,
                "signatureSealPlacementPassRate": 100,
                "hrBusinessPermissionPassRate": 100,
                "finalFileCount": 5,
                "verifiedFileCount": 5,
                "placementReviewedPageCount": 10,
                "placementPassedPageCount": 10,
                "notificationRecoveryCount": 1,
            },
            "signoff": {
                "hrOwner": "HR Owner",
                "legalOwner": "Legal Owner",
                "qaOrBusinessOwner": "QA Owner",
                "approvedAt": NOW,
                "decision": "approved",
            },
        }
        _write_json(evidence_path, value)
        self._sign(evidence_path)
        return evidence_path, value

    def _resign_final(self, evidence_path: Path, value: dict) -> None:
        _write_json(evidence_path, value)
        self._sign(evidence_path)

    def _verify(self, evidence_path: Path, candidate_commit: str):
        return verify(
            evidence_path,
            ROOT,
            candidate_commit,
            self.public_key,
            self.signer_fingerprint,
        )

    def test_accepts_signed_generated_artifact_backed_evidence(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            evidence_path, value = self._valid_evidence(Path(temporary))
            summary = self._verify(evidence_path, value["candidateCommit"])
            self.assertEqual(summary["scenarioCount"], 5)
            self.assertEqual(summary["exceptionCaseCount"], 15)

    def test_rejects_tampered_screenshot_under_signed_manifest(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            evidence_path, value = self._valid_evidence(Path(temporary))
            browser_path = Path(value["artifacts"]["browser"]["path"])
            browser = json.loads(browser_path.read_text(encoding="utf-8"))
            manifest_path = Path(browser["journeys"][0]["screenshotManifest"]["path"])
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            Path(manifest["files"][0]["path"]).write_bytes(b"tampered screenshot")
            with self.assertRaises(EvidenceError):
                self._verify(evidence_path, value["candidateCommit"])

    def test_rejects_tampered_mysql_execution_log(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            evidence_path, value = self._valid_evidence(Path(temporary))
            mysql_path = Path(value["artifacts"]["mysql57"]["path"])
            mysql = json.loads(mysql_path.read_text(encoding="utf-8"))
            Path(mysql["executionLog"]["path"]).write_text("tampered", encoding="utf-8")
            with self.assertRaises(EvidenceError):
                self._verify(evidence_path, value["candidateCommit"])

    def test_rejects_bad_signature_even_when_hash_is_redeclared(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            evidence_path, value = self._valid_evidence(Path(temporary))
            api_path = Path(value["artifacts"]["api"]["path"])
            api = json.loads(api_path.read_text(encoding="utf-8"))
            api["status"] = "failed"
            _write_json(api_path, api)
            value["artifacts"]["api"]["sha256"] = _sha(api_path)
            self._resign_final(evidence_path, value)
            with self.assertRaises(EvidenceError):
                self._verify(evidence_path, value["candidateCommit"])

    def test_rejects_legacy_actor_receiving_task_center_root(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            evidence_path, value = self._valid_evidence(Path(temporary))
            legacy = next(row for row in value["actors"] if row["alias"] == "legacyPackageOnly")
            legacy["menuIds"].append(TASK_CENTER_ROOT_MENU_ID)
            self._resign_final(evidence_path, value)
            with self.assertRaises(EvidenceError):
                self._verify(evidence_path, value["candidateCommit"])

    def test_rejects_admin_read_only_api_and_missing_case_after_valid_resigning(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            evidence_path, value = self._valid_evidence(directory)
            value["actors"][0]["isAdministrator"] = True
            self._resign_final(evidence_path, value)
            with self.assertRaises(EvidenceError):
                self._verify(evidence_path, value["candidateCommit"])

            evidence_path, value = self._valid_evidence(directory)
            api_path = Path(value["artifacts"]["api"]["path"])
            api = json.loads(api_path.read_text(encoding="utf-8"))
            api["writeMode"] = False
            api["skippedWriteCount"] = 2
            _write_json(api_path, api)
            self._sign(api_path)
            value["artifacts"]["api"]["sha256"] = _sha(api_path)
            self._resign_final(evidence_path, value)
            with self.assertRaises(EvidenceError):
                self._verify(evidence_path, value["candidateCommit"])

            evidence_path, value = self._valid_evidence(directory)
            value["exceptionCases"].pop()
            self._resign_final(evidence_path, value)
            with self.assertRaises(EvidenceError):
                self._verify(evidence_path, value["candidateCommit"])


if __name__ == "__main__":
    unittest.main()
