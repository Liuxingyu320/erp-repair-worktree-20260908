// Inventory quantities use DECIMAL(16,2); validate without clamping or rounding input.
function validInventoryQuantity(value) {
  const text = String(value == null ? "" : value).trim()
  return /^\d+(?:\.\d{1,2})?$/.test(text) && Number(text) > 0 && Number(text) < 1e14
}
module.exports = { validInventoryQuantity }
