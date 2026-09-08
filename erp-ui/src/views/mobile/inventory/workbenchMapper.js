const EMPTY_DISPLAY = {
  title: "",
  selector: "",
  overview: { title: "", subtitle: "" },
  metrics: [],
  priorityItems: [],
  quickActions: [],
  bottomNav: []
}

const {
  resolvePurchaseBusinessStage,
  purchaseBusinessStageLabel
} = require("../../../utils/purchaseBusinessStage")

const METRIC_RULES = [
  {
    label: "今日销售",
    unit: "单",
    icon: "trend",
    tone: "blue",
    summaryKeys: [
      "todaySalesCount",
      "todaySalesOrderCount",
      "salesCount",
      "salesOrderCount"
    ],
    rowsKey: "salesRows"
  },
  {
    label: "采购待办",
    unit: "单",
    icon: "inbound",
    tone: "amber",
    summaryKeys: [
      "purchasePendingCount",
      "pendingInboundCount",
      "pendingReceiveCount",
      "pendingPurchaseCount",
      "waitInboundCount"
    ],
    rowsKey: "purchaseRows"
  },
  {
    label: "低库存",
    unit: "种",
    icon: "alert",
    tone: "red",
    summaryKeys: [
      "warningCount",
      "lowStockCount",
      "lowStockItemCount",
      "zeroStockCount"
    ],
    rowsKey: "lowStockRows"
  },
  {
    label: "待发货",
    unit: "单",
    icon: "truck",
    tone: "teal",
    summaryKeys: [
      "pendingDeliveryCount",
      "pendingDeliverCount",
      "deliveryPendingCount",
      "waitDeliveryCount",
      "pendingShipmentCount"
    ],
    rowsKey: "deliveryRows"
  }
]

const PRIORITY_DEFAULTS = [
  {
    title: "销售待发货",
    tone: "blue",
    imageType: "boxes",
    icon: "document",
    domain: "sales"
  },
  {
    title: "采购待办",
    tone: "amber",
    imageType: "pallet",
    icon: "package",
    domain: "purchase"
  },
  {
    title: "低库存预警",
    tone: "red",
    imageType: "scanner",
    icon: "warning",
    domain: "inventory"
  },
  {
    title: "盘点差异",
    tone: "teal",
    imageType: "shelf",
    icon: "check",
    domain: "warehouse"
  }
]

const DELIVERY_STATUS_LABELS = {
  pending: "待发货",
  delivering: "发货中",
  completed: "已发货",
  cancelled: "已取消",
  submitted: "待发货"
}

const PURCHASE_STATUS_LABELS = {
  draft: "草稿",
  submitted: "待入库",
  received: "已入库",
  cancelled: "已取消",
  pending: "待入库"
}

const LOW_STOCK_STATUS_LABELS = {
  empty: "缺货",
  zero: "缺货",
  low: "待补货",
  warning: "待补货",
  normal: "正常"
}

const STOCK_CHECK_STATUS_LABELS = {
  draft: "待盘点",
  pending_approval: "待审批",
  rejected: "已驳回",
  invalidated: "需重盘",
  completed: "已完成",
  cancelled: "已取消"
}

function mapWorkbenchResponse(raw, fallbackData) {
  const fallback = clonePlainObject(fallbackData || EMPTY_DISPLAY)
  const result = clonePlainObject(fallback)
  const source = isPlainObject(raw) ? raw : {}
  const hasDisplayData = hasRawDisplayData(source)

  result.title = safeText(source.title, fallback.title)
  result.selector = safeText(source.selectedDeptName, fallback.selector)
  result.overview = mapOverview(source, fallback.overview)
  result.metrics = hasDisplayData
    ? mapMetrics(source, fallback.metrics)
    : clonePlainObject(fallback.metrics || [])
  // Actionable work now comes exclusively from the shared Todo Store.
  // Keep the response field for callers that still consume the workbench shape.
  result.priorityItems = []
  result.quickActions = sanitizeActions(fallback.quickActions)
  result.bottomNav = sanitizeBottomNav(fallback.bottomNav)

  return result
}

function mapOverview(source, fallbackOverview) {
  const fallback = isPlainObject(fallbackOverview) ? fallbackOverview : {}

  return {
    title: safeText(source.overviewTitle, fallback.title),
    subtitle: safeText(source.overviewSubtitle, fallback.subtitle)
  }
}

function mapMetrics(source, fallbackMetrics) {
  const summary = isPlainObject(source.stockSummary) ? source.stockSummary : {}
  const totals = isPlainObject(source.totals) ? source.totals : {}

  return METRIC_RULES.map(function(rule, index) {
    const fallbackMetric = findByLabel(fallbackMetrics, rule.label) ||
      clonePlainObject((fallbackMetrics || [])[index] || {})
    const metric = Object.assign({}, fallbackMetric)
    const resolved = firstValue(summary, rule.summaryKeys)
    const totalCount = rule.rowsKey && hasValue(totals[rule.rowsKey])
      ? totals[rule.rowsKey]
      : null
    const rowsCount = rule.rowsKey && Array.isArray(source[rule.rowsKey])
      ? source[rule.rowsKey].length
      : null
    const value = hasValue(resolved)
      ? resolved
      : hasValue(totalCount)
        ? totalCount
        : rowsCount

    metric.label = safeText(metric.label, rule.label)
    metric.value = hasValue(value)
      ? formatCount(value)
      : safeText(metric.value, "0")
    metric.unit = safeText(metric.unit, rule.unit)
    metric.icon = safeText(metric.icon, rule.icon)
    metric.tone = safeText(metric.tone, rule.tone)

    return metric
  })
}

function mapPriorityItems(source, fallbackItems) {
  const groups = [
    { row: firstRow(source.deliveryRows), fallbackIndex: 0, mapper: mapDeliveryPriority },
    { row: firstRow(source.purchaseRows), fallbackIndex: 1, mapper: mapPurchasePriority },
    { row: firstRow(source.lowStockRows), fallbackIndex: 2, mapper: mapLowStockPriority },
    { row: firstRow(source.stockCheckRows), fallbackIndex: 3, mapper: mapStockCheckPriority }
  ]

  return groups.reduce(function(items, group) {
    if (group.row) {
      items.push(group.mapper(group.row, fallbackPriority(fallbackItems, group.fallbackIndex)))
    }
    return items
  }, [])
}

function mapDeliveryPriority(row, fallback) {
  if (!row) return sanitizePriority(fallback, PRIORITY_DEFAULTS[0])

  const details = rowDetails(row)
  const customer = firstText(row, [
    "customerName",
    "targetDeptName",
    "orderTitle",
    "salesOrderNo"
  ])
  const detailCount = details.length > 0
    ? details.length
    : firstValue(row, ["itemCount", "productCount", "detailCount", "totalQuantity"])
  const meta = hasValue(customer) && hasValue(detailCount)
    ? safeText(customer, "") + " · " + formatCount(detailCount) + " 件商品"
    : null

  return sanitizePriority(Object.assign({}, fallback, {
    title: PRIORITY_DEFAULTS[0].title,
    code: safeText(firstValue(row, ["noticeNo", "salesOrderNo", "orderNo", "code"]), fallback.code),
    meta: safeText(meta, fallback.meta),
    status: statusLabel(row.status, DELIVERY_STATUS_LABELS, fallback.status)
  }), PRIORITY_DEFAULTS[0])
}

function mapPurchasePriority(row, fallback) {
  if (!row) return sanitizePriority(fallback, PRIORITY_DEFAULTS[1])

  const supplier = firstText(row, ["supplierName", "orderTitle", "orderNo"])
  const pendingQty = firstValue(row, [
    "remainingQuantity",
    "pendingQuantity",
    "waitInboundQuantity",
    "totalQuantity"
  ])
  const details = rowDetails(row)
  let meta = null

  const stage = resolvePurchaseBusinessStage(row)
  if (stage === "pending_qc" && hasValue(supplier)) {
    meta = safeText(supplier, "") + " · 本次收货待质检"
  } else if (stage === "qc_rejected" && hasValue(supplier)) {
    meta = safeText(supplier, "") + " · 质检拒收，待重新收货"
  } else if (hasValue(supplier) && hasValue(pendingQty)) {
    meta = safeText(supplier, "") + " · 待收货 " + formatQuantity(pendingQty) + " 件"
  } else if (hasValue(supplier) && details.length > 0) {
    meta = safeText(supplier, "") + " · " + formatCount(details.length) + " 件商品"
  }

  return sanitizePriority(Object.assign({}, fallback, {
    title: PRIORITY_DEFAULTS[1].title,
    code: safeText(firstValue(row, ["orderNo", "purchaseNo", "code"]), fallback.code),
    meta: safeText(meta, fallback.meta),
    status: purchaseBusinessStageLabel(row)
  }), PRIORITY_DEFAULTS[1])
}

function mapLowStockPriority(row, fallback) {
  if (!row) return sanitizePriority(fallback, PRIORITY_DEFAULTS[2])

  const productName = firstText(row, ["productName", "skuName", "goodsName"])
  const remain = firstValue(row, [
    "availableQuantity",
    "currentQuantity",
    "remainingQuantity",
    "stockQuantity"
  ])
  const meta = hasValue(productName) && hasValue(remain)
    ? safeText(productName, "") + " · 剩余 " + formatQuantity(remain) + " 件"
    : null
  const code = firstValue(row, ["productCode", "skuCode", "stockCode", "code"])

  return sanitizePriority(Object.assign({}, fallback, {
    title: PRIORITY_DEFAULTS[2].title,
    code: safeText(code, fallback.code),
    meta: safeText(meta, fallback.meta),
    status: statusLabel(row.stockStatus || row.status, LOW_STOCK_STATUS_LABELS, fallback.status)
  }), PRIORITY_DEFAULTS[2])
}

function mapStockCheckPriority(row, fallback) {
  if (!row) return sanitizePriority(fallback, PRIORITY_DEFAULTS[3])

  const place = firstText(row, [
    "warehouseName",
    "warehouseDeptName",
    "shopDeptName",
    "checkDate"
  ])
  const details = rowDetails(row)
  const diffCount = hasValue(row.diffCount)
    ? row.diffCount
    : hasValue(row.differenceCount)
      ? row.differenceCount
      : countDiffDetails(details)
  let meta = null

  if (hasValue(place) && hasValue(diffCount) && Number(diffCount) !== 0) {
    meta = safeText(place, "") + " · " + formatCount(diffCount) + " 个差异"
  } else if (hasValue(place) && details.length > 0) {
    meta = safeText(place, "") + " · " + formatCount(details.length) + " 个货位"
  }

  return sanitizePriority(Object.assign({}, fallback, {
    title: PRIORITY_DEFAULTS[3].title,
    code: safeText(firstValue(row, ["checkNo", "orderNo", "code"]), fallback.code),
    meta: safeText(meta, fallback.meta),
    status: statusLabel(row.status, STOCK_CHECK_STATUS_LABELS, fallback.status)
  }), PRIORITY_DEFAULTS[3])
}

function sanitizePriority(item, defaults) {
  const source = Object.assign({}, defaults || {}, item || {})

  return {
    title: safeText(source.title, defaults && defaults.title),
    code: safeText(source.code, "-"),
    meta: safeText(source.meta, "-"),
    status: safeText(source.status, "-"),
    tone: safeText(source.tone, defaults && defaults.tone),
    imageType: safeText(source.imageType, defaults && defaults.imageType),
    icon: safeText(source.icon, defaults && defaults.icon),
    domain: safeText(source.domain, defaults && defaults.domain)
  }
}

function sanitizeActions(actions) {
  return (Array.isArray(actions) ? actions : []).map(function(action) {
    const sanitized = {
      label: safeText(action && action.label, "-"),
      icon: safeText(action && action.icon, ""),
      tone: safeText(action && action.tone, "")
    }

    if (action && action.path !== undefined) {
      sanitized.path = safeText(action.path, "")
    }

    return sanitized
  })
}

function sanitizeBottomNav(items) {
  return (Array.isArray(items) ? items : []).map(function(item) {
    const navItem = {
      label: safeText(item && item.label, "-"),
      icon: safeText(item && item.icon, "")
    }

    if (item && item.active !== undefined) {
      navItem.active = Boolean(item.active)
    }

    if (item && item.path !== undefined) {
      navItem.path = safeText(item.path, "")
    }

    return navItem
  })
}

function fallbackPriority(items, index) {
  return clonePlainObject((Array.isArray(items) ? items : [])[index] || PRIORITY_DEFAULTS[index])
}

function hasRawDisplayData(source) {
  if (hasObjectValues(source.stockSummary)) return true
  if (hasObjectValues(source.totals)) return true

  return ["salesRows", "lowStockRows", "deliveryRows", "purchaseRows", "stockCheckRows"].some(function(key) {
    return Array.isArray(source[key]) && source[key].length > 0
  })
}

function hasObjectValues(value) {
  if (!isPlainObject(value)) return false

  return Object.keys(value).some(function(key) {
    return hasValue(value[key])
  })
}

function findByLabel(items, label) {
  if (!Array.isArray(items)) return null

  return items.find(function(item) {
    return item && item.label === label
  }) || null
}

function firstRow(rows) {
  if (!Array.isArray(rows)) return null

  return rows.find(function(row) {
    return isPlainObject(row)
  }) || null
}

function rowDetails(row) {
  const details = firstValue(row, ["details", "items", "detailRows", "products"])

  return Array.isArray(details) ? details : []
}

function countDiffDetails(details) {
  if (!Array.isArray(details) || details.length === 0) return null

  return details.filter(function(detail) {
    const diffQty = firstValue(detail || {}, ["diffQty", "differenceQty"])

    return hasValue(diffQty) && Number(diffQty) !== 0
  }).length
}

function firstText(source, keys) {
  const value = firstValue(source, keys)

  if (!hasValue(value) || isPlainObject(value) || Array.isArray(value)) return null

  return String(value).trim()
}

function firstValue(source, keys) {
  if (!isPlainObject(source) || !Array.isArray(keys)) return null

  for (let i = 0; i < keys.length; i += 1) {
    const value = source[keys[i]]

    if (hasValue(value)) return value
  }

  return null
}

function statusLabel(status, labels, fallback) {
  if (!hasValue(status)) return safeText(fallback, "-")

  const key = String(status)

  return safeText(labels[key] || "未知状态", fallback)
}

function formatCount(value) {
  const numberValue = toNumber(value)

  if (numberValue === null) return safeText(value, "0")

  return String(Math.max(0, Math.round(numberValue)))
}

function formatQuantity(value) {
  const numberValue = toNumber(value)

  if (numberValue === null) return safeText(value, "0")

  if (Math.floor(numberValue) === numberValue) return String(numberValue)

  return numberValue.toFixed(2).replace(/\.?0+$/, "")
}

function toNumber(value) {
  if (!hasValue(value)) return null

  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : null
}

function safeText(value, fallback) {
  if (hasValue(value) && !isPlainObject(value) && !Array.isArray(value)) {
    return String(value).trim()
  }

  if (hasValue(fallback) && !isPlainObject(fallback) && !Array.isArray(fallback)) {
    return String(fallback).trim()
  }

  return "-"
}

function hasValue(value) {
  if (value === undefined || value === null) return false

  if (typeof value === "string") {
    const text = value.trim().toLowerCase()

    return text !== "" && text !== "undefined" && text !== "null"
  }

  return true
}

function isPlainObject(value) {
  return Object.prototype.toString.call(value) === "[object Object]"
}

function clonePlainObject(value) {
  if (value === undefined || value === null) return value

  return JSON.parse(JSON.stringify(value))
}

module.exports = {
  mapWorkbenchResponse
}
