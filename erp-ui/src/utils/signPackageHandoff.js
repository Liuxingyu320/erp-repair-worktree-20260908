function normalizePackageId(value) {
  if (!["string", "number"].includes(typeof value) || (typeof value === "number" && !Number.isSafeInteger(value))) return ""
  const id = String(value)
  return /^[1-9]\d{0,18}$/.test(id) && (id.length < 19 || id <= "9223372036854775807") ? id : ""
}

function createHandoffUrl(origin, packageId) {
  const id = normalizePackageId(packageId)
  if (!id) return ""
  try {
    const trusted = new URL(origin)
    if (!["http:", "https:"].includes(trusted.protocol) || trusted.origin !== origin || trusted.username || trusted.password) return ""
    return trusted.origin + "/mobile/sign-package?packageId=" + id
  } catch (error) { return "" }
}

module.exports = { normalizePackageId, createHandoffUrl }
