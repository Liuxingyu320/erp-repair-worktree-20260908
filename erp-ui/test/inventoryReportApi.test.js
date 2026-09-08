const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const apiSource = fs.readFileSync(path.resolve(uiRoot, "src/api/inventory/report.js"), "utf8")
const routerSource = fs.readFileSync(path.resolve(uiRoot, "src/router/index.js"), "utf8")
const viewSource = fs.readFileSync(path.resolve(uiRoot, "src/views/inventory/report/index.vue"), "utf8")
const reportRouteStart = routerSource.indexOf("path: '/inventory/report'")
const dynamicRoutesEnd = routerSource.indexOf("\n]\n\n// 防止", reportRouteStart)
const reportRouteSource = routerSource.slice(reportRouteStart, dynamicRoutesEnd)

assert.ok(
  apiSource.includes("export function getReportSummary"),
  "report API should export getReportSummary"
)
assert.ok(
  apiSource.includes("url: '/inventory/report/summary'"),
  "getReportSummary should call /inventory/report/summary"
)
assert.ok(
  apiSource.includes("export function listStockWarning"),
  "report API should export listStockWarning"
)
assert.ok(
  apiSource.includes("url: '/inventory/report/stock-warning'"),
  "listStockWarning should call /inventory/report/stock-warning"
)

assert.ok(
  viewSource.includes("getReportSummary") &&
    viewSource.includes("listStockWarning") &&
    viewSource.includes("库存预警与阈值配置") &&
    viewSource.includes("grossMargin"),
  "report center page should render summary metrics and warning stock rows"
)

assert.ok(
  viewSource.includes("净销售收入") &&
    viewSource.includes("净销售成本") &&
    viewSource.includes("已实现毛利") &&
    viewSource.includes("净销售成本为负，请核对历史退货成本") &&
    viewSource.includes("不同计量单位不再直接相加") &&
    !viewSource.includes("销售金额 - 采购金额"),
  "report center should explain the realized revenue, cost, margin, and unit-safe stock metrics"
)

assert.ok(
  routerSource.includes("inventory/report/index") &&
    routerSource.includes("inv:report:list"),
  "router fallback should expose the inventory report center component and permission"
)

assert.ok(
  reportRouteStart > -1 &&
    dynamicRoutesEnd > reportRouteStart &&
    reportRouteSource.includes("path: ''"),
  "router fallback should render the report center directly at /inventory/report"
)

console.log("inventoryReportApi tests passed")
