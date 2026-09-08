#!/usr/bin/env python3
"""Run secret-free, declarative contract-signing API UAT against real HTTP."""

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

from contract_signing_uat_common import (
    REQUIRED_API_CASES,
    UatConfigError,
    WRITE_METHODS,
    full_hash,
    load_config,
    public_config,
    render_templates,
    validate_api_probe,
)


MAX_RESPONSE_BYTES = 25 * 1024 * 1024
FORBIDDEN_HEADER_NAMES = {"authorization", "cookie", "proxy-authorization"}
IDENTITY_RESPONSE_PATHS = {
    "environmentId": "data.environmentId",
    "database": "data.database",
    "databaseFingerprintSha256": "data.databaseFingerprintSha256",
    "releaseId": "data.releaseId",
    "runId": "data.runId",
    "candidateCommit": "data.candidateCommit",
    "isolated": "data.isolated",
    "productionDataCopied": "data.productionDataCopied",
}


class _RejectRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise AssertionError(f"redirects are forbidden for credentialed UAT requests (HTTP {code})")


def _open_no_redirect(request: urllib.request.Request, timeout: float):
    return urllib.request.build_opener(_RejectRedirects()).open(request, timeout=timeout)


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


def _extract_path(value: Any, dotted: str) -> Any:
    current = value
    for part in dotted.removeprefix("$.").split("."):
        if isinstance(current, dict) and part in current:
            current = current[part]
            continue
        if isinstance(current, list) and part.isdigit() and int(part) < len(current):
            current = current[int(part)]
            continue
        raise AssertionError(f"response path not found: {dotted}")
    return current


def _perform(
    config: dict[str, Any],
    probe: dict[str, Any],
    captures: dict[str, Any],
    body_override: Any = None,
) -> dict[str, Any]:
    method = str(probe.get("method") or "GET").upper()
    path = render_templates(str(probe["path"]), config, captures)
    url = config["apiBaseUrl"] + str(path)
    headers = {
        "Accept": str(probe.get("accept") or "application/json"),
        "User-Agent": "ERP-Contract-Signing-UAT/1",
        "X-UAT-Run-Id": str(config["runId"]),
    }
    actor_name = probe.get("actor")
    if actor_name:
        headers["Authorization"] = "Bearer " + config["actors"][actor_name]["_token"]
    organization = probe.get("organization")
    if organization:
        org = config["organizations"][organization]
        headers["Dept-NumId"] = str(org["deptId"])
        headers["Organization-Id"] = str(org["organizationId"])
    for key, value in (probe.get("headers") or {}).items():
        if str(key).lower() in FORBIDDEN_HEADER_NAMES:
            raise UatConfigError(f"{probe['id']}: credential headers are forbidden in config")
        headers[str(key)] = str(render_templates(value, config, captures))

    payload = probe.get("body") if body_override is None else body_override
    payload = render_templates(payload, config, captures)
    request_body = None
    if payload is not None:
        request_body = json.dumps(
            payload, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")
        headers["Content-Type"] = "application/json;charset=utf-8"
    request = urllib.request.Request(url, data=request_body, method=method, headers=headers)
    started = time.monotonic()
    try:
        with _open_no_redirect(
            request, timeout=float(probe.get("timeoutSeconds", 20))
        ) as response:
            status = int(response.status)
            content_type = str(response.headers.get("Content-Type") or "")
            body = response.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as exc:
        status = int(exc.code)
        content_type = str(exc.headers.get("Content-Type") or "") if exc.headers else ""
        body = exc.read(MAX_RESPONSE_BYTES + 1)
    if len(body) > MAX_RESPONSE_BYTES:
        raise AssertionError(f"{probe['id']}: response exceeds {MAX_RESPONSE_BYTES} bytes")
    if 300 <= status < 400:
        raise AssertionError(f"{probe['id']}: redirects are forbidden (HTTP {status})")
    parsed = _json_body(body)
    app_code = parsed.get("code") if isinstance(parsed, dict) else None
    return {
        "httpStatus": status,
        "appCode": app_code,
        "contentType": content_type[:120],
        "durationMs": round((time.monotonic() - started) * 1000),
        "bodyBytes": len(body),
        "bodySha256": hashlib.sha256(body).hexdigest(),
        "_parsed": parsed,
    }


def _app_code_is_success(app_code: Any) -> bool:
    if app_code is None:
        return True
    try:
        return int(app_code) == 200
    except (TypeError, ValueError):
        return False


def _assert_one(probe: dict[str, Any], result: dict[str, Any]) -> None:
    parsed = result["_parsed"]
    http_status = int(result["httpStatus"])
    app_code = result["appCode"]
    if 300 <= http_status < 400:
        raise AssertionError(f"redirect responses are never acceptable: HTTP {http_status}")
    if http_status >= 500:
        raise AssertionError(f"server errors are never acceptable: HTTP {http_status}")
    success = 200 <= http_status < 300 and _app_code_is_success(app_code)
    if probe.get("expect") == "success" and not success:
        raise AssertionError(f"expected success, got HTTP {http_status}/app {app_code}")
    if probe.get("expect") == "failure":
        allowed_http = {int(value) for value in probe.get("allowedHttpStatuses", [])}
        allowed_app = {int(value) for value in probe.get("allowedAppCodes", [])}
        try:
            normalized_app = int(app_code) if app_code is not None else None
        except (TypeError, ValueError):
            normalized_app = None
        matched = http_status in allowed_http or normalized_app in allowed_app
        if success or not matched:
            raise AssertionError(
                f"expected declared failure {sorted(allowed_http)}/{sorted(allowed_app)}, "
                f"got HTTP {http_status}/app {app_code}"
            )

    expected_hash = probe.get("expectedBodySha256")
    if expected_hash is not None:
        full_hash(expected_hash, f"{probe['id']}.expectedBodySha256")
        if result["bodySha256"] != expected_hash:
            raise AssertionError("response body SHA-256 does not match the frozen file hash")
    content_fragment = probe.get("contentTypeIncludes")
    if content_fragment and str(content_fragment).lower() not in result["contentType"].lower():
        raise AssertionError(f"response content type does not include {content_fragment!r}")

    if parsed is None:
        if any(
            probe.get(key)
            for key in ("forbiddenKeys", "requiredKeys", "expectedValues", "messageIncludes", "captures")
        ):
            raise AssertionError("response is not JSON; declared JSON assertions cannot run")
        return
    present_keys = {key.lower() for key, _ in _walk_keys(parsed)}
    forbidden = {str(key).lower() for key in probe.get("forbiddenKeys", [])}
    leaked = sorted(present_keys & forbidden)
    if leaked:
        raise AssertionError(f"forbidden response keys present: {', '.join(leaked)}")
    missing = [
        key
        for key in probe.get("requiredKeys", [])
        if str(key).lower() not in present_keys
    ]
    if missing:
        raise AssertionError(f"required response keys missing: {', '.join(map(str, missing))}")
    for path, allowed in (probe.get("expectedValues") or {}).items():
        actual = _extract_path(parsed, str(path))
        allowed_values = allowed if isinstance(allowed, list) else [allowed]
        if actual not in allowed_values:
            raise AssertionError(f"{path} value {actual!r} is outside {allowed_values!r}")
    fragments = [str(value) for value in probe.get("messageIncludes", [])]
    if fragments:
        message = str(parsed.get("msg") or parsed.get("message") or "") if isinstance(parsed, dict) else ""
        if not any(fragment in message for fragment in fragments):
            raise AssertionError("failure response did not contain an approved authorization/business message")


def _capture_values(probe: dict[str, Any], result: dict[str, Any], captures: dict[str, Any]) -> list[str]:
    names = []
    for name, path in (probe.get("captures") or {}).items():
        value = _extract_path(result["_parsed"], str(path))
        if isinstance(value, (dict, list)) or value in (None, ""):
            raise AssertionError(f"capture {name} must resolve to a non-empty scalar")
        captures[str(name)] = value
        names.append(str(name))
    return names


def _assert_parallel_failure(probe: dict[str, Any], result: dict[str, Any]) -> None:
    _assert_one(
        {
            "id": probe["id"],
            "expect": "failure",
            "allowedHttpStatuses": probe.get("parallelFailureAllowedHttpStatuses", []),
            "allowedAppCodes": probe.get("parallelFailureAllowedAppCodes", []),
            "messageIncludes": probe.get("parallelFailureMessageIncludes", []),
        },
        result,
    )


def _public_execution(execution: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in execution.items() if not key.startswith("_")}


def _stable_identity_hash(value: Any) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode(
        "utf-8"
    )
    return hashlib.sha256(encoded).hexdigest()


def _verify_environment_identity(config: dict[str, Any]) -> dict[str, Any]:
    configured = config["environmentIdentityProbe"]
    probe = {
        "id": configured["id"],
        "actor": configured["actor"],
        "method": "GET",
        "path": configured["path"],
        "expect": "success",
        "requiredKeys": list(IDENTITY_RESPONSE_PATHS),
    }
    execution = _perform(config, probe, {})
    _assert_one(probe, execution)
    parsed = execution.get("_parsed")
    if not isinstance(parsed, dict):
        raise AssertionError("environment identity response must be JSON")
    expected = {
        "environmentId": config["expectedEnvironmentId"],
        "database": config["database"],
        "databaseFingerprintSha256": config["databaseFingerprintSha256"],
        "releaseId": config["releaseId"],
        "runId": config["runId"],
        "candidateCommit": config["candidateCommit"],
        "isolated": True,
        "productionDataCopied": False,
    }
    observed: dict[str, Any] = {}
    for key, path in IDENTITY_RESPONSE_PATHS.items():
        actual = _extract_path(parsed, path)
        expected_value = expected[key]
        if type(actual) is not type(expected_value) or actual != expected_value:
            raise AssertionError(
                f"environment identity mismatch for {key}: observed value is not the pinned expectation"
            )
        observed[key] = actual
    return {
        "id": configured["id"],
        "actor": configured["actor"],
        "method": "GET",
        "path": configured["path"],
        **_public_execution(execution),
        "observed": observed,
        "status": "passed",
    }


def run(config: dict[str, Any], allow_write: bool) -> dict[str, Any]:
    probes = config["apiProbes"]
    if allow_write and os.environ.get("ERP_CONTRACT_SIGN_UAT_WRITE_APPROVAL") != config["runId"]:
        raise UatConfigError(
            "ERP_CONTRACT_SIGN_UAT_WRITE_APPROVAL must exactly equal runId for write-capable UAT"
        )
    for probe in probes:
        validate_api_probe(probe, config, allow_write=True)
    environment_identity = None
    if allow_write:
        environment_identity = _verify_environment_identity(config)
    selected = [
        probe
        for probe in probes
        if allow_write or str(probe.get("method") or "GET").upper() not in WRITE_METHODS
    ]
    skipped_write_count = len(probes) - len(selected)
    captures: dict[str, Any] = {}
    results = []
    failed = 0
    passed_cases: set[str] = set()
    for probe in selected:
        runtime_probe = render_templates(probe, config, captures)
        public_result = {
            "id": probe["id"],
            "case": probe["case"],
            "scenario": probe.get("scenario"),
            "actor": probe.get("actor"),
            "organization": probe.get("organization"),
            "method": str(probe.get("method") or "GET").upper(),
            "expect": probe["expect"],
        }
        try:
            bodies = runtime_probe.get("parallelBodies")
            if bodies:
                rendered_bodies = [render_templates(body, config, captures) for body in bodies]
                with ThreadPoolExecutor(max_workers=len(rendered_bodies)) as pool:
                    executions = list(
                        pool.map(
                            lambda body: _perform(config, runtime_probe, dict(captures), body),
                            rendered_bodies,
                        )
                    )
                successes = 0
                assertion_statuses = []
                successful_identities: list[Any] = []
                for execution in executions:
                    try:
                        _assert_one(runtime_probe, execution)
                        successes += 1
                        assertion_statuses.append("success")
                        if runtime_probe.get("case") == "duplicate-request":
                            identity = _extract_path(
                                execution.get("_parsed"),
                                str(runtime_probe["parallelSuccessIdentityPath"]),
                            )
                            if isinstance(identity, (dict, list)) or identity in (None, ""):
                                raise AssertionError(
                                    "duplicate-request success identity must be a non-empty scalar"
                                )
                            successful_identities.append(identity)
                    except AssertionError:
                        _assert_parallel_failure(runtime_probe, execution)
                        assertion_statuses.append("accepted-idempotent-failure")
                minimum = int(runtime_probe.get("minSuccess", 0))
                maximum = int(runtime_probe.get("maxSuccess", len(executions)))
                if not minimum <= successes <= maximum:
                    raise AssertionError(
                        f"parallel success count {successes} outside [{minimum}, {maximum}]"
                    )
                public_result["executions"] = [
                    {**_public_execution(item), "assertionStatus": assertion_statuses[index]}
                    for index, item in enumerate(executions)
                ]
                public_result["successCount"] = successes
                if runtime_probe.get("case") == "duplicate-request":
                    if not successful_identities:
                        raise AssertionError("duplicate-request must return at least one stable identity")
                    stable_identity = successful_identities[0]
                    if any(
                        type(identity) is not type(stable_identity) or identity != stable_identity
                        for identity in successful_identities[1:]
                    ):
                        raise AssertionError(
                            "duplicate-request successful responses returned different identities"
                        )
                    postcondition = runtime_probe["postconditionProbe"]
                    post_probe = {
                        "id": postcondition["id"],
                        "actor": postcondition["actor"],
                        "organization": postcondition["organization"],
                        "method": "GET",
                        "path": postcondition["path"],
                        "expect": "success",
                    }
                    post_execution = _perform(config, post_probe, captures)
                    _assert_one(post_probe, post_execution)
                    post_parsed = post_execution.get("_parsed")
                    observed_count = _extract_path(post_parsed, str(postcondition["countPath"]))
                    if isinstance(observed_count, bool) or not isinstance(observed_count, int):
                        raise AssertionError("duplicate postcondition count must be an integer")
                    if observed_count != 1:
                        raise AssertionError(
                            "duplicate postcondition must prove exactly one business-key row"
                        )
                    post_identity = _extract_path(
                        post_parsed, str(postcondition["identityPath"])
                    )
                    if type(post_identity) is not type(stable_identity) or post_identity != stable_identity:
                        raise AssertionError(
                            "duplicate postcondition identity does not match concurrent responses"
                        )
                    identity_hash = _stable_identity_hash(stable_identity)
                    public_result["stableIdentitySha256"] = identity_hash
                    public_result["postcondition"] = {
                        "id": postcondition["id"],
                        **_public_execution(post_execution),
                        "observedBusinessKeyCount": 1,
                        "stableIdentitySha256": identity_hash,
                        "status": "passed",
                    }
            else:
                execution = _perform(config, runtime_probe, captures)
                _assert_one(runtime_probe, execution)
                public_result.update(_public_execution(execution))
                captured_names = _capture_values(runtime_probe, execution, captures)
                if captured_names:
                    public_result["capturedAliases"] = captured_names
            public_result["status"] = "passed"
            passed_cases.add(str(probe["case"]))
        except Exception as exc:  # Every failure belongs in the response-free evidence.
            failed += 1
            public_result["status"] = "failed"
            public_result["error"] = str(exc)[:300]
        results.append(public_result)
    return {
        **public_config(config),
        "kind": "contract-signing-api-uat",
        "completedAt": datetime.now(timezone.utc).isoformat(),
        "status": "passed" if failed == 0 else "failed",
        "writeMode": allow_write,
        "environmentIdentityVerified": environment_identity is not None,
        "environmentIdentity": environment_identity,
        "skippedWriteCount": skipped_write_count,
        "probeCount": len(results),
        "failedCount": failed,
        "coveredCases": sorted(passed_cases),
        "requiredCases": sorted(REQUIRED_API_CASES),
        "capturedAliasCount": len(captures),
        "results": results,
    }


def _output_path(value: str, root: Path) -> Path:
    candidate = Path(value).expanduser()
    if candidate.is_symlink():
        raise UatConfigError("API evidence output must not be a symlink")
    output = candidate.resolve()
    try:
        output.relative_to((root / "output").resolve())
    except ValueError as exc:
        raise UatConfigError("API evidence output must be stored under output/") from exc
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--allow-write", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        config = load_config(args.config, root)
        output = _output_path(args.output, root)
        evidence = run(config, args.allow_write)
    except (UatConfigError, OSError, AssertionError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(evidence, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"[INFO] response-free contract-signing API UAT evidence: {output}")
    if not args.allow_write and evidence["skippedWriteCount"]:
        print(
            f"[INFO] read-only default skipped {evidence['skippedWriteCount']} write probe(s)"
        )
    if evidence["status"] != "passed":
        print(f"[FAIL] {evidence['failedCount']} API UAT probe(s) failed", file=sys.stderr)
        return 1
    print(f"[PASS] {evidence['probeCount']} API UAT probe(s) passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
