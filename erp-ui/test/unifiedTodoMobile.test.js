const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.resolve(root, relativePath), "utf8")

const routeSource = read("src/views/mobile/mobileRouteDefinitions.js")
const navigationSource = read("src/views/mobile/mobileNavigation.js")
const shellSource = read("src/views/mobile/components/MobileWorkbenchShell.vue")
const inventorySource = read("src/views/mobile/inventory/index.vue")
const mobileTodoPath = path.resolve(root, "src/views/mobile/todo/index.vue")
const mobileTodoQuickSheetPath = path.resolve(root, "src/views/mobile/todo/components/MobileTodoQuickApproveSheet.vue")
const mobileTodoBatchSheetPath = path.resolve(root, "src/views/mobile/todo/components/MobileTodoBatchApproveSheet.vue")
const mobileHrEmployeePath = path.resolve(root, "src/views/mobile/hr/employee/index.vue")
const mobileHrOnboardingPath = path.resolve(root, "src/views/mobile/hr/onboarding/index.vue")
const mobileHrOnboardingDetailPath = path.resolve(root, "src/views/mobile/hr/onboarding/detail.vue")
const mobileHrCompletenessPath = path.resolve(root, "src/views/mobile/hr/completeness/index.vue")
const mobileHrProfileEditorPath = path.resolve(root, "src/views/mobile/hr/components/MobileHrProfileEditor.vue")
const { mobileRouteDefinitions } = require("../src/views/mobile/mobileRouteDefinitions")
const { mobileBottomNav, isMobileContextOptionalPath } = require("../src/views/mobile/mobileNavigation")
const mobileWorkbenchPolicy = require("../src/views/mobile/components/mobileWorkbenchPolicy")
const todoApprovalActions = require("../src/utils/todoApprovalActions")

function deferred() {
  let resolve
  let reject
  const promise = new Promise((onResolve, onReject) => {
    resolve = onResolve
    reject = onReject
  })
  return { promise, resolve, reject }
}

function loadTodoFilterQuery() {
  const file = path.resolve(__dirname, "../src/utils/todoFilterQuery.js")
  let source = fs.readFileSync(file, "utf8")
    .replace(/export\s+const\s+/g, "const ")
    .replace(/export\s+function\s+/g, "function ")
  source += `
module.exports = {
  normalizeTodoFilterQuery,
  buildTodoFilterQuery,
  todoFilterStateEquals,
  todoRouteQueryMatches,
  hasActiveTodoFilters
}`
  const sandbox = { module: { exports: {} }, exports: {}, Object, Array, String, Number, JSON }
  vm.runInNewContext(source, sandbox, { filename: file })
  return sandbox.module.exports
}

const mobileFilterQuery = loadTodoFilterQuery()

assert.ok(fs.existsSync(mobileTodoPath), "mobile unified todo page must exist")
const todoSource = fs.existsSync(mobileTodoPath) ? fs.readFileSync(mobileTodoPath, "utf8") : ""
assert.ok(fs.existsSync(mobileTodoQuickSheetPath), "mobile quick approval sheet must exist")
assert.ok(fs.existsSync(mobileTodoBatchSheetPath), "mobile batch approval sheet must exist")
const quickApprovalSheetSource = fs.readFileSync(mobileTodoQuickSheetPath, "utf8")
const batchApprovalSheetSource = fs.readFileSync(mobileTodoBatchSheetPath, "utf8")
const mobileApprovalSource = `${todoSource}\n${quickApprovalSheetSource}\n${batchApprovalSheetSource}`

const todoRoute = mobileRouteDefinitions.find(route => route.path === "/mobile/todo")
assert.ok(todoRoute && todoRoute.hidden === true, "/mobile/todo must be a hidden processing route")
assert.ok(todoRoute && String(todoRoute.component).includes("mobile/todo"), "/mobile/todo must load the unified mobile todo page")
assert.strictEqual(mobileBottomNav.length, 5, "todo must not become a sixth bottom-nav item")
assert.ok(!mobileBottomNav.some(item => item.path === "/mobile/todo"), "todo must stay out of the bottom navigation")
assert.strictEqual(isMobileContextOptionalPath("/mobile/todo"), true,
  "direct-owner mobile todos must remain reachable when no shop context is selected")
assert.ok(!navigationSource.includes('label: "待办", icon') || !mobileBottomNav.some(item => item.label === "待办"),
  "the existing mobile business shortcuts must remain unchanged")
for (const [routePath, componentPart, permission] of [
  ["/mobile/hr/employee", "mobile/hr/employee", "hr:employee:list"],
  ["/mobile/hr/onboarding", "mobile/hr/onboarding", "hr:onboarding:list"],
  ["/mobile/hr/completeness", "mobile/hr/completeness", "hr:completeness:list"]
]) {
  const route = mobileRouteDefinitions.find(item => item.path === routePath)
  assert.ok(route && route.hidden === true, `${routePath} must be a hidden todo processing route`)
  assert.ok(String(route.component).includes(componentPart), `${routePath} must load its HR page`)
  assert.ok(route.meta && route.meta.permissions === permission, `${routePath} must declare ${permission}`)
}
for (const file of [mobileHrEmployeePath, mobileHrOnboardingPath, mobileHrCompletenessPath]) {
  assert.ok(fs.existsSync(file), `${file} should exist`)
}
const mobileHrEmployeeSource = fs.existsSync(mobileHrEmployeePath) ? fs.readFileSync(mobileHrEmployeePath, "utf8") : ""
const mobileHrOnboardingSource = fs.existsSync(mobileHrOnboardingPath) ? fs.readFileSync(mobileHrOnboardingPath, "utf8") : ""
const mobileHrOnboardingDetailSource = fs.existsSync(mobileHrOnboardingDetailPath) ? fs.readFileSync(mobileHrOnboardingDetailPath, "utf8") : ""
const mobileHrCompletenessSource = fs.existsSync(mobileHrCompletenessPath) ? fs.readFileSync(mobileHrCompletenessPath, "utf8") : ""
const mobileHrProfileEditorSource = fs.existsSync(mobileHrProfileEditorPath) ? fs.readFileSync(mobileHrProfileEditorPath, "utf8") : ""
assert.ok(mobileHrEmployeeSource.includes("offboardAccountOnly") && mobileHrEmployeeSource.includes("changeUserStatus") &&
  mobileHrEmployeeSource.includes("todo/refreshSummaries"),
"mobile HR employee page must handle offboard-account todos and refresh after disabling")
assert.ok(mobileHrOnboardingDetailSource.includes("confirmHrOnboarding") && mobileHrOnboardingDetailSource.includes("cancelHrOnboarding") &&
  mobileHrOnboardingDetailSource.includes("todo/refreshSummaries"),
"mobile HR onboarding detail must support confirm/cancel and refresh todos")
assert.ok(mobileHrOnboardingDetailSource.includes("入职必填完成度"))
assert.ok(!mobileHrOnboardingDetailSource.includes('<span>员工档案</span>'))
assert.ok(mobileHrOnboardingDetailSource.includes("档案覆盖度"))
assert.ok(mobileHrOnboardingDetailSource.includes("getHrEmployee(row.linkedUserId)"))
assert.ok(mobileHrCompletenessSource.includes("listHrCompleteness") && mobileHrCompletenessSource.includes("missingFields"),
  "mobile HR completeness page must show missing profile fields")
assert.ok(!mobileHrCompletenessSource.includes("const REQUIRED"),
  "mobile completeness must not own a second field list")
assert.ok(mobileHrCompletenessSource.includes("profileCompletionPercent"))
assert.ok(mobileHrCompletenessSource.includes("missingProfileFields"))
assert.ok(mobileHrCompletenessSource.includes("userId: row.userId"))
assert.ok(!mobileHrCompletenessSource.includes("profileId:"))
assert.ok(mobileHrEmployeeSource.includes("getHrEmployee"))
assert.ok(mobileHrEmployeeSource.includes("updateHrEmployee(userId, patch)"))
assert.ok(mobileHrEmployeeSource.includes("accountEnabled"))
assert.ok(mobileHrProfileEditorSource.includes("buildPatch"))
assert.ok(mobileHrProfileEditorSource.includes("dirtySensitiveFields"))
for (const [source, label] of [[mobileHrOnboardingSource, "onboarding"], [mobileHrCompletenessSource, "completeness"]]) {
  assert.ok(source.includes("pageSize: 20") && source.includes("hasMore") && source.includes("loadMore"),
    `mobile HR ${label} page must paginate instead of truncating or rendering 100 records at once`)
}

for (const source of [shellSource, inventorySource]) {
  assert.ok(source.includes("todoTotal") && source.includes("todoCounts") && source.includes("todoRecent"),
    "mobile workbenches must read the shared Todo Store summary")
  assert.ok(source.includes("todoProviderStates"), "mobile workbenches must expose provider health")
  assert.ok(source.includes("/mobile/notice"), "notice must remain a separate action from todo")
  assert.ok(source.includes("listNoticeTop") && source.includes("noticeUnreadCount"),
    "notice must retain its own unread count instead of borrowing the todo total")
  assert.ok(source.includes("/mobile/todo"), "mobile workbenches must expose the unified todo entry")
}
assert.ok(todoSource.includes("normalizeTodoFilterQuery") &&
  !todoSource.includes('aria-label="组织范围"') &&
  !todoSource.includes('value="current_org"') && !todoSource.includes('value="all_authorized"'),
  "mobile todo must not expose either legacy organization-scope control")
assert.ok(/import\s*\{[^}]*hasValidInventoryDeptContext[^}]*\}\s*from\s*['"]@\/utils\/shopContext['"]/.test(todoSource) &&
  !/function\s+hasValidTodoContext\s*\(/.test(todoSource),
  "mobile todo must reuse the shared inventory context predicate instead of a local copy")
assert.ok(todoSource.includes("退回修改") && !todoSource.includes("已驳回"),
  "mobile todo must use the unified returned category label")
assert.ok(todoSource.includes("aggregateSummaries") &&
  todoSource.includes("fetchTodoSummary") && todoSource.includes("scopedProviderStates") &&
  !todoSource.includes("todoTotal: 'todoTotal'") && !todoSource.includes("todoCounts: 'todoCounts'"),
  "mobile todo summary counts must fetch and aggregate the selected page scope instead of global Todo Store totals")
assert.ok(shellSource.includes("todoEmptyTitle") && inventorySource.includes("todoEmptyTitle"),
  "pending, unknown, stale, and fresh empty todo lists need explicit workbench copy")
assert.ok(todoSource.includes('aria-label="刷新待办"'), "the icon-only mobile todo refresh button needs an accessible name")
assert.ok(todoSource.includes('erp:dept-changed') && todoSource.includes('handleDeptChanged') &&
  todoSource.includes('requestVersion') && todoSource.includes('requestedPage'),
"mobile todo must isolate organization changes and paged requests with local request identity")
assert.ok(todoSource.includes("isTodoRouteActive") && todoSource.includes("suspendTodoActivity") &&
  todoSource.includes("activated()") && todoSource.includes("deactivated()"),
"mobile todo must stop cached listeners from touching a business route")
assert.ok(todoSource.includes("scopeDescription") && todoSource.includes("contextSnapshot.deptName") &&
  todoSource.includes("本人专属待办跨已授权门店") &&
  todoSource.includes("选择门店后显示公共待办"),
"mobile todo must name the current organization and explain scope semantics")
assert.ok(shellSource.includes("getMobileWorkbenchSummary") && shellSource.includes("workbenchSummary"),
  "the shared mobile shell must retain inventory summary compatibility for profile metrics")
assert.ok(!shellSource.includes("summary.todoCount") && !shellSource.includes("summary.todos"),
  "the compatibility summary must never become a second todo count or list")
assert.ok(!inventorySource.includes("getMobileWorkbenchSummary"),
  "the inventory page should keep summary loading behind its workbench service")
assert.ok(shellSource.includes("99+") && shellSource.includes("todoBadgeText"), "mobile shell todo badge must cap at 99+")
assert.ok(shellSource.includes("todoRecent") && shellSource.includes("approval") &&
  shellSource.includes("execution") && shellSource.includes("risk"),
"mobile shell cards must come from recent approval, execution, and risk todos")

for (const category of ["all", "approval", "execution", "returned", "risk", "personal"]) {
  assert.ok(todoSource.includes(`value: "${category}"`) || todoSource.includes(`value: '${category}'`),
    `mobile todo page must offer the ${category} category`)
}
for (const source of ["all", "inventory", "oa", "system"]) {
  assert.ok(todoSource.includes(`value: "${source}"`) || todoSource.includes(`value: '${source}'`),
    `mobile todo page must offer the ${source} source`)
}
for (const priority of ["all", "urgent", "important", "normal"]) {
  assert.ok(todoSource.includes(`value: "${priority}"`) || todoSource.includes(`value: '${priority}'`),
    `mobile todo page must offer the ${priority} priority`)
}
assert.ok(!todoSource.includes("current_org") && !todoSource.includes("all_authorized"),
  "mobile todo page must not restore legacy organization scopes")
assert.ok(todoSource.includes("todo/refreshPage") && todoSource.includes("loadMore") && todoSource.includes("todoKey"),
  "mobile todo page must load cached Todo Store pages and deduplicate accumulated rows")
assert.ok(todoSource.includes("total === null") && todoSource.includes("总数未知"),
  "mobile todo page must render unknown totals explicitly")
assert.ok(todoSource.includes("TODO_SORT_LABEL") && todoSource.includes('class="todo-sort-label"') &&
  todoSource.includes("{{ sortLabel }}"),
"mobile todo should expose the fixed priority and creation-time ordering beside the list title")
assert.ok(todoSource.includes("summaryScopeHelp") && todoSource.includes("当前关键词在各分类中的结果"),
  "mobile summary guidance should explain when category counts are filtered by a keyword")
assert.ok(todoSource.includes("providerStates") && todoSource.includes("staleSources") && todoSource.includes("unknownSources"),
  "mobile todo page must render failed, stale, and unknown provider states")
assert.ok(todoSource.includes("navigateTodo") && todoSource.includes("platform: 'mobile'") &&
  todoSource.includes("constantRoutes") && todoSource.includes("permission_routes") &&
  todoSource.includes("setSelectedDept") && todoSource.includes("todo/refreshSummaries"),
"mobile todo processing must use the shared safe navigator and refresh after context/permission failures")
assert.ok(todoSource.includes("beginContextLease: beginTodoContextLease") &&
  todoSource.includes("rollbackContextLease") && todoSource.includes("getReturnRoute") &&
  todoSource.includes("formatTodoContextSwitchNotice"),
"mobile todo processing must preserve, restore, and explain temporary organization context")
const openTodoSource = (todoSource.match(/openTodo\(row\)\s*\{([\s\S]*?)\n    \}\n  \}\n\}/) || [])[1] || ""
assert.ok(openTodoSource && !openTodoSource.includes("invalidateAfterMutation"),
  "opening a todo must not complete it locally")
assert.ok(todoSource.includes("MobileTodoQuickApproveSheet") && todoSource.includes("MobileTodoBatchApproveSheet"),
  "mobile todo must use phone-native quick and batch approval sheets")
assert.ok(mobileApprovalSource.includes("直接通过") && mobileApprovalSource.includes("通过并处理下一条") &&
  mobileApprovalSource.includes("批量通过"),
  "mobile todo must expose direct, continuous, and batch approval wording")
assert.ok(todoSource.includes("canQuickApproveTodo") && todoSource.includes("canBatchApproveTodo") &&
  todoSource.includes("executeTodoApprovalBatch"),
  "mobile approval surfaces must reuse the shared fail-closed approval executor")
assert.ok(todoSource.includes("reloadLoadedTodoPages") && todoSource.includes("targetPageCount"),
  "mobile approval convergence must restore every previously loaded page")
assert.ok(
  /var\(--mobile-safe-bottom,\s*env\(safe-area-inset-bottom(?:,\s*0px)?\)\)/.test(todoSource),
  "mobile todo page must respect the shared phone safe-area token with an env fallback"
)
assert.ok(
  todoSource.includes("normalizeTodoFilterQuery") && todoSource.includes("buildTodoFilterQuery") &&
    todoSource.includes("todoFilterStateEquals") && todoSource.includes("todoRouteQueryMatches") &&
    todoSource.includes("hasActiveTodoFilters") && todoSource.includes("$router.replace"),
  "mobile filters must use the shared canonical URL contract"
)
assert.ok(todoSource.includes("'$route.query'") && todoSource.includes("handleRouteQuery"),
  "browser back and forward must reapply mobile filters")
assert.ok(todoSource.includes("routeOperationSequence") && todoSource.includes("expectedQuery") &&
  todoSource.includes("localRouteAttempts"),
"mobile route synchronization must assign watcher ownership to sequenced canonical operations")
assert.ok(todoSource.includes("hasActiveFilters") && todoSource.includes("clearFilters") && todoSource.includes("清除筛选"),
  "mobile filtered empty states must offer one-step reset")
assert.ok(todoSource.includes('class="summary-stat"') && todoSource.includes(':aria-pressed=') &&
  /grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\)/.test(todoSource) &&
  !/\.summary-card\s*\{[^}]*overflow-x:\s*auto/.test(todoSource),
"all six mobile categories must remain visible in a keyboard-accessible 2x3 grid")
assert.ok(todoSource.includes('aria-label="待办优先级"') && todoSource.includes(':aria-pressed=') &&
  todoSource.includes("selectPriority") && /\.priority-filter-track[\s\S]*?overflow-x:\s*auto/.test(todoSource),
"mobile priority controls must be an accessible horizontally scrollable selection group")
assert.ok(/\.priority-filter-button[\s\S]*?min-height:\s*44px/.test(todoSource),
  "mobile priority controls must retain a 44px touch target")
assert.ok(todoSource.includes("优先级：") && todoSource.includes("key: 'priority'") &&
  /filters\.priority === 'all' \? '' : (?:this\.)?filters\.priority/.test(todoSource),
"mobile active chips, summaries, and list requests must carry the exact priority")
assert.ok(todoSource.includes("当前优先级"),
  "mobile empty-state guidance must explain the selected priority")
assert.ok(todoSource.includes('aria-label="待办关键词"') && todoSource.includes("activeFilterItems") &&
  todoSource.includes("重置全部"),
"mobile filters must expose an accessible search name, active chips, and a persistent reset")
assert.ok(
  todoSource.includes('v-model.trim="keywordDraft"') &&
    todoSource.includes('@submit.prevent="handleQuery"') &&
    todoSource.includes("this.filters.keyword = String(this.keywordDraft"),
  "mobile keyword text must remain a draft until the user submits the search form"
)
assert.ok(todoSource.includes("本人专属待办跨已授权门店") &&
  todoSource.includes("公共待办：当前门店"),
  "mobile summary counts must show direct-owner and current-store public scope beside the numbers")
assert.ok(todoSource.includes("processLabel(row)") && !todoSource.includes('todo-process-label">去处理'),
  "mobile todo actions must describe their next step instead of using a generic label")
assert.ok(todoSource.includes('class="todo-card-action"') && todoSource.includes(".todo-card-action:focus-visible"),
  "each mobile card must expose one semantic full-card button with visible focus")
assert.ok(!/<article\b[^>]*@click="openTodo\(row\)"/.test(todoSource),
  "the mobile article must not remain a second interactive target")
assert.ok(/<article[\s\S]*?<button[\s\S]*?class="todo-card-action"[\s\S]*?<\/button>[\s\S]*?<\/article>/.test(todoSource),
  "each mobile todo article must contain one native full-card action")
assert.strictEqual((todoSource.match(/@click="openTodo\(row\)"/g) || []).length, 1,
  "a mobile todo card must expose exactly one click action")
assert.ok(todoSource.includes("todo-process-label") && !todoSource.includes("@click.stop=\"openTodo(row)\""),
  "the process label must remain non-interactive inside the single card button")
assert.ok(todoSource.includes('@click="retryPage"') && !todoSource.includes('@click="resetAndLoad"'),
  "mobile page retries must pass through the pending-route lifecycle guard")
assert.ok(todoSource.includes(':disabled="pageLoading || !!pendingRouteOperation"'),
  "mobile refresh must be disabled while a canonical route operation owns the next page")
for (const [contract, label] of [
  [/\.back-button, \.refresh-button[\s\S]*?width:\s*44px[\s\S]*?height:\s*44px/, "header controls"],
  [/\.summary-stat[\s\S]*?min-height:\s*64px/, "category summary buttons"],
  [/\.filter-row select[\s\S]*?height:\s*44px/, "source and scope selectors"],
  [/\.keyword-row input[\s\S]*?height:\s*44px/, "keyword input"],
  [/\.keyword-row button[\s\S]*?min-height:\s*44px/, "keyword submit"],
  [/\.todo-process-label[\s\S]*?min-height:\s*44px/, "process label"],
  [/\.mobile-todo-quick-approve[\s\S]*?min-height:\s*44px/, "quick approval"],
  [/\.mobile-todo-batch-select[\s\S]*?min-height:\s*44px/, "batch selection"]
]) assert.ok(contract.test(todoSource), `missing 44px mobile touch contract for ${label}`)

function loadVueComponent(source, overrides = {}) {
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match, "Vue component script must exist")
  const script = match[1]
    .replace(/import[\s\S]*?from\s*['"][^'"]+['"]\s*;?/g, "")
    .replace(/export default/, "module.exports =")
  const context = {
    module: { exports: {} },
    exports: {},
    mapGetters: () => ({}),
    defaultSettings: { todoQuickApproveEnabled: true, todoBatchApproveEnabled: true },
    MobileTodoBatchApproveSheet: {},
    MobileTodoQuickApproveSheet: {},
    approveApprovalTask: () => Promise.resolve(),
    approveTransfer: () => Promise.resolve(),
    getTransferDetail: () => Promise.resolve({ data: {} }),
    ...todoApprovalActions,
    constantRoutes: [],
    buildAvailableRouteSet: () => new Set(),
    navigateTodo: () => Promise.resolve({ ok: true }),
    fetchTodoSummary: () => Promise.resolve({ data: {} }),
    getSelectedDeptContext: () => ({}),
    getSelectedDeptId: () => (context.getSelectedDeptContext() || {}).deptId,
    hasValidInventoryDeptContext: context => {
      const source = context || {}
      const hasDeptId = source.deptId !== undefined && source.deptId !== null && String(source.deptId).trim() !== ""
      const deptType = source.deptType ? String(source.deptType).trim().toUpperCase() : ""
      return hasDeptId && (deptType === "STORE" || deptType === "WAREHOUSE")
    },
    aggregateSummaries: entries => {
      const categories = ["approval", "execution", "returned", "risk", "personal"]
      const counts = { total: 0, approval: 0, execution: 0, returned: 0, risk: 0, personal: 0 }
      ;(entries || []).forEach(entry => {
        const summary = entry && entry.summary ? entry.summary : {}
        categories.forEach(category => {
          const value = Number(summary[category] ?? (summary.counts && summary.counts[category]))
          counts[category] += Number.isFinite(value) && value > 0 ? value : 0
        })
      })
      counts.total = categories.reduce((total, category) => total + counts[category], 0)
      return { counts, typeCounts: {}, recent: [] }
    },
    ...mobileFilterQuery,
    setSelectedDept: () => {},
    clearSelectedDept: () => {},
    beginTodoContextLease: () => ({ ok: true }),
    rollbackTodoContextLease: () => ({ ok: true }),
    formatTodoContextSwitchNotice: () => "临时切换组织",
    TODO_SORT_LABEL: "紧急优先 · 同级按最早创建",
    require(id) { return require(id.startsWith("@/") ? path.resolve(root, "src", id.slice(2)) : id) },
    window: { addEventListener() {}, removeEventListener() {}, innerHeight: 800, scrollY: 0 },
    document: { documentElement: { scrollHeight: 1600 } },
    Promise,
    Object,
    Array,
    Set,
    String,
    Number,
    Date,
    Math,
    ...overrides
  }
  vm.runInNewContext(script, context)
  return context.module.exports
}

function createMobileHarness({
  selectedContext = {},
  query = {},
  replaceFactory,
  pageFactory,
  summaryFactory,
  scopedSummaryFactory,
  nearBottom = false,
  navigationFactory,
  componentOverrides = {},
  permissions = ["inv:transfer:approve"]
} = {}) {
  let currentContext = selectedContext
  const routeReplacements = []
  const pageQueries = []
  const summaryRequests = []
  const scopedSummaryRequests = []
  const mutationRequests = []
  const component = loadVueComponent(todoSource, {
    getSelectedDeptContext: () => currentContext,
    navigateTodo: (row, options) => navigationFactory
      ? navigationFactory(row, options)
      : Promise.resolve({ ok: true }),
    fetchTodoSummary: (source, params) => {
      scopedSummaryRequests.push({ source, params: JSON.parse(JSON.stringify(params || {})) })
      return scopedSummaryFactory
        ? scopedSummaryFactory(source, params, context)
        : Promise.resolve({ data: {} })
    },
    window: { addEventListener() {}, removeEventListener() {}, innerHeight: 800, scrollY: 0 },
    document: { documentElement: { scrollHeight: nearBottom ? 800 : 1600 } },
    ...mobileFilterQuery,
    ...componentOverrides
  })
  const messages = []
  const context = {
    ...component.data(),
    pageSize: 2,
    permissions,
    permissionRoutes: [],
    $route: { path: "/mobile/todo", query: { ...query } },
    $router: {
      replace(location) {
        routeReplacements.push(JSON.parse(JSON.stringify(location)))
        if (replaceFactory) return replaceFactory(location, context)
        context.$route.query = { ...location.query }
        return Promise.resolve()
      }
    },
    $nextTick(callback) {
      return Promise.resolve().then(callback)
    },
    $refs: {},
    $message: {
      success(message) { messages.push({ type: "success", message }) },
      warning(message) { messages.push({ type: "warning", message }) },
      info(message) { messages.push({ type: "info", message }) },
      error(message) { messages.push({ type: "error", message }) }
    },
    $store: {
      state: { todo: { pageLoading: false } },
      dispatch(action, pageQuery) {
        if (action === "todo/refreshSummaries") {
          summaryRequests.push(action)
          return summaryFactory ? summaryFactory(context) : Promise.resolve()
        }
        if (action === "todo/invalidateAfterMutation") {
          mutationRequests.push(action)
          return Promise.resolve()
        }
        assert.strictEqual(action, "todo/refreshPage")
        pageQueries.push(JSON.parse(JSON.stringify(pageQuery)))
        if (pageFactory) return pageFactory(pageQuery, context)
        return Promise.resolve({
          rows: [], total: 0, estimatedTotal: 0,
          staleSources: [], unknownSources: [], failures: []
        })
      }
    }
  }
  Object.assign(context, component.methods)
  Object.defineProperties(context, {
    hasCurrentOrgContext: {
      get() { return component.computed.hasCurrentOrgContext.call(context) }
    },
    selectedProviders: {
      get() { return component.computed.selectedProviders.call(context) }
    },
    hasActiveFilters: {
      get() { return component.computed.hasActiveFilters.call(context) }
    },
    pageLoading: {
      get() { return component.computed.pageLoading.call(context) }
    },
    canLoadMore: {
      get() { return component.computed.canLoadMore.call(context) }
    },
    quickNextAvailable: {
      get() { return component.computed.quickNextAvailable.call(context) }
    },
    batchSelectableRows: {
      get() { return component.computed.batchSelectableRows.call(context) }
    },
    selectedBatchRows: {
      get() { return component.computed.selectedBatchRows.call(context) }
    },
    allBatchRowsSelected: {
      get() { return component.computed.allBatchRowsSelected.call(context) }
    }
  })
  return {
    component,
    context,
    routeReplacements,
    pageQueries,
    summaryRequests,
    scopedSummaryRequests,
    mutationRequests,
    messages,
    setSelectedContext(next) { currentContext = next }
  }
}

function approvalRow(index, overrides = {}) {
  return {
    todoKey: `inventory:INV_TRANSFER_APPROVAL:${index}:approve`,
    provider: "inventory",
    source: "inventory",
    type: "INV_TRANSFER_APPROVAL",
    category: "approval",
    businessId: String(index),
    businessNo: `TF${index}`,
    title: `调拨审批 TF${index}`,
    requiredPermission: "inv:transfer:approve",
    routeParams: {
      businessId: String(index),
      contextDeptId: "88",
      approvalEngine: "LEGACY"
    },
    ...overrides
  }
}

async function flushPromises() {
  await new Promise(resolve => setImmediate(resolve))
}

async function runBehaviorContracts() {
  const completenessComponent = loadVueComponent(mobileHrCompletenessSource, {
    profileFieldLabel: key => ({ bankName: "开户银行" }[key] || key)
  })
  assert.strictEqual(completenessComponent.methods.completion({ profileCompletionPercent: 73 }), 73,
    "mobile completeness must display the formal server percentage")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(
    completenessComponent.methods.missingFields({ missingProfileFields: ["bankName"] })
  )), ["开户银行"])

  const updateCalls = []
  let detailCalls = 0
  const employeeComponent = loadVueComponent(mobileHrEmployeeSource, {
    MobileHrProfileEditor: {},
    getHrEmployee: userId => { detailCalls += 1; return Promise.resolve({ data: { userId, employeeName: "张三" } }) },
    updateHrEmployee: (userId, patch) => { updateCalls.push({ userId, patch }); return Promise.resolve() }
  })
  const employeeContext = {
    ...employeeComponent.data(),
    $route: { query: {} },
    $message: { success() {} },
    $store: { dispatch: () => Promise.resolve() }
  }
  Object.assign(employeeContext, employeeComponent.methods)
  employeeContext.reload = () => Promise.resolve()
  await employeeContext.openEditor({ userId: 7 })
  assert.strictEqual(detailCalls, 1, "mobile editing must load the masked detail before opening")
  assert.strictEqual(employeeContext.editing.employeeName, "张三")
  await employeeContext.saveProfile({ userId: 7, patch: { bankName: "测试银行" } })
  assert.deepStrictEqual(JSON.parse(JSON.stringify(updateCalls)), [
    { userId: "7", patch: { bankName: "测试银行" } }
  ])

  const editorComponent = loadVueComponent(mobileHrProfileEditorSource)
  const emitted = []
  const editorContext = {
    ...editorComponent.data(),
    visible: true,
    detail: {
      userId: 7,
      employeeName: "张三",
      employeeNo: "E007",
      fields: { bankName: "原银行", emergencyContact: "李四" },
      phoneNumberMasked: "138****8000"
    },
    $emit: (name, payload) => emitted.push({ name, payload }),
    $message: { warning() {} }
  }
  Object.assign(editorContext, editorComponent.methods)
  editorContext.reset()
  editorContext.form.bankName = "测试银行"
  editorContext.setSensitiveField("phoneNumber", "13900000000")
  editorContext.submit()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(emitted[0])), {
    name: "save", payload: { userId: 7, patch: { bankName: "测试银行", phoneNumber: "13900000000" } }
  })

  const shellComponent = loadVueComponent(shellSource, {
    require: request => request === "./mobileWorkbenchPolicy" ? mobileWorkbenchPolicy : (request.startsWith("@/") ? require(path.resolve(root, "src", request.slice(2))) : {})
  })
  assert.strictEqual(shellComponent.computed.todoBadgeText.call({
    hasUnknownTodoProviders: false, todoTotal: 120
  }), "99+", "mobile todo badges must cap large Todo Store totals")
  assert.strictEqual(shellComponent.computed.todoBadgeText.call({
    hasUnknownTodoProviders: true, todoTotal: 0
  }), "?", "an unknown provider must never be presented as zero")
  assert.ok(shellComponent.computed.todoHealthMessage.call({
    todoProviderStates: { system: { unknown: true, status: "unknown", stale: false } },
    hasUnknownTodoProviders: true,
    hasPendingTodoProviders: false
  }).includes("未知"), "mobile workbenches must explain unknown provider state")
  assert.strictEqual(shellComponent.methods.countFor.call({
    todoCounts: { approval: 4 }, hasUnknownTodoProviders: false, hasPendingTodoProviders: false
  }, "approval"), 4)
  assert.strictEqual(shellComponent.methods.countFor.call({
    todoCounts: { approval: 0 }, hasUnknownTodoProviders: true, hasPendingTodoProviders: false
  }, "approval"), "?", "unknown category counts must not render as a trusted zero")
  assert.strictEqual(shellComponent.methods.countFor.call({
    todoCounts: { approval: 0 }, hasUnknownTodoProviders: false, hasPendingTodoProviders: true
  }, "approval"), "…", "pending category counts must keep a loading state")
  assert.ok(!shellComponent.computed.todoEmptyTitle.call({
    hasUnknownTodoProviders: false, hasPendingTodoProviders: true, hasStaleTodoProviders: false
  }).includes("暂无"), "pending empty recent lists must render a loading state")
  assert.ok(!shellComponent.computed.todoEmptyTitle.call({
    hasUnknownTodoProviders: true, hasPendingTodoProviders: false, hasStaleTodoProviders: false
  }).includes("暂无"), "unknown empty recent lists must not claim there are no todos")
  assert.ok(shellComponent.computed.todoEmptyTitle.call({
    hasUnknownTodoProviders: false, hasPendingTodoProviders: false,
    hasStaleTodoProviders: false, hasFreshTodoProviders: true
  }).includes("暂无"), "only fresh settled empty summaries may claim there are no todos")

  const inventoryComponent = loadVueComponent(inventorySource, {
    require(request) {
      if (request.includes("workbenchData")) {
        return { workbenchData: { title: "库存", selector: "", overview: {}, quickActions: [], bottomNav: [] } }
      }
      if (request.includes("workbenchMapper")) return { mapWorkbenchResponse: value => value }
      if (request.includes("mobileNavigation")) return { mobileQuickActions: [], mobileBottomNav: [] }
      return {}
    }
  })
  assert.strictEqual(inventoryComponent.methods.todoCount.call({
    todoCounts: { approval: 0 }, hasUnknownTodoProviders: false, hasPendingTodoProviders: true
  }, "approval"), "…")
  assert.strictEqual(inventoryComponent.methods.todoCount.call({
    todoCounts: { approval: 0 }, hasUnknownTodoProviders: true, hasPendingTodoProviders: false
  }, "approval"), "?")
  assert.strictEqual(inventoryComponent.methods.todoCount.call({
    todoCounts: { approval: 3 }, hasUnknownTodoProviders: true, hasPendingTodoProviders: false
  }, "approval"), "3+")
  assert.ok(!inventoryComponent.computed.todoEmptyTitle.call({
    hasUnknownTodoProviders: false, hasPendingTodoProviders: true,
    hasStaleTodoProviders: false, hasFreshTodoProviders: false
  }).includes("暂无"))
  assert.ok(!inventoryComponent.computed.todoEmptyTitle.call({
    hasUnknownTodoProviders: true, hasPendingTodoProviders: false,
    hasStaleTodoProviders: false, hasFreshTodoProviders: false
  }).includes("暂无"))

  const createdCalls = []
  shellComponent.created.call({
    loadWorkbenchSummary: () => { createdCalls.push("summary"); return Promise.resolve() },
    $store: { dispatch: () => { createdCalls.push("todo"); return Promise.resolve() } },
    loadNoticeCount: () => { createdCalls.push("notice"); return Promise.resolve() }
  })
  assert.deepStrictEqual(createdCalls.sort(), ["notice", "summary", "todo"],
    "shell creation must independently load metrics, unified todos, and notices")

  let summaryQuery = null
  const metricShell = loadVueComponent(shellSource, {
    require: request => request === "./mobileWorkbenchPolicy" ? mobileWorkbenchPolicy : (request.startsWith("@/") ? require(path.resolve(root, "src", request.slice(2))) : {}),
    getSelectedDeptContext: () => ({ deptId: "88", deptType: "WAREHOUSE", isWarehouse: true }),
    getMobileWorkbenchSummary: query => {
      summaryQuery = query
      return Promise.resolve({ data: { selectedDeptId: "88", selectedDeptType: "WAREHOUSE", pendingReceiveCount: 7, pendingDeliverCount: 5, lowStockCount: 3 } })
    }
  })
  const metricContext = { normalizedContextType: "WAREHOUSE", contextType: "WAREHOUSE", summaryContextRevision: 0, $store: { getters: { id: "7" } }, workbenchSummary: {} }
  Object.assign(metricContext, metricShell.methods)
  await metricContext.loadWorkbenchSummary()
  const metricProfile = metricShell.computed.profile.call(metricContext)
  const metricValues = Object.fromEntries(metricProfile.metrics.map(metric => [metric.label, metric.value]))
  assert.strictEqual(metricValues["待入库"], "7")
  assert.strictEqual(metricValues["待发货"], "5")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(summaryQuery)), {
    selectedDeptId: "88", selectedDeptType: "WAREHOUSE", warehouseId: "88"
  })

  const component = loadVueComponent(todoSource)
  const data = component.data()
  const selectedProviderStates = {
    inventory: { status: "fresh", summary: { approval: 100 } },
    oa: { status: "fresh", summary: { approval: 4 } },
    system: { status: "unknown", unknown: true, summary: null }
  }
  const selectedCounts = component.computed.selectedCounts.call({
    selectedProviders: ["oa", "system"],
    scopedProviderStates: selectedProviderStates
  })
  assert.strictEqual(selectedCounts.approval, 4,
    "mobile category counts must exclude unselected provider summaries")
  assert.strictEqual(component.methods.countFor.call({
    selectedCounts, hasUnknownProvider: true, hasPendingProvider: false
  }, "approval"), "4+", "known selected counts with an unknown selected provider must be a lower bound")
  assert.strictEqual(component.computed.todoTotalDisplay.call({
    selectedCounts, hasUnknownProvider: true, hasPendingProvider: false, todoTotal: 999
  }), "4+", "mobile total display must use selected counts instead of the global total")

  selectedProviderStates.oa.summary = { approval: 0 }
  const unknownZeroCounts = component.computed.selectedCounts.call({
    selectedProviders: ["oa", "system"], scopedProviderStates: selectedProviderStates
  })
  assert.strictEqual(component.methods.countFor.call({
    selectedCounts: unknownZeroCounts, hasUnknownProvider: true, hasPendingProvider: false
  }, "approval"), "?", "an unknown selected provider must not turn a known zero into an exact zero")
  assert.strictEqual(component.computed.todoTotalDisplay.call({
    selectedCounts: unknownZeroCounts, hasUnknownProvider: true, hasPendingProvider: false
  }), "?")

  selectedProviderStates.system = { status: "pending", unknown: false, summary: null }
  const pendingZeroCounts = component.computed.selectedCounts.call({
    selectedProviders: ["oa", "system"], scopedProviderStates: selectedProviderStates
  })
  assert.strictEqual(component.methods.countFor.call({
    selectedCounts: pendingZeroCounts, hasUnknownProvider: false, hasPendingProvider: true
  }, "approval"), "…", "a pending selected provider with no known count must show loading")
  assert.strictEqual(component.computed.todoTotalDisplay.call({
    selectedCounts: pendingZeroCounts, hasUnknownProvider: false, hasPendingProvider: true
  }), "…")

  selectedProviderStates.oa.summary = { approval: 3, risk: 2 }
  const pendingPositiveCounts = component.computed.selectedCounts.call({
    selectedProviders: ["oa", "system"], scopedProviderStates: selectedProviderStates
  })
  assert.strictEqual(component.methods.countFor.call({
    selectedCounts: pendingPositiveCounts, hasUnknownProvider: false, hasPendingProvider: true
  }, "approval"), "3+", "a known positive count must remain a lower bound while a provider is pending")
  assert.strictEqual(component.computed.todoTotalDisplay.call({
    selectedCounts: pendingPositiveCounts, hasUnknownProvider: false, hasPendingProvider: true
  }), "5+")
  assert.strictEqual(component.methods.countFor.call({
    selectedCounts: pendingPositiveCounts, hasUnknownProvider: false, hasPendingProvider: false
  }, "approval"), 3, "fresh selected provider counts must remain exact")
  assert.strictEqual(component.computed.todoTotalDisplay.call({
    selectedCounts: pendingPositiveCounts, hasUnknownProvider: false, hasPendingProvider: false
  }), 5, "fresh selected provider totals must remain exact")
  const calls = []
  const pages = [
    { rows: [{ todoKey: "A" }, { todoKey: "B" }], total: null, estimatedTotal: 2, staleSources: ["oa"], unknownSources: [] },
    { rows: [{ todoKey: "B" }, { todoKey: "C" }], total: null, estimatedTotal: 4, staleSources: [], unknownSources: ["system"] }
  ]
  const context = {
    ...data,
    pageSize: 2,
    selectedProviders: ["approval", "inventory", "oa", "system"],
    scopedProviderStates: data.scopedProviderStates,
    pageLoading: false,
    hasCurrentOrgContext: false,
    $route: {
      path: "/mobile/todo",
      query: { category: "all", source: "all", scope: "actionable", priority: "all" }
    },
    $router: {
      replace(location) {
        context.$route.query = { ...location.query }
        return Promise.resolve()
      }
    },
    $nextTick: callback => Promise.resolve().then(callback),
    $refs: {},
    $store: {
      dispatch(action, query) {
        calls.push({ action, query })
        return Promise.resolve(pages.shift())
      }
    }
  }
  Object.assign(context, component.methods)
  Object.defineProperty(context, "canLoadMore", {
    get() { return component.computed.canLoadMore.call(context) }
  })

  const noContextComponent = loadVueComponent(todoSource, { getSelectedDeptContext: () => ({}) })
  assert.strictEqual(noContextComponent.data().filters.scopeMode, "actionable",
    "mobile todo must use actionable scope when no valid store/warehouse context exists")
  const storeContextComponent = loadVueComponent(todoSource, {
    getSelectedDeptContext: () => ({ deptId: "9", deptType: "STORE", isStore: true })
  })
  assert.strictEqual(storeContextComponent.data().filters.scopeMode, "actionable")

  const unsafeScope = createMobileHarness({ query: { scope: "current_org" } })
  await unsafeScope.component.created.call(unsafeScope.context)
  assert.strictEqual(unsafeScope.context.filters.scopeMode, "actionable",
    "a legacy current-org route query must normalize to actionable")
  assert.deepStrictEqual(unsafeScope.routeReplacements[0].query, {
    category: "all", source: "all", scope: "actionable", priority: "all"
  }, "legacy or partial mobile routes must be replaced by the no-pagination canonical URL")
  assert.strictEqual(unsafeScope.pageQueries.length, 1,
    "mobile initialization must request its canonical page exactly once")
  assert.strictEqual(unsafeScope.pageQueries[0].scopeMode, "actionable",
    "the actual first page dispatch must use the actionable scope")
  assert.strictEqual(unsafeScope.pageQueries[0].pageNum, 1)
  assert.deepStrictEqual(unsafeScope.scopedSummaryRequests.map(request => request.source).sort(),
    ["approval", "inventory", "oa", "system"],
    "the mobile summary grid must request every selected provider")
  assert.ok(unsafeScope.scopedSummaryRequests.every(request =>
    request.params.scopeMode === "actionable" && request.params.contextDeptId === "" &&
      request.params.keyword === "" && request.params.priority === ""
  ), "page summary requests must use the same normalized no-context scope as the list")
  await unsafeScope.context.handleRouteQuery(unsafeScope.context.$route.query)
  assert.strictEqual(unsafeScope.pageQueries.length, 1,
    "replaying the settled mobile route must not reset or dispatch twice")

  unsafeScope.context.$route.query = {
    category: "risk", source: "oa", scope: "all_authorized", keyword: " 缺货 ", priority: "urgent", pageNum: "9"
  }
  await unsafeScope.context.handleRouteQuery(unsafeScope.context.$route.query)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(unsafeScope.context.filters)), {
    category: "risk", source: "oa", scopeMode: "actionable", keyword: "缺货", priority: "urgent"
  }, "browser navigation must restore normalized mobile filter state without pagination")
  assert.deepStrictEqual(unsafeScope.routeReplacements.at(-1).query, {
    category: "risk", source: "oa", scope: "actionable", keyword: "缺货", priority: "urgent"
  }, "mobile URLs must canonicalize away page parameters")
  assert.strictEqual(unsafeScope.pageQueries.length, 2,
    "one browser route change must reset and request page one once")
  assert.strictEqual(unsafeScope.pageQueries.at(-1).pageNum, 1)
  assert.strictEqual(unsafeScope.pageQueries.at(-1).priority, "urgent")
  assert.deepStrictEqual(unsafeScope.scopedSummaryRequests.at(-1), {
    source: "oa", params: { contextDeptId: "", scopeMode: "actionable", keyword: "缺货", priority: "urgent" }
  }, "source changes must refresh the summary with the same source and scope as the list")

  unsafeScope.context.filters.category = "approval"
  unsafeScope.context.filters.keyword = "采购"
  await unsafeScope.context.handleFilterChange()
  assert.deepStrictEqual(unsafeScope.routeReplacements.at(-1).query, {
    category: "approval", source: "oa", scope: "actionable", keyword: "采购", priority: "urgent"
  })
  assert.strictEqual("pageNum" in unsafeScope.routeReplacements.at(-1).query, false,
    "mobile filter operations must never serialize local pagination")
  assert.strictEqual(unsafeScope.pageQueries.length, 3)
  assert.deepStrictEqual(unsafeScope.scopedSummaryRequests.at(-1), {
    source: "oa", params: { contextDeptId: "", scopeMode: "actionable", keyword: "采购", priority: "urgent" }
  }, "keyword filters must refresh category counts with the same normalized keyword as the list")
  await unsafeScope.context.handleRouteQuery(unsafeScope.context.$route.query)
  assert.strictEqual(unsafeScope.pageQueries.length, 3,
    "the watcher replay after a local replacement must not duplicate its page-one request")

  const keywordSummaryGates = {}
  let deferKeywordSummaries = false
  const keywordSummary = createMobileHarness({
    query: { source: "oa", scope: "actionable", priority: "all" },
    scopedSummaryFactory(source, params) {
      if (!deferKeywordSummaries) return Promise.resolve({ data: { approval: 0 } })
      const gate = deferred()
      keywordSummaryGates[params.keyword] = keywordSummaryGates[params.keyword] || {}
      keywordSummaryGates[params.keyword][source] = gate
      return gate.promise
    }
  })
  await keywordSummary.component.created.call(keywordSummary.context)
  deferKeywordSummaries = true
  keywordSummary.context.filters.keyword = "甲"
  const keywordARequest = keywordSummary.context.refreshScopedSummaries({ force: true })
  keywordSummary.context.filters.keyword = "乙"
  const keywordBRequest = keywordSummary.context.refreshScopedSummaries({ force: true })
  await flushPromises()
  Object.values(keywordSummaryGates["乙"]).forEach(gate => gate.resolve({ data: { approval: 22 } }))
  await keywordBRequest
  assert.strictEqual(keywordSummary.context.scopedProviderStates.oa.summary.approval, 22,
    "the newest keyword should own the visible category summary")
  Object.values(keywordSummaryGates["甲"]).forEach(gate => gate.resolve({ data: { approval: 11 } }))
  const staleKeywordResult = await keywordARequest
  assert.deepStrictEqual(JSON.parse(JSON.stringify(staleKeywordResult)), { discarded: true })
  assert.strictEqual(keywordSummary.context.scopedProviderStates.oa.summary.approval, 22,
    "a late response for an older keyword must not overwrite newer summary counts")
  keywordSummary.context.filters.keyword = ""
  const clearedKeywordRequest = keywordSummary.context.refreshScopedSummaries({ force: true })
  await flushPromises()
  Object.values(keywordSummaryGates[""]).forEach(gate => gate.resolve({ data: { approval: 0 } }))
  await clearedKeywordRequest
  assert.strictEqual(keywordSummary.scopedSummaryRequests.at(-1).params.keyword, "",
    "clearing search should refresh category counts with an empty keyword")

  const replacementCountBeforeLoadMore = unsafeScope.routeReplacements.length
  unsafeScope.context.total = null
  unsafeScope.context.lastPageSize = 2
  await unsafeScope.context.loadMore()
  assert.strictEqual(unsafeScope.routeReplacements.length, replacementCountBeforeLoadMore,
    "load-more must remain local and never rewrite the mobile URL")
  assert.strictEqual(unsafeScope.pageQueries.at(-1).pageNum, 2)

  await unsafeScope.context.clearFilters()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(unsafeScope.context.filters)), {
    category: "all", source: "all", scopeMode: "actionable", keyword: "", priority: "all"
  }, "clear filters must restore context-aware mobile defaults")
  assert.strictEqual(unsafeScope.context.pageNum, 1)
  assert.strictEqual(unsafeScope.context.hasActiveFilters, false)

  unsafeScope.context.filters.scopeMode = "current_org"
  await unsafeScope.context.handleFilterChange()
  assert.strictEqual(unsafeScope.pageQueries.at(-1).scopeMode, "actionable",
    "filter changes must normalize scope again before every load")

  const validContextClear = createMobileHarness({
    selectedContext: { deptId: "9", deptType: "STORE" },
    query: { category: "risk", source: "inventory", scope: "all_authorized", keyword: "盘点", priority: "important" }
  })
  await validContextClear.component.created.call(validContextClear.context)
  await validContextClear.context.clearFilters()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(validContextClear.context.filters)), {
    category: "all", source: "all", scopeMode: "actionable", keyword: "", priority: "all"
  }, "clear filters must restore actionable defaults for a valid inventory context")
  assert.ok(validContextClear.scopedSummaryRequests.slice(-4).every(request =>
    request.params.scopeMode === "actionable" && request.params.contextDeptId === "9" &&
      request.params.keyword === "" && request.params.priority === ""
  ), "actionable summary counts must preserve the selected organization id")

  let liveContext = { deptId: "10", deptType: "STORE", isStore: true }
  const fakeWindow = { addEventListener: () => {}, removeEventListener: () => {}, innerHeight: 800, scrollY: 0 }
  const orgComponent = loadVueComponent(todoSource, {
    getSelectedDeptContext: () => liveContext,
    window: fakeWindow
  })
  const page3Gate = deferred()
  const unmountGate = deferred()
  let mode = "org-switch"
  const orgContext = {
    ...orgComponent.data(),
    pageNum: 2,
    pageSize: 2,
    total: null,
    lastPageSize: 2,
    rows: [{ todoKey: "OLD-1" }, { todoKey: "OLD-2" }],
    selectedProviders: ["inventory"],
    pageLoading: false,
    $route: {
      path: "/mobile/todo",
      query: { category: "all", source: "all", scope: "actionable", priority: "all" }
    },
    $router: {
      replace(location) {
        orgContext.$route.query = { ...location.query }
        return Promise.resolve()
      }
    },
    $nextTick: callback => Promise.resolve().then(callback),
    $refs: {},
    $store: {
      dispatch(action, query) {
        if (mode === "org-switch" && query.pageNum === 3) return page3Gate.promise
        if (mode === "org-switch" && query.pageNum === 1) {
          return Promise.resolve({ rows: [{ todoKey: "NEW-1" }], total: 1, estimatedTotal: 1, staleSources: [], unknownSources: [], failures: [] })
        }
        if (mode === "discard") return Promise.resolve({ discarded: true, rows: [] })
        if (mode === "fail") return Promise.reject(new Error("offline"))
        return unmountGate.promise
      }
    }
  }
  Object.assign(orgContext, orgComponent.methods)
  Object.defineProperty(orgContext, "canLoadMore", {
    get() { return orgComponent.computed.canLoadMore.call(orgContext) }
  })
  Object.defineProperty(orgContext, "hasCurrentOrgContext", {
    get() { return orgComponent.computed.hasCurrentOrgContext.call(orgContext) }
  })
  const oldPage3 = orgContext.loadMore()
  liveContext = { deptId: "20", deptType: "WAREHOUSE", isWarehouse: true }
  await orgContext.handleDeptChanged()
  assert.strictEqual(orgContext.pageNum, 1, "organization changes must atomically reset pagination")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(orgContext.rows.map(row => row.todoKey))), ["NEW-1"])
  page3Gate.resolve({ rows: [{ todoKey: "OLD-3" }], total: 3, estimatedTotal: 3, staleSources: [], unknownSources: [], failures: [] })
  await oldPage3
  assert.deepStrictEqual(JSON.parse(JSON.stringify(orgContext.rows.map(row => row.todoKey))), ["NEW-1"], "old organization page3 must never append to new rows")
  assert.strictEqual(orgContext.pageNum, 1, "an old request must not roll back the new organization page")

  liveContext = { deptId: "", deptType: "COMPANY" }
  await orgContext.handleDeptChanged()
  assert.strictEqual(orgContext.filters.scopeMode, "actionable",
    "losing a valid inventory context must keep the actionable mobile scope")
  assert.strictEqual(orgContext.$route.query.scope, "actionable",
    "organization invalidation must keep the canonical URL aligned with actionable scope")
  assert.strictEqual(orgContext.pageNum, 1)

  mode = "discard"
  orgContext.total = null
  orgContext.lastPageSize = 2
  await orgContext.loadMore()
  assert.strictEqual(orgContext.pageNum, 1, "a discarded load-more request must safely roll back its requested page")
  mode = "fail"
  orgContext.lastPageSize = 2
  await orgContext.loadMore()
  assert.strictEqual(orgContext.pageNum, 1, "a failed load-more request must safely roll back its requested page")

  mode = "unmount"
  const afterUnmount = orgContext.resetAndLoad()
  orgComponent.beforeDestroy.call(orgContext)
  unmountGate.resolve({ rows: [{ todoKey: "TOO-LATE" }], total: 1, estimatedTotal: 1, staleSources: [], unknownSources: [], failures: [] })
  await afterUnmount
  assert.strictEqual(orgContext.rows.length, 0, "responses after unmount must not update mobile todo state")

  const canonicalDefaultQuery = {
    category: "all", source: "all", scope: "actionable", priority: "all"
  }
  const delayedNavigations = []
  const rapidFilters = createMobileHarness({
    query: canonicalDefaultQuery,
    replaceFactory(location, harnessContext) {
      const request = deferred()
      const navigation = {
        location: JSON.parse(JSON.stringify(location)),
        request,
        land() {
          harnessContext.$route.query = { ...location.query }
          const watcher = Promise.resolve(harnessContext.handleRouteQuery(harnessContext.$route.query))
          request.resolve()
          return watcher
        }
      }
      delayedNavigations.push(navigation)
      return request.promise
    }
  })
  await rapidFilters.component.created.call(rapidFilters.context)
  rapidFilters.pageQueries.length = 0
  rapidFilters.context.filters.category = "approval"
  const approvalOperation = rapidFilters.context.handleFilterChange()
  rapidFilters.context.filters.category = "risk"
  const riskOperation = rapidFilters.context.handleFilterChange()
  assert.strictEqual(delayedNavigations.length, 2,
    "rapid mobile filters must retain independently settleable route operations")
  await delayedNavigations[1].land()
  await riskOperation
  assert.deepStrictEqual(rapidFilters.pageQueries.map(query => query.category), ["risk"],
    "rapid approval to risk filtering may reset and load only the final filter")
  const staleApprovalWatcher = delayedNavigations[0].land()
  await flushPromises()
  if (delayedNavigations[2]) await delayedNavigations[2].land()
  await Promise.all([approvalOperation, staleApprovalWatcher])
  assert.deepStrictEqual(rapidFilters.pageQueries.map(query => query.category), ["risk"],
    "a stale local mobile navigation must never dispatch its page")
  assert.strictEqual(rapidFilters.context.filters.category, "risk")
  assert.strictEqual(rapidFilters.context.$route.query.category, "risk",
    "a stale local landing must be repaired to the latest canonical filter")

  const pendingRouteGate = deferred()
  let pendingRouteLocation = null
  const pendingRouteOwner = createMobileHarness({
    query: canonicalDefaultQuery,
    nearBottom: true,
    replaceFactory(location) {
      pendingRouteLocation = JSON.parse(JSON.stringify(location))
      return pendingRouteGate.promise
    },
    pageFactory(query) {
      return Promise.resolve({
        rows: [{ todoKey: query.category === "risk" ? "RISK-1" : "OLD-LOAD" }],
        total: null, estimatedTotal: 1,
        staleSources: [], unknownSources: [], failures: []
      })
    }
  })
  await pendingRouteOwner.component.created.call(pendingRouteOwner.context)
  pendingRouteOwner.pageQueries.length = 0
  pendingRouteOwner.context.rows = [{ todoKey: "OLD-1" }, { todoKey: "OLD-2" }]
  pendingRouteOwner.context.pageNum = 2
  pendingRouteOwner.context.total = null
  pendingRouteOwner.context.lastPageSize = 2
  pendingRouteOwner.context.filters.category = "risk"
  const pendingRiskOperation = pendingRouteOwner.context.handleFilterChange()
  assert.ok(pendingRouteOwner.context.pendingRouteOperation,
    "a deferred canonical replacement must own the next page until it settles")
  assert.strictEqual(pendingRouteOwner.context.canLoadMore, false,
    "load-more availability must close while the route owner is pending")
  const pendingLoadMoreResult = await pendingRouteOwner.context.loadMore()
  const pendingRefreshResult = await pendingRouteOwner.context.manualRefresh()
  const pendingRetryResult = await pendingRouteOwner.context.retryPage()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(pendingLoadMoreResult)), { discarded: true })
  assert.deepStrictEqual(JSON.parse(JSON.stringify(pendingRefreshResult)), { discarded: true })
  assert.deepStrictEqual(JSON.parse(JSON.stringify(pendingRetryResult)), { discarded: true })
  assert.strictEqual(pendingRouteOwner.summaryRequests.length, 1,
    "manual refresh may still refresh provider summaries during route ownership")
  assert.strictEqual(pendingRouteOwner.pageQueries.length, 0,
    "load-more, refresh, and retry must not dispatch the old page while routing")
  assert.strictEqual(pendingRouteOwner.context.pageNum, 2)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(pendingRouteOwner.context.rows)), [
    { todoKey: "OLD-1" }, { todoKey: "OLD-2" }
  ], "pending route guards must not clear or merge the currently rendered rows")
  pendingRouteOwner.context.$route.query = { ...pendingRouteLocation.query }
  const pendingRouteWatcher = pendingRouteOwner.context.handleRouteQuery(pendingRouteOwner.context.$route.query)
  pendingRouteGate.resolve()
  await Promise.all([pendingRiskOperation, pendingRouteWatcher])
  assert.deepStrictEqual(pendingRouteOwner.pageQueries.map(query => ({
    category: query.category, pageNum: query.pageNum
  })), [{ category: "risk", pageNum: 1 }],
  "the landed route owner must issue the sole final risk page-one request")

  const refreshSummaryGate = deferred()
  const refreshRouteGate = deferred()
  let refreshRouteLocation = null
  const routeChangedDuringRefresh = createMobileHarness({
    query: canonicalDefaultQuery,
    summaryFactory: () => refreshSummaryGate.promise,
    replaceFactory(location) {
      refreshRouteLocation = JSON.parse(JSON.stringify(location))
      return refreshRouteGate.promise
    }
  })
  await routeChangedDuringRefresh.component.created.call(routeChangedDuringRefresh.context)
  routeChangedDuringRefresh.pageQueries.length = 0
  const oldRefreshOperation = routeChangedDuringRefresh.context.manualRefresh()
  routeChangedDuringRefresh.context.filters.category = "risk"
  const changedRouteOperation = routeChangedDuringRefresh.context.handleFilterChange()
  routeChangedDuringRefresh.context.$route.query = { ...refreshRouteLocation.query }
  const changedRouteWatcher = routeChangedDuringRefresh.context.handleRouteQuery(
    routeChangedDuringRefresh.context.$route.query
  )
  refreshRouteGate.resolve()
  await Promise.all([changedRouteOperation, changedRouteWatcher])
  assert.deepStrictEqual(routeChangedDuringRefresh.pageQueries.map(query => query.category), ["risk"])
  refreshSummaryGate.resolve()
  const oldRefreshResult = await oldRefreshOperation
  assert.deepStrictEqual(JSON.parse(JSON.stringify(oldRefreshResult)), { discarded: true })
  assert.deepStrictEqual(routeChangedDuringRefresh.pageQueries.map(query => query.category), ["risk"],
    "a summary refresh started before a later filter must not duplicate the route owner's page request")

  const abortedFilter = createMobileHarness({
    query: canonicalDefaultQuery,
    replaceFactory() {
      return Promise.reject(new Error("navigation aborted"))
    }
  })
  await abortedFilter.component.created.call(abortedFilter.context)
  abortedFilter.pageQueries.length = 0
  abortedFilter.context.filters.category = "risk"
  await abortedFilter.context.handleFilterChange()
  assert.strictEqual(abortedFilter.context.filters.category, "all",
    "an aborted mobile replacement must restore filters from the route that remained active")
  assert.deepStrictEqual(abortedFilter.pageQueries.map(query => query.category), [""],
    "an aborted mobile replacement must reload the active route instead of uncommitted filters")

  const rejectedAfterLanding = createMobileHarness({
    query: canonicalDefaultQuery,
    replaceFactory(location, harnessContext) {
      harnessContext.$route.query = { ...location.query }
      return Promise.reject(new Error("duplicated navigation"))
    }
  })
  await rejectedAfterLanding.component.created.call(rejectedAfterLanding.context)
  rejectedAfterLanding.pageQueries.length = 0
  rejectedAfterLanding.context.filters.category = "approval"
  await rejectedAfterLanding.context.handleFilterChange()
  assert.strictEqual(rejectedAfterLanding.context.filters.category, "approval")
  assert.deepStrictEqual(rejectedAfterLanding.pageQueries.map(query => query.category), ["approval"],
    "a rejected mobile replacement may load when its expected route actually landed")

  const delayedCreatedNavigation = deferred()
  const destroyedDuringCreated = createMobileHarness({
    query: {},
    replaceFactory(location, harnessContext) {
      delayedCreatedNavigation.location = location
      delayedCreatedNavigation.context = harnessContext
      return delayedCreatedNavigation.promise
    }
  })
  const createdOperation = destroyedDuringCreated.component.created.call(destroyedDuringCreated.context)
  destroyedDuringCreated.component.beforeDestroy.call(destroyedDuringCreated.context)
  delayedCreatedNavigation.context.$route.query = { ...delayedCreatedNavigation.location.query }
  delayedCreatedNavigation.resolve()
  await createdOperation
  assert.strictEqual(destroyedDuringCreated.pageQueries.length, 0,
    "destroying during mobile canonicalization must prevent its eventual page request")

  const delayedLocalNavigation = deferred()
  const destroyedDuringLocalFilter = createMobileHarness({
    query: canonicalDefaultQuery,
    replaceFactory(location, harnessContext) {
      delayedLocalNavigation.location = location
      delayedLocalNavigation.context = harnessContext
      return delayedLocalNavigation.promise
    }
  })
  await destroyedDuringLocalFilter.component.created.call(destroyedDuringLocalFilter.context)
  destroyedDuringLocalFilter.pageQueries.length = 0
  destroyedDuringLocalFilter.context.filters.category = "risk"
  const localFilterOperation = destroyedDuringLocalFilter.context.handleFilterChange()
  destroyedDuringLocalFilter.component.beforeDestroy.call(destroyedDuringLocalFilter.context)
  delayedLocalNavigation.reject(new Error("navigation rejected after destroy"))
  await localFilterOperation
  assert.strictEqual(destroyedDuringLocalFilter.pageQueries.length, 0,
    "destroying during a local mobile replacement must prevent its eventual page request")

  const latePageSuccess = deferred()
  const destroyedDuringPage = createMobileHarness({
    query: canonicalDefaultQuery,
    pageFactory: () => latePageSuccess.promise
  })
  const pendingPage = destroyedDuringPage.component.created.call(destroyedDuringPage.context)
  await flushPromises()
  destroyedDuringPage.component.beforeDestroy.call(destroyedDuringPage.context)
  latePageSuccess.resolve({
    rows: [{ todoKey: "late" }], total: 1, estimatedTotal: 1,
    staleSources: ["system"], unknownSources: [], failures: []
  })
  await pendingPage
  assert.deepStrictEqual(JSON.parse(JSON.stringify(destroyedDuringPage.context.rows)), [],
    "a mobile page response arriving after destroy must not write rows")
  assert.strictEqual(destroyedDuringPage.context.total, 0,
    "a mobile page response arriving after destroy must not write totals")

  const inactiveRoute = createMobileHarness({
    selectedContext: { deptId: "9", deptName: "测试门店", deptType: "STORE" },
    query: { category: "all", source: "all", scope: "actionable", priority: "all" }
  })
  await inactiveRoute.component.created.call(inactiveRoute.context)
  inactiveRoute.routeReplacements.length = 0
  inactiveRoute.pageQueries.length = 0
  inactiveRoute.context.$route = {
    path: "/mobile/transfer-approval",
    query: { businessId: "59", contextDeptId: "1176" }
  }
  await inactiveRoute.context.handleRouteQuery(inactiveRoute.context.$route.query)
  inactiveRoute.setSelectedContext({ deptId: "1176", deptName: "北京柏悦", deptType: "STORE" })
  await inactiveRoute.context.handleDeptChanged()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(inactiveRoute.context.$route.query)), {
    businessId: "59", contextDeptId: "1176"
  }, "a cached mobile todo page must preserve a business focus query")
  assert.strictEqual(inactiveRoute.routeReplacements.length, 0)
  assert.strictEqual(inactiveRoute.pageQueries.length, 0)

  inactiveRoute.context.$route = {
    path: "/mobile/todo",
    query: { category: "risk", source: "all", scope: "actionable", priority: "normal" }
  }
  inactiveRoute.component.deactivated.call(inactiveRoute.context)
  await inactiveRoute.component.activated.call(inactiveRoute.context)
  assert.strictEqual(inactiveRoute.context.filters.category, "risk")
  assert.deepStrictEqual(inactiveRoute.pageQueries.map(query => query.category), ["risk"])
  const selectedScopeContext = {
    filters: { scopeMode: "actionable" },
    hasCurrentOrgContext: true,
    contextSnapshot: { deptName: "北京柏悦" }
  }
  selectedScopeContext.executionScopeDescription = inactiveRoute.component.computed.executionScopeDescription.call(selectedScopeContext)
  selectedScopeContext.approvalScopeDescription = inactiveRoute.component.computed.approvalScopeDescription.call(selectedScopeContext)
  assert.strictEqual(inactiveRoute.component.computed.scopeDescription.call(selectedScopeContext),
    "本人专属待办跨已授权门店 · 公共待办：当前门店（北京柏悦）")

  let navigationHarness
  let activeDuringNavigation = true
  navigationHarness = createMobileHarness({
    selectedContext: { deptId: "9", deptName: "测试门店", deptType: "STORE" },
    query: { category: "all", source: "all", scope: "actionable", priority: "all" },
    navigationFactory() {
      activeDuringNavigation = navigationHarness.context.todoActive
      navigationHarness.context.$route = {
        path: "/mobile/transfer-approval",
        query: { businessId: "59", contextDeptId: "1176" }
      }
      return Promise.resolve({ ok: true })
    }
  })
  await navigationHarness.component.created.call(navigationHarness.context)
  await navigationHarness.context.openTodo({ todoKey: "inventory:TRANSFER_APPROVAL:59:approve" })
  assert.strictEqual(activeDuringNavigation, false,
    "mobile todo must suspend organization and route listeners before exact navigation begins")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(navigationHarness.context.$route.query)), {
    businessId: "59", contextDeptId: "1176"
  })

  await context.resetAndLoad()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(context.rows.map(row => row.todoKey))), ["A", "B"], "first mobile page must replace rows")
  await context.loadMore()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(context.rows.map(row => row.todoKey))), ["A", "B", "C"],
    "load-more must keep stable store order while deduplicating todoKey")
  assert.deepStrictEqual(calls.map(call => call.query.pageNum), [1, 2], "load-more must advance the cached page query")
  assert.strictEqual(context.total, null)
  assert.strictEqual(component.computed.canLoadMore.call({
    total: null, rows: Array(10).fill({}), pageSize: 10, lastPageSize: 10, pageLoading: false
  }), true, "a full unknown page may load more")
  assert.strictEqual(component.computed.canLoadMore.call({
    total: null, rows: Array(9).fill({}), pageSize: 10, lastPageSize: 9, pageLoading: false
  }), false, "a short unknown page must stop loading")

  context.filters.category = "risk"
  pages.push({ rows: [{ todoKey: "R" }], total: 1, estimatedTotal: 1, staleSources: [], unknownSources: [] })
  await context.handleFilterChange()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(context.rows.map(row => row.todoKey))), ["R"], "filter changes must reset accumulated rows")
  assert.strictEqual(context.pageNum, 1, "filter changes must reset the page")

  const eligibilityHarness = createMobileHarness({
    query: { category: "all", source: "all", scope: "actionable", priority: "all" }
  })
  eligibilityHarness.context.rows = Array.from({ length: 21 }, (_, index) => approvalRow(1000 + index))
  assert.strictEqual(eligibilityHarness.context.isQuickApprovable(eligibilityHarness.context.rows[0]), true,
    "mobile direct approval must accept a fresh permitted transfer approval")
  eligibilityHarness.context.permissions = []
  assert.strictEqual(eligibilityHarness.context.isQuickApprovable(eligibilityHarness.context.rows[0]), false,
    "mobile direct approval must fail closed when permission changes")
  eligibilityHarness.context.permissions = ["inv:transfer:approve"]
  eligibilityHarness.context.staleSources = ["inventory"]
  assert.strictEqual(eligibilityHarness.context.isBatchApprovable(eligibilityHarness.context.rows[0]), false,
    "mobile batch approval must fail closed for a stale provider")
  eligibilityHarness.context.staleSources = []
  eligibilityHarness.context.toggleAllBatchRows(true)
  assert.strictEqual(eligibilityHarness.context.selectedTodoKeys.length, 20,
    "mobile batch selection must cap one operation at twenty items")
  eligibilityHarness.context.toggleBatchRow(eligibilityHarness.context.rows[20], true)
  assert.strictEqual(eligibilityHarness.context.selectedTodoKeys.length, 20)
  assert.ok(eligibilityHarness.messages.some(item => item.type === "warning" && item.message.includes("最多选择20条")),
    "attempting a twenty-first selection must explain the limit")

  const validationRow = approvalRow(1901)
  let validationWrites = 0
  const validationHarness = createMobileHarness({
    query: { category: "all", source: "all", scope: "actionable", priority: "all" },
    componentOverrides: {
      approveTransfer() {
        validationWrites += 1
        return Promise.resolve({ code: 200 })
      }
    }
  })
  Object.assign(validationHarness.context, {
    rows: [validationRow], total: 1, estimatedTotal: 1, pageNum: 1, lastPageSize: 1,
    selectedTodoKeys: [validationRow.todoKey]
  })
  validationHarness.context.openBatchApproval()
  validationHarness.context.permissions = []
  await validationHarness.context.submitBatchApproval({ comment: "" })
  assert.strictEqual(validationWrites, 0,
    "permission revoked after the batch sheet opens must prevent every stale snapshot write")
  assert.strictEqual(validationHarness.mutationRequests.length, 0)
  assert.deepStrictEqual(validationHarness.context.batchResults.map(item => item.errorKind), ["VALIDATION"])
  assert.deepStrictEqual(JSON.parse(JSON.stringify(validationHarness.context.selectedTodoKeys)), [],
    "a non-retryable permission validation failure must not remain selected")

  validationHarness.context.closeBatchApproval()
  validationHarness.context.permissions = ["inv:transfer:approve"]
  validationHarness.context.staleSources = []
  validationHarness.context.selectedTodoKeys = [validationRow.todoKey]
  validationHarness.context.openBatchApproval()
  validationHarness.context.staleSources = ["inventory"]
  await validationHarness.context.submitBatchApproval({ comment: "" })
  assert.strictEqual(validationWrites, 0,
    "a provider marked stale after the batch sheet opens must not submit its snapshot")
  assert.deepStrictEqual(validationHarness.context.batchResults.map(item => item.errorKind), ["VALIDATION"])

  let forbiddenRetryInvoked = false
  validationHarness.context.batchResults = [{
    todoKey: validationRow.todoKey,
    requestId: "MOBILE:BATCH:FORBIDDEN",
    status: "FAILED",
    errorKind: "FORBIDDEN",
    row: validationRow
  }]
  validationHarness.context.runBatchApproval = () => { forbiddenRetryInvoked = true }
  validationHarness.context.retryFailedBatchApproval({ comment: "" })
  assert.strictEqual(forbiddenRetryInvoked, false,
    "a forbidden batch result must require refresh instead of direct retry")
  assert.ok(validationHarness.context.batchError.includes("不可直接重试"))

  const quickRows = [approvalRow(2001), approvalRow(2002)]
  const quickCalls = []
  const quickHarness = createMobileHarness({
    query: { category: "all", source: "all", scope: "actionable", priority: "all" },
    componentOverrides: {
      getTransferDetail(transferId) {
        return Promise.resolve({ data: { transferId: Number(transferId), status: "submitted" } })
      },
      approveTransfer(payload, config) {
        quickCalls.push({ payload, config })
        return Promise.resolve({ code: 200 })
      }
    },
    pageFactory() {
      return Promise.resolve({
        rows: quickRows.slice(), total: 2, estimatedTotal: 2,
        staleSources: [], unknownSources: [], failures: []
      })
    }
  })
  Object.assign(quickHarness.context, {
    rows: quickRows.slice(), total: 2, estimatedTotal: 2, pageNum: 1, lastPageSize: 2
  })
  await quickHarness.context.openQuickApproval(quickRows[0])
  const firstQuickRequestId = quickHarness.context.quickRequestId
  assert.ok(firstQuickRequestId && quickHarness.context.quickPreview,
    "opening mobile direct approval must load a fresh matching preview and create one request id")
  await quickHarness.context.submitQuickApproval({ comment: "", continueNext: true })
  assert.strictEqual(quickCalls.length, 1)
  assert.strictEqual(quickCalls[0].payload.comment, "",
    "mobile direct approval must allow an empty optional opinion")
  assert.strictEqual(quickCalls[0].config.headers["X-Request-Id"], firstQuickRequestId)
  assert.strictEqual(quickCalls[0].config.suppressTodoMutationRefresh, true)
  assert.strictEqual(quickHarness.mutationRequests.length, 1,
    "one mobile direct approval must invalidate the aggregate todo cache exactly once")
  assert.strictEqual(quickHarness.pageQueries.length, 1,
    "one mobile direct approval must converge with one full loaded-page refresh")
  assert.strictEqual(quickHarness.context.quickRow.todoKey, quickRows[1].todoKey)
  assert.strictEqual(quickHarness.context.quickDialogVisible, true,
    "continuous approval must keep the phone sheet open on the next eligible item")
  assert.notStrictEqual(quickHarness.context.quickRequestId, firstQuickRequestId,
    "the next approval item must receive its own request id")

  let blockedQuickWrites = 0
  const blockedQuickRow = approvalRow(2051)
  const blockedQuickHarness = createMobileHarness({
    query: { category: "all", source: "all", scope: "actionable", priority: "all" },
    componentOverrides: {
      getTransferDetail(transferId) {
        return Promise.resolve({ data: { transferId: Number(transferId), status: "submitted" } })
      },
      approveTransfer() {
        blockedQuickWrites += 1
        return Promise.resolve({ code: 200 })
      }
    }
  })
  blockedQuickHarness.context.rows = [blockedQuickRow]
  await blockedQuickHarness.context.openQuickApproval(blockedQuickRow)
  blockedQuickHarness.context.permissions = []
  assert.strictEqual(blockedQuickHarness.context.isCurrentQuickApprovalEligible(blockedQuickRow), false)
  await blockedQuickHarness.context.submitQuickApproval({ comment: "", continueNext: false })
  assert.strictEqual(blockedQuickWrites, 0,
    "permission revoked after the quick sheet opens must disable and block submission")
  assert.ok(blockedQuickHarness.context.quickError.includes("权限"))
  assert.strictEqual(blockedQuickHarness.mutationRequests.length, 0)

  const retryRow = approvalRow(2101)
  const retryRequestIds = []
  let retryAttempts = 0
  const retryHarness = createMobileHarness({
    query: { category: "all", source: "all", scope: "actionable", priority: "all" },
    componentOverrides: {
      getTransferDetail(transferId) {
        return Promise.resolve({ data: { transferId: Number(transferId), status: "submitted" } })
      },
      approveTransfer(payload, config) {
        retryAttempts += 1
        retryRequestIds.push(config.headers["X-Request-Id"])
        return retryAttempts === 1 ? Promise.reject(new Error("network unavailable")) : Promise.resolve({ code: 200 })
      }
    },
    pageFactory() {
      return Promise.resolve({
        rows: retryAttempts === 1 ? [retryRow] : [],
        total: retryAttempts === 1 ? 1 : 0,
        estimatedTotal: retryAttempts === 1 ? 1 : 0,
        staleSources: [], unknownSources: [], failures: []
      })
    }
  })
  Object.assign(retryHarness.context, {
    rows: [retryRow], total: 1, estimatedTotal: 1, pageNum: 1, lastPageSize: 1
  })
  await retryHarness.context.openQuickApproval(retryRow)
  const retryRequestId = retryHarness.context.quickRequestId
  await retryHarness.context.submitQuickApproval({ comment: "", continueNext: false })
  assert.strictEqual(retryHarness.context.quickRetryAvailable, true,
    "an unknown response with the item still pending must offer a safe retry")
  assert.strictEqual(retryHarness.mutationRequests.length, 0,
    "an unknown response must never invalidate as if approval had succeeded")
  await retryHarness.context.retryQuickApproval({ comment: "" })
  assert.deepStrictEqual(retryRequestIds, [retryRequestId, retryRequestId],
    "unknown-result retry must reuse the original request id")
  assert.strictEqual(retryHarness.mutationRequests.length, 1)
  assert.strictEqual(retryHarness.context.quickDialogVisible, false)

  const batchRows = [approvalRow(2201), approvalRow(2202)]
  const batchRequestIds = []
  const batchHarness = createMobileHarness({
    query: { category: "all", source: "all", scope: "actionable", priority: "all" },
    componentOverrides: {
      approveTransfer(payload, config) {
        batchRequestIds.push({ transferId: String(payload.transferId), requestId: config.headers["X-Request-Id"] })
        if (String(payload.transferId) === "2202") {
          const error = new Error("conflict")
          error.response = { status: 409 }
          return Promise.reject(error)
        }
        return Promise.resolve({ code: 200 })
      }
    },
    pageFactory() {
      return Promise.resolve({
        rows: batchRows.slice(), total: 2, estimatedTotal: 2,
        staleSources: [], unknownSources: [], failures: []
      })
    }
  })
  Object.assign(batchHarness.context, {
    rows: batchRows.slice(), total: 2, estimatedTotal: 2, pageNum: 1, lastPageSize: 2,
    selectedTodoKeys: batchRows.map(row => row.todoKey)
  })
  batchHarness.context.openBatchApproval()
  const immutableBatchIds = Object.fromEntries(batchHarness.context.batchItems.map(item => [
    String(item.row.businessId), item.requestId
  ]))
  await batchHarness.context.submitBatchApproval({ comment: "" })
  assert.strictEqual(batchHarness.mutationRequests.length, 1,
    "one mobile batch must invalidate the aggregate todo cache exactly once")
  assert.strictEqual(batchHarness.pageQueries.length, 1,
    "one mobile batch must converge with one loaded-page refresh")
  assert.deepStrictEqual(Object.fromEntries(batchRequestIds.map(item => [item.transferId, item.requestId])), immutableBatchIds,
    "mobile batch transports must receive their immutable per-item request ids")
  assert.deepStrictEqual(batchHarness.context.batchResults.map(item => item.status), ["SUCCESS", "FAILED"])
  assert.deepStrictEqual(JSON.parse(JSON.stringify(batchHarness.context.selectedTodoKeys)), [batchRows[1].todoKey],
    "a partially failed mobile batch must retain only the failed loaded item for retry")

  const refreshRows = Array.from({ length: 4 }, (_, index) => approvalRow(2301 + index))
  const partialRefresh = createMobileHarness({
    query: { category: "all", source: "all", scope: "actionable", priority: "all" },
    pageFactory(query) {
      if (query.pageNum === 1) {
        return Promise.resolve({
          rows: refreshRows.slice(0, 2), total: null, estimatedTotal: 4,
          staleSources: [], unknownSources: [], failures: []
        })
      }
      return Promise.reject(new Error("page two unavailable"))
    }
  })
  Object.assign(partialRefresh.context, {
    rows: refreshRows.slice(), total: null, estimatedTotal: 4, pageNum: 2, lastPageSize: 2
  })
  const partialRefreshResult = await partialRefresh.context.reloadLoadedTodoPages()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(partialRefreshResult)), {
    refreshed: false, authoritative: false
  })
  assert.deepStrictEqual(partialRefresh.context.rows.map(row => row.todoKey), refreshRows.map(row => row.todoKey),
    "a failed later page must restore the complete previously rendered mobile list")
  assert.strictEqual(partialRefresh.context.pageNum, 2)
}

const behaviorTimeout = setTimeout(() => {
  console.error(new Error("unifiedTodoMobile behavior contracts timed out with an unresolved async gate"))
  process.exit(1)
}, 15000)

runBehaviorContracts().then(() => {
  console.log("unifiedTodoMobile tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
}).finally(() => {
  clearTimeout(behaviorTimeout)
})
