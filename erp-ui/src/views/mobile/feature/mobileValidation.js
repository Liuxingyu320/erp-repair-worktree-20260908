const { isReturnSelected } = require("../../../utils/returnSelection")

function getMobileFormValidationError(config, data) {
  const source = data || {}
  const fields = (config && config.fields) || []

  for (let i = 0; i < fields.length; i += 1) {
    const field = fields[i]
    const value = source[field.key]
    const required = isFieldRequired(field, source)
    if (field.type === "line-items") {
      const validationError = validateLineItems(field, value, required)
      if (validationError) return validationError
    } else if (required && !hasValue(value)) {
      return createValidationError("请填写" + field.label, field.key)
    }
  }

  return null
}

function validateMobileForm(config, data) {
  const validationError = getMobileFormValidationError(config, data)
  return validationError ? validationError.message : ""
}

function validateLineItems(field, value, required) {
  const rows = Array.isArray(value) ? value : []
  if (required && rows.length === 0) {
    return createValidationError("请添加" + field.label, field.key)
  }
  if (field.selectionScoped && required && !rows.some(isReturnSelected)) {
    return createValidationError("请至少勾选一条" + field.label, field.key)
  }
  for (let i = 0; i < rows.length; i += 1) {
    const row = rows[i] || {}
    if (field.selectionScoped && !isReturnSelected(row)) continue
    if (field.selectionScoped && (!Number.isFinite(Number(row.quantity)) || Number(row.quantity) <= 0 ||
        (hasValue(row.maxReturnQuantity) && (!Number.isFinite(Number(row.maxReturnQuantity)) || Number(row.quantity) > Number(row.maxReturnQuantity))))) {
      return createValidationError(field.label + "第" + (i + 1) + "行数量须大于0且不超过可退数量", field.key, i, "quantity")
    }
    const lineNo = i + 1
    if (field.payloadMode === "stock-check-products" && !hasValue(row.productId)) {
      return createValidationError(field.label + "第" + lineNo + "行请填写商品", field.key, i, "productId")
    }
    const itemFields = field.itemFields || []
    for (let j = 0; j < itemFields.length; j += 1) {
      const itemField = itemFields[j]
      const rowValue = row[itemField.key]
      if (isFieldRequired(itemField, row) && !hasValue(rowValue)) {
        return createValidationError(
          field.label + "第" + lineNo + "行请填写" + itemField.label,
          field.key,
          i,
          itemField.key
        )
      }
      if (itemField.key === "quantity" && hasValue(rowValue) && Number(rowValue) <= 0) {
        return createValidationError(
          field.label + "第" + lineNo + "行数量必须大于0",
          field.key,
          i,
          itemField.key
        )
      }
    }
  }
  return null
}

function createValidationError(message, fieldKey, rowIndex, itemFieldKey) {
  return {
    message,
    fieldKey,
    rowIndex: rowIndex === undefined ? null : rowIndex,
    itemFieldKey: itemFieldKey === undefined ? null : itemFieldKey
  }
}

function isFieldRequired(field, source) {
  if (!field) return false
  const currentSource = source || {}
  if (field.requiredWhen && field.requiredWhen.key) {
    return String(currentSource[field.requiredWhen.key]) === String(field.requiredWhen.value)
  }
  if (field.requiredUnless && field.requiredUnless.key) {
    return !hasValue(currentSource[field.requiredUnless.key])
  }
  return !!field.required
}

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

module.exports = {
  getMobileFormValidationError,
  isFieldRequired,
  validateMobileForm
}
