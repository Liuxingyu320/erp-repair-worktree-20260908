const MOBILE_CONTEXT_TYPES = {
  store: "STORE",
  warehouse: "WAREHOUSE"
}

const { mobileRouteDefinitions, MOBILE_ROUTES } = require("./mobileRouteDefinitions")
const { MOBILE_NAV_PROFILES } = require("./mobileRoutePolicy")

const PERMISSIONS = {
  drive: ["drive:access"],
  sales: ["inv:sales:list"],
  purchase: ["inv:purchase:list"],
  salesReturn: ["inv:salesReturn:list"],
  purchaseReturn: ["inv:purchaseReturn:list"],
  stock: ["inv:stock:list"],
  category: ["inv:category:list"],
  customer: ["inv:customer:list"],
  stockLog: ["inv:stock:log"],
  stockCheck: ["inv:stockCheck:list"],
  transferRecords: ["inv:transfer:records"],
  replenishment: ["inv:transfer:list"],
  transfer: ["inv:transfer:list"],
  transferApproval: ["inv:transfer:approve"],
  outbound: ["inv:deliveryNotice:list"],
  product: ["inv:product:list"],
  oe: ["inv:oe:list"],
  gift: ["inv:gift:list"],
  supplier: ["inv:supplier:list"],
  fixedAssetRepair: ["oa:fixedAsset:repair:list"],
  reimbursement: ["oa:reimbursement:self"],
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
  transferRules: ["inv:transfer:rule:list"]
}

const mobileSelfServiceFeatureKeys = [
  "attendance",
  "salary",
  "notice",
  "profile",
  "mine"
]

const mobileQuickActions = [
  { label: "销售开单", icon: "sales", tone: "blue", path: MOBILE_ROUTES.sales },
  { label: "采购入库", icon: "purchase", tone: "amber", path: MOBILE_ROUTES.purchase },
  { label: "查库存", icon: "search", tone: "blue", path: MOBILE_ROUTES.stock },
  { label: "盘点", icon: "check", tone: "teal", path: MOBILE_ROUTES.stockCheck }
]

const mobileBottomNav = [
  { label: "工作台", icon: "home", path: MOBILE_ROUTES.workbench },
  { label: "销售", icon: "cart", path: MOBILE_ROUTES.sales },
  { label: "采购", icon: "bag", path: MOBILE_ROUTES.purchase },
  { label: "库存", icon: "cube", path: MOBILE_ROUTES.stock },
  { label: "我的", icon: "user", path: MOBILE_ROUTES.mine }
]

const mobileAdminManageActions = []

const mobileUnavailableFeatureKeys = []

const mobileUnavailablePaths = mobileUnavailableFeatureKeys
  .map(key => MOBILE_ROUTES[key])
  .filter(Boolean)

const mobileContextOptionalPaths = [
  MOBILE_ROUTES.mine,
  MOBILE_ROUTES.profile,
  MOBILE_ROUTES.notice,
  MOBILE_ROUTES.drive,
  MOBILE_ROUTES.todo,
  MOBILE_ROUTES.signPackage
].filter(Boolean)

const mobileAuthenticatedUnpermissionedPaths = [
  MOBILE_ROUTES.storeWorkbench,
  MOBILE_ROUTES.warehouseWorkbench,
  MOBILE_ROUTES.workbench,
  MOBILE_ROUTES.todo,
  MOBILE_ROUTES.contract,
  MOBILE_ROUTES.signPackage,
  MOBILE_ROUTES.onboardData
].filter(Boolean)

const mobileMineActivePaths = [
  MOBILE_ROUTES.mine,
  MOBILE_ROUTES.profile,
  MOBILE_ROUTES.notice,
  MOBILE_ROUTES.drive,
  MOBILE_ROUTES.todo,
  MOBILE_ROUTES.signPackage
].filter(Boolean)

const mobileHrBottomNav = [
  { label: "工作台", icon: "home", path: MOBILE_ROUTES.hrWorkbench, permissions: ["hr:onboarding:workbench"] },
  { label: "人事", icon: "user", path: MOBILE_ROUTES.hrOnboardingList, permissions: ["hr:onboarding:list"] },
  { label: "待办", icon: "check", path: MOBILE_ROUTES.hrOnboardingList, query: { view: "tasks" }, permissions: ["hr:onboarding:list"] },
  { label: "我的", icon: "user", path: MOBILE_ROUTES.mine }
]

const mobileSelfBottomNav = [
  { label: "待办", icon: "check", path: MOBILE_ROUTES.todo },
  { label: "云盘", icon: "cloud", path: MOBILE_ROUTES.drive, permissions: PERMISSIONS.drive, featureFlag: "drive" },
  { label: "通知", icon: "bell", path: MOBILE_ROUTES.notice },
  { label: "我的", icon: "user", path: MOBILE_ROUTES.mine }
]

const mobileWorkflowBottomNav = [
  { label: "待办", icon: "check", path: MOBILE_ROUTES.todo },
  { label: "报销", icon: "document", path: MOBILE_ROUTES.reimbursement, permissions: PERMISSIONS.reimbursement },
  { label: "通知", icon: "bell", path: MOBILE_ROUTES.notice },
  { label: "我的", icon: "user", path: MOBILE_ROUTES.mine }
]

const mobileAdminBottomNav = [
  { label: "管理", icon: "home", path: MOBILE_ROUTES.systemUser, permissions: PERMISSIONS.systemUser },
  { label: "待办", icon: "check", path: MOBILE_ROUTES.todo },
  { label: "通知", icon: "bell", path: MOBILE_ROUTES.notice },
  { label: "我的", icon: "user", path: MOBILE_ROUTES.mine }
]

const mobileHrListQuickAction = {
  label: "入职任务",
  icon: "document",
  tone: "teal",
  path: MOBILE_ROUTES.hrOnboardingList,
  permissions: ["hr:onboarding:list"]
}

const mobileAdminOnlyFeatureKeys = [
  "systemUser",
  "systemRole",
  "systemPost",
  "systemDept",
  "systemMenu",
  "userShop",
  "systemConfig",
  "systemDictType",
  "systemDictData",
  "systemLogininfor",
  "systemOperlog",
  "monitorJob",
  "monitorJobLog",
  "monitorOnline",
  "salaryScheme",
  "transferRules"
]

const mobileRouteDefinitionByPath = mobileRouteDefinitions.reduce((routes, definition) => {
  routes[normalizeMobilePath(definition.path)] = definition
  return routes
}, {})

const storeProfile = {
  type: MOBILE_CONTEXT_TYPES.store,
  title: "店铺工作台",
  selectorPrefix: "店铺",
  overview: {
    title: "门店经营",
    subtitle: "销售 · 库存 · 退货 · 补货"
  },
  metrics: [
    { label: "今日销售", value: "0", unit: "单", icon: "trend", tone: "blue" },
    { label: "待发货", value: "0", unit: "单", icon: "truck", tone: "teal" },
    { label: "低库存", value: "0", unit: "种", icon: "alert", tone: "red" },
    { label: "待退货", value: "0", unit: "单", icon: "sales", tone: "amber" }
  ],
  priorityItems: [
    { title: "销售待发货", code: "STORE-SALES", meta: "门店订单 · 待处理", status: "待发货", tone: "blue", icon: "document", imageType: "boxes" },
    { title: "低库存商品", code: "STORE-STOCK", meta: "门店库存 · 建议补货", status: "待补货", tone: "red", icon: "warning", imageType: "scanner" },
    { title: "销售退货", code: "STORE-RETURN", meta: "客户退货 · 待确认", status: "待确认", tone: "amber", icon: "sales", imageType: "pallet" }
  ],
  quickActions: [
    { label: "销售开单", icon: "sales", tone: "blue", path: MOBILE_ROUTES.sales, permissions: PERMISSIONS.sales },
    { label: "查库存", icon: "search", tone: "blue", path: MOBILE_ROUTES.stock, permissions: PERMISSIONS.stock },
    { label: "销售退货", icon: "document", tone: "amber", path: MOBILE_ROUTES.salesReturn, permissions: PERMISSIONS.salesReturn, placement: "more" },
    { label: "调拨管理", icon: "warehouse", tone: "teal", path: MOBILE_ROUTES.transfer, permissions: PERMISSIONS.transfer },
    { label: "资产报修", icon: "document", tone: "amber", path: MOBILE_ROUTES.fixedAssetRepair, permissions: PERMISSIONS.fixedAssetRepair, placement: "more" },
    { label: "费用报销", icon: "document", tone: "teal", path: MOBILE_ROUTES.reimbursement, permissions: PERMISSIONS.reimbursement, placement: "more" },
    { label: "商品资料", icon: "cube", tone: "blue", path: MOBILE_ROUTES.product, permissions: PERMISSIONS.product, placement: "more" },
    { label: "OE资料", icon: "cube", tone: "teal", path: MOBILE_ROUTES.oe, permissions: PERMISSIONS.oe, placement: "more" },
    { label: "礼盒资料", icon: "document", tone: "amber", path: MOBILE_ROUTES.gift, permissions: PERMISSIONS.gift, placement: "more" },
    { label: "云盘", icon: "cloud", tone: "blue", path: MOBILE_ROUTES.drive, permissions: PERMISSIONS.drive, featureFlag: "drive", placement: "more" }
  ],
  bottomNav: [
    { label: "工作台", icon: "home", path: MOBILE_ROUTES.storeWorkbench },
    { label: "销售", icon: "cart", path: MOBILE_ROUTES.sales, permissions: PERMISSIONS.sales },
    { label: "库存", icon: "cube", path: MOBILE_ROUTES.stock, permissions: PERMISSIONS.stock },
    { label: "退货", icon: "document", path: MOBILE_ROUTES.salesReturn, permissions: PERMISSIONS.salesReturn },
    { label: "我的", icon: "user", path: MOBILE_ROUTES.mine }
  ]
}

const warehouseProfile = {
  type: MOBILE_CONTEXT_TYPES.warehouse,
  title: "仓库工作台",
  selectorPrefix: "仓库",
  overview: {
    title: "仓内作业",
    subtitle: "入库 · 出库 · 退货 · 调拨"
  },
  metrics: [
    { label: "待入库", value: "0", unit: "单", icon: "inbound", tone: "amber" },
    { label: "待出库", value: "0", unit: "单", icon: "truck", tone: "teal" },
    { label: "待盘点", value: "0", unit: "单", icon: "check", tone: "blue" },
    { label: "待退货", value: "0", unit: "单", icon: "return", tone: "red" }
  ],
  priorityItems: [
    { title: "采购待入库", code: "WH-INBOUND", meta: "供应商到货 · 待验收", status: "待入库", tone: "amber", icon: "package", imageType: "pallet" },
    { title: "销售待出库", code: "WH-OUTBOUND", meta: "销售订单 · 待拣货", status: "待出库", tone: "teal", icon: "truck", imageType: "boxes" },
    { title: "盘点差异", code: "WH-CHECK", meta: "仓位盘点 · 待复核", status: "待复核", tone: "blue", icon: "check", imageType: "shelf" },
    { title: "采购退货", code: "WH-PURCHASE-RETURN", meta: "供应商退货 · 待确认", status: "待退货", tone: "red", icon: "return", imageType: "pallet" }
  ],
  quickActions: [
    { label: "采购入库", icon: "purchase", tone: "amber", path: MOBILE_ROUTES.purchase, permissions: PERMISSIONS.purchase },
    { label: "发货处理", icon: "truck", tone: "teal", path: MOBILE_ROUTES.outbound, permissions: PERMISSIONS.outbound },
    { label: "采购退货", icon: "return", tone: "red", path: MOBILE_ROUTES.purchaseReturn, permissions: PERMISSIONS.purchaseReturn, placement: "more" },
    { label: "库存盘点", icon: "check", tone: "blue", path: MOBILE_ROUTES.stockCheck, permissions: PERMISSIONS.stockCheck },
    { label: "调拨处理", icon: "warehouse", tone: "teal", path: MOBILE_ROUTES.transfer, permissions: PERMISSIONS.transfer, placement: "more" },
    { label: "商品资料", icon: "cube", tone: "blue", path: MOBILE_ROUTES.product, permissions: PERMISSIONS.product, placement: "more" },
    { label: "OE资料", icon: "cube", tone: "teal", path: MOBILE_ROUTES.oe, permissions: PERMISSIONS.oe, placement: "more" },
    { label: "礼盒资料", icon: "document", tone: "amber", path: MOBILE_ROUTES.gift, permissions: PERMISSIONS.gift, placement: "more" },
    { label: "供应商", icon: "warehouse", tone: "amber", path: MOBILE_ROUTES.supplier, permissions: PERMISSIONS.supplier, placement: "more" },
    { label: "费用报销", icon: "document", tone: "teal", path: MOBILE_ROUTES.reimbursement, permissions: PERMISSIONS.reimbursement, placement: "more" },
    { label: "云盘", icon: "cloud", tone: "blue", path: MOBILE_ROUTES.drive, permissions: PERMISSIONS.drive, featureFlag: "drive", placement: "more" }
  ],
  bottomNav: [
    { label: "工作台", icon: "home", path: MOBILE_ROUTES.warehouseWorkbench },
    { label: "入库", icon: "bag", path: MOBILE_ROUTES.purchase, permissions: PERMISSIONS.purchase },
    { label: "出库", icon: "truck", path: MOBILE_ROUTES.outbound, permissions: PERMISSIONS.outbound },
    { label: "库存", icon: "cube", path: MOBILE_ROUTES.stock, permissions: PERMISSIONS.stock },
    { label: "我的", icon: "user", path: MOBILE_ROUTES.mine }
  ]
}

const mobileContextProfiles = {
  [MOBILE_CONTEXT_TYPES.store]: storeProfile,
  [MOBILE_CONTEXT_TYPES.warehouse]: warehouseProfile
}

function normalizeMobileContextType(deptType) {
  return deptType ? String(deptType).trim().toUpperCase() : ""
}

function getMobileContextProfile(deptType) {
  const normalizedType = normalizeMobileContextType(deptType)
  return mobileContextProfiles[normalizedType] || storeProfile
}

function hasGrantedMobilePermission(permission, permissions) {
  return Array.isArray(permissions) && permissions.indexOf(permission) > -1
}

function getMobileHomePath(deptType, permissions) {
  if (hasGrantedMobilePermission("hr:onboarding:workbench", permissions)) {
    return MOBILE_ROUTES.hrWorkbench
  }
  const normalizedType = normalizeMobileContextType(deptType)
  if (normalizedType === "COMPANY" || normalizedType === "GROUP") {
    return "/index"
  }
  return getMobileContextProfile(normalizedType).type === MOBILE_CONTEXT_TYPES.warehouse
    ? MOBILE_ROUTES.warehouseWorkbench
    : MOBILE_ROUTES.storeWorkbench
}

function getMobileContextRouteRedirect(path, deptType, permissions) {
  const homePath = getMobileHomePath(deptType, permissions)
  const profileType = getMobileContextProfile(deptType).type

  if (path === MOBILE_ROUTES.workbench) {
    return homePath
  }

  if (path === MOBILE_ROUTES.storeWorkbench && profileType === MOBILE_CONTEXT_TYPES.warehouse) {
    return homePath
  }

  if (path === MOBILE_ROUTES.warehouseWorkbench && profileType === MOBILE_CONTEXT_TYPES.store) {
    return homePath
  }

  return ""
}

function getMobileContextDisplayName(deptType) {
  const profileType = getMobileContextProfile(deptType).type
  return profileType === MOBILE_CONTEXT_TYPES.warehouse ? "仓库" : "店铺"
}

function getMobileRedirectTargetName(redirectPath, deptType) {
  if (redirectPath === MOBILE_ROUTES.hrWorkbench) return "人事工作台"
  if (redirectPath === MOBILE_ROUTES.warehouseWorkbench) return "仓库工作台"
  if (redirectPath === MOBILE_ROUTES.storeWorkbench) return "店铺工作台"
  if (redirectPath === MOBILE_ROUTES.todo) return "我的待办"
  if (redirectPath === MOBILE_ROUTES.mine) return "我的页面"
  if (redirectPath === MOBILE_ROUTES.notice) return "通知页面"
  if (redirectPath === "/index") return "系统首页"
  return getMobileContextDisplayName(deptType) + "工作台"
}

function createMobileRouteAccessInfo(redirect, reason, message) {
  return {
    redirect: redirect || "",
    reason: reason || "",
    message: message || ""
  }
}

function createMobileRouteAccessDecision(path, reason, message) {
  return {
    path: path || "",
    reason: reason || "",
    message: message || ""
  }
}

function normalizeMobilePath(path) {
  const pathname = String(path || "").split(/[?#]/)[0]
  return pathname.length > 1 ? pathname.replace(/\/+$/, "") : pathname
}

function isMobileNavigationPath(path) {
  return normalizeMobilePath(path).indexOf("/mobile/") === 0
}

function resolveMobileNavigationRedirect(options) {
  const settings = options || {}
  const path = settings.path || ""
  const isRootEntry = path === "/" || path === "/index"
  const entryPath = settings.mobileViewport && isRootEntry
    ? settings.mobileEntryPath
    : path

  if (!isMobileNavigationPath(entryPath)) {
    return { redirect: entryPath, reason: "", message: "" }
  }

  if (!settings.validatedContext && !isMobileContextOptionalPath(entryPath)) {
    return { redirect: entryPath, reason: "", message: "" }
  }

  const accessDecision = getMobileRouteAccessDecision(
    entryPath,
    settings.deptType,
    settings.permissions,
    settings.featureState
  )
  return accessDecision.path
    ? { redirect: accessDecision.path, reason: accessDecision.reason, message: accessDecision.message }
    : { redirect: entryPath, reason: "", message: "" }
}

function getMobileRouteDefinition(path) {
  const normalizedPath = normalizeMobilePath(path)
  const exactDefinition = mobileRouteDefinitionByPath[normalizedPath]
  if (exactDefinition) return exactDefinition
  return mobileRouteDefinitions.find(definition =>
    definition.routeMatcher instanceof RegExp && definition.routeMatcher.test(normalizedPath)
  ) || null
}

function getMobileRouteFeature(path) {
  const definition = getMobileRouteDefinition(path)
  return definition && definition.meta ? definition.meta.mobileFeature : null
}

function getMobileRoutePolicy(path) {
  const definition = getMobileRouteDefinition(path)
  return definition && definition.meta ? definition.meta.mobilePolicy || null : null
}

function getMobileAllowedDeptTypes(path) {
  const feature = getMobileRouteFeature(path)
  return feature && Array.isArray(feature.allowedDeptTypes)
    ? feature.allowedDeptTypes.map(type => normalizeMobileContextType(type)).filter(Boolean)
    : []
}

function getMobileUnavailablePaths() {
  return mobileUnavailablePaths.slice()
}

function isMobileUnavailablePath(path) {
  const normalizedPath = normalizeMobilePath(path)
  return mobileUnavailablePaths.some(routePath => routePath === normalizedPath)
}

function isMobileContextOptionalPath(path) {
  const normalizedPath = normalizeMobilePath(path)
  const policy = getMobileRoutePolicy(normalizedPath)
  if (policy) {
    return policy.contextRequired === false
  }
  const feature = getMobileRouteFeature(normalizedPath)
  if (feature && feature.requiresBusinessContext === false) {
    return true
  }
  return mobileContextOptionalPaths.some(routePath => routePath === normalizedPath)
}

function getMobileRouteAccessRedirect(path, deptType, permissions, featureState) {
  return getMobileRouteAccessDecision(path, deptType, permissions, featureState).path
}

function getRequiredContextMessage(path, deptType) {
  const allowedDeptTypes = getMobileAllowedDeptTypes(path)
  const feature = getMobileRouteFeature(path) || {}
  const featureTitle = feature.title || "该模块"
  const currentContextName = getMobileContextDisplayName(deptType)
  const requiresWarehouse = allowedDeptTypes.indexOf(MOBILE_CONTEXT_TYPES.warehouse) > -1 &&
    allowedDeptTypes.indexOf(MOBILE_CONTEXT_TYPES.store) === -1
  const requiresStore = allowedDeptTypes.indexOf(MOBILE_CONTEXT_TYPES.store) > -1 &&
    allowedDeptTypes.indexOf(MOBILE_CONTEXT_TYPES.warehouse) === -1

  if (requiresWarehouse) {
    return "当前选择的是" + currentContextName + "，" + featureTitle + "需要切换到仓库后使用"
  }
  if (requiresStore) {
    return "当前选择的是" + currentContextName + "，" + featureTitle + "需要切换到门店后使用"
  }
  return "当前选择的是" + currentContextName + "，" + featureTitle + "需要切换到门店或仓库后使用"
}

function isMobileRouteAllowedForContext(path, deptType) {
  const allowedDeptTypes = getMobileAllowedDeptTypes(path)
  if (!allowedDeptTypes.length) {
    return true
  }
  return allowedDeptTypes.indexOf(normalizeMobileContextType(deptType)) > -1
}

function getMobileRouteRequiredPermissions(path) {
  const definition = getMobileRouteDefinition(path)
  const meta = definition && definition.meta ? definition.meta : {}
  const feature = meta.mobileFeature || {}
  const configured = feature.permissions !== undefined
    ? feature.permissions
    : (PERMISSIONS[feature.featureKey] || meta.permissions)
  if (Array.isArray(configured)) return configured.slice()
  return configured ? [configured] : []
}

function hasMobileRoutePermissionMapping(path) {
  return getMobileRouteRequiredPermissions(path).length > 0
}

function isMobileAuthenticatedUnpermissionedPath(path) {
  const normalizedPath = normalizeMobilePath(path)
  return mobileAuthenticatedUnpermissionedPaths.some(routePath => routePath === normalizedPath)
}

function isMobileAdminOnlyPath(path) {
  const policy = getMobileRoutePolicy(path)
  if (policy) return policy.adminOnly === true
  const feature = getMobileRouteFeature(path) || {}
  return mobileAdminOnlyFeatureKeys.indexOf(feature.featureKey) > -1
}

function getMobileRouteFallbackPath(path, deptType, permissions) {
  const policy = getMobileRoutePolicy(path)
  const fallback = policy && policy.entryFallback
  if (fallback === "todo") return MOBILE_ROUTES.todo
  if (fallback === "mine" || fallback === "admin-home") return MOBILE_ROUTES.mine
  if (fallback === "hr-home" && hasGrantedMobilePermission("hr:onboarding:workbench", permissions)) {
    return MOBILE_ROUTES.hrWorkbench
  }
  return getMobileHomePath(deptType, permissions)
}

function isMobileFeatureEnabled(featureFlag, featureState) {
  if (!featureFlag) return true
  if (featureFlag === "drive") {
    return !!featureState && featureState.driveEnabled === true
  }
  return !!featureState && !!featureState.businessFeatures &&
    featureState.businessFeatures[featureFlag] === true
}

function getMobileRouteFeatureFlag(path) {
  const feature = getMobileRouteFeature(path) || {}
  return feature.featureFlag || (feature.featureKey === "drive" ? "drive" : "")
}

function isMobileRouteAllowedForPermissions(path, permissions, featureState) {
  if (featureState !== undefined && !isMobileFeatureEnabled(getMobileRouteFeatureFlag(path), featureState)) {
    return false
  }
  if (isMobileAdminOnlyPath(path)) {
    return Array.isArray(permissions) && permissions.indexOf("*:*:*") > -1
  }
  const definition = getMobileRouteDefinition(path)
  const feature = getMobileRouteFeature(path)
  if (!definition) return false
  if (!feature) {
    const requiredPermissions = getMobileRouteRequiredPermissions(path)
    return requiredPermissions.length > 0
      ? hasAnyMobilePermission(requiredPermissions, permissions)
      : isMobileAuthenticatedUnpermissionedPath(path)
  }
  if (!Array.isArray(permissions)) return false
  if (!hasMobileRoutePermissionMapping(path)) {
    return mobileSelfServiceFeatureKeys.indexOf(feature.featureKey) > -1
  }
  return hasAnyMobilePermission(getMobileRouteRequiredPermissions(path), permissions)
}

function getMobileRouteAccessDecision(path, deptType, permissions, featureState) {
  const normalizedPath = normalizeMobilePath(path)
  if (!isMobileFeatureEnabled(getMobileRouteFeatureFlag(normalizedPath), featureState)) {
    const redirect = getMobileRouteFallbackPath(normalizedPath, deptType, permissions)
    return createMobileRouteAccessDecision(
      redirect,
      "feature-disabled",
      (getMobileRouteFeatureFlag(normalizedPath) === "drive" ? "企业云盘" : "该业务功能") +
        "暂未开启，已返回" + getMobileRedirectTargetName(redirect, deptType)
    )
  }
  if (isMobileUnavailablePath(normalizedPath)) {
    const redirect = getMobileRouteFallbackPath(normalizedPath, deptType, permissions)
    return createMobileRouteAccessDecision(
      redirect,
      "unavailable",
      "该功能移动端暂未开放，已返回" + getMobileRedirectTargetName(redirect, deptType)
    )
  }

  const redirect = getMobileContextRouteRedirect(normalizedPath, deptType, permissions)
  if (redirect && normalizedPath === MOBILE_ROUTES.workbench) {
    return createMobileRouteAccessDecision(
      redirect,
      "entry-normalized",
      "已进入" + getMobileRedirectTargetName(redirect, deptType)
    )
  }

  if (redirect) {
    return createMobileRouteAccessDecision(
      redirect,
      "context-mismatch",
      "当前选择的是" + getMobileContextDisplayName(deptType) + "，已返回" + getMobileRedirectTargetName(redirect, deptType)
    )
  }

  if (!isMobileRouteAllowedForContext(normalizedPath, deptType)) {
    return createMobileRouteAccessDecision(
      getMobileRouteFallbackPath(normalizedPath, deptType, permissions),
      "context-mismatch",
      getRequiredContextMessage(normalizedPath, deptType)
    )
  }

  if (!isMobileRouteAllowedForPermissions(normalizedPath, permissions, featureState)) {
    const redirect = getMobileRouteFallbackPath(normalizedPath, deptType, permissions)
    const feature = getMobileRouteFeature(normalizedPath) || {}
    return createMobileRouteAccessDecision(
      redirect,
      "permission-mismatch",
      "当前账号没有" + (feature.title || "该模块") + "权限，已返回" + getMobileRedirectTargetName(redirect, deptType)
    )
  }

  return createMobileRouteAccessDecision()
}

function getMobileRouteAccessInfo(path, deptType, permissions, featureState) {
  const decision = getMobileRouteAccessDecision(path, deptType, permissions, featureState)
  return createMobileRouteAccessInfo(decision.path, decision.reason, decision.message)
}

function cloneList(items) {
  return (items || []).map(item => Object.assign({}, item))
}

function hasAnyMobilePermission(requiredPermissions, userPermissions) {
  if (!Array.isArray(requiredPermissions) || requiredPermissions.length === 0) {
    return true
  }
  if (!Array.isArray(userPermissions)) {
    return false
  }
  if (userPermissions.indexOf("*:*:*") > -1) {
    return true
  }
  return requiredPermissions.some(permission => userPermissions.indexOf(permission) > -1)
}

function filterMobileItemsByPermission(items, permissions, featureState, filterPermissions = true) {
  return cloneList(items).filter(item => {
    return isMobileFeatureEnabled(item.featureFlag, featureState) &&
      (!filterPermissions || hasAnyMobilePermission(item.permissions, permissions))
  })
}

function getMobileQuickActions(deptType, permissions, featureState) {
  const filterPermissions = arguments.length >= 2
  const actions = getMobileContextProfile(deptType).quickActions.slice()
  const isHrWorkbenchUser = hasGrantedMobilePermission("hr:onboarding:workbench", permissions)
  const isHrListUser = hasGrantedMobilePermission("hr:onboarding:list", permissions)
  if (isHrListUser && !isHrWorkbenchUser) {
    actions.unshift(mobileHrListQuickAction)
  }
  return filterMobileItemsByPermission(actions, permissions, featureState, filterPermissions)
}

function getMobileBottomNav(deptType, permissions, featureState) {
  const filterPermissions = arguments.length >= 2
  if (hasGrantedMobilePermission("hr:onboarding:workbench", permissions)) {
    return filterMobileItemsByPermission(mobileHrBottomNav, permissions, featureState, filterPermissions)
  }
  return filterMobileItemsByPermission(
    getMobileContextProfile(deptType).bottomNav,
    permissions,
    featureState,
    filterPermissions
  )
}

function getMobileRouteBottomNav(path, deptType, permissions, featureState) {
  const policy = getMobileRoutePolicy(path)
  const filterPermissions = arguments.length >= 3
  const filterItems = items => filterMobileItemsByPermission(
    items,
    permissions,
    featureState,
    filterPermissions
  )

  if (!policy) return getMobileBottomNav(deptType, permissions, featureState)
  if (policy.navProfile === MOBILE_NAV_PROFILES.NONE) return []
  if (policy.navProfile === MOBILE_NAV_PROFILES.HR) {
    return filterPermissions
      ? getMobileHrBottomNav(permissions, featureState)
      : cloneList(mobileHrBottomNav)
  }
  if (policy.navProfile === MOBILE_NAV_PROFILES.ADMIN) return filterItems(mobileAdminBottomNav)
  if (policy.navProfile === MOBILE_NAV_PROFILES.SELF) return filterItems(mobileSelfBottomNav)
  if (policy.navProfile === MOBILE_NAV_PROFILES.WORKFLOW) return filterItems(mobileWorkflowBottomNav)
  const effectiveType = policy.navProfile === MOBILE_NAV_PROFILES.STORE
    ? MOBILE_CONTEXT_TYPES.store
    : (policy.navProfile === MOBILE_NAV_PROFILES.WAREHOUSE
        ? MOBILE_CONTEXT_TYPES.warehouse
        : deptType)
  return getMobileBottomNav(effectiveType, permissions, featureState)
}

function getMobileHrBottomNav(permissions, featureState) {
  return filterMobileItemsByPermission(mobileHrBottomNav, permissions, featureState)
}

function isMobileBottomNavItemActive(itemPath, currentPath) {
  const normalizedItemPath = normalizeMobilePath(itemPath)
  const normalizedCurrentPath = normalizeMobilePath(currentPath)
  if (normalizedItemPath === MOBILE_ROUTES.mine) {
    return mobileMineActivePaths.indexOf(normalizedCurrentPath) > -1
  }
  return normalizedItemPath === normalizedCurrentPath
}

function getMobileAdminManageActions() {
  return cloneList(mobileAdminManageActions)
}

module.exports = {
  MOBILE_CONTEXT_TYPES,
  MOBILE_ROUTES,
  mobileQuickActions,
  mobileBottomNav,
  mobileAdminManageActions,
  mobileSelfServiceFeatureKeys,
  getMobileHomePath,
  getMobileContextRouteRedirect,
  getMobileRouteAccessDecision,
  getMobileRouteAccessInfo,
  getMobileRouteAccessRedirect,
  getMobileRouteDefinition,
  getMobileRouteFeature,
  getMobileRoutePolicy,
  getMobileAllowedDeptTypes,
  getMobileRouteRequiredPermissions,
  isMobileFeatureEnabled,
  isMobileAdminOnlyPath,
  isMobileContextOptionalPath,
  isMobileNavigationPath,
  resolveMobileNavigationRedirect,
  isMobileBottomNavItemActive,
  isMobileRouteAllowedForContext,
  isMobileRouteAllowedForPermissions,
  getMobileUnavailablePaths,
  isMobileUnavailablePath,
  getMobileContextProfile,
  getMobileQuickActions,
  getMobileBottomNav,
  getMobileRouteBottomNav,
  getMobileHrBottomNav,
  getMobileAdminManageActions,
  hasAnyMobilePermission
}
