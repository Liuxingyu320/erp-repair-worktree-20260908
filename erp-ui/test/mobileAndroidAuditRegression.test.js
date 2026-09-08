const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  getMobileRouteAccessInfo,
  getMobileUnavailablePaths,
  isMobileUnavailablePath
} = require("../src/views/mobile/mobileNavigation")

const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const selectShopSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/select-shop/index.vue"),
  "utf8"
)
const requestSource = fs.readFileSync(
  path.resolve(__dirname, "../src/utils/request.js"),
  "utf8"
)
const appSource = fs.readFileSync(
  path.resolve(__dirname, "../src/App.vue"),
  "utf8"
)

const previouslyRedirectedMobileRoutes = [
  "/mobile/customer",
  "/mobile/category",
  "/mobile/stock-log",
  "/mobile/transfer-records",
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

previouslyRedirectedMobileRoutes.forEach(routePath => {
  assert.strictEqual(
    isMobileUnavailablePath(routePath),
    false,
    `${routePath} should remain directly testable on mobile instead of being hidden by a static redirect`
  )
  assert.notStrictEqual(
    getMobileRouteAccessInfo(routePath, "STORE").reason,
    "unavailable",
    `${routePath} should not be reported as unavailable for store users`
  )
  assert.notStrictEqual(
    getMobileRouteAccessInfo(routePath, "WAREHOUSE").reason,
    "unavailable",
    `${routePath} should not be reported as unavailable for warehouse users`
  )
})

assert.deepStrictEqual(
  getMobileUnavailablePaths(),
  [],
  "mobile QA should be able to open every registered phone route directly; entry filtering belongs in menus and action buttons"
)

assert.ok(
  featurePageSource.includes("errorStateLabel") &&
    !featurePageSource.includes('if (this.errorMessage) return "接口异常"') &&
    featurePageSource.includes('return "组织不匹配"') &&
    featurePageSource.includes('return "无权限"') &&
    featurePageSource.includes('return "加载失败"'),
  "mobile list panels should label context, permission, and load failures instead of always saying 接口异常"
)

assert.ok(
  featurePageSource.includes("normalizeMobileErrorMessage(message)") &&
    featurePageSource.includes("当前账号没有该组织的业务权限") &&
    featurePageSource.includes("当前账号没有该模块权限"),
  "mobile pages should translate backend shop-scope and permission errors into actionable user-facing text"
)

assert.ok(
  featurePageSource.includes("当前选择的是门店，") &&
    featurePageSource.includes("当前选择的是仓库，") &&
    !featurePageSource.includes("请选择仓库后再进入") &&
    !featurePageSource.includes("请选择店铺后再进入"),
  "mobile context mismatch copy should name the current context and the required context without looking like an interface failure"
)

assert.ok(
  featurePageSource.includes("if (this.errorMessage) return false") &&
    featurePageSource.includes("canShowTopAction(action)") &&
    featurePageSource.includes("canUseActionInSelectedContext(action)"),
  "mobile top actions such as attendance check-in should be hidden while the page is in an error or wrong-organization state"
)

assert.ok(
  featurePageSource.includes("canUseActionInSelectedContext(action)") &&
    featurePageSource.includes("allowedDeptTypes.indexOf(this.selectedDeptType) > -1"),
  "mobile mine actions should hide store-only entries such as attendance and salary when the selected organization is a warehouse"
)

assert.ok(
  requestSource.includes("isMobilePageRequest") &&
    requestSource.includes("window.location.pathname.indexOf('/mobile/') === 0") &&
    requestSource.includes("config.mobileSilentError !== false"),
  "mobile pages should suppress duplicate global request toasts so page-level error copy is the source of truth"
)

assert.ok(
  selectShopSource.includes("listShopTree({ silentError: true") &&
    selectShopSource.includes("获取可选组织失败，请稍后重试"),
  "mobile-critical shop selection should silence the global request toast and keep one page-level retryable error"
)

assert.ok(
  selectShopSource.includes("mobileNoBusinessAccess") &&
    selectShopSource.includes("当前账号暂无移动端可用门店或仓库权限") &&
    selectShopSource.includes(":disabled=\"mobileNoBusinessAccess\""),
  "mobile organization selection should show a clear no-mobile-business-access state instead of looping on select-shop"
)

assert.ok(
  featurePageSource.includes("createMobileRouteLoadGuard") &&
    featurePageSource.includes("releaseAllMobileOverlays") &&
    featurePageSource.includes("routeLoadGuard.begin(this.featureKey)") &&
    featurePageSource.includes("routeLoadGuard.isCurrent(loadToken, this.featureKey)"),
  "mobile feature routes should release stale overlays and ignore responses from the previous feature"
)

assert.ok(
  appSource.includes('<router-view :key="routeViewKey" />') &&
    appSource.includes('path.indexOf("/mobile/") === 0') &&
    appSource.includes('return "desktop-router-view"'),
  "the root router view should isolate mobile paths without remounting every desktop route"
)
