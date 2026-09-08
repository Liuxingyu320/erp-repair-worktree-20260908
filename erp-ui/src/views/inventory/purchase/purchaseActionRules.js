function normalizeStatus(value) {
  if (value === undefined || value === null) return ""
  return String(value).trim()
}

function hasKnownRemainingQuantity(row) {
  return row && row.remainingQuantity !== undefined && row.remainingQuantity !== null && row.remainingQuantity !== ""
}

function remainingQuantity(row) {
  const quantity = Number(row.remainingQuantity)
  return isNaN(quantity) ? 0 : quantity
}

function receivedQuantity(row) {
  const quantity = Number(row && row.receivedQuantity)
  return isNaN(quantity) ? 0 : quantity
}

function canReceivePurchase(row) {
  const status = normalizeStatus(row && row.status)
  const qcStatus = normalizeStatus(row && row.qcStatus)
  if (status !== "submitted" || qcStatus === "pending") return false
  return !hasKnownRemainingQuantity(row) || remainingQuantity(row) > 0
}

function canQualityCheckPurchase(row) {
  const status = normalizeStatus(row && row.status)
  const qcStatus = normalizeStatus(row && row.qcStatus)
  return status === "submitted" && qcStatus === "pending"
}

function canCancelPurchase(row) {
  const status = normalizeStatus(row && row.status)
  const qcStatus = normalizeStatus(row && row.qcStatus)
  if (status !== "draft" && status !== "submitted") return false
  if (qcStatus !== "") return false
  return receivedQuantity(row) <= 0
}

module.exports = {
  canReceivePurchase,
  canQualityCheckPurchase,
  canCancelPurchase
}
