#!/usr/bin/env python3
"""Fail-closed source contract for contract-signing Release A."""

from __future__ import annotations

import fnmatch
import hashlib
import json
import re
import subprocess
import unittest
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "scripts/contract-signing-release-20260716.json"
MIGRATION_LIST_PATH = ROOT / "scripts/contract-signing-migrations-20260716.list"
FILE_LIST_PATH = ROOT / "scripts/contract-signing-release-files-20260716.list"
SCOPE_PATH = ROOT / "docs/releases/20260716-contract-signing-release-scope.md"
SCOPE_BEGIN = "<!-- CONTRACT_SIGNING_SCOPE_JSON_BEGIN -->"
SCOPE_END = "<!-- CONTRACT_SIGNING_SCOPE_JSON_END -->"

EXPECTED_RELEASE_ID = "contract-signing-release-a-20260716"
EXPECTED_MIGRATIONS = (
    "erp_oa_sign_menu_permission_repair_20260716.sql",
    "erp_oa_sign_package_lifecycle_20260716.sql",
    "erp_oa_sign_document_policy_snapshot_20260716.sql",
    "erp_system_user_push_delivery_20260717.sql",
)
EXPECTED_PHASES = (
    "01-menu-permission-repair-before-lifecycle",
    "02-lifecycle-deadline-after-menu",
    "03-document-policy-snapshot-after-lifecycle",
    "04-mobile-push-ledger-before-backend",
)
EXPECTED_EXECUTION_PHASES = (
    "01-menu-permission-repair",
    "02-lifecycle-deadline",
    "03-document-policy-snapshot",
    "04-mobile-push-ledger",
    "05-backend-deploy",
    "06-dedicated-non-admin-hr-permission-uat",
    "07-frontend-deploy",
)
EXPECTED_FEATURE_DEFAULTS = {
    "oa.sign.expiry.enabled": False,
    "oa.sign.reminder.enabled": False,
    "oa.sign.emergency-create.enabled": False,
    "VUE_APP_SIGN_EMERGENCY_CREATE_ENABLED": False,
    "plan.autoSendCondition.enabled": False,
}
EXPECTED_EXACT_EXTENSIONS = {
    "docker/mysql/bootstrap-files.list",
    "docker/mysql/db/erp_system_user_push_delivery_20260717.sql",
    "scripts/qa/contract_signing_uat_common.py",
    "scripts/verify-docker-mysql-bootstrap.sh",
    "sql/erp_system_user_push_delivery_20260717.sql",
}
REQUIRED_RELEASE_FILES = {
    "docs/releases/20260716-contract-signing-release-manifest.md",
    "docs/releases/20260716-contract-signing-release-scope.md",
    "docs/releases/contract-signing-release-checklist.md",
    "docs/releases/contract-signing-uat-runbook.md",
    "docs/runbooks/contract-signing-operations.md",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignTaskController.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaSignReminderCandidate.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignNotificationOutboxMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaSignPackageMapper.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationOutboxService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageExpiryScheduler.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageLifecycleService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlacementPolicyService.java",
    "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignReminderScheduler.java",
    "erp-modules/erp-oa/src/main/resources/bootstrap.yml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignNotificationOutboxMapper.xml",
    "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/mapper/OaSignTaskMigrationTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationDispatcherTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignNotificationOutboxServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageExpirySchedulerTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPackageLifecycleServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignPlacementPolicyServiceTest.java",
    "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaSignReminderSchedulerTest.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysUserPushDelivery.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserPushDeliveryMapper.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserNotificationServiceImpl.java",
    "erp-modules/erp-system/src/main/java/com/erp/system/service/push/PushDeliveryClient.java",
    "erp-modules/erp-system/src/main/resources/mapper/system/SysUserPushDeliveryMapper.xml",
    "erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserNotificationServiceImplTest.java",
    "erp-ui/src/api/oa/signPackage.js",
    "erp-ui/src/api/oa/signTask.js",
    "erp-ui/src/views/mobile/signPackage/index.vue",
    "erp-ui/src/views/oa/signTask/SignTaskDetailDrawer.vue",
    "erp-ui/test/signDeadlineUx.test.js",
    "erp-ui/test/signPackageRefusal.test.js",
    "erp-ui/test/signTaskCenter.test.js",
    "erp-ui/test/signTaskExceptionResolution.test.js",
    "scripts/contract-signing-migrations-20260716.list",
    "scripts/contract-signing-release-20260716.json",
    "scripts/contract-signing-release-files-20260716.list",
    "scripts/qa/contract-signing-uat.example.json",
    "scripts/qa/contract_signing_uat_common.py",
    "scripts/qa/prepare_contract_signing_uat.py",
    "scripts/qa/run_contract_signing_api_uat.py",
    "scripts/qa/verify_contract_signing_evidence.py",
    "scripts/test_contract_signing_release_contract.py",
    "scripts/test_contract_signing_uat.py",
    "scripts/verify-contract-signing-release.sh",
}


class ContractError(ValueError):
    """Raised when a Release A source contract is unsafe or incomplete."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_ordered_list(path: Path) -> list[str]:
    return [
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def load_scope(path: Path = SCOPE_PATH) -> dict:
    source = path.read_text(encoding="utf-8")
    require(SCOPE_BEGIN in source and SCOPE_END in source, "scope JSON markers are missing")
    payload = source.split(SCOPE_BEGIN, 1)[1].split(SCOPE_END, 1)[0]
    return json.loads(payload)


def extract_segment(source: str, start: str, end: str) -> str:
    require(start in source, f"legacy-role boundary marker is missing: {start}")
    require(end in source, f"legacy-role boundary marker is missing: {end}")
    segment = source.split(start, 1)[1].split(end, 1)[0]
    require(segment.strip(), f"legacy-role boundary segment is empty: {start}")
    return segment


def validate_manifest_payload(manifest: dict, root: Path = ROOT) -> None:
    require(manifest.get("schemaVersion") == 1, "unexpected manifest schemaVersion")
    require(manifest.get("releaseId") == EXPECTED_RELEASE_ID, "unexpected releaseId")
    require(manifest.get("status") in {"development", "ready"}, "unsafe release status")
    require(manifest.get("executionPolicy") == "manual-phased", "migrations must be manual-phased")
    require(manifest.get("sourceDirectory") == "sql", "unexpected migration source directory")
    require(manifest.get("deployDirectory") == "docker/mysql/db", "unexpected migration mirror directory")
    require(
        manifest.get("migrationList") == "scripts/contract-signing-migrations-20260716.list",
        "unexpected migration list",
    )

    migrations = manifest.get("migrations")
    require(isinstance(migrations, list), "manifest migrations must be an array")
    require(manifest.get("migrationCount") == len(EXPECTED_MIGRATIONS), "migrationCount drift")
    require(
        tuple(item.get("file") for item in migrations) == EXPECTED_MIGRATIONS,
        "migration order/content drift",
    )
    require(
        tuple(item.get("phase") for item in migrations) == EXPECTED_PHASES,
        "migration phase order drift",
    )
    require(tuple(manifest.get("executionPhases", ())) == EXPECTED_EXECUTION_PHASES, "deployment phase drift")

    for item in migrations:
        name = item["file"]
        expected_hash = item.get("sha256")
        require(bool(re.fullmatch(r"[0-9a-f]{64}", str(expected_hash))), f"invalid hash: {name}")
        source = root / "sql" / name
        mirror = root / "docker/mysql/db" / name
        require(source.is_file() and mirror.is_file(), f"migration pair is incomplete: {name}")
        require(source.read_bytes() == mirror.read_bytes(), f"migration pair differs byte-for-byte: {name}")
        require(sha256_file(source) == expected_hash, f"migration hash drift: {name}")
        tables = item.get("requiresTables")
        columns = item.get("requiresColumns")
        require(isinstance(tables, list) and tables and len(tables) == len(set(tables)), f"invalid table preconditions: {name}")
        require(isinstance(columns, dict) and columns, f"invalid column preconditions: {name}")
        require(set(columns).issubset(set(tables)), f"column precondition table is undeclared: {name}")
        for table, names in columns.items():
            require(isinstance(names, list) and names and len(names) == len(set(names)), f"invalid columns: {name}:{table}")
        indexes = item.get("requiresIndexes", [])
        require(isinstance(indexes, list), f"invalid index preconditions: {name}")
        for index in indexes:
            require(
                isinstance(index, dict)
                and set(index) == {"table", "name", "columns", "unique"}
                and index["table"] in tables
                and isinstance(index["name"], str)
                and index["name"]
                and isinstance(index["columns"], list)
                and index["columns"]
                and isinstance(index["unique"], bool),
                f"invalid index precondition entry: {name}",
            )

    menu_migration = migrations[0]
    require(
        {
            "oa_sign_notification_outbox",
            "oa_sign_task",
            "oa_sign_task_hr_reassignment",
            "sys_config",
        }.issubset(menu_migration["requiresTables"]),
        "menu/HR reassignment hidden table dependencies are not declared",
    )
    require(
        {
            "outbox_id",
            "channel",
            "recipient_user_id",
            "business_key",
            "payload_json",
            "status",
            "retry_count",
            "next_retry_time",
            "last_result",
            "last_error",
            "version",
            "created_time",
            "updated_time",
        }.issubset(menu_migration["requiresColumns"]["oa_sign_notification_outbox"]),
        "menu/HR reassignment outbox column prerequisites are incomplete",
    )
    require(
        menu_migration.get("requiresIndexes") == [
            {
                "table": "oa_sign_notification_outbox",
                "name": "uk_oa_sign_notification_business",
                "columns": ["channel", "recipient_user_id", "business_key"],
                "unique": True,
            }
        ],
        "menu/HR reassignment outbox unique-index prerequisite drift",
    )
    require(
        menu_migration.get("requiresColumnDefinitions")
        == [
            {
                "table": "oa_sign_notification_outbox",
                "name": "payload_json",
                "dataType": "json",
                "nullable": False,
            }
        ],
        "menu/HR reassignment outbox JSON type prerequisite drift",
    )
    require(
        menu_migration.get("requiresDataAssertions")
        == ["oa_sign_notification_outbox.payload_json must be valid JSON"],
        "menu/HR reassignment JSON data prerequisite is missing",
    )

    lifecycle_columns = migrations[1]["requiresColumns"]
    for table in ("oa_sign_package", "oa_sign_task"):
        require(
            {"employee_id", "shop_dept_id", "plan_version_id"}.issubset(lifecycle_columns[table]),
            f"lifecycle readiness columns are incomplete: {table}",
        )

    require(tuple(read_ordered_list(MIGRATION_LIST_PATH)) == EXPECTED_MIGRATIONS, "ordered migration list drift")
    require(manifest.get("featureDefaults") == EXPECTED_FEATURE_DEFAULTS, "feature defaults must all remain false")
    role_boundary = manifest.get("legacyRoleBoundary", {})
    require(role_boundary.get("preserveMenuIds") == list(range(4520, 4527)), "legacy package IDs must be preserved")
    require(role_boundary.get("taskCenterMenuId") == 9650, "task-center menu ID drift")
    require(role_boundary.get("taskCenterGrantSourceMenuId") == 4600, "task-center source ID drift")
    require(role_boundary.get("packageOnlyRolesMayReceiveTaskCenter") is False, "package-only roles must not receive 9650")
    mysql57 = manifest.get("externalGates", {}).get("nativeMySql57", {})
    require(mysql57.get("requiredWhenProductionIs57") is True, "native MySQL 5.7 gate must be required")
    require(
        mysql57.get("status") in {"external-pending", "passed", "not-applicable-version-verified"},
        "invalid native MySQL 5.7 gate status",
    )
    require(mysql57.get("dockerOrMySql8CannotSubstitute") is True, "MySQL 8/Docker must not substitute for MySQL 5.7")
    signed_uat = manifest.get("externalGates", {}).get("signedUat", {})
    require(signed_uat.get("required") is True, "signed UAT gate must be required")
    require(signed_uat.get("status") in {"external-pending", "passed"}, "invalid signed UAT gate status")
    require(
        signed_uat.get("requiresSeparateLegalAndTestApprovers") is True,
        "legal and test approval cannot be collapsed into one approver",
    )
    if manifest.get("status") == "ready":
        require(mysql57.get("status") in {"passed", "not-applicable-version-verified"}, "ready release lacks MySQL version gate")
        require(signed_uat.get("status") == "passed", "ready release lacks signed UAT")
    else:
        require(mysql57.get("status") == "external-pending", "development manifest must expose pending MySQL gate")
        require(signed_uat.get("status") == "external-pending", "development manifest must expose pending UAT")
    rollback = manifest.get("rollbackPolicy", {})
    require(rollback.get("deleteBusinessEvidence") is False, "rollback must preserve business evidence")
    require(rollback.get("dropColumnsOrTablesWithBusinessData") is False, "rollback must not drop evidence schema")
    require(rollback.get("preserveLegacyPackageMenus") is True, "rollback must preserve 4520-4526")


def validate_release_file_list(root: Path = ROOT) -> None:
    entries = read_ordered_list(root / "scripts/contract-signing-release-files-20260716.list")
    require(entries, "release file list is empty")
    require(len(entries) == len(set(entries)), "release file list contains duplicates")
    require(entries == sorted(entries), "release file list must stay bytewise sorted")
    for entry in entries:
        path = PurePosixPath(entry)
        require(not path.is_absolute() and ".." not in path.parts and "\\" not in entry, f"unsafe release path: {entry}")
        source = root / entry
        require(source.is_file(), f"release file is missing: {entry}")
        require(not source.is_symlink(), f"release file must not be a symlink: {entry}")

    scope = load_scope(root / "docs/releases/20260716-contract-signing-release-scope.md")
    exact_extensions = scope.get("releaseAExactExtensions")
    require(set(exact_extensions or ()) == EXPECTED_EXACT_EXTENSIONS, "scope exact extensions drift")
    allowed = set(scope.get("releaseAAllowlist", ())) | EXPECTED_EXACT_EXTENSIONS
    require(EXPECTED_EXACT_EXTENSIONS.issubset(entries), "release list misses an exact scope extension")
    outside = sorted(set(entries) - allowed)
    require(not outside, "release files are outside the scope: " + ", ".join(outside))
    denied = []
    for entry in entries:
        for rule in scope.get("denylist", ()):
            if fnmatch.fnmatchcase(entry, rule.get("pattern", "")):
                denied.append(f"{entry} ({rule.get('category')})")
    require(not denied, "release files hit denylist: " + ", ".join(denied))
    require(REQUIRED_RELEASE_FILES.issubset(entries), "release file list misses critical sources")
    for migration in EXPECTED_MIGRATIONS:
        require(f"sql/{migration}" in entries, f"source migration missing from release files: {migration}")
        require(f"docker/mysql/db/{migration}" in entries, f"mirror migration missing from release files: {migration}")

    if (root / ".git").exists():
        status = subprocess.run(
            ["git", "-C", str(root), "status", "--porcelain=v1", "--untracked-files=all"],
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        ).stdout
        dirty_allowed = set()
        for line in status.splitlines():
            path = line[3:]
            if " -> " in path:
                path = path.split(" -> ", 1)[1]
            if path in allowed and (root / path).is_file():
                dirty_allowed.add(path)
        require(
            dirty_allowed.issubset(entries),
            "dirty allowlisted paths are absent from release files: "
            + ", ".join(sorted(dirty_allowed - set(entries))),
        )


def validate_legacy_role_boundary(root: Path = ROOT, source: str | None = None) -> None:
    if source is None:
        source = (root / "sql/erp_oa_sign_menu_permission_repair_20260716.sql").read_text(encoding="utf-8")

    package = extract_segment(
        source,
        "-- Keep the legacy sign-package C route independently reachable.",
        "-- Migrate the task-center entry only from an unmistakable legacy task entry.",
    )
    require("package_root.menu_id = 4520" in package, "legacy package reachability must match exact 4520")
    require("oa_root.menu_id = 3000" in package, "package-only role may receive only OA parent 3000")
    require("9650" not in package, "package-only reachability block must never grant 9650")
    for identity in (
        "package_root.parent_id = 3000",
        "package_root.path = BINARY 'sign-package'",
        "package_root.component = BINARY 'oa/signPackage/index'",
        "package_root.route_name = BINARY 'OaSignPackage'",
        "package_root.menu_type = 'C'",
        "package_root.perms = BINARY 'oa:signPackage:list'",
    ):
        require(identity in package, f"legacy package route identity is incomplete: {identity}")

    grant = extract_segment(
        source,
        "-- Migrate the task-center entry only from an unmistakable legacy task entry.",
        "-- Remove links only from exact legacy signing identities.",
    )
    require("SELECT role_menu.role_id, 9650" in grant, "task-center grant must target 9650")
    require("old_entry.menu_id = 4600" in grant, "9650 must originate from exact legacy 4600")
    require("4520" not in grant and "4521" not in grant, "package ownership must not grant 9650")
    for identity in (
        "old_entry.parent_id = 3000",
        "old_entry.path = BINARY 'sign-task'",
        "old_entry.component = BINARY 'oa/signTask/index'",
        "old_entry.route_name = BINARY 'OaSignTask'",
        "old_entry.menu_type = 'C'",
        "old_entry.perms = BINARY 'oa:signTask:list'",
    ):
        require(identity in grant, f"legacy task entry identity is incomplete: {identity}")

    deletion = extract_segment(
        source,
        "-- Remove links only from exact legacy signing identities.",
        "-- Disable only the legacy task root whose complete route identity is unmistakably",
    )
    disable = extract_segment(
        source,
        "-- Disable only the legacy task root whose complete route identity is unmistakably",
        "-- The one-time confirmation action was retired rather than remapped.",
    )
    for menu_id in range(4520, 4527):
        require(str(menu_id) not in deletion, f"legacy package role mapping {menu_id} must not be deleted")
        require(str(menu_id) not in disable, f"legacy package menu {menu_id} must not be disabled")

    for statement in re.findall(r"(?:DELETE|UPDATE)\b.*?;", source, flags=re.IGNORECASE | re.DOTALL):
        if re.search(r"\b(?:sys_menu|sys_role_menu)\b", statement, flags=re.IGNORECASE):
            for menu_id in range(4520, 4527):
                require(
                    not re.search(rf"\b{menu_id}\b", statement),
                    f"destructive statement touches preserved legacy package menu {menu_id}",
                )
            for lower, upper in re.findall(
                r"menu_id\s+BETWEEN\s+(\d+)\s+AND\s+(\d+)",
                statement,
                flags=re.IGNORECASE,
            ):
                require(
                    int(upper) < 4520 or int(lower) > 4526,
                    "destructive menu range overlaps preserved legacy package menus",
                )


def validate_feature_defaults(root: Path = ROOT) -> None:
    bootstrap = (root / "erp-modules/erp-oa/src/main/resources/bootstrap.yml").read_text(encoding="utf-8")
    for marker in (
        "enabled: ${OA_SIGN_EXPIRY_ENABLED:false}",
        "enabled: ${OA_SIGN_REMINDER_ENABLED:false}",
        "enabled: ${OA_SIGN_EMERGENCY_CREATE_ENABLED:false}",
    ):
        require(marker in bootstrap, f"missing fail-closed backend default: {marker}")
    for path, prefix in (
        ("erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPackageExpiryScheduler.java", "oa.sign.expiry"),
        ("erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignReminderScheduler.java", "oa.sign.reminder"),
    ):
        scheduler = (root / path).read_text(encoding="utf-8")
        require(f'prefix = "{prefix}"' in scheduler, f"scheduler property prefix drift: {prefix}")
        require('havingValue = "true"' in scheduler, f"scheduler must require explicit true: {prefix}")
        require("matchIfMissing = false" in scheduler, f"scheduler must fail closed: {prefix}")
    controller = (root / "erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaSignPackageController.java").read_text(encoding="utf-8")
    require('@Value("${oa.sign.emergency-create.enabled:false}")' in controller, "emergency create backend default drift")
    frontend = (root / "erp-ui/src/views/oa/signPackage/index.vue").read_text(encoding="utf-8")
    require('process.env.VUE_APP_SIGN_EMERGENCY_CREATE_ENABLED || "false"' in frontend, "emergency create frontend default drift")
    plan_service = (root / "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignPlanVersionServiceImpl.java").read_text(encoding="utf-8")
    require('"{\\"enabled\\":false}"' in plan_service, "auto-send condition must default false")


def validate_critical_sources(root: Path = ROOT) -> None:
    package_api = (root / "erp-ui/src/api/oa/signPackage.js").read_text(encoding="utf-8")
    task_api = (root / "erp-ui/src/api/oa/signTask.js").read_text(encoding="utf-8")
    for marker in ("/refuse", "/final-confirm", "/final-file"):
        require(marker in package_api, f"mobile signing API is missing: {marker}")
    for marker in ("/notification/retry", "/resolve"):
        require(marker in task_api, f"task exception API is missing: {marker}")

    menu_sql = (root / "sql/erp_oa_sign_menu_permission_repair_20260716.sql").read_text(encoding="utf-8")
    for marker in (
        "Signing HR reassignment prerequisite tables are missing",
        "Signing HR reassignment prerequisite columns are missing",
        "Signing notification outbox schema is incompatible",
        "uk_oa_sign_notification_business",
        "channel,recipient_user_id,business_key",
    ):
        require(marker in menu_sql, f"menu/HR reassignment fail-closed precondition is missing: {marker}")

    lifecycle_sql = (root / "sql/erp_oa_sign_package_lifecycle_20260716.sql").read_text(encoding="utf-8")
    for marker in ("terminal_reason_code", "resolution_status", "reissue_of_task_id", "reissued_to_task_id"):
        require(marker in lifecycle_sql, f"lifecycle migration misses {marker}")
    policy_sql = (root / "sql/erp_oa_sign_document_policy_snapshot_20260716.sql").read_text(encoding="utf-8")
    for marker in ("signature_position_json", "company_seal_position_json", "company_seal_required", "document_policy_mode"):
        require(marker in policy_sql, f"document-policy migration misses {marker}")

    push_sql = (root / "sql/erp_system_user_push_delivery_20260717.sql").read_text(encoding="utf-8")
    for marker in (
        "CREATE TABLE IF NOT EXISTS sys_user_push_delivery",
        "UNIQUE KEY uk_sys_user_push_delivery_business",
        "(user_id, channel, business_key_hash)",
        "payload_hash char(64) NOT NULL",
    ):
        require(marker in push_sql, f"push delivery ledger contract is missing: {marker}")
    push_service = (root / "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserNotificationServiceImpl.java").read_text(encoding="utf-8")
    for marker in ("insertOrLoadPushDelivery", "validatePushDeliveryIdentity", "businessKeyHash", "payloadHash"):
        require(marker in push_service, f"durable push delivery source is missing: {marker}")

    outbox_service = (root / "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaSignNotificationOutboxService.java").read_text(encoding="utf-8")
    for left, right in (
        ("task.getPackageId()", "signPackage.getPackageId()"),
        ("task.getTaskId()", "signPackage.getTaskId()"),
        ("task.getEmployeeId()", "signPackage.getEmployeeId()"),
        ("task.getShopDeptId()", "signPackage.getShopDeptId()"),
        ("task.getPlanVersionId()", "signPackage.getPlanVersionId()"),
    ):
        alternatives = (
            rf"!\s*Objects\.equals\(\s*{re.escape(left)}\s*,\s*{re.escape(right)}\s*\)",
            rf"!\s*Objects\.equals\(\s*{re.escape(right)}\s*,\s*{re.escape(left)}\s*\)",
        )
        require(
            any(re.search(pattern, outbox_service) for pattern in alternatives),
            f"task/package notification linkage is incomplete: {left}<=>{right}",
        )


def validate_release_documents(root: Path = ROOT) -> None:
    manifest_doc = (root / "docs/releases/20260716-contract-signing-release-manifest.md").read_text(encoding="utf-8")
    runbook = (root / "docs/runbooks/contract-signing-operations.md").read_text(encoding="utf-8")
    checklist = (root / "docs/releases/contract-signing-release-checklist.md").read_text(encoding="utf-8")
    combined = "\n".join((manifest_doc, runbook, checklist))
    for marker in ("4520–4526", "package-only", "9650", "4600", "3000"):
        require(marker in combined, f"release docs miss legacy-role boundary: {marker}")
    for marker in ("SHA-256", "MySQL 5.7", "外部", "Docker", "3 个", "不删除", "DROP"):
        require(marker in combined, f"release docs miss operational guard: {marker}")
    require("菜单/权限修复 → 生命周期/截止 → 文件策略快照" in runbook, "documented release order drift")
    require("管理员成功不能" in runbook, "dedicated non-admin HR gate is missing")


def validate_release(root: Path = ROOT) -> dict:
    manifest = json.loads((root / "scripts/contract-signing-release-20260716.json").read_text(encoding="utf-8"))
    validate_manifest_payload(manifest, root)
    validate_release_file_list(root)
    validate_legacy_role_boundary(root)
    validate_feature_defaults(root)
    validate_critical_sources(root)
    validate_release_documents(root)
    return {
        "releaseId": manifest["releaseId"],
        "migrationCount": len(manifest["migrations"]),
        "fileCount": len(read_ordered_list(root / "scripts/contract-signing-release-files-20260716.list")),
        "nativeMySql57": manifest["externalGates"]["nativeMySql57"]["status"],
    }


class ContractSigningReleaseContractTest(unittest.TestCase):
    def test_current_release_contract_passes(self) -> None:
        result = validate_release(ROOT)
        self.assertEqual(EXPECTED_RELEASE_ID, result["releaseId"])
        self.assertEqual(4, result["migrationCount"])
        self.assertEqual("external-pending", result["nativeMySql57"])

    def test_manifest_order_is_menu_lifecycle_policy_push_then_backend_frontend(self) -> None:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        self.assertEqual(EXPECTED_MIGRATIONS, tuple(item["file"] for item in manifest["migrations"]))
        self.assertEqual(EXPECTED_EXECUTION_PHASES, tuple(manifest["executionPhases"]))

    def test_each_migration_has_a_byte_identical_hash_pinned_pair(self) -> None:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        for migration in manifest["migrations"]:
            source = ROOT / "sql" / migration["file"]
            mirror = ROOT / "docker/mysql/db" / migration["file"]
            self.assertEqual(source.read_bytes(), mirror.read_bytes())
            self.assertEqual(migration["sha256"], sha256_file(source))

    def test_package_only_role_never_receives_task_center_and_legacy_package_is_preserved(self) -> None:
        validate_legacy_role_boundary(ROOT)

    def test_package_grant_mutation_to_9650_fails_closed(self) -> None:
        source = (ROOT / "sql/erp_oa_sign_menu_permission_repair_20260716.sql").read_text(encoding="utf-8")
        mutated = source.replace("oa_root.menu_id = 3000", "oa_root.menu_id = 9650", 1)
        with self.assertRaisesRegex(ContractError, "3000|9650"):
            validate_legacy_role_boundary(ROOT, mutated)

    def test_task_center_source_mutation_from_4600_to_4520_fails_closed(self) -> None:
        source = (ROOT / "sql/erp_oa_sign_menu_permission_repair_20260716.sql").read_text(encoding="utf-8")
        grant = extract_segment(
            source,
            "-- Migrate the task-center entry only from an unmistakable legacy task entry.",
            "-- Remove links only from exact legacy signing identities.",
        )
        mutated_grant = grant.replace("old_entry.menu_id = 4600", "old_entry.menu_id = 4520", 1)
        mutated = source.replace(grant, mutated_grant, 1)
        with self.assertRaisesRegex(ContractError, "4600|package"):
            validate_legacy_role_boundary(ROOT, mutated)

    def test_disabling_legacy_package_4520_fails_closed(self) -> None:
        source = (ROOT / "sql/erp_oa_sign_menu_permission_repair_20260716.sql").read_text(encoding="utf-8")
        disable = extract_segment(
            source,
            "-- Disable only the legacy task root whose complete route identity is unmistakably",
            "-- The one-time confirmation action was retired rather than remapped.",
        )
        mutated = source.replace(disable, disable.replace("old_menu.menu_id = 4600", "old_menu.menu_id = 4520", 1), 1)
        with self.assertRaisesRegex(ContractError, "4520|legacy package"):
            validate_legacy_role_boundary(ROOT, mutated)

    def test_deleting_legacy_package_range_fails_closed(self) -> None:
        source = (ROOT / "sql/erp_oa_sign_menu_permission_repair_20260716.sql").read_text(encoding="utf-8")
        deletion = extract_segment(
            source,
            "-- Remove links only from exact legacy signing identities.",
            "-- Disable only the legacy task root whose complete route identity is unmistakably",
        )
        mutated = source.replace(deletion, deletion.replace("old_menu.menu_id = 4600", "old_menu.menu_id BETWEEN 4520 AND 4526", 1), 1)
        with self.assertRaisesRegex(ContractError, "4520|legacy package|overlaps"):
            validate_legacy_role_boundary(ROOT, mutated)

    def test_every_automatic_switch_is_fail_closed(self) -> None:
        validate_feature_defaults(ROOT)
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        self.assertTrue(all(value is False for value in manifest["featureDefaults"].values()))

    def test_rollback_preserves_business_evidence_and_mysql57_stays_external(self) -> None:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        self.assertFalse(manifest["rollbackPolicy"]["deleteBusinessEvidence"])
        self.assertFalse(manifest["rollbackPolicy"]["dropColumnsOrTablesWithBusinessData"])
        self.assertIn(
            manifest["externalGates"]["nativeMySql57"]["status"],
            {"external-pending", "passed", "not-applicable-version-verified"},
        )
        self.assertIn(manifest["externalGates"]["signedUat"]["status"], {"external-pending", "passed"})


if __name__ == "__main__":
    unittest.main()
