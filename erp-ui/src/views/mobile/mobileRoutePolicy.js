const MOBILE_SCOPE_POLICIES = Object.freeze({
  STORE: "store",
  WAREHOUSE: "warehouse",
  BUSINESS_ANY: "business-any",
  HR: "hr",
  GLOBAL: "global",
  SELF: "self",
  WORKFLOW: "workflow"
})

const MOBILE_NAV_PROFILES = Object.freeze({
  STORE: "store",
  WAREHOUSE: "warehouse",
  CONTEXT: "context",
  HR: "hr",
  ADMIN: "admin",
  SELF: "self",
  WORKFLOW: "workflow",
  NONE: "none"
})

function routePolicy(scopePolicy, navProfile, contextRequired, entryFallback, contextLabel, options) {
  return Object.freeze({
    scopePolicy,
    navProfile,
    contextRequired,
    entryFallback,
    contextLabel,
    adminOnly: !!(options && options.adminOnly)
  })
}

const storePolicy = routePolicy(
  MOBILE_SCOPE_POLICIES.STORE,
  MOBILE_NAV_PROFILES.STORE,
  true,
  "context-home",
  "当前门店"
)
const warehousePolicy = routePolicy(
  MOBILE_SCOPE_POLICIES.WAREHOUSE,
  MOBILE_NAV_PROFILES.WAREHOUSE,
  true,
  "context-home",
  "当前仓库"
)
const businessPolicy = routePolicy(
  MOBILE_SCOPE_POLICIES.BUSINESS_ANY,
  MOBILE_NAV_PROFILES.CONTEXT,
  true,
  "context-home",
  "当前业务组织"
)
const hrPolicy = routePolicy(
  MOBILE_SCOPE_POLICIES.HR,
  MOBILE_NAV_PROFILES.HR,
  false,
  "hr-home",
  "人事权限范围"
)
const selfPolicy = routePolicy(
  MOBILE_SCOPE_POLICIES.SELF,
  MOBILE_NAV_PROFILES.SELF,
  false,
  "mine",
  "仅当前账号"
)
const workflowPolicy = routePolicy(
  MOBILE_SCOPE_POLICIES.WORKFLOW,
  MOBILE_NAV_PROFILES.WORKFLOW,
  false,
  "todo",
  "按待办授权范围"
)
const workflowContextPolicy = routePolicy(
  MOBILE_SCOPE_POLICIES.WORKFLOW,
  MOBILE_NAV_PROFILES.WORKFLOW,
  true,
  "context-home",
  "当前业务组织"
)
const adminPolicy = routePolicy(
  MOBILE_SCOPE_POLICIES.GLOBAL,
  MOBILE_NAV_PROFILES.ADMIN,
  false,
  "admin-home",
  "全局管理范围",
  { adminOnly: true }
)

// Every registered phone route must be listed exactly once. This deliberately
// excludes retired HR-team and standalone OE-replenishment routes.
const MOBILE_ROUTE_POLICY_BY_DEFINITION_PATH = Object.freeze({
  "/mobile/store": storePolicy,
  "/mobile/warehouse": warehousePolicy,
  "/mobile/inventory": businessPolicy,
  "/mobile/hr": hrPolicy,
  "/mobile/hr/onboarding": hrPolicy,
  "/mobile/hr/onboarding/create": hrPolicy,
  "/mobile/hr/onboarding/:id(\\d+)/edit": hrPolicy,
  "/mobile/hr/onboarding/:id(\\d+)": hrPolicy,
  "/mobile/drive": selfPolicy,
  "/mobile/todo": workflowPolicy,
  "/mobile/oa-purchase-approval": routePolicy(
    MOBILE_SCOPE_POLICIES.WORKFLOW,
    MOBILE_NAV_PROFILES.NONE,
    false,
    "todo",
    "按待办授权范围"
  ),
  "/mobile/reimbursement": workflowContextPolicy,
  "/mobile/hr/employee": hrPolicy,
  "/mobile/hr/completeness": hrPolicy,
  "/mobile/hr/health-certificate": hrPolicy,
  "/mobile/sales": storePolicy,
  "/mobile/purchase": warehousePolicy,
  "/mobile/purchase-return": warehousePolicy,
  "/mobile/stock": businessPolicy,
  "/mobile/product": businessPolicy,
  "/mobile/oe": businessPolicy,
  "/mobile/gift": businessPolicy,
  "/mobile/category": adminPolicy,
  "/mobile/customer": storePolicy,
  "/mobile/supplier": warehousePolicy,
  "/mobile/stock-log": businessPolicy,
  "/mobile/stock-check": businessPolicy,
  "/mobile/transfer-records": businessPolicy,
  "/mobile/sales-return": storePolicy,
  "/mobile/replenishment": storePolicy,
  "/mobile/outbound": warehousePolicy,
  "/mobile/transfer": businessPolicy,
  "/mobile/transfer-approval": workflowContextPolicy,
  "/mobile/fixed-asset-repair": routePolicy(
    MOBILE_SCOPE_POLICIES.STORE,
    MOBILE_NAV_PROFILES.SELF,
    true,
    "context-home",
    "当前门店"
  ),
  "/mobile/attendance": selfPolicy,
  "/mobile/salary": selfPolicy,
  "/mobile/notice": selfPolicy,
  "/mobile/profile": selfPolicy,
  "/mobile/system-user": adminPolicy,
  "/mobile/system-role": adminPolicy,
  "/mobile/system-post": adminPolicy,
  "/mobile/system-dept": adminPolicy,
  "/mobile/system-menu": adminPolicy,
  "/mobile/user-shop": adminPolicy,
  "/mobile/system-config": adminPolicy,
  "/mobile/system-dict-type": adminPolicy,
  "/mobile/system-dict-data": adminPolicy,
  "/mobile/system-logininfor": adminPolicy,
  "/mobile/system-operlog": adminPolicy,
  "/mobile/monitor-job": adminPolicy,
  "/mobile/monitor-job-log": adminPolicy,
  "/mobile/monitor-online": adminPolicy,
  "/mobile/salary-scheme": adminPolicy,
  "/mobile/transfer-rules": adminPolicy,
  "/mobile/contract": selfPolicy,
  "/mobile/sign-package": selfPolicy,
  "/mobile/onboard-data": selfPolicy,
  "/mobile/mine": routePolicy(
    MOBILE_SCOPE_POLICIES.SELF,
    MOBILE_NAV_PROFILES.CONTEXT,
    false,
    "mine",
    "仅当前账号"
  )
})

function applyMobileRoutePolicies(definitions) {
  const routes = Array.isArray(definitions) ? definitions : []
  const registeredPaths = new Set(routes.map(definition => definition.path))
  const missing = routes
    .map(definition => definition.path)
    .filter(path => !MOBILE_ROUTE_POLICY_BY_DEFINITION_PATH[path])
  const stale = Object.keys(MOBILE_ROUTE_POLICY_BY_DEFINITION_PATH)
    .filter(path => !registeredPaths.has(path))

  if (missing.length || stale.length) {
    const details = [
      missing.length ? `missing: ${missing.join(", ")}` : "",
      stale.length ? `stale: ${stale.join(", ")}` : ""
    ].filter(Boolean).join("; ")
    throw new Error(`Mobile route policy coverage mismatch (${details})`)
  }

  routes.forEach(definition => {
    definition.meta = definition.meta || {}
    definition.meta.mobilePolicy = MOBILE_ROUTE_POLICY_BY_DEFINITION_PATH[definition.path]
  })
  return routes
}

function getMobileRoutePolicyByDefinitionPath(path) {
  return MOBILE_ROUTE_POLICY_BY_DEFINITION_PATH[path] || null
}

module.exports = {
  MOBILE_SCOPE_POLICIES,
  MOBILE_NAV_PROFILES,
  MOBILE_ROUTE_POLICY_BY_DEFINITION_PATH,
  applyMobileRoutePolicies,
  getMobileRoutePolicyByDefinitionPath
}
