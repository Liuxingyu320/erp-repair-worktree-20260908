const assert = require("assert")
const {
  applyWorkbenchSummaryToMetrics,
  createWorkbenchSummaryQuery,
  enrichWorkbenchAction,
  enrichWorkbenchNavItem,
  formatWorkbenchDeptLabel,
  getFallbackWorkbenchProfile,
  getWorkbenchActionDetails,
  normalizeWorkbenchContextType,
  resolveWorkbenchTodoItems,
  workbenchCategoryLabel,
  workbenchSourceLabel
} = require("../src/views/mobile/components/mobileWorkbenchPolicy")

assert.strictEqual(normalizeWorkbenchContextType("WAREHOUSE"), "WAREHOUSE")
assert.strictEqual(normalizeWorkbenchContextType("warehouse"), "STORE")
assert.strictEqual(normalizeWorkbenchContextType("COMPANY"), "STORE")

assert.deepStrictEqual(createWorkbenchSummaryQuery(null), {})
assert.deepStrictEqual(createWorkbenchSummaryQuery({ deptId: " " }), {})
assert.deepStrictEqual(
  createWorkbenchSummaryQuery({ deptId: 17, deptType: " store " }),
  {
    selectedDeptId: "17",
    selectedDeptType: "STORE",
    shopDeptId: "17"
  }
)
assert.deepStrictEqual(
  createWorkbenchSummaryQuery({ deptId: "88", deptType: "warehouse" }),
  {
    selectedDeptId: "88",
    selectedDeptType: "WAREHOUSE",
    warehouseId: "88"
  }
)
assert.deepStrictEqual(
  createWorkbenchSummaryQuery({ deptId: 9, deptType: "company" }),
  {
    selectedDeptId: "9",
    selectedDeptType: "COMPANY"
  },
  "unknown organization types should not be projected into a store or warehouse filter"
)

const metrics = [
  { label: "待入库", value: "1", unit: "单" },
  { label: "待发货", value: "2", unit: "单" },
  { label: "低库存", value: "3", unit: "款" },
  { label: "今日开单", value: "4", unit: "单" }
]
const mappedMetrics = applyWorkbenchSummaryToMetrics(metrics, {
  pendingReceiveCount: 7,
  pendingDeliverCount: -3,
  lowStockCount: "not-a-number"
})
assert.deepStrictEqual(
  mappedMetrics.map(item => item.value),
  ["7", "0", "3", "4"],
  "summary metrics should update known finite counts, clamp negatives, and retain unavailable values"
)
assert.notStrictEqual(mappedMetrics[0], metrics[0])
assert.deepStrictEqual(metrics.map(item => item.value), ["1", "2", "3", "4"])

const storeProfile = getFallbackWorkbenchProfile("STORE")
const warehouseProfile = getFallbackWorkbenchProfile("WAREHOUSE")
assert.strictEqual(storeProfile.homePath, "/mobile/store")
assert.strictEqual(warehouseProfile.homePath, "/mobile/warehouse")
assert.ok(
  warehouseProfile.flow.some(step => step.text === "供应商到货后确认入库"),
  "warehouse fallback copy must not promise unsupported camera scanning"
)
assert.notStrictEqual(getFallbackWorkbenchProfile("WAREHOUSE"), warehouseProfile)
assert.notStrictEqual(getFallbackWorkbenchProfile("WAREHOUSE").metrics, warehouseProfile.metrics)

assert.deepStrictEqual(
  getWorkbenchActionDetails("STORE", "客户资料").permissions,
  ["inv:customerCard:list"],
  "customer cards should use their dedicated permission"
)
assert.deepStrictEqual(
  getWorkbenchActionDetails("WAREHOUSE", "调拨记录").permissions,
  ["inv:transfer:records"],
  "transfer records should use their dedicated permission"
)
assert.deepStrictEqual(getWorkbenchActionDetails("STORE", "未知入口"), {})

const enrichedCustomer = enrichWorkbenchAction(
  { label: "客户资料", summary: "查看客户服务卡" },
  "STORE",
  "/mobile/store"
)
assert.deepStrictEqual(enrichedCustomer, {
  icon: "user",
  tone: "teal",
  path: "/mobile/customer",
  summary: "查看客户服务卡",
  permissions: ["inv:customerCard:list"],
  label: "客户资料"
})

const unknownAction = enrichWorkbenchAction(
  { label: "未知入口" },
  "WAREHOUSE",
  "/mobile/warehouse"
)
assert.deepStrictEqual(unknownAction, {
  label: "未知入口",
  icon: "cube",
  tone: "blue",
  path: "/mobile/warehouse",
  summary: "立即处理"
})

const navSource = { label: "库存", query: { tab: "low" } }
const enrichedNav = enrichWorkbenchNavItem(
  navSource,
  "WAREHOUSE",
  [{ label: "库存", icon: "cube", path: "/mobile/stock" }],
  "/mobile/warehouse"
)
assert.deepStrictEqual(enrichedNav, {
  label: "库存",
  icon: "cube",
  path: "/mobile/stock",
  query: { tab: "low" }
})
assert.deepStrictEqual(navSource, { label: "库存", query: { tab: "low" } })

assert.strictEqual(formatWorkbenchDeptLabel(null), "")
assert.strictEqual(formatWorkbenchDeptLabel({ deptName: "西湖店", isStore: true }), "门店：西湖店")
assert.strictEqual(formatWorkbenchDeptLabel({ deptName: "总仓", isWarehouse: true }), "仓库：总仓")
assert.strictEqual(formatWorkbenchDeptLabel({ deptName: "集团" }), "组织：集团")
assert.strictEqual(workbenchSourceLabel("approval"), "统一审批")
assert.strictEqual(workbenchSourceLabel("unknown"), "未知来源")
assert.strictEqual(workbenchCategoryLabel("returned"), "退回修改")
assert.strictEqual(workbenchCategoryLabel("personal"), "个人事项")
assert.strictEqual(workbenchCategoryLabel("unknown"), "待处理")

const todoRows = [
  { todoKey: "1", category: "approval", title: "审批" },
  { todoKey: "2", category: "execution", title: "执行" },
  { todoKey: "3", category: "returned", title: "退回" },
  { todoKey: "4", category: "risk", title: "风险" },
  { todoKey: "5", category: "personal", title: "个人" },
  { todoKey: "6", category: "unsupported", title: "其他" },
  null
]
const resolvedTodos = resolveWorkbenchTodoItems(todoRows)
assert.deepStrictEqual(
  resolvedTodos.map(item => [item.todoKey, item.icon, item.tone]),
  [
    ["1", "check", "teal"],
    ["2", "truck", "blue"],
    ["3", "return", "red"]
  ],
  "workbench preview should retain category order, filter unsupported rows, and stop at three"
)
assert.notStrictEqual(resolvedTodos[0], todoRows[0])
assert.deepStrictEqual(
  resolveWorkbenchTodoItems(todoRows.slice(3), 2)
    .map(item => [item.todoKey, item.icon, item.tone]),
  [
    ["4", "warning", "red"],
    ["5", "user", "violet"]
  ]
)

console.log("mobileWorkbenchPolicy tests passed")
