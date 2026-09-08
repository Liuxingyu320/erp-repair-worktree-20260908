const {
  getRouteDefinition,
  normalizeContext,
  resolveTodoRoute,
  sameOrganization,
  validOrganization
} = require("./todoRouteResolver")

const ERROR_MESSAGES = Object.freeze({
  permission_changed: "待办权限已发生变化，请刷新后重试",
  route_missing: "待办处理页面当前不可用，请刷新后重试",
  organization_required: "请先切换到待办所属的门店或仓库",
  context_lease_unavailable: "无法安全暂存原组织，请稍后重试"
})
const activeNavigations = new WeakMap()

function readOption(options, getterName, valueName, fallback) {
  if (typeof options[getterName] === "function") {
    return options[getterName]()
  }
  return options[valueName] === undefined ? fallback : options[valueName]
}

async function reportFailure(result, options) {
  const reason = result.reason || "route_missing"
  if (typeof options.showError === "function") {
    try {
      options.showError(ERROR_MESSAGES[reason] || ERROR_MESSAGES.route_missing)
    } catch (error) {
      // Reporting must not replace the navigation failure.
    }
  }
  if (typeof options.refreshSummaries === "function") {
    try {
      await options.refreshSummaries()
    } catch (error) {
      // A failed refresh must not replace the original reason.
    }
  }
  return { ok: false, reason }
}

function resolveWithCurrentOptions(item, options, currentContext) {
  return resolveTodoRoute(item, {
    platform: options.platform,
    permissions: readOption(options, "getPermissions", "permissions", []),
    availableRouteSet: readOption(options, "getAvailableRouteSet", "availableRouteSet", new Set()),
    currentContext
  })
}

function isRouterFailure(result) {
  return !isNavigationDuplicated(result) && (result instanceof Error || !!(result && (
    result.ok === false ||
    result.success === false ||
    result.failure ||
    result.error instanceof Error
  )))
}

function isNavigationDuplicated(result) {
  return result instanceof Error && result.name === "NavigationDuplicated"
}

async function rollbackContextLease(options, leasePayload) {
  if (!leasePayload || typeof options.rollbackContextLease !== "function") return
  try {
    await options.rollbackContextLease(leasePayload)
  } catch (error) {
    // The original navigation failure remains authoritative.
  }
}

async function reportFailureWithRollback(result, options, leasePayload) {
  await rollbackContextLease(options, leasePayload)
  return reportFailure(result, options)
}

function reportContextSwitch(options, leasePayload) {
  if (!leasePayload || typeof options.showContextNotice !== "function") return
  try {
    const result = options.showContextNotice(leasePayload)
    if (result && typeof result.then === "function") Promise.resolve(result).catch(() => {})
  } catch (error) {
    // Informational copy must not replace a successful navigation.
  }
}

async function performTodoNavigation(item, options) {
  const route = getRouteDefinition(item && item.type)
  const itemContext = normalizeContext(item)
  const preflightContext = route && route.organizationBound ? itemContext :
    readOption(options, "getCurrentContext", "currentContext", null)
  const preflight = resolveWithCurrentOptions(item, options, preflightContext)
  if (!preflight.ok) {
    return reportFailure(preflight, options)
  }

  let currentContext = readOption(options, "getCurrentContext", "currentContext", null)
  let leasePayload = null
  if (route.organizationBound && !sameOrganization(itemContext, currentContext)) {
    if (!validOrganization(itemContext) || typeof options.setSelectedDept !== "function") {
      return reportFailure({ ok: false, reason: "organization_required" }, options)
    }
    if (typeof options.beginContextLease !== "function" || typeof options.rollbackContextLease !== "function") {
      return reportFailure({ ok: false, reason: "context_lease_unavailable" }, options)
    }

    let returnRoute
    try {
      returnRoute = readOption(options, "getReturnRoute", "returnRoute", null)
    } catch (error) {
      return reportFailure({ ok: false, reason: "context_lease_unavailable" }, options)
    }
    leasePayload = {
      originContext: currentContext,
      targetContext: itemContext,
      returnRoute
    }
    let leaseResult
    try {
      leaseResult = options.beginContextLease(leasePayload)
    } catch (error) {
      return reportFailure({ ok: false, reason: "context_lease_unavailable" }, options)
    }
    if (leaseResult && typeof leaseResult.then === "function") {
      Promise.resolve(leaseResult).catch(() => {})
      return reportFailure({ ok: false, reason: "context_lease_unavailable" }, options)
    }
    if (!leaseResult || leaseResult.ok !== true) {
      leasePayload = null
      return reportFailure({ ok: false, reason: "context_lease_unavailable" }, options)
    }
    leasePayload = {
      ...leasePayload,
      contextLease: leaseResult.lease || null
    }

    let contextChangeResult
    try {
      contextChangeResult = options.setSelectedDept(
        itemContext.deptId,
        itemContext.deptName,
        itemContext.deptType
      )
    } catch (error) {
      return reportFailureWithRollback({ ok: false, reason: "organization_required" }, options, leasePayload)
    }
    if (contextChangeResult && typeof contextChangeResult.then === "function") {
      Promise.resolve(contextChangeResult).catch(() => {})
      return reportFailureWithRollback({ ok: false, reason: "organization_required" }, options, leasePayload)
    }
    try {
      currentContext = readOption(options, "getCurrentContext", "currentContext", null)
    } catch (error) {
      return reportFailureWithRollback({ ok: false, reason: "organization_required" }, options, leasePayload)
    }
    if (!sameOrganization(itemContext, currentContext)) {
      return reportFailureWithRollback({ ok: false, reason: "organization_required" }, options, leasePayload)
    }
  }

  const resolved = resolveWithCurrentOptions(item, options, currentContext)
  if (!resolved.ok) {
    return reportFailureWithRollback(resolved, options, leasePayload)
  }
  if (!options.router || typeof options.router.push !== "function") {
    return reportFailureWithRollback({ ok: false, reason: "route_missing" }, options, leasePayload)
  }

  try {
    const pushResult = await options.router.push(resolved.location)
    if (isRouterFailure(pushResult)) {
      return reportFailureWithRollback({ ok: false, reason: "route_missing" }, options, leasePayload)
    }
    reportContextSwitch(options, leasePayload)
    return resolved
  } catch (error) {
    if (isNavigationDuplicated(error)) {
      reportContextSwitch(options, leasePayload)
      return resolved
    }
    return reportFailureWithRollback({ ok: false, reason: "route_missing" }, options, leasePayload)
  }
}

function navigationKey(item) {
  const source = item || {}
  if (source.todoKey !== undefined && source.todoKey !== null && String(source.todoKey)) {
    return String(source.todoKey)
  }
  const routeParams = source.routeParams && typeof source.routeParams === "object" ? source.routeParams : {}
  const businessId = source.businessId ?? routeParams.businessId ?? source.purchaseId ?? source.id ?? ""
  const deptId = source.contextDeptId ?? source.deptId ?? ""
  return [source.source || "", source.type || "", businessId, deptId].map(String).join(":")
}

function navigateTodo(item, options = {}) {
  const router = options.router
  const canDeduplicate = router && (typeof router === "object" || typeof router === "function")
  if (!canDeduplicate) {
    return performTodoNavigation(item, options)
  }

  let routerNavigations = activeNavigations.get(router)
  if (!routerNavigations) {
    routerNavigations = new Map()
    activeNavigations.set(router, routerNavigations)
  }
  const key = navigationKey(item)
  const active = routerNavigations.get(key)
  if (active) return active

  const navigation = Promise.resolve().then(() => performTodoNavigation(item, options))
  routerNavigations.set(key, navigation)
  navigation.finally(() => {
    if (routerNavigations.get(key) === navigation) routerNavigations.delete(key)
  }).catch(() => {})
  return navigation
}

module.exports = {
  ERROR_MESSAGES,
  isNavigationDuplicated,
  isRouterFailure,
  navigateTodo
}
