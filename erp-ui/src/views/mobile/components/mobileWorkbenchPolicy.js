const SUMMARY_METRIC_KEYS = {
  "待入库": "pendingReceiveCount",
  "待发货": "pendingDeliverCount",
  "待出库": "pendingDeliverCount",
  "低库存": "lowStockCount"
}

const ACTION_DETAILS = {
  STORE: {
    "销售开单": { icon: "sales", tone: "blue", path: "/mobile/sales", summary: "新建销售", permissions: ["inv:sales:list"] },
    "查库存": { icon: "search", tone: "teal", path: "/mobile/stock", summary: "余量确认", permissions: ["inv:stock:list"] },
    "销售退货": { icon: "return", tone: "red", path: "/mobile/sales-return", summary: "退货登记", permissions: ["inv:salesReturn:list"] },
    "补货申请": { icon: "inbound", tone: "amber", path: "/mobile/replenishment", summary: "门店补货", permissions: ["inv:transfer:list"] },
    "审批管理": { icon: "check", tone: "teal", path: "/mobile/transfer-approval", summary: "调拨审批", permissions: ["inv:transfer:approve"] },
    "商品资料": { icon: "cube", tone: "blue", path: "/mobile/product", summary: "查商品", permissions: ["inv:product:list"] },
    "客户资料": { icon: "user", tone: "teal", path: "/mobile/customer", summary: "查客户", permissions: ["inv:customerCard:list"] }
  },
  WAREHOUSE: {
    "采购入库": { icon: "purchase", tone: "amber", path: "/mobile/purchase", query: { status: "submitted" }, summary: "到货确认", permissions: ["inv:purchase:list"] },
    "发货处理": { icon: "truck", tone: "blue", path: "/mobile/outbound", query: { statusGroup: "deliverable" }, summary: "销售出库", permissions: ["inv:deliveryNotice:list"] },
    "采购退货": { icon: "return", tone: "red", path: "/mobile/purchase-return", summary: "退货出库", permissions: ["inv:purchaseReturn:list"] },
    "库存盘点": { icon: "check", tone: "teal", path: "/mobile/stock-check", summary: "差异复核", permissions: ["inv:stockCheck:list"] },
    "审批管理": { icon: "check", tone: "teal", path: "/mobile/transfer-approval", summary: "调拨审批", permissions: ["inv:transfer:approve"] },
    "调拨处理": { icon: "transfer", tone: "violet", path: "/mobile/transfer", summary: "仓间流转", permissions: ["inv:transfer:list"] },
    "调拨收货": { icon: "transfer", tone: "violet", path: "/mobile/transfer", query: { statusGroup: "receivable", direction: "receive" }, summary: "调拨入库", permissions: ["inv:transfer:list"] },
    "调拨发货": { icon: "transfer", tone: "violet", path: "/mobile/transfer", query: { statusGroup: "deliverable", direction: "deliver" }, summary: "调拨出库", permissions: ["inv:transfer:list"] },
    "销售出库": { icon: "truck", tone: "blue", path: "/mobile/outbound", query: { statusGroup: "deliverable" }, summary: "销售发货", permissions: ["inv:deliveryNotice:list"] },
    "商品资料": { icon: "cube", tone: "blue", path: "/mobile/product", summary: "查商品", permissions: ["inv:product:list"] },
    "供应商": { icon: "warehouse", tone: "amber", path: "/mobile/supplier", summary: "供货档案", permissions: ["inv:supplier:list"] },
    "分类": { icon: "document", tone: "teal", path: "/mobile/category", summary: "分类树", permissions: ["inv:category:list"] },
    "库存流水": { icon: "document", tone: "blue", path: "/mobile/stock-log", summary: "变动记录", permissions: ["inv:stock:log"] },
    "调拨记录": { icon: "transfer", tone: "violet", path: "/mobile/transfer-records", summary: "历史单据", permissions: ["inv:transfer:records"] }
  }
}

const FALLBACK_PROFILES = {
  STORE: {
    title: "店铺工作台",
    tone: "blue",
    homePath: "/mobile/store",
    selectorIcon: "store",
    selectorFallback: "选择店铺",
    contextTitle: "门店业务上下文",
    contextText: "销售开单、退货处理和补货申请会按当前门店过滤",
    contextAction: "切换店铺",
    heroIcon: "store",
    heroTitle: "门店销售优先处理",
    heroSubtitle: "把开单、查库存、退货和补货放在同一个手机入口",
    todoTitle: "门店待办",
    boardTitle: "销售闭环",
    quickActions: [
      { label: "销售开单" },
      { label: "查库存" },
      { label: "销售退货" },
      { label: "补货申请" }
    ],
    bottomNav: [
      { label: "工作台", icon: "home", path: "/mobile/store" },
      { label: "销售", icon: "cart", path: "/mobile/sales" },
      { label: "库存", icon: "cube", path: "/mobile/stock" },
      { label: "退货", icon: "return", path: "/mobile/sales-return" },
      { label: "我的", icon: "user", path: "/mobile/mine" }
    ],
    metrics: [
      { label: "今日开单", value: "0", unit: "单", icon: "sales", tone: "blue" },
      { label: "待退货", value: "0", unit: "单", icon: "return", tone: "red" },
      { label: "低库存", value: "0", unit: "款", icon: "warning", tone: "amber" },
      { label: "补货中", value: "0", unit: "单", icon: "inbound", tone: "teal" }
    ],
    flow: [
      { label: "开单", text: "门店销售单进入待发货或直接完成", tone: "blue" },
      { label: "库存确认", text: "查当前门店可售、低库存和缺货商品", tone: "teal" },
      { label: "退货与补货", text: "退货入库、缺货补货按门店任务追踪", tone: "amber" }
    ]
  },
  WAREHOUSE: {
    title: "仓库工作台",
    tone: "amber",
    homePath: "/mobile/warehouse",
    selectorIcon: "warehouse",
    selectorFallback: "选择仓库",
    contextTitle: "仓库作业上下文",
    contextText: "入库、出库、盘点和调拨任务会按当前仓库过滤",
    contextAction: "切换仓库",
    heroIcon: "warehouse",
    heroTitle: "仓库收发优先处理",
    heroSubtitle: "把采购入库、发货处理、采购退货、盘点和调拨放在同一个手机入口",
    todoTitle: "仓库待办",
    boardTitle: "仓储流转",
    quickActions: [
      { label: "采购入库" },
      { label: "发货处理" },
      { label: "采购退货" },
      { label: "库存盘点" },
      { label: "调拨处理" }
    ],
    bottomNav: [
      { label: "工作台", icon: "home", path: "/mobile/warehouse" },
      { label: "入库", icon: "inbound", path: "/mobile/purchase" },
      { label: "出库", icon: "truck", path: "/mobile/outbound" },
      { label: "库存", icon: "cube", path: "/mobile/stock" },
      { label: "我的", icon: "user", path: "/mobile/mine" }
    ],
    metrics: [
      { label: "待入库", value: "0", unit: "单", icon: "purchase", tone: "amber" },
      { label: "待发货", value: "0", unit: "单", icon: "truck", tone: "blue" },
      { label: "待退货", value: "0", unit: "单", icon: "return", tone: "red" },
      { label: "调拨单", value: "0", unit: "张", icon: "transfer", tone: "violet" }
    ],
    flow: [
      { label: "收货", text: "供应商到货后确认入库", tone: "amber" },
      { label: "发货与退货", text: "销售发货和采购退货都从仓库出库确认", tone: "blue" },
      { label: "盘点调拨", text: "库存差异和仓间调拨统一追踪", tone: "violet" }
    ]
  }
}

const TODO_PRESENTATION = {
  approval: { icon: "check", tone: "teal" },
  execution: { icon: "truck", tone: "blue" },
  returned: { icon: "return", tone: "red" },
  risk: { icon: "warning", tone: "red" },
  personal: { icon: "user", tone: "violet" }
}

function normalizeWorkbenchContextType(contextType) {
  return contextType === "WAREHOUSE" ? "WAREHOUSE" : "STORE"
}

function createWorkbenchSummaryQuery(context) {
  const source = context || {}
  if (source.deptId === undefined || source.deptId === null || String(source.deptId).trim() === "") return {}
  const selectedDeptId = String(source.deptId)
  const selectedDeptType = source.deptType ? String(source.deptType).trim().toUpperCase() : ""
  const query = { selectedDeptId, selectedDeptType }
  if (selectedDeptType === "WAREHOUSE") query.warehouseId = selectedDeptId
  if (selectedDeptType === "STORE") query.shopDeptId = selectedDeptId
  return query
}

function applyWorkbenchSummaryToMetrics(metrics, summary) {
  const source = summary && typeof summary === "object" ? summary : {}
  return (metrics || []).map(metric => {
    const key = SUMMARY_METRIC_KEYS[metric.label]
    const value = key ? Number(source[key]) : NaN
    if (!Number.isFinite(value)) return Object.assign({}, metric)
    return Object.assign({}, metric, { value: String(Math.max(0, value)) })
  })
}

function cloneWorkbenchItem(item) {
  const cloned = Object.assign({}, item || {})
  if (item && item.query) cloned.query = Object.assign({}, item.query)
  if (item && Array.isArray(item.permissions)) cloned.permissions = item.permissions.slice()
  return cloned
}

function getFallbackWorkbenchProfile(contextType) {
  const profile = FALLBACK_PROFILES[normalizeWorkbenchContextType(contextType)]
  return Object.assign({}, profile, {
    quickActions: profile.quickActions.map(cloneWorkbenchItem),
    bottomNav: profile.bottomNav.map(cloneWorkbenchItem),
    metrics: profile.metrics.map(cloneWorkbenchItem),
    flow: profile.flow.map(cloneWorkbenchItem)
  })
}

function getWorkbenchActionDetails(contextType, label) {
  const actions = ACTION_DETAILS[normalizeWorkbenchContextType(contextType)]
  return cloneWorkbenchItem(actions[label])
}

function enrichWorkbenchAction(action, contextType, homePath) {
  const source = action || {}
  const details = getWorkbenchActionDetails(contextType, source.label)
  const path = source.path || details.path || homePath
  return Object.assign({}, details, source, {
    icon: source.icon || details.icon || "cube",
    tone: source.tone || details.tone || "blue",
    path,
    summary: source.summary || details.summary || "立即处理"
  })
}

function enrichWorkbenchNavItem(item, contextType, profileBottomNav, homePath) {
  const source = item || {}
  if (source.icon && source.path) return cloneWorkbenchItem(source)

  const actionDetails = getWorkbenchActionDetails(contextType, source.label)
  const profileItem = (profileBottomNav || []).find(nav => nav.label === source.label) || {}
  const path = source.path || profileItem.path || actionDetails.path || homePath

  return Object.assign({}, profileItem, actionDetails, source, {
    icon: source.icon || profileItem.icon || actionDetails.icon || "cube",
    path
  })
}

function formatWorkbenchDeptLabel(context) {
  if (!context || !context.deptName) return ""
  const prefix = context.isWarehouse ? "仓库" : context.isStore ? "门店" : "组织"
  return prefix + "：" + context.deptName
}

function workbenchSourceLabel(source) {
  return { approval: "统一审批", inventory: "库存", oa: "OA", system: "系统" }[source] || "未知来源"
}

function workbenchCategoryLabel(category) {
  return { approval: "待审批", execution: "待执行", returned: "退回修改", risk: "风险", personal: "个人事项" }[category] || "待处理"
}

function resolveWorkbenchTodoItems(todoRecent, limit = 3) {
  const parsedLimit = Number(limit)
  const resolvedLimit = Number.isFinite(parsedLimit) ? Math.max(0, Math.floor(parsedLimit)) : 3
  return (Array.isArray(todoRecent) ? todoRecent : [])
    .filter(item => item && TODO_PRESENTATION[item.category])
    .slice(0, resolvedLimit)
    .map(item => Object.assign({}, item, TODO_PRESENTATION[item.category]))
}

module.exports = {
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
}
