const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const uiRoot = path.resolve(__dirname, "..")

function read(relativePath) {
  return fs.readFileSync(path.join(uiRoot, relativePath), "utf8")
}

const api = read("src/api/oa/signTask.js")
const page = read("src/views/oa/signTask/index.vue")
const dialog = read("src/views/oa/signTask/SignTaskBatchFinalizeDialog.vue")
const onboardCard = read("src/views/oa/signTask/SignOnboardCompanyWork.vue")
const onboardDialog = read("src/views/oa/signTask/SignOnboardCompanyWorkDialog.vue")
const signTaskExportPolicy = require("../src/views/oa/signTask/signTaskExportPolicy")

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function loadSignTaskComponent(apiOverrides = {}) {
  const scriptMatch = page.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, "sign task page script should exist")
  const script = scriptMatch[1]
    .replace(/^import[^\n]*\n/gm, "")
    .replace(/export default/, "module.exports =")
  const sandbox = {
    module: { exports: {} },
    exports: {},
    deleteSignTasksBatch: () => Promise.resolve({ data: {} }),
    getSignTaskCapabilities: () => Promise.resolve({ data: { excelImportEnabled: false } }),
    getSignTaskMetrics: () => Promise.resolve({ data: {} }),
    listSignTaskNotificationFailures: () => Promise.resolve({ data: [] }),
    listSignTasks: () => Promise.resolve({ rows: [], total: 0 }),
    SignScopeSelector: {},
    SignOnboardCompanyWork: {},
    SignTaskBatchFinalizeDialog: {},
    SignTaskDetailDrawer: {},
    SIGN_TASK_STATUS_LABELS: {},
    signTaskStatusLabel: value => value,
    SIGN_TASK_SCENARIO_OPTIONS: [],
    signScenarioLabel: value => value,
    signBusinessText: value => value,
    signTaskNumberLabel: value => value,
    require(request) {
      if (request === "@/utils/signScenario") {
        return { SIGN_TASK_SCENARIO_OPTIONS: [], signScenarioLabel: value => value }
      }
      if (request === "@/utils/signDisplayText") {
        return {
          signBusinessText: value => value,
          signTaskNumberLabel: value => value
        }
      }
      if (request === "./signTaskExportPolicy") return signTaskExportPolicy
      throw new Error(`Unexpected sign task require: ${request}`)
    },
    process: { env: { VUE_APP_SIGN_EXCEL_IMPORT_ENABLED: "false" } },
    Promise,
    Object,
    Array,
    String,
    Number,
    Boolean,
    Math,
    Date,
    Set,
    RegExp,
    TypeError,
    ...apiOverrides
  }
  vm.runInNewContext(script, sandbox, { filename: "oa/signTask/index.vue" })
  return sandbox.module.exports
}

function createSignTaskContext(component, overrides = {}) {
  const context = {
    ...component.data(),
    $route: { query: {} },
    $refs: {},
    $store: { dispatch() { return Promise.resolve() } },
    $nextTick(callback) {
      return Promise.resolve().then(() => callback && callback())
    },
    ...component.methods,
    ...overrides
  }
  Object.defineProperty(context, "hasValidSignScope", {
    configurable: true,
    get() { return component.computed.hasValidSignScope.call(context) }
  })
  Object.defineProperty(context, "excelImportAvailable", {
    configurable: true,
    get() { return component.computed.excelImportAvailable.call(context) }
  })
  if (!Object.prototype.hasOwnProperty.call(overrides, "canBatchFinalize")) {
    Object.defineProperty(context, "canBatchFinalize", {
      configurable: true,
      get() { return component.computed.canBatchFinalize.call(context) }
    })
  }
  Object.defineProperty(context, "canShowCompanyWork", {
    configurable: true,
    get() { return component.computed.canShowCompanyWork.call(context) }
  })
  Object.defineProperty(context, "signScopeComponentKey", {
    configurable: true,
    get() { return component.computed.signScopeComponentKey.call(context) }
  })
  return context
}

assert.ok(api.includes("/oa/signTask/batch/finalize/preview"),
  "batch company/seal workbench should use the server-side preview endpoint")
assert.ok(api.includes("/oa/signTask/batch/finalize"),
  "batch company/seal workbench should expose the execute endpoint")
;[
  "/oa/signTask/onboard/company-work",
  "/oa/signTask/onboard/company-work/options",
  "/oa/signTask/onboard/company-work/preview",
  "/oa/signTask/onboard/company-work/execute"
].forEach(endpoint => assert.ok(api.includes(endpoint), `signature-first company work should expose ${endpoint}`))
assert.ok(api.includes("silentError: true"),
  "batch workbench should render item-level failures instead of a duplicate global request toast")

assert.ok(page.includes("oa:signTask:batchFinalize"),
  "the batch company/seal entry should require its dedicated permission")
assert.ok(page.includes("SignTaskBatchFinalizeDialog"),
  "the task center should mount the batch workbench")
assert.ok(api.includes("/oa/signTask/capabilities") &&
  page.includes("getSignTaskCapabilities") &&
  page.includes("hasValidSignScope") &&
  page.includes("signCapabilitiesResolved") &&
  page.includes("canShowCompanyWork") &&
  page.includes(':key="signScopeComponentKey"'),
  "the task center must load capabilities before scoped data and gate the Excel workbench")
assert.ok(page.includes("请先选择签约组织") &&
  page.includes("return Promise.resolve(false)"),
  "a missing sign scope must clear the workspace without issuing scoped requests")
assert.ok(page.includes("batchFinalizeSelectedIds") && page.includes("selectedTasks"),
  "company/seal selection must remain independent from administrator hard-delete selection")
assert.ok(page.includes('type="selection"') && page.includes(":selectable=\"isTaskDeletable\""),
  "the existing administrator deletion selection should remain intact")
assert.ok(page.includes("String(task.status || '').toUpperCase() === 'PENDING_COMPANY'"),
  "only pending-company tasks may be selected")
assert.ok(page.includes("String(task.packageId || '')"),
  "tasks without a generated signing package must be rejected before preview")
assert.ok(page.includes("isExcelStagedPackageTask(task)") &&
  page.includes("'MANUAL_SIGN_EXCEL_IMPORT'") &&
  page.includes("!this.isExcelStagedPackageTask(task)"),
"Excel staged-shell tasks must be excluded from the generic batch-finalize selection")
assert.ok(page.includes("请在上方“Excel 签名优先 · 待选公司与印章”专区处理") &&
  page.includes("Excel 签名优先任务请使用上方“待选公司与印章”专区"),
"excluded staged-shell tasks must direct HR to the dedicated company/seal work area")

const finalizableMethodMatch = page.match(
  /    isTaskBatchFinalizable\(task\) \{\n([\s\S]*?)\n    \},\n    isExcelStagedPackageTask/
)
const stagedSourceMethodMatch = page.match(
  /    isExcelStagedPackageTask\(task\) \{\n([\s\S]*?)\n    \},\n    batchFinalizeDisabledReason/
)
assert.ok(finalizableMethodMatch && stagedSourceMethodMatch,
  "focused batch-finalize eligibility methods should remain executable in isolation")
const isExcelStagedPackageTask = new Function("task", stagedSourceMethodMatch[1])
const eligibilityHarness = { isExcelStagedPackageTask }
const isTaskBatchFinalizable = new Function("task", finalizableMethodMatch[1])
const pendingCompanyTask = { status: "PENDING_COMPANY", packageId: "301", sourceType: "LIFECYCLE" }
assert.strictEqual(isTaskBatchFinalizable.call(eligibilityHarness, pendingCompanyTask), true,
  "ordinary pending-company packages should remain available in the generic action")
assert.strictEqual(isTaskBatchFinalizable.call(eligibilityHarness, {
  status: "PENDING_COMPANY", packageId: "302", sourceType: "MANUAL_SIGN_EXCEL_IMPORT"
}), false, "Excel staged shells must not enter the generic batch-finalize dialog")
assert.strictEqual(isTaskBatchFinalizable.call(eligibilityHarness, {
  status: "PENDING_COMPANY", packageId: "303", taskSourceType: "manual_sign_excel_import"
}), false, "the compatibility taskSourceType field must enforce the same exclusion")
assert.strictEqual(isTaskBatchFinalizable.call(eligibilityHarness, {
  status: "READY_TO_SEND", packageId: "304", sourceType: "LIFECYCLE"
}), false, "non-pending-company tasks must remain excluded")
assert.ok(page.includes("this.batchFinalizeSelectedIds.length >= 20") && page.includes("\u5355\u6b21\u6700\u591a\u5904\u740620"),
  "the browser should enforce the 20-task batch limit")
assert.ok(page.includes("todo/invalidateAfterMutation") &&
  page.includes("handleBatchFinalizeCompleted"),
"successful mutations should refresh the list, metrics and todo summaries")

assert.ok(dialog.includes("previewSignTaskBatchFinalize({ taskIds })"),
  "preview requests should contain only normalized selected task ids")
;[
  "taskId: row.taskId",
  "packageId: row.packageId",
  "expectedTaskVersion: row.taskVersion",
  "expectedPackageVersion: row.packageVersion",
  "legalEntityId: this.normalizeId(row.legalEntityId)",
  "sealId: this.requiresSeal(row)",
  "correctionReason: String(row.correctionReason"
].forEach(fragment => assert.ok(dialog.includes(fragment), `execute payload should include ${fragment}`))
assert.ok(dialog.includes("blockerMessages: this.normalizeBlockers") && dialog.includes("rowBlockers(row)"),
  "server preview blockers and local required-field blockers must stop execution")
assert.ok(dialog.includes("item.blockingReasons || item.blockers") && dialog.includes("item.companyCandidates"),
  "the task workbench should consume the server's nested per-task candidate contract")
assert.ok(dialog.includes("availableContractSeals") && dialog.includes("recommendedLegalEntityId"),
  "task company and seal defaults should come from the authoritative preview")
assert.ok(dialog.includes("applyCompanyToAll") && dialog.includes("applySealToSameCompany"),
  "HR should be able to apply one company globally and an eligible seal to rows of that company")
assert.ok(dialog.includes("recommendationMismatch(row)") && dialog.includes("\u8bf7\u586b\u5199\u6539\u9009\u539f\u56e0"),
  "choosing a company different from the recommendation should require a reason")
assert.ok(dialog.includes("\u4e8c\u6b21\u786e\u8ba4") && dialog.includes("this.confirmed"),
  "formal contract generation should require both row review acknowledgement and a second confirmation")
assert.ok(dialog.includes("applyExecutionResults") && dialog.includes("resultMessage"),
  "partial success and per-item failures should remain visible in the dialog")
assert.ok(dialog.includes("expectedTaskVersion") && dialog.includes("expectedPackageVersion"),
  "optimistic-lock versions from preview must be frozen into execute requests")

assert.ok(page.includes("SignOnboardCompanyWork") && page.includes("handleOnboardCompanyWorkCompleted"),
  "the task center should include signature-first staged packages waiting for company and seal")
assert.ok(onboardCard.includes("sourceType: item.sourceType || 'ONBOARD_IMPORT_ROW'") &&
  onboardCard.includes("Excel \u7b7e\u540d\u4f18\u5148"),
"the separate work queue should identify its Excel staged-package source explicitly")
assert.ok(dialog.includes("本步骤只生成最终合同，不会自动发送") &&
  dialog.includes("预览后另行发送最终文件"),
"company/seal finalization must explain that generation and final-file sending are separate operations")
assert.ok(onboardCard.includes("this.selectedKeys.length >= 20") && onboardCard.includes("signatureCaptured === true"),
  "the signature-first queue should enforce signature evidence and the 20-row limit")
assert.ok(onboardCard.includes("sourceRowNumber || scope.row.rowId") &&
  onboardDialog.includes("sourceRowNumber || scope.row.rowId"),
"the work queue should show the original Excel row number rather than the database row id")
assert.ok(onboardDialog.includes("getOnboardSignCompanyWorkOptions") &&
  onboardDialog.includes("ensureCompanySeals(row.legalEntityId)"),
"company changes must reload seals through the dedicated batch-finalize options endpoint")
;[
  "batchId: row.batchId",
  "rowId: row.rowId",
  "version: row.version",
  "legalEntityId: this.normalizeId(row.legalEntityId)",
  "sealId: this.normalizeId(row.sealId)",
  "noExternalContractConfirmed: row.noExternalContractConfirmed === true",
  "historicalReason: String(row.historicalReason",
  "warningReason: String(row.warningReason"
].forEach(fragment => assert.ok(onboardDialog.includes(fragment), `onboard execute payload should include ${fragment}`))
assert.ok(onboardDialog.includes("previewOnboardSignCompanyWork(this.payload())") &&
  onboardDialog.includes("this.rows.every(row => row.result === 'READY')"),
"onboard company work must pass server preflight before execution")
assert.ok(onboardDialog.includes("\u4e8c\u6b21\u786e\u8ba4") && onboardDialog.includes("executeOnboardSignCompanyWork"),
  "signature-first formal draft generation should require a second confirmation")
assert.ok(onboardDialog.includes("\u4e0d\u4f1a\u5728\u672c\u6b65\u9aa4\u81ea\u52a8\u53d1\u9001") &&
  onboardDialog.includes("\u751f\u6210\u6b63\u5f0f\u5408\u540c\u8349\u7a3f"),
"the UI must accurately explain that execution creates a draft and does not auto-send")

;(async () => {
  const listRequests = []
  const metricRequests = []
  const notificationRequests = []
  const component = loadSignTaskComponent({
    listSignTasks: () => {
      const request = deferred()
      listRequests.push(request)
      return request.promise
    },
    getSignTaskMetrics: () => {
      const request = deferred()
      metricRequests.push(request)
      return request.promise
    },
    listSignTaskNotificationFailures: () => {
      const request = deferred()
      notificationRequests.push(request)
      return request.promise
    }
  })
  const context = createSignTaskContext(component, {
    signScopeResolved: true,
    selectedSignScope: { deptId: "17" },
    signCapabilitiesResolved: true,
    signCapabilities: { excelImportEnabled: false }
  })
  let companyWorkRefreshes = 0
  context.$refs.onboardCompanyWork = { refresh() { companyWorkRefreshes += 1 } }
  const firstRefresh = context.refreshAll()
  assert.deepStrictEqual(
    [listRequests.length, metricRequests.length, notificationRequests.length],
    [1, 1, 1],
    "organization A should issue one list, metric and notification request"
  )

  context.handleSignScopeChange({ deptId: "18" })
  assert.deepStrictEqual(
    [listRequests.length, metricRequests.length, notificationRequests.length],
    [2, 2, 2],
    "organization B should start a fresh request set"
  )
  listRequests[0].resolve({ rows: [{ taskId: "A" }], total: 1 })
  metricRequests[0].resolve({ data: { needsData: 99 } })
  notificationRequests[0].resolve({ data: [{ taskId: "A", notificationBusinessKey: "A" }] })
  await Promise.resolve()
  await Promise.resolve()
  assert.strictEqual(context.taskList.length, 0,
    "a response from organization A must be ignored after switching to B")
  assert.strictEqual(context.metricCounts.needsData, 0)
  assert.strictEqual(context.notificationFailures.length, 0)

  listRequests[1].resolve({ rows: [{ taskId: "B" }], total: 1 })
  metricRequests[1].resolve({ data: { needsData: 18 } })
  notificationRequests[1].resolve({ data: [{ taskId: "B", notificationBusinessKey: "B" }] })
  await Promise.all([firstRefresh, Promise.resolve(), Promise.resolve()])
  assert.deepStrictEqual(Array.from(context.taskList).map(row => row.taskId), ["B"],
    "the current organization response should populate the list")
  assert.strictEqual(context.metricCounts.needsData, 18)
  assert.deepStrictEqual(
    Array.from(context.notificationFailures).map(row => [row.taskId, row.notificationBusinessKey]),
    [["B", "B"]]
  )
  assert.strictEqual(context.loading, false)
  assert.strictEqual(context.metricLoading, false)
  assert.strictEqual(companyWorkRefreshes, 0,
    "the Excel company-work area remains closed when its capability is false")

  const keyedComponent = loadSignTaskComponent({
    process: { env: { VUE_APP_SIGN_EXCEL_IMPORT_ENABLED: "true" } }
  })
  const keyedContext = createSignTaskContext(keyedComponent, {
    signScopeResolved: true,
    selectedSignScope: { deptId: "17", deptType: "COMPANY" },
    signCapabilitiesResolved: true,
    signCapabilities: { excelImportEnabled: true },
    canBatchFinalize: true
  })
  assert.strictEqual(keyedContext.canShowCompanyWork, true,
    "the Excel company-work component should mount when both switches and permission are enabled")
  const oldScopeKey = keyedContext.signScopeComponentKey
  let oldRefRefreshes = 0
  keyedContext.$refs.onboardCompanyWork = {
    refresh() { oldRefRefreshes += 1 }
  }
  keyedContext.refreshAll()
  keyedContext.handleSignScopeChange({ deptId: "18", deptType: "COMPANY" })
  const newScopeKey = keyedContext.signScopeComponentKey
  assert.notStrictEqual(newScopeKey, oldScopeKey,
    "A to B organization changes must produce a different keyed component identity")
  assert.ok(newScopeKey.includes("18|COMPANY|"))
  assert.strictEqual(keyedContext.canShowCompanyWork, true)
  let newRefRefreshes = 0
  keyedContext.$refs.onboardCompanyWork = {
    refresh() { newRefRefreshes += 1 }
  }
  await Promise.resolve()
  await Promise.resolve()
  assert.strictEqual(oldRefRefreshes, 0,
    "a parent nextTick scheduled for A must not refresh the old company-work ref after switching to B")
  assert.strictEqual(newRefRefreshes, 1,
    "the current B keyed instance is the only company-work ref eligible for parent refresh")

  const secondListRequests = []
  const secondMetricRequests = []
  const secondNotificationRequests = []
  const secondComponent = loadSignTaskComponent({
    listSignTasks: () => {
      const request = deferred()
      secondListRequests.push(request)
      return request.promise
    },
    getSignTaskMetrics: () => {
      const request = deferred()
      secondMetricRequests.push(request)
      return request.promise
    },
    listSignTaskNotificationFailures: () => {
      const request = deferred()
      secondNotificationRequests.push(request)
      return request.promise
    }
  })
  const secondContext = createSignTaskContext(secondComponent, {
    signScopeResolved: true,
    selectedSignScope: { deptId: "17" },
    signCapabilitiesResolved: true,
    signCapabilities: { excelImportEnabled: false }
  })
  secondContext.refreshAll()
  secondContext.handleSignScopeChange({ deptId: "18" })
  secondListRequests[1].resolve({ rows: [{ taskId: "B2" }], total: 1 })
  secondMetricRequests[1].resolve({ data: { needsData: 182 } })
  secondNotificationRequests[1].resolve({ data: [{ taskId: "B2", notificationBusinessKey: "B2" }] })
  await Promise.resolve()
  await Promise.resolve()
  secondListRequests[0].resolve({ rows: [{ taskId: "A2" }], total: 1 })
  secondMetricRequests[0].resolve({ data: { needsData: 172 } })
  secondNotificationRequests[0].resolve({ data: [{ taskId: "A2", notificationBusinessKey: "A2" }] })
  await Promise.resolve()
  await Promise.resolve()
  assert.deepStrictEqual(Array.from(secondContext.taskList).map(row => row.taskId), ["B2"],
    "an earlier organization response cannot overwrite a newer response even when it returns last")
  assert.strictEqual(secondContext.metricCounts.needsData, 182)
  assert.strictEqual(secondContext.notificationFailures[0].taskId, "B2")

  let noScopeListCalls = 0
  let noScopeMetricCalls = 0
  let noScopeNotificationCalls = 0
  const noScopeComponent = loadSignTaskComponent({
    listSignTasks: () => { noScopeListCalls += 1; return Promise.resolve({ rows: [] }) },
    getSignTaskMetrics: () => { noScopeMetricCalls += 1; return Promise.resolve({ data: {} }) },
    listSignTaskNotificationFailures: () => { noScopeNotificationCalls += 1; return Promise.resolve({ data: [] }) }
  })
  const noScopeContext = createSignTaskContext(noScopeComponent, {
    signScopeResolved: true,
    selectedSignScope: null,
    signCapabilitiesResolved: true,
    $route: { query: { taskId: "900" } }
  })
  let noScopeCompanyWorkRefreshes = 0
  noScopeContext.$refs.onboardCompanyWork = {
    refresh() { noScopeCompanyWorkRefreshes += 1 }
  }
  noScopeContext.handleSignScopeChange(null)
  await noScopeContext.refreshAll()
  await noScopeContext.getList()
  await noScopeContext.loadMetrics()
  await noScopeContext.loadNotificationFailures()
  noScopeContext.openTargetFromRoute()
  await Promise.resolve()
  assert.deepStrictEqual(
    [noScopeListCalls, noScopeMetricCalls, noScopeNotificationCalls, companyWorkRefreshes],
    [0, 0, 0, 0],
    "without a valid organization no scoped endpoint or company-work refresh may run"
  )
  assert.strictEqual(noScopeCompanyWorkRefreshes, 0)
})().then(() => {
  console.log("sign task batch company/seal workbench tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
