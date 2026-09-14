const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  MOBILE_ROUTES,
  getMobileRouteDefinition,
  getMobileRouteAccessInfo,
  getMobileRouteAccessRedirect,
  getMobileAdminManageActions,
  getMobileBottomNav,
  getMobileRouteBottomNav,
  getMobileRoutePolicy,
  getMobileQuickActions,
  getMobileRouteRequiredPermissions,
  getMobileUnavailablePaths,
  hasAnyMobilePermission,
  isMobileUnavailablePath
} = require("../src/views/mobile/mobileNavigation")

const routerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/router/index.js"),
  "utf8"
)
const mobileRouteSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/mobileRouteDefinitions.js"),
  "utf8"
)
const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const { mobileRouteDefinitions } = require("../src/views/mobile/mobileRouteDefinitions")
const mobileExperience = require("../src/views/mobile/mobileExperience")
const prioritizeMobileFeatureActions = mobileExperience.prioritizeMobileFeatureActions

const routedFeatureActions = mobileRouteDefinitions.flatMap(sourceRoute => {
  const feature = sourceRoute.meta && sourceRoute.meta.mobileFeature
  return (feature && Array.isArray(feature.actions) ? feature.actions : [])
    .filter(action => action && action.path)
    .map(action => ({ sourceRoute, action }))
})

routedFeatureActions.forEach(({ sourceRoute, action }) => {
  const targetRoute = mobileRouteDefinitions.find(definition => definition.path === action.path)
  if (!targetRoute) return
  const targetPermissions = getMobileRouteRequiredPermissions(action.path)
  if (!targetPermissions.length) return
  const effectiveVisibilityPermissions = Array.isArray(action.permissions)
    ? action.permissions
    : getMobileRouteRequiredPermissions(sourceRoute.path)
  assert.ok(
    effectiveVisibilityPermissions.some(permission => targetPermissions.includes(permission)),
    `${sourceRoute.path} ${action.label} should only be visible with a permission accepted by ${action.path}`
  )
})

;[
  ["/mobile/purchase", "采购退货", "inv:purchase:list", "inv:purchaseReturn:list"],
  ["/mobile/transfer-approval", "调拨处理", "inv:transfer:approve", "inv:transfer:list"]
].forEach(([sourcePath, label, sourcePermission, targetPermission]) => {
  const sourceRoute = mobileRouteDefinitions.find(definition => definition.path === sourcePath)
  const action = sourceRoute.meta.mobileFeature.actions.find(item => item.label === label)
  assert.strictEqual(
    hasAnyMobilePermission(action.permissions, [sourcePermission]),
    false,
    `${label} should be hidden from users who only have the source-page permission`
  )
  assert.strictEqual(
    hasAnyMobilePermission(action.permissions, [targetPermission]),
    true,
    `${label} should be visible when the target-page permission is granted`
  )
})

assert.ok(
  featurePageSource.includes("filter(action => this.canShowTopAction(action))") &&
    featurePageSource.includes("action.permissions || MOBILE_ACTION_PERMISSIONS[action.actionId]"),
  "mobile feature action rendering should consume declared action permissions"
)

const explicitLowFrequencyActions = [
  ["/mobile/purchase", "采购退货"],
  ["/mobile/profile", "退出登录"],
  ["/mobile/monitor-job", "调度日志"],
  ["/mobile/mine", "我的签约"],
  ["/mobile/mine", "考勤"],
  ["/mobile/mine", "工资"],
  ["/mobile/mine", "资产报修"],
  ["/mobile/mine", "退出登录"]
]

explicitLowFrequencyActions.forEach(([routePath, label]) => {
  const route = mobileRouteDefinitions.find(definition => definition.path === routePath)
  const feature = route && route.meta && route.meta.mobileFeature
  const actions = feature && Array.isArray(feature.actions)
    ? (typeof prioritizeMobileFeatureActions === "function"
        ? prioritizeMobileFeatureActions(feature.actions)
        : feature.actions)
    : []
  const action = actions.length
    ? actions.find(item => item.label === label)
    : null
  assert.strictEqual(
    action && action.placement,
    "more",
    `${routePath} ${label} should remain in more even when higher-frequency actions are permission-filtered`
  )
})

assert.strictEqual(
  new Set(mobileRouteDefinitions.map(route => route.path)).size,
  mobileRouteDefinitions.length,
  "mobile route definitions should not register duplicate phone-web paths"
)

assert.strictEqual(
  mobileRouteDefinitions.length,
  59,
  "the active mobile matrix should include the personal message center and exclude retired business routes"
)
mobileRouteDefinitions.forEach(route => {
  const policy = getMobileRoutePolicy(route.path)
  assert.ok(policy, `${route.path} should have one route policy`)
  assert.ok(
    ["store", "warehouse", "business-any", "hr", "global", "self", "workflow"].includes(policy.scopePolicy) &&
      ["store", "warehouse", "context", "hr", "admin", "self", "workflow", "none"].includes(policy.navProfile) &&
      typeof policy.contextRequired === "boolean" &&
      policy.entryFallback,
    `${route.path} should declare scope, navigation, context, and recovery policy`
  )
})
assert.ok(
  !mobileRouteDefinitions.some(route => ["/mobile/hr/team", "/mobile/oe-replenishment"].includes(route.path)),
  "retired HR-team and standalone OE-replenishment routes should stay outside the active matrix"
)

const p0Routes = [
  ["attendance", "/mobile/attendance"],
  ["stock", "/mobile/stock"],
  ["purchase", "/mobile/purchase"],
  ["outbound", "/mobile/outbound"],
  ["stockCheck", "/mobile/stock-check"],
  ["transfer", "/mobile/transfer"],
  ["transferApproval", "/mobile/transfer-approval"]
]

const hrRoutes = [
  ["hrWorkbench", "/mobile/hr", "hr:onboarding:workbench", "@/views/mobile/hr/index"],
  ["hrOnboardingList", "/mobile/hr/onboarding", "hr:onboarding:list", "@/views/mobile/hr/onboarding/index"],
  ["hrOnboardingAdd", "/mobile/hr/onboarding/create", "hr:onboarding:add", "@/views/mobile/hr/onboarding/form"],
  ["hrOnboardingDetail", "/mobile/hr/onboarding/:id(\\d+)", "hr:onboarding:query", "@/views/mobile/hr/onboarding/detail"],
  ["hrOnboardingEdit", "/mobile/hr/onboarding/:id(\\d+)/edit", "hr:onboarding:edit", "@/views/mobile/hr/onboarding/form"]
]

hrRoutes.forEach(([key, routePath, permission, componentPath]) => {
  const route = mobileRouteDefinitions.find(definition => definition.path === routePath)
  assert.strictEqual(MOBILE_ROUTES[key], routePath, `${key} should expose a stable HR mobile route`)
  assert.deepStrictEqual(
    route && route.meta && route.meta.mobileFeature && route.meta.mobileFeature.permissions,
    [permission],
    `${routePath} should declare ${permission} in route metadata`
  )
  assert.strictEqual(
    route && route.meta && route.meta.mobileFeature && route.meta.mobileFeature.requiresBusinessContext,
    false,
    `${routePath} should explicitly opt out of store and warehouse context`
  )
  assert.ok(
    route && String(route.component).includes(componentPath),
    `${routePath} should lazy-load its dedicated ${componentPath} page`
  )
})

assert.strictEqual(
  getMobileRouteDefinition("/mobile/hr/onboarding/7").path,
  "/mobile/hr/onboarding/:id(\\d+)",
  "numeric onboarding detail should resolve to the dynamic detail definition"
)

assert.strictEqual(
  getMobileRouteDefinition("/mobile/hr/onboarding/7/edit").path,
  "/mobile/hr/onboarding/:id(\\d+)/edit",
  "numeric onboarding edit should resolve to the dynamic edit definition"
)

;["1", "42", "987654321"].forEach(id => {
  assert.strictEqual(getMobileRouteDefinition(`/mobile/hr/onboarding/${id}`).meta.mobileFeature.featureKey, "hrOnboardingDetail")
  assert.strictEqual(getMobileRouteDefinition(`/mobile/hr/onboarding/${id}/edit`).meta.mobileFeature.featureKey, "hrOnboardingEdit")
})
;["0x10", "-1", "1.5", "1/extra"].forEach(id => {
  assert.strictEqual(getMobileRouteDefinition(`/mobile/hr/onboarding/${id}`), null, `invalid dynamic id ${id} should not resolve`)
})

p0Routes.forEach(([key, routePath]) => {
  assert.strictEqual(MOBILE_ROUTES[key], routePath, `${key} should have a stable mobile route`)
  assert.ok(
    mobileRouteSource.includes(`path: '${routePath}'`) &&
      mobileRouteSource.includes(`featureKey: '${key}'`),
    `${routePath} should be registered as a mobile feature route`
  )
})

const mineRouteSource = mobileRouteSource.slice(
  mobileRouteSource.indexOf("path: '/mobile/mine'"),
  mobileRouteSource.length
)
const mineRoute = mobileRouteDefinitions.find(route => route.path === "/mobile/mine")
const mineFeatureActions = mineRoute && mineRoute.meta && mineRoute.meta.mobileFeature
  ? mineRoute.meta.mobileFeature.actions || []
  : []

assert.ok(
  routerSource.includes("mobileRouteDefinitions"),
  "router should mount the shared mobile route definition list"
)

const defaultBlockedAdminRoutes = [
  "/mobile/system-user",
  "/mobile/system-role",
  "/mobile/system-post",
  "/mobile/system-dept",
  "/mobile/system-menu",
  "/mobile/user-shop",
  "/mobile/system-config",
  "/mobile/system-dict-type",
  "/mobile/system-dict-data",
  "/mobile/system-logininfor",
  "/mobile/system-operlog",
  "/mobile/monitor-job",
  "/mobile/monitor-job-log",
  "/mobile/monitor-online",
  "/mobile/salary-scheme",
  "/mobile/transfer-rules"
]

const previouslyBlockedDirectAccessMobileRoutes = [
  "/mobile/customer",
  "/mobile/category",
  "/mobile/stock-log",
  "/mobile/transfer-records",
  ...defaultBlockedAdminRoutes
]

defaultBlockedAdminRoutes.forEach(routePath => {
  assert.ok(
    !mineRouteSource.includes(`path: '${routePath}'`),
    `mobile mine default actions should not expose backend/admin route ${routePath}`
  )
})

assert.ok(
  ["考勤", "工资"].every(label => {
    const action = mineFeatureActions.find(item => item.label === label)
    return action && Array.isArray(action.allowedDeptTypes) && action.allowedDeptTypes.includes("STORE")
  }),
  "mobile mine should hide store-only attendance and salary entries when the current context is a warehouse"
)

assert.deepStrictEqual(
  getMobileUnavailablePaths(),
  [],
  "registered mobile routes should stay directly testable; unavailable behavior should not be implemented as a static route blocklist"
)

previouslyBlockedDirectAccessMobileRoutes.forEach(routePath => {
  assert.strictEqual(
    isMobileUnavailablePath(routePath),
    false,
    `${routePath} should no longer be hidden behind a static unavailable redirect`
  )
  assert.notStrictEqual(
    getMobileRouteAccessInfo(routePath, "WAREHOUSE").reason,
    "unavailable",
    `${routePath} should not report an unavailable reason when opened directly`
  )
})

assert.deepStrictEqual(
  getMobileRouteAccessInfo("/mobile/warehouse", "STORE"),
  {
    redirect: "/mobile/store",
    reason: "context-mismatch",
    message: "当前选择的是店铺，已返回店铺工作台"
  },
  "store users redirected away from warehouse-only homepage should see a context mismatch reason"
)

assert.deepStrictEqual(
  getMobileRouteAccessInfo("/mobile/store", "WAREHOUSE"),
  {
    redirect: "/mobile/warehouse",
    reason: "context-mismatch",
    message: "当前选择的是仓库，已返回仓库工作台"
  },
  "warehouse users redirected away from store-only homepage should see a context mismatch reason"
)

;[
  "/mobile/product",
  "/mobile/supplier",
  "/mobile/stock-check",
  "/mobile/transfer-approval"
].forEach(routePath => {
  assert.strictEqual(isMobileUnavailablePath(routePath), false, `${routePath} should remain available on mobile`)
})

assert.ok(
  getMobileAdminManageActions().length === 0,
  "mobile admin management routes should not be exposed from the phone workbench"
)

assert.deepStrictEqual(
  getMobileBottomNav("STORE").map(item => item.path),
  ["/mobile/store", "/mobile/sales", "/mobile/stock", "/mobile/sales-return", "/mobile/mine"],
  "store mobile bottom nav should stay focused on daily store work"
)

assert.deepStrictEqual(
  getMobileBottomNav("STORE", []).map(item => item.path),
  ["/mobile/store", "/mobile/mine"],
  "store mobile bottom nav should hide permission-gated business tabs once the account permissions are known"
)

assert.deepStrictEqual(
  getMobileBottomNav("WAREHOUSE").map(item => item.path),
  ["/mobile/warehouse", "/mobile/purchase", "/mobile/outbound", "/mobile/stock", "/mobile/mine"],
  "warehouse mobile bottom nav should stay focused on warehouse work"
)

assert.deepStrictEqual(
  getMobileBottomNav("WAREHOUSE", []).map(item => item.path),
  ["/mobile/warehouse", "/mobile/mine"],
  "warehouse mobile bottom nav should hide permission-gated business tabs once the account permissions are known"
)

assert.deepStrictEqual(
  getMobileRouteBottomNav("/mobile/system-user", "STORE", ["*:*:*"]).map(item => item.label),
  ["管理", "待办", "通知", "我的"],
  "global management pages should use an admin navigation instead of the store sales navigation"
)
assert.deepStrictEqual(
  getMobileRouteBottomNav("/mobile/profile", "", [], { driveEnabled: false }).map(item => item.label),
  ["待办", "通知", "我的"],
  "self-service pages should stay usable without a selected business organization"
)

assert.deepStrictEqual(
  getMobileBottomNav("STORE", ["inv:stock:list"]).map(item => item.path),
  ["/mobile/store", "/mobile/stock", "/mobile/mine"],
  "store bottom nav should show only the business tabs granted to the current account"
)

assert.deepStrictEqual(
  getMobileQuickActions("STORE", ["inv:stock:list"]).map(item => item.path),
  ["/mobile/stock"],
  "store quick actions should hide actions whose module permissions are missing"
)

assert.deepStrictEqual(
  getMobileQuickActions("WAREHOUSE", ["inv:deliveryNotice:list"]).map(item => item.path),
  ["/mobile/outbound"],
  "warehouse quick actions should hide actions whose module permissions are missing"
)

assert.strictEqual(
  hasAnyMobilePermission(["inv:sales:list"], ["*:*:*"]),
  true,
  "mobile entry filtering should keep wildcard admin permissions working"
)

const storeQuickPaths = getMobileQuickActions("STORE").map(item => item.path)
const warehouseQuickPaths = getMobileQuickActions("WAREHOUSE").map(item => item.path)

;[
  "/mobile/sales",
  "/mobile/stock",
  "/mobile/sales-return",
  "/mobile/transfer",
  "/mobile/product"
].forEach(routePath => {
  assert.ok(storeQuickPaths.includes(routePath), `store quick actions should include ${routePath}`)
})

assert.ok(
  !storeQuickPaths.includes("/mobile/customer"),
  "store quick actions should not expose customer reference lookup"
)

;[
  "/mobile/purchase",
  "/mobile/outbound",
  "/mobile/purchase-return",
  "/mobile/stock-check",
  "/mobile/transfer",
  "/mobile/supplier"
].forEach(routePath => {
  assert.ok(warehouseQuickPaths.includes(routePath), `warehouse quick actions should include ${routePath}`)
})

;[
  "/mobile/category",
  "/mobile/stock-log",
  "/mobile/transfer-records"
].forEach(routePath => {
  assert.ok(!warehouseQuickPaths.includes(routePath), `warehouse quick actions should not expose low-frequency route ${routePath}`)
})

assert.ok(
  featurePageSource.includes("partitionMobileActions") &&
    featurePageSource.includes("primaryFeatureActions") &&
    featurePageSource.includes("overflowFeatureActions") &&
    featurePageSource.includes('placement: "more"'),
  "mobile feature pages should cap primary shortcuts and always place export in more"
)

assert.ok(
  featurePageSource.includes("resolveMobileListState") &&
    featurePageSource.includes("handleListStateAction(actionId)") &&
    featurePageSource.includes('aria-live="polite"') &&
    featurePageSource.includes('aria-live="assertive"'),
  "mobile feature list states should distinguish announced loading and recoverable failures"
)

assert.ok(
  featurePageSource.includes('v-for="item in displayItems"') &&
    featurePageSource.includes("list-row") &&
    featurePageSource.includes('type="button"'),
  "mobile feature data rows should be keyboard and assistive-technology actionable"
)
