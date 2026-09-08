'use strict'

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ''
}

function normalizeRows(response) {
  if (!response) return []
  if (Array.isArray(response)) return response
  if (Array.isArray(response.rows)) return response.rows
  if (Array.isArray(response.data)) return response.data
  return []
}

function normalizeItemType(value) {
  const normalized = hasValue(value) ? String(value).trim().toLowerCase() : 'product'
  return ['product', 'oe', 'gift'].includes(normalized) ? normalized : 'product'
}

function materialId(row) {
  if (!row) return undefined
  return row.itemId || row.productId || row.giftId || row.oeItemId
}

function sameMaterial(row, detail) {
  return String(materialId(row)) === String(materialId(detail)) &&
    normalizeItemType(row && row.itemType) === normalizeItemType(detail && detail.itemType)
}

function availableQuantity(row) {
  if (hasValue(row && row.availableQuantity)) return row.availableQuantity
  const currentQuantity = Number(row && row.currentQuantity)
  const lockedQuantity = Number(row && row.lockedQuantity || 0)
  if (!Number.isFinite(currentQuantity)) return 0
  return Math.max(currentQuantity - (Number.isFinite(lockedQuantity) ? lockedQuantity : 0), 0)
}

function createAvailabilityQuery(formData, detail) {
  const warehouseId = formData.fromWarehouseId || formData.fromDeptId
  return {
    pageNum: 1,
    pageSize: 1,
    itemType: normalizeItemType(detail && detail.itemType),
    itemId: materialId(detail),
    shopDeptId: formData.fromDeptId || warehouseId,
    warehouseId,
    transferSource: true
  }
}

function refreshMobileTransferAvailability(formData, loadStock) {
  const source = formData || {}
  const details = Array.isArray(source.details) ? source.details : []
  const materialDetails = details.filter(detail => hasValue(materialId(detail)))
  const clonedForm = Object.assign({}, source, {
    details: details.map(detail => Object.assign({}, detail))
  })

  if (!materialDetails.length) return Promise.resolve(clonedForm)
  if (!hasValue(source.fromWarehouseId || source.fromDeptId)) {
    return Promise.reject(new Error('草稿缺少来源组织，请重新选择补货仓库'))
  }
  if (typeof loadStock !== 'function') {
    return Promise.reject(new Error('来源库存复查服务不可用'))
  }

  return Promise.all(clonedForm.details.map(detail => {
    if (!hasValue(materialId(detail))) return Promise.resolve(detail)
    const query = createAvailabilityQuery(source, detail)
    return Promise.resolve(loadStock(query)).then(response => {
      const stock = normalizeRows(response).find(row => sameMaterial(row, detail))
      const next = Object.assign({}, detail, {
        availableQuantity: stock ? availableQuantity(stock) : 0,
        availabilityStatus: 'loaded'
      })
      if (stock && hasValue(stock.costPrice)) next.costPrice = stock.costPrice
      return next
    })
  })).then(refreshedDetails => Object.assign({}, clonedForm, { details: refreshedDetails }))
}

module.exports = {
  availableQuantity,
  createAvailabilityQuery,
  refreshMobileTransferAvailability
}
