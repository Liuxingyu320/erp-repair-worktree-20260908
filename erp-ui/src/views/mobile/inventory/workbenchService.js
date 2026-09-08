import { getStockSummary, listStock } from "@/api/inventory/stock"
import { listSales } from "@/api/inventory/sales"
import { listPurchase } from "@/api/inventory/purchase"
import { listDeliveryNotice } from "@/api/inventory/deliveryNotice"
import { listStockCheck } from "@/api/inventory/stockCheck"
import { getMobileWorkbenchSummary } from "@/api/inventory/mobile"

const normalizeRows = (response) => {
  if (!response) {
    return []
  }
  if (Array.isArray(response.rows)) {
    return response.rows
  }
  if (Array.isArray(response.data)) {
    return response.data
  }
  return []
}

const normalizeData = (response) => {
  return response && response.data && typeof response.data === "object" && !Array.isArray(response.data)
    ? response.data
    : {}
}

const normalizeTotal = (response) => {
  const total = response && response.total
  return Number.isFinite(Number(total)) ? Number(total) : normalizeRows(response).length
}

const settle = (key, promise, normalize) => {
  return promise
    .then(response => ({ key, ok: true, value: normalize(response), total: normalizeTotal(response) }))
    .catch(error => ({ key, ok: false, value: normalize(), total: 0, error }))
}

const createShopQuery = (selectedDeptId) => {
  if (!selectedDeptId) {
    return {}
  }
  return {
    shopDeptId: selectedDeptId
  }
}

const createWorkbenchSummaryQuery = (selectedDeptId, selectedDeptType) => {
  if (!selectedDeptId) {
    return {}
  }
  const query = {
    selectedDeptId,
    selectedDeptType
  }
  if (selectedDeptType === "WAREHOUSE") {
    query.warehouseId = selectedDeptId
  } else if (selectedDeptType === "STORE") {
    query.shopDeptId = selectedDeptId
  }
  return query
}

const today = () => {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, "0")
  const day = String(now.getDate()).padStart(2, "0")
  return `${now.getFullYear()}-${month}-${day}`
}

export function fetchMobileInventoryWorkbench(options = {}) {
  const selectedDeptId = typeof options === "object" ? options.selectedDeptId : options
  const selectedDeptType = typeof options === "object" ? options.selectedDeptType : ""
  const shopQuery = createShopQuery(selectedDeptId)
  const pageQuery = { pageNum: 1, pageSize: 5 }
  const todayValue = today()
  const purchaseRowsRequest = selectedDeptType === "WAREHOUSE"
    ? settle("purchaseRows", listPurchase({ ...pageQuery, ...shopQuery, status: "submitted" }), normalizeRows)
    : Promise.resolve({ key: "purchaseRows", ok: true, value: [], total: 0 })

  const requests = [
    settle("workbenchSummary", getMobileWorkbenchSummary(createWorkbenchSummaryQuery(selectedDeptId, selectedDeptType)), normalizeData),
    settle("stockSummary", getStockSummary({ ...shopQuery, stockScope: "" }), normalizeData),
    settle("lowStockRows", listStock({ ...pageQuery, ...shopQuery, stockScope: "warning" }), normalizeRows),
    settle("salesRows", listSales({ ...pageQuery, ...shopQuery, status: "submitted", orderDate: todayValue }), normalizeRows),
    settle("deliveryRows", listDeliveryNotice({ ...pageQuery, ...shopQuery, status: "pending" }), normalizeRows),
    purchaseRowsRequest,
    settle("stockCheckRows", listStockCheck({ ...pageQuery, ...shopQuery, status: "pending_approval" }), normalizeRows)
  ]

  return Promise.all(requests).then(results => {
    const failed = results.filter(item => !item.ok)
    if (failed.length === results.length) {
      const error = new Error("库存服务暂不可用")
      error.details = failed
      throw error
    }

    const raw = results.reduce((acc, item) => {
      acc[item.key] = item.value
      acc.totals[item.key] = item.total
      if (!item.ok) {
        acc.errors.push({ key: item.key, error: item.error })
      }
      return acc
    }, {
      selectedDeptId,
      selectedDeptName: options.selectedDeptName || "",
      totals: {},
      errors: []
    })
    return applyBackendSummary(raw)
  })
}

function applyBackendSummary(raw) {
  const source = raw || {}
  const summary = normalizeData({ data: source.workbenchSummary })
  if (!Object.keys(summary).length) return source

  source.stockSummary = Object.assign({}, source.stockSummary || {}, {
    lowStockCount: summary.lowStockCount,
    pendingReceiveCount: summary.pendingReceiveCount,
    pendingDeliverCount: summary.pendingDeliverCount
  })
  source.totals = Object.assign({}, source.totals || {}, {
    purchaseRows: summary.purchasePendingCount === undefined
      ? summary.pendingReceiveCount
      : summary.purchasePendingCount,
    deliveryRows: summary.pendingDeliverCount,
    lowStockRows: summary.lowStockCount
  })
  source.backendSummary = summary
  return source
}

export { applyBackendSummary }
