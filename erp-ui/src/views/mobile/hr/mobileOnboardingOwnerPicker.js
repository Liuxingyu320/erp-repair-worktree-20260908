export const OWNER_PAGE_SIZE = 20
export const OWNER_KEYWORD_MAX_LENGTH = 64
export const OWNER_RECENT_LIMIT = 5
export const OWNER_RECENT_TTL_MS = 30 * 24 * 60 * 60 * 1000
const OWNER_RECENT_PREFIX = "erp:mobile-hr:onboarding-owner-recent:v1"
const { mobileHrErrorMessage } = require("./mobileHrError")

export function normalizedOwnerId(value) {
  if (value === "" || value === null || value === undefined) return null
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
}

export function normalizeOwnerOption(option) {
  const source = option && typeof option === "object" ? option : {}
  const userId = normalizedOwnerId(source.userId === undefined ? source.value : source.userId)
  if (!userId) return null
  return {
    userId,
    label: String(source.label || "").trim() || `用户 ${userId}`,
    deptId: normalizedOwnerId(source.deptId),
    deptName: String(source.deptName || "").trim(),
    deptPath: String(source.deptPath || "").trim(),
    employeeNo: String(source.employeeNo || "").trim(),
    phoneMasked: String(source.phoneMasked || "").trim()
  }
}

export function normalizeOwnerPage(response) {
  const source = response && response.data && !Array.isArray(response.rows) ? response.data : response
  const rows = Array.isArray(source && source.rows) ? source.rows : []
  const normalized = rows.map(normalizeOwnerOption).filter(Boolean)
  const total = Number(source && source.total)
  return {
    rows: normalized,
    total: Number.isFinite(total) && total >= 0 ? total : normalized.length
  }
}

export function mergeOwnerOptions(current, incoming) {
  const result = []
  const seen = new Set()
  ;[].concat(current || [], incoming || []).forEach(item => {
    const option = normalizeOwnerOption(item)
    if (!option || seen.has(option.userId)) return
    seen.add(option.userId)
    result.push(option)
  })
  return result
}

export function ownerOptionMeta(option) {
  const source = normalizeOwnerOption(option)
  if (!source) return ""
  return [source.deptPath || source.deptName, source.employeeNo && `工号 ${source.employeeNo}`, source.phoneMasked]
    .filter(Boolean)
    .join(" · ")
}

export function ownerPickerError(error) {
  const response = error && error.response && typeof error.response === "object" ? error.response : {}
  const status = Number(response.status === undefined ? error && error.status : response.status)
  if (status === 403) return { type: "forbidden", message: "你没有查看入职负责人的权限。" }
  return {
    type: "error",
    message: mobileHrErrorMessage(error, "负责人加载失败，请检查网络后重试。")
  }
}

export function ownerRecentStorageKey(accountId, deptId) {
  const account = normalizedOwnerId(accountId)
  const department = normalizedOwnerId(deptId)
  return account && department ? `${OWNER_RECENT_PREFIX}:${account}:${department}` : ""
}

export function readRecentOwners(storage, key, now = Date.now()) {
  if (!storage || !key) return []
  let parsed
  try {
    parsed = JSON.parse(storage.getItem(key) || "[]")
  } catch (error) {
    return []
  }
  if (!Array.isArray(parsed)) return []
  const seen = new Set()
  return parsed.reduce((result, entry) => {
    const userId = normalizedOwnerId(entry && entry.userId)
    const timestamp = Number(entry && entry.timestamp)
    if (!userId || !Number.isFinite(timestamp) || timestamp <= 0 || now - timestamp > OWNER_RECENT_TTL_MS || seen.has(userId)) {
      return result
    }
    seen.add(userId)
    result.push({ userId, timestamp })
    return result
  }, []).sort((left, right) => right.timestamp - left.timestamp).slice(0, OWNER_RECENT_LIMIT)
}

export function writeRecentOwners(storage, key, entries) {
  if (!storage || !key) return
  const safeEntries = (Array.isArray(entries) ? entries : []).slice(0, OWNER_RECENT_LIMIT).map(entry => ({
    userId: normalizedOwnerId(entry.userId),
    timestamp: Number(entry.timestamp)
  })).filter(entry => entry.userId && Number.isFinite(entry.timestamp) && entry.timestamp > 0)
  try {
    storage.setItem(key, JSON.stringify(safeEntries))
  } catch (error) {
    // Storage can be unavailable in private mode; recent choices must never block the picker.
  }
}

export function rememberRecentOwner(storage, key, userId, now = Date.now()) {
  const normalized = normalizedOwnerId(userId)
  if (!normalized || !key) return []
  const next = [{ userId: normalized, timestamp: now }].concat(
    readRecentOwners(storage, key, now).filter(entry => entry.userId !== normalized)
  ).slice(0, OWNER_RECENT_LIMIT)
  writeRecentOwners(storage, key, next)
  return next
}
