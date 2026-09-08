const INVENTORY_PATH = /^\/inventory(?:\/|$)/
const ATTENDANCE_PATH = /^\/oa\/attendance-v2(?:\/|$)/

function normalizeRequestPath(url) {
  const value = String(url || "")
  const withoutOrigin = value.replace(/^[a-z][a-z\d+.-]*:\/\/[^/]+/i, "")
  return withoutOrigin.split(/[?#]/)[0] || "/"
}

function normalizePositiveDeptId(value) {
  if (value === undefined || value === null || String(value).trim() === "") return ""
  const normalized = String(value).trim()
  return /^\d+$/.test(normalized) && Number(normalized) > 0 ? normalized : ""
}

function normalizeDeptType(value) {
  return value === undefined || value === null ? "" : String(value).trim().toUpperCase()
}

function applyInventoryDeptRequestContext(config, selectedDeptId, selectedDeptType) {
  const target = config || {}
  const hasOverride = Object.prototype.hasOwnProperty.call(target, "inventoryDeptId")
  const requestPath = normalizeRequestPath(target.url)
  const selected = normalizePositiveDeptId(selectedDeptId)
  let deptId = selected

  if (hasOverride) {
    const override = normalizePositiveDeptId(target.inventoryDeptId)
    delete target.inventoryDeptId
    if (!INVENTORY_PATH.test(requestPath)) {
      throw new Error("inventoryDeptId 只能用于库存请求")
    }
    if (!override) {
      throw new Error("inventoryDeptId 必须为正整数")
    }
    deptId = override
  }

  const selectedType = normalizeDeptType(selectedDeptType)
  const attendanceStoreAllowed = !ATTENDANCE_PATH.test(requestPath) || selectedType === "STORE"
  if (deptId && attendanceStoreAllowed) {
    target.headers = target.headers || {}
    target.headers["Dept-NumId"] = deptId
  }
  return target
}

module.exports = {
  applyInventoryDeptRequestContext,
  normalizeDeptType,
  normalizePositiveDeptId,
  normalizeRequestPath
}
