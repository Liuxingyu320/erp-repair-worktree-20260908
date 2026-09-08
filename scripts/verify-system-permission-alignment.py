#!/usr/bin/env python3
"""Compare Release A system permissions across DB, Java and Vue contracts."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


CATEGORIES = (
    "db_without_code",
    "code_without_db",
    "frontend_without_backend",
    "backend_without_frontend",
)


@dataclass(frozen=True)
class Difference:
    category: str
    permission: str
    menu_id: str


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def managed_permission_policy(policy: dict) -> tuple[set[str], set[str]]:
    rows = policy.get("managedPermissions")
    if not isinstance(rows, list) or not rows:
        raise ValueError("policy managedPermissions must be a non-empty list")
    active: set[str] = set()
    disabled: set[str] = set()
    for row in rows:
        permission = str(row.get("permission", "")).strip()
        expected_status = row.get("expectedStatus")
        if not permission.startswith("system:") or expected_status not in {"active", "disabled"}:
            raise ValueError(f"invalid managed permission policy row: {row!r}")
        target = active if expected_status == "active" else disabled
        target.add(permission)
    if active & disabled:
        raise ValueError("a permission cannot be both active and disabled")
    return active, disabled


def _extract_sql_tuples(values_sql: str) -> Iterable[str]:
    depth = 0
    start = None
    quoted = False
    index = 0
    while index < len(values_sql):
        char = values_sql[index]
        if quoted:
            if char == "'":
                if index + 1 < len(values_sql) and values_sql[index + 1] == "'":
                    index += 1
                else:
                    quoted = False
        elif char == "'":
            quoted = True
        elif char == "(":
            if depth == 0:
                start = index + 1
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0 and start is not None:
                yield values_sql[start:index]
                start = None
        index += 1
    if quoted or depth != 0:
        raise ValueError("unterminated sys_menu INSERT statement")


def parse_seed_menu_catalog(seed_path: Path) -> dict[str, tuple[str, str]]:
    source = seed_path.read_text(encoding="utf-8")
    match = re.search(r"INSERT\s+INTO\s+`?sys_menu`?\s+VALUES\s+(.*?);", source,
                      flags=re.IGNORECASE | re.DOTALL)
    if not match:
        raise ValueError(f"sys_menu seed INSERT not found: {seed_path}")
    catalog: dict[str, tuple[str, str]] = {}
    for tuple_sql in _extract_sql_tuples(match.group(1)):
        row = next(csv.reader([tuple_sql], delimiter=",", quotechar="'", doublequote=True,
                              skipinitialspace=True))
        if len(row) < 14:
            raise ValueError("sys_menu seed row has fewer than 14 columns")
        permission = row[13].strip()
        if not permission or permission.upper() == "NULL":
            continue
        catalog[permission] = (row[0].strip(), row[12].strip())
    return catalog


def default_database_catalog(root: Path, policy: dict,
                             expected_active: set[str], expected_disabled: set[str]) -> dict[str, tuple[str, str]]:
    sources = policy["defaultDatabaseSources"]
    seed_path = root / sources["seed"]
    expand_path = root / sources["expand"]
    cutover_path = root / sources["cutover"]
    for path in (seed_path, expand_path, cutover_path):
        if not path.is_file():
            raise ValueError(f"required permission source is missing: {path}")
    expand_source = expand_path.read_text(encoding="utf-8")
    cutover_source = cutover_path.read_text(encoding="utf-8")
    catalog = parse_seed_menu_catalog(seed_path)
    for permission in expected_active | expected_disabled:
        if permission not in catalog and permission in expand_source:
            catalog[permission] = (f"DYNAMIC:{permission}", "0")
    for permission in expected_disabled:
        if permission not in cutover_source:
            raise ValueError(f"disabled permission is not handled by cutover SQL: {permission}")
        if permission in catalog:
            catalog[permission] = (catalog[permission][0], "1")
    return catalog


def read_database_catalog(path: Path) -> dict[str, tuple[str, str]]:
    """Read menu_id, perms, status from TSV/CSV or a JSON array/object."""
    if path.suffix.lower() == ".json":
        payload = load_json(path)
        rows = payload.get("rows", payload) if isinstance(payload, dict) else payload
        if not isinstance(rows, list):
            raise ValueError("database permission JSON must be an array or contain rows")
        parsed = [(str(row["menu_id"]), str(row["perms"]), str(row["status"])) for row in rows]
    else:
        lines = [line for line in path.read_text(encoding="utf-8").splitlines()
                 if line.strip() and not line.lstrip().startswith("#")]
        if not lines:
            raise ValueError("database permission file is empty")
        delimiter = "\t" if "\t" in lines[0] else ","
        rows = list(csv.reader(lines, delimiter=delimiter))
        if rows[0] and rows[0][0].strip().lower() == "menu_id":
            rows = rows[1:]
        parsed = [(row[0].strip(), row[1].strip(), row[2].strip()) for row in rows if len(row) >= 3]
    catalog: dict[str, tuple[str, str]] = {}
    for menu_id, permission, status in parsed:
        if permission in catalog:
            raise ValueError(f"duplicate permission in database input: {permission}")
        catalog[permission] = (menu_id, status)
    return catalog


def extract_backend_permissions(paths: Iterable[Path], managed: set[str]) -> set[str]:
    permissions: set[str] = set()
    annotation = re.compile(r"@RequiresPermissions\s*\((.*?)\)", re.DOTALL)
    for path in paths:
        source = path.read_text(encoding="utf-8")
        for match in annotation.finditer(source):
            for value in re.findall(r'"([A-Za-z0-9_.*-]+(?::[A-Za-z0-9_.*-]+)+)"', match.group(1)):
                if value in managed:
                    permissions.add(value)
    return permissions


def extract_frontend_permissions(paths: Iterable[Path], managed: set[str]) -> set[str]:
    permissions: set[str] = set()
    contexts = (
        re.compile(r"v-hasPermi\s*=\s*\"(.*?)\"", re.DOTALL),
        re.compile(r"hasPermi(?:And|Or)?\s*\((.*?)\)", re.DOTALL),
        re.compile(r"permissions\s*:\s*(\[[^\]]*\]|['\"][^'\"]+['\"])", re.DOTALL),
    )
    permission_literal = re.compile(r"['\"]([A-Za-z0-9_.*-]+(?::[A-Za-z0-9_.*-]+)+)['\"]")
    for path in paths:
        source = path.read_text(encoding="utf-8")
        for context in contexts:
            for match in context.finditer(source):
                for value in permission_literal.findall(match.group(1)):
                    if value in managed:
                        permissions.add(value)
    return permissions


def calculate_differences(db_active: set[str], backend: set[str], frontend: set[str],
                          menu_ids: dict[str, str]) -> list[Difference]:
    differences: list[Difference] = []
    sets = {
        "db_without_code": db_active - backend,
        "code_without_db": backend - db_active,
        "frontend_without_backend": frontend - backend,
        "backend_without_frontend": backend - frontend,
    }
    for category in CATEGORIES:
        for permission in sorted(sets[category]):
            differences.append(Difference(category, permission, menu_ids.get(permission, "NONE")))
    return differences


def validate_and_apply_allowlist(differences: list[Difference], payload: dict,
                                 managed: set[str], today: dt.date) -> tuple[list[Difference], list[str]]:
    errors: list[str] = []
    entries = payload.get("entries")
    if payload.get("schemaVersion") != 1 or not isinstance(entries, list):
        return differences, ["allowlist must have schemaVersion=1 and an entries array"]
    allowed: set[tuple[str, str, str]] = set()
    for index, entry in enumerate(entries):
        required = {"category", "permission", "menu_id", "reason", "owner", "expires_at"}
        missing = required - set(entry)
        if missing:
            errors.append(f"allowlist entry {index} missing: {', '.join(sorted(missing))}")
            continue
        category = str(entry["category"])
        permission = str(entry["permission"])
        menu_id = str(entry["menu_id"])
        reason = str(entry["reason"]).strip()
        owner = str(entry["owner"]).strip()
        try:
            expires_at = dt.date.fromisoformat(str(entry["expires_at"]))
        except ValueError:
            errors.append(f"allowlist entry {index} has invalid expires_at")
            continue
        if category not in CATEGORIES or permission not in managed:
            errors.append(f"allowlist entry {index} is outside the managed permission scope")
        if not menu_id or not reason or not owner:
            errors.append(f"allowlist entry {index} must include menu_id, reason and owner")
        if expires_at < today:
            errors.append(f"allowlist entry {index} expired on {expires_at.isoformat()}")
        allowed.add((category, permission, menu_id))
    remaining = [item for item in differences
                 if (item.category, item.permission, item.menu_id) not in allowed]
    return remaining, errors


def discover_paths(root: Path) -> tuple[list[Path], list[Path]]:
    controller_root = root / "erp-modules/erp-system/src/main/java/com/erp/system/controller"
    frontend_root = root / "erp-ui/src/views/system"
    router_root = root / "erp-ui/src/router"
    backend = sorted(controller_root.glob("Sys*Controller.java"))
    frontend = sorted(frontend_root.rglob("*.vue")) + sorted(router_root.rglob("*.js"))
    if not backend or not frontend:
        raise ValueError("system controller or frontend permission sources are missing")
    return backend, frontend


def print_difference_group(category: str, rows: list[Difference]) -> None:
    labels = {
        "db_without_code": "DB 无代码",
        "code_without_db": "代码无 DB",
        "frontend_without_backend": "仅前端",
        "backend_without_frontend": "仅后端",
    }
    print(f"{labels[category]} ({len(rows)}):")
    if not rows:
        print("  - 无")
    for row in rows:
        print(f"  - {row.permission} [menu_id={row.menu_id}]")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, help="repository root")
    parser.add_argument("--db-permissions", type=Path,
                        help="post-migration menu_id/perms/status TSV, CSV or JSON")
    parser.add_argument("--policy", type=Path)
    parser.add_argument("--allowlist", type=Path)
    args = parser.parse_args(argv)

    script_dir = Path(__file__).resolve().parent
    root = (args.root or script_dir.parent).resolve()
    policy_path = args.policy or script_dir / "system-permission-alignment-policy.json"
    allowlist_path = args.allowlist or script_dir / "system-permission-alignment-allowlist.json"
    try:
        policy = load_json(policy_path)
        expected_active, expected_disabled = managed_permission_policy(policy)
        managed = expected_active | expected_disabled
        catalog = (read_database_catalog(args.db_permissions.resolve()) if args.db_permissions
                   else default_database_catalog(root, policy, expected_active, expected_disabled))
        backend_paths, frontend_paths = discover_paths(root)
        backend = extract_backend_permissions(backend_paths, managed)
        frontend = extract_frontend_permissions(frontend_paths, managed)
        db_active = {permission for permission, (_, status) in catalog.items()
                     if permission in managed and status == "0"}
        actual_disabled = {permission for permission, (_, status) in catalog.items()
                           if permission in managed and status != "0"}
        state_errors = []
        if db_active != expected_active:
            state_errors.append("active DB permissions do not match policy")
        if not expected_disabled.issubset(actual_disabled):
            state_errors.append("one or more permissions expected disabled are missing or active")
        menu_ids = {permission: menu_id for permission, (menu_id, _) in catalog.items()}
        differences = calculate_differences(db_active, backend, frontend, menu_ids)
        remaining, allowlist_errors = validate_and_apply_allowlist(
            differences, load_json(allowlist_path), managed, dt.date.today())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"权限对齐检查失败：{exc}", file=sys.stderr)
        return 2

    source_label = str(args.db_permissions) if args.db_permissions else "seed + Release A migrations"
    print(f"权限对齐范围：{policy.get('scope')}")
    print(f"数据库输入：{source_label}")
    print(f"活动 DB 权限：{len(db_active)}；后端权限：{len(backend)}；前端权限：{len(frontend)}")
    for category in CATEGORIES:
        print_difference_group(category, [row for row in differences if row.category == category])
    if state_errors or allowlist_errors or remaining:
        for error in state_errors + allowlist_errors:
            print(f"错误：{error}", file=sys.stderr)
        for row in remaining:
            print(f"未批准差异：{row.category} {row.permission} menu_id={row.menu_id}", file=sys.stderr)
        print("RESULT: FAIL")
        return 1
    print(f"已批准临时差异：{len(differences)}")
    print("RESULT: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
