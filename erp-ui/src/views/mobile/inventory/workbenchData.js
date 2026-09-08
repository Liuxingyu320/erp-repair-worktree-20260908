const PUBLIC_MOBILE_INVENTORY_PATH = "/mobile/inventory"

const workbenchData = {
  businessDomains: ["sales", "purchase", "inventory", "warehouse"],
  browserUrl: "erp.example.com/ops",
  title: "进销存工作台",
  selector: "总仓 A区",
  overview: {
    title: "今日经营",
    subtitle: "采购 · 销售 · 库存协同"
  },
  metrics: [
    { label: "今日销售", value: "86", unit: "单", icon: "trend", tone: "blue" },
    { label: "采购待办", value: "12", unit: "单", icon: "inbound", tone: "amber" },
    { label: "低库存", value: "38", unit: "种", icon: "alert", tone: "red" },
    { label: "待发货", value: "16", unit: "单", icon: "truck", tone: "teal" }
  ],
  priorityItems: [],
  quickActions: [
    { label: "销售开单", icon: "sales", tone: "blue" },
    { label: "采购入库", icon: "purchase", tone: "amber" },
    { label: "查库存", icon: "search", tone: "blue" },
    { label: "盘点", icon: "check", tone: "teal" }
  ],
  bottomNav: [
    { label: "工作台", icon: "home", active: true },
    { label: "销售", icon: "cart" },
    { label: "采购", icon: "bag" },
    { label: "库存", icon: "cube" },
    { label: "我的", icon: "user" }
  ]
}

module.exports = {
  PUBLIC_MOBILE_INVENTORY_PATH,
  workbenchData
}
