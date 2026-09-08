const TRANSFER_RECIPIENT_FIELDS = ["recipientName", "recipientPhone", "shippingAddress"]

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

function firstValue(source, keys) {
  for (let index = 0; index < keys.length; index += 1) {
    const value = source && source[keys[index]]
    if (hasValue(value)) return value
  }
  return undefined
}

function normalizeItemType(value) {
  const itemType = hasValue(value) ? String(value).trim().toLowerCase() : "product"
  return ["product", "gift"].indexOf(itemType) > -1 ? itemType : "product"
}

function transferDetailKey(source) {
  const itemType = normalizeItemType(source && source.itemType)
  const itemId = firstValue(source, ["itemId", "productId", "giftId"])
  return hasValue(itemId) ? itemType + ":" + String(itemId) : ""
}

function numberValue(value) {
  const result = Number(value)
  return Number.isFinite(result) ? result : 0
}

function smartPasteCandidateToDetail(candidate, quantity) {
  const source = candidate || {}
  const itemType = normalizeItemType(source.itemType)
  const itemId = firstValue(source, ["itemId", "productId", "giftId"])
  const itemName = firstValue(source, ["itemName", "productName", "giftName"])
  const itemCode = firstValue(source, ["itemCode", "productCode", "giftCode"])
  const detail = {
    itemType,
    itemId,
    itemName: itemName || "",
    itemCode: itemCode || "",
    quantity: numberValue(quantity),
    availableQuantity: hasValue(source.availableQuantity) ? source.availableQuantity : 0,
    availabilityStatus: source.availabilityStatus || "loaded",
    unit: source.itemUnit || source.unit || "",
    spec: source.itemSpec || source.spec || "",
    grade: source.itemGrade || source.grade || "",
    remark: ""
  }
  if (source.costPrice !== undefined && source.costPrice !== null && source.costPrice !== "") {
    detail.costPrice = source.costPrice
  }
  return detail
}

function mergeMobileTransferSmartPasteDetails(currentDetails, items) {
  const details = (Array.isArray(currentDetails) ? currentDetails : []).map(row => Object.assign({}, row))
  const indexByKey = details.reduce((map, row, index) => {
    const key = transferDetailKey(row)
    if (key && !map.has(key)) map.set(key, index)
    return map
  }, new Map())
  let addedCount = 0
  let mergedCount = 0

  ;(Array.isArray(items) ? items : []).forEach(item => {
    const candidate = item && item.candidate
    const key = transferDetailKey(candidate)
    if (!key) return
    const existingIndex = indexByKey.get(key)
    if (existingIndex !== undefined) {
      const existing = details[existingIndex]
      const archiveUnit = firstValue(candidate, ["itemUnit", "unit"])
      details.splice(existingIndex, 1, Object.assign({}, existing, {
        quantity: numberValue(existing.quantity) + numberValue(item.quantity),
        unit: archiveUnit || existing.unit || ""
      }))
      mergedCount += 1
      return
    }

    const detail = smartPasteCandidateToDetail(candidate, item.quantity)
    indexByKey.set(key, details.length)
    details.push(detail)
    addedCount += 1
  })

  return { details, addedCount, mergedCount }
}

function applyMobileTransferSmartPasteRecipient(formData, recipient) {
  const result = Object.assign({}, formData || {})
  TRANSFER_RECIPIENT_FIELDS.forEach(field => {
    if (hasValue(recipient && recipient[field])) result[field] = String(recipient[field]).trim()
  })
  return result
}

module.exports = {
  TRANSFER_RECIPIENT_FIELDS,
  applyMobileTransferSmartPasteRecipient,
  mergeMobileTransferSmartPasteDetails,
  smartPasteCandidateToDetail,
  transferDetailKey
}
