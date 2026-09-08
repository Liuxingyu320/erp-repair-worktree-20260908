#!/usr/bin/env python3
"""Secret-safe validation and rendering helpers for contract-signing UAT."""

from __future__ import annotations

import json
import os
import re
import stat
from datetime import date
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


RELEASE_ID = "contract-signing-release-a-20260716"
TOKEN_COOKIE = "Admin-Token"
SCENARIOS = ("ONBOARD", "REGULARIZE", "TRANSFER", "RENEWAL", "OFFBOARD")
CONTRACT_TYPES = {"LABOR", "SERVICE"}
WRITE_METHODS = {"POST", "PUT", "PATCH", "DELETE"}
REQUIRED_ACTORS = {
    "dedicatedHrOrgA": "HR",
    "dedicatedHrOrgB": "HR",
    "technicalEvidenceReviewer": "TECHNICAL_EVIDENCE",
    "legacyPackageOnly": "LEGACY_PACKAGE_ONLY",
    "employeeOnboard": "EMPLOYEE",
    "employeeRegularize": "EMPLOYEE",
    "employeeTransfer": "EMPLOYEE",
    "employeeRenewal": "EMPLOYEE",
    "employeeOffboard": "EMPLOYEE",
}
LEGACY_PACKAGE_PERMISSIONS = {
    "oa:signPackage:list",
    "oa:signPackage:query",
    "oa:signPackage:add",
    "oa:signPackage:send",
    "oa:signPackage:void",
    "oa:signPackage:template",
    "oa:signPackage:sign",
}
OA_ROOT_MENU_ID = 3000
LEGACY_PACKAGE_MENU_IDS = (4520, 4521, 4522, 4523, 4524, 4525, 4526)
TASK_CENTER_ROOT_MENU_ID = 9650
HR_BUSINESS_PERMISSIONS = {
    "oa:signTask:list",
    "oa:signTask:query",
    "oa:signTask:revalidate",
    "oa:signTask:send",
    "oa:signTask:retry",
    "oa:signTask:cancel",
    "oa:signTask:remind",
    "oa:signTask:resolveRefusal",
    "oa:signTask:resolveExpiry",
    "oa:signPackage:list",
    "oa:signPackage:query",
    "oa:signPackage:add",
    "oa:signPackage:send",
    "oa:signPackage:void",
    "oa:signPackage:template",
    "oa:signCompany:list",
    "oa:signSeal:list",
}
TECHNICAL_PERMISSION = "oa:signTask:technicalEvidence"
REQUIRED_API_CASES = {
    "permissions-dedicated-hr",
    "legacy-package-route-preserved",
    "legacy-task-center-denied",
    "cross-organization-denied",
    "success-chain-state",
    "refusal-terminal",
    "expiry-terminal",
    "replacement-version",
    "notification-failure",
    "notification-recovery",
    "duplicate-request",
    "file-verification",
}
REQUIRED_FAULT_CASES = {
    "cross-organization-denied",
    "wrong-legal-entity",
    "expired-seal",
    "seal-hash-mismatch",
    "template-coordinate-out-of-bounds",
    "file-missing",
    "file-tampered",
    "duplicate-request",
    "network-timeout-replay",
    "notification-unavailable",
    "notification-recovery",
    "concurrent-expiry-scan",
    "refusal",
    "expiry",
    "replacement-version",
}
ALLOWED_API_CASES = REQUIRED_API_CASES | {
    "wrong-legal-entity",
    "expired-seal",
    "seal-hash-mismatch",
    "template-coordinate-out-of-bounds",
    "file-missing",
    "file-tampered",
    "network-timeout-replay",
    "concurrent-expiry-scan",
}
HR_BROWSER_CHECKS = {
    "task-generated",
    "data-validated",
    "sent",
    "company-and-seal-ready",
    "finalized",
    "download-and-verify",
    "console-clean",
}
EMPLOYEE_BROWSER_CHECKS = {
    "documents-loaded",
    "read-each-document",
    "initial-signature",
    "final-documents-read",
    "final-confirmed",
    "focus-returned",
    "load-failure-explained",
    "deep-link-recovered",
    "console-clean",
}

SAFE_ID = re.compile(r"^[A-Za-z0-9._-]{6,64}$")
SAFE_DATABASE = re.compile(r"^[A-Za-z0-9_]{1,64}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
TEMPLATE = re.compile(r"\$\{([A-Za-z][A-Za-z0-9_.-]{0,159})\}")
SECRET_KEY = re.compile(
    r"(?:password|passwd|secret|access.?key|private.?key|bearer|token)$", re.I
)
SENSITIVE_DATA_KEY = re.compile(
    r"(?:signatureDataUrl|contract(?:Body|Text)|idCard|phone|bankAccount|homeAddress)$",
    re.I,
)


class UatConfigError(ValueError):
    pass


def required_text(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise UatConfigError(f"{label} is required")
    return text


def positive_id(value: Any, label: str) -> str:
    text = required_text(value, label)
    if not text.isdigit() or int(text) <= 0:
        raise UatConfigError(f"{label} must be a positive numeric id")
    return text


def full_hash(value: Any, label: str) -> str:
    text = str(value or "")
    if not SHA256.fullmatch(text) or text == "0" * 64:
        raise UatConfigError(f"{label} must be a non-placeholder SHA-256")
    return text


def _reject_embedded_secrets(value: Any, path: str = "config") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            key_text = str(key)
            if SECRET_KEY.search(key_text):
                raise UatConfigError(
                    f"{path}.{key_text} must not contain a credential; "
                    "use a Playwright storageState file"
                )
            if SENSITIVE_DATA_KEY.search(key_text):
                raise UatConfigError(
                    f"{path}.{key_text} must not contain employee or signing content; "
                    "perform that step in the controlled browser session"
                )
            _reject_embedded_secrets(child, f"{path}.{key_text}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _reject_embedded_secrets(child, f"{path}[{index}]")


def validate_target_url(value: Any, label: str) -> str:
    parsed = urlparse(required_text(value, label))
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
            f"{label} host must be visibly isolated (qa/uat/test/stage/dev)"
        )
    return parsed.geturl().rstrip("/")


def validate_storage_state(path_value: Any, repo_root: Path, host: str) -> tuple[Path, str]:
    path = Path(required_text(path_value, "actor.storageState")).expanduser()
    if not path.is_absolute():
        raise UatConfigError("actor.storageState must be an absolute path")
    if path.is_symlink():
        raise UatConfigError(f"storageState must not be a symlink: {path}")
    try:
        resolved = path.resolve(strict=True)
    except FileNotFoundError as exc:
        raise UatConfigError(f"storageState does not exist: {path}") from exc
    if not resolved.is_file():
        raise UatConfigError(f"storageState must be a regular file: {resolved}")
    try:
        resolved.relative_to(repo_root.resolve())
    except ValueError:
        pass
    else:
        raise UatConfigError("storageState must live outside the repository")
    if stat.S_IMODE(resolved.stat().st_mode) & 0o077:
        raise UatConfigError(f"storageState must be owner-only (chmod 600): {resolved}")
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
    matching = [token for token in matching if token]
    if len(matching) != 1:
        raise UatConfigError(
            f"storageState must contain exactly one non-empty {TOKEN_COOKIE} cookie for {host}"
        )
    return resolved, matching[0]


def _validate_actors(config: dict[str, Any], repo_root: Path, host: str) -> None:
    actors = config.get("actors")
    if not isinstance(actors, dict):
        raise UatConfigError("actors must be an object")
    if set(actors) != set(REQUIRED_ACTORS):
        missing = sorted(set(REQUIRED_ACTORS) - set(actors))
        extra = sorted(set(actors) - set(REQUIRED_ACTORS))
        raise UatConfigError(f"actors must match the required aliases; missing={missing}, extra={extra}")
    paths: set[str] = set()
    tokens: set[str] = set()
    for alias, required_kind in REQUIRED_ACTORS.items():
        actor = actors[alias]
        if not isinstance(actor, dict):
            raise UatConfigError(f"actors.{alias} must be an object")
        kind = required_text(actor.get("kind"), f"actors.{alias}.kind").upper()
        if kind != required_kind:
            raise UatConfigError(f"actors.{alias}.kind must equal {required_kind}")
        actor["kind"] = kind
        if actor.get("isAdministrator") is not False:
            raise UatConfigError(f"actors.{alias}.isAdministrator must be false")
        role_keys = actor.get("expectedRoleKeys")
        permissions = actor.get("expectedPermissions")
        organizations = actor.get("organizations")
        if not isinstance(role_keys, list) or not all(isinstance(item, str) for item in role_keys):
            raise UatConfigError(f"actors.{alias}.expectedRoleKeys must be a string array")
        if not isinstance(permissions, list) or not all(isinstance(item, str) for item in permissions):
            raise UatConfigError(f"actors.{alias}.expectedPermissions must be a string array")
        if not isinstance(organizations, list) or not organizations:
            raise UatConfigError(f"actors.{alias}.organizations must be a non-empty array")
        if any("admin" in key.lower() for key in role_keys) or "*:*:*" in permissions:
            raise UatConfigError(f"actors.{alias} must not be an administrator")
        if kind == "HR":
            if "sign_single_hr" not in role_keys:
                raise UatConfigError(f"actors.{alias} must use the dedicated sign_single_hr role")
            missing_permissions = sorted(HR_BUSINESS_PERMISSIONS - set(permissions))
            if missing_permissions:
                raise UatConfigError(
                    f"actors.{alias} lacks expected business permissions: {missing_permissions}"
                )
            if TECHNICAL_PERMISSION in permissions:
                raise UatConfigError(
                    f"actors.{alias} must not receive technical evidence permission"
                )
            if len(organizations) != 1:
                raise UatConfigError(f"actors.{alias} must be scoped to exactly one organization")
        elif kind == "TECHNICAL_EVIDENCE":
            if TECHNICAL_PERMISSION not in permissions:
                raise UatConfigError(
                    "technicalEvidenceReviewer must hold the technical evidence permission"
                )
            if "sign_single_hr" in role_keys or HR_BUSINESS_PERMISSIONS & set(permissions):
                raise UatConfigError(
                    "technical evidence reviewer must be independent from the HR business role"
                )
        elif kind == "LEGACY_PACKAGE_ONLY":
            if "sign_single_hr" in role_keys or TECHNICAL_PERMISSION in permissions:
                raise UatConfigError(
                    "legacyPackageOnly must not receive task-center or technical-evidence roles"
                )
            if set(permissions) != LEGACY_PACKAGE_PERMISSIONS:
                raise UatConfigError(
                    "legacyPackageOnly permissions must equal the seven legacy sign-package permissions"
                )
            if len(organizations) != 1:
                raise UatConfigError("legacyPackageOnly must be scoped to exactly one organization")
            expected_menu_ids = actor.get("expectedMenuIds")
            forbidden_menu_ids = actor.get("forbiddenMenuIds")
            if expected_menu_ids != [OA_ROOT_MENU_ID, *LEGACY_PACKAGE_MENU_IDS]:
                raise UatConfigError(
                    "legacyPackageOnly.expectedMenuIds must preserve 3000 and 4520-4526 exactly"
                )
            if forbidden_menu_ids != [TASK_CENTER_ROOT_MENU_ID]:
                raise UatConfigError(
                    "legacyPackageOnly.forbiddenMenuIds must contain only task-center root 9650"
                )
        resolved, token = validate_storage_state(actor.get("storageState"), repo_root, host)
        if str(resolved) in paths or token in tokens:
            raise UatConfigError("every actor must use a distinct storageState and login token")
        paths.add(str(resolved))
        tokens.add(token)
        actor["storageState"] = str(resolved)
        actor["_token"] = token


def _validate_organizations(config: dict[str, Any]) -> None:
    organizations = config.get("organizations")
    if not isinstance(organizations, dict) or set(organizations) != {"orgA", "orgB"}:
        raise UatConfigError("organizations must contain exactly orgA and orgB")
    org_ids: set[str] = set()
    dept_ids: set[str] = set()
    entity_ids: set[str] = set()
    seal_ids: set[str] = set()
    for alias, org in organizations.items():
        if not isinstance(org, dict):
            raise UatConfigError(f"organizations.{alias} must be an object")
        org_id = positive_id(org.get("organizationId"), f"organizations.{alias}.organizationId")
        dept_id = positive_id(org.get("deptId"), f"organizations.{alias}.deptId")
        if org_id in org_ids or dept_id in dept_ids:
            raise UatConfigError("organizationId and deptId values must be distinct")
        org_ids.add(org_id)
        dept_ids.add(dept_id)
        entities = org.get("legalEntities")
        if not isinstance(entities, list) or not entities:
            raise UatConfigError(f"organizations.{alias}.legalEntities must be non-empty")
        for index, entity in enumerate(entities):
            label = f"organizations.{alias}.legalEntities[{index}]"
            if not isinstance(entity, dict):
                raise UatConfigError(f"{label} must be an object")
            entity_id = positive_id(entity.get("legalEntityId"), f"{label}.legalEntityId")
            if entity_id in entity_ids:
                raise UatConfigError("legalEntityId values must be distinct")
            entity_ids.add(entity_id)
            if set(entity.get("candidateContractTypes") or []) != CONTRACT_TYPES:
                raise UatConfigError(f"{label} must cover LABOR and SERVICE candidates")
            seal = entity.get("seal")
            if not isinstance(seal, dict):
                raise UatConfigError(f"{label}.seal must be an object")
            seal_id = positive_id(seal.get("sealId"), f"{label}.seal.sealId")
            if seal_id in seal_ids:
                raise UatConfigError("sealId values must be distinct")
            seal_ids.add(seal_id)
            full_hash(seal.get("sha256"), f"{label}.seal.sha256")
            if seal.get("status") != "ACTIVE":
                raise UatConfigError(f"{label}.seal.status must equal ACTIVE")
            valid_until = required_text(seal.get("validUntil"), f"{label}.seal.validUntil")
            try:
                expiry = date.fromisoformat(valid_until)
            except ValueError as exc:
                raise UatConfigError(f"{label}.seal.validUntil must be YYYY-MM-DD") from exc
            if expiry <= date.today():
                raise UatConfigError(f"{label}.seal.validUntil must be in the future")

    for alias, actor in config["actors"].items():
        unknown = set(actor["organizations"]) - set(organizations)
        if unknown:
            raise UatConfigError(f"actors.{alias}.organizations contains unknown aliases: {sorted(unknown)}")
    if config["actors"]["dedicatedHrOrgA"]["organizations"] != ["orgA"]:
        raise UatConfigError("dedicatedHrOrgA must be scoped only to orgA")
    if config["actors"]["dedicatedHrOrgB"]["organizations"] != ["orgB"]:
        raise UatConfigError("dedicatedHrOrgB must be scoped only to orgB")


def _validate_employees_and_scenarios(config: dict[str, Any]) -> None:
    employees = config.get("employees")
    scenarios = config.get("scenarios")
    if not isinstance(employees, dict) or not isinstance(scenarios, dict):
        raise UatConfigError("employees and scenarios must be objects")
    if set(scenarios) != set(SCENARIOS):
        raise UatConfigError("scenarios must contain exactly the five signing scenarios")
    expected_employees = {
        "ONBOARD": "employeeOnboard",
        "REGULARIZE": "employeeRegularize",
        "TRANSFER": "employeeTransfer",
        "RENEWAL": "employeeRenewal",
        "OFFBOARD": "employeeOffboard",
    }
    if set(employees) != set(expected_employees.values()):
        raise UatConfigError("employees must contain one synthetic employee per scenario")
    employee_ids: set[str] = set()
    user_ids: set[str] = set()
    for alias, employee in employees.items():
        if not isinstance(employee, dict):
            raise UatConfigError(f"employees.{alias} must be an object")
        employee_id = positive_id(employee.get("employeeId"), f"employees.{alias}.employeeId")
        user_id = positive_id(employee.get("userId"), f"employees.{alias}.userId")
        if employee_id in employee_ids or user_id in user_ids:
            raise UatConfigError("test employeeId and userId values must be distinct")
        employee_ids.add(employee_id)
        user_ids.add(user_id)
        organization = required_text(employee.get("organization"), f"employees.{alias}.organization")
        if organization not in config["organizations"]:
            raise UatConfigError(f"employees.{alias}.organization is unknown")
        if employee.get("dataClass") != "SYNTHETIC_UAT":
            raise UatConfigError(f"employees.{alias}.dataClass must equal SYNTHETIC_UAT")
        if config["actors"][alias]["organizations"] != [organization]:
            raise UatConfigError(f"employees.{alias} and its login actor must use the same organization")

    plan_ids: set[str] = set()
    task_ids: set[str] = set()
    package_ids: set[str] = set()
    used_organizations: set[str] = set()
    for scenario in SCENARIOS:
        fixture = scenarios[scenario]
        label = f"scenarios.{scenario}"
        if not isinstance(fixture, dict):
            raise UatConfigError(f"{label} must be an object")
        employee_alias = required_text(fixture.get("employee"), f"{label}.employee")
        if employee_alias != expected_employees[scenario]:
            raise UatConfigError(f"{label}.employee must equal {expected_employees[scenario]}")
        organization = required_text(fixture.get("organization"), f"{label}.organization")
        if organization != employees[employee_alias]["organization"]:
            raise UatConfigError(f"{label} organization does not match the test employee")
        used_organizations.add(organization)
        if fixture.get("contractType") not in CONTRACT_TYPES:
            raise UatConfigError(f"{label}.contractType must be LABOR or SERVICE")
        plan_id = positive_id(fixture.get("planVersionId"), f"{label}.planVersionId")
        task_id = positive_id(fixture.get("taskId"), f"{label}.taskId")
        package_id = positive_id(fixture.get("packageId"), f"{label}.packageId")
        if plan_id in plan_ids or task_id in task_ids or package_id in package_ids:
            raise UatConfigError("scenario planVersionId, taskId and packageId values must be unique")
        plan_ids.add(plan_id)
        task_ids.add(task_id)
        package_ids.add(package_id)
        full_hash(fixture.get("planVersionSha256"), f"{label}.planVersionSha256")
        template_hashes = fixture.get("templateHashes")
        if not isinstance(template_hashes, list) or not template_hashes:
            raise UatConfigError(f"{label}.templateHashes must be non-empty")
        for index, value in enumerate(template_hashes):
            full_hash(value, f"{label}.templateHashes[{index}]")
        required_text(fixture.get("approvalRef"), f"{label}.approvalRef")
    if used_organizations != {"orgA", "orgB"}:
        raise UatConfigError("five scenarios must exercise both organizations")

    coverage = config.get("contractCoverage")
    if not isinstance(coverage, list):
        raise UatConfigError("contractCoverage must be an array")
    actual: set[tuple[str, str, str]] = set()
    for index, row in enumerate(coverage):
        label = f"contractCoverage[{index}]"
        if not isinstance(row, dict):
            raise UatConfigError(f"{label} must be an object")
        organization = required_text(row.get("organization"), f"{label}.organization")
        if organization not in config["organizations"]:
            raise UatConfigError(f"{label}.organization is unknown")
        entity_id = positive_id(row.get("legalEntityId"), f"{label}.legalEntityId")
        valid_entities = {
            str(item["legalEntityId"])
            for item in config["organizations"][organization]["legalEntities"]
        }
        if entity_id not in valid_entities:
            raise UatConfigError(f"{label}.legalEntityId is not part of {organization}")
        contract_type = required_text(row.get("contractType"), f"{label}.contractType").upper()
        if contract_type not in CONTRACT_TYPES:
            raise UatConfigError(f"{label}.contractType must be LABOR or SERVICE")
        key = (organization, entity_id, contract_type)
        if key in actual:
            raise UatConfigError(f"duplicate contract coverage: {key}")
        actual.add(key)
        positive_id(row.get("planVersionId"), f"{label}.planVersionId")
        full_hash(row.get("templateSha256"), f"{label}.templateSha256")
        required_text(row.get("approvalRef"), f"{label}.approvalRef")
    expected = {
        (organization, str(entity["legalEntityId"]), contract_type)
        for organization, org in config["organizations"].items()
        for entity in org["legalEntities"]
        for contract_type in CONTRACT_TYPES
    }
    if actual != expected:
        raise UatConfigError("contractCoverage must prove LABOR and SERVICE for every legal entity")


def _same_origin_path(value: Any, label: str) -> str:
    path = required_text(value, label)
    if not path.startswith("/") or "://" in path or "#" in path:
        raise UatConfigError(f"{label} must be a same-origin absolute path")
    if re.search(r"[?&](?:token|access_token|authorization|password)=", path, re.I):
        raise UatConfigError(f"{label} must not carry credentials in the query string")
    return path


def _validate_environment_identity_probe(config: dict[str, Any]) -> None:
    probe = config.get("environmentIdentityProbe")
    if not isinstance(probe, dict):
        raise UatConfigError("environmentIdentityProbe must be an object")
    if set(probe) != {"id", "actor", "method", "path"}:
        raise UatConfigError(
            "environmentIdentityProbe must contain exactly id, actor, method and path"
        )
    if probe.get("id") != "contract-signing-environment-identity":
        raise UatConfigError(
            "environmentIdentityProbe.id must equal contract-signing-environment-identity"
        )
    if probe.get("method") != "GET":
        raise UatConfigError("environmentIdentityProbe must use authenticated read-only GET")
    actor = required_text(probe.get("actor"), "environmentIdentityProbe.actor")
    if actor not in config["actors"]:
        raise UatConfigError("environmentIdentityProbe.actor is unknown")
    if config["actors"][actor]["kind"] == "EMPLOYEE":
        raise UatConfigError("environmentIdentityProbe must use a non-employee authenticated actor")
    _same_origin_path(probe.get("path"), "environmentIdentityProbe.path")


def _validate_legacy_menu_boundary(config: dict[str, Any]) -> None:
    boundary = config.get("legacyMenuBoundary")
    if not isinstance(boundary, dict):
        raise UatConfigError("legacyMenuBoundary must be an object")
    expected = {
        "actor": "legacyPackageOnly",
        "oaRootMenuId": OA_ROOT_MENU_ID,
        "requiredMenuIds": list(LEGACY_PACKAGE_MENU_IDS),
        "forbiddenMenuIds": [TASK_CENTER_ROOT_MENU_ID],
        "packageRoute": "sign-package",
        "packageComponent": "oa/signPackage/index",
        "taskRoute": "sign-task",
        "packageBrowserPath": "/oa/sign-package",
        "taskBrowserPath": "/oa/sign-task",
    }
    if boundary != expected:
        raise UatConfigError(
            "legacyMenuBoundary must exactly preserve 4520-4526/sign-package and deny 9650/sign-task"
        )


def _validate_browser_journeys(config: dict[str, Any]) -> None:
    journeys = config.get("browserJourneys")
    if not isinstance(journeys, list):
        raise UatConfigError("browserJourneys must be an array")
    seen_ids: set[str] = set()
    coverage: set[tuple[str, str]] = set()
    for index, journey in enumerate(journeys):
        label = f"browserJourneys[{index}]"
        if not isinstance(journey, dict):
            raise UatConfigError(f"{label} must be an object")
        journey_id = required_text(journey.get("id"), f"{label}.id")
        if not SAFE_ID.fullmatch(journey_id) or journey_id in seen_ids:
            raise UatConfigError(f"{label}.id must be unique and use safe characters")
        seen_ids.add(journey_id)
        scenario = required_text(journey.get("scenario"), f"{label}.scenario").upper()
        if scenario not in SCENARIOS:
            raise UatConfigError(f"{label}.scenario is unknown")
        mode = required_text(journey.get("mode"), f"{label}.mode").upper()
        if mode not in {"HR_DESKTOP", "EMPLOYEE_MOBILE"}:
            raise UatConfigError(f"{label}.mode is invalid")
        actor_alias = required_text(journey.get("actor"), f"{label}.actor")
        actor = config["actors"].get(actor_alias)
        if actor is None:
            raise UatConfigError(f"{label}.actor is unknown")
        organization = required_text(journey.get("organization"), f"{label}.organization")
        if organization not in actor["organizations"]:
            raise UatConfigError(f"{label} actor is not scoped to the journey organization")
        viewport = journey.get("viewport")
        if not isinstance(viewport, dict):
            raise UatConfigError(f"{label}.viewport must be an object")
        checks = set(journey.get("checks") or [])
        if mode == "HR_DESKTOP":
            if actor["kind"] != "HR":
                raise UatConfigError(f"{label} desktop journey must use a dedicated HR")
            if int(viewport.get("width") or 0) < 1280 or int(viewport.get("height") or 0) < 720:
                raise UatConfigError(f"{label} HR viewport must be at least 1280x720")
            if not HR_BROWSER_CHECKS <= checks:
                raise UatConfigError(f"{label} is missing required HR browser checks")
        else:
            expected_actor = config["scenarios"][scenario]["employee"]
            if actor_alias != expected_actor:
                raise UatConfigError(f"{label} must use the scenario employee actor")
            if viewport != {"width": 390, "height": 844}:
                raise UatConfigError(f"{label} employee viewport must equal 390x844")
            if not EMPLOYEE_BROWSER_CHECKS <= checks:
                raise UatConfigError(f"{label} is missing required employee browser checks")
        _same_origin_path(journey.get("path"), f"{label}.path")
        coverage.add((scenario, mode))
    expected = {(scenario, mode) for scenario in SCENARIOS for mode in ("HR_DESKTOP", "EMPLOYEE_MOBILE")}
    if coverage != expected or len(journeys) != len(expected):
        raise UatConfigError("browserJourneys must contain one HR and one employee journey per scenario")


def validate_api_probe(probe: Any, config: dict[str, Any], allow_write: bool) -> None:
    if not isinstance(probe, dict):
        raise UatConfigError("every apiProbe must be an object")
    probe_id = required_text(probe.get("id"), "apiProbe.id")
    if not SAFE_ID.fullmatch(probe_id):
        raise UatConfigError(f"{probe_id}: id must use safe characters")
    case = required_text(probe.get("case"), f"{probe_id}.case")
    if case not in ALLOWED_API_CASES:
        raise UatConfigError(f"{probe_id}: unsupported case {case}")
    method = str(probe.get("method") or "GET").upper()
    if method not in {"GET", "HEAD", *WRITE_METHODS}:
        raise UatConfigError(f"{probe_id}: unsupported method {method}")
    if method in WRITE_METHODS:
        required_text(probe.get("writePurpose"), f"{probe_id}.writePurpose")
        if not allow_write:
            raise UatConfigError(f"{probe_id}: write probe requires --allow-write")
    _same_origin_path(probe.get("path"), f"{probe_id}.path")
    actor_alias = probe.get("actor")
    if actor_alias is not None and actor_alias not in config["actors"]:
        raise UatConfigError(f"{probe_id}: unknown actor {actor_alias!r}")
    organization = probe.get("organization")
    if organization is not None and organization not in config["organizations"]:
        raise UatConfigError(f"{probe_id}: unknown organization {organization!r}")
    if actor_alias and organization:
        scoped = organization in config["actors"][actor_alias]["organizations"]
        if not scoped and not (
            case == "cross-organization-denied" and probe.get("expect") == "failure"
        ):
            raise UatConfigError(f"{probe_id}: actor is outside the declared organization scope")
    scenario = probe.get("scenario")
    if scenario is not None and str(scenario).upper() not in SCENARIOS:
        raise UatConfigError(f"{probe_id}: unknown scenario {scenario!r}")
    if probe.get("expect") not in {"success", "failure"}:
        raise UatConfigError(f"{probe_id}: expect must be success or failure")
    if probe.get("expect") == "failure":
        if not probe.get("allowedHttpStatuses") and not probe.get("allowedAppCodes"):
            raise UatConfigError(f"{probe_id}: failure probes require explicit statuses/codes")
        if not probe.get("messageIncludes"):
            raise UatConfigError(f"{probe_id}: failure probes require messageIncludes")
    variants = probe.get("parallelBodies")
    if variants is not None and (not isinstance(variants, list) or len(variants) < 2):
        raise UatConfigError(f"{probe_id}: parallelBodies must contain at least two bodies")
    if variants is not None:
        minimum = probe.get("minSuccess")
        maximum = probe.get("maxSuccess")
        if (
            not isinstance(minimum, int)
            or not isinstance(maximum, int)
            or not 0 <= minimum <= maximum <= len(variants)
        ):
            raise UatConfigError(f"{probe_id}: parallel success bounds are invalid")
        if minimum < len(variants):
            if not probe.get("parallelFailureAllowedHttpStatuses") and not probe.get(
                "parallelFailureAllowedAppCodes"
            ):
                raise UatConfigError(
                    f"{probe_id}: partial parallel success requires explicit failure statuses/codes"
                )
            if not probe.get("parallelFailureMessageIncludes"):
                raise UatConfigError(
                    f"{probe_id}: partial parallel success requires approved failure messages"
                )
    captures = probe.get("captures")
    if captures is not None and (
        not isinstance(captures, dict)
        or not all(SAFE_ID.fullmatch(str(name)) and isinstance(path, str) for name, path in captures.items())
    ):
        raise UatConfigError(f"{probe_id}: captures must map safe aliases to response paths")
    if case == "legacy-package-route-preserved":
        if (
            actor_alias != "legacyPackageOnly"
            or method not in {"GET", "HEAD"}
            or probe.get("expect") != "success"
            or not str(probe.get("path") or "").startswith("/signPackage/")
        ):
            raise UatConfigError(
                f"{probe_id}: legacy package preservation must GET signPackage as legacyPackageOnly"
            )
    if case == "legacy-task-center-denied":
        if (
            actor_alias != "legacyPackageOnly"
            or method not in {"GET", "HEAD"}
            or probe.get("expect") != "failure"
            or not str(probe.get("path") or "").startswith("/signTask/")
        ):
            raise UatConfigError(
                f"{probe_id}: legacy task denial must GET signTask as legacyPackageOnly"
            )
    if case == "duplicate-request":
        identity_path = probe.get("parallelSuccessIdentityPath")
        postcondition = probe.get("postconditionProbe")
        if variants is None or not isinstance(identity_path, str) or not identity_path.strip():
            raise UatConfigError(
                f"{probe_id}: duplicate-request requires parallelBodies and parallelSuccessIdentityPath"
            )
        if not isinstance(postcondition, dict):
            raise UatConfigError(f"{probe_id}: duplicate-request requires postconditionProbe")
        required_postcondition_keys = {
            "id", "actor", "organization", "method", "path", "countPath", "identityPath"
        }
        if set(postcondition) != required_postcondition_keys:
            raise UatConfigError(
                f"{probe_id}: postconditionProbe must contain exactly {sorted(required_postcondition_keys)}"
            )
        if postcondition.get("method") != "GET":
            raise UatConfigError(f"{probe_id}: postconditionProbe must be read-only GET")
        if postcondition.get("actor") != actor_alias or postcondition.get("organization") != organization:
            raise UatConfigError(
                f"{probe_id}: postconditionProbe must use the same actor and organization"
            )
        postcondition_id = required_text(postcondition.get("id"), f"{probe_id}.postconditionProbe.id")
        if not SAFE_ID.fullmatch(postcondition_id):
            raise UatConfigError(f"{probe_id}: postconditionProbe.id must use safe characters")
        _same_origin_path(postcondition.get("path"), f"{probe_id}.postconditionProbe.path")
        required_text(postcondition.get("countPath"), f"{probe_id}.postconditionProbe.countPath")
        required_text(postcondition.get("identityPath"), f"{probe_id}.postconditionProbe.identityPath")


def _validate_api_probes(config: dict[str, Any]) -> None:
    probes = config.get("apiProbes")
    if not isinstance(probes, list) or not probes:
        raise UatConfigError("apiProbes must be a non-empty array")
    ids: set[str] = set()
    cases: set[str] = set()
    success_scenarios: set[str] = set()
    for probe in probes:
        validate_api_probe(probe, config, allow_write=True)
        probe_id = str(probe["id"])
        if probe_id in ids:
            raise UatConfigError(f"duplicate apiProbe id: {probe_id}")
        ids.add(probe_id)
        cases.add(str(probe["case"]))
        if probe["case"] == "success-chain-state" and probe.get("expect") == "success":
            success_scenarios.add(str(probe.get("scenario") or "").upper())
    if not REQUIRED_API_CASES <= cases:
        raise UatConfigError(f"apiProbes are missing required cases: {sorted(REQUIRED_API_CASES - cases)}")
    if success_scenarios != set(SCENARIOS):
        raise UatConfigError("success-chain-state API probes must cover all five scenarios")


def _validate_fault_cases(config: dict[str, Any]) -> None:
    rows = config.get("faultCases")
    if not isinstance(rows, list) or len(rows) != len(REQUIRED_FAULT_CASES):
        raise UatConfigError("faultCases must contain exactly all required failure/recovery cases")
    seen: set[str] = set()
    for index, row in enumerate(rows):
        label = f"faultCases[{index}]"
        if not isinstance(row, dict):
            raise UatConfigError(f"{label} must be an object")
        case_id = required_text(row.get("id"), f"{label}.id")
        if case_id not in REQUIRED_FAULT_CASES or case_id in seen:
            raise UatConfigError(f"{label}.id is unknown or duplicated")
        if row.get("executionMode") not in {"API", "BROWSER", "FAULT_INJECTION", "API_AND_BROWSER"}:
            raise UatConfigError(f"{label}.executionMode is invalid")
        if row.get("writeRequired") not in {True, False}:
            raise UatConfigError(f"{label}.writeRequired must be boolean")
        if case_id in {"notification-unavailable", "notification-recovery", "network-timeout-replay"}:
            if row.get("recoveryRequired") is not True:
                raise UatConfigError(f"{label}.recoveryRequired must be true")
        seen.add(case_id)


def load_config(path_value: str | os.PathLike[str], repo_root: Path) -> dict[str, Any]:
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
    run_id = required_text(config.get("runId"), "runId")
    if not SAFE_ID.fullmatch(run_id):
        raise UatConfigError("runId must contain 6-64 safe characters")
    database = required_text(config.get("database"), "database")
    lower_database = database.lower()
    if (
        not SAFE_DATABASE.fullmatch(database)
        or lower_database in {"erp", "prod", "production", "erp_prod"}
        or "production" in lower_database
        or "erp_prod" in lower_database
    ):
        raise UatConfigError("database must identify a safe, isolated UAT database")
    if config.get("productionDataCopied") is not False:
        raise UatConfigError("productionDataCopied must be false")
    environment_id = required_text(config.get("expectedEnvironmentId"), "expectedEnvironmentId")
    if not SAFE_ID.fullmatch(environment_id):
        raise UatConfigError("expectedEnvironmentId must contain 6-64 safe characters")
    candidate = str(config.get("candidateCommit") or "")
    if not COMMIT.fullmatch(candidate) or candidate == "0" * 40:
        raise UatConfigError("candidateCommit must be a non-placeholder full commit")
    full_hash(config.get("databaseFingerprintSha256"), "databaseFingerprintSha256")
    full_hash(config.get("migrationBundleSha256"), "migrationBundleSha256")
    full_hash(
        config.get("trustedSignerPublicKeySha256"),
        "trustedSignerPublicKeySha256",
    )
    engine = config.get("databaseEngine")
    if not isinstance(engine, dict) or str(engine.get("vendor") or "").lower() != "mysql":
        raise UatConfigError("databaseEngine.vendor must equal mysql")
    if not str(engine.get("version") or "").startswith("5.7."):
        raise UatConfigError("Release A UAT must include the authorized MySQL 5.7 rehearsal")

    config["baseUrl"] = validate_target_url(config.get("baseUrl"), "baseUrl")
    config["apiBaseUrl"] = validate_target_url(config.get("apiBaseUrl"), "apiBaseUrl")
    web = urlparse(config["baseUrl"])
    api = urlparse(config["apiBaseUrl"])
    if (web.scheme, web.hostname, web.port) != (api.scheme, api.hostname, api.port):
        raise UatConfigError("baseUrl and apiBaseUrl must use the same isolated origin")

    _validate_actors(config, repo_root, web.hostname or "")
    _validate_organizations(config)
    _validate_environment_identity_probe(config)
    _validate_legacy_menu_boundary(config)
    _validate_employees_and_scenarios(config)
    _validate_browser_journeys(config)
    _validate_api_probes(config)
    _validate_fault_cases(config)

    org_ids = ",".join(
        str(config["organizations"][alias]["organizationId"])
        for alias in sorted(config["organizations"])
    )
    expected_env = {
        "ERP_CONTRACT_SIGN_UAT_RUN_ID": run_id,
        "ERP_CONTRACT_SIGN_UAT_DATABASE": database,
        "ERP_CONTRACT_SIGN_UAT_ENVIRONMENT_ID": environment_id,
        "ERP_CONTRACT_SIGN_UAT_DATABASE_FINGERPRINT_SHA256": config[
            "databaseFingerprintSha256"
        ],
        "ERP_CONTRACT_SIGN_UAT_TRUSTED_SIGNER_SHA256": config[
            "trustedSignerPublicKeySha256"
        ],
        "ERP_CONTRACT_SIGN_UAT_ALLOWED_ORG_IDS": org_ids,
        "ERP_CONTRACT_SIGN_UAT_COMMIT": candidate,
        "ERP_UAT_APPROVE_BASE_URL": config["baseUrl"],
    }
    for name, expected in expected_env.items():
        if os.environ.get(name, "") != expected:
            raise UatConfigError(f"{name} must exactly equal the configured value")
    return config


def _lookup_path(config: dict[str, Any], captures: dict[str, Any], dotted: str) -> Any:
    if dotted.startswith("capture."):
        key = dotted.split(".", 1)[1]
        if key not in captures:
            raise UatConfigError(f"template references unavailable capture: {key}")
        return captures[key]
    current: Any = config
    for part in dotted.split("."):
        if not isinstance(current, dict) or part not in current:
            raise UatConfigError(f"template references unknown config value: {dotted}")
        current = current[part]
    if isinstance(current, (dict, list)) or current is None:
        raise UatConfigError(f"template reference must resolve to a scalar: {dotted}")
    return current


def render_templates(value: Any, config: dict[str, Any], captures: dict[str, Any] | None = None) -> Any:
    runtime = captures or {}
    if isinstance(value, dict):
        return {key: render_templates(child, config, runtime) for key, child in value.items()}
    if isinstance(value, list):
        return [render_templates(child, config, runtime) for child in value]
    if not isinstance(value, str):
        return value
    match = TEMPLATE.fullmatch(value)
    if match:
        return _lookup_path(config, runtime, match.group(1))
    return TEMPLATE.sub(lambda item: str(_lookup_path(config, runtime, item.group(1))), value)


def public_config(config: dict[str, Any]) -> dict[str, Any]:
    """Return release metadata without tokens, state paths, names, or employee ids."""
    return {
        "schemaVersion": config["schemaVersion"],
        "releaseId": config["releaseId"],
        "runId": config["runId"],
        "expectedEnvironmentId": config["expectedEnvironmentId"],
        "candidateCommit": config["candidateCommit"],
        "database": config["database"],
        "databaseEngine": config["databaseEngine"],
        "databaseFingerprintSha256": config["databaseFingerprintSha256"],
        "migrationBundleSha256": config["migrationBundleSha256"],
        "trustedSignerPublicKeySha256": config["trustedSignerPublicKeySha256"],
        "baseUrl": config["baseUrl"],
        "apiBaseUrl": config["apiBaseUrl"],
        "organizations": {
            alias: {
                "organizationId": str(org["organizationId"]),
                "deptId": str(org["deptId"]),
                "legalEntities": [
                    {
                        "legalEntityId": str(entity["legalEntityId"]),
                        "candidateContractTypes": sorted(entity["candidateContractTypes"]),
                        "sealId": str(entity["seal"]["sealId"]),
                        "sealSha256": entity["seal"]["sha256"],
                    }
                    for entity in org["legalEntities"]
                ],
            }
            for alias, org in config["organizations"].items()
        },
        "actors": {
            alias: {
                "kind": actor["kind"],
                "isAdministrator": False,
                "organizations": list(actor["organizations"]),
                "expectedRoleKeys": list(actor["expectedRoleKeys"]),
                "expectedPermissions": list(actor["expectedPermissions"]),
                **(
                    {
                        "expectedMenuIds": list(actor["expectedMenuIds"]),
                        "forbiddenMenuIds": list(actor["forbiddenMenuIds"]),
                    }
                    if actor["kind"] == "LEGACY_PACKAGE_ONLY"
                    else {}
                ),
            }
            for alias, actor in config["actors"].items()
        },
        "employees": {
            alias: {
                "organization": employee["organization"],
                "dataClass": employee["dataClass"],
            }
            for alias, employee in config["employees"].items()
        },
        "scenarios": {
            scenario: {
                "organization": fixture["organization"],
                "employee": fixture["employee"],
                "contractType": fixture["contractType"],
                "planVersionId": str(fixture["planVersionId"]),
                "planVersionSha256": fixture["planVersionSha256"],
                "templateHashes": list(fixture["templateHashes"]),
                "approvalRef": fixture["approvalRef"],
            }
            for scenario, fixture in config["scenarios"].items()
        },
        "legacyMenuBoundary": dict(config["legacyMenuBoundary"]),
    }
