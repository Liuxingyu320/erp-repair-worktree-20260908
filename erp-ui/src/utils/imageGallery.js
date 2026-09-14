const { sanitizeFileUrl } = require("./urlSecurity")

function imageUrls(value) {
  let raw = value
  if (value && !Array.isArray(value) && typeof value === "object") {
    raw = value.imageUrls !== undefined && value.imageUrls !== null ? value.imageUrls : value.imageUrl
  }
  if (typeof raw === "string" && raw.trim().startsWith("[")) {
    try { raw = JSON.parse(raw) } catch (_) { return [] }
  }
  const values = Array.isArray(raw) ? raw : typeof raw === "string" ? raw.split(/[,\r\n]+/) : []
  return [...new Set(values.map(item => sanitizeFileUrl(typeof item === "string" ? item : item && item.url)).filter(Boolean))]
}

module.exports = { imageUrls }
