#!/usr/bin/env python3

import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from parse_new_business_reconciliation import ISSUE_PATTERN
from verify_new_business_gray_evidence import (
    EXPECTED_ALERTS,
    EXPECTED_RECONCILIATION_ISSUES,
    EXPECTED_RECONCILIATION_SQL_SHA256,
    FEATURE_ORDER,
    GrayEvidenceError,
    RULE_GROUP_NAME,
    _candidate_alerts,
    _duration_seconds,
    verify,
)


ROOT = Path(__file__).resolve().parents[1]


class GrayEvidenceTests(unittest.TestCase):
    def _sha(self, path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def _artifact(self, path: Path):
        return {
            "path": str(path),
            "sha256": self._sha(path),
            "status": "passed",
        }

    def test_reconciliation_issue_contract_matches_candidate_sql(self):
        active_sql = ROOT / "scripts" / "new-business-daily-reconciliation.sql"
        source = active_sql.read_text(encoding="utf-8")
        self.assertEqual(
            EXPECTED_RECONCILIATION_ISSUES,
            frozenset(ISSUE_PATTERN.findall(source)),
        )
        self.assertEqual(
            EXPECTED_RECONCILIATION_SQL_SHA256,
            self._sha(active_sql),
        )

    def _prometheus_rules(self):
        contract = _candidate_alerts(
            ROOT / "ops" / "monitoring" / "new-business-alert-rules.yml"
        )
        return {
            "status": "success",
            "data": {
                "groups": [
                    {
                        "name": RULE_GROUP_NAME,
                        "interval": 60,
                        "rules": [
                            {
                                "name": name,
                                "type": "alerting",
                                "health": "ok",
                                "query": contract[name]["query"],
                                "state": "inactive",
                                "duration": _duration_seconds(contract[name]["for"]),
                                "labels": {"severity": contract[name]["severity"]},
                            }
                            for name in EXPECTED_ALERTS
                        ],
                    }
                ]
            },
        }

    def _write_loaded_rules(
        self, path: Path, value: dict, evidence: dict
    ) -> None:
        path.write_text(json.dumps(value), encoding="utf-8")
        evidence["monitoring"]["loadEvidence"] = self._artifact(path)

    def _evidence(self, directory: Path):
        reconciliation = {
            "schemaVersion": 1,
            "releaseId": "new-business-20260714",
            "kind": "daily-reconciliation",
            "reconciliationSqlSha256": EXPECTED_RECONCILIATION_SQL_SHA256,
            "status": "passed",
            "issueCount": 0,
            "issues": {
                code: 0 for code in sorted(EXPECTED_RECONCILIATION_ISSUES)
            },
        }
        before = directory / "before.json"
        after = directory / "after.json"
        before.write_text(
            json.dumps(
                {**reconciliation, "completedAt": "2026-07-14T17:00:00+08:00"}
            ),
            encoding="utf-8",
        )
        after.write_text(
            json.dumps(
                {**reconciliation, "completedAt": "2026-07-14T20:10:00+08:00"}
            ),
            encoding="utf-8",
        )
        loaded = directory / "prometheus-rules-alerts.json"
        loaded.write_text(json.dumps(self._prometheus_rules()), encoding="utf-8")
        rollback = directory / "rollback.txt"
        rollback.write_text("entry closed; in-flight completed\n", encoding="utf-8")
        completed_at = "2026-07-14T21:00:00+08:00"
        changes = [
            {
                "key": key,
                "previousValue": "false",
                "newValue": "true",
                "scope": "UAT/灰度角色和门店",
                "operator": "Release Operator",
                "reason": "approved staged rollout",
                "ticket": f"CHG-{index:03d}",
                "auditRecordId": index,
                "changedAt": (
                    f"2026-07-14T{18 + ((index - 1) * 10) // 60:02d}:"
                    f"{((index - 1) * 10) % 60:02d}:00+08:00"
                ),
            }
            for index, key in enumerate(FEATURE_ORDER, 1)
        ]
        rules = ROOT / "ops" / "monitoring" / "new-business-alert-rules.yml"
        return {
            "schemaVersion": 1,
            "releaseId": "new-business-20260714",
            "candidateCommit": "d" * 40,
            "runId": "gray-unit-001",
            "completedAt": completed_at,
            "phases": [
                {
                    "name": "single-store-single-warehouse",
                    "stores": ["storeA"],
                    "warehouses": ["warehouseA"],
                    "status": "passed",
                    "reviewer": "Ops Reviewer",
                    "completedAt": "2026-07-14T19:10:00+08:00",
                },
                {
                    "name": "second-store-isolation",
                    "stores": ["storeA", "storeB"],
                    "warehouses": ["warehouseA", "warehouseB"],
                    "status": "passed",
                    "reviewer": "Ops Reviewer",
                    "completedAt": "2026-07-14T20:00:00+08:00",
                },
            ],
            "featureChanges": changes,
            "monitoring": {
                "candidateRulesSha256": self._sha(rules),
                "loadedAt": "2026-07-14T17:30:00+08:00",
                "operator": "Monitoring Operator",
                "loadEvidence": self._artifact(loaded),
            },
            "reconciliation": {
                "before": self._artifact(before),
                "after": self._artifact(after),
            },
            "rollbackDrill": {
                "newEntryDisabled": True,
                "inFlightCompleted": True,
                "reconciliationStayedClean": True,
                "evidence": self._artifact(rollback),
                "reviewer": "Ops Reviewer",
                "completedAt": "2026-07-14T20:30:00+08:00",
            },
            "signoff": {
                "businessOwner": "Business Owner",
                "operationsOwner": "Operations Owner",
                "releaseOwner": "Release Owner",
                "approvedAt": "2026-07-14T20:45:00+08:00",
                "decision": "approved",
            },
        }

    def test_accepts_complete_gray_rollout(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._evidence(directory)
            path = directory / "gray.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            result = verify(path, ROOT, "d" * 40)
            self.assertEqual(result["featureChangeCount"], 4)

    def test_rejects_unsafe_feature_order(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._evidence(directory)
            value["featureChanges"][2], value["featureChanges"][3] = (
                value["featureChanges"][3],
                value["featureChanges"][2],
            )
            path = directory / "gray.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(GrayEvidenceError):
                verify(path, ROOT, "d" * 40)

    def test_rejects_plain_text_monitoring_evidence(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._evidence(directory)
            loaded = directory / "prometheus-rules-alerts.json"
            loaded.write_text("SUCCESS: rules loaded\n", encoding="utf-8")
            value["monitoring"]["loadEvidence"] = self._artifact(loaded)
            path = directory / "gray.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaisesRegex(GrayEvidenceError, "Prometheus"):
                verify(path, ROOT, "d" * 40)

    def test_rejects_monitoring_evidence_sha_mismatch(self):
        (ROOT / "output").mkdir(exist_ok=True)
        for scenario in ("candidate", "artifact"):
            with self.subTest(scenario=scenario):
                with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
                    directory = Path(temporary)
                    value = self._evidence(directory)
                    if scenario == "candidate":
                        value["monitoring"]["candidateRulesSha256"] = "0" * 64
                        message = "candidate alert-rule SHA"
                    else:
                        value["monitoring"]["loadEvidence"]["sha256"] = "0" * 64
                        message = "sha256"
                    path = directory / "gray.json"
                    path.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaisesRegex(GrayEvidenceError, message):
                        verify(path, ROOT, "d" * 40)

    def test_rejects_empty_or_reused_reconciliation_evidence(self):
        (ROOT / "output").mkdir(exist_ok=True)
        scenarios = (
            "empty-issues",
            "boolean-count",
            "same-artifact",
            "hardlink-artifact",
        )
        for scenario in scenarios:
            with self.subTest(scenario=scenario):
                with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
                    directory = Path(temporary)
                    value = self._evidence(directory)
                    if scenario in {"empty-issues", "boolean-count"}:
                        before = Path(value["reconciliation"]["before"]["path"])
                        result = json.loads(before.read_text(encoding="utf-8"))
                        if scenario == "empty-issues":
                            result["issues"] = {}
                            message = "all expected issues"
                        else:
                            result["issueCount"] = False
                            message = "all expected issues"
                        before.write_text(json.dumps(result), encoding="utf-8")
                        value["reconciliation"]["before"] = self._artifact(before)
                    elif scenario == "same-artifact":
                        value["reconciliation"]["after"] = dict(
                            value["reconciliation"]["before"]
                        )
                        message = "distinct artifacts"
                    else:
                        before = Path(value["reconciliation"]["before"]["path"])
                        hardlink = directory / "before-hardlink.json"
                        hardlink.hardlink_to(before)
                        value["reconciliation"]["after"] = self._artifact(hardlink)
                        message = "distinct artifacts"
                    path = directory / "gray.json"
                    path.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaisesRegex(GrayEvidenceError, message):
                        verify(path, ROOT, "d" * 40)

    def test_rejects_secret_aliases_and_unexpected_evidence_fields(self):
        (ROOT / "output").mkdir(exist_ok=True)
        for field in ("authorization", "cookie", "apiKey", "credential", "sessionId"):
            with self.subTest(field=field):
                with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
                    directory = Path(temporary)
                    value = self._evidence(directory)
                    value[field] = "must-not-be-persisted"
                    path = directory / "gray.json"
                    path.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaisesRegex(GrayEvidenceError, "forbidden"):
                        verify(path, ROOT, "d" * 40)

        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._evidence(directory)
            value["unexpected"] = "extra"
            path = directory / "gray.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaisesRegex(GrayEvidenceError, "unexpected"):
                verify(path, ROOT, "d" * 40)

    def test_rejects_unsuccessful_prometheus_response(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._evidence(directory)
            loaded = directory / "prometheus-rules-alerts.json"
            response = self._prometheus_rules()
            response["status"] = "error"
            self._write_loaded_rules(loaded, response, value)
            path = directory / "gray.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaisesRegex(GrayEvidenceError, "status must be success"):
                verify(path, ROOT, "d" * 40)

    def test_rejects_missing_or_duplicate_target_group(self):
        (ROOT / "output").mkdir(exist_ok=True)
        scenarios = ("missing", "duplicate", "unrelated-extra")
        for scenario in scenarios:
            with self.subTest(scenario=scenario):
                with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
                    directory = Path(temporary)
                    value = self._evidence(directory)
                    loaded = directory / "prometheus-rules-alerts.json"
                    response = self._prometheus_rules()
                    groups = response["data"]["groups"]
                    if scenario == "missing":
                        groups[0]["name"] = "another-group"
                    elif scenario == "duplicate":
                        groups.append(dict(groups[0]))
                    else:
                        unrelated = dict(groups[0])
                        unrelated["name"] = "another-group"
                        groups.append(unrelated)
                    self._write_loaded_rules(loaded, response, value)
                    path = directory / "gray.json"
                    path.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaisesRegex(
                        GrayEvidenceError, "only one.*group"
                    ):
                        verify(path, ROOT, "d" * 40)

    def test_rejects_boolean_feature_audit_record_id(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._evidence(directory)
            value["featureChanges"][0]["auditRecordId"] = True
            path = directory / "gray.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaisesRegex(GrayEvidenceError, "positive id"):
                verify(path, ROOT, "d" * 40)

    def test_rejects_nontext_boolean_duplicate_or_nonincreasing_fields(self):
        (ROOT / "output").mkdir(exist_ok=True)
        scenarios = ("schema-bool", "operator-object", "duplicate-audit", "same-time")
        for scenario in scenarios:
            with self.subTest(scenario=scenario):
                with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
                    directory = Path(temporary)
                    value = self._evidence(directory)
                    if scenario == "schema-bool":
                        value["schemaVersion"] = True
                        message = "schema/releaseId"
                    elif scenario == "operator-object":
                        value["featureChanges"][0]["operator"] = {"name": "not-text"}
                        message = "must be text"
                    elif scenario == "duplicate-audit":
                        value["featureChanges"][1]["auditRecordId"] = 1
                        message = "must be unique"
                    else:
                        value["featureChanges"][1]["changedAt"] = value[
                            "featureChanges"
                        ][0]["changedAt"]
                        message = "must be increasing"
                    path = directory / "gray.json"
                    path.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaisesRegex(GrayEvidenceError, message):
                        verify(path, ROOT, "d" * 40)

    def test_rejects_duplicate_missing_or_extra_alert(self):
        (ROOT / "output").mkdir(exist_ok=True)
        scenarios = ("duplicate", "missing", "extra")
        for scenario in scenarios:
            with self.subTest(scenario=scenario):
                with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
                    directory = Path(temporary)
                    value = self._evidence(directory)
                    loaded = directory / "prometheus-rules-alerts.json"
                    response = self._prometheus_rules()
                    rules = response["data"]["groups"][0]["rules"]
                    if scenario == "duplicate":
                        rules[-1] = dict(rules[0])
                        message = "duplicate alerts"
                    elif scenario == "missing":
                        rules.pop()
                        message = "alert set differs"
                    else:
                        rules.append(
                            {
                                "name": "UnexpectedAlert",
                                "type": "alerting",
                                "health": "ok",
                            }
                        )
                        message = "alert set differs"
                    self._write_loaded_rules(loaded, response, value)
                    path = directory / "gray.json"
                    path.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaisesRegex(GrayEvidenceError, message):
                        verify(path, ROOT, "d" * 40)

    def test_rejects_non_alerting_or_unhealthy_rule(self):
        (ROOT / "output").mkdir(exist_ok=True)
        scenarios = ("recording", "unhealthy")
        for scenario in scenarios:
            with self.subTest(scenario=scenario):
                with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
                    directory = Path(temporary)
                    value = self._evidence(directory)
                    loaded = directory / "prometheus-rules-alerts.json"
                    response = self._prometheus_rules()
                    rule = response["data"]["groups"][0]["rules"][0]
                    if scenario == "recording":
                        rule["type"] = "recording"
                        message = "type=alerting"
                    else:
                        rule["health"] = "err"
                        message = "health=ok"
                    self._write_loaded_rules(loaded, response, value)
                    path = directory / "gray.json"
                    path.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaisesRegex(GrayEvidenceError, message):
                        verify(path, ROOT, "d" * 40)

    def test_rejects_missing_or_drifted_loaded_query(self):
        (ROOT / "output").mkdir(exist_ok=True)
        scenarios = ("missing", "drifted")
        for scenario in scenarios:
            with self.subTest(scenario=scenario):
                with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
                    directory = Path(temporary)
                    value = self._evidence(directory)
                    loaded = directory / "prometheus-rules-alerts.json"
                    response = self._prometheus_rules()
                    rule = response["data"]["groups"][0]["rules"][0]
                    if scenario == "missing":
                        rule.pop("query")
                        message = "must include query"
                    else:
                        rule["query"] = "vector(1)"
                        message = "query differs"
                    self._write_loaded_rules(loaded, response, value)
                    path = directory / "gray.json"
                    path.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaisesRegex(GrayEvidenceError, message):
                        verify(path, ROOT, "d" * 40)

    def test_rejects_candidate_rule_contract_drift(self):
        rules = ROOT / "ops" / "monitoring" / "new-business-alert-rules.yml"
        source = rules.read_text(encoding="utf-8")
        scenarios = {
            "query": (
                "expr: sum(increase(erp_inventory_customer_card_scope_denied_total[15m])) > 10",
                "expr: vector(0)",
            ),
            "duration": ("for: 5m", "for: 1m"),
            "severity": ("severity: warning", "severity: critical"),
            "extra-semantics": (
                "    interval: 1m",
                "    interval: 1m\n    query_offset: 24h",
            ),
        }
        for scenario, (before, after) in scenarios.items():
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as temporary:
                candidate = Path(temporary) / "rules.yml"
                candidate.write_text(source.replace(before, after, 1), encoding="utf-8")
                with self.assertRaisesRegex(GrayEvidenceError, "pinned release contract"):
                    _candidate_alerts(candidate)

    def test_monitoring_shell_cannot_import_contract_from_cwd(self):
        script = ROOT / "scripts" / "verify-new-business-monitoring.sh"
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "verify_new_business_gray_evidence.py").write_text(
                "class GrayEvidenceError(ValueError):\n"
                "    pass\n\n"
                "def _candidate_alerts(path):\n"
                "    return {}\n",
                encoding="utf-8",
            )
            for module_name in ("pathlib.py", "json.py"):
                (directory / module_name).write_text(
                    "import os\nos._exit(0)\n",
                    encoding="utf-8",
                )
            result = subprocess.run(
                ["bash", str(script)],
                cwd=directory,
                capture_output=True,
                text=True,
                check=False,
                env={**os.environ, "PYTHONPATH": str(directory)},
            )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("active monitoring contract: 6 alerts", result.stdout)
        self.assertNotIn("active monitoring contract: 0 alerts", result.stdout)

    def test_rejects_active_duration_label_or_nonminimal_api_evidence(self):
        (ROOT / "output").mkdir(exist_ok=True)
        scenarios = (
            "active",
            "duration",
            "duration-bool",
            "labels",
            "interval",
            "extra",
        )
        for scenario in scenarios:
            with self.subTest(scenario=scenario):
                with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
                    directory = Path(temporary)
                    value = self._evidence(directory)
                    loaded = directory / "prometheus-rules-alerts.json"
                    response = self._prometheus_rules()
                    group = response["data"]["groups"][0]
                    rule = group["rules"][0]
                    if scenario == "active":
                        rule["state"] = "pending"
                        message = "inactive"
                    elif scenario == "duration":
                        rule["duration"] += 60
                        message = "duration differs"
                    elif scenario == "duration-bool":
                        rule["duration"] = False
                        message = "duration differs"
                    elif scenario == "labels":
                        rule["labels"]["severity"] = "critical"
                        message = "labels differ"
                    elif scenario == "interval":
                        group["interval"] = 30
                        message = "interval"
                    else:
                        rule["alerts"] = []
                        message = "fields are not minimized"
                    self._write_loaded_rules(loaded, response, value)
                    path = directory / "gray.json"
                    path.write_text(json.dumps(value), encoding="utf-8")
                    with self.assertRaisesRegex(GrayEvidenceError, message):
                        verify(path, ROOT, "d" * 40)


if __name__ == "__main__":
    unittest.main()
