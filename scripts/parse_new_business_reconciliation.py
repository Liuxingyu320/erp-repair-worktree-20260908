#!/usr/bin/env python3
"""Reduce MySQL reconciliation output to counts without retaining row data."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path


ISSUE_PATTERN = re.compile(r"'([A-Z0-9_]+)'\s+AS\s+issue_code", re.I)
SUMMARY_PATTERN = re.compile(r"'([A-Z0-9_]+)'\s+AS\s+summary_code", re.I)
FINAL_SUMMARY = "NEW_BUSINESS_RECONCILIATION_SUMMARY"
EXPECTED_SQL_SHA256 = "2ee23f4352dabf260f58efed825dce6338932580a55ba33e1d6b37333b8a8468"
EXPECTED_ISSUE_CODES = frozenset(
    {
        "CUSTOMER_SERVICE_AUDIT_SCOPE_MISMATCH",
        "CUSTOMER_SERVICE_PROFILE_CARDINALITY",
        "CUSTOMER_SERVICE_RECORD_AUDIT_MISSING",
        "CUSTOMER_SERVICE_RECORD_SCOPE_MISMATCH",
        "HEALTH_CERTIFICATE_CURRENT_DUPLICATE",
        "HEALTH_CERTIFICATE_CURRENT_INVALID",
        "HEALTH_CERTIFICATE_PENDING_STALE",
        "STORE_RETURN_DIRECTION_OR_APPROVAL_INVALID",
        "STORE_RETURN_SHIPMENT_QUANTITY_CONSERVATION",
        "TRANSFER_DISCREPANCY_DETAIL_INVALID",
        "TRANSFER_DISCREPANCY_MASTER_DETAIL_MISMATCH",
        "TRANSFER_DISCREPANCY_OVERDUE_24H",
    }
)


class ReconciliationError(ValueError):
    pass


def parse(sql_path: Path, raw_path: Path) -> dict:
    sql_bytes = sql_path.read_bytes()
    sql_sha256 = hashlib.sha256(sql_bytes).hexdigest()
    if sql_sha256 != EXPECTED_SQL_SHA256:
        raise ReconciliationError(
            "reconciliation SQL differs from the pinned release contract"
        )
    try:
        sql = sql_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ReconciliationError("reconciliation SQL must be UTF-8") from exc
    issue_codes = sorted(set(ISSUE_PATTERN.findall(sql)))
    summary_codes = set(SUMMARY_PATTERN.findall(sql))
    if set(issue_codes) != EXPECTED_ISSUE_CODES or FINAL_SUMMARY not in summary_codes:
        raise ReconciliationError(
            "reconciliation SQL issue/summary contract is incomplete"
        )
    counts = {code: 0 for code in issue_codes}
    seen_summaries = set()
    with raw_path.open("r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            first = line.rstrip("\r\n").split("\t", 1)[0]
            if first in counts:
                counts[first] += 1
            elif first in summary_codes:
                seen_summaries.add(first)
    if FINAL_SUMMARY not in seen_summaries:
        raise ReconciliationError(
            "MySQL output is incomplete: final reconciliation summary was not returned"
        )
    total = sum(counts.values())
    return {
        "schemaVersion": 1,
        "releaseId": "active-business-20260730",
        "kind": "daily-reconciliation",
        "reconciliationSqlSha256": sql_sha256,
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "status": "passed" if total == 0 else "failed",
        "issueCount": total,
        "issues": counts,
    }


def write_prometheus(path: Path, result: dict) -> None:
    now = int(time.time())
    lines = [
        "# HELP erp_new_business_reconciliation_success Whether the latest reconciliation had zero issues.",
        "# TYPE erp_new_business_reconciliation_success gauge",
        f"erp_new_business_reconciliation_success {1 if result['status'] == 'passed' else 0}",
        "# HELP erp_new_business_reconciliation_last_run_timestamp_seconds Unix timestamp of the latest completed reconciliation.",
        "# TYPE erp_new_business_reconciliation_last_run_timestamp_seconds gauge",
        f"erp_new_business_reconciliation_last_run_timestamp_seconds {now}",
        "# HELP erp_new_business_reconciliation_issue_total Current issue rows grouped by stable issue code.",
        "# TYPE erp_new_business_reconciliation_issue_total gauge",
    ]
    for code, count in sorted(result["issues"].items()):
        lines.append(
            f'erp_new_business_reconciliation_issue_total{{issue_code="{code}"}} {count}'
        )
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(
        dir=path.parent, prefix=path.name + ".", suffix=".tmp"
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sql", required=True)
    parser.add_argument("--raw", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--prometheus-output")
    args = parser.parse_args()
    try:
        result = parse(Path(args.sql), Path(args.raw))
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(result, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        if args.prometheus_output:
            write_prometheus(Path(args.prometheus_output), result)
    except (OSError, ReconciliationError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2
    print(f"[INFO] reconciliation summary: {args.output}")
    if result["issueCount"]:
        print(
            f"[FAIL] reconciliation found {result['issueCount']} issue row(s)",
            file=sys.stderr,
        )
        return 1
    print("[PASS] reconciliation returned zero issue rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
