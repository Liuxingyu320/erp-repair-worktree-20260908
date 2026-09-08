const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

function read(relativePath, root = uiRoot) {
  return fs.readFileSync(path.join(root, relativePath), "utf8")
}

function assertFile(relativePath) {
  assert.ok(fs.existsSync(path.join(uiRoot, relativePath)), `${relativePath} should exist`)
}

;[
  "src/api/oa/signTask.js",
  "src/views/oa/signTask/index.vue",
  "src/views/oa/signTask/SignTaskDetailDrawer.vue"
].forEach(assertFile)

const api = read("src/api/oa/signTask.js")
;[
  "/oa/signTask/list",
  "/oa/signTask/metrics",
  "/oa/signTask/notification/failures",
  "/revalidate",
  "/send",
  "/retry",
  "/notification/retry",
  "/cancel",
  "/batch/delete"
].forEach(endpoint => assert.ok(api.includes(endpoint), `sign task API should expose ${endpoint}`))
assert.ok(!api.includes("/confirm"), "the retired HR review endpoint must not remain in the frontend API")

const page = read("src/views/oa/signTask/index.vue")
;[
  "待补资料",
  "待选公司盖章",
  "处理失败",
  "已查看未签",
  "即将逾期",
  "员工拒签",
  "本月已完成"
].forEach(label => assert.ok(page.includes(label), `task center should show metric ${label}`))
assert.ok(read("src/utils/signDictionary.js").includes("FAILED: '发送失败'"),
  "the shared task status dictionary should retain the business failure label")

;["HR专员", "HR负责人", "提交负责人审核", "复核人", "批准人", "任务转派"].forEach(forbidden => {
  assert.ok(!page.includes(forbidden), `single-HR center must not contain ${forbidden}`)
})

assert.ok(page.includes("SignTaskDetailDrawer"))
assert.ok(page.includes("SIGN_TASK_STATUS_LABELS") && page.includes("signTaskStatusLabel(value)"),
  "task list and drawer must reuse the shared task status dictionary")
assert.ok(page.includes("notificationBusinessKey"), "notification failure todo should open the retry entry")
assert.ok(page.includes("listSignTaskNotificationFailures") &&
  !page.includes("@/api/oa/todo") && !page.includes("listTodos"),
"task center notification recovery must use the signing-scope endpoint instead of unified inventory-scoped todos")
assert.ok(page.includes("Array.isArray(response.data)"),
  "task center should consume the minimal signing notification response")
assert.ok(page.includes("this.$route.query.taskId"), "targeted task links should open the requested task")
assert.ok(page.includes("getSignTaskMetrics") && page.includes("this.metricCounts = response.data"),
  "the task center should load all seven metrics from one backend aggregation request")
assert.ok(!page.includes("loadAllMetricRows") && !page.includes("METRIC_PAGE_SIZE"),
  "metrics must not page through signing history in the browser")
assert.ok(page.includes("['oa:signTask:query', 'oa:signTask:technicalEvidence']"),
  "task detail entry must allow business query or technical evidence permission")
assert.ok(page.includes("todo/refreshSummaries"),
  "read-only task-center refreshes should refresh todo summaries without invalidating caches")
assert.ok(!/["']todo\/refresh["']/.test(page),
  "task-center refreshes should not dispatch the legacy todo/refresh action")
assert.ok(page.includes("deleteSignTasksBatch") && page.includes('v-if="isAdmin"'),
  "only administrators should see and invoke the task hard-delete entry")
assert.ok(page.includes('type="selection"') && page.includes(':selectable="isTaskDeletable"'),
  "administrators should be able to select multiple eligible tasks")
assert.ok(page.includes("['SIGNED', 'NO_ACTION']") && page.includes("已完成的签约任务不能硬删除"),
  "final signing tasks must be protected before the request reaches the server")
assert.ok(page.includes("确认删除后不可恢复") && page.includes("irreversibleConfirmed: true"),
  "hard deletion must require an explicit irreversible-operation confirmation")
assert.ok(page.includes("deleteRequestId: ''") &&
  page.includes("this.deleteRequestId = this.createRequestId('hard-delete')") &&
  page.includes("requestId: this.deleteRequestId") &&
  !page.includes("requestId: this.createRequestId('hard-delete')"),
"hard-delete retries must reuse one durable request ID until the dialog is reset")
assert.ok(page.includes("const items = this.selectedTasks.map") &&
  page.includes("expectedVersion: Number(task.version)") &&
  page.includes("items,") && !page.includes("taskIds,"),
"each hard-delete action must carry the task version observed by the administrator")
assert.ok(page.includes("Number.isSafeInteger(Number(task.version))") &&
  page.includes("所选任务缺少有效版本"),
"tasks without a safe version must remain unselectable until the list is refreshed")
assert.ok(page.includes("todo/invalidateAfterMutation"),
  "successful hard deletion should invalidate todo caches")

const detail = read("src/views/oa/signTask/SignTaskDetailDrawer.vue")
assert.ok(detail.includes("todo/invalidateAfterMutation"),
  "successful sign-task mutations should invalidate todo caches")
assert.ok(!/["']todo\/refresh["']/.test(detail),
  "sign-task mutations should not dispatch the legacy todo/refresh action")

function methodSlice(source, methodName, nextMethodName) {
  const start = source.indexOf(`    ${methodName}(`)
  const end = source.indexOf(`    ${nextMethodName}(`, start)
  assert.ok(start >= 0 && end > start, `${methodName} should appear before ${nextMethodName}`)
  return source.slice(start, end)
}

const retryNotificationMethod = methodSlice(detail, "retryNotification", "cancel")
const runDetailActionMethod = methodSlice(detail, "runDetailAction", "refreshTodo")
;[retryNotificationMethod, runDetailActionMethod].forEach(method => {
  assert.ok(/\.then\s*\([\s\S]*this\.refreshTodo\s*\(\)/.test(method),
    "todo invalidation should run only after a successful mutation")
  assert.ok(!/\.finally\s*\([\s\S]*this\.refreshTodo\s*\(\)/.test(method),
    "failed mutations must not trigger success-style todo invalidation from finally")
})
assert.ok(
  /refreshTodo\s*\(\)\s*\{\s*return\s+this\.\$store\.dispatch\(["']todo\/invalidateAfterMutation["']\)\.catch\(\(\)\s*=>\s*\{\}\)\s*\}/.test(detail),
  "refreshTodo should return a self-contained invalidation promise"
)

const orderedSections = [
  "任务基本信息",
  "员工与组织资料",
  "合同与薪资字段",
  "匹配方案",
  "文件清单和预览",
  "系统校验结果",
  "任务处理"
]
let previousIndex = -1
orderedSections.forEach(section => {
  const index = detail.indexOf(section)
  assert.ok(index > previousIndex, `${section} should appear in the approved detail order`)
  previousIndex = index
})

;["验真通过", "旧版验真", "验真异常", "文件缺失", "尚未完成验真"].forEach(label => {
  assert.ok(detail.includes(label), `business validation should expose ${label}`)
})
const validationPresentationMethod = methodSlice(detail, "validationPresentation", "canRevalidate")
const verificationLabelMethod = methodSlice(detail, "verificationLabel", "verificationTagType")
;[validationPresentationMethod, verificationLabelMethod].forEach(method => {
  assert.ok(!method.includes("可发送") && !method.includes("不能发送"),
    "file verification must not claim to be the authoritative send-readiness result")
})
assert.ok(detail.includes("SIGNATURE_SAMPLE: '本任务手写签名样本'"),
  "technical verification should label the package-level one-time signature sample")
assert.ok(detail.includes("oa:signTask:technicalEvidence"))
assert.ok(detail.includes("canViewTechnicalEvidence"))
assert.ok(detail.includes("businessActionsAllowed") && detail.includes("technicalEvidenceView"),
  "server action mode must override wildcard frontend permissions")
assert.ok(detail.includes("hasConfirmation") && !detail.includes("!this.task.confirmedSnapshotHash"),
  "revalidation must use the safe confirmation flag instead of a redacted hash")
assert.ok(detail.includes('v-if="businessActionsAllowed"') &&
  !detail.includes("<sign-task-confirm-dialog") && !detail.includes("SignTaskConfirmDialog"),
"task mutations must be hidden in technical read-only mode and the retired HR review dialog must stay removed")
;["任务事件链", "签约包事件链", "expectedHash", "actualHash", "fileUrl", "expectedSize", "actualSize"].forEach(text => {
  assert.ok(detail.includes(text), `technical task view should expose ${text}`)
})
assert.ok(detail.includes("retrySignTaskNotification"))
assert.ok(detail.includes("downloadSignPackageDocument"))
assert.ok(detail.includes("补充资料") && detail.includes("/oa/sign-task/package"),
  "missing-data tasks should link the HR to the editable package snapshot")
assert.ok(detail.includes("canCreateDraft") && detail.includes(": { taskId: String(this.task.taskId)"),
  "a task without a package should open a task-bound draft form instead of calling revalidate into failure")
assert.ok(detail.includes("!!this.task.packageId") && detail.includes("canRevalidate"),
  "revalidation should only be offered after a task draft exists")
assert.ok(detail.includes("nextDetail.task.status === 'FAILED'"),
  "HTTP 200 responses that contain a FAILED task must not be reported as successful")
assert.ok(detail.includes("detailRequestSequence") && detail.includes("validationRequestSequence"),
  "task and verification requests should ignore stale responses when the drawer target changes")
assert.ok(detail.includes("requestedTaskId === String(this.taskId)"),
  "detail responses must stay bound to the task that initiated the request")

assert.ok(!fs.existsSync(path.join(uiRoot, "src/views/oa/signTask/SignTaskConfirmDialog.vue")),
  "the superseded HR review dialog must not be reintroduced")

const sql = read("sql/erp_oa_sign_task_center_20260711.sql", repoRoot)
const dockerSql = read("docker/mysql/db/erp_oa_sign_task_center_20260711.sql", repoRoot)
assert.strictEqual(sql, dockerSql, "root and Docker task-center migrations must stay identical")
assert.ok(sql.includes("合同签约中心"))
const repairSql = read("sql/erp_oa_sign_menu_permission_repair_20260716.sql", repoRoot)
const repairDockerSql = read("docker/mysql/db/erp_oa_sign_menu_permission_repair_20260716.sql", repoRoot)
assert.strictEqual(repairSql, repairDockerSql,
  "root and Docker signing menu repair migrations must stay identical")
assert.ok(repairSql.includes("menu_id BETWEEN 9650 AND 9669") && repairSql.includes("SIGNAL SQLSTATE '45000'"),
  "the forward migration must reserve the signing menu range without overwriting another feature")
assert.ok(repairSql.includes("AND NOT COALESCE(("),
  "NULL ownership fields must be treated as a conflict instead of bypassing the preflight")
assert.ok(repairSql.includes("BINARY path = BINARY 'sign-task'") &&
  repairSql.includes("BINARY route_name = BINARY 'OaSignTask'") &&
  repairSql.includes("menu_type = 'C'") && repairSql.includes("component IS NULL") &&
  repairSql.includes("menu_type = 'F'"),
"reserved menu ownership must validate the complete route identity before updating rows")
;[
  "oa:signTask:list",
  "oa:signTask:query",
  "oa:signTask:revalidate",
  "oa:signTask:send",
  "oa:signTask:retry",
  "oa:signTask:cancel",
  "oa:signTask:technicalEvidence",
  "oa:signTask:resolveRefusal",
  "oa:signTask:resolveExpiry",
  "oa:signPackage:send",
  "oa:signPackage:void",
  "oa:signCompany:list",
  "oa:signSeal:list"
].forEach(permission => assert.ok(repairSql.includes(permission), `menu repair SQL should define ${permission}`))
assert.ok(repairSql.includes("old_menu.menu_id = 4603") &&
  repairSql.includes("BINARY old_menu.perms = BINARY 'oa:signTask:confirm'"),
"the retired standalone confirmation permission must be removed only from its exact legacy identity")
assert.ok(repairSql.includes("query = VALUES(query)") &&
  repairSql.includes("is_frame = VALUES(is_frame)") &&
  repairSql.includes("is_cache = VALUES(is_cache)"),
"reruns must converge every declared structural menu field")
assert.ok(repairSql.includes("WHERE BINARY role_key = BINARY 'sign_single_hr'"),
  "managed signing role discovery must not repurpose a case-insensitive role-key variant")
assert.ok(repairSql.includes("sign.hr.user-id") && repairSql.includes("sys_user_role"),
  "task permissions should be assigned through the configured HR user")
assert.ok(repairSql.includes("task_menu.perms IN") && repairSql.includes("'oa:signPackage:list'") &&
  repairSql.includes("'oa:signPackage:query'") && repairSql.includes("'oa:signPackage:send'"),
"configured HR package permissions should be resolved by stable permission keys rather than reused menu IDs")
assert.ok(!repairSql.includes("task_menu.menu_id IN (4520, 4521, 4522"),
  "configured HR permissions must not depend on menu IDs reused by later migrations")
assert.ok(repairSql.includes("sys_sign_hr_menu_grant") && repairSql.includes("sync_sign_hr_permissions"),
  "HR permission assignment should be repeatable and revoke only grants managed by this feature")
assert.ok(repairSql.includes("tmp_sign_eligible_role_20260716") &&
  repairSql.includes("evidence_menu.menu_id IN (4520, 4521, 4522, 4523, 4524, 4525,") &&
  repairSql.includes("4605, 4606, 4608, 4609, 4610)") &&
  repairSql.includes("JOIN tmp_sign_eligible_role_20260716 eligible"),
"legacy role grants must migrate only when a non-conflicting signing permission proves ownership")
assert.ok(repairSql.includes("JOIN sys_menu old_entry ON old_entry.menu_id = role_menu.menu_id") &&
  repairSql.includes("old_entry.menu_id = 4600") &&
  repairSql.includes("BINARY old_entry.perms = BINARY 'oa:signTask:list'") &&
  !repairSql.includes("SELECT eligible.role_id, 9650"),
"the task-center entry must migrate only from a provable legacy task entry, not package-only grants")
const legacyPermissionSnapshot = repairSql.slice(
  repairSql.indexOf("INSERT IGNORE INTO tmp_sign_role_permission_20260716 (role_id, perms)"),
  repairSql.indexOf("INSERT IGNORE INTO tmp_sign_role_permission_20260716 (role_id, perms)",
    repairSql.indexOf("INSERT IGNORE INTO tmp_sign_role_permission_20260716 (role_id, perms)") + 1))
assert.ok(legacyPermissionSnapshot.includes("old_menu.menu_id = 4600") &&
  legacyPermissionSnapshot.includes("old_menu.menu_type = 'C'"),
"legacy permission snapshots must not turn a hybrid/collided 4600 row into task-center access")
const legacyPackageReachability = repairSql.slice(
  repairSql.indexOf("-- Keep the legacy sign-package C route independently reachable"),
  repairSql.indexOf("-- Migrate the task-center entry only"))
;[
  "SELECT package_role.role_id, oa_root.menu_id",
  "package_root.menu_id = 4520",
  "package_root.parent_id = 3000",
  "BINARY package_root.path = BINARY 'sign-package'",
  "BINARY package_root.component = BINARY 'oa/signPackage/index'",
  "BINARY package_root.route_name = BINARY 'OaSignPackage'",
  "package_root.menu_type = 'C'",
  "BINARY package_root.perms = BINARY 'oa:signPackage:list'",
  "oa_root.menu_id = 3000",
  "oa_root.parent_id = 0",
  "BINARY oa_root.path = BINARY 'oa'",
  "BINARY oa_root.route_name = BINARY 'OaRoot'"
].forEach(contract => assert.ok(legacyPackageReachability.includes(contract),
  `legacy package reachability must retain ${contract}`))
assert.ok(!legacyPackageReachability.includes("9650"),
  "legacy package reachability must never grant the task-center entry")
const taskCenterEntryMigration = repairSql.slice(
  repairSql.indexOf("-- Migrate the task-center entry only"),
  repairSql.indexOf("-- Remove links only from exact legacy signing identities"))
assert.ok(taskCenterEntryMigration.includes("SELECT role_menu.role_id, 9650") &&
  taskCenterEntryMigration.includes("old_entry.menu_id = 4600") &&
  taskCenterEntryMigration.includes("BINARY old_entry.component = BINARY 'oa/signTask/index'") &&
  taskCenterEntryMigration.includes("BINARY old_entry.perms = BINARY 'oa:signTask:list'") &&
  !taskCenterEntryMigration.includes("4520") &&
  !taskCenterEntryMigration.includes("package_root") &&
  !taskCenterEntryMigration.includes("package_role"),
"only a provable legacy task route may receive the task-center entry")
const legacyRoleCleanup = repairSql.slice(
  repairSql.indexOf("DELETE role_menu"), repairSql.indexOf("-- Disable only the legacy task root"))
const legacyRootDisable = repairSql.slice(
  repairSql.indexOf("-- Disable only the legacy task root"),
  repairSql.indexOf("-- The one-time confirmation action"))
assert.ok(legacyRoleCleanup.includes("old_menu.menu_type IN ('C', 'M')") &&
  legacyRootDisable.includes("old_menu.menu_type IN ('C', 'M')") &&
  [4520, 4521, 4522, 4523, 4524, 4525, 4526].every(menuId =>
    !legacyRoleCleanup.includes(String(menuId))) &&
  !legacyRootDisable.includes("4520") &&
  !legacyRootDisable.includes("BINARY old_menu.path = BINARY 'sign-package'") &&
  !legacyRootDisable.includes("BINARY old_menu.route_name = BINARY 'OaSignPackage'"),
"legacy task nodes must be retired while the independent package route and grants remain active")
assert.ok(repairSql.includes("old_menu.menu_id = 4607") &&
  repairSql.includes("old_menu.perms = 'oa:signTask:technicalEvidence'"),
"legacy technical-evidence grants must migrate independently without proving business access")
assert.ok(/INSERT\s+INTO\s+sys_role\b/i.test(repairSql) && repairSql.includes("sign_single_hr") && repairSql.includes("managed_role_id"),
  "migration must maintain one stable dedicated single-HR role")
const permissionProcedure = repairSql.slice(
  repairSql.indexOf("CREATE PROCEDURE sync_sign_hr_permissions()"),
  repairSql.indexOf("DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_plan"))
assert.ok(!permissionProcedure.includes("'oa:signTask:confirm'"),
  "dedicated HR role must not receive retired confirmation permission")
assert.ok(!permissionProcedure.includes("'oa:signTask:technicalEvidence'"),
  "dedicated HR role must not receive technical evidence permission")
assert.ok(permissionProcedure.includes("'oa:signPackage:add'"),
  "dedicated HR role must retain task-bound draft creation until that permission is split")
assert.ok(permissionProcedure.includes("managed.menu_id IN (") &&
  !permissionProcedure.includes("DELETE FROM sys_sign_hr_menu_grant;"),
"base synchronization must revoke only signing grants and retain transfer/offboarding ownership")
assert.ok(permissionProcedure.includes("managed_role_count") && permissionProcedure.includes("managed_role_key"),
  "managed role state must fail closed instead of repurposing an unrelated role")
assert.ok(!permissionProcedure.includes("AND task_menu.menu_id <> 9656"),
  "permission refresh must not sweep independently administered signing permissions")
assert.ok(!permissionProcedure.includes("DELETE FROM sys_role_menu\n         WHERE role_id = managed_sign_role_id;"),
  "permission refresh must not erase independently administered technical evidence grants")
assert.ok(repairSql.includes("IF transfer_menu_count <> 1 THEN") &&
  repairSql.includes("IF offboard_menu_count <> 1 THEN"),
"lifecycle wrappers must fail closed when a permission key has zero or multiple active menu owners")

const packagePage = read("src/views/oa/signPackage/index.vue")
assert.ok(packagePage.includes("this.$route.query.taskId") && packagePage.includes("taskId: task.taskId"),
  "the package editor should create a draft bound to the originating task")
assert.ok(
  packagePage.includes('v-model="packageForm.scenario"') &&
    packagePage.includes(':disabled="!!packageForm.taskId"'),
  "task-bound package editing must keep the task scenario read-only"
)
const router = read("src/router/index.js")
assert.ok(router.includes("path: '/oa/sign-task/package'"),
  "task-bound package editing should have a stable hidden route independent of legacy menu IDs")

console.log("signTaskCenter tests passed")
