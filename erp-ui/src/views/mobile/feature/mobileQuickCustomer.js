function trimmed(value) {
  return value === undefined || value === null ? "" : String(value).trim()
}

const { mobileErrorMessage } = require("../mobileErrorMessage")

function createCustomerDraft(keyword) {
  return {
    customerName: trimmed(keyword),
    contactPerson: "",
    contactPhone: ""
  }
}

function normalizeQuickCustomerPayload(draft) {
  const value = draft || {}
  const customerName = trimmed(value.customerName)
  if (!customerName) {
    throw new Error("客户名称不能为空")
  }
  return {
    customerName,
    contactPerson: trimmed(value.contactPerson),
    contactPhone: trimmed(value.contactPhone)
  }
}

function mapCreatedCustomerOption(customer) {
  const row = customer || {}
  if (row.customerId === undefined || row.customerId === null || trimmed(row.customerId) === "") {
    throw new Error("创建成功但未返回客户编号，请重新加载客户列表")
  }
  const customerName = trimmed(row.customerName) || "新客户"
  const meta = [row.customerCode, row.contactPerson, row.contactPhone]
    .map(trimmed)
    .filter(Boolean)
    .join(" · ")
  return {
    value: row.customerId,
    label: customerName,
    meta,
    row
  }
}

function contextValue(source, primaryKey, legacyKeys) {
  const valueSource = source || {}
  if (Object.prototype.hasOwnProperty.call(valueSource, primaryKey)) {
    return valueSource[primaryKey]
  }
  for (const key of legacyKeys || []) {
    if (Object.prototype.hasOwnProperty.call(valueSource, key)) return valueSource[key]
  }
  return ""
}

function normalizeCustomerServiceCardContext(context) {
  const source = context || {}
  const deptId = trimmed(contextValue(source, "selectedDeptId", [
    "deptId", "shopDeptId", "warehouseId"
  ]))
  const hasPrimaryType = Object.prototype.hasOwnProperty.call(source, "selectedDeptType")
  const explicitType = trimmed(contextValue(source, "selectedDeptType", ["deptType"])).toUpperCase()
  const deptType = hasPrimaryType
    ? explicitType
    : (explicitType || (source.isStore ? "STORE" : source.isWarehouse ? "WAREHOUSE" : ""))
  const valid = /^[1-9]\d{0,18}$/.test(deptId) && ["STORE", "WAREHOUSE"].includes(deptType)
  return {
    deptId: valid ? deptId : "",
    deptType: valid ? deptType : "",
    contextKey: valid ? `${deptId}|${deptType}` : "",
    valid
  }
}

function isValidCustomerServiceCardContextKey(contextKey) {
  return /^[1-9]\d{0,18}\|(STORE|WAREHOUSE)$/.test(String(contextKey || ""))
}

function normalizeCustomerServiceCardCapabilities(response, contextKey) {
  const data = response && response.data
  const validContext = isValidCustomerServiceCardContextKey(contextKey)
  const resolved = validContext && !!data && typeof data === "object" &&
    typeof data.writeEnabled === "boolean"
  return {
    contextKey: String(contextKey || ""),
    resolved,
    writeEnabled: resolved && data.writeEnabled === true
  }
}

function isCustomerQuickCreateAllowed(options = {}) {
  const field = options.field || {}
  const capability = options.capability || {}
  return options.entity === "customer" && field.quickCreate === true &&
    options.hasPermission === true && capability.resolved === true &&
    capability.writeEnabled === true &&
    capability.contextKey === String(options.contextKey || "")
}

function isCustomerCapabilityDisabledError(error) {
  const body = error && error.response && error.response.data && typeof error.response.data === "object"
    ? error.response.data
    : error && error.data && typeof error.data === "object" ? error.data : error || {}
  const text = [body.code, body.msg, body.message, error && error.code, error && error.message]
    .filter(value => value !== undefined && value !== null)
    .join(" ")
    .toUpperCase()
  return /FEATURE_DISABLED|FOR_SHOP|WRITE_DISABLED/.test(text)
}

function getQuickCustomerErrorMessage(error, fallback) {
  if (isCustomerCapabilityDisabledError(error)) {
    return "当前门店暂未开放客户新建，请联系管理员"
  }
  return mobileErrorMessage(error, fallback || "操作失败，请重试")
}

module.exports = {
  createCustomerDraft,
  normalizeQuickCustomerPayload,
  mapCreatedCustomerOption,
  normalizeCustomerServiceCardContext,
  isValidCustomerServiceCardContextKey,
  normalizeCustomerServiceCardCapabilities,
  isCustomerQuickCreateAllowed,
  isCustomerCapabilityDisabledError,
  getQuickCustomerErrorMessage
}
