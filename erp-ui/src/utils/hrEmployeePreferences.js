const PAGE_SIZES = [10, 20, 30, 50, 100]
const DEFAULTS = { advancedFilterOpen: false, selectedOptionalColumns: [], pageSize: 10 }

function permissionFingerprint(permissions) {
  const text = (Array.isArray(permissions) ? permissions : []).map(String).sort().join("\u001f")
  let hash = 0x811c9dc5
  for (let index = 0; index < text.length; index += 1) {
    hash = Math.imul(hash ^ text.charCodeAt(index), 0x01000193) >>> 0
  }
  return hash.toString(16).padStart(8, "0")
}

export function preferenceKey(userId, permissions) {
  return `erp:hr-employee-preferences:v1:${Number(userId) || 0}:${permissionFingerprint(permissions)}`
}

export function sanitizeHrEmployeePreferences(value, allowedColumns) {
  const source = value && typeof value === "object" ? value : {}
  const allowed = new Set(Array.isArray(allowedColumns) ? allowedColumns : [])
  const selected = Array.isArray(source.selectedOptionalColumns) ? source.selectedOptionalColumns : []
  const uniqueAllowedColumns = Array.from(new Set(selected.filter(column => allowed.has(column))))
  const requestedPageSize = Number(source.pageSize)
  return {
    advancedFilterOpen: Boolean(source.advancedFilterOpen),
    selectedOptionalColumns: uniqueAllowedColumns,
    pageSize: PAGE_SIZES.includes(requestedPageSize) ? requestedPageSize : 10
  }
}

export function loadHrEmployeePreferences(storage, userId, permissions, allowedColumns) {
  const key = preferenceKey(userId, permissions)
  if (!storage || typeof storage.getItem !== "function") return { ...DEFAULTS, selectedOptionalColumns: [] }
  try {
    const raw = storage.getItem(key)
    if (!raw) return { ...DEFAULTS, selectedOptionalColumns: [] }
    return sanitizeHrEmployeePreferences(JSON.parse(raw), allowedColumns)
  } catch (error) {
    try { storage.removeItem(key) } catch (ignored) { /* storage is unavailable */ }
    return { ...DEFAULTS, selectedOptionalColumns: [] }
  }
}

export function saveHrEmployeePreferences(storage, userId, permissions, value, allowedColumns) {
  const sanitized = sanitizeHrEmployeePreferences(value, allowedColumns)
  if (!storage || typeof storage.setItem !== "function") return sanitized
  try { storage.setItem(preferenceKey(userId, permissions), JSON.stringify(sanitized)) } catch (ignored) { /* storage quota or privacy mode */ }
  return sanitized
}
