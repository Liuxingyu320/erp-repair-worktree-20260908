#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APPROVAL_SCHEMA_SQL="erp_unified_approval_schema_20260716.sql"
APPROVAL_SEED_SQL="erp_unified_approval_seed_20260716.sql"
MANUAL_CUTOVER_SQL="erp_unified_approval_center_20260714.sql"
MANUAL_INVENTORY_CUTOVER_SQL="erp_inventory_unified_approval_cutover_20260714.sql"
MANUAL_SQL_DIR="$ROOT_DIR/docker/mysql/manual"
HEALTH_EXPAND_SQL="erp_hr_health_certificate_unified_approval_20260714.sql"
INVENTORY_EXPAND_SQL="erp_inventory_unified_approval_expand_20260716.sql"
OA_EXPAND_SQL="erp_oa_purchase_unified_approval_expand_20260716.sql"
PURGE_SQL="erp_oa_flowable_test_data_purge_20260714.sql"
TODO_RELEASE_LIST="$ROOT_DIR/scripts/unified-todo-release-20260714.list"
NEW_BUSINESS_RELEASE_LIST="$ROOT_DIR/scripts/new-business-migrations-20260713.list"
CLEAN_INSTALL_FIXTURE="$ROOT_DIR/scripts/fixtures/unified-approval-clean-install.sql"

fail()
{
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

require_file()
{
    [[ -r "$1" ]] || fail "missing required file: $1"
}

require_dir()
{
    [[ -d "$1" ]] || fail "missing required directory: $1"
}

for migration in "$HEALTH_EXPAND_SQL" "$INVENTORY_EXPAND_SQL" \
    "$OA_EXPAND_SQL" "$APPROVAL_SCHEMA_SQL" "$APPROVAL_SEED_SQL"; do
    require_file "$ROOT_DIR/sql/$migration"
    require_file "$ROOT_DIR/docker/mysql/db/$migration"
    cmp -s "$ROOT_DIR/sql/$migration" "$ROOT_DIR/docker/mysql/db/$migration" \
        || fail "approval dependency differs from Docker bootstrap mirror: $migration"
    grep -Fxq "$migration" "$ROOT_DIR/docker/mysql/bootstrap-files.list" \
        || fail "approval dependency is absent from Docker bootstrap list: $migration"
    grep -Fxq "$migration" "$NEW_BUSINESS_RELEASE_LIST" \
        || fail "approval dependency is absent from new-business release list: $migration"
done
require_file "$ROOT_DIR/sql/$MANUAL_CUTOVER_SQL"
require_file "$MANUAL_SQL_DIR/$MANUAL_CUTOVER_SQL"
require_file "$ROOT_DIR/sql/$MANUAL_INVENTORY_CUTOVER_SQL"
require_file "$MANUAL_SQL_DIR/$MANUAL_INVENTORY_CUTOVER_SQL"
require_file "$ROOT_DIR/sql/$PURGE_SQL"
require_file "$ROOT_DIR/docker/mysql/bootstrap-files.list"
require_file "$CLEAN_INSTALL_FIXTURE"
require_file "$TODO_RELEASE_LIST"
require_file "$NEW_BUSINESS_RELEASE_LIST"
require_file "$ROOT_DIR/scripts/mobile-implementation-retest.cjs"
require_dir "$ROOT_DIR/erp-modules/erp-approval/src/main/java"
require_dir "$ROOT_DIR/erp-ui/src/views/approval/manage"
require_file "$ROOT_DIR/erp-modules/erp-approval/src/main/java/com/erp/approval/legacy/SystemLegacyApprovalBridge.java"
require_file "$ROOT_DIR/erp-modules/erp-approval/src/main/java/com/erp/approval/legacy/client/SystemLegacyApprovalClient.java"
require_file "$ROOT_DIR/erp-modules/erp-system/src/main/java/com/erp/system/controller/SystemLegacyApprovalController.java"
require_file "$ROOT_DIR/erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SystemLegacyApprovalService.java"
require_file "$ROOT_DIR/erp-modules/erp-system/src/main/resources/mapper/system/HrHealthCertificateLegacyApprovalMapper.xml"

cmp -s "$ROOT_DIR/sql/$MANUAL_CUTOVER_SQL" \
    "$MANUAL_SQL_DIR/$MANUAL_CUTOVER_SQL" \
    || fail 'manual approval cutover differs from its Docker mirror'
cmp -s "$ROOT_DIR/sql/$MANUAL_INVENTORY_CUTOVER_SQL" \
    "$MANUAL_SQL_DIR/$MANUAL_INVENTORY_CUTOVER_SQL" \
    || fail 'manual inventory cutover differs from its Docker mirror'

if grep -Fxq "$MANUAL_CUTOVER_SQL" "$ROOT_DIR/docker/mysql/bootstrap-files.list" \
    || grep -Fxq "$MANUAL_CUTOVER_SQL" "$NEW_BUSINESS_RELEASE_LIST"; then
    fail 'manual approval cutover must never enter automatic bootstrap/release lists'
fi
if grep -Fxq 'erp_inventory_unified_approval_cutover_20260714.sql' \
    "$ROOT_DIR/docker/mysql/bootstrap-files.list" \
    || grep -Fxq 'erp_inventory_unified_approval_cutover_20260714.sql' \
        "$NEW_BUSINESS_RELEASE_LIST"; then
    fail 'legacy inventory cutover must never enter automatic bootstrap/release lists'
fi
if rg -q "SET[[:space:]]+config_value[[:space:]]*=[[:space:]]*'true'" \
    "$ROOT_DIR/sql/$APPROVAL_SEED_SQL"; then
    fail 'automatic approval seed must not enable feature flags'
fi

if grep -Fxq "$PURGE_SQL" "$ROOT_DIR/docker/mysql/bootstrap-files.list"; then
    fail 'destructive OA/Flowable purge must never enter Docker bootstrap list'
fi

[[ ! -e "$ROOT_DIR/docker/mysql/db/$PURGE_SQL" ]] \
    || fail 'destructive OA/Flowable purge must not be copied into Docker bootstrap'

if grep -Fxq 'erp_unified_todo_business_scope_20260713.sql' \
    "$ROOT_DIR/docker/mysql/bootstrap-files.list"; then
    fail 'superseded Flowable todo gate must not remain in active Docker bootstrap'
fi
if grep -Fxq 'erp_unified_todo_business_scope_20260713.sql' \
    "$TODO_RELEASE_LIST"; then
    fail 'superseded Flowable todo gate must not remain in the ordinary release list'
fi

fixture_residue_hit="$(rg -n -m 1 \
    'ACT_[A-Z_]+|FLW_[A-Z_]+|process_instance_id|current_task_id|oa_purchase_comment' \
    "$CLEAN_INSTALL_FIXTURE" \
    2>/dev/null || true)"
[[ -z "$fixture_residue_hit" ]] \
    || fail "Flowable/OA legacy residue remains in the sanitized clean-install fixture: $fixture_residue_hit"

bootstrap_residue_hit="$(rg -n -m 1 \
    'ACT_[A-Z_]+|FLW_[A-Z_]+|process_instance_id|current_task_id|oa_purchase_comment' \
    "$ROOT_DIR/docker/mysql/db" \
    2>/dev/null || true)"
[[ -z "$bootstrap_residue_hit" ]] \
    || fail "Flowable/OA legacy residue remains in active Docker bootstrap: $bootstrap_residue_hit"

flowable_hit="$(rg -n -i -m 1 \
    'flowable|org\.flowable|\.bpmn' \
    "$ROOT_DIR/erp-modules/erp-oa/pom.xml" \
    "$ROOT_DIR/erp-modules/erp-oa/src/main" \
    2>/dev/null || true)"
[[ -z "$flowable_hit" ]] \
    || fail "Flowable production residue remains: $flowable_hit"

flowable_table_hit="$(rg -n -m 1 \
    '(^|[^A-Z0-9_])(ACT|FLW)_[A-Z0-9_]+' \
    "$ROOT_DIR/erp-modules/erp-oa/pom.xml" \
    "$ROOT_DIR/erp-modules/erp-oa/src/main" \
    2>/dev/null || true)"
[[ -z "$flowable_table_hit" ]] \
    || fail "Flowable table reference remains in production: $flowable_table_hit"

legacy_purchase_hit="$(rg -n -m 1 \
    'process_instance_id|current_task_id|processInstanceId|currentTaskId' \
    "$ROOT_DIR/erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaPurchase.java" \
    "$ROOT_DIR/erp-modules/erp-oa/src/main/resources/mapper/oa/OaPurchaseMapper.xml" \
    2>/dev/null || true)"
[[ -z "$legacy_purchase_hit" ]] \
    || fail "legacy OA purchase workflow fields remain: $legacy_purchase_hit"

legacy_oa_ui_hit="$(rg -n -m 1 \
    'listTodoPurchases|listDonePurchases|approvePurchase|/oa/purchase/(todo|done|approve)' \
    "$ROOT_DIR/erp-ui/src" 2>/dev/null || true)"
[[ -z "$legacy_oa_ui_hit" ]] \
    || fail "legacy OA purchase UI/API residue remains: $legacy_oa_ui_hit"

legacy_mobile_hit="$(rg -n -i -m 1 \
    'flowable|ACT_[A-Z_]+|FLW_[A-Z_]+|process_instance_id|current_task_id' \
    "$ROOT_DIR/scripts/mobile-implementation-retest.cjs" \
    2>/dev/null || true)"
[[ -z "$legacy_mobile_hit" ]] \
    || fail "retired mobile workflow script still contains legacy execution paths: $legacy_mobile_hit"
rg -q '旧审批引擎移动端回归脚本已退役' \
    "$ROOT_DIR/scripts/mobile-implementation-retest.cjs" \
    || fail 'legacy mobile workflow script does not explicitly refuse execution'
[[ ! -e "$ROOT_DIR/erp-ui/src/views/oa/todo/index.vue" ]] \
    || fail 'retired OA todo page still exists'
[[ ! -e "$ROOT_DIR/erp-ui/src/views/oa/done/index.vue" ]] \
    || fail 'retired OA done page still exists'
require_file "$ROOT_DIR/erp-ui/src/views/mobile/oa/purchaseApproval/index.vue"

for retired_menu_id in 3003 3004 3105 3106; do
    if rg -q "\\([0-9]+,[[:space:]]*${retired_menu_id}\\)" \
        "$CLEAN_INSTALL_FIXTURE"; then
        fail "retired OA menu still has a clean-install fixture role grant: ${retired_menu_id}"
    fi
done
rg -q "'oa:todo:approve'" \
    "$CLEAN_INSTALL_FIXTURE" \
    || fail 'OA native approval permission is missing from the clean-install fixture'
rg -q '\([0-9]+,[[:space:]]*3103\)' \
    "$CLEAN_INSTALL_FIXTURE" \
    || fail 'OA native approval permission has no clean-install fixture role grant'

for required in \
    approval_template approval_rule approval_rule_version approval_rule_condition approval_version_node \
    approval_instance approval_task approval_task_candidate approval_action_log \
    approval_callback_outbox approval_validation_run approval_validation_issue; do
    rg -q "CREATE TABLE.*${required}" "$ROOT_DIR/sql/$APPROVAL_SCHEMA_SQL" \
        || fail "approval schema is missing table: $required"
done

for business_code in \
    OA_PURCHASE INV_TRANSFER INV_STOCK_CHECK HR_HEALTH_CERTIFICATE; do
    rg -q "${business_code}" "$ROOT_DIR/sql/$APPROVAL_SEED_SQL" \
        || fail "approval seed is missing template: $business_code"
done

health_legacy_mapper="$ROOT_DIR/erp-modules/erp-system/src/main/resources/mapper/system/HrHealthCertificateLegacyApprovalMapper.xml"
rg -qi 'approval_instance_id[[:space:]]+is[[:space:]]+null' \
    "$health_legacy_mapper" \
    || fail 'health-certificate Legacy Bridge does not exclude native instances'
for legacy_status in PENDING_REVIEW APPROVED REJECTED; do
    rg -q "$legacy_status" "$health_legacy_mapper" \
        || fail "health-certificate Legacy Bridge is missing status: $legacy_status"
done
if rg -q "review_status in[^;]*'DRAFT'" "$health_legacy_mapper"; then
    fail 'health-certificate Legacy Bridge must not treat drafts as approval instances'
fi

health_todo_mapper="$ROOT_DIR/erp-modules/erp-system/src/main/resources/mapper/system/SysTodoMapper.xml"
rg -q "review_status in \('REJECTED', 'RETURNED'\)" "$health_todo_mapper" \
    || fail 'health-certificate returned todo does not include unified RETURNED state'

inventory_todo_mapper="$ROOT_DIR/erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml"
rg -q "right\(o.last_approval_event_key, 7\) = ':RETURN'" "$inventory_todo_mapper" \
    || fail 'native transfer RETURN is missing from applicant returned todos'
oa_todo_mapper="$ROOT_DIR/erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml"
rg -q "p.status = 'returned'" "$oa_todo_mapper" \
    || fail 'OA purchase RETURN is missing from applicant returned todos'

for permission in \
    'oa:todo:approve' 'inv:transfer:approve' \
    'inv:stockCheck:approve' 'hr:healthCertificate:review'; do
    rg -q "SELECT '${permission}', menu_id" "$ROOT_DIR/sql/$APPROVAL_SEED_SQL" \
        || fail "approval candidate route grant is missing: $permission"
done
rg -q 'tmp_approval_route_roles' "$ROOT_DIR/sql/$APPROVAL_SEED_SQL" \
    || fail 'approval candidate route-role contract is missing'
rg -q 'CREATE PROCEDURE expand_approval_route_ancestors' \
    "$ROOT_DIR/sql/$APPROVAL_SEED_SQL" \
    || fail 'approval route grants do not recursively include all M/C ancestors'
rg -q 'restore_oa_purchase_roles_sql' "$ROOT_DIR/sql/$APPROVAL_SEED_SQL" \
    || fail 'OA purchase role grants are not restored from the 20260713 audit backup'
rg -q "'feature.oa.purchase.enabled'.*" "$ROOT_DIR/sql/$APPROVAL_SEED_SQL" \
    || fail 'OA purchase fail-closed feature configuration is missing'

purge_sql="$ROOT_DIR/sql/$PURGE_SQL"
rg -q 'tmp_flowable_purge_tables' "$purge_sql" \
    || fail 'destructive purge lacks the explicit 41-table boundary set'
rg -q 'v_flowable_boundary_fk_count' "$purge_sql" \
    || fail 'destructive purge lacks cross-boundary FK fail-closed validation'
rg -q 'DECLARE EXIT HANDLER FOR SQLEXCEPTION' "$purge_sql" \
    || fail 'destructive purge lacks its foreign-key-check restoration handler'
rg -q 'SET FOREIGN_KEY_CHECKS = v_old_foreign_key_checks' "$purge_sql" \
    || fail 'destructive purge does not restore the original FK-check state'

task_service="$ROOT_DIR/erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalTaskService.java"
permission_recheck_count="$(
    rg -c 'requireCurrentApprovalPermission\(instance, candidate, operatorId\);' \
        "$task_service" || true
)"
[[ "$permission_recheck_count" == '2' ]] \
    || fail 'approve and close actions must both re-check current candidate permission'
rg -q 'String permission = candidatePermission\(instance, candidate\);' \
    "$task_service" \
    || fail 'approval actions do not resolve the candidate-specific live permission'
rg -q 'AuthUtil\.checkPermi\(permission\);' "$task_service" \
    || fail 'approval actions do not re-check the current login-session permission'
rg -q 'selectActiveUserById\(operatorId, permission\)' "$task_service" \
    || fail 'approval actions do not re-check the active directory permission'

definition_service="$ROOT_DIR/erp-modules/erp-approval/src/main/java/com/erp/approval/service/ApprovalDefinitionService.java"
definition_controller="$ROOT_DIR/erp-modules/erp-approval/src/main/java/com/erp/approval/controller/ApprovalDefinitionController.java"
rg -qF 'request.getRemark(), SecurityUtils.getUsername()' "$definition_controller" \
    || fail 'disable-rule request drops its operator remark'
rg -q 'rule.setRemark\(remark.trim\(\)\)' "$definition_service" \
    || fail 'disable-rule reason is not persisted on the rule'
for fixed_field in getStrategyType getApprovalMode getRequiredCount \
    getMissingPolicy getSelfPolicy getReturnAllowed getRejectAllowed \
    getStrategyConfig; do
    rg -q "$fixed_field" "$definition_service" \
        || fail "transfer fixed-chain validation is missing: $fixed_field"
done

runtime_mapper="$ROOT_DIR/erp-modules/erp-approval/src/main/resources/mapper/approval/ApprovalRuntimeMapper.xml"
rg -qF 'i.callback_status = #{callbackStatus}' "$runtime_mapper" \
    || fail 'runtime monitor callback-status filter is not applied'
rg -q 'template.template_name' "$runtime_mapper" \
    || fail 'runtime monitor does not project the template name'
rg -q 'current_task.node_name as current_node_name' "$runtime_mapper" \
    || fail 'runtime monitor does not project the current node name'

legacy_monitor="$ROOT_DIR/erp-modules/erp-approval/src/main/java/com/erp/approval/service/LegacyApprovalMonitorService.java"
rg -qF 'int needed = pageNum * pageSize' "$legacy_monitor" \
    || fail 'legacy monitor does not calculate the requested global page prefix'
rg -qF 'leadingPage(bridge, filter, needed)' "$legacy_monitor" \
    || fail 'legacy monitor does not page each bridge up to the requested prefix'

callback_controller="$ROOT_DIR/erp-modules/erp-approval/src/main/java/com/erp/approval/controller/ApprovalCallbackController.java"
rg -qF '@RequiresPermissions("approval:callback:replay")' \
    "$callback_controller" \
    || fail 'callback replay is not protected by its dedicated permission'
rg -q "'approval:callback:replay'" "$ROOT_DIR/sql/$APPROVAL_SEED_SQL" \
    || fail 'callback replay permission is missing from approval admin menus'

oa_purchase_service="$ROOT_DIR/erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaPurchaseServiceImpl.java"
oa_purchase_controller="$ROOT_DIR/erp-modules/erp-oa/src/main/java/com/erp/oa/controller/OaPurchaseController.java"
rg -qF 'requireEnabled(BusinessFeatureGate.PURCHASE)' "$oa_purchase_service" \
    || fail 'OA purchase submission is not protected by the fail-closed gate'
rg -qF '@GetMapping("/availability")' "$oa_purchase_controller" \
    || fail 'OA purchase availability endpoint is missing'
rg -q '"oa:purchase:query", "oa:purchase:add", "oa:todo:approve"' \
    "$oa_purchase_controller" \
    || fail 'OA returned applicants cannot read their purchase detail'

definition_api="$ROOT_DIR/erp-modules/erp-approval/src/main/java/com/erp/approval/controller/ApprovalDefinitionController.java"
rg -qF '@PostMapping("/rules/{id}/drafts")' "$definition_api" \
    || fail 'approval rule version-draft API is missing'
rg -qF 'STATUS_ACTIVE.equals(rule.getRuleStatus())' "$definition_service" \
    || fail 'published rule scope is not protected from direct PUT edits'

for ui_file in \
    api/approval/definition.js api/approval/monitor.js api/approval/task.js \
    api/approval/validation.js views/approval/manage/index.vue; do
    require_file "$ROOT_DIR/erp-ui/src/$ui_file"
done

printf '[PASS] unified approval cutover static gate passed\n'
