#!/usr/bin/env python3

import hashlib
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts" / "qa"))
sys.path.insert(0, str(ROOT / "scripts"))

from new_business_uat_common import (  # noqa: E402
    REQUIRED_ACTORS,
    UatConfigError,
    load_config,
    validate_target_url,
)
from run_new_business_api_uat import _assert_one  # noqa: E402
from verify_new_business_uat_evidence import (  # noqa: E402
    EvidenceError,
    SCENARIOS,
    verify,
)


class UatConfigTests(unittest.TestCase):
    def _config(self, directory: Path):
        actors = {}
        for index, actor in enumerate(sorted(REQUIRED_ACTORS), 1):
            state = directory / f"{actor}.json"
            state.write_text(
                json.dumps(
                    {
                        "cookies": [
                            {
                                "name": "Admin-Token",
                                "value": f"test-token-{index}",
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
            actors[actor] = {"storageState": str(state)}
        return {
            "schemaVersion": 1,
            "releaseId": "new-business-20260714",
            "runId": "uat-unit-001",
            "database": "erp_uat_unit",
            "organizationId": "900001",
            "baseUrl": "http://localhost:8080",
            "apiBaseUrl": "http://localhost:8080/prod-api",
            "contexts": {
                "storeA": {
                    "deptId": "1",
                    "deptName": "A",
                    "deptType": "STORE",
                },
                "storeB": {
                    "deptId": "2",
                    "deptName": "B",
                    "deptType": "STORE",
                },
                "warehouseA": {
                    "deptId": "3",
                    "deptName": "WA",
                    "deptType": "WAREHOUSE",
                },
                "warehouseB": {
                    "deptId": "4",
                    "deptName": "WB",
                    "deptType": "WAREHOUSE",
                },
            },
            "actors": actors,
        }

    def test_rejects_non_isolated_host(self):
        with self.assertRaises(UatConfigError):
            validate_target_url("https://erp.example.com", "baseUrl")

    def test_loads_distinct_external_storage_states(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            config = self._config(directory)
            config_path = directory / "uat.json"
            config_path.write_text(json.dumps(config), encoding="utf-8")
            environment = {
                "ERP_QA_RUN_ID": "uat-unit-001",
                "ERP_QA_DATABASE": "erp_uat_unit",
                "ERP_QA_ALLOWED_ORG_ID": "900001",
                "ERP_UAT_APPROVE_BASE_URL": "http://localhost:8080",
            }
            with patch.dict(os.environ, environment, clear=False):
                loaded = load_config(config_path, ROOT)
            self.assertEqual(len(loaded["actors"]), len(REQUIRED_ACTORS))
            self.assertEqual(loaded["contexts"]["warehouseB"]["deptId"], "4")

    def test_rejects_embedded_token_field(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            config = self._config(directory)
            config["apiToken"] = "must-not-be-here"
            config_path = directory / "uat.json"
            config_path.write_text(json.dumps(config), encoding="utf-8")
            with self.assertRaises(UatConfigError):
                load_config(config_path, ROOT)


class ApiAssertionTests(unittest.TestCase):
    def test_negative_probe_requires_declared_code_and_message(self):
        probe = {
            "expect": "failure",
            "allowedAppCodes": [500],
            "messageIncludes": ["无权"],
        }
        _assert_one(
            probe,
            {
                "httpStatus": 200,
                "appCode": 500,
                "_parsed": {"code": 500, "msg": "无权访问"},
            },
        )


class UatEvidenceTests(unittest.TestCase):
    def _valid(self, directory: Path):
        api = directory / "api.json"
        browser = directory / "browser.tsv"
        api.write_text("{}\n", encoding="utf-8")
        browser.write_text("id\tstatus\nhealth\tpassed\n", encoding="utf-8")
        sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
        scenarios = [
            {
                "id": scenario,
                "status": "passed",
                "evidenceTypes": ["api", "browser"],
                "negativeCaseCount": 1,
                "reviewer": "QA Reviewer",
                "reviewedAt": "2026-07-14T18:00:00+08:00",
            }
            for scenario in sorted(SCENARIOS)
        ]
        return {
            "schemaVersion": 1,
            "releaseId": "new-business-20260714",
            "candidateCommit": "a" * 40,
            "runId": "uat-unit-001",
            "completedAt": "2026-07-14T18:00:00+08:00",
            "environment": {
                "databaseFingerprintSha256": "b" * 64,
                "organizationFingerprintSha256": "c" * 64,
                "stores": ["storeA", "storeB"],
                "warehouses": ["warehouseA", "warehouseB"],
            },
            "artifacts": {
                "api": {
                    "path": str(api),
                    "sha256": sha(api),
                    "checkCount": 1,
                    "status": "passed",
                },
                "browser": {
                    "path": str(browser),
                    "sha256": sha(browser),
                    "checkCount": 1,
                    "status": "passed",
                },
            },
            "scenarios": scenarios,
            "signoff": {
                "businessOwner": "Business Owner",
                "qaOwner": "QA Owner",
                "releaseOwner": "Release Owner",
                "approvedAt": "2026-07-14T18:00:00+08:00",
                "decision": "approved",
            },
        }

    def test_accepts_complete_artifact_backed_evidence(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._valid(directory)
            evidence = directory / "evidence.json"
            evidence.write_text(json.dumps(value), encoding="utf-8")
            summary = verify(evidence, ROOT, "a" * 40)
            self.assertEqual(summary["scenarioCount"], 4)

    def test_rejects_missing_negative_case(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._valid(directory)
            value["scenarios"][0]["negativeCaseCount"] = 0
            evidence = directory / "evidence.json"
            evidence.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(EvidenceError):
                verify(evidence, ROOT, "a" * 40)


if __name__ == "__main__":
    unittest.main()
