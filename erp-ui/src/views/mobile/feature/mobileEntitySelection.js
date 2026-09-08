function hasEntityValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

function normalizeEntityLabel(value) {
  return value === undefined || value === null ? "" : String(value).trim()
}

function shouldClearEntitySelection(value, keyword, selectedLabel) {
  if (!hasEntityValue(value)) return false
  const normalizedKeyword = normalizeEntityLabel(keyword)
  const normalizedSelectedLabel = normalizeEntityLabel(selectedLabel)
  if (!normalizedSelectedLabel) return normalizedKeyword !== ""
  return normalizedKeyword !== normalizedSelectedLabel
}

module.exports = {
  shouldClearEntitySelection
}
