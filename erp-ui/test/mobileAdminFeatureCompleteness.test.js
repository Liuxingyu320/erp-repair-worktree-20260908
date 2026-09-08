const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  MOBILE_ROUTES
} = require("../src/views/mobile/mobileNavigation")

const {
  mapMobileFeatureRows
} = require("../src/views/mobile/feature/featureMapper")

const routerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/router/index.js"),
  "utf8"
)
const mobileRouteSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/mobileRouteDefinitions.js"),
  "utf8"
)
const featureSearchConfigsSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureSearchConfigs.js"),
  "utf8"
)
const featureServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureService.js"),
  "utf8"
)
const featureMapperSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureMapper.js"),
  "utf8"
)

const adminMobileRoutes = [
  ["systemUser", "/mobile/system-user"],
  ["systemRole", "/mobile/system-role"],
  ["systemPost", "/mobile/system-post"],
  ["systemDept", "/mobile/system-dept"],
  ["systemMenu", "/mobile/system-menu"],
  ["userShop", "/mobile/user-shop"],
  ["systemConfig", "/mobile/system-config"],
  ["systemDictType", "/mobile/system-dict-type"],
  ["systemDictData", "/mobile/system-dict-data"],
  ["systemLogininfor", "/mobile/system-logininfor"],
  ["systemOperlog", "/mobile/system-operlog"],
  ["monitorJob", "/mobile/monitor-job"],
  ["monitorJobLog", "/mobile/monitor-job-log"],
  ["monitorOnline", "/mobile/monitor-online"],
  ["salaryScheme", "/mobile/salary-scheme"],
  ["transferRules", "/mobile/transfer-rules"]
]

adminMobileRoutes.forEach(([key, routePath]) => {
  assert.strictEqual(
    MOBILE_ROUTES[key],
    routePath,
    `${key} should have a dedicated phone-web route`
  )
  assert.ok(
    mobileRouteSource.includes(`path: '${routePath}'`) &&
      mobileRouteSource.includes(`featureKey: '${key}'`),
    `${routePath} should be registered as a mobile feature route`
  )
})

const mineRouteSource = mobileRouteSource.slice(
  mobileRouteSource.indexOf("path: '/mobile/mine'"),
  mobileRouteSource.length
)

assert.ok(
  routerSource.includes("mobileRouteDefinitions"),
  "router should mount the shared mobile route definition list"
)

;[
  "/mobile/system-user",
  "/mobile/system-role",
  "/mobile/system-dept",
  "/mobile/system-menu",
  "/mobile/user-shop",
  "/mobile/system-config",
  "/mobile/system-dict-type",
  "/mobile/system-logininfor",
  "/mobile/system-operlog",
  "/mobile/monitor-job",
  "/mobile/monitor-online",
  "/mobile/transfer-rules"
].forEach(routePath => {
  assert.ok(
    !mineRouteSource.includes(`path: '${routePath}'`),
    `mobile mine page should not expose ${routePath} as a default staff entry`
  )
})

assert.ok(
  featureServiceSource.includes("listUser") &&
    featureServiceSource.includes("listRole") &&
    featureServiceSource.includes("listPost") &&
    featureServiceSource.includes("listDept") &&
    featureServiceSource.includes("listMenu") &&
    featureServiceSource.includes("getMenu") &&
    featureServiceSource.includes("batchUserShop") &&
    featureServiceSource.includes("shopTree") &&
    featureServiceSource.includes("getUserShop") &&
    featureServiceSource.includes("listConfig") &&
    featureServiceSource.includes("listType") &&
    featureServiceSource.includes("listData") &&
    featureServiceSource.includes("listLogininfor") &&
    featureServiceSource.includes("listOperlog") &&
    featureServiceSource.includes("listJob") &&
    featureServiceSource.includes("listJobLog") &&
    featureServiceSource.includes("listOnline") &&
    featureServiceSource.includes("listSalaryScheme") &&
    featureServiceSource.includes("listTransferApprovalRule") &&
    featureServiceSource.includes("CONTEXT_SCOPED_FEATURES"),
  "mobile feature service should reuse desktop admin/monitor APIs without forcing store/warehouse filters"
)

assert.ok(
  featureSearchConfigsSource.includes("systemUser:") &&
    featureSearchConfigsSource.includes("systemRole:") &&
    featureSearchConfigsSource.includes("systemMenu:") &&
    featureSearchConfigsSource.includes("userShop:") &&
    featureSearchConfigsSource.includes("systemConfig:") &&
    featureSearchConfigsSource.includes("systemLogininfor:") &&
    featureSearchConfigsSource.includes("monitorJob:") &&
    featureSearchConfigsSource.includes("monitorOnline:") &&
    featureSearchConfigsSource.includes("transferRules:"),
  "mobile feature page should provide search controls for admin and monitor modules"
)

assert.ok(
  featureMapperSource.includes("mapSystemUserRow") &&
    featureMapperSource.includes("mapSystemRoleRow") &&
    featureMapperSource.includes("mapSystemMenuRow") &&
    featureMapperSource.includes("mapUserShopRow") &&
    featureMapperSource.includes("mapSystemConfigRow") &&
    featureMapperSource.includes("mapSystemOperlogRow") &&
    featureMapperSource.includes("mapMonitorJobRow") &&
    featureMapperSource.includes("mapTransferRulesRow"),
  "mobile feature mapper should cover admin and monitor desktop modules"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("systemMenu", [{
    menuId: 101,
    menuName: "库存管理",
    menuType: "C",
    perms: "inv:stock:list",
    path: "stock",
    component: "inventory/stock/index",
    status: "0"
  }]),
  [{
    title: "库存管理",
    code: "inv:stock:list",
    detail: "菜单 · stock · inventory/stock/index",
    status: "正常"
  }],
  "mobile system-menu rows should expose menu type, permission key, path, component, and status"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("userShop", [{
    userId: 10,
    userName: "zhangsan",
    nickName: "张三",
    phonenumber: "13800000000",
    dept: { deptName: "总部" },
    shopScopeLabel: "湖滨店、中央仓",
    status: "0"
  }]),
  [{
    title: "张三",
    code: "zhangsan",
    detail: "湖滨店、中央仓 · 13800000000",
    status: "正常"
  }],
  "mobile user-shop rows should show each user's authorized store and warehouse scope"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("systemUser", [{
    userId: 10,
    userName: "zhangsan",
    nickName: "张三",
    dept: { deptName: "杭州湖滨店" },
    phonenumber: "13800000000",
    status: "0"
  }]),
  [{
    title: "张三",
    code: "zhangsan",
    detail: "杭州湖滨店 · 13800000000",
    status: "正常"
  }],
  "mobile system-user rows should expose the same account fields as desktop user management"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("systemRole", [{
    roleId: 2,
    roleName: "店长",
    roleKey: "store_manager",
    roleSort: 3,
    status: "0"
  }]),
  [{
    title: "店长",
    code: "store_manager",
    detail: "排序 3",
    status: "正常"
  }],
  "mobile role rows should map desktop role name, key, sort, and status"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("systemDictData", [{
    dictCode: 21,
    dictLabel: "正常",
    dictValue: "0",
    dictType: "sys_normal_disable",
    status: "0"
  }]),
  [{
    title: "正常",
    code: "0",
    detail: "sys_normal_disable",
    status: "正常"
  }],
  "mobile dict-data rows should expose dictionary label, value, type, and status"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("systemOperlog", [{
    operId: 31,
    title: "用户管理",
    businessType: 2,
    operName: "admin",
    operIp: "127.0.0.1",
    status: 0
  }]),
  [{
    title: "用户管理",
    code: "OPER-31",
    detail: "修改 · admin · 127.0.0.1",
    status: "成功"
  }],
  "mobile operation-log rows should map desktop operation log fields"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("monitorJob", [{
    jobId: 8,
    jobName: "库存同步",
    jobGroup: "DEFAULT",
    cronExpression: "0 0/5 * * * ?",
    status: "0"
  }]),
  [{
    title: "库存同步",
    code: "JOB-8",
    detail: "DEFAULT · 0 0/5 * * * ?",
    status: "正常"
  }],
  "mobile job rows should map desktop schedule job fields"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("transferRules", [{
    ruleId: 6,
    ruleName: "门店补货审批",
    sourceDeptName: "湖滨店",
    targetDeptName: "中央仓",
    status: "0"
  }]),
  [{
    title: "门店补货审批",
    code: "RULE-6",
    detail: "湖滨店 · 中央仓",
    status: "正常"
  }],
  "mobile transfer-rule rows should expose the inventory approval rule configuration"
)
