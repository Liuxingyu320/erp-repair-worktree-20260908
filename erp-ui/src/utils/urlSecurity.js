function sanitizeNavigableUrl(rawValue) {
  if (typeof rawValue !== "string") return ""
  const value = rawValue.trim()
  if (!value || /[\u0000-\u001f\u007f]/.test(value) ||
    value.startsWith("//") || value.startsWith("\\") || value.includes("\\")) {
    return ""
  }

  const schemeMatch = value.match(/^([a-z][a-z\d+.-]*):/i)
  if (!schemeMatch) return value
  if (!/^https?$/i.test(schemeMatch[1])) return ""

  try {
    const parsed = new URL(value)
    if (parsed.username || parsed.password) return ""
    return value
  } catch (_) {
    return ""
  }
}

function sanitizeFileUrl(value) {
  return sanitizeNavigableUrl(value)
}

function sanitizeFrameUrl(value) {
  return sanitizeNavigableUrl(value)
}

module.exports = {
  sanitizeFileUrl,
  sanitizeFrameUrl,
  sanitizeNavigableUrl
}
