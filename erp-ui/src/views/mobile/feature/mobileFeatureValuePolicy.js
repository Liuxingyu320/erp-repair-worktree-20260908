function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

function safeText(value, fallback) {
  if (!hasValue(value)) return hasValue(fallback) ? String(fallback) : ""
  return String(value).trim()
}

function firstValue(source, keys) {
  if (!source || typeof source !== "object") return null

  for (let i = 0; i < keys.length; i += 1) {
    const value = source[keys[i]]
    if (hasValue(value)) return value
  }

  return null
}

function firstText(source, keys) {
  const value = firstValue(source, keys)
  return hasValue(value) ? String(value).trim() : ""
}

function firstNonZeroValue(source, keys) {
  if (!source || typeof source !== "object") return null

  let zeroValue = null
  for (let i = 0; i < keys.length; i += 1) {
    const value = source[keys[i]]
    if (!hasValue(value)) continue

    if (zeroValue === null) {
      zeroValue = value
    }

    const numberValue = Number(value)
    if (!Number.isFinite(numberValue) || numberValue !== 0) {
      return value
    }
  }

  return zeroValue
}

function joinDetail(values) {
  const parts = values
    .map(function(value) {
      return hasValue(value) ? String(value).trim() : ""
    })
    .filter(Boolean)

  return parts.length ? parts.join(" · ") : "-"
}

function toNumber(value) {
  if (!hasValue(value)) return null
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function formatMoneyText(value) {
  if (!hasValue(value)) return ""
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) return String(value)
  return "¥" + numberValue.toFixed(2)
}

function formatQuantity(value) {
  const numberValue = toNumber(value)
  if (numberValue === null) return "0"
  if (Number.isInteger(numberValue)) return String(numberValue)
  return numberValue.toFixed(2).replace(/\.?0+$/, "")
}

function formatSignedQuantity(value) {
  const numberValue = toNumber(value)
  if (numberValue === null) return safeText(value, "")
  const text = formatQuantity(numberValue)
  return numberValue > 0 ? "+" + text : text
}

function unitText(unit) {
  return unit ? " " + unit : ""
}

function formatOptionalQuantity(value, unit) {
  if (!hasValue(value)) return ""
  return formatQuantity(value) + unitText(unit)
}

function formatDateText(value) {
  const text = safeText(value, "")
  if (!text) return ""
  const plainDate = text.match(/^(\d{4}-\d{2}-\d{2})(?:\s|$)/)
  if (plainDate) return plainDate[1]
  const parsed = new Date(text)
  if (Number.isNaN(parsed.getTime())) return text
  const year = parsed.getFullYear()
  const month = String(parsed.getMonth() + 1).padStart(2, "0")
  const day = String(parsed.getDate()).padStart(2, "0")
  return year + "-" + month + "-" + day
}

function formatDateTimeText(value) {
  const text = safeText(value, "")
  if (!text) return ""
  const plainDateTime = text.match(/^(\d{4}-\d{2}-\d{2})(?:[ T](\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?)?$/)
  if (plainDateTime) {
    return plainDateTime[2]
      ? plainDateTime[1] + " " + plainDateTime[2] + ":" + plainDateTime[3]
      : plainDateTime[1]
  }
  const parsed = new Date(text)
  if (Number.isNaN(parsed.getTime())) return text
  const year = parsed.getFullYear()
  const month = String(parsed.getMonth() + 1).padStart(2, "0")
  const day = String(parsed.getDate()).padStart(2, "0")
  const hours = String(parsed.getHours()).padStart(2, "0")
  const minutes = String(parsed.getMinutes()).padStart(2, "0")
  return year + "-" + month + "-" + day + " " + hours + ":" + minutes
}

function formatTimeText(value) {
  const text = safeText(value, "")
  if (!text) return ""
  const timeOnly = text.match(/(?:^|\s|T)(\d{2}):(\d{2})(?::\d{2})?/)
  if (timeOnly && text.indexOf("T") === -1) return timeOnly[1] + ":" + timeOnly[2]
  const parsed = new Date(text)
  if (Number.isNaN(parsed.getTime())) {
    return timeOnly ? timeOnly[1] + ":" + timeOnly[2] : text
  }
  return String(parsed.getHours()).padStart(2, "0") + ":" + String(parsed.getMinutes()).padStart(2, "0")
}

function sumValues(source, keys) {
  const values = keys
    .map(function(key) {
      return toNumber(source && source[key])
    })
    .filter(function(value) {
      return value !== null
    })

  if (!values.length) return ""
  return values.reduce(function(total, value) {
    return total + value
  }, 0)
}

function normalizeContextType(options) {
  return options && options.selectedDeptType ? String(options.selectedDeptType).trim().toUpperCase() : ""
}

function isStoreContext(options) {
  return normalizeContextType(options) === "STORE"
}

function hasMapperPermission(options, permission) {
  const permissions = options && Array.isArray(options.permissions) ? options.permissions : []
  return permissions.indexOf("*:*:*") > -1 || permissions.indexOf(permission) > -1
}

function canViewCost(options) {
  return hasMapperPermission(options, "inv:cost:view")
}

function resolveProductReferenceCost(row) {
  return firstNonZeroValue(row, ["costPrice", "referenceCostPrice"])
}

function formatProductReferenceCostText(row) {
  const referenceCost = resolveProductReferenceCost(row)
  const moneyText = formatMoneyText(referenceCost)
  return moneyText ? "参考成本价 " + moneyText : ""
}

function resolveStockReferenceCost(row) {
  return firstValue(row, ["costPrice", "referenceCostPrice"])
}

function formatStockReferenceCostText(row) {
  const moneyText = formatMoneyText(resolveStockReferenceCost(row))
  return moneyText ? "参考成本价 " + moneyText : ""
}

function stockLocationText(row) {
  const parts = [
    firstText(row, ["locationCode"]),
    firstText(row, ["locationName"])
  ].filter(Boolean)
  return parts.join(" / ")
}

module.exports = {
  canViewCost,
  firstNonZeroValue,
  firstText,
  firstValue,
  formatDateText,
  formatDateTimeText,
  formatMoneyText,
  formatOptionalQuantity,
  formatProductReferenceCostText,
  formatQuantity,
  formatSignedQuantity,
  formatStockReferenceCostText,
  formatTimeText,
  hasMapperPermission,
  hasValue,
  isStoreContext,
  joinDetail,
  normalizeContextType,
  resolveProductReferenceCost,
  resolveStockReferenceCost,
  safeText,
  stockLocationText,
  sumValues,
  toNumber,
  unitText
}
