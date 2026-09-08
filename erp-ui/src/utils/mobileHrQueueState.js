const MAX_CACHE_ENTRIES = 6
const MAX_CACHE_ROWS = 5000
const CACHE_TTL_MS = 15 * 60 * 1000
const cache = new Map()
let counter = 0
let now = () => Date.now()

function clean(value) {
  return value === undefined || value === null ? "" : String(value).trim()
}

function hash(value) {
  let first = 2166136261
  let second = 0x9e3779b9
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index)
    first = Math.imul(first ^ code, 16777619)
    second = Math.imul(second ^ code, 2246822507)
  }
  return `${(first >>> 0).toString(16).padStart(8, "0")}${(second >>> 0).toString(16).padStart(8, "0")}`
}

function createMobileHrQueueOwnerFingerprint({ userId, deptId, permissions } = {}) {
  const normalizedUser = clean(userId)
  if (!normalizedUser) return ""
  const normalizedDept = clean(deptId) || "0"
  const normalizedPermissions = Array.isArray(permissions)
    ? permissions.map(clean).filter(Boolean).sort().join("|")
    : ""
  return `owner_${hash(`${normalizedUser}:${normalizedDept}:${normalizedPermissions}`)}`
}

function isStateKey(value) {
  return /^qs_[a-z0-9_]{12,80}$/i.test(clean(value))
}

function createStateKey() {
  counter += 1
  return `qs_${now().toString(36)}_${counter.toString(36)}_${Math.random().toString(36).slice(2, 12)}`
}

function cloneFilters(filters) {
  const source = filters || {}
  return {
    keyword: clean(source.keyword).slice(0, 200),
    expectedEntryDateFrom: clean(source.expectedEntryDateFrom),
    expectedEntryDateTo: clean(source.expectedEntryDateTo),
    targetDeptId: source.targetDeptId || "",
    targetStoreId: source.targetStoreId || "",
    employeeCategory: clean(source.employeeCategory),
    ownerUserId: source.ownerUserId || ""
  }
}

function cloneRows(rows) {
  return Array.isArray(rows) ? rows.map(row => Object.assign({}, row)) : []
}

function purge() {
  const timestamp = now()
  Array.from(cache.entries()).forEach(([key, entry]) => {
    if (!entry || entry.expiresAt <= timestamp) cache.delete(key)
  })
  while (cache.size > MAX_CACHE_ENTRIES) cache.delete(cache.keys().next().value)
}

function clearMobileHrQueueStateCache() {
  cache.clear()
}

function removeMobileHrQueueState(ownerFingerprint, key) {
  purge()
  if (!clean(ownerFingerprint) || !isStateKey(key) || !cache.has(key)) return false
  const entry = cache.get(key)
  if (!entry || entry.ownerFingerprint !== ownerFingerprint) return false
  cache.delete(key)
  return true
}

function writeMobileHrQueueState(ownerFingerprint, key, state = {}) {
  purge()
  if (!clean(ownerFingerprint)) return ""
  const nextKey = isStateKey(key) ? key : createStateKey()
  const sourceRows = Array.isArray(state.rows) ? state.rows : []
  const overflow = sourceRows.length > MAX_CACHE_ROWS
  cache.delete(nextKey)
  cache.set(nextKey, {
    ownerFingerprint,
    status: clean(state.status) || "DRAFT",
    taskView: !!state.taskView,
    filters: cloneFilters(state.filters),
    rows: overflow ? [] : cloneRows(sourceRows),
    total: overflow ? 0 : (Number(state.total) || 0),
    pageNum: overflow ? 1 : (Number(state.pageNum) > 0 ? Number(state.pageNum) : 1),
    scrollTop: overflow ? 0 : (Number(state.scrollTop) > 0 ? Number(state.scrollTop) : 0),
    overflow,
    expiresAt: now() + CACHE_TTL_MS
  })
  purge()
  return nextKey
}

function readMobileHrQueueState(ownerFingerprint, key) {
  purge()
  if (!clean(ownerFingerprint) || !isStateKey(key) || !cache.has(key)) return null
  const entry = cache.get(key)
  if (entry.ownerFingerprint !== ownerFingerprint) {
    cache.delete(key)
    return null
  }
  cache.delete(key)
  if (!entry.overflow) cache.set(key, entry)
  return {
    status: entry.status,
    taskView: entry.taskView,
    filters: cloneFilters(entry.filters),
    rows: cloneRows(entry.rows),
    total: entry.total,
    pageNum: entry.pageNum,
    scrollTop: entry.scrollTop,
    overflow: entry.overflow
  }
}

function resetMobileHrQueueStateCacheForTests() {
  cache.clear()
  counter = 0
  now = () => Date.now()
}

function setMobileHrQueueStateNowForTests(value) {
  now = () => Number(value)
}

function getMobileHrQueueStateCacheSnapshotForTests() {
  purge()
  return Array.from(cache, ([key, entry]) => ({
    key,
    expiresAt: entry.expiresAt,
    pageNum: entry.pageNum,
    rowCount: entry.rows.length,
    overflow: entry.overflow
  }))
}

module.exports = {
  MAX_CACHE_ENTRIES,
  MAX_CACHE_ROWS,
  CACHE_TTL_MS,
  createMobileHrQueueOwnerFingerprint,
  clearMobileHrQueueStateCache,
  removeMobileHrQueueState,
  writeMobileHrQueueState,
  readMobileHrQueueState,
  resetMobileHrQueueStateCacheForTests,
  setMobileHrQueueStateNowForTests,
  getMobileHrQueueStateCacheSnapshotForTests
}
