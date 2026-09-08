#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

# Read-only production evidence collector and verifier for the Beijing 0716
# signing-template/plan release. It never reads Redis sessions, creates JWTs,
# calls a mutation API, or writes business rows to MySQL.

MODE="${1:-preflight}"
case "$MODE" in
  preflight|postcheck) ;;
  *) echo "usage: $0 preflight|postcheck" >&2; exit 2 ;;
esac

DB="bosserp_stock_state_75c59ee"
PASS_FILE="/root/.erp-mysql-root-pass"
HR_USER_ID=940
HR_USERNAME="16657049808"
HR_NICK_NAME="段继康"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="/opt/erp-new-data-backups/sign-publish-readonly-${MODE}-${STAMP}"
LOCK_FILE="/var/lock/erp-sign-publish-readonly.lock"

for command in python3 mysql mysqldump gzip sha256sum flock systemctl; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "MISSING_COMMAND_${command}" >&2
    exit 3
  }
done
[[ "$(id -u)" == "0" ]] || { echo "ROOT_REQUIRED" >&2; exit 3; }
[[ -r "$PASS_FILE" ]] || { echo "MYSQL_PASSWORD_FILE_UNAVAILABLE" >&2; exit 3; }
case "$EVIDENCE_DIR" in
  /opt/erp-new-data-backups/sign-publish-readonly-*) ;;
  *) echo "UNSAFE_EVIDENCE_PATH" >&2; exit 3 ;;
esac
mkdir -m 700 "$EVIDENCE_DIR"
exec 9>"$LOCK_FILE"
flock -n 9 || { echo "READONLY_AUDIT_ALREADY_RUNNING" >&2; exit 5; }

python3 - "$PASS_FILE" "$EVIDENCE_DIR/mysql.cnf" <<'PY'
import os
import sys
from pathlib import Path

password = Path(sys.argv[1]).read_text(encoding="utf-8").rstrip("\r\n")
if not password or "\n" in password or "\r" in password:
    raise SystemExit("INVALID_MYSQL_PASSWORD_FILE")
escaped = password.replace("\\", "\\\\").replace('"', '\\"')
Path(sys.argv[2]).write_text(
    '[client]\nuser=root\npassword="' + escaped + '"\n', encoding="utf-8"
)
os.chmod(sys.argv[2], 0o600)
PY
mysql_cmd=(mysql --defaults-extra-file="$EVIDENCE_DIR/mysql.cnf" --protocol=socket \
  --batch --raw --skip-column-names "$DB")
dump_cmd=(mysqldump --defaults-extra-file="$EVIDENCE_DIR/mysql.cnf" --protocol=socket \
  --single-transaction --quick --hex-blob --set-gtid-purged=OFF --column-statistics=0 \
  --skip-lock-tables --complete-insert --skip-add-locks --skip-disable-keys --skip-triggers)

state="$EVIDENCE_DIR/state.jsonl"
"${mysql_cmd[@]}" > "$state" <<'SQL'
SELECT JSON_OBJECT('recordType','hrConfig','configId',config_id,'configValue',config_value,'configType',config_type)
FROM sys_config WHERE config_key='sign.hr.user-id' ORDER BY config_id;
SELECT JSON_OBJECT('recordType','hrUser','userId',user_id,'userName',user_name,'nickName',nick_name,
                   'status',status,'delFlag',del_flag,'credentialState',credential_state)
FROM sys_user WHERE user_id=940;
SELECT JSON_OBJECT('recordType','hrRole','roleId',r.role_id,'roleKey',r.role_key,
                   'roleStatus',r.status,'roleDelFlag',r.del_flag)
FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id
WHERE ur.user_id=940 ORDER BY r.role_id;
SELECT JSON_OBJECT('recordType','hrPermission','roleId',r.role_id,'menuId',m.menu_id,
                   'permission',m.perms,'menuStatus',m.status)
FROM sys_user_role ur JOIN sys_role r ON r.role_id=ur.role_id
JOIN sys_role_menu rm ON rm.role_id=r.role_id JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE ur.user_id=940 AND m.perms IN (
    'oa:signPackage:list','oa:signPackage:template','oa:signTask:send'
)
ORDER BY m.perms,m.menu_id;
SELECT JSON_OBJECT('recordType','dept','deptId',dept_id,'deptName',dept_name,'deptType',dept_type,
                   'parentId',parent_id,'ancestors',ancestors,'status',status,'delFlag',del_flag)
FROM sys_dept WHERE dept_id IN (1171,1176,1157) ORDER BY dept_id;
SELECT JSON_OBJECT('recordType','hrShopScope','targetDeptId',target.dept_id,
                   'targetDeptName',target.dept_name,'targetDeptType',target.dept_type,
                   'scopeCount',(
                       SELECT COUNT(DISTINCT effective_scope.dept_id)
                       FROM sys_user_shop effective_scope
                       JOIN sys_dept scope_dept ON scope_dept.dept_id=effective_scope.dept_id
                       WHERE effective_scope.user_id=940
                         AND scope_dept.del_flag='0' AND scope_dept.status='0'
                         AND scope_dept.dept_type IN ('GROUP','COMPANY','STORE','WAREHOUSE')
                         AND (target.dept_id=scope_dept.dept_id
                              OR FIND_IN_SET(scope_dept.dept_id,target.ancestors))
                   ),
                   'directScopeCount',(
                       SELECT COUNT(*) FROM sys_user_shop direct_scope
                       WHERE direct_scope.user_id=940 AND direct_scope.dept_id=target.dept_id
                   ))
FROM sys_dept target
WHERE target.dept_id IN (1171,1176,1157)
  AND target.del_flag='0' AND target.status='0'
ORDER BY target.dept_id;
SELECT JSON_OBJECT('recordType','onboardGuardColumn','columnName',column_name,
                   'dataType',data_type,'extra',extra,'generationExpression',generation_expression)
FROM information_schema.COLUMNS
WHERE table_schema=DATABASE() AND table_name='oa_sign_task'
 AND column_name='open_onboard_employee_id';
SELECT JSON_OBJECT('recordType','onboardGuardIndex','indexName',index_name,
                   'nonUnique',MIN(non_unique),
                   'columns',GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','))
FROM information_schema.STATISTICS
WHERE table_schema=DATABASE() AND table_name='oa_sign_task'
 AND index_name='uk_oa_sign_task_open_onboard_employee'
GROUP BY index_name;
SELECT JSON_OBJECT('recordType','template','templateId',template_id,'templateType',template_type,
                   'templateName',template_name,'templateVersion',template_version,'scenario',scenario,
                   'employmentType',employment_type,'socialType',social_type,
                   'postLevelScope',post_level_scope,'salaryVersion',salary_version,
                   'fileUrl',file_url,'fileName',file_name,'fileSize',file_size,'fileHash',file_hash,
                   'employeeVisible',employee_visible,'readConfirmationRequired',read_confirmation_required,
                   'employeeSignRequired',employee_sign_required,
                   'signaturePositionJson',signature_position_json,
                   'companySealRequired',company_seal_required,
                   'companySealPositionJson',company_seal_position_json,
                   'matchConditionJson',match_condition_json,
                   'status',status,'sortOrder',sort_order)
FROM oa_sign_template
WHERE template_version='20260718-v5-draft'
  AND template_type IN (
      'ONBOARD_COMMITMENT','ONBOARD_LABOR_CONTRACT','ONBOARD_HANDBOOK_RECEIPT',
      'ONBOARD_SALARY_CONFIRM','ONBOARD_SERVICE_CONTRACT','ONBOARD_SERVICE_RECEIPT',
      'ONBOARD_CONFIDENTIAL_NONCOMPETE','ONBOARD_MINOR_NONSTUDENT_DECLARATION'
  )
ORDER BY template_type,salary_version,template_id;
SELECT JSON_OBJECT('recordType','plan','planId',plan_id,'planName',plan_name,'scenario',scenario,
                   'postName',post_name,'employmentType',employment_type,'socialType',social_type,
                   'servicePersonType',service_person_type,'insuranceType',insurance_type,
                   'postLevelSnapshot',post_level_snapshot,'salaryVersion',salary_version,
                   'shopDeptId',shop_dept_id,'legalEntityId',legal_entity_id,
                   'ruleJson',rule_json,'defaultValuesJson',default_values_json,
                   'signDeadlineDays',sign_deadline_days,'reminderPolicyJson',reminder_policy_json,
                   'autoSendConditionJson',auto_send_condition_json,'status',status,'sortOrder',sort_order)
FROM oa_sign_plan
WHERE LOWER(TRIM(scenario))='onboard'
ORDER BY shop_dept_id,plan_id;
SELECT JSON_OBJECT('recordType','binding','id',pt.id,'planId',pt.plan_id,
                   'planName',p.plan_name,'templateId',pt.template_id,'sortOrder',pt.sort_order)
FROM oa_sign_plan_template pt JOIN oa_sign_plan p ON p.plan_id=pt.plan_id
WHERE LOWER(TRIM(p.scenario))='onboard'
ORDER BY pt.plan_id,pt.sort_order,pt.id;
SELECT JSON_OBJECT('recordType','version','versionId',v.version_id,'planId',v.plan_id,
                   'planName',v.plan_name,'versionNo',v.version_no,'scenario',v.scenario,
                   'shopDeptId',v.shop_dept_id,'legalEntityId',v.legal_entity_id,
                   'ruleJson',v.rule_json,'defaultValuesJson',v.default_values_json,
                   'signDeadlineDays',v.sign_deadline_days,'reminderPolicyJson',v.reminder_policy_json,
                   'autoSendConditionJson',v.auto_send_condition_json,
                   'publishStatus',v.publish_status,'matchingStatus',v.matching_status,
                   'versionHash',v.version_hash,'publishedByUserId',v.published_by_user_id,
                   'publishedBy',v.published_by)
FROM oa_sign_plan_version v
WHERE LOWER(TRIM(v.scenario))='onboard'
ORDER BY v.shop_dept_id,v.version_id;
SELECT JSON_OBJECT('recordType','versionTemplate','id',vt.id,
                   'planVersionId',vt.plan_version_id,'planId',v.plan_id,
                   'planName',v.plan_name,'templateId',vt.template_id,
                   'sourceFileUrl',vt.source_file_url,'sourceFileHash',vt.source_file_hash,
                   'employeeVisible',vt.employee_visible,
                   'readConfirmationRequired',vt.read_confirmation_required,
                   'employeeSignRequired',vt.employee_sign_required,
                   'signaturePositionJson',vt.signature_position_json,
                   'companySealRequired',vt.company_seal_required,
                   'companySealPositionJson',vt.company_seal_position_json,
                   'matchConditionJson',vt.match_condition_json,'sortOrder',vt.sort_order)
FROM oa_sign_plan_version_template vt
JOIN oa_sign_plan_version v ON v.version_id=vt.plan_version_id
WHERE LOWER(TRIM(v.scenario))='onboard'
ORDER BY vt.plan_version_id,vt.sort_order,vt.id;
SQL
chmod 600 "$state"

# Prove that the running OA artifact contains the exact class bytes from the
# locally tested release. These fingerprints do not authorize an HR action.
oa_pid="$(systemctl show erp-new@oa.service -p MainPID --value 2>/dev/null || true)"
python3 - "$oa_pid" >> "$state" <<'PY'
import hashlib
import json
import os
import sys
import zipfile
from pathlib import Path

pid = sys.argv[1]
result = {"recordType": "oaRuntime", "pid": pid, "serviceActive": False,
          "jarPath": None, "jarSha256": None,
          "fingerprintScheme": "critical-entry-sha256-v1",
          "criticalEntrySha256": {}}
critical_entries = {
    "onboardScenario": "BOOT-INF/classes/com/erp/oa/service/rule/OnboardSignScenarioRule.class",
    "placementPolicy": "BOOT-INF/classes/com/erp/oa/service/impl/OaSignPlacementPolicyService.class",
    "signedPdf": "BOOT-INF/classes/com/erp/oa/service/impl/OaSignedPdfService.class",
    "signPackageService": "BOOT-INF/classes/com/erp/oa/service/impl/OaSignPackageServiceImpl.class",
    "signScope": "BOOT-INF/classes/com/erp/oa/service/impl/OaSignScopeService.class",
    "signTaskController": "BOOT-INF/classes/com/erp/oa/controller/OaSignTaskController.class",
    "signTaskService": "BOOT-INF/classes/com/erp/oa/service/impl/OaSignTaskServiceImpl.class",
    "signNotificationFailureQuery": "BOOT-INF/classes/com/erp/oa/service/impl/OaSignNotificationFailureQueryService.class",
    "signPlanService": "BOOT-INF/classes/com/erp/oa/service/impl/OaSignPlanServiceImpl.class",
    "signScopeMapperXml": "BOOT-INF/classes/mapper/oa/OaSignScopeMapper.xml",
    "signNotificationOutboxMapperXml": "BOOT-INF/classes/mapper/oa/OaSignNotificationOutboxMapper.xml",
}
try:
    if not pid.isdigit() or int(pid) <= 0:
        raise RuntimeError("OA_PID_INVALID")
    result["serviceActive"] = True
    argv = Path(f"/proc/{pid}/cmdline").read_bytes().split(b"\0")
    values = [item.decode("utf-8", "replace") for item in argv if item]
    index = values.index("-jar")
    raw = Path(values[index + 1])
    if not raw.is_absolute():
        raw = Path(os.path.realpath(f"/proc/{pid}/cwd")) / raw
    jar = raw.resolve(strict=True)
    result["jarPath"] = str(jar)
    digest = hashlib.sha256()
    with jar.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    result["jarSha256"] = digest.hexdigest()
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        missing = sorted(path for path in critical_entries.values() if path not in names)
        if missing:
            raise RuntimeError("CRITICAL_ENTRY_MISSING")
        result["criticalEntrySha256"] = {
            key: hashlib.sha256(archive.read(path)).hexdigest()
            for key, path in critical_entries.items()
        }
except Exception as exc:
    result["error"] = type(exc).__name__
print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
PY

# Hash the exact source files referenced by the reviewed v5 template rows. IDs are
# deliberately discovered from the database: the script never guesses registrations.
python3 - "$state" >> "$state" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

state_path = Path(sys.argv[1])
rows = [json.loads(line) for line in state_path.read_text(encoding="utf-8").splitlines() if line]
root = Path("/opt/erp-new-data/uploadPath").resolve()

def template_key(row):
    template_type = str(row.get("templateType") or "").strip().upper()
    salary_version = str(row.get("salaryVersion") or "").strip().upper()
    return f"{template_type}:{salary_version}" if salary_version else template_type

for template in (row for row in rows if row.get("recordType") == "template"):
    template_id = template.get("templateId")
    file_url = str(template.get("fileUrl") or "")
    result = {
        "recordType": "templateFile",
        "templateId": template_id,
        "templateKey": template_key(template),
        "path": None,
        "exists": False,
        "size": None,
        "sha256": None,
    }
    try:
        if not file_url.startswith("/profile/"):
            raise ValueError("UNSAFE_TEMPLATE_FILE_URL")
        path = (root / file_url.removeprefix("/profile/")).resolve()
        if path != root and root not in path.parents:
            raise ValueError("TEMPLATE_PATH_ESCAPE")
        result["path"] = str(path)
        result["exists"] = path.is_file()
        if path.is_file():
            result["size"] = path.stat().st_size
            digest = hashlib.sha256()
            with path.open("rb") as handle:
                for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                    digest.update(chunk)
            result["sha256"] = digest.hexdigest()
    except Exception as exc:
        result["error"] = type(exc).__name__
    print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
PY

"${dump_cmd[@]}" "$DB" \
  oa_sign_template oa_sign_plan oa_sign_plan_template \
  oa_sign_plan_version oa_sign_plan_version_template \
  | gzip -9 > "$EVIDENCE_DIR/signing-config-readonly.sql.gz"
gzip -t "$EVIDENCE_DIR/signing-config-readonly.sql.gz"
sha256sum "$EVIDENCE_DIR/signing-config-readonly.sql.gz" "$state" \
  > "$EVIDENCE_DIR/evidence.sha256"
chmod 600 "$EVIDENCE_DIR/signing-config-readonly.sql.gz" "$EVIDENCE_DIR/evidence.sha256"
rm -f -- "$EVIDENCE_DIR/mysql.cnf"

set +e
python3 - "$MODE" "$state" "$EVIDENCE_DIR/report.json" <<'PY'
from __future__ import annotations

import json
import os
import sys
from pathlib import Path
from typing import Any

mode, state_path, report_path = sys.argv[1:]
rows = [json.loads(line) for line in Path(state_path).read_text(encoding="utf-8").splitlines() if line]

EXPECTED_TEMPLATE_VERSION = "20260718-v5-draft"
# key: file name, size, SHA-256, company seal, employment type, post-level scope
EXPECTED_FILES = {
    "ONBOARD_COMMITMENT": (
        "05_ONBOARD_COMMITMENT.docx", 15841,
        "9cc4474fa78b6e9707cb87316d87023ab6b654b31e6488c4bbd857edd47b5816", "N", None, None),
    "ONBOARD_LABOR_CONTRACT": (
        "09_ONBOARD_LABOR_CONTRACT.docx", 34494,
        "fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118", "Y", "劳动合同", None),
    "ONBOARD_HANDBOOK_RECEIPT": (
        "10_ONBOARD_HANDBOOK_RECEIPT.docx", 17721,
        "65675b6182ee5c9cdd6650f94341cfccd4b23e69dfbde87c806cd41aee2f4b66", "N", "劳动合同", None),
    "ONBOARD_SALARY_CONFIRM:A": (
        "11_ONBOARD_SALARY_CONFIRM_A.docx", 19527,
        "c3341591b4806fcc5909315040a2e174da0eaa90245169cf74aba735d5ad8551", "N", "劳动合同", None),
    "ONBOARD_SALARY_CONFIRM:B": (
        "12_ONBOARD_SALARY_CONFIRM_B.docx", 18847,
        "e2ba862e63fc8e41485fc26348e354fc568126247f9676571690a030f3957c1c", "N", "劳动合同", None),
    "ONBOARD_SERVICE_CONTRACT": (
        "13_ONBOARD_SERVICE_CONTRACT.docx", 33456,
        "3794b2a1f54b734fd36acd49dfd6e7920892a290744de6fc34e19d9ef51eb5b4", "Y", "劳务合同", None),
    "ONBOARD_SERVICE_RECEIPT": (
        "14_ONBOARD_SERVICE_RECEIPT.docx", 19478,
        "4c72f4ec27b75aa070df051c736b316dae1c9752550ef46b9d670ede887c6a13", "N", "劳务合同", None),
    "ONBOARD_CONFIDENTIAL_NONCOMPETE": (
        "15_ONBOARD_CONFIDENTIAL_NONCOMPETE.docx", 19016,
        "0ab9894b08ad4b91ea1b5b1cd3b2c07332c5ef6483453af525069bee7999ba40", "Y", None, "7级及以上"),
    "ONBOARD_MINOR_NONSTUDENT_DECLARATION": (
        "16_ONBOARD_MINOR_NONSTUDENT_DECLARATION.docx", 13381,
        "264b642c03c9699ac234d1642e4da06d77f4c10bba5d6e2cffc5e65fca2031c9", "N", None, None),
}
APPENDED_CONFIRMATION_PAGE = {"mode": "APPENDED_CONFIRMATION_PAGE"}
EXPECTED_RUNTIME_ENTRY_SHA256 = {
    "onboardScenario": "50f6f65f6bf04e0ad33fbc1c4545138f63f16c410abb4b1bd99f83d5f8996500",
    "placementPolicy": "0c29df6d12c94b205ad580a29ad6b0d8c6970dfd7603ce3a8b973acd69c2f7da",
    "signedPdf": "483c2601cd7b8c0688cbd4b8122f91ca190613cc91c860864148a7e97c9d85b1",
    "signPackageService": "6f7bd89cfe83cfdd05bd99649e68c89f154e3c4861dfd1ea4f554037f3d7be1b",
    "signScope": "90e91570e021e274251905ee239468480fc476f455a31aa8a3cb85ed8afbe5ff",
    "signTaskController": "7ea550ebcf622baf6b1f87929d2682f39972aba8c1b6186ed8b03089ace591d8",
    "signTaskService": "29db260bdcca07063e93fbbbaf3dd5a12a4f94a459d2ca50788763d18b7a1c69",
    "signNotificationFailureQuery": "8df1e0e1a621311760a3a664d1be316a8d636ec37314bf711bdb2010f301de9b",
    "signPlanService": "8529c78e840d3d4d49a4345860cc011fcd49a5618e26ea35fdd96a44a0059df6",
    "signScopeMapperXml": "13a39084ab44b971840fd85bd44fb3a0e8a1612af0f3552a911fa63a7648d1c1",
    "signNotificationOutboxMapperXml": "cbf26a323c9dba54fa8571db679399fe74ea49c8a401bd1a8350c721ac6892c2",
}

errors: list[str] = []
blockers: list[str] = []

def kind(name: str) -> list[dict[str, Any]]:
    return [row for row in rows if row.get("recordType") == name]

def one(name: str) -> dict[str, Any]:
    found = kind(name)
    if len(found) != 1:
        errors.append(f"{name}_COUNT_{len(found)}")
        return {}
    return found[0]

config = one("hrConfig")
if str(config.get("configValue")) != "940" or config.get("configType") != "Y":
    errors.append("HR_CONFIG_MISMATCH")
user = one("hrUser")
if (str(user.get("userId")) != "940" or user.get("userName") != "16657049808"
        or user.get("nickName") != "段继康" or user.get("status") != "0"
        or user.get("delFlag") != "0"):
    errors.append("HR_USER_MISMATCH")
if not any(row.get("roleKey") == "sign_single_hr" and row.get("roleStatus") == "0"
           and row.get("roleDelFlag") == "0" for row in kind("hrRole")):
    errors.append("HR_ROLE_MISSING")
permissions = {row.get("permission") for row in kind("hrPermission") if row.get("menuStatus") == "0"}
if not {"oa:signPackage:list", "oa:signPackage:template", "oa:signTask:send"}.issubset(permissions):
    errors.append("HR_PERMISSIONS_MISSING")

guard_column = one("onboardGuardColumn")
expression = str(guard_column.get("generationExpression") or "").lower()
if (guard_column.get("dataType") != "bigint" or guard_column.get("extra") != "STORED GENERATED"
        or not all(token in expression for token in (
            "scenario", "onboard", "status", "signed", "refused", "expired",
            "cancelled", "no_action", "employee_id"))):
    errors.append("ONBOARD_GUARD_COLUMN_MISMATCH")
guard_index = one("onboardGuardIndex")
if (guard_index.get("columns") != "open_onboard_employee_id"
        or int(guard_index.get("nonUnique") or 1) != 0):
    errors.append("ONBOARD_GUARD_INDEX_MISMATCH")
runtime = one("oaRuntime")
if not runtime.get("serviceActive"):
    errors.append("OA_SERVICE_NOT_ACTIVE")
if runtime.get("fingerprintScheme") != "critical-entry-sha256-v1":
    errors.append("OA_CRITICAL_ENTRY_FINGERPRINT_SCHEME_MISMATCH")
runtime_entry_sha256 = runtime.get("criticalEntrySha256")
if (not isinstance(runtime_entry_sha256, dict)
        or set(runtime_entry_sha256) != set(EXPECTED_RUNTIME_ENTRY_SHA256)):
    errors.append("OA_CRITICAL_ENTRY_FINGERPRINT_KEY_SET_MISMATCH")
elif runtime_entry_sha256 != EXPECTED_RUNTIME_ENTRY_SHA256:
    errors.append("OA_CRITICAL_ENTRY_FINGERPRINT_MISMATCH")

departments = {int(row["deptId"]): row for row in kind("dept")}
for dept_id in (1171, 1176):
    row = departments.get(dept_id, {})
    if row.get("deptType") != "STORE" or row.get("status") != "0" or row.get("delFlag") != "0":
        errors.append(f"DEPT_{dept_id}_NOT_ACTIVE_STORE")
xian_company = departments.get(1157, {})
if (xian_company.get("deptName") != "西安区域运营"
        or xian_company.get("deptType") != "COMPANY"
        or xian_company.get("status") != "0" or xian_company.get("delFlag") != "0"):
    errors.append("DEPT_1157_NOT_ACTIVE_XIAN_COMPANY")
scope_rows = {int(row["targetDeptId"]): row for row in kind("hrShopScope")}
scope = {dept_id: int(row.get("scopeCount") or 0) for dept_id, row in scope_rows.items()}
for dept_id in (1171, 1176):
    if scope.get(dept_id, 0) < 1:
        errors.append(f"HR_SCOPE_MISSING_{dept_id}")
if scope.get(1157, 0) < 1:
    errors.append("HR_SCOPE_MISSING_1157")
if int(scope_rows.get(1157, {}).get("directScopeCount") or 0) != 1:
    errors.append("HR_EXPLICIT_SCOPE_MISSING_1157")

def template_key(row: dict[str, Any]) -> str:
    template_type = str(row.get("templateType") or "").strip().upper()
    salary_version = str(row.get("salaryVersion") or "").strip().upper()
    return f"{template_type}:{salary_version}" if salary_version else template_type

templates = {int(row["templateId"]): row for row in kind("template")}
templates_by_key: dict[str, dict[str, Any]] = {}
for row in templates.values():
    key = template_key(row)
    if key in templates_by_key:
        errors.append(f"TARGET_TEMPLATE_DUPLICATE:{key}")
    templates_by_key[key] = row
files_by_key: dict[str, dict[str, Any]] = {}
for row in kind("templateFile"):
    key = str(row.get("templateKey") or "")
    if key in files_by_key:
        errors.append(f"TARGET_TEMPLATE_FILE_DUPLICATE:{key}")
    files_by_key[key] = row
if set(templates_by_key) != set(EXPECTED_FILES):
    errors.append("TARGET_TEMPLATE_SET_MISMATCH")
for key, expected in EXPECTED_FILES.items():
    row = templates_by_key.get(key, {})
    if (row.get("templateVersion") != EXPECTED_TEMPLATE_VERSION
            or row.get("fileName") != expected[0]
            or row.get("employmentType") != expected[4]
            or row.get("postLevelScope") != expected[5]):
        errors.append(f"TEMPLATE_IDENTITY_MISMATCH:{key}")
    if (int(row.get("fileSize") or 0) != expected[1]
            or str(row.get("fileHash") or "").lower() != expected[2]
            or str(row.get("scenario") or "").lower() != "onboard"):
        errors.append(f"TEMPLATE_FILE_METADATA_MISMATCH:{key}")
    file_row = files_by_key.get(key, {})
    if (not file_row.get("exists") or int(file_row.get("size") or 0) != expected[1]
            or str(file_row.get("sha256") or "").lower() != expected[2]):
        errors.append(f"TEMPLATE_PHYSICAL_FILE_MISMATCH:{key}")

def json_obj(raw: Any) -> dict[str, Any]:
    if raw is None or str(raw).strip() == "":
        return {}
    value = raw if isinstance(raw, dict) else json.loads(str(raw))
    return value if isinstance(value, dict) else {}

def canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))

insurance_map = {"COMMERCIAL_ACCIDENT": "商业意外保险", "EMPLOYER_LIABILITY": "雇主责任险"}
b1_code = ""
b3_code = ""
if mode == "preflight":
    blockers.extend([
        "B1_INSURANCE_UNCONFIRMED",
        "B3_INSURANCE_UNCONFIRMED",
    ])
else:
    b1_code = os.environ.get("B1_INSURANCE_TYPE_CODE", "")
    b3_code = os.environ.get("B3_INSURANCE_TYPE_CODE", "")
    if b1_code not in insurance_map:
        errors.append("B1_INSURANCE_TYPE_CODE_INVALID")
    if b3_code not in insurance_map:
        errors.append("B3_INSURANCE_TYPE_CODE_INVALID")

if mode == "postcheck":
    for key, expected in EXPECTED_FILES.items():
        row = templates_by_key.get(key, {})
        actual_signature = json_obj(row.get("signaturePositionJson"))
        actual_seal = json_obj(row.get("companySealPositionJson"))
        wanted_signature = APPENDED_CONFIRMATION_PAGE
        wanted_seal = APPENDED_CONFIRMATION_PAGE if expected[3] == "Y" else None
        if (row.get("status") != "0" or row.get("employeeVisible") != "Y"
                or row.get("readConfirmationRequired") != "Y"
                or row.get("employeeSignRequired") != "Y"
                or row.get("companySealRequired") != expected[3]
                or canonical(actual_signature) != canonical(wanted_signature)
                or (canonical(actual_seal) if actual_seal else None)
                   != (canonical(wanted_seal) if wanted_seal else None)):
            errors.append(f"TEMPLATE_PUBLISH_POLICY_MISMATCH:{key}")

    definitions = [
        ("入职-A4-劳动合同-无社保-2至4级",0,"A4","劳动合同","无社保",None,None,"2-4","A",
         ("ONBOARD_COMMITMENT","ONBOARD_LABOR_CONTRACT","ONBOARD_HANDBOOK_RECEIPT",
          "ONBOARD_SALARY_CONFIRM:A","ONBOARD_MINOR_NONSTUDENT_DECLARATION"),100),
        ("入职-A5-劳动合同-无社保-5至6级",0,"A5","劳动合同","无社保",None,None,"5-6","A",
         ("ONBOARD_COMMITMENT","ONBOARD_LABOR_CONTRACT","ONBOARD_HANDBOOK_RECEIPT",
          "ONBOARD_SALARY_CONFIRM:A","ONBOARD_MINOR_NONSTUDENT_DECLARATION"),101),
        ("入职-A1-劳动合同-有社保-2至4级",0,"A1","劳动合同","有社保",None,None,"2-4","B",
         ("ONBOARD_COMMITMENT","ONBOARD_LABOR_CONTRACT","ONBOARD_HANDBOOK_RECEIPT",
          "ONBOARD_SALARY_CONFIRM:B","ONBOARD_MINOR_NONSTUDENT_DECLARATION"),102),
        ("入职-B1-在校实习生劳务合同-2至4级",0,"B1","劳务合同","无社保","在校实习生",insurance_map.get(b1_code),"2-4",None,
         ("ONBOARD_COMMITMENT","ONBOARD_SERVICE_CONTRACT","ONBOARD_SERVICE_RECEIPT",
          "ONBOARD_MINOR_NONSTUDENT_DECLARATION"),110),
        ("入职-B3-退休返聘劳务合同-7至9级",0,"B3","劳务合同","无社保","退休返聘",insurance_map.get(b3_code),"7-9",None,
         ("ONBOARD_COMMITMENT","ONBOARD_SERVICE_CONTRACT","ONBOARD_SERVICE_RECEIPT",
          "ONBOARD_CONFIDENTIAL_NONCOMPETE","ONBOARD_MINOR_NONSTUDENT_DECLARATION"),111),
    ]
    target_routes = ("A4", "A5", "A1", "B1", "B3")

    def derive_route(rule: dict[str, Any]) -> str | None:
        # Mirror OnboardSignScenarioRule.parseRule: explicit and legacy fields
        # must normalize successfully and must not conflict.  Never trust an
        # explicit routeCode without deriving the same route from its facts.
        route_fields = (
            "routeCode", "contractTypeCode", "socialTypeCode", "jobGradeBand",
            "employmentType", "socialType", "postLevel",
            "servicePersonType", "insuranceType",
        )
        if any(
            rule.get(field) is not None and not isinstance(rule.get(field), str)
            for field in route_fields
        ):
            return None

        def raw_text(field: str) -> str | None:
            value = rule.get(field)
            return value.strip() if isinstance(value, str) else None

        def code(value: str | None) -> str | None:
            normalized = value.strip().upper() if value is not None else ""
            return normalized or None

        def explicit_band(value: str | None) -> str | None:
            normalized = code(value)
            if normalized in {"2-4", "5-6"}:
                return normalized
            if normalized in {"7-8", "7-9"}:
                return "7-9"
            return None

        def legacy_band(value: str | None) -> str | None:
            normalized = code(value)
            band = explicit_band(normalized)
            if band is not None:
                return band
            if normalized is None:
                return None
            number = normalized[1:] if normalized.startswith("P") else normalized
            if number not in {str(item) for item in range(2, 10)}:
                return None
            grade = int(number)
            return "2-4" if grade <= 4 else "5-6" if grade <= 6 else "7-9"

        explicit_route_raw = raw_text("routeCode")
        explicit_contract_raw = raw_text("contractTypeCode")
        explicit_social_raw = raw_text("socialTypeCode")
        explicit_band_raw = raw_text("jobGradeBand")
        legacy_contract_raw = raw_text("employmentType")
        legacy_social_raw = raw_text("socialType")
        legacy_band_raw = raw_text("postLevel")

        explicit_route = code(explicit_route_raw)
        explicit_contract = code(explicit_contract_raw)
        explicit_social = code(explicit_social_raw)
        normalized_explicit_band = explicit_band(explicit_band_raw)
        legacy_contract = {
            "劳动合同": "LABOR_CONTRACT",
            "劳务合同": "SERVICE_CONTRACT",
        }.get(legacy_contract_raw or "")
        legacy_social = {
            "有社保": "SOCIAL_INSURED",
            "无社保": "SOCIAL_UNINSURED",
        }.get(legacy_social_raw or "")
        normalized_legacy_band = legacy_band(legacy_band_raw)

        normalized = (
            (explicit_route_raw, explicit_route),
            (explicit_contract_raw, explicit_contract),
            (explicit_social_raw, explicit_social),
            (explicit_band_raw, normalized_explicit_band),
            (legacy_contract_raw, legacy_contract),
            (legacy_social_raw, legacy_social),
            (legacy_band_raw, normalized_legacy_band),
        )
        if any(raw is not None and value is None for raw, value in normalized):
            return None
        if any(
            left is not None and right is not None and left != right
            for left, right in (
                (explicit_contract, legacy_contract),
                (explicit_social, legacy_social),
                (normalized_explicit_band, normalized_legacy_band),
            )
        ):
            return None

        contract = explicit_contract or legacy_contract
        social = explicit_social or legacy_social
        band = normalized_explicit_band or normalized_legacy_band
        index = {"2-4": 1, "5-6": 2, "7-9": 3}.get(band or "")
        derived = None
        if index and contract == "LABOR_CONTRACT" and social == "SOCIAL_INSURED":
            derived = f"A{index}"
        elif index and contract == "LABOR_CONTRACT" and social == "SOCIAL_UNINSURED":
            derived = f"A{index + 3}"
        elif index and contract == "SERVICE_CONTRACT" and social == "SOCIAL_UNINSURED":
            derived = f"B{index}"
        if derived is None or (explicit_route is not None and explicit_route != derived):
            return None
        return derived

    def active_plan_release_metrics(
        versions: list[dict[str, Any]],
        expected_routes: tuple[str, ...],
        parse_rule: Any,
    ) -> dict[str, Any]:
        active_versions = [
            version for version in versions
            if version.get("publishStatus") == "PUBLISHED"
            and version.get("matchingStatus") == "ENABLED"
        ]
        derived_active = [
            (version, derive_route(parse_rule(version.get("ruleJson"))))
            for version in active_versions
        ]
        active_keys: dict[tuple[int, str], list[int]] = {}
        for version, route in derived_active:
            if route is not None:
                active_keys.setdefault(
                    (int(version.get("shopDeptId") or 0), route), []
                ).append(int(version.get("versionId") or 0))
        return {
            "activeKeys": active_keys,
            "activeTargetRouteCount": sum(
                1
                for route in expected_routes
                if len(active_keys.get((0, route), [])) == 1
            ),
            "targetRouteConflictCount": sum(
                max(0, len(active_keys.get((0, route), [])) - 1)
                for route in expected_routes
            ),
            "nonGlobalActivePlanCount": sum(
                1 for version, _route in derived_active
                if int(version.get("shopDeptId") or 0) != 0
            ),
            "isolatedUatPlanInProductionCount": sum(
                1 for version, route in derived_active
                if int(version.get("shopDeptId") or 0) == 0 and route == "A3"
            ),
            "unexpectedGlobalActiveRouteCount": sum(
                1 for version, route in derived_active
                if int(version.get("shopDeptId") or 0) == 0
                and route is not None
                and route not in expected_routes
            ),
            "invalidActiveRuleCount": sum(
                1 for _version, route in derived_active if route is None
            ),
        }

    plan_rows = kind("plan")
    bindings = kind("binding")
    versions = kind("version")
    snapshots = kind("versionTemplate")
    release_metrics = active_plan_release_metrics(versions, target_routes, json_obj)
    active_keys = release_metrics["activeKeys"]
    active_target_route_count = release_metrics["activeTargetRouteCount"]
    target_route_conflict_count = release_metrics["targetRouteConflictCount"]
    non_global_target_plan_count = release_metrics["nonGlobalActivePlanCount"]
    isolated_uat_plan_in_production_count = release_metrics[
        "isolatedUatPlanInProductionCount"
    ]
    unexpected_global_active_route_count = release_metrics[
        "unexpectedGlobalActiveRouteCount"
    ]
    invalid_active_rule_count = release_metrics["invalidActiveRuleCount"]
    if active_target_route_count != 5:
        errors.append(f"ACTIVE_TARGET_ROUTE_COUNT_MISMATCH:{active_target_route_count}")
    if target_route_conflict_count != 0:
        errors.append(f"TARGET_ROUTE_CONFLICT_COUNT:{target_route_conflict_count}")
    if non_global_target_plan_count != 0:
        errors.append(f"NON_GLOBAL_TARGET_PLAN_COUNT:{non_global_target_plan_count}")
    if isolated_uat_plan_in_production_count != 0:
        errors.append(
            f"ISOLATED_UAT_PLAN_IN_PRODUCTION_COUNT:{isolated_uat_plan_in_production_count}"
        )
    if unexpected_global_active_route_count != 0:
        errors.append(
            f"UNEXPECTED_GLOBAL_ACTIVE_ROUTE_COUNT:{unexpected_global_active_route_count}"
        )
    if invalid_active_rule_count != 0:
        errors.append(f"INVALID_ACTIVE_RULE_COUNT:{invalid_active_rule_count}")
    for (name, dept, route, employment, social, person_type, insurance, band,
            salary_version, template_keys, sort_order) in definitions:
        sources = [row for row in plan_rows if row.get("planName") == name]
        if len(sources) != 1:
            errors.append(f"PLAN_SOURCE_COUNT_MISMATCH:{name}")
            continue
        source = sources[0]
        plan_id = int(source.get("planId") or 0)
        if (int(source.get("shopDeptId") or 0) != dept or source.get("status") != "0"
                or str(source.get("scenario") or "").lower() != "onboard"
                or source.get("employmentType") != employment or source.get("socialType") != social
                or source.get("servicePersonType") != person_type or source.get("insuranceType") != insurance
                or source.get("postLevelSnapshot") != band
                or (source.get("salaryVersion") or None) != salary_version
                or int(source.get("signDeadlineDays") or 0) != 7
                or int(source.get("sortOrder") or 0) != sort_order
                or source.get("legalEntityId") is not None):
            errors.append(f"PLAN_SOURCE_FIELDS_MISMATCH:{name}")
        bound_ids = [int(row.get("templateId") or 0) for row in sorted(
            (row for row in bindings if int(row.get("planId") or 0) == plan_id),
            key=lambda row: (int(row.get("sortOrder") or 0), int(row.get("id") or 0)))]
        bound_keys = [template_key(templates.get(template_id, {})) for template_id in bound_ids]
        if bound_keys != list(template_keys):
            errors.append(f"PLAN_BINDINGS_MISMATCH:{name}")
        active = [row for row in versions if row.get("planName") == name
                  and row.get("publishStatus") == "PUBLISHED" and row.get("matchingStatus") == "ENABLED"]
        if len(active) != 1:
            errors.append(f"ACTIVE_VERSION_COUNT_MISMATCH:{name}")
            continue
        version = active[0]
        version_id = int(version.get("versionId") or 0)
        rule_json = json_obj(version.get("ruleJson"))
        if (derive_route(rule_json) != route or int(version.get("shopDeptId") or 0) != dept
                or int(version.get("publishedByUserId") or 0) != 940
                or version.get("publishedBy") != "16657049808"
                or version.get("legalEntityId") is not None):
            errors.append(f"VERSION_METADATA_MISMATCH:{name}")
        if person_type and rule_json.get("servicePersonType") != person_type:
            errors.append(f"VERSION_SERVICE_PERSON_MISMATCH:{name}")
        if insurance and rule_json.get("insuranceType") != insurance:
            errors.append(f"VERSION_INSURANCE_MISMATCH:{name}")
        if len(active_keys.get((dept, route), [])) != 1:
            errors.append(f"ACTIVE_ROUTE_CONFLICT:{dept}:{route}")
        version_files = sorted((row for row in snapshots if int(row.get("planVersionId") or 0) == version_id),
                               key=lambda row: (int(row.get("sortOrder") or 0), int(row.get("id") or 0)))
        version_keys = [template_key(templates.get(int(row.get("templateId") or 0), {}))
                        for row in version_files]
        if version_keys != list(template_keys):
            errors.append(f"VERSION_TEMPLATE_SET_MISMATCH:{name}")
        for snapshot in version_files:
            template_id = int(snapshot.get("templateId") or 0)
            template = templates.get(template_id, {})
            key = template_key(template)
            expected = EXPECTED_FILES.get(key)
            if expected is None:
                errors.append(f"VERSION_UNKNOWN_TEMPLATE:{name}:{template_id}")
                continue
            actual_signature = json_obj(snapshot.get("signaturePositionJson"))
            actual_seal = json_obj(snapshot.get("companySealPositionJson"))
            wanted_signature = APPENDED_CONFIRMATION_PAGE
            wanted_seal = APPENDED_CONFIRMATION_PAGE if expected[3] == "Y" else None
            snapshot_condition = json_obj(snapshot.get("matchConditionJson"))
            if (str(snapshot.get("sourceFileHash") or "").lower() != expected[2]
                    or snapshot.get("sourceFileUrl") != template.get("fileUrl")
                    or snapshot.get("employeeVisible") != "Y"
                    or snapshot.get("readConfirmationRequired") != "Y"
                    or snapshot.get("employeeSignRequired") != "Y"
                    or snapshot.get("companySealRequired") != expected[3]
                    or (key == "ONBOARD_CONFIDENTIAL_NONCOMPETE"
                        and snapshot_condition.get("postLevelScope") != "7级及以上")
                    or canonical(actual_signature) != canonical(wanted_signature)
                    or (canonical(actual_seal) if actual_seal else None)
                       != (canonical(wanted_seal) if wanted_seal else None)):
                errors.append(f"VERSION_TEMPLATE_POLICY_MISMATCH:{name}:{key}")

report = {
    "mode": mode,
    "readOnly": True,
    "businessMutationPerformed": False,
    "hrUserId": 940,
    "planReleaseMetrics": {
        "productionActiveTargetRouteCount": (
            active_target_route_count if mode == "postcheck" else None
        ),
        "productionTargetRouteConflictCount": (
            target_route_conflict_count if mode == "postcheck" else None
        ),
        "productionNonGlobalTargetPlanCount": (
            non_global_target_plan_count if mode == "postcheck" else None
        ),
        "isolatedUatPlanInProductionCount": (
            isolated_uat_plan_in_production_count if mode == "postcheck" else None
        ),
        "productionUnexpectedActiveRouteCount": (
            unexpected_global_active_route_count if mode == "postcheck" else None
        ),
        "productionInvalidActiveRuleCount": (
            invalid_active_rule_count if mode == "postcheck" else None
        ),
    },
    "errors": errors,
    "blockers": blockers,
    "ready": not errors and not blockers,
    "evidenceState": state_path,
}
Path(report_path).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
os.chmod(report_path, 0o600)
print(json.dumps(report, ensure_ascii=False))
raise SystemExit(0 if report["ready"] else (42 if blockers and not errors else 41))
PY
rc=$?
set -e
sha256sum "$EVIDENCE_DIR/report.json" >> "$EVIDENCE_DIR/evidence.sha256"
echo "READONLY_EVIDENCE_DIR=$EVIDENCE_DIR"
exit "$rc"
