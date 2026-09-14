const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const {
  MOBILE_CONTEXT_TYPES,
  MOBILE_ROUTES,
  getMobileHomePath,
  getMobileContextRouteRedirect,
  getMobileContextProfile,
  getMobileQuickActions,
  getMobileBottomNav
} = require("../src/views/mobile/mobileNavigation")

const allMobilePermissions = ["*:*:*"]
const mobileExperience = require("../src/views/mobile/mobileExperience")
const mobileWorkbenchPolicy = require("../src/views/mobile/components/mobileWorkbenchPolicy")
const {
  partitionMobileActions,
  resolveMobileWorkbenchSummaryState
} = mobileExperience
const { getWorkbenchActionDetails } = mobileWorkbenchPolicy

const routerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/router/index.js"),
  "utf8"
)
const mobileRouteSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/mobileRouteDefinitions.js"),
  "utf8"
)
const permissionSource = fs.readFileSync(
  path.resolve(__dirname, "../src/permission.js"),
  "utf8"
)
const requestSource = fs.readFileSync(
  path.resolve(__dirname, "../src/utils/request.js"),
  "utf8"
)
const selectShopSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/select-shop/index.vue"),
  "utf8"
)
const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const mobileSheetSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/mobileSheet.scss"),
  "utf8"
)
const featureSearchConfigsSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureSearchConfigs.js"),
  "utf8"
)
const featureListPolicySource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/mobileFeatureListPolicy.js"),
  "utf8"
)
const featureServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureService.js"),
  "utf8"
)
const workbenchShellSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/components/MobileWorkbenchShell.vue"),
  "utf8"
)
const mobileProfileSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/profile/index.vue"),
  "utf8"
)

function loadWorkbenchComponent() {
  const scriptMatch = workbenchShellSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, "mobile workbench script must exist")
  const script = scriptMatch[1]
    .replace(/^import .*$/gm, "")
    .replace(/export default/, "module.exports =")
  const module = { exports: {} }
  const navigation = require("../src/views/mobile/mobileNavigation")
  vm.runInNewContext(script, {
    module,
    exports: module.exports,
    require(specifier) {
      if (specifier === "@/utils/uiOperationScope") return require("../src/utils/uiOperationScope")
      if (specifier === "../mobileNavigation") return navigation
      if (specifier === "../mobileViewport") {
        return {
          startMobileViewportSync: () => true,
          stopMobileViewportSync: () => true
        }
      }
      if (specifier === "../mobileExperience") return mobileExperience
      if (specifier === "./mobileWorkbenchPolicy") return mobileWorkbenchPolicy
      throw new Error(`unexpected mobile workbench dependency: ${specifier}`)
    },
    mapGetters: () => ({}),
    constantRoutes: [],
    buildAvailableRouteSet: () => new Set(),
    navigateTodo: () => Promise.resolve({ ok: true }),
    beginTodoContextLease: () => ({ ok: true }),
    formatTodoContextSwitchNotice: () => "临时切换组织",
    rollbackTodoContextLease: () => ({ ok: true }),
    clearSelectedDept: () => {},
    getSelectedDeptContext: () => ({}),
    setSelectedDept: () => {},
    listNoticeTop: () => Promise.resolve({ data: [] }),
    getMobileWorkbenchSummary: () => Promise.resolve({ data: null }),
    Promise,
    Object,
    Array,
    Set,
    String,
    Number,
    Date,
    Math
  })
  return module.exports
}

function resolveTodoEmptyCopy(component, todoProviderStates, todoTotal = 0) {
  const context = { todoProviderStates, todoTotal }
  for (const name of [
    "hasUnknownTodoProviders",
    "hasPendingTodoProviders",
    "hasStaleTodoProviders",
    "hasFreshTodoProviders"
  ]) {
    Object.defineProperty(context, name, {
      get() {
        return component.computed[name].call(context)
      }
    })
  }
  return {
    title: component.computed.todoEmptyTitle.call(context),
    message: component.computed.todoEmptyMessage.call(context)
  }
}

assert.deepStrictEqual(
  MOBILE_CONTEXT_TYPES,
  { store: "STORE", warehouse: "WAREHOUSE" },
  "mobile context types should distinguish store and warehouse users"
)

assert.strictEqual(
  MOBILE_ROUTES.storeWorkbench,
  "/mobile/store",
  "store users should have a dedicated mobile workbench route"
)

assert.strictEqual(
  MOBILE_ROUTES.warehouseWorkbench,
  "/mobile/warehouse",
  "warehouse users should have a dedicated mobile workbench route"
)

assert.strictEqual(
  getMobileHomePath("STORE"),
  "/mobile/store",
  "store selection should enter the store mobile homepage"
)

assert.strictEqual(
  getMobileHomePath("WAREHOUSE"),
  "/mobile/warehouse",
  "warehouse selection should enter the warehouse mobile homepage"
)

assert.strictEqual(
  getMobileHomePath("COMPANY"),
  "/index",
  "company selection should enter the general system homepage instead of a store or warehouse workbench"
)

assert.strictEqual(
  getMobileHomePath("GROUP"),
  "/index",
  "group selection should enter the general system homepage instead of a store or warehouse workbench"
)

assert.strictEqual(
  getMobileHomePath(""),
  "/mobile/store",
  "unknown mobile context should fall back to the store homepage"
)

assert.strictEqual(
  getMobileHomePath("", ["hr:onboarding:workbench"]),
  "/mobile/hr",
  "HR home selection must not depend on a selected store or warehouse"
)
assert.strictEqual(
  getMobileHomePath("STORE", ["*:*:*"]),
  "/mobile/store",
  "administrator wildcard permissions must not replace the selected organization persona"
)
assert.strictEqual(
  getMobileHomePath("STORE", ["hr:onboarding:list"]),
  "/mobile/store",
  "list-only supervisors should keep their business home while receiving an onboarding shortcut"
)

assert.strictEqual(
  getMobileContextRouteRedirect("/mobile/inventory", "WAREHOUSE"),
  "/mobile/warehouse",
  "legacy mobile inventory entry should redirect warehouse users to the warehouse homepage"
)

assert.strictEqual(
  getMobileContextRouteRedirect("/mobile/store", "WAREHOUSE"),
  "/mobile/warehouse",
  "warehouse users should not remain on the store mobile homepage"
)

assert.strictEqual(
  getMobileContextRouteRedirect("/mobile/warehouse", "STORE"),
  "/mobile/store",
  "store users should not remain on the warehouse mobile homepage"
)

assert.strictEqual(
  getMobileContextRouteRedirect("/mobile/store", "STORE"),
  "",
  "matching store mobile homepage should not redirect"
)

assert.deepStrictEqual(
  getMobileQuickActions("STORE", allMobilePermissions).map(item => item.label),
  ["销售开单", "查库存", "销售退货", "调拨管理", "资产报修", "费用报销", "商品资料", "OE资料", "礼盒资料"],
  "store quick actions should cover selling, transfers, repair reporting, reimbursements, and product lookups"
)

assert.deepStrictEqual(
  getMobileQuickActions("STORE", allMobilePermissions).map(item => item.path),
  [MOBILE_ROUTES.sales, MOBILE_ROUTES.stock, MOBILE_ROUTES.salesReturn, MOBILE_ROUTES.transfer, MOBILE_ROUTES.fixedAssetRepair, MOBILE_ROUTES.reimbursement, MOBILE_ROUTES.product, MOBILE_ROUTES.oe, MOBILE_ROUTES.gift],
  "store quick actions should enter dedicated phone routes for returns, transfers, repairs, reimbursements, and reference data"
)

assert.deepStrictEqual(
  getMobileQuickActions("WAREHOUSE", allMobilePermissions).map(item => item.label),
  ["采购入库", "发货处理", "采购退货", "库存盘点", "调拨处理", "商品资料", "OE资料", "礼盒资料", "供应商", "费用报销"],
  "warehouse quick actions should cover daily execution, reimbursements, and essential reference lookups"
)

assert.deepStrictEqual(
  getMobileQuickActions("STORE", allMobilePermissions).filter(item => item.placement === "more").map(item => item.label),
  ["销售退货", "资产报修", "费用报销", "商品资料", "OE资料", "礼盒资料"],
  "store reference, repair, and reimbursement actions should remain low-frequency"
)

assert.deepStrictEqual(
  getMobileQuickActions("WAREHOUSE", allMobilePermissions).filter(item => item.placement === "more").map(item => item.label),
  ["采购退货", "调拨处理", "商品资料", "OE资料", "礼盒资料", "供应商", "费用报销"],
  "warehouse transfer, reference, and reimbursement actions should remain in the low-frequency disclosure"
)

const storeReturnOnlyActions = partitionMobileActions(
  getMobileQuickActions("STORE", ["inv:salesReturn:list"]),
  4
)
assert.deepStrictEqual(
  {
    primary: storeReturnOnlyActions.primary.map(item => item.label),
    overflow: storeReturnOnlyActions.overflow.map(item => item.label)
  },
  { primary: [], overflow: ["销售退货"] },
  "store sales return should not rise into primary when higher-frequency permissions are hidden"
)

const warehouseReturnOnlyActions = partitionMobileActions(
  getMobileQuickActions("WAREHOUSE", ["inv:purchaseReturn:list"]),
  4
)
assert.deepStrictEqual(
  {
    primary: warehouseReturnOnlyActions.primary.map(item => item.label),
    overflow: warehouseReturnOnlyActions.overflow.map(item => item.label)
  },
  { primary: [], overflow: ["采购退货"] },
  "warehouse purchase return should not rise into primary when higher-frequency permissions are hidden"
)

assert.deepStrictEqual(
  getMobileQuickActions("WAREHOUSE", allMobilePermissions).map(item => item.path),
  [MOBILE_ROUTES.purchase, MOBILE_ROUTES.outbound, MOBILE_ROUTES.purchaseReturn, MOBILE_ROUTES.stockCheck, MOBILE_ROUTES.transfer, MOBILE_ROUTES.product, MOBILE_ROUTES.oe, MOBILE_ROUTES.gift, MOBILE_ROUTES.supplier, MOBILE_ROUTES.reimbursement],
  "warehouse quick actions should enter dedicated execution, reference, and reimbursement phone routes"
)

assert.deepStrictEqual(
  getMobileBottomNav("STORE", allMobilePermissions).map(item => item.label),
  ["工作台", "销售", "库存", "退货", "我的"],
  "store bottom navigation should not expose warehouse receiving as a primary tab"
)

assert.deepStrictEqual(
  getMobileBottomNav("WAREHOUSE", allMobilePermissions).map(item => item.label),
  ["工作台", "入库", "出库", "库存", "我的"],
  "warehouse bottom navigation should not expose sales order entry as a primary tab"
)

assert.strictEqual(
  getMobileContextProfile("STORE").title,
  "店铺工作台",
  "store profile should present store-specific page copy"
)

assert.strictEqual(
  getMobileContextProfile("WAREHOUSE").title,
  "仓库工作台",
  "warehouse profile should present warehouse-specific page copy"
)

const mobileContextEntryPaths = Array.from(new Set([
  getMobileHomePath("STORE"),
  getMobileHomePath("WAREHOUSE"),
  ...getMobileQuickActions("STORE", allMobilePermissions).map(item => item.path),
  ...getMobileQuickActions("WAREHOUSE", allMobilePermissions).map(item => item.path),
  ...getMobileBottomNav("STORE", allMobilePermissions).map(item => item.path),
  ...getMobileBottomNav("WAREHOUSE", allMobilePermissions).map(item => item.path)
]))

const unregisteredMobileContextEntryPaths = mobileContextEntryPaths.filter(entryPath => {
  return !mobileRouteSource.includes(`path: '${entryPath}'`)
})

const outboundRouteSource = mobileRouteSource.slice(
  mobileRouteSource.indexOf("path: '/mobile/outbound'"),
  mobileRouteSource.indexOf("path: '/mobile/transfer'")
)

assert.deepStrictEqual(
  unregisteredMobileContextEntryPaths,
  [],
  "all store and warehouse mobile quick-action and bottom-nav paths should resolve to registered mobile routes"
)

assert.ok(
  !featurePageSource.includes("EXISTING_FEATURE_ROUTE_FALLBACKS") &&
    !workbenchShellSource.includes("EXISTING_FEATURE_ROUTE_FALLBACKS"),
  "mobile pages should use real feature routes instead of silently falling back to another module"
)

assert.ok(
  !workbenchShellSource.includes('value: "18"') &&
    !workbenchShellSource.includes('value: "16"') &&
    !workbenchShellSource.includes('count: "12"') &&
    !workbenchShellSource.includes('count: "16"'),
  "mobile context workbench fallbacks should not display stale hard-coded business counts"
)

assert.ok(
  mobileRouteSource.includes("path: '/mobile/store'") &&
    mobileRouteSource.includes("path: '/mobile/warehouse'"),
  "router should register separate store and warehouse mobile homepages"
)

assert.ok(
  mobileRouteSource.includes("allowedDeptTypes: ['STORE']") &&
    mobileRouteSource.includes("allowedDeptTypes: ['WAREHOUSE']"),
  "context-specific mobile feature routes should declare whether they belong to store or warehouse users"
)

assert.ok(
  mobileRouteSource.includes("featureKey: 'transfer'") &&
    mobileRouteSource.includes("allowedDeptTypes: ['STORE', 'WAREHOUSE']"),
  "mobile transfer route should allow both store receiving and warehouse shipping workflows"
)

assert.ok(
  routerSource.includes("mobileRouteDefinitions"),
  "router should mount the shared mobile route definition list"
)

assert.ok(
  outboundRouteSource.includes("defaultQuery: { statusGroup: 'deliverable' }") &&
    outboundRouteSource.includes("{ label: '待出库', icon: 'truck', query: { statusGroup: 'deliverable' } }"),
  "mobile outbound should query all deliverable delivery notices like the workbench summary"
)

assert.ok(
  permissionSource.includes("getMobileHomePath") &&
    permissionSource.includes("getSelectedDeptType()"),
  "permission guard should redirect mobile root entry by selected department type"
)

assert.ok(
  permissionSource.includes("getMobileRouteAccessInfo"),
  "permission guard should correct legacy, blocked, or mismatched mobile routes by selected department type"
)

assert.ok(
  permissionSource.includes("getMobileRouteAccessInfo") &&
    permissionSource.includes("mobileRedirectReason") &&
    permissionSource.includes("mobileRedirectMessage") &&
    permissionSource.includes("mobileRedirectFrom"),
  "permission guard should preserve a user-facing reason when it redirects blocked or mismatched mobile routes"
)

assert.ok(
  !permissionSource.includes("isMobileRoutePreview") &&
    !permissionSource.includes("to.query.preview === '1'") &&
    !permissionSource.includes("query.demo === '1'"),
  "permission guard should not allow mobile preview or demo routes to bypass authentication"
)

assert.ok(
  featurePageSource.includes("getMobileRouteBottomNav(routePath, this.selectedDeptType") &&
    featurePageSource.includes("getMobileRoutePolicy(this.$route") &&
    featurePageSource.includes('v-if="showContextSelector"') &&
    featurePageSource.includes("routeScopeLabel") &&
    !featurePageSource.includes("mobileBottomNav.filter"),
  "mobile feature pages should render route-aware navigation and hide organization controls on global or self-service pages"
)

assert.ok(
  featurePageSource.includes("routeNoticeMessage") &&
    featurePageSource.includes("mobile-redirect-banner") &&
    featurePageSource.includes("resolveRouteNotice") &&
    featureListPolicySource.includes('"mobileRedirectReason"') &&
    featureListPolicySource.includes('"mobileRedirectMessage"') &&
    featureListPolicySource.includes('"mobileRedirectFrom"'),
  "mobile feature pages should show redirect reasons without leaking redirect query keys into business API filters"
)

assert.ok(
  workbenchShellSource.includes("routeNoticeMessage") &&
    workbenchShellSource.includes("mobile-redirect-banner") &&
    workbenchShellSource.includes("mobileRedirectReason") &&
    workbenchShellSource.includes("mobileRedirectMessage"),
  "mobile store and warehouse workbenches should show redirect reasons after blocked or mismatched route redirects"
)

const workbenchComponent = loadWorkbenchComponent()
const pendingTodoCopy = resolveTodoEmptyCopy(workbenchComponent, {
  inventory: { status: "pending", stale: false }
})
const unknownTodoCopy = resolveTodoEmptyCopy(workbenchComponent, {
  inventory: { status: "unknown", unknown: true, stale: false }
})
const staleTodoCopy = resolveTodoEmptyCopy(workbenchComponent, {
  inventory: { status: "fresh", stale: true }
})
const freshEmptyTodoCopy = resolveTodoEmptyCopy(workbenchComponent, {
  inventory: { status: "fresh", stale: false, error: null },
  approval: { status: "fresh", stale: false, error: null }
})
assert.ok(/加载|汇总/.test(pendingTodoCopy.title + pendingTodoCopy.message) &&
  !/暂无可处理/.test(pendingTodoCopy.title),
  "pending providers must render a loading state instead of a trusted empty result")
assert.ok(/无法确认|不可用|未知/.test(unknownTodoCopy.title + unknownTodoCopy.message) &&
  !/暂无可处理/.test(unknownTodoCopy.title),
  "unknown providers must explain uncertainty instead of claiming there are no todos")
assert.ok(/缓存|刷新/.test(staleTodoCopy.title + staleTodoCopy.message) &&
  !/暂无可处理/.test(staleTodoCopy.title),
  "stale providers must identify cached data instead of presenting a final empty result")
assert.ok(/暂无可处理/.test(freshEmptyTodoCopy.title) &&
  /权限/.test(freshEmptyTodoCopy.message),
  "only fresh, settled provider results may present the permission-aware empty state")

for (const contextType of ["STORE", "WAREHOUSE"]) {
  const noPermissionActions = getMobileQuickActions(contextType, [])
  const groups = partitionMobileActions(noPermissionActions, 4)
  assert.deepStrictEqual(Array.from(noPermissionActions), [],
    `${contextType} context must not expose quick actions without module permissions`)
  assert.deepStrictEqual({
    primary: Array.from(groups.primary),
    overflow: Array.from(groups.overflow)
  }, { primary: [], overflow: [] },
    `${contextType} context must produce a genuine empty quick-action result`)
}

const completeZeroSummary = {
  selectedDeptId: "88",
  selectedDeptType: "WAREHOUSE",
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
assert.strictEqual(
  resolveMobileWorkbenchSummaryState({ contextId: "", summary: completeZeroSummary }).type,
  "context-required",
  "a summary cannot become an empty result before an organization is selected"
)
const matchingContextState = resolveMobileWorkbenchSummaryState({
  contextId: "88",
  summary: completeZeroSummary
})
assert.strictEqual(matchingContextState.type, "ready",
  "a complete zero summary is valid only for its selected organization")
assert.strictEqual(matchingContextState.summary.selectedDeptId, "88")
assert.strictEqual(
  resolveMobileWorkbenchSummaryState({ contextId: "99", summary: completeZeroSummary }).type,
  "network-error",
  "a summary from another organization must not be shown as the current context's empty result"
)

assert.ok(
  featurePageSource.includes("resolveMobileErrorMessage(error)") &&
    featurePageSource.includes("当前账号没有该模块权限") &&
    featurePageSource.includes("接口暂不可用，请稍后重试"),
  "mobile feature pages should distinguish permission errors from generic interface failures"
)

assert.ok(
  requestSource.includes("normalizeRequestError") &&
    requestSource.includes("error.code = code") &&
    requestSource.includes("error.response = response") &&
    !requestSource.includes("return Promise.reject('error')"),
  "request wrapper should reject structured Error objects so mobile pages can identify 403 permission failures"
)

assert.ok(
  featurePageSource.includes("calc(124px + env(safe-area-inset-bottom))") &&
    featurePageSource.includes("min-height: 44px") &&
    featurePageSource.includes("bottom: max(10px, env(safe-area-inset-bottom))"),
  "mobile feature pages should reserve bottom-nav safe area and keep tap targets at least 44px high"
)

assert.ok(
  featurePageSource.includes(".feature-action-overflow:not([open]) > .overflow-feature-actions") &&
    featurePageSource.includes("display: none"),
  "collapsed mobile overflow actions should stay hidden instead of extending beneath the bottom nav"
)

assert.ok(
  workbenchShellSource.includes("calc(148px + env(safe-area-inset-bottom))") &&
    workbenchShellSource.includes(".context-card button") &&
    workbenchShellSource.includes("min-height: 44px") &&
    workbenchShellSource.includes("bottom: max(10px, env(safe-area-inset-bottom))"),
  "mobile workbench should reserve enough bottom-nav safe area and keep compact controls tappable"
)

const createBaseQuerySource = featureServiceSource.slice(
  featureServiceSource.indexOf("const createBaseQuery"),
  featureServiceSource.indexOf("const resolveList")
)

assert.ok(
  createBaseQuerySource.includes('if (source.selectedDeptType === "STORE")') &&
    createBaseQuerySource.includes("query.shopDeptId = source.selectedDeptId") &&
    createBaseQuerySource.includes('if (source.selectedDeptType === "WAREHOUSE")') &&
    createBaseQuerySource.includes("query.warehouseId = source.selectedDeptId"),
  "mobile feature list queries should send shopDeptId only for stores and warehouseId only for warehouses"
)

assert.ok(
  featureSearchConfigsSource.includes("stock:") &&
    featureSearchConfigsSource.includes("productKeyword") &&
    featureSearchConfigsSource.includes("商品") &&
    featureSearchConfigsSource.includes("replenishment:") &&
    featureSearchConfigsSource.includes("orderNo") &&
    featureSearchConfigsSource.includes("补货单号"),
  "mobile stock should expose product search, and replenishment should expose transfer-order search"
)

assert.ok(
  featureServiceSource.includes("listProduct") &&
    featureServiceSource.includes("resolveStockProductQuery") &&
    featureServiceSource.includes("productKeyword") &&
    featureServiceSource.includes("productCode") &&
    featureServiceSource.includes("productId"),
  "mobile stock data service should resolve product keyword searches to productId before calling the stock list API"
)

assert.ok(
  featurePageSource.includes("feature.allowedDeptTypes") &&
    featurePageSource.includes("selectedDeptType") &&
    featurePageSource.includes("isFeatureAllowed"),
  "mobile feature pages should enforce route-level store or warehouse context restrictions"
)

assert.ok(
  /loadData\(options = \{\}\) \{[\s\S]*?if \(!this\.isFeatureAllowed\(\)\)[\s\S]*?fetchMobileFeatureData/.test(featurePageSource) &&
    !featurePageSource.includes("isPreviewMode") &&
    !featurePageSource.includes("seedItems"),
  "mobile feature pages should enforce store or warehouse context before loading live data, without preview seed branches"
)

assert.ok(
  featurePageSource.includes('@click="openItem(item)"') &&
    featurePageSource.includes("selectedItem") &&
    featurePageSource.includes("detail-sheet"),
  "mobile feature list rows should open a phone-friendly detail sheet instead of being static text"
)

assert.ok(
  featurePageSource.includes("filter-summary") &&
    featurePageSource.includes("activeFilterLabel") &&
    featurePageSource.includes("resolveActionLabelForQuery(this.featureQuery)") &&
    featurePageSource.includes("mobileFeatureListPolicy") &&
    featureListPolicySource.includes("cleanComparableQuery"),
  "mobile feature pages should surface the current quick filter like desktop query forms do"
)

assert.ok(
  featurePageSource.includes("load-more-row") &&
    featurePageSource.includes("hasMoreItems") &&
    featurePageSource.includes("loadMore()") &&
    featurePageSource.includes("pageNum: this.pageNum") &&
    featurePageSource.includes("pageSize: this.pageSize") &&
    featurePageSource.includes("this.items.concat(nextItems)"),
  "mobile feature lists should provide load-more pagination like desktop tables"
)

assert.ok(
  featureSearchConfigsSource.includes("noticeNo") &&
    featureSearchConfigsSource.includes("returnNo") &&
    featureSearchConfigsSource.includes("customerName") &&
    featureSearchConfigsSource.includes("supplierName") &&
    featurePageSource.includes("buildRequestQuery()") &&
    featurePageSource.includes("query: this.buildRequestQuery()"),
  "mobile feature pages should expose compact search using desktop list query fields"
)

assert.ok(
  featureSearchConfigsSource.includes("STOCK_STATUS_FILTERS") &&
    !featureSearchConfigsSource.includes("全部预警") &&
    featurePageSource.includes("stockStatusFilters") &&
    featurePageSource.includes("activeStockStatus") &&
    featurePageSource.includes("selectStockStatus(status)") &&
    featureListPolicySource.includes("query.stockStatus = source.activeStockStatus"),
  "mobile stock pages should expose desktop stockStatus filters without applying inventory warning filters to replenishment"
)

assert.ok(
  featurePageSource.includes("submitSearch()") &&
    featurePageSource.includes("clearSearch()") &&
    featurePageSource.includes("selectSearchField(fieldKey)") &&
    featurePageSource.includes("this.pageNum = 1"),
  "mobile feature search should reset pagination and let users switch searchable desktop fields"
)

assert.ok(
  mobileSheetSource.includes(".detail-sheet") &&
    mobileSheetSource.includes("max-height: 100%") &&
    /\.detail-sheet-body\s*\{[\s\S]*?overflow-y:\s*auto;[\s\S]*?-webkit-overflow-scrolling:\s*touch;/.test(mobileSheetSource),
  "mobile feature detail sheets should keep one internal content scroller above fixed actions on short phones"
)

assert.deepStrictEqual(
  [
    getWorkbenchActionDetails("STORE", "销售退货").path,
    getWorkbenchActionDetails("STORE", "补货申请").path,
    getWorkbenchActionDetails("WAREHOUSE", "发货处理").path,
    getWorkbenchActionDetails("WAREHOUSE", "采购退货").path,
    getWorkbenchActionDetails("WAREHOUSE", "调拨处理").path
  ],
  [
    "/mobile/sales-return",
    "/mobile/replenishment",
    "/mobile/outbound",
    "/mobile/purchase-return",
    "/mobile/transfer"
  ],
  "mobile workbench action details should not route return, replenishment, outbound, purchase return, or transfer todos through generic modules"
)

assert.ok(
  selectShopSource.includes("getMobileHomePath(this.selectedDeptType, this.userPermissions)") &&
    workbenchShellSource.includes("getMobileHomePath(this.normalizedContextType, this.userPermissions)") &&
    mobileProfileSource.includes("getMobileHomePath(this.selectedContext.deptType, this.userPermissions)") &&
    selectShopSource.includes("getFallbackRedirect"),
  "all mobile homepage callers should propagate account permissions into HR-aware home selection"
)
