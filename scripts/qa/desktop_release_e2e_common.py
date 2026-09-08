#!/usr/bin/env python3
"""Safety primitives for the isolated desktop release E2E environment.

The module deliberately uses the local mysql Unix socket and loopback HTTP
only.  It never prints credentials, tokens, cookies, or response bodies.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import socket
import stat
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


RUN_ID = "CODEX_QA_20260728"
PREFIX = RUN_ID + "_"
DATABASE = "BossERP_NEW"
DEFAULT_BASE_URL = "http://127.0.0.1:1028/prod-api"
WRITE_APPROVAL_ENV = "ERP_QA_WRITE_APPROVAL"
WRITE_APPROVAL_VALUE = RUN_ID

CREDENTIAL_PATH = Path("/tmp/codex_qa_20260728_credentials.json")
MANIFEST_PATH = Path("/tmp/codex_qa_20260728_manifest.json")
EVIDENCE_PATH = Path("/tmp/codex_qa_20260728_evidence.json")
LOOPBACK_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))

ID_MIN = 922_607_280_000
ID_MAX = 922_607_289_999

DEPT_COMPANY = 922_607_280_100
DEPT_STORE_A = 922_607_280_101
DEPT_STORE_B = 922_607_280_102
DEPT_WAREHOUSE_A = 922_607_280_103

ROLE_EMPLOYEE = 922_607_280_201
ROLE_MANAGER = 922_607_280_202
ROLE_PRIVILEGED = 922_607_280_203

USER_EMPLOYEE = 922_607_280_301
USER_MANAGER = 922_607_280_302
USER_PRIVILEGED = 922_607_280_303
USER_OTHER_A = 922_607_280_304
USER_OTHER_B = 922_607_280_305

PROFILE_EMPLOYEE = 922_607_280_401
PROFILE_MANAGER = 922_607_280_402
PROFILE_PRIVILEGED = 922_607_280_403
PROFILE_OTHER_A = 922_607_280_404
PROFILE_OTHER_B = 922_607_280_405

PRODUCT_A = 922_607_280_501
PRODUCT_B = 922_607_280_502
STOCK_A = 922_607_280_601
STOCK_B = 922_607_280_602

CONTRACT_A = 922_607_280_701
CONTRACT_B = 922_607_280_702
JOB_ID = 922_607_280_750

TRANSFER_DRAFT = 922_607_280_801
TRANSFER_APPROVAL = 922_607_280_802
TRANSFER_DETAIL_DRAFT = 922_607_280_811
TRANSFER_DETAIL_APPROVAL = 922_607_280_812

APPROVAL_RULE = 922_607_280_901
APPROVAL_VERSION = 922_607_280_902
APPROVAL_NODE = 922_607_280_903
APPROVAL_INSTANCE = 922_607_280_904
APPROVAL_TASK = 922_607_280_905
APPROVAL_CANDIDATE = 922_607_280_906
APPROVAL_FIXTURE_ROUND = 1

ACTORS = {
    "employee": {
        "userId": USER_EMPLOYEE,
        "username": PREFIX + "E",
        "roleId": ROLE_EMPLOYEE,
        "deptId": DEPT_STORE_A,
    },
    "manager": {
        "userId": USER_MANAGER,
        "username": PREFIX + "M",
        "roleId": ROLE_MANAGER,
        "deptId": DEPT_STORE_A,
    },
    # This is intentionally not called "superAdmin": the application defines
    # true super-administration as user_id=1, which an isolated high-ID actor
    # cannot emulate.
    "privilegedAdmin": {
        "userId": USER_PRIVILEGED,
        "username": PREFIX + "A",
        "roleId": ROLE_PRIVILEGED,
        "deptId": DEPT_COMPANY,
    },
}

ALL_EXPLICIT_IDS = {
    DEPT_COMPANY,
    DEPT_STORE_A,
    DEPT_STORE_B,
    DEPT_WAREHOUSE_A,
    ROLE_EMPLOYEE,
    ROLE_MANAGER,
    ROLE_PRIVILEGED,
    USER_EMPLOYEE,
    USER_MANAGER,
    USER_PRIVILEGED,
    USER_OTHER_A,
    USER_OTHER_B,
    PROFILE_EMPLOYEE,
    PROFILE_MANAGER,
    PROFILE_PRIVILEGED,
    PROFILE_OTHER_A,
    PROFILE_OTHER_B,
    PRODUCT_A,
    PRODUCT_B,
    STOCK_A,
    STOCK_B,
    CONTRACT_A,
    CONTRACT_B,
    JOB_ID,
    TRANSFER_DRAFT,
    TRANSFER_APPROVAL,
    TRANSFER_DETAIL_DRAFT,
    TRANSFER_DETAIL_APPROVAL,
    APPROVAL_RULE,
    APPROVAL_VERSION,
    APPROVAL_NODE,
    APPROVAL_INSTANCE,
    APPROVAL_TASK,
    APPROVAL_CANDIDATE,
}

AUTO_INCREMENT_TABLES = (
    "sys_dept",
    "sys_role",
    "sys_user",
    "sys_user_profile",
    "inv_product",
    "inv_stock",
    "oa_labor_contract",
    "sys_job",
    "inv_transfer_order",
    "inv_transfer_detail",
    "approval_rule",
    "approval_rule_version",
    "approval_version_node",
    "approval_instance",
    "approval_task",
    "approval_task_candidate",
)

HIGH_ID_COLUMNS = {
    "sys_dept": "dept_id",
    "sys_role": "role_id",
    "sys_user": "user_id",
    "sys_user_profile": "profile_id",
    "inv_product": "product_id",
    "inv_stock": "stock_id",
    "oa_labor_contract": "contract_id",
    "sys_job": "job_id",
    "inv_transfer_order": "transfer_id",
    "inv_transfer_detail": "detail_id",
    "approval_rule": "rule_id",
    "approval_rule_version": "version_id",
    "approval_version_node": "node_id",
    "approval_instance": "instance_id",
    "approval_task": "task_id",
    "approval_task_candidate": "candidate_id",
}

# Marker scans intentionally exclude password/token/cookie/PII columns.  They
# prove QA-prefix absence without reading existing credentials or real PII.
MARKER_COLUMNS = {
    "sys_dept": ("dept_name", "leader", "create_by", "update_by", "remark"),
    "sys_role": ("role_name", "role_key", "create_by", "update_by", "remark"),
    "sys_user": ("user_name", "nick_name", "create_by", "update_by", "remark"),
    "sys_user_profile": (
        "employee_name",
        "employee_no",
        "position_no",
        "job_grade",
        "create_by",
        "update_by",
    ),
    "sys_user_shop": ("create_by",),
    "approval_rule": (
        "rule_code",
        "rule_name",
        "scope_name",
        "create_by",
        "update_by",
        "remark",
    ),
    "approval_rule_version": (
        "published_by_name",
        "create_by",
        "update_by",
        "remark",
    ),
    "approval_version_node": (
        "node_code",
        "node_name",
        "create_by",
        "update_by",
        "remark",
    ),
    "approval_instance": (
        "business_id",
        "idempotency_key",
        "applicant_name",
        "anchor_dept_name",
        "create_by",
        "update_by",
        "remark",
    ),
    "approval_task": (
        "node_code",
        "node_name",
        "create_by",
        "update_by",
        "remark",
    ),
    "approval_task_candidate": (
        "user_name",
        "dept_name",
        "candidate_source_code",
        "create_by",
        "update_by",
        "remark",
    ),
    "approval_action_log": (
        "action_key",
        "operator_name",
        "request_id",
        "create_by",
        "remark",
    ),
    "approval_callback_outbox": (
        "event_key",
        "business_id",
        "create_by",
        "update_by",
        "remark",
    ),
    "inv_product": (
        "product_name",
        "product_code",
        "sku",
        "create_by",
        "update_by",
        "remark",
    ),
    "inv_stock": (
        "batch_no",
        "location_code",
        "location_name",
        "create_by",
        "update_by",
        "remark",
    ),
    "inv_transfer_order": (
        "order_no",
        "from_dept_name",
        "to_dept_name",
        "create_by",
        "update_by",
        "remark",
    ),
    "inv_transfer_detail": (
        "item_code",
        "item_name",
        "product_code",
        "product_name",
        "condition_note",
    ),
    "inv_transfer_status_log": ("operator_name", "reason"),
    "oa_labor_contract": (
        "contract_no",
        "contract_title",
        "employee_name",
        "post_name",
        "create_by",
        "update_by",
        "remark",
    ),
    "oa_labor_contract_event": (
        "event_summary",
        "operator_name",
        "request_id",
        "create_by",
        "remark",
    ),
    "sys_job": (
        "job_name",
        "job_group",
        "create_by",
        "update_by",
        "remark",
    ),
    "sys_job_log": ("job_name", "job_group"),
    "sys_logininfor": ("user_name",),
    "sys_oper_log": ("oper_name", "dept_name"),
}


class QaSafetyError(RuntimeError):
    pass


def require_write_approval() -> None:
    if os.environ.get(WRITE_APPROVAL_ENV) != WRITE_APPROVAL_VALUE:
        raise QaSafetyError(
            f"{WRITE_APPROVAL_ENV} must exactly equal {WRITE_APPROVAL_VALUE}"
        )


def sql_quote(value: Any) -> str:
    if value is None:
        return "NULL"
    text = str(value)
    return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'"


def mysql(sql: str, *, database: str = DATABASE) -> str:
    command = [
        "mysql",
        "--protocol=socket",
        "-uroot",
        "-N",
        "-B",
        database,
    ]
    result = subprocess.run(
        command,
        input=sql,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        # mysql diagnostics do not include stdin SQL; keep the message short.
        diagnostic = (result.stderr or "local mysql command failed").strip()
        raise QaSafetyError(diagnostic[:500])
    return result.stdout


def query_rows(sql: str) -> list[list[str]]:
    output = mysql(sql)
    return [
        line.split("\t")
        for line in output.splitlines()
        if line.strip()
    ]


def table_exists(table: str) -> bool:
    rows = query_rows(
        "SELECT COUNT(*) FROM information_schema.tables "
        f"WHERE table_schema={sql_quote(DATABASE)} "
        f"AND table_name={sql_quote(table)};"
    )
    return bool(rows and rows[0][0] == "1")


def existing_columns(table: str) -> set[str]:
    return {
        row[0]
        for row in query_rows(
            "SELECT column_name FROM information_schema.columns "
            f"WHERE table_schema={sql_quote(DATABASE)} "
            f"AND table_name={sql_quote(table)};"
        )
    }


def verify_database_identity() -> dict[str, Any]:
    rows = query_rows(
        "SELECT @@hostname,@@port,@@socket,DATABASE(),@@version_comment;"
    )
    if len(rows) != 1 or len(rows[0]) != 5:
        raise QaSafetyError("cannot verify local database identity")
    db_host, port, db_socket, database, version_comment = rows[0]
    local_names = {
        socket.gethostname().lower(),
        socket.getfqdn().lower(),
        socket.gethostname().split(".")[0].lower(),
    }
    normalized_host = db_host.lower()
    local_host = (
        normalized_host in local_names
        or normalized_host.split(".")[0] in local_names
        or normalized_host.endswith(".local")
    )
    if (
        not local_host
        or port != "3306"
        or not db_socket.startswith("/")
        or database.lower() != DATABASE.lower()
        or "homebrew" not in version_comment.lower()
    ):
        raise QaSafetyError("database is not the approved local Homebrew target")
    return {
        "host": db_host,
        "port": int(port),
        "socket": db_socket,
        "database": database,
        "distribution": version_comment,
    }


def validate_loopback_base_url(value: str | None = None) -> str:
    url = (value or os.environ.get("ERP_QA_BASE_URL") or DEFAULT_BASE_URL).rstrip(
        "/"
    )
    parsed = urllib.parse.urlparse(url)
    if (
        parsed.scheme != "http"
        or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}
        or parsed.username
        or parsed.password
        or parsed.fragment
    ):
        raise QaSafetyError("QA base URL must be credential-free loopback HTTP")
    if not parsed.path.endswith("/prod-api"):
        raise QaSafetyError("QA base URL must end in /prod-api")
    return url


def _read_http(url: str, timeout: float = 4) -> tuple[int, bytes]:
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "User-Agent": "ERP-QA-Preflight/1"},
    )
    try:
        with LOOPBACK_OPENER.open(request, timeout=timeout) as response:
            return response.status, response.read(1024 * 1024)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read(1024 * 1024)


def verify_local_services() -> dict[str, str]:
    services: dict[str, str] = {}
    for port in (8080, 9200, 9201, 9203, 9204, 9205, 9206, 9300):
        status, body = _read_http(f"http://127.0.0.1:{port}/actuator/health")
        if status != 200:
            raise QaSafetyError(f"local service on port {port} is not healthy")
        parsed = json.loads(body.decode("utf-8"))
        if str(parsed.get("status", "")).upper() != "UP":
            raise QaSafetyError(f"local service on port {port} is not UP")
        services[str(port)] = "UP"
    status, _ = _read_http("http://127.0.0.1:1028/release-info.json")
    if status != 200:
        raise QaSafetyError("candidate UI on port 1028 is not ready")
    services["1028"] = "UP"
    return services


def prefix_hits() -> dict[str, int]:
    statements: list[str] = []
    for table, configured_columns in MARKER_COLUMNS.items():
        if not table_exists(table):
            continue
        columns = existing_columns(table)
        selected = [column for column in configured_columns if column in columns]
        if not selected:
            continue
        predicates = " OR ".join(
            f"`{column}` LIKE {sql_quote(PREFIX + '%')}" for column in selected
        )
        statements.append(
            f"SELECT {sql_quote(table)},COUNT(*) FROM `{table}` "
            f"WHERE {predicates};"
        )
    rows = query_rows("\n".join(statements)) if statements else []
    return {table: int(count) for table, count in rows if int(count) > 0}


def high_id_hits() -> dict[str, int]:
    statements = [
        f"SELECT {sql_quote(table)},COUNT(*) FROM `{table}` "
        f"WHERE `{column}` BETWEEN {ID_MIN} AND {ID_MAX};"
        for table, column in HIGH_ID_COLUMNS.items()
        if table_exists(table) and column in existing_columns(table)
    ]
    rows = query_rows("\n".join(statements)) if statements else []
    return {table: int(count) for table, count in rows if int(count) > 0}


def capture_auto_increment() -> dict[str, int | None]:
    names = ",".join(sql_quote(table) for table in AUTO_INCREMENT_TABLES)
    rows = query_rows(
        "SELECT table_name,auto_increment FROM information_schema.tables "
        f"WHERE table_schema={sql_quote(DATABASE)} AND table_name IN ({names});"
    )
    return {
        table: None if value == "NULL" else int(value) for table, value in rows
    }


def sha256_json(value: Any) -> str:
    encoded = json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def owner_only_json(path: Path, payload: Any) -> None:
    flags = os.O_WRONLY | os.O_CREAT | os.O_TRUNC
    descriptor = os.open(path, flags, 0o600)
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
    except Exception:
        try:
            os.close(descriptor)
        except OSError:
            pass
        raise
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode != 0o600 or path.is_symlink():
        raise QaSafetyError(f"temporary artifact is not owner-only: {path}")


def read_owner_only_json(path: Path) -> Any:
    if not path.exists() or path.is_symlink() or not path.is_file():
        raise QaSafetyError(f"required owner-only artifact is missing: {path}")
    if stat.S_IMODE(path.stat().st_mode) != 0o600:
        raise QaSafetyError(f"temporary artifact must have mode 0600: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def synthetic_id_card(seed17: str = "99999920000101001") -> str:
    if not re.fullmatch(r"\d{17}", seed17):
        raise QaSafetyError("synthetic ID seed must contain 17 digits")
    weights = (7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    check_codes = "10X98765432"
    total = sum(int(value) * weight for value, weight in zip(seed17, weights))
    return seed17 + check_codes[total % 11]


def http_json(
    base_url: str,
    method: str,
    path: str,
    *,
    token: str | None = None,
    dept_id: int | None = None,
    payload: Any = None,
    idempotency_key: str | None = None,
    timeout: float = 20,
) -> tuple[dict[str, Any], dict[str, Any]]:
    if not path.startswith("/") or "://" in path or "#" in path:
        raise QaSafetyError("HTTP path must be same-origin and absolute")
    headers = {
        "Accept": "application/json",
        "User-Agent": "ERP-Desktop-Release-QA/1",
    }
    if token:
        headers["Authorization"] = "Bearer " + token
    if dept_id is not None:
        headers["Dept-NumId"] = str(dept_id)
    if idempotency_key:
        headers["Idempotency-Key"] = idempotency_key
    body = None
    if payload is not None:
        body = json.dumps(
            payload, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")
        headers["Content-Type"] = "application/json;charset=utf-8"
    request = urllib.request.Request(
        base_url + path, data=body, method=method.upper(), headers=headers
    )
    started = time.monotonic()
    try:
        with LOOPBACK_OPENER.open(request, timeout=timeout) as response:
            status = response.status
            raw = response.read(5 * 1024 * 1024 + 1)
    except urllib.error.HTTPError as exc:
        status = exc.code
        raw = exc.read(5 * 1024 * 1024 + 1)
    if len(raw) > 5 * 1024 * 1024:
        raise QaSafetyError("HTTP response exceeded the 5 MiB QA limit")
    try:
        parsed = json.loads(raw.decode("utf-8")) if raw else {}
    except (UnicodeDecodeError, json.JSONDecodeError):
        parsed = {}
    if not isinstance(parsed, dict):
        parsed = {"data": parsed}
    public = {
        "httpStatus": int(status),
        "appCode": parsed.get("code"),
        "durationMs": round((time.monotonic() - started) * 1000),
        "bodySha256": hashlib.sha256(raw).hexdigest(),
    }
    return parsed, public


def local_captcha_fields(base_url: str) -> dict[str, str]:
    """Issue one local captcha and read only its exact ephemeral Redis key."""
    validate_loopback_base_url(base_url)
    request = urllib.request.Request(
        base_url + "/code",
        headers={
            "Accept": "text/plain",
            "User-Agent": "ERP-Desktop-Release-QA/1",
        },
    )
    try:
        with LOOPBACK_OPENER.open(request, timeout=5) as response:
            raw = response.read(1024 * 1024)
    except urllib.error.HTTPError as exc:
        raise QaSafetyError(
            f"local captcha issuance failed with HTTP {exc.code}"
        ) from exc
    try:
        parsed = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise QaSafetyError("local captcha response is invalid") from exc
    uuid = parsed.get("uuid") if isinstance(parsed, dict) else None
    if not isinstance(uuid, str) or not re.fullmatch(r"[0-9a-fA-F]{32}", uuid):
        raise QaSafetyError("local captcha response did not contain a safe UUID")
    key = "captcha_codes:" + uuid
    result = subprocess.run(
        ["redis-cli", "--raw", "GET", key],
        text=True,
        capture_output=True,
        check=False,
    )
    encoded_code = result.stdout.strip()
    try:
        decoded_code = json.loads(encoded_code)
    except json.JSONDecodeError:
        decoded_code = encoded_code
    code = str(decoded_code).strip()
    if result.returncode != 0 or not code or len(code) > 16:
        raise QaSafetyError("cannot resolve the exact local QA captcha")
    return {"code": code, "uuid": uuid}


def response_success(parsed: dict[str, Any], public: dict[str, Any]) -> bool:
    code = parsed.get("code")
    return 200 <= public["httpStatus"] < 300 and (
        code is None or str(code) == "200"
    )


def require_success(
    label: str, parsed: dict[str, Any], public: dict[str, Any]
) -> None:
    if not response_success(parsed, public):
        raise AssertionError(
            f"{label} failed with HTTP {public['httpStatus']}/"
            f"app {public.get('appCode')}"
        )


def response_denied(parsed: dict[str, Any], public: dict[str, Any]) -> bool:
    if public["httpStatus"] in {401, 403}:
        return True
    code = str(parsed.get("code", ""))
    message = str(parsed.get("msg") or parsed.get("message") or "")
    return code in {"401", "403", "500"} and any(
        word in message
        for word in ("无权", "权限", "未认证", "登录", "不允许", "拒绝")
    )


def rows_payload(parsed: dict[str, Any]) -> list[Any]:
    rows = parsed.get("rows")
    if isinstance(rows, list):
        return rows
    data = parsed.get("data")
    if isinstance(data, dict) and isinstance(data.get("rows"), list):
        return data["rows"]
    if isinstance(data, list):
        return data
    return []


def extract_data(parsed: dict[str, Any]) -> Any:
    return parsed.get("data")


def assert_prepared_manifest() -> dict[str, Any]:
    manifest = read_owner_only_json(MANIFEST_PATH)
    if manifest.get("runId") != RUN_ID or manifest.get("prefix") != PREFIX:
        raise QaSafetyError("manifest does not belong to this QA run")
    missing = sorted(
        record_id
        for record_id in ALL_EXPLICIT_IDS
        if not ID_MIN <= int(record_id) <= ID_MAX
    )
    if missing:
        raise QaSafetyError("manifest ID fence is invalid")
    return manifest
