const { isReturnSelected } = require("../../../utils/returnSelection")

function cloneMobileEditFormSource(item) {
  const source = item && (item._raw || item.raw || item)
  if (!source || typeof source !== "object") return {}
  return cloneMobileFormValue(source)
}

function snapshotMobileFormSheet(formSheet) {
  const source = formSheet || {}
  return {
    open: source.open === true,
    mode: source.mode || "create",
    saving: source.saving === true,
    error: source.error || "",
    validationError: cloneMobileFormValue(source.validationError),
    config: source.config || null,
    data: cloneMobileFormValue(source.data),
    initialData: cloneMobileFormValue(source.initialData)
  }
}

function restoreMobileFormSheet(snapshot) {
  const source = snapshot || {}
  return {
    open: true,
    mode: source.mode || "create",
    saving: false,
    error: source.error || "",
    validationError: cloneMobileFormValue(source.validationError),
    config: source.config || null,
    data: cloneMobileFormValue(source.data),
    initialData: cloneMobileFormValue(source.initialData)
  }
}

function cloneMobileFormValue(value) {
  if (value === undefined || value === null) return value
  try {
    return JSON.parse(JSON.stringify(value))
  } catch (error) {
    if (Array.isArray(value)) return value.map(item => cloneMobileFormValue(item))
    if (typeof value !== "object") return value
    return Object.keys(value).reduce((cloned, key) => {
      cloned[key] = cloneMobileFormValue(value[key])
      return cloned
    }, {})
  }
}

function createMobileFormData(config, item, options) {
  const source = config || {}
  const row = item && (item._raw || item.raw || item) ? (item._raw || item.raw || item) : {}
  const data = {}

  if (source.idKey && row[source.idKey] !== undefined && row[source.idKey] !== null && String(row[source.idKey]).trim() !== "") {
    data[source.idKey] = row[source.idKey]
  }
  ;(source.passthroughFields || []).forEach(key => {
    if (row[key] !== undefined && row[key] !== null) data[key] = row[key]
  })

  ;(source.fields || []).forEach(field => {
    const value = resolveFormFieldValue(field, row, options, source)
    if (field.type === "line-items") {
      if (field.payloadMode === "stock-check-products") {
        data[field.key] = normalizeStockCheckProductItems(value || row.details || [], { includeDisplayMeta: true })
      } else {
        data[field.key] = normalizeLineItems(
          value || row.details || [],
          getLineItemNormalizeOptions(source, field, "form")
        )
      }
    } else if (value !== undefined && value !== null && String(value).trim() !== "") {
      data[field.key] = normalizeFieldValue(field, value)
    } else if (field.defaultValue !== undefined) {
      data[field.key] = field.defaultValue
    } else {
      data[field.key] = ""
    }
  })

  copyFormFallbackLabels(source, data, row)
  const result = applyFormDefaults(source, data, options)
  if (source.fixedTransferType === "cross_store" &&
    String(row.sourceConfirmStatus || "").toUpperCase() === "RESELECT_REQUIRED") {
    result.fromDeptId = ""
    result.fromWarehouseId = ""
  }
  return result
}

function buildMobileFormPayload(config, data, options) {
  const source = data || {}
  const settings = options || {}
  const payload = {}

  if (config.idKey && hasValue(source[config.idKey])) {
    payload[config.idKey] = normalizeIdValue(source[config.idKey])
  }
  ;(config.passthroughFields || []).forEach(key => {
    if (source[key] !== undefined && source[key] !== null) payload[key] = source[key]
  })

  ;(config.fields || []).forEach(field => {
    const value = source[field.key]
    if (field.type === "line-items") {
      if (field.payloadMode === "stock-check-products") {
        const rows = normalizeStockCheckProductItems(value)
        if (rows.length) payload[field.key] = rows
        return
      }
      const selectedValue = field.selectionScoped ? (Array.isArray(value) ? value.filter(isReturnSelected) : []) : value
      const rows = normalizeLineItems(selectedValue, getLineItemNormalizeOptions(config, field, "payload"))
      if (rows.length) payload[field.key] = rows
      return
    }
    if (!hasValue(value)) return
    payload[field.key] = normalizeFieldValue(field, value)
  })

  applyPayloadDefaults(config, payload, source, settings)

  if (settings.submitAction) {
    payload.submitAction = settings.submitAction
  }

  return payload
}

function normalizeLineItems(value, options) {
  const settings = options || {}
  const includePrice = settings.includePrice !== false
  const includeEstimatedPrice = settings.includeEstimatedPrice !== false
  const rows = parseLineItemRows(value).filter(row => isAllowedLineItemType(normalizeLineItemType(row), settings))
  return rows.map((row, index) => {
    const itemType = normalizeLineItemType(row)
    const itemId = normalizeIdValue(firstValue(row, ["itemId", "productId"]))
    const quantity = normalizeNumber(row.quantity)
    const price = firstNormalizedNumber(row, ["unitPrice", "price", "referenceCostPrice"])
    const unitPrice = normalizeNumber(row.unitPrice)
    const estimatedPrice = normalizeNumber(row.estimatedPrice)
    const costPrice = normalizeNumber(row.costPrice)
    const item = settings.legacyProductPayload
      ? { productId: itemId, quantity, remark: hasValue(row.remark) ? String(row.remark).trim() : "" }
      : { itemType, itemId, quantity, remark: hasValue(row.remark) ? String(row.remark).trim() : "" }
    if (includePrice && price !== undefined) {
      if (settings.outputUnitPrice) item.unitPrice = price
      else item.price = price
    }
    if (settings.preserveReturnDetailMeta && unitPrice !== undefined) item.unitPrice = unitPrice
    if (includeEstimatedPrice && estimatedPrice !== undefined) item.estimatedPrice = estimatedPrice
    if (settings.includeCostPrice && costPrice !== undefined) item.costPrice = costPrice
    if (hasValue(row.reason)) item.reason = String(row.reason).trim()
    copyLineItemMaterialMeta(item, row, settings)
    copyReturnDetailMeta(item, row, settings)
    copyTransferDetailMeta(item, row, settings)
    if (settings.preserveSalesWarehouse && hasValue(row.warehouseId)) item.warehouseId = normalizeIdValue(row.warehouseId)
    if (settings.preserveSalesWarehouse && settings.includeDisplayMeta && hasValue(row.warehouseName)) item.warehouseName = row.warehouseName
    if (settings.addSortOrder) {
      item.sortOrder = hasValue(row.sortOrder) ? normalizeIdValue(row.sortOrder) : index
    }
    const amountPrice = includePrice && price !== undefined
      ? price
      : (unitPrice !== undefined ? unitPrice : (includeEstimatedPrice && estimatedPrice !== undefined ? estimatedPrice : (settings.includeCostPrice ? costPrice : undefined)))
    if (quantity !== undefined && amountPrice !== undefined) {
      item.amount = Number((quantity * amountPrice).toFixed(2))
    }
    return item
  }).filter(item => item && hasValue(settings.legacyProductPayload ? item.productId : item.itemId) && Number(item.quantity) > 0)
}

function normalizeStockCheckProductItems(value, options) {
  const settings = options || {}
  const seen = {}
  return parseLineItemRows(value).reduce((items, row) => {
    const itemType = row.itemType || "product"
    const productId = normalizeIdValue(row.productId)
    const itemId = normalizeIdValue(row.itemId == null ? productId : row.itemId)
    if (!["product", "oe", "gift"].includes(itemType) || !hasValue(itemId)) return items
    const key = itemType + ":" + itemId
    if (seen[key]) return items
    seen[key] = true
    const item = itemType === "product" ? { productId: itemId } : { itemType, itemId }
    if (settings.includeDisplayMeta) {
      ;[
        "productName",
        "productCode",
        "unit",
        "spec",
        "grade",
        "availableQuantity",
        "currentQuantity",
        "costPrice",
        "availabilityStatus"
      ].forEach(metaKey => {
        if (hasValue(row[metaKey])) item[metaKey] = row[metaKey]
      })
    }
    items.push(item)
    return items
  }, [])
}

function parseLineItemRows(value) {
  if (Array.isArray(value)) return value
  const text = String(value || "").trim()
  if (!text) return []
  if (text.charAt(0) === "[") {
    try {
      const parsed = JSON.parse(text)
      return Array.isArray(parsed) ? parsed : []
    } catch (error) {
      return []
    }
  }
  return text.split(/\n+/).map(line => {
    const parts = line.split(",").map(part => part.trim())
    return {
      productId: parts[0],
      quantity: parts[1],
      price: parts[2],
      remark: parts[3] || ""
    }
  })
}

function normalizeFieldValue(field, value) {
  if (field.type === "number") return Number(value)
  if (field.type === "entity-picker" || field.type === "context-dept") return normalizeIdValue(value)
  if (field.type === "datetime-local") {
    const normalized = String(value || "").trim().replace("T", " ")
    return normalized.length === 16 ? normalized + ":00" : normalized
  }
  return value
}

function getLineItemNormalizeOptions(config, field, mode) {
  const itemFields = Array.isArray(field && field.itemFields) ? field.itemFields : []
  const itemKeys = itemFields.reduce((keys, itemField) => {
    if (itemField && itemField.key) keys[itemField.key] = true
    return keys
  }, {})
  const hasExplicitItemFields = itemFields.length > 0
  const replenishment = isReplenishmentConfig(config)
  const returnForm = isSalesReturnConfig(config) || isPurchaseReturnConfig(config)

  return {
    includePrice: returnForm || !hasExplicitItemFields || !!itemKeys.price,
    includeEstimatedPrice: !hasExplicitItemFields || !!itemKeys.estimatedPrice,
    includeCostPrice: replenishment || !!itemKeys.costPrice,
    preserveProductMeta: replenishment || returnForm || !!(itemKeys.itemName || itemKeys.itemCode || itemKeys.productName || itemKeys.productCode || itemKeys.unit || itemKeys.spec || itemKeys.grade),
    includeDisplayMeta: mode === "form",
    addSortOrder: isTransferConfig(config),
    preserveReturnDetailMeta: returnForm,
    preserveLegacyReturnAlias: isSalesReturnConfig(config),
    preserveTransferDetailMeta: isTransferConfig(config),
    preserveSalesWarehouse: isSalesConfig(config),
    outputUnitPrice: mode === "payload" && (isSalesConfig(config) || isPurchaseConfig(config) || returnForm),
    legacyProductPayload: false,
    allowedItemTypes: Array.isArray(field && field.allowedItemTypes) ? field.allowedItemTypes : null
  }
}

function copyLineItemMaterialMeta(item, row, settings) {
  const itemCode = firstValue(row, ["itemCode", "productCode", "skuCode", "sku"])
  const itemName = firstValue(row, ["itemName", "productName", "skuName"])
  if (settings.legacyProductPayload) {
    if (settings.preserveProductMeta && hasValue(itemName)) item.productName = String(itemName).trim()
    if (settings.preserveProductMeta && hasValue(itemCode)) item.productCode = String(itemCode).trim()
  } else {
    if (hasValue(itemCode)) item.itemCode = String(itemCode).trim()
    if (hasValue(itemName)) item.itemName = String(itemName).trim()
  }

  if (settings.preserveLegacyReturnAlias) {
    if (hasValue(itemName)) item.productName = String(itemName).trim()
    if (hasValue(row.productId) && normalizeLineItemType(row) === "product") item.productId = normalizeIdValue(row.productId)
  }
  if (settings.preserveProductMeta) {
    ;["unit", "spec", "grade"].forEach(key => {
      if (hasValue(row[key])) item[key] = String(row[key]).trim()
    })
  }

  if (settings.includeDisplayMeta) {
    ;["availableQuantity", "availabilityStatus", "costPrice"].forEach(key => {
      if (hasValue(row[key])) item[key] = row[key]
    })
  }
}

function normalizeLineItemType(row) {
  return hasValue(row && row.itemType) ? String(row.itemType).trim().toLowerCase() : "product"
}

function isAllowedLineItemType(itemType, settings) {
  const supportedTypes = ["product", "oe", "gift"]
  if (supportedTypes.indexOf(itemType) === -1) return false
  const allowedTypes = Array.isArray(settings && settings.allowedItemTypes)
    ? settings.allowedItemTypes.map(type => String(type).trim().toLowerCase())
    : supportedTypes
  return allowedTypes.indexOf(itemType) > -1
}

function firstValue(source, keys) {
  for (let i = 0; i < keys.length; i += 1) {
    const value = source && source[keys[i]]
    if (hasValue(value)) return value
  }
  return undefined
}

function firstNormalizedNumber(source, keys) {
  return normalizeNumber(firstValue(source, keys))
}

function copyReturnDetailMeta(item, row, settings) {
  if (!settings.preserveReturnDetailMeta) return
  ;["salesDetailId", "purchaseDetailId"].forEach(key => {
    if (hasValue(row[key])) item[key] = normalizeIdValue(row[key])
  })
  ;["maxReturnQuantity", "returnedQuantity"].forEach(key => {
    const value = normalizeNumber(row[key])
    if (value !== undefined) item[key] = value
  })
}

function copyTransferDetailMeta(item, row, settings) {
  if (!settings.preserveTransferDetailMeta) return
  ;["detailId", "transferId", "lotId", "sourceLocationId"].forEach(key => {
    if (hasValue(row[key])) item[key] = normalizeIdValue(row[key])
  })
  ;["goodsCondition", "conditionNote"].forEach(key => {
    if (hasValue(row[key])) item[key] = String(row[key]).trim()
  })
}

function normalizeIdValue(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) && String(value).trim() !== "" ? numberValue : value
}

function normalizeNumber(value) {
  if (!hasValue(value)) return undefined
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? Number(numberValue.toFixed(2)) : undefined
}

function resolveFormFieldValue(field, row, options, config) {
  if (!field) return undefined
  const directValue = row && row[field.key]
  if (hasValue(directValue)) return directValue
  if (field.key !== "warehouseId") return directValue

  const details = row && Array.isArray(row.details) ? row.details : []
  if (isSalesConfig(config)) {
    const ids = Array.from(new Set(details.filter(item => hasValue(item.warehouseId)).map(item => String(item.warehouseId))))
    return ids.length === 1 ? normalizeIdValue(ids[0]) : undefined
  }
  const detailWarehouseId = firstValue(details[0], ["warehouseId"])
  if (hasValue(detailWarehouseId)) return detailWarehouseId

  const orderWarehouseId = firstValue(row, ["shopDeptId", "targetDeptId"])
  if (hasValue(orderWarehouseId)) return orderWarehouseId

  return options && options.selectedDeptId
}

function copyFormFallbackLabels(config, data, row) {
  ;((config && config.fields) || []).forEach(field => {
    const key = field && field.fallbackLabelKey
    if (key && hasValue(row && row[key])) data[key] = row[key]
  })
}

function applyFormDefaults(config, data, options) {
  const result = data || {}
  const selectedDeptId = options && options.selectedDeptId
  if (hasValue(selectedDeptId)) {
    ;((config && config.fields) || []).forEach(field => {
      if (field && field.type === "context-dept" && !hasValue(result[field.key])) {
        result[field.key] = normalizeIdValue(selectedDeptId)
      }
    })
  }
  if (isFixedAssetRepairConfig(config)) {
    if (hasValue(selectedDeptId) && !hasValue(result.shopDeptId)) {
      result.shopDeptId = normalizeIdValue(selectedDeptId)
    }
    return result
  }
  if (!isTransferConfig(config)) return result

  if (config.fixedTransferType) {
    result.transferType = config.fixedTransferType
  }

  const selectedDeptType = options && options.selectedDeptType
  const storeReturn = String(result.transferType || "") === "store_return"
  if (hasValue(selectedDeptId)) {
    const deptId = normalizeIdValue(selectedDeptId)
    if (selectedDeptType === "STORE") {
      if (storeReturn) {
        if (!hasValue(result.fromDeptId)) result.fromDeptId = deptId
        if (!hasValue(result.fromWarehouseId)) result.fromWarehouseId = deptId
      } else {
        if (!hasValue(result.toDeptId)) result.toDeptId = deptId
        if (!hasValue(result.toWarehouseId)) result.toWarehouseId = deptId
      }
    }
    if (selectedDeptType === "WAREHOUSE") {
      if (storeReturn) {
        if (!hasValue(result.toDeptId)) result.toDeptId = deptId
        if (!hasValue(result.toWarehouseId)) result.toWarehouseId = deptId
      } else {
        if (!hasValue(result.fromDeptId)) result.fromDeptId = deptId
        if (!hasValue(result.fromWarehouseId)) result.fromWarehouseId = deptId
      }
    }
  }

  if (hasValue(result.fromDeptId) && !hasValue(result.fromWarehouseId)) {
    result.fromWarehouseId = normalizeIdValue(result.fromDeptId)
  }
  if (hasValue(result.toDeptId) && !hasValue(result.toWarehouseId)) {
    result.toWarehouseId = normalizeIdValue(result.toDeptId)
  }
  if (isFixedTransferConfig(config)) {
    if (hasValue(result.fromDeptId)) result.fromWarehouseId = normalizeIdValue(result.fromDeptId)
    if (hasValue(result.toDeptId)) result.toWarehouseId = normalizeIdValue(result.toDeptId)
  }

  return result
}

function applyPayloadDefaults(config, payload, source, options) {
  if (isSalesConfig(config)) {
    copyPayloadFields(payload, source, ["customerName"])
    applySalesPayloadDefaults(payload)
    return
  }

  if (isPurchaseConfig(config)) {
    copyPayloadFields(payload, source, ["supplierName"])
    applyOrderWarehouseDefaults(payload)
    return
  }

  if (isSalesReturnConfig(config)) {
    copyPayloadFields(payload, source, ["salesOrderNo", "returnTitle", "customerName"])
    return
  }

  if (isPurchaseReturnConfig(config)) {
    copyPayloadFields(payload, source, ["purchaseOrderNo", "returnTitle", "supplierName"])
    return
  }

  if (isCustomerServiceCardConfig(config)) {
    payload.requestKey = hasValue(source.requestKey) ? String(source.requestKey).trim() : createRequestKey("customer-card")
    payload.sourceClient = "MOBILE"
    return
  }

  if (isFixedAssetRepairConfig(config)) {
    const selectedDeptId = options && options.selectedDeptId
    if (hasValue(source.oeItemId) && !hasValue(payload.oeItemId)) {
      payload.oeItemId = normalizeIdValue(source.oeItemId)
    }
    copyPayloadFields(payload, source, ["oeItemName"])
    if (hasValue(source.shopDeptId) && !hasValue(payload.shopDeptId)) {
      payload.shopDeptId = normalizeIdValue(source.shopDeptId)
    }
    if (hasValue(selectedDeptId) && !hasValue(payload.shopDeptId)) {
      payload.shopDeptId = normalizeIdValue(selectedDeptId)
    }
    return
  }

  if (!isTransferConfig(config)) return

  if (config.fixedTransferType) {
    payload.transferType = config.fixedTransferType
  }

  if (hasValue(source.fromWarehouseId) && !hasValue(payload.fromWarehouseId)) {
    payload.fromWarehouseId = normalizeIdValue(source.fromWarehouseId)
  }
  if (hasValue(source.toWarehouseId) && !hasValue(payload.toWarehouseId)) {
    payload.toWarehouseId = normalizeIdValue(source.toWarehouseId)
  }

  const selectedDeptId = options && options.selectedDeptId
  const selectedDeptType = options && options.selectedDeptType
  const storeReturn = String(payload.transferType || "") === "store_return"
  if (hasValue(selectedDeptId)) {
    const deptId = normalizeIdValue(selectedDeptId)
    if (selectedDeptType === "STORE") {
      if (storeReturn) {
        if (!hasValue(payload.fromDeptId)) payload.fromDeptId = deptId
        if (!hasValue(payload.fromWarehouseId)) payload.fromWarehouseId = deptId
      } else {
        if (!hasValue(payload.toDeptId)) payload.toDeptId = deptId
        if (!hasValue(payload.toWarehouseId)) payload.toWarehouseId = deptId
      }
    }
    if (selectedDeptType === "WAREHOUSE") {
      if (storeReturn) {
        if (!hasValue(payload.toDeptId)) payload.toDeptId = deptId
        if (!hasValue(payload.toWarehouseId)) payload.toWarehouseId = deptId
      } else {
        if (!hasValue(payload.fromDeptId)) payload.fromDeptId = deptId
        if (!hasValue(payload.fromWarehouseId)) payload.fromWarehouseId = deptId
      }
    }
  }

  if (hasValue(payload.fromDeptId) && !hasValue(payload.fromWarehouseId)) {
    payload.fromWarehouseId = normalizeIdValue(payload.fromDeptId)
  }
  if (hasValue(payload.toDeptId) && !hasValue(payload.toWarehouseId)) {
    payload.toWarehouseId = normalizeIdValue(payload.toDeptId)
  }
  if (isFixedTransferConfig(config)) {
    if (hasValue(payload.fromDeptId)) payload.fromWarehouseId = normalizeIdValue(payload.fromDeptId)
    if (hasValue(payload.toDeptId)) payload.toWarehouseId = normalizeIdValue(payload.toDeptId)
  }
}

function applySalesPayloadDefaults(payload) {
  if (!payload || !hasValue(payload.warehouseId) || !Array.isArray(payload.details)) return
  const warehouseId = normalizeIdValue(payload.warehouseId)
  payload.details = payload.details.map(item => Object.assign({}, item, hasValue(item.warehouseId) ? {} : { warehouseId }))
}

function applyOrderWarehouseDefaults(payload) {
  if (!payload || !hasValue(payload.warehouseId) || !Array.isArray(payload.details)) return
  const warehouseId = normalizeIdValue(payload.warehouseId)
  payload.details = payload.details.map(item => Object.assign({}, item, { warehouseId }))
}

function copyPayloadFields(payload, source, keys) {
  ;(keys || []).forEach(key => {
    if (hasValue(source[key]) && !hasValue(payload[key])) {
      payload[key] = source[key]
    }
  })
}

function isSalesConfig(config) {
  return config && config.idKey === "orderId" && (config.fields || []).some(field => field && field.key === "customerId")
}

function isPurchaseConfig(config) {
  return config && config.idKey === "orderId" && (config.fields || []).some(field => field && field.key === "supplierId")
}

function isSalesReturnConfig(config) {
  return config && config.idKey === "returnId" && (config.fields || []).some(field => field && field.key === "salesOrderId")
}

function isPurchaseReturnConfig(config) {
  return config && config.idKey === "returnId" && (config.fields || []).some(field => field && field.key === "purchaseOrderId")
}

function isTransferConfig(config) {
  return config && config.idKey === "transferId"
}

function isFixedAssetRepairConfig(config) {
  return config && config.idKey === "repairId"
}

function isCustomerServiceCardConfig(config) {
  return config && config.idKey === "customerId" &&
    (config.fields || []).some(field => field && field.key === "teaPreferences")
}

function isReplenishmentConfig(config) {
  return config && config.idKey === "transferId" && config.fixedTransferType === "warehouse"
}

function isFixedTransferConfig(config) {
  return isTransferConfig(config) && hasValue(config.fixedTransferType)
}

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

function createRequestKey(prefix) {
  return prefix + "-" + Date.now() + "-" + Math.random().toString(36).slice(2, 10)
}

module.exports = {
  cloneMobileEditFormSource,
  snapshotMobileFormSheet,
  restoreMobileFormSheet,
  createMobileFormData,
  buildMobileFormPayload,
  normalizeLineItems
}
