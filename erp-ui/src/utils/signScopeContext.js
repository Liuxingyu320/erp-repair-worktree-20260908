const SIGN_SCOPE_DEPT_ID_KEY = "selected_sign_scope_dept_id"
const SIGN_SCOPE_DEPT_NAME_KEY = "selected_sign_scope_dept_name"
const SIGN_SCOPE_DEPT_TYPE_KEY = "selected_sign_scope_dept_type"
const SIGN_SCOPE_BROADCAST_CHANNEL = "erp-sign-scope-session"
const SIGN_SCOPE_CLEAR_MESSAGE = "clear-selected-sign-scope"
const SIGN_SCOPE_STORAGE_EVENT_KEY = "erp_sign_scope_clear_nonce"

export const SIGN_SCOPE_HEADER = "Sign-Scope-Dept-Id"
export const SIGN_SCOPE_TYPES = ["STORE", "COMPANY"]

function storage() {
  if (typeof sessionStorage === "undefined") return null
  try {
    return sessionStorage
  } catch (e) {
    return null
  }
}

function fallbackStorage() {
  if (typeof localStorage === "undefined") return null
  try {
    return localStorage
  } catch (e) {
    return null
  }
}

function safeGetItem(target, key) {
  if (!target) return null
  try {
    return target.getItem(key)
  } catch (e) {
    return null
  }
}

function safeSetItem(target, key, value) {
  if (!target) return false
  try {
    target.setItem(key, value)
    return true
  } catch (e) {
    return false
  }
}

function safeRemoveItem(target, key) {
  if (!target) return false
  try {
    target.removeItem(key)
    return true
  } catch (e) {
    return false
  }
}

function normalizeId(value) {
  if (value === undefined || value === null) return ""
  const text = String(value).trim()
  return /^[1-9]\d{0,18}$/.test(text) ? text : ""
}

function normalizeType(value) {
  return value ? String(value).trim().toUpperCase() : ""
}

function emitChange(detail) {
  if (typeof window === "undefined" || typeof window.dispatchEvent !== "function") return
  try {
    const event = typeof CustomEvent === "function"
      ? new CustomEvent("erp:sign-scope-changed", { detail })
      : { type: "erp:sign-scope-changed", detail }
    window.dispatchEvent(event)
  } catch (e) {
    // Local teardown remains authoritative when UI notification is unavailable.
  }
}

function clearLocalSignScope() {
  const target = storage()
  safeRemoveItem(target, SIGN_SCOPE_DEPT_ID_KEY)
  safeRemoveItem(target, SIGN_SCOPE_DEPT_NAME_KEY)
  safeRemoveItem(target, SIGN_SCOPE_DEPT_TYPE_KEY)
  emitChange({ deptId: null, deptName: "", deptType: "" })
}

function broadcastFallbackClear() {
  const nonce = `${Date.now()}:${Math.random()}`
  safeSetItem(fallbackStorage(), SIGN_SCOPE_STORAGE_EVENT_KEY, nonce)
}

function registerStorageFallback() {
  if (typeof window === "undefined" || typeof window.addEventListener !== "function") return
  try {
    window.addEventListener("storage", event => {
      if (event && event.key === SIGN_SCOPE_STORAGE_EVENT_KEY) clearLocalSignScope()
    })
  } catch (e) {
    // Cross-tab propagation remains best-effort.
  }
}

function createSignScopeBroadcastChannel() {
  if (typeof BroadcastChannel !== "function") return null
  try {
    const channel = new BroadcastChannel(SIGN_SCOPE_BROADCAST_CHANNEL)
    channel.onmessage = event => {
      if (event && event.data && event.data.type === SIGN_SCOPE_CLEAR_MESSAGE) clearLocalSignScope()
    }
    return channel
  } catch (e) {
    return null
  }
}

const signScopeBroadcastChannel = createSignScopeBroadcastChannel()
registerStorageFallback()

export function isValidSignScopeType(value) {
  return SIGN_SCOPE_TYPES.includes(normalizeType(value))
}

export function getSelectedSignScopeContext() {
  const target = storage()
  const deptId = normalizeId(safeGetItem(target, SIGN_SCOPE_DEPT_ID_KEY))
  const deptType = normalizeType(safeGetItem(target, SIGN_SCOPE_DEPT_TYPE_KEY))
  return {
    deptId,
    deptName: safeGetItem(target, SIGN_SCOPE_DEPT_NAME_KEY) || "",
    deptType,
    valid: !!deptId && isValidSignScopeType(deptType)
  }
}

export function getSelectedSignScopeDeptId() {
  const context = getSelectedSignScopeContext()
  return context.valid ? context.deptId : null
}

export function setSelectedSignScope(option) {
  const source = option || {}
  const deptId = normalizeId(source.deptId)
  const deptType = normalizeType(source.deptType)
  if (!deptId || !isValidSignScopeType(deptType)) return false
  const target = storage()
  if (!target) return false
  const stored = safeSetItem(target, SIGN_SCOPE_DEPT_ID_KEY, deptId) &&
    safeSetItem(target, SIGN_SCOPE_DEPT_NAME_KEY, source.deptName || "") &&
    safeSetItem(target, SIGN_SCOPE_DEPT_TYPE_KEY, deptType)
  if (!stored) {
    safeRemoveItem(target, SIGN_SCOPE_DEPT_ID_KEY)
    safeRemoveItem(target, SIGN_SCOPE_DEPT_NAME_KEY)
    safeRemoveItem(target, SIGN_SCOPE_DEPT_TYPE_KEY)
    return false
  }
  emitChange({ deptId, deptName: source.deptName || "", deptType })
  return true
}

export function clearSelectedSignScope() {
  clearLocalSignScope()
  if (signScopeBroadcastChannel) {
    try {
      signScopeBroadcastChannel.postMessage({ type: SIGN_SCOPE_CLEAR_MESSAGE })
      return
    } catch (e) {
      // Fall through to the same-origin storage-event channel.
    }
  }
  broadcastFallbackClear()
}
