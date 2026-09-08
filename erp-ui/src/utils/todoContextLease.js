const TODO_CONTEXT_LEASE_VERSION = 2
const TODO_CONTEXT_LEASE_KEY = "erp.todo_context_lease.v1"
const DEFAULT_TODO_CONTEXT_LEASE_TTL_MS = 30 * 60 * 1000
const RESTORABLE_DEPT_TYPES = new Set(["STORE", "WAREHOUSE", "COMPANY", "GROUP"])
let leaseSequence = 0

function resolveStorage(options) {
  if (options && Object.prototype.hasOwnProperty.call(options, "storage")) {
    return options.storage
  }
  if (typeof sessionStorage === "undefined") return null
  try {
    return sessionStorage
  } catch (error) {
    return null
  }
}

function resolveNow(options) {
  const source = options && options.now
  const value = typeof source === "function" ? source() : source
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : Date.now()
}

function normalizeContext(source) {
  const value = source || {}
  const deptId = value.deptId !== undefined ? value.deptId : value.contextDeptId
  const deptName = value.deptName !== undefined ? value.deptName : value.contextDeptName
  const deptType = value.deptType !== undefined ? value.deptType : value.contextDeptType
  return {
    deptId: deptId === undefined || deptId === null ? "" : String(deptId).trim(),
    deptName: deptName === undefined || deptName === null ? "" : String(deptName).trim(),
    deptType: deptType === undefined || deptType === null ? "" : String(deptType).trim().toUpperCase()
  }
}

function hasContext(source) {
  const context = normalizeContext(source)
  return !!context.deptId && RESTORABLE_DEPT_TYPES.has(context.deptType)
}

function isValidTargetContext(source) {
  const context = normalizeContext(source)
  return !!context.deptId && !!context.deptName &&
    (context.deptType === "STORE" || context.deptType === "WAREHOUSE")
}

function snapshotOrigin(source) {
  const context = normalizeContext(source)
  if (!hasContext(context)) return { hasContext: false }
  return {
    hasContext: true,
    deptId: context.deptId,
    deptName: context.deptName,
    deptType: context.deptType
  }
}

function snapshotTarget(source) {
  const context = normalizeContext(source)
  return {
    deptId: context.deptId,
    deptName: context.deptName,
    deptType: context.deptType
  }
}

function normalizeReturnRoute(source) {
  const route = source || {}
  const path = String(route.path || "").trim()
  const fullPath = String(route.fullPath || path).trim()
  if (!path.startsWith("/") || !fullPath.startsWith("/") || path.length > 2048 || fullPath.length > 4096) {
    return null
  }
  return { path, fullPath }
}

function sameContext(left, right) {
  const leftContext = normalizeContext(left)
  const rightContext = normalizeContext(right)
  return leftContext.deptId === rightContext.deptId && leftContext.deptType === rightContext.deptType
}

function normalizeStoredLease(value) {
  if (!value || typeof value !== "object" || value.version !== TODO_CONTEXT_LEASE_VERSION) return null
  const leaseId = String(value.leaseId || "").trim()
  if (!leaseId || leaseId.length > 128) return null
  const createdAt = Number(value.createdAt)
  const expiresAt = Number(value.expiresAt)
  if (!Number.isFinite(createdAt) || !Number.isFinite(expiresAt) || expiresAt <= createdAt) return null
  if (!isValidTargetContext(value.target)) return null

  let origin
  if (value.origin && value.origin.hasContext === false) {
    origin = { hasContext: false }
  } else if (value.origin && value.origin.hasContext === true && hasContext(value.origin)) {
    origin = snapshotOrigin(value.origin)
  } else {
    return null
  }

  const returnRoute = normalizeReturnRoute(value.returnRoute)
  if (!returnRoute) return null

  return {
    version: TODO_CONTEXT_LEASE_VERSION,
    leaseId,
    origin,
    target: snapshotTarget(value.target),
    returnRoute,
    createdAt,
    expiresAt
  }
}

function createLeaseId(now) {
  leaseSequence = (leaseSequence + 1) % Number.MAX_SAFE_INTEGER
  return `${now.toString(36)}-${leaseSequence.toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

function removeStoredLease(storage) {
  try {
    storage.removeItem(TODO_CONTEXT_LEASE_KEY)
    return true
  } catch (error) {
    return false
  }
}

function readTodoContextLease(options = {}) {
  const storage = resolveStorage(options)
  if (!storage || typeof storage.getItem !== "function" || typeof storage.removeItem !== "function") {
    return { ok: false, lease: null, reason: "storage_unavailable" }
  }

  let rawValue
  try {
    rawValue = storage.getItem(TODO_CONTEXT_LEASE_KEY)
  } catch (error) {
    return { ok: false, lease: null, reason: "storage_unavailable" }
  }
  if (!rawValue) return { ok: true, lease: null, reason: "missing" }

  let parsed
  try {
    parsed = JSON.parse(rawValue)
  } catch (error) {
    removeStoredLease(storage)
    return { ok: true, lease: null, reason: "invalid" }
  }

  const lease = normalizeStoredLease(parsed)
  if (!lease) {
    removeStoredLease(storage)
    return { ok: true, lease: null, reason: "invalid" }
  }
  if (resolveNow(options) >= lease.expiresAt) {
    removeStoredLease(storage)
    return { ok: true, lease: null, reason: "expired" }
  }
  return { ok: true, lease }
}

function clearTodoContextLease(options = {}) {
  const storage = resolveStorage(options)
  if (!storage || typeof storage.removeItem !== "function") {
    return { ok: false, reason: "storage_unavailable" }
  }
  return removeStoredLease(storage)
    ? { ok: true, cleared: true }
    : { ok: false, reason: "storage_unavailable" }
}

function beginTodoContextLease(options = {}) {
  const storage = resolveStorage(options)
  if (!storage || typeof storage.setItem !== "function" || typeof storage.getItem !== "function" ||
    typeof storage.removeItem !== "function") {
    return { ok: false, reason: "storage_unavailable" }
  }
  if (!isValidTargetContext(options.targetContext)) {
    return { ok: false, reason: "invalid_target" }
  }
  const requestedReturnRoute = normalizeReturnRoute(options.returnRoute)
  if (!requestedReturnRoute) {
    return { ok: false, reason: "invalid_return_route" }
  }

  const now = resolveNow(options)
  const existingResult = readTodoContextLease({ storage, now })
  if (!existingResult.ok) return { ok: false, reason: existingResult.reason }

  const currentContext = normalizeContext(options.originContext)
  const existing = existingResult.lease
  const continueExistingLease = !!existing && sameContext(currentContext, existing.target)
  const ttlValue = Number(options.ttlMs)
  const ttlMs = Number.isFinite(ttlValue) && ttlValue > 0 ? ttlValue : DEFAULT_TODO_CONTEXT_LEASE_TTL_MS
  const lease = {
    version: TODO_CONTEXT_LEASE_VERSION,
    leaseId: createLeaseId(now),
    origin: continueExistingLease ? existing.origin : snapshotOrigin(currentContext),
    target: snapshotTarget(options.targetContext),
    returnRoute: continueExistingLease ? existing.returnRoute : requestedReturnRoute,
    createdAt: continueExistingLease ? existing.createdAt : now,
    expiresAt: now + ttlMs
  }
  const serialized = JSON.stringify(lease)

  try {
    storage.setItem(TODO_CONTEXT_LEASE_KEY, serialized)
    if (storage.getItem(TODO_CONTEXT_LEASE_KEY) !== serialized) {
      removeStoredLease(storage)
      return { ok: false, reason: "storage_unavailable" }
    }
  } catch (error) {
    removeStoredLease(storage)
    return { ok: false, reason: "storage_unavailable" }
  }

  return { ok: true, lease }
}

function readCurrentContext(options) {
  try {
    const context = typeof options.getCurrentContext === "function"
      ? options.getCurrentContext()
      : options.currentContext
    return { ok: true, context: normalizeContext(context) }
  } catch (error) {
    return { ok: false, reason: "context_unavailable" }
  }
}

function isPromiseLike(value) {
  return !!value && typeof value.then === "function"
}

function applyOriginContext(origin, options) {
  try {
    const result = origin.hasContext
      ? typeof options.setSelectedDept === "function" && options.setSelectedDept(origin.deptId, origin.deptName, origin.deptType)
      : typeof options.clearSelectedDept === "function" && options.clearSelectedDept()
    if (result === false || isPromiseLike(result)) {
      if (isPromiseLike(result)) Promise.resolve(result).catch(() => {})
      return { ok: false, reason: "context_restore_failed" }
    }
  } catch (error) {
    return { ok: false, reason: "context_restore_failed" }
  }

  if (typeof options.getCurrentContext === "function") {
    const current = readCurrentContext(options)
    if (!current.ok) return current
    const restored = origin.hasContext ? sameContext(current.context, origin) : !hasContext(current.context)
    if (!restored) return { ok: false, reason: "context_restore_failed" }
  }
  return { ok: true }
}

function consumeTodoContextLease(options, lease) {
  const current = readCurrentContext(options)
  if (!current.ok) return { ok: false, restored: false, reason: current.reason }

  const alreadyAtOrigin = lease.origin.hasContext
    ? sameContext(current.context, lease.origin)
    : !hasContext(current.context)
  if (alreadyAtOrigin) {
    const cleared = clearTodoContextLease(options)
    return { ok: cleared.ok, restored: false, reason: "already_restored" }
  }
  if (!sameContext(current.context, lease.target)) {
    const cleared = clearTodoContextLease(options)
    return { ok: cleared.ok, restored: false, reason: "context_changed" }
  }

  const applied = applyOriginContext(lease.origin, options)
  if (!applied.ok) return { ok: false, restored: false, reason: applied.reason }
  const cleared = clearTodoContextLease(options)
  return { ok: cleared.ok, restored: true, reason: cleared.ok ? "restored" : cleared.reason }
}

function restoreTodoContextLeaseForRoute(route, options = {}) {
  const readResult = readTodoContextLease(options)
  if (!readResult.ok || !readResult.lease) {
    return { ok: readResult.ok, restored: false, reason: readResult.reason }
  }
  const currentRoute = normalizeReturnRoute(route)
  if (!currentRoute || currentRoute.path !== readResult.lease.returnRoute.path) {
    return { ok: true, restored: false, reason: "route_mismatch" }
  }
  return consumeTodoContextLease(options, readResult.lease)
}

function rollbackTodoContextLease(options = {}) {
  const readResult = readTodoContextLease(options)
  if (!readResult.ok || !readResult.lease) {
    return { ok: readResult.ok, restored: false, reason: readResult.reason }
  }
  const expectedLeaseId = String(options.expectedLeaseId || "").trim()
  if (expectedLeaseId && readResult.lease.leaseId !== expectedLeaseId) {
    return { ok: true, restored: false, reason: "lease_replaced" }
  }
  return consumeTodoContextLease(options, readResult.lease)
}

function formatTodoContextSwitchNotice(payload = {}) {
  const target = normalizeContext(payload.targetContext)
  const origin = snapshotOrigin(payload.originContext)
  const targetName = target.deptName || "待办所属组织"
  const restoreText = origin.hasContext && origin.deptName
    ? "恢复「" + origin.deptName + "」"
    : "恢复原组织状态"
  return "已临时切换到「" + targetName + "」，返回原页面后" + restoreText
}

module.exports = {
  TODO_CONTEXT_LEASE_KEY,
  TODO_CONTEXT_LEASE_VERSION,
  DEFAULT_TODO_CONTEXT_LEASE_TTL_MS,
  beginTodoContextLease,
  clearTodoContextLease,
  formatTodoContextSwitchNotice,
  readTodoContextLease,
  restoreTodoContextLeaseForRoute,
  rollbackTodoContextLease
}
