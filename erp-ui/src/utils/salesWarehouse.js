function hasWarehouseId(value) {
  return value !== undefined && value !== null && value !== ""
}

// The selection remains visible to the operator. Never choose the first of many.
function defaultSalesWarehouse(options, selectedDeptId, existingId) {
  const warehouses = options || []
  const match = id => warehouses.find(row => hasWarehouseId(id) && String(row.deptId) === String(id))
  const existing = match(existingId)
  if (existing) return existing.deptId
  const current = match(selectedDeptId)
  if (current) return current.deptId
  return warehouses.length === 1 ? warehouses[0].deptId : undefined
}

function fillEmptySalesWarehouses(details, warehouseId) {
  return (details || []).map(row => Object.assign({}, row,
    !hasWarehouseId(row.warehouseId) && hasWarehouseId(warehouseId) ? { warehouseId } : {}))
}

function deliveryWarehouseRows(items, warehouseId) {
  if (!hasWarehouseId(warehouseId)) return []
  return (items || []).filter(row => String(row.warehouseId) === String(warehouseId))
}

module.exports = { hasWarehouseId, defaultSalesWarehouse, fillEmptySalesWarehouses, deliveryWarehouseRows }
