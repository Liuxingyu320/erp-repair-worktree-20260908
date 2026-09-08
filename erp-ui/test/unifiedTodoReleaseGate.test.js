const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const read = relativePath => fs.readFileSync(path.join(repoRoot, relativePath), "utf8")

const todoSettings = read("erp-ui/src/settings.js")
const todoDevelopmentEnv = read("erp-ui/.env.development")
const todoStagingEnv = read("erp-ui/.env.staging")
const todoProductionEnv = read("erp-ui/.env.production")
const releaseBuildValidator = read("erp-ui/scripts/validate-release-build.cjs")
const releaseInfoWriter = read("erp-ui/scripts/write-release-info.cjs")
const mobileTodoSource = read("erp-ui/src/views/mobile/todo/index.vue")

assert.ok(todoSettings.includes("todoQuickApproveEnabled") && todoSettings.includes("todoBatchApproveEnabled") &&
  todoSettings.includes("=== 'true'"),
"todo approval capabilities must fail closed unless their build flags are explicitly true")
for (const source of [todoDevelopmentEnv, todoStagingEnv, todoProductionEnv]) {
  assert.ok(/VUE_APP_TODO_QUICK_APPROVE_ENABLED\s*=\s*(?:true|false)/.test(source) &&
    /VUE_APP_TODO_BATCH_APPROVE_ENABLED\s*=\s*(?:true|false)/.test(source),
  "every frontend environment must explicitly configure both todo approval flags")
}
assert.ok(/VUE_APP_TODO_QUICK_APPROVE_ENABLED\s*=\s*true/.test(todoProductionEnv) &&
  /VUE_APP_TODO_BATCH_APPROVE_ENABLED\s*=\s*false/.test(todoProductionEnv),
"production phase one must enable quick approval while keeping batch approval off")
assert.ok(releaseBuildValidator.includes("validateBooleanFlag") &&
  releaseBuildValidator.includes("cannot be true while quick approval is disabled"),
"release validation must reject misspelled flags and an unsafe batch-only configuration")
assert.ok(releaseInfoWriter.includes("todoQuickApproveEnabled") &&
  releaseInfoWriter.includes("todoBatchApproveEnabled"),
"the machine-verifiable release marker must record the effective todo approval flags")
assert.ok(mobileTodoSource.includes("defaultSettings.todoQuickApproveEnabled === true") &&
  mobileTodoSource.includes("defaultSettings.todoBatchApproveEnabled === true"),
"mobile direct and batch approval must use the same fail-closed build flags as desktop")

const expectedMigrations = [
  "erp_inventory_purchase_return_item_20260713.sql",
  "erp_inventory_stock_check_scope_20260713.sql",
  "erp_inventory_receipt_quality_20260713.sql",
  "erp_inventory_performance_indexes_20260713.sql",
  "erp_inventory_warehouse_navigation_20260713.sql",
  "erp_hr_health_certificate_20260713.sql"
]

const releaseList = read("scripts/unified-todo-release-20260714.list")
  .split(/\r?\n/)
  .map(line => line.trim())
  .filter(line => line && !line.startsWith("#"))
const runner = read("scripts/verify-unified-todo-release.sh")
const performanceMigration = read("sql/erp_inventory_performance_indexes_20260713.sql")
const warehouseMigration = read("sql/erp_inventory_warehouse_navigation_20260713.sql")
const warehouseRollback = read("sql/erp_inventory_warehouse_navigation_rollback_20260713.sql")
const purgeName = "erp_oa_flowable_test_data_purge_20260714.sql"
const purge = read(`sql/${purgeName}`)
const bootstrapList = read("docker/mysql/bootstrap-files.list")
const bootstrapFiles = bootstrapList.split(/\r?\n/)
  .map(line => line.trim())
  .filter(line => line && !line.startsWith("#"))
const approvalGate = read("scripts/verify-unified-approval-cutover.sh")
const cleanInstallFixturePath = "scripts/fixtures/unified-approval-clean-install.sql"
const cleanInstallFixture = read(cleanInstallFixturePath)

assert.deepStrictEqual(releaseList, expectedMigrations,
  "unified-todo release must keep the reviewed dependency order")
assert.strictEqual(new Set(releaseList).size, releaseList.length,
  "unified-todo release list must not contain duplicates")

releaseList.forEach(name => {
  assert.ok(fs.existsSync(path.join(repoRoot, "sql", name)), `${name} must exist in sql/`)
})

assert.ok(runner.includes("set -Eeuo pipefail"), "release runner must fail closed")
assert.ok(runner.includes("EXPECTED_MIGRATIONS"), "runner must enforce the canonical order")
assert.ok(runner.includes("MySQL 8.0 or newer is required"), "runner must reject the MySQL 5.7 environment drift")
assert.ok(runner.includes("APPLY_UNIFIED_TODO_20260714"), "apply mode must require an explicit confirmation token")
assert.ok(runner.includes("GET_LOCK") && runner.includes("RELEASE_LOCK"),
  "the full migration set must run under one named lock")
assert.ok(runner.includes("verify-unified-approval-cutover.sh") &&
  !runner.includes("OA purchase runtime tasks") && !runner.includes("ACT_RU_"),
"the historical warehouse runner must delegate OA/Flowable cutover to the unified approval gate")
assert.ok(approvalGate.includes(cleanInstallFixturePath) &&
  !approvalGate.includes("docker/mysql/seed/") &&
  !approvalGate.includes("mysql --") && !approvalGate.includes("mysql -"),
"the independent approval cutover gate must use the sanitized fixture and remain static by default")
assert.ok(runner.includes("inventory/purchase/index") && runner.includes("inv:purchase:list"),
  "inventory purchase must be protected before OA retirement")
assert.ok(runner.includes("--preflight") && runner.includes("--checksums"),
  "runner must expose read-only preflight and checksum modes")
assert.ok(!runner.includes("DROP DATABASE"), "release runner must never drop a database")
assert.ok(!runner.includes(purgeName), "ordinary release runner must never invoke the one-time purge")
assert.ok(!/\b(?:DELETE|TRUNCATE)\s+(?:TABLE\s+)?oa_purchase\b/i.test(runner),
  "ordinary release runner must not destructively clean OA purchases")
assert.ok(!/\bDROP\s+TABLE[\s\S]*\bACT_/i.test(runner),
  "ordinary release runner must not remove Flowable tables")

assert.ok(fs.existsSync(path.join(repoRoot, "sql", purgeName)),
  "the reviewed one-time OA/Flowable purge must exist only in sql/")
assert.ok(!fs.existsSync(path.join(repoRoot, "docker/mysql/db", purgeName)),
  "the destructive purge must not have a Docker bootstrap mirror")
assert.ok(!bootstrapList.includes(purgeName),
  "the destructive purge must not be listed for automatic initialization")
assert.ok(!releaseList.includes(purgeName),
  "the destructive purge must not enter the ordinary release list")
assert.ok(!releaseList.includes("erp_unified_todo_business_scope_20260713.sql"),
  "the historical Flowable-aware scope migration must not remain in the ordinary release chain")
assert.ok(purge.includes("@expected_database := 'BossERP_NEW'") &&
  purge.includes("v_act_total_count <> 39") &&
  purge.includes("v_act_expected_count <> 39") &&
  purge.includes("v_flw_total_count <> 2") &&
  purge.includes("v_flw_expected_count <> 2"),
"the destructive purge must fail closed on database identity and the exact 39 ACT plus 2 FLW table sets")
assert.ok(purge.includes("TRUNCATE TABLE oa_purchase") &&
  purge.includes("DROP TABLE oa_purchase_comment") &&
  purge.includes("ACT_HI_IDENTITYLINK") &&
  purge.includes("FLW_RU_BATCH_PART") &&
  purge.includes("remaining_act_table_count") &&
  purge.includes("remaining_flw_table_count"),
"the whitelisted purge must explicitly clean and verify the complete current schema")

const destructiveOaOrActScripts = fs.readdirSync(path.join(repoRoot, "sql"))
  .filter(name => name.endsWith(".sql"))
  .filter(name => {
    const value = read(`sql/${name}`)
    return /\bTRUNCATE\s+TABLE\s+`?oa_purchase`?\b/i.test(value) ||
      /\bDROP\s+TABLE\s+`?oa_purchase_comment`?\b/i.test(value) ||
      (/\bDROP\s+TABLE\b/i.test(value) &&
        (/\bACT_HI_IDENTITYLINK\b/.test(value) || /\bFLW_RU_BATCH\b/.test(value)))
  })
assert.deepStrictEqual(destructiveOaOrActScripts, [purgeName],
  "only the reviewed whitelist script may destructively clean OA/Flowable data")

bootstrapFiles.forEach(name => {
  const value = read(`docker/mysql/db/${name}`)
  assert.ok(!/\b(?:ACT|FLW)_[A-Z0-9_]+\b/.test(value),
    `${name} must not depend on removed Flowable tables in active bootstrap`)
})
assert.ok(!bootstrapFiles.includes("erp_unified_todo_business_scope_20260713.sql"),
  "the superseded Flowable-aware scope script must not remain in active bootstrap")
assert.ok(!fs.existsSync(path.join(repoRoot, "docker/mysql/db/erp_unified_todo_business_scope_20260713.sql")),
  "the superseded Flowable-aware scope script must not keep an active Docker mirror")
assert.ok(!/CREATE TABLE `(?:ACT|FLW)_[A-Z0-9_]+`/.test(cleanInstallFixture),
  "the sanitized clean-install fixture must not recreate Flowable tables")
assert.ok(!/\boa_purchase_comment\b/.test(cleanInstallFixture) &&
  !/\b(?:process_instance_id|current_task_id|idx_oa_purchase_proc)\b/.test(cleanInstallFixture),
  "the sanitized clean-install fixture must not recreate the legacy OA purchase schema")
assert.ok(cleanInstallFixture.includes("`approval_instance_id` bigint") &&
  cleanInstallFixture.includes("`approval_round` int") && cleanInstallFixture.includes("`row_version` bigint") &&
  cleanInstallFixture.includes("`last_approval_event_key` varchar(128)") &&
  cleanInstallFixture.includes("idx_oa_purchase_approval_instance"),
  "the sanitized clean-install fixture must contain the unified OA approval columns and index")
assert.ok(cleanInstallFixture.includes("(3003, '我的待办（已退役）'") &&
  cleanInstallFixture.includes("(3004, '我的已办（已退役）'") &&
  cleanInstallFixture.includes("(3105, '待办导出（已退役）'") &&
  cleanInstallFixture.includes("(3106, '已办导出（已退役）'"),
  "the sanitized clean-install fixture must visibly retire legacy OA list and export menu nodes")
for (const retiredMenuId of [3003, 3004, 3105, 3106]) {
  assert.ok(!new RegExp(`\\(\\d+,\\s*${retiredMenuId}\\)`).test(cleanInstallFixture),
    `retired OA menu ${retiredMenuId} must not retain role grants in the clean-install fixture`)
}
assert.ok(cleanInstallFixture.includes("'oa:todo:approve'") && /\(\d+,\s*3103\)/.test(cleanInstallFixture),
  "the unified OA task approval permission must remain active and assigned in the sanitized fixture")
assert.ok(performanceMigration.includes("), ALGORITHM=INPLACE, LOCK=NONE"),
  "online index DDL must separate the index clause from ALTER options")
assert.ok(!performanceMigration.includes(") ALGORITHM=INPLACE, LOCK=NONE"),
  "invalid ALTER TABLE option syntax must not return")
assert.ok(warehouseMigration.includes("sys_menu_warehouse_nav_backup_20260713"),
  "warehouse navigation must snapshot mutable menu fields before changing them")
assert.ok(warehouseMigration.includes("@warehouse_nav_snapshot_exists = 0"),
  "warehouse role snapshots must be frozen on the first migration transaction")
assert.ok(warehouseRollback.includes("menu.icon = backup.icon") &&
  warehouseRollback.includes("menu.update_time = backup.update_time"),
  "warehouse rollback must restore exact menu values instead of approximate defaults")

const healthSource = fs.readFileSync(path.join(repoRoot, "sql/erp_hr_health_certificate_20260713.sql"))
const healthDeploy = fs.readFileSync(path.join(repoRoot, "docker/mysql/db/erp_hr_health_certificate_20260713.sql"))
const todoSource = read("sql/erp_unified_todo_business_scope_20260713.sql")
const healthApprovalSource = fs.readFileSync(path.join(repoRoot, "sql/erp_hr_health_certificate_unified_approval_20260714.sql"))
const healthApprovalDeploy = fs.readFileSync(path.join(repoRoot, "docker/mysql/db/erp_hr_health_certificate_unified_approval_20260714.sql"))
assert.ok(healthSource.equals(healthDeploy), "health-certificate source/deploy SQL must remain identical")
assert.ok(healthApprovalSource.equals(healthApprovalDeploy),
  "health-certificate approval source/deploy SQL must remain identical")
assert.ok(todoSource.startsWith("-- 历史脚本：已被 2026-07-14 统一审批中心切换方案取代。"),
  "the retained source migration must be explicitly marked as historical")

console.log("unifiedTodoReleaseGate tests passed")
