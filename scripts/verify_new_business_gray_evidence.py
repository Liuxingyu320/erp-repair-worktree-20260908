#!/usr/bin/env python3
"""Verify staged rollout, alert loading, reconciliation and rollback evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Any


RELEASE_ID = "new-business-20260714"
RULE_GROUP_NAME = "erp-active-business"
EXPECTED_RULES_SHA256 = "43a6a4bc4e6aa036ff0a22bd651b962e3d162057370954316948799bb602dad6"
EXPECTED_RECONCILIATION_SQL_SHA256 = (
    "2ee23f4352dabf260f58efed825dce6338932580a55ba33e1d6b37333b8a8468"
)
EXPECTED_ALERT_RULES = {
    "ErpNewBusinessCustomerScopeDeniedSpike": {
        "query": "sum(increase(erp_inventory_customer_card_scope_denied_total[15m])) > 10",
        "for": "5m",
        "severity": "warning",
    },
    "ErpNewBusinessCustomerOptimisticConflictSpike": {
        "query": 'sum(increase(erp_inventory_customer_card_write_total{outcome="optimistic_conflict"}[15m])) > 10',
        "for": "5m",
        "severity": "warning",
    },
    "ErpNewBusinessHealthReminderFailure": {
        "query": 'sum(increase(erp_hr_health_certificate_reminder_total{outcome="failure"}[15m])) > 0',
        "for": "0m",
        "severity": "warning",
    },
    "ErpNewBusinessReconciliationFailed": {
        "query": "max(erp_new_business_reconciliation_success) == 0",
        "for": "0m",
        "severity": "critical",
    },
    "ErpNewBusinessReconciliationStale": {
        "query": "absent(erp_new_business_reconciliation_last_run_timestamp_seconds) or (time() - max(erp_new_business_reconciliation_last_run_timestamp_seconds) > 93600)",
        "for": "10m",
        "severity": "critical",
    },
    "ErpNewBusinessTransferDiscrepancyOverdue24h": {
        "query": 'max(erp_new_business_reconciliation_issue_total{issue_code="TRANSFER_DISCREPANCY_OVERDUE_24H"}) > 0',
        "for": "0m",
        "severity": "critical",
    },
}
EXPECTED_ALERTS = tuple(EXPECTED_ALERT_RULES)
EXPECTED_RECONCILIATION_ISSUES = frozenset(
    {
        "CUSTOMER_SERVICE_AUDIT_SCOPE_MISMATCH",
        "CUSTOMER_SERVICE_PROFILE_CARDINALITY",
        "CUSTOMER_SERVICE_RECORD_AUDIT_MISSING",
        "CUSTOMER_SERVICE_RECORD_SCOPE_MISMATCH",
        "HEALTH_CERTIFICATE_CURRENT_DUPLICATE",
        "HEALTH_CERTIFICATE_CURRENT_INVALID",
        "HEALTH_CERTIFICATE_PENDING_STALE",
        "STORE_RETURN_DIRECTION_OR_APPROVAL_INVALID",
        "STORE_RETURN_SHIPMENT_QUANTITY_CONSERVATION",
        "TRANSFER_DISCREPANCY_DETAIL_INVALID",
        "TRANSFER_DISCREPANCY_MASTER_DETAIL_MISMATCH",
        "TRANSFER_DISCREPANCY_OVERDUE_24H",
    }
)
FEATURE_ORDER = [
    "feature.hr.health-certificate.enabled",
    "feature.inventory.transfer-discrepancy.enabled",
    "feature.inventory.store-return.enabled",
    "feature.inventory.customer-service-card.enabled",
]
SHA256 = re.compile(r"^[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
PLACEHOLDER = re.compile(r"^(?:todo|tbd|pending|unknown|待定|未填写|示例)$", re.I)
SECRET_KEY = re.compile(
    r"(?:password|passwd|secret|access.?key|private.?key|bearer|token|"
    r"authorization|cookie|credential|api.?key|session(?:id|key|token)?)$",
    re.I,
)
ALERT_LINE = re.compile(
    r"^ {6}- alert: ([A-Za-z][A-Za-z0-9_]*)$", re.MULTILINE
)
GROUP_LINE = re.compile(
    r"^  - name: ([A-Za-z][A-Za-z0-9_-]*)$", re.MULTILINE
)


class GrayEvidenceError(ValueError):
    pass


def _exact_object(value: Any, fields: set[str], label: str) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) != fields:
        raise GrayEvidenceError(f"{label} fields are incomplete or unexpected")
    return value


def _text(value: Any, label: str) -> str:
    if not isinstance(value, str):
        raise GrayEvidenceError(f"{label} must be text")
    result = value.strip()
    if not result or PLACEHOLDER.fullmatch(result):
        raise GrayEvidenceError(f"{label} is required and cannot be a placeholder")
    return result


def _time(value: Any, label: str) -> datetime:
    result = _text(value, label)
    try:
        parsed = datetime.fromisoformat(result.replace("Z", "+00:00"))
    except ValueError as exc:
        raise GrayEvidenceError(f"{label} must be ISO-8601") from exc
    if parsed.tzinfo is None:
        raise GrayEvidenceError(f"{label} must include a timezone")
    return parsed


def _reject_secrets(value: Any, path: str = "evidence") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if SECRET_KEY.search(str(key)):
                raise GrayEvidenceError(f"{path}.{key} is a forbidden credential field")
            _reject_secrets(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            _reject_secrets(child, f"{path}[{index}]")


def _sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _artifact(value: Any, label: str, evidence_dir: Path, root: Path) -> Path:
    value = _exact_object(value, {"path", "sha256", "status"}, label)
    if value.get("status") != "passed":
        raise GrayEvidenceError(f"{label} must be a passed artifact")
    candidate = Path(_text(value.get("path"), f"{label}.path")).expanduser()
    if not candidate.is_absolute():
        candidate = evidence_dir / candidate
    if candidate.is_symlink():
        raise GrayEvidenceError(f"{label}.path must not be a symlink")
    path = candidate.resolve()
    if not path.is_file():
        raise GrayEvidenceError(f"{label}.path must be a regular file")
    try:
        path.relative_to(root.resolve())
    except ValueError:
        raise GrayEvidenceError(f"{label}.path must be under the workspace")
    declared = str(value.get("sha256") or "")
    if not SHA256.fullmatch(declared) or _sha(path) != declared:
        raise GrayEvidenceError(f"{label}.sha256 does not match its artifact")
    return path


def _reconciliation(
    path: Path, label: str, candidate_sql_sha256: str
) -> datetime:
    try:
        result = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise GrayEvidenceError(f"{label} is not valid reconciliation JSON") from exc
    result = _exact_object(
        result,
        {
            "schemaVersion",
            "releaseId",
            "kind",
            "reconciliationSqlSha256",
            "completedAt",
            "status",
            "issueCount",
            "issues",
        },
        label,
    )
    _reject_secrets(result, label)
    issues = result.get("issues")
    if (
        type(result.get("schemaVersion")) is not int
        or result.get("schemaVersion") != 1
        or result.get("releaseId") != RELEASE_ID
        or result.get("kind") != "daily-reconciliation"
        or result.get("reconciliationSqlSha256") != candidate_sql_sha256
        or result.get("status") != "passed"
        or type(result.get("issueCount")) is not int
        or result.get("issueCount") != 0
        or not isinstance(issues, dict)
        or set(issues) != EXPECTED_RECONCILIATION_ISSUES
        or any(
            isinstance(count, bool) or not isinstance(count, int) or count != 0
            for count in issues.values()
        )
    ):
        raise GrayEvidenceError(
            f"{label} must use the pinned SQL and report all expected issues as zero"
        )
    return _time(result.get("completedAt"), f"{label}.completedAt")


def _normalized_query(value: str) -> str:
    return value.strip()


def _duration_seconds(value: str) -> int:
    match = re.fullmatch(r"([0-9]+)([smhd])", value)
    if match is None:
        raise GrayEvidenceError(f"unsupported alert duration: {value!r}")
    multipliers = {"s": 1, "m": 60, "h": 3600, "d": 86400}
    return int(match.group(1)) * multipliers[match.group(2)]


def _candidate_alerts(path: Path) -> dict[str, dict[str, str]]:
    try:
        source = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise GrayEvidenceError(f"cannot read candidate alert rules: {path}") from exc
    if _sha(path) != EXPECTED_RULES_SHA256:
        raise GrayEvidenceError(
            "candidate alert rules SHA differs from the pinned release contract"
        )
    if GROUP_LINE.findall(source) != [RULE_GROUP_NAME]:
        raise GrayEvidenceError(
            f"candidate alert rules must contain only the {RULE_GROUP_NAME} group"
        )
    intervals = re.findall(r"^ {4}interval: ([^\s]+)$", source, re.MULTILINE)
    if intervals != ["1m"]:
        raise GrayEvidenceError("candidate alert group interval must be exactly 1m")
    names = ALERT_LINE.findall(source)
    if tuple(names) != EXPECTED_ALERTS:
        raise GrayEvidenceError(
            "candidate alert rules must contain the 6 ordered active alerts"
        )
    records: dict[str, dict[str, str]] = {name: {} for name in names}
    current: str | None = None
    in_labels = False
    for line in source.splitlines():
        alert_match = re.fullmatch(
            r" {6}- alert: ([A-Za-z][A-Za-z0-9_]*)", line
        )
        if alert_match:
            current = alert_match.group(1)
            in_labels = False
            continue
        if current is None:
            continue
        field: tuple[str, str] | None = None
        expression_match = re.fullmatch(r" {8}expr: (.+)", line)
        duration_match = re.fullmatch(r" {8}for: ([^\s]+)", line)
        if expression_match:
            field = ("query", _normalized_query(expression_match.group(1)))
            in_labels = False
        elif duration_match:
            field = ("for", duration_match.group(1))
            in_labels = False
        elif line == "        labels:":
            in_labels = True
        elif re.match(r"^ {8}\S", line):
            in_labels = False
        elif in_labels:
            label_match = re.fullmatch(r" {10}(severity): (.+)", line)
            if label_match:
                field = (label_match.group(1), label_match.group(2).strip())
        if field is not None:
            key, value = field
            if key in records[current]:
                raise GrayEvidenceError(
                    f"candidate alert {current} contains duplicate {key}"
                )
            records[current][key] = value
    for name, expected in EXPECTED_ALERT_RULES.items():
        if records.get(name) != expected:
            raise GrayEvidenceError(
                f"candidate alert {name} differs from the pinned release contract"
            )
    return records


def _prometheus_rules(path: Path, candidate_rules: Path, label: str) -> None:
    try:
        response = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise GrayEvidenceError(
            f"{label} must be Prometheus /api/v1/rules?type=alert JSON"
        ) from exc
    if not isinstance(response, dict):
        raise GrayEvidenceError(f"{label} must be a Prometheus API JSON object")
    _reject_secrets(response, label)
    if set(response) != {"status", "data"}:
        raise GrayEvidenceError(f"{label} must be a privacy-minimized API projection")
    if response.get("status") != "success":
        raise GrayEvidenceError(f"{label}.status must be success")
    data = response.get("data")
    if not isinstance(data, dict) or set(data) != {"groups"}:
        raise GrayEvidenceError(f"{label}.data must contain only groups")
    groups = data.get("groups") if isinstance(data, dict) else None
    if not isinstance(groups, list):
        raise GrayEvidenceError(f"{label}.data.groups must be a list")
    if (
        len(groups) != 1
        or not isinstance(groups[0], dict)
        or groups[0].get("name") != RULE_GROUP_NAME
    ):
        raise GrayEvidenceError(
            f"{label} must contain only one {RULE_GROUP_NAME} group"
        )
    group = groups[0]
    if set(group) != {"name", "interval", "rules"}:
        raise GrayEvidenceError(f"{label} target group fields are not minimized")
    if type(group.get("interval")) is not int or group.get("interval") != 60:
        raise GrayEvidenceError(f"{label} target group interval must be 60 seconds")
    rules = group.get("rules")
    if not isinstance(rules, list):
        raise GrayEvidenceError(f"{label} target group rules must be a list")
    if any(not isinstance(rule, dict) for rule in rules):
        raise GrayEvidenceError(f"{label} target group contains an invalid rule")
    names = [rule.get("name") for rule in rules]
    if any(not isinstance(name, str) or not name for name in names):
        raise GrayEvidenceError(f"{label} target group contains an unnamed rule")
    if len(names) != len(set(names)):
        raise GrayEvidenceError(f"{label} target group contains duplicate alerts")
    expected = _candidate_alerts(candidate_rules)
    if tuple(names) != EXPECTED_ALERTS:
        actual = set(names)
        expected_names = set(expected)
        missing = sorted(expected_names - actual)
        extra = sorted(actual - expected_names)
        raise GrayEvidenceError(
            f"{label} target group alert set differs or order is wrong: "
            f"missing={missing} extra={extra}"
        )
    for rule in rules:
        name = str(rule["name"])
        if rule.get("type") != "alerting":
            raise GrayEvidenceError(f"{label} alert {name} must be type=alerting")
        if rule.get("health") != "ok":
            raise GrayEvidenceError(f"{label} alert {name} must have health=ok")
        query = rule.get("query")
        if not isinstance(query, str) or not query.strip():
            raise GrayEvidenceError(f"{label} alert {name} must include query")
        if _normalized_query(query) != expected[name]["query"]:
            raise GrayEvidenceError(
                f"{label} alert {name} query differs from the candidate rule"
            )
        if rule.get("state") != "inactive":
            raise GrayEvidenceError(f"{label} alert {name} must be inactive")
        if (
            type(rule.get("duration")) is not int
            or rule.get("duration") != _duration_seconds(expected[name]["for"])
        ):
            raise GrayEvidenceError(f"{label} alert {name} duration differs")
        expected_labels = {"severity": expected[name]["severity"]}
        if rule.get("labels") != expected_labels:
            raise GrayEvidenceError(f"{label} alert {name} labels differ")
        if set(rule) != {
            "name",
            "type",
            "health",
            "state",
            "query",
            "duration",
            "labels",
        }:
            raise GrayEvidenceError(f"{label} alert {name} fields are not minimized")


def verify(path: Path, root: Path, expected_commit: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise GrayEvidenceError(f"cannot read gray evidence: {path}") from exc
    if not isinstance(value, dict):
        raise GrayEvidenceError("gray evidence must be a JSON object")
    _reject_secrets(value)
    value = _exact_object(
        value,
        {
            "schemaVersion",
            "releaseId",
            "candidateCommit",
            "runId",
            "completedAt",
            "phases",
            "featureChanges",
            "monitoring",
            "reconciliation",
            "rollbackDrill",
            "signoff",
        },
        "evidence",
    )
    if (
        type(value.get("schemaVersion")) is not int
        or value.get("schemaVersion") != 1
        or value.get("releaseId") != RELEASE_ID
    ):
        raise GrayEvidenceError("gray evidence schema/releaseId is invalid")
    candidate = value.get("candidateCommit")
    if (
        not isinstance(candidate, str)
        or not COMMIT.fullmatch(candidate)
        or candidate != expected_commit
    ):
        raise GrayEvidenceError("candidateCommit does not match the release candidate")
    _text(value.get("runId"), "runId")
    completed_at = _time(value.get("completedAt"), "completedAt")

    phases = value.get("phases")
    expected_phases = [
        ("single-store-single-warehouse", ["storeA"], ["warehouseA"]),
        (
            "second-store-isolation",
            ["storeA", "storeB"],
            ["warehouseA", "warehouseB"],
        ),
    ]
    if not isinstance(phases, list) or len(phases) != 2:
        raise GrayEvidenceError("phases must contain the two ordered rollout phases")
    phase_times: list[datetime] = []
    for phase, expected in zip(phases, expected_phases):
        name, stores, warehouses = expected
        phase = _exact_object(
            phase,
            {"name", "stores", "warehouses", "status", "reviewer", "completedAt"},
            f"phase {name}",
        )
        if (
            phase.get("name") != name
            or phase.get("stores") != stores
            or phase.get("warehouses") != warehouses
            or phase.get("status") != "passed"
        ):
            raise GrayEvidenceError(f"rollout phase {name} is incomplete or out of order")
        phase_times.append(
            _time(phase.get("completedAt"), f"phase {name}.completedAt")
        )
        _text(phase.get("reviewer"), f"phase {name}.reviewer")
    if phase_times[0] >= phase_times[1]:
        raise GrayEvidenceError("rollout phases must have increasing completion times")

    changes = value.get("featureChanges")
    if not isinstance(changes, list) or len(changes) != len(FEATURE_ORDER):
        raise GrayEvidenceError("featureChanges must follow the dependency-safe four-flag order")
    for index, change in enumerate(changes):
        _exact_object(
            change,
            {
                "key",
                "previousValue",
                "newValue",
                "scope",
                "operator",
                "reason",
                "ticket",
                "auditRecordId",
                "changedAt",
            },
            f"featureChanges[{index}]",
        )
    if [item.get("key") for item in changes] != FEATURE_ORDER:
        raise GrayEvidenceError("featureChanges must follow the dependency-safe four-flag order")
    change_times: list[datetime] = []
    audit_ids: list[int] = []
    for index, change in enumerate(changes):
        label = f"featureChanges[{index}]"
        previous = change.get("previousValue")
        new = change.get("newValue")
        if (
            not isinstance(previous, str)
            or not isinstance(new, str)
            or previous.lower() != "false"
            or new.lower() != "true"
        ):
            raise GrayEvidenceError(f"{label} must record false -> true")
        _text(change.get("scope"), f"{label}.scope")
        _text(change.get("operator"), f"{label}.operator")
        _text(change.get("reason"), f"{label}.reason")
        _text(change.get("ticket"), f"{label}.ticket")
        audit_id = change.get("auditRecordId")
        if (
            isinstance(audit_id, bool)
            or not isinstance(audit_id, int)
            or audit_id <= 0
        ):
            raise GrayEvidenceError(f"{label}.auditRecordId must be a positive id")
        audit_ids.append(audit_id)
        change_times.append(_time(change.get("changedAt"), f"{label}.changedAt"))
    if len(set(audit_ids)) != len(audit_ids):
        raise GrayEvidenceError("featureChanges auditRecordId values must be unique")
    if any(left >= right for left, right in zip(change_times, change_times[1:])):
        raise GrayEvidenceError("featureChanges changedAt values must be increasing")

    rules = root / "ops" / "monitoring" / "new-business-alert-rules.yml"
    monitoring = _exact_object(
        value.get("monitoring"),
        {"candidateRulesSha256", "loadedAt", "operator", "loadEvidence"},
        "monitoring",
    )
    if monitoring.get("candidateRulesSha256") != _sha(rules):
        raise GrayEvidenceError("candidate alert-rule SHA does not match the workspace")
    loaded_at = _time(monitoring.get("loadedAt"), "monitoring.loadedAt")
    _text(monitoring.get("operator"), "monitoring.operator")
    load_evidence = _artifact(
        monitoring.get("loadEvidence"),
        "monitoring.loadEvidence",
        path.parent,
        root,
    )
    _prometheus_rules(load_evidence, rules, "monitoring.loadEvidence")

    reconciliation = _exact_object(
        value.get("reconciliation"), {"before", "after"}, "reconciliation"
    )
    reconciliation_sql = root / "scripts" / "new-business-daily-reconciliation.sql"
    if _sha(reconciliation_sql) != EXPECTED_RECONCILIATION_SQL_SHA256:
        raise GrayEvidenceError(
            "candidate reconciliation SQL differs from the pinned release contract"
        )
    before = _artifact(
        reconciliation.get("before"),
        "reconciliation.before",
        path.parent,
        root,
    )
    after = _artifact(
        reconciliation.get("after"),
        "reconciliation.after",
        path.parent,
        root,
    )
    if before == after or before.samefile(after):
        raise GrayEvidenceError(
            "reconciliation.before and reconciliation.after must be distinct artifacts"
        )
    before_completed = _reconciliation(
        before,
        "reconciliation.before",
        EXPECTED_RECONCILIATION_SQL_SHA256,
    )
    after_completed = _reconciliation(
        after,
        "reconciliation.after",
        EXPECTED_RECONCILIATION_SQL_SHA256,
    )
    if before_completed >= after_completed:
        raise GrayEvidenceError(
            "reconciliation.after must be completed after reconciliation.before"
        )

    rollback = _exact_object(
        value.get("rollbackDrill"),
        {
            "newEntryDisabled",
            "inFlightCompleted",
            "reconciliationStayedClean",
            "evidence",
            "reviewer",
            "completedAt",
        },
        "rollbackDrill",
    )
    if any(
        rollback.get(field) is not True
        for field in (
            "newEntryDisabled",
            "inFlightCompleted",
            "reconciliationStayedClean",
        )
    ):
        raise GrayEvidenceError(
            "rollback drill must prove entry closure, in-flight completion and clean reconciliation"
        )
    _artifact(
        rollback.get("evidence"), "rollbackDrill.evidence", path.parent, root
    )
    _text(rollback.get("reviewer"), "rollbackDrill.reviewer")
    rollback_completed = _time(
        rollback.get("completedAt"), "rollbackDrill.completedAt"
    )

    signoff = _exact_object(
        value.get("signoff"),
        {
            "businessOwner",
            "operationsOwner",
            "releaseOwner",
            "approvedAt",
            "decision",
        },
        "signoff",
    )
    if signoff.get("decision") != "approved":
        raise GrayEvidenceError("gray signoff decision must be approved")
    owners = [
        _text(signoff.get(role), f"signoff.{role}")
        for role in ("businessOwner", "operationsOwner", "releaseOwner")
    ]
    if len(set(owners)) < 2:
        raise GrayEvidenceError("gray signoff requires at least two distinct people")
    approved_at = _time(signoff.get("approvedAt"), "signoff.approvedAt")
    if loaded_at > change_times[0] or before_completed > change_times[0]:
        raise GrayEvidenceError(
            "monitoring and before reconciliation must precede feature rollout"
        )
    if not (
        change_times[-1]
        <= phase_times[0]
        < phase_times[1]
        <= after_completed
        <= rollback_completed
        <= approved_at
        <= completed_at
    ):
        raise GrayEvidenceError("gray evidence completion timeline is inconsistent")
    return {
        "releaseId": RELEASE_ID,
        "candidateCommit": candidate,
        "phaseCount": 2,
        "featureChangeCount": len(FEATURE_ORDER),
        "status": "passed",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    parser.add_argument("--candidate-commit")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    commit = args.candidate_commit or subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"], text=True
    ).strip()
    try:
        result = verify(Path(args.evidence).resolve(), root, commit)
    except (OSError, GrayEvidenceError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print("[PASS] staged gray rollout evidence is complete")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
