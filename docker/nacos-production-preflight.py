#!/usr/bin/env python3
"""Read-only Nacos production readiness and configuration gate."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import ProxyHandler, Request, build_opener


REQUIRED_PROD_DATA_IDS = (
    "application-prod.yml",
    "erp-gateway-prod.yml",
    "erp-auth-prod.yml",
    "erp-system-prod.yml",
    "erp-job-prod.yml",
    "erp-oa-prod.yml",
    "erp-inventory-prod.yml",
    "erp-approval-prod.yml",
    "erp-file-prod.yml",
    "erp-monitor-prod.yml",
)
PLACEHOLDER_RE = re.compile(
    r"(replace-with|change[-_ ]?me|changeme|example|placeholder|your[-_])",
    re.IGNORECASE,
)
DIRECT_OPENER = build_opener(ProxyHandler({}))


class NacosPreflightError(RuntimeError):
    """Sanitized production gate failure."""


def parse_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise NacosPreflightError("production environment file is missing")
    values: dict[str, str] = {}
    for number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            raise NacosPreflightError(
                f"invalid production environment assignment at line {number}"
            )
        name, value = line.split("=", 1)
        name = name.strip()
        if not re.fullmatch(r"[A-Z][A-Z0-9_]*", name) or name in values:
            raise NacosPreflightError(
                f"invalid or duplicate environment variable at line {number}"
            )
        value = value.strip()
        if (value.startswith('"') and value.endswith('"')) or (
            value.startswith("'") and value.endswith("'")
        ):
            value = value[1:-1]
        values[name] = value
    return values


def required_value(values: dict[str, str], name: str) -> str:
    value = values.get(name, "")
    if not value or PLACEHOLDER_RE.search(value):
        raise NacosPreflightError(
            f"{name} is missing or still contains a placeholder"
        )
    return value


def load_json_response(response, label: str) -> dict:
    try:
        payload = json.loads(response.read(1024 * 1024))
    except (json.JSONDecodeError, UnicodeDecodeError):
        raise NacosPreflightError(f"{label} returned an invalid JSON response") from None
    if not isinstance(payload, dict):
        raise NacosPreflightError(f"{label} returned an invalid response object")
    return payload


def open_request(request: Request, timeout: float, label: str):
    try:
        return DIRECT_OPENER.open(request, timeout=timeout)
    except HTTPError as exc:
        raise NacosPreflightError(
            f"{label} was rejected with HTTP {exc.code}"
        ) from None
    except (URLError, TimeoutError, OSError):
        raise NacosPreflightError(f"{label} is unreachable") from None


def verify_readiness(base_url: str, timeout: float) -> None:
    request = Request(
        f"{base_url}/nacos/v3/admin/core/state/readiness",
        headers={"Accept": "application/json"},
        method="GET",
    )
    with open_request(request, timeout, "Nacos readiness endpoint") as response:
        payload = load_json_response(response, "Nacos readiness endpoint")
    ready = str(payload.get("data", "")).strip().lower()
    if payload.get("code") != 0 or ready not in {"ok", "up", "ready"}:
        raise NacosPreflightError("Nacos readiness endpoint reported not ready")


def authenticate(
    base_url: str, username: str, password: str, timeout: float
) -> str:
    body = urlencode({"username": username, "password": password}).encode("utf-8")
    request = Request(
        f"{base_url}/nacos/v3/auth/user/login",
        data=body,
        headers={
            "Accept": "application/json",
            "Content-Type": "application/x-www-form-urlencoded",
        },
        method="POST",
    )
    with open_request(request, timeout, "Nacos client authentication") as response:
        payload = load_json_response(response, "Nacos client authentication")
    token = payload.get("accessToken")
    if not isinstance(token, str) or not token:
        raise NacosPreflightError(
            "Nacos client authentication did not return an access token"
        )
    if payload.get("globalAdmin") is True:
        raise NacosPreflightError(
            "Nacos client account must not be a global administrator"
        )
    return token


def verify_config(
    base_url: str, token: str, data_id: str, timeout: float
) -> None:
    query = urlencode(
        {
            "namespaceId": "public",
            "groupName": "DEFAULT_GROUP",
            "dataId": data_id,
        }
    )
    request = Request(
        f"{base_url}/nacos/v3/client/cs/config?{query}",
        headers={
            "Accept": "application/json",
            "Authorization": f"Bearer {token}",
        },
        method="GET",
    )
    with open_request(request, timeout, f"Nacos config {data_id}") as response:
        payload = load_json_response(response, f"Nacos config {data_id}")
    data = payload.get("data")
    if (
        payload.get("code") != 0
        or not isinstance(data, dict)
        or data.get("success") is not True
        or data.get("resultCode") != 200
        or not isinstance(data.get("md5"), str)
        or not data["md5"]
        or not isinstance(data.get("content"), str)
        or not data["content"].strip()
    ):
        raise NacosPreflightError(
            f"Nacos config {data_id} is missing, empty, or unreadable"
        )


def run(env_file: Path, timeout: float) -> None:
    values = parse_env(env_file)
    address = required_value(values, "ECS_NACOS_SERVER_ADDR")
    if not re.fullmatch(r"127\.0\.0\.1:[1-9][0-9]{0,4}", address):
        raise NacosPreflightError(
            "ECS_NACOS_SERVER_ADDR must use explicit IPv4 loopback and port"
        )
    port = int(address.rsplit(":", 1)[1])
    if port > 65535:
        raise NacosPreflightError("ECS_NACOS_SERVER_ADDR port is invalid")
    username = required_value(values, "NACOS_CLIENT_USERNAME")
    password = required_value(values, "NACOS_CLIENT_PASSWORD")
    base_url = f"http://{address}"

    verify_readiness(base_url, timeout)
    token = authenticate(base_url, username, password, timeout)
    try:
        for data_id in REQUIRED_PROD_DATA_IDS:
            verify_config(base_url, token, data_id, timeout)
    finally:
        token = ""


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify Nacos readiness, client authentication and prod Data IDs"
    )
    parser.add_argument("--env-file", required=True, type=Path)
    parser.add_argument("--timeout", type=float, default=5.0)
    args = parser.parse_args()
    if not (0 < args.timeout <= 30):
        print("Nacos preflight timeout must be between 0 and 30 seconds", file=sys.stderr)
        return 64
    try:
        run(args.env_file, args.timeout)
    except NacosPreflightError as exc:
        print(f"Nacos production preflight failed: {exc}", file=sys.stderr)
        return 70
    print(
        "Nacos production preflight passed: "
        f"readiness=ready authentication=passed data_ids={len(REQUIRED_PROD_DATA_IDS)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
