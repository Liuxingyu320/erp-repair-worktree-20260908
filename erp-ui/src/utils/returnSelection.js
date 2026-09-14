// Existing persisted return lines have no UI flag; positive rows stay selected when editing.
function isReturnSelected(row) {
  return !!row && (row.returnSelected === true || (row.returnSelected === undefined && Number(row.quantity) > 0))
}
function selectAllReturnRows(rows) {
  return (rows || []).map(row => ({ ...row, returnSelected: Number(row.maxReturnQuantity) > 0,
    quantity: Number(row.maxReturnQuantity) > 0 ? Number(row.maxReturnQuantity) : 0 }))
}
module.exports = { isReturnSelected, selectAllReturnRows }
