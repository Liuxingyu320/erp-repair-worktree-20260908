#!/usr/bin/env python3
"""Measure secret-free P50/P95/P99 for the four active critical read paths."""

from __future__ import annotations

import argparse
import json
import math
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

from new_business_uat_common import UatConfigError, load_config


PERFORMANCE_SCENARIOS = {
    "health-certificate-expiry-list",
    "customer-service-card-search",
    "transfer-workbench",
    "transfer-discrepancy-list",
}
MAX_RESPONSE_BYTES = 5 * 1024 * 1024


def _number(value: Any, label: str, minimum: int, maximum: int) -> int:
    if isinstance(value, bool):
        raise UatConfigError(f"{label} must be an integer")
    try:
        result = int(value)
    except (TypeError, ValueError) as exc:
        raise UatConfigError(f"{label} must be an integer") from exc
    if result < minimum or result > maximum:
        raise UatConfigError(f"{label} must be within [{minimum}, {maximum}]")
    return result


def _percentile(values: list[float], percentile: int) -> float:
    ordered = sorted(values)
    index = max(0, math.ceil(len(ordered) * percentile / 100) - 1)
    return round(ordered[index], 1)


def _validate_probe(probe: Any, config: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(probe, dict):
        raise UatConfigError("every performanceProbe must be an object")
    probe_id = str(probe.get("id") or "")
    if probe_id not in PERFORMANCE_SCENARIOS:
        raise UatConfigError(f"unknown performanceProbe id: {probe_id!r}")
    path = str(probe.get("path") or "")
    parsed = urlsplit(path)
    if (
        not path.startswith("/")
        or parsed.scheme
        or parsed.netloc
        or parsed.fragment
        or "\\" in path
    ):
        raise UatConfigError(f"{probe_id}: path must be a same-origin path")
    actor = probe.get("actor")
    context = probe.get("context")
    if actor not in config["actors"]:
        raise UatConfigError(f"{probe_id}: actor is unknown")
    if context not in config["contexts"]:
        raise UatConfigError(f"{probe_id}: context is unknown")
    return {
        **probe,
        "id": probe_id,
        "path": path,
        "publicPath": parsed.path,
        "samples": _number(probe.get("samples", 50), f"{probe_id}.samples", 30, 500),
        "warmup": _number(probe.get("warmup", 5), f"{probe_id}.warmup", 0, 50),
        "intervalMs": _number(
            probe.get("intervalMs", 100), f"{probe_id}.intervalMs", 0, 5000
        ),
        "timeoutSeconds": _number(
            probe.get("timeoutSeconds", 30),
            f"{probe_id}.timeoutSeconds",
            1,
            120,
        ),
    }


def _perform(config: dict[str, Any], probe: dict[str, Any]) -> tuple[bool, float]:
    headers = {
        "Accept": "application/json",
        "Authorization": "Bearer " + config["actors"][probe["actor"]]["_token"],
        "Dept-NumId": str(config["contexts"][probe["context"]]["deptId"]),
        "User-Agent": "ERP-New-Business-Performance/1",
    }
    request = urllib.request.Request(
        config["apiBaseUrl"] + probe["path"], method="GET", headers=headers
    )
    started = time.monotonic()
    try:
        with urllib.request.urlopen(
            request, timeout=probe["timeoutSeconds"]
        ) as response:
            status = response.status
            body = response.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as exc:
        status = exc.code
        body = exc.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.URLError:
        return False, round((time.monotonic() - started) * 1000, 1)
    duration = round((time.monotonic() - started) * 1000, 1)
    if len(body) > MAX_RESPONSE_BYTES:
        return False, duration
    app_code = None
    if body:
        try:
            value = json.loads(body.decode("utf-8"))
            app_code = value.get("code") if isinstance(value, dict) else None
        except (UnicodeDecodeError, json.JSONDecodeError):
            pass
    try:
        app_success = app_code is None or int(app_code) == 200
    except (TypeError, ValueError):
        app_success = False
    success = 200 <= status < 300 and app_success
    return success, duration


def run(config: dict[str, Any], phase: str, git_commit: str) -> dict[str, Any]:
    raw_probes = config.get("performanceProbes")
    if not isinstance(raw_probes, list):
        raise UatConfigError("performanceProbes must be an array")
    probes = [_validate_probe(probe, config) for probe in raw_probes]
    ids = [probe["id"] for probe in probes]
    if len(ids) != len(PERFORMANCE_SCENARIOS) or set(ids) != PERFORMANCE_SCENARIOS:
        raise UatConfigError(
            "performanceProbes must contain each of the four active critical scenarios exactly once"
        )

    results = []
    failed = 0
    for probe in probes:
        for _ in range(probe["warmup"]):
            _perform(config, probe)
            if probe["intervalMs"]:
                time.sleep(probe["intervalMs"] / 1000)
        durations: list[float] = []
        errors = 0
        for _ in range(probe["samples"]):
            success, duration = _perform(config, probe)
            if success:
                durations.append(duration)
            else:
                errors += 1
            if probe["intervalMs"]:
                time.sleep(probe["intervalMs"] / 1000)
        status = "passed" if errors == 0 and len(durations) == probe["samples"] else "failed"
        if status == "failed":
            failed += 1
        result = {
            "id": probe["id"],
            "route": probe["publicPath"],
            "actor": probe["actor"],
            "context": probe["context"],
            "sampleCount": probe["samples"],
            "successCount": len(durations),
            "errorCount": errors,
            "status": status,
        }
        if durations:
            result.update(
                {
                    "p50Ms": _percentile(durations, 50),
                    "p95Ms": _percentile(durations, 95),
                    "p99Ms": _percentile(durations, 99),
                    "maxMs": round(max(durations), 1),
                }
            )
        results.append(result)

    return {
        "schemaVersion": 1,
        "releaseId": config["releaseId"],
        "kind": "api-performance",
        "phase": phase,
        "gitCommit": git_commit,
        "runId": config["runId"],
        "database": config["database"],
        "baseUrl": config["apiBaseUrl"],
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "status": "passed" if failed == 0 else "failed",
        "scenarioCount": len(results),
        "failedCount": failed,
        "scenarios": results,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--phase", required=True, choices=("before", "after"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output_root = (root / "output").resolve()
    output = Path(args.output).expanduser().resolve()
    try:
        output.relative_to(output_root)
    except ValueError:
        print("[FAIL] performance evidence output must be under output/", file=sys.stderr)
        return 2
    try:
        config = load_config(args.config, root)
        commit = subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
        ).strip()
        evidence = run(config, args.phase, commit)
    except (OSError, subprocess.CalledProcessError, UatConfigError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(output.name + ".tmp")
    temporary.write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    os.replace(temporary, output)
    print(f"[INFO] response-free performance evidence: {output}")
    if evidence["status"] != "passed":
        print(f"[FAIL] {evidence['failedCount']} performance scenario(s) failed", file=sys.stderr)
        return 1
    print(f"[PASS] {evidence['scenarioCount']} performance scenario(s) measured")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
