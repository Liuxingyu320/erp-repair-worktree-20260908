#!/usr/bin/env python3
"""Validate parsed, PII-free evidence for the seven 20260718 release gates.

This module deliberately performs no I/O.  The readiness orchestrator owns
safe file reads, JSON duplicate-key rejection, and byte-level SHA-256 checks;
this validator only accepts the already parsed index/documents and binds their
claims to the detached-signature context.
"""

from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from datetime import datetime, timedelta, timezone
from typing import Any, Mapping


RELEASE_ID = "onboard-contract-excel-20260718"
TEMPLATE_SET_VERSION = "20260718-v5-draft"
WORKBOOK_HMAC_KEY_ID = "onboard-hr-qa-workbook-hmac-20260718-v1"

GATES = (
    "nativeMySqlRehearsal",
    "templateHrLegalApproval",
    "templateRegistrationAndPlanPublish",
    "isolatedThreePersonUat",
    "selected27WorkbookUat",
    "finalPdfHashAuditEvidence",
    "oneScopeGrayApproval",
)

GATE_ROLES = {
    "nativeMySqlRehearsal": frozenset({"DBA_RELEASE_APPROVER"}),
    "templateHrLegalApproval": frozenset({"HR_LEGAL_APPROVER"}),
    "templateRegistrationAndPlanPublish": frozenset(
        {"SIGN_TEMPLATE_RELEASE_APPROVER"}
    ),
    "isolatedThreePersonUat": frozenset({"QA_BUSINESS_APPROVER"}),
    "selected27WorkbookUat": frozenset({"HR_QA_BATCH_APPROVER"}),
    "finalPdfHashAuditEvidence": frozenset({"SIGN_EVIDENCE_AUDITOR"}),
    "oneScopeGrayApproval": frozenset({"RELEASE_SCOPE_APPROVER"}),
}

GATE_ENVIRONMENTS = {
    "nativeMySqlRehearsal": frozenset({"isolated-mysql"}),
    "templateHrLegalApproval": frozenset({"approval-workflow"}),
    "templateRegistrationAndPlanPublish": frozenset({"staging/isolated-uat"}),
    "isolatedThreePersonUat": frozenset({"isolated-uat"}),
    "selected27WorkbookUat": frozenset({"one-scope-gray"}),
    "finalPdfHashAuditEvidence": frozenset({"one-scope-gray"}),
    "oneScopeGrayApproval": frozenset({"one-scope-gray"}),
}

BINDING_KEYS = {
    "candidateCommit",
    "approvedPatchSha256",
    "approvedSourceManifestSha256",
    "sourceApprovalSha256",
    "oaJarSha256",
    "systemJarSha256",
    "frontendTreeSha256",
}
INDEX_KEYS = {
    "schemaVersion",
    "releaseId",
    "status",
    "containsPii",
    "binding",
    "gates",
}
INDEX_GATE_KEYS = {
    "status",
    "evidenceId",
    "evidenceSha256",
    "subject",
    "approvedBy",
    "approvedRole",
    "approvedAt",
    "environment",
}
DOCUMENT_KEYS = {
    "schemaVersion",
    "releaseId",
    "gate",
    "status",
    "containsPii",
    "subject",
    "approvedBy",
    "approvedRole",
    "approvedAt",
    "environment",
    "binding",
    "result",
}

MIGRATIONS = (
    (
        "erp_oa_sign_onboard_import_20260718.sql",
        "e79554c95992186ebf15318cd14066fb65ec77883c1887ca01b642f595e3f945",
    ),
    (
        "erp_system_sign_profile_supplement_20260718.sql",
        "07e04d77a4e60dd174846151f9f3cdfca1a732e42f0239c85717408b9ad2ba3e",
    ),
)
REQUIRED_DATABASE_OBJECTS = (
    "oa_sign_onboard_import_batch",
    "oa_sign_onboard_import_row",
    "oa_sign_onboard_data_request",
    "sys_sign_profile_supplement_audit",
)
REQUIRED_DATABASE_INDEXES = (
    "oa_sign_task.uk_oa_sign_task_open_onboard_employee",
    "oa_sign_onboard_import_batch.uk_oa_sign_onboard_import_batch_no",
    "oa_sign_onboard_import_batch.idx_oa_sign_onboard_import_reuse",
    "oa_sign_onboard_import_row.uk_oa_sign_onboard_import_source_row",
    "oa_sign_onboard_import_row.uk_oa_sign_onboard_import_employee",
    "oa_sign_onboard_import_row.idx_oa_sign_onboard_import_row_status",
    "oa_sign_onboard_import_row.idx_oa_sign_onboard_import_task",
    "oa_sign_onboard_data_request.uk_oa_sign_onboard_data_request_no",
    "oa_sign_onboard_data_request.idx_oa_sign_onboard_data_request_row_history",
    "oa_sign_onboard_data_request.idx_oa_sign_onboard_data_request_mine",
    "sys_sign_profile_supplement_audit.uk_sign_profile_supplement_request",
    "sys_sign_profile_supplement_audit.idx_sign_profile_supplement_employee",
)
UAT_ALIASES = ("UAT-01", "UAT-02", "UAT-03")
UAT_SCENARIOS = frozenset({"LABOR", "SERVICE", "EMPLOYEE_FACTS_SUPPLEMENT"})
MINOR_TEMPLATE_FILE = "16_ONBOARD_MINOR_NONSTUDENT_DECLARATION.docx"
ROUTE_POLICIES = {
    "A1": {
        "contractTypeCode": "LABOR_CONTRACT",
        "socialTypeCode": "SOCIAL_INSURED",
        "jobGradeBand": "2-4",
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "09_ONBOARD_LABOR_CONTRACT.docx",
            "10_ONBOARD_HANDBOOK_RECEIPT.docx",
            "12_ONBOARD_SALARY_CONFIRM_B.docx",
        ),
    },
    "A2": {
        "contractTypeCode": "LABOR_CONTRACT",
        "socialTypeCode": "SOCIAL_INSURED",
        "jobGradeBand": "5-6",
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "09_ONBOARD_LABOR_CONTRACT.docx",
            "10_ONBOARD_HANDBOOK_RECEIPT.docx",
            "12_ONBOARD_SALARY_CONFIRM_B.docx",
        ),
    },
    "A3": {
        "contractTypeCode": "LABOR_CONTRACT",
        "socialTypeCode": "SOCIAL_INSURED",
        "jobGradeBand": "7-9",
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "09_ONBOARD_LABOR_CONTRACT.docx",
            "10_ONBOARD_HANDBOOK_RECEIPT.docx",
            "12_ONBOARD_SALARY_CONFIRM_B.docx",
            "15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx",
        ),
    },
    "A4": {
        "contractTypeCode": "LABOR_CONTRACT",
        "socialTypeCode": "SOCIAL_UNINSURED",
        "jobGradeBand": "2-4",
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "09_ONBOARD_LABOR_CONTRACT.docx",
            "10_ONBOARD_HANDBOOK_RECEIPT.docx",
            "11_ONBOARD_SALARY_CONFIRM_A.docx",
        ),
    },
    "A5": {
        "contractTypeCode": "LABOR_CONTRACT",
        "socialTypeCode": "SOCIAL_UNINSURED",
        "jobGradeBand": "5-6",
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "09_ONBOARD_LABOR_CONTRACT.docx",
            "10_ONBOARD_HANDBOOK_RECEIPT.docx",
            "11_ONBOARD_SALARY_CONFIRM_A.docx",
        ),
    },
    "A6": {
        "contractTypeCode": "LABOR_CONTRACT",
        "socialTypeCode": "SOCIAL_UNINSURED",
        "jobGradeBand": "7-9",
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "09_ONBOARD_LABOR_CONTRACT.docx",
            "10_ONBOARD_HANDBOOK_RECEIPT.docx",
            "11_ONBOARD_SALARY_CONFIRM_A.docx",
            "15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx",
        ),
    },
    "B1": {
        "contractTypeCode": "SERVICE_CONTRACT",
        "socialTypeCode": "SOCIAL_UNINSURED",
        "jobGradeBand": "2-4",
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "13_ONBOARD_SERVICE_CONTRACT.docx",
            "14_ONBOARD_SERVICE_RECEIPT.docx",
        ),
    },
    "B2": {
        "contractTypeCode": "SERVICE_CONTRACT",
        "socialTypeCode": "SOCIAL_UNINSURED",
        "jobGradeBand": "5-6",
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "13_ONBOARD_SERVICE_CONTRACT.docx",
            "14_ONBOARD_SERVICE_RECEIPT.docx",
        ),
    },
    "B3": {
        "contractTypeCode": "SERVICE_CONTRACT",
        "socialTypeCode": "SOCIAL_UNINSURED",
        "jobGradeBand": "7-9",
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "13_ONBOARD_SERVICE_CONTRACT.docx",
            "14_ONBOARD_SERVICE_RECEIPT.docx",
            "15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx",
        ),
    },
}
SELECTED_ROUTE_COUNTS = (
    ("A4", 19),
    ("A5", 5),
    ("A1", 1),
    ("B1", 1),
    ("B3", 1),
)
PRODUCTION_ROUTES = tuple(route for route, _ in SELECTED_ROUTE_COUNTS)
ISOLATED_UAT_ROUTES = ("A3",)
SERVICE_PERSON_TYPE_BY_ROUTE = {
    "B1": "STUDENT_INTERN",
    "B3": "RETIRED_REHIRE",
}
ALLOWED_INSURANCE_TYPE_CODES = frozenset(
    {"COMMERCIAL_ACCIDENT", "EMPLOYER_LIABILITY"}
)
SELECTED_BASE_DOCUMENT_COUNT = sum(
    employee_count * len(ROUTE_POLICIES[route_code]["templateFiles"])
    for route_code, employee_count in SELECTED_ROUTE_COUNTS
)
UAT_CASE_POLICIES = (
    {
        "caseAlias": "UAT-01",
        "scenario": "LABOR",
        "routeCode": "A3",
        "contractTypeCode": "LABOR_CONTRACT",
        "socialTypeCode": "SOCIAL_INSURED",
        "jobGradeBand": "7-9",
        "employeeLevel": 7,
        "minorNonstudentApplied": False,
        "planDeploymentClass": "ISOLATED_UAT_ONLY",
        "serviceFactMatchRequired": False,
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "09_ONBOARD_LABOR_CONTRACT.docx",
            "10_ONBOARD_HANDBOOK_RECEIPT.docx",
            "12_ONBOARD_SALARY_CONFIRM_B.docx",
            "15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx",
        ),
    },
    {
        "caseAlias": "UAT-02",
        "scenario": "SERVICE",
        "routeCode": "B3",
        "contractTypeCode": "SERVICE_CONTRACT",
        "socialTypeCode": "SOCIAL_UNINSURED",
        "jobGradeBand": "7-9",
        "employeeLevel": 7,
        "minorNonstudentApplied": False,
        "planDeploymentClass": "PRODUCTION_CANDIDATE",
        "serviceFactMatchRequired": True,
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "13_ONBOARD_SERVICE_CONTRACT.docx",
            "14_ONBOARD_SERVICE_RECEIPT.docx",
            "15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx",
        ),
    },
    {
        "caseAlias": "UAT-03",
        "scenario": "EMPLOYEE_FACTS_SUPPLEMENT",
        "routeCode": "A5",
        "contractTypeCode": "LABOR_CONTRACT",
        "socialTypeCode": "SOCIAL_UNINSURED",
        "jobGradeBand": "5-6",
        "employeeLevel": 6,
        "minorNonstudentApplied": True,
        "planDeploymentClass": "PRODUCTION_CANDIDATE",
        "serviceFactMatchRequired": False,
        "templateFiles": (
            "05_ONBOARD_COMMITMENT.docx",
            "09_ONBOARD_LABOR_CONTRACT.docx",
            "10_ONBOARD_HANDBOOK_RECEIPT.docx",
            "11_ONBOARD_SALARY_CONFIRM_A.docx",
            "16_ONBOARD_MINOR_NONSTUDENT_DECLARATION.docx",
        ),
    },
)
REQUIRED_EVENT_TYPES = (
    "DOCUMENT_READ_CONFIRMED",
    "EMPLOYEE_INITIAL_SIGNED",
    "FINAL_CONTRACT_GENERATED",
    "FINAL_CONTRACT_CONFIRMED",
)
REQUIRED_EVIDENCE_TYPES = (
    "RENDERED_SOURCE",
    "REVIEW_PDF",
    "SIGNATURE_IMAGE",
    "SIGNED_PDF",
    "SIGN_CERTIFICATE",
    "FINAL_RENDERED_SOURCE",
    "FINAL_REVIEW_PDF",
    "COMPANY_SEAL",
    "FINAL_SIGNED_PDF",
)
FEATURE_FLAGS = {
    "oa.sign.excel-import.enabled": True,
    "VUE_APP_SIGN_EXCEL_IMPORT_ENABLED": True,
    "hr.sign.lifecycle-automation.enabled": False,
    "oa.sign.emergency-create.enabled": False,
    "oa.sign.expiry.enabled": False,
    "oa.sign.reminder.enabled": False,
}

HEX_40 = re.compile(r"^[0-9a-f]{40}$")
HEX_64 = re.compile(r"^[0-9a-f]{64}$")
MYSQL_VERSION = re.compile(
    r"^(?:5\.7|8\.0)\.\d+(?:-[0-9A-Za-z][0-9A-Za-z._+~-]{0,95})?$",
    re.IGNORECASE,
)
RFC3339 = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"
    r"(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$"
)
PLACEHOLDER = re.compile(
    r"^(?:todo|tbd|pending|unknown|none|null|n/?a|test|example|placeholder|"
    r"待定|未填写|示例|测试|审批人|角色|x{2,}|[-_]+)$",
    re.IGNORECASE,
)
CONTROL_CHARACTER = re.compile(r"[\x00-\x1f\x7f]")


class GateEvidenceError(ValueError):
    """A parsed external-gate evidence claim failed closed."""


def _fail(message: str) -> None:
    raise GateEvidenceError(message)


def _mapping(value: object, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        _fail(f"{label} must be an object")
    return value


def _exact_keys(value: Mapping[str, Any], expected: set[str], label: str) -> None:
    if any(not isinstance(key, str) for key in value):
        _fail(f"{label} keys must be strings")
    actual = set(value)
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    if missing or extra:
        details: list[str] = []
        if missing:
            details.append("missing " + ", ".join(missing))
        if extra:
            details.append("unexpected " + ", ".join(extra))
        _fail(f"{label} keys are not exact ({'; '.join(details)})")


def _text(
    value: object,
    label: str,
    *,
    maximum: int = 256,
    minimum: int = 1,
    reject_placeholder: bool = False,
) -> str:
    if not isinstance(value, str) or value != value.strip():
        _fail(f"{label} must be a trimmed string")
    if (
        not minimum <= len(value) <= maximum
        or CONTROL_CHARACTER.search(value)
        or any(unicodedata.category(character).startswith("C") for character in value)
    ):
        _fail(f"{label} length or characters are invalid")
    if reject_placeholder and PLACEHOLDER.fullmatch(value):
        _fail(f"{label} must not be a placeholder")
    return value


def _identity(value: object, label: str) -> str:
    return _text(
        value,
        label,
        maximum=256,
        minimum=2,
        reject_placeholder=True,
    )


def _identity_key(value: str) -> str:
    return unicodedata.normalize("NFKC", value).casefold()


def _hash(value: object, label: str) -> str:
    if (
        not isinstance(value, str)
        or HEX_64.fullmatch(value) is None
        or len(set(value)) == 1
    ):
        _fail(f"{label} must be a lowercase SHA-256")
    return value


def _canonical_sha256(value: object) -> str:
    """Hash a PII-free JSON value using one stable, documented encoding."""
    encoded = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _service_rule_sha256(
    route_code: str, service_person_type_code: str, insurance_type_code: str
) -> str:
    return _canonical_sha256(
        {
            "schema": "onboard-service-rule-v1",
            "routeCode": route_code,
            "servicePersonTypeCode": service_person_type_code,
            "insuranceTypeCode": insurance_type_code,
        }
    )


def _commit(value: object, label: str) -> str:
    if (
        not isinstance(value, str)
        or HEX_40.fullmatch(value) is None
        or len(set(value)) == 1
    ):
        _fail(f"{label} must be a lowercase 40-character commit")
    return value


def _integer(
    value: object,
    label: str,
    *,
    minimum: int | None = None,
    maximum: int | None = None,
) -> int:
    if type(value) is not int:
        _fail(f"{label} must be an integer (boolean is not accepted)")
    if minimum is not None and value < minimum:
        _fail(f"{label} must be at least {minimum}")
    if maximum is not None and value > maximum:
        _fail(f"{label} must be at most {maximum}")
    return value


def _literal(value: object, expected: object, label: str) -> None:
    if type(expected) is bool:
        if type(value) is not bool or value is not expected:
            _fail(f"{label} must be {str(expected).lower()}")
    elif type(expected) is int:
        if type(value) is not int or value != expected:
            _fail(f"{label} must be integer {expected}")
    elif value != expected:
        _fail(f"{label} must be {expected!r}")


def _time(value: object, label: str) -> datetime:
    if (
        not isinstance(value, str)
        or len(value) > 64
        or RFC3339.fullmatch(value) is None
    ):
        _fail(f"{label} must be an RFC3339 timestamp with a timezone")
    candidate = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = datetime.fromisoformat(candidate)
    except ValueError as exc:
        _fail(f"{label} is not a valid RFC3339 timestamp: {exc}")
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        _fail(f"{label} must include a timezone")
    return parsed.astimezone(timezone.utc)


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _list(value: object, label: str, *, length: int | None = None) -> list[Any]:
    if not isinstance(value, list):
        _fail(f"{label} must be an array")
    if length is not None and len(value) != length:
        _fail(f"{label} must contain exactly {length} items")
    return value


def _expected_binding(signed: Mapping[str, Any]) -> dict[str, str]:
    if signed.get("releaseId") != RELEASE_ID:
        _fail("signed context releaseId is incorrect")
    source_approval = _mapping(signed.get("sourceApproval"), "signed sourceApproval")
    artifacts = _mapping(signed.get("artifactEvidence"), "signed artifactEvidence")
    oa = _mapping(artifacts.get("oaJar"), "signed oaJar")
    system = _mapping(artifacts.get("systemJar"), "signed systemJar")
    frontend = _mapping(artifacts.get("frontendDist"), "signed frontendDist")
    return {
        "candidateCommit": _commit(
            signed.get("candidateCommit"), "signed candidateCommit"
        ),
        "approvedPatchSha256": _hash(
            signed.get("approvedPatchSha256"), "signed approvedPatchSha256"
        ),
        "approvedSourceManifestSha256": _hash(
            signed.get("approvedSourceManifestSha256"),
            "signed approvedSourceManifestSha256",
        ),
        "sourceApprovalSha256": _hash(
            source_approval.get("sha256"), "signed sourceApproval sha256"
        ),
        "oaJarSha256": _hash(oa.get("sha256"), "signed OA JAR sha256"),
        "systemJarSha256": _hash(
            system.get("sha256"), "signed System JAR sha256"
        ),
        "frontendTreeSha256": _hash(
            frontend.get("treeSha256"), "signed frontend treeSha256"
        ),
    }


def _verify_binding(
    value: object, expected: Mapping[str, str], label: str
) -> None:
    binding = _mapping(value, label)
    _exact_keys(binding, BINDING_KEYS, label)
    for key, expected_value in expected.items():
        if key == "candidateCommit":
            actual = _commit(binding.get(key), f"{label}.{key}")
        else:
            actual = _hash(binding.get(key), f"{label}.{key}")
        if actual != expected_value:
            _fail(f"{label}.{key} does not match the signed context")


def _source_templates(source_manifest: Mapping[str, Any]) -> list[dict[str, Any]]:
    if source_manifest.get("releaseId") != RELEASE_ID:
        _fail("source manifest releaseId is incorrect")
    candidates = _mapping(
        source_manifest.get("templateCandidates"),
        "source manifest templateCandidates",
    )
    _literal(
        candidates.get("version"),
        TEMPLATE_SET_VERSION,
        "source manifest templateCandidates.version",
    )
    _literal(candidates.get("count"), 9, "source manifest templateCandidates.count")
    items = _list(
        candidates.get("items"),
        "source manifest templateCandidates.items",
        length=9,
    )
    result: list[dict[str, Any]] = []
    files: set[str] = set()
    hashes: set[str] = set()
    tuples: set[tuple[object, ...]] = set()
    for position, raw in enumerate(items):
        label = f"source manifest templateCandidates.items[{position}]"
        item = _mapping(raw, label)
        _exact_keys(item, {"file", "type", "sha256", "size"}, label)
        file_name = _text(item.get("file"), f"{label}.file", maximum=256)
        template_type = _text(item.get("type"), f"{label}.type", maximum=128)
        digest = _hash(item.get("sha256"), f"{label}.sha256")
        size = _integer(item.get("size"), f"{label}.size", minimum=1)
        signature = (file_name, template_type, digest, size)
        if file_name in files or digest in hashes or signature in tuples:
            _fail("source manifest template candidates must be unique")
        files.add(file_name)
        hashes.add(digest)
        tuples.add(signature)
        result.append(
            {"file": file_name, "type": template_type, "sha256": digest, "size": size}
        )
    return result


def _verify_native(result: object) -> None:
    label = "nativeMySqlRehearsal result"
    value = _mapping(result, label)
    _exact_keys(
        value,
        {
            "databaseProduct",
            "databaseVersion",
            "nativeServer",
            "isolatedEnvironment",
            "migrationOrder",
            "migrations",
            "requiredObjects",
            "requiredIndexes",
            "failedAssertionCount",
            "passedAssertionCount",
            "rehearsalRunRootSha256",
        },
        label,
    )
    _literal(value.get("databaseProduct"), "MySQL", f"{label}.databaseProduct")
    database_version = _text(
        value.get("databaseVersion"),
        f"{label}.databaseVersion",
        maximum=128,
        reject_placeholder=True,
    )
    if (
        MYSQL_VERSION.fullmatch(database_version) is None
        or "mariadb" in database_version.casefold()
        or "percona" in database_version.casefold()
    ):
        _fail(f"{label}.databaseVersion is not a supported native MySQL version")
    _literal(value.get("nativeServer"), True, f"{label}.nativeServer")
    _literal(
        value.get("isolatedEnvironment"), True, f"{label}.isolatedEnvironment"
    )
    _literal(
        value.get("migrationOrder"),
        [item[0] for item in MIGRATIONS],
        f"{label}.migrationOrder",
    )
    migrations = _list(value.get("migrations"), f"{label}.migrations", length=2)
    for position, (raw, expected) in enumerate(zip(migrations, MIGRATIONS)):
        item_label = f"{label}.migrations[{position}]"
        item = _mapping(raw, item_label)
        _exact_keys(item, {"file", "sha256", "applied"}, item_label)
        _literal(item.get("file"), expected[0], f"{item_label}.file")
        _literal(item.get("sha256"), expected[1], f"{item_label}.sha256")
        _literal(item.get("applied"), True, f"{item_label}.applied")
    _literal(
        value.get("requiredObjects"),
        list(REQUIRED_DATABASE_OBJECTS),
        f"{label}.requiredObjects",
    )
    _literal(
        value.get("requiredIndexes"),
        list(REQUIRED_DATABASE_INDEXES),
        f"{label}.requiredIndexes",
    )
    _literal(
        value.get("failedAssertionCount"), 0, f"{label}.failedAssertionCount"
    )
    _integer(
        value.get("passedAssertionCount"),
        f"{label}.passedAssertionCount",
        minimum=18,
        maximum=1_000_000,
    )
    _hash(value.get("rehearsalRunRootSha256"), f"{label}.rehearsalRunRootSha256")


def _verify_template_approval(
    result: object, templates: list[dict[str, Any]], envelope_time: datetime
) -> None:
    label = "templateHrLegalApproval result"
    value = _mapping(result, label)
    _exact_keys(
        value,
        {
            "templateSetVersion",
            "templateCount",
            "templates",
            "hrDecision",
            "legalDecision",
            "hrApprover",
            "legalApprover",
            "hrApprovedAt",
            "legalApprovedAt",
        },
        label,
    )
    _literal(
        value.get("templateSetVersion"),
        TEMPLATE_SET_VERSION,
        f"{label}.templateSetVersion",
    )
    _literal(value.get("templateCount"), 9, f"{label}.templateCount")
    actual_templates = _list(value.get("templates"), f"{label}.templates", length=9)
    for position, (raw, expected) in enumerate(zip(actual_templates, templates)):
        item_label = f"{label}.templates[{position}]"
        item = _mapping(raw, item_label)
        _exact_keys(item, {"file", "type", "sha256", "size"}, item_label)
        for field in ("file", "type", "sha256"):
            _literal(item.get(field), expected[field], f"{item_label}.{field}")
        _literal(item.get("size"), expected["size"], f"{item_label}.size")
    _literal(value.get("hrDecision"), "approved", f"{label}.hrDecision")
    _literal(value.get("legalDecision"), "approved", f"{label}.legalDecision")
    hr = _identity(value.get("hrApprover"), f"{label}.hrApprover")
    legal = _identity(value.get("legalApprover"), f"{label}.legalApprover")
    if _identity_key(hr) == _identity_key(legal):
        _fail(f"{label} requires distinct HR and legal approvers")
    hr_time = _time(value.get("hrApprovedAt"), f"{label}.hrApprovedAt")
    legal_time = _time(value.get("legalApprovedAt"), f"{label}.legalApprovedAt")
    if hr_time > envelope_time or legal_time > envelope_time:
        _fail(f"{label} internal approvals must not follow envelope approvedAt")


def _verify_registration(
    result: object, templates: list[dict[str, Any]]
) -> dict[int, dict[str, Any]]:
    label = "templateRegistrationAndPlanPublish result"
    value = _mapping(result, label)
    _exact_keys(
        value,
        {
            "templateSetVersion",
            "registrationCount",
            "registrations",
            "productionPlanVersionCount",
            "productionPlanVersions",
            "isolatedUatPlanVersionCount",
            "isolatedUatPlanVersions",
            "productionActiveTargetRouteCount",
            "productionTargetRouteConflictCount",
            "productionNonGlobalTargetPlanCount",
            "isolatedUatPlanInProductionCount",
            "productionUnexpectedActiveRouteCount",
            "productionInvalidActiveRuleCount",
            "unregisteredTemplateCount",
            "unboundTemplateCount",
            "productionPublishSnapshotSha256",
            "isolatedUatPublishSnapshotSha256",
        },
        label,
    )
    _literal(
        value.get("templateSetVersion"),
        TEMPLATE_SET_VERSION,
        f"{label}.templateSetVersion",
    )
    _literal(value.get("registrationCount"), 9, f"{label}.registrationCount")
    registrations = _list(
        value.get("registrations"), f"{label}.registrations", length=9
    )
    registered_ids: set[int] = set()
    registered_templates: dict[int, dict[str, Any]] = {}
    for position, (raw, template) in enumerate(zip(registrations, templates)):
        item_label = f"{label}.registrations[{position}]"
        item = _mapping(raw, item_label)
        _exact_keys(
            item,
            {"file", "sourceSha256", "templateId", "templateVersion", "status"},
            item_label,
        )
        _literal(item.get("file"), template["file"], f"{item_label}.file")
        _literal(
            item.get("sourceSha256"),
            template["sha256"],
            f"{item_label}.sourceSha256",
        )
        template_id = _integer(
            item.get("templateId"), f"{item_label}.templateId", minimum=1
        )
        template_version = _integer(
            item.get("templateVersion"),
            f"{item_label}.templateVersion",
            minimum=1,
        )
        _literal(item.get("status"), "ENABLED", f"{item_label}.status")
        if template_id in registered_ids:
            _fail(f"{label} templateId values must be unique")
        registered_ids.add(template_id)
        registered_templates[template_id] = {
            "templateId": template_id,
            "templateVersion": template_version,
            "file": template["file"],
            "type": template["type"],
            "sourceSha256": template["sha256"],
        }

    _literal(
        value.get("productionPlanVersionCount"),
        len(PRODUCTION_ROUTES),
        f"{label}.productionPlanVersionCount",
    )
    production_plans = _list(
        value.get("productionPlanVersions"),
        f"{label}.productionPlanVersions",
        length=len(PRODUCTION_ROUTES),
    )
    _literal(
        value.get("isolatedUatPlanVersionCount"),
        len(ISOLATED_UAT_ROUTES),
        f"{label}.isolatedUatPlanVersionCount",
    )
    isolated_plans = _list(
        value.get("isolatedUatPlanVersions"),
        f"{label}.isolatedUatPlanVersions",
        length=len(ISOLATED_UAT_ROUTES),
    )
    _literal(
        value.get("productionActiveTargetRouteCount"),
        len(PRODUCTION_ROUTES),
        f"{label}.productionActiveTargetRouteCount",
    )
    for field in (
        "productionTargetRouteConflictCount",
        "productionNonGlobalTargetPlanCount",
        "isolatedUatPlanInProductionCount",
        "productionUnexpectedActiveRouteCount",
        "productionInvalidActiveRuleCount",
    ):
        _literal(value.get(field), 0, f"{label}.{field}")
    plan_ids: set[int] = set()
    plan_version_ids: set[int] = set()
    plan_hashes: set[str] = set()
    rule_hashes: set[str] = set()
    bound_template_ids: set[int] = set()
    published: dict[int, dict[str, Any]] = {}

    def verify_plans(
        raw_plans: list[Any],
        collection_name: str,
        expected_routes: tuple[str, ...],
        deployment_class: str,
        production_eligible: bool,
    ) -> None:
        seen_routes: set[str] = set()
        for position, raw in enumerate(raw_plans):
            item_label = f"{label}.{collection_name}[{position}]"
            item = _mapping(raw, item_label)
            _exact_keys(
                item,
                {
                    "planId",
                    "planVersionId",
                    "planVersionSha256",
                    "ruleSnapshotSha256",
                    "status",
                    "matchingStatus",
                    "scenario",
                    "scopeType",
                    "shopDeptId",
                    "runtimeEnvironment",
                    "routeCode",
                    "contractTypeCode",
                    "socialTypeCode",
                    "jobGradeBand",
                    "servicePersonTypeCode",
                    "insuranceTypeCode",
                    "serviceFactRuleSha256",
                    "deploymentClass",
                    "productionEligible",
                    "templateIds",
                },
                item_label,
            )
            plan_id = _integer(
                item.get("planId"), f"{item_label}.planId", minimum=1
            )
            version_id = _integer(
                item.get("planVersionId"),
                f"{item_label}.planVersionId",
                minimum=1,
            )
            if plan_id in plan_ids or version_id in plan_version_ids:
                _fail(f"{label} plan and plan-version IDs must be unique")
            plan_ids.add(plan_id)
            plan_version_ids.add(version_id)
            plan_hash = _hash(
                item.get("planVersionSha256"),
                f"{item_label}.planVersionSha256",
            )
            if plan_hash in plan_hashes:
                _fail(f"{label} planVersionSha256 values must be unique")
            plan_hashes.add(plan_hash)
            rule_hash = _hash(
                item.get("ruleSnapshotSha256"),
                f"{item_label}.ruleSnapshotSha256",
            )
            if rule_hash in rule_hashes:
                _fail(f"{label} ruleSnapshotSha256 values must be unique")
            rule_hashes.add(rule_hash)
            _literal(item.get("status"), "PUBLISHED", f"{item_label}.status")
            _literal(
                item.get("matchingStatus"),
                "ENABLED",
                f"{item_label}.matchingStatus",
            )
            _literal(item.get("scenario"), "ONBOARD", f"{item_label}.scenario")
            _literal(
                item.get("scopeType"),
                "GLOBAL_HR_CATALOG",
                f"{item_label}.scopeType",
            )
            _literal(item.get("shopDeptId"), 0, f"{item_label}.shopDeptId")
            expected_environment = (
                "staging" if production_eligible else "isolated-uat"
            )
            _literal(
                item.get("runtimeEnvironment"),
                expected_environment,
                f"{item_label}.runtimeEnvironment",
            )
            _literal(
                item.get("deploymentClass"),
                deployment_class,
                f"{item_label}.deploymentClass",
            )
            _literal(
                item.get("productionEligible"),
                production_eligible,
                f"{item_label}.productionEligible",
            )
            route_code = item.get("routeCode")
            if route_code not in expected_routes:
                _fail(f"{item_label}.routeCode is not allowed for this plan class")
            assert isinstance(route_code, str)
            if route_code in seen_routes:
                _fail(f"{label}.{collection_name} routeCode values must be unique")
            seen_routes.add(route_code)
            policy = ROUTE_POLICIES[route_code]
            for field in (
                "contractTypeCode",
                "socialTypeCode",
                "jobGradeBand",
            ):
                _literal(item.get(field), policy[field], f"{item_label}.{field}")
            service_fact_hash: str | None = None
            service_person_type: str | None = None
            insurance_type: str | None = None
            if route_code in SERVICE_PERSON_TYPE_BY_ROUTE:
                service_person_type = SERVICE_PERSON_TYPE_BY_ROUTE[route_code]
                _literal(
                    item.get("servicePersonTypeCode"),
                    service_person_type,
                    f"{item_label}.servicePersonTypeCode",
                )
                insurance_type = item.get("insuranceTypeCode")
                if insurance_type not in ALLOWED_INSURANCE_TYPE_CODES:
                    _fail(f"{item_label}.insuranceTypeCode is not allowed")
                assert isinstance(insurance_type, str)
                service_fact_hash = _hash(
                    item.get("serviceFactRuleSha256"),
                    f"{item_label}.serviceFactRuleSha256",
                )
                expected_service_fact_hash = _service_rule_sha256(
                    route_code, service_person_type, insurance_type
                )
                if service_fact_hash != expected_service_fact_hash:
                    _fail(f"{item_label}.serviceFactRuleSha256 is incorrect")
            else:
                _literal(
                    item.get("servicePersonTypeCode"),
                    None,
                    f"{item_label}.servicePersonTypeCode",
                )
                _literal(
                    item.get("insuranceTypeCode"),
                    None,
                    f"{item_label}.insuranceTypeCode",
                )
                _literal(
                    item.get("serviceFactRuleSha256"),
                    None,
                    f"{item_label}.serviceFactRuleSha256",
                )

            template_ids = _list(
                item.get("templateIds"), f"{item_label}.templateIds"
            )
            if not 1 <= len(template_ids) <= 9:
                _fail(f"{item_label}.templateIds must contain 1 to 9 items")
            local: set[int] = set()
            plan_templates: list[dict[str, Any]] = []
            for template_position, raw_id in enumerate(template_ids):
                template_id = _integer(
                    raw_id,
                    f"{item_label}.templateIds[{template_position}]",
                    minimum=1,
                )
                if template_id in local:
                    _fail(f"{item_label}.templateIds must be unique")
                if template_id not in registered_ids:
                    _fail(
                        f"{item_label}.templateIds contains an unregistered template"
                    )
                local.add(template_id)
                bound_template_ids.add(template_id)
                plan_templates.append(registered_templates[template_id])
            expected_files = policy["templateFiles"] + (MINOR_TEMPLATE_FILE,)
            plan_files = tuple(template["file"] for template in plan_templates)
            if plan_files != expected_files:
                _fail(
                    f"{item_label}.templateIds do not match the route candidate policy"
                )
            published[version_id] = {
                "planId": plan_id,
                "planVersionId": version_id,
                "planVersionSha256": plan_hash,
                "ruleSnapshotSha256": rule_hash,
                "scenario": "ONBOARD",
                "scopeType": "GLOBAL_HR_CATALOG",
                "shopDeptId": 0,
                "runtimeEnvironment": expected_environment,
                "routeCode": route_code,
                "contractTypeCode": policy["contractTypeCode"],
                "socialTypeCode": policy["socialTypeCode"],
                "jobGradeBand": policy["jobGradeBand"],
                "servicePersonTypeCode": service_person_type,
                "insuranceTypeCode": insurance_type,
                "serviceFactRuleSha256": service_fact_hash,
                "deploymentClass": deployment_class,
                "productionEligible": production_eligible,
                "templates": tuple(plan_templates),
            }
        if seen_routes != set(expected_routes):
            _fail(f"{label}.{collection_name} does not cover the required routes")

    verify_plans(
        production_plans,
        "productionPlanVersions",
        PRODUCTION_ROUTES,
        "PRODUCTION_CANDIDATE",
        True,
    )
    verify_plans(
        isolated_plans,
        "isolatedUatPlanVersions",
        ISOLATED_UAT_ROUTES,
        "ISOLATED_UAT_ONLY",
        False,
    )
    _literal(
        value.get("unregisteredTemplateCount"),
        0,
        f"{label}.unregisteredTemplateCount",
    )
    _literal(
        value.get("unboundTemplateCount"), 0, f"{label}.unboundTemplateCount"
    )
    if bound_template_ids != registered_ids:
        _fail(f"{label} does not bind every registered template")
    production_snapshot = _hash(
        value.get("productionPublishSnapshotSha256"),
        f"{label}.productionPublishSnapshotSha256",
    )
    expected_production_snapshot = _canonical_sha256(production_plans)
    if production_snapshot != expected_production_snapshot:
        _fail(f"{label}.productionPublishSnapshotSha256 is incorrect")
    isolated_snapshot = _hash(
        value.get("isolatedUatPublishSnapshotSha256"),
        f"{label}.isolatedUatPublishSnapshotSha256",
    )
    expected_isolated_snapshot = _canonical_sha256(isolated_plans)
    if isolated_snapshot != expected_isolated_snapshot:
        _fail(f"{label}.isolatedUatPublishSnapshotSha256 is incorrect")
    if production_snapshot == isolated_snapshot:
        _fail(f"{label} production and isolated UAT snapshots must be distinct")
    return published


def _verify_case_templates(
    value: object,
    expected: tuple[dict[str, Any], ...],
    label: str,
) -> None:
    items = _list(value, label, length=len(expected))
    keys = {"templateId", "templateVersion", "file", "type", "sourceSha256"}
    for position, (raw, expected_item) in enumerate(zip(items, expected)):
        item_label = f"{label}[{position}]"
        item = _mapping(raw, item_label)
        _exact_keys(item, keys, item_label)
        _literal(
            item.get("templateId"), expected_item["templateId"], f"{item_label}.templateId"
        )
        _literal(
            item.get("templateVersion"),
            expected_item["templateVersion"],
            f"{item_label}.templateVersion",
        )
        _literal(item.get("file"), expected_item["file"], f"{item_label}.file")
        _literal(item.get("type"), expected_item["type"], f"{item_label}.type")
        actual_hash = _hash(item.get("sourceSha256"), f"{item_label}.sourceSha256")
        if actual_hash != expected_item["sourceSha256"]:
            _fail(f"{item_label}.sourceSha256 does not match the registered source")


def _templates_for_files(
    plan: Mapping[str, Any], files: tuple[str, ...], label: str
) -> tuple[dict[str, Any], ...]:
    templates = plan.get("templates")
    if not isinstance(templates, tuple):
        _fail(f"{label} has no immutable registered templates")
    by_file = {template["file"]: template for template in templates}
    if len(by_file) != len(templates):
        _fail(f"{label} registered template files are not unique")
    try:
        return tuple(by_file[file_name] for file_name in files)
    except KeyError as exc:
        _fail(f"{label} does not contain required template {exc.args[0]}")


def _resolved_template_set_sha256(
    templates: tuple[dict[str, Any], ...]
) -> str:
    payload = [
        {
            "templateId": template["templateId"],
            "templateVersion": template["templateVersion"],
            "file": template["file"],
            "type": template["type"],
            "sourceSha256": template["sourceSha256"],
        }
        for template in templates
    ]
    encoded = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _conditional_placement_summary_sha256(
    resolved_usage: list[Any], minor_count: int, document_count: int
) -> str:
    variants = [dict(_mapping(item, "conditional placement variant")) for item in resolved_usage]
    return _canonical_sha256(
        {
            "schema": "onboard-conditional-placement-summary-v1",
            "variants": variants,
            "minorNonstudentPlacementCount": minor_count,
            "documentCount": document_count,
        }
    )


def _service_fact_summary_sha256(
    plan_usage: list[Any], approved_count: int, employee_editable_count: int
) -> str:
    routes = []
    for raw in plan_usage:
        item = _mapping(raw, "service fact route")
        route_code = item.get("routeCode")
        if route_code not in SERVICE_PERSON_TYPE_BY_ROUTE:
            continue
        routes.append(
            {
                "routeCode": route_code,
                "planVersionId": item.get("planVersionId"),
                "servicePersonTypeCode": item.get("servicePersonTypeCode"),
                "insuranceTypeCode": item.get("insuranceTypeCode"),
                "serviceFactRuleSha256": item.get("serviceFactRuleSha256"),
                "employeeCount": item.get("employeeCount"),
            }
        )
    return _canonical_sha256(
        {
            "schema": "onboard-service-fact-summary-v1",
            "serviceFactsHrApprovedCount": approved_count,
            "employeeEditableLaborIdentityCount": employee_editable_count,
            "routes": routes,
        }
    )


def _selected_aggregate_summary_sha256(value: Mapping[str, Any]) -> str:
    # The aggregate commitment also binds the opaque workbook HMAC, its
    # algorithm and key id, without exposing the workbook or HMAC secret.
    excluded = {"rowOutcomeRootSha256"}
    payload = {
        key: value[key]
        for key in sorted(value)
        if key not in excluded
    }
    return _canonical_sha256(
        {
            "schema": "onboard-selected-27-aggregate-v1",
            "result": payload,
        }
    )


def _verify_three_person_uat(
    result: object,
    published_plans: Mapping[int, dict[str, Any]],
) -> None:
    label = "isolatedThreePersonUat result"
    value = _mapping(result, label)
    _exact_keys(
        value,
        {
            "caseCount",
            "passedCount",
            "failedCount",
            "oaArtifactStarted",
            "systemArtifactStarted",
            "oaHealthProbePassed",
            "systemHealthProbePassed",
            "frontendServed",
            "frontendBundleLoaded",
            "browserFlowPassed",
            "browserConsoleErrorCount",
            "startupProbeRootSha256",
            "cases",
            "runRootSha256",
        },
        label,
    )
    _literal(value.get("caseCount"), 3, f"{label}.caseCount")
    _literal(value.get("passedCount"), 3, f"{label}.passedCount")
    _literal(value.get("failedCount"), 0, f"{label}.failedCount")
    for field in (
        "oaArtifactStarted",
        "systemArtifactStarted",
        "oaHealthProbePassed",
        "systemHealthProbePassed",
        "frontendServed",
        "frontendBundleLoaded",
        "browserFlowPassed",
    ):
        _literal(value.get(field), True, f"{label}.{field}")
    _literal(
        value.get("browserConsoleErrorCount"),
        0,
        f"{label}.browserConsoleErrorCount",
    )
    _hash(value.get("startupProbeRootSha256"), f"{label}.startupProbeRootSha256")
    cases = _list(value.get("cases"), f"{label}.cases", length=3)
    scenarios: set[str] = set()
    used_plan_versions: set[int] = set()
    for position, raw in enumerate(cases):
        policy = UAT_CASE_POLICIES[position]
        item_label = f"{label}.cases[{position}]"
        item = _mapping(raw, item_label)
        _exact_keys(
            item,
            {
                "caseAlias",
                "scenario",
                "routeCode",
                "contractTypeCode",
                "socialTypeCode",
                "jobGradeBand",
                "employeeLevel",
                "planVersionId",
                "planVersionSha256",
                "planDeploymentClass",
                "servicePersonTypeCode",
                "insuranceTypeCode",
                "serviceFactMatchSha256",
                "expectedTemplates",
                "actualTemplates",
                "syntheticData",
                "externalSignedContractAbsentConfirmed",
                "previewPassed",
                "generated",
                "initialDocumentCount",
                "initialReadConfirmedCount",
                "handwrittenSignatureCaptured",
                "companyOptionalAtInitialSign",
                "hrLegalEntitySelected",
                "validSealSelected",
                "finalDocumentCount",
                "finalReadConfirmedCount",
                "finalConfirmed",
                "terminalStatus",
                "laborIdentityEmployeeEditable",
                "employeeFactsSupplementRequired",
                "employeeFactsSubmitted",
                "hrReviewApproved",
                "profileSyncSucceeded",
                "contractRematchedAfterSupplement",
                "minorConditionEvaluated",
                "minorNonstudentApplied",
                "minorIncomeStartYearMonthHrApproved",
            },
            item_label,
        )
        _literal(
            item.get("caseAlias"), policy["caseAlias"], f"{item_label}.caseAlias"
        )
        scenario = item.get("scenario")
        _literal(scenario, policy["scenario"], f"{item_label}.scenario")
        if scenario in scenarios:
            _fail(f"{label} scenarios must be unique")
        assert isinstance(scenario, str)
        scenarios.add(scenario)
        for field in (
            "routeCode",
            "contractTypeCode",
            "socialTypeCode",
            "jobGradeBand",
        ):
            _literal(item.get(field), policy[field], f"{item_label}.{field}")
        _literal(
            item.get("employeeLevel"),
            policy["employeeLevel"],
            f"{item_label}.employeeLevel",
        )
        plan_version_id = _integer(
            item.get("planVersionId"), f"{item_label}.planVersionId", minimum=1
        )
        if plan_version_id in used_plan_versions:
            _fail(f"{label} planVersionId values must be unique")
        used_plan_versions.add(plan_version_id)
        if plan_version_id not in published_plans:
            _fail(f"{item_label}.planVersionId is not a published registered plan")
        plan = published_plans[plan_version_id]
        for field in (
            "routeCode",
            "contractTypeCode",
            "socialTypeCode",
            "jobGradeBand",
        ):
            if plan[field] != policy[field]:
                _fail(f"{item_label} published plan metadata violates the case policy")
        _literal(
            item.get("planVersionSha256"),
            plan["planVersionSha256"],
            f"{item_label}.planVersionSha256",
        )
        _literal(
            item.get("planDeploymentClass"),
            policy["planDeploymentClass"],
            f"{item_label}.planDeploymentClass",
        )
        if plan["deploymentClass"] != policy["planDeploymentClass"]:
            _fail(f"{item_label} uses a plan from the wrong deployment class")
        if policy["serviceFactMatchRequired"]:
            _literal(
                item.get("servicePersonTypeCode"),
                plan["servicePersonTypeCode"],
                f"{item_label}.servicePersonTypeCode",
            )
            _literal(
                item.get("insuranceTypeCode"),
                plan["insuranceTypeCode"],
                f"{item_label}.insuranceTypeCode",
            )
            service_fact_hash = _hash(
                item.get("serviceFactMatchSha256"),
                f"{item_label}.serviceFactMatchSha256",
            )
            if service_fact_hash != plan["serviceFactRuleSha256"]:
                _fail(f"{item_label}.serviceFactMatchSha256 does not match plan")
        else:
            for field in ("servicePersonTypeCode", "insuranceTypeCode"):
                _literal(item.get(field), None, f"{item_label}.{field}")
            _literal(
                item.get("serviceFactMatchSha256"),
                None,
                f"{item_label}.serviceFactMatchSha256",
            )
        policy_files = tuple(policy["templateFiles"])
        plan_templates = _templates_for_files(plan, policy_files, item_label)
        _verify_case_templates(
            item.get("expectedTemplates"),
            plan_templates,
            f"{item_label}.expectedTemplates",
        )
        _verify_case_templates(
            item.get("actualTemplates"),
            plan_templates,
            f"{item_label}.actualTemplates",
        )
        for field in (
            "syntheticData",
            "externalSignedContractAbsentConfirmed",
            "previewPassed",
            "generated",
            "handwrittenSignatureCaptured",
            "companyOptionalAtInitialSign",
            "hrLegalEntitySelected",
            "validSealSelected",
            "finalConfirmed",
        ):
            _literal(item.get(field), True, f"{item_label}.{field}")
        initial = _integer(
            item.get("initialDocumentCount"),
            f"{item_label}.initialDocumentCount",
            minimum=1,
            maximum=100,
        )
        if initial != len(plan_templates):
            _fail(f"{item_label} initial document count does not match templates")
        initial_read = _integer(
            item.get("initialReadConfirmedCount"),
            f"{item_label}.initialReadConfirmedCount",
            minimum=1,
            maximum=100,
        )
        if initial_read != initial:
            _fail(f"{item_label} initial read count does not match")
        final = _integer(
            item.get("finalDocumentCount"),
            f"{item_label}.finalDocumentCount",
            minimum=1,
            maximum=100,
        )
        final_read = _integer(
            item.get("finalReadConfirmedCount"),
            f"{item_label}.finalReadConfirmedCount",
            minimum=1,
            maximum=100,
        )
        if final_read != final:
            _fail(f"{item_label} final read count does not match")
        if final != initial:
            _fail(f"{item_label} final document count does not match initial package")
        _literal(item.get("terminalStatus"), "SIGNED", f"{item_label}.terminalStatus")
        _literal(
            item.get("laborIdentityEmployeeEditable"),
            False,
            f"{item_label}.laborIdentityEmployeeEditable",
        )
        supplement_expected = scenario == "EMPLOYEE_FACTS_SUPPLEMENT"
        for field in (
            "employeeFactsSupplementRequired",
            "employeeFactsSubmitted",
            "hrReviewApproved",
            "profileSyncSucceeded",
            "contractRematchedAfterSupplement",
        ):
            _literal(item.get(field), supplement_expected, f"{item_label}.{field}")
        _literal(
            item.get("minorConditionEvaluated"),
            True,
            f"{item_label}.minorConditionEvaluated",
        )
        minor_applied = policy["minorNonstudentApplied"]
        _literal(
            item.get("minorNonstudentApplied"),
            minor_applied,
            f"{item_label}.minorNonstudentApplied",
        )
        _literal(
            item.get("minorIncomeStartYearMonthHrApproved"),
            minor_applied,
            f"{item_label}.minorIncomeStartYearMonthHrApproved",
        )
    if scenarios != UAT_SCENARIOS:
        _fail(f"{label} must cover all three scenarios")
    _hash(value.get("runRootSha256"), f"{label}.runRootSha256")


def _verify_selected_27(
    result: object,
    published_plans: Mapping[int, dict[str, Any]],
) -> int:
    label = "selected27WorkbookUat result"
    value = _mapping(result, label)
    fixed_counts = {
        "selectedRowCount": 27,
        "uniqueMatchedCount": 27,
        "unmatchedCount": 0,
        "ambiguousMatchCount": 0,
        "externalSignedConflictCount": 0,
        "planMatchedCount": 27,
        "previewReadyCount": 27,
        "generatedCount": 27,
        "sendAttemptedCount": 27,
        "sentCount": 27,
        "failedCount": 0,
        "excludedTestRowCount": 1,
        "planVersionLockedCount": 27,
        "planVersionDriftCount": 0,
        "minorConditionEvaluatedCount": 27,
        "resolvedTemplateSetVerifiedCount": 27,
    }
    _exact_keys(
        value,
        set(fixed_counts)
        | {
            "deferredMissingDataCount",
            "serviceFactsHrApprovedCount",
            "employeeEditableLaborIdentityCount",
            "routeDistribution",
            "routePlanUsage",
            "resolvedTemplateSetUsage",
            "minorNonstudentPlacementCount",
            "conditionalPlacementRootSha256",
            "serviceFactOutcomeRootSha256",
            "expectedDocumentCount",
            "generatedDocumentCount",
            "workbookIncluded",
            "rowDetailsIncluded",
            "workbookHmacAlgorithm",
            "workbookHmacKeyId",
            "workbookHmacSha256",
            "rowOutcomeRootSha256",
        },
        label,
    )
    for field, expected in fixed_counts.items():
        _literal(value.get(field), expected, f"{label}.{field}")
    _integer(
        value.get("deferredMissingDataCount"),
        f"{label}.deferredMissingDataCount",
        minimum=0,
        maximum=27,
    )
    service_facts_hr_approved_count = 2
    _literal(
        value.get("serviceFactsHrApprovedCount"),
        service_facts_hr_approved_count,
        f"{label}.serviceFactsHrApprovedCount",
    )
    employee_editable_labor_identity_count = 0
    _literal(
        value.get("employeeEditableLaborIdentityCount"),
        employee_editable_labor_identity_count,
        f"{label}.employeeEditableLaborIdentityCount",
    )

    route_distribution = _list(
        value.get("routeDistribution"),
        f"{label}.routeDistribution",
        length=len(SELECTED_ROUTE_COUNTS),
    )
    for position, (raw, (route_code, employee_count)) in enumerate(
        zip(route_distribution, SELECTED_ROUTE_COUNTS)
    ):
        item_label = f"{label}.routeDistribution[{position}]"
        item = _mapping(raw, item_label)
        _exact_keys(item, {"routeCode", "employeeCount"}, item_label)
        _literal(item.get("routeCode"), route_code, f"{item_label}.routeCode")
        _literal(
            item.get("employeeCount"),
            employee_count,
            f"{item_label}.employeeCount",
        )

    plan_usage = _list(
        value.get("routePlanUsage"),
        f"{label}.routePlanUsage",
        length=len(SELECTED_ROUTE_COUNTS),
    )
    used_plan_versions: set[int] = set()
    usage_by_route: dict[str, dict[str, Any]] = {}
    for position, (raw, (expected_route, expected_count)) in enumerate(
        zip(plan_usage, SELECTED_ROUTE_COUNTS)
    ):
        item_label = f"{label}.routePlanUsage[{position}]"
        item = _mapping(raw, item_label)
        _exact_keys(
            item,
            {
                "routeCode",
                "contractTypeCode",
                "socialTypeCode",
                "jobGradeBand",
                "scopeType",
                "shopDeptId",
                "planVersionId",
                "planVersionSha256",
                "ruleSnapshotSha256",
                "servicePersonTypeCode",
                "insuranceTypeCode",
                "serviceFactRuleSha256",
                "employeeCount",
            },
            item_label,
        )
        _literal(item.get("routeCode"), expected_route, f"{item_label}.routeCode")
        plan_version_id = _integer(
            item.get("planVersionId"), f"{item_label}.planVersionId", minimum=1
        )
        if plan_version_id in used_plan_versions:
            _fail(f"{label}.routePlanUsage planVersionId values must be unique")
        if plan_version_id not in published_plans:
            _fail(f"{item_label}.planVersionId is not a published registered plan")
        used_plan_versions.add(plan_version_id)
        plan = published_plans[plan_version_id]
        if not plan["productionEligible"] or plan["deploymentClass"] != "PRODUCTION_CANDIDATE":
            _fail(f"{item_label}.planVersionId is not production eligible")
        if plan["routeCode"] != expected_route:
            _fail(f"{item_label}.planVersionId belongs to a different route")
        for field in (
            "contractTypeCode",
            "socialTypeCode",
            "jobGradeBand",
            "scopeType",
            "shopDeptId",
        ):
            _literal(item.get(field), plan[field], f"{item_label}.{field}")
        actual_plan_hash = _hash(
            item.get("planVersionSha256"), f"{item_label}.planVersionSha256"
        )
        if actual_plan_hash != plan["planVersionSha256"]:
            _fail(f"{item_label}.planVersionSha256 does not match registration")
        actual_rule_hash = _hash(
            item.get("ruleSnapshotSha256"), f"{item_label}.ruleSnapshotSha256"
        )
        if actual_rule_hash != plan["ruleSnapshotSha256"]:
            _fail(f"{item_label}.ruleSnapshotSha256 does not match registration")
        if expected_route.startswith("B"):
            _literal(
                item.get("servicePersonTypeCode"),
                plan["servicePersonTypeCode"],
                f"{item_label}.servicePersonTypeCode",
            )
            _literal(
                item.get("insuranceTypeCode"),
                plan["insuranceTypeCode"],
                f"{item_label}.insuranceTypeCode",
            )
            service_hash = _hash(
                item.get("serviceFactRuleSha256"),
                f"{item_label}.serviceFactRuleSha256",
            )
            if service_hash != plan["serviceFactRuleSha256"]:
                _fail(
                    f"{item_label}.serviceFactRuleSha256 does not match registration"
                )
        else:
            for field in ("servicePersonTypeCode", "insuranceTypeCode"):
                _literal(item.get(field), None, f"{item_label}.{field}")
            _literal(
                item.get("serviceFactRuleSha256"),
                None,
                f"{item_label}.serviceFactRuleSha256",
            )
        employee_count = _integer(
            item.get("employeeCount"),
            f"{item_label}.employeeCount",
            minimum=1,
            maximum=27,
        )
        if employee_count != expected_count:
            _fail(f"{item_label}.employeeCount does not match the workbook route")
        usage_by_route[expected_route] = {
            "planVersionId": plan_version_id,
            "plan": plan,
            "employeeCount": employee_count,
        }

    resolved_usage = _list(
        value.get("resolvedTemplateSetUsage"),
        f"{label}.resolvedTemplateSetUsage",
    )
    if not len(SELECTED_ROUTE_COUNTS) <= len(resolved_usage) <= 10:
        _fail(f"{label}.resolvedTemplateSetUsage must contain 5 to 10 items")
    resolved_employee_counts = {route: 0 for route in PRODUCTION_ROUTES}
    resolved_plan_counts: dict[int, int] = {}
    seen_resolved_keys: set[tuple[str, bool]] = set()
    derived_document_total = 0
    derived_minor_count = 0
    for position, raw in enumerate(resolved_usage):
        item_label = f"{label}.resolvedTemplateSetUsage[{position}]"
        item = _mapping(raw, item_label)
        _exact_keys(
            item,
            {
                "routeCode",
                "planVersionId",
                "planVersionSha256",
                "minorNonstudentApplied",
                "templateIds",
                "resolvedTemplateSetSha256",
                "employeeCount",
                "documentsPerPackage",
                "documentCount",
            },
            item_label,
        )
        route_code = item.get("routeCode")
        if route_code not in usage_by_route:
            _fail(f"{item_label}.routeCode is not in the fixed workbook distribution")
        assert isinstance(route_code, str)
        minor_applied = item.get("minorNonstudentApplied")
        if type(minor_applied) is not bool:
            _fail(f"{item_label}.minorNonstudentApplied must be a boolean")
        if minor_applied and route_code in {"B1", "B3"}:
            _fail(
                f"{item_label}.minorNonstudentApplied conflicts with the approved service identity"
            )
        resolved_key = (route_code, minor_applied)
        if resolved_key in seen_resolved_keys:
            _fail(f"{label}.resolvedTemplateSetUsage variants must be unique")
        seen_resolved_keys.add(resolved_key)
        usage = usage_by_route[route_code]
        plan = usage["plan"]
        plan_version_id = _integer(
            item.get("planVersionId"), f"{item_label}.planVersionId", minimum=1
        )
        if plan_version_id != usage["planVersionId"]:
            _fail(f"{item_label}.planVersionId does not match routePlanUsage")
        _literal(
            item.get("planVersionSha256"),
            plan["planVersionSha256"],
            f"{item_label}.planVersionSha256",
        )
        expected_files = ROUTE_POLICIES[route_code]["templateFiles"]
        if minor_applied:
            expected_files = expected_files + (MINOR_TEMPLATE_FILE,)
        resolved_templates = _templates_for_files(plan, expected_files, item_label)
        expected_template_ids = [
            template["templateId"] for template in resolved_templates
        ]
        _literal(
            item.get("templateIds"),
            expected_template_ids,
            f"{item_label}.templateIds",
        )
        expected_set_hash = _resolved_template_set_sha256(resolved_templates)
        actual_set_hash = _hash(
            item.get("resolvedTemplateSetSha256"),
            f"{item_label}.resolvedTemplateSetSha256",
        )
        if actual_set_hash != expected_set_hash:
            _fail(f"{item_label}.resolvedTemplateSetSha256 is incorrect")
        employee_count = _integer(
            item.get("employeeCount"),
            f"{item_label}.employeeCount",
            minimum=1,
            maximum=27,
        )
        documents_per_package = _integer(
            item.get("documentsPerPackage"),
            f"{item_label}.documentsPerPackage",
            minimum=1,
            maximum=9,
        )
        if documents_per_package != len(resolved_templates):
            _fail(f"{item_label}.documentsPerPackage does not match resolved templates")
        document_count = _integer(
            item.get("documentCount"),
            f"{item_label}.documentCount",
            minimum=1,
            maximum=10_000,
        )
        if document_count != employee_count * documents_per_package:
            _fail(
                f"{item_label}.documentCount does not equal resolved package count"
            )
        resolved_employee_counts[route_code] += employee_count
        resolved_plan_counts[plan_version_id] = (
            resolved_plan_counts.get(plan_version_id, 0) + employee_count
        )
        derived_document_total += document_count
        if minor_applied:
            derived_minor_count += employee_count
    for route_code, expected_count in SELECTED_ROUTE_COUNTS:
        if resolved_employee_counts[route_code] != expected_count:
            _fail(
                f"{label}.resolvedTemplateSetUsage employee count does not match route {route_code}"
            )
        plan_version_id = usage_by_route[route_code]["planVersionId"]
        if resolved_plan_counts.get(plan_version_id) != expected_count:
            _fail(
                f"{label}.resolvedTemplateSetUsage does not match routePlanUsage"
            )
    minor_count = _integer(
        value.get("minorNonstudentPlacementCount"),
        f"{label}.minorNonstudentPlacementCount",
        minimum=0,
        maximum=27,
    )
    if minor_count != derived_minor_count:
        _fail(
            f"{label}.minorNonstudentPlacementCount does not match resolved usage"
        )
    if derived_document_total != SELECTED_BASE_DOCUMENT_COUNT + minor_count:
        _fail(
            f"{label}.resolved document total must equal 107 plus minor placements"
        )
    expected_documents = _integer(
        value.get("expectedDocumentCount"),
        f"{label}.expectedDocumentCount",
        minimum=SELECTED_BASE_DOCUMENT_COUNT,
        maximum=SELECTED_BASE_DOCUMENT_COUNT + 27,
    )
    generated_documents = _integer(
        value.get("generatedDocumentCount"),
        f"{label}.generatedDocumentCount",
        minimum=SELECTED_BASE_DOCUMENT_COUNT,
        maximum=SELECTED_BASE_DOCUMENT_COUNT + 27,
    )
    if generated_documents != expected_documents:
        _fail(f"{label}.generatedDocumentCount does not match expectedDocumentCount")
    if expected_documents != derived_document_total:
        _fail(
            f"{label}.expectedDocumentCount does not match resolvedTemplateSetUsage"
        )
    conditional_root = _hash(
        value.get("conditionalPlacementRootSha256"),
        f"{label}.conditionalPlacementRootSha256",
    )
    expected_conditional_root = _conditional_placement_summary_sha256(
        resolved_usage, minor_count, expected_documents
    )
    if conditional_root != expected_conditional_root:
        _fail(f"{label}.conditionalPlacementRootSha256 is incorrect")
    service_fact_root = _hash(
        value.get("serviceFactOutcomeRootSha256"),
        f"{label}.serviceFactOutcomeRootSha256",
    )
    expected_service_fact_root = _service_fact_summary_sha256(
        plan_usage,
        service_facts_hr_approved_count,
        employee_editable_labor_identity_count,
    )
    if service_fact_root != expected_service_fact_root:
        _fail(f"{label}.serviceFactOutcomeRootSha256 is incorrect")
    _literal(value.get("workbookIncluded"), False, f"{label}.workbookIncluded")
    _literal(value.get("rowDetailsIncluded"), False, f"{label}.rowDetailsIncluded")
    _literal(
        value.get("workbookHmacAlgorithm"),
        "HMAC-SHA256",
        f"{label}.workbookHmacAlgorithm",
    )
    workbook_hmac_key_id = _text(
        value.get("workbookHmacKeyId"),
        f"{label}.workbookHmacKeyId",
        maximum=128,
        reject_placeholder=True,
    )
    if workbook_hmac_key_id != WORKBOOK_HMAC_KEY_ID:
        _fail(f"{label}.workbookHmacKeyId is not the registered release key")
    _hash(value.get("workbookHmacSha256"), f"{label}.workbookHmacSha256")
    row_outcome_root = _hash(
        value.get("rowOutcomeRootSha256"), f"{label}.rowOutcomeRootSha256"
    )
    expected_row_outcome_root = _selected_aggregate_summary_sha256(value)
    if row_outcome_root != expected_row_outcome_root:
        _fail(f"{label}.rowOutcomeRootSha256 is incorrect")
    return expected_documents


def _verify_final_evidence(result: object) -> int:
    label = "finalPdfHashAuditEvidence result"
    value = _mapping(result, label)
    _exact_keys(
        value,
        {
            "packageCount",
            "signedPackageCount",
            "expectedDocumentCount",
            "documentCount",
            "verifiedDocumentCount",
            "packagesWithVerifiedDocumentsCount",
            "packagesWithoutDocumentsCount",
            "mismatchCount",
            "missingFileCount",
            "verifiedPackageRootCount",
            "verifiedAuditChainCount",
            "requiredEventTypes",
            "missingRequiredEventCount",
            "missingRequiredEvidenceCount",
            "requiredEvidenceTypes",
            "verificationRootSha256",
            "pdfFilesIncluded",
            "auditPayloadIncluded",
        },
        label,
    )
    for field in (
        "packageCount",
        "signedPackageCount",
        "packagesWithVerifiedDocumentsCount",
        "verifiedPackageRootCount",
        "verifiedAuditChainCount",
    ):
        _literal(value.get(field), 27, f"{label}.{field}")
    expected_documents = _integer(
        value.get("expectedDocumentCount"),
        f"{label}.expectedDocumentCount",
        minimum=SELECTED_BASE_DOCUMENT_COUNT,
        maximum=SELECTED_BASE_DOCUMENT_COUNT + 27,
    )
    documents = _integer(
        value.get("documentCount"),
        f"{label}.documentCount",
        minimum=SELECTED_BASE_DOCUMENT_COUNT,
        maximum=SELECTED_BASE_DOCUMENT_COUNT + 27,
    )
    verified_documents = _integer(
        value.get("verifiedDocumentCount"),
        f"{label}.verifiedDocumentCount",
        minimum=1,
        maximum=10_000,
    )
    if verified_documents != documents:
        _fail(f"{label}.verifiedDocumentCount does not match documentCount")
    if documents != expected_documents:
        _fail(f"{label}.documentCount does not match expectedDocumentCount")
    for field in (
        "packagesWithoutDocumentsCount",
        "mismatchCount",
        "missingFileCount",
        "missingRequiredEventCount",
        "missingRequiredEvidenceCount",
    ):
        _literal(value.get(field), 0, f"{label}.{field}")
    _literal(
        value.get("requiredEventTypes"),
        list(REQUIRED_EVENT_TYPES),
        f"{label}.requiredEventTypes",
    )
    _literal(
        value.get("requiredEvidenceTypes"),
        list(REQUIRED_EVIDENCE_TYPES),
        f"{label}.requiredEvidenceTypes",
    )
    _hash(value.get("verificationRootSha256"), f"{label}.verificationRootSha256")
    _literal(value.get("pdfFilesIncluded"), False, f"{label}.pdfFilesIncluded")
    _literal(
        value.get("auditPayloadIncluded"), False, f"{label}.auditPayloadIncluded"
    )
    return expected_documents


def _verify_gray(result: object) -> None:
    label = "oneScopeGrayApproval result"
    value = _mapping(result, label)
    _exact_keys(
        value,
        {
            "decision",
            "scopeType",
            "enabledScopeCount",
            "selectedScopeHmacSha256",
            "nonSelectedEnabledScopeCount",
            "globalEnable",
            "featureFlags",
            "rollbackSwitchVerified",
            "configSnapshotSha256",
            "effectiveAt",
            "expiresAt",
        },
        label,
    )
    _literal(value.get("decision"), "approved", f"{label}.decision")
    _literal(value.get("scopeType"), "SHOP_DEPT", f"{label}.scopeType")
    _literal(value.get("enabledScopeCount"), 1, f"{label}.enabledScopeCount")
    _hash(value.get("selectedScopeHmacSha256"), f"{label}.selectedScopeHmacSha256")
    _literal(
        value.get("nonSelectedEnabledScopeCount"),
        0,
        f"{label}.nonSelectedEnabledScopeCount",
    )
    _literal(value.get("globalEnable"), False, f"{label}.globalEnable")
    flags = _mapping(value.get("featureFlags"), f"{label}.featureFlags")
    _exact_keys(flags, set(FEATURE_FLAGS), f"{label}.featureFlags")
    for name, expected in FEATURE_FLAGS.items():
        _literal(flags.get(name), expected, f"{label}.featureFlags.{name}")
    _literal(
        value.get("rollbackSwitchVerified"),
        True,
        f"{label}.rollbackSwitchVerified",
    )
    _hash(value.get("configSnapshotSha256"), f"{label}.configSnapshotSha256")
    effective = _time(value.get("effectiveAt"), f"{label}.effectiveAt")
    expires = _time(value.get("expiresAt"), f"{label}.expiresAt")
    if expires <= effective:
        _fail(f"{label}.expiresAt must follow effectiveAt")


def _signed_build_times(signed_context: Mapping[str, Any]) -> tuple[datetime, datetime]:
    build = _mapping(
        signed_context.get("buildAttestation"), "signed buildAttestation"
    )
    built = _time(build.get("builtAt"), "signed buildAttestation.builtAt")
    approved = _time(
        build.get("approvedAt"), "signed buildAttestation.approvedAt"
    )
    if approved < built:
        _fail("signed build attestation approvedAt precedes builtAt")
    verified = signed_context.get("verifiedTimes")
    if verified is not None:
        verified_map = _mapping(verified, "signed verifiedTimes")
        verified_built = _time(verified_map.get("builtAt"), "signed verifiedTimes.builtAt")
        verified_approved = _time(
            verified_map.get("buildApprovedAt"),
            "signed verifiedTimes.buildApprovedAt",
        )
        if verified_built != built or verified_approved != approved:
            _fail("signed verifiedTimes disagree with buildAttestation times")
    return built, approved


def verify_gate_evidence(
    index: dict,
    documents: Mapping[str, dict],
    signed_context: dict,
    source_manifest: dict,
) -> None:
    """Validate one complete v2 evidence set or raise ``GateEvidenceError``.

    ``documents`` is keyed by the seven gate names.  The caller remains
    responsible for proving that each value came from the index item's unique
    ``evidenceId`` and matched its ``evidenceSha256`` bytes.
    """

    index_map = _mapping(index, "evidence index")
    signed = _mapping(signed_context, "signed context")
    source = _mapping(source_manifest, "source manifest")
    document_map = _mapping(documents, "evidence documents")
    expected_binding = _expected_binding(signed)
    templates = _source_templates(source)
    built_at, build_approved_at = _signed_build_times(signed)

    _exact_keys(index_map, INDEX_KEYS, "evidence index")
    _literal(index_map.get("schemaVersion"), 2, "evidence index.schemaVersion")
    _literal(index_map.get("releaseId"), RELEASE_ID, "evidence index.releaseId")
    _literal(index_map.get("status"), "verified", "evidence index.status")
    _literal(index_map.get("containsPii"), False, "evidence index.containsPii")
    _verify_binding(index_map.get("binding"), expected_binding, "evidence index.binding")

    gates = _mapping(index_map.get("gates"), "evidence index.gates")
    _exact_keys(gates, set(GATES), "evidence index.gates")
    _exact_keys(document_map, set(GATES), "evidence documents")
    evidence_ids: set[str] = set()
    evidence_hashes: set[str] = set()
    approval_times: dict[str, datetime] = {}
    published_plans: dict[int, dict[str, Any]] = {}
    selected_expected_documents: int | None = None
    final_expected_documents: int | None = None

    for gate in GATES:
        item_label = f"evidence index.gates.{gate}"
        item = _mapping(gates.get(gate), item_label)
        _exact_keys(item, INDEX_GATE_KEYS, item_label)
        _literal(item.get("status"), "passed", f"{item_label}.status")
        evidence_id = _text(
            item.get("evidenceId"),
            f"{item_label}.evidenceId",
            maximum=1024,
            reject_placeholder=True,
        )
        evidence_hash = _hash(
            item.get("evidenceSha256"), f"{item_label}.evidenceSha256"
        )
        if evidence_id in evidence_ids:
            _fail("evidence index evidenceId values must be unique")
        if evidence_hash in evidence_hashes:
            _fail("evidence index evidenceSha256 values must be unique")
        evidence_ids.add(evidence_id)
        evidence_hashes.add(evidence_hash)
        subject = _text(
            item.get("subject"),
            f"{item_label}.subject",
            maximum=256,
            reject_placeholder=True,
        )
        approved_by = _identity(item.get("approvedBy"), f"{item_label}.approvedBy")
        approved_role = item.get("approvedRole")
        if approved_role not in GATE_ROLES[gate]:
            _fail(f"{item_label}.approvedRole is not allowed")
        environment = item.get("environment")
        if environment not in GATE_ENVIRONMENTS[gate]:
            _fail(f"{item_label}.environment is not allowed")
        approved_at = _time(item.get("approvedAt"), f"{item_label}.approvedAt")
        approval_times[gate] = approved_at

        document_label = f"evidence document {gate}"
        document = _mapping(document_map.get(gate), document_label)
        _exact_keys(document, DOCUMENT_KEYS, document_label)
        _literal(document.get("schemaVersion"), 2, f"{document_label}.schemaVersion")
        _literal(document.get("releaseId"), RELEASE_ID, f"{document_label}.releaseId")
        _literal(document.get("gate"), gate, f"{document_label}.gate")
        _literal(document.get("status"), "passed", f"{document_label}.status")
        _literal(
            document.get("containsPii"), False, f"{document_label}.containsPii"
        )
        for field, expected_value in (
            ("subject", subject),
            ("approvedBy", approved_by),
            ("approvedRole", approved_role),
            ("approvedAt", item.get("approvedAt")),
            ("environment", environment),
        ):
            if document.get(field) != expected_value:
                _fail(f"{document_label}.{field} does not match the index")
        _verify_binding(
            document.get("binding"), expected_binding, f"{document_label}.binding"
        )
        result = document.get("result")
        if gate == "nativeMySqlRehearsal":
            _verify_native(result)
        elif gate == "templateHrLegalApproval":
            _verify_template_approval(result, templates, approved_at)
        elif gate == "templateRegistrationAndPlanPublish":
            published_plans = _verify_registration(result, templates)
        elif gate == "isolatedThreePersonUat":
            _verify_three_person_uat(result, published_plans)
        elif gate == "selected27WorkbookUat":
            selected_expected_documents = _verify_selected_27(result, published_plans)
        elif gate == "finalPdfHashAuditEvidence":
            final_expected_documents = _verify_final_evidence(result)
        elif gate == "oneScopeGrayApproval":
            _verify_gray(result)

    latest_gate_approval = max(approval_times.values())
    if (
        selected_expected_documents is None
        or final_expected_documents is None
        or selected_expected_documents != final_expected_documents
    ):
        _fail("selected-27 and final evidence expectedDocumentCount values differ")
    if latest_gate_approval > build_approved_at:
        _fail("a gate approvedAt follows the signed Build Attestation approvedAt")
    for gate in (
        "isolatedThreePersonUat",
        "selected27WorkbookUat",
        "finalPdfHashAuditEvidence",
        "oneScopeGrayApproval",
    ):
        if approval_times[gate] < built_at:
            _fail(f"{gate} approvedAt precedes the signed artifact builtAt")

    registration_time = approval_times["templateRegistrationAndPlanPublish"]
    if approval_times["nativeMySqlRehearsal"] > registration_time:
        _fail("native MySQL rehearsal must precede template registration/publish")
    if approval_times["templateHrLegalApproval"] > registration_time:
        _fail("HR/legal template approval must precede template registration/publish")
    if registration_time > approval_times["isolatedThreePersonUat"]:
        _fail("template registration/publish must precede isolated UAT")
    if registration_time > approval_times["selected27WorkbookUat"]:
        _fail("template registration/publish must precede the selected-27 UAT")

    gray_result = _mapping(
        document_map["oneScopeGrayApproval"].get("result"),
        "oneScopeGrayApproval result",
    )
    gray_effective = _time(
        gray_result.get("effectiveAt"), "oneScopeGrayApproval result.effectiveAt"
    )
    gray_expires = _time(
        gray_result.get("expiresAt"), "oneScopeGrayApproval result.expiresAt"
    )
    gray_approved = approval_times["oneScopeGrayApproval"]
    isolated_approved = approval_times["isolatedThreePersonUat"]
    selected_approved = approval_times["selected27WorkbookUat"]
    final_approved = approval_times["finalPdfHashAuditEvidence"]
    now = _now()
    if gray_effective > now + timedelta(minutes=5):
        _fail("one-scope gray effectiveAt is still in the future")
    if gray_expires <= now:
        _fail("one-scope gray approval has expired")
    if gray_approved > gray_effective:
        _fail("one-scope gray approval must precede effectiveAt")
    if isolated_approved > gray_approved:
        _fail("isolated three-person UAT must precede one-scope gray approval")
    if selected_approved < max(gray_approved, gray_effective):
        _fail("selected-27 UAT occurred before the approved gray window")
    if final_approved < selected_approved:
        _fail("final PDF/hash/audit evidence precedes the selected-27 UAT")
    if selected_approved > gray_expires or final_approved > gray_expires:
        _fail("selected-27 or final evidence falls outside the gray window")
    if build_approved_at > gray_expires:
        _fail("Build Attestation approval falls outside the gray window")


__all__ = [
    "GateEvidenceError",
    "GATES",
    "GATE_ENVIRONMENTS",
    "GATE_ROLES",
    "verify_gate_evidence",
]
