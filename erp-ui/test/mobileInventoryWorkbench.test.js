const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  PUBLIC_MOBILE_INVENTORY_PATH,
  workbenchData
} = require("../src/views/mobile/inventory/workbenchData")
const {
  MOBILE_ROUTES,
  mobileBottomNav,
  mobileQuickActions
} = require("../src/views/mobile/mobileNavigation")
const {
  mapWorkbenchResponse
} = require("../src/views/mobile/inventory/workbenchMapper")
const { getFallbackWorkbenchProfile } = require("../src/views/mobile/components/mobileWorkbenchPolicy")

assert.strictEqual(
  PUBLIC_MOBILE_INVENTORY_PATH,
  "/mobile/inventory",
  "mobile inventory route should be stable for the authenticated mobile workbench"
)

assert.deepStrictEqual(
  workbenchData.businessDomains,
  ["sales", "purchase", "inventory", "warehouse"],
  "workbench should cover sales, purchase, inventory, and warehouse domains"
)

assert.deepStrictEqual(
  workbenchData.metrics.map(item => item.label),
  ["今日销售", "采购待办", "低库存", "待发货"],
  "hero metrics should represent the current front-end inventory modules"
)

assert.deepStrictEqual(
  workbenchData.quickActions.map(item => item.label),
  ["销售开单", "采购入库", "查库存", "盘点"],
  "quick actions should include sales, purchase, stock lookup, and stock check"
)

assert.deepStrictEqual(
  workbenchData.priorityItems,
  [],
  "legacy inventory samples must not act as a second todo source"
)

assert.deepStrictEqual(
  mobileQuickActions.map(item => item.path),
  ["/mobile/sales", "/mobile/purchase", "/mobile/stock", "/mobile/stock-check"],
  "quick actions should stay inside the phone-web route group"
)

assert.deepStrictEqual(
  workbenchData.bottomNav.map(item => item.label),
  ["工作台", "销售", "采购", "库存", "我的"],
  "bottom navigation should match the approved mobile IA"
)

assert.deepStrictEqual(
  mobileBottomNav.map(item => item.path),
  ["/mobile/inventory", "/mobile/sales", "/mobile/purchase", "/mobile/stock", "/mobile/mine"],
  "bottom navigation should not jump into desktop business routes"
)

assert.ok(
  !mobileBottomNav.some(item => item.path === "/mobile/todo"),
  "unified todo should be a hidden processing page, not a sixth bottom-nav item"
)

assert.ok(
  ["workbench", "sales", "purchase", "stock", "stockCheck", "mine"].every(key => Object.prototype.hasOwnProperty.call(MOBILE_ROUTES, key)),
  "mobile route constants should keep the first phone-web IA aliases"
)

const mappedTotals = mapWorkbenchResponse({
  selectedDeptName: "云岫茶室 总店",
  totals: {
    salesRows: 27,
    purchaseRows: 14,
    lowStockRows: 9,
    deliveryRows: 18
  },
  salesRows: [{ salesOrderNo: "SO001" }],
  purchaseRows: [{ orderNo: "PO001" }],
  lowStockRows: [{ productName: "龙井", currentQuantity: 2 }],
  deliveryRows: [{ noticeNo: "DN001" }]
}, workbenchData)

assert.deepStrictEqual(
  mappedTotals.metrics.map(item => item.value),
  ["27", "14", "9", "18"],
  "workbench metrics should prefer backend total counts over the first page row length"
)

const mappedEmptyBackend = mapWorkbenchResponse({
  selectedDeptName: "云岫茶室 总店",
  totals: {
    salesRows: 0,
    purchaseRows: 0,
    lowStockRows: 0,
    deliveryRows: 0,
    stockCheckRows: 0
  },
  salesRows: [],
  purchaseRows: [],
  lowStockRows: [],
  deliveryRows: [],
  stockCheckRows: []
}, workbenchData)

assert.deepStrictEqual(
  mappedEmptyBackend.priorityItems,
  [],
  "workbench should not mix fallback sample tasks into a successful empty backend response"
)

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
const workbenchSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/inventory/index.vue"),
  "utf8"
)
const workbenchServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/inventory/workbenchService.js"),
  "utf8"
)
const workbenchMapperSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/inventory/workbenchMapper.js"),
  "utf8"
)
const workbenchShellSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/components/MobileWorkbenchShell.vue"),
  "utf8"
)
const commonStyleSource = fs.readFileSync(
  path.resolve(__dirname, "../src/assets/styles/common.scss"),
  "utf8"
)

assert.ok(
  mobileRouteSource.includes("path: '/mobile/inventory'"),
  "router should register /mobile/inventory"
)

assert.ok(
  mobileRouteSource.includes("path: '/mobile/sales'") &&
    mobileRouteSource.includes("path: '/mobile/purchase'") &&
    mobileRouteSource.includes("path: '/mobile/stock'") &&
    mobileRouteSource.includes("path: '/mobile/stock-check'") &&
    mobileRouteSource.includes("path: '/mobile/mine'"),
  "router should register the first mobile sales, purchase, stock, stock-check, and mine routes"
)

assert.ok(
  mobileRouteSource.includes("@/views/mobile/inventory/index"),
  "router should load the mobile inventory workbench component"
)

assert.ok(
  mobileRouteSource.includes("@/views/mobile/feature/index"),
  "router should load the shared mobile feature component for first phone-web modules"
)

assert.ok(
  mobileRouteSource.includes("featureKey: 'sales'") &&
    mobileRouteSource.includes("featureKey: 'purchase'") &&
    mobileRouteSource.includes("featureKey: 'stock'") &&
    mobileRouteSource.includes("featureKey: 'stockCheck'"),
  "mobile feature routes should declare backend feature keys"
)

assert.ok(
  routerSource.includes("mobileRouteDefinitions"),
  "router should mount the shared mobile route definition list"
)

assert.ok(
  permissionSource.includes("const whiteList = ['/login', '/register']") &&
    permissionSource.includes("shouldSelectShop(to.path)"),
  "mobile inventory should require authentication and store selection before entering"
)

assert.ok(
  !workbenchSource.includes("browser-chrome") &&
    !workbenchSource.includes("status-row") &&
    !workbenchSource.includes("address-bar"),
  "mobile workbench should render as a real web page without a fake phone/browser frame"
)

assert.ok(
  workbenchSource.includes("mobileQuickActions") &&
    workbenchSource.includes("mobileBottomNav") &&
    !workbenchSource.includes('"/inventory/sales"') &&
    !workbenchSource.includes('"/inventory/purchase"') &&
    !workbenchSource.includes('"/inventory/stock"') &&
    !workbenchSource.includes('"/user/profile"'),
  "mobile workbench navigation should use phone-web routes instead of desktop pages"
)

assert.ok(
  workbenchSource.includes("todoRecent") && workbenchSource.includes("todoProviderStates") &&
    workbenchSource.includes("/mobile/todo") && workbenchSource.includes("/mobile/notice") &&
    !workbenchSource.includes("getMobileWorkbenchSummary"),
  "mobile inventory workbench should use unified todos while keeping notices separate"
)

assert.ok(
  !workbenchServiceSource.includes("todoCount: summary.todoCount") &&
    workbenchMapperSource.includes("result.priorityItems = []"),
  "inventory data compatibility may keep metrics but must not maintain a parallel todo total/list"
)

assert.ok(
  workbenchShellSource.includes("getMobileWorkbenchSummary") &&
    workbenchShellSource.includes("workbenchSummary") &&
    workbenchShellSource.includes("loadWorkbenchSummary") &&
    !workbenchShellSource.includes("summary.todoCount") &&
    !workbenchShellSource.includes("summary.todos"),
  "shared mobile shell should use the legacy summary only to update profile metrics"
)

assert.ok(
  workbenchSource.includes("env(safe-area-inset-bottom)"),
  "mobile inventory workbench content padding should include safe-area inset for phone bottom navigation"
)

assert.ok(
  workbenchSource.includes(".bottom-nav {\n  position: fixed"),
  "mobile inventory bottom navigation should stay fixed to the phone viewport on long pages"
)

assert.ok(
  commonStyleSource.includes("@media (max-width: 480px)") &&
    commonStyleSource.includes(".el-message-box") &&
    commonStyleSource.includes("calc(100vw - 32px)"),
  "mobile confirmation dialogs should fit within narrow phone viewports"
)

const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)

assert.ok(
  featurePageSource.includes("env(safe-area-inset-bottom)"),
  "mobile feature page content padding should include safe-area inset for phone bottom navigation"
)

assert.ok(
  featurePageSource.includes(".bottom-nav {\n  position: fixed"),
  "mobile feature bottom navigation should stay fixed to the phone viewport on long pages"
)

const warehouseFallbackProfile = getFallbackWorkbenchProfile("WAREHOUSE")
assert.ok(
  !JSON.stringify(warehouseFallbackProfile).includes("扫码确认入库") &&
    warehouseFallbackProfile.flow.some(step => step.text === "供应商到货后确认入库"),
  "warehouse workbench should not promise camera scanning when the native shell has no scanner capability"
)

assert.ok(
  workbenchShellSource.includes("@media (max-width: 380px)") &&
    workbenchShellSource.includes(".notify-button") &&
    workbenchShellSource.includes("width: 48px") &&
    workbenchShellSource.includes(".title-row > div"),
  "mobile workbench header should shrink controls on narrow phones instead of pushing the notify button off-screen"
)

assert.ok(
  workbenchShellSource.includes("@media (max-width: 380px), (max-height: 700px)") &&
    workbenchShellSource.includes(".hero-card") &&
    workbenchShellSource.includes(".metric-cell") &&
    workbenchShellSource.includes(".bottom-nav"),
  "mobile workbench should compact the first viewport on narrow or short phones so fixed bottom navigation does not cover key cards"
)
