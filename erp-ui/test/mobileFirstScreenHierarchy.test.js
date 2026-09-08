const assert = require("assert")
const fs = require("fs")
const path = require("path")

const experiencePath = path.resolve(__dirname, "../src/views/mobile/mobileExperience.js")
const mobileExperience = fs.existsSync(experiencePath) ? require(experiencePath) : {}
const {
  createClearedFeatureQuery,
  filterMobileShortcutActionsForListState,
  hasMobileFeatureQueryOverrides,
  partitionMobileActions,
  prioritizeMobileFeatureActions,
  resolveMobileListStateActionLabel,
  resolveMobileListState,
  resolveMobileWorkbenchSummaryState,
  resolveMobileWorkbenchTodos
} = mobileExperience

assert.strictEqual(
  typeof createClearedFeatureQuery,
  "function",
  "mobile experience should expose pure feature-query reset behavior"
)

assert.strictEqual(
  typeof hasMobileFeatureQueryOverrides,
  "function",
  "mobile experience should compare current feature queries with their intrinsic default scope"
)

assert.strictEqual(
  typeof filterMobileShortcutActionsForListState,
  "function",
  "mobile experience should expose pure empty-state shortcut de-duplication"
)

assert.strictEqual(
  typeof partitionMobileActions,
  "function",
  "mobile experience should expose a pure action partition helper"
)

assert.strictEqual(
  typeof resolveMobileListState,
  "function",
  "mobile experience should expose a pure list-state resolver"
)

assert.strictEqual(
  typeof prioritizeMobileFeatureActions,
  "function",
  "mobile experience should classify explicit low-frequency feature actions before permission filtering"
)

assert.strictEqual(
  typeof resolveMobileListStateActionLabel,
  "function",
  "mobile experience should expose contextual list-state action labels"
)

assert.strictEqual(
  typeof resolveMobileWorkbenchSummaryState,
  "function",
  "mobile experience should validate and classify workbench summaries"
)

assert.strictEqual(
  typeof resolveMobileWorkbenchTodos,
  "function",
  "mobile experience should derive honest workbench todo rows"
)

const actions = [
  { id: "forced-more", placement: "more", path: "/more", permissions: ["demo:list"], payload: { id: 1 } },
  { id: "one", path: "/one" },
  { id: "two", path: "/two" },
  { id: "three", path: "/three" },
  { id: "four", path: "/four" },
  { id: "five", path: "/five", query: { status: "pending" } }
]
const actionsSnapshot = JSON.stringify(actions)
const partitionedActions = partitionMobileActions(actions, 4)

assert.deepStrictEqual(
  partitionedActions.primary.map(action => action.id),
  ["one", "two", "three", "four"],
  "mobile primary actions should preserve source order and stop at four"
)

assert.deepStrictEqual(
  partitionedActions.overflow.map(action => action.id),
  ["forced-more", "five"],
  "forced-more and over-limit actions should preserve their source order in overflow"
)

assert.strictEqual(
  partitionedActions.overflow[0],
  actions[0],
  "partitioning should preserve the original action object and its permissions, route, and payload"
)

assert.strictEqual(
  JSON.stringify(actions),
  actionsSnapshot,
  "partitioning should not mutate the source action list"
)

const featureDefaultQuery = {
  businessType: "delivery",
  transferType: "warehouse",
  statusGroup: "deliverable"
}
const featureDefaultQuerySnapshot = JSON.stringify(featureDefaultQuery)
const clearedFeatureQuery = createClearedFeatureQuery(featureDefaultQuery)
assert.deepStrictEqual(
  clearedFeatureQuery,
  featureDefaultQuery,
  "clearing filters should retain intrinsic businessType, transferType, and statusGroup scope"
)
assert.notStrictEqual(
  clearedFeatureQuery,
  featureDefaultQuery,
  "clearing filters should return a new query object"
)
assert.strictEqual(
  JSON.stringify(featureDefaultQuery),
  featureDefaultQuerySnapshot,
  "clearing filters should not mutate the feature default query"
)

const currentDefaultQuery = {
  statusGroup: "deliverable",
  transferType: "warehouse",
  businessType: "delivery"
}
const currentDefaultQuerySnapshot = JSON.stringify(currentDefaultQuery)
assert.strictEqual(
  hasMobileFeatureQueryOverrides(currentDefaultQuery, featureDefaultQuery),
  false,
  "equivalent default business scope should not count as a user filter"
)
assert.strictEqual(
  hasMobileFeatureQueryOverrides(
    Object.assign({}, currentDefaultQuery, { status: "approved" }),
    featureDefaultQuery
  ),
  true,
  "an added or changed query value should count as a user filter override"
)
assert.strictEqual(
  hasMobileFeatureQueryOverrides(createClearedFeatureQuery(featureDefaultQuery), featureDefaultQuery),
  false,
  "clearing filters back to defaults should not loop into filtered-empty"
)
assert.strictEqual(
  JSON.stringify(currentDefaultQuery),
  currentDefaultQuerySnapshot,
  "query override comparison should not mutate the current query"
)

const prioritizedFeatureActions = prioritizeMobileFeatureActions([
  { label: "待入库", query: { status: "submitted" } },
  { label: "采购退货", path: "/mobile/purchase-return", permissions: ["inv:purchaseReturn:list"] },
  { label: "已办记录", path: "/mobile/oa-done" },
  { label: "退出登录", behavior: "logout" },
  { label: "导出", behavior: "export", payload: { scope: "current" } }
])

assert.deepStrictEqual(
  prioritizedFeatureActions.map(action => action.placement || "primary"),
  ["primary", "more", "more", "more", "more"],
  "return, history, logout, and export actions should remain low-frequency"
)

assert.deepStrictEqual(
  prioritizedFeatureActions[1].permissions,
  ["inv:purchaseReturn:list"],
  "low-frequency classification should preserve permissions and routes"
)

assert.deepStrictEqual(
  prioritizedFeatureActions[4].payload,
  { scope: "current" },
  "low-frequency classification should preserve action payloads"
)

assert.deepStrictEqual(
  resolveMobileListState({ loading: true }),
  {
    type: "loading",
    title: "正在加载业务内容",
    description: "正在获取最新数据，请稍候。",
    actionId: ""
  },
  "loading should remain visible instead of being presented as an empty list"
)

assert.deepStrictEqual(
  resolveMobileListState({
    errorType: "permission",
    errorMessage: "当前账号没有该模块权限"
  }),
  {
    type: "permission-error",
    title: "你没有查看此内容的权限",
    description: "当前账号没有该模块权限",
    actionId: "switch-context"
  },
  "permission failures should remain distinct and let the user switch organization"
)

assert.deepStrictEqual(
  resolveMobileListState({
    errorType: "session",
    errorMessage: "登录状态已过期"
  }),
  {
    type: "session-expired",
    title: "登录状态已过期",
    description: "登录状态已过期",
    actionId: "relogin"
  },
  "session expiry must win over empty or network states"
)

assert.deepStrictEqual(
  resolveMobileListState({
    errorType: "network",
    errorMessage: "接口暂不可用，请稍后重试"
  }),
  {
    type: "network-error",
    title: "暂时无法加载数据",
    description: "接口暂不可用，请稍后重试",
    actionId: "retry"
  },
  "network failures should offer retry"
)

assert.deepStrictEqual(
  resolveMobileListState({ itemCount: 0, hasActiveFilters: true }),
  {
    type: "filtered-empty",
    title: "没有符合当前条件的记录",
    description: "当前搜索或筛选没有匹配数据。",
    actionId: "clear-filters"
  },
  "filtered empty lists should let the user clear search and filters"
)

assert.deepStrictEqual(
  resolveMobileListState({ itemCount: 0, listTitle: "销售待处理", canCreate: true }),
  {
    type: "empty",
    title: "还没有销售待处理",
    description: "当前列表还没有数据。",
    actionId: "create"
  },
  "true empty lists should offer create when the user can create"
)

const purchaseCreateAction = { label: "新建采购", behavior: "create-form" }
const purchaseOtherActions = [
  purchaseCreateAction,
  { label: "待入库", path: "/mobile/purchase?status=submitted" },
  { label: "全部采购", path: "/mobile/purchase" }
]
const truePurchaseEmptyState = resolveMobileListState({
  itemCount: 0,
  listTitle: "采购待处理",
  canCreate: true
})
assert.deepStrictEqual(
  filterMobileShortcutActionsForListState(purchaseOtherActions, truePurchaseEmptyState),
  purchaseOtherActions.slice(1),
  "a true empty list should keep one contextual create CTA while preserving other shortcuts"
)
assert.strictEqual(
  resolveMobileListStateActionLabel(truePurchaseEmptyState, purchaseOtherActions),
  "新建采购",
  "the true-empty create CTA should use the business-specific form label"
)

const filteredPurchaseEmptyState = resolveMobileListState({
  itemCount: 0,
  hasActiveFilters: true,
  canCreate: true
})
assert.deepStrictEqual(
  filterMobileShortcutActionsForListState(purchaseOtherActions, filteredPurchaseEmptyState),
  purchaseOtherActions,
  "filtered empty lists should preserve the create shortcut because clearing filters is the state CTA"
)
assert.strictEqual(
  resolveMobileListStateActionLabel(filteredPurchaseEmptyState, purchaseOtherActions),
  "清除筛选",
  "filtered empty lists should retain their recovery action"
)

assert.deepStrictEqual(
  filterMobileShortcutActionsForListState(purchaseOtherActions, null),
  purchaseOtherActions,
  "populated lists should preserve the create shortcut"
)
assert.deepStrictEqual(
  filterMobileShortcutActionsForListState(
    purchaseOtherActions,
    resolveMobileListState({ errorType: "permission", errorMessage: "无权限" })
  ),
  purchaseOtherActions,
  "permission failures should preserve the existing shortcut model"
)
assert.strictEqual(
  resolveMobileListStateActionLabel(
    resolveMobileListState({ itemCount: 0, listTitle: "采购待处理", canCreate: false }),
    purchaseOtherActions.slice(1)
  ),
  "刷新",
  "users without create capability should retain the existing refresh action"
)

assert.strictEqual(
  resolveMobileListState({ itemCount: 1 }),
  null,
  "a populated list should not render an empty or error state"
)

const zeroWorkbenchSummary = {
  selectedDeptId: 7,
  selectedDeptType: "STORE",
  todoCount: 0,
  lowStockCount: 0,
  pendingReceiveCount: 0,
  pendingDeliverCount: 0,
  pendingApprovalCount: 0,
  purchaseReceiveCount: 0,
  transferReceiveCount: 0,
  deliveryNoticeCount: 0,
  transferDeliverCount: 0
}

assert.deepStrictEqual(
  resolveMobileWorkbenchSummaryState({ contextId: "" }),
  {
    type: "context-required",
    title: "选择店铺或仓库后继续",
    description: "选择组织后才能查看待办和业务数据。",
    actionId: "select-context",
    summary: null
  },
  "missing organization should offer organization selection instead of claiming zero todos"
)

const sessionSummaryState = resolveMobileWorkbenchSummaryState({
  contextId: 7,
  error: { response: { status: 401 }, message: "登录状态已过期" }
})
assert.strictEqual(sessionSummaryState.type, "session-expired", "401 should be treated as session expiry")
assert.strictEqual(sessionSummaryState.actionId, "relogin", "session expiry should offer re-login")

const emptySummaryState = resolveMobileWorkbenchSummaryState({ contextId: 7, summary: {} })
assert.strictEqual(emptySummaryState.type, "network-error", "an empty summary response should be a load failure")
assert.strictEqual(emptySummaryState.actionId, "retry", "an empty summary response should be retryable")

const incompleteSummary = Object.assign({}, zeroWorkbenchSummary)
delete incompleteSummary.pendingApprovalCount
assert.strictEqual(
  resolveMobileWorkbenchSummaryState({ contextId: 7, summary: incompleteSummary }).type,
  "network-error",
  "a summary missing a known count should not become a successful zero state"
)

const permissionSummaryState = resolveMobileWorkbenchSummaryState({
  contextId: 7,
  error: { response: { status: 403 } }
})
assert.strictEqual(permissionSummaryState.type, "permission-error", "403 should remain a permission failure")
assert.strictEqual(permissionSummaryState.actionId, "switch-context", "permission failures should offer switching organization")
assert.ok(
  permissionSummaryState.description.includes("联系管理员") &&
    !/网络|重试/.test(permissionSummaryState.description),
  "permission failures should use permission guidance rather than network guidance"
)

const networkSummaryState = resolveMobileWorkbenchSummaryState({
  contextId: 7,
  error: new Error("offline")
})
assert.strictEqual(networkSummaryState.type, "network-error", "non-permission failures should remain network failures")
assert.strictEqual(networkSummaryState.actionId, "retry", "network failures should offer retry")

const readyZeroSummaryState = resolveMobileWorkbenchSummaryState({
  contextId: 7,
  summary: zeroWorkbenchSummary
})
assert.strictEqual(readyZeroSummaryState.type, "ready", "all explicit known zero counts should be a valid summary")
assert.deepStrictEqual(
  resolveMobileWorkbenchTodos([
    { label: "查库存", count: "0", path: "/mobile/stock" }
  ], readyZeroSummaryState.summary, { path: "/mobile/sales" }),
  [],
  "only a complete all-zero summary should render the true no-todo state"
)

const unmatchedPositiveSummary = Object.assign({}, zeroWorkbenchSummary, {
  todoCount: 0,
  pendingDeliverCount: 4,
  deliveryNoticeCount: 4
})
const fallbackTodos = resolveMobileWorkbenchTodos([
  { label: "销售开单", count: "0", path: "/mobile/sales" },
  { label: "查库存", count: "0", path: "/mobile/stock" }
], unmatchedPositiveSummary, {
  path: "/mobile/sales",
  query: { status: "submitted" }
})
assert.deepStrictEqual(
  fallbackTodos.map(item => ({ label: item.label, count: item.count, path: item.path, query: item.query })),
  [{ label: "待办汇总", count: "4", path: "/mobile/sales", query: { status: "submitted" } }],
  "unmatched positive summary counts should create a real clickable fallback todo"
)

const stockOnlyFallbackTodos = resolveMobileWorkbenchTodos([
  { label: "查库存", count: "0", path: "/mobile/stock" }
], unmatchedPositiveSummary, {
  path: "/mobile/sales",
  query: { status: "submitted" }
})
assert.deepStrictEqual(
  stockOnlyFallbackTodos,
  [],
  "stock-only access should not create an unreachable sales summary todo"
)

const explicitlyDeniedFallbackTodos = resolveMobileWorkbenchTodos([
  { label: "销售开单", count: "0", path: "/mobile/sales" }
], unmatchedPositiveSummary, {
  path: "/mobile/sales",
  allowed: false
})
assert.deepStrictEqual(
  explicitlyDeniedFallbackTodos,
  [],
  "an explicitly denied fallback should never be generated"
)

const sortedTodos = resolveMobileWorkbenchTodos([
  { label: "零待办", count: "0" },
  { label: "正数待办", count: "2" }
], Object.assign({}, zeroWorkbenchSummary, { todoCount: 2, lowStockCount: 2 }))
assert.deepStrictEqual(
  sortedTodos.map(item => item.label),
  ["正数待办", "零待办"],
  "mapped positive todos should remain ahead of zero-count rows"
)

const featureSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const workbenchSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/components/MobileWorkbenchShell.vue"),
  "utf8"
)
const featureTemplate = featureSource.slice(0, featureSource.indexOf("<script>"))
const workbenchTemplate = workbenchSource.slice(0, workbenchSource.indexOf("<script>"))
const clearListFiltersSource = featureSource.slice(
  featureSource.indexOf("clearListFilters()"),
  featureSource.indexOf("handleListStateAction(actionId)")
)
const hasActiveListFiltersSource = featureSource.slice(
  featureSource.indexOf("hasActiveListFilters()"),
  featureSource.indexOf("canCreateListItem()")
)

assert.ok(
  clearListFiltersSource.includes("createClearedFeatureQuery(this.feature.defaultQuery)") &&
    !clearListFiltersSource.includes("this.featureQuery = {}"),
  "clearing user filters should restore the feature default query and preserve its business scope"
)

assert.ok(
  hasActiveListFiltersSource.includes("hasMobileFeatureQueryOverrides(this.featureQuery, this.feature.defaultQuery)") &&
    !hasActiveListFiltersSource.includes("this.activeFilterLabel"),
  "default-query labels should not make true empty lists look filtered"
)

function assertSourceOrder(source, markers, message) {
  const positions = markers.map(marker => source.indexOf(marker))
  assert.ok(positions.every(position => position >= 0), `${message}: every marker should exist`)
  assert.deepStrictEqual(positions, positions.slice().sort((left, right) => left - right), message)
}

assertSourceOrder(
  workbenchTemplate,
  [
    '<header class="title-row',
    'class="glass-card mobile-redirect-banner',
    'class="section-head todo-title',
    'class="section-head action-title',
    'class="glass-card hero-card'
  ],
  "workbench should show notices and todos before shortcuts and metrics"
)

assertSourceOrder(
  featureTemplate,
  [
    '<header class="feature-header',
    'class="glass-panel mobile-redirect-banner',
    'class="section-title scope-title',
    'class="glass-panel list-panel',
    'class="section-title shortcut-title'
  ],
  "feature pages should put identity and data scope before the list and shortcuts after it"
)

assert.ok(
  /<button[\s\S]*?v-for="item in todoItems"[\s\S]*?class="todo-row"[\s\S]*?type="button"/.test(workbenchTemplate),
  "workbench todo rows should be semantic buttons"
)

assert.ok(
  /<button[\s\S]*?v-for="item in displayItems"[\s\S]*?class="list-row[^"]*"[\s\S]*?type="button"/.test(featureTemplate),
  "feature list rows should be semantic buttons"
)

assert.ok(
  workbenchTemplate.includes('aria-live="polite"') &&
    workbenchTemplate.includes('aria-live="assertive"') &&
    workbenchTemplate.includes('@click="loadWorkbenchSummary"'),
  "workbench loading and error states should be announced and failed summaries should be retryable"
)

assert.ok(
  workbenchSource.includes("resolveMobileWorkbenchSummaryState") &&
    workbenchSource.includes("resolveMobileWorkbenchTodos") &&
    workbenchTemplate.includes("summaryState.type === 'context-required'") &&
    workbenchTemplate.includes("summaryState.type === 'permission-error'") &&
    workbenchTemplate.includes("summaryState.type === 'session-expired'") &&
    workbenchTemplate.includes('@click="goSelectShop"'),
  "workbench should wire pure summary states to organization, permission, and session recovery actions"
)

assert.ok(
  featureTemplate.includes("list-state-action") &&
    featureTemplate.includes('@click="handleListStateAction(listState.actionId)"') &&
    featureSource.includes("clearListFilters()") &&
    featureSource.includes("goSelectShop()") &&
    featureSource.includes('actionId === "relogin"'),
  "feature list states should support retry, clear filters, switch organization, session recovery, create, or refresh"
)

assert.ok(
  featureSource.includes("filterMobileShortcutActionsForListState(this.featureActionItems, this.listState)") &&
    featureSource.includes("resolveMobileListStateActionLabel(this.listState, this.featureActionItems)") &&
    featureTemplate.includes('v-if="primaryFeatureActions.length || overflowFeatureActions.length"'),
  "true empty feature lists should use one contextual create CTA without leaving an empty shortcut section"
)

assert.ok(
  featureSource.includes("filterMobileShortcutActionsForListState(this.featureActionItems, this.listState)") &&
    featureSource.includes("resolveMobileListStateActionLabel(this.listState, this.featureActionItems)") &&
    featureTemplate.includes('v-if="primaryFeatureActions.length || overflowFeatureActions.length"'),
  "true empty feature lists should use one contextual create CTA without leaving an empty shortcut section"
)

assert.ok(
  workbenchTemplate.includes('<details v-if="overflowQuickActions.length"') &&
    workbenchTemplate.includes('@click="openAction(action)"'),
  "workbench overflow actions should use an accessible disclosure and the existing action handler"
)

assert.ok(
  featureTemplate.includes('<details v-if="overflowFeatureActions.length"') &&
    featureTemplate.includes('@click="handleAction(action)"'),
  "feature overflow actions should use an accessible disclosure and the existing action handler"
)

const narrowFeatureStyles = featureSource.slice(featureSource.indexOf("@media (max-width: 360px)"))
assert.ok(
  narrowFeatureStyles.includes(".feature-actions") &&
    narrowFeatureStyles.includes("grid-template-columns: repeat(2, minmax(0, 1fr))") &&
    !narrowFeatureStyles.includes("grid-template-columns: 1fr"),
  "320px feature shortcuts should stay in a compact two-column grid after the list"
)
