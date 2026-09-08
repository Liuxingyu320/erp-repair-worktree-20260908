const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const read = relativePath => fs.readFileSync(path.resolve(__dirname, relativePath), "utf8")

const navbar = read("../src/layout/components/Navbar.vue")
const router = read("../src/router/index.js")
const home = read("../src/views/index.vue")

const headerTodoPath = path.resolve(__dirname, "../src/layout/components/HeaderTodo/index.vue")
const todoPagePath = path.resolve(__dirname, "../src/views/workbench/todo/index.vue")
const quickDialogPath = path.resolve(__dirname, "../src/views/workbench/todo/components/TodoQuickApproveDialog.vue")

assert.ok(fs.existsSync(headerTodoPath), "desktop header todo component must exist")
assert.ok(fs.existsSync(todoPagePath), "unified desktop todo page must exist")

const headerTodo = fs.existsSync(headerTodoPath) ? fs.readFileSync(headerTodoPath, "utf8") : ""
const todoPage = fs.existsSync(todoPagePath) ? fs.readFileSync(todoPagePath, "utf8") : ""
const quickDialog = fs.existsSync(quickDialogPath) ? fs.readFileSync(quickDialogPath, "utf8") : ""

assert.ok(
  navbar.includes("<header-todo") && navbar.includes("<header-notice"),
  "navbar must render HeaderTodo and HeaderNotice as separate controls"
)
assert.ok(
  navbar.includes("import HeaderTodo from './HeaderTodo'") && navbar.includes("HeaderTodo"),
  "navbar must register the independent HeaderTodo component"
)

assert.ok(headerTodo.includes("todoTotal") && !headerTodo.includes("unreadCount"),
  "todo badge must read Todo Store totals, never notice unreadCount")
assert.ok(headerTodo.includes("99+") && /Math\.min|>\s*99/.test(headerTodo),
  "todo badge must cap totals above 99 as 99+")
assert.ok(headerTodo.includes("todoRecent") && /\.slice\(0,\s*5\)/.test(headerTodo),
  "header dropdown must show no more than five recent todos")
assert.ok(headerTodo.includes("todoPartialFailure") && headerTodo.includes("todoProviderStates"),
  "header must expose stale, unknown, or partial provider state")
assert.ok(headerTodo.includes("navigateTodo") && headerTodo.includes("todo/refreshSummaries"),
  "header todo actions must use safe navigation and refresh summaries on failure")
assert.ok(headerTodo.includes("buildAvailableRouteSet") && headerTodo.includes("constantRoutes") &&
  headerTodo.includes("permission_routes") && !headerTodo.includes("sidebarRouters"),
  "header navigation route availability must use registered constant and permission routes, not sidebar visibility")
assert.ok(headerTodo.includes("getSelectedDeptContext") && headerTodo.includes("setSelectedDept"),
  "header navigation must use the shared organization context helpers")
assert.ok(headerTodo.includes("beginTodoContextLease") && headerTodo.includes("rollbackTodoContextLease") &&
  headerTodo.includes("formatTodoContextSwitchNotice"),
"header navigation must preserve and explain temporary cross-organization context")
assert.ok(headerTodo.includes("/workbench/todo") && headerTodo.includes("查看全部") && headerTodo.includes("processLabel(item)"),
  "header dropdown must expose view-all and category-specific processing actions")
assert.ok(headerTodo.includes("summaryScopeHint") && headerTodo.includes("审批含跨组织") &&
  headerTodo.includes("hasValidInventoryDeptContext"),
"header dropdown and accessible name must explain the summary scope")
assert.ok(headerTodo.includes("退回修改") && !headerTodo.includes("已驳回"),
  "header todo must use the unified returned category label")

assert.ok(
  router.includes("path: '/workbench'") && router.includes("path: 'todo'") &&
    router.includes("@/views/workbench/todo") && router.includes("hidden: true"),
  "the unified todo center must be a hidden, registered constant route under Layout"
)
assert.ok(
  !router.includes("path: '/oa/todo/process'") && !router.includes("name: 'OaTodoProcess'"),
  "retired OA purchase approvals must not keep a hidden processing route"
)

assert.ok(home.includes("todoTotal") && home.includes("todoCounts") && home.includes("todoRecent"),
  "home summary and recommendations must use Todo Store getters")
assert.ok(home.includes("部分数据未知") && home.includes("todoProviderStates"),
  "home must explicitly identify providers that never loaded")
assert.ok(
  ["approval", "execution", "returned", "risk", "personal"].every(category =>
    home.includes(`todoMetric('${category}')`)
  ),
  "home todo card must show all five unified category subtotals"
)
assert.ok(home.includes('returned: "退回修改"') && !home.includes('returned: "已驳回"'),
  "home todo recommendation must use the unified returned category label")
assert.ok(home.includes('path: "/workbench/todo"') && home.includes("集中处理审批、执行与风险事项"),
  "the home todo shortcut must open the unified center and describe more than approvals")
assert.ok(!home.includes('value: "我的待办"'), "home must not retain a static todo summary value")

for (const category of ["all", "approval", "execution", "returned", "risk", "personal"]) {
  assert.ok(todoPage.includes(`value: "${category}"`) || todoPage.includes(`name=\"${category}\"`) || todoPage.includes(`name: "${category}"`),
    `todo page must offer the ${category} category filter`)
}
for (const source of ["all", "inventory", "oa", "system"]) {
  assert.ok(todoPage.includes(`value: "${source}"`) || todoPage.includes(`label=\"${source}\"`) || todoPage.includes(`source: "${source}"`),
    `todo page must offer the ${source} source filter`)
}
for (const priority of ["all", "urgent", "important", "normal"]) {
  assert.ok(todoPage.includes(`value: "${priority}"`) || todoPage.includes(`label="${priority}"`),
    `todo page must offer the ${priority} priority filter`)
}
assert.ok(!todoPage.includes("current_org") && !todoPage.includes("all_authorized") &&
  !todoPage.includes('aria-label="组织范围"'),
  "todo page must not expose either legacy organization-scope control")
assert.ok(/import\s*\{[^}]*hasValidInventoryDeptContext[^}]*\}\s*from\s*['"]@\/utils\/shopContext['"]/.test(todoPage) &&
  todoPage.includes("hasCurrentOrgContext"),
  "desktop todo must reuse the shared context predicate for actionable-scope guidance")
assert.ok(todoPage.includes("退回修改") && !todoPage.includes("已驳回"),
  "desktop todo must use the unified returned category label")
assert.ok(todoPage.includes("集中查看审批、执行、退回修改、风险和个人事项。"),
  "desktop todo guidance must explain the returned category consistently")
assert.ok(todoPage.includes("todo/refreshPage") && todoPage.includes("sources") && todoPage.includes("keyword") &&
  todoPage.includes("pageNum") && todoPage.includes("pageSize"),
  "todo page filters and pagination must dispatch the cached Todo Store page action")
assert.ok(todoPage.includes("providerStates") && todoPage.includes("staleSources") && todoPage.includes("unknownSources"),
  "todo page must render provider, stale, and unknown states explicitly")
assert.ok(todoPage.includes("estimatedTotal") && todoPage.includes("total === null"),
  "todo page must label estimated totals when exact totals are unknown")
const openTodoMethodSource = /openTodo\(row\)\s*\{[\s\S]*?\n\s{4}\}/.exec(todoPage)
assert.ok(todoPage.includes("navigateTodo") && openTodoMethodSource &&
  !openTodoMethodSource[0].includes("invalidateAfterMutation"),
"opening details must navigate without completing the todo locally; only confirmed quick and batch writes may invalidate it")
assert.ok(todoPage.includes("beginContextLease: beginTodoContextLease") &&
  todoPage.includes("rollbackContextLease") && todoPage.includes("getReturnRoute"),
"desktop todo navigation must use a rollback-capable temporary organization lease")
assert.ok(
  todoPage.includes("normalizeTodoFilterQuery") && todoPage.includes("buildTodoFilterQuery") &&
    todoPage.includes("todoFilterStateEquals") && todoPage.includes("todoRouteQueryMatches") &&
    todoPage.includes("hasActiveTodoFilters") && todoPage.includes("$router.replace"),
  "desktop filters must use the shared canonical URL contract"
)
assert.ok(todoPage.includes("'$route.query'") && todoPage.includes("handleRouteQuery"),
  "browser back and forward must reapply desktop filters")
assert.ok(todoPage.includes("hasActiveFilters") && todoPage.includes("clearFilters") && todoPage.includes("清除筛选"),
  "filtered empty states must offer one-step reset")
assert.strictEqual((todoPage.match(/v-if="hasActiveFilters"[\s\S]*?@click="clearFilters"/g) || []).length, 3,
  "active results plus exact and unknown empty states must expose reset while filters are active")
assert.ok(todoPage.includes("activeFilterItems") && todoPage.includes("removeActiveFilter") &&
  todoPage.includes("重置全部"),
"desktop results must keep active filters and one-step reset visible")
for (const label of ['aria-label="待办来源"', 'aria-label="待办关键词"']) {
  assert.ok(todoPage.includes(label), `desktop filter control must include ${label}`)
}
assert.ok(todoPage.includes('aria-label="待办优先级"') && todoPage.includes("handlePriorityChange"),
  "desktop priority controls must expose an accessible group and reset pagination through one handler")
assert.ok(todoPage.includes("优先级：") && todoPage.includes("key: 'priority'") &&
  /filters\.priority === 'all' \? '' : (?:this\.)?filters\.priority/.test(todoPage),
"desktop active filters and provider requests must carry the exact priority")
assert.ok(todoPage.includes("当前优先级"),
  "desktop empty-state guidance must explain the selected priority")
assert.ok(
  /class="todo-filter-grid"[\s\S]*?class="todo-filter-group todo-source-group"[\s\S]*?class="todo-filter-group todo-keyword-group"/.test(todoPage) &&
    /class="todo-keyword-control"[\s\S]*?<el-input[\s\S]*?<el-button[^>]*@click="handleQuery"[^>]*>查询<\/el-button>/.test(todoPage),
  "desktop filters should use source and keyword groups with the query button inside the keyword group"
)
assert.ok(
  todoPage.includes('v-model="keywordDraft"') &&
    todoPage.includes("this.filters.keyword = String(this.keywordDraft") &&
    todoPage.includes("keywordDraft: filters.keyword"),
  "desktop keyword text must remain a draft until the user explicitly submits it"
)
assert.ok(
  /\.todo-filter-grid\s*\{[\s\S]*?grid-template-columns:\s*minmax\([^;]+\)\s+minmax\([^;]+\);/.test(todoPage) &&
    /@media \(max-width:\s*820px\)[\s\S]*?\.todo-filter-grid\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0,\s*1fr\);/.test(todoPage),
  "desktop todo filters should use two columns when wide and one on small screens"
)
assert.ok(todoPage.includes("TODO_SORT_LABEL") && todoPage.includes('class="todo-sort-label"') &&
  todoPage.includes("{{ sortLabel }}"),
"desktop todo should expose the fixed priority and creation-time ordering beside the list title")
assert.ok(todoPage.includes("processLabel(row)") && !todoPage.includes('todo-process-label">去处理'),
  "desktop action labels must identify the next step")
assert.ok(todoPage.includes('class="todo-row-action"') && todoPage.includes(".todo-row-action:focus-visible"),
  "each desktop row must expose a semantic details button with visible focus")
assert.ok(!/<article\b[^>]*@click="openTodo\(row\)"/.test(todoPage),
  "the article container must not be a second interactive target")
assert.ok(/<article[\s\S]*?<button[\s\S]*?class="todo-row-action"[\s\S]*?<\/button>[\s\S]*?<\/article>/.test(todoPage),
  "each desktop todo article must retain one native details action")
assert.strictEqual((todoPage.match(/@click="handleTodoAction\(row\)"/g) || []).length, 1,
  "a desktop todo row must expose exactly one routed details action")
assert.ok(!/<el-button[\s\S]*?@click\.stop="openTodo\(row\)"/.test(todoPage),
  "the desktop row must not retain the nested process button")
assert.ok(todoPage.includes('class="todo-batch-selector"') &&
  todoPage.includes('class="todo-quick-approve"') &&
  todoPage.includes('@click="openQuickApproval(row, \'quick\')"'),
"eligible transfer approvals must expose independent selection, details, and quick-approve controls")
const handleTodoActionSource = /handleTodoAction\(row\)\s*\{[\s\S]*?\n\s{4}\}/.exec(todoPage)
assert.ok(handleTodoActionSource && handleTodoActionSource[0].includes("openQuickApproval(row, 'detail')") &&
  handleTodoActionSource[0].includes("return this.openTodo(row)"),
"view approval must open the in-place complete detail for eligible transfers and preserve normal navigation for other todo types")
const submitQuickApprovalMethod = /async submitQuickApproval\([\s\S]*?\n\s{4}isTodoRoutePath\(\)/.exec(todoPage)
const quickMethodSource = submitQuickApprovalMethod ? submitQuickApprovalMethod[0] : ""
const quickInvalidationIndex = quickMethodSource.indexOf("todo/invalidateAfterMutation")
const fullReloadIndex = quickMethodSource.indexOf("await this.loadPage()", quickInvalidationIndex)
const targetedReloadIndex = quickMethodSource.indexOf("loadPage({ networkSources:", quickInvalidationIndex)
assert.ok(quickInvalidationIndex >= 0 && fullReloadIndex > quickInvalidationIndex &&
  (targetedReloadIndex < 0 || fullReloadIndex < targetedReloadIndex),
"a successful quick approval must reload every selected provider after invalidating the aggregate page cache")
assert.ok(!/<button[^>]*class="todo-row-action"[\s\S]*?(?:todo-batch-selector|todo-quick-approve)[\s\S]*?<\/button>/.test(todoPage),
  "selection and quick approval controls must never be nested inside the details button")
assert.ok(todoPage.includes("executeTodoApprovalBatch") && todoPage.includes("concurrency: 3") &&
  todoPage.includes("slice(0, 20)"),
"desktop batch approval must cap a current-page selection at 20 and write concurrency at three")
assert.ok(todoPage.includes('await this.ensureValidKnownPage()') &&
  todoPage.includes('quickRetryAvailable = true') && todoPage.includes(':retry-available="quickRetryAvailable"'),
"quick approval must repair an emptied known page and expose a same-request-id retry for unknown outcomes")
assert.ok(quickDialog.includes('ref="continueAction"') && quickDialog.includes('focusPrimaryAction') &&
  quickDialog.includes('preventScroll: true'),
"continuous approval must move keyboard focus to the next approval action without disturbing scroll")
assert.ok(!quickDialog.includes("查看完整详情") && !todoPage.includes('@view-details=') &&
  !todoPage.includes("viewQuickApprovalDetails"),
"neither approval entry may require a second details navigation")
assert.ok(
  quickDialog.includes("调拨基本信息") && quickDialog.includes("审批进度") &&
    quickDialog.includes("调拨明细") && quickDialog.includes('收货地址') &&
    quickDialog.includes(':data="detailRows"') && quickDialog.includes("参考成本价") &&
    quickDialog.includes("小计"),
  "the view-approval mode must show complete header, approval, delivery, and item-line decision context"
)
assert.ok(quickDialog.includes('todo-approval-detail-modal') &&
  quickDialog.includes("max-height") && quickDialog.includes("overflow-y: auto"),
"the expanded in-place detail must remain usable in a viewport-sized scrollable dialog")
assert.ok(todoPage.includes(':mode="quickDialogMode"') && todoPage.includes("quickDialogMode: 'quick'") &&
  quickDialog.includes("showFullDetails") && quickDialog.includes('class="quick-approve-compact-summary"'),
"view approval and quick approve must share safe approval behavior while rendering distinct full-detail and compact-confirmation modes")
assert.ok(todoPage.includes('@pagination="handlePagination"') && !todoPage.includes('@pagination="loadPage"'),
  "desktop pagination must synchronize its canonical URL before loading")
assert.ok(todoPage.includes("todoDisposed") && todoPage.includes("routeOperationSequence") &&
  todoPage.includes("expectedQuery") && todoPage.includes("pageRequestSequence"),
  "desktop todo must guard route and page work with lifecycle-aware operation snapshots")
assert.ok(todoPage.includes("isTodoRouteActive") && todoPage.includes("suspendTodoActivity") &&
  todoPage.includes("activated()") && todoPage.includes("deactivated()"),
  "desktop todo must stop route synchronization while cached behind a business page")
assert.ok(todoPage.includes("本人专属待办跨已授权门店") &&
  todoPage.includes("本人专属待办正常显示") &&
  todoPage.includes("公共待办：当前门店"),
  "desktop guidance must explain direct-owner and current-store public queues")
for (const field of ["contextDeptName", "waiting", "priority", "source", "category"]) {
  assert.ok(todoPage.includes(field), `todo page must display ${field}`)
}

function loadVueComponent(source, overrides = {}) {
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match, "Vue component script must exist")
  const script = match[1]
    .replace(/^import(?:[\s\S]*?)\s+from\s+['"][^'"]+['"]\s*$/gm, "")
    .replace(/export default/, "module.exports =")
  const context = {
    module: { exports: {} },
    exports: {},
    mapGetters: () => ({}),
    getSelectedDeptContext: () => ({}),
    listShopTree: () => Promise.resolve({ data: [] }),
    findDeptNodeById: () => null,
    setSelectedDept: () => {},
    clearSelectedDept: () => {},
    beginTodoContextLease: () => ({ ok: true }),
    rollbackTodoContextLease: () => ({ ok: true }),
    formatTodoContextSwitchNotice: () => "临时切换组织",
    defaultSettings: {
      buildCommit: "", buildTime: "",
      todoQuickApproveEnabled: false,
      todoBatchApproveEnabled: false
    },
    TodoQuickApproveDialog: {},
    TodoBatchApproveDialog: {},
    canQuickApproveTodo: () => false,
    canBatchApproveTodo: () => false,
    classifyTodoApprovalError: () => ({ errorKind: "UNKNOWN" }),
    createBatchApprovalItems: rows => rows,
    createTodoApprovalRequestId: () => "request-id",
    executeTodoApproval: () => Promise.resolve(),
    executeTodoApprovalBatch: () => Promise.resolve([]),
    loadTodoApprovalPreview: () => Promise.resolve({}),
    beginTodoActionReturn: () => ({ ok: false }),
    clearTodoActionReturn: () => ({ ok: true }),
    consumeTodoActionReturn: () => ({ ok: true, context: null, reason: "missing" }),
    getTransferDetail: () => Promise.resolve({ data: {} }),
    approveTransfer: () => Promise.resolve(),
    approveApprovalTask: () => Promise.resolve(),
    TODO_SORT_LABEL: "紧急优先 · 同级按最早创建",
    hasValidInventoryDeptContext: context => {
      const source = context || {}
      const hasDeptId = source.deptId !== undefined && source.deptId !== null && String(source.deptId).trim() !== ""
      const deptType = source.deptType ? String(source.deptType).trim().toUpperCase() : ""
      return hasDeptId && (deptType === "STORE" || deptType === "WAREHOUSE")
    },
    ...loadTodoFilterQuery(),
    window: { addEventListener() {}, removeEventListener() {}, scrollTo() {}, scrollY: 0, pageYOffset: 0 },
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

const todoFilterQuery = loadTodoFilterQuery()

function createDesktopHarness({
  selectedContext = {},
  query = {},
  rejectReplace = false,
  replaceFactory,
  pageFactory,
  navigationFactory
} = {}) {
  let currentContext = selectedContext
  const routeReplacements = []
  const pageQueries = []
  const addedListeners = []
  const removedListeners = []
  const eventTarget = {
    addEventListener(type, listener) {
      addedListeners.push({ type, listener })
    },
    removeEventListener(type, listener) {
      removedListeners.push({ type, listener })
    }
  }
  const component = loadVueComponent(todoPage, {
    getSelectedDeptContext: () => currentContext,
    navigateTodo: (row, options) => navigationFactory
      ? navigationFactory(row, options)
      : Promise.resolve({ ok: true }),
    ...todoFilterQuery,
    window: eventTarget
  })
  const context = {
    ...component.data(),
    $route: { path: "/workbench/todo", query: { ...query } },
    $router: {
      replace(location) {
        routeReplacements.push(JSON.parse(JSON.stringify(location)))
        if (replaceFactory) return replaceFactory(location, context)
        if (rejectReplace) return Promise.reject(new Error("router replace rejected"))
        context.$route.query = { ...location.query }
        return Promise.resolve()
      }
    },
    $store: {
      state: { todo: { pageLoading: false } },
      dispatch(action, pageQuery) {
        assert.strictEqual(action, "todo/refreshPage")
        pageQueries.push(JSON.parse(JSON.stringify(pageQuery)))
        if (pageFactory) return pageFactory(pageQuery, context)
        return Promise.resolve({
          rows: [], total: 0, estimatedTotal: 0,
          staleSources: [], unknownSources: [], failures: []
        })
      }
    },
    $nextTick(callback) {
      return Promise.resolve().then(callback)
    }
  }
  Object.assign(context, component.methods)
  Object.defineProperties(context, {
    hasCurrentOrgContext: {
      get() { return component.computed.hasCurrentOrgContext.call(context) }
    },
    selectedSources: {
      get() { return component.computed.selectedSources.call(context) }
    },
    selectedProviders: {
      get() { return component.computed.selectedProviders.call(context) }
    },
    batchSelectableRows: {
      get() { return component.computed.batchSelectableRows.call(context) }
    },
    hasActiveFilters: {
      get() { return component.computed.hasActiveFilters.call(context) }
    },
    pageLoading: {
      get() { return component.computed.pageLoading.call(context) }
    },
    canPreviousUnknownPage: {
      get() { return component.computed.canPreviousUnknownPage.call(context) }
    },
    canNextUnknownPage: {
      get() { return component.computed.canNextUnknownPage.call(context) }
    }
  })
  return {
    component,
    context,
    routeReplacements,
    pageQueries,
    addedListeners,
    removedListeners,
    setSelectedContext(next) { currentContext = next }
  }
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((onResolve, onReject) => {
    resolve = onResolve
    reject = onReject
  })
  return { promise, resolve, reject }
}

async function flushPromises() {
  await new Promise(resolve => setImmediate(resolve))
}

async function runQualityContracts() {
  assert.ok(
    todoPage.includes('v-if="total !== null && total > 0"') && todoPage.includes(':total="total"') &&
      !todoPage.includes(':total="displayTotal"'),
    "only known exact totals may be passed to the exact Pagination component"
  )
  assert.ok(todoPage.includes("unknown-pagination") && todoPage.includes("总数未知") && todoPage.includes("第 {{ filters.pageNum }} 页"),
    "unknown totals must use independent previous/next controls and identify the current page")
  assert.ok(todoPage.includes('rows.length === 0 && total === null') && todoPage.includes("无法确认"),
    "an empty unknown page must never be presented as a confirmed no-todo state")

  const todoPageComponent = loadVueComponent(todoPage)
  const reliableReconciliationContext = {
    rows: [], staleSources: [], unknownSources: [],
    batchFailureMessage: todoPageComponent.methods.batchFailureMessage
  }
  const unknownAfterRefresh = todoPageComponent.methods.reconcileBatchResults.call(reliableReconciliationContext, [{
    todoKey: "inventory:INV_TRANSFER_APPROVAL:1:approve",
    status: "FAILED",
    errorKind: "UNKNOWN",
    row: { provider: "inventory" }
  }])[0]
  assert.strictEqual(unknownAfterRefresh.status, "FAILED",
    "an item missing only from the current page must never turn an unknown network outcome into success")
  const conflictAfterRefresh = todoPageComponent.methods.reconcileBatchResults.call(reliableReconciliationContext, [{
    todoKey: "inventory:INV_TRANSFER_APPROVAL:2:approve",
    status: "FAILED",
    errorKind: "CONFLICT",
    row: { provider: "inventory" }
  }])[0]
  assert.strictEqual(conflictAfterRefresh.status, "ALREADY_HANDLED",
    "a server-reported conflict may reconcile only after a reliable provider refresh")
  const staleConflict = todoPageComponent.methods.reconcileBatchResults.call({
    rows: [], staleSources: ["inventory"], unknownSources: [],
    batchFailureMessage: todoPageComponent.methods.batchFailureMessage
  }, [{
    todoKey: "inventory:INV_TRANSFER_APPROVAL:3:approve",
    status: "FAILED",
    errorKind: "CONFLICT",
    row: { provider: "inventory" }
  }])[0]
  assert.strictEqual(staleConflict.status, "FAILED",
    "cached provider data must not be used to claim that a conflicting task is already handled")
  assert.strictEqual(todoPageComponent.watch["$route.query"].deep, true,
    "desktop todo must observe nested query changes used by browser navigation")
  assert.strictEqual(todoPageComponent.computed.canPreviousUnknownPage.call({
    total: null, pageLoading: false, filters: { pageNum: 2 }
  }), true, "unknown pagination may go back after page one")
  assert.strictEqual(todoPageComponent.computed.canNextUnknownPage.call({
    total: null, pageLoading: false, rows: Array(10).fill({}), filters: { pageSize: 10 }
  }), true, "a full unknown page may request the next page")
  assert.strictEqual(todoPageComponent.computed.canNextUnknownPage.call({
    total: null, pageLoading: false, rows: Array(9).fill({}), filters: { pageSize: 10 }
  }), false, "a short unknown page must disable next")

  const noContextComponent = loadVueComponent(todoPage, { getSelectedDeptContext: () => ({}) })
  assert.strictEqual(noContextComponent.data().filters.scopeMode, "actionable",
    "desktop todo must use actionable scope without an inventory context")
  const storeContextComponent = loadVueComponent(todoPage, {
    getSelectedDeptContext: () => ({ deptId: "9", deptType: "STORE" })
  })
  assert.strictEqual(storeContextComponent.data().filters.scopeMode, "actionable",
    "desktop todo must use actionable scope for a valid store context")

  const unsafeScope = createDesktopHarness({ query: { scope: "current_org" } })
  await unsafeScope.component.created.call(unsafeScope.context)
  assert.strictEqual(unsafeScope.context.filters.scopeMode, "actionable",
    "desktop legacy current-org route query must normalize to actionable")
  assert.strictEqual(unsafeScope.routeReplacements.length, 1,
    "desktop initialization must replace an unsafe query with one canonical URL")
  assert.deepStrictEqual(unsafeScope.routeReplacements[0].query, {
    category: "all", source: "all", scope: "actionable", priority: "all", pageNum: "1", pageSize: "10"
  })
  assert.strictEqual(unsafeScope.pageQueries.length, 1,
    "desktop initialization must request its canonical page exactly once")
  assert.strictEqual(unsafeScope.pageQueries[0].scopeMode, "actionable",
    "desktop first page dispatch must use the actionable scope")

  await unsafeScope.context.handleRouteQuery(unsafeScope.context.$route.query)
  assert.strictEqual(unsafeScope.pageQueries.length, 1,
    "replaying the canonical route must not request the page twice")

  unsafeScope.context.$route.query = {
    category: "risk", source: "all", scope: "all_authorized",
    keyword: " 缺货 ", priority: "urgent", pageNum: "2", pageSize: "20"
  }
  await unsafeScope.context.handleRouteQuery(unsafeScope.context.$route.query)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(unsafeScope.context.filters)), {
    category: "risk", source: "all", scopeMode: "actionable",
    keyword: "缺货", priority: "urgent", pageNum: 2, pageSize: 20
  }, "browser route changes must restore normalized desktop filter state")
  assert.strictEqual(unsafeScope.pageQueries.length, 2,
    "one browser route change must request one page")

  unsafeScope.context.filters.category = "approval"
  await unsafeScope.context.handleFilterChange()
  assert.strictEqual(unsafeScope.routeReplacements.at(-1).query.category, "approval")
  assert.strictEqual(unsafeScope.routeReplacements.at(-1).query.pageNum, "1",
    "filter operations must reset and serialize the page number")
  assert.strictEqual(unsafeScope.pageQueries.length, 3,
    "one local filter operation must request one page")

  unsafeScope.context.keywordDraft = "采购"
  await unsafeScope.context.handleQuery()
  assert.strictEqual(unsafeScope.routeReplacements.at(-1).query.keyword, "采购",
    "keyword queries must use the same canonical route path")
  assert.strictEqual(unsafeScope.pageQueries.length, 4)
  await unsafeScope.context.handleRouteQuery(unsafeScope.context.$route.query)
  assert.strictEqual(unsafeScope.pageQueries.length, 4,
    "the watcher replay after a local route replacement must not duplicate its request")

  unsafeScope.context.filters.pageNum = 3
  unsafeScope.context.filters.pageSize = 30
  await unsafeScope.context.handlePagination()
  assert.strictEqual(unsafeScope.routeReplacements.at(-1).query.pageNum, "3")
  assert.strictEqual(unsafeScope.routeReplacements.at(-1).query.pageSize, "30",
    "ordinary pagination must serialize numbers as canonical strings")
  assert.strictEqual(unsafeScope.pageQueries.length, 5)

  unsafeScope.context.total = null
  unsafeScope.context.rows = Array(30).fill({})
  await unsafeScope.context.goUnknownPage(1)
  assert.strictEqual(unsafeScope.routeReplacements.at(-1).query.pageNum, "4",
    "unknown-total pagination must synchronize the canonical page number")
  assert.strictEqual(unsafeScope.pageQueries.length, 6)

  await unsafeScope.context.clearFilters()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(unsafeScope.context.filters)), {
    category: "all", source: "all", scopeMode: "actionable",
    keyword: "", priority: "all", pageNum: 1, pageSize: 10
  }, "clear filters must restore context-aware desktop defaults")
  assert.strictEqual(unsafeScope.context.hasActiveFilters, false)
  assert.strictEqual(unsafeScope.pageQueries.length, 7)

  const validContextClear = createDesktopHarness({
    selectedContext: { deptId: "9", deptType: "STORE" },
    query: { category: "risk", scope: "all_authorized", priority: "urgent", pageNum: "3", pageSize: "20" }
  })
  await validContextClear.component.created.call(validContextClear.context)
  await validContextClear.context.clearFilters()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(validContextClear.context.filters)), {
    category: "all", source: "all", scopeMode: "actionable",
    keyword: "", priority: "all", pageNum: 1, pageSize: 10
  }, "clear filters must restore actionable defaults when inventory context is valid")
  assert.strictEqual(validContextClear.routeReplacements.at(-1).query.scope, "actionable")

  const priorityFilter = createDesktopHarness({
    selectedContext: { deptId: "9", deptType: "STORE" },
    query: {
      category: "all", source: "inventory", scope: "actionable",
      priority: "urgent", pageNum: "3", pageSize: "10"
    }
  })
  await priorityFilter.component.created.call(priorityFilter.context)
  assert.strictEqual(priorityFilter.context.filters.priority, "urgent",
    "desktop deep links must restore the selected priority")
  assert.strictEqual(priorityFilter.pageQueries[0].priority, "urgent")
  assert.ok(priorityFilter.component.computed.activeFilterItems.call(priorityFilter.context)
    .some(item => item.key === "priority" && item.label === "优先级：紧急"))

  priorityFilter.context.filters.pageNum = 4
  priorityFilter.context.filters.priority = "important"
  await priorityFilter.context.handlePriorityChange()
  assert.strictEqual(priorityFilter.routeReplacements.at(-1).query.priority, "important")
  assert.strictEqual(priorityFilter.routeReplacements.at(-1).query.pageNum, "1",
    "changing desktop priority must reset the page")
  assert.strictEqual(priorityFilter.pageQueries.at(-1).priority, "important")

  await priorityFilter.context.removeActiveFilter("priority")
  assert.strictEqual(priorityFilter.context.filters.priority, "all")
  assert.strictEqual(priorityFilter.routeReplacements.at(-1).query.priority, "all")
  assert.strictEqual(priorityFilter.pageQueries.at(-1).priority, "",
    "desktop all-priority selection must clear the server filter")

  const contextChange = createDesktopHarness({
    selectedContext: { deptId: "9", deptType: "STORE" },
    query: {
      category: "risk", source: "inventory", scope: "actionable", priority: "important",
      pageNum: "4", pageSize: "20"
    }
  })
  await contextChange.component.created.call(contextChange.context)
  assert.strictEqual(contextChange.context.filters.scopeMode, "actionable")
  contextChange.setSelectedContext({ deptId: "", deptType: "COMPANY" })
  await contextChange.context.handleDeptChanged()
  assert.strictEqual(contextChange.context.filters.scopeMode, "actionable",
    "losing a valid inventory context must keep the actionable scope")
  assert.strictEqual(contextChange.context.filters.pageNum, 1,
    "organization changes must return desktop todo to page one")
  assert.strictEqual(contextChange.routeReplacements.at(-1).query.scope, "actionable")
  assert.strictEqual(contextChange.pageQueries.at(-1).scopeMode, "actionable",
    "organization changes must keep URL state and dispatch scope aligned")

  contextChange.component.mounted.call(contextChange.context)
  assert.deepStrictEqual(contextChange.addedListeners, [{
    type: "erp:dept-changed", listener: contextChange.context.handleDeptChanged
  }], "desktop todo must subscribe to organization changes after mounting")
  contextChange.component.beforeDestroy.call(contextChange.context)
  assert.deepStrictEqual(contextChange.removedListeners, [{
    type: "erp:dept-changed", listener: contextChange.context.handleDeptChanged
  }], "desktop todo must remove the exact organization listener before destroy")

  const inactiveRoute = createDesktopHarness({
    selectedContext: { deptId: "9", deptName: "测试门店", deptType: "STORE" },
    query: {
      category: "all", source: "all", scope: "actionable", priority: "all", pageNum: "1", pageSize: "10"
    }
  })
  await inactiveRoute.component.created.call(inactiveRoute.context)
  inactiveRoute.routeReplacements.length = 0
  inactiveRoute.pageQueries.length = 0
  inactiveRoute.context.$route = {
    path: "/inventory/transfer",
    query: { todoType: "TRANSFER_APPROVAL", businessId: "59" }
  }
  await inactiveRoute.context.handleRouteQuery(inactiveRoute.context.$route.query)
  inactiveRoute.setSelectedContext({ deptId: "1176", deptName: "北京柏悦", deptType: "STORE" })
  await inactiveRoute.context.handleDeptChanged()
  assert.deepStrictEqual(JSON.parse(JSON.stringify(inactiveRoute.context.$route.query)), {
    todoType: "TRANSFER_APPROVAL", businessId: "59"
  }, "a cached desktop todo page must preserve the business page focus query")
  assert.strictEqual(inactiveRoute.routeReplacements.length, 0,
    "a cached desktop todo page must never replace a business route")
  assert.strictEqual(inactiveRoute.pageQueries.length, 0,
    "a cached desktop todo page must not reload for organization events on another route")

  inactiveRoute.context.$route = {
    path: "/workbench/todo",
    query: { category: "risk", source: "all", scope: "actionable", priority: "normal", pageNum: "1", pageSize: "10" }
  }
  inactiveRoute.component.deactivated.call(inactiveRoute.context)
  await inactiveRoute.component.activated.call(inactiveRoute.context)
  assert.strictEqual(inactiveRoute.context.todoActive, true)
  assert.strictEqual(inactiveRoute.context.filters.category, "risk",
    "reactivating desktop todo must restore filters from its own URL")
  assert.deepStrictEqual(inactiveRoute.pageQueries.map(query => query.category), ["risk"],
    "reactivating desktop todo must refresh exactly the restored page")

  let navigationHarness
  let activeDuringNavigation = true
  navigationHarness = createDesktopHarness({
    selectedContext: { deptId: "9", deptName: "测试门店", deptType: "STORE" },
    query: {
      category: "all", source: "all", scope: "actionable", priority: "all", pageNum: "1", pageSize: "10"
    },
    navigationFactory() {
      activeDuringNavigation = navigationHarness.context.todoActive
      navigationHarness.context.$route = {
        path: "/inventory/transfer",
        query: { todoType: "TRANSFER_APPROVAL", businessId: "59" }
      }
      return Promise.resolve({ ok: true })
    }
  })
  await navigationHarness.component.created.call(navigationHarness.context)
  await navigationHarness.context.openTodo({ todoKey: "inventory:TRANSFER_APPROVAL:59:approve" })
  assert.strictEqual(activeDuringNavigation, false,
    "desktop todo must suspend organization and route listeners before exact navigation begins")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(navigationHarness.context.$route.query)), {
    todoType: "TRANSFER_APPROVAL", businessId: "59"
  })

  const rejectedReplace = createDesktopHarness({
    query: { category: "risk", scope: "all_authorized" },
    rejectReplace: true
  })
  await rejectedReplace.component.created.call(rejectedReplace.context)
  assert.strictEqual(rejectedReplace.routeReplacements.length, 1)
  assert.strictEqual(rejectedReplace.pageQueries.length, 1,
    "a rejected canonical route replacement must not drop the normalized page load")
  assert.strictEqual(rejectedReplace.pageQueries[0].category, "risk")

  const canonicalDefaultQuery = {
    category: "all", source: "all", scope: "actionable", priority: "all", pageNum: "1", pageSize: "10"
  }
  const delayedNavigations = []
  const rapidFilters = createDesktopHarness({
    query: canonicalDefaultQuery,
    replaceFactory(location, context) {
      const request = deferred()
      const navigation = {
        location: JSON.parse(JSON.stringify(location)),
        request,
        land() {
          context.$route.query = { ...location.query }
          const watcher = Promise.resolve(context.handleRouteQuery(context.$route.query))
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
    "rapid filters must retain independently settleable route operations")
  await delayedNavigations[1].land()
  await riskOperation
  assert.deepStrictEqual(rapidFilters.pageQueries.map(query => query.category), ["risk"],
    "the latest landed filter may request its page once")
  const staleApprovalWatcher = delayedNavigations[0].land()
  await flushPromises()
  if (delayedNavigations[2]) await delayedNavigations[2].land()
  await Promise.all([approvalOperation, staleApprovalWatcher])
  assert.deepStrictEqual(rapidFilters.pageQueries.map(query => query.category), ["risk"],
    "a stale local navigation must not dispatch after a newer filter has settled")
  assert.strictEqual(rapidFilters.context.filters.category, "risk")
  assert.strictEqual(rapidFilters.context.$route.query.category, "risk",
    "a stale local landing must not leave the URL behind the latest filter")

  const abortedFilter = createDesktopHarness({
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
    "an aborted replacement must restore filters from the route that actually remained active")
  assert.deepStrictEqual(abortedFilter.pageQueries.map(query => query.category), [""],
    "an aborted replacement must reload the active route, never the uncommitted filter")

  const rejectedAfterLanding = createDesktopHarness({
    query: canonicalDefaultQuery,
    replaceFactory(location, context) {
      context.$route.query = { ...location.query }
      return Promise.reject(new Error("duplicated navigation"))
    }
  })
  await rejectedAfterLanding.component.created.call(rejectedAfterLanding.context)
  rejectedAfterLanding.pageQueries.length = 0
  rejectedAfterLanding.context.filters.category = "approval"
  await rejectedAfterLanding.context.handleFilterChange()
  assert.strictEqual(rejectedAfterLanding.context.filters.category, "approval")
  assert.deepStrictEqual(rejectedAfterLanding.pageQueries.map(query => query.category), ["approval"],
    "a rejected replacement may load when the expected route actually landed")

  const delayedCreatedNavigation = deferred()
  const destroyedDuringCreated = createDesktopHarness({
    query: {},
    replaceFactory(location, context) {
      delayedCreatedNavigation.location = location
      delayedCreatedNavigation.context = context
      return delayedCreatedNavigation.promise
    }
  })
  const createdOperation = destroyedDuringCreated.component.created.call(destroyedDuringCreated.context)
  destroyedDuringCreated.component.beforeDestroy.call(destroyedDuringCreated.context)
  delayedCreatedNavigation.context.$route.query = { ...delayedCreatedNavigation.location.query }
  delayedCreatedNavigation.resolve()
  await createdOperation
  assert.strictEqual(destroyedDuringCreated.pageQueries.length, 0,
    "destroying during the created route replacement must prevent its eventual page request")

  const delayedLocalNavigation = deferred()
  const destroyedDuringLocalFilter = createDesktopHarness({
    query: canonicalDefaultQuery,
    replaceFactory(location, context) {
      delayedLocalNavigation.location = location
      delayedLocalNavigation.context = context
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
    "destroying during a local route replacement must prevent its eventual page request")

  const latePageSuccess = deferred()
  const destroyedDuringPage = createDesktopHarness({
    query: canonicalDefaultQuery,
    pageFactory: () => latePageSuccess.promise
  })
  const pendingPage = destroyedDuringPage.component.created.call(destroyedDuringPage.context)
  await flushPromises()
  destroyedDuringPage.component.beforeDestroy.call(destroyedDuringPage.context)
  latePageSuccess.resolve({
    rows: [{ todoKey: "late", title: "迟到数据" }], total: 1, estimatedTotal: 1,
    staleSources: ["system"], unknownSources: [], failures: []
  })
  await pendingPage
  assert.deepStrictEqual(JSON.parse(JSON.stringify(destroyedDuringPage.context.rows)), [],
    "a page response arriving after destroy must not write rows")
  assert.strictEqual(destroyedDuringPage.context.total, 0,
    "a page response arriving after destroy must not write totals")

  const latePageFailure = deferred()
  const destroyedBeforePageFailure = createDesktopHarness({
    query: canonicalDefaultQuery,
    pageFactory: () => latePageFailure.promise
  })
  const failingPage = destroyedBeforePageFailure.component.created.call(destroyedBeforePageFailure.context)
  await flushPromises()
  destroyedBeforePageFailure.component.beforeDestroy.call(destroyedBeforePageFailure.context)
  latePageFailure.reject(new Error("late page failure"))
  await failingPage
  assert.strictEqual(destroyedBeforePageFailure.context.pageError, "",
    "a page failure arriving after destroy must not write an error")

  const overlappingPageRequests = []
  const sequencedPages = createDesktopHarness({
    query: canonicalDefaultQuery,
    pageFactory() {
      const request = deferred()
      overlappingPageRequests.push(request)
      return request.promise
    }
  })
  const olderPage = sequencedPages.component.created.call(sequencedPages.context)
  await flushPromises()
  const newerPage = sequencedPages.context.loadPage()
  overlappingPageRequests[1].resolve({
    rows: [{ todoKey: "newer" }], total: 1, estimatedTotal: 1,
    staleSources: [], unknownSources: [], failures: []
  })
  await newerPage
  overlappingPageRequests[0].resolve({
    rows: [{ todoKey: "older" }], total: 9, estimatedTotal: 9,
    staleSources: ["inventory"], unknownSources: [], failures: []
  })
  await olderPage
  assert.deepStrictEqual(JSON.parse(JSON.stringify(sequencedPages.context.rows)), [{ todoKey: "newer" }],
    "the component page sequence must discard an older response even without a store discarded flag")
  assert.strictEqual(sequencedPages.context.total, 1)

  const headerComponent = loadVueComponent(headerTodo)
  const pendingStates = { inventory: { summary: null, unknown: false, status: "pending", stale: false } }
  const unknownStates = { inventory: { summary: null, unknown: true, status: "unknown", stale: false } }
  assert.strictEqual(headerComponent.computed.hasUnknownProvider.call({ todoProviderStates: pendingStates }), false,
    "pending header providers must not be labelled unknown")
  assert.strictEqual(headerComponent.computed.isSummaryPending.call({ todoProviderStates: pendingStates }), true,
    "pending header providers must expose loading state")
  assert.strictEqual(headerComponent.computed.healthHint.call({ todoProviderStates: pendingStates }), "",
    "pending header providers must not show a health warning")
  assert.strictEqual(headerComponent.computed.hasHealthIssue.call({
    todoPartialFailure: false, hasUnknownProvider: false
  }), false, "pending header providers must not render the health indicator")
  assert.strictEqual(headerComponent.computed.hasUnknownProvider.call({ todoProviderStates: unknownStates }), true,
    "failed never-loaded header providers must remain unknown")

  let popoverCloses = 0
  let pushedPath = ""
  let summaryRetries = 0
  const headerContext = {
    $refs: { todoPopover: { doClose() { popoverCloses += 1 } } },
    $router: { push(pathname) { pushedPath = pathname; return Promise.resolve() } },
    $store: { dispatch(action) { assert.strictEqual(action, "todo/refreshSummaries"); summaryRetries += 1; return Promise.resolve() } },
    summaryLoading: false
  }
  Object.assign(headerContext, headerComponent.methods)
  headerContext.viewAll()
  await headerContext.retrySummary()
  assert.strictEqual(popoverCloses, 1, "view-all must close the header popover before navigation")
  assert.strictEqual(pushedPath, "/workbench/todo")
  assert.strictEqual(summaryRetries, 1, "unknown header summaries must offer an explicit retry")

  let navbarContext = { deptId: "9", deptName: "测试门店", deptType: "STORE", isStore: true }
  const navbarListeners = []
  const navbarComponent = loadVueComponent(navbar, {
    getSelectedDeptContext: () => navbarContext,
    process: { env: {} },
    Breadcrumb: {},
    TopBar: {},
    Hamburger: {},
    Screenfull: {},
    SizeSelect: {},
    Search: {},
    HeaderTodo: {},
    HeaderNotice: {},
    window: {
      addEventListener(type, listener) { navbarListeners.push({ type, listener }) },
      removeEventListener() {}
    }
  })
  const navbarVm = { ...navbarComponent.data() }
  Object.assign(navbarVm, navbarComponent.methods)
  assert.strictEqual(navbarComponent.computed.currentShopName.call(navbarVm), "门店：测试门店")
  navbarContext = { deptId: "1176", deptName: "北京柏悦", deptType: "STORE", isStore: true }
  navbarVm.handleDeptChanged()
  assert.strictEqual(navbarComponent.computed.currentShopName.call(navbarVm), "门店：北京柏悦",
    "navbar organization text must update immediately after a todo context switch")
  navbarComponent.mounted.call(navbarVm)
  assert.strictEqual(navbarListeners[0].type, "erp:dept-changed")

  const homeComponent = loadVueComponent(home)
  assert.strictEqual(homeComponent.computed.hasUnknownTodoProviders.call({ todoProviderStates: pendingStates }), false,
    "pending home providers must not be labelled unknown")
  assert.strictEqual(homeComponent.computed.hasPendingTodoProviders.call({ todoProviderStates: pendingStates }), true,
    "home must identify pending providers")
  assert.strictEqual(homeComponent.computed.todoTotalDisplay.call({
    hasPendingTodoProviders: true, hasUnknownTodoProviders: false, todoTotal: 0
  }), "加载中", "home must show loading instead of zero while summaries are pending")

  assert.ok(/<button[\s\S]*?slot="reference"[\s\S]*?type="button"/.test(headerTodo),
    "the HeaderTodo popover reference must be a semantic button")
  assert.ok(headerTodo.includes(".todo-trigger:focus") || headerTodo.includes(".todo-trigger:focus-visible"),
    "the HeaderTodo trigger must have a visible keyboard focus style")
}

runQualityContracts().then(() => {
  console.log("unifiedTodoDesktop tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
