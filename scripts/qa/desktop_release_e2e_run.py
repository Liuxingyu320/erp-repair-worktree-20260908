#!/usr/bin/env python3
"""Run secret-free API E2E evidence against the isolated QA fixture."""

from __future__ import annotations

import json
import sys
import time
from datetime import datetime, timezone
from typing import Any, Callable

from desktop_release_e2e_common import (
    ACTORS,
    APPROVAL_INSTANCE,
    APPROVAL_TASK,
    CONTRACT_A,
    CONTRACT_B,
    CREDENTIAL_PATH,
    DEPT_STORE_A,
    DEPT_STORE_B,
    DEPT_WAREHOUSE_A,
    EVIDENCE_PATH,
    JOB_ID,
    PREFIX,
    PRODUCT_A,
    PRODUCT_B,
    RUN_ID,
    STOCK_A,
    STOCK_B,
    TRANSFER_APPROVAL,
    TRANSFER_DRAFT,
    USER_OTHER_A,
    USER_OTHER_B,
    QaSafetyError,
    assert_prepared_manifest,
    extract_data,
    http_json,
    local_captcha_fields,
    mysql,
    owner_only_json,
    read_owner_only_json,
    require_success,
    require_write_approval,
    response_denied,
    rows_payload,
    table_exists,
    validate_loopback_base_url,
    verify_database_identity,
    verify_local_services,
)


class FlowBlocked(RuntimeError):
    pass


class Runner:
    def __init__(self, base_url: str, credentials: dict[str, Any]):
        self.base_url = base_url
        self.credentials = credentials
        self.tokens: dict[str, str] = {}
        self.results: list[dict[str, Any]] = []
        self.permission_matrix: dict[str, Any] = {}

    def request(
        self,
        flow: dict[str, Any],
        actor: str,
        method: str,
        path: str,
        *,
        dept_id: int | None = None,
        payload: Any = None,
        idempotency_key: str | None = None,
        expect: str = "success",
    ) -> dict[str, Any]:
        parsed, public = http_json(
            self.base_url,
            method,
            path,
            token=self.tokens.get(actor),
            dept_id=dept_id,
            payload=payload,
            idempotency_key=idempotency_key,
        )
        step = {
            "actor": actor,
            "method": method.upper(),
            "path": path.split("?", 1)[0],
            "expected": expect,
            **public,
        }
        if expect == "success":
            try:
                require_success(path, parsed, public)
                step["status"] = "passed"
            except AssertionError:
                step["status"] = "failed"
                flow["steps"].append(step)
                raise
        elif expect == "denied":
            if not response_denied(parsed, public):
                step["status"] = "failed"
                flow["steps"].append(step)
                raise AssertionError(
                    f"{path} did not enforce the declared authorization denial"
                )
            step["status"] = "passed"
        elif expect == "failure":
            if 200 <= public["httpStatus"] < 300 and str(
                parsed.get("code", "200")
            ) == "200":
                step["status"] = "failed"
                flow["steps"].append(step)
                raise AssertionError(f"{path} unexpectedly succeeded")
            step["status"] = "passed"
        else:
            raise ValueError(f"unsupported expectation: {expect}")
        flow["steps"].append(step)
        return parsed

    def login_all(self, flow: dict[str, Any]) -> None:
        for actor in ("employee", "manager", "privilegedAdmin"):
            record = self.credentials["actors"][actor]
            parsed, public = http_json(
                self.base_url,
                "POST",
                "/auth/login",
                payload={
                    "username": record["username"],
                    "password": record["password"],
                    **local_captcha_fields(self.base_url),
                },
            )
            require_success(f"login:{actor}", parsed, public)
            data = extract_data(parsed)
            token = data.get("access_token") if isinstance(data, dict) else None
            if not token or not isinstance(token, str):
                raise AssertionError(f"login:{actor} did not return an access token")
            self.tokens[actor] = token
            flow["steps"].append(
                {
                    "actor": actor,
                    "method": "POST",
                    "path": "/auth/login",
                    "expected": "success",
                    "status": "passed",
                    **public,
                }
            )

    def logout_all(self) -> None:
        for token in tuple(self.tokens.values()):
            try:
                http_json(
                    self.base_url,
                    "DELETE",
                    "/auth/logout",
                    token=token,
                )
            except Exception:
                pass
        self.tokens.clear()

    def run_flow(
        self, flow_id: str, name: str, function: Callable[[dict[str, Any]], None]
    ) -> None:
        flow: dict[str, Any] = {
            "id": flow_id,
            "name": name,
            "status": "passed",
            "steps": [],
        }
        try:
            function(flow)
        except FlowBlocked as exc:
            flow["status"] = "blocked"
            flow["reason"] = str(exc)[:400]
        except Exception as exc:
            flow["status"] = "failed"
            flow["reason"] = str(exc)[:400]
        self.results.append(flow)

    @staticmethod
    def flatten_router_paths(value: Any) -> set[str]:
        paths: set[str] = set()
        if isinstance(value, list):
            for item in value:
                paths.update(Runner.flatten_router_paths(item))
        elif isinstance(value, dict):
            path = value.get("path")
            if isinstance(path, str):
                paths.add(path.strip("/").lower())
            paths.update(Runner.flatten_router_paths(value.get("children")))
        return paths

    @staticmethod
    def id_set(rows: list[Any], *keys: str) -> set[int]:
        result: set[int] = set()
        for row in rows:
            if not isinstance(row, dict):
                continue
            for key in keys:
                value = row.get(key)
                if value is None:
                    continue
                try:
                    result.add(int(value))
                except (TypeError, ValueError):
                    pass
                break
        return result

    def flow_login(self, flow: dict[str, Any]) -> None:
        self.login_all(flow)
        required = {
            "employee": {
                "allow": {
                    "oa:index:view",
                    "inv:stock:list",
                    "inv:transfer:add",
                    "inv:transfer:submit",
                },
                "deny": {
                    "hr:employee:list",
                    "monitor:job:list",
                    "system:user:list",
                },
            },
            "manager": {
                "allow": {
                    "hr:employee:list",
                    "hr:employee:edit",
                    "oa:laborContract:add",
                    "inv:transfer:approve",
                    "inv:report:list",
                },
                "deny": {"monitor:job:list", "system:role:list"},
            },
            "privilegedAdmin": {
                "allow": {
                    "system:user:list",
                    "monitor:job:list",
                    "approval:template:list",
                    "oa:laborContract:send",
                },
                "deny": {"*:*:*"},
            },
        }
        for actor, policy in required.items():
            parsed = self.request(
                flow, actor, "GET", "/system/user/getInfo"
            )
            permissions = {
                str(value) for value in parsed.get("permissions", []) if value
            }
            missing = sorted(policy["allow"] - permissions)
            leaked = sorted(policy["deny"] & permissions)
            if missing or leaked:
                raise AssertionError(
                    f"{actor} permission mismatch: missing={missing}, leaked={leaked}"
                )
            self.permission_matrix[actor] = {
                "requiredAllowed": sorted(policy["allow"]),
                "requiredDenied": sorted(policy["deny"]),
                "permissionCount": len(permissions),
                "status": "passed",
            }

    def flow_home(self, flow: dict[str, Any]) -> None:
        expected = {
            "employee": {
                "present": {"oa", "inventory"},
                "absent": {"system", "monitor", "hr"},
            },
            "manager": {
                "present": {"oa", "inventory", "hr"},
                "absent": {"monitor"},
            },
            "privilegedAdmin": {
                "present": {"system", "monitor", "oa", "inventory", "hr"},
                "absent": set(),
            },
        }
        for actor, policy in expected.items():
            parsed = self.request(
                flow, actor, "GET", "/system/menu/getRouters"
            )
            paths = self.flatten_router_paths(extract_data(parsed))
            missing = sorted(policy["present"] - paths)
            leaked = sorted(policy["absent"] & paths)
            if missing or leaked:
                raise AssertionError(
                    f"{actor} router mismatch: missing={missing}, leaked={leaked}"
                )
        self.request(
            flow,
            "employee",
            "GET",
            "/system/hr/employee/list?pageNum=1&pageSize=10",
            expect="denied",
        )
        self.request(
            flow,
            "manager",
            "GET",
            "/schedule/job/list?pageNum=1&pageSize=10",
            expect="denied",
        )
        self.request(
            flow,
            "privilegedAdmin",
            "GET",
            "/schedule/job/list?pageNum=1&pageSize=10",
        )

    def transfer_payload(self, version: int) -> dict[str, Any]:
        return {
            "transferId": TRANSFER_DRAFT,
            "fromDeptId": DEPT_WAREHOUSE_A,
            "fromWarehouseId": DEPT_WAREHOUSE_A,
            "toDeptId": DEPT_STORE_A,
            "toWarehouseId": DEPT_STORE_A,
            "transferType": "warehouse",
            "version": version,
            "remark": PREFIX + "TRANSFER_SAVE",
            "details": [
                {
                    "itemType": "product",
                    "itemId": PRODUCT_A,
                    "productId": PRODUCT_A,
                    "quantity": 2,
                    "costPrice": 10,
                    "goodsCondition": "NORMAL",
                    "conditionNote": PREFIX + "DETAIL",
                }
            ],
        }

    def flow_approval(self, flow: dict[str, Any]) -> None:
        saved = self.request(
            flow,
            "employee",
            "POST",
            "/inventory/transfer/save",
            dept_id=DEPT_STORE_A,
            payload=self.transfer_payload(0),
            idempotency_key=PREFIX + "TRANSFER_SAVE_1",
        )
        saved_data = extract_data(saved)
        version = int(saved_data.get("version", 1)) if isinstance(
            saved_data, dict
        ) else 1

        migration_present = table_exists(
            "inv_transfer_approval_start_outbox"
        )
        if migration_present:
            self.request(
                flow,
                "employee",
                "POST",
                "/inventory/transfer/submit",
                dept_id=DEPT_STORE_A,
                payload=self.transfer_payload(version),
                idempotency_key=PREFIX + "TRANSFER_SUBMIT_1",
            )
            deadline = time.monotonic() + 35
            outbox_status = ""
            state = ""
            while time.monotonic() < deadline:
                row = mysql(
                    "SELECT status FROM inv_transfer_approval_start_outbox "
                    f"WHERE transfer_id={TRANSFER_DRAFT} "
                    "ORDER BY outbox_id DESC LIMIT 1;"
                ).strip()
                state = mysql(
                    "SELECT status FROM inv_transfer_order "
                    f"WHERE transfer_id={TRANSFER_DRAFT};"
                ).strip()
                outbox_status = row
                if outbox_status == "SUCCEEDED":
                    break
                if outbox_status == "FAILED":
                    break
                time.sleep(1)
            if outbox_status != "SUCCEEDED" or state not in {
                "submitted",
                "approved",
            }:
                raise AssertionError(
                    "candidate migration did not complete real transfer "
                    f"submission: outbox={outbox_status or 'missing'}, "
                    f"transfer={state or 'missing'}"
                )
        else:
            self.request(
                flow,
                "employee",
                "POST",
                "/inventory/transfer/submit",
                dept_id=DEPT_STORE_A,
                payload=self.transfer_payload(version),
                idempotency_key=PREFIX + "TRANSFER_SUBMIT_1",
                expect="failure",
            )
            state = mysql(
                "SELECT status FROM inv_transfer_order "
                f"WHERE transfer_id={TRANSFER_DRAFT};"
            ).strip()
            if state != "draft":
                raise AssertionError("failed submit did not roll back to draft")

        todo = self.request(
            flow,
            "manager",
            "GET",
            "/approval/todo/list?pageNum=1&pageSize=20",
        )
        if TRANSFER_APPROVAL not in self.id_set(
            rows_payload(todo), "businessId"
        ):
            raise AssertionError("manager approval todo does not contain the QA task")
        self.request(
            flow,
            "employee",
            "POST",
            f"/approval/tasks/{APPROVAL_TASK}/approve",
            payload={
                "requestId": PREFIX + "EMPLOYEE_DENIED",
                "reason": PREFIX + "DENIED",
            },
            expect="failure",
        )
        self.request(
            flow,
            "manager",
            "POST",
            f"/approval/tasks/{APPROVAL_TASK}/approve",
            payload={
                "requestId": PREFIX + "MANAGER_APPROVE",
                "reason": PREFIX + "APPROVED",
            },
        )

        deadline = time.monotonic() + 35
        final_status = ""
        callback_status = ""
        transfer_status = ""
        while time.monotonic() < deadline:
            row = mysql(
                "SELECT status,callback_status FROM approval_instance "
                f"WHERE instance_id={APPROVAL_INSTANCE};"
            ).strip()
            if row:
                final_status, callback_status = row.split("\t", 1)
            transfer_status = mysql(
                "SELECT status FROM inv_transfer_order "
                f"WHERE transfer_id={TRANSFER_APPROVAL};"
            ).strip()
            if (
                final_status == "APPROVED"
                and callback_status == "SUCCEEDED"
                and transfer_status == "approved"
            ):
                break
            time.sleep(1)
        if (
            final_status != "APPROVED"
            or callback_status != "SUCCEEDED"
            or transfer_status != "approved"
        ):
            raise AssertionError(
                "isolated approval callback did not reach the approved result"
            )
        self.request(
            flow,
            "manager",
            "GET",
            f"/approval/instances/{APPROVAL_INSTANCE}",
        )
        if not migration_present:
            raise FlowBlocked(
                "候选审批、审核与本地回调均通过，但真实调拨提交因缺少 "
                "inv_transfer_approval_start_outbox 被正确阻断"
            )

    def flow_inventory(self, flow: dict[str, Any]) -> None:
        employee = self.request(
            flow,
            "employee",
            "GET",
            "/inventory/stock/list?pageNum=1&pageSize=50",
            dept_id=DEPT_STORE_A,
        )
        employee_ids = self.id_set(rows_payload(employee), "stockId")
        if STOCK_A not in employee_ids or STOCK_B in employee_ids:
            raise AssertionError("employee store-A stock isolation failed")
        manager = self.request(
            flow,
            "manager",
            "GET",
            "/inventory/stock/list?pageNum=1&pageSize=50",
            dept_id=DEPT_STORE_A,
        )
        manager_ids = self.id_set(rows_payload(manager), "stockId")
        if STOCK_A not in manager_ids or STOCK_B in manager_ids:
            raise AssertionError("manager store-A stock isolation failed")
        self.request(
            flow,
            "manager",
            "GET",
            "/inventory/stock/list?pageNum=1&pageSize=50",
            dept_id=DEPT_STORE_B,
            expect="failure",
        )
        privileged = self.request(
            flow,
            "privilegedAdmin",
            "GET",
            "/inventory/stock/list?pageNum=1&pageSize=50",
            dept_id=DEPT_STORE_B,
        )
        if STOCK_B not in self.id_set(rows_payload(privileged), "stockId"):
            raise AssertionError("privileged store-B stock scope failed")
        self.request(
            flow,
            "manager",
            "GET",
            f"/inventory/stock/{STOCK_B}",
            dept_id=DEPT_STORE_A,
            expect="failure",
        )

    def contract_payload(self) -> dict[str, Any]:
        from desktop_release_e2e_common import synthetic_id_card

        return {
            "contractId": CONTRACT_A,
            "templateId": 1,
            "employeeId": ACTORS["employee"]["userId"],
            "employeeDeptId": DEPT_STORE_A,
            "shopDeptId": DEPT_STORE_A,
            "contractNo": PREFIX + "CONTRACT_A",
            "contractTitle": PREFIX + "CONTRACT_A_UPDATED",
            "employeeName": PREFIX + "EMP",
            "employeeIdCard": synthetic_id_card(),
            "employeePhone": "19900000001",
            "postName": PREFIX + "POST",
            "socialType": "有社保",
            "contractStartDate": "2026-08-01",
            "contractEndDate": "2027-07-31",
            "baseSalary": 1,
            "totalSalary": 1,
            "status": "draft",
            "signProvider": "internal",
            "remark": PREFIX + "DRAFT_ONLY",
        }

    def flow_contract(self, flow: dict[str, Any]) -> None:
        self.request(
            flow,
            "manager",
            "POST",
            "/oa/laborContract/save",
            dept_id=DEPT_STORE_A,
            payload=self.contract_payload(),
            idempotency_key=PREFIX + "CONTRACT_SAVE_1",
        )
        listed = self.request(
            flow,
            "manager",
            "GET",
            "/oa/laborContract/list?pageNum=1&pageSize=50",
            dept_id=DEPT_STORE_A,
        )
        ids = self.id_set(rows_payload(listed), "contractId")
        if CONTRACT_A not in ids or CONTRACT_B in ids:
            raise AssertionError("manager contract store isolation failed")
        mine = self.request(
            flow,
            "employee",
            "GET",
            "/oa/laborContract/mobile/my?pageNum=1&pageSize=50",
        )
        if CONTRACT_A not in self.id_set(rows_payload(mine), "contractId"):
            raise AssertionError("employee cannot see the isolated own draft")
        self.request(
            flow,
            "manager",
            "GET",
            f"/oa/laborContract/{CONTRACT_B}",
            dept_id=DEPT_STORE_A,
            expect="failure",
        )
        self.request(
            flow,
            "privilegedAdmin",
            "GET",
            f"/oa/laborContract/{CONTRACT_B}",
            dept_id=DEPT_STORE_B,
        )
        # Permission denial proves that this run cannot send to signature/file
        # channels.
        self.request(
            flow,
            "manager",
            "POST",
            "/oa/laborContract/send",
            dept_id=DEPT_STORE_A,
            payload={"contractId": CONTRACT_A},
            expect="denied",
        )

    def flow_employee(self, flow: dict[str, Any]) -> None:
        rows = self.request(
            flow,
            "manager",
            "GET",
            "/system/hr/employee/list?pageNum=1&pageSize=100",
        )
        user_ids = self.id_set(rows_payload(rows), "userId")
        if USER_OTHER_A not in user_ids or USER_OTHER_B in user_ids:
            raise AssertionError("manager employee data scope failed")
        self.request(
            flow,
            "manager",
            "PATCH",
            f"/system/hr/employee/{USER_OTHER_A}",
            payload={"jobGrade": PREFIX + "GRADE_UPDATED"},
        )
        updated = self.request(
            flow,
            "manager",
            "GET",
            f"/system/hr/employee/{USER_OTHER_A}",
        )
        data = extract_data(updated)
        profile = data.get("profile") if isinstance(data, dict) else None
        if not isinstance(profile, dict) or profile.get("jobGrade") != (
            PREFIX + "GRADE_UPDATED"
        ):
            raise AssertionError("employee profile save result is not visible")
        self.request(
            flow,
            "manager",
            "GET",
            f"/system/hr/employee/{USER_OTHER_B}",
            expect="failure",
        )
        self.request(
            flow,
            "employee",
            "GET",
            "/system/hr/employee/list?pageNum=1&pageSize=10",
            expect="denied",
        )
        self.request(
            flow,
            "privilegedAdmin",
            "GET",
            f"/system/hr/employee/{USER_OTHER_B}",
        )

    def flow_task(self, flow: dict[str, Any]) -> None:
        job_name = PREFIX + "NOOP_JOB"
        job_group = PREFIX + "GROUP"
        before_logs = int(
            mysql(
                "SELECT COUNT(*) FROM sys_job_log "
                f"WHERE job_name={_quote(job_name)} "
                f"AND job_group={_quote(job_group)};"
            ).strip()
            or "0"
        )
        if before_logs != 0:
            raise AssertionError("paused QA job already has execution logs")
        payload = {
            "jobId": JOB_ID,
            "jobName": job_name,
            "jobGroup": job_group,
            "invokeTarget": "ryTask.ryNoParams",
            "cronExpression": "0 0 0 1 1 ?",
            "misfirePolicy": "3",
            "concurrent": "1",
            "status": "1",
            "remark": PREFIX + "PAUSED_NO_EXTERNAL_SIDE_EFFECT",
        }
        self.request(
            flow, "privilegedAdmin", "PUT", "/schedule/job", payload=payload
        )
        detail = self.request(
            flow, "privilegedAdmin", "GET", f"/schedule/job/{JOB_ID}"
        )
        data = extract_data(detail)
        if (
            not isinstance(data, dict)
            or str(data.get("status")) != "1"
            or data.get("invokeTarget") != "ryTask.ryNoParams"
        ):
            raise AssertionError("QA no-op job is not paused")
        self.request(
            flow,
            "employee",
            "GET",
            "/schedule/job/list?pageNum=1&pageSize=10",
            expect="denied",
        )
        self.request(
            flow,
            "manager",
            "GET",
            "/schedule/job/list?pageNum=1&pageSize=10",
            expect="denied",
        )
        after_logs = int(
            mysql(
                "SELECT COUNT(*) FROM sys_job_log "
                f"WHERE job_name={_quote(job_name)} "
                f"AND job_group={_quote(job_group)};"
            ).strip()
            or "0"
        )
        if after_logs != 0:
            raise AssertionError("paused QA job unexpectedly executed")

    def flow_report(self, flow: dict[str, Any]) -> None:
        self.request(
            flow,
            "manager",
            "GET",
            "/inventory/report/summary",
            dept_id=DEPT_STORE_A,
        )
        warning_a = self.request(
            flow,
            "manager",
            "GET",
            "/inventory/report/stock-warning?pageNum=1&pageSize=100",
            dept_id=DEPT_STORE_A,
        )
        product_ids = self.id_set(rows_payload(warning_a), "productId", "itemId")
        if PRODUCT_B in product_ids:
            raise AssertionError("store-B product leaked into store-A report")
        self.request(
            flow,
            "manager",
            "GET",
            "/inventory/report/summary",
            dept_id=DEPT_STORE_B,
            expect="failure",
        )
        self.request(
            flow,
            "privilegedAdmin",
            "GET",
            "/inventory/report/summary",
            dept_id=DEPT_STORE_B,
        )
        self.request(
            flow,
            "employee",
            "GET",
            "/inventory/report/summary",
            dept_id=DEPT_STORE_A,
            expect="denied",
        )

    def execute(self) -> dict[str, Any]:
        self.run_flow("E2E-01", "登录", self.flow_login)
        if not self.tokens:
            return self.evidence()
        self.run_flow("E2E-02", "首页与菜单", self.flow_home)
        self.run_flow("E2E-03", "审批", self.flow_approval)
        self.run_flow("E2E-04", "库存", self.flow_inventory)
        self.run_flow("E2E-05", "合同/签约草稿", self.flow_contract)
        self.run_flow("E2E-06", "员工", self.flow_employee)
        self.run_flow("E2E-07", "任务", self.flow_task)
        self.run_flow("E2E-08", "报表", self.flow_report)
        return self.evidence()

    def evidence(self) -> dict[str, Any]:
        counts = {
            status: sum(1 for result in self.results if result["status"] == status)
            for status in ("passed", "blocked", "failed")
        }
        return {
            "schemaVersion": 1,
            "kind": "desktop-release-permission-e2e",
            "runId": RUN_ID,
            "completedAt": datetime.now(timezone.utc).isoformat(),
            "status": (
                "passed"
                if counts["blocked"] == 0 and counts["failed"] == 0
                else "failed"
            ),
            "goNoGo": (
                "GO"
                if counts["blocked"] == 0 and counts["failed"] == 0
                else "NO-GO"
            ),
            "flowCount": len(self.results),
            "passedCount": counts["passed"],
            "blockedCount": counts["blocked"],
            "failedCount": counts["failed"],
            "browserExecuted": False,
            "permissionMatrix": self.permission_matrix,
            "results": self.results,
            "knownLimitations": [
                "The isolated privileged actor is not the hard-coded user_id=1 super administrator.",
                "The transfer outbox migration is validated only in the local candidate database.",
                "Contract send/sign is excluded; only draft save/read and permission denial are exercised.",
                "No browser was used by this agent; main-controller in-app-browser evidence remains separate.",
            ],
        }


def _quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> int:
    runner: Runner | None = None
    try:
        require_write_approval()
        manifest = assert_prepared_manifest()
        verify_database_identity()
        verify_local_services()
        base_url = validate_loopback_base_url(manifest.get("baseUrl"))
        credentials = read_owner_only_json(CREDENTIAL_PATH)
        if credentials.get("runId") != RUN_ID:
            raise QaSafetyError("credentials do not belong to this QA run")
        runner = Runner(base_url, credentials)
        evidence = runner.execute()
        owner_only_json(EVIDENCE_PATH, evidence)
        print(f"[INFO] secret-free E2E evidence: {EVIDENCE_PATH}")
        print(
            f"[RESULT] passed={evidence['passedCount']}/8 "
            f"blocked={evidence['blockedCount']} failed={evidence['failedCount']}"
        )
        return 0 if evidence["status"] == "passed" else 1
    except (QaSafetyError, OSError, ValueError, AssertionError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2
    finally:
        if runner is not None:
            runner.logout_all()


if __name__ == "__main__":
    raise SystemExit(main())
