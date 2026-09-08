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

function resolveMobileLineItemDefaultPrice(row, itemType, featureKey) {
  const source = row || {}
  const type = String(itemType || source.itemType || "product").trim().toLowerCase()
  const feature = String(featureKey || "").trim()

  if (feature === "purchase" || feature === "purchaseReturn") {
    return firstValue(source, ["purchasePrice", "costPrice"])
  }

  if (feature === "sales" || feature === "salesReturn") {
    return type === "gift"
      ? firstValue(source, ["guidePrice1", "guidePrice2", "salePrice500g", "salesPrice"])
      : firstValue(source, ["salePrice500g", "salesPrice", "guidePrice1", "guidePrice2"])
  }

  if (feature === "transfer" || feature === "replenishment") {
    return firstValue(source, ["costPrice", "purchasePrice"])
  }

  if (type === "oe") return firstValue(source, ["costPrice", "purchasePrice"])
  if (type === "gift") return firstValue(source, ["guidePrice1", "guidePrice2", "salesPrice"])
  return firstValue(source, ["salePrice500g", "salesPrice", "purchasePrice", "costPrice"])
}

module.exports = {
  resolveMobileLineItemDefaultPrice
}
