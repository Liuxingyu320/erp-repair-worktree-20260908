const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  MOBILE_ROUTES
} = require("../src/views/mobile/mobileNavigation")

const {
  mapMobileFeatureRows
} = require("../src/views/mobile/feature/featureMapper")

const {
  getMobileFeatureActions
} = require("../src/views/mobile/feature/featureActions")
const {
  FEATURE_ACTION_FALLBACKS
} = require("../src/views/mobile/feature/mobileFeaturePolicy")

const routerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/router/index.js"),
  "utf8"
)
const mobileRouteSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/mobileRouteDefinitions.js"),
  "utf8"
)
const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const featureServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureService.js"),
  "utf8"
)
const featureActionServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureActionService.js"),
  "utf8"
)
const mobileAttendanceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/attendance/index.vue"),
  "utf8"
)
const mobileExportConfigSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/mobileExportConfigs.js"),
  "utf8"
)
const oaPurchaseApprovalSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/oa/purchaseApproval/index.vue"),
  "utf8"
)
const oaPurchasePermissionSql = fs.readFileSync(
  path.resolve(__dirname, "../../sql/erp_oa_purchase_permission_alignment_20260704.sql"),
  "utf8"
)

const oaMobileRoutes = [
  ["attendance", "/mobile/attendance"],
  ["salary", "/mobile/salary"]
]

oaMobileRoutes.forEach(([key, routePath]) => {
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

;["oaPurchase", "oaTodo", "oaDone"].forEach(key => {
  assert.strictEqual(MOBILE_ROUTES[key], undefined, `${key} must not expose a retired mobile route`)
})
for (const routePath of ["/mobile/oa-purchase", "/mobile/oa-todo", "/mobile/oa-done"]) {
  assert.ok(!mobileRouteSource.includes(`path: '${routePath}'`), `${routePath} must be retired`)
}
assert.ok(
  mobileRouteSource.includes("path: '/mobile/oa-purchase-approval'") &&
    mobileRouteSource.includes("@/views/mobile/oa/purchaseApproval/index") &&
    mobileRouteSource.includes("permissions: 'oa:todo:approve'"),
  "unified OA purchase approval must use its dedicated mobile route"
)

const mineRouteSource = mobileRouteSource.slice(
  mobileRouteSource.indexOf("path: '/mobile/mine'"),
  mobileRouteSource.length
)
const salaryRouteSource = mobileRouteSource.slice(
  mobileRouteSource.indexOf("path: '/mobile/salary'"),
  mobileRouteSource.indexOf("path: '/mobile/mine'")
)

assert.ok(
  routerSource.includes("mobileRouteDefinitions"),
  "router should mount the shared mobile route definition list"
)

const mineDefaultOfficeRoutes = [
  "/mobile/attendance",
  "/mobile/salary"
]

mineDefaultOfficeRoutes.forEach(routePath => {
  assert.ok(
    mineRouteSource.includes(`path: '${routePath}'`),
    `mobile mine page should expose ${routePath} as an office-work entry`
  )
})

assert.ok(
  !mineRouteSource.includes("path: '/mobile/oa-purchase'") &&
    !mineRouteSource.includes("path: '/mobile/oa-todo'") &&
    !mineRouteSource.includes("path: '/mobile/oa-done'"),
  "mobile mine page must not expose retired OA purchase entries"
)

assert.ok(
  featureServiceSource.includes("listMyPurchases") &&
    !featureServiceSource.includes("listTodoPurchases") &&
    !featureServiceSource.includes("listDonePurchases") &&
    featureServiceSource.includes("listMySalary") &&
    featureServiceSource.includes("getOaPurchaseDetail") &&
    featureServiceSource.includes("getSalary"),
  "generic mobile feature service should retain purchase and salary APIs"
)

assert.ok(
  mobileRouteSource.includes("@/views/mobile/attendance/index") &&
    mobileAttendanceSource.includes("getTodayAttendanceContext") &&
    mobileAttendanceSource.includes("createPunchChallenge") &&
    mobileAttendanceSource.includes("navigator.geolocation.getCurrentPosition") &&
    mobileAttendanceSource.includes("getUserMedia(") &&
    mobileAttendanceSource.includes('ref="liveCameraVideo"') &&
    !mobileAttendanceSource.includes('capture="environment"') &&
    !featureServiceSource.includes("normalizeAttendanceQuery") &&
    !featureServiceSource.includes("getAttendanceRecord"),
  "attendance must use its dedicated challenge, location and camera page instead of the legacy generic list endpoint"
)

assert.ok(
  salaryRouteSource.includes("salaryMonthScope: 'all'") &&
    featureServiceSource.includes("salaryMonthScope") &&
    featureServiceSource.includes("delete salaryQuery.salaryMonthScope"),
  "mobile salary all-records action should not be overwritten by the current-month default"
)

assert.ok(
  featurePageSource.includes("runTopFeatureAction") &&
    featurePageSource.includes("action.actionId") &&
    Array.isArray(FEATURE_ACTION_FALLBACKS.oaPurchase) &&
    FEATURE_ACTION_FALLBACKS.attendance === undefined &&
    Array.isArray(FEATURE_ACTION_FALLBACKS.salary),
  "generic mobile feature page should not expose attendance punch fallbacks"
)

assert.ok(
  featurePageSource.includes("resolveTopActionItem(action)") &&
    !featurePageSource.includes("resolveAttendanceActionItem(action)") &&
    !featureActionServiceSource.includes("listMyRecords as listMyAttendanceRecords") &&
    !featureActionServiceSource.includes("listMyAttendanceRecords"),
  "generic action service must not retain the retired attendance checkout lookup"
)

assert.ok(
  featureActionServiceSource.includes("submitOaPurchase") &&
    !featureActionServiceSource.includes("approvePurchase") &&
    !featureActionServiceSource.includes("checkIn") &&
    !featureActionServiceSource.includes("checkOut") &&
    featureActionServiceSource.includes("calculateSalary"),
  "mobile generic action service should retain OA submission but remove legacy attendance actions"
)

assert.ok(
  mobileExportConfigSource.includes('oaPurchase: { url: "oa/purchase/export/my", name: "我的采购申请", permissions: ["oa:purchase:export"] }') &&
    !mobileExportConfigSource.includes("oa/purchase/export/todo") &&
    !mobileExportConfigSource.includes("oa/purchase/export/done"),
  "mobile OA exports must not expose the retired todo and done endpoints"
)

assert.ok(
  oaPurchaseApprovalSource.includes("getApprovalInstance") &&
    oaPurchaseApprovalSource.includes("approveApprovalTask") &&
    oaPurchaseApprovalSource.includes("returnApprovalTask") &&
    oaPurchaseApprovalSource.includes("rejectApprovalTask"),
  "dedicated mobile OA approval must execute tasks through the unified approval APIs"
)

assert.ok(
  oaPurchaseApprovalSource.includes("hasValidPurchaseId ? '重试' : '返回待办'") &&
    oaPurchaseApprovalSource.includes('this.$router.replace("/mobile/todo")') &&
    oaPurchaseApprovalSource.includes("OA_PURCHASE:${this.approvalTaskId}:${action}:v1") &&
    !oaPurchaseApprovalSource.includes("OA_PURCHASE:${this.approvalTaskId}:${action}:${Date.now()}"),
  "missing purchase context should return to todo and approval retries should keep a stable request id"
)

assert.ok(
  oaPurchaseApprovalSource.includes('inputmode="decimal"') &&
    oaPurchaseApprovalSource.includes("function normalizeAmount(value)") &&
    !oaPurchaseApprovalSource.includes('v-model.number="editForm.amount"'),
  "mobile purchase amounts should remain decimal text instead of being coerced through floating-point v-model"
)

assert.ok(
  oaPurchasePermissionSql.includes("status = '0'") &&
    oaPurchasePermissionSql.includes("visible = '0'") &&
    oaPurchasePermissionSql.includes("'oa:purchase:list'") &&
    oaPurchasePermissionSql.includes("'oa:purchase:query'") &&
    oaPurchasePermissionSql.includes("'oa:purchase:add'") &&
    oaPurchasePermissionSql.includes("'oa:purchase:export'") &&
    oaPurchasePermissionSql.includes("'yyzj'") &&
    oaPurchasePermissionSql.includes("'dz'"),
  "OA purchase permission alignment SQL should enable the purchase page/buttons for intended mobile business roles"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("oaPurchase", [{
    purchaseId: 1001,
    title: "门店茶具采购",
    amount: 328.5,
    status: "pending",
    createTime: "2026-06-10 09:30:00"
  }]),
  [{
    title: "门店茶具采购",
    code: "1001",
    detail: "¥328.50 · 2026-06-10 09:30",
    status: "审批中"
  }],
  "mobile OA purchase rows should map desktop purchase application fields"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("attendance", [{
    recordId: 7,
    workDate: "2026-06-10",
    checkInTime: "09:02",
    checkOutTime: "18:08",
    workHours: 8,
    status: "late"
  }]),
  [{
    title: "2026-06-10",
    code: "ATT-7",
    detail: "上班 09:02 · 下班 18:08 · 工时 8h",
    status: "迟到"
  }],
  "mobile attendance rows should map desktop attendance record fields"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("attendance", [{
    recordId: 8,
    workDate: "2026-07-04T16:00:00.000Z",
    checkInTime: "2026-07-05 15:49:46",
    checkOutTime: "2026-07-05 15:49:57",
    status: "late_and_early"
  }]),
  [{
    title: "2026-07-05",
    code: "ATT-8",
    detail: "上班 15:49 · 下班 15:49",
    status: "迟到+早退"
  }],
  "mobile attendance rows should show local dates and times instead of raw UTC strings"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("salary", [{
    salaryId: 9,
    salaryMonth: "2026-06",
    userName: "王五",
    workDays: 21,
    totalSalary: 6800
  }]),
  [{
    title: "2026-06 工资",
    code: "SAL-9",
    detail: "王五 · 出勤 21 天 · ¥6800.00",
    status: "已生成"
  }],
  "mobile salary rows should map desktop salary statement fields"
)

assert.deepStrictEqual(
  getMobileFeatureActions("oaPurchase", { _statusKey: "draft", _raw: { purchaseId: 31 } }).map(action => action.id),
  ["editOaPurchase", "submitOaPurchase"],
  "draft OA purchase applications should expose mobile edit and submit"
)

assert.deepStrictEqual(
  getMobileFeatureActions("oaPurchase", { _statusKey: "returned", _raw: { purchaseId: 32 } }).map(action => action.id),
  ["editOaPurchase", "submitOaPurchase"],
  "returned OA purchase applications should remain editable and resubmittable"
)
