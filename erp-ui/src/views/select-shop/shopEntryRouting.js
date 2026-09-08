const { getMobileHomePath } = require("../mobile/mobileNavigation")
const { isMobileRoutePath } = require("../../utils/clientPlatform")

function isSafeInternalRedirect(value) {
  return typeof value === "string" && value.startsWith("/") && !value.startsWith("//")
}

function resolveShopEntryRedirect(options) {
  const value = options || {}
  if (value.isMobileViewport) {
    return getMobileHomePath(value.deptType, value.permissions)
  }
  if (isMobileRoutePath(value.requestedRedirect)) {
    return "/"
  }
  return isSafeInternalRedirect(value.requestedRedirect) ? value.requestedRedirect : "/"
}

const contextOptionalPermissionRoutes = [
  ["system:user:list", "/system/user"],
  ["hr:employee:list", "/hr/employee"],
  ["system:role:list", "/system/role"],
  ["system:menu:list", "/system/menu"],
  ["system:dept:list", "/system/dept"],
  ["system:post:list", "/system/post"],
  ["system:dict:list", "/system/dict"],
  ["system:config:list", "/system/config"],
  ["system:salary:list", "/system/salary"],
  ["system:userShop:list", "/system/shop"],
  ["system:operlog:list", "/monitor/operlog"],
  ["system:logininfor:list", "/monitor/logininfor"]
]

/**
 * Accounts that only administer the platform or HR may legitimately have no
 * store/warehouse assignment. Send them to an authorized context-free page
 * instead of trapping them on an empty organization selector.
 */
function resolveNoBusinessContextRedirect(options) {
  const value = options || {}
  if (value.isMobileViewport || Number(value.businessDeptCount) > 0) return ""

  const requestedRedirect = value.requestedRedirect
  if (isSafeInternalRedirect(requestedRedirect) &&
      requestedRedirect !== "/" && requestedRedirect !== "/index" &&
      value.requestedRedirectRequiresContext !== true) {
    return requestedRedirect
  }

  const permissions = Array.isArray(value.permissions) ? value.permissions : []
  if (permissions.includes("*:*:*")) return "/system/user"
  const match = contextOptionalPermissionRoutes.find(([permission]) => permissions.includes(permission))
  return match ? match[1] : ""
}

module.exports = {
  isSafeInternalRedirect,
  resolveShopEntryRedirect,
  resolveNoBusinessContextRedirect
}
