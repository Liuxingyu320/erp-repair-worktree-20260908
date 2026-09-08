#!/usr/bin/env python3
"""Run declarative real-HTTP UAT without persisting response bodies or tokens."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from new_business_uat_common import UatConfigError, load_config, public_config


MAX_RESPONSE_BYTES = 5 * 1024 * 1024
WRITE_METHODS = {"POST", "PUT", "PATCH", "DELETE"}


def _json_body(body: bytes) -> Any:
    if not body:
        return None
    try:
        return json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None


def _walk_keys(value: Any):
    if isinstance(value, dict):
        for key, child in value.items():
            yield str(key), child
            yield from _walk_keys(child)
    elif isinstance(value, list):
        for child in value:
            yield from _walk_keys(child)


def _perform(
    config: dict[str, Any], probe: dict[str, Any], body_override: Any = None
) -> dict[str, Any]:
    method = str(probe.get("method") or "GET").upper()
    url = config["apiBaseUrl"] + str(probe["path"])
    actor_name = probe.get("actor")
    headers = {
        "Accept": "application/json",
        "User-Agent": "ERP-New-Business-UAT/1",
    }
    if actor_name:
        headers["Authorization"] = (
            "Bearer " + config["actors"][actor_name]["_token"]
        )
    context_name = probe.get("context")
    if context_name:
        headers["Dept-NumId"] = str(
            config["contexts"][context_name]["deptId"]
        )
    payload = probe.get("body") if body_override is None else body_override
    request_body = None
    if payload is not None:
        request_body = json.dumps(
            payload, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")
        headers["Content-Type"] = "application/json;charset=utf-8"
    request = urllib.request.Request(
        url, data=request_body, method=method, headers=headers
    )
    started = time.monotonic()
    try:
        with urllib.request.urlopen(
            request, timeout=float(probe.get("timeoutSeconds", 20))
        ) as response:
            status = response.status
            body = response.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as exc:
        status = exc.code
        body = exc.read(MAX_RESPONSE_BYTES + 1)
    if len(body) > MAX_RESPONSE_BYTES:
        raise AssertionError(
            f"{probe['id']}: response exceeds {MAX_RESPONSE_BYTES} bytes"
        )
    parsed = _json_body(body)
    app_code = parsed.get("code") if isinstance(parsed, dict) else None
    return {
        "httpStatus": int(status),
        "appCode": app_code,
        "durationMs": round((time.monotonic() - started) * 1000),
        "bodySha256": hashlib.sha256(body).hexdigest(),
        "_parsed": parsed,
    }


def _assert_one(probe: dict[str, Any], result: dict[str, Any]) -> None:
    parsed = result["_parsed"]
    http_status = result["httpStatus"]
    app_code = result["appCode"]
    success = 200 <= http_status < 300 and (
        app_code is None or int(app_code) == 200
    )
    if probe.get("expect") == "success" and not success:
        raise AssertionError(
            f"expected success, got HTTP {http_status}/app {app_code}"
        )
    if probe.get("expect") == "failure":
        allowed_http = {
            int(value) for value in probe.get("allowedHttpStatuses", [])
        }
        allowed_app = {
            int(value) for value in probe.get("allowedAppCodes", [])
        }
        matched = http_status in allowed_http or (
            app_code is not None and int(app_code) in allowed_app
        )
        if success or not matched:
            raise AssertionError(
                f"expected declared failure {sorted(allowed_http)}/"
                f"{sorted(allowed_app)}, got HTTP {http_status}/app {app_code}"
            )

    if parsed is None:
        if probe.get("forbiddenKeys") or probe.get("requiredKeys"):
            raise AssertionError("response is not JSON; field assertions cannot run")
        return
    present_keys = {key.lower() for key, _ in _walk_keys(parsed)}
    forbidden = {
        str(key).lower() for key in probe.get("forbiddenKeys", [])
    }
    leaked = sorted(present_keys & forbidden)
    if leaked:
        raise AssertionError(
            f"forbidden response keys present: {', '.join(leaked)}"
        )
    missing = [
        key
        for key in probe.get("requiredKeys", [])
        if str(key).lower() not in present_keys
    ]
    if missing:
        raise AssertionError(
            f"required response keys missing: {', '.join(map(str, missing))}"
        )
    fragments = [str(value) for value in probe.get("messageIncludes", [])]
    if fragments:
        message = (
            str(parsed.get("msg") or parsed.get("message") or "")
            if isinstance(parsed, dict)
            else ""
        )
        if not any(fragment in message for fragment in fragments):
            raise AssertionError(
                "failure response did not contain an approved authorization message"
            )


def _validate_probe(
    probe: Any, config: dict[str, Any], allow_write: bool
) -> None:
    if not isinstance(probe, dict):
        raise UatConfigError("every apiProbe must be an object")
    probe_id = str(probe.get("id") or "")
    if not probe_id or not all(
        ch.isalnum() or ch in "._-" for ch in probe_id
    ):
        raise UatConfigError("apiProbe.id must use safe characters")
    method = str(probe.get("method") or "GET").upper()
    if method not in {"GET", "HEAD", *WRITE_METHODS}:
        raise UatConfigError(f"{probe_id}: unsupported method {method}")
    if method in WRITE_METHODS and not allow_write:
        raise UatConfigError(f"{probe_id}: write probe requires --allow-write")
    path = str(probe.get("path") or "")
    if not path.startswith("/") or "://" in path or "#" in path:
        raise UatConfigError(
            f"{probe_id}: path must be a same-origin absolute path"
        )
    actor = probe.get("actor")
    if actor is not None and actor not in config["actors"]:
        raise UatConfigError(f"{probe_id}: unknown actor {actor!r}")
    context = probe.get("context")
    if context is not None and context not in config["contexts"]:
        raise UatConfigError(f"{probe_id}: unknown context {context!r}")
    if probe.get("expect") not in {"success", "failure"}:
        raise UatConfigError(
            f"{probe_id}: expect must be success or failure"
        )
    if probe.get("expect") == "failure":
        if not probe.get("allowedHttpStatuses") and not probe.get(
            "allowedAppCodes"
        ):
            raise UatConfigError(
                f"{probe_id}: failure probes require explicit allowed status/code"
            )
        if not probe.get("messageIncludes"):
            raise UatConfigError(
                f"{probe_id}: failure probes require messageIncludes"
            )
    variants = probe.get("parallelBodies")
    if variants is not None and (
        not isinstance(variants, list) or len(variants) < 2
    ):
        raise UatConfigError(
            f"{probe_id}: parallelBodies must contain at least two bodies"
        )


def run(config: dict[str, Any], allow_write: bool) -> dict[str, Any]:
    probes = config.get("apiProbes")
    if not isinstance(probes, list) or not probes:
        raise UatConfigError("apiProbes must be a non-empty array")
    if allow_write and os.environ.get("ERP_UAT_WRITE_APPROVAL") != config[
        "runId"
    ]:
        raise UatConfigError(
            "ERP_UAT_WRITE_APPROVAL must exactly equal runId for write-capable UAT"
        )
    results = []
    failed = 0
    for probe in probes:
        _validate_probe(probe, config, allow_write)
        public_result = {
            "id": probe["id"],
            "actor": probe.get("actor"),
            "context": probe.get("context"),
        }
        try:
            bodies = probe.get("parallelBodies")
            if bodies:
                with ThreadPoolExecutor(max_workers=len(bodies)) as pool:
                    executions = list(
                        pool.map(lambda body: _perform(config, probe, body), bodies)
                    )
                successes = 0
                for execution in executions:
                    try:
                        _assert_one(probe, execution)
                        successes += 1
                    except AssertionError:
                        pass
                minimum = int(probe.get("minSuccess", 0))
                maximum = int(probe.get("maxSuccess", len(executions)))
                if not minimum <= successes <= maximum:
                    raise AssertionError(
                        f"parallel success count {successes} outside "
                        f"[{minimum}, {maximum}]"
                    )
                public_result["executions"] = [
                    {
                        key: value
                        for key, value in item.items()
                        if not key.startswith("_")
                    }
                    for item in executions
                ]
                public_result["successCount"] = successes
            else:
                execution = _perform(config, probe)
                _assert_one(probe, execution)
                public_result.update(
                    {
                        key: value
                        for key, value in execution.items()
                        if not key.startswith("_")
                    }
                )
            public_result["status"] = "passed"
        except Exception as exc:  # Every failure belongs in the evidence file.
            failed += 1
            public_result["status"] = "failed"
            public_result["error"] = str(exc)[:300]
        results.append(public_result)
    return {
        **public_config(config),
        "kind": "api-uat",
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "status": "passed" if failed == 0 else "failed",
        "probeCount": len(results),
        "failedCount": failed,
        "results": results,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--allow-write", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    output = Path(args.output).expanduser().resolve()
    try:
        config = load_config(args.config, root)
        evidence = run(config, args.allow_write)
    except (UatConfigError, OSError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"[INFO] secret-free API UAT evidence: {output}")
    if evidence["status"] != "passed":
        print(
            f"[FAIL] {evidence['failedCount']} API UAT probe(s) failed",
            file=sys.stderr,
        )
        return 1
    print(f"[PASS] {evidence['probeCount']} API UAT probe(s) passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
