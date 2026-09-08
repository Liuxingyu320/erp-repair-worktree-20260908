const FOCUS_CONFIG = Object.freeze({
  INV_PURCHASE_QC: config("purchase", "orderId", ["orderId", "purchaseId", "id"], "qualityCheckPurchase"),
  INV_PURCHASE_RECEIVE: config("purchase", "orderId", ["orderId", "purchaseId", "id"], "receivePurchaseAll"),
  INV_SALES_NOTICE_CREATE: config("sales", "orderId", ["orderId", "salesOrderId", "id"], "createDeliveryNotice"),
  INV_DELIVERY_EXECUTE: config("outbound", "noticeId", ["noticeId", "deliveryNoticeId", "id"], "deliverDeliveryNoticeAll"),
  INV_TRANSFER_APPROVAL: config(["transferApproval", "transfer"], "transferId", ["transferId", "id"], "approveTransfer"),
  INV_TRANSFER_SOURCE_CONFIRM: config("transfer", "transferId", ["transferId", "id"], "confirmTransferSource"),
  INV_TRANSFER_DELIVER: config("transfer", "transferId", ["transferId", "id"], "deliverTransferAll"),
  INV_TRANSFER_RECEIVE: config("transfer", "transferId", ["transferId", "id"], "receiveTransferShipment"),
  INV_TRANSFER_DISCREPANCY: config("transfer", "transferId", ["transferId", "id"], "handleTransferDiscrepancy"),
  INV_TRANSFER_RETURNED: config(
    "transfer",
    "transferId",
    ["transferId", "id"],
    "editReplenishment",
    ["editTransfer"]
  ),
  INV_TRANSFER_SOURCE_RESELECT: config("transfer", "transferId", ["transferId", "id"], "editTransfer"),
  INV_STOCK_CHECK_APPROVAL: config("stockCheck", "checkId", ["checkId", "stockCheckId", "id"], "approveStockCheck"),
  INV_STOCK_CHECK_EXECUTE: config("stockCheck", "checkId", ["checkId", "stockCheckId", "id"], "saveStockCheckInput"),
  INV_STOCK_CHECK_RETURNED: config("stockCheck", "checkId", ["checkId", "stockCheckId", "id"], "saveStockCheckInput"),
  INV_STOCK_CHECK_RESTART: config("stockCheck", "checkId", ["checkId", "stockCheckId", "id"], "restartStockCheck"),
  INV_PURCHASE_RETURN_CONFIRM: config("purchaseReturn", "returnId", ["returnId", "purchaseReturnId", "id"], "confirmPurchaseReturn"),
  INV_SALES_RETURN_CONFIRM: config("salesReturn", "returnId", ["returnId", "salesReturnId", "id"], "confirmSalesReturn"),
  INV_OUT_OF_STOCK: riskConfig("outOfStock", "empty"),
  INV_LOW_STOCK: riskConfig("lowStock", "low"),
  OA_PURCHASE_APPROVAL: {
    featureKey: "oaPurchase",
    featureKeys: ["oaPurchase"],
    queryIdKey: "purchaseId",
    rowIdKeys: ["purchaseId", "id"],
    actionId: "viewOaPurchaseApproval",
    mode: "record"
  },
  OA_REIMBURSEMENT_APPROVAL: {
    featureKey: "reimbursement",
    featureKeys: ["reimbursement"],
    queryIdKey: "reimbursementId",
    rowIdKeys: ["reimbursementId", "id"],
    actionId: "viewReimbursementApproval",
    mode: "record"
  },
  OA_LABOR_CONTRACT_SIGN: config("laborContract", "contractId", ["contractId", "id"], "openLaborContract"),
  OA_SIGN_PACKAGE_SIGN: config("signPackage", "packageId", ["packageId", "id"], "openSignPackage")
})

function config(featureKey, queryIdKey, rowIdKeys, actionId, actionIdAliases) {
  const featureKeys = Array.isArray(featureKey) ? featureKey.slice() : [featureKey]
  return {
    featureKey: featureKeys[0],
    featureKeys,
    queryIdKey,
    rowIdKeys,
    actionId,
    actionIdAliases: Array.isArray(actionIdAliases) ? actionIdAliases.slice() : [],
    mode: "record"
  }
}

function riskConfig(riskFilter, stockStatus) {
  return {
    featureKey: "stock",
    queryIdKey: "shopDeptId",
    rowIdKeys: ["shopDeptId", "deptId"],
    actionId: null,
    mode: "filter",
    riskFilter,
    stockStatus
  }
}

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

function scalar(value) {
  const candidate = Array.isArray(value) ? value[0] : value
  return hasValue(candidate) ? String(candidate).trim() : ""
}

function getTodoFocus(featureKey, query) {
  const source = query || {}
  const todoType = scalar(source.todoType)
  const businessId = scalar(source.businessId)
  const definition = Object.prototype.hasOwnProperty.call(FOCUS_CONFIG, todoType)
    ? FOCUS_CONFIG[todoType]
    : null
  const featureKeys = definition && (definition.featureKeys || [definition.featureKey])
  if (!definition || featureKeys.indexOf(featureKey) === -1 || !businessId) return null

  const targetId = scalar(source[definition.queryIdKey]) || businessId
  return {
    todoType,
    businessId,
    targetId,
    approvalTaskId: scalar(source.approvalTaskId),
    approvalInstanceId: scalar(source.approvalInstanceId),
    featureKey,
    queryIdKey: definition.queryIdKey,
    rowIdKeys: definition.rowIdKeys.slice(),
    actionId: definition.actionIds && definition.actionIds[featureKey] !== undefined
      ? definition.actionIds[featureKey]
      : definition.actionId,
    actionIdAliases: (definition.actionIdAliases || []).slice(),
    mode: definition.mode,
    riskFilter: definition.riskFilter || "",
    stockStatus: definition.stockStatus || "",
    shipmentId: todoType === "INV_TRANSFER_RECEIVE"
      ? (scalar(source.shipmentId) || businessId)
      : ""
  }
}

function getTodoFocusActionIds(focus) {
  if (!focus) return []
  return [focus.actionId].concat(focus.actionIdAliases || []).filter((actionId, index, actionIds) =>
    hasValue(actionId) && actionIds.indexOf(actionId) === index
  )
}

function buildTodoFocusQuery(featureKey, query) {
  const requestQuery = consumeTodoFocusQuery(query)
  const focus = getTodoFocus(featureKey, query)
  if (!focus) return requestQuery

  requestQuery.businessId = focus.businessId
  requestQuery[focus.queryIdKey] = focus.targetId
  if (focus.riskFilter) requestQuery.riskFilter = focus.riskFilter
  if (focus.stockStatus) requestQuery.stockStatus = focus.stockStatus
  return requestQuery
}

function consumeTodoFocusQuery(query) {
  const nextQuery = Object.assign({}, query || {})
  delete nextQuery.todoType
  return nextQuery
}

function findTodoFocusRow(rows, focus) {
  if (!focus || focus.mode !== "record") return null
  const targetId = String(focus.targetId)
  return (Array.isArray(rows) ? rows : []).find(row => {
    const raw = row && (row._raw || row.raw || row)
    return focus.rowIdKeys.some(key => hasValue(raw && raw[key]) && String(raw[key]) === targetId)
  }) || null
}

function normalizeTodoFocusRow(row, focus) {
  if (!row || typeof row !== "object" || !focus || focus.mode !== "record") return row
  const idKey = focus.rowIdKeys && focus.rowIdKeys[0]
  if (!idKey) return row
  const approvalContext = {}
  if (focus.approvalTaskId) approvalContext.approvalTaskId = focus.approvalTaskId
  if (focus.approvalInstanceId) approvalContext.approvalInstanceId = focus.approvalInstanceId
  const normalized = Object.assign({}, row, approvalContext, { [idKey]: focus.targetId })
  if (row._raw && typeof row._raw === "object") {
    normalized._raw = Object.assign({}, row._raw, approvalContext, { [idKey]: focus.targetId })
  }
  if (row.raw && typeof row.raw === "object") {
    normalized.raw = Object.assign({}, row.raw, approvalContext, { [idKey]: focus.targetId })
  }
  return normalized
}

function findTodoFocusShipment(detail, focus) {
  if (!focus || !focus.shipmentId) return null
  const shipments = Array.isArray(detail && detail.shipments) ? detail.shipments : []
  return shipments.find(item =>
    item && String(item.status || "") === "pending_receive" &&
    String(item.shipmentId) === String(focus.shipmentId)
  ) || null
}

function resolveTodoFocusResult(rows, focus) {
  if (!focus) return { status: "none", row: null, actionId: null }
  if (focus.mode === "filter") {
    return {
      status: Array.isArray(rows) && rows.length > 0 ? "filtered" : "handled",
      row: null,
      actionId: null
    }
  }
  const row = findTodoFocusRow(rows, focus)
  return {
    status: row ? "found" : "handled",
    row,
    actionId: focus.actionId
  }
}

module.exports = {
  FOCUS_CONFIG,
  buildTodoFocusQuery,
  consumeTodoFocusQuery,
  findTodoFocusShipment,
  findTodoFocusRow,
  getTodoFocus,
  getTodoFocusActionIds,
  normalizeTodoFocusRow,
  resolveTodoFocusResult
}
