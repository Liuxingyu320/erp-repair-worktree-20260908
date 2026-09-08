#!/usr/bin/env python3
"""Shared, secret-safe validation helpers for new-business UAT runners."""

from __future__ import annotations

import json
import os
import re
import stat
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


RELEASE_ID = "new-business-20260714"
TOKEN_COOKIE = "Admin-Token"
REQUIRED_CONTEXTS = {
    "storeA": "STORE",
    "storeB": "STORE",
    "warehouseA": "WAREHOUSE",
    "warehouseB": "WAREHOUSE",
}
REQUIRED_ACTORS = {
    "employeeStoreA",
    "employeeStoreB",
    "managerStoreA",
    "directSupervisor",
    "hrReviewer",
    "warehouseEmployeeA",
    "warehouseManagerA",
    "warehouseEmployeeB",
    "admin",
}
SAFE_ID = re.compile(r"^[A-Za-z0-9._-]{6,64}$")
SAFE_DATABASE = re.compile(r"^[A-Za-z0-9_]{1,64}$")
SECRET_KEY = re.compile(
    r"(?:password|passwd|secret|access.?key|private.?key|bearer|token)$", re.I
)


class UatConfigError(ValueError):
    pass


def _required_text(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise UatConfigError(f"{label} is required")
    return text


def _reject_embedded_secrets(value: Any, path: str = "config") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if SECRET_KEY.search(str(key)):
                raise UatConfigError(
                    f"{path}.{key} must not contain a credential; "
                    "use a Playwright storageState file"
                )
            _reject_embedded_secrets(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _reject_embedded_secrets(child, f"{path}[{index}]")


def validate_target_url(url: str, label: str) -> str:
    parsed = urlparse(_required_text(url, label))
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise UatConfigError(f"{label} must be an absolute HTTP(S) URL")
    if parsed.username or parsed.password or parsed.fragment:
        raise UatConfigError(f"{label} must not contain credentials or a fragment")
    host = parsed.hostname.lower()
    loopback = host in {"localhost", "127.0.0.1", "::1"}
    if parsed.scheme != "https" and not loopback:
        raise UatConfigError(f"{label} must use HTTPS outside loopback")
    if not loopback and not re.search(
        r"(?:^|[.-])(qa|uat|test|stage|staging|dev)(?:[.-]|$)", host
    ):
        raise UatConfigError(
            f"{label} host must be visibly isolated (qa/uat/test/stage/dev), got {host!r}"
        )
    return parsed.geturl().rstrip("/")


def validate_storage_state(
    path_value: Any, repo_root: Path, host: str
) -> tuple[Path, str]:
    path = Path(_required_text(path_value, "actor.storageState")).expanduser()
    if not path.is_absolute():
        raise UatConfigError("actor.storageState must be an absolute path")
    if path.is_symlink():
        raise UatConfigError(f"storageState must not be a symlink: {path}")
    try:
        resolved = path.resolve(strict=True)
    except FileNotFoundError as exc:
        raise UatConfigError(f"storageState does not exist: {path}") from exc
    if not resolved.is_file():
        raise UatConfigError(
            f"storageState must be a regular, non-symlink file: {resolved}"
        )
    try:
        resolved.relative_to(repo_root.resolve())
    except ValueError:
        pass
    else:
        raise UatConfigError("storageState must live outside the repository")
    mode = stat.S_IMODE(resolved.stat().st_mode)
    if mode & 0o077:
        raise UatConfigError(
            f"storageState must be owner-only (chmod 600): {resolved}"
        )
    try:
        payload = json.loads(resolved.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise UatConfigError(f"invalid Playwright storageState: {resolved}") from exc
    matching = []
    for cookie in payload.get("cookies", []):
        if cookie.get("name") != TOKEN_COOKIE:
            continue
        domain = str(cookie.get("domain") or "").lstrip(".").lower()
        if host == domain or host.endswith("." + domain):
            matching.append(str(cookie.get("value") or ""))
    matching = [value for value in matching if value]
    if len(matching) != 1:
        raise UatConfigError(
            f"storageState must contain exactly one non-empty {TOKEN_COOKIE} cookie "
            f"for {host}"
        )
    return resolved, matching[0]


def load_config(
    path_value: str | os.PathLike[str], repo_root: Path
) -> dict[str, Any]:
    path = Path(path_value).expanduser().resolve()
    try:
        config = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise UatConfigError(f"cannot read UAT config: {path}") from exc
    if not isinstance(config, dict):
        raise UatConfigError("UAT config must be a JSON object")
    _reject_embedded_secrets(config)
    if config.get("schemaVersion") != 1:
        raise UatConfigError("schemaVersion must equal 1")
    if config.get("releaseId") != RELEASE_ID:
        raise UatConfigError(f"releaseId must equal {RELEASE_ID}")

    config["baseUrl"] = validate_target_url(config.get("baseUrl"), "baseUrl")
    config["apiBaseUrl"] = validate_target_url(
        config.get("apiBaseUrl"), "apiBaseUrl"
    )
    web = urlparse(config["baseUrl"])
    api = urlparse(config["apiBaseUrl"])
    if (web.scheme, web.hostname, web.port) != (
        api.scheme,
        api.hostname,
        api.port,
    ):
        raise UatConfigError(
            "baseUrl and apiBaseUrl must use the same isolated origin"
        )

    run_id = _required_text(config.get("runId"), "runId")
    if not SAFE_ID.fullmatch(run_id):
        raise UatConfigError("runId must contain 6-64 safe characters")
    database = _required_text(config.get("database"), "database")
    normalized_database = database.lower()
    if (
        not SAFE_DATABASE.fullmatch(database)
        or normalized_database in {"erp", "erp_prod", "production", "prod"}
        or "production" in normalized_database
        or "erp_prod" in normalized_database
    ):
        raise UatConfigError("database must identify a safe, isolated UAT database")
    organization_id = _required_text(
        config.get("organizationId"), "organizationId"
    )
    if not organization_id.isdigit():
        raise UatConfigError("organizationId must be numeric")

    expected_env = {
        "ERP_QA_RUN_ID": run_id,
        "ERP_QA_DATABASE": database,
        "ERP_QA_ALLOWED_ORG_ID": organization_id,
        "ERP_UAT_APPROVE_BASE_URL": config["baseUrl"],
    }
    for name, expected in expected_env.items():
        actual = os.environ.get(name, "")
        if actual != expected:
            raise UatConfigError(
                f"{name} must exactly equal the configured value"
            )

    contexts = config.get("contexts")
    if not isinstance(contexts, dict):
        raise UatConfigError("contexts must be an object")
    dept_ids: set[str] = set()
    for name, expected_type in REQUIRED_CONTEXTS.items():
        context = contexts.get(name)
        if not isinstance(context, dict):
            raise UatConfigError(f"contexts.{name} is required")
        dept_id = _required_text(
            context.get("deptId"), f"contexts.{name}.deptId"
        )
        if not dept_id.isdigit() or dept_id in dept_ids:
            raise UatConfigError(
                "all four context deptId values must be distinct numeric ids"
            )
        dept_ids.add(dept_id)
        if str(context.get("deptType") or "").upper() != expected_type:
            raise UatConfigError(
                f"contexts.{name}.deptType must equal {expected_type}"
            )
        _required_text(context.get("deptName"), f"contexts.{name}.deptName")

    actors = config.get("actors")
    if not isinstance(actors, dict):
        raise UatConfigError("actors must be an object")
    missing = sorted(REQUIRED_ACTORS - set(actors))
    if missing:
        raise UatConfigError(
            f"missing required UAT actors: {', '.join(missing)}"
        )
    state_paths: set[str] = set()
    actor_tokens: set[str] = set()
    for name in sorted(REQUIRED_ACTORS):
        actor = actors.get(name)
        if not isinstance(actor, dict):
            raise UatConfigError(f"actors.{name} must be an object")
        resolved, token = validate_storage_state(
            actor.get("storageState"), repo_root, web.hostname or ""
        )
        if str(resolved) in state_paths or token in actor_tokens:
            raise UatConfigError(
                "every required actor must use a distinct storageState and login token"
            )
        state_paths.add(str(resolved))
        actor_tokens.add(token)
        actor["storageState"] = str(resolved)
        actor["_token"] = token

    return config


def public_config(config: dict[str, Any]) -> dict[str, Any]:
    """Return non-sensitive metadata; never include state paths or tokens."""
    return {
        "schemaVersion": config["schemaVersion"],
        "releaseId": config["releaseId"],
        "runId": config["runId"],
        "database": config["database"],
        "organizationId": config["organizationId"],
        "baseUrl": config["baseUrl"],
        "apiBaseUrl": config["apiBaseUrl"],
        "contextIds": {
            name: str(value["deptId"])
            for name, value in config["contexts"].items()
        },
        "actors": sorted(REQUIRED_ACTORS),
    }
