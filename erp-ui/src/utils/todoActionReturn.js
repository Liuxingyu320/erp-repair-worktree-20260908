const TODO_ACTION_RETURN_KEY = "erp.todo_action_return.v1"
const TODO_ACTION_RETURN_VERSION = 2
const DEFAULT_TODO_ACTION_RETURN_TTL_MS = 30 * 60 * 1000
const TODO_RETURN_PATH = "/workbench/todo"

function resolveStorage(options = {}) {
  if (Object.prototype.hasOwnProperty.call(options, "storage")) return options.storage
  if (typeof sessionStorage === "undefined") return null
  try { return sessionStorage } catch (error) { return null }
}

function resolveNow(options = {}) {
  const source = options.now
  const value = typeof source === "function" ? source() : source
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : Date.now()
}

function normalizeTodoKey(value) {
  const normalized = value === undefined || value === null ? "" : String(value).trim()
  return normalized && normalized.length <= 512 ? normalized : ""
}

function normalizeBusinessTarget(todoType, businessId) {
  const type = todoType === undefined || todoType === null ? "" : String(todoType).trim().toUpperCase()
  const id = businessId === undefined || businessId === null ? "" : String(businessId).trim()
  if (!type || type.length > 128 || !/^[A-Z0-9_:-]+$/.test(type)) return null
  if (!id || id.length > 128 || !/^[A-Za-z0-9._:-]+$/.test(id)) return null
  return { todoType: type, businessId: id }
}

function normalizeReturnRoute(route) {
  const source = route || {}
  const path = String(source.path || "").trim()
  const fullPath = String(source.fullPath || path).trim()
  if (path !== TODO_RETURN_PATH) return null
  if (fullPath !== TODO_RETURN_PATH && !fullPath.startsWith(`${TODO_RETURN_PATH}?`) && !fullPath.startsWith(`${TODO_RETURN_PATH}#`)) {
    return null
  }
  if (fullPath.length > 4096 || /[\r\n]/.test(fullPath)) return null
  return { path: TODO_RETURN_PATH, fullPath }
}

function normalizeStoredContext(value) {
  if (!value || typeof value !== "object" || value.version !== TODO_ACTION_RETURN_VERSION) return null
  const returnRoute = normalizeReturnRoute(value.returnRoute)
  const todoKey = normalizeTodoKey(value.todoKey)
  const nextTodoKey = normalizeTodoKey(value.nextTodoKey)
  const target = normalizeBusinessTarget(value.todoType, value.businessId)
  const scrollTop = Number(value.scrollTop)
  const createdAt = Number(value.createdAt)
  const expiresAt = Number(value.expiresAt)
  if (!returnRoute || !todoKey || !target || !Number.isFinite(createdAt) || !Number.isFinite(expiresAt) || expiresAt <= createdAt) return null
  return {
    version: TODO_ACTION_RETURN_VERSION,
    returnRoute,
    todoKey,
    nextTodoKey,
    todoType: target.todoType,
    businessId: target.businessId,
    scrollTop: Number.isFinite(scrollTop) && scrollTop >= 0 ? scrollTop : 0,
    createdAt,
    expiresAt
  }
}

function removeStoredContext(storage) {
  try {
    storage.removeItem(TODO_ACTION_RETURN_KEY)
    return true
  } catch (error) {
    return false
  }
}

function clearTodoActionReturn(options = {}) {
  const storage = resolveStorage(options)
  if (!storage || typeof storage.removeItem !== "function") return { ok: false, reason: "storage_unavailable" }
  return removeStoredContext(storage)
    ? { ok: true, cleared: true }
    : { ok: false, reason: "storage_unavailable" }
}

function beginTodoActionReturn(options = {}) {
  const storage = resolveStorage(options)
  if (!storage || typeof storage.setItem !== "function" || typeof storage.getItem !== "function" ||
    typeof storage.removeItem !== "function") {
    return { ok: false, reason: "storage_unavailable" }
  }
  const returnRoute = normalizeReturnRoute(options.returnRoute)
  const todoKey = normalizeTodoKey(options.todoKey)
  const target = normalizeBusinessTarget(options.todoType, options.businessId)
  if (!returnRoute || !todoKey || !target) {
    removeStoredContext(storage)
    return { ok: false, reason: "invalid_context" }
  }
  const now = resolveNow(options)
  const requestedTtl = Number(options.ttlMs)
  const ttlMs = Number.isFinite(requestedTtl) && requestedTtl > 0
    ? requestedTtl
    : DEFAULT_TODO_ACTION_RETURN_TTL_MS
  const context = {
    version: TODO_ACTION_RETURN_VERSION,
    returnRoute,
    todoKey,
    nextTodoKey: normalizeTodoKey(options.nextTodoKey),
    todoType: target.todoType,
    businessId: target.businessId,
    scrollTop: Math.max(0, Number(options.scrollTop) || 0),
    createdAt: now,
    expiresAt: now + ttlMs
  }
  const serialized = JSON.stringify(context)
  try {
    storage.setItem(TODO_ACTION_RETURN_KEY, serialized)
    if (storage.getItem(TODO_ACTION_RETURN_KEY) !== serialized) {
      removeStoredContext(storage)
      return { ok: false, reason: "storage_unavailable" }
    }
  } catch (error) {
    removeStoredContext(storage)
    return { ok: false, reason: "storage_unavailable" }
  }
  return { ok: true, context }
}

function readTodoActionReturn(options = {}) {
  const storage = resolveStorage(options)
  if (!storage || typeof storage.getItem !== "function" || typeof storage.removeItem !== "function") {
    return { ok: false, context: null, reason: "storage_unavailable" }
  }
  let raw
  try { raw = storage.getItem(TODO_ACTION_RETURN_KEY) } catch (error) {
    return { ok: false, context: null, reason: "storage_unavailable" }
  }
  if (!raw) return { ok: true, context: null, reason: "missing" }
  let parsed
  try { parsed = JSON.parse(raw) } catch (error) {
    removeStoredContext(storage)
    return { ok: true, context: null, reason: "invalid" }
  }
  const context = normalizeStoredContext(parsed)
  if (!context) {
    removeStoredContext(storage)
    return { ok: true, context: null, reason: "invalid" }
  }
  if (resolveNow(options) >= context.expiresAt) {
    removeStoredContext(storage)
    return { ok: true, context: null, reason: "expired" }
  }
  return { ok: true, context }
}

function consumeTodoActionReturn(options = {}) {
  const result = readTodoActionReturn(options)
  if (!result.ok || !result.context) return result
  const cleared = clearTodoActionReturn(options)
  return cleared.ok
    ? { ok: true, context: result.context, reason: "consumed" }
    : { ok: false, context: null, reason: cleared.reason }
}

async function returnAfterTodoAction(options = {}) {
  const result = readTodoActionReturn(options)
  if (!result.ok || !result.context) return { ok: result.ok, returned: false, reason: result.reason }
  const router = options.router
  const expectedTarget = normalizeBusinessTarget(options.todoType, options.businessId)
  if (!expectedTarget || expectedTarget.todoType !== result.context.todoType ||
    expectedTarget.businessId !== result.context.businessId) {
    clearTodoActionReturn(options)
    return { ok: false, returned: false, reason: "target_mismatch" }
  }
  if (!router || typeof router.replace !== "function") {
    clearTodoActionReturn(options)
    return { ok: false, returned: false, reason: "router_unavailable" }
  }
  try {
    await router.replace(result.context.returnRoute.fullPath)
    return { ok: true, returned: true, context: result.context }
  } catch (error) {
    clearTodoActionReturn(options)
    return { ok: false, returned: false, reason: "navigation_failed", error }
  }
}

module.exports = {
  DEFAULT_TODO_ACTION_RETURN_TTL_MS,
  TODO_ACTION_RETURN_KEY,
  TODO_ACTION_RETURN_VERSION,
  TODO_RETURN_PATH,
  beginTodoActionReturn,
  clearTodoActionReturn,
  consumeTodoActionReturn,
  normalizeBusinessTarget,
  normalizeReturnRoute,
  readTodoActionReturn,
  returnAfterTodoAction
}
