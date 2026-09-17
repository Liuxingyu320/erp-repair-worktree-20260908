const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const repositoryRoot = path.resolve(root, "..")
const read = file => fs.readFileSync(path.join(root, file), "utf8")
const paths = {
  index: "src/views/hr/onboarding/index.vue",
  list: "src/views/hr/onboarding/components/HrOnboardingListPane.vue",
  detail: "src/views/hr/onboarding/components/HrOnboardingDetailPane.vue",
  config: "src/views/hr/onboarding/onboardingFieldConfig.js",
  create: "src/views/hr/onboarding/components/HrOnboardingCreateDialog.vue",
  edit: "src/views/hr/onboarding/components/HrOnboardingEditDrawer.vue",
  confirm: "src/views/hr/onboarding/components/HrOnboardingConfirmDialog.vue"
}

for (const [name, file] of Object.entries(paths)) {
  assert.ok(fs.existsSync(path.join(root, file)), `${name} workbench file must exist`)
}

const index = read(paths.index)
const list = read(paths.list)
const detail = read(paths.detail)
const configSource = read(paths.config)
const createDialog = fs.existsSync(path.join(root, paths.create)) ? read(paths.create) : ""
const editDrawer = fs.existsSync(path.join(root, paths.edit)) ? read(paths.edit) : ""
const confirmDialog = fs.existsSync(path.join(root, paths.confirm)) ? read(paths.confirm) : ""
const menuSql = fs.readFileSync(path.join(repositoryRoot, "sql/erp_user_hr_onboarding_20260710.sql"), "utf8")
const backendRules = fs.readFileSync(path.join(repositoryRoot,
  "erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrOnboardingRuleService.java"), "utf8")

assert.ok(index.includes("hr-onboarding-workbench"))
assert.ok(index.includes("hr-onboarding-workbench--reference"), "workbench must expose the reference-layout contract")
assert.ok(index.includes("hr-onboarding-heading__actions"), "heading actions must use the compact reference grouping")
assert.ok(index.includes("autoSelectFirstRow"), "the first visible record must become the default detail")
assert.ok(index.includes("HrOnboardingListPane"))
assert.ok(index.includes("HrOnboardingDetailPane"))
assert.ok(index.includes('name: "HrOnboarding"'), "component name must match the menu keep-alive contract")
assert.ok(menuSql.includes("'hr/onboarding/index', 'HrOnboarding'"), "menu SQL must name the onboarding route component")
assert.ok(!index.includes('default-employee-status="待入职"'))
assert.ok(list.includes("DRAFT") && list.includes("READY") && list.includes("CONFIRMED") && list.includes("CANCELLED"))
for (const token of [
  "status-tab-label", "status-count", "filter-trigger", "result-count",
  "onboarding-row__arrow", "onboarding-list-footer"
]) assert.ok(list.includes(token), `missing reference list token: ${token}`)
for (const label of ["待补资料", "待到岗", "已入职", "已取消"]) {
  assert.ok(configSource.includes(label), `missing reference status label: ${label}`)
}
for (const key of ["keyword", "expectedEntryDateFrom", "expectedEntryDateTo", "targetDeptId", "targetStoreId", "employeeCategory", "ownerUserId"]) {
  assert.ok(list.includes(key), `server list filter ${key} must be present`)
}
assert.ok(index.includes("checkPermi"), "selection must use the runtime permission helper")
assert.ok(index.includes(':can-select="canSelect"') && index.includes(':can-query="canQuery"'))
assert.ok(detail.includes("allowedActions"))
assert.ok(detail.includes("missingOnboardingFields"))
assert.ok(detail.includes("missingProfileFields"))
assert.ok(detail.includes("operationLogs"))
assert.ok(detail.includes("presentedOperationLogs"), "operation logs must use the human-readable presenter")
assert.ok(!detail.includes("log.summary"), "raw backend summaries must not be rendered")
assert.ok(!detail.includes("log.operationType"), "raw operation codes must not be rendered")
assert.ok(detail.includes("confirmDisabledReason"), "disabled confirmation must explain the next step")
assert.ok(detail.includes("postEntryFieldCount"), "post-entry profile gaps must be counted separately from onboarding blockers")
assert.ok(detail.includes("入职必填完成度"))
assert.ok(!detail.includes("<span>资料完整度</span>"))
assert.ok(detail.includes("档案覆盖度"))
assert.ok(index.includes("getHrEmployee(detail.linkedUserId)"), "linked confirmed employees must load formal profile coverage")
assert.ok(detail.includes("当前无确认入职阻塞项") && detail.includes("可后续完善（不影响确认入职）"),
  "post-entry profile gaps must be labelled as non-blocking follow-up work")
assert.ok(detail.includes("accountConfigurationRiskCodes"))
assert.ok(detail.includes("readinessBlockingCodes"), "detail must explain server-owned readiness blockers")
assert.ok(detail.includes("流程阻塞原因"), "readiness blockers need a visible user-facing section")
for (const token of [
  "detail-identity", "detail-facts", "onboarding-stage-track",
  "missing-material-row", "missing-badge", "primary-confirm-action"
]) assert.ok(detail.includes(token), `missing reference detail token: ${token}`)
assert.ok(!detail.includes("grid-template-columns: 105px"), "1280px detail must keep the completeness label on one line")
assert.ok(!detail.includes("grid-template-columns: 132px"), "1280px material groups must keep Chinese labels on one line")
assert.ok(index.includes("empty-selection"))
assert.ok(index.includes("新建入职"))
assert.ok(index.includes("listRequestSequence"), "list requests must ignore stale responses")
assert.ok(index.includes("detailRequestSequence"), "detail requests must ignore stale responses")
assert.ok(!detail.includes("onboardingCompletionPercent === 100"), "readiness must not be inferred from completeness")
assert.ok(configSource.includes('ACCOUNT_CONFIGURATION_MISSING: "账号或权限配置缺失"'))
assert.ok(backendRules.includes('"ACCOUNT_CONFIGURATION_MISSING"'), "frontend risk label must match a backend risk code")

for (const key of ["employeeName", "phoneNumber", "expectedEntryDate", "targetDeptId", "targetPostId", "employeeCategory", "ownerUserId"]) {
  assert.ok(createDialog.includes(key), `quick create field ${key} must be present`)
}
assert.ok(
  createDialog.includes("创建并继续下一位") &&
    createDialog.includes("prepareNextPerson") &&
    createDialog.includes("employeeName: \"\"") &&
    createDialog.includes("phoneNumber: \"\""),
  "quick create must keep shared org fields and clear personal identity for the next person"
)
assert.ok(createDialog.includes("append-to-body"),
  "quick create dialog must escape the desktop stacking context so its modal cannot cover the dialog")
assert.ok(editDrawer.includes("append-to-body"),
  "onboarding editor must escape the desktop stacking context so its modal cannot cover the drawer")
assert.ok(confirmDialog.includes("append-to-body"),
  "onboarding confirmation must escape the desktop stacking context so its modal cannot cover the dialog")
for (const step of ["基础信息", "组织岗位", "身份与紧急联系人", "用工合同与社保"]) {
  assert.ok(`${editDrawer}\n${configSource}`.includes(step), `edit step ${step} must be present`)
}
for (const key of ["actualEntryDate", "conflictAction", "version", "idempotencyKey"]) {
  assert.ok(confirmDialog.includes(key), `confirmation field ${key} must be present`)
}
assert.ok(createDialog.includes("fieldErrors") && editDrawer.includes("fieldErrors") && confirmDialog.includes("fieldErrors"),
  "all write flows must render backend field errors")
assert.ok(confirmDialog.includes("candidateOnboardingId"), "every conflict summary must identify onboarding candidates")
assert.ok(confirmDialog.includes("conflict-summary"), "every conflict must render before bind selection")
assert.ok(confirmDialog.includes("conflictLabel(candidate)"), "conflict types must render through the Chinese label mapper")
assert.ok(!confirmDialog.includes("candidate.conflictType ||"), "raw conflict codes must never be used as display fallbacks")
for (const safeCandidateKey of ["name", "maskedPhone", "departmentLabel"]) {
  assert.ok(confirmDialog.includes(safeCandidateKey), `bind candidates must render safe ${safeCandidateKey}`)
}
for (const forbiddenCandidateKey of ["candidate.phoneNumber", "candidate.idNumber", "candidate.bankAccount", "candidate.currentAddress"]) {
  assert.ok(!confirmDialog.includes(forbiddenCandidateKey), `bind candidates must not render ${forbiddenCandidateKey}`)
}
for (const componentSource of [createDialog, editDrawer, confirmDialog]) {
  assert.ok(!componentSource.includes("localStorage") && !componentSource.includes("sessionStorage"),
    "sensitive values and one-time passwords must not be persisted")
  assert.ok(componentSource.includes("beforeDestroy()") && componentSource.includes("this.invalidateLifecycle()"),
    "dialog destruction must invalidate pending writes")
}
assert.ok(editDrawer.includes(':close-on-press-escape="!submitting"'), "edit drawer must block Escape while submitting")
assert.ok(index.includes("markHrOnboardingReady") && index.includes("returnHrOnboardingToDraft"))
assert.ok(index.includes("cancelHrOnboarding") && index.includes("restoreHrOnboarding"))

for (const cssContract of [
  "height: calc(100vh - 180px)",
  ".hr-onboarding-detail-shell",
  "min-height: 0",
  ".detail-scroll",
  "overflow-y: auto",
  "flex-shrink: 0"
]) assert.ok(`${index}\n${detail}`.includes(cssContract), `missing layout contract: ${cssContract}`)

function loadConfig() {
  const source = configSource
    .replace(/export const /g, "const ")
    .replace(/export function /g, "function ")
  const sandbox = { module: { exports: {} }, exports: {} }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(`${source}\nmodule.exports = { ONBOARDING_ACTIONS, ONBOARDING_STATUS_TABS, ACCOUNT_RISK_LABELS, READINESS_BLOCKER_LABELS, visibleOnboardingActions, statusMeta, onboardingStage, employeeInitial, missingGroupLabel, postEntryMissingFieldGroups, presentOnboardingLog, QUICK_CREATE_FIELDS, EDIT_STEPS, DERIVED_ONBOARDING_FIELDS, SENSITIVE_ONBOARDING_FIELDS, buildOnboardingUpdatePayload, firstErrorLocation, eligibleBindCandidates, eligibleRehireCandidates, preferredConflictSelection, isOnboardingMaskPlaceholder, createIdempotencyKey, onboardingErrorBody, isOnboardingVersionConflict, sanitizeConfirmResult }`, sandbox, {
    filename: paths.config
  })
  return sandbox.module.exports
}

const config = loadConfig()
assert.strictEqual(config.onboardingStage("DRAFT"), 2)
assert.strictEqual(config.onboardingStage("READY"), 3)
assert.strictEqual(config.onboardingStage("CONFIRMED"), 5)
assert.strictEqual(config.onboardingStage("CANCELLED"), 1)
assert.strictEqual(config.employeeInitial("QA确认验收0712"), "确")
assert.strictEqual(config.employeeInitial("Alice 42"), "A")
assert.strictEqual(config.missingGroupLabel("IDENTITY"), "身份资料")
assert.strictEqual(config.missingGroupLabel("ORGANIZATION"), "组织岗位")
assert.strictEqual(config.missingGroupLabel("EMPLOYMENT"), "合同社保")
assert.strictEqual(config.READINESS_BLOCKER_LABELS.DERIVED_DEPARTMENT_SUPERVISOR_MISSING,
  "入职资料已保存；目标组织未配置部门负责人，请由管理员在组织管理中补充后刷新")
const stateLog = config.presentOnboardingLog({
  operationType: "STATE_CHANGE", fromStatus: "READY", toStatus: "DRAFT",
  operatorName: "admin", operationTime: "2026-07-12T08:20:29.000Z", changedFieldKeys: ["status"]
})
assert.strictEqual(stateLog.title, "退回待补资料")
assert.strictEqual(stateLog.description, "状态由“待到岗”变为“待补资料”")
assert.strictEqual(stateLog.time, "2026-07-12 16:20")
const updateLog = config.presentOnboardingLog({
  operationType: "UPDATE", operatorName: "admin", changedFieldKeys: ["targetStoreId", "remark", "idNumber"]
})
assert.strictEqual(updateLog.title, "更新入职资料")
assert.strictEqual(updateLog.description, "修改了目标门店、备注、证件号码")
assert.ok(!JSON.stringify(updateLog).includes("targetStoreId"), "activity presenter must not expose backend field keys")
assert.strictEqual(config.presentOnboardingLog({ operationType: "UPDATE", changedFieldKeys: [] }), null,
  "empty historical updates must be hidden")
const postEntryOnly = config.postEntryMissingFieldGroups(
  { IDENTITY: [{ key: "phoneNumber", label: "手机号" }, { key: "idNumber", label: "证件号码" }] },
  { IDENTITY: [{ key: "phoneNumber", label: "手机号" }] }
)
assert.deepStrictEqual(JSON.parse(JSON.stringify(postEntryOnly)), [{
  group: "IDENTITY",
  fields: [{ key: "idNumber", label: "证件号码" }]
}], "a field already blocking onboarding must not also appear as post-entry follow-up")
assert.ok(index.includes("grid-template-columns: minmax(390px, 35%) minmax(0, 65%)"), "reference workbench must use the 35/65 split")
const actions = JSON.parse(JSON.stringify(config.visibleOnboardingActions([
  "EDIT", "MARK_READY", "FUTURE_ACTION", "RETURN_TO_DRAFT", "CONFIRM", "CANCEL", "RESTORE"
])))
assert.deepStrictEqual(actions.map(action => action.key), [
  "EDIT", "MARK_READY", "RETURN_TO_DRAFT", "CONFIRM", "CANCEL", "RESTORE"
], "unknown backend actions must be ignored")
assert.deepStrictEqual(actions.map(action => action.permission), [
  "hr:onboarding:edit", "hr:onboarding:ready", "hr:onboarding:return",
  "hr:onboarding:confirm", "hr:onboarding:cancel", "hr:onboarding:restore"
], "known actions must map to the exact backend permissions")

function loadSfc(source, filename, globals) {
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(script, `${filename} must have a script block`)
  const transformed = script[1]
    .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")
  const sandbox = { module: { exports: {} }, exports: {},
    getSelectedDeptId: () => "10",
    require(id) {
      if (id === "@/utils/positiveDecimalId") return require("../src/utils/positiveDecimalId")
      if (id === "@/utils/uiOperationScope") return require("../src/utils/uiOperationScope")
      throw new Error("Unexpected import " + id)
    }, ...globals }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(transformed, sandbox, { filename })
  return sandbox.module.exports
}

function loadIndex(globals) {
  return loadSfc(index, paths.index, {
    HrOnboardingListPane: {},
    HrOnboardingDetailPane: {},
    HrOnboardingCreateDialog: {},
    HrOnboardingEditDrawer: {},
    HrOnboardingConfirmDialog: {},
    HrOnboardingImportDialog: {},
    isOnboardingVersionConflict: config.isOnboardingVersionConflict,
    ...globals
  })
}

function loadCreate(globals) {
  return loadSfc(createDialog, paths.create, { QUICK_CREATE_FIELDS: config.QUICK_CREATE_FIELDS, ...globals })
}

function loadEdit(globals) {
  return loadSfc(editDrawer, paths.edit, {
    EDIT_STEPS: config.EDIT_STEPS,
    DERIVED_ONBOARDING_FIELDS: config.DERIVED_ONBOARDING_FIELDS,
    SENSITIVE_ONBOARDING_FIELDS: config.SENSITIVE_ONBOARDING_FIELDS,
    buildOnboardingUpdatePayload: config.buildOnboardingUpdatePayload,
    firstErrorLocation: config.firstErrorLocation,
    onboardingErrorBody: config.onboardingErrorBody,
    isOnboardingVersionConflict: config.isOnboardingVersionConflict,
    ...globals
  })
}

function loadConfirm(globals) {
  return loadSfc(confirmDialog, paths.confirm, {
    eligibleBindCandidates: config.eligibleBindCandidates,
    eligibleRehireCandidates: config.eligibleRehireCandidates,
    preferredConflictSelection: config.preferredConflictSelection,
    onboardingErrorBody: config.onboardingErrorBody,
    isOnboardingVersionConflict: config.isOnboardingVersionConflict,
    sanitizeConfirmResult: config.sanitizeConfirmResult,
    ...globals
  })
}

function loadListPane() {
  return loadSfc(list, paths.list, {
    ONBOARDING_STATUS_TABS: config.ONBOARDING_STATUS_TABS,
    statusMeta: config.statusMeta
  })
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

function bind(component, initial = {}) {
  const emitted = []
  const base = { ...initial, $emit(name, value) { emitted.push({ name, value }) } }
  const data = component.data ? component.data.call(base) : {}
  const target = { ...base, ...data, ...initial, emitted }
  Object.entries(component.methods || {}).forEach(([name, method]) => { target[name] = method.bind(target) })
  Object.entries(component.computed || {}).forEach(([name, computed]) => {
    const getter = typeof computed === "function" ? computed : computed.get
    if (getter) Object.defineProperty(target, name, { configurable: true, get: getter.bind(target) })
  })
  return target
}

const flushPromises = () => new Promise(resolve => setImmediate(resolve))
const plain = value => JSON.parse(JSON.stringify(value))

async function testPermissionsAndFinalQueryPayloads() {
  let detailCalls = 0
  const listRequests = []
  const component = loadIndex({
    listHrOnboarding: params => { listRequests.push(plain(params)); return Promise.resolve({ rows: [], total: 0 }) },
    getHrOnboarding: () => { detailCalls += 1; return Promise.resolve({ data: {} }) },
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }),
    checkPermi: () => false
  })
  const indexTarget = bind(component)
  assert.strictEqual(indexTarget.canSelect, false)
  assert.strictEqual(indexTarget.canQuery, false)
  indexTarget.selectOnboarding({ onboardingId: 8 })
  assert.strictEqual(detailCalls, 0, "list-only users must never trigger detail requests")

  const pane = bind(loadListPane(), {
    query: { pageNum: 1, pageSize: 10, status: "DRAFT" },
    rows: [], total: 0, loading: false, error: "", selectedId: undefined, options: {},
    canSelect: false, canQuery: false
  })
  pane.handleSelect({ onboardingId: 8 })
  assert.ok(!pane.emitted.some(event => event.name === "select"), "non-query users cannot emit row selection")
  pane.canSelect = true
  pane.canQuery = true
  pane.handleSelect({ onboardingId: 8 })
  assert.deepStrictEqual(plain(pane.emitted.pop()), { name: "select", value: { onboardingId: 8 } })

  indexTarget.selectedOnboardingId = "8"
  indexTarget.detail = { onboardingId: 8 }
  pane.activeStatus = "READY"
  pane.handleStatusChange()
  indexTarget.handleQueryChange(pane.emitted.pop().value)
  assert.strictEqual(indexTarget.selectedOnboardingId, undefined, "query changes must clear selection synchronously")
  assert.strictEqual(indexTarget.detail, null, "query changes must clear detail synchronously")
  await flushPromises()
  assert.deepStrictEqual(listRequests.at(-1), {
    pageNum: 1, pageSize: 10, keyword: "", status: "READY"
  })

  pane.filters = {
    keyword: "张三", expectedEntryDateRange: ["2026-07-12", "2026-07-31"],
    targetDeptId: 11, targetStoreId: 22, employeeCategory: "FULL_TIME", ownerUserId: 33
  }
  pane.submitFilters()
  indexTarget.handleQueryChange(pane.emitted.pop().value)
  await flushPromises()
  pane.changePage(4)
  indexTarget.handleQueryChange(pane.emitted.pop().value)
  await flushPromises()
  assert.deepStrictEqual(listRequests.at(-1), {
    pageNum: 4, pageSize: 10, keyword: "张三", status: "READY",
    expectedEntryDateFrom: "2026-07-12", expectedEntryDateTo: "2026-07-31",
    targetDeptId: 11, targetStoreId: 22, employeeCategory: "FULL_TIME", ownerUserId: 33
  }, "status, filters, date range, and pagination must reach the final server request")
}

async function testStaleListRejectionAndCurrentListFailure() {
  const first = deferred()
  const second = deferred()
  const currentFailure = deferred()
  const requests = [first, second, currentFailure]
  const component = loadIndex({
    listHrOnboarding: () => requests.shift().promise,
    getHrOnboarding: () => Promise.resolve({ data: {} }),
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }),
    checkPermi: () => true
  })
  const target = bind(component)
  target.loadList()
  target.loadList()
  first.reject(new Error("stale list failure"))
  await flushPromises()
  assert.strictEqual(target.listError, "", "stale list rejection must not surface")
  assert.strictEqual(target.listLoading, true, "stale list finally must not clear active loading")
  second.resolve({ rows: [{ onboardingId: 2 }], total: 1 })
  await flushPromises()
  assert.strictEqual(target.rows[0].onboardingId, 2)
  assert.strictEqual(target.listLoading, false)

  target.selectedOnboardingId = "2"
  target.detail = { onboardingId: 2, allowedActions: ["EDIT"] }
  target.loadList()
  currentFailure.reject(new Error("current list failure"))
  await flushPromises()
  assert.strictEqual(target.selectedOnboardingId, undefined, "current list failure must clear actionable selection")
  assert.strictEqual(target.detail, null, "current list failure must clear actionable detail")
  assert.strictEqual(target.listLoading, false)
  assert.strictEqual(target.listError, "current list failure")
}

async function testStaleDetailRejectionAndNullDetailState() {
  const first = deferred()
  const second = deferred()
  const nullDetail = deferred()
  const currentFailure = deferred()
  const requests = [first, second, nullDetail, currentFailure]
  const component = loadIndex({
    listHrOnboarding: () => Promise.resolve({ rows: [], total: 0 }),
    getHrOnboarding: () => requests.shift().promise,
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }),
    checkPermi: () => true
  })
  const target = bind(component)
  target.selectOnboarding({ onboardingId: 1 })
  target.selectOnboarding({ onboardingId: 2 })
  first.reject(new Error("stale detail failure"))
  await flushPromises()
  assert.strictEqual(target.detailError, "", "stale detail rejection must not surface")
  assert.strictEqual(target.detailLoading, true, "stale detail finally must not clear active loading")
  second.resolve({ data: { onboardingId: 2 } })
  await flushPromises()
  assert.strictEqual(target.detail.onboardingId, 2)
  assert.strictEqual(target.detailLoading, false)

  target.selectOnboarding({ onboardingId: 3 })
  nullDetail.resolve({ data: null })
  await flushPromises()
  assert.strictEqual(target.detail, null)
  assert.ok(target.detailError, "a null detail envelope must render a sensible error state")
  assert.strictEqual(target.detailLoading, false)

  target.selectOnboarding({ onboardingId: 4 })
  currentFailure.reject(new Error("current detail failure"))
  await flushPromises()
  assert.strictEqual(target.detail, null)
  assert.strictEqual(target.detailError, "current detail failure")
  assert.strictEqual(target.detailLoading, false)
}

async function testActionPayloadsAndCreateSelection() {
  const calls = []
  const component = loadIndex({
    listHrOnboarding: () => Promise.resolve({ rows: [], total: 0 }),
    getHrOnboarding: id => Promise.resolve({ data: { onboardingId: id, version: 8 } }),
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }),
    markHrOnboardingReady: (id, data) => { calls.push(["ready", id, plain(data)]); return Promise.resolve({ data: { onboardingId: id, version: 9 } }) },
    returnHrOnboardingToDraft: (id, data) => { calls.push(["return", id, plain(data)]); return Promise.resolve({ data: { onboardingId: id, version: 9 } }) },
    cancelHrOnboarding: (id, data) => { calls.push(["cancel", id, plain(data)]); return Promise.resolve({ data: { onboardingId: id, version: 9 } }) },
    restoreHrOnboarding: (id, data) => { calls.push(["restore", id, plain(data)]); return Promise.resolve({ data: { onboardingId: id, version: 9 } }) },
    checkPermi: () => true
  })
  const target = bind(component, {
    $message: { success() {}, error() {} },
    $prompt: () => Promise.resolve({ value: "个人原因" })
  })
  target.selectedOnboardingId = "41"
  target.selectedOnboardingId = "41"
  target.detail = { onboardingId: 41, version: 8, allowedActions: ["MARK_READY", "RETURN_TO_DRAFT", "CANCEL", "RESTORE"] }
  await target.handleAction({ key: "MARK_READY" })
  target.selectedOnboardingId = "41"
  target.detail = { onboardingId: 41, version: 8, allowedActions: ["RETURN_TO_DRAFT"] }
  await target.handleAction({ key: "RETURN_TO_DRAFT" })
  target.selectedOnboardingId = "41"
  target.detail = { onboardingId: 41, version: 8, allowedActions: ["CANCEL"] }
  await target.handleAction({ key: "CANCEL" })
  target.selectedOnboardingId = "41"
  target.detail = { onboardingId: 41, version: 8, allowedActions: ["RESTORE"] }
  await target.handleAction({ key: "RESTORE" })
  assert.deepStrictEqual(calls, [
    ["ready", "41", { version: 8 }],
    ["return", "41", { version: 8 }],
    ["cancel", "41", { version: 8, reason: "个人原因" }],
    ["restore", "41", { version: 8 }]
  ], "all state actions must carry the currently displayed version and cancel reason")

  target.createVisible = true
  await target.handleCreated({ onboardingId: 72 })
  assert.strictEqual(target.createVisible, false)
  assert.strictEqual(target.selectedOnboardingId, "72")
  assert.strictEqual(target.detail.onboardingId, "72")
}

async function testStateActionVersionConflictRefreshesVersion() {
  const payloads = []
  let call = 0
  const component = loadIndex({
    listHrOnboarding: () => Promise.resolve({ rows: [{ onboardingId: 41 }], total: 1 }),
    getHrOnboarding: () => Promise.resolve({ data: { onboardingId: 41, version: 5, allowedActions: ["MARK_READY"] } }),
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }),
    markHrOnboardingReady: (id, data) => {
      payloads.push(plain(data))
      call += 1
      if (call === 1) return Promise.reject({ response: { data: { errorCode: "ONBOARDING_VERSION_CONFLICT" } } })
      return Promise.resolve({ data: { onboardingId: id, version: 6 } })
    },
    checkPermi: () => true
  })
  const target = bind(component, { $message: { warning() {}, error() {}, success() {} } })
  target.selectedOnboardingId = "41"
  target.selectedOnboardingId = "41"
  target.detail = { onboardingId: 41, version: 4, allowedActions: ["MARK_READY"] }
  await target.handleAction("MARK_READY")
  assert.strictEqual(target.detail.version, 5, "version conflict must refresh selected detail")
  await target.handleAction("MARK_READY")
  assert.deepStrictEqual(payloads, [{ version: 4 }, { version: 5 }], "next action must use the refreshed version")
}

function testMaskGlyphsAndRealUuid() {
  for (const value of ["*", "＊", "•", "●", "○", "◯", "◎", "◉", "◌", "◍", "◦", "∙", "上海*", "A•B", "*●"]) {
    assert.strictEqual(config.isOnboardingMaskPlaceholder(value), true, `mask glyph must be rejected: ${value}`)
  }
  assert.strictEqual(config.isOnboardingMaskPlaceholder("张三·李四"), false, "legitimate middle dot must remain valid")
  assert.strictEqual(config.isOnboardingMaskPlaceholder(""), false, "intentional empty clear must remain valid")
  const first = config.createIdempotencyKey()
  const second = config.createIdempotencyKey()
  const uuidV4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
  assert.match(first, uuidV4)
  assert.match(second, uuidV4)
  assert.notStrictEqual(first, second, "fresh opens need unique real UUID v4 keys")
}

async function testCreatePayloadAndFieldErrors() {
  let submitted
  const component = loadCreate({
    createHrOnboarding: data => { submitted = plain(data); return Promise.resolve({ data: { onboardingId: 9 } }) }
  })
  const target = bind(component, {
    visible: true,
    options: {},
    $refs: { form: { validate(callback) { callback(true) } } },
    $emit() {},
    $nextTick(callback) { callback() }
  })
  target.model = {
    employeeName: "新员工", phoneNumber: "13800138000", expectedEntryDate: "2026-07-15",
    targetDeptId: 1, targetPostId: 2, employeeCategory: "FULL_TIME", ownerUserId: 3
  }
  await target.submit()
  assert.deepStrictEqual(submitted, target.model, "quick create must submit exactly the seven-field model")

  const failing = loadCreate({ createHrOnboarding: () => Promise.reject({ response: { data: { fieldErrors: { phoneNumber: "手机号已存在" } } } }) })
  const failedTarget = bind(failing, {
    visible: true, options: {},
    $refs: { form: { validate(callback) { callback(true) } } },
    $emit() {}, $nextTick(callback) { callback() }
  })
  await failedTarget.submit()
  assert.deepStrictEqual(plain(failedTarget.fieldErrors), { phoneNumber: "手机号已存在" })
}

async function testCreateWriteLifecycle() {
  const pending = deferred()
  let calls = 0
  const component = loadCreate({ createHrOnboarding: () => { calls += 1; return pending.promise } })
  const target = bind(component, {
    visible: true, options: {},
    $refs: { form: { validate(callback) { callback(true) }, clearValidate() {} } },
    $nextTick(callback) { callback() }
  })
  const first = target.submit()
  const duplicate = target.submit()
  target.reset()
  target.close()
  assert.strictEqual(calls, 1, "duplicate create submit must make one API call")
  assert.strictEqual(target.submitting, true, "opening/reset cannot clear a pending create flag")
  assert.strictEqual(target.emitted.length, 0, "close must be blocked while create is pending")
  pending.resolve({ data: { onboardingId: 19 } })
  await Promise.all([first, duplicate])
  assert.strictEqual(target.emitted.filter(event => event.name === "created").length, 1)

  const stale = deferred()
  const staleComponent = loadCreate({ createHrOnboarding: () => stale.promise })
  const staleTarget = bind(staleComponent, {
    visible: true, options: {},
    $refs: { form: { validate(callback) { callback(true) }, clearValidate() {} } },
    $nextTick(callback) { callback() }
  })
  const request = staleTarget.submit()
  staleTarget.invalidateLifecycle()
  stale.resolve({ data: { onboardingId: 20 } })
  await request
  assert.ok(!staleTarget.emitted.some(event => event.name === "created"), "stale create resolve must not emit")

  const asyncPending = deferred()
  let asyncCalls = 0
  const asyncComponent = loadCreate({ createHrOnboarding: () => { asyncCalls += 1; return asyncPending.promise } })
  const asyncTarget = bind(asyncComponent, {
    visible: true, options: {},
    $refs: { form: { validate(callback) { setImmediate(() => callback(true)) }, clearValidate() {} } },
    $nextTick(callback) { callback() }
  })
  const asyncFirst = asyncTarget.submit()
  const asyncDuplicate = asyncTarget.submit()
  await flushPromises()
  assert.strictEqual(asyncCalls, 1, "async form validation must still reserve the create submission")
  asyncPending.resolve({ data: { onboardingId: 23 } })
  await Promise.all([asyncFirst, asyncDuplicate])
}

async function testEditPayloadMaskSafetyAndFieldStep() {
  const component = loadEdit({ updateHrOnboarding: () => Promise.resolve({ data: {} }) })
  const detailValue = {
    onboardingId: 7, version: 4, employeeName: "员工", phoneNumberMasked: "138****8000",
    idNumberMasked: "3500**********0000", bankAccountMasked: "6222***********4567",
    currentAddressMasked: "福建*******", registeredResidenceMasked: "福建*******",
    emergencyContactPhoneMasked: "139****9000", targetDeptId: 1, targetPostId: 2,
    jobGrade: "P2", remark: "旧备注"
  }
  const target = bind(component, {
    visible: true, detail: detailValue, options: {},
    $refs: {}, $emit() {}, $nextTick(callback) { callback() }
  })
  target.initialize(detailValue)
  target.model.remark = "新备注"
  let payload = target.buildUpdatePayload()
  assert.deepStrictEqual(plain(payload), { version: 4, remark: "新备注" },
    "untouched sensitive masks and unchanged values must be omitted")
  target.markSensitiveDirty("idNumber")
  target.sensitiveValues.idNumber = "3500**********0000"
  payload = target.buildUpdatePayload()
  assert.strictEqual(payload, null, "a masking placeholder must never be submitted")
  assert.ok(target.fieldErrors.idNumber)

  target.applyServerErrors({ response: { data: { fieldErrors: { bankAccount: "银行卡格式错误", employeeName: "姓名必填" } } } })
  assert.strictEqual(target.activeStep, 0, "the first erroneous step must become active")
  assert.strictEqual(target.pendingFocusField, "employeeName")
}

async function testEditLifecycleAndVersionConflict() {
  const pending = deferred()
  let calls = 0
  const component = loadEdit({ updateHrOnboarding: () => { calls += 1; return pending.promise } })
  const target = bind(component, {
    visible: true, detail: { onboardingId: 7, version: 4 }, options: {},
    $refs: {}, $nextTick(callback) { callback() }
  })
  target.initialize(target.detail)
  target.model.employeeName = "修改"
  const first = target.submit()
  const duplicate = target.submit()
  target.close()
  assert.strictEqual(calls, 1, "duplicate edit submit must make one API call")
  assert.strictEqual(target.emitted.length, 0, "close must be blocked while edit is pending")
  pending.reject({ response: { data: { errorCode: "ONBOARDING_VERSION_CONFLICT", msg: "版本冲突" } } })
  await Promise.all([first, duplicate])
  assert.ok(target.emitted.some(event => event.name === "version-conflict" && event.value.onboardingId === 7))
  assert.ok(target.emitted.some(event => event.name === "update:visible" && event.value === false))
  assert.strictEqual(target.submitMessage, "", "version conflict must reconcile instead of leaving stale retry UI")

  const stale = deferred()
  const staleComponent = loadEdit({ updateHrOnboarding: () => stale.promise })
  const staleTarget = bind(staleComponent, {
    visible: true, detail: { onboardingId: 21, version: 1 }, options: {},
    $refs: {}, $nextTick(callback) { callback() }
  })
  staleTarget.initialize(staleTarget.detail)
  staleTarget.model.employeeName = "旧记录修改"
  const staleRequest = staleTarget.submit()
  staleTarget.detail = { onboardingId: 22, version: 2 }
  staleTarget.initialize(staleTarget.detail)
  stale.resolve({ data: { onboardingId: 21, version: 2 } })
  await staleRequest
  assert.ok(!staleTarget.emitted.some(event => event.name === "saved"), "cross-record stale edit result must not emit")
  assert.strictEqual(staleTarget.model.employeeName, undefined, "new record initializes after stale request settles")
}

async function testConfirmConflictsIdempotencyAndOtpReplay() {
  const first = deferred()
  const second = deferred()
  const confirms = []
  let conflictCall = 0
  const idempotencyKeys = ["uuid-open-1", "uuid-open-2"]
  const component = loadConfirm({
    getHrOnboardingConflicts: () => {
      conflictCall += 1
      if (conflictCall === 1) return first.promise
      if (conflictCall === 2) return second.promise
      return Promise.resolve({ data: [] })
    },
    confirmHrOnboarding: (id, data) => { confirms.push([id, plain(data)]); return Promise.resolve({ data: { accountStatus: "ENABLED", oneTimePassword: "Once-123", oneTimePasswordExpiresAt: "2026-07-12T00:00:00+00:00", replayed: false } }) },
    createIdempotencyKey: () => idempotencyKeys.shift()
  })
  const target = bind(component, {
    visible: true,
    detail: { onboardingId: 8, version: 3, preferredConflictAction: "BIND_EXISTING", preferredBindUserId: 11 },
    $refs: { form: { validate(callback) { callback(true) } } },
    $nextTick(callback) { callback() }
  })
  assert.strictEqual(target.conflictLabel({ conflictType: "PHONE" }), "手机号重复")
  assert.strictEqual(target.conflictLabel({ conflictType: "FUTURE_BACKEND_CODE" }), "账号信息冲突")
  target.openDialog()
  target.loadConflicts()
  first.resolve({ data: [{ candidateUserId: 10, name: "旧候选", maskedPhone: "138****0000", departmentLabel: "旧部门", eligibleForBind: true, allowedDecisions: ["BIND_EXISTING"] }] })
  await flushPromises()
  assert.strictEqual(target.conflicts.length, 0, "stale conflict responses must be ignored")
  second.resolve({ data: [
    { candidateUserId: 11, name: "安全候选", maskedPhone: "139****0000", departmentLabel: "人事部", eligibleForBind: true, allowedDecisions: ["BIND_EXISTING"] },
    { candidateUserId: 12, candidateOnboardingId: 88, name: "不可绑定", maskedPhone: "137****0000", departmentLabel: "财务部", eligibleForBind: false, blocking: true, allowedDecisions: [] }
  ] })
  await flushPromises()
  assert.strictEqual(target.model.conflictAction, "BIND_EXISTING")
  assert.strictEqual(target.model.bindUserId, 11, "an import preference is used only when the candidate remains eligible")
  assert.strictEqual(target.conflicts.length, 2, "ineligible/backend onboarding candidates remain in safe summary")
  assert.deepStrictEqual(target.eligibleCandidates.map(candidate => candidate.candidateUserId), [11], "only eligible users are selectable")
  target.model.actualEntryDate = "2026-07-12"
  await target.submit()
  assert.deepStrictEqual(confirms[0], [8, {
    version: 3, actualEntryDate: "2026-07-12", conflictAction: "BIND_EXISTING",
    bindUserId: 11, idempotencyKey: "uuid-open-1"
  }])
  assert.strictEqual(target.oneTimePassword, "Once-123")
  assert.strictEqual(target.oneTimePasswordExpiresAt, "2026-07-12T00:00:00+00:00")
  assert.ok(Number.isFinite(new Date(target.oneTimePasswordExpiresAt).getTime()), "ISO-8601 expiry must parse in the browser")
  assert.ok(target.oneTimePasswordExpiresAtDisplay, "PC dialog must render a human-readable expiry")
  assert.ok(target.resultMessage.includes("首次登录必须改密"), "create-new result must require password change")
  assert.ok(confirmDialog.includes("oneTimePasswordExpiresAtDisplay"), "PC result UI must show temporary password expiry")
  assert.ok(confirmDialog.includes("首次登录必须修改密码"), "PC result UI must require first-login password change")
  assert.ok(!Object.prototype.hasOwnProperty.call(target.confirmResult, "oneTimePassword"), "confirmResult must never retain OTP")
  assert.ok(!Object.prototype.hasOwnProperty.call(target.confirmResult, "oneTimePasswordExpiresAt"), "confirmResult must never retain OTP expiry")
  const confirmedEvent = target.emitted.find(event => event.name === "confirmed")
  assert.ok(confirmedEvent && !Object.prototype.hasOwnProperty.call(confirmedEvent.value, "oneTimePassword"),
    `emitted metadata must be sanitized: ${JSON.stringify(target.emitted)}`)
  assert.ok(confirmedEvent && !Object.prototype.hasOwnProperty.call(confirmedEvent.value, "oneTimePasswordExpiresAt"),
    "emitted metadata must not retain OTP expiry")
  target.acknowledgePassword()
  assert.strictEqual(target.oneTimePassword, null, "one-time password must be discarded after acknowledgement")
  assert.strictEqual(target.oneTimePasswordExpiresAt, null, "OTP expiry must be discarded with the secret")
  assert.ok(JSON.stringify(target).indexOf("Once-123") === -1, "acknowledgement must clear every component-held OTP reference")

  target.applyConfirmResult({ accountStatus: "ENABLED", oneTimePassword: null, replayed: true })
  assert.ok(target.resultMessage.includes("重置密码"))
  assert.ok(!target.resultMessage.includes("找回一次性密码"), "replay copy must never imply OTP recovery")

  target.close()
  assert.strictEqual(target.confirmResult, null, "close must discard result metadata")
  assert.strictEqual(target.resultMessage, "", "close must discard result copy")

  target.openDialog()
  assert.strictEqual(target.idempotencyKey, "uuid-open-2", "each dialog open must receive a fresh UUID")
  await flushPromises()
  assert.deepStrictEqual(plain(config.preferredConflictSelection(
    { preferredConflictAction: "BIND_EXISTING", preferredBindUserId: 99 },
    [{ candidateUserId: 99, eligibleForBind: false, allowedDecisions: ["BIND_EXISTING"] }]
  )), { conflictAction: "CREATE_NEW", bindUserId: null }, "an ineligible import preference must never be preselected")

  const rehireConfirms = []
  const rehireComponent = loadConfirm({
    getHrOnboardingConflicts: () => Promise.resolve({ data: [{
      candidateUserId: 88, name: "离职员工", maskedPhone: "138****0000", departmentLabel: "原门店",
      eligibleForBind: false, eligibleForRehire: true, blocking: true,
      allowedDecisions: ["REHIRE_EXISTING"]
    }] }),
    confirmHrOnboarding: (id, data) => {
      rehireConfirms.push([id, plain(data)])
      return Promise.resolve({ data: { accountStatus: "ENABLED", replayed: false } })
    },
    createIdempotencyKey: () => "uuid-rehire"
  })
  const rehireTarget = bind(rehireComponent, {
    visible: true, detail: { onboardingId: 9, version: 4 },
    $refs: { form: { validate(callback) { callback(true) } } },
    $nextTick(callback) { callback() }
  })
  rehireTarget.openDialog()
  await flushPromises()
  assert.strictEqual(rehireTarget.model.conflictAction, "REHIRE_EXISTING")
  assert.strictEqual(rehireTarget.model.bindUserId, 88)
  rehireTarget.model.actualEntryDate = "2026-08-27"
  await rehireTarget.submit()
  assert.deepStrictEqual(rehireConfirms[0], [9, {
    version: 4, actualEntryDate: "2026-08-27", conflictAction: "REHIRE_EXISTING",
    bindUserId: 88, idempotencyKey: "uuid-rehire"
  }])
}

async function testConfirmPendingLifecycleAndVersionConflict() {
  const conflict = Promise.resolve({ data: [] })
  const first = deferred()
  const second = deferred()
  const confirmations = [first, second]
  let calls = 0
  const component = loadConfirm({
    getHrOnboardingConflicts: () => conflict,
    confirmHrOnboarding: () => { calls += 1; return confirmations.shift().promise },
    createIdempotencyKey: (() => { let index = 0; return () => `uuid-${++index}` })()
  })
  const target = bind(component, {
    visible: true, detail: { onboardingId: 8, version: 3 },
    $refs: {}, $nextTick(callback) { callback() }
  })
  await target.openDialog()
  target.model.actualEntryDate = "2026-07-12"
  const pending = target.submit()
  const duplicate = target.submit()
  target.close()
  assert.strictEqual(calls, 1, "duplicate confirm submit must make one API call")
  assert.ok(!target.emitted.some(event => event.name === "update:visible"), "confirm close must be blocked while pending")
  target.detail = { onboardingId: 9, version: 6 }
  target.handleDetailChange(target.detail, { onboardingId: 8, version: 3 })
  first.resolve({ data: { onboardingId: 8, oneTimePassword: "STALE-SECRET", accountStatus: "ENABLED" } })
  await Promise.all([pending, duplicate])
  assert.strictEqual(target.oneTimePassword, null, "cross-record stale confirmation must not expose OTP")
  assert.ok(!target.emitted.some(event => event.name === "confirmed"), "cross-record stale resolve must not emit")
  await flushPromises()

  target.model.actualEntryDate = "2026-07-13"
  const conflicted = target.submit()
  second.reject({ response: { data: { errorCode: "ONBOARDING_VERSION_CONFLICT", msg: "版本冲突" } } })
  await conflicted
  assert.ok(target.emitted.some(event => event.name === "version-conflict" && event.value.onboardingId === 9))
  assert.strictEqual(target.oneTimePassword, null)
  assert.strictEqual(target.confirmResult, null)

  const staleFailure = deferred()
  const staleComponent = loadConfirm({
    getHrOnboardingConflicts: () => Promise.resolve({ data: [] }),
    confirmHrOnboarding: () => staleFailure.promise,
    createIdempotencyKey: () => "stale-key"
  })
  const staleTarget = bind(staleComponent, {
    visible: true, detail: { onboardingId: 31, version: 1 },
    $refs: {}, $nextTick(callback) { callback() }
  })
  await staleTarget.openDialog()
  staleTarget.model.actualEntryDate = "2026-07-14"
  const staleRequest = staleTarget.submit()
  staleTarget.detail = { onboardingId: 32, version: 2 }
  staleTarget.handleDetailChange(staleTarget.detail, { onboardingId: 31, version: 1 })
  staleFailure.reject(new Error("stale confirm failure"))
  await staleRequest
  assert.strictEqual(staleTarget.submitMessage, "", "stale confirm rejection must not mutate the next record")
  assert.ok(!staleTarget.emitted.some(event => event.name === "confirmed"), "stale confirm rejection must not emit")
}

async function testConfirmedResultDefersParentRefreshUntilDialogCloses() {
  let listCalls = 0
  let detailCalls = 0
  const component = loadIndex({
    listHrOnboarding: () => { listCalls += 1; return Promise.resolve({ rows: [], total: 0 }) },
    getHrOnboarding: () => { detailCalls += 1; return Promise.resolve({ data: {} }) },
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }),
    checkPermi: () => true
  })
  const parent = bind(component)
  parent.confirmVisible = true
  parent.selectedOnboardingId = "4"
  parent.detail = { onboardingId: 4, version: 1, status: "READY" }
  parent.rows = [{ onboardingId: 4, version: 1, status: "READY" }]

  const childComponent = loadConfirm({
    getHrOnboardingConflicts: () => Promise.resolve({ data: [] }),
    confirmHrOnboarding: () => Promise.resolve({ data: {} }),
    createIdempotencyKey: () => "result-session-key"
  })
  const child = bind(childComponent, {
    visible: true, detail: parent.detail,
    $refs: {}, $nextTick(callback) { callback() }
  })
  await child.openDialog()
  child.applyConfirmResult({ onboardingId: 4, accountStatus: "ENABLED", oneTimePassword: "VISIBLE-ONCE", oneTimePasswordExpiresAt: "2026-07-12T08:00:00+00:00", replayed: false })
  parent.handleConfirmed(child.confirmResult)
  assert.strictEqual(listCalls, 0, "confirmed event must not refresh while result dialog is visible")
  assert.strictEqual(parent.confirmRefreshPending, true)
  assert.strictEqual(parent.rows[0].status, "READY", "list state remains unchanged while confirmation result is visible")
  assert.strictEqual(child.oneTimePassword, "VISIBLE-ONCE")
  assert.strictEqual(child.oneTimePasswordExpiresAt, "2026-07-12T08:00:00+00:00")
  assert.ok(child.confirmResult)
  assert.strictEqual(child.idempotencyKey, "result-session-key")

  child.detail = { onboardingId: 4, version: 2, status: "CONFIRMED" }
  child.handleDetailChange(child.detail, { onboardingId: 4, version: 1, status: "READY" })
  assert.strictEqual(child.oneTimePassword, "VISIBLE-ONCE", "background detail changes cannot reset result-mode OTP")
  assert.strictEqual(child.oneTimePasswordExpiresAt, "2026-07-12T08:00:00+00:00", "background detail changes cannot reset OTP expiry")
  assert.ok(child.confirmResult, "background detail changes cannot reset result metadata")

  child.close()
  assert.strictEqual(child.oneTimePassword, null)
  assert.strictEqual(child.oneTimePasswordExpiresAt, null)
  assert.strictEqual(child.confirmResult, null)
  parent.confirmVisible = false
  await parent.handleConfirmVisibilityChange(false)
  assert.strictEqual(listCalls, 1, "closing result dialog flushes exactly one deferred list refresh")
  assert.strictEqual(detailCalls, 0, "READY record disappearance clears selection instead of loading stale detail")
  assert.strictEqual(parent.selectedOnboardingId, undefined)
  await parent.handleConfirmVisibilityChange(false)
  assert.strictEqual(listCalls, 1, "repeated hidden notifications cannot duplicate refresh")

  parent.confirmVisible = true
  child.visible = true
  child.detail = { onboardingId: 5, version: 3, status: "CONFIRMED" }
  child.openDialog()
  child.applyConfirmResult({ onboardingId: 5, accountStatus: "ENABLED", oneTimePassword: null, replayed: true })
  parent.handleConfirmed(child.confirmResult)
  assert.strictEqual(listCalls, 1, "replay/no-OTP result also defers refresh")
  assert.ok(child.resultMessage.includes("重置密码"))
  child.close()
  parent.confirmVisible = false
  await parent.handleConfirmVisibilityChange(false)
  assert.strictEqual(listCalls, 2, "replay session flushes once after dismissal")
}

async function testOnboardingDeepLinkOverridesDefaultStatusAndOpensScopedDetail() {
  const listCalls = []
  let detailCalls = 0
  const component = loadIndex({
    listHrOnboarding: params => { listCalls.push(JSON.parse(JSON.stringify(params))); return Promise.resolve({ rows: [{ onboardingId: 9 }], total: 1 }) },
    getHrOnboarding: id => { detailCalls += 1; return Promise.resolve({ data: { onboardingId: id, status: "CONFIRMED" } }) },
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }), checkPermi: () => true
  })
  const target = bind(component, { $route: { query: { onboardingId: "9" } } })

  target.applyRouteDeepLink()
  await target.loadList()

  assert.strictEqual(listCalls[0].onboardingId, "9")
  assert.strictEqual(listCalls[0].status, undefined, "deep link must not retain the default DRAFT tab")
  assert.strictEqual(target.selectedOnboardingId, "9")
  assert.strictEqual(target.detail.onboardingId, "9")
  assert.strictEqual(detailCalls, 1)
}

async function testOnboardingQueueRouteFiltersReachTheFirstServerRequest() {
  const listCalls = []
  const component = loadIndex({
    listHrOnboarding: params => { listCalls.push(plain(params)); return Promise.resolve({ rows: [], total: 0 }) },
    getHrOnboarding: () => Promise.resolve({ data: {} }),
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }), checkPermi: () => true
  })
  const target = bind(component, { $route: { query: { status: "ready", targetDeptId: "11" } } })

  await target.loadList()

  assert.strictEqual(listCalls[0].status, "READY")
  assert.strictEqual(listCalls[0].targetDeptId, "11")
}

async function testLinkedEmployeeLoadsFormalCoverageWithoutBlockingOnboardingDetail() {
  const employeeCalls = []
  const component = loadIndex({
    listHrOnboarding: () => Promise.resolve({ rows: [], total: 0 }),
    getHrOnboarding: () => Promise.resolve({ data: { onboardingId: 9, linkedUserId: 7 } }),
    getHrEmployee: userId => { employeeCalls.push(userId); return Promise.resolve({ data: { userId, profileCompletionPercent: 73 } }) },
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }), checkPermi: () => true
  })
  const target = bind(component, { $route: { query: {} } })
  target.selectedOnboardingId = "9"

  await target.loadDetail(9)

  assert.deepStrictEqual(employeeCalls, [7])
  assert.strictEqual(target.detail.onboardingId, 9)
  assert.strictEqual(target.linkedEmployeeProfile.profileCompletionPercent, 73)
}

async function testFirstVisibleRecordBecomesTheDefaultDetail() {
  const rows = [{ onboardingId: 41 }, { onboardingId: 42 }]
  const detailCalls = []
  const component = loadIndex({
    listHrOnboarding: () => Promise.resolve({ rows, total: 2 }),
    getHrOnboarding: id => {
      detailCalls.push(id)
      return Promise.resolve({ data: { onboardingId: id, status: "DRAFT" } })
    },
    getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }),
    checkPermi: () => true
  })
  const target = bind(component, { $route: { query: {} } })

  await target.loadList()

  assert.strictEqual(target.selectedOnboardingId, "41")
  assert.strictEqual(target.detail.onboardingId, "41")
  assert.deepStrictEqual(detailCalls, ["41"])
}

async function testNormalQueueActionClearsHiddenDeepLinkWithoutDuplicateRequest() {
  const listCalls = []
  const replaced = []
  const component = loadIndex({
    listHrOnboarding: params => { listCalls.push(JSON.parse(JSON.stringify(params))); return Promise.resolve({ rows: [], total: 0 }) },
    getHrOnboarding: () => Promise.resolve({ data: {} }), getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }),
    checkPermi: () => true
  })
  const route = { query: { onboardingId: "9", source: "completeness" } }
  const target = bind(component, {
    $route: route,
    $router: { replace(location) { replaced.push(location); route.query = location.query; return Promise.resolve() } }
  })
  target.applyRouteDeepLink()
  await target.handleQueryChange({ status: "READY", keyword: "张三", pageNum: 3 })
  const watcher = component.watch["$route.query.onboardingId"]
  watcher.call(target, undefined, "9")
  await flushPromises()

  assert.strictEqual(listCalls.length, 1, "route synchronization must not duplicate the normal queue request")
  assert.strictEqual(listCalls[0].onboardingId, undefined)
  assert.strictEqual(listCalls[0].status, "READY")
  assert.strictEqual(listCalls[0].keyword, "张三")
  assert.strictEqual(listCalls[0].pageNum, 3, "the user's selected patch must not be reset")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(replaced[0].query)), { source: "completeness" })
}

Promise.resolve()
  .then(testPermissionsAndFinalQueryPayloads)
  .then(testStaleListRejectionAndCurrentListFailure)
  .then(testStaleDetailRejectionAndNullDetailState)
  .then(testActionPayloadsAndCreateSelection)
  .then(testStateActionVersionConflictRefreshesVersion)
  .then(testMaskGlyphsAndRealUuid)
  .then(testCreatePayloadAndFieldErrors)
  .then(testCreateWriteLifecycle)
  .then(testEditPayloadMaskSafetyAndFieldStep)
  .then(testEditLifecycleAndVersionConflict)
  .then(testConfirmConflictsIdempotencyAndOtpReplay)
  .then(testConfirmPendingLifecycleAndVersionConflict)
  .then(testConfirmedResultDefersParentRefreshUntilDialogCloses)
  .then(testOnboardingDeepLinkOverridesDefaultStatusAndOpensScopedDetail)
  .then(testOnboardingQueueRouteFiltersReachTheFirstServerRequest)
  .then(testLinkedEmployeeLoadsFormalCoverageWithoutBlockingOnboardingDetail)
  .then(testFirstVisibleRecordBecomesTheDefaultDetail)
  .then(testNormalQueueActionClearsHiddenDeepLinkWithoutDuplicateRequest)
  .then(() => console.log("hrOnboardingWorkbench tests passed"))
  .catch(error => { console.error(error); process.exitCode = 1 })
