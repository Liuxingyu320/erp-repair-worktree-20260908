#!/usr/bin/env python3

import json
import tempfile
import unittest
from pathlib import Path

from parse_new_business_reconciliation import (
    EXPECTED_SQL_SHA256,
    FINAL_SUMMARY,
    ReconciliationError,
    parse,
    write_prometheus,
)


ROOT = Path(__file__).resolve().parents[1]
SQL = ROOT / "scripts" / "new-business-daily-reconciliation.sql"


class ReconciliationParserTests(unittest.TestCase):
    def test_zero_issue_output_passes_without_retaining_rows(self):
        with tempfile.TemporaryDirectory() as temporary:
            raw = Path(temporary) / "raw.tsv"
            raw.write_text(
                "TRANSFER_DISCREPANCY_STATUS_SUMMARY\tbusinessRowValue-should-not-leak\t2\n"
                + FINAL_SUMMARY
                + "\t0\t0\t0\n",
                encoding="utf-8",
            )
            result = parse(SQL, raw)
            self.assertEqual(result["status"], "passed")
            self.assertEqual(result["issueCount"], 0)
            self.assertEqual(result["reconciliationSqlSha256"], EXPECTED_SQL_SHA256)
            self.assertNotIn("businessRowValue-should-not-leak", json.dumps(result))

    def test_rejects_commented_or_semantically_drifted_candidate_sql(self):
        source = SQL.read_text(encoding="utf-8")
        scenarios = {
            "commented-query": source.replace(
                "SELECT 'STORE_RETURN_DIRECTION_OR_APPROVAL_INVALID' AS issue_code,",
                "/* SELECT 'STORE_RETURN_DIRECTION_OR_APPROVAL_INVALID' AS issue_code, */",
                1,
            ),
            "constant-false": source.replace(
                "WHERE o.transfer_type = 'store_return'",
                "WHERE o.transfer_type = 'store_return' AND 1=0",
                1,
            ),
        }
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            raw = directory / "raw.tsv"
            raw.write_text(FINAL_SUMMARY + "\t0\t0\t0\n", encoding="utf-8")
            for scenario, candidate in scenarios.items():
                with self.subTest(scenario=scenario):
                    sql = directory / f"{scenario}.sql"
                    sql.write_text(candidate, encoding="utf-8")
                    with self.assertRaisesRegex(ReconciliationError, "pinned"):
                        parse(sql, raw)

    def test_counts_issue_codes_but_not_business_columns(self):
        with tempfile.TemporaryDirectory() as temporary:
            raw = Path(temporary) / "raw.tsv"
            raw.write_text(
                "CUSTOMER_SERVICE_RECORD_SCOPE_MISMATCH\t42\t测试客户\n"
                "CUSTOMER_SERVICE_RECORD_SCOPE_MISMATCH\t43\t另一客户\n"
                + FINAL_SUMMARY
                + "\t0\t0\t0\n",
                encoding="utf-8",
            )
            result = parse(SQL, raw)
            self.assertEqual(result["issueCount"], 2)
            self.assertEqual(
                result["issues"]["CUSTOMER_SERVICE_RECORD_SCOPE_MISMATCH"],
                2,
            )
            self.assertNotIn("测试客户", json.dumps(result, ensure_ascii=False))

    def test_requires_final_summary(self):
        with tempfile.TemporaryDirectory() as temporary:
            raw = Path(temporary) / "raw.tsv"
            raw.write_text("", encoding="utf-8")
            with self.assertRaises(ReconciliationError):
                parse(SQL, raw)

    def test_writes_atomic_prometheus_counts(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "result.prom"
            write_prometheus(
                output,
                {
                    "status": "failed",
                    "issues": {"TRANSFER_DISCREPANCY_OVERDUE_24H": 3},
                },
            )
            text = output.read_text(encoding="utf-8")
            self.assertIn("reconciliation_success 0", text)
            self.assertIn('issue_code="TRANSFER_DISCREPANCY_OVERDUE_24H"} 3', text)


if __name__ == "__main__":
    unittest.main()
