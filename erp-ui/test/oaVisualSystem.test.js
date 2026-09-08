const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.join(root, file), "utf8")

const styleEntry = read("src/layout/index.vue")
const oaStyles = read("src/assets/styles/oa-workspace.scss")
const pageFiles = [
  "src/views/oa/index.vue",
  "src/views/oa/attendance/index.vue",
  "src/views/oa/salary/index.vue",
  "src/views/oa/purchase/index.vue",
  "src/views/oa/fixedAsset/config/index.vue",
  "src/views/oa/fixedAsset/repair/index.vue",
  "src/views/oa/laborContract/index.vue",
  "src/views/oa/signTask/index.vue",
  "src/views/oa/signPackage/index.vue"
]

assert.ok(styleEntry.includes("@/assets/styles/oa-workspace.scss"), "lazy desktop layout should load the OA visual system")

;[
  ".oa-workspace-page",
  ".oa-page-hero",
  ".oa-hero__actions",
  ".oa-metric-grid",
  ".oa-filter-card",
  ".oa-table-card",
  ".oa-tabs-shell",
  ".oa-module-card",
  ":focus-visible",
  "prefers-reduced-motion"
].forEach(contract => {
  assert.ok(oaStyles.includes(contract), `OA visual system should define ${contract}`)
})

pageFiles.forEach(file => {
  const source = read(file)
  assert.ok(source.includes("oa-workspace-page"), `${file} should opt into the OA workspace visual system`)
  assert.ok(source.includes("oa-page-hero"), `${file} should expose a consistent OA page heading`)
})

const overview = read("src/views/oa/index.vue")
assert.ok(
  overview.includes("oa-module-grid") &&
    overview.includes('type="button"') &&
    overview.includes(":aria-label=\"`\u8fdb\u5165${module.title}`\"") &&
    overview.includes("visibleModules"),
  "OA overview cards should be keyboard-operable, named and permission-aware"
)
assert.ok(
  oaStyles.includes("font: inherit;") && oaStyles.includes("color: inherit;"),
  "OA overview buttons should inherit the product typography and text color"
)

const attendance = read("src/views/oa/attendance/index.vue")
const attendanceShift = read("src/views/oa/attendance/components/ShiftManagement.vue")
const attendanceSchedule = read("src/views/oa/attendance/components/WeeklySchedule.vue")
const attendanceDayResult = read("src/views/oa/attendance/components/DayResultManagement.vue")
assert.ok(
    attendance.includes("attendance-center-card") &&
    attendance.includes("<shift-management") &&
    attendance.includes("<attendance-site-management") &&
    attendance.includes("<weekly-schedule") &&
    attendance.includes("<day-result-management") &&
    attendanceShift.includes("search-card oa-filter-card") &&
    attendanceSchedule.includes("schedule-card") &&
    attendanceDayResult.includes("preflight-summary") &&
    attendanceDayResult.includes("day-table-card"),
  "attendance V2 should separate shifts, attendance sites, scheduling and daily settlement into clear management surfaces"
)

const salary = read("src/views/oa/salary/index.vue")
assert.ok(
  salary.includes("pagePayrollAmount") &&
    salary.includes("\u672c\u9875\u5b9e\u53d1\u5408\u8ba1") &&
    salary.includes('class="salary-total"'),
  "salary should show a scoped summary and avoid inline amount styling"
)

;["src/views/oa/laborContract/index.vue", "src/views/oa/signPackage/index.vue"].forEach(file => {
  assert.ok(read(file).includes('class="oa-tabs-shell"'), `${file} should use the shared OA tab shell`)
})

console.log("OA visual system tests passed")
