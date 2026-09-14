const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  getMobileRouteDefinition,
  getMobileRouteFeature,
  getMobileRouteRequiredPermissions,
  getMobileHomePath,
  getMobileBottomNav,
  getMobileHrBottomNav,
  getMobileQuickActions,
  getMobileRouteAccessDecision,
  getMobileRouteAccessRedirect,
  isMobileRouteAllowedForPermissions,
  isMobileAdminOnlyPath,
  isMobileContextOptionalPath,
  isMobileNavigationPath,
  resolveMobileNavigationRedirect
} = require("../src/views/mobile/mobileNavigation")
const { mobileRouteDefinitions } = require("../src/views/mobile/mobileRouteDefinitions")

const permissionSource = fs.readFileSync(
  path.resolve(__dirname, "../src/permission.js"),
  "utf8"
)
const workbenchShellSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/components/MobileWorkbenchShell.vue"),
  "utf8"
)

const normalizedRedirectHelperSource = permissionSource.slice(
  permissionSource.indexOf("const getNormalizedMobileRedirectInfo"),
  permissionSource.indexOf("const appendMobileRedirectNotice")
)

const hrRoutes = [
  ["/mobile/hr", "hrWorkbench", "hr:onboarding:workbench"],
  ["/mobile/hr/onboarding", "hrOnboardingList", "hr:onboarding:list"],
  ["/mobile/hr/onboarding/create", "hrOnboardingAdd", "hr:onboarding:add"],
  ["/mobile/hr/onboarding/42", "hrOnboardingDetail", "hr:onboarding:query"],
  ["/mobile/hr/onboarding/42/edit", "hrOnboardingEdit", "hr:onboarding:edit"]
]

assert.strictEqual(
  isMobileNavigationPath("/mobile/hr/onboarding"),
  true,
  "mobile access normalization should apply to mobile routes"
)

assert.strictEqual(
  isMobileNavigationPath("/hr/onboarding"),
  false,
  "desktop HR routes must bypass mobile access normalization"
)

const redirectCases = [
  {
    name: "desktop HR routes bypass mobile access normalization",
    options: { path: "/hr/onboarding", mobileViewport: true, mobileEntryPath: "/mobile/hr", validatedContext: true },
    expected: { redirect: "/hr/onboarding", reason: "", message: "" }
  },
  {
    name: "a narrow root entry resolves to the computed mobile home",
    options: {
      path: "/",
      mobileViewport: true,
      mobileEntryPath: "/mobile/hr",
      validatedContext: false,
      permissions: ["hr:onboarding:workbench"]
    },
    expected: { redirect: "/mobile/hr", reason: "", message: "" }
  },
  {
    name: "an unknown mobile route stays unchanged before context selection",
    options: { path: "/mobile/not-registered", mobileViewport: true, mobileEntryPath: "/mobile/hr", validatedContext: false },
    expected: { redirect: "/mobile/not-registered", reason: "", message: "" }
  },
  {
    name: "a selected context executes the real mobile access decision",
    options: {
      path: "/mobile/hr/onboarding",
      mobileViewport: true,
      mobileEntryPath: "/mobile/hr",
      validatedContext: true,
      deptType: "SHOP",
      permissions: []
    },
    expected: getMobileRouteAccessDecision("/mobile/hr/onboarding", "SHOP", [])
  }
]

redirectCases.forEach(({ name, options, expected }) => {
  const decision = resolveMobileNavigationRedirect(options)
  const normalizedExpected = expected.path
    ? { redirect: expected.path, reason: expected.reason, message: expected.message }
    : expected
  assert.deepStrictEqual(decision, normalizedExpected, name)
})

assert.ok(
  normalizedRedirectHelperSource.includes("resolveMobileNavigationRedirect"),
  "the global router guard should delegate to the executable redirect resolver"
)

assert.ok(
  permissionSource.includes("const mobileContextFreeSystemPaths = new Set(['/401', '/404', '/lock'])") &&
    permissionSource.includes("mobileContextFreeSystemPaths.has(path)"),
  "system recovery pages should not be blocked by mobile organization selection"
)

hrRoutes.forEach(([routePath, featureKey, permission]) => {
  const feature = getMobileRouteFeature(routePath)
  assert.strictEqual(
    feature && feature.featureKey,
    featureKey,
    `${routePath} should resolve through the shared exact-or-dynamic route matcher`
  )
  assert.deepStrictEqual(
    getMobileRouteRequiredPermissions(routePath),
    [permission],
    `${routePath} should prefer its route-definition permission metadata`
  )
  assert.strictEqual(
    isMobileContextOptionalPath(routePath),
    true,
    `${routePath} should not require a store or warehouse context`
  )
  assert.strictEqual(
    isMobileRouteAllowedForPermissions(routePath, []),
    false,
    `${routePath} should deny an account without ${permission}`
  )
  assert.strictEqual(
    isMobileRouteAllowedForPermissions(routePath, [permission]),
    true,
    `${routePath} should allow an account with ${permission}`
  )
})

const legacyHrPermissionRoutes = [
  ["/mobile/hr/employee", ["hr:employee:list"]],
  ["/mobile/hr/completeness", ["hr:completeness:list"]],
  ["/mobile/hr/health-certificate", [
    "hr:healthCertificate:self:edit",
    "hr:healthCertificate:list",
    "hr:healthCertificate:review"
  ]]
]

legacyHrPermissionRoutes.forEach(([routePath, expectedPermissions]) => {
  assert.strictEqual(
    Boolean(getMobileRouteFeature(routePath)),
    false,
    `${routePath} should exercise route-level permission metadata without a mobileFeature`
  )
  assert.deepStrictEqual(
    getMobileRouteRequiredPermissions(routePath),
    expectedPermissions,
    `${routePath} should consume its route-level meta.permissions`
  )
  ;[undefined, null, [], ["unrelated:permission"]].forEach(permissions => {
    assert.strictEqual(
      isMobileRouteAllowedForPermissions(routePath, permissions),
      false,
      `${routePath} should fail closed without one of its route-level permissions`
    )
  })
  expectedPermissions.forEach(permission => {
    assert.strictEqual(
      isMobileRouteAllowedForPermissions(routePath, [permission]),
      true,
      `${routePath} should allow its declared ${permission}`
    )
  })
  assert.strictEqual(
    isMobileRouteAllowedForPermissions(routePath, ["*:*:*"]),
    true,
    `${routePath} should allow the administrator wildcard`
  )
})

const employeeRouteWithoutPermissionMetadata = getMobileRouteDefinition("/mobile/hr/employee")
const employeePermissionMetadata = employeeRouteWithoutPermissionMetadata.meta.permissions
delete employeeRouteWithoutPermissionMetadata.meta.permissions
try {
  assert.strictEqual(
    isMobileRouteAllowedForPermissions("/mobile/hr/employee", ["*:*:*"]),
    false,
    "a registered route with omitted access metadata must fail closed even for an administrator"
  )
} finally {
  employeeRouteWithoutPermissionMetadata.meta.permissions = employeePermissionMetadata
}

assert.strictEqual(
  getMobileRouteDefinition("/mobile/hr/onboarding/not-a-number"),
  null,
  "onboarding detail matcher should reject non-numeric ids"
)

assert.strictEqual(
  getMobileRouteDefinition("/mobile/hr/onboarding/42/extra"),
  null,
  "onboarding detail matcher should reject trailing path segments"
)

assert.deepStrictEqual(
  getMobileRouteAccessDecision("/mobile/hr/onboarding/42", "", ["hr:onboarding:query"]),
  { path: "", reason: "", message: "" },
  "onboarding detail should be independently guarded by query permission"
)

assert.strictEqual(
  isMobileRouteAllowedForPermissions("/mobile/hr/onboarding/42", ["hr:onboarding:list"]),
  false,
  "list-only supervisors should not inherit onboarding detail access"
)

const expectedPermissionsByFeature = {
  drive: ["drive:access"],
  hrWorkbench: ["hr:onboarding:workbench"],
  hrOnboardingList: ["hr:onboarding:list"],
  hrOnboardingAdd: ["hr:onboarding:add"],
  hrOnboardingEdit: ["hr:onboarding:edit"],
  hrOnboardingDetail: ["hr:onboarding:query"],
  sales: ["inv:sales:list"],
  purchase: ["inv:purchase:list"],
  purchaseReturn: ["inv:purchaseReturn:list"],
  stock: ["inv:stock:list"],
  product: ["inv:product:list"],
  oe: ["inv:oe:list"],
  gift: ["inv:gift:list"],
  category: ["inv:category:list"],
  customer: ["inv:customerCard:list"],
  supplier: ["inv:supplier:list"],
  stockLog: ["inv:stock:log"],
  stockCheck: ["inv:stockCheck:list"],
  transferRecords: ["inv:transfer:records"],
  salesReturn: ["inv:salesReturn:list"],
  replenishment: ["inv:transfer:list"],
  outbound: ["inv:deliveryNotice:list"],
  transfer: ["inv:transfer:list"],
  transferApproval: ["inv:transfer:approve"],
  oaPurchaseApproval: ["oa:todo:approve"],
  reimbursement: [
    "oa:reimbursement:self",
    "oa:reimbursement:approve",
    "oa:reimbursement:finance:approve",
    "oa:reimbursement:finance:list"
  ],
  fixedAssetRepair: ["oa:fixedAsset:repair:list"],
  attendance: [
    "oa:attendance:punch:self",
    "oa:attendance:record:self",
    "oa:attendance:leave:self",
    "oa:attendance:leave:list",
    "oa:attendance:leave:approve",
    "oa:attendance:correction:self",
    "oa:attendance:correction:list",
    "oa:attendance:correction:approve"
  ],
  salary: [],
  notice: [],
  messages: [],
  profile: [],
  systemUser: ["system:user:list"],
  systemRole: ["system:role:list"],
  systemPost: ["system:post:list"],
  systemDept: ["system:dept:list"],
  systemMenu: ["system:menu:list"],
  userShop: ["system:userShop:list"],
  systemConfig: ["system:config:list"],
  systemDictType: ["system:dict:list"],
  systemDictData: ["system:dict:list"],
  systemLogininfor: ["system:logininfor:list"],
  systemOperlog: ["system:operlog:list"],
  monitorJob: ["monitor:job:list"],
  monitorJobLog: ["monitor:job:list"],
  monitorOnline: ["monitor:online:list"],
  salaryScheme: ["system:salary:list"],
  transferRules: ["inv:transfer:rule:list"],
  mine: []
}

const authenticatedSelfServiceFeatures = new Set(["salary", "notice", "messages", "profile", "mine"])
const definedMobileFeatures = mobileRouteDefinitions
  .map(definition => definition.meta && definition.meta.mobileFeature && definition.meta.mobileFeature.featureKey)
  .filter(Boolean)

assert.deepStrictEqual(
  [...new Set(definedMobileFeatures)].sort(),
  Object.keys(expectedPermissionsByFeature).sort(),
  "every registered mobile feature should be explicitly classified as permissioned or authenticated self-service"
)

mobileRouteDefinitions.forEach(definition => {
  const feature = definition.meta && definition.meta.mobileFeature
  if (!feature) return
  const expectedPermissions = expectedPermissionsByFeature[feature.featureKey]
  assert.deepStrictEqual(
    getMobileRouteRequiredPermissions(definition.path),
    expectedPermissions,
    `${feature.featureKey} should use the permission enforced by its real list endpoint`
  )
  assert.strictEqual(
    isMobileRouteAllowedForPermissions(definition.path, undefined),
    false,
    `${feature.featureKey} should fail closed when permission context is missing`
  )
  assert.strictEqual(
    isMobileRouteAllowedForPermissions(definition.path, null),
    false,
    `${feature.featureKey} should fail closed when permission context is null`
  )
  if (authenticatedSelfServiceFeatures.has(feature.featureKey)) {
    assert.strictEqual(
      isMobileRouteAllowedForPermissions(definition.path, []),
      true,
      `${feature.featureKey} should remain available as an authenticated self-service route`
    )
  } else {
    if (!isMobileAdminOnlyPath(definition.path)) {
      assert.strictEqual(
        isMobileRouteAllowedForPermissions(definition.path, expectedPermissions),
        true,
        `${feature.featureKey} should remain accessible with its legitimate permission`
      )
    }
  }
})

assert.strictEqual(
  isMobileRouteAllowedForPermissions("/mobile/not-registered", ["*:*:*"]),
  false,
  "truly unknown mobile routes should remain denied"
)

assert.strictEqual(
  getMobileHomePath("STORE", ["hr:onboarding:workbench"]),
  "/mobile/hr",
  "HR workbench permission should select the HR mobile homepage without role-name checks"
)

assert.strictEqual(
  getMobileHomePath("STORE", ["*:*:*"]),
  "/mobile/store",
  "the administrator wildcard should keep the organization persona instead of selecting HR"
)

assert.strictEqual(
  getMobileHomePath("WAREHOUSE", ["hr:onboarding:list"]),
  "/mobile/warehouse",
  "list-only supervisors should retain their warehouse homepage"
)

assert.deepStrictEqual(
  getMobileBottomNav("STORE", ["hr:onboarding:workbench"]).map(item => [item.label, item.path]),
  [
    ["工作台", "/mobile/hr"],
    ["我的", "/mobile/mine"]
  ],
  "workbench-only HR users should not see list links they cannot open"
)

assert.deepStrictEqual(
  getMobileBottomNav("STORE", ["hr:onboarding:workbench", "hr:onboarding:list"]).map(item => [item.label, item.path]),
  [
    ["工作台", "/mobile/hr"],
    ["人事", "/mobile/hr/onboarding"],
    ["待办", "/mobile/hr/onboarding"],
    ["我的", "/mobile/mine"]
  ],
  "HR accounts should receive the dedicated four-item bottom navigation"
)

assert.deepStrictEqual(
  getMobileBottomNav("STORE", ["*:*:*"]).map(item => item.label),
  ["工作台", "销售", "库存", "退货", "我的"],
  "the administrator wildcard should keep the store persona in global navigation"
)

assert.deepStrictEqual(
  getMobileHrBottomNav(["*:*:*"]).map(item => [item.label, item.path]),
  [
    ["工作台", "/mobile/hr"],
    ["人事", "/mobile/hr/onboarding"],
    ["待办", "/mobile/hr/onboarding"],
    ["我的", "/mobile/mine"]
  ],
  "an administrator already inside the HR shell should receive complete HR navigation"
)

;[undefined, null, []].forEach(permissions => {
  assert.deepStrictEqual(
    getMobileQuickActions("STORE", permissions),
    [],
    "permissioned quick actions must not appear before permissions are available"
  )
  assert.deepStrictEqual(
    getMobileBottomNav("STORE", permissions).map(item => item.path),
    ["/mobile/store", "/mobile/mine"],
    "global navigation should render only its safe skeleton before permissions are available"
  )
  assert.deepStrictEqual(
    getMobileHrBottomNav(permissions).map(item => item.path),
    ["/mobile/mine"],
    "HR navigation should render only its safe self-service item before permissions are available"
  )
})

;[
  "/mobile/store",
  "/mobile/warehouse",
  "/mobile/inventory",
  "/mobile/todo",
  "/mobile/contract",
  "/mobile/sign-package",
  "/mobile/onboard-data"
].forEach(routePath => {
  ;[undefined, null, []].forEach(permissions => {
    assert.strictEqual(
      isMobileRouteAllowedForPermissions(routePath, permissions),
      true,
      `${routePath} should remain available through the explicit authenticated/context whitelist`
    )
  })
})

assert.deepStrictEqual(
  getMobileBottomNav("STORE", ["hr:onboarding:list"]).map(item => item.path),
  ["/mobile/store", "/mobile/mine"],
  "list-only supervisors should retain the store navigation instead of switching to HR navigation"
)

assert.ok(
  getMobileQuickActions("STORE", ["hr:onboarding:list"]).some(item =>
    item.label === "入职任务" && item.path === "/mobile/hr/onboarding"
  ),
  "list-only supervisors should receive a visible onboarding-task quick action"
)

assert.strictEqual(
  isMobileRouteAllowedForPermissions("/mobile/hr", undefined),
  false,
  "HR route authorization should fail closed when permissions have not loaded"
)

assert.strictEqual(
  isMobileRouteAllowedForPermissions("/mobile/hr", null),
  false,
  "HR route authorization should fail closed for a null permission context"
)

assert.ok(
  workbenchShellSource.includes("Array.isArray(getters.permissions) ? getters.permissions : []"),
  "mobile workbench should hide permissioned items while account permissions are unavailable"
)

assert.deepStrictEqual(
  getMobileRouteAccessDecision("/mobile/sales", "STORE", ["hr:onboarding:workbench"]),
  {
    path: "/mobile/hr",
    reason: "permission-mismatch",
    message: "当前账号没有销售权限，已返回人事工作台"
  },
  "HR redirects should name the HR workbench instead of a store or warehouse"
)

assert.ok(
  permissionSource.includes("getMobileHomePath(getSelectedDeptType(), store.getters.permissions)") &&
    permissionSource.includes("getMobileRouteFeature(path)") &&
    permissionSource.includes("requiresBusinessContext === false"),
  "permission guard should pass account permissions into home selection and skip shop selection for context-free routes"
)

assert.ok(
  permissionSource.indexOf("const mobileRedirectInfo = getNormalizedMobileRedirectInfo(to.path)") > -1 &&
    permissionSource.indexOf("const mobileRedirectInfo = getNormalizedMobileRedirectInfo(to.path)") <
      permissionSource.indexOf("if (shouldSelectShop(to.path))"),
  "the executable mobile access resolver should run before shop selection"
)

hrRoutes.forEach(([routePath, , permission]) => {
  assert.strictEqual(
    getMobileRouteAccessDecision(routePath, "", []).reason,
    "permission-mismatch",
    `logged-in users without ${permission} should be denied even when no department context is selected`
  )
  assert.deepStrictEqual(
    getMobileRouteAccessDecision(routePath, "", [permission]),
    { path: "", reason: "", message: "" },
    `logged-in users with ${permission} should proceed without selecting a shop`
  )
})

;[1, 42, 987654321].forEach(id => {
  assert.strictEqual(
    getMobileRouteRequiredPermissions(`/mobile/hr/onboarding/${id}`)[0],
    "hr:onboarding:query",
    `dynamic onboarding detail ${id} should remain independently permission guarded`
  )
  assert.strictEqual(
    getMobileRouteAccessDecision(`/mobile/hr/onboarding/${id}/edit`, "", []).reason,
    "permission-mismatch",
    `direct edit navigation ${id} should fail closed without permission and without a shop redirect`
  )
})

assert.strictEqual(
  getMobileRouteAccessRedirect("/mobile/purchase", "STORE"),
  "/mobile/store",
  "store users should be redirected away from warehouse-only mobile purchase pages"
)

assert.strictEqual(
  getMobileRouteAccessRedirect("/mobile/outbound", "STORE"),
  "/mobile/store",
  "store users should be redirected away from warehouse-only mobile outbound pages"
)

assert.strictEqual(
  getMobileRouteAccessRedirect("/mobile/sales", "WAREHOUSE"),
  "/mobile/warehouse",
  "warehouse users should be redirected away from store-only mobile sales pages"
)

assert.strictEqual(
  getMobileRouteAccessRedirect("/mobile/replenishment", "WAREHOUSE"),
  "/mobile/warehouse",
  "warehouse users should be redirected away from store-only replenishment pages"
)

assert.strictEqual(
  getMobileRouteAccessRedirect("/mobile/fixed-asset-repair", "WAREHOUSE"),
  "/mobile/warehouse",
  "warehouse users should be redirected away from store-only fixed asset repair pages"
)

{
  const mineRoute = getMobileRouteDefinition("/mobile/mine")
  const actions = mineRoute.meta.mobileFeature.actions
  const repairAction = actions.find(action => action.path === "/mobile/fixed-asset-repair")
  assert.deepStrictEqual(
    repairAction.allowedDeptTypes,
    ["STORE"],
    "mine page should hide fixed asset repair when the selected mobile context is a warehouse"
  )
}

assert.deepStrictEqual(
  getMobileRouteAccessDecision("/mobile/system-user", "STORE", []),
  {
    path: "/mobile/mine",
    reason: "permission-mismatch",
    message: "当前账号没有用户权限，已返回我的页面"
  },
  "business users without system permissions should recover to an organization-independent page"
)

;[
  ["/mobile/system-user", "system:user:list"],
  ["/mobile/system-role", "system:role:list"],
  ["/mobile/system-post", "system:post:list"],
  ["/mobile/system-dept", "system:dept:list"],
  ["/mobile/system-menu", "system:menu:list"],
  ["/mobile/user-shop", "system:userShop:list"],
  ["/mobile/system-config", "system:config:list"],
  ["/mobile/system-dict-type", "system:dict:list"],
  ["/mobile/system-dict-data", "system:dict:list"],
  ["/mobile/system-logininfor", "system:logininfor:list"],
  ["/mobile/system-operlog", "system:operlog:list"],
  ["/mobile/monitor-job", "monitor:job:list"],
  ["/mobile/monitor-job-log", "monitor:job:list"],
  ["/mobile/monitor-online", "monitor:online:list"],
  ["/mobile/salary-scheme", "system:salary:list"],
  ["/mobile/transfer-rules", "inv:transfer:rule:list"]
].forEach(([routePath, permission]) => {
  assert.strictEqual(
    isMobileRouteAllowedForPermissions(routePath, []),
    false,
    `${routePath} should not be directly visible to roles without ${permission}`
  )
  assert.strictEqual(
    isMobileRouteAllowedForPermissions(routePath, [permission]),
    false,
    `${routePath} should remain hidden on mobile for non-admin roles even when ${permission} is granted`
  )
  assert.strictEqual(
    isMobileRouteAllowedForPermissions(routePath, ["*:*:*"]),
    true,
    `${routePath} should be directly visible only to the administrator wildcard permission`
  )
})

assert.deepStrictEqual(
  getMobileRouteAccessDecision("/mobile/system-user", "STORE", ["system:user:list"]),
  {
    path: "/mobile/mine",
    reason: "permission-mismatch",
    message: "当前账号没有用户权限，已返回我的页面"
  },
  "mobile admin/system pages should stay admin-only even when a non-admin role has the underlying list permission"
)

;[
  "/mobile/mine",
  "/mobile/profile",
  "/mobile/notice",
  "/mobile/messages",
  "/mobile/attendance",
  "/mobile/salary",
  "/mobile/contract",
  "/mobile/sign-package",
  "/mobile/onboard-data",
  "/mobile/hr/employee",
  "/mobile/hr/completeness",
  "/mobile/hr/health-certificate",
  "/mobile/system-user"
].forEach(routePath => {
  assert.strictEqual(
    isMobileContextOptionalPath(routePath),
    true,
    `${routePath} should not force organization selection for users without store or warehouse scope`
  )
})

;["/mobile/sales", "/mobile/outbound", "/mobile/stock-check"].forEach(routePath => {
  assert.strictEqual(
    isMobileContextOptionalPath(routePath),
    false,
    `${routePath} should still require a selected business organization`
  )
})

assert.ok(
  permissionSource.includes("isMobileContextOptionalPath") &&
    permissionSource.includes("isMobileContextOptionalPath(path)") &&
    permissionSource.includes("return false"),
  "permission guard should not redirect organization-optional mobile account pages to select-shop"
)

assert.deepStrictEqual(
  getMobileRouteAccessDecision("/mobile/purchase", "STORE"),
  {
    path: "/mobile/store",
    reason: "context-mismatch",
    message: "当前选择的是店铺，采购需要切换到仓库后使用"
  },
  "context-mismatched mobile routes should produce an explicit redirect reason and message"
)

assert.strictEqual(
  isMobileRouteAllowedForPermissions("/mobile/sales", []),
  false,
  "mobile sales route should require the sales list permission"
)

assert.strictEqual(
  isMobileRouteAllowedForPermissions("/mobile/sales", ["inv:sales:list"]),
  true,
  "mobile sales route should be allowed when the role has sales list permission"
)

assert.deepStrictEqual(
  getMobileRouteAccessDecision("/mobile/sales", "STORE", []),
  {
    path: "/mobile/store",
    reason: "permission-mismatch",
    message: "当前账号没有销售权限，已返回店铺工作台"
  },
  "store users without sales permission should be kept on the mobile store workbench instead of seeing a dead no-permission sales page"
)

assert.ok(
  permissionSource.includes("resolveMobileNavigationRedirect") &&
    permissionSource.includes("store.getters.permissions") &&
    permissionSource.includes("store.getters.driveEnabled") &&
    permissionSource.includes("mobileRedirectReason") &&
    permissionSource.includes("mobileRedirectMessage"),
  "permission guard should preserve mobile redirect reason and message in route query"
)

assert.ok(
  !getMobileBottomNav("WAREHOUSE", []).some(item => item.path === "/mobile/outbound"),
  "warehouse bottom nav should hide outbound when the role lacks outbound list permission"
)

assert.ok(
  !getMobileQuickActions("STORE", []).some(item => item.path === "/mobile/sales"),
  "store quick actions should hide sales when the role lacks sales list permission"
)

assert.ok(
  getMobileQuickActions("STORE", ["inv:sales:list"]).some(item => item.path === "/mobile/sales"),
  "store quick actions should show sales when the role has sales list permission"
)

console.log("mobileAccessBoundary tests passed")
