#!/usr/bin/env python3
"""Classify sensitive operation-log rows without printing payload values."""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable, TextIO


FIELDS = ("oper_param", "json_result", "error_msg")
PATTERNS = {
    "sensitive_key": re.compile(
        r"(?i)(signaturedataurl|password|passwd|secret|token|credential|bankaccount|bankcard|"
        r"idnumber|idcard|privatekey|accesskey|phonenumber|emergencycontact)"
    ),
    "data_url_or_base64": re.compile(r"(?i);base64,|(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{128,}={0,2}"),
    "jwt_or_private_key": re.compile(
        r"(?i)eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|-----BEGIN (?:RSA )?PRIVATE KEY-----"
    ),
    "long_identity_or_bank_number": re.compile(r"(?<!\d)\d{15,19}(?!\d)"),
    "private_path": re.compile(r"(?i)(/Users/|/home/|/root/|/var/[^/]+/|[A-Z]:\\Users\\)"),
}


def classify_record(record: dict) -> set[str]:
    text = " ".join(str(record.get(field) or "") for field in FIELDS)
    return {name for name, pattern in PATTERNS.items() if pattern.search(text)}


def summarize(records: Iterable[dict]) -> dict:
    scanned = 0
    sensitive_rows = 0
    ids: list[int] = []
    category_counts: Counter[str] = Counter()
    for record in records:
        scanned += 1
        categories = classify_record(record)
        if not categories:
            continue
        sensitive_rows += 1
        category_counts.update(categories)
        try:
            ids.append(int(record.get("oper_id")))
        except (TypeError, ValueError):
            pass
    return {
        "schemaVersion": 1,
        "scannedRows": scanned,
        "sensitiveRows": sensitive_rows,
        "minimumSensitiveOperId": min(ids) if ids else None,
        "maximumSensitiveOperId": max(ids) if ids else None,
        "categoryCounts": {name: category_counts.get(name, 0) for name in sorted(PATTERNS)},
    }


def read_jsonl(handle: TextIO) -> Iterable[dict]:
    for line_number, line in enumerate(handle, 1):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid JSON on line {line_number}") from exc
        if not isinstance(record, dict):
            raise ValueError(f"line {line_number} is not an object")
        yield record


def read_csv(handle: TextIO) -> Iterable[dict]:
    reader = csv.DictReader(handle)
    required = {"oper_id", *FIELDS}
    if not required.issubset(reader.fieldnames or []):
        raise ValueError("CSV header must contain oper_id, oper_param, json_result and error_msg")
    yield from reader


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="-", help="JSONL/CSV input file, or - for stdin")
    parser.add_argument("--format", choices=("jsonl", "csv"), default="jsonl")
    parser.add_argument("--fail-on-sensitive", action="store_true")
    args = parser.parse_args()

    handle: TextIO
    if args.input == "-":
        handle = sys.stdin
        close = False
    else:
        handle = Path(args.input).open("r", encoding="utf-8", newline="")
        close = True
    try:
        records = read_csv(handle) if args.format == "csv" else read_jsonl(handle)
        report = summarize(records)
    except ValueError as exc:
        print(f"classification input error: {exc}", file=sys.stderr)
        return 2
    finally:
        if close:
            handle.close()

    json.dump(report, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")
    return 3 if args.fail_on_sensitive and report["sensitiveRows"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
