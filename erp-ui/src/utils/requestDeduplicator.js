const DEFAULT_INTERVAL = 1000
const MAX_TRACKED_REQUESTS = 200
const recentRequests = new Map()

const SENSITIVE_PATHS = [
  /^\/auth\/(?:login|register|unlockscreen|refresh)(?:\/|$)/,
  /^\/system\/user\/(?:profile\/updatePwd|resetPwd)(?:\/|$)/
]

function assertRequestNotDuplicate(config, now = Date.now()) {
  if (shouldSkipRequestDeduplication(config)) return true

  const interval = normalizeInterval(readHeader(config.headers, "interval"))
  removeExpiredRequests(now)

  const method = String(config.method || "").toUpperCase()
  const url = String(config.url || "")
  const payloadHash = hashPayload(serializePayload(config.data))
  const fingerprint = [method, url, payloadHash].join(":")
  const existing = recentRequests.get(fingerprint)

  if (existing && now < existing.expiresAt) {
    throw new Error("数据正在处理，请勿重复提交")
  }

  recentRequests.set(fingerprint, {
    method,
    url,
    payloadHash,
    time: now,
    expiresAt: now + interval
  })
  trimOldestRequests()
  return true
}

function shouldSkipRequestDeduplication(config = {}) {
  const method = String(config.method || "").toLowerCase()
  if (method !== "post" && method !== "put") return true
  if (readHeader(config.headers, "repeatSubmit") === false) return true
  if (isSensitivePath(config.url)) return true
  if (isMultipartRequest(config)) return true
  return false
}

function isSensitivePath(url) {
  const requestPath = normalizeRequestPath(url)
  return SENSITIVE_PATHS.some(pattern => pattern.test(requestPath))
}

function normalizeRequestPath(url) {
  const value = String(url || "")
  const withoutOrigin = value.replace(/^[a-z][a-z\d+.-]*:\/\/[^/]+/i, "")
  return withoutOrigin.split(/[?#]/)[0] || "/"
}

function isMultipartRequest(config) {
  const contentType = String(readHeader(config.headers, "Content-Type") || "").toLowerCase()
  if (contentType.indexOf("multipart/form-data") > -1) return true
  return typeof FormData !== "undefined" && config.data instanceof FormData
}

function readHeader(headers, name) {
  if (!headers) return undefined
  if (typeof headers.get === "function") {
    const value = headers.get(name)
    if (value !== undefined && value !== null) return value
  }
  const target = String(name).toLowerCase()
  const key = Object.keys(headers).find(item => String(item).toLowerCase() === target)
  return key ? headers[key] : undefined
}

function normalizeInterval(value) {
  const interval = Number(value)
  return Number.isFinite(interval) && interval > 0 ? interval : DEFAULT_INTERVAL
}

function serializePayload(data) {
  if (data === undefined || data === null) return ""
  if (typeof data === "string") return data
  try {
    return JSON.stringify(data)
  } catch (error) {
    return String(data)
  }
}

function hashPayload(payload) {
  let hash = 0x811c9dc5
  for (let index = 0; index < payload.length; index += 1) {
    hash ^= payload.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193)
  }
  return (hash >>> 0).toString(16).padStart(8, "0")
}

function removeExpiredRequests(now) {
  recentRequests.forEach((entry, fingerprint) => {
    if (entry.expiresAt <= now) recentRequests.delete(fingerprint)
  })
}

function trimOldestRequests() {
  while (recentRequests.size > MAX_TRACKED_REQUESTS) {
    const oldestFingerprint = recentRequests.keys().next().value
    recentRequests.delete(oldestFingerprint)
  }
}

function getRequestDeduplicatorStateForTest() {
  return Array.from(recentRequests.values()).map(entry => Object.assign({}, entry))
}

function resetRequestDeduplicatorForTest() {
  recentRequests.clear()
}

module.exports = {
  assertRequestNotDuplicate,
  getRequestDeduplicatorStateForTest,
  resetRequestDeduplicatorForTest,
  shouldSkipRequestDeduplication
}
