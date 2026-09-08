const { buildTodoFocusQuery } = require("../../../utils/todoBusinessFocus")

const ROUTE_QUERY_IGNORED_KEYS = Object.freeze([
  "preview",
  "redirect",
  "mobileRedirectReason",
  "mobileRedirectMessage",
  "mobileRedirectFrom"
])

function firstQueryValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function hasQueryValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

function buildApprovalRouteContext(query) {
  const source = query || {}
  return {
    approvalTaskId: firstQueryValue(source.approvalTaskId) || "",
    approvalInstanceId: firstQueryValue(source.approvalInstanceId) || ""
  }
}

function buildRouteFeatureQuery(featureKey, routeQuery) {
  const source = routeQuery || {}
  const normalizedQuery = Object.keys(source).reduce((result, key) => {
    if (ROUTE_QUERY_IGNORED_KEYS.indexOf(key) > -1) return result
    const value = source[key]
    if (hasQueryValue(value)) {
      result[key] = firstQueryValue(value)
    }
    return result
  }, {})
  return buildTodoFocusQuery(featureKey, normalizedQuery)
}

function normalizeSearchKeyword(value) {
  return value === undefined || value === null ? "" : String(value).trim()
}

function buildFeatureRequestQuery(options) {
  const source = options || {}
  const query = Object.assign({}, source.featureQuery || {})
  const keyword = normalizeSearchKeyword(source.searchKeyword)
  const field = source.activeSearchFieldConfig

  if (keyword && field && field.key) {
    query[field.key] = keyword
  }
  if (source.activeStockStatus) {
    query.stockStatus = source.activeStockStatus
  }
  return query
}

function cleanComparableQuery(query) {
  return Object.keys(query || {}).reduce((result, key) => {
    const value = query[key]
    if (hasQueryValue(value)) {
      result[key] = value
    }
    return result
  }, {})
}

function isSameFeatureQuery(left, right) {
  const leftQuery = cleanComparableQuery(left)
  const rightQuery = cleanComparableQuery(right)
  const leftKeys = Object.keys(leftQuery).sort()
  const rightKeys = Object.keys(rightQuery).sort()

  if (leftKeys.length !== rightKeys.length) return false
  return leftKeys.every((key, index) => {
    return key === rightKeys[index] && String(leftQuery[key]) === String(rightQuery[key])
  })
}

function resolveActionLabelForQuery(actions, query) {
  const candidates = Array.isArray(actions) ? actions : []
  for (let index = 0; index < candidates.length; index += 1) {
    const action = candidates[index]
    if (
      action &&
      action.query !== undefined &&
      isSameFeatureQuery(action.query, query || {})
    ) {
      return action.label || ""
    }
  }
  return ""
}

function flattenDeptList(depts) {
  const result = []
  const visit = list => {
    if (!Array.isArray(list)) return
    list.forEach(item => {
      result.push(item)
      if (item && Array.isArray(item.children) && item.children.length) {
        visit(item.children)
      }
    })
  }
  visit(depts)
  return result
}

function normalizeManagedStoreOptions(depts) {
  const stores = []
  const seen = {}
  flattenDeptList(depts).forEach(dept => {
    const deptType = dept && dept.deptType ? String(dept.deptType).toUpperCase() : ""
    const deptId = dept && dept.deptId
    if (!deptId || deptType !== "STORE" || seen[String(deptId)]) return
    seen[String(deptId)] = true
    stores.push({
      deptId,
      deptName: dept.deptName || "未命名门店"
    })
  })
  return stores
}

function normalizeManagedStoreSelection(managedStoreId, managedStoreOptions, allManagedStoreValue) {
  if (Number(managedStoreId) === allManagedStoreValue) {
    return allManagedStoreValue
  }
  const options = Array.isArray(managedStoreOptions) ? managedStoreOptions : []
  const exists = options.some(store => String(store.deptId) === String(managedStoreId))
  return exists ? managedStoreId : allManagedStoreValue
}

function resolveManagedStoreName(options) {
  const source = options || {}
  if (!source.showManagedStoreSelect) return ""
  if (Number(source.managedStoreId) === source.allManagedStoreValue) {
    return "全部管理门店"
  }
  const stores = Array.isArray(source.managedStoreOptions) ? source.managedStoreOptions : []
  const store = stores.find(item => String(item.deptId) === String(source.managedStoreId))
  return store ? store.deptName : "管理门店"
}

function buildFeatureExportQuery(options) {
  const source = options || {}
  const query = Object.assign({}, source.requestQuery || {})
  if (source.showManagedStoreSelect) {
    query.ownOnly = false
    query.shopDeptId = source.managedStoreId || source.allManagedStoreValue
    return query
  }
  if (source.exportConfig && source.exportConfig.contextScoped && source.selectedDeptId) {
    query.shopDeptId = source.selectedDeptId
    if (source.selectedDeptType === "WAREHOUSE") {
      query.warehouseId = source.selectedDeptId
    }
  }
  return query
}

function resolveRouteNotice(query) {
  const source = query || {}
  const reason = firstQueryValue(source.mobileRedirectReason)
  const message = firstQueryValue(source.mobileRedirectMessage)
  let title = "移动端提示"
  if (reason === "unavailable") title = "移动端暂未开放"
  if (reason === "context-mismatch") title = "已切换到当前组织"
  return {
    title,
    message: message ? String(message) : ""
  }
}

function resolveActiveFilterLabel(options) {
  const source = options || {}
  const labels = []
  const quickFilterLabel = source.activeActionLabel || source.resolvedActionLabel
  const keyword = normalizeSearchKeyword(source.searchKeyword)
  if (quickFilterLabel) labels.push(quickFilterLabel)
  if (source.selectedManagedStoreName) labels.push(source.selectedManagedStoreName)
  if (source.activeStockStatusLabel) labels.push(source.activeStockStatusLabel)
  if (keyword && source.activeSearchFieldLabel) {
    labels.push(source.activeSearchFieldLabel + "：" + keyword)
  }
  return labels.join(" / ")
}

module.exports = {
  ROUTE_QUERY_IGNORED_KEYS,
  buildApprovalRouteContext,
  buildFeatureExportQuery,
  buildFeatureRequestQuery,
  buildRouteFeatureQuery,
  cleanComparableQuery,
  firstQueryValue,
  isSameFeatureQuery,
  normalizeManagedStoreOptions,
  normalizeManagedStoreSelection,
  normalizeSearchKeyword,
  resolveActionLabelForQuery,
  resolveActiveFilterLabel,
  resolveManagedStoreName,
  resolveRouteNotice
}
