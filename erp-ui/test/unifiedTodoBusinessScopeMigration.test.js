const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const sourcePath = path.join(repoRoot, "sql/erp_unified_todo_business_scope_20260713.sql")
const deployPath = path.join(repoRoot, "docker/mysql/db/erp_unified_todo_business_scope_20260713.sql")
const rollbackPath = path.join(repoRoot, "sql/erp_unified_todo_business_scope_rollback_20260713.sql")

const source = fs.readFileSync(sourcePath)
const forward = source.toString("utf8")
const rollback = fs.readFileSync(rollbackPath, "utf8")
const purgeName = "erp_oa_flowable_test_data_purge_20260714.sql"
const purgePath = path.join(repoRoot, "sql", purgeName)
const purge = fs.readFileSync(purgePath, "utf8")

assert.ok(forward.startsWith("-- 历史脚本：已被 2026-07-14 统一审批中心切换方案取代。"),
  "the retained migration must be visibly marked as historical")
assert.ok(!fs.existsSync(deployPath), "the superseded Flowable-aware migration must not keep a Docker mirror")
assert.ok(!fs.readFileSync(path.join(repoRoot, "docker/mysql/bootstrap-files.list"), "utf8")
  .split(/\r?\n/).map(line => line.trim()).includes("erp_unified_todo_business_scope_20260713.sql"),
"the superseded migration must not remain in active bootstrap")
assert.ok(forward.includes("UNIFIED_TODO_SCOPE_20260713_V1"), "migration must use a stable release batch")
assert.ok(rollback.includes("UNIFIED_TODO_SCOPE_20260713_V1"), "rollback must address the same release batch")
assert.ok(forward.includes("COLLATE utf8mb4_general_ci;"), "release batch variables must use the system-table collation")
assert.ok(rollback.includes("COLLATE utf8mb4_general_ci;"), "rollback batch variables must use the system-table collation")

const verificationCall = forward.indexOf("CALL verify_unified_todo_business_scope_20260713()")
const firstMutation = forward.indexOf("START TRANSACTION")
assert.ok(verificationCall >= 0 && firstMutation > verificationCall, "blocking verification must run before business mutations")
assert.ok(forward.includes("LOWER(status) = 'submitted'"), "OA submitted applications must block retirement")
assert.ok(forward.includes("INNER JOIN ACT_RU_TASK"), "OA runtime tasks must be checked")
assert.ok(forward.includes("FROM ACT_RU_EXECUTION"), "OA runtime instances must be checked")
assert.ok(forward.includes("PROC_DEF_ID_ LIKE 'oaPurchaseApproval:%'"), "only the OA purchase process must be checked")
assert.ok(forward.includes("feature.oa.purchase.enabled 必须先由管理员确认为 false"), "an existing enabled OA purchase flag must fail closed")

assert.ok(forward.includes("sys_unified_todo_config_backup_20260713"), "configuration state must be backed up")
assert.ok(forward.includes("sys_unified_todo_menu_backup_20260713"), "menu state must be backed up")
assert.ok(forward.includes("sys_unified_todo_role_menu_backup_20260713"), "role grants must be backed up")
assert.ok(forward.includes("PRIMARY KEY (release_batch, config_key)"), "configuration backup must be repeat-safe")
assert.ok(forward.includes("PRIMARY KEY (release_batch, menu_id)"), "menu backup must be repeat-safe")
assert.ok(forward.includes("PRIMARY KEY (release_batch, role_id, menu_id)"), "role backup must be repeat-safe")
assert.ok((forward.match(/COLLATE=utf8mb4_general_ci/g) || []).length >= 3, "backup tables must interoperate with the project's general-ci system tables")

assert.ok(forward.includes("'oa/purchase/index'"), "legacy purchase menu must be identified by component")
assert.ok(forward.includes("'oa/todo/index'"), "legacy OA todo menu must be identified by component")
assert.ok(forward.includes("'oa/done/index'"), "legacy OA done menu must be identified by component")
assert.ok(forward.includes("'inventory/purchase-request/index'"), "repurposed purchase-request menu must also be retired")
assert.ok(forward.includes("'inventory/purchase-request/history'"), "purchase-request history must also be retired")
assert.ok(forward.includes("LIKE 'oa:purchase:%'"), "purchase permissions must be identified independently of menu ids")
assert.ok(forward.includes("LIKE 'oa:todo:%'"), "legacy todo permissions must be identified independently of menu ids")
assert.ok(forward.includes("LIKE 'oa:done:%'"), "legacy done permissions must be identified independently of menu ids")
assert.ok(forward.includes("LIKE 'inv:purchase-request:%'"), "repurposed purchase-request permissions must be retired")
assert.ok(forward.includes("3002, 3003, 3004, 3005, 3006, 3007, 3008, 3009, 3010"), "known purchase-request ids must be reported for metadata validation")
assert.ok(forward.includes("3101, 3102, 3103, 3104, 3105, 3106, 3108"), "known OA purchase ids must be reported for metadata validation")
assert.ok(!forward.includes("inv:purchase:"), "inventory purchase permissions must remain outside OA retirement")

assert.ok(forward.includes("'feature.oa.purchase.enabled', 'false'"), "missing OA purchase flag must default off")
assert.ok(forward.includes("'todo.stock-check.due-soon.hours', '24'"), "missing stock-check warning threshold must default to 24 hours")
assert.ok(forward.match(/WHERE NOT EXISTS \([\s\S]*feature\.oa\.purchase\.enabled/), "OA feature insertion must preserve an administrator value")
assert.ok(forward.match(/WHERE NOT EXISTS \([\s\S]*todo\.stock-check\.due-soon\.hours/), "stock-check threshold insertion must preserve an administrator value")

assert.ok(forward.includes("COLUMN_NAME IN ('counter_user_id', 'counter_name', 'deadline')"), "stock-check responsibility columns must be verified")
assert.ok(forward.includes("'counter_user_id,status,deadline'"), "stock-check todo index order must be verified")
assert.ok(!forward.includes("ALTER TABLE inv_stock_check ADD"), "scope migration must not recreate or backfill responsibility fields")
assert.ok(forward.includes("counter_user_id IS NULL OR deadline IS NULL"), "unassigned active stock checks must be emitted for governance")
assert.ok(forward.includes("DATE_SUB(NOW(), INTERVAL 7 DAY)"), "stale drafts must be reported")

assert.ok(rollback.includes("SET menu.visible = backup.visible"), "rollback must restore menu visibility")
assert.ok(rollback.includes("INSERT IGNORE INTO sys_role_menu"), "rollback must restore original role grants repeat-safely")
assert.ok(rollback.includes("backup.existed = 0"), "rollback may delete only configuration created by this release")
assert.ok(rollback.includes("backup.config_key COLLATE utf8mb4_general_ci"), "rollback must tolerate backup tables created under either supported MySQL default collation")
assert.ok(rollback.includes("配置已偏离本批次默认值，回滚未自动删除"), "administrator changes must be retained and reported")
assert.ok(!rollback.includes("config_value = 'true'"), "rollback must never reopen OA purchase automatically")
assert.ok(!/\b(?:DELETE|TRUNCATE)\s+(?:TABLE\s+)?oa_purchase\b/i.test(rollback),
  "ordinary rollback must not perform the separately authorized OA purge")
assert.ok(!rollback.includes("DELETE FROM inv_stock_check"), "rollback must preserve stock-check history")

assert.ok(purge.includes("assert_oa_flowable_purge_preconditions") &&
  purge.includes("assert_oa_flowable_purge_result"),
"the separate destructive script must have precondition and postcondition gates")
assert.ok(!fs.existsSync(path.join(repoRoot, "docker/mysql/db", purgeName)),
  "the separately authorized purge must remain outside automatic migrations")

console.log("unifiedTodoBusinessScopeMigration tests passed")
