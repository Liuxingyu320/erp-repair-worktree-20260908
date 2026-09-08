/**
 * Mutual-exclusive mobile page/list state model.
 * Priority: session > permission > context > network > loading > empty/filtered-empty > ready.
 */

const PAGE_STATE_PRIORITY = [
  "session-expired",
  "permission-error",
  "context-required",
  "context-mismatch",
  "network-error",
  "loading",
  "filtered-empty",
  "empty",
  "feature-closed",
  "ready"
]

const STATE_COPY = {
  "session-expired": {
    title: "登录状态已过期",
    description: "请重新登录后继续操作。",
    actionId: "relogin",
    actionLabel: "重新登录"
  },
  "permission-error": {
    title: "你没有查看此内容的权限",
    description: "可返回有效入口，或联系管理员开通权限。",
    actionId: "go-home",
    actionLabel: "返回有效入口"
  },
  "context-required": {
    title: "选择店铺或仓库后继续",
    description: "当前页面需要组织上下文后才能加载业务数据。",
    actionId: "select-context",
    actionLabel: "选择组织"
  },
  "context-mismatch": {
    title: "请切换到适用组织后继续",
    description: "当前组织类型不支持该功能。",
    actionId: "switch-context",
    actionLabel: "切换组织"
  },
  "network-error": {
    title: "暂时无法加载数据",
    description: "网络或服务暂不可用，请稍后重试。",
    actionId: "retry",
    actionLabel: "重试"
  },
  loading: {
    title: "正在加载业务内容",
    description: "正在获取最新数据，请稍候。",
    actionId: "",
    actionLabel: ""
  },
  empty: {
    title: "还没有相关记录",
    description: "当前列表还没有数据。",
    actionId: "refresh",
    actionLabel: "刷新"
  },
  "filtered-empty": {
    title: "没有符合当前条件的记录",
    description: "当前搜索或筛选没有匹配数据。",
    actionId: "clear-filters",
    actionLabel: "清除筛选"
  },
  "feature-closed": {
    title: "该功能当前未开放",
    description: "请查看说明或返回其他入口。",
    actionId: "go-home",
    actionLabel: "返回"
  },
  ready: {
    title: "",
    description: "",
    actionId: "",
    actionLabel: ""
  }
}

const ACTION_LABELS = {
  retry: "重试",
  refresh: "刷新",
  create: "新增",
  "clear-filters": "清除筛选",
  "select-context": "选择组织",
  "switch-context": "切换组织",
  relogin: "重新登录",
  "go-home": "返回有效入口",
  "load-latest": "加载最新状态"
}

function normalizeText(value) {
  return value === undefined || value === null ? "" : String(value).trim()
}

function isSessionExpiredSignal(source = {}) {
  if (source.sessionExpired === true || source.authExpired === true || source.errorType === "session") {
    return true
  }
  const status = source.status !== undefined ? source.status : source.code
  if (String(status) === "401") return true
  const message = normalizeText(source.errorMessage || source.error || source.message)
  return /登录状态已过期|登录已过期|会话失效|token\s*过期|未登录|请重新登录/i.test(message)
}

function isPermissionSignal(source = {}) {
  if (
    source.permissionError === true ||
    source.isPermissionError === true ||
    source.errorType === "permission"
  ) {
    return true
  }
  const status = source.status !== undefined ? source.status : source.code
  if (String(status) === "403") return true
  const message = normalizeText(source.errorMessage || source.error || source.message)
  return /权限|无权|未授权|NotPermission|Forbidden/i.test(message)
}

function isContextRequiredSignal(source = {}) {
  if (source.contextRequired === true || source.errorType === "context-required") return true
  const contextId = source.contextId !== undefined ? source.contextId : source.selectedDeptId
  if (source.requireContext === true && (contextId === undefined || contextId === null || String(contextId).trim() === "")) {
    return true
  }
  const message = normalizeText(source.errorMessage || source.error || source.message)
  return /请先选择|未选择组织|选择店铺或仓库|选择组织后/i.test(message)
}

function isContextMismatchSignal(source = {}) {
  if (source.contextMismatch === true || source.errorType === "context-mismatch") return true
  const message = normalizeText(source.errorMessage || source.error || source.message)
  return /组织不能使用|不支持该功能|组织类型不匹配|切换到适用组织/i.test(message)
}

function createPageState(type, overrides = {}) {
  const defaults = STATE_COPY[type] || STATE_COPY["network-error"]
  return {
    type,
    title: overrides.title !== undefined ? overrides.title : defaults.title,
    description: overrides.description !== undefined ? overrides.description : defaults.description,
    actionId: overrides.actionId !== undefined ? overrides.actionId : defaults.actionId,
    actionLabel: overrides.actionLabel !== undefined ? overrides.actionLabel : defaults.actionLabel,
    priority: PAGE_STATE_PRIORITY.indexOf(type)
  }
}

/**
 * Resolve a single page-level blocking state.
 * Returns null when content should render (ready / partial success).
 */
function resolveMobilePageState(options = {}) {
  const source = options || {}

  if (isSessionExpiredSignal(source)) {
    return createPageState("session-expired", {
      description: normalizeText(source.errorMessage || source.error) || STATE_COPY["session-expired"].description
    })
  }

  if (isPermissionSignal(source) && !isSessionExpiredSignal(source)) {
    return createPageState("permission-error", {
      description: normalizeText(source.errorMessage || source.error) || STATE_COPY["permission-error"].description,
      actionId: source.permissionActionId || "go-home",
      actionLabel: source.permissionActionLabel || STATE_COPY["permission-error"].actionLabel
    })
  }

  if (isContextRequiredSignal(source)) {
    return createPageState("context-required", {
      description: normalizeText(source.errorMessage || source.error) || STATE_COPY["context-required"].description
    })
  }

  if (isContextMismatchSignal(source)) {
    return createPageState("context-mismatch", {
      description: normalizeText(source.errorMessage || source.error) || STATE_COPY["context-mismatch"].description
    })
  }

  if (source.featureClosed === true || source.errorType === "feature-closed") {
    return createPageState("feature-closed", {
      description: normalizeText(source.errorMessage || source.error) || STATE_COPY["feature-closed"].description
    })
  }

  const errorMessage = normalizeText(source.errorMessage || source.error)
  if (errorMessage || source.errorType === "network") {
    return createPageState("network-error", {
      description: errorMessage || STATE_COPY["network-error"].description,
      actionId: "retry"
    })
  }

  if (source.loading === true) {
    return createPageState("loading", {
      title: source.loadingTitle || STATE_COPY.loading.title,
      description: source.loadingDescription || STATE_COPY.loading.description
    })
  }

  if (source.ready === false) {
    return createPageState("loading")
  }

  const items = Array.isArray(source.items)
    ? source.items
    : Array.isArray(source.displayItems)
      ? source.displayItems
      : null
  const parsedItemCount = Number(source.itemCount)
  const itemCount = source.itemCount !== undefined && Number.isFinite(parsedItemCount)
    ? parsedItemCount
    : items
      ? items.length
      : null

  if (itemCount !== null && itemCount <= 0) {
    const hasActiveFilters = source.hasActiveFilters === true ||
      source.hasFilters === true ||
      source.filtered === true ||
      Boolean(source.searchKeyword)

    if (hasActiveFilters) {
      return createPageState("filtered-empty", {
        description: source.filteredEmptyDescription || STATE_COPY["filtered-empty"].description
      })
    }

    return createPageState("empty", {
      title: source.emptyTitle || ("还没有" + (source.listTitle || "相关记录")),
      description: source.emptyDescription || STATE_COPY.empty.description,
      actionId: source.canCreate === true ? "create" : (source.emptyActionId || "refresh"),
      actionLabel: source.canCreate === true
        ? (source.createLabel || "新增")
        : (source.emptyActionLabel || "刷新")
    })
  }

  return null
}

function resolveMobilePageStateActionLabel(state, actions) {
  if (!state || !state.actionId) return ""
  if (state.actionLabel) return state.actionLabel
  if (state.actionId === "create") {
    const createAction = (Array.isArray(actions) ? actions : [])
      .find(action => action && action.behavior === "create-form")
    return createAction && createAction.label ? createAction.label : "新增"
  }
  return ACTION_LABELS[state.actionId] || "继续"
}

/**
 * Business status visual tone mapping.
 * Always pair with text via MobileStatusChip / status labels.
 */
function resolveMobileStatusTone(status) {
  const text = normalizeText(status)
  if (!text) return "neutral"
  if (/草稿|停用|关闭|禁用|作废|已取消|未启用/.test(text)) return "draft"
  if (/驳回|失败|异常|退回|拒绝|拒签|超期|过期|错误|风险/.test(text)) return "danger"
  if (/待|审批|补充|临期|确认|验收|审核|处理|签署|阅读/.test(text) && !/已/.test(text)) return "pending"
  if (/中|识别|同步|进行|上传|加载/.test(text)) return "processing"
  if (/完成|通过|付款|成功|已验收|已签署|正常|启用|合作/.test(text)) return "success"
  return "neutral"
}

function isBlockingPageState(state) {
  if (!state || !state.type) return false
  return ["session-expired", "permission-error", "context-required", "context-mismatch", "network-error", "feature-closed", "loading"].includes(state.type)
}

module.exports = {
  PAGE_STATE_PRIORITY,
  STATE_COPY,
  ACTION_LABELS,
  createPageState,
  resolveMobilePageState,
  resolveMobilePageStateActionLabel,
  resolveMobileStatusTone,
  isBlockingPageState,
  isSessionExpiredSignal,
  isPermissionSignal,
  isContextRequiredSignal,
  isContextMismatchSignal
}
