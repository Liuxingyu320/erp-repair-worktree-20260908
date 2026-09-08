#!/usr/bin/env python3
"""Precisely remove the isolated desktop release E2E fixture."""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from typing import Any

from desktop_release_e2e_common import (
    ACTORS,
    APPROVAL_INSTANCE,
    APPROVAL_RULE,
    APPROVAL_TASK,
    APPROVAL_VERSION,
    CONTRACT_A,
    CONTRACT_B,
    CREDENTIAL_PATH,
    EVIDENCE_PATH,
    HIGH_ID_COLUMNS,
    JOB_ID,
    MANIFEST_PATH,
    PREFIX,
    PRODUCT_A,
    PRODUCT_B,
    ROLE_EMPLOYEE,
    ROLE_MANAGER,
    ROLE_PRIVILEGED,
    RUN_ID,
    STOCK_A,
    STOCK_B,
    TRANSFER_APPROVAL,
    TRANSFER_DRAFT,
    USER_EMPLOYEE,
    USER_MANAGER,
    USER_OTHER_A,
    USER_OTHER_B,
    USER_PRIVILEGED,
    DEPT_COMPANY,
    DEPT_STORE_A,
    DEPT_STORE_B,
    DEPT_WAREHOUSE_A,
    QaSafetyError,
    assert_prepared_manifest,
    high_id_hits,
    http_json,
    local_captcha_fields,
    mysql,
    prefix_hits,
    read_owner_only_json,
    require_write_approval,
    response_success,
    sql_quote,
    validate_loopback_base_url,
    verify_database_identity,
)


QA_USERS = (
    USER_EMPLOYEE,
    USER_MANAGER,
    USER_PRIVILEGED,
    USER_OTHER_A,
    USER_OTHER_B,
)
QA_ROLES = (ROLE_EMPLOYEE, ROLE_MANAGER, ROLE_PRIVILEGED)
QA_DEPTS = (
    DEPT_STORE_A,
    DEPT_STORE_B,
    DEPT_WAREHOUSE_A,
    DEPT_COMPANY,
)
QA_TRANSFERS = (TRANSFER_DRAFT, TRANSFER_APPROVAL)
QA_CONTRACTS = (CONTRACT_A, CONTRACT_B)


def csv(values: tuple[int, ...]) -> str:
    return ",".join(str(value) for value in values)


def remove_registered_job(base_url: str, credentials: dict[str, Any]) -> None:
    actor = credentials["actors"]["privilegedAdmin"]
    login, login_public = http_json(
        base_url,
        "POST",
        "/auth/login",
        payload={
            "username": actor["username"],
            "password": actor["password"],
            **local_captcha_fields(base_url),
        },
    )
    if not response_success(login, login_public):
        raise QaSafetyError("cannot authenticate isolated administrator for job cleanup")
    data = login.get("data")
    token = data.get("access_token") if isinstance(data, dict) else None
    if not isinstance(token, str) or not token:
        raise QaSafetyError("job cleanup login did not return an access token")
    try:
        deleted, public = http_json(
            base_url,
            "DELETE",
            f"/schedule/job/{JOB_ID}",
            token=token,
        )
        if not response_success(deleted, public):
            # Direct prepare inserts the paused row before it is registered in
            # Quartz. In that state an absent API row is safe to clean in SQL.
            exists = mysql(
                f"SELECT COUNT(*) FROM sys_job WHERE job_id={JOB_ID};"
            ).strip()
            if exists != "1":
                return
            raise QaSafetyError("paused QA job could not be unregistered")
    finally:
        try:
            http_json(base_url, "DELETE", "/auth/logout", token=token)
        except Exception:
            pass


def cleanup_sql() -> str:
    users = csv(QA_USERS)
    roles = csv(QA_ROLES)
    transfers = csv(QA_TRANSFERS)
    contracts = csv(QA_CONTRACTS)
    marker_like = sql_quote(PREFIX + "%")
    usernames = ",".join(
        sql_quote(ACTORS[name]["username"])
        for name in ("employee", "manager", "privilegedAdmin")
    )
    return f"""
SET SESSION sql_safe_updates=0;
START TRANSACTION;

DELETE FROM approval_callback_outbox
WHERE instance_id={APPROVAL_INSTANCE}
   OR (business_code='INV_TRANSFER'
       AND business_id IN ({sql_quote(str(TRANSFER_APPROVAL))},
                           {sql_quote(str(TRANSFER_DRAFT))}));
DELETE c FROM approval_task_candidate c
JOIN approval_instance i ON i.instance_id=c.instance_id
WHERE i.instance_id={APPROVAL_INSTANCE}
   OR (i.business_code='INV_TRANSFER'
       AND i.business_id IN ({sql_quote(str(TRANSFER_APPROVAL))},
                             {sql_quote(str(TRANSFER_DRAFT))}));
DELETE a FROM approval_action_log a
JOIN approval_instance i ON i.instance_id=a.instance_id
WHERE i.instance_id={APPROVAL_INSTANCE}
   OR (i.business_code='INV_TRANSFER'
       AND i.business_id IN ({sql_quote(str(TRANSFER_APPROVAL))},
                             {sql_quote(str(TRANSFER_DRAFT))}));
DELETE t FROM approval_task t
JOIN approval_instance i ON i.instance_id=t.instance_id
WHERE i.instance_id={APPROVAL_INSTANCE}
   OR (i.business_code='INV_TRANSFER'
       AND i.business_id IN ({sql_quote(str(TRANSFER_APPROVAL))},
                             {sql_quote(str(TRANSFER_DRAFT))}));
DELETE FROM approval_instance
WHERE instance_id={APPROVAL_INSTANCE}
   OR (business_code='INV_TRANSFER'
       AND business_id IN ({sql_quote(str(TRANSFER_APPROVAL))},
                           {sql_quote(str(TRANSFER_DRAFT))}));
DELETE FROM approval_rule_condition WHERE version_id={APPROVAL_VERSION};
DELETE FROM approval_version_node WHERE version_id={APPROVAL_VERSION};
DELETE FROM approval_rule_version
WHERE version_id={APPROVAL_VERSION} OR rule_id={APPROVAL_RULE};
DELETE FROM approval_rule WHERE rule_id={APPROVAL_RULE};

DELETE FROM inv_transfer_status_log WHERE transfer_id IN ({transfers});
DELETE FROM inv_transfer_approval_start_outbox
WHERE transfer_id IN ({transfers});
DELETE FROM inv_transfer_detail WHERE transfer_id IN ({transfers});
DELETE FROM inv_transfer_order WHERE transfer_id IN ({transfers});

DELETE FROM oa_labor_contract_event WHERE contract_id IN ({contracts});
DELETE FROM oa_labor_contract WHERE contract_id IN ({contracts});

DELETE FROM sys_job_log
WHERE job_name={sql_quote(PREFIX + 'NOOP_JOB')}
  AND job_group={sql_quote(PREFIX + 'GROUP')};
DELETE FROM sys_job
WHERE job_id={JOB_ID}
  AND job_name={sql_quote(PREFIX + 'NOOP_JOB')};

DELETE FROM inv_stock WHERE stock_id IN ({STOCK_A},{STOCK_B});
DELETE FROM inv_product WHERE product_id IN ({PRODUCT_A},{PRODUCT_B});

DELETE FROM sys_user_pii_access_audit
WHERE viewer_user_id IN ({users}) OR target_user_id IN ({users});
DELETE FROM sys_user_notification WHERE user_id IN ({users});
DELETE FROM sys_user_device_token WHERE user_id IN ({users});
DELETE FROM sys_security_session_outbox WHERE user_id IN ({users});
DELETE FROM sys_oper_log
WHERE oper_name IN ({usernames}) OR oper_name LIKE {marker_like};
DELETE FROM sys_logininfor WHERE user_name IN ({usernames});

DELETE FROM sys_user_shop WHERE user_id IN ({users});
DELETE FROM sys_user_role
WHERE user_id IN ({users}) OR role_id IN ({roles});
DELETE FROM sys_role_menu WHERE role_id IN ({roles});
DELETE FROM sys_role_dept WHERE role_id IN ({roles});
DELETE FROM sys_user_profile WHERE user_id IN ({users});
DELETE FROM sys_user WHERE user_id IN ({users});
DELETE FROM sys_role WHERE role_id IN ({roles});
DELETE FROM sys_dept
WHERE dept_id IN ({DEPT_STORE_A},{DEPT_STORE_B},{DEPT_WAREHOUSE_A});
DELETE FROM sys_dept WHERE dept_id={DEPT_COMPANY};

COMMIT;
"""


def restore_auto_increment(manifest: dict[str, Any]) -> dict[str, str]:
    restored: dict[str, str] = {}
    originals = manifest.get("autoIncrementBefore")
    if not isinstance(originals, dict):
        raise QaSafetyError("manifest does not contain auto-increment provenance")
    for table, original in originals.items():
        column = HIGH_ID_COLUMNS.get(table)
        if column is None or original is None:
            restored[table] = "not-applicable"
            continue
        maximum_text = mysql(
            f"SELECT COALESCE(MAX(`{column}`),0) FROM `{table}`;"
        ).strip()
        maximum = int(maximum_text or "0")
        original_value = int(original)
        if maximum >= original_value:
            restored[table] = "preserved-newer-data"
            continue
        mysql(f"ALTER TABLE `{table}` AUTO_INCREMENT={original_value};")
        restored[table] = "restored"
    return restored


def unlink_exact(path: Path) -> None:
    allowed = {CREDENTIAL_PATH, MANIFEST_PATH}
    if path not in allowed:
        raise QaSafetyError("refusing to remove an undeclared temporary path")
    if path.exists():
        if path.is_symlink() or not path.is_file():
            raise QaSafetyError(f"refusing to remove non-regular path: {path}")
        path.unlink()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--confirm", required=True)
    args = parser.parse_args()
    try:
        if args.confirm != RUN_ID:
            raise QaSafetyError("cleanup confirmation does not match the QA run")
        require_write_approval()
        verify_database_identity()
        manifest = assert_prepared_manifest()
        credentials = read_owner_only_json(CREDENTIAL_PATH)
        if credentials.get("runId") != RUN_ID:
            raise QaSafetyError("credentials do not belong to this QA run")
        base_url = validate_loopback_base_url(manifest.get("baseUrl"))
        remove_registered_job(base_url, credentials)
        mysql(cleanup_sql())
        restored = restore_auto_increment(manifest)

        # Async audit/login writers may finish just after logout. Remove only
        # the exact QA actors one final time, then verify the complete fence.
        time.sleep(1)
        usernames = ",".join(
            sql_quote(ACTORS[name]["username"])
            for name in ("employee", "manager", "privilegedAdmin")
        )
        mysql(
            "DELETE FROM sys_oper_log "
            f"WHERE oper_name IN ({usernames}) "
            f"OR oper_name LIKE {sql_quote(PREFIX + '%')};"
            "DELETE FROM sys_logininfor "
            f"WHERE user_name IN ({usernames});"
        )
        remaining_prefix = prefix_hits()
        remaining_ids = high_id_hits()
        if remaining_prefix or remaining_ids:
            raise QaSafetyError(
                "cleanup verification failed; owner-only artifacts were retained"
            )
        unlink_exact(CREDENTIAL_PATH)
        unlink_exact(MANIFEST_PATH)
        print("[PASS] isolated QA fixture removed")
        print(
            "[INFO] secret-free evidence retained: "
            f"{EVIDENCE_PATH if EVIDENCE_PATH.exists() else 'none'}"
        )
        print(
            "[INFO] auto-increment restoration: "
            + ",".join(f"{key}={value}" for key, value in sorted(restored.items()))
        )
        return 0
    except (QaSafetyError, OSError, ValueError, KeyError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
