const { exactBusinessId } = require("./todoAggregator")
const { sanitizeRouteParams } = require("./todoRouteParams")

const PURCHASE = ["/cangku/purchase", "/inventory/purchase"]
const SALES = ["/inventory/sales", "/cangku/sales"]
const DELIVERY = [
  "/cangku/deliveryNotice", "/cangku/delivery-notice",
  "/inventory/deliveryNotice", "/inventory/delivery-notice"
]
const TRANSFER = ["/cangku/transfer", "/inventory/transfer"]
const STOCK_CHECK = [
  "/inventory/stockCheck", "/inventory/stock-check",
  "/cangku/stockCheck", "/cangku/stock-check"
]
const PURCHASE_RETURN = [
  "/cangku/purchaseReturn", "/cangku/purchase-return",
  "/inventory/purchaseReturn", "/inventory/purchase-return"
]
const SALES_RETURN = [
  "/inventory/salesReturn", "/inventory/sales-return",
  "/cangku/salesReturn", "/cangku/sales-return"
]
const STOCK = [
  "/cangku/warehouse-stock", "/cangku/store-stock",
  "/inventory/stock", "/cangku/stock"
]
const SIGN_TASK = ["/oa/sign-task"]
const OA_PURCHASE = ["/oa/purchase"]
const OA_REIMBURSEMENT = ["/oa/reimbursement"]
const OA_ATTENDANCE = ["/oa/attendance-v2"]

function organization(desktop, mobile, fixedQuery) {
  return { desktop, mobile: [mobile], organizationBound: true, fixedQuery }
}

function personal(path) {
  return { desktop: [path], mobile: [path], organizationBound: false }
}

function personalAcross(desktop, mobile) {
  return { desktop: [desktop], mobile: [mobile], organizationBound: false }
}

const ROUTE_MATRIX = Object.freeze({
  INV_PURCHASE_QC: organization(PURCHASE, "/mobile/purchase"),
  INV_PURCHASE_RECEIVE: organization(PURCHASE, "/mobile/purchase"),
  INV_SALES_NOTICE_CREATE: organization(SALES, "/mobile/sales"),
  INV_DELIVERY_EXECUTE: organization(DELIVERY, "/mobile/outbound"),
  INV_TRANSFER_APPROVAL: organization(TRANSFER, "/mobile/transfer-approval"),
  INV_TRANSFER_SOURCE_CONFIRM: organization(TRANSFER, "/mobile/transfer"),
  INV_TRANSFER_DELIVER: organization(TRANSFER, "/mobile/transfer"),
  INV_TRANSFER_RECEIVE: organization(TRANSFER, "/mobile/transfer"),
  INV_TRANSFER_RETURNED: organization(TRANSFER, "/mobile/transfer"),
  INV_TRANSFER_SOURCE_RESELECT: organization(TRANSFER, "/mobile/transfer"),
  INV_TRANSFER_DISCREPANCY: organization(TRANSFER, "/mobile/transfer"),
  INV_STOCK_CHECK_APPROVAL: organization(STOCK_CHECK, "/mobile/stock-check"),
  INV_STOCK_CHECK_EXECUTE: organization(STOCK_CHECK, "/mobile/stock-check"),
  INV_STOCK_CHECK_RETURNED: organization(STOCK_CHECK, "/mobile/stock-check"),
  INV_STOCK_CHECK_RESTART: organization(STOCK_CHECK, "/mobile/stock-check"),
  INV_PURCHASE_RETURN_CONFIRM: organization(PURCHASE_RETURN, "/mobile/purchase-return"),
  INV_SALES_RETURN_CONFIRM: organization(SALES_RETURN, "/mobile/sales-return"),
  INV_OUT_OF_STOCK: organization(STOCK, "/mobile/stock"),
  INV_LOW_STOCK: organization(STOCK, "/mobile/stock"),
  OA_LABOR_CONTRACT_SIGN: personal("/mobile/contract"),
  OA_SIGN_PACKAGE_SIGN: personal("/mobile/sign-package"),
  OA_SIGN_ONBOARD_DATA_REQUEST: personal("/mobile/sign-package"),
  OA_SIGN_NEEDS_DATA: organization(SIGN_TASK, "/oa/sign-task"),
  OA_SIGN_COMPANY_FINALIZE: organization(SIGN_TASK, "/oa/sign-task"),
  OA_SIGN_SEND_FAILED: organization(SIGN_TASK, "/oa/sign-task"),
  OA_SIGN_REFUSED: organization(SIGN_TASK, "/oa/sign-task"),
  OA_SIGN_EXPIRED: organization(SIGN_TASK, "/oa/sign-task"),
  OA_PURCHASE_APPROVAL: organization(OA_PURCHASE, "/mobile/oa-purchase-approval"),
  OA_REIMBURSEMENT_APPROVAL: organization(OA_REIMBURSEMENT, "/mobile/reimbursement"),
  OA_ATTENDANCE_LEAVE_APPROVAL: organization(
    OA_ATTENDANCE,
    "/mobile/attendance",
    () => ({ tab: "leave" })
  ),
  OA_ATTENDANCE_CORRECTION_APPROVAL: organization(
    OA_ATTENDANCE,
    "/mobile/attendance",
    () => ({ tab: "correction" })
  ),
  HR_PROFILE_INCOMPLETE: organization(["/hr/completeness"], "/mobile/hr/completeness"),
  HR_HEALTH_CERT_DUE: organization(["/hr/healthCertificate"], "/mobile/hr/health-certificate"),
  HR_HEALTH_CERT_REVIEW: organization(["/hr/healthCertificate"], "/mobile/hr/health-certificate"),
  HR_HEALTH_CERT_RETURNED: personalAcross("/hr/healthCertificate", "/mobile/hr/health-certificate"),
  HR_ONBOARDING_CONFIRM: organization(["/hr/onboarding"], "/mobile/hr/onboarding"),
  HR_CONTRACT_DUE: organization(["/hr/employee"], "/mobile/hr/employee", () => ({ contractDue: true })),
  HR_OFFBOARD_ACCOUNT: organization(["/hr/employee"], "/mobile/hr/employee", () => ({ offboardAccountOnly: true }))
})

function toSet(values) {
  if (values instanceof Set) {
    return values
  }
  return new Set(Array.isArray(values) ? values : [])
}

function normalizeRoutePath(path) {
  const normalized = String(path || "").replace(/\/{2,}/g, "/")
  if (!normalized) {
    return ""
  }
  const withLeadingSlash = normalized.startsWith("/") ? normalized : `/${normalized}`
  return withLeadingSlash.length > 1 ? withLeadingSlash.replace(/\/$/, "") : withLeadingSlash
}

function fullRoutePath(parentPath, routePath) {
  const path = String(routePath || "")
  if (!path) {
    return normalizeRoutePath(parentPath)
  }
  if (path.startsWith("/")) {
    return normalizeRoutePath(path)
  }
  return normalizeRoutePath(`${parentPath || ""}/${path}`)
}

function routePermissions(route) {
  const required = route && (route.permissions !== undefined
    ? route.permissions
    : route.meta && route.meta.permissions)
  if (required === undefined || required === null || required === "") {
    return []
  }
  return Array.isArray(required) ? required : [required]
}

function canAccessRoute(route, permissions) {
  const required = routePermissions(route)
  if (required.length === 0) {
    return true
  }
  const permissionSet = toSet(permissions)
  return permissionSet.has("*:*:*") || required.some(permission => permissionSet.has(permission))
}

function collectRegisteredRoutes(routes, available, parentPath, permissions, filterPermissions) {
  ;(routes || []).forEach(route => {
    if (!route || (filterPermissions && !canAccessRoute(route, permissions))) {
      return
    }
    const fullPath = fullRoutePath(parentPath, route.path)
    if (fullPath) {
      available.add(fullPath)
    }
    collectRegisteredRoutes(
      route.children,
      available,
      fullPath || parentPath,
      permissions,
      filterPermissions
    )
  })
}

function buildAvailableRouteSet(options = {}) {
  const available = new Set()
  collectRegisteredRoutes(options.constantRoutes, available, "", options.permissions, false)
  collectRegisteredRoutes(options.dynamicRoutes, available, "", options.permissions, true)
  return available
}

function hasPermission(requiredPermission, permissions) {
  if (!requiredPermission) {
    return true
  }
  const permissionSet = toSet(permissions)
  return permissionSet.has("*:*:*") || permissionSet.has(requiredPermission)
}

function normalizeContext(context) {
  const source = context || {}
  return {
    deptId: source.deptId ?? source.contextDeptId,
    deptName: source.deptName ?? source.contextDeptName,
    deptType: String(source.deptType ?? source.contextDeptType ?? "").trim().toUpperCase()
  }
}

function validOrganization(context) {
  const normalized = normalizeContext(context)
  return normalized.deptId !== undefined && normalized.deptId !== null && normalized.deptId !== "" &&
    !!String(normalized.deptName || "").trim() &&
    (normalized.deptType === "STORE" || normalized.deptType === "WAREHOUSE")
}

function sameOrganization(left, right) {
  const leftContext = normalizeContext(left)
  const rightContext = normalizeContext(right)
  return String(leftContext.deptId) === String(rightContext.deptId) &&
    leftContext.deptType === rightContext.deptType
}

function getRouteDefinition(type) {
  return typeof type === "string" && Object.prototype.hasOwnProperty.call(ROUTE_MATRIX, type)
    ? ROUTE_MATRIX[type]
    : null
}

function resolveTodoRoute(item, options = {}) {
  const route = getRouteDefinition(item && item.type)
  if (!hasPermission(item && item.requiredPermission, options.permissions)) {
    return { ok: false, reason: "permission_changed" }
  }
  if (!route) {
    return { ok: false, reason: "route_missing" }
  }
  if (route.organizationBound) {
    if (!validOrganization(item) || !validOrganization(options.currentContext) ||
      !sameOrganization(item, options.currentContext)) {
      return { ok: false, reason: "organization_required" }
    }
  }

  const platform = options.platform === "mobile" ? "mobile" : "desktop"
  const availableRouteSet = toSet(options.availableRouteSet)
  const path = route[platform].find(candidate => availableRouteSet.has(candidate))
  if (!path) {
    return { ok: false, reason: "route_missing" }
  }

  const sanitized = sanitizeRouteParams(item.routeParams)
  if (!sanitized.ok) {
    return { ok: false, reason: "route_missing" }
  }
  const safeItem = Object.assign({}, item, { routeParams: sanitized.params })
  const fixedQuery = typeof route.fixedQuery === "function" ? route.fixedQuery(safeItem) : (route.fixedQuery || {})
  const query = Object.assign(
    {},
    sanitized.params,
    { todoType: item.type, businessId: exactBusinessId(safeItem) },
    fixedQuery
  )
  return { ok: true, location: { path, query } }
}

module.exports = {
  ROUTE_MATRIX,
  buildAvailableRouteSet,
  canAccessRoute,
  fullRoutePath,
  getRouteDefinition,
  hasPermission,
  normalizeContext,
  resolveTodoRoute,
  sanitizeRouteParams,
  sameOrganization,
  validOrganization
}
