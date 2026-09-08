#!/usr/bin/env python3
"""Versioned, fail-closed migration contract used by the ECS deploy helper."""

from __future__ import annotations

import hashlib
import json
import re
import shlex
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


SAFE_ID = re.compile(r"[a-z0-9][a-z0-9-]+")
SAFE_DATABASE = re.compile(r"[A-Za-z0-9_]+")
SAFE_USER = re.compile(r"[A-Za-z0-9_@.-]+")
SAFE_SQL = re.compile(r"[A-Za-z0-9._-]+\.sql")
SAFE_TABLE = re.compile(r"[a-z][a-z0-9_]*")
SAFE_INDEX = re.compile(r"(?:PRIMARY|[a-z][a-z0-9_]*)")
APPROVAL_SCHEMA_SOURCE = (
    Path(__file__).resolve().parent.parent
    / "sql/erp_unified_approval_schema_expand_stage0_20260716.sql"
)
EXPECTED_APPROVAL_TABLES = (
    "approval_template",
    "approval_rule",
    "approval_rule_version",
    "approval_rule_condition",
    "approval_version_node",
    "approval_instance",
    "approval_task",
    "approval_task_candidate",
    "approval_action_log",
    "approval_callback_outbox",
    "approval_validation_run",
    "approval_validation_issue",
)
EXPECTED_APPROVAL_COLUMN_COUNT = 232
EXPECTED_APPROVAL_UNIQUE_KEY_COUNT = 24
CURRENT_TIMESTAMP_DEFAULT = "__CURRENT_TIMESTAMP__"
SIGN_COLUMN_FINGERPRINTS = (
    # table, column, data_type, character length, nullable, default, auto_increment
    ("sys_legal_entity", "legal_entity_id", "bigint", None, False, None, True),
    ("sys_legal_entity", "legal_entity_code", "varchar", 64, False, None, False),
    ("sys_legal_entity", "legal_entity_name", "varchar", 160, False, None, False),
    ("sys_legal_entity", "unified_social_credit_code", "varchar", 32, True, None, False),
    ("sys_legal_entity", "registered_address", "varchar", 255, True, None, False),
    ("sys_legal_entity", "legal_representative", "varchar", 64, True, None, False),
    ("sys_legal_entity", "contact_phone", "varchar", 32, True, None, False),
    ("sys_legal_entity", "status", "char", 1, False, "0", False),
    ("sys_legal_entity", "version", "bigint", None, False, "0", False),
    ("sys_legal_entity", "create_by", "varchar", 64, True, "", False),
    ("sys_legal_entity", "create_time", "datetime", None, True, None, False),
    ("sys_legal_entity", "update_by", "varchar", 64, True, "", False),
    ("sys_legal_entity", "update_time", "datetime", None, True, None, False),
    ("sys_legal_entity", "remark", "varchar", 500, True, None, False),
    ("oa_sign_final_confirmation", "confirmation_id", "bigint", None, False, None, True),
    ("oa_sign_final_confirmation", "package_id", "bigint", None, False, None, False),
    ("oa_sign_final_confirmation", "employee_id", "bigint", None, False, None, False),
    ("oa_sign_final_confirmation", "final_document_version", "varchar", 64, False, None, False),
    ("oa_sign_final_confirmation", "document_root_hash", "varchar", 64, False, None, False),
    ("oa_sign_final_confirmation", "confirmation_text", "varchar", 500, False, None, False),
    ("oa_sign_final_confirmation", "identity_method", "varchar", 32, False, "LOGIN_TOKEN", False),
    ("oa_sign_final_confirmation", "request_id", "varchar", 64, False, None, False),
    ("oa_sign_final_confirmation", "ip_address", "varchar", 64, True, None, False),
    ("oa_sign_final_confirmation", "user_agent", "varchar", 500, True, None, False),
    ("oa_sign_final_confirmation", "confirmed_time", "datetime", None, False, None, False),
    ("oa_sign_final_confirmation", "create_time", "datetime", None, False, CURRENT_TIMESTAMP_DEFAULT, False),
    ("oa_sign_final_confirmation_document", "confirmation_document_id", "bigint", None, False, None, True),
    ("oa_sign_final_confirmation_document", "confirmation_id", "bigint", None, False, None, False),
    ("oa_sign_final_confirmation_document", "package_id", "bigint", None, False, None, False),
    ("oa_sign_final_confirmation_document", "document_id", "bigint", None, False, None, False),
    ("oa_sign_final_confirmation_document", "final_document_version", "varchar", 64, False, None, False),
    ("oa_sign_final_confirmation_document", "final_pdf_hash", "varchar", 64, False, None, False),
    ("oa_sign_final_confirmation_document", "create_time", "datetime", None, False, CURRENT_TIMESTAMP_DEFAULT, False),
    ("sys_dept", "legal_entity_id", "bigint", None, True, None, False),
    ("oa_company_seal_config", "legal_entity_id", "bigint", None, True, None, False),
    ("oa_company_seal_config", "seal_code", "varchar", 64, True, None, False),
    ("oa_company_seal_config", "seal_type", "varchar", 32, False, "CONTRACT", False),
    ("oa_company_seal_config", "is_default", "char", 1, False, "N", False),
    ("oa_company_seal_config", "seal_image_hash", "varchar", 64, True, None, False),
    ("oa_company_seal_config", "valid_from", "datetime", None, True, None, False),
    ("oa_company_seal_config", "valid_to", "datetime", None, True, None, False),
    ("oa_sign_package", "legal_entity_source_dept_id", "bigint", None, True, None, False),
    ("oa_sign_package", "legal_entity_resolve_mode", "varchar", 20, True, None, False),
    ("oa_sign_package", "legal_entity_credit_code_snapshot", "varchar", 32, True, None, False),
    ("oa_sign_package", "legal_entity_address_snapshot", "varchar", 255, True, None, False),
    ("oa_sign_package", "legal_representative_snapshot", "varchar", 64, True, None, False),
    ("oa_sign_package", "legal_entity_phone_snapshot", "varchar", 32, True, None, False),
    ("oa_sign_package", "legal_entity_override_reason", "varchar", 500, True, None, False),
    ("oa_sign_package", "seal_id_snapshot", "bigint", None, True, None, False),
    ("oa_sign_package", "seal_name_snapshot", "varchar", 100, True, None, False),
    ("oa_sign_package", "seal_image_url_snapshot", "varchar", 500, True, None, False),
    ("oa_sign_package", "seal_image_hash_snapshot", "varchar", 64, True, None, False),
    ("oa_sign_package", "initial_signed_time", "datetime", None, True, None, False),
    ("oa_sign_package", "final_document_version", "varchar", 64, True, None, False),
    ("oa_sign_package", "final_document_root_hash", "varchar", 64, True, None, False),
    ("oa_sign_package", "final_generated_time", "datetime", None, True, None, False),
    ("oa_sign_package", "final_confirmed_time", "datetime", None, True, None, False),
    ("oa_sign_package", "final_confirmation_status", "varchar", 20, True, None, False),
    ("oa_sign_package_document", "final_pdf_url", "varchar", 500, True, None, False),
    ("oa_sign_package_document", "final_pdf_hash", "varchar", 64, True, None, False),
    ("oa_sign_package_document", "final_document_version", "varchar", 64, True, None, False),
    ("oa_sign_package_document", "final_read_confirmed", "char", 1, False, "N", False),
    ("oa_sign_plan_version", "legal_entity_id", "bigint", None, True, None, False),
    ("oa_sign_plan_version", "legal_entity_name", "varchar", 160, True, None, False),
)

UNSAFE_AUTOMATIC_MIGRATIONS = frozenset({
    "erp_unified_approval_center_20260714.sql",
    "erp_inventory_unified_approval_cutover_20260714.sql",
    "erp_oa_flowable_test_data_purge_20260714.sql",
})

AUTOMATIC_MIGRATION_DEPENDENCIES = {
    "erp_hr_health_certificate_unified_approval_20260714.sql": (
        "erp_hr_health_certificate_20260713.sql",
    ),
    "erp_unified_approval_seed_20260716.sql": (
        "erp_hr_health_certificate_unified_approval_20260714.sql",
        "erp_inventory_unified_approval_expand_20260716.sql",
        "erp_oa_purchase_unified_approval_expand_20260716.sql",
        "erp_unified_approval_schema_20260716.sql",
    ),
    "erp_inventory_transfer_approval_start_outbox_20260716.sql": (
        "erp_inventory_unified_approval_expand_20260716.sql",
        "erp_unified_approval_seed_20260716.sql",
    ),
    "erp_inventory_stock_check_approval_start_outbox_20260716.sql": (
        "erp_inventory_unified_approval_expand_20260716.sql",
        "erp_unified_approval_seed_20260716.sql",
    ),
    "erp_oa_purchase_approval_start_outbox_20260716.sql": (
        "erp_oa_purchase_unified_approval_expand_20260716.sql",
        "erp_unified_approval_seed_20260716.sql",
    ),
    "erp_hr_health_certificate_approval_start_outbox_20260716.sql": (
        "erp_hr_health_certificate_unified_approval_20260714.sql",
        "erp_unified_approval_seed_20260716.sql",
    ),
}


@dataclass(frozen=True)
class MigrationPlan:
    enabled: bool
    release_id: str = ""
    manifest_name: str = ""
    manifest_sha256: str = ""
    database_name: str = ""
    mysql_user: str = "root"
    mysql_password_file: str = "/root/.erp-mysql-root-pass"
    created_tables: tuple[str, ...] = ()
    required_tables: tuple[str, ...] = ()
    required_columns: tuple[tuple[str, str], ...] = ()
    required_indexes: tuple[tuple[str, str, str, bool], ...] = ()


def validate_automatic_migration_order(names: list[str]) -> None:
    positions = {name: index for index, name in enumerate(names)}
    unsafe = sorted(UNSAFE_AUTOMATIC_MIGRATIONS.intersection(positions))
    if unsafe:
        raise ValueError(
            "unsafe manual migration in automatic manifest: " + ", ".join(unsafe)
        )

    for migration, dependencies in AUTOMATIC_MIGRATION_DEPENDENCIES.items():
        if migration not in positions:
            continue
        missing = [dependency for dependency in dependencies if dependency not in positions]
        if missing:
            raise ValueError(
                f"automatic migration {migration} requires dependency closure: "
                + ", ".join(missing)
            )
        late = [
            dependency
            for dependency in dependencies
            if positions[dependency] >= positions[migration]
        ]
        if late:
            raise ValueError(
                f"automatic migration dependencies must precede {migration}: "
                + ", ".join(late)
            )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def approval_schema_contract() -> tuple[str, str, int, str, int]:
    """Return vetted table/column/key SQL fragments derived from safe expand DDL."""

    try:
        source = APPROVAL_SCHEMA_SOURCE.read_text(encoding="utf-8")
    except OSError as exc:
        raise ValueError(f"approval schema source is unavailable: {exc}") from exc
    tables: dict[str, list[str]] = {}
    unique_keys: list[tuple[str, str, str]] = []
    for match in re.finditer(
        r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+"
        r"([a-z][a-z0-9_]*)\s*\((.*?)\)\s*ENGINE=",
        source,
        re.IGNORECASE | re.DOTALL,
    ):
        table = match.group(1).lower()
        columns = []
        for line in match.group(2).splitlines():
            column_match = re.match(r"\s{4}([a-z][a-z0-9_]*)\s+", line)
            if column_match:
                columns.append(column_match.group(1).lower())
        if not columns or len(columns) != len(set(columns)):
            raise ValueError(f"approval schema columns are invalid: {table}")
        tables[table] = columns
        primary = re.search(r"PRIMARY\s+KEY\s*\(([^)]+)\)", match.group(2), re.IGNORECASE)
        if primary is None:
            raise ValueError(f"approval schema primary key is missing: {table}")

        def normalize_columns(raw: str) -> str:
            values = [value.strip().strip("`").lower() for value in raw.split(",")]
            if not values or any(not SAFE_TABLE.fullmatch(value) for value in values):
                raise ValueError(f"approval schema key columns are invalid: {table}")
            return ",".join(values)

        unique_keys.append((table, "PRIMARY", normalize_columns(primary.group(1))))
        for key_match in re.finditer(
            r"UNIQUE\s+KEY\s+([a-z][a-z0-9_]*)\s*\(([^)]+)\)",
            match.group(2),
            re.IGNORECASE,
        ):
            unique_keys.append(
                (table, key_match.group(1).lower(), normalize_columns(key_match.group(2)))
            )
    if tuple(tables) != EXPECTED_APPROVAL_TABLES:
        raise ValueError("approval schema source table order/content is unexpected")
    table_names = ",".join(f"'{name}'" for name in tables)
    column_conditions = " OR ".join(
        f"(table_name='{table}' AND column_name IN ("
        + ",".join(f"'{column}'" for column in columns)
        + "))"
        for table, columns in tables.items()
    )
    column_count = sum(map(len, tables.values()))
    if column_count != EXPECTED_APPROVAL_COLUMN_COUNT:
        raise ValueError(
            "approval schema column contract is unexpected: "
            f"{column_count} != {EXPECTED_APPROVAL_COLUMN_COUNT}"
        )
    if (
        len(unique_keys) != EXPECTED_APPROVAL_UNIQUE_KEY_COUNT
        or len({(table, key) for table, key, _ in unique_keys})
        != EXPECTED_APPROVAL_UNIQUE_KEY_COUNT
    ):
        raise ValueError("approval schema unique-key contract is unexpected")
    key_conditions = " OR ".join(
        f"(table_name='{table}' AND index_name='{key}' AND columns_csv='{columns}')"
        for table, key, columns in unique_keys
    )
    return (
        table_names,
        column_conditions,
        EXPECTED_APPROVAL_COLUMN_COUNT,
        key_conditions,
        EXPECTED_APPROVAL_UNIQUE_KEY_COUNT,
    )


def sign_column_fingerprint_contract() -> tuple[str, int]:
    """Return exact sign-cutover column type/null/default fingerprint predicates."""

    if len(SIGN_COLUMN_FINGERPRINTS) != 64 or len(
        {(item[0], item[1]) for item in SIGN_COLUMN_FINGERPRINTS}
    ) != len(SIGN_COLUMN_FINGERPRINTS):
        raise ValueError("sign column fingerprint contract is unexpected")
    conditions = []
    for table, column, data_type, length, nullable, default, auto_increment in (
        SIGN_COLUMN_FINGERPRINTS
    ):
        parts = [
            f"table_name='{table}'",
            f"column_name='{column}'",
            f"data_type='{data_type}'",
            f"is_nullable='{'YES' if nullable else 'NO'}'",
        ]
        if length is not None:
            parts.append(f"character_maximum_length={length}")
        if data_type in {"bigint", "int", "decimal"}:
            parts.append("LOWER(column_type) NOT LIKE '%unsigned%'")
        if default == CURRENT_TIMESTAMP_DEFAULT:
            parts.append(
                "UPPER(REPLACE(COALESCE(column_default,''),'()',''))="
                "'CURRENT_TIMESTAMP'"
            )
        elif default is None:
            parts.append("column_default IS NULL")
        else:
            escaped_default = default.replace("'", "''")
            parts.append(f"column_default='{escaped_default}'")
        if auto_increment:
            parts.append("LOWER(extra) LIKE '%auto_increment%'")
        conditions.append("(" + " AND ".join(parts) + ")")
    return " OR ".join(conditions), len(SIGN_COLUMN_FINGERPRINTS)


def load_and_validate_manifest(
    path: Path, *, require_deploy_mirror: bool = True
) -> dict:
    path = path.resolve()
    if not path.is_file():
        raise ValueError(f"migration manifest not found: {path}")
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"invalid migration manifest: {path}: {exc}") from exc

    if manifest.get("schemaVersion") != 1:
        raise ValueError("unsupported migration manifest schemaVersion")
    release_id = manifest.get("releaseId")
    if not isinstance(release_id, str) or not SAFE_ID.fullmatch(release_id):
        raise ValueError("invalid migration releaseId")

    root = path.parent.parent
    source_dir = root / str(manifest.get("sourceDirectory", ""))
    deploy_dir = root / str(manifest.get("deployDirectory", ""))
    list_path = root / str(manifest.get("migrationList", ""))
    if not list_path.is_file():
        raise ValueError(f"migration list not found: {list_path}")
    ordered = [
        line.strip()
        for line in list_path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]

    migrations = manifest.get("migrations")
    expected_count = manifest.get("migrationCount")
    if (
        not isinstance(expected_count, int)
        or isinstance(expected_count, bool)
        or expected_count < 1
    ):
        raise ValueError("migration manifest migrationCount must be a positive integer")
    if not isinstance(migrations, list) or len(migrations) != expected_count:
        raise ValueError("migration manifest entries differ from migrationCount")
    names = [item.get("file") for item in migrations]
    if names != ordered or len(set(names)) != len(names):
        raise ValueError("migration manifest and ordered list differ")
    validate_automatic_migration_order(names)

    for item in migrations:
        name = item.get("file")
        expected = item.get("sha256")
        if not isinstance(name, str) or not SAFE_SQL.fullmatch(name):
            raise ValueError(f"unsafe migration filename: {name!r}")
        if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
            raise ValueError(f"invalid migration hash: {name}")
        source = source_dir / name
        deploy = deploy_dir / name
        if not source.is_file() or (
            require_deploy_mirror and not deploy.is_file()
        ):
            raise ValueError(f"migration source/deploy pair is incomplete: {name}")
        if sha256_file(source) != expected or (
            require_deploy_mirror and sha256_file(deploy) != expected
        ):
            raise ValueError(f"migration hash mismatch: {name}")
        for key in ("creates", "backupTables"):
            values = item.get(key)
            if not isinstance(values, list) or any(
                not isinstance(value, str) or not SAFE_TABLE.fullmatch(value)
                for value in values
            ):
                raise ValueError(f"invalid {key} list for {name}")
    preconditions = manifest.get("preconditions", {})
    if not isinstance(preconditions, dict) or set(preconditions) - {
        "tables",
        "columns",
        "indexes",
    }:
        raise ValueError("invalid migration preconditions")
    required_tables = preconditions.get("tables", [])
    if (
        not isinstance(required_tables, list)
        or len(required_tables) != len(set(required_tables))
        or any(
            not isinstance(table, str) or not SAFE_TABLE.fullmatch(table)
            for table in required_tables
        )
    ):
        raise ValueError("invalid migration table preconditions")
    column_groups = preconditions.get("columns", [])
    if not isinstance(column_groups, list):
        raise ValueError("invalid migration column preconditions")
    seen_precondition_tables: set[str] = set()
    for group in column_groups:
        if not isinstance(group, dict) or set(group) != {"table", "names"}:
            raise ValueError("invalid migration column precondition group")
        table = group.get("table")
        columns = group.get("names")
        if (
            not isinstance(table, str)
            or not SAFE_TABLE.fullmatch(table)
            or table in seen_precondition_tables
            or not isinstance(columns, list)
            or not columns
            or len(columns) != len(set(columns))
            or any(
                not isinstance(column, str) or not SAFE_TABLE.fullmatch(column)
                for column in columns
            )
        ):
            raise ValueError("invalid migration column preconditions")
        seen_precondition_tables.add(table)
    indexes = preconditions.get("indexes", [])
    if not isinstance(indexes, list):
        raise ValueError("invalid migration index preconditions")
    seen_indexes: set[tuple[str, str]] = set()
    for index in indexes:
        if not isinstance(index, dict) or set(index) != {
            "table",
            "name",
            "columns",
            "unique",
        }:
            raise ValueError("invalid migration index precondition")
        table = index.get("table")
        name = index.get("name")
        columns = index.get("columns")
        unique = index.get("unique")
        if (
            not isinstance(table, str)
            or not SAFE_TABLE.fullmatch(table)
            or not isinstance(name, str)
            or not SAFE_INDEX.fullmatch(name)
            or (table, name) in seen_indexes
            or not isinstance(columns, list)
            or not columns
            or len(columns) != len(set(columns))
            or any(
                not isinstance(column, str) or not SAFE_TABLE.fullmatch(column)
                for column in columns
            )
            or not isinstance(unique, bool)
        ):
            raise ValueError("invalid migration index preconditions")
        seen_indexes.add((table, name))
    return manifest


def manifest_prerequisites(
    manifest: dict,
) -> tuple[
    tuple[str, ...],
    tuple[tuple[str, str], ...],
    tuple[tuple[str, str, str, bool], ...],
]:
    tables = {
        table
        for migration in manifest["migrations"]
        for table in migration["backupTables"]
    }
    tables.update(manifest.get("preconditions", {}).get("tables", []))
    columns = []
    for group in manifest.get("preconditions", {}).get("columns", []):
        tables.add(group["table"])
        columns.extend((group["table"], column) for column in group["names"])
    indexes = []
    for index in manifest.get("preconditions", {}).get("indexes", []):
        tables.add(index["table"])
        indexes.append(
            (
                index["table"],
                index["name"],
                ",".join(index["columns"]),
                index["unique"],
            )
        )
    return tuple(sorted(tables)), tuple(sorted(columns)), tuple(sorted(indexes))


def prepare_migration_plan(
    *,
    apply_migrations: bool,
    manifest_path: str | None,
    approval: str | None,
    database_name: str | None,
    mysql_user: str,
    mysql_password_file: str,
) -> MigrationPlan:
    if not apply_migrations:
        if approval:
            raise ValueError("--approve-migrations requires --apply-migrations")
        if database_name:
            raise ValueError("--database-name is only valid with --apply-migrations")
        if manifest_path:
            load_and_validate_manifest(
                Path(manifest_path), require_deploy_mirror=False
            )
        return MigrationPlan(enabled=False)

    if not manifest_path:
        raise ValueError("--migration-manifest is required with --apply-migrations")
    manifest_file = Path(manifest_path).resolve()
    manifest = load_and_validate_manifest(
        manifest_file, require_deploy_mirror=False
    )
    release_id = manifest["releaseId"]
    if manifest.get("executionPolicy", "automatic") != "automatic":
        raise ValueError(
            f"release {release_id!r} requires its documented manual phased migration workflow"
        )
    if approval != release_id:
        raise ValueError(
            f"--approve-migrations must exactly equal releaseId {release_id!r}"
        )
    if not database_name or not SAFE_DATABASE.fullmatch(database_name):
        raise ValueError("--database-name must contain only letters, numbers and underscore")
    if not SAFE_USER.fullmatch(mysql_user):
        raise ValueError("invalid --mysql-user")
    password_path = PurePosixPath(mysql_password_file)
    if not password_path.is_absolute() or "\x00" in mysql_password_file or "\n" in mysql_password_file:
        raise ValueError("--mysql-password-file must be a safe absolute remote path")

    required_tables, required_columns, required_indexes = manifest_prerequisites(manifest)
    created_tables = tuple(
        sorted(
            {
                table
                for migration in manifest["migrations"]
                for table in migration["creates"]
            }
        )
    )
    return MigrationPlan(
        enabled=True,
        release_id=release_id,
        manifest_name=manifest_file.name,
        manifest_sha256=sha256_file(manifest_file),
        database_name=database_name,
        mysql_user=mysql_user,
        mysql_password_file=mysql_password_file,
        created_tables=created_tables,
        required_tables=required_tables,
        required_columns=required_columns,
        required_indexes=required_indexes,
    )


def render_remote_migration_prerequisite_script(
    plan: MigrationPlan, provided_tables: frozenset[str] = frozenset()
) -> str:
    """Render a read-only gate that runs before the maintenance window."""

    if not plan.enabled:
        return 'echo "Migration prerequisite check skipped: migrations are disabled."'
    conditional_tables = tuple(
        table for table in plan.required_tables if table in provided_tables
    )
    required_tables = tuple(
        table for table in plan.required_tables if table not in provided_tables
    )
    required_columns = tuple(
        item for item in plan.required_columns if item[0] not in provided_tables
    )
    required_indexes = tuple(
        item for item in plan.required_indexes if item[0] not in provided_tables
    )
    if not required_tables and not conditional_tables:
        return f'echo "MIGRATION_PREREQUISITES_OK release={plan.release_id} requirements=0"'
    table_names = ",".join(f"'{table}'" for table in required_tables) or "''"
    column_conditions = " OR ".join(
        f"(table_name='{table}' AND column_name='{column}')"
        for table, column in required_columns
    ) or "FALSE"
    index_conditions = " OR ".join(
        "(table_name='{}' AND index_name='{}' AND columns_csv='{}' "
        "AND non_unique={})".format(table, name, columns, 0 if unique else 1)
        for table, name, columns, unique in required_indexes
    ) or "FALSE"
    conditional_checks = []
    for position, table in enumerate(conditional_tables, 1):
        columns = tuple(
            column
            for required_table, column in plan.required_columns
            if required_table == table
        )
        indexes = tuple(
            item for item in plan.required_indexes if item[0] == table
        )
        conditional_column_conditions = " OR ".join(
            f"(table_name='{table}' AND column_name='{column}')"
            for column in columns
        ) or "FALSE"
        conditional_index_conditions = " OR ".join(
            "(table_name='{}' AND index_name='{}' AND columns_csv='{}' "
            "AND non_unique={})".format(
                index_table, name, index_columns, 0 if unique else 1
            )
            for index_table, name, index_columns, unique in indexes
        ) or "FALSE"
        conditional_checks.append(
            f'''CONDITIONAL_TABLE_COUNT_{position}="$(mysql "${{prerequisite_mysql_args[@]}}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=database() AND table_name='{table}'")"
if [ "$CONDITIONAL_TABLE_COUNT_{position}" = 1 ]; then
  CONDITIONAL_COLUMN_COUNT_{position}="$(mysql "${{prerequisite_mysql_args[@]}}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND ({conditional_column_conditions})")"
  CONDITIONAL_INDEX_COUNT_{position}="$(mysql "${{prerequisite_mysql_args[@]}}" -e "SELECT COUNT(*) FROM (SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv FROM information_schema.statistics WHERE table_schema=database() GROUP BY table_name,index_name,non_unique) actual_indexes WHERE ({conditional_index_conditions})")"
  if [ "$CONDITIONAL_COLUMN_COUNT_{position}" != {len(columns)} ] || [ "$CONDITIONAL_INDEX_COUNT_{position}" != {len(indexes)} ]; then
    echo "migration prerequisite table already exists but is incomplete: release=$PREREQUISITE_RELEASE_ID table={table} columns=$CONDITIONAL_COLUMN_COUNT_{position}/{len(columns)} indexes=$CONDITIONAL_INDEX_COUNT_{position}/{len(indexes)}" >&2
    exit 60
  fi
elif [ "$CONDITIONAL_TABLE_COUNT_{position}" != 0 ]; then
  echo "migration prerequisite table count is invalid: release=$PREREQUISITE_RELEASE_ID table={table} count=$CONDITIONAL_TABLE_COUNT_{position}" >&2
  exit 60
fi
echo "CONDITIONAL_MIGRATION_PREREQUISITE_OK release=$PREREQUISITE_RELEASE_ID table={table} existing=$CONDITIONAL_TABLE_COUNT_{position}"'''
        )
    replacements = {
        "__RELEASE_ID__": shlex.quote(plan.release_id),
        "__DATABASE_NAME__": shlex.quote(plan.database_name),
        "__MYSQL_USER__": shlex.quote(plan.mysql_user),
        "__PASSWORD_FILE__": shlex.quote(plan.mysql_password_file),
        "__REQUIRED_TABLES__": table_names,
        "__REQUIRED_TABLE_COUNT__": str(len(required_tables)),
        "__REQUIRED_COLUMN_CONDITIONS__": column_conditions,
        "__REQUIRED_COLUMN_COUNT__": str(len(required_columns)),
        "__REQUIRED_INDEX_CONDITIONS__": index_conditions,
        "__REQUIRED_INDEX_COUNT__": str(len(required_indexes)),
        "__CONDITIONAL_TABLE_CHECKS__": "\n".join(conditional_checks),
        "__CONDITIONAL_TABLE_COUNT__": str(len(conditional_tables)),
    }
    script = r'''PREREQUISITE_RELEASE_ID=__RELEASE_ID__
PREREQUISITE_DATABASE=__DATABASE_NAME__
PREREQUISITE_MYSQL_USER=__MYSQL_USER__
PREREQUISITE_PASSWORD_FILE=__PASSWORD_FILE__
test -r "$PREREQUISITE_PASSWORD_FILE"
command -v mysql >/dev/null 2>&1
MYSQL_PWD="$(cat "$PREREQUISITE_PASSWORD_FILE")"
test -n "$MYSQL_PWD"
export MYSQL_PWD
prerequisite_mysql_args=(
  --protocol=tcp --batch --raw --skip-column-names
  --default-character-set=utf8mb4
  -u"$PREREQUISITE_MYSQL_USER" "$PREREQUISITE_DATABASE"
)
__CONDITIONAL_TABLE_CHECKS__
PREREQUISITE_TABLE_COUNT="$(mysql "${prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=database() AND table_name IN (__REQUIRED_TABLES__)")"
if [ "$PREREQUISITE_TABLE_COUNT" != __REQUIRED_TABLE_COUNT__ ]; then
  echo "migration prerequisites are incomplete: release=$PREREQUISITE_RELEASE_ID tables=$PREREQUISITE_TABLE_COUNT expected=__REQUIRED_TABLE_COUNT__" >&2
  exit 60
fi
PREREQUISITE_COLUMN_COUNT="$(mysql "${prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND (__REQUIRED_COLUMN_CONDITIONS__)")"
if [ "$PREREQUISITE_COLUMN_COUNT" != __REQUIRED_COLUMN_COUNT__ ]; then
  echo "migration prerequisites are incomplete: release=$PREREQUISITE_RELEASE_ID columns=$PREREQUISITE_COLUMN_COUNT expected=__REQUIRED_COLUMN_COUNT__" >&2
  exit 60
fi
PREREQUISITE_INDEX_COUNT="$(mysql "${prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM (SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv FROM information_schema.statistics WHERE table_schema=database() GROUP BY table_name,index_name,non_unique) actual_indexes WHERE (__REQUIRED_INDEX_CONDITIONS__)")"
if [ "$PREREQUISITE_INDEX_COUNT" != __REQUIRED_INDEX_COUNT__ ]; then
  echo "migration prerequisites are incomplete: release=$PREREQUISITE_RELEASE_ID indexes=$PREREQUISITE_INDEX_COUNT expected=__REQUIRED_INDEX_COUNT__" >&2
  exit 60
fi
unset MYSQL_PWD
echo "MIGRATION_PREREQUISITES_OK release=$PREREQUISITE_RELEASE_ID tables=$PREREQUISITE_TABLE_COUNT columns=$PREREQUISITE_COLUMN_COUNT indexes=$PREREQUISITE_INDEX_COUNT conditional_tables=__CONDITIONAL_TABLE_COUNT__"
'''
    for marker, value in replacements.items():
        script = script.replace(marker, value)
    return script


def render_remote_migration_script(plan: MigrationPlan) -> str:
    if not plan.enabled:
        return 'echo "Database migrations skipped: --apply-migrations was not supplied."'

    replacements = {
        "__RELEASE_ID__": shlex.quote(plan.release_id),
        "__MANIFEST_NAME__": shlex.quote(plan.manifest_name),
        "__MANIFEST_SHA__": shlex.quote(plan.manifest_sha256),
        "__DATABASE_NAME__": shlex.quote(plan.database_name),
        "__MYSQL_USER__": shlex.quote(plan.mysql_user),
        "__PASSWORD_FILE__": shlex.quote(plan.mysql_password_file),
    }
    script = r'''MIGRATION_RELEASE_ID=__RELEASE_ID__
MIGRATION_MANIFEST="$NEW_DOCKER/release/"__MANIFEST_NAME__
MIGRATION_SQL_DIR="$NEW_DOCKER/mysql/releases/$MIGRATION_RELEASE_ID"
MIGRATION_EXPECTED_MANIFEST_SHA=__MANIFEST_SHA__
MIGRATION_DATABASE=__DATABASE_NAME__
MIGRATION_MYSQL_USER=__MYSQL_USER__
MIGRATION_PASSWORD_FILE=__PASSWORD_FILE__
MIGRATION_RECORD_DIR="$RELEASE_DIR/db-backup/$MIGRATION_RELEASE_ID"

test -f "$MIGRATION_MANIFEST"
test -d "$MIGRATION_SQL_DIR"
test -r "$MIGRATION_PASSWORD_FILE"
command -v mysql >/dev/null 2>&1
command -v mysqldump >/dev/null 2>&1

ACTUAL_MANIFEST_SHA="$(sha256sum "$MIGRATION_MANIFEST" | awk '{print $1}')"
if [ "$ACTUAL_MANIFEST_SHA" != "$MIGRATION_EXPECTED_MANIFEST_SHA" ]; then
  echo "migration manifest sha256 mismatch" >&2
  exit 61
fi

mkdir -p "$MIGRATION_RECORD_DIR"
python3 - "$MIGRATION_MANIFEST" "$MIGRATION_SQL_DIR" "$MIGRATION_RECORD_DIR" <<'PYMIGRATION'
import hashlib
import json
import re
import sys
from pathlib import Path

manifest_path = Path(sys.argv[1])
sql_dir = Path(sys.argv[2])
record_dir = Path(sys.argv[3])
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
if manifest.get("schemaVersion") != 1:
    raise SystemExit("unsupported remote migration manifest")
migrations = manifest.get("migrations")
expected_count = manifest.get("migrationCount")
if (not isinstance(expected_count, int) or isinstance(expected_count, bool)
        or expected_count < 1 or not isinstance(migrations, list)
        or len(migrations) != expected_count):
    raise SystemExit("remote manifest entries differ from migrationCount")

files = []
required_backup_tables = set()
creates_tables = set()
hash_rows = []
unsafe_automatic_migrations = {
    "erp_unified_approval_center_20260714.sql",
    "erp_inventory_unified_approval_cutover_20260714.sql",
    "erp_oa_flowable_test_data_purge_20260714.sql",
}
automatic_dependencies = {
    "erp_hr_health_certificate_unified_approval_20260714.sql": (
        "erp_hr_health_certificate_20260713.sql",
    ),
    "erp_unified_approval_seed_20260716.sql": (
        "erp_hr_health_certificate_unified_approval_20260714.sql",
        "erp_inventory_unified_approval_expand_20260716.sql",
        "erp_oa_purchase_unified_approval_expand_20260716.sql",
        "erp_unified_approval_schema_20260716.sql",
    ),
    "erp_inventory_transfer_approval_start_outbox_20260716.sql": (
        "erp_inventory_unified_approval_expand_20260716.sql",
        "erp_unified_approval_seed_20260716.sql",
    ),
    "erp_inventory_stock_check_approval_start_outbox_20260716.sql": (
        "erp_inventory_unified_approval_expand_20260716.sql",
        "erp_unified_approval_seed_20260716.sql",
    ),
    "erp_oa_purchase_approval_start_outbox_20260716.sql": (
        "erp_oa_purchase_unified_approval_expand_20260716.sql",
        "erp_unified_approval_seed_20260716.sql",
    ),
    "erp_hr_health_certificate_approval_start_outbox_20260716.sql": (
        "erp_hr_health_certificate_unified_approval_20260714.sql",
        "erp_unified_approval_seed_20260716.sql",
    ),
}
for item in migrations:
    name = item.get("file")
    expected = item.get("sha256")
    if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9._-]+\.sql", name):
        raise SystemExit("unsafe remote migration filename")
    if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise SystemExit("invalid remote migration sha256")
    path = sql_dir / name
    if not path.is_file():
        raise SystemExit("missing packaged migration: " + name)
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        raise SystemExit("packaged migration sha256 mismatch: " + name)
    files.append(name)
    hash_rows.append(actual + "  " + name)
    for field, destination in (
        ("backupTables", required_backup_tables),
        ("creates", creates_tables),
    ):
        tables = item.get(field)
        if not isinstance(tables, list):
            raise SystemExit("invalid remote " + field + " list")
        for table in tables:
            if (not isinstance(table, str)
                    or not re.fullmatch(r"[a-z][a-z0-9_]*", table)):
                raise SystemExit("unsafe " + field + " table")
            destination.add(table)

positions = {name: index for index, name in enumerate(files)}
unsafe = sorted(unsafe_automatic_migrations.intersection(positions))
if unsafe:
    raise SystemExit("unsafe manual migration in automatic manifest: " + ", ".join(unsafe))
for migration, dependencies in automatic_dependencies.items():
    if migration not in positions:
        continue
    missing = [dependency for dependency in dependencies if dependency not in positions]
    if missing:
        raise SystemExit(
            "automatic migration dependency closure is incomplete for "
            + migration + ": " + ", ".join(missing)
        )
    late = [
        dependency for dependency in dependencies
        if positions[dependency] >= positions[migration]
    ]
    if late:
        raise SystemExit(
            "automatic migration dependencies must precede "
            + migration + ": " + ", ".join(late)
        )

(record_dir / "migration-files.txt").write_text("\n".join(files) + "\n", encoding="utf-8")
(record_dir / "migration-sha256.txt").write_text("\n".join(hash_rows) + "\n", encoding="utf-8")
(record_dir / "declared-backup-tables.txt").write_text(
    "\n".join(sorted(required_backup_tables))
    + ("\n" if required_backup_tables else ""),
    encoding="utf-8",
)
(record_dir / "declared-creates-tables.txt").write_text(
    "\n".join(sorted(creates_tables))
    + ("\n" if creates_tables else ""),
    encoding="utf-8",
)
PYMIGRATION

MYSQL_PWD="$(cat "$MIGRATION_PASSWORD_FILE")"
test -n "$MYSQL_PWD"
export MYSQL_PWD
mysql_args=(--protocol=tcp --batch --raw --default-character-set=utf8mb4 -u"$MIGRATION_MYSQL_USER" "$MIGRATION_DATABASE")

SERVER_INFO="$(mysql "${mysql_args[@]}" --skip-column-names -e "select concat(@@version, '|', @@character_set_database, '|', database())")"
SERVER_VERSION="${SERVER_INFO%%|*}"
case "$SERVER_VERSION" in
  5.7.*|8.0.*) ;;
  *) echo "unsupported MySQL version: $SERVER_VERSION" >&2; exit 62 ;;
esac
printf '%s\n' "$SERVER_INFO" > "$MIGRATION_RECORD_DIR/server-info.txt"

: > "$MIGRATION_RECORD_DIR/table-presence-before.txt"
: > "$MIGRATION_RECORD_DIR/backup-tables.candidates.txt"

while IFS= read -r table; do
  exists="$(mysql "${mysql_args[@]}" --skip-column-names -e "select count(*) from information_schema.tables where table_schema = database() and table_name = '$table'")"
  if [ "$exists" != "1" ]; then
    printf 'backupTables\t%s\tcreated-before=false\n' "$table" \
      >> "$MIGRATION_RECORD_DIR/table-presence-before.txt"
    echo "required backup table is missing: $table" >&2
    exit 63
  fi
  printf 'backupTables\t%s\tcreated-before=true\n' "$table" \
    >> "$MIGRATION_RECORD_DIR/table-presence-before.txt"
  printf '%s\n' "$table" >> "$MIGRATION_RECORD_DIR/backup-tables.candidates.txt"
done < "$MIGRATION_RECORD_DIR/declared-backup-tables.txt"

while IFS= read -r table; do
  exists="$(mysql "${mysql_args[@]}" --skip-column-names -e "select count(*) from information_schema.tables where table_schema = database() and table_name = '$table'")"
  case "$exists" in
    1)
      printf 'creates\t%s\tcreated-before=true\n' "$table" \
        >> "$MIGRATION_RECORD_DIR/table-presence-before.txt"
      printf '%s\n' "$table" >> "$MIGRATION_RECORD_DIR/backup-tables.candidates.txt"
      ;;
    0)
      printf 'creates\t%s\tcreated-before=false\n' "$table" \
        >> "$MIGRATION_RECORD_DIR/table-presence-before.txt"
      ;;
    *)
      echo "unexpected table-existence result for creates table $table: $exists" >&2
      exit 64
      ;;
  esac
done < "$MIGRATION_RECORD_DIR/declared-creates-tables.txt"

LC_ALL=C sort -u "$MIGRATION_RECORD_DIR/backup-tables.candidates.txt" \
  > "$MIGRATION_RECORD_DIR/backup-tables.txt"
rm -f "$MIGRATION_RECORD_DIR/backup-tables.candidates.txt"
if [ -s "$MIGRATION_RECORD_DIR/backup-tables.txt" ]; then
  BACKUP_TABLES=()
  while IFS= read -r table; do
    BACKUP_TABLES+=("$table")
  done < "$MIGRATION_RECORD_DIR/backup-tables.txt"
  mysqldump --protocol=tcp --single-transaction --quick --default-character-set=utf8mb4 \
    -u"$MIGRATION_MYSQL_USER" "$MIGRATION_DATABASE" "${BACKUP_TABLES[@]}" \
    | gzip > "$MIGRATION_RECORD_DIR/tables_before.sql.gz"
else
  printf '%s\n' '-- no migration-managed tables existed before this release' \
    | gzip > "$MIGRATION_RECORD_DIR/tables_before.sql.gz"
fi
test -s "$MIGRATION_RECORD_DIR/tables_before.sql.gz"
sha256sum "$MIGRATION_RECORD_DIR/tables_before.sql.gz" \
  > "$MIGRATION_RECORD_DIR/tables_before.sql.gz.sha256"

MIGRATION_FILES=()
while IFS= read -r name; do
  MIGRATION_FILES+=("$name")
done < "$MIGRATION_RECORD_DIR/migration-files.txt"
{
  printf "SET @erp_release_lock_status = GET_LOCK('erp:%s', 30);\n" "$MIGRATION_RELEASE_ID"
  printf '%s\n' \
    "SET @erp_release_lock_guard = IF(@erp_release_lock_status = 1, 'DO 0', 'SELECT * FROM __erp_release_lock_not_acquired__');" \
    'PREPARE erp_release_lock_stmt FROM @erp_release_lock_guard;' \
    'EXECUTE erp_release_lock_stmt;' \
    'DEALLOCATE PREPARE erp_release_lock_stmt;'
  for name in "${MIGRATION_FILES[@]}"; do
    printf 'SOURCE %s/%s;\n' "$MIGRATION_SQL_DIR" "$name"
  done
  printf "SELECT RELEASE_LOCK('erp:%s');\n" "$MIGRATION_RELEASE_ID"
} | mysql "${mysql_args[@]}" --show-warnings \
  > "$MIGRATION_RECORD_DIR/migration-output.txt"

unset MYSQL_PWD
printf 'MIGRATIONS_APPLIED release=%s database=%s backup=%s\n' \
  "$MIGRATION_RELEASE_ID" "$MIGRATION_DATABASE" "$MIGRATION_RECORD_DIR/tables_before.sql.gz"
'''
    for marker, value in replacements.items():
        script = script.replace(marker, value)
    return script


def render_sign_final_confirmation_prerequisite(plan: MigrationPlan) -> str:
    """Render the sign-only read gate that must pass before maintenance/writes."""

    sign_column_conditions, sign_column_count = sign_column_fingerprint_contract()
    replacements = {
        "__DATABASE_NAME__": shlex.quote(plan.database_name),
        "__MYSQL_USER__": shlex.quote(plan.mysql_user),
        "__PASSWORD_FILE__": shlex.quote(plan.mysql_password_file),
        "__SIGN_COLUMN_FINGERPRINT_CONDITIONS__": sign_column_conditions,
        "__SIGN_COLUMN_FINGERPRINT_COUNT__": str(sign_column_count),
    }
    script = r'''SIGN_PREREQUISITE_DATABASE=__DATABASE_NAME__
SIGN_PREREQUISITE_MYSQL_USER=__MYSQL_USER__
SIGN_PREREQUISITE_PASSWORD_FILE=__PASSWORD_FILE__

test -r "$SIGN_PREREQUISITE_PASSWORD_FILE"
command -v mysql >/dev/null 2>&1
MYSQL_PWD="$(cat "$SIGN_PREREQUISITE_PASSWORD_FILE")"
test -n "$MYSQL_PWD"
export MYSQL_PWD
sign_prerequisite_mysql_args=(
  --protocol=tcp --batch --raw --skip-column-names
  --default-character-set=utf8mb4
  -u"$SIGN_PREREQUISITE_MYSQL_USER" "$SIGN_PREREQUISITE_DATABASE"
)
SIGN_TABLE_COUNT="$(mysql "${sign_prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=database() AND table_name IN ('sys_legal_entity','oa_sign_final_confirmation','oa_sign_final_confirmation_document')")"
if [ "$SIGN_TABLE_COUNT" != 3 ]; then
  echo "sign final-confirmation schema is incomplete: created_tables=$SIGN_TABLE_COUNT expected=3" >&2
  exit 67
fi
SIGN_CREATED_TABLE_COLUMN_COUNT="$(mysql "${sign_prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND ((table_name='sys_legal_entity' AND column_name IN ('legal_entity_id','legal_entity_code','legal_entity_name','unified_social_credit_code','registered_address','legal_representative','contact_phone','status','version','create_by','create_time','update_by','update_time','remark')) OR (table_name='oa_sign_final_confirmation' AND column_name IN ('confirmation_id','package_id','employee_id','final_document_version','document_root_hash','confirmation_text','identity_method','request_id','ip_address','user_agent','confirmed_time','create_time')) OR (table_name='oa_sign_final_confirmation_document' AND column_name IN ('confirmation_document_id','confirmation_id','package_id','document_id','final_document_version','final_pdf_hash','create_time')))")"
if [ "$SIGN_CREATED_TABLE_COLUMN_COUNT" != 33 ]; then
  echo "sign final-confirmation created tables are incomplete: columns=$SIGN_CREATED_TABLE_COLUMN_COUNT expected=33" >&2
  exit 67
fi
SIGN_CREATED_TABLE_KEY_COUNT="$(mysql "${sign_prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM (SELECT table_name,index_name,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv FROM information_schema.statistics WHERE table_schema=database() AND non_unique=0 AND ((table_name='sys_legal_entity' AND index_name IN ('PRIMARY','uk_sys_legal_entity_code')) OR (table_name='oa_sign_final_confirmation' AND index_name IN ('PRIMARY','uk_oa_sign_final_confirmation_request','uk_oa_sign_final_confirmation_version')) OR (table_name='oa_sign_final_confirmation_document' AND index_name IN ('PRIMARY','uk_oa_sign_final_confirmation_document'))) GROUP BY table_name,index_name) actual_keys WHERE (table_name='sys_legal_entity' AND index_name='PRIMARY' AND columns_csv='legal_entity_id') OR (table_name='sys_legal_entity' AND index_name='uk_sys_legal_entity_code' AND columns_csv='legal_entity_code') OR (table_name='oa_sign_final_confirmation' AND index_name='PRIMARY' AND columns_csv='confirmation_id') OR (table_name='oa_sign_final_confirmation' AND index_name='uk_oa_sign_final_confirmation_request' AND columns_csv='request_id') OR (table_name='oa_sign_final_confirmation' AND index_name='uk_oa_sign_final_confirmation_version' AND columns_csv='package_id,final_document_version') OR (table_name='oa_sign_final_confirmation_document' AND index_name='PRIMARY' AND columns_csv='confirmation_document_id') OR (table_name='oa_sign_final_confirmation_document' AND index_name='uk_oa_sign_final_confirmation_document' AND columns_csv='confirmation_id,document_id')")"
if [ "$SIGN_CREATED_TABLE_KEY_COUNT" != 7 ]; then
  echo "sign final-confirmation created-table keys are incomplete: keys=$SIGN_CREATED_TABLE_KEY_COUNT expected=7" >&2
  exit 67
fi
SIGN_COLUMN_COUNT="$(mysql "${sign_prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND ((table_name='sys_dept' AND column_name IN ('legal_entity_id')) OR (table_name='oa_company_seal_config' AND column_name IN ('legal_entity_id','seal_code','seal_type','is_default','seal_image_hash','valid_from','valid_to')) OR (table_name='oa_sign_package' AND column_name IN ('legal_entity_source_dept_id','legal_entity_resolve_mode','legal_entity_credit_code_snapshot','legal_entity_address_snapshot','legal_representative_snapshot','legal_entity_phone_snapshot','legal_entity_override_reason','seal_id_snapshot','seal_name_snapshot','seal_image_url_snapshot','seal_image_hash_snapshot','initial_signed_time','final_document_version','final_document_root_hash','final_generated_time','final_confirmed_time','final_confirmation_status')) OR (table_name='oa_sign_package_document' AND column_name IN ('final_pdf_url','final_pdf_hash','final_document_version','final_read_confirmed')))")"
if [ "$SIGN_COLUMN_COUNT" != 29 ]; then
  echo "sign final-confirmation schema is incomplete: added_columns=$SIGN_COLUMN_COUNT expected=29" >&2
  exit 67
fi
SIGN_COLUMN_FINGERPRINT_COUNT="$(mysql "${sign_prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND (__SIGN_COLUMN_FINGERPRINT_CONDITIONS__)")"
if [ "$SIGN_COLUMN_FINGERPRINT_COUNT" != __SIGN_COLUMN_FINGERPRINT_COUNT__ ]; then
  echo "sign final-confirmation column fingerprint is incomplete: columns=$SIGN_COLUMN_FINGERPRINT_COUNT expected=__SIGN_COLUMN_FINGERPRINT_COUNT__" >&2
  exit 67
fi
SIGN_NULLABLE_PLAN_COLUMN_COUNT="$(mysql "${sign_prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND table_name='oa_sign_plan_version' AND column_name IN ('legal_entity_id','legal_entity_name') AND is_nullable='YES'")"
if [ "$SIGN_NULLABLE_PLAN_COLUMN_COUNT" != 2 ]; then
  echo "sign final-confirmation plan columns are not nullable" >&2
  exit 67
fi
SIGN_INDEX_COUNT="$(mysql "${sign_prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM (SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv FROM information_schema.statistics WHERE table_schema=database() AND ((table_name='sys_dept' AND index_name='idx_sys_dept_legal_entity') OR (table_name='oa_company_seal_config' AND index_name='idx_oa_company_seal_entity')) GROUP BY table_name,index_name,non_unique) actual_indexes WHERE (table_name='sys_dept' AND index_name='idx_sys_dept_legal_entity' AND columns_csv='legal_entity_id' AND non_unique=1) OR (table_name='oa_company_seal_config' AND index_name='idx_oa_company_seal_entity' AND columns_csv='legal_entity_id,status,is_default' AND non_unique=1)")"
if [ "$SIGN_INDEX_COUNT" != 2 ]; then
  echo "sign final-confirmation indexes are incomplete: indexes=$SIGN_INDEX_COUNT expected=2" >&2
  exit 67
fi
SIGN_LEGACY_TASK_COUNT="$(mysql "${sign_prerequisite_mysql_args[@]}" -e "SELECT COUNT(*) FROM oa_sign_task WHERE status='WAITING_HR_CONFIRM'")"
if [ "$SIGN_LEGACY_TASK_COUNT" != 0 ]; then
  echo "legacy WAITING_HR_CONFIRM tasks have no handler: count=$SIGN_LEGACY_TASK_COUNT" >&2
  exit 68
fi
unset MYSQL_PWD
echo "SIGN_FINAL_CONFIRMATION_PREREQUISITE_OK database=$SIGN_PREREQUISITE_DATABASE"
'''
    for marker, value in replacements.items():
        script = script.replace(marker, value)
    return script


def render_sign_final_confirmation_preflight(plan: MigrationPlan) -> str:
    """Render a read-only target gate required before sign-cutover code switches."""

    (
        approval_tables,
        approval_columns,
        approval_column_count,
        approval_key_conditions,
        approval_key_count,
    ) = approval_schema_contract()
    sign_column_conditions, sign_column_count = sign_column_fingerprint_contract()
    replacements = {
        "__DATABASE_NAME__": shlex.quote(plan.database_name),
        "__MYSQL_USER__": shlex.quote(plan.mysql_user),
        "__PASSWORD_FILE__": shlex.quote(plan.mysql_password_file),
        "__APPROVAL_TABLES__": approval_tables,
        "__APPROVAL_TABLE_COUNT__": str(len(EXPECTED_APPROVAL_TABLES)),
        "__APPROVAL_COLUMN_CONDITIONS__": approval_columns,
        "__APPROVAL_COLUMN_COUNT__": str(approval_column_count),
        "__APPROVAL_KEY_CONDITIONS__": approval_key_conditions,
        "__APPROVAL_KEY_COUNT__": str(approval_key_count),
        "__SIGN_COLUMN_FINGERPRINT_CONDITIONS__": sign_column_conditions,
        "__SIGN_COLUMN_FINGERPRINT_COUNT__": str(sign_column_count),
    }
    script = r'''SIGN_PREFLIGHT_DATABASE=__DATABASE_NAME__
SIGN_PREFLIGHT_MYSQL_USER=__MYSQL_USER__
SIGN_PREFLIGHT_PASSWORD_FILE=__PASSWORD_FILE__

test -r "$SIGN_PREFLIGHT_PASSWORD_FILE"
command -v mysql >/dev/null 2>&1
MYSQL_PWD="$(cat "$SIGN_PREFLIGHT_PASSWORD_FILE")"
test -n "$MYSQL_PWD"
export MYSQL_PWD
sign_mysql_args=(
  --protocol=tcp --batch --raw --skip-column-names
  --default-character-set=utf8mb4
  -u"$SIGN_PREFLIGHT_MYSQL_USER" "$SIGN_PREFLIGHT_DATABASE"
)
APPROVAL_TABLE_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=database() AND table_name IN (__APPROVAL_TABLES__)")"
if [ "$APPROVAL_TABLE_COUNT" != __APPROVAL_TABLE_COUNT__ ]; then
  echo "unified approval schema is incomplete: tables=$APPROVAL_TABLE_COUNT expected=__APPROVAL_TABLE_COUNT__" >&2
  exit 70
fi
APPROVAL_COLUMN_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND (__APPROVAL_COLUMN_CONDITIONS__)")"
if [ "$APPROVAL_COLUMN_COUNT" != __APPROVAL_COLUMN_COUNT__ ]; then
  echo "unified approval schema is incomplete: columns=$APPROVAL_COLUMN_COUNT expected=__APPROVAL_COLUMN_COUNT__" >&2
  exit 70
fi
APPROVAL_UNIQUE_KEY_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM (SELECT table_name,index_name,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv FROM information_schema.statistics WHERE table_schema=database() AND table_name IN (__APPROVAL_TABLES__) AND non_unique=0 GROUP BY table_name,index_name) actual_keys WHERE (__APPROVAL_KEY_CONDITIONS__)")"
if [ "$APPROVAL_UNIQUE_KEY_COUNT" != __APPROVAL_KEY_COUNT__ ]; then
  echo "unified approval schema unique keys are incomplete: keys=$APPROVAL_UNIQUE_KEY_COUNT expected=__APPROVAL_KEY_COUNT__" >&2
  exit 70
fi
APPROVAL_ASSOCIATION_COLUMN_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND ((table_name='inv_stock_check' AND column_name IN ('approval_instance_id','approval_round','approval_engine','last_approval_event_key','row_version')) OR (table_name='inv_transfer_order' AND column_name IN ('approval_instance_id','approval_round','approval_engine','last_approval_event_key')) OR (table_name='hr_employee_health_certificate' AND column_name IN ('approval_instance_id','approval_round','last_approval_event_key')) OR (table_name='oa_purchase' AND column_name IN ('approval_instance_id','approval_round','row_version','last_approval_event_key')))")"
if [ "$APPROVAL_ASSOCIATION_COLUMN_COUNT" != 16 ]; then
  echo "unified approval business associations are incomplete: columns=$APPROVAL_ASSOCIATION_COLUMN_COUNT expected=16" >&2
  exit 70
fi
APPROVAL_ASSOCIATION_INDEX_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM (SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv FROM information_schema.statistics WHERE table_schema=database() AND ((table_name='inv_stock_check' AND index_name='idx_inv_stock_check_approval_engine_status') OR (table_name='inv_transfer_order' AND index_name='idx_inv_transfer_approval_engine_status') OR (table_name='hr_employee_health_certificate' AND index_name='idx_hr_health_approval_instance') OR (table_name='oa_purchase' AND index_name='idx_oa_purchase_approval_instance')) GROUP BY table_name,index_name,non_unique) actual_indexes WHERE (table_name='inv_stock_check' AND index_name='idx_inv_stock_check_approval_engine_status' AND columns_csv='approval_engine,status,submitted_time' AND non_unique=1) OR (table_name='inv_transfer_order' AND index_name='idx_inv_transfer_approval_engine_status' AND columns_csv='approval_engine,status,submitted_time' AND non_unique=1) OR (table_name='hr_employee_health_certificate' AND index_name='idx_hr_health_approval_instance' AND columns_csv='approval_instance_id,approval_round' AND non_unique=1) OR (table_name='oa_purchase' AND index_name='idx_oa_purchase_approval_instance' AND columns_csv='approval_instance_id,approval_round' AND non_unique=1)")"
if [ "$APPROVAL_ASSOCIATION_INDEX_COUNT" != 4 ]; then
  echo "unified approval business association indexes are incomplete: indexes=$APPROVAL_ASSOCIATION_INDEX_COUNT expected=4" >&2
  exit 70
fi
OA_LEGACY_PENDING_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM oa_purchase WHERE approval_instance_id IS NULL AND COALESCE(LOWER(TRIM(status)),'') NOT IN ('draft','approved','returned','rejected','withdrawn','terminated','cancelled')")"
if [ "$OA_LEGACY_PENDING_COUNT" != 0 ]; then
  echo "legacy OA purchase approvals have no retained handler: count=$OA_LEGACY_PENDING_COUNT" >&2
  exit 71
fi
SIGN_TABLE_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=database() AND table_name IN ('sys_legal_entity','oa_sign_final_confirmation','oa_sign_final_confirmation_document')")"
if [ "$SIGN_TABLE_COUNT" != 3 ]; then
  echo "sign final-confirmation schema is incomplete: created_tables=$SIGN_TABLE_COUNT expected=3" >&2
  exit 67
fi
SIGN_CREATED_TABLE_COLUMN_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND ((table_name='sys_legal_entity' AND column_name IN ('legal_entity_id','legal_entity_code','legal_entity_name','unified_social_credit_code','registered_address','legal_representative','contact_phone','status','version','create_by','create_time','update_by','update_time','remark')) OR (table_name='oa_sign_final_confirmation' AND column_name IN ('confirmation_id','package_id','employee_id','final_document_version','document_root_hash','confirmation_text','identity_method','request_id','ip_address','user_agent','confirmed_time','create_time')) OR (table_name='oa_sign_final_confirmation_document' AND column_name IN ('confirmation_document_id','confirmation_id','package_id','document_id','final_document_version','final_pdf_hash','create_time')))")"
if [ "$SIGN_CREATED_TABLE_COLUMN_COUNT" != 33 ]; then
  echo "sign final-confirmation created tables are incomplete: columns=$SIGN_CREATED_TABLE_COLUMN_COUNT expected=33" >&2
  exit 67
fi
SIGN_CREATED_TABLE_KEY_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM (SELECT table_name,index_name,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv FROM information_schema.statistics WHERE table_schema=database() AND non_unique=0 AND ((table_name='sys_legal_entity' AND index_name IN ('PRIMARY','uk_sys_legal_entity_code')) OR (table_name='oa_sign_final_confirmation' AND index_name IN ('PRIMARY','uk_oa_sign_final_confirmation_request','uk_oa_sign_final_confirmation_version')) OR (table_name='oa_sign_final_confirmation_document' AND index_name IN ('PRIMARY','uk_oa_sign_final_confirmation_document'))) GROUP BY table_name,index_name) actual_keys WHERE (table_name='sys_legal_entity' AND index_name='PRIMARY' AND columns_csv='legal_entity_id') OR (table_name='sys_legal_entity' AND index_name='uk_sys_legal_entity_code' AND columns_csv='legal_entity_code') OR (table_name='oa_sign_final_confirmation' AND index_name='PRIMARY' AND columns_csv='confirmation_id') OR (table_name='oa_sign_final_confirmation' AND index_name='uk_oa_sign_final_confirmation_request' AND columns_csv='request_id') OR (table_name='oa_sign_final_confirmation' AND index_name='uk_oa_sign_final_confirmation_version' AND columns_csv='package_id,final_document_version') OR (table_name='oa_sign_final_confirmation_document' AND index_name='PRIMARY' AND columns_csv='confirmation_document_id') OR (table_name='oa_sign_final_confirmation_document' AND index_name='uk_oa_sign_final_confirmation_document' AND columns_csv='confirmation_id,document_id')")"
if [ "$SIGN_CREATED_TABLE_KEY_COUNT" != 7 ]; then
  echo "sign final-confirmation created-table keys are incomplete: keys=$SIGN_CREATED_TABLE_KEY_COUNT expected=7" >&2
  exit 67
fi
SIGN_COLUMN_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND ((table_name='sys_dept' AND column_name IN ('legal_entity_id')) OR (table_name='oa_company_seal_config' AND column_name IN ('legal_entity_id','seal_code','seal_type','is_default','seal_image_hash','valid_from','valid_to')) OR (table_name='oa_sign_package' AND column_name IN ('legal_entity_source_dept_id','legal_entity_resolve_mode','legal_entity_credit_code_snapshot','legal_entity_address_snapshot','legal_representative_snapshot','legal_entity_phone_snapshot','legal_entity_override_reason','seal_id_snapshot','seal_name_snapshot','seal_image_url_snapshot','seal_image_hash_snapshot','initial_signed_time','final_document_version','final_document_root_hash','final_generated_time','final_confirmed_time','final_confirmation_status')) OR (table_name='oa_sign_package_document' AND column_name IN ('final_pdf_url','final_pdf_hash','final_document_version','final_read_confirmed')))")"
if [ "$SIGN_COLUMN_COUNT" != 29 ]; then
  echo "sign final-confirmation schema is incomplete: added_columns=$SIGN_COLUMN_COUNT expected=29" >&2
  exit 67
fi
SIGN_COLUMN_FINGERPRINT_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND (__SIGN_COLUMN_FINGERPRINT_CONDITIONS__)")"
if [ "$SIGN_COLUMN_FINGERPRINT_COUNT" != __SIGN_COLUMN_FINGERPRINT_COUNT__ ]; then
  echo "sign final-confirmation column fingerprint is incomplete: columns=$SIGN_COLUMN_FINGERPRINT_COUNT expected=__SIGN_COLUMN_FINGERPRINT_COUNT__" >&2
  exit 67
fi
SIGN_NULLABLE_PLAN_COLUMN_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=database() AND table_name='oa_sign_plan_version' AND column_name IN ('legal_entity_id','legal_entity_name') AND is_nullable='YES'")"
if [ "$SIGN_NULLABLE_PLAN_COLUMN_COUNT" != 2 ]; then
  echo "sign final-confirmation plan columns are not nullable" >&2
  exit 67
fi
SIGN_INDEX_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM (SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv FROM information_schema.statistics WHERE table_schema=database() AND ((table_name='sys_dept' AND index_name='idx_sys_dept_legal_entity') OR (table_name='oa_company_seal_config' AND index_name='idx_oa_company_seal_entity')) GROUP BY table_name,index_name,non_unique) actual_indexes WHERE (table_name='sys_dept' AND index_name='idx_sys_dept_legal_entity' AND columns_csv='legal_entity_id' AND non_unique=1) OR (table_name='oa_company_seal_config' AND index_name='idx_oa_company_seal_entity' AND columns_csv='legal_entity_id,status,is_default' AND non_unique=1)")"
if [ "$SIGN_INDEX_COUNT" != 2 ]; then
  echo "sign final-confirmation indexes are incomplete: indexes=$SIGN_INDEX_COUNT expected=2" >&2
  exit 67
fi
NATIVE_APPROVAL_SAFE_FLAG_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM (SELECT config_key FROM sys_config WHERE config_key IN ('feature.inventory.stock-check-native-approval.enabled','feature.inventory.transfer-native-approval.enabled') GROUP BY config_key HAVING COUNT(*)=1 AND SUM(LOWER(TRIM(config_value)) IN ('false','0','no','off'))=1) exact_safe_flags")"
if [ "$NATIVE_APPROVAL_SAFE_FLAG_COUNT" != 2 ]; then
  echo "inventory native approval flags must both exist exactly once with explicit false values" >&2
  exit 69
fi
SIGN_LEGACY_TASK_COUNT="$(mysql "${sign_mysql_args[@]}" -e "SELECT COUNT(*) FROM oa_sign_task WHERE status='WAITING_HR_CONFIRM'")"
if [ "$SIGN_LEGACY_TASK_COUNT" != 0 ]; then
  echo "legacy WAITING_HR_CONFIRM tasks have no handler: count=$SIGN_LEGACY_TASK_COUNT" >&2
  exit 68
fi
unset MYSQL_PWD
echo "SIGN_FINAL_CONFIRMATION_PREFLIGHT_OK database=$SIGN_PREFLIGHT_DATABASE"
'''
    for marker, value in replacements.items():
        script = script.replace(marker, value)
    return script
