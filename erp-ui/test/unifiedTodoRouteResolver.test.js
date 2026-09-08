const assert = require("assert")

const {
  buildAvailableRouteSet,
  resolveTodoRoute,
  ROUTE_MATRIX
} = require("../src/utils/todoRouteResolver")

const inventoryContext = { deptId: 20, deptName: "华东仓", deptType: "WAREHOUSE" }
const availableRouteSet = new Set([
  "/cangku/purchase", "/inventory/purchase", "/inventory/sales", "/cangku/sales",
  "/cangku/deliveryNotice", "/cangku/delivery-notice", "/inventory/deliveryNotice", "/inventory/delivery-notice",
  "/inventory/transfer", "/cangku/transfer", "/inventory/stockCheck", "/inventory/stock-check",
  "/cangku/stockCheck", "/cangku/stock-check", "/cangku/purchaseReturn", "/cangku/purchase-return",
  "/inventory/purchaseReturn", "/inventory/purchase-return", "/inventory/salesReturn", "/inventory/sales-return",
  "/cangku/salesReturn", "/cangku/sales-return", "/inventory/stock", "/cangku/stock",
  "/oa/fixedAsset/repair", "/oa/fixed-asset/config", "/oa/sign-task", "/mobile/contract", "/mobile/sign-package",
  "/hr/completeness", "/hr/onboarding", "/hr/employee", "/mobile/purchase", "/mobile/sales",
  "/mobile/outbound", "/mobile/transfer-approval", "/mobile/transfer", "/mobile/stock-check",
  "/mobile/purchase-return", "/mobile/sales-return", "/mobile/stock",
  "/mobile/fixed-asset-repair", "/mobile/hr/completeness",
  "/mobile/hr/onboarding", "/mobile/hr/employee",
  "/hr/healthCertificate", "/mobile/hr/health-certificate"
])

const cases = [
  ["INV_PURCHASE_QC", "/cangku/purchase", "/mobile/purchase"],
  ["INV_PURCHASE_RECEIVE", "/cangku/purchase", "/mobile/purchase"],
  ["INV_SALES_NOTICE_CREATE", "/inventory/sales", "/mobile/sales"],
  ["INV_DELIVERY_EXECUTE", "/cangku/deliveryNotice", "/mobile/outbound"],
  ["INV_TRANSFER_APPROVAL", "/cangku/transfer", "/mobile/transfer-approval"],
  ["INV_TRANSFER_SOURCE_CONFIRM", "/cangku/transfer", "/mobile/transfer"],
  ["INV_TRANSFER_DELIVER", "/cangku/transfer", "/mobile/transfer"],
  ["INV_TRANSFER_RECEIVE", "/cangku/transfer", "/mobile/transfer"],
  ["INV_TRANSFER_DISCREPANCY", "/cangku/transfer", "/mobile/transfer"],
  ["INV_TRANSFER_RETURNED", "/cangku/transfer", "/mobile/transfer"],
  ["INV_TRANSFER_SOURCE_RESELECT", "/cangku/transfer", "/mobile/transfer"],
  ["INV_STOCK_CHECK_APPROVAL", "/inventory/stockCheck", "/mobile/stock-check"],
  ["INV_STOCK_CHECK_EXECUTE", "/inventory/stockCheck", "/mobile/stock-check"],
  ["INV_STOCK_CHECK_RETURNED", "/inventory/stockCheck", "/mobile/stock-check"],
  ["INV_STOCK_CHECK_RESTART", "/inventory/stockCheck", "/mobile/stock-check"],
  ["INV_PURCHASE_RETURN_CONFIRM", "/cangku/purchaseReturn", "/mobile/purchase-return"],
  ["INV_SALES_RETURN_CONFIRM", "/inventory/salesReturn", "/mobile/sales-return"],
  ["INV_OUT_OF_STOCK", "/inventory/stock", "/mobile/stock"],
  ["INV_LOW_STOCK", "/inventory/stock", "/mobile/stock"],
  ["OA_LABOR_CONTRACT_SIGN", "/mobile/contract", "/mobile/contract", true],
  ["OA_SIGN_PACKAGE_SIGN", "/mobile/sign-package", "/mobile/sign-package", true],
  ["OA_SIGN_ONBOARD_DATA_REQUEST", "/mobile/sign-package", "/mobile/sign-package", true],
  ["OA_SIGN_NEEDS_DATA", "/oa/sign-task", "/oa/sign-task"],
  ["OA_SIGN_COMPANY_FINALIZE", "/oa/sign-task", "/oa/sign-task"],
  ["OA_SIGN_SEND_FAILED", "/oa/sign-task", "/oa/sign-task"],
  ["OA_SIGN_REFUSED", "/oa/sign-task", "/oa/sign-task"],
  ["OA_SIGN_EXPIRED", "/oa/sign-task", "/oa/sign-task"],
  ["HR_PROFILE_INCOMPLETE", "/hr/completeness", "/mobile/hr/completeness"],
  ["HR_HEALTH_CERT_DUE", "/hr/healthCertificate", "/mobile/hr/health-certificate"],
  ["HR_HEALTH_CERT_REVIEW", "/hr/healthCertificate", "/mobile/hr/health-certificate"],
  ["HR_HEALTH_CERT_RETURNED", "/hr/healthCertificate", "/mobile/hr/health-certificate", true],
  ["HR_ONBOARDING_CONFIRM", "/hr/onboarding", "/mobile/hr/onboarding"],
  ["HR_CONTRACT_DUE", "/hr/employee", "/mobile/hr/employee"],
  ["HR_OFFBOARD_ACCOUNT", "/hr/employee", "/mobile/hr/employee"]
]

cases.forEach(([type, desktopPath, mobilePath, personal]) => {
  const item = {
    type,
    source: type.startsWith("INV_") ? "inventory" : type.startsWith("OA_") ? "oa" : "system",
    businessId: 77,
    requiredPermission: "todo:handle",
    routeParams: { status: "pending", todoType: "unsafe", businessId: "77", process: 9 },
    ...(personal ? {} : { contextDeptId: 20, contextDeptName: "华东仓", contextDeptType: "WAREHOUSE" })
  }
  const baseOptions = { permissions: ["todo:handle"], availableRouteSet, currentContext: personal ? null : inventoryContext }
  const desktop = resolveTodoRoute(item, { ...baseOptions, platform: "desktop" })
  const mobile = resolveTodoRoute(item, { ...baseOptions, platform: "mobile" })
  assert.strictEqual(desktop.ok, true, `${type} desktop route should resolve`)
  assert.strictEqual(desktop.location.path, desktopPath, `${type} desktop path`)
  assert.strictEqual(mobile.ok, true, `${type} mobile route should resolve`)
  assert.strictEqual(mobile.location.path, mobilePath, `${type} mobile path`)
  assert.strictEqual(desktop.location.query.todoType, type)
  assert.strictEqual(desktop.location.query.businessId, "77")
})

const onboardRequestId = "9999999999999997"
const onboardRequestRoute = resolveTodoRoute({
  source: "oa",
  type: "OA_SIGN_ONBOARD_DATA_REQUEST",
  businessId: JSON.parse(`{"value":${onboardRequestId}}`).value,
  routeParams: { businessId: onboardRequestId, requestId: onboardRequestId, source: "oa" }
}, {
  platform: "mobile", permissions: [], availableRouteSet, currentContext: null
})
assert.deepStrictEqual(onboardRequestRoute.location, {
  path: "/mobile/sign-package",
  query: {
    businessId: onboardRequestId,
    requestId: onboardRequestId,
    source: "oa",
    todoType: "OA_SIGN_ONBOARD_DATA_REQUEST"
  }
}, "onboard employee todo links must preserve the exact request id inside My Signings")

const signNotificationRoute = resolveTodoRoute({
  source: "oa", type: "OA_SIGN_SEND_FAILED", businessId: 91,
  requiredPermission: "oa:signTask:retry",
  contextDeptId: 20, contextDeptName: "华东仓", contextDeptType: "WAREHOUSE",
  routeParams: {
    businessId: "91", taskId: "91", notificationBusinessKey: "SIGN_SENT:90:SP-90-V1"
  }
}, {
  platform: "desktop", permissions: ["oa:signTask:retry"], availableRouteSet,
  currentContext: inventoryContext
})
assert.deepStrictEqual(signNotificationRoute.location, {
  path: "/oa/sign-task",
  query: {
    businessId: "91", taskId: "91", notificationBusinessKey: "SIGN_SENT:90:SP-90-V1",
    todoType: "OA_SIGN_SEND_FAILED"
  }
})

const inventoryApproval = {
  source: "inventory",
  type: "INV_TRANSFER_APPROVAL",
  businessId: JSON.parse('{"value":9999999999999999}').value,
  requiredPermission: "inv:transfer:approve",
  contextDeptId: 20,
  contextDeptName: "华东仓",
  contextDeptType: "WAREHOUSE",
  todoKey: "inventory:INV_TRANSFER_APPROVAL:9999999999999999:approve",
  routeParams: { businessId: "9999999999999999" }
}
const approvalRoute = resolveTodoRoute(inventoryApproval, {
  platform: "desktop",
  permissions: ["inv:transfer:approve"],
  availableRouteSet,
  currentContext: inventoryContext
})
assert.deepStrictEqual(approvalRoute.location.query, {
  todoType: "INV_TRANSFER_APPROVAL",
  businessId: "9999999999999999"
})

const aliasRoutes = new Set(availableRouteSet)
aliasRoutes.delete("/cangku/purchase")
assert.strictEqual(resolveTodoRoute({ ...inventoryApproval, type: "INV_PURCHASE_QC" }, {
  platform: "desktop", permissions: ["inv:transfer:approve"], availableRouteSet: aliasRoutes, currentContext: inventoryContext
}).location.path, "/inventory/purchase")

assert.deepStrictEqual(resolveTodoRoute(inventoryApproval, {
  platform: "desktop", permissions: [], availableRouteSet, currentContext: inventoryContext
}), { ok: false, reason: "permission_changed" })
assert.deepStrictEqual(resolveTodoRoute(inventoryApproval, {
  platform: "desktop", permissions: ["inv:transfer:approve"], availableRouteSet: new Set(), currentContext: inventoryContext
}), { ok: false, reason: "route_missing" })
assert.deepStrictEqual(resolveTodoRoute({ ...inventoryApproval, contextDeptId: null }, {
  platform: "desktop", permissions: ["inv:transfer:approve"], availableRouteSet, currentContext: inventoryContext
}), { ok: false, reason: "organization_required" })
assert.deepStrictEqual(resolveTodoRoute(inventoryApproval, {
  platform: "desktop", permissions: ["inv:transfer:approve"], availableRouteSet,
  currentContext: { deptId: 21, deptName: "其他仓", deptType: "WAREHOUSE" }
}), { ok: false, reason: "organization_required" })
assert.ok(!cases.some(entry => entry[0] === "HR_OFFBOARD_ACCOUNT" && entry[1].includes("system/user")))

const routeSetFromRegistration = buildAvailableRouteSet({
  constantRoutes: [
    {
      path: "/mobile",
      children: [
        { path: "stock-check", hidden: true },
        { path: "contract", hidden: true }
      ]
    }
  ],
  dynamicRoutes: [
    {
      path: "/hr",
      permissions: ["hr:root"],
      children: [
        { path: "employee", meta: { permissions: ["hr:employee:list"] } },
        { path: "onboarding", permissions: ["hr:onboarding:list"] },
        { path: "/hr/completeness", permissions: ["hr:completeness:list"] }
      ]
    },
    {
      path: "/inventory",
      meta: { permissions: "inv:root" },
      children: [{ path: "stock", hidden: true }]
    }
  ],
  permissions: ["hr:root", "hr:employee:list", "hr:onboarding:list", "inv:root"]
})
assert.ok(routeSetFromRegistration.has("/mobile/stock-check"), "hidden constant routes must remain navigable")
assert.ok(routeSetFromRegistration.has("/mobile/contract"))
assert.ok(routeSetFromRegistration.has("/hr/employee"), "relative dynamic children must use their full registered path")
assert.ok(routeSetFromRegistration.has("/hr/onboarding"))
assert.ok(routeSetFromRegistration.has("/inventory/stock"), "meta.permissions may authorize dynamic routes")
assert.ok(!routeSetFromRegistration.has("/hr/completeness"), "denied dynamic children must not leak into available routes")
assert.ok(!routeSetFromRegistration.has("/mobile/hr/employee"), "unregistered future mobile HR routes must not be invented")

const wildcardRouteSet = buildAvailableRouteSet({
  constantRoutes: [],
  dynamicRoutes: [{ path: "/secure", permissions: ["secure:list"] }],
  permissions: ["*:*:*"]
})
assert.ok(wildcardRouteSet.has("/secure"), "superuser wildcard must authorize dynamic routes")

const missingRegisteredHrMobile = resolveTodoRoute({
  source: "system", type: "HR_PROFILE_INCOMPLETE", businessId: 20,
  contextDeptId: 20, contextDeptName: "华东仓", contextDeptType: "WAREHOUSE"
}, {
  platform: "mobile", permissions: [], availableRouteSet: routeSetFromRegistration,
  currentContext: inventoryContext
})
assert.deepStrictEqual(missingRegisteredHrMobile, { ok: false, reason: "route_missing" })

;["__proto__", "constructor", "prototype"].forEach(type => {
  assert.doesNotThrow(() => {
    assert.deepStrictEqual(resolveTodoRoute({ type }, {
      platform: "desktop", permissions: [], availableRouteSet, currentContext: inventoryContext
    }), { ok: false, reason: "route_missing" })
  }, `prototype-like todo type ${type} must not escape the route matrix`)
})

const routeParamBase = {
  source: "inventory", type: "INV_TRANSFER_RECEIVE", businessId: 77,
  contextDeptId: 20, contextDeptName: "华东仓", contextDeptType: "WAREHOUSE",
  routeParams: { businessId: "77", status: "pending", direction: "receive" }
}
const copiedProviderQuery = resolveTodoRoute(routeParamBase, {
  platform: "desktop", permissions: [], availableRouteSet, currentContext: inventoryContext
})
assert.deepStrictEqual(copiedProviderQuery.location.query, {
  businessId: "77", status: "pending", direction: "receive", todoType: "INV_TRANSFER_RECEIVE"
})

const hrProfileRoute = resolveTodoRoute({
  source: "system", type: "HR_PROFILE_INCOMPLETE", businessId: 20,
  contextDeptId: 20, contextDeptName: "华东仓", contextDeptType: "WAREHOUSE",
  routeParams: {
    contextDeptId: "20", contextDeptName: "华东仓", contextDeptType: "WAREHOUSE",
    deptId: "20", completenessStatus: "INCOMPLETE", count: "2", affectedCount: "2",
    scopeMode: "current_org"
  }
}, {
  platform: "desktop", permissions: [], availableRouteSet, currentContext: inventoryContext
})
assert.deepStrictEqual(hrProfileRoute.location.query, {
  contextDeptId: "20", contextDeptName: "华东仓", contextDeptType: "WAREHOUSE",
  deptId: "20", completenessStatus: "INCOMPLETE", count: "2", affectedCount: "2",
  scopeMode: "current_org", todoType: "HR_PROFILE_INCOMPLETE", businessId: "20"
})

const unsafeRouteParams = [
  JSON.parse('{"businessId":"77","__proto__":"pollute"}'),
  JSON.parse('{"businessId":"77","constructor":"pollute"}'),
  JSON.parse('{"businessId":"77","prototype":"pollute"}'),
  { businessId: "77", nested: { status: "pending" } },
  ["not", "plain"]
]
unsafeRouteParams.forEach(routeParams => {
  assert.deepStrictEqual(resolveTodoRoute({ ...routeParamBase, routeParams }, {
    platform: "desktop", permissions: [], availableRouteSet, currentContext: inventoryContext
  }), { ok: false, reason: "route_missing" }, "unsafe route params must fail closed")
})

console.log("unifiedTodoRouteResolver tests passed")
