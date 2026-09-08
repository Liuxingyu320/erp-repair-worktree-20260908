function createCustomerServiceRecordForm(customer, now) {
  const source = customer || {}
  const current = now instanceof Date ? now : new Date()
  return {
    serviceDate: formatLocalDateTime(current),
    partySize: 1,
    teaServed: "",
    preferenceSnapshot: trimText(source.teaPreferences),
    cautionSnapshot: trimText(source.cautions),
    serviceNote: "",
    consumptionAmount: null,
    requestKey: createRequestKey("customer-record")
  }
}

function validateCustomerServiceRecord(data) {
  const source = data || {}
  if (!normalizeServiceDate(source.serviceDate)) return "请选择服务日期"

  const partySize = Number(source.partySize)
  if (!Number.isInteger(partySize) || partySize <= 0) return "到店人数必须为大于 0 的整数"

  if (hasValue(source.consumptionAmount)) {
    const consumptionAmount = Number(source.consumptionAmount)
    if (!Number.isFinite(consumptionAmount) || consumptionAmount < 0) return "消费金额不能小于 0"
  }
  return ""
}

function buildCustomerServiceRecordPayload(data, options) {
  const validationError = validateCustomerServiceRecord(data)
  if (validationError) throw new Error(validationError)

  const source = data || {}
  const settings = options || {}
  return {
    serviceDate: normalizeServiceDate(source.serviceDate),
    partySize: Number(source.partySize),
    teaServed: trimText(source.teaServed),
    preferenceSnapshot: trimText(source.preferenceSnapshot),
    cautionSnapshot: trimText(source.cautionSnapshot),
    serviceNote: trimText(source.serviceNote),
    consumptionAmount: hasValue(source.consumptionAmount) ? Number(source.consumptionAmount) : null,
    requestKey: settings.requestKey || source.requestKey || createRequestKey("customer-record"),
    sourceClient: "MOBILE"
  }
}

function normalizeServiceDate(value) {
  const text = trimText(value)
  if (!text) return ""
  const normalized = text.replace("T", " ")
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(normalized)) return normalized + ":00"
  if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(normalized)) return normalized
  return ""
}

function formatLocalDateTime(value) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ""
  const pad = number => String(number).padStart(2, "0")
  return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate()) +
    "T" + pad(date.getHours()) + ":" + pad(date.getMinutes())
}

function createRequestKey(prefix) {
  return prefix + "-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10)
}

function trimText(value) {
  return value === undefined || value === null ? "" : String(value).trim()
}

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

module.exports = {
  createCustomerServiceRecordForm,
  validateCustomerServiceRecord,
  buildCustomerServiceRecordPayload,
  normalizeServiceDate
}
