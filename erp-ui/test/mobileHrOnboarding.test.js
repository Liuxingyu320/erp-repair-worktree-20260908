const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")
const compiler = require("../node_modules/vue-template-compiler")

const {
  getMobileBottomNav,
  getMobileHrBottomNav,
  getMobileRouteDefinition,
  isMobileRouteAllowedForPermissions
} = require("../src/views/mobile/mobileNavigation")

function componentSource(relativePath) {
  const absolutePath = path.resolve(__dirname, relativePath)
  assert.ok(fs.existsSync(absolutePath), `${relativePath} should exist`)
  return fs.readFileSync(absolutePath, "utf8")
}

function loadComponent(relativePath, dependencies = {}, returnModule = false) {
  const source = componentSource(relativePath)
  const descriptor = compiler.parseComponent(source)
  const transformed = babel.transformSync(descriptor.script.content, {
    filename: path.resolve(__dirname, relativePath + ".js"),
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  })
  const module = { exports: {} }
  const sandbox = {
    module,
    exports: module.exports,
    document: dependencies.__document,
    require(request) {
      if (Object.prototype.hasOwnProperty.call(dependencies, request)) {
        return dependencies[request]
      }
      if (request === "@/utils/shopContext") {
        return { getSelectedDeptContext: () => ({ deptType: "STORE" }) }
      }
      if (request === "../../mobileNavigation") {
        return require("../src/views/mobile/mobileNavigation")
      }
      if (request === "../mobileHrError" || request === "./mobileHrError") {
        return require("../src/views/mobile/hr/mobileHrError")
      }
      throw new Error(`unexpected component dependency: ${request}`)
    }
  }
  vm.runInNewContext(transformed.code, sandbox, { filename: relativePath })
  return returnModule ? module.exports : (module.exports.default || module.exports)
}

function bindMethods(methods) {
  const context = {}
  Object.keys(methods).forEach(name => {
    context[name] = (...args) => methods[name].apply(context, args)
  })
  return context
}

const shell = componentSource("../src/views/mobile/hr/components/MobileHrShell.vue")
const list = componentSource("../src/views/mobile/hr/components/MobileOnboardingList.vue")
const profile = componentSource("../src/views/mobile/profile/index.vue")
const shellComponent = loadComponent("../src/views/mobile/hr/components/MobileHrShell.vue")
const listComponent = loadComponent("../src/views/mobile/hr/components/MobileOnboardingList.vue")

function assertTemplateCompiles(relativePath) {
  const descriptor = compiler.parseComponent(componentSource(relativePath))
  const result = compiler.compile(descriptor.template.content)
  assert.deepStrictEqual(result.errors, [], `${relativePath} template should compile`)
}

// The shared HR shell owns layout and states, but reuses the single permission-aware
// navigation source instead of defining another list of mobile destinations.
assert.ok(shell.includes("env(safe-area-inset-bottom)"), "HR shell should reserve the device bottom safe area")
assert.ok(shell.includes("max-width: 430px"), "HR shell should remain phone-width on large screens")
assert.ok(shell.includes("getMobileHrBottomNav"), "HR shell should consume explicit HR-context navigation")
assert.ok(shell.includes("getMobileRouteAccessDecision"), "HR shell should recheck access before navigation")
assert.ok(!shell.includes("const bottomNav = ["), "HR shell should not duplicate bottom navigation definitions")
;["title", "back", "loading", "error"].forEach(prop => {
  assert.ok(new RegExp(`\\b${prop}:\\s*\\{`).test(shell), `HR shell should expose the ${prop} prop`)
})
assert.ok(/back:\s*\{[^}]*Function/s.test(shell), "HR shell should accept an exclusive functional back handler")
;["name=\"header\"", "<slot />", "name=\"footer\""].forEach(slot => {
  assert.ok(shell.includes(slot), `HR shell should expose ${slot}`)
})
assert.ok(shell.includes('role="status"') && shell.includes('aria-live="polite"'), "loading should be announced politely")
assert.ok(shell.includes('role="alert"') && shell.includes('@click="$emit(\'retry\')"'), "errors should be announced and retryable")
assert.ok(shell.includes(':aria-current="item.active ? \'page\' : null"'), "the active navigation button should expose aria-current directly")
assert.ok(shell.includes('aria-label="返回"'), "the back affordance should have an accessible name")

const hrPermissions = ["hr:onboarding:workbench", "hr:onboarding:list"]
const allowedHrNav = getMobileBottomNav("STORE", hrPermissions)
assert.ok(allowedHrNav.length > 0, "HR navigation should expose at least the permitted workbench")
allowedHrNav.forEach(item => {
  assert.ok(getMobileRouteDefinition(item.path), `${item.path} should resolve to a registered mobile route`)
  assert.strictEqual(
    isMobileRouteAllowedForPermissions(item.path, hrPermissions),
    true,
    `${item.path} should be allowed for the same permissions that rendered it`
  )
})
assert.ok(
  !getMobileBottomNav("STORE", ["hr:onboarding:workbench"]).some(item => item.path === "/mobile/hr/onboarding"),
  "workbench-only users should not receive an onboarding-list link"
)
const tasksTarget = allowedHrNav.find(item => item.label === "待办")
assert.deepStrictEqual(
  tasksTarget && tasksTarget.query,
  { view: "tasks" },
  "the shared navigation source should distinguish the task queue with a query marker"
)

const adminHrNavigation = shellComponent.computed.navigationItems.call({
  $route: { path: "/mobile/hr/employee", query: {} },
  userPermissions: ["*:*:*"]
})
assert.deepStrictEqual(
  adminHrNavigation.map(item => item.label),
  ["工作台", "人事", "待办", "我的"],
  "an administrator inside MobileHrShell should retain the complete HR navigation"
)
assert.deepStrictEqual(
  getMobileHrBottomNav(undefined).map(item => item.path),
  ["/mobile/mine"],
  "HR shell initialization should expose only a safe self-service skeleton before permissions load"
)

function navigationFor(pathname, query) {
  return shellComponent.computed.navigationItems.call({
    $route: { path: pathname, query: query || {} },
    selectedDeptType: "STORE",
    userPermissions: hrPermissions
  })
}

const regularQueueNavigation = navigationFor("/mobile/hr/onboarding", { status: "READY", keyword: "店长" })
assert.deepStrictEqual(
  regularQueueNavigation.filter(item => item.active).map(item => item.label),
  ["人事"],
  "ordinary queue filters should keep 人事 as the single active destination"
)

const taskQueueNavigation = navigationFor("/mobile/hr/onboarding", { view: "tasks", status: "DRAFT" })
assert.deepStrictEqual(
  taskQueueNavigation.filter(item => item.active).map(item => item.label),
  ["待办"],
  "the task marker should make 待办 the single active destination"
)

;[
  ["/mobile/hr/onboarding/42", {}, "人事"],
  ["/mobile/hr/onboarding/42", { view: "tasks" }, "待办"],
  ["/mobile/hr/onboarding/42/edit", {}, "人事"],
  ["/mobile/hr/onboarding/42/edit", { view: "tasks" }, "待办"]
].forEach(([routePath, query, expectedLabel]) => {
  assert.deepStrictEqual(
    navigationFor(routePath, query).filter(item => item.active).map(item => item.label),
    [expectedLabel],
    `${routePath} should keep only ${expectedLabel} active for ${JSON.stringify(query)}`
  )
})

assert.deepStrictEqual(
  navigationFor("/mobile/hr/onboarding-archive", { view: "tasks" }).filter(item => item.active),
  [],
  "an unrelated path sharing only the onboarding text prefix should not activate HR queue navigation"
)

const pushedTargets = []
const shellContext = {
  $route: { path: "/mobile/hr", query: {} },
  $router: {
    push(target) {
      pushedTargets.push(target)
      return Promise.resolve()
    },
    back() {}
  },
  selectedDeptType: "STORE",
  userPermissions: hrPermissions,
  $emit() {}
}
shellContext.openPath = target => shellComponent.methods.openPath.call(shellContext, target)
shellComponent.methods.openNavigation.call(
  shellContext,
  navigationFor("/mobile/hr", {}).find(item => item.label === "待办")
)
assert.deepStrictEqual(
  JSON.parse(JSON.stringify(pushedTargets)),
  [{ path: "/mobile/hr/onboarding", query: { view: "tasks" } }],
  "the shell should push the complete shared navigation target"
)

const duplicatePushes = []
const activeTask = taskQueueNavigation.find(item => item.label === "待办")
shellComponent.methods.openNavigation.call({
  openPath(target) { duplicatePushes.push(target) }
}, activeTask)
assert.deepStrictEqual(duplicatePushes, [], "the active task target should not be pushed twice")

let functionalBackCalls = 0
let functionalRouterBackCalls = 0
let functionalBackEmits = 0
shellComponent.methods.handleBack.call({
  back() { functionalBackCalls += 1 },
  $emit() { functionalBackEmits += 1 },
  $router: { back() { functionalRouterBackCalls += 1 } }
})
assert.strictEqual(functionalBackCalls, 1, "a functional back handler should run exactly once")
assert.strictEqual(functionalRouterBackCalls, 0, "a functional back handler must not also call router.back")
assert.strictEqual(functionalBackEmits, 0, "a functional back handler must not also emit a second navigation path")

const queueSwitches = []
const taskRouteContext = {
  $route: { path: "/mobile/hr/onboarding", query: { view: "tasks" } },
  $router: {
    push(target) {
      queueSwitches.push(target)
      return Promise.resolve()
    }
  },
  selectedDeptType: "STORE",
  userPermissions: hrPermissions,
  $emit() {}
}
taskRouteContext.openPath = target => shellComponent.methods.openPath.call(taskRouteContext, target)
shellComponent.methods.openNavigation.call(
  taskRouteContext,
  taskQueueNavigation.find(item => item.label === "人事")
)
assert.deepStrictEqual(
  JSON.parse(JSON.stringify(queueSwitches)),
  [{ path: "/mobile/hr/onboarding" }],
  "switching from 待办 to 人事 should clear the task marker with a real navigation"
)
assert.ok(
  profile.includes('@click="openNavigation(item)"') &&
    /openNavigation\(item\)\s*\{[\s\S]*?path:\s*item\.path,[\s\S]*?query:\s*item\.query/s.test(profile),
  "the profile navigation consumer should preserve optional shared target queries"
)

// The list consumes only masked, display-safe fields and exposes parent-controlled actions.
;["expectedEntryDate", "missingOnboardingFields", "phoneNumberMasked"].forEach(field => {
  assert.ok(list.includes(field), `mobile onboarding rows should render ${field}`)
})
assert.ok(!list.includes("idNumber"), "mobile onboarding list should never consume identity numbers")
assert.ok(!list.includes("bankAccount"), "mobile onboarding list should never consume bank accounts")
assert.ok(!list.includes("item.phoneNumber ||"), "mobile onboarding list should never fall back to an unmasked phone field")
;["items", "selectedStatus", "loading", "hasMore", "canSelect"].forEach(prop => {
  assert.ok(new RegExp(`\\b${prop}:\\s*\\{`).test(list), `mobile onboarding list should expose the ${prop} prop`)
})
;["select", "load-more", "refresh"].forEach(eventName => {
  assert.ok(list.includes(`$emit(\"${eventName}\"`), `mobile onboarding list should emit ${eventName}`)
})
assert.ok(!/from\s+["'][^"']*\/api\//.test(list), "the reusable list should not call APIs directly")
assert.ok(list.includes('role="list"') && list.includes('role="listitem"'), "rows should expose list semantics")
assert.ok(list.includes('aria-busy="true"') && list.includes('aria-live="polite"'), "async list state should be announced")
assert.ok(/\.onboarding-row\s*\+\s*\.onboarding-row\s*\{[^}]*border-top:/s.test(list), "rows should use separators instead of isolated heavy cards")
assert.ok(/\.onboarding-row-button\s*\{[^}]*min-height:\s*44px/s.test(list), "each row should provide a touch-sized target")
assert.ok(list.includes(':disabled="loading"'), "refresh and load-more controls should prevent duplicate loading actions")
assert.ok(list.includes(':disabled="!canSelect"') && list.includes(':aria-disabled="!canSelect ? \'true\' : null"'), "list-only rows should be noninteractive")
assert.ok(list.includes("暂无详情查看权限"), "list-only users should receive a visible explanation")
assert.ok(shell.includes("modalOpen") && shell.includes("inert") && shell.includes('name="overlay"'), "the shell should isolate modal overlays from inert background content")

const listContext = bindMethods(listComponent.methods)
const safeRowLabel = listContext.rowAriaLabel({
  employeeName: "林晓",
  phoneNumberMasked: "138****2468",
  positionName: "店长",
  companyName: "华东区",
  expectedEntryDate: "2026-07-20",
  status: "READY",
  missingOnboardingFields: [{ key: "address" }]
})
;["林晓", "138****2468", "店长", "华东区", "2026-07-20", "待确认", "缺少 1 项资料"].forEach(value => {
  assert.ok(safeRowLabel.includes(value), `the row accessible name should include ${value}`)
})
const listOnlyEmits = []
listComponent.methods.selectItem.call({ canSelect: false, $emit: (...args) => listOnlyEmits.push(args) }, { onboardingId: 1 })
assert.deepStrictEqual(listOnlyEmits, [], "list-only users should never emit a detail selection")
listComponent.methods.selectItem.call({ canSelect: true, $emit: (...args) => listOnlyEmits.push(args) }, { onboardingId: 1 })
assert.strictEqual(listOnlyEmits.length, 1, "query-capable users should retain row selection")

const workbench = componentSource("../src/views/mobile/hr/index.vue")
const queue = componentSource("../src/views/mobile/hr/onboarding/index.vue")
const detail = componentSource("../src/views/mobile/hr/onboarding/detail.vue")
assertTemplateCompiles("../src/views/mobile/hr/index.vue")
assertTemplateCompiles("../src/views/mobile/hr/onboarding/index.vue")
assertTemplateCompiles("../src/views/mobile/hr/onboarding/detail.vue")

// Detail consumes only the masked DTO, keeps every mutation behind both gates,
// and owns mobile-safe modal and one-time-secret lifecycles.
;[
  "phoneNumberMasked", "idNumberMasked", "bankAccountMasked", "registeredResidenceMasked",
  "currentAddressMasked", "emergencyContactPhoneMasked", "onboardingCompletionPercent",
  "profileCompletionPercent", "missingOnboardingFields", "missingProfileFields",
  "operationLogs", "accountConfigurationRiskCodes", "readinessBlockingCodes", "allowedActions"
].forEach(field => assert.ok(detail.includes(field), `mobile detail should render safe field ${field}`))
;["revealHr", "getSensitive", "localStorage", "sessionStorage", "navigator.clipboard", "execCommand"].forEach(forbidden => {
  assert.ok(!detail.includes(forbidden), `mobile detail must not use ${forbidden}`)
})
assert.ok(!/\b(detail|candidate)\.(phoneNumber|idNumber|bankAccount|registeredResidence|currentAddress|emergencyContactPhone)\b/.test(detail),
  "mobile detail must never read an unmasked sensitive field")
;["EDIT", "MARK_READY", "RETURN_TO_DRAFT", "CONFIRM", "CANCEL", "RESTORE"].forEach(action => {
  assert.ok(detail.includes(action), `mobile detail should map ${action}`)
})
;["getHrOnboarding", "markHrOnboardingReady", "returnHrOnboardingToDraft", "getHrOnboardingConflicts",
  "confirmHrOnboarding", "cancelHrOnboarding", "restoreHrOnboarding"].forEach(api => {
  assert.ok(detail.includes(api), `mobile detail should use the ${api} wrapper`)
})
assert.ok(detail.includes("checkPermi") && detail.includes("allowedActions"), "every detail action should require permission and server allowance")
assert.ok(detail.includes(':back="goBack"') && !detail.includes('@back="goBack"'), "detail should provide one exclusive back path")
assert.ok(detail.includes("stateKey") && !/query:\s*\{[^}]*employeeName/.test(detail), "detail navigation should preserve only opaque queue state")
assert.ok(detail.includes("env(safe-area-inset-bottom)") && /min-height:\s*44px/.test(detail), "detail controls should respect safe areas and touch size")
assert.ok(detail.includes('aria-modal="true"') && detail.includes("handleDialogKeydown"), "detail dialogs should be modal, escapable, and focus trapped")
assert.ok(detail.includes('maxlength="200"'), "cancel reason UI should match the backend 200-character limit")
assert.ok(detail.includes("confirmRefreshWarning"), "background confirmation refresh failures should remain non-destructive")
assert.ok(detail.includes("REHIRE_EXISTING") && detail.includes("恢复原账号（再入职）") && detail.includes("eligibleForRehire"),
  "mobile confirmation must expose the explicit rehire decision without creating a duplicate account")
assert.ok(detail.includes("入职资料已保存；目标组织未配置部门负责人，请由管理员在桌面端组织管理中补充后刷新"),
  "readiness blockers should distinguish saved onboarding data from organization configuration")
assert.ok(detail.includes("可后续完善") && detail.includes("不影响确认入职"),
  "formal profile gaps should not be presented as current onboarding blockers")
assert.ok(detail.includes("presentOnboardingLog") && detail.includes("presentedOperationLogs"),
  "mobile operation history should reuse the translated desktop presenter")
assert.ok(detail.includes('FULL_TIME: "正式员工"'), "mobile details should not expose dictionary codes")

// The workbench consumes the already-scoped masked summary and nothing else.
assert.ok(workbench.includes("getHrOnboardingSummary"), "workbench should load the summary endpoint")
assert.ok(!workbench.includes("listHrOnboarding"), "workbench should never duplicate the summary with a list request")
;["todayArrivalCount", "pendingConfirmCount", "todayTasks", "currentAction", "phoneNumberMasked"].forEach(field => {
  assert.ok(workbench.includes(field), `workbench should render ${field}`)
})
assert.ok(!workbench.includes("accountConfigurationRiskCount"), "workbench should not show unrelated summary metrics")
assert.ok(!workbench.includes("idNumber") && !workbench.includes("bankAccount"), "workbench should consume masked list-safe data only")
assert.ok(workbench.includes("hr:onboarding:add") && workbench.includes("hr:onboarding:list"), "create and all-records links should be permission guarded")
assert.ok(workbench.includes("hr:onboarding:query"), "task detail navigation should require query permission")

// The queue is a server-driven four-state search with every backend filter.
;["DRAFT", "READY", "CONFIRMED", "CANCELLED"].forEach(status => {
  assert.ok(queue.includes(status), `queue should expose the ${status} server state`)
})
;[
  "keyword",
  "expectedEntryDateFrom",
  "expectedEntryDateTo",
  "targetDeptId",
  "targetStoreId",
  "employeeCategory",
  "ownerUserId",
  "pageNum",
  "pageSize"
].forEach(field => assert.ok(queue.includes(field), `queue should pass ${field} to the backend`))
assert.ok(queue.includes("setTimeout") && queue.includes("300"), "keyword search should be debounced")
assert.ok(queue.includes("view") && queue.includes("tasks"), "queue should consume the actionable-task marker")
assert.ok(queue.includes("scrollTop"), "queue should preserve its scroll position in route state")
assert.ok(!/localStorage|sessionStorage/.test(queue), "queue should not persist PII or records in browser storage")
assert.ok(queue.includes("hr:onboarding:query"), "queue detail navigation should be permission guarded")
assert.ok(queue.includes("hr:onboarding:add"), "queue create navigation should be permission guarded")
assert.ok(queue.includes(":can-select=\"canQuery\""), "queue should pass detail capability to the shared list")
assert.ok(queue.includes("filterDraft"), "filter sheet should edit a draft instead of live request filters")
assert.ok(queue.includes("optionsLoading") && queue.includes("optionsError"), "filter options should expose loading and retryable errors")
assert.ok(queue.includes("beforeRouteLeave") && queue.includes("persistQueueState"), "queue should persist its opaque state before every navigation")
assert.ok(!/query\.keyword\s*=|query\.scrollTop\s*=/.test(queue), "PII keyword and scroll offsets must not be serialized into route query")
assert.ok(queue.includes("stateKey"), "route state should use an opaque queue cache key")
assert.ok(queue.includes("@/utils/mobileHrQueueState") && !queue.includes("const queueStateCache = new Map"), "queue cache should live in an auth-clearable utility module")

// Task 6 keeps rendered list/detail regression fixtures deliberately display-only.
// Full identifiers are permitted in form contracts, never in rendered snapshots.
const renderedListFixture = {
  employeeName: "周*",
  phoneNumberMasked: "138****0000",
  expectedEntryDate: "2026-07-20",
  positionName: "店长"
}
const renderedDetailFixture = {
  employeeName: "周*",
  phoneNumberMasked: "138****0000",
  idNumberMasked: "1101**********0000",
  bankAccountMasked: "6222********1234",
  registeredResidenceMasked: "已填写",
  currentAddressMasked: "已填写"
}
for (const fixture of [renderedListFixture, renderedDetailFixture]) {
  const snapshot = JSON.stringify(fixture)
  assert.ok(!/\b1\d{10}\b/.test(snapshot), "rendered HR fixtures must not contain full phone numbers")
  assert.ok(!/\b\d{16,19}\b/.test(snapshot), "rendered HR fixtures must not contain full ID or bank numbers")
  ;["idNumber\"", "bankAccount\"", "registeredResidence\"", "currentAddress\""].forEach(rawKey => {
    assert.ok(!snapshot.includes(rawKey), `rendered HR fixtures must not expose ${rawKey}`)
  })
}

async function runPageBehaviorTests() {
  let summaryCalls = 0
  const workbenchComponent = loadComponent("../src/views/mobile/hr/index.vue", {
    "@/api/hr/onboarding": {
      getHrOnboardingSummary() {
        summaryCalls += 1
        return Promise.resolve({
          data: {
            todayArrivalCount: 2,
            pendingConfirmCount: 3,
            todayTasks: [{ onboardingId: 7, employeeName: "周*", phoneNumberMasked: "138****0000", currentAction: "CONFIRM" }]
          }
        })
      }
    },
    "@/utils/permission": { checkPermi: permissions => permissions.includes("hr:onboarding:query") },
    "./components/MobileHrShell": {},
    "./components/MobileOnboardingList": {}
  })
  const workbenchContext = Object.assign(workbenchComponent.data(), {
    $router: { push() { return Promise.resolve() } }
  })
  Object.keys(workbenchComponent.methods).forEach(name => {
    workbenchContext[name] = (...args) => workbenchComponent.methods[name].apply(workbenchContext, args)
  })
  await workbenchContext.loadSummary()
  assert.strictEqual(summaryCalls, 1, "one workbench load should issue exactly one summary request")
  assert.strictEqual(workbenchContext.summary.todayTasks[0].currentAction, "CONFIRM", "backend current action should remain authoritative")

  const listCalls = []
  const listResolvers = []
  const optionResolvers = []
  const routeReplacements = []
  const routePushes = []
  const fakeDocument = { activeElement: null }
  const queueState = require("../src/utils/mobileHrQueueState")
  const authStore = { getters: { token: "access-token", isLock: false, id: 7, deptId: 12, permissions: ["hr:onboarding:list", "hr:onboarding:query"] } }
  const ownerFingerprint = queueState.createMobileHrQueueOwnerFingerprint({ userId: 7, deptId: 12, permissions: authStore.getters.permissions })
  const queueModule = loadComponent("../src/views/mobile/hr/onboarding/index.vue", {
    __document: fakeDocument,
    "@/utils/mobileHrQueueState": queueState,
    "@/api/hr/onboarding": {
      getHrOnboardingFormOptions: () => new Promise((resolve, reject) => optionResolvers.push({ resolve, reject })),
      listHrOnboarding(params) {
        listCalls.push(Object.assign({}, params))
        return new Promise((resolve, reject) => listResolvers.push({ resolve, reject }))
      }
    },
    "@/utils/permission": { checkPermi: permissions => permissions.includes("hr:onboarding:query") },
    "../components/MobileHrShell": {},
    "../components/MobileOnboardingList": {}
  }, true)
  const queueComponent = queueModule.default
  queueModule.resetMobileHrQueueStateCacheForTests()
  const queueContext = Object.assign(queueComponent.data.call({
    $route: { query: { view: "tasks", keyword: "张三13800000000", targetDeptId: "12", scrollTop: "88" } },
    $store: authStore
  }), {
    $store: authStore,
    $route: { path: "/mobile/hr/onboarding", query: { view: "tasks", keyword: "张三13800000000", targetDeptId: "12", scrollTop: "88" } },
    $router: {
      replace(target) { routeReplacements.push(target); return Promise.resolve() },
      push(target) { routePushes.push(target); return Promise.resolve() }
    },
    $nextTick(callback) { callback() },
    $refs: { scrollRegion: { scrollTop: 0 }, filterTrigger: { focus() {} } }
  })
  Object.keys(queueComponent.methods).forEach(name => {
    queueContext[name] = (...args) => queueComponent.methods[name].apply(queueContext, args)
  })

  assert.strictEqual(queueContext.filters.keyword, "", "URL keyword must be ignored instead of restored")
  assert.strictEqual(queueContext.savedScrollTop, 0, "URL scroll offsets must be ignored")
  const firstLoad = queueContext.loadPage({ reset: true })
  assert.strictEqual(listCalls[0].keyword, "")
  assert.strictEqual(listCalls[0].status, "READY")
  assert.strictEqual(listCalls[0].targetDeptId, 12)
  listResolvers[0].resolve({ rows: [{ onboardingId: 1, employeeName: "张*", phoneNumberMasked: "138****0000" }], total: 1 })
  await firstLoad
  const firstRoute = routeReplacements[routeReplacements.length - 1].query
  assert.ok(/^qs_/.test(firstRoute.stateKey), "successful load should install an opaque queue key")
  assert.ok(!Object.prototype.hasOwnProperty.call(firstRoute, "keyword") && !Object.prototype.hasOwnProperty.call(firstRoute, "scrollTop"), "route must not contain keyword or scroll")
  assert.ok(!JSON.stringify(firstRoute).includes("张") && !JSON.stringify(firstRoute).includes("138"), "route must not contain row PII")

  queueContext.filters.keyword = "原条件"
  queueContext.openFilters({ currentTarget: queueContext.$refs.filterTrigger })
  queueContext.filterDraft.keyword = "草稿条件"
  queueContext.filterDraft.targetStoreId = 55
  queueContext.closeFilters()
  assert.strictEqual(queueContext.filters.keyword, "原条件", "closing the sheet should discard draft edits")
  assert.strictEqual(queueContext.filters.targetStoreId, "", "closing should not mutate applied filters")

  const inFlight = queueContext.loadPage({ reset: true })
  const requestIndex = listCalls.length - 1
  queueContext.openFilters({ currentTarget: queueContext.$refs.filterTrigger })
  queueContext.filterDraft.keyword = "请求中草稿"
  assert.strictEqual(listCalls[requestIndex].keyword, "原条件", "in-flight request should capture immutable applied filters")
  listResolvers[requestIndex].resolve({ rows: [{ onboardingId: 2 }], total: 1 })
  await inFlight
  assert.strictEqual(queueContext.filters.keyword, "原条件", "draft edits during a request must remain unapplied")
  queueContext.closeFilters()

  queueContext.openFilters({ currentTarget: queueContext.$refs.filterTrigger })
  queueContext.filterDraft.targetStoreId = 55
  const appliedDraft = queueContext.applyFiltersAndClose()
  const appliedIndex = listCalls.length - 1
  assert.strictEqual(queueContext.filters.targetStoreId, 55, "Apply should atomically promote the draft")
  assert.strictEqual(listCalls[appliedIndex].targetStoreId, 55)
  listResolvers[appliedIndex].resolve({ rows: [{ onboardingId: 3 }], total: 1 })
  await appliedDraft

  const optionOne = queueContext.loadOptions()
  const optionTwo = queueContext.loadOptions()
  optionResolvers[0].reject(new Error("stale options"))
  optionResolvers[1].resolve({ data: { stores: [{ value: 1, label: "A" }] } })
  await Promise.all([optionOne, optionTwo])
  assert.strictEqual(queueContext.options.stores.length, 1, "stale option failure should not clear current options")
  const optionFailure = queueContext.loadOptions()
  optionResolvers[2].reject(new Error("current options"))
  await optionFailure
  assert.deepStrictEqual(JSON.parse(JSON.stringify(queueContext.options)), {}, "current option failure should clear stale options")
  assert.ok(queueContext.optionsError, "current option failure should remain visible and retryable")

  const maskedRows = [{ onboardingId: 9001, employeeName: "李*", phoneNumberMasked: "139****1111" }]
  const deepKey = queueModule.seedMobileHrQueueStateForTests({ status: "CONFIRMED", taskView: false,
    filters: { keyword: "李四13900001111", targetDeptId: 9 }, rows: maskedRows, total: 4000, pageNum: 200, scrollTop: 360 }, ownerFingerprint)
  const deepData = queueComponent.data.call({ $route: { query: { stateKey: deepKey, status: "CONFIRMED" } }, $store: authStore })
  assert.strictEqual(deepData.cacheHit, true)
  assert.strictEqual(deepData.pageNum, 200)
  assert.strictEqual(deepData.rows.length, 1)
  assert.strictEqual(deepData.filters.keyword, "李四13900001111")
  assert.strictEqual(deepData.pendingScrollTop, 360)
  const requestsBeforeCachedReuse = listCalls.length
  const routeWatcher = queueComponent.watch["$route.query"]
  await routeWatcher.handler.call(queueContext, { stateKey: deepKey, status: "CONFIRMED" }, {})
  assert.strictEqual(listCalls.length, requestsBeforeCachedReuse, "deep cached return should issue zero list requests")
  assert.deepStrictEqual(Array.from(queueContext.rows, row => row.onboardingId), [9001])
  assert.strictEqual(queueContext.pageNum, 200)
  assert.strictEqual(queueContext.total, 4000)
  assert.strictEqual(queueContext.$refs.scrollRegion.scrollTop, 360)

  const otherAuthStore = { getters: { token: "other-token", isLock: false, id: 8, deptId: 12, permissions: authStore.getters.permissions } }
  const mismatchKey = queueModule.seedMobileHrQueueStateForTests({ status: "READY", filters: {}, rows: maskedRows, total: 1, pageNum: 1 }, ownerFingerprint)
  const mismatchData = queueComponent.data.call({ $route: { query: { stateKey: mismatchKey } }, $store: otherAuthStore })
  assert.strictEqual(mismatchData.cacheHit, false)
  assert.deepStrictEqual(Array.from(mismatchData.rows), [], "owner mismatch must never render cached rows before an authenticated backend request")
  assert.deepStrictEqual(Array.from(queueComponent.computed.visibleRows.call({ queueOwnerFingerprint: "owner-b", ownerFingerprint: "owner-a", rows: maskedRows })), [], "live scope changes should hide rows before watcher cleanup")

  const exactCacheRows = Array.from({ length: 5000 }, (_, index) => ({ onboardingId: 10000 + index }))
  const exactCacheKey = queueModule.seedMobileHrQueueStateForTests({ status: "CONFIRMED", filters: {}, rows: exactCacheRows, total: 5000, pageNum: 250, scrollTop: 500 }, ownerFingerprint)
  const exactCacheData = queueComponent.data.call({ $route: { query: { stateKey: exactCacheKey } }, $store: authStore })
  assert.strictEqual(exactCacheData.cacheHit, true)
  assert.strictEqual(exactCacheData.rows.length, 5000, "exact cache row bound should remain coherent")
  assert.strictEqual(exactCacheData.pageNum, 250)

  const overflowCacheRows = Array.from({ length: 6000 }, (_, index) => ({ onboardingId: 20000 + index }))
  const overflowCacheKey = queueModule.seedMobileHrQueueStateForTests({ status: "CONFIRMED", filters: { keyword: "overflow-secret" }, rows: overflowCacheRows, total: 7000, pageNum: 300, scrollTop: 900 }, ownerFingerprint)
  const overflowCacheData = queueComponent.data.call({ $route: { query: { stateKey: overflowCacheKey } }, $store: authStore })
  assert.strictEqual(overflowCacheData.cacheHit, false)
  assert.deepStrictEqual(Array.from(overflowCacheData.rows), [], "overflow snapshots must render zero cached rows")
  assert.strictEqual(overflowCacheData.pageNum, 1)
  assert.strictEqual(overflowCacheData.total, 0)
  assert.strictEqual(overflowCacheData.pendingScrollTop, 0)
  assert.strictEqual(queueComponent.computed.hasMore.call(overflowCacheData), false, "overflow state must not expose a phantom page-301 load-more path")

  queueContext.canQuery = true
  queueContext.openDetail(queueContext.rows[0])
  const detailQuery = routePushes[routePushes.length - 1].query
  assert.strictEqual(detailQuery.stateKey, deepKey)
  assert.ok(!detailQuery.keyword && !detailQuery.scrollTop && !JSON.stringify(detailQuery).includes("李四"), "detail navigation should carry only opaque/non-sensitive state")

  queueModule.resetMobileHrQueueStateCacheForTests()
  queueModule.setMobileHrQueueStateNowForTests(1000)
  const expiredKey = queueModule.seedMobileHrQueueStateForTests({ status: "DRAFT", filters: { keyword: "secret" }, rows: maskedRows, total: 1, pageNum: 1, scrollTop: 1 }, ownerFingerprint)
  queueModule.setMobileHrQueueStateNowForTests(1000 + (16 * 60 * 1000))
  const expiredData = queueComponent.data.call({ $route: { query: { stateKey: expiredKey, pageNum: "999" } }, $store: authStore })
  assert.strictEqual(expiredData.cacheHit, false)
  assert.strictEqual(expiredData.pageNum, 10, "expired cache keys should fall back to bounded server restore")
  assert.strictEqual(expiredData.filters.keyword, "")

  queueModule.resetMobileHrQueueStateCacheForTests()
  queueModule.setMobileHrQueueStateNowForTests(5000)
  const createdKeys = []
  for (let index = 0; index < 8; index += 1) {
    createdKeys.push(queueModule.seedMobileHrQueueStateForTests({ status: "DRAFT", filters: { keyword: `pii-${index}` }, rows: maskedRows, total: 1, pageNum: 1, scrollTop: index }, ownerFingerprint))
  }
  const cacheSnapshot = Array.from(queueModule.getMobileHrQueueStateCacheSnapshotForTests())
  assert.strictEqual(cacheSnapshot.length, 6, "queue cache should evict deterministically at its entry bound")
  assert.ok(!JSON.stringify(cacheSnapshot).includes("pii-") && !JSON.stringify(cacheSnapshot).includes("139"), "cache diagnostics must not leak cached values")
  const evictedData = queueComponent.data.call({ $route: { query: { stateKey: createdKeys[0] } }, $store: authStore })
  assert.strictEqual(evictedData.cacheHit, false, "oldest key should be evicted first")
  queueModule.resetMobileHrQueueStateCacheForTests()
  assert.deepStrictEqual(Array.from(queueModule.getMobileHrQueueStateCacheSnapshotForTests()), [])

  queueContext.status = "DRAFT"
  queueContext.filters = { keyword: "", expectedEntryDateFrom: "", expectedEntryDateTo: "", targetDeptId: "", targetStoreId: "", employeeCategory: "", ownerUserId: "" }
  const boundedStart = listCalls.length
  const boundedFallback = queueContext.restorePageWindow(10, { reconcileRoute: true })
  listResolvers[boundedStart].resolve({ rows: [{ onboardingId: 6001 }], total: 1 })
  await boundedFallback
  assert.strictEqual(listCalls.length, boundedStart + 1, "bounded fallback should stop as soon as total is reached")

  const staleStart = listCalls.length
  const staleFallback = queueContext.restorePageWindow(3)
  const currentFallback = queueContext.loadPage({ reset: true })
  listResolvers[staleStart].resolve({ rows: Array.from({ length: 20 }, (_, index) => ({ onboardingId: 7000 + index })), total: 60 })
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(listCalls.length, staleStart + 2, "a stale fallback response should not continue replaying pages")
  listResolvers[staleStart + 1].resolve({ rows: [{ onboardingId: 8001 }], total: 1 })
  await Promise.all([staleFallback, currentFallback])
  assert.deepStrictEqual(Array.from(queueContext.rows, row => row.onboardingId), [8001], "stale fallback rows should not mix into current rows")

  let restoredFocus = 0
  queueContext.filterTriggerElement = { focus() { restoredFocus += 1 } }
  queueContext.filterVisible = true
  let prevented = 0
  queueContext.handleFilterKeydown({ key: "Escape", preventDefault() { prevented += 1 } })
  assert.strictEqual(prevented, 1)
  assert.strictEqual(queueContext.filterVisible, false)
  assert.strictEqual(restoredFocus, 1, "Escape should close the modal and restore trigger focus")
  let firstFocused = 0
  let tabPrevented = 0
  const firstFocusable = { focus() { firstFocused += 1 } }
  const lastFocusable = { focus() {} }
  fakeDocument.activeElement = lastFocusable
  queueContext.$refs.filterSheet = { querySelectorAll() { return [firstFocusable, lastFocusable] } }
  queueContext.handleFilterKeydown({ key: "Tab", shiftKey: false, preventDefault() { tabPrevented += 1 } })
  assert.strictEqual(tabPrevented, 1, "Tab on the last modal control should be trapped")
  assert.strictEqual(firstFocused, 1, "focus trap should wrap to the first modal control")

  let left = false
  queueContext.rows = maskedRows
  queueContext.filters.keyword = "leave-secret"
  queueContext.$refs.scrollRegion.scrollTop = 222
  queueComponent.beforeRouteLeave.call(queueContext, {}, {}, () => { left = true })
  assert.strictEqual(left, true)
  assert.ok(queueContext.stateKey, "route leave should persist an opaque state key")

  const lockedKey = queueContext.stateKey
  queueState.clearMobileHrQueueStateCache()
  authStore.getters.isLock = true
  queueComponent.beforeRouteLeave.call(queueContext, {}, {}, () => {})
  queueComponent.beforeDestroy.call(queueContext)
  assert.deepStrictEqual(queueState.getMobileHrQueueStateCacheSnapshotForTests(), [], "lock clear followed by leave/destroy must not reinsert queue state")
  authStore.getters.isLock = false
  const unlockedData = queueComponent.data.call({ $route: { query: { stateKey: lockedKey } }, $store: authStore })
  assert.strictEqual(unlockedData.cacheHit, false, "unlock return must not revive the pre-lock entry")

  queueContext.requestSequence = 0
  queueContext.optionsRequestSequence = 0
  queueContext.stateKey = ""
  queueContext.ownerFingerprint = ownerFingerprint
  queueContext.persistQueueState(10)
  assert.ok(queueState.getMobileHrQueueStateCacheSnapshotForTests().length, "ordinary authenticated persistence should still work")
  queueState.clearMobileHrQueueStateCache()
  authStore.getters.token = ""
  queueContext.persistQueueState(10)
  queueComponent.beforeRouteLeave.call(queueContext, {}, {}, () => {})
  assert.deepStrictEqual(queueState.getMobileHrQueueStateCacheSnapshotForTests(), [], "LogOut/FedLogOut token removal followed by leave must not reinsert state")

  const detailCalls = []
  const detailResolvers = []
  const actionCalls = { ready: [], returned: [], cancel: [], restore: [], confirm: [], conflicts: [] }
  const actionResolvers = { ready: [], returned: [], cancel: [], restore: [], confirm: [], conflicts: [] }
  let grantedPermissions = []
  const detailDocument = { activeElement: null }
  const apiDeferred = (bucket, args) => new Promise((resolve, reject) => {
    actionCalls[bucket].push(args)
    actionResolvers[bucket].push({ resolve, reject })
  })
  const detailModule = loadComponent("../src/views/mobile/hr/onboarding/detail.vue", {
    __document: detailDocument,
    "@/api/hr/onboarding": {
      getHrOnboarding(id) {
        detailCalls.push(id)
        return new Promise((resolve, reject) => detailResolvers.push({ resolve, reject }))
      },
      markHrOnboardingReady: (id, payload) => apiDeferred("ready", [id, payload]),
      returnHrOnboardingToDraft: (id, payload) => apiDeferred("returned", [id, payload]),
      cancelHrOnboarding: (id, payload) => apiDeferred("cancel", [id, payload]),
      restoreHrOnboarding: (id, payload) => apiDeferred("restore", [id, payload]),
      confirmHrOnboarding: (id, payload) => apiDeferred("confirm", [id, payload]),
      getHrOnboardingConflicts: id => apiDeferred("conflicts", [id])
    },
    "@/utils/permission": { checkPermi: permissions => permissions.some(permission => grantedPermissions.includes(permission)) },
    "@/api/hr/employee": { getHrEmployee: () => Promise.resolve({ data: null }) },
    "@/views/hr/onboarding/onboardingFieldConfig": {
      presentOnboardingLog: log => log && log.operationType === "STATE_CHANGE"
        ? { time: "2026-07-13 09:30", operator: log.operatorName || "系统", title: "退回待补资料", description: "状态由“待到岗”变为“待补资料”" }
        : null
    },
    "../components/MobileHrShell": {}
  }, true)
  const detailComponent = detailModule.default
  const detailPushes = []
  const detailMessages = []
  const detailContext = Object.assign(detailComponent.data.call({ $route: { params: { id: "42" }, query: { stateKey: "qs_safe_123456789", keyword: "PII" } } }), {
    $route: { path: "/mobile/hr/onboarding/42", params: { id: "42" }, query: { stateKey: "qs_safe_123456789", keyword: "PII" } },
    $router: {
      push(target) { detailPushes.push(target); return Promise.resolve() },
      replace(target) { detailPushes.push(target); return Promise.resolve() }
    },
    $message: {
      success(value) { detailMessages.push(["success", value]) },
      warning(value) { detailMessages.push(["warning", value]) },
      error(value) { detailMessages.push(["error", value]) }
    },
    $nextTick(callback) { if (callback) callback() },
    $refs: {}
  })
  Object.keys(detailComponent.methods).forEach(name => {
    detailContext[name] = (...args) => detailComponent.methods[name].apply(detailContext, args)
  })
  Object.keys(detailComponent.computed).forEach(name => {
    Object.defineProperty(detailContext, name, { configurable: true, get: () => detailComponent.computed[name].call(detailContext) })
  })

  ;["actionSheet", "cancelDialog", "confirmDialog", "resultDialog"].forEach(refName => {
    let firstFocus = 0
    let lastFocus = 0
    let rootFocus = 0
    const first = { focus() { firstFocus += 1 } }
    const last = { focus() { lastFocus += 1 } }
    const root = { focus() { rootFocus += 1 }, querySelectorAll() { return [first, last] } }
    detailContext.$refs[refName] = root
    detailContext.focusDialog(refName)
    assert.strictEqual(firstFocus, 1, `${refName} should focus its first enabled interactive control`)
    assert.strictEqual(rootFocus, 0, `${refName} should not focus the dialog root when a control exists`)

    let prevented = 0
    detailDocument.activeElement = root
    detailContext.handleDialogKeydown({ key: "Tab", shiftKey: false, preventDefault() { prevented += 1 } }, () => {}, refName)
    assert.strictEqual(firstFocus, 2, `${refName} Tab from the root should move to the first control`)
    detailDocument.activeElement = { outside: true }
    detailContext.handleDialogKeydown({ key: "Tab", shiftKey: true, preventDefault() { prevented += 1 } }, () => {}, refName)
    assert.strictEqual(lastFocus, 1, `${refName} Shift+Tab from outside should move to the last control`)
    assert.strictEqual(prevented, 2, `${refName} should trap both root/outside keyboard entries`)
  })
  let emptyRootFocus = 0
  detailContext.$refs.emptyDialog = { focus() { emptyRootFocus += 1 }, querySelectorAll() { return [] } }
  detailContext.focusDialog("emptyDialog")
  assert.strictEqual(emptyRootFocus, 1, "a dialog with no enabled controls should fall back to its root")
  detailContext.$refs = {}

  const invalidContext = Object.assign(detailComponent.data.call({ $route: { params: { id: "not-a-number" }, query: {} } }), {
    $route: { params: { id: "not-a-number" }, query: {} }, $nextTick() {}, $refs: {}
  })
  Object.keys(detailComponent.methods).forEach(name => {
    invalidContext[name] = (...args) => detailComponent.methods[name].apply(invalidContext, args)
  })
  await invalidContext.loadDetail()
  assert.strictEqual(detailCalls.length, 0, "invalid route IDs must never reach the detail endpoint")
  assert.ok(invalidContext.error, "invalid route IDs should render a safe retryable error")
  invalidContext.$route.params = {}
  await invalidContext.loadDetail()
  assert.strictEqual(detailCalls.length, 0, "missing route IDs must never reach the detail endpoint")

  const firstDetail = detailContext.loadDetail()
  detailContext.$route.params.id = "43"
  const secondDetail = detailContext.loadDetail()
  detailResolvers[1].resolve({ data: { onboardingId: 43, employeeName: "新记录", phoneNumberMasked: "139****0000", version: 2, allowedActions: [] } })
  await secondDetail
  detailResolvers[0].resolve({ data: { onboardingId: 42, employeeName: "旧记录", phoneNumberMasked: "138****0000", version: 1, allowedActions: [] } })
  await firstDetail
  assert.strictEqual(detailContext.detail.onboardingId, 43, "stale detail responses must never replace the current route record")

  detailContext.detail = {
    onboardingId: 43, status: "DRAFT", version: 7, onboardingCompletionPercent: 60,
    allowedActions: ["EDIT", "MARK_READY", "RETURN_TO_DRAFT", "CONFIRM", "CANCEL", "RESTORE", "UNTRUSTED"]
  }
  grantedPermissions = ["hr:onboarding:edit", "hr:onboarding:ready", "hr:onboarding:return", "hr:onboarding:confirm", "hr:onboarding:cancel", "hr:onboarding:restore"]
  const actionKeys = detailComponent.computed.availableActions.call(detailContext).map(action => action.key)
  assert.deepStrictEqual(Array.from(actionKeys), ["EDIT", "MARK_READY", "RETURN_TO_DRAFT", "CANCEL", "RESTORE"],
    "known actions need both gates and incomplete drafts must not expose confirm")
  assert.strictEqual(detailContext.primaryAction.key, "MARK_READY")
  assert.strictEqual(detailContext.primaryActionLabel, "标记资料就绪", "a server-approved ready action should be primary on DRAFT")
  assert.strictEqual(detailContext.employeeCategoryLabel("FULL_TIME"), "正式员工")
  detailContext.detail.operationLogs = [{ operationType: "STATE_CHANGE", operatorName: "HR" }]
  assert.strictEqual(detailContext.presentedOperationLogs[0].title, "退回待补资料")
  assert.ok(detailContext.secondaryActions.length, "other permitted DRAFT actions should remain in the secondary sheet")
  grantedPermissions = ["hr:onboarding:confirm"]
  assert.deepStrictEqual(Array.from(detailComponent.computed.availableActions.call(detailContext).map(action => action.key)), [],
    "permission alone must never expose an action")
  detailContext.detail.status = "READY"
  detailContext.detail.onboardingCompletionPercent = 100
  detailContext.detail.allowedActions = ["CONFIRM"]
  assert.deepStrictEqual(Array.from(detailComponent.computed.availableActions.call(detailContext).map(action => action.key)), ["CONFIRM"],
    "READY complete records should expose confirm only when permission and allowedActions agree")
  assert.strictEqual(detailContext.primaryAction.key, "CONFIRM", "READY confirm should be the primary action")

  grantedPermissions = ["hr:onboarding:edit"]
  detailContext.detail = { onboardingId: 43, status: "DRAFT", version: 7, onboardingCompletionPercent: 50, allowedActions: ["EDIT"] }
  detailContext.openEdit()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(detailPushes.pop())), { path: "/mobile/hr/onboarding/43/edit", query: { stateKey: "qs_safe_123456789" } },
    "edit navigation should preserve only the opaque queue state")
  detailContext.goBack()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(detailPushes.pop())), { path: "/mobile/hr/onboarding", query: { stateKey: "qs_safe_123456789" } },
    "back navigation should preserve only the opaque queue state")
  assert.strictEqual(detailComponent.methods.navigationQuery.call({ $route: { query: { stateKey: "张三13800000000" } } }), undefined,
    "a non-opaque stateKey must not become a PII side channel")
  const backPushCount = detailPushes.length
  let detailRouterBackCalls = 0
  shellComponent.methods.handleBack.call({
    back: detailContext.goBack,
    $emit() { throw new Error("functional detail back must not emit") },
    $router: { back() { detailRouterBackCalls += 1 } }
  })
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(detailPushes.length, backPushCount + 1, "one detail back click should issue exactly one list navigation")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(detailPushes.pop())), { path: "/mobile/hr/onboarding", query: { stateKey: "qs_safe_123456789" } })
  assert.strictEqual(detailRouterBackCalls, 0, "detail back should never also call router.back")

  let detailTriggerFocus = 0
  const detailTrigger = { focus() { detailTriggerFocus += 1 } }
  detailContext.$refs.actionTrigger = detailTrigger
  grantedPermissions = ["hr:onboarding:cancel"]
  detailContext.detail = { onboardingId: 43, status: "DRAFT", version: 7, onboardingCompletionPercent: 50, allowedActions: ["CANCEL"] }
  detailContext.openActionSheet({ currentTarget: detailTrigger })
  detailContext.openCancel()
  assert.strictEqual(detailTriggerFocus, 0, "moving from the action sheet into a dialog must not focus inert background content")
  detailContext.closeCancel()
  assert.strictEqual(detailTriggerFocus, 1, "closing a dialog should restore focus to the action trigger")

  const runStateAction = async (key, permission, bucket) => {
    grantedPermissions = [permission]
    detailContext.detail = { onboardingId: 43, status: key === "RESTORE" ? "CANCELLED" : "DRAFT", version: 7, onboardingCompletionPercent: 100, allowedActions: [key] }
    const first = detailContext.performAction(key)
    const duplicate = detailContext.performAction(key)
    assert.strictEqual(actionCalls[bucket].length, 1, `${key} should suppress duplicate submissions`)
    assert.deepStrictEqual(JSON.parse(JSON.stringify(actionCalls[bucket][0])), [43, { version: 7 }], `${key} should send the current version`)
    actionResolvers[bucket][0].resolve({ data: { onboardingId: 43, version: 8, allowedActions: [] } })
    await Promise.all([first, duplicate])
    detailContext.actionSubmitting = false
  }
  await runStateAction("MARK_READY", "hr:onboarding:ready", "ready")
  await runStateAction("RETURN_TO_DRAFT", "hr:onboarding:return", "returned")
  await runStateAction("RESTORE", "hr:onboarding:restore", "restore")

  grantedPermissions = ["hr:onboarding:ready"]
  detailContext.detail = { onboardingId: 43, status: "DRAFT", version: 13, onboardingCompletionPercent: 100, allowedActions: ["MARK_READY"] }
  const conflictReadyIndex = actionCalls.ready.length
  const conflictDetailIndex = detailCalls.length
  const versionConflictRequest = detailContext.performAction("MARK_READY")
  actionResolvers.ready[conflictReadyIndex].reject({ response: { data: { errorCode: "ONBOARDING_VERSION_CONFLICT" } } })
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(detailCalls[conflictDetailIndex], 43, "a version conflict should refresh the current numeric record")
  assert.ok(detailMessages.some(message => message[0] === "warning" && /最新版本/.test(message[1])), "a version conflict should inform the user")
  detailResolvers[conflictDetailIndex].resolve({ data: { onboardingId: 43, status: "READY", version: 14, onboardingCompletionPercent: 100, allowedActions: ["CONFIRM"] } })
  await versionConflictRequest
  assert.strictEqual(detailContext.detail.version, 14, "a version conflict should install the safely refreshed detail")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(detailContext.navigationQuery())), { stateKey: "qs_safe_123456789" }, "a conflict refresh must preserve unrelated opaque queue state")

  grantedPermissions = ["hr:onboarding:cancel"]
  detailContext.detail = { onboardingId: 43, status: "CONFIRMED", version: 9, onboardingCompletionPercent: 100, allowedActions: ["CANCEL"] }
  let primaryCancelFocus = 0
  const primaryCancelTrigger = { focus() { primaryCancelFocus += 1 } }
  await detailContext.performAction("CANCEL", { currentTarget: primaryCancelTrigger })
  assert.strictEqual(detailContext.primaryAction.key, "CANCEL", "a sole non-DRAFT cancel action should be primary")
  assert.strictEqual(primaryCancelFocus, 0, "opening primary cancel must not focus inert background content")
  detailContext.closeCancel()
  assert.strictEqual(primaryCancelFocus, 1, "closing a direct primary cancel dialog should restore its trigger once")
  detailContext.closeCancel()
  assert.strictEqual(primaryCancelFocus, 1, "closing primary cancel repeatedly must not restore focus twice")

  detailContext.detail = { onboardingId: 43, status: "DRAFT", version: 9, onboardingCompletionPercent: 50, allowedActions: ["CANCEL"] }
  detailContext.openCancel()
  detailContext.cancelReason = "   "
  await detailContext.submitCancel()
  assert.strictEqual(actionCalls.cancel.length, 0, "blank cancel reasons must be rejected locally")
  assert.ok(detailContext.cancelError)
  detailContext.cancelReason = "超".repeat(201)
  await detailContext.submitCancel()
  assert.strictEqual(actionCalls.cancel.length, 0, "cancel reasons over 200 characters must not call the API")
  assert.ok(/200/.test(detailContext.cancelError), "overlong cancel reasons should show the backend-aligned limit")
  detailContext.cancelReason = " 候选人取消 "
  const cancelRequest = detailContext.submitCancel()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(actionCalls.cancel[0])), [43, { version: 9, reason: "候选人取消" }])
  actionResolvers.cancel[0].resolve({ data: { onboardingId: 43, version: 10, status: "CANCELLED", allowedActions: [] } })
  await cancelRequest

  grantedPermissions = ["hr:onboarding:confirm"]
  detailContext.detail = {
    onboardingId: 43, status: "READY", version: 11, onboardingCompletionPercent: 100, allowedActions: ["CONFIRM"],
    preferredConflictAction: "BIND_EXISTING", preferredBindUserId: 88
  }
  let dismissedConfirmFocus = 0
  const dismissedConfirmTrigger = { focus() { dismissedConfirmFocus += 1 } }
  const dismissedPreview = detailContext.performAction("CONFIRM", { currentTarget: dismissedConfirmTrigger })
  actionResolvers.conflicts[0].resolve({ data: [] })
  await dismissedPreview
  assert.strictEqual(dismissedConfirmFocus, 0, "opening primary confirm must not focus inert background content")
  detailContext.closeConfirm()
  assert.strictEqual(dismissedConfirmFocus, 1, "closing a direct primary confirm dialog should restore its trigger once")
  detailContext.closeConfirm()
  assert.strictEqual(dismissedConfirmFocus, 1, "closing primary confirm repeatedly must not restore focus twice")

  let resultConfirmFocus = 0
  const resultConfirmTrigger = { focus() { resultConfirmFocus += 1 } }
  const conflictsRequest = detailContext.performAction("CONFIRM", { currentTarget: resultConfirmTrigger })
  assert.deepStrictEqual(actionCalls.conflicts[1], [43], "confirm should fetch the masked conflict preview before submit")
  actionResolvers.conflicts[1].resolve({ data: [
    { candidateUserId: 88, name: "王某", maskedPhone: "138****1111", departmentLabel: "上海一店", eligibleForBind: true, allowedDecisions: ["BIND_EXISTING"], phoneNumber: "13800001111", idNumber: "RAW-ID" },
    { candidateUserId: 99, name: "李某", maskedPhone: "139****2222", departmentLabel: "上海二店", eligibleForBind: false, allowedDecisions: [] },
    { candidateUserId: "not-a-number", name: "非法候选", maskedPhone: "137****3333", departmentLabel: "未知部门", eligibleForBind: true, allowedDecisions: ["BIND_EXISTING"] }
  ] })
  await conflictsRequest
  assert.strictEqual(detailContext.conflicts[0].phoneNumberMasked, "138****1111")
  assert.ok(!Object.prototype.hasOwnProperty.call(detailContext.conflicts[0], "phoneNumber") && !Object.prototype.hasOwnProperty.call(detailContext.conflicts[0], "idNumber"),
    "conflict candidates must be reduced to a display-safe whitelist")
  assert.strictEqual(detailContext.conflicts[2].candidateUserId, null)
  assert.strictEqual(detailContext.conflicts[2].eligibleForBind, false, "a truthy nonnumeric candidate ID must never be bind eligible")
  assert.ok(!detailContext.conflicts[2].key.includes("not-a-number"), "candidate keys should use only the normalized ID or safe index")
  assert.strictEqual(detailContext.confirmModel.conflictAction, "BIND_EXISTING")
  assert.strictEqual(detailContext.confirmModel.bindUserId, 88, "an import-approved preferred candidate may be prefilled while still eligible")
  detailContext.confirmModel.actualEntryDate = "2026-07-12"
  detailContext.confirmModel.bindUserId = null
  await detailContext.submitConfirm()
  assert.strictEqual(actionCalls.confirm.length, 0, "BIND_EXISTING with a null candidate must never call confirm")
  detailContext.confirmModel.bindUserId = 88
  const confirmRequest = detailContext.submitConfirm()
  const duplicateConfirm = detailContext.submitConfirm()
  assert.strictEqual(actionCalls.confirm.length, 1, "confirm should suppress duplicate submissions")
  const confirmPayload = actionCalls.confirm[0][1]
  assert.strictEqual(confirmPayload.version, 11)
  assert.strictEqual(confirmPayload.actualEntryDate, "2026-07-12")
  assert.strictEqual(confirmPayload.conflictAction, "BIND_EXISTING")
  assert.strictEqual(confirmPayload.bindUserId, 88)
  assert.ok(/^[0-9a-f-]{36}$/i.test(confirmPayload.idempotencyKey), "each confirmation should send a fresh UUID idempotency key")
  const successRefreshIndex = detailCalls.length
  actionResolvers.confirm[0].resolve({ data: { onboardingId: 43, userId: 101, employeeNo: "E101", accountStatus: "ENABLED", oneTimePassword: "Once-Only", oneTimePasswordExpiresAt: "2026-07-12T00:00:00+00:00", replayed: false, version: 12 } })
  await Promise.all([confirmRequest, duplicateConfirm])
  assert.strictEqual(detailContext.detail.status, "CONFIRMED", "confirm success should immediately lock the local detail to CONFIRMED")
  assert.strictEqual(detailContext.detail.version, 12, "confirm success should install a returned version")
  assert.deepStrictEqual(Array.from(detailContext.detail.allowedActions), [], "confirm success should immediately disable every stale action")
  assert.ok(!detailContext.availableActions.some(action => action.key === "CONFIRM"), "confirm must disappear before background refresh")
  assert.strictEqual(detailContext.oneTimePassword, "Once-Only", "returned OTP should be visible exactly once in component state")
  assert.strictEqual(detailContext.oneTimePasswordExpiresAt, "2026-07-12T00:00:00+00:00", "OTP expiry should stay only while the secret is visible")
  assert.ok(Number.isFinite(new Date(detailContext.oneTimePasswordExpiresAt).getTime()), "mobile must parse ISO-8601 expiry with timezone")
  assert.ok(detailContext.oneTimePasswordExpiresAtDisplay, "mobile result UI must show temporary password expiry")
  assert.ok(detailContext.resultMessage.includes("首次登录必须改密"), "mobile create-new result must require password change")
  assert.ok(detail.includes("oneTimePasswordExpiresAtDisplay"), "mobile template must render temporary password expiry")
  assert.ok(detail.includes("首次登录必须修改密码"), "mobile template must require first-login password change")
  assert.ok(!Object.prototype.hasOwnProperty.call(detailContext.confirmResult, "oneTimePassword"), "ordinary result state must not retain the OTP")
  assert.ok(!Object.prototype.hasOwnProperty.call(detailContext.confirmResult, "oneTimePasswordExpiresAt"), "ordinary result state must not retain OTP expiry")
  assert.strictEqual(detailCalls[successRefreshIndex], 43, "confirm success should start a guarded background detail refresh")
  const conflictCallsAfterSuccess = actionCalls.conflicts.length
  const confirmCallsAfterSuccess = actionCalls.confirm.length
  await detailContext.performAction("CONFIRM", { currentTarget: resultConfirmTrigger })
  assert.strictEqual(actionCalls.conflicts.length, conflictCallsAfterSuccess, "locally confirmed detail must not reopen conflict preview")
  assert.strictEqual(actionCalls.confirm.length, confirmCallsAfterSuccess, "locally confirmed detail must not issue another new-key confirmation")
  detailResolvers[successRefreshIndex].resolve({ data: { onboardingId: 43, status: "CONFIRMED", version: 13, onboardingCompletionPercent: 100, allowedActions: [], phoneNumberMasked: "138****1111" } })
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(detailContext.detail.version, 13, "background confirmation refresh should install the latest safe detail")
  assert.strictEqual(detailContext.oneTimePassword, "Once-Only", "background refresh must not clear the OTP modal")
  assert.strictEqual(detailContext.oneTimePasswordExpiresAt, "2026-07-12T00:00:00+00:00", "background refresh must not clear OTP expiry")
  assert.strictEqual(detailContext.resultVisible, true, "background refresh must not close the result modal")
  assert.strictEqual(resultConfirmFocus, 0, "result display must keep focus inside the modal")
  detailContext.closeOtp()
  assert.strictEqual(detailContext.oneTimePassword, null, "closing the OTP modal must destroy the secret")
  assert.strictEqual(detailContext.oneTimePasswordExpiresAt, null, "closing the OTP modal must destroy OTP expiry")
  assert.strictEqual(detailContext.detail.status, "CONFIRMED", "closing result must not restore stale READY state")
  assert.strictEqual(detailContext.detail.version, 13)
  assert.strictEqual(resultConfirmFocus, 1, "closing the result from direct primary confirm should restore its trigger once")
  detailContext.closeOtp()
  assert.strictEqual(resultConfirmFocus, 1, "closing the result repeatedly must not restore focus twice")

  detailContext.$route.path = "/mobile/hr/onboarding/44"
  detailContext.$route.params.id = "44"
  detailContext.detail = { onboardingId: 44, status: "READY", version: 21, onboardingCompletionPercent: 100, allowedActions: ["CONFIRM"] }
  const replayConflicts = detailContext.openConfirm()
  actionResolvers.conflicts[2].resolve({ data: [] })
  await replayConflicts
  detailContext.confirmModel.actualEntryDate = "2026-07-13"
  detailContext.confirmModel.conflictAction = "CREATE_NEW"
  detailContext.confirmModel.bindUserId = 88
  const retainedReplayKey = detailContext.idempotencyKey
  const uncertainRequest = detailContext.submitConfirm()
  const uncertainPayload = actionCalls.confirm[1][1]
  assert.strictEqual(uncertainPayload.bindUserId, null, "CREATE_NEW must never send a stale bind candidate")
  assert.strictEqual(uncertainPayload.idempotencyKey, retainedReplayKey)
  actionResolvers.confirm[1].reject(new Error("unknown network outcome"))
  await uncertainRequest
  assert.strictEqual(detailContext.idempotencyKey, retainedReplayKey, "unknown outcomes must retain the original idempotency key")
  assert.strictEqual(detailContext.confirmVisible, true, "unknown outcomes should keep the same confirmation flow open")

  const replayRefreshIndex = detailCalls.length
  const replayRequest = detailContext.submitConfirm()
  const replayPayload = actionCalls.confirm[2][1]
  assert.strictEqual(replayPayload.idempotencyKey, retainedReplayKey, "retry must reuse the exact uncertain-outcome key")
  assert.strictEqual(actionCalls.confirm.length, 3, "replay should be the retry of one uncertain call, not a new confirmation flow")
  actionResolvers.confirm[2].resolve({ data: { onboardingId: 44, userId: 202, employeeNo: "E202", accountStatus: "ENABLED", oneTimePassword: null, replayed: true, version: 22 } })
  await replayRequest
  assert.strictEqual(detailContext.detail.status, "CONFIRMED")
  assert.deepStrictEqual(Array.from(detailContext.detail.allowedActions), [])
  assert.strictEqual(detailContext.oneTimePassword, null)
  assert.ok(/重置密码/.test(detailContext.resultMessage) && /不会再次展示/.test(detailContext.resultMessage),
    "idempotent replay should direct the user to the normal reset-password flow")
  assert.strictEqual(detailCalls[replayRefreshIndex], 44)
  detailResolvers[replayRefreshIndex].reject(new Error("refresh unavailable"))
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(detailContext.detail.status, "CONFIRMED", "refresh failure must keep the safe local confirmed state")
  assert.strictEqual(detailContext.resultVisible, true, "refresh failure must not close replay result")
  assert.ok(detailContext.confirmRefreshWarning, "refresh failure should leave a non-destructive warning")
  assert.ok(detailMessages.some(message => message[0] === "warning" && /最新详情/.test(message[1])))
  detailContext.closeOtp()
  assert.strictEqual(detailContext.detail.status, "CONFIRMED", "closing replay result must keep confirmed state")

  detailContext.$route.path = "/mobile/hr/onboarding/45"
  detailContext.$route.params.id = "45"
  detailContext.detail = { onboardingId: 45, status: "CONFIRMED", version: 30, allowedActions: [] }
  const staleRefreshIndex = detailCalls.length
  const staleConfirmedRefresh = detailContext.refreshConfirmedDetail(45)
  detailContext.$route.path = "/mobile/hr/onboarding/46"
  detailContext.$route.params.id = "46"
  detailContext.detail = { onboardingId: 46, status: "DRAFT", version: 1, allowedActions: ["EDIT"] }
  detailResolvers[staleRefreshIndex].resolve({ data: { onboardingId: 45, status: "CONFIRMED", version: 31, allowedActions: [] } })
  await staleConfirmedRefresh
  assert.strictEqual(detailContext.detail.onboardingId, 46, "a stale confirmation refresh must not overwrite another route")

  detailContext.oneTimePassword = "destroy-me"
  detailContext.oneTimePasswordExpiresAt = "2026-07-12T00:00:00+00:00"
  let detailLeft = false
  detailComponent.beforeRouteLeave.call(detailContext, {}, {}, () => { detailLeft = true })
  assert.strictEqual(detailLeft, true)
  assert.strictEqual(detailContext.oneTimePassword, null, "route leave must destroy OTP state")
  assert.strictEqual(detailContext.oneTimePasswordExpiresAt, null, "route leave must destroy OTP expiry")
}

runPageBehaviorTests()
  .then(() => console.log("mobileHrOnboarding tests passed"))
  .catch(error => {
    console.error(error)
    process.exitCode = 1
  })
