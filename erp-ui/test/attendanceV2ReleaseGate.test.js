const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  getMobileRouteAccessDecision,
  getMobileRouteDefinition,
  isMobileFeatureEnabled,
  isMobileRouteAllowedForPermissions
} = require("../src/views/mobile/mobileNavigation")

const rootDir = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(rootDir, relativePath), "utf8")

const attendancePermissions = ["oa:attendance:punch:self"]
const disabledState = {
  driveEnabled: false,
  businessFeatures: { attendanceV2: false }
}
const enabledState = {
  driveEnabled: false,
  businessFeatures: { attendanceV2: true }
}

const attendanceRoute = getMobileRouteDefinition("/mobile/attendance")
const attendanceFeature = attendanceRoute && attendanceRoute.meta && attendanceRoute.meta.mobileFeature
assert.strictEqual(attendanceFeature && attendanceFeature.featureFlag, "attendanceV2",
  "the dedicated attendance route must use the authenticated Attendance V2 release flag")

const mineRoute = getMobileRouteDefinition("/mobile/mine")
const mineActions = mineRoute.meta.mobileFeature.actions
const attendanceEntry = mineActions.find(item => item.path === "/mobile/attendance")
assert.strictEqual(attendanceEntry && attendanceEntry.featureFlag, "attendanceV2",
  "the mine-page attendance entry must disappear while Attendance V2 is disabled")

assert.strictEqual(isMobileFeatureEnabled("attendanceV2", undefined), false,
  "missing authenticated feature state must fail closed")
assert.strictEqual(isMobileFeatureEnabled("attendanceV2", disabledState), false,
  "a disabled Attendance V2 flag must fail closed")
assert.strictEqual(isMobileFeatureEnabled("attendanceV2", enabledState), true,
  "an exact true Attendance V2 flag must enable the route")

assert.strictEqual(
  isMobileRouteAllowedForPermissions(
    "/mobile/attendance", attendancePermissions, disabledState
  ),
  false,
  "attendance permission alone must not bypass the release switch"
)
assert.deepStrictEqual(
  getMobileRouteAccessDecision(
    "/mobile/attendance", "STORE", attendancePermissions, disabledState
  ),
  {
    path: "/mobile/mine",
    reason: "feature-disabled",
    message: "该业务功能暂未开启，已返回我的页面"
  },
  "a disabled direct attendance URL must return to the mobile self-service fallback"
)
assert.deepStrictEqual(
  getMobileRouteAccessDecision(
    "/mobile/attendance", "STORE", attendancePermissions, enabledState
  ),
  { path: "", reason: "", message: "" },
  "an enabled Attendance V2 route must remain reachable to an authorized employee"
)

const routeSource = read("src/views/mobile/mobileRouteDefinitions.js")
const legacyRuntime = read("src/views/mobile/feature/featureActionRuntime.js")
const legacyActions = read("src/views/mobile/feature/featureActions.js")
const legacyService = read("src/views/mobile/feature/featureService.js")
assert.ok(
  /path:\s*['"]\/mobile\/attendance['"][\s\S]*?import\(['"]@\/views\/mobile\/attendance\/index['"]\)/.test(routeSource),
  "the released route must resolve only to the dedicated Attendance V2 page"
)
assert.ok(!legacyRuntime.includes("checkInAttendance") && !legacyRuntime.includes("checkOutAttendance"),
  "the generic runtime must not restore legacy attendance writes")
assert.ok(!legacyActions.includes("checkInAttendance") && !legacyActions.includes("checkOutAttendance"),
  "the generic action catalog must not restore legacy attendance buttons")
assert.ok(!legacyService.includes("@/api/oa/attendance"),
  "the V2 route chain must not import the legacy attendance API")

console.log("attendance V2 release gate tests passed")
