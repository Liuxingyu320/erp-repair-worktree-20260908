#!/usr/bin/env python3

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts" / "qa"))

from run_new_business_performance_probe import _percentile  # noqa: E402
from verify_new_business_performance_evidence import (  # noqa: E402
    PerformanceEvidenceError,
    SCENARIOS,
    verify,
)


class PerformanceEvidenceTests(unittest.TestCase):
    candidate = "d" * 40
    baseline = "b" * 40
    when = "2026-07-14T20:00:00+08:00"

    def _sha(self, path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def _artifact(self, path: Path):
        return {
            "path": str(path),
            "sha256": self._sha(path),
            "status": "passed",
            "sanitized": True,
        }

    def _write_probe(self, path: Path, phase: str, commit: str, p95: float):
        value = {
            "schemaVersion": 1,
            "releaseId": "new-business-20260714",
            "kind": "api-performance",
            "phase": phase,
            "gitCommit": commit,
            "status": "passed",
            "scenarios": [
                {
                    "id": scenario_id,
                    "sampleCount": 50,
                    "errorCount": 0,
                    "status": "passed",
                    "p95Ms": p95,
                }
                for scenario_id in SCENARIOS
            ],
        }
        path.write_text(json.dumps(value), encoding="utf-8")

    def _evidence(self, directory: Path):
        row_counts = directory / "row-counts.txt"
        row_counts.write_text("sanitized aggregate counts only\n", encoding="utf-8")
        explain_before = directory / "explain-before.txt"
        explain_before.write_text("EXPLAIN FORMAT=JSON (sanitized)\n", encoding="utf-8")
        explain_after = directory / "explain-after.txt"
        explain_after.write_text("EXPLAIN FORMAT=JSON (sanitized)\n", encoding="utf-8")
        slow_before = directory / "slow-before.txt"
        slow_before.write_text("slow query digest summary before\n", encoding="utf-8")
        slow_after = directory / "slow-after.txt"
        slow_after.write_text("slow query digest summary after\n", encoding="utf-8")
        load_before = directory / "load-before.json"
        load_after = directory / "load-after.json"
        self._write_probe(load_before, "before", self.baseline, 800)
        self._write_probe(load_after, "after", self.candidate, 700)

        datasets = {}
        for dataset, _ in SCENARIOS.values():
            datasets[dataset] = {
                "fixtureRows": 10000,
                "productionReferenceRows": 10000,
                "referenceCapturedAt": self.when,
                "rowCountEvidence": self._artifact(row_counts),
            }
        endpoints = {
            "health-certificate-expiry-list": "GET /system/hr/health-certificate/expiry",
            "customer-service-card-search": "GET /inventory/customer/service-card/list",
            "transfer-workbench": "GET /inventory/transfer/list",
            "transfer-discrepancy-list": "GET /inventory/transfer/discrepancy/list",
        }
        scenarios = []
        for scenario_id, (dataset, maximum) in SCENARIOS.items():
            scenarios.append(
                {
                    "id": scenario_id,
                    "dataset": dataset,
                    "endpoint": endpoints[scenario_id],
                    "queryDigestSha256": "a" * 64,
                    "sampleCountBefore": 50,
                    "sampleCountAfter": 50,
                    "p95BeforeMs": 800,
                    "p95AfterMs": 700,
                    "budgetMs": maximum,
                    "status": "passed",
                    "artifacts": {
                        "explainBefore": self._artifact(explain_before),
                        "explainAfter": self._artifact(explain_after),
                        "slowQueryBefore": self._artifact(slow_before),
                        "slowQueryAfter": self._artifact(slow_after),
                        "loadBefore": self._artifact(load_before),
                        "loadAfter": self._artifact(load_after),
                    },
                    "indexChanges": [],
                    "reviewer": "Performance Reviewer",
                    "completedAt": self.when,
                }
            )
        return {
            "schemaVersion": 1,
            "releaseId": "new-business-20260714",
            "candidateCommit": self.candidate,
            "baselineCommit": self.baseline,
            "runId": "performance-unit-001",
            "completedAt": self.when,
            "dataHandling": {
                "syntheticOrAnonymized": True,
                "containsProductionPersonalData": False,
                "responseBodiesPersisted": False,
            },
            "datasets": datasets,
            "scenarios": scenarios,
            "signoff": {
                "backendOwner": "Backend Owner",
                "databaseOwner": "Database Owner",
                "qaOwner": "QA Owner",
                "approvedAt": self.when,
                "decision": "approved",
            },
        }

    def test_percentile_uses_nearest_rank(self):
        self.assertEqual(_percentile([1, 2, 3, 4, 5], 95), 5)

    def test_accepts_artifact_backed_performance_result(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._evidence(directory)
            path = directory / "performance.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            result = verify(path, ROOT, self.candidate)
            self.assertEqual(result["scenarioCount"], 4)

    def test_rejects_fixture_far_below_reference_volume(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._evidence(directory)
            value["datasets"]["transferOrders"]["fixtureRows"] = 100
            path = directory / "performance.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(PerformanceEvidenceError):
                verify(path, ROOT, self.candidate)

    def test_rejects_self_declared_p95_that_differs_from_probe(self):
        (ROOT / "output").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / "output") as temporary:
            directory = Path(temporary)
            value = self._evidence(directory)
            value["scenarios"][0]["p95AfterMs"] = 650
            path = directory / "performance.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(PerformanceEvidenceError):
                verify(path, ROOT, self.candidate)


if __name__ == "__main__":
    unittest.main()
