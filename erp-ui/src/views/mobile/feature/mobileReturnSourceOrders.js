function createSalesReturnDataFromOrder(order, currentData) {
  const source = order || {}
  const current = currentData || {}
  const orderNo = firstText(source, ["orderNo", "salesOrderNo"])
  const data = {
    salesOrderId: normalizeIdValue(source.orderId || source.salesOrderId),
    salesOrderNo: orderNo,
    customerId: normalizeIdValue(source.customerId || current.customerId),
    customerName: firstText(source, ["customerName"]) || current.customerName || "",
    shopDeptId: normalizeIdValue(current.shopDeptId || source.shopDeptId || source.warehouseId || firstDetailValue(source, "warehouseId")),
    returnTitle: current.returnTitle || (orderNo ? "销售退货-" + orderNo : ""),
    details: normalizeReturnDetails(source.details || source.salesDetails || [], "sales")
  }
  return removeEmptyHeaderValues(data)
}

function createPurchaseReturnDataFromOrder(order, currentData) {
  const source = order || {}
  const current = currentData || {}
  const orderNo = firstText(source, ["orderNo", "purchaseOrderNo"])
  const data = {
    purchaseOrderId: normalizeIdValue(source.orderId || source.purchaseOrderId),
    purchaseOrderNo: orderNo,
    supplierId: normalizeIdValue(source.supplierId || current.supplierId),
    supplierName: firstText(source, ["supplierName"]) || current.supplierName || "",
    shopDeptId: normalizeIdValue(current.shopDeptId || source.shopDeptId || source.warehouseId || firstDetailValue(source, "warehouseId")),
    returnTitle: current.returnTitle || (orderNo ? "采购退货-" + orderNo : ""),
    details: normalizeReturnDetails(source.details || source.purchaseDetails || [], "purchase")
  }
  return removeEmptyHeaderValues(data)
}

function normalizeReturnDetails(details, type) {
  return (Array.isArray(details) ? details : []).map(detail => {
    const historicalReturnedQuantity = normalizeNumber(
      firstValue(detail, ["historicalReturnedQuantity", "returnedQuantity"])
    ) || 0
    const explicitReturnableQuantity = normalizeNumber(
      firstValue(detail, ["returnableQuantity", "maxReturnQuantity"])
    )
    const completedQuantity = normalizeNumber(type === "sales"
      ? firstValue(detail, ["deliveredQuantity"])
      : firstValue(detail, ["receivedQuantity"]))
    const maxReturnQuantity = explicitReturnableQuantity !== undefined
      ? explicitReturnableQuantity
      : Math.max((completedQuantity || 0) - historicalReturnedQuantity, 0)
    if (!maxReturnQuantity || maxReturnQuantity <= 0) return null
    const unitPrice = normalizeNumber(firstValue(detail, ["unitPrice", "price", "costPrice"])) || 0
    const initialQuantity = 0
    const row = {
      productId: normalizeIdValue(detail.productId),
      productName: firstText(detail, ["productName"]),
      sku: firstText(detail, ["sku"]),
      spec: firstText(detail, ["spec"]),
      unit: firstText(detail, ["unit"]),
      quantity: initialQuantity,
      maxReturnQuantity,
      unitPrice,
      price: unitPrice,
      amount: Number((initialQuantity * unitPrice).toFixed(2)),
      returnedQuantity: historicalReturnedQuantity,
      remark: ""
    }
    if (type === "sales") {
      row.salesDetailId = normalizeIdValue(detail.detailId || detail.salesDetailId)
    } else {
      row.itemType = firstText(detail, ["itemType"]) || "product"
      row.itemId = normalizeIdValue(detail.itemId || detail.productId)
      row.itemCode = firstText(detail, ["itemCode", "productCode", "sku"])
      row.itemName = firstText(detail, ["itemName", "productName"])
      row.purchaseDetailId = normalizeIdValue(detail.detailId || detail.purchaseDetailId)
    }
    return removeEmptyDetailValues(row)
  }).filter(Boolean)
}

function removeEmptyHeaderValues(data) {
  const result = {}
  Object.keys(data).forEach(key => {
    const value = data[key]
    if (key === "details" || hasValue(value)) {
      result[key] = value
    }
  })
  return result
}

function removeEmptyDetailValues(data) {
  const result = {}
  Object.keys(data).forEach(key => {
    if (hasValue(data[key]) || ["unitPrice", "price", "amount", "returnedQuantity", "remark"].indexOf(key) > -1) {
      result[key] = data[key]
    }
  })
  return result
}

function firstDetailValue(source, key) {
  const details = Array.isArray(source.details) ? source.details : []
  const row = details.find(item => hasValue(item && item[key]))
  return row ? row[key] : undefined
}

function firstText(source, keys) {
  const value = firstValue(source, keys)
  return hasValue(value) ? String(value).trim() : ""
}

function firstValue(source, keys) {
  for (let i = 0; i < keys.length; i += 1) {
    const value = source && source[keys[i]]
    if (hasValue(value)) return value
  }
  return undefined
}

function normalizeIdValue(value) {
  if (!hasValue(value)) return value
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : value
}

function normalizeNumber(value) {
  if (!hasValue(value)) return undefined
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? Number(numberValue.toFixed(2)) : undefined
}

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

module.exports = {
  createSalesReturnDataFromOrder,
  createPurchaseReturnDataFromOrder,
  normalizeReturnDetails
}
