function decorateCategories(list, parentPath) {
  const pathPrefix = parentPath || []
  return (list || []).map(item => {
    const currentPath = pathPrefix.concat(item.categoryName || "")
    const children = decorateCategories(item.children || [], currentPath)
    return Object.assign({}, item, {
      categoryFullPath: item.categoryFullPath || currentPath.filter(Boolean).join(" / "),
      children
    })
  })
}

function flattenCategories(list) {
  const result = []
  const walk = items => {
    ;(items || []).forEach(item => {
      result.push(item)
      if (item.children && item.children.length) {
        walk(item.children)
      }
    })
  }
  walk(list)
  return result
}

function findCategoryOption(categoryId, categoryOptions) {
  return (categoryOptions || []).find(item => String(item.categoryId) === String(categoryId))
}

function getCategoryFilterIds(categoryId, categoryOptions) {
  const selectedId = String(categoryId)
  const ids = new Set([selectedId])
  ;(categoryOptions || []).forEach(item => {
    if (String(item.categoryId) === selectedId ||
      String(item.ancestors || "").split(",").includes(selectedId)) {
      ids.add(String(item.categoryId))
    }
  })
  const selected = findCategoryOption(categoryId, categoryOptions)
  const collect = items => {
    ;(items || []).forEach(item => {
      if (item.categoryId !== null && item.categoryId !== undefined) {
        ids.add(String(item.categoryId))
      }
      collect(item.children || [])
    })
  }
  if (selected) {
    collect(selected.children || [])
  }
  return ids
}

function detailCategoryTextMatches(row, selected) {
  if (!selected) return false
  const rowText = [row.categoryFullPath, row.categoryName].filter(Boolean).join(" / ")
  if (!rowText) return false
  const selectedName = selected.categoryName || ""
  const selectedPath = selected.categoryFullPath || selectedName
  return Boolean(
    (selectedPath && rowText.indexOf(selectedPath) !== -1) ||
    (selectedName && rowText.split(" / ").includes(selectedName))
  )
}

function filterDetailsByCategory(details, categoryId, categoryOptions) {
  const rows = Array.isArray(details) ? details : []
  if (!categoryId) return rows
  const categoryIds = getCategoryFilterIds(categoryId, categoryOptions)
  const selected = findCategoryOption(categoryId, categoryOptions)
  return rows.filter(row => {
    if (!row) return false
    if (row.categoryId !== null && row.categoryId !== undefined &&
      categoryIds.has(String(row.categoryId))) {
      return true
    }
    return detailCategoryTextMatches(row, selected)
  })
}

function hasDetailCategoryMetadata(row) {
  return Boolean(row && (
    row.categoryId !== null && row.categoryId !== undefined ||
    row.categoryName ||
    row.categoryFullPath
  ))
}

function isActualQtyMissing(row) {
  return !row || row.actualQty === null || row.actualQty === undefined || row.actualQty === ""
}

function numberValue(value, fallback = 0) {
  if (value === null || value === undefined || value === "") {
    return fallback
  }
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

function effectiveActualQty(row) {
  if (row && row.recountQty !== null && row.recountQty !== undefined && row.recountQty !== "") {
    return numberValue(row.recountQty)
  }
  return numberValue(row && row.actualQty)
}

function resolveDiffType(diffQty) {
  const quantity = Number(diffQty || 0)
  if (quantity > 0) return "profit"
  if (quantity < 0) return "loss"
  return "none"
}

function refreshDetailDiff(row) {
  if (isActualQtyMissing(row) ||
    row.bookQty === null || row.bookQty === undefined || row.bookQty === "") {
    row.diffQty = null
    row.diffType = "none"
    return row
  }
  const actualQty = effectiveActualQty(row)
  const bookQty = Number(row.bookQty || 0)
  row.diffQty = Number((actualQty - bookQty).toFixed(2))
  row.diffType = resolveDiffType(row.diffQty)
  return row
}

function normalizeDetail(item) {
  return refreshDetailDiff(Object.assign({}, item))
}

function requiresRecount(row, thresholdValue) {
  if (isActualQtyMissing(row)) return false
  if (row.bookQty === null || row.bookQty === undefined || row.bookQty === "") {
    return row.recountRequired === "1"
  }
  const threshold = Number(thresholdValue || 0)
  if (!Number.isFinite(threshold) || threshold <= 0) return false
  const difference = Math.abs(numberValue(row.actualQty) - numberValue(row.bookQty))
  return difference >= threshold
}

function applyActualQtyChange(row, thresholdValue) {
  if (row.bookQty !== null && row.bookQty !== undefined && row.bookQty !== "") {
    row.recountRequired = requiresRecount(row, thresholdValue) ? "1" : "0"
    if (row.recountRequired !== "1") {
      row.recountQty = null
    }
  } else {
    row.recountRequired = row.recountRequired || "0"
  }
  return refreshDetailDiff(row)
}

function lossQuantity(row) {
  if (isActualQtyMissing(row) ||
    row.bookQty === null || row.bookQty === undefined || row.bookQty === "") {
    return null
  }
  const bookQty = numberValue(row.bookQty)
  const actualQty = effectiveActualQty(row)
  return Math.max(Number((bookQty - actualQty).toFixed(2)), 0)
}

function profitQuantity(row) {
  if (isActualQtyMissing(row) ||
    row.bookQty === null || row.bookQty === undefined || row.bookQty === "") {
    return 0
  }
  const bookQty = numberValue(row.bookQty)
  const actualQty = effectiveActualQty(row)
  return Math.max(Number((actualQty - bookQty).toFixed(2)), 0)
}

function buildStockCheckSummary(details) {
  const rows = Array.isArray(details) ? details : []
  return rows.reduce((summary, row) => {
    summary.itemCount += 1
    if (isActualQtyMissing(row)) {
      summary.missingCount += 1
    } else {
      summary.enteredCount += 1
    }
    summary.lossQuantity += lossQuantity(row) || 0
    summary.profitQuantity += profitQuantity(row)
    return summary
  }, {
    itemCount: 0,
    enteredCount: 0,
    missingCount: 0,
    lossQuantity: 0,
    profitQuantity: 0
  })
}

function rowLossQuantity(row) {
  const diffQty = numberValue(row && row.totalDiffQuantity)
  return diffQty < 0 ? Math.abs(diffQty) : 0
}

function formatQuantity(value) {
  if (value === null || value === undefined || value === "") return "-"
  const number = Number(value)
  if (!Number.isFinite(number)) return "-"
  return number.toFixed(2).replace(/\.00$/, "").replace(/(\.\d)0$/, "$1")
}

module.exports = {
  applyActualQtyChange,
  buildStockCheckSummary,
  decorateCategories,
  detailCategoryTextMatches,
  effectiveActualQty,
  filterDetailsByCategory,
  findCategoryOption,
  flattenCategories,
  formatQuantity,
  getCategoryFilterIds,
  hasDetailCategoryMetadata,
  isActualQtyMissing,
  lossQuantity,
  normalizeDetail,
  numberValue,
  profitQuantity,
  refreshDetailDiff,
  requiresRecount,
  resolveDiffType,
  rowLossQuantity
}
