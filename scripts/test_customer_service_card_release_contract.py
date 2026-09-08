#!/usr/bin/env python3
"""Fail-closed source tests for the customer-service-card code-only release."""

from __future__ import annotations

import json
import os
import re
import subprocess
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path, PurePosixPath


ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "scripts/customer-service-card-release-20260719.json"
FILE_LIST_PATH = ROOT / "scripts/customer-service-card-release-files-20260719.list"
VERIFY_PATH = ROOT / "scripts/verify-customer-service-card-release.sh"
PREFLIGHT_PATH = ROOT / "scripts/remote_customer_service_card_preflight_20260719.sh"
POSTCHECK_PATH = ROOT / "scripts/remote_customer_service_card_postcheck_20260719.sh"
DOC_PATH = ROOT / "docs/releases/20260719-customer-service-card-release.md"
CONFIG_UI_PATH = ROOT / "erp-ui/src/views/system/config/index.vue"
CONFIG_SERVICE_PATH = ROOT / "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysConfigServiceImpl.java"
CONFIG_DOMAIN_PATH = ROOT / "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysConfig.java"

EXPECTED_FEATURE_FILES = {
    "erp-common/erp-common-security/src/main/java/com/erp/common/security/annotation/IdempotentSubmit.java",
    "erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/IdempotentSubmitAspect.java",
    "erp-common/erp-common-security/src/test/java/com/erp/common/security/aspect/IdempotentSubmitAspectTest.java",
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvCustomerController.java",
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/vo/InvCustomerServiceCardVo.java",
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/BusinessFeatureGate.java",
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/IInvCustomerServiceCardService.java",
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceCardServiceImpl.java",
    "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceImpl.java",
    "erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvCustomerMapper.xml",
    "erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvCustomerServiceCardMapper.xml",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InvCustomerServiceCardMapperBindingTest.java",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/BusinessFeatureGateTest.java",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvCustomerServiceCardServiceImplTest.java",
    "erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvCustomerServiceImplTest.java",
    "erp-ui/src/api/inventory/customer.js",
    "erp-ui/src/views/inventory/customer/index.vue",
    "erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue",
    "erp-ui/src/views/mobile/customer/index.vue",
    "erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue",
    "erp-ui/src/views/mobile/feature/mobileCustomerServiceRecord.js",
    "erp-ui/src/views/mobile/feature/mobileFormPayloads.js",
    "erp-ui/test/customerServiceCardFlow.test.js",
    "erp-ui/test/customerServiceCardMobileFlow.test.js",
    "erp-ui/test/mobileQuickCustomer.test.js",
}
EXPECTED_CONTRACT_FILES = {
    "docs/releases/20260719-customer-service-card-release.md",
    "scripts/customer-service-card-release-20260719.json",
    "scripts/customer-service-card-release-files-20260719.list",
    "scripts/remote_customer_service_card_postcheck_20260719.sh",
    "scripts/remote_customer_service_card_preflight_20260719.sh",
    "scripts/test_customer_service_card_release_contract.py",
    "scripts/verify-customer-service-card-release.sh",
}
EXPECTED_TABLES = {
    "inv_customer_service_profile",
    "inv_customer_service_record",
    "inv_customer_service_change_log",
}
EXPECTED_PERMISSIONS = {
    "inv:customer:option",
    "inv:customerCard:list",
    "inv:customerCard:query",
    "inv:customerCard:add",
    "inv:customerCard:edit",
    "inv:customerCard:record:add",
    "inv:customerCard:archive",
    "inv:customerCard:audit",
}


def read_list() -> list[str]:
    return [
        line.strip()
        for line in FILE_LIST_PATH.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]


def sql_heredocs(source: str) -> str:
    return "\n".join(re.findall(r"<<'?SQL'?\n(.*?)\nSQL", source, flags=re.DOTALL))


def java_method_body(source: str, signature: str) -> str:
    match = re.search(signature, source)
    if not match:
        raise AssertionError(f"Java method signature is missing: {signature}")
    opening = source.find("{", match.end())
    if opening < 0:
        raise AssertionError(f"Java method body is missing: {signature}")
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"Unbalanced Java method body: {signature}")


def assert_ordered(test: unittest.TestCase, body: str, markers: tuple[str, ...]) -> None:
    cursor = -1
    for marker in markers:
        position = body.find(marker, cursor + 1)
        test.assertGreaterEqual(position, 0, marker)
        cursor = position


def assert_python_heredocs_compile(source: str, name: str) -> None:
    for index, block in enumerate(
        re.findall(r"<<'?PY'?\n(.*?)\nPY", source, flags=re.DOTALL),
        start=1,
    ):
        compile(block, f"{name}:PY:{index}", "exec")


def strict_id_validation_block(source: str) -> str:
    match = re.search(
        r"# BEGIN STRICT_POSITIVE_LONG_ID_VALIDATION\n(.*?)"
        r"# END STRICT_POSITIVE_LONG_ID_VALIDATION",
        source,
        flags=re.DOTALL,
    )
    if not match:
        raise AssertionError("strict positive-Long ID validation block is missing")
    return match.group(1).strip()


class CustomerServiceCardReleaseContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        cls.files = read_list()
        cls.preflight = PREFLIGHT_PATH.read_text(encoding="utf-8")
        cls.postcheck = POSTCHECK_PATH.read_text(encoding="utf-8")
        cls.doc = DOC_PATH.read_text(encoding="utf-8")
        cls.config_ui = CONFIG_UI_PATH.read_text(encoding="utf-8")
        cls.config_service = CONFIG_SERVICE_PATH.read_text(encoding="utf-8")
        cls.config_domain = CONFIG_DOMAIN_PATH.read_text(encoding="utf-8")

    def test_manifest_is_zero_migration_zero_flag_code_only(self) -> None:
        self.assertEqual("customer-service-card-code-only-20260719", self.manifest["releaseId"])
        self.assertEqual("code-only", self.manifest["deploymentMode"])
        self.assertEqual(0, self.manifest["migrationCount"])
        self.assertEqual([], self.manifest["migrations"])
        self.assertEqual(0, self.manifest["featureFlagMutationCount"])
        self.assertEqual([], self.manifest["featureFlags"])
        self.assertEqual(0, self.manifest["databaseMutationCount"])
        self.assertEqual([], self.manifest["databaseMutations"])
        self.assertFalse(self.manifest["packagePolicy"]["containsHistoricalMigrationBundle"])
        assembly = self.manifest["buildAssemblyPolicy"]
        self.assertFalse(assembly["dockerCopyScriptAllowed"])
        self.assertFalse(assembly["reuseExistingPackagedJarOrDistAllowed"])
        self.assertFalse(assembly["copyExistingDockerErpTreeAllowed"])
        self.assertFalse(assembly["copyExistingDockerNginxTreeAllowed"])
        self.assertEqual(
            "CUSTOMER_CARD_EXPECTED_COMMIT",
            assembly["expectedCommitEnvironmentVariable"],
        )
        self.assertTrue(assembly["expectedCommitRequiredForArchiveVerification"])
        self.assertTrue(assembly["jarBuildCommitMustMatchCandidate"])
        self.assertEqual(10, assembly["requiredFreshJarCount"])
        self.assertEqual(10, assembly["requiredServiceDockerfileCount"])
        self.assertTrue(assembly["frontendReleaseInfoCommitMustMatchCandidate"])
        self.assertTrue(assembly["archiveFilesMustBeNonEmpty"])
        for segment in (
            "mysql", "history", "upload", "log", "cache", "key", "secret"
        ):
            self.assertIn(segment, {
                value.lower()
                for value in self.manifest["packagePolicy"]["forbiddenPathSegments"]
            })
        self.assertTrue(self.manifest["packagePolicy"]["forbiddenEnvironmentFiles"])
        hunk_isolation = self.manifest["hunkIsolation"]
        self.assertIn("erp-ui/src/views/mobile/feature/mobileFormPayloads.js", hunk_isolation)
        self.assertIn("erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue", hunk_isolation)
        self.assertIn("progressive-redesign CSS", hunk_isolation[
            "erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue"
        ])
        self.assertIn(
            "erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/IdempotentSubmitAspect.java",
            hunk_isolation,
        )
        self.assertIn(
            "erp-common/erp-common-security/src/test/java/com/erp/common/security/aspect/IdempotentSubmitAspectTest.java",
            hunk_isolation,
        )
        self.assertIn("SignScopeHeaderUtils", hunk_isolation[
            "erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/IdempotentSubmitAspect.java"
        ])
        self.assertEqual("short-lived in-flight guard only", self.manifest["idempotencyContract"]["redisRole"])
        self.assertFalse(self.manifest["idempotencyContract"]["releaseOnSuccessDefault"])
        self.assertEqual(128, self.manifest["idempotencyContract"]["durableKeyMaxLength"])
        self.assertEqual(
            {
                "wholeValueTrimmed": True,
                "preserveTrailingEmptyTokens": True,
                "tokenEdgesTrimmedOnly": True,
                "wildcardAllowed": False,
                "emptyTokenAllowed": False,
                "embeddedWhitespaceAllowed": False,
                "leadingZeroAllowed": False,
                "positiveLongIdsOnly": True,
                "maximumIdInclusive": "9223372036854775807",
                "overflowFailsClosed": True,
                "remoteValidationInputs": [
                    "allowlist config value in preflight",
                    "allowlist config value in postcheck",
                    "CUSTOMER_CARD_EXPECTED_SHOPS",
                    "CUSTOMER_CARD_PILOT_SHOP_ID",
                    "CUSTOMER_CARD_NONPILOT_SHOP_ID",
                ],
                "canonicalization": (
                    "validated decimal strings sorted lexicographically; "
                    "numeric sort is forbidden"
                ),
            },
            self.manifest["allowlistParsingContract"],
        )
        config_contract = self.manifest["configurationUiContract"]
        self.assertEqual(
            "erp-ui/src/views/system/config/index.vue",
            config_contract["validatorEvidenceFile"],
        )
        self.assertEqual(
            "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysConfigServiceImpl.java",
            config_contract["serviceValidatorEvidenceFile"],
        )
        self.assertEqual(
            "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysConfig.java",
            config_contract["valueLengthEvidenceFile"],
        )
        self.assertFalse(config_contract["validatorEvidenceIncludedInRelease"])
        self.assertFalse(config_contract["serviceValidatorEvidenceIncludedInRelease"])
        self.assertFalse(config_contract["valueLengthEvidenceIncludedInRelease"])
        self.assertFalse(config_contract["configValueBlankOrNullAllowed"])
        self.assertEqual(
            [
                "verify global enabled is explicitly false",
                "require approval for one first-pilot active STORE ID; stop if absent",
                "create allowedShops through the system parameter UI with exactly that approved ID",
                "verify the successful parameter operation log",
                "run disabled preflight",
            ],
            config_contract["missingAllowlistKeyOrderedSteps"],
        )
        self.assertTrue(config_contract["disabledPreflightAllowsNonEmptyAllowlist"])
        self.assertTrue(config_contract["globalFalseStillBlocksWrites"])
        self.assertEqual(
            [
                "set global enabled false through the system parameter UI",
                "verify the successful parameter operation log",
                "invalidate rollout sessions and require re-login",
                "retain the last approved explicit valid allowlist",
                "roll back code",
                "run disabled preflight and postcheck",
            ],
            config_contract["rollbackOrderedSteps"],
        )
        self.assertFalse(config_contract["rollbackAllowlistBlankOrNullAllowed"])
        self.assertFalse(config_contract["rollbackAllowlistPlaceholderAllowed"])
        self.assertEqual(
            {
                "parameterAuditWindowMaximumMinutes": 1440,
                "systemConfigValueMaximumCharacters": 500,
                "fullRolloutOverlengthAction": (
                    "stop all-store rollout; keep approved single-store or "
                    "two-store operation and design an approved grouping scheme"
                ),
            },
            self.manifest["operationalLimits"],
        )
        self.assertEqual(100, self.manifest["paginationContract"]["pageSizeMaximum"])
        self.assertEqual(
            "parse CustomerCardPage only; never import or call PageHelper",
            self.manifest["paginationContract"]["controllerRole"],
        )

    def test_allowlist_is_exact_sorted_and_safe(self) -> None:
        expected = EXPECTED_FEATURE_FILES | EXPECTED_CONTRACT_FILES
        self.assertEqual(sorted(expected), self.files)
        self.assertEqual(len(self.files), len(set(self.files)))
        self.assertEqual(len(self.files), self.manifest["sourceFileCount"])
        self.assertNotIn("erp-ui/src/views/system/config/index.vue", self.files)
        self.assertNotIn(
            "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysConfigServiceImpl.java",
            self.files,
        )
        self.assertNotIn(
            "erp-modules/erp-system/src/main/java/com/erp/system/domain/SysConfig.java",
            self.files,
        )
        for name in self.files:
            path = PurePosixPath(name)
            self.assertFalse(path.is_absolute(), name)
            self.assertNotIn("..", path.parts, name)
            self.assertTrue((ROOT / name).is_file(), name)
            self.assertNotEqual(".sql", path.suffix.lower(), name)
            self.assertNotIn("mysql", {part.lower() for part in path.parts}, name)
            self.assertNotIn("new-business-release", name.lower(), name)
            self.assertNotIn("new-business-manifest", name.lower(), name)

    def test_manifest_declares_exact_schema_and_permission_prerequisites(self) -> None:
        self.assertEqual(EXPECTED_TABLES, set(self.manifest["schemaPrerequisites"]))
        for table, columns in self.manifest["schemaPrerequisites"].items():
            self.assertTrue(columns, table)
            self.assertEqual(len(columns), len(set(columns)), table)
        self.assertEqual(EXPECTED_PERMISSIONS, set(self.manifest["requiredPermissions"]))

    def test_preflight_is_read_only_and_covers_launch_anomalies(self) -> None:
        self.assertIn("SET SESSION TRANSACTION READ ONLY", self.preflight)
        self.assertNotIn("--apply-migrations", self.preflight)
        sql = sql_heredocs(self.preflight)
        self.assertTrue(sql)
        forbidden = re.compile(
            r"\b(?:INSERT|UPDATE|DELETE|ALTER|DROP|CREATE|REPLACE|TRUNCATE|CALL|LOCK\s+TABLES|SET\s+GLOBAL)\b",
            re.IGNORECASE,
        )
        self.assertIsNone(forbidden.search(sql))
        for table in EXPECTED_TABLES | {"inv_customer", "sys_config", "sys_menu", "sys_user_shop"}:
            self.assertIn(table, self.preflight)
        for permission in EXPECTED_PERMISSIONS:
            self.assertIn(permission, self.preflight)
        for marker in (
            "schema_missing",
            "required_index_missing",
            "active_store_employee_permission_gaps",
            "orphan_profiles",
            "orphan_records",
            "orphan_change_logs",
            "missing_profiles",
            "record_cross_store",
            "change_log_cross_store",
            "invalid_customer_store_scope",
            "wildcard $label is forbidden",
        ):
            self.assertIn(marker, self.preflight)

        normalized_sql = re.sub(r"\s+", " ", sql)
        for condition in (
            "LEFT JOIN sys_dept shop ON shop.dept_id=customer.shop_dept_id",
            "customer.shop_dept_id IS NULL",
            "customer.shop_dept_id<=0",
            "shop.dept_id IS NULL",
            "UPPER(COALESCE(shop.dept_type,''))<>'STORE'",
            "COALESCE(shop.status,'')<>'0'",
            "COALESCE(shop.del_flag,'')<>'0'",
            "NOT (service_record.shop_dept_id <=> customer.shop_dept_id)",
            "NOT (change_log.shop_dept_id <=> customer.shop_dept_id)",
        ):
            self.assertIn(condition, normalized_sql)
        self.assertNotIn("customer_without_store", self.preflight)
        self.assertIn("ALLOWLIST_MAX_CHARS=500", self.preflight)
        self.assertIn(
            "(( ${#allowlist_value} <= ALLOWLIST_MAX_CHARS ))", self.preflight
        )

        preflight_validation = strict_id_validation_block(self.preflight)
        postcheck_validation = strict_id_validation_block(self.postcheck)
        self.assertEqual(preflight_validation, postcheck_validation)
        for source in (self.preflight, self.postcheck):
            self.assertNotIn("tr -d '[:space:]'", source)
            self.assertNotRegex(source, r"\bsort\s+-n\b")
            for marker in (
                "LONG_MAX_ID='9223372036854775807'",
                '[[ "$candidate" =~ ^[1-9][0-9]*$ ]]',
                'remaining="${remaining#*,}"',
                "LC_ALL=C sort -u",
                'parse_positive_long_id_list "$allowlist_value"',
            ):
                self.assertIn(marker, source)
        self.assertIn(
            'parse_positive_long_id_list "$EXPECTED_SHOPS" '
            "'CUSTOMER_CARD_EXPECTED_SHOPS'",
            self.preflight,
        )
        self.assertIn(
            'validate_positive_long_id "$PILOT_SHOP_ID_RAW"', self.postcheck
        )
        self.assertIn(
            'validate_positive_long_id "$NONPILOT_SHOP_ID_RAW"', self.postcheck
        )

        parser_probe = "\n".join((
            "set -Eeuo pipefail",
            "ID_ERROR_PREFIX='TEST_BLOCKED'",
            preflight_validation,
            'parse_positive_long_id_list "$1" test-list',
            "printf '%s|%s|%s\\n' \"$PARSED_ID_LIST\" "
            '"$PARSED_ID_SORTED" "$PARSED_ID_COUNT"',
        ))
        valid_lists = {
            "1": "1|1|1",
            " 2 , 10 ": "2,10|10,2|2",
            "9223372036854775807": (
                "9223372036854775807|9223372036854775807|1"
            ),
        }
        for value, expected in valid_lists.items():
            completed = subprocess.run(
                ["bash", "-c", parser_probe, "id-parser", value],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertEqual(expected, completed.stdout.strip())
        for malformed in (
            "",
            "*",
            "0",
            "-1",
            "01",
            "1 2",
            "1,",
            "1,   ",
            "1,,2",
            "1, ,2",
            "1,1",
            "9223372036854775808",
        ):
            completed = subprocess.run(
                ["bash", "-c", parser_probe, "id-parser", malformed],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            self.assertNotEqual(0, completed.returncode, malformed)

    def test_postcheck_is_read_only_and_requires_runtime_and_parameter_audit(self) -> None:
        self.assertIn("SET SESSION TRANSACTION READ ONLY", self.postcheck)
        self.assertIn("sys_oper_log", self.postcheck)
        self.assertIn("参数管理", self.postcheck)
        self.assertIn("erp-new@inventory.service", self.postcheck)
        self.assertIn("/actuator/health", self.postcheck)
        self.assertIn("featureFlagMutationCount", self.postcheck)
        self.assertIn("AUDIT_WINDOW_MAX_MINUTES=1440", self.postcheck)
        self.assertIn(
            "(( 10#$AUDIT_WINDOW_MINUTES <= AUDIT_WINDOW_MAX_MINUTES ))",
            self.postcheck,
        )
        self.assertIn("ALLOWLIST_MAX_CHARS=500", self.postcheck)
        self.assertIn(
            "(( ${#allowlist_value} <= ALLOWLIST_MAX_CHARS ))", self.postcheck
        )
        for marker in (
            "capability_true",
            "capability_false",
            "legacy_replaced_admin",
            "CUSTOMER_CARD_PILOT_TOKEN_FILE",
            "CUSTOMER_CARD_NONPILOT_TOKEN_FILE",
            "CUSTOMER_CARD_LEGACY_ADMIN_TOKEN_FILE",
        ):
            self.assertIn(marker, self.postcheck)
        self.assertNotRegex(self.postcheck, r"api_call\s+(?:POST|PUT|PATCH|DELETE)\b")

    def test_idempotency_contract_is_fail_closed_and_durable(self) -> None:
        annotation = (ROOT / "erp-common/erp-common-security/src/main/java/com/erp/common/security/annotation/IdempotentSubmit.java").read_text(encoding="utf-8")
        aspect = (ROOT / "erp-common/erp-common-security/src/main/java/com/erp/common/security/aspect/IdempotentSubmitAspect.java").read_text(encoding="utf-8")
        controller = (ROOT / "erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvCustomerController.java").read_text(encoding="utf-8")
        service = (ROOT / "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvCustomerServiceCardServiceImpl.java").read_text(encoding="utf-8")
        self.assertRegex(annotation, r"boolean\s+releaseOnSuccess\s*\(\s*\)\s*default\s+false\s*;")
        self.assertIn("idempotentSubmit.releaseOnSuccess()", aspect)
        self.assertEqual(4, controller.count("@IdempotentSubmit(timeout = 30, releaseOnSuccess = true)"))
        self.assertIn("请求标识不能为空", service)
        self.assertIn("normalized.length() > 128", service)
        self.assertIn("请求标识长度不能超过128", service)
        self.assertNotIn("UUID.randomUUID", service)

        picker = (ROOT / "erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue").read_text(encoding="utf-8")
        quick_test = (ROOT / "erp-ui/test/mobileQuickCustomer.test.js").read_text(encoding="utf-8")
        open_quick = re.search(
            r"openQuickCustomerForm\(\)\s*\{([\s\S]*?)\n\s*\},\n\s*closeQuickCustomerForm",
            picker,
        )
        create_quick = re.search(
            r"createQuickCustomer\(\)\s*\{([\s\S]*?)\n\s*\},\n\s*focusSearchInputAfterQuickCreate",
            picker,
        )
        self.assertIsNotNone(open_quick)
        self.assertIsNotNone(create_quick)
        self.assertEqual(1, picker.count("Date.now()"))
        self.assertIn("Date.now()", open_quick.group(1))
        self.assertIn("requestKey: this.quickCustomerRequestKey", create_quick.group(1))
        self.assertNotIn("Date.now()", create_quick.group(1))
        self.assertIn("quick-customer retries should reuse the key", quick_test)

        feature_gate = (ROOT / "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/BusinessFeatureGate.java").read_text(encoding="utf-8")
        feature_gate_test = (ROOT / "erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/BusinessFeatureGateTest.java").read_text(encoding="utf-8")
        for marker in (
            'normalized.split(",", -1)',
            'candidate.matches("[1-9][0-9]*")',
            "Long.parseLong(candidate)",
            "catch (NumberFormatException ex)",
            "shopDeptId <= 0",
        ):
            self.assertIn(marker, feature_gate)
        self.assertNotIn('"*".equals(normalized)', feature_gate)
        for marker in (
            "blankWildcardAndMalformedAllowlistsDenyEveryShop",
            'R.ok("*")',
            '"10,garbage"',
            '"10,"',
            '"10,,12"',
            '"0,10"',
            '"-1,10"',
            '"10,9223372036854775808"',
        ):
            self.assertIn(marker, feature_gate_test)

        workbench = (ROOT / "erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue").read_text(encoding="utf-8")
        mobile_flow_test = (ROOT / "erp-ui/test/customerServiceCardMobileFlow.test.js").read_text(encoding="utf-8")
        customer_entry = next(line for line in workbench.splitlines() if '"客户资料"' in line)
        self.assertIn('permissions: ["inv:customerCard:list"]', customer_entry)
        self.assertNotIn('permissions: ["inv:customer:list"]', customer_entry)
        self.assertIn("mobile workbench should expose customer cards through the dedicated permission", mobile_flow_test)

        controller = (ROOT / "erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvCustomerController.java").read_text(encoding="utf-8")
        self.assertNotIn("PageHelper", controller)
        controller_card = java_method_body(controller, r"public\s+TableDataInfo\s+serviceCardList\s*\(")
        controller_audit = java_method_body(controller, r"public\s+TableDataInfo\s+serviceCardAudit\s*\(")
        for body, service_call in (
            (controller_card, "customerServiceCardService.selectList"),
            (controller_audit, "customerServiceCardService.selectAuditList"),
        ):
            assert_ordered(self, body, ("CustomerCardPage page = customerCardPage(request);", service_call))
            self.assertNotIn("startPage(", body)

        service_card = java_method_body(service, r"public\s+List<InvCustomerServiceCardVo>\s+selectList\s*\(")
        service_audit = java_method_body(service, r"public\s+List<InvCustomerServiceAuditVo>\s+selectAuditList\s*\(")
        local_start = java_method_body(service, r"private\s+void\s+startPage\s*\(")
        assert_ordered(self, service_card, (
            "requireStoreContext",
            "safeQuery.setShopDeptId(shopDeptId);",
            "startPage(pageNum, pageSize);",
            "mapper.selectCardList(safeQuery)",
        ))
        assert_ordered(self, service_audit, (
            "requireStoreContext",
            "safeQuery.setShopDeptId(shopDeptId);",
            "assertScopedCard",
            "startPage(pageNum, pageSize);",
            "mapper.selectAuditList(safeQuery)",
        ))
        for body, target in (
            (service_card, "return mapper.selectCardList(safeQuery);"),
            (service_audit, "return mapper.selectAuditList(safeQuery);"),
        ):
            lines = [line.strip() for line in body.splitlines() if line.strip()]
            target_index = lines.index(target)
            self.assertEqual("startPage(pageNum, pageSize);", lines[target_index - 1])
        normalized_start = re.sub(r"\s+", " ", local_start)
        self.assertIn("PageHelper.startPage(Math.max(1, pageNum),", normalized_start)
        self.assertIn("Math.min(100, Math.max(1, pageSize))", normalized_start)

        service_test = (ROOT / "erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvCustomerServiceCardServiceImplTest.java").read_text(encoding="utf-8")
        list_test = java_method_body(service_test, r"void\s+cardListStartsBoundedPaginationAfterStoreScopeChecks\s*\(")
        audit_test = java_method_body(service_test, r"void\s+auditListOverwritesCallerScopeAndReturnsOnlySafeProjection\s*\(")
        assert_ordered(self, list_test, (
            "countUserShopScope",
            "PageHelper.getLocalPage()).isNull()",
            "selectDeptTypeById",
            "PageHelper.getLocalPage()).isNull()",
            "mapper.selectCardList",
            "PageHelper.getLocalPage()",
            ".isNotNull()",
            "getPageNum()).isEqualTo(1)",
            "getPageSize()).isEqualTo(100)",
        ))
        assert_ordered(self, audit_test, (
            "mapper.selectCardById",
            "PageHelper.getLocalPage()).isNull()",
            "mapper.selectAuditList",
            "PageHelper.getLocalPage()",
            ".isNotNull()",
        ))
        self.assertNotRegex(
            sql_heredocs(self.postcheck),
            r"(?i)\b(?:INSERT|UPDATE|DELETE|ALTER|DROP|CREATE|REPLACE|TRUNCATE|CALL)\b",
        )

    def test_document_has_audited_rollout_session_and_rollback_contract(self) -> None:
        for marker in (
            "单店",
            "双店",
            "全量",
            "会话失效",
            "重新登录",
            "系统参数",
            "操作日志",
            "停止扩量",
            "回滚",
            "code-only",
            "migrationCount = 0",
        ):
            self.assertIn(marker, self.doc)
        self.assertIn(
            'if (value == null || String(value).trim() === "")',
            self.config_ui,
        )
        self.assertIn('new Error("参数键值不能为空")', self.config_ui)
        self.assertIn(
            '@Size(min = 0, max = 500, message = "参数键值长度不能超过500个字符")',
            self.config_domain,
        )
        insert_config = java_method_body(
            self.config_service, r"public\s+int\s+insertConfig\s*\("
        )
        update_resolver = java_method_body(
            self.config_service, r"private\s+void\s+resolveUpdatedConfigValue\s*\("
        )
        required_value = java_method_body(
            self.config_service, r"private\s+void\s+requireConfiguredValue\s*\("
        )
        self.assertIn("requireConfiguredValue(config.getConfigValue());", insert_config)
        self.assertIn("requireConfiguredValue(update.getConfigValue());", update_resolver)
        self.assertIn("StringUtils.isEmpty(configValue)", required_value)
        self.assertIn('参数键值不能为空', required_value)

        preflight_section = self.doc.split("## 生产只读预检", 1)[1]
        preflight_section = preflight_section.split("## 代码部署", 1)[0]
        assert_ordered(self, preflight_section, (
            "总开关为显式 `false`",
            "首个试点 active STORE ID 的审批",
            "系统参数 UI 直接把 key 创建为该单个有效 ID",
            "“参数管理”操作日志",
            "`disabled` preflight",
        ))
        self.assertIn("首个试点尚未审批，立即停止", preflight_section)
        self.assertIn("非空 allowlist", preflight_section)
        self.assertIn("写能力仍由显式关闭的总开关阻断", preflight_section)
        self.assertNotIn("建立空值参数", self.doc)
        self.assertNotRegex(self.doc, r"(?:把|将)\s*allowlist\s*清空")

        rollback_section = self.doc.split("## 停止与回滚条件", 1)[1]
        assert_ordered(self, rollback_section, (
            "系统参数页面把总开关改回 `false`",
            "复核成功操作日志",
            "会话失效",
            "allowlist 保留最后审批的显式有效列表",
            "代码回滚",
            "`disabled` 预检和后检",
        ))
        self.assertIn("不随回滚删除或改成空字符串/null", rollback_section)
        self.assertIn("不填任何占位非法值", rollback_section)
        rollout_section = self.doc.split("## 审计式灰度", 1)[1].split(
            "## 停止与回滚条件", 1
        )[0]
        for marker in (
            "`sys_config.config_value`",
            "上限为 500 字符",
            "完整显式 ID 串长度",
            "`<= 500`",
            "停止全量扩量",
            "单店/双店阶段可继续运行",
            "另行设计、审批分组方案",
            "1440 分钟（24 小时）",
        ):
            self.assertIn(marker, rollout_section)
        deploy_blocks = re.findall(r"```bash\n(.*?)\n```", self.doc, flags=re.DOTALL)
        deploy_blocks = [block for block in deploy_blocks if "aliyun_ecs_deploy_helper.py deploy-host" in block]
        self.assertTrue(deploy_blocks)
        forbidden_options = (
            "--apply-migrations",
            "--migration-manifest",
            "--approve-migrations",
            "--database-name",
            "--mysql-user",
            "--mysql-password-file",
        )
        for block in deploy_blocks:
            for option in forbidden_options:
                self.assertNotIn(option, block)

        build_section = self.doc.split("## 本地冻结与代码专包构建", 1)[1]
        build_section = build_section.split("## 生产只读预检", 1)[0]
        self.assertIn("严禁执行 `docker/copy.sh`", build_section)
        self.assertIn('PACKAGE_STAGE="$(mktemp -d)"', build_section)
        self.assertIn('-Dbuild.commit="$CUSTOMER_CARD_EXPECTED_COMMIT"', build_section)
        self.assertIn('VUE_APP_BUILD_COMMIT="$CUSTOMER_CARD_EXPECTED_COMMIT"', build_section)
        self.assertIn("cp -R erp-ui/dist/.", build_section)
        self.assertIn("CUSTOMER_CARD_EXPECTED_COMMIT=", build_section)
        self.assertNotIn("rsync", build_section)
        self.assertNotRegex(build_section, r"cp\s+-a\s+docker/(?:erp|nginx)(?:/|\s)")
        self.assertNotRegex(
            build_section,
            r"(?m)^cp\s+docker/erp/.*/jar/[^ ]+\.jar\s+",
        )
        jar_stage_commands = re.findall(
            r"(?m)^cp\s+[^ ]+/target/[^ ]+\.jar\s+"
            r'"\$PACKAGE_STAGE/docker/erp/[^\n]+\.jar"$',
            build_section,
        )
        dockerfile_stage_commands = re.findall(
            r"(?m)^cp\s+docker/erp/[^ ]+/dockerfile\s+"
            r'"\$PACKAGE_STAGE/docker/erp/[^\n]+/dockerfile"$',
            build_section,
        )
        self.assertEqual(10, len(jar_stage_commands))
        self.assertEqual(10, len(dockerfile_stage_commands))
        for marker in (
            "docker/nginx/dockerfile",
            "docker/nginx/conf/nginx.conf",
            "docker/nginx/conf/nginx.host.conf",
            "docker/run-erp-service.sh",
            "docker/release/customer-service-card-release-20260719.json",
        ):
            self.assertIn(marker, build_section)

    def test_shell_scripts_parse(self) -> None:
        for script in (VERIFY_PATH, PREFLIGHT_PATH, POSTCHECK_PATH):
            subprocess.run(["bash", "-n", str(script)], check=True)
            assert_python_heredocs_compile(
                script.read_text(encoding="utf-8"), script.name
            )

    def test_archive_gate_rejects_sql_mysql_and_old_bundle(self) -> None:
        expected_commit = "a" * 40

        def write_valid_stage(root: Path) -> Path:
            stage = root / "stage"
            jar_paths = {
                name
                for name in self.manifest["runtimeArtifacts"]
                if name.endswith(".jar")
            }
            for name in self.manifest["runtimeArtifacts"]:
                target = stage / name
                target.parent.mkdir(parents=True, exist_ok=True)
                if name in jar_paths:
                    with zipfile.ZipFile(target, "w") as jar:
                        jar.writestr(
                            "BOOT-INF/classes/META-INF/build-info.properties",
                            f"build.commit={expected_commit}\n",
                        )
                elif name.endswith("release-info.json"):
                    target.write_text(
                        json.dumps({"commit": expected_commit, "buildTime": "2026-07-20T00:00:00Z"}),
                        encoding="utf-8",
                    )
                elif name.endswith("customer-service-card-release-20260719.json"):
                    target.write_text(
                        json.dumps(self.manifest, ensure_ascii=False),
                        encoding="utf-8",
                    )
                else:
                    target.write_text("fresh\n", encoding="utf-8")
            return stage

        def make_archive(root: Path, unsafe: str | None = None) -> Path:
            stage = write_valid_stage(root)
            if unsafe:
                payload = stage / unsafe
                payload.parent.mkdir(parents=True, exist_ok=True)
                payload.write_text("unsafe\n", encoding="utf-8")
            archive = root / "package.tar.gz"
            with tarfile.open(archive, "w:gz") as handle:
                handle.add(stage / "docker", arcname="docker")
            return archive

        gate_env = {
            **os.environ,
            "CUSTOMER_CARD_CONTRACT_TEST_ACTIVE": "1",
            "CUSTOMER_CARD_EXPECTED_COMMIT": expected_commit,
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            valid_archive = make_archive(root)
            completed = subprocess.run(
                ["bash", str(VERIFY_PATH), "--archive", str(valid_archive)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=gate_env,
            )
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertIn("jars=9 dockerfiles=9", completed.stdout)

            missing_commit = subprocess.run(
                ["bash", str(VERIFY_PATH), "--archive", str(valid_archive)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env={**os.environ, "CUSTOMER_CARD_CONTRACT_TEST_ACTIVE": "1"},
            )
            self.assertNotEqual(0, missing_commit.returncode)

            wrong_commit = subprocess.run(
                ["bash", str(VERIFY_PATH), "--archive", str(valid_archive)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env={**gate_env, "CUSTOMER_CARD_EXPECTED_COMMIT": "b" * 40},
            )
            self.assertNotEqual(0, wrong_commit.returncode)

            unsafe_names = (
                "docker/release/forbidden.sql",
                "docker/mysql/releases/customer/seed.txt",
                "docker/release/new-business-release-20260714.json",
                "docker/release/unapproved-release.json",
                "docker/history/old-package.tar.gz",
                "docker/.env",
                "docker/upload/customer.bin",
                "docker/log/inventory.log",
                "docker/cache/frontend.bin",
                "docker/key/private.pem",
            )
            for index, unsafe in enumerate(unsafe_names):
                case_root = root / f"unsafe-{index}"
                case_root.mkdir()
                archive = make_archive(case_root, unsafe)
                completed = subprocess.run(
                    ["bash", str(VERIFY_PATH), "--archive", str(archive)],
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    env=gate_env,
                )
                self.assertNotEqual(0, completed.returncode, unsafe)


if __name__ == "__main__":
    unittest.main(verbosity=2)
