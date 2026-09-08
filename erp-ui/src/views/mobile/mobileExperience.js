const DEFAULT_PRIMARY_ACTION_LIMIT = 4
const LOW_FREQUENCY_FEATURE_ACTION_LABELS = new Set([
  "采购退货",
  "已办记录",
  "调度日志",
  "我的签约",
  "我的采购",
  "考勤",
  "工资",
  "资产报修"
])
const LOW_FREQUENCY_FEATURE_ACTION_PATHS = new Set([
  "/mobile/purchase-return",
  "/mobile/sales-return",
  "/mobile/monitor-job-log",
  "/mobile/sign-package",
  "/mobile/attendance",
  "/mobile/salary",
  "/mobile/fixed-asset-repair",
  "/mobile/product",
  "/mobile/oe",
  "/mobile/gift",
  "/mobile/customer",
  "/mobile/supplier",
  "/mobile/category",
  "/mobile/stock-log",
  "/mobile/transfer-records",
  "/mobile/system-user",
  "/mobile/system-role",
  "/mobile/system-post",
  "/mobile/system-dept",
  "/mobile/system-menu",
  "/mobile/user-shop",
  "/mobile/system-config",
  "/mobile/system-dict-type",
  "/mobile/system-dict-data",
  "/mobile/system-logininfor",
  "/mobile/system-operlog",
  "/mobile/monitor-job",
  "/mobile/monitor-job-log",
  "/mobile/monitor-online",
  "/mobile/salary-scheme",
  "/mobile/transfer-rules"
])
const WORKBENCH_SUMMARY_COUNT_KEYS = [
  "todoCount",
  "lowStockCount",
  "pendingReceiveCount",
  "pendingDeliverCount",
  "pendingApprovalCount",
  "purchaseReceiveCount",
  "transferReceiveCount",
  "deliveryNoticeCount",
  "transferDeliverCount"
]

function createClearedFeatureQuery(defaultQuery) {
  return Object.assign({}, defaultQuery || {})
}

function normalizeComparableMobileQuery(query) {
  return Object.keys(query || {}).sort().reduce((normalized, key) => {
    const value = query[key]
    if (value === undefined || value === null) return normalized
    const normalizedValue = Array.isArray(value)
      ? value.map(item => String(item).trim()).join(",")
      : String(value).trim()
    if (normalizedValue !== "") normalized[key] = normalizedValue
    return normalized
  }, {})
}

function hasMobileFeatureQueryOverrides(featureQuery, defaultQuery) {
  const current = normalizeComparableMobileQuery(featureQuery)
  const defaults = normalizeComparableMobileQuery(defaultQuery)
  const currentKeys = Object.keys(current)
  const defaultKeys = Object.keys(defaults)
  if (currentKeys.length !== defaultKeys.length) return true
  return currentKeys.some(key => !Object.prototype.hasOwnProperty.call(defaults, key) || current[key] !== defaults[key])
}

function partitionMobileActions(actions, maxPrimary = DEFAULT_PRIMARY_ACTION_LIMIT) {
  const source = Array.isArray(actions) ? actions : []
  const parsedLimit = Number(maxPrimary)
  const primaryLimit = Number.isFinite(parsedLimit)
    ? Math.max(0, Math.floor(parsedLimit))
    : DEFAULT_PRIMARY_ACTION_LIMIT
  const primary = []
  const overflow = []

  source.forEach(action => {
    if (action && action.placement === "more") {
      overflow.push(action)
      return
    }
    if (primary.length < primaryLimit) {
      primary.push(action)
      return
    }
    overflow.push(action)
  })

  return { primary, overflow }
}

function prioritizeMobileFeatureActions(actions) {
  const source = Array.isArray(actions) ? actions : []
  return source.map(action => {
    if (!action || action.placement === "more") return action
    const isLowFrequency = action.behavior === "export" ||
      action.behavior === "logout" ||
      LOW_FREQUENCY_FEATURE_ACTION_LABELS.has(action.label) ||
      LOW_FREQUENCY_FEATURE_ACTION_PATHS.has(action.path)
    return isLowFrequency ? Object.assign({}, action, { placement: "more" }) : action
  })
}

function filterMobileShortcutActionsForListState(actions, listState) {
  const source = Array.isArray(actions) ? actions : []
  if (!listState || listState.actionId !== "create") return source.slice()
  return source.filter(action => !action || action.behavior !== "create-form")
}

function resolveMobileListStateActionLabel(listState, actions) {
  if (!listState || !listState.actionId) return ""
  if (listState.actionId === "create") {
    const createAction = (Array.isArray(actions) ? actions : [])
      .find(action => action && action.behavior === "create-form")
    return createAction && createAction.label ? createAction.label : "新增"
  }
  return {
    retry: "重试",
    refresh: "刷新",
    "clear-filters": "清除筛选",
    "switch-context": "切换组织",
    "select-context": "选择组织",
    relogin: "重新登录",
    "go-home": "返回有效入口"
  }[listState.actionId] || "继续"
}

function createWorkbenchSummaryState(type, title, description, actionId, summary = null) {
  return { type, title, description, actionId, summary }
}

function normalizeCompleteWorkbenchSummary(summary, contextId) {
  if (!summary || typeof summary !== "object" || Array.isArray(summary)) return null
  if (
    summary.selectedDeptId === undefined ||
    summary.selectedDeptId === null ||
    String(summary.selectedDeptId) !== String(contextId) ||
    !String(summary.selectedDeptType || "").trim()
  ) return null

  const normalized = Object.assign({}, summary)
  for (let index = 0; index < WORKBENCH_SUMMARY_COUNT_KEYS.length; index += 1) {
    const key = WORKBENCH_SUMMARY_COUNT_KEYS[index]
    const value = summary[key]
    if (typeof value !== "number" || !Number.isFinite(value) || value < 0) return null
    normalized[key] = Math.round(value)
  }
  return normalized
}

function isWorkbenchSessionError(error) {
  const response = error && error.response
  const status = response && response.status !== undefined
    ? response.status
    : error && (error.status !== undefined ? error.status : error.code)
  const message = error && error.message ? error.message : ""
  return String(status) === "401" ||
    /登录状态已过期|登录已过期|会话失效|请重新登录|未登录/i.test(String(message))
}

function isWorkbenchPermissionError(error) {
  if (isWorkbenchSessionError(error)) return false
  const response = error && error.response
  const status = response && response.status !== undefined
    ? response.status
    : error && (error.status !== undefined ? error.status : error.code)
  const message = error && error.message ? error.message : ""
  return String(status) === "403" ||
    /权限|无权|未授权|NotPermission|Forbidden/i.test(String(message))
}

function resolveMobileWorkbenchSummaryState(options = {}) {
  const source = options || {}
  const contextId = source.contextId
  if (source.sessionExpired === true || isWorkbenchSessionError(source.error)) {
    return createWorkbenchSummaryState(
      "session-expired",
      "登录状态已过期",
      "请重新登录后继续查看待办。",
      "relogin"
    )
  }
  if (contextId === undefined || contextId === null || String(contextId).trim() === "") {
    return createWorkbenchSummaryState(
      "context-required",
      "选择店铺或仓库后继续",
      "选择组织后才能查看待办和业务数据。",
      "select-context"
    )
  }
  if (source.loading === true) {
    return createWorkbenchSummaryState(
      "loading",
      "正在加载待办",
      "正在获取当前组织的最新待办，请稍候。",
      ""
    )
  }
  if (source.error) {
    if (isWorkbenchPermissionError(source.error)) {
      return createWorkbenchSummaryState(
        "permission-error",
        "你没有查看此内容的权限",
        "当前账号无权查看该组织待办，请切换组织或联系管理员。",
        "switch-context"
      )
    }
    return createWorkbenchSummaryState(
      "network-error",
      "暂时无法加载数据",
      "网络或服务暂不可用，请稍后重试。",
      "retry"
    )
  }

  const summary = normalizeCompleteWorkbenchSummary(source.summary, contextId)
  if (!summary) {
    return createWorkbenchSummaryState(
      "network-error",
      "待办加载失败",
      "待办数据返回不完整，请重试。",
      "retry"
    )
  }
  return createWorkbenchSummaryState("ready", "", "", "", summary)
}

function workbenchSummaryTodoCount(summary) {
  const count = key => {
    const value = Number(summary && summary[key])
    return Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0
  }
  const aggregateCount = count("lowStockCount") +
    count("pendingReceiveCount") +
    count("pendingDeliverCount") +
    count("pendingApprovalCount")
  const detailedCount = count("lowStockCount") +
    count("purchaseReceiveCount") +
    count("transferReceiveCount") +
    count("deliveryNoticeCount") +
    count("transferDeliverCount") +
    count("pendingApprovalCount")
  return Math.max(count("todoCount"), aggregateCount, detailedCount)
}

function normalizeMobileActionPath(path) {
  const pathname = String(path || "").split(/[?#]/)[0]
  return pathname.length > 1 ? pathname.replace(/\/+$/, "") : pathname
}

function resolveMobileWorkbenchTodos(todos, summary, fallbackAction) {
  const source = Array.isArray(todos) ? todos.slice() : []
  const hasPositiveTodo = source.some(item => Number(item && item.count) > 0)
  if (hasPositiveTodo) {
    return source.sort((left, right) => {
      return Number(Number(right && right.count) > 0) - Number(Number(left && left.count) > 0)
    })
  }

  const todoCount = workbenchSummaryTodoCount(summary)
  if (todoCount <= 0) return []
  const fallbackPath = normalizeMobileActionPath(fallbackAction && fallbackAction.path)
  const fallbackAllowed = Boolean(
    fallbackPath &&
    fallbackAction &&
    fallbackAction.allowed !== false &&
    source.some(item => normalizeMobileActionPath(item && item.path) === fallbackPath)
  )
  if (!fallbackAllowed) return []
  return [Object.assign({}, fallbackAction || {}, {
    label: "待办汇总",
    meta: "当前组织还有未归入快捷分类的待办，进入列表查看详情。",
    count: String(todoCount),
    unit: "项",
    icon: "check",
    tone: "blue"
  })]
}

function resolveMobileListState(options = {}) {
  const source = options || {}
  const errorMessage = source.errorMessage || source.error || ""
  const messageText = String(errorMessage || "")
  const sessionExpired = source.errorType === "session" ||
    source.sessionExpired === true ||
    source.authExpired === true ||
    String(source.status) === "401" ||
    /登录状态已过期|登录已过期|会话失效|请重新登录|未登录/i.test(messageText)
  const contextRequired = source.errorType === "context-required" ||
    source.contextRequired === true ||
    /请先选择|未选择组织|选择店铺或仓库|选择组织后/i.test(messageText)
  const contextMismatch = source.errorType === "context-mismatch" ||
    source.contextMismatch === true ||
    /组织不能使用|不支持该功能|组织类型不匹配|切换到适用组织/i.test(messageText)
  const permissionError = source.errorType === "permission" ||
    source.permissionError === true ||
    source.isPermissionError === true ||
    String(source.status) === "403" ||
    (/权限|无权|未授权|NotPermission|Forbidden/i.test(messageText) && !sessionExpired)

  // Blocking states are mutual exclusive and ordered by priority.
  if (sessionExpired) {
    return {
      type: "session-expired",
      title: "登录状态已过期",
      description: String(errorMessage || "请重新登录后继续操作。"),
      actionId: "relogin"
    }
  }

  if (permissionError) {
    return {
      type: "permission-error",
      title: "你没有查看此内容的权限",
      description: String(errorMessage || "当前账号没有该列表权限。"),
      actionId: "switch-context"
    }
  }

  if (contextRequired) {
    return {
      type: "context-required",
      title: "选择店铺或仓库后继续",
      description: String(errorMessage || "选择组织后才能查看列表。"),
      actionId: "switch-context"
    }
  }

  if (contextMismatch) {
    return {
      type: "context-mismatch",
      title: "请切换到适用组织后继续",
      description: String(errorMessage || "当前组织类型不支持该功能。"),
      actionId: "switch-context"
    }
  }

  if (source.loading === true) {
    return {
      type: "loading",
      title: "正在加载业务内容",
      description: "正在获取最新数据，请稍候。",
      actionId: ""
    }
  }

  if (errorMessage || source.errorType) {
    return {
      type: "network-error",
      title: "暂时无法加载数据",
      description: String(errorMessage || "接口暂不可用，请稍后重试"),
      actionId: "retry"
    }
  }

  const items = Array.isArray(source.items)
    ? source.items
    : Array.isArray(source.displayItems) ? source.displayItems : []
  const parsedItemCount = Number(source.itemCount)
  const itemCount = source.itemCount !== undefined && Number.isFinite(parsedItemCount)
    ? parsedItemCount
    : items.length

  if (itemCount > 0) return null

  const hasActiveFilters = source.hasActiveFilters === true ||
    source.hasFilters === true ||
    source.filtered === true ||
    Boolean(source.searchKeyword)

  if (hasActiveFilters) {
    return {
      type: "filtered-empty",
      title: "没有符合当前条件的记录",
      description: "当前搜索或筛选没有匹配数据。",
      actionId: "clear-filters"
    }
  }

  return {
    type: "empty",
    title: "还没有" + (source.listTitle || "相关记录"),
    description: "当前列表还没有数据。",
    actionId: source.canCreate === true ? "create" : "refresh"
  }
}

module.exports = {
  DEFAULT_PRIMARY_ACTION_LIMIT,
  createClearedFeatureQuery,
  filterMobileShortcutActionsForListState,
  hasMobileFeatureQueryOverrides,
  partitionMobileActions,
  prioritizeMobileFeatureActions,
  resolveMobileListStateActionLabel,
  resolveMobileListState,
  resolveMobileWorkbenchSummaryState,
  resolveMobileWorkbenchTodos,
  isWorkbenchSessionError,
  isWorkbenchPermissionError
}
