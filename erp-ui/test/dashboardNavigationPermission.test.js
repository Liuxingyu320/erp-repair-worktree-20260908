const assert = require("assert")
const fs = require("fs")
const path = require("path")
const { visibleDashboardEntries } = require("../src/utils/dashboardNavigation")

const dashboardSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/index.vue"),
  "utf8"
)

const entries = [
  { title: "选择组织", path: "/select-shop", always: true },
  { title: "销售管理", path: "/inventory/sales" },
  { title: "采购管理", path: "/cangku/purchase" },
  { title: "我的待办", path: "/workbench/todo", always: true }
]

assert.deepStrictEqual(
  visibleDashboardEntries(entries, new Set(["/inventory/sales"])).map(entry => entry.title),
  ["选择组织", "销售管理", "我的待办"],
  "dashboard entries must include only explicitly available business routes"
)
assert.deepStrictEqual(
  visibleDashboardEntries(entries, new Set()).map(entry => entry.title),
  ["选择组织", "我的待办"],
  "an empty permission set must not fall back to every business shortcut"
)
assert.ok(
  !dashboardSource.includes("links.length > 1 ? links : this.quickLinks") &&
    dashboardSource.includes("visibleDashboardEntries(cards, this.todoAvailableRouteSet)") &&
    dashboardSource.includes("页面暂时无法打开，请刷新后重试"),
  "desktop dashboard must not leak shortcuts and must surface navigation failures"
)

console.log("dashboard navigation permission tests passed")
