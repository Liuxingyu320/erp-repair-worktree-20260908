const assert = require("assert")
const fs = require("fs")
const path = require("path")

const read = relative => fs.readFileSync(path.resolve(__dirname, "../", relative), "utf8")
const api = read("src/api/hr/healthCertificate.js")
const desktop = read("src/views/hr/healthCertificate/index.vue")
const mobile = read("src/views/mobile/hr/healthCertificate/index.vue")

assert.ok(api.includes("getHealthCertificateCapability") && api.includes("/system/hr/health-certificate/capability"),
  "health certificate UI should load the fail-closed intake capability")
assert.ok(desktop.includes(":disabled=\"!intakeEnabled\"") && desktop.includes("requireIntake()"),
  "new, edit, draft, and submit actions should be disabled and guarded when intake is closed")
assert.ok(desktop.includes("历史查询、审核和提醒") || desktop.includes("历史记录；人事仍可处理已有待审核"),
  "maintenance guidance should explain which existing workflows remain available")
assert.ok(desktop.includes("if (popup && !popup.closed) popup.close()"),
  "failed attachment previews should close their empty popup")
assert.ok(
  mobile.includes("<health-certificate-page") &&
    !mobile.includes("getHealthCertificateCapability") &&
    desktop.includes("getHealthCertificateCapability"),
  "mobile health certificate entry should reuse the page capability request instead of issuing a duplicate request"
)
assert.ok(
  desktop.includes('class="admin-health-table"') &&
    mobile.includes(".admin-health-table .el-table__row td:nth-child(7)::before") &&
    mobile.includes(".mine-health-table .el-table__row td:nth-child(7)::before"),
  "mobile health certificate tables should render both personal and HR rows as labelled cards"
)
assert.ok(
  desktop.includes('custom-class="health-certificate-mine-dialog"') &&
    desktop.includes('custom-class="health-certificate-review-dialog"') &&
    mobile.includes("@media (max-width: 760px)") &&
    mobile.includes(".health-approval-dialog"),
  "appended health certificate dialogs should remain reachable within a phone viewport"
)
assert.ok(
  desktop.includes("HR_HEALTH_CERTIFICATE:${this.approvalTaskId}:${action}:v1") &&
    !desktop.includes("HR_HEALTH_CERTIFICATE:${this.approvalTaskId}:${action}:${Date.now()}"),
  "health certificate approval retries should reuse a stable action request id"
)

console.log("hrHealthCertificate.test.js passed")
