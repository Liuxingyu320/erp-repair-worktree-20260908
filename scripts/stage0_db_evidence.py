#!/usr/bin/env python3
"""Collect and verify privacy-minimized Stage 0 database evidence.

The collector deliberately supports only fixed, aggregate SELECT statements.  It
uses one MySQL connection and an explicitly read-only consistent snapshot.  The
offline verifier binds the evidence to the current candidate commit, this
collector, the four release manifests, and the generated query contract.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping

from release_migration_contract import (
    EXPECTED_APPROVAL_TABLES,
    SAFE_DATABASE,
    SAFE_TABLE,
    SAFE_USER,
    approval_schema_contract,
    load_and_validate_manifest,
    manifest_prerequisites,
    sha256_file,
    sign_column_fingerprint_contract,
)


ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = Path(__file__).resolve()
EVIDENCE_KIND = "stage0-database-readonly-evidence"
CONTRACT_KIND = "stage0-database-readonly-contract"
SCHEMA_VERSION = 1
PROFILES = ("pre-maintenance", "release-ready")
PROFILE_MAX_AGE = {
    "pre-maintenance": timedelta(hours=24),
    "release-ready": timedelta(hours=2),
}
SYSTEM_DATABASES = {"information_schema", "mysql", "performance_schema", "sys"}
ALLOWED_DEFAULTS_OPTIONS = frozenset(
    {
        "connect-timeout",
        "default-character-set",
        "get-server-public-key",
        "host",
        "password",
        "port",
        "protocol",
        "server-public-key-path",
        "socket",
        "ssl-ca",
        "ssl-capath",
        "ssl-cert",
        "ssl-cipher",
        "ssl-crl",
        "ssl-crlpath",
        "ssl-key",
        "ssl-mode",
        "tls-ciphersuites",
        "tls-version",
        "user",
    }
)
EXPECTED_NEW_BUSINESS_FEATURE_FLAGS = (
    "feature.hr.health-certificate.enabled",
    "feature.inventory.store-return.enabled",
    "feature.inventory.transfer-discrepancy.enabled",
    "feature.inventory.customer-service-card.enabled",
    "feature.oa.purchase.enabled",
    "feature.inventory.stock-check-native-approval.enabled",
    "feature.inventory.transfer-native-approval.enabled",
)
SHA256_RE = re.compile(r"[0-9a-f]{64}")
COMMIT_RE = re.compile(r"[0-9a-f]{40}")
INTEGER_RE = re.compile(r"0|[1-9][0-9]*")
SERVER_VERSION_RE = re.compile(r"[0-9][A-Za-z0-9._+~-]{0,127}")
HOST_RE = re.compile(r"[A-Za-z0-9._:-]+")
TARGET_LABEL_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
OUTPUT_NAME_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
CHECK_ID_RE = re.compile(r"[A-Z][A-Z0-9_]+")
SECRET_KEY_RE = re.compile(
    r"(?:password|passwd|secret|access.?key|private.?key|bearer|token)", re.I
)
META_SERVER_MARKER = "STAGE0_META_SERVER_VERSION"
CHECK_MARKER_PREFIX = "STAGE0_CHECK_"
SENTINEL_MARKER = "STAGE0_EVIDENCE_SENTINEL"
SENTINEL_VALUE = "COMPLETE"

MANIFEST_PATHS = {
    "newBusiness": ROOT / "scripts/new-business-release-20260714.json",
    "approvalExpand": ROOT
    / "scripts/unified-approval-expand-stage0-release-20260716.json",
    "signFinal": ROOT / "scripts/sign-final-confirmation-release-20260714.json",
    "inventorySafety": ROOT
    / "scripts/inventory-native-approval-stage0-disable-release-20260716.json",
}
EXPECTED_RELEASE_IDS = {
    "newBusiness": "new-business-20260714",
    "approvalExpand": "unified-approval-expand-stage0-20260716",
    "signFinal": "sign-final-confirmation-20260714",
    "inventorySafety": "inventory-native-approval-stage0-disable-20260716",
}


class EvidenceError(ValueError):
    """Base class for contract, validation, and verification failures."""


class ContractError(EvidenceError):
    """The repository no longer matches the pinned Stage 0 evidence contract."""


class ProtocolError(EvidenceError):
    """MySQL output was missing, malformed, duplicated, or unexpected."""


@dataclass(frozen=True)
class CheckSpec:
    check_id: str
    expression: str
    expected: int | tuple[int, ...]
    comparison: str = "eq"

    def __post_init__(self) -> None:
        if not CHECK_ID_RE.fullmatch(self.check_id):
            raise ContractError(f"unsafe check id: {self.check_id!r}")
        if self.comparison not in {"eq", "gte", "in", "health-columns"}:
            raise ContractError(
                f"unsupported comparison for {self.check_id}: {self.comparison}"
            )

    @property
    def marker(self) -> str:
        return CHECK_MARKER_PREFIX + self.check_id

    @property
    def query(self) -> str:
        return f"SELECT '{self.marker}', CAST(({self.expression}) AS CHAR);"

    @property
    def query_sha256(self) -> str:
        return sha256_bytes(self.query.encode("utf-8"))

    def contract_value(self) -> dict[str, Any]:
        expected: int | list[int]
        if isinstance(self.expected, tuple):
            expected = list(self.expected)
        else:
            expected = self.expected
        return {
            "id": self.check_id,
            "querySha256": self.query_sha256,
            "expected": expected,
            "comparison": self.comparison,
        }


@dataclass(frozen=True)
class EvidenceContract:
    profile: str
    checks: tuple[CheckSpec, ...]
    manifest_sha256: Mapping[str, str]

    def canonical_value(self) -> dict[str, Any]:
        return {
            "schemaVersion": SCHEMA_VERSION,
            "kind": CONTRACT_KIND,
            "profile": self.profile,
            "mysqlProtocol": {
                "singleConnection": True,
                "sessionReadOnly": True,
                "consistentSnapshot": True,
                "transactionIsolation": "REPEATABLE READ",
                "sentinel": SENTINEL_VALUE,
            },
            "manifestSha256": dict(self.manifest_sha256),
            "checks": [check.contract_value() for check in self.checks],
        }

    @property
    def sha256(self) -> str:
        return canonical_sha256(self.canonical_value())


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical_json_bytes(value: Any) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def canonical_sha256(value: Any) -> str:
    return sha256_bytes(canonical_json_bytes(value))


def manifest_hashes(root: Path = ROOT) -> dict[str, str]:
    paths = manifest_paths(root)
    result: dict[str, str] = {}
    for label, path in paths.items():
        manifest = load_and_validate_manifest(path, require_deploy_mirror=False)
        if manifest.get("releaseId") != EXPECTED_RELEASE_IDS[label]:
            raise ContractError(f"unexpected releaseId in {path.name}")
        if label == "signFinal" and manifest.get("executionPolicy") != "manual-phased":
            raise ContractError("sign final-confirmation must remain manual-phased")
        if label in {"approvalExpand", "inventorySafety"} and manifest.get(
            "executionPolicy", "automatic"
        ) != "automatic":
            raise ContractError(f"{path.name} must remain automatic")
        result[label] = sha256_file(path)
    return result


def manifest_paths(root: Path = ROOT) -> dict[str, Path]:
    return {
        "newBusiness": root / "scripts/new-business-release-20260714.json",
        "approvalExpand": root
        / "scripts/unified-approval-expand-stage0-release-20260716.json",
        "signFinal": root
        / "scripts/sign-final-confirmation-release-20260714.json",
        "inventorySafety": root
        / "scripts/inventory-native-approval-stage0-disable-release-20260716.json",
    }


def quoted_values(values: Iterable[str]) -> str:
    safe = []
    for value in values:
        if not SAFE_TABLE.fullmatch(value):
            raise ContractError(f"unsafe schema identifier: {value!r}")
        safe.append("'" + value + "'")
    if not safe:
        raise ContractError("empty schema identifier contract")
    return ",".join(safe)


def quoted_config_keys(values: Iterable[str]) -> str:
    safe = []
    for value in values:
        if not re.fullmatch(r"[a-z][a-z0-9.-]{0,127}", value):
            raise ContractError(f"unsafe config key: {value!r}")
        safe.append("'" + value + "'")
    if not safe:
        raise ContractError("empty config-key contract")
    return ",".join(safe)


def column_conditions(values: Iterable[tuple[str, str]]) -> str:
    conditions = []
    for table, column in values:
        if not SAFE_TABLE.fullmatch(table) or not SAFE_TABLE.fullmatch(column):
            raise ContractError("unsafe manifest column prerequisite")
        conditions.append(f"(table_name='{table}' AND column_name='{column}')")
    if not conditions:
        raise ContractError("empty column prerequisite contract")
    return " OR ".join(conditions)


def index_conditions(values: Iterable[tuple[str, str, str, bool]]) -> str:
    conditions = []
    for table, name, columns, unique in values:
        if not SAFE_TABLE.fullmatch(table):
            raise ContractError("unsafe manifest index table")
        if not re.fullmatch(r"(?:PRIMARY|[a-z][a-z0-9_]*)", name):
            raise ContractError("unsafe manifest index name")
        if any(not SAFE_TABLE.fullmatch(value) for value in columns.split(",")):
            raise ContractError("unsafe manifest index columns")
        conditions.append(
            "(table_name='{}' AND index_name='{}' AND columns_csv='{}' "
            "AND non_unique={})".format(
                table, name, columns, 0 if unique else 1
            )
        )
    if not conditions:
        raise ContractError("empty index prerequisite contract")
    return " OR ".join(conditions)


def count_tables_expression(names: Iterable[str]) -> str:
    return (
        "SELECT COUNT(*) FROM information_schema.tables "
        "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' "
        "AND table_name IN ("
        + quoted_values(names)
        + ")"
    )


def count_columns_expression(values: Iterable[tuple[str, str]]) -> str:
    return (
        "SELECT COUNT(*) FROM information_schema.columns "
        "WHERE table_schema=DATABASE() AND ("
        + column_conditions(values)
        + ")"
    )


def count_column_conditions_expression(conditions: str) -> str:
    if not conditions or ";" in conditions:
        raise ContractError("unsafe column prerequisite conditions")
    return (
        "SELECT COUNT(*) FROM information_schema.columns "
        "WHERE table_schema=DATABASE() AND ("
        + conditions
        + ")"
    )


def count_indexes_expression(values: Iterable[tuple[str, str, str, bool]]) -> str:
    return (
        "SELECT COUNT(*) FROM ("
        "SELECT table_name,index_name,non_unique,"
        "GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_csv "
        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
        "GROUP BY table_name,index_name,non_unique) actual_indexes WHERE ("
        + index_conditions(values)
        + ")"
    )


SIGN_CREATED_COLUMN_CONDITIONS = (
    "(table_name='sys_legal_entity' AND column_name IN ("
    "'legal_entity_id','legal_entity_code','legal_entity_name',"
    "'unified_social_credit_code','registered_address','legal_representative',"
    "'contact_phone','status','version','create_by','create_time','update_by',"
    "'update_time','remark')) OR "
    "(table_name='oa_sign_final_confirmation' AND column_name IN ("
    "'confirmation_id','package_id','employee_id','final_document_version',"
    "'document_root_hash','confirmation_text','identity_method','request_id',"
    "'ip_address','user_agent','confirmed_time','create_time')) OR "
    "(table_name='oa_sign_final_confirmation_document' AND column_name IN ("
    "'confirmation_document_id','confirmation_id','package_id','document_id',"
    "'final_document_version','final_pdf_hash','create_time'))"
)

SIGN_CREATED_KEY_CONDITIONS = (
    "(table_name='sys_legal_entity' AND index_name='PRIMARY' "
    "AND columns_csv='legal_entity_id') OR "
    "(table_name='sys_legal_entity' AND index_name='uk_sys_legal_entity_code' "
    "AND columns_csv='legal_entity_code') OR "
    "(table_name='oa_sign_final_confirmation' AND index_name='PRIMARY' "
    "AND columns_csv='confirmation_id') OR "
    "(table_name='oa_sign_final_confirmation' "
    "AND index_name='uk_oa_sign_final_confirmation_request' "
    "AND columns_csv='request_id') OR "
    "(table_name='oa_sign_final_confirmation' "
    "AND index_name='uk_oa_sign_final_confirmation_version' "
    "AND columns_csv='package_id,final_document_version') OR "
    "(table_name='oa_sign_final_confirmation_document' AND index_name='PRIMARY' "
    "AND columns_csv='confirmation_document_id') OR "
    "(table_name='oa_sign_final_confirmation_document' "
    "AND index_name='uk_oa_sign_final_confirmation_document' "
    "AND columns_csv='confirmation_id,document_id')"
)

SIGN_ADDED_COLUMN_CONDITIONS = (
    "(table_name='sys_dept' AND column_name='legal_entity_id') OR "
    "(table_name='oa_company_seal_config' AND column_name IN ("
    "'legal_entity_id','seal_code','seal_type','is_default','seal_image_hash',"
    "'valid_from','valid_to')) OR "
    "(table_name='oa_sign_package' AND column_name IN ("
    "'legal_entity_source_dept_id','legal_entity_resolve_mode',"
    "'legal_entity_credit_code_snapshot','legal_entity_address_snapshot',"
    "'legal_representative_snapshot','legal_entity_phone_snapshot',"
    "'legal_entity_override_reason','seal_id_snapshot','seal_name_snapshot',"
    "'seal_image_url_snapshot','seal_image_hash_snapshot','initial_signed_time',"
    "'final_document_version','final_document_root_hash','final_generated_time',"
    "'final_confirmed_time','final_confirmation_status')) OR "
    "(table_name='oa_sign_package_document' AND column_name IN ("
    "'final_pdf_url','final_pdf_hash','final_document_version',"
    "'final_read_confirmed'))"
)

SIGN_KEY_SOURCE_FILTER = (
    "(table_name='sys_legal_entity' AND index_name IN ("
    "'PRIMARY','uk_sys_legal_entity_code')) OR "
    "(table_name='oa_sign_final_confirmation' AND index_name IN ("
    "'PRIMARY','uk_oa_sign_final_confirmation_request',"
    "'uk_oa_sign_final_confirmation_version')) OR "
    "(table_name='oa_sign_final_confirmation_document' AND index_name IN ("
    "'PRIMARY','uk_oa_sign_final_confirmation_document'))"
)

SIGN_INDEX_CONDITIONS = (
    "(table_name='sys_dept' AND index_name='idx_sys_dept_legal_entity' "
    "AND columns_csv='legal_entity_id' AND non_unique=1) OR "
    "(table_name='oa_company_seal_config' "
    "AND index_name='idx_oa_company_seal_entity' "
    "AND columns_csv='legal_entity_id,status,is_default' AND non_unique=1)"
)

APPROVAL_ASSOCIATION_COLUMN_CONDITIONS = (
    "(table_name='inv_stock_check' AND column_name IN ("
    "'approval_instance_id','approval_round','approval_engine',"
    "'last_approval_event_key','row_version')) OR "
    "(table_name='inv_transfer_order' AND column_name IN ("
    "'approval_instance_id','approval_round','approval_engine',"
    "'last_approval_event_key')) OR "
    "(table_name='hr_employee_health_certificate' AND column_name IN ("
    "'approval_instance_id','approval_round','last_approval_event_key')) OR "
    "(table_name='oa_purchase' AND column_name IN ("
    "'approval_instance_id','approval_round','row_version',"
    "'last_approval_event_key'))"
)

APPROVAL_ASSOCIATION_INDEX_FILTER = (
    "(table_name='inv_stock_check' "
    "AND index_name='idx_inv_stock_check_approval_engine_status') OR "
    "(table_name='inv_transfer_order' "
    "AND index_name='idx_inv_transfer_approval_engine_status') OR "
    "(table_name='hr_employee_health_certificate' "
    "AND index_name='idx_hr_health_approval_instance') OR "
    "(table_name='oa_purchase' "
    "AND index_name='idx_oa_purchase_approval_instance')"
)

APPROVAL_ASSOCIATION_INDEX_CONDITIONS = (
    "(table_name='inv_stock_check' "
    "AND index_name='idx_inv_stock_check_approval_engine_status' "
    "AND columns_csv='approval_engine,status,submitted_time' AND non_unique=1) OR "
    "(table_name='inv_transfer_order' "
    "AND index_name='idx_inv_transfer_approval_engine_status' "
    "AND columns_csv='approval_engine,status,submitted_time' AND non_unique=1) OR "
    "(table_name='hr_employee_health_certificate' "
    "AND index_name='idx_hr_health_approval_instance' "
    "AND columns_csv='approval_instance_id,approval_round' AND non_unique=1) OR "
    "(table_name='oa_purchase' "
    "AND index_name='idx_oa_purchase_approval_instance' "
    "AND columns_csv='approval_instance_id,approval_round' AND non_unique=1)"
)


def build_contract(profile: str, root: Path = ROOT) -> EvidenceContract:
    if profile not in PROFILES:
        raise ContractError(f"unsupported profile: {profile}")
    paths = manifest_paths(root)
    manifests = {
        label: load_and_validate_manifest(path, require_deploy_mirror=False)
        for label, path in paths.items()
    }
    hashes = manifest_hashes(root)

    new_tables, new_columns, new_indexes = manifest_prerequisites(
        manifests["newBusiness"]
    )
    expand_tables, expand_columns, expand_indexes = manifest_prerequisites(
        manifests["approvalExpand"]
    )
    inventory_tables, inventory_columns, inventory_indexes = manifest_prerequisites(
        manifests["inventorySafety"]
    )
    sign_tables, sign_columns, sign_indexes = manifest_prerequisites(
        manifests["signFinal"]
    )
    new_created = tuple(
        sorted(
            {
                table
                for migration in manifests["newBusiness"]["migrations"]
                for table in migration["creates"]
            }
        )
    )
    if len(new_created) != 26:
        raise ContractError("new-business created-table contract drifted")
    feature_flags = tuple(manifests["newBusiness"].get("featureFlags", ()))
    if feature_flags != EXPECTED_NEW_BUSINESS_FEATURE_FLAGS:
        raise ContractError("new-business feature-flag contract drifted")
    conditional_tables = tuple(
        table for table in expand_tables if table in new_created
    )
    if conditional_tables != ("hr_employee_health_certificate",):
        raise ContractError(
            "expected only hr_employee_health_certificate to be conditional"
        )
    health_table = conditional_tables[0]
    required_expand_tables = tuple(
        table for table in expand_tables if table != health_table
    )
    required_expand_columns = tuple(
        item for item in expand_columns if item[0] != health_table
    )
    health_columns = tuple(item for item in expand_columns if item[0] == health_table)
    required_expand_indexes = tuple(
        item for item in expand_indexes if item[0] != health_table
    )

    pinned_counts = (
        len(new_tables),
        len(new_columns),
        len(new_indexes),
        len(expand_tables),
        len(expand_columns),
        len(expand_indexes),
        len(required_expand_tables),
        len(required_expand_columns),
        len(required_expand_indexes),
        len(health_columns),
        len(inventory_tables),
        len(inventory_columns),
        len(inventory_indexes),
        len(sign_tables),
        len(sign_columns),
        len(sign_indexes),
    )
    if pinned_counts != (17, 0, 0, 10, 134, 10, 9, 132, 10, 2, 1, 0, 0, 7, 0, 0):
        raise ContractError(f"Stage 0 manifest prerequisite counts drifted: {pinned_counts}")
    if inventory_tables != ("sys_config",):
        raise ContractError("inventory safety manifest must require only sys_config")

    sign_fingerprint_conditions, sign_fingerprint_count = (
        sign_column_fingerprint_contract()
    )
    if sign_fingerprint_count != 64:
        raise ContractError("sign column fingerprint count drifted")

    checks: list[CheckSpec] = [
        CheckSpec(
            "MYSQL_MAJOR",
            "SELECT CAST(SUBSTRING_INDEX(VERSION(),'.',1) AS UNSIGNED)",
            8,
            "gte",
        ),
        CheckSpec(
            "MYSQL_IS_MARIADB",
            "SELECT CASE WHEN LOWER(VERSION()) LIKE '%mariadb%' OR "
            "LOWER(@@version_comment) LIKE '%mariadb%' THEN 1 ELSE 0 END",
            0,
        ),
        CheckSpec(
            "SESSION_REPEATABLE_READ",
            "SELECT CASE WHEN @@session.transaction_isolation="
            "'REPEATABLE-READ' THEN 1 ELSE 0 END",
            1,
        ),
        CheckSpec("SESSION_READ_ONLY", "SELECT @@session.transaction_read_only", 1),
        CheckSpec(
            "NEW_BUSINESS_BACKUP_TABLES",
            count_tables_expression(new_tables),
            len(new_tables),
        ),
        CheckSpec(
            "UNIFIED_REQUIRED_TABLES",
            count_tables_expression(required_expand_tables),
            len(required_expand_tables),
        ),
        CheckSpec(
            "UNIFIED_REQUIRED_COLUMNS",
            count_columns_expression(required_expand_columns),
            len(required_expand_columns),
        ),
        CheckSpec(
            "UNIFIED_REQUIRED_INDEXES",
            count_indexes_expression(required_expand_indexes),
            len(required_expand_indexes),
        ),
        CheckSpec(
            "HEALTH_TABLE_COUNT",
            count_tables_expression((health_table,)),
            1 if profile == "release-ready" else (0, 1),
            "eq" if profile == "release-ready" else "in",
        ),
        CheckSpec(
            "HEALTH_REQUIRED_COLUMNS",
            count_columns_expression(health_columns),
            2,
            "eq" if profile == "release-ready" else "health-columns",
        ),
        CheckSpec(
            "TODO_BASE_TABLES",
            count_tables_expression(
                (
                    "inv_purchase_return",
                    "inv_purchase_return_detail",
                    "inv_stock_check",
                    "inv_stock_check_detail",
                    "inv_inbound_record",
                    "inv_stock_log",
                    "inv_purchase_order",
                    "sys_menu",
                    "sys_role",
                    "sys_role_menu",
                    "sys_config",
                    "sys_user",
                    "sys_dept",
                    "sys_user_profile",
                )
            ),
            14,
        ),
        CheckSpec(
            "TODO_MENU_ANCHORS",
            "SELECT COUNT(*) FROM sys_menu WHERE menu_id IN "
            "(4040,4100,4300,4308,4400,4450,4460,4465,4470)",
            9,
        ),
        CheckSpec(
            "TODO_PURCHASE_ANCHOR",
            "SELECT COUNT(*) FROM sys_menu WHERE "
            "LOWER(COALESCE(component,''))='inventory/purchase/index' AND "
            "LOWER(COALESCE(perms,''))='inv:purchase:list' AND "
            "visible='0' AND status='0'",
            1,
            "gte",
        ),
        CheckSpec(
            "TODO_DUPLICATE_CONFIG_KEYS",
            "SELECT COUNT(*) FROM (SELECT config_key FROM sys_config WHERE "
            "config_key IN ('todo.stock-check.due-soon.hours',"
            "'feature.hr.health-certificate.enabled',"
            "'todo.health-certificate.warning-days') GROUP BY config_key "
            "HAVING COUNT(*)>1) duplicate_config",
            0,
        ),
        CheckSpec(
            "SIGN_CREATED_TABLES",
            count_tables_expression(
                (
                    "sys_legal_entity",
                    "oa_sign_final_confirmation",
                    "oa_sign_final_confirmation_document",
                )
            ),
            3,
        ),
        CheckSpec(
            "SIGN_CREATED_COLUMNS",
            count_column_conditions_expression(SIGN_CREATED_COLUMN_CONDITIONS),
            33,
        ),
        CheckSpec(
            "SIGN_CREATED_UNIQUE_KEYS",
            "SELECT COUNT(*) FROM (SELECT table_name,index_name,"
            "GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
            "AS columns_csv FROM information_schema.statistics WHERE "
            "table_schema=DATABASE() AND non_unique=0 AND ("
            + SIGN_KEY_SOURCE_FILTER
            + ") GROUP BY table_name,index_name) actual_keys WHERE ("
            + SIGN_CREATED_KEY_CONDITIONS
            + ")",
            7,
        ),
        CheckSpec(
            "SIGN_ADDED_COLUMNS",
            count_column_conditions_expression(SIGN_ADDED_COLUMN_CONDITIONS),
            29,
        ),
        CheckSpec(
            "SIGN_COLUMN_FINGERPRINTS",
            count_column_conditions_expression(sign_fingerprint_conditions),
            64,
        ),
        CheckSpec(
            "SIGN_PLAN_NULLABLE_COLUMNS",
            "SELECT COUNT(*) FROM information_schema.columns WHERE "
            "table_schema=DATABASE() AND table_name='oa_sign_plan_version' "
            "AND column_name IN ('legal_entity_id','legal_entity_name') "
            "AND is_nullable='YES'",
            2,
        ),
        CheckSpec(
            "SIGN_INDEXES",
            "SELECT COUNT(*) FROM (SELECT table_name,index_name,non_unique,"
            "GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
            "AS columns_csv FROM information_schema.statistics WHERE "
            "table_schema=DATABASE() AND ((table_name='sys_dept' AND "
            "index_name='idx_sys_dept_legal_entity') OR "
            "(table_name='oa_company_seal_config' AND "
            "index_name='idx_oa_company_seal_entity')) "
            "GROUP BY table_name,index_name,non_unique) actual_indexes WHERE ("
            + SIGN_INDEX_CONDITIONS
            + ")",
            2,
        ),
        CheckSpec(
            "SIGN_WAITING_HR_CONFIRM",
            "SELECT COUNT(*) FROM oa_sign_task WHERE status='WAITING_HR_CONFIRM'",
            0,
        ),
    ]

    if profile == "release-ready":
        (
            approval_tables,
            approval_columns,
            approval_column_count,
            approval_key_conditions,
            approval_key_count,
        ) = approval_schema_contract()
        checks.extend(
            (
                CheckSpec(
                    "NEW_BUSINESS_CREATED_TABLES",
                    count_tables_expression(new_created),
                    len(new_created),
                ),
                CheckSpec(
                    "NEW_BUSINESS_SAFE_FLAGS",
                    "SELECT COUNT(*) FROM (SELECT config_key FROM sys_config "
                    "WHERE config_key IN ("
                    + quoted_config_keys(feature_flags)
                    + ") GROUP BY config_key HAVING COUNT(*)=1 AND "
                    "SUM(LOWER(TRIM(config_value)) IN "
                    "('false','0','no','off'))=1) exact_safe_flags",
                    len(feature_flags),
                ),
                CheckSpec(
                    "SIGN_SEAL_CODE_MISSING",
                    "SELECT COUNT(*) FROM oa_company_seal_config WHERE "
                    "seal_code IS NULL OR TRIM(seal_code)=''",
                    0,
                ),
                CheckSpec(
                    "SIGN_ROLE_CONTRACT",
                    "SELECT COUNT(*) FROM (SELECT role_key FROM sys_role WHERE "
                    "role_key='sign_single_hr' GROUP BY role_key "
                    "HAVING COUNT(*)=1 AND SUM(role_name='合同签约经办人' "
                    "AND status='0' AND del_flag='0')=1) exact_sign_role",
                    1,
                ),
                CheckSpec(
                    "APPROVAL_TABLES",
                    "SELECT COUNT(*) FROM information_schema.tables WHERE "
                    "table_schema=DATABASE() AND table_name IN ("
                    + approval_tables
                    + ")",
                    len(EXPECTED_APPROVAL_TABLES),
                ),
                CheckSpec(
                    "APPROVAL_COLUMNS",
                    "SELECT COUNT(*) FROM information_schema.columns WHERE "
                    "table_schema=DATABASE() AND ("
                    + approval_columns
                    + ")",
                    approval_column_count,
                ),
                CheckSpec(
                    "APPROVAL_UNIQUE_KEYS",
                    "SELECT COUNT(*) FROM (SELECT table_name,index_name,"
                    "GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
                    "AS columns_csv FROM information_schema.statistics WHERE "
                    "table_schema=DATABASE() AND table_name IN ("
                    + approval_tables
                    + ") AND non_unique=0 GROUP BY table_name,index_name) "
                    "actual_keys WHERE ("
                    + approval_key_conditions
                    + ")",
                    approval_key_count,
                ),
                CheckSpec(
                    "APPROVAL_ASSOCIATION_COLUMNS",
                    count_column_conditions_expression(
                        APPROVAL_ASSOCIATION_COLUMN_CONDITIONS
                    ),
                    16,
                ),
                CheckSpec(
                    "APPROVAL_ASSOCIATION_INDEXES",
                    "SELECT COUNT(*) FROM (SELECT table_name,index_name,non_unique,"
                    "GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
                    "AS columns_csv FROM information_schema.statistics WHERE "
                    "table_schema=DATABASE() AND ("
                    + APPROVAL_ASSOCIATION_INDEX_FILTER
                    + ") GROUP BY table_name,index_name,non_unique) actual_indexes WHERE ("
                    + APPROVAL_ASSOCIATION_INDEX_CONDITIONS
                    + ")",
                    4,
                ),
                CheckSpec(
                    "OA_LEGACY_PENDING",
                    "SELECT COUNT(*) FROM oa_purchase WHERE "
                    "approval_instance_id IS NULL AND "
                    "COALESCE(LOWER(TRIM(status)),'') "
                    "NOT IN ('draft','approved','returned','rejected',"
                    "'withdrawn','terminated','cancelled')",
                    0,
                ),
                CheckSpec(
                    "INVENTORY_SAFE_FLAGS",
                    "SELECT COUNT(*) FROM (SELECT config_key FROM sys_config "
                    "WHERE config_key IN ("
                    "'feature.inventory.stock-check-native-approval.enabled',"
                    "'feature.inventory.transfer-native-approval.enabled') "
                    "GROUP BY config_key HAVING COUNT(*)=1 AND "
                    "SUM(LOWER(TRIM(config_value)) IN "
                    "('false','0','no','off'))=1) exact_safe_flags",
                    2,
                ),
            )
        )

    check_ids = [check.check_id for check in checks]
    if len(check_ids) != len(set(check_ids)):
        raise ContractError("duplicate check ids in generated contract")
    return EvidenceContract(profile, tuple(checks), hashes)


def render_mysql_sql(contract: EvidenceContract) -> str:
    lines = [
        "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;",
        "SET SESSION TRANSACTION READ ONLY;",
        "START TRANSACTION WITH CONSISTENT SNAPSHOT;",
        f"SELECT '{META_SERVER_MARKER}', VERSION();",
    ]
    lines.extend(check.query for check in contract.checks)
    lines.extend(
        (
            "COMMIT;",
            f"SELECT '{SENTINEL_MARKER}', '{SENTINEL_VALUE}';",
        )
    )
    return "\n".join(lines) + "\n"


def parse_mysql_output(
    output: str, contract: EvidenceContract
) -> tuple[str, dict[str, int]]:
    known_markers = {check.marker: check.check_id for check in contract.checks}
    expected_sequence = [META_SERVER_MARKER]
    expected_sequence.extend(check.marker for check in contract.checks)
    expected_sequence.append(SENTINEL_MARKER)
    marker_sequence: list[str] = []
    server_version: str | None = None
    metrics: dict[str, int] = {}
    sentinel_count = 0
    for line_number, raw in enumerate(output.splitlines(), 1):
        line = raw.rstrip("\r")
        if not line:
            continue
        fields = line.split("\t")
        if len(fields) != 2:
            raise ProtocolError(f"malformed MySQL evidence row at line {line_number}")
        marker, value = fields
        marker_sequence.append(marker)
        if marker == META_SERVER_MARKER:
            if server_version is not None:
                raise ProtocolError("duplicate server-version marker")
            if not SERVER_VERSION_RE.fullmatch(value):
                raise ProtocolError("invalid server-version value")
            server_version = value
        elif marker == SENTINEL_MARKER:
            sentinel_count += 1
            if sentinel_count != 1 or value != SENTINEL_VALUE:
                raise ProtocolError("invalid or duplicate completion sentinel")
        elif marker in known_markers:
            check_id = known_markers[marker]
            if check_id in metrics:
                raise ProtocolError(f"duplicate check marker: {check_id}")
            if not INTEGER_RE.fullmatch(value):
                raise ProtocolError(f"non-integer check value: {check_id}")
            metrics[check_id] = int(value)
        else:
            raise ProtocolError(f"unknown MySQL evidence marker: {marker!r}")
    if server_version is None:
        raise ProtocolError("missing server-version marker")
    if sentinel_count != 1:
        raise ProtocolError("missing completion sentinel")
    expected_ids = {check.check_id for check in contract.checks}
    missing = sorted(expected_ids - set(metrics))
    if missing:
        raise ProtocolError("missing check marker(s): " + ", ".join(missing))
    if marker_sequence != expected_sequence:
        raise ProtocolError("MySQL evidence markers are out of contract order")
    return server_version, metrics


def effective_expected(
    check: CheckSpec, metrics: Mapping[str, int]
) -> int | list[int]:
    if check.comparison == "health-columns":
        table_count = metrics.get("HEALTH_TABLE_COUNT")
        if table_count == 0:
            return 0
        return 2
    if isinstance(check.expected, tuple):
        return list(check.expected)
    return check.expected


def check_passes(check: CheckSpec, metrics: Mapping[str, int]) -> bool:
    actual = metrics[check.check_id]
    if check.comparison == "eq":
        return actual == check.expected
    if check.comparison == "gte":
        return isinstance(check.expected, int) and actual >= check.expected
    if check.comparison == "in":
        return isinstance(check.expected, tuple) and actual in check.expected
    if check.comparison == "health-columns":
        table_count = metrics.get("HEALTH_TABLE_COUNT")
        return (table_count == 0 and actual == 0) or (
            table_count == 1 and actual == 2
        )
    raise ContractError(f"unsupported comparison: {check.comparison}")


def evidence_checks(
    contract: EvidenceContract, metrics: Mapping[str, int]
) -> tuple[list[dict[str, Any]], bool]:
    if set(metrics) != {check.check_id for check in contract.checks}:
        raise ProtocolError("metric ids do not exactly match the evidence contract")
    values = []
    all_passed = True
    for check in contract.checks:
        passed = check_passes(check, metrics)
        all_passed = all_passed and passed
        values.append(
            {
                "id": check.check_id,
                "querySha256": check.query_sha256,
                "actual": metrics[check.check_id],
                "expected": effective_expected(check, metrics),
                "comparison": check.comparison,
                "status": "passed" if passed else "blocked",
            }
        )
    return values, all_passed


def build_evidence(
    *,
    contract: EvidenceContract,
    database: str,
    target_label: str,
    server_version: str,
    metrics: Mapping[str, int],
    candidate_commit: str,
    collected_at: datetime | None = None,
    run_id: str | None = None,
    collector_path: Path = SCRIPT_PATH,
) -> dict[str, Any]:
    validate_database(database)
    validate_target_label(target_label)
    if not SERVER_VERSION_RE.fullmatch(server_version):
        raise ProtocolError("invalid server version")
    if not COMMIT_RE.fullmatch(candidate_commit):
        raise EvidenceError("candidate commit must be a full lowercase Git SHA")
    checks, all_passed = evidence_checks(contract, metrics)
    timestamp = collected_at or datetime.now(timezone.utc)
    if timestamp.tzinfo is None:
        raise EvidenceError("collected_at must include a timezone")
    timestamp = timestamp.astimezone(timezone.utc)
    identity = {
        "profile": contract.profile,
        "database": database,
        "targetLabel": target_label,
        "serverVersion": server_version,
        "metrics": [
            {"id": check.check_id, "actual": metrics[check.check_id]}
            for check in contract.checks
        ],
    }
    return {
        "schemaVersion": SCHEMA_VERSION,
        "kind": EVIDENCE_KIND,
        "profile": contract.profile,
        "candidateCommit": candidate_commit,
        "runId": run_id or str(uuid.uuid4()),
        "collectedAt": timestamp.isoformat().replace("+00:00", "Z"),
        "collectorSha256": sha256_file(collector_path),
        "contractSha256": contract.sha256,
        "manifestSha256": dict(contract.manifest_sha256),
        "target": {
            "database": database,
            "targetLabel": target_label,
            "serverVersion": server_version,
            "sessionReadOnly": metrics["SESSION_READ_ONLY"] == 1,
            "schemaFingerprintSha256": canonical_sha256(identity),
        },
        "checks": checks,
        "status": "passed" if all_passed else "blocked",
    }


def validate_database(database: str) -> None:
    if not SAFE_DATABASE.fullmatch(database):
        raise EvidenceError("database must contain only letters, numbers and underscore")
    if database.lower() in SYSTEM_DATABASES:
        raise EvidenceError("system databases are not allowed evidence targets")


def validate_target_label(target_label: str) -> None:
    if not TARGET_LABEL_RE.fullmatch(target_label):
        raise EvidenceError(
            "target label must be 1-64 characters using letters, numbers, dot, "
            "underscore or hyphen"
        )


def validate_defaults_file_options(path: Path) -> None:
    try:
        source = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise EvidenceError("--defaults-file must be readable UTF-8 text") from exc
    in_client_group = False
    saw_client_group = False
    seen_options: set[str] = set()
    for line_number, raw_line in enumerate(source.splitlines(), 1):
        stripped = raw_line.strip()
        if not stripped or stripped.startswith(("#", ";")):
            continue
        if stripped.startswith("!"):
            raise EvidenceError("--defaults-file include directives are forbidden")
        if stripped.startswith("["):
            if not re.fullmatch(r"\[client\]", stripped, re.I):
                raise EvidenceError(
                    "--defaults-file may contain only one [client] group"
                )
            if saw_client_group:
                raise EvidenceError("--defaults-file contains duplicate [client]")
            saw_client_group = True
            in_client_group = True
            continue
        if not in_client_group:
            raise EvidenceError(
                f"--defaults-file option outside [client] at line {line_number}"
            )
        option_match = re.fullmatch(
            r"([A-Za-z][A-Za-z0-9_-]*)\s*=.*", raw_line.strip()
        )
        if option_match is None:
            raise EvidenceError(
                f"--defaults-file has unsupported syntax at line {line_number}"
            )
        option = option_match.group(1).lower().replace("_", "-")
        if option not in ALLOWED_DEFAULTS_OPTIONS:
            raise EvidenceError(
                f"--defaults-file option is forbidden: {option}"
            )
        if option in seen_options:
            raise EvidenceError(f"--defaults-file option is duplicated: {option}")
        seen_options.add(option)
    if not saw_client_group:
        raise EvidenceError("--defaults-file must contain one [client] group")


def validate_defaults_file(raw_path: str) -> Path:
    path = Path(raw_path)
    if not path.is_absolute():
        raise EvidenceError("--defaults-file must be an absolute path")
    if path.is_symlink():
        raise EvidenceError("--defaults-file must not be a symlink")
    try:
        details = path.stat()
    except OSError as exc:
        raise EvidenceError("--defaults-file is not readable") from exc
    if not stat.S_ISREG(details.st_mode):
        raise EvidenceError("--defaults-file must be a regular file")
    if details.st_uid not in {0, os.geteuid()}:
        raise EvidenceError("--defaults-file must be owned by root or the current user")
    if stat.S_IMODE(details.st_mode) != 0o600:
        raise EvidenceError("--defaults-file permissions must be exactly 0600")
    if not os.access(path, os.R_OK):
        raise EvidenceError("--defaults-file is not readable")
    resolved = path.resolve(strict=True)
    validate_defaults_file_options(resolved)
    return resolved


def validate_mysql_bin(raw: str) -> str:
    resolved = shutil.which(raw)
    if resolved is None:
        raise EvidenceError(f"mysql client is unavailable: {raw}")
    path = Path(resolved)
    if not path.is_file() or not os.access(path, os.X_OK):
        raise EvidenceError(f"mysql client is not executable: {raw}")
    return str(path.resolve())


def safe_child_environment() -> dict[str, str]:
    environment = dict(os.environ)
    for key in list(environment):
        normalized = key.upper()
        if any(
            token in normalized
            for token in ("PASSWORD", "PASSWD", "SECRET", "ACCESS_KEY", "TOKEN")
        ) or normalized == "MYSQL_PWD":
            environment.pop(key, None)
    environment["LC_ALL"] = "C"
    environment["LANG"] = "C"
    environment["MYSQL_HISTFILE"] = "/dev/null"
    environment["MYSQL_TEST_LOGIN_FILE"] = "/dev/null"
    return environment


def execute_mysql(
    *,
    mysql_bin: str,
    defaults_file: Path,
    database: str,
    sql: str,
    host: str | None = None,
    port: int | None = None,
    user: str | None = None,
    timeout_seconds: int = 120,
) -> str:
    defaults_file = validate_defaults_file(str(defaults_file))
    if host is None or port is None or user is None:
        raise EvidenceError("--host, --port and --user are required")
    command = [
        mysql_bin,
        f"--defaults-file={defaults_file}",
        "--batch",
        "--raw",
        "--skip-column-names",
        "--default-character-set=utf8mb4",
        "--connect-timeout=10",
    ]
    if not HOST_RE.fullmatch(host):
        raise EvidenceError("invalid --host")
    command.extend(("--protocol=TCP", f"--host={host}"))
    if port < 1 or port > 65535:
        raise EvidenceError("--port must be between 1 and 65535")
    command.append(f"--port={port}")
    if not SAFE_USER.fullmatch(user):
        raise EvidenceError("invalid --user")
    command.append(f"--user={user}")
    command.append(f"--database={database}")
    if any(argument.startswith("--password") for argument in command):
        raise EvidenceError("password command-line arguments are forbidden")
    try:
        result = subprocess.run(
            command,
            input=sql,
            text=True,
            capture_output=True,
            timeout=timeout_seconds,
            env=safe_child_environment(),
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ProtocolError("mysql evidence collection did not complete") from exc
    if result.returncode != 0:
        raise ProtocolError(
            f"mysql evidence collection failed with exit code {result.returncode}"
        )
    return result.stdout


def git_commit(root: Path = ROOT) -> str:
    try:
        value = subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "HEAD"],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError) as exc:
        raise EvidenceError("cannot determine the current Git commit") from exc
    if not COMMIT_RE.fullmatch(value):
        raise EvidenceError("current Git commit is not a full lowercase SHA")
    return value


def require_clean_worktree(root: Path = ROOT) -> None:
    try:
        result = subprocess.run(
            [
                "git",
                "-C",
                str(root),
                "status",
                "--porcelain=v1",
                "--untracked-files=normal",
            ],
            text=True,
            capture_output=True,
            check=False,
        )
    except OSError as exc:
        raise EvidenceError("cannot inspect the Git worktree") from exc
    if result.returncode != 0:
        raise EvidenceError("cannot inspect the Git worktree")
    if result.stdout.strip():
        raise EvidenceError("database evidence requires a clean Git worktree")


def output_companion(path: Path) -> Path:
    return Path(str(path) + ".sha256")


def validate_output_destination(path: Path) -> None:
    if not OUTPUT_NAME_RE.fullmatch(path.name):
        raise EvidenceError(
            "evidence output filename must use only letters, numbers, dot, "
            "underscore or hyphen"
        )
    for candidate in (path, output_companion(path)):
        if candidate.is_symlink():
            raise EvidenceError(f"refusing symlink output: {candidate}")
        if candidate.exists() and not candidate.is_file():
            raise EvidenceError(f"output is not a regular file: {candidate}")


def prepare_output_destination(path: Path, *, overwrite: bool) -> None:
    validate_output_destination(path)
    existing = [
        candidate
        for candidate in (path, output_companion(path))
        if candidate.exists()
    ]
    if existing and not overwrite:
        raise EvidenceError(
            "evidence output already exists; pass --overwrite to replace it"
        )


def stage_file_0600(path: Path, content: bytes) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=path.parent, prefix=path.name + ".", suffix=".tmp"
    )
    temporary = Path(temporary_name)
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "wb") as handle:
            descriptor = -1
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        return temporary
    except Exception:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
        raise
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def fsync_directory(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def backup_hardlink(path: Path) -> Path:
    for _ in range(10):
        backup = path.parent / f".{path.name}.{uuid.uuid4().hex}.backup"
        try:
            os.link(path, backup, follow_symlinks=False)
            return backup
        except FileExistsError:
            continue
    raise EvidenceError(f"cannot reserve evidence backup path for {path.name}")


def write_evidence(
    path: Path, evidence: Mapping[str, Any], *, overwrite: bool = False
) -> None:
    path = path.absolute()
    companion = output_companion(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    validate_output_destination(path)
    rendered = (
        json.dumps(evidence, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")
    digest = sha256_bytes(rendered)
    checksum = f"{digest}  {path.name}\n".encode("ascii")
    lock_path = Path(str(path) + ".lock")
    lock_flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_NOFOLLOW"):
        lock_flags |= os.O_NOFOLLOW
    try:
        lock_descriptor = os.open(lock_path, lock_flags, 0o600)
    except FileExistsError as exc:
        raise EvidenceError(
            f"evidence output is locked; inspect stale lock: {lock_path}"
        ) from exc
    staged: list[Path] = []
    backups: dict[Path, Path] = {}
    originally_present: dict[Path, bool] = {}
    try:
        os.fchmod(lock_descriptor, 0o600)
        prepare_output_destination(path, overwrite=overwrite)
        staged_evidence = stage_file_0600(path, rendered)
        staged.append(staged_evidence)
        staged_checksum = stage_file_0600(companion, checksum)
        staged.append(staged_checksum)
        destinations = ((staged_evidence, path), (staged_checksum, companion))
        if not overwrite:
            published: list[Path] = []
            try:
                for source, destination in destinations:
                    os.link(source, destination, follow_symlinks=False)
                    published.append(destination)
                fsync_directory(path.parent)
            except OSError:
                for destination in reversed(published):
                    try:
                        destination.unlink()
                    except FileNotFoundError:
                        pass
                raise
        else:
            for _, destination in destinations:
                originally_present[destination] = destination.exists()
                if originally_present[destination]:
                    backups[destination] = backup_hardlink(destination)
            try:
                for source, destination in destinations:
                    os.replace(source, destination)
                    staged.remove(source)
                fsync_directory(path.parent)
            except OSError as publish_error:
                rollback_errors = []
                for _, destination in destinations:
                    backup = backups.get(destination)
                    try:
                        if backup is not None and backup.exists():
                            if destination.exists() and os.path.samestat(
                                backup.stat(), destination.stat()
                            ):
                                backup.unlink()
                            else:
                                os.replace(backup, destination)
                            backups.pop(destination, None)
                        elif not originally_present.get(destination, False):
                            try:
                                destination.unlink()
                            except FileNotFoundError:
                                pass
                    except OSError as rollback_error:
                        rollback_errors.append(str(rollback_error))
                if rollback_errors:
                    raise EvidenceError(
                        "evidence publish failed and rollback was incomplete"
                    ) from publish_error
                raise
    finally:
        for temporary in staged:
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass
        for backup in backups.values():
            try:
                backup.unlink()
            except FileNotFoundError:
                pass
        os.close(lock_descriptor)
        try:
            lock_path.unlink()
        except FileNotFoundError:
            pass


def validate_secure_artifact(path: Path, label: str) -> os.stat_result:
    if not path.is_absolute():
        raise EvidenceError(f"{label} path must be absolute")
    if path.is_symlink():
        raise EvidenceError(f"{label} must not be a symlink")
    try:
        details = path.stat()
    except OSError as exc:
        raise EvidenceError(f"{label} is not readable") from exc
    if not stat.S_ISREG(details.st_mode):
        raise EvidenceError(f"{label} must be a regular file")
    if details.st_uid not in {0, os.geteuid()}:
        raise EvidenceError(f"{label} must be owned by root or the current user")
    if stat.S_IMODE(details.st_mode) != 0o600:
        raise EvidenceError(f"{label} permissions must be exactly 0600")
    return details


def reject_secret_fields(value: Any, path: str = "evidence") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if SECRET_KEY_RE.search(str(key)):
                raise EvidenceError(f"forbidden credential field: {path}.{key}")
            reject_secret_fields(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            reject_secret_fields(child, f"{path}[{index}]")


def parse_timestamp(value: Any) -> datetime:
    if not isinstance(value, str):
        raise EvidenceError("collectedAt must be an ISO-8601 string")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise EvidenceError("collectedAt must be ISO-8601") from exc
    if parsed.tzinfo is None:
        raise EvidenceError("collectedAt must include a timezone")
    return parsed.astimezone(timezone.utc)


def verify_evidence(
    evidence_path: Path,
    *,
    root: Path = ROOT,
    expected_commit: str | None = None,
    expected_profile: str | None = None,
    expected_database: str | None = None,
    expected_target_label: str,
    now: datetime | None = None,
    collector_path: Path = SCRIPT_PATH,
) -> dict[str, Any]:
    validate_target_label(expected_target_label)
    evidence_path = evidence_path.absolute()
    companion = output_companion(evidence_path)
    validate_secure_artifact(evidence_path, "evidence")
    validate_secure_artifact(companion, "evidence checksum")
    rendered = evidence_path.read_bytes()
    try:
        checksum_text = companion.read_text(encoding="ascii")
    except (OSError, UnicodeDecodeError) as exc:
        raise EvidenceError("evidence checksum is not readable ASCII") from exc
    match = re.fullmatch(r"([0-9a-f]{64})  ([^\r\n]+)\r?\n?", checksum_text)
    if match is None or match.group(2) != evidence_path.name:
        raise EvidenceError("invalid evidence checksum file")
    if match.group(1) != sha256_bytes(rendered):
        raise EvidenceError("evidence checksum mismatch")
    try:
        evidence = json.loads(rendered.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise EvidenceError("evidence is not valid UTF-8 JSON") from exc
    if not isinstance(evidence, dict):
        raise EvidenceError("evidence must be a JSON object")
    reject_secret_fields(evidence)

    expected_top_keys = {
        "schemaVersion",
        "kind",
        "profile",
        "candidateCommit",
        "runId",
        "collectedAt",
        "collectorSha256",
        "contractSha256",
        "manifestSha256",
        "target",
        "checks",
        "status",
    }
    if set(evidence) != expected_top_keys:
        raise EvidenceError("evidence top-level fields are incomplete or unexpected")
    if evidence.get("schemaVersion") != SCHEMA_VERSION:
        raise EvidenceError("unsupported evidence schemaVersion")
    if evidence.get("kind") != EVIDENCE_KIND:
        raise EvidenceError("unexpected evidence kind")
    profile = evidence.get("profile")
    if profile not in PROFILES:
        raise EvidenceError("unsupported evidence profile")
    if expected_profile is not None and profile != expected_profile:
        raise EvidenceError("evidence profile does not match the requested profile")
    contract = build_contract(profile, root)
    commit = expected_commit or git_commit(root)
    if evidence.get("candidateCommit") != commit or not COMMIT_RE.fullmatch(commit):
        raise EvidenceError("candidateCommit does not match the current candidate")
    if evidence.get("collectorSha256") != sha256_file(collector_path):
        raise EvidenceError("collectorSha256 does not match the current collector")
    if evidence.get("contractSha256") != contract.sha256:
        raise EvidenceError("contractSha256 does not match the current contract")
    if evidence.get("manifestSha256") != dict(contract.manifest_sha256):
        raise EvidenceError("manifestSha256 does not match the release manifests")

    try:
        parsed_uuid = uuid.UUID(str(evidence.get("runId")))
    except ValueError as exc:
        raise EvidenceError("runId must be a UUID") from exc
    if str(parsed_uuid) != evidence.get("runId"):
        raise EvidenceError("runId must use canonical UUID form")
    collected_at = parse_timestamp(evidence.get("collectedAt"))
    current_time = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    if collected_at > current_time:
        raise EvidenceError("future evidence is not accepted")
    if current_time - collected_at > PROFILE_MAX_AGE[profile]:
        raise EvidenceError("database evidence has expired")

    raw_checks = evidence.get("checks")
    if not isinstance(raw_checks, list) or len(raw_checks) != len(contract.checks):
        raise EvidenceError("evidence checks do not match the contract")
    metrics: dict[str, int] = {}
    for position, (raw, spec) in enumerate(zip(raw_checks, contract.checks)):
        if not isinstance(raw, dict) or set(raw) != {
            "id",
            "querySha256",
            "actual",
            "expected",
            "comparison",
            "status",
        }:
            raise EvidenceError(f"invalid evidence check at position {position}")
        if raw.get("id") != spec.check_id:
            raise EvidenceError("evidence check order/id mismatch")
        if raw.get("querySha256") != spec.query_sha256:
            raise EvidenceError(f"querySha256 mismatch: {spec.check_id}")
        actual = raw.get("actual")
        if isinstance(actual, bool) or not isinstance(actual, int) or actual < 0:
            raise EvidenceError(f"invalid actual value: {spec.check_id}")
        if raw.get("comparison") != spec.comparison:
            raise EvidenceError(f"comparison mismatch: {spec.check_id}")
        metrics[spec.check_id] = actual
    expected_checks, all_passed = evidence_checks(contract, metrics)
    if raw_checks != expected_checks:
        raise EvidenceError("check expectations or statuses were altered")

    target = evidence.get("target")
    if not isinstance(target, dict) or set(target) != {
        "database",
        "targetLabel",
        "serverVersion",
        "sessionReadOnly",
        "schemaFingerprintSha256",
    }:
        raise EvidenceError("target fields are incomplete or unexpected")
    database = target.get("database")
    if not isinstance(database, str):
        raise EvidenceError("target.database must be a string")
    validate_database(database)
    if expected_database is not None and database != expected_database:
        raise EvidenceError("evidence database does not match the requested database")
    target_label = target.get("targetLabel")
    if not isinstance(target_label, str):
        raise EvidenceError("target.targetLabel must be a string")
    validate_target_label(target_label)
    if target_label != expected_target_label:
        raise EvidenceError("evidence target label does not match the requested target")
    server_version = target.get("serverVersion")
    if not isinstance(server_version, str) or not SERVER_VERSION_RE.fullmatch(
        server_version
    ):
        raise EvidenceError("target.serverVersion is invalid")
    major_match = re.match(r"([0-9]+)", server_version)
    if major_match is None or int(major_match.group(1)) != metrics["MYSQL_MAJOR"]:
        raise EvidenceError("serverVersion and MYSQL_MAJOR differ")
    if target.get("sessionReadOnly") is not (metrics["SESSION_READ_ONLY"] == 1):
        raise EvidenceError("sessionReadOnly does not match the collected metric")
    identity = {
        "profile": profile,
        "database": database,
        "targetLabel": target_label,
        "serverVersion": server_version,
        "metrics": [
            {"id": check.check_id, "actual": metrics[check.check_id]}
            for check in contract.checks
        ],
    }
    if target.get("schemaFingerprintSha256") != canonical_sha256(identity):
        raise EvidenceError("schemaFingerprintSha256 mismatch")
    expected_status = "passed" if all_passed else "blocked"
    if evidence.get("status") != expected_status:
        raise EvidenceError("overall evidence status was altered")
    return evidence


def collect_command(args: argparse.Namespace) -> int:
    output = Path(args.output).absolute()
    try:
        prepare_output_destination(output, overwrite=args.overwrite)
        validate_database(args.database)
        validate_target_label(args.target_label)
        defaults_file = validate_defaults_file(args.defaults_file)
        mysql_bin = validate_mysql_bin(args.mysql_bin)
        require_clean_worktree(ROOT)
        contract = build_contract(args.profile, ROOT)
        sql = render_mysql_sql(contract)
        raw = execute_mysql(
            mysql_bin=mysql_bin,
            defaults_file=defaults_file,
            database=args.database,
            sql=sql,
            host=args.host,
            port=args.port,
            user=args.user,
            timeout_seconds=args.timeout,
        )
        server_version, metrics = parse_mysql_output(raw, contract)
        evidence = build_evidence(
            contract=contract,
            database=args.database,
            target_label=args.target_label,
            server_version=server_version,
            metrics=metrics,
            candidate_commit=git_commit(ROOT),
        )
        write_evidence(output, evidence, overwrite=args.overwrite)
    except (EvidenceError, OSError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2
    print(f"[INFO] Stage 0 database evidence: {output}")
    if evidence["status"] == "blocked":
        print("[BLOCK] one or more Stage 0 database checks failed", file=sys.stderr)
        return 1
    print("[PASS] Stage 0 database evidence is complete and passed")
    return 0


def verify_command(args: argparse.Namespace) -> int:
    try:
        validate_database(args.database)
        validate_target_label(args.target_label)
        evidence = verify_evidence(
            Path(args.evidence),
            root=ROOT,
            expected_profile=args.profile,
            expected_database=args.database,
            expected_target_label=args.target_label,
        )
    except (EvidenceError, OSError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2
    if evidence["status"] == "blocked":
        print(
            "[BLOCK] evidence is structurally valid and integrity-checked, "
            "but contains failed checks",
            file=sys.stderr,
        )
        return 1
    print(
        json.dumps(
            {
                "profile": evidence["profile"],
                "candidateCommit": evidence["candidateCommit"],
                "database": evidence["target"]["database"],
                "targetLabel": evidence["target"]["targetLabel"],
                "collectedAt": evidence["collectedAt"],
                "checkCount": len(evidence["checks"]),
                "status": evidence["status"],
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    print("[PASS] Stage 0 database evidence passed offline verification")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    collect = subparsers.add_parser("collect", help="collect read-only MySQL evidence")
    collect.add_argument("--profile", required=True, choices=PROFILES)
    collect.add_argument("--defaults-file", required=True)
    collect.add_argument("--database", required=True)
    collect.add_argument("--target-label", required=True)
    collect.add_argument("--output", required=True)
    collect.add_argument("--mysql-bin", default="mysql")
    collect.add_argument("--host", required=True)
    collect.add_argument("--port", required=True, type=int)
    collect.add_argument("--user", required=True)
    collect.add_argument("--timeout", type=int, default=120)
    collect.add_argument(
        "--overwrite",
        action="store_true",
        help="explicitly replace an existing evidence/checksum pair after a successful run",
    )
    collect.set_defaults(func=collect_command)

    verify = subparsers.add_parser("verify", help="verify evidence without MySQL")
    verify.add_argument("--evidence", required=True)
    verify.add_argument("--profile", required=True, choices=PROFILES)
    verify.add_argument("--database", required=True)
    verify.add_argument("--target-label", required=True)
    verify.set_defaults(func=verify_command)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if getattr(args, "timeout", 1) < 1 or getattr(args, "timeout", 1) > 3600:
        parser.error("--timeout must be between 1 and 3600 seconds")
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
