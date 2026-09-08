const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.join(uiRoot, file), "utf8")

const confirmDialog = readUi("src/views/hr/onboarding/components/HrOnboardingConfirmDialog.vue")
const importDialog = readUi("src/views/hr/onboarding/components/HrOnboardingImportDialog.vue")
const runtimeMonitor = readUi("src/views/approval/manage/components/RuntimeMonitor.vue")
const oaTodoMapper = fs.readFileSync(path.join(repoRoot, "erp-modules/erp-oa/src/main/resources/mapper/oa/OaTodoMapper.xml"), "utf8")

assert.ok(confirmDialog.includes("conflictLabel(candidate)"))
assert.ok(!confirmDialog.includes("candidate.conflictType ||"), "conflict codes must not be rendered directly")
assert.ok(importDialog.includes("categoryLabel(scope.row.category)"))
assert.ok(importDialog.includes("importIssueListLabel(scope.row.warningCodes)"))
assert.ok(!importDialog.includes("plainText(scope.row.category)"), "import categories must not use their backend value as display text")
assert.ok(runtimeMonitor.includes("callbackErrorText(scope.row)"))
assert.ok(!runtimeMonitor.includes("scope.row.lastErrorMessage || scope.row.lastError"), "callback error codes/messages need a safe Chinese presenter")
assert.ok(!oaTodoMapper.includes("d.dept_name, o.last_error_code"), "desktop/mobile todos must not receive a raw fixed-asset error code")
assert.ok(!oaTodoMapper.includes("nullif(left(o.last_error"), "notification todos must not receive a raw remote error")

const approvalUiPath = path.join(uiRoot, "src/views/approval/manage/components/approvalUi.js")
const approvalUiSource = fs.readFileSync(approvalUiPath, "utf8")
  .replace(/export function /g, "function ")
  .replace(/export const /g, "const ")
const sandbox = { module: { exports: {} }, exports: {} }
sandbox.exports = sandbox.module.exports
vm.runInNewContext(`${approvalUiSource}\nmodule.exports = { statusLabel, businessCodeLabel, businessSubtypeLabel, validationIssueLabel }`, sandbox, { filename: approvalUiPath })
const labels = sandbox.module.exports

assert.strictEqual(labels.statusLabel("PENDING"), "待处理")
assert.strictEqual(labels.statusLabel("FUTURE_STATUS"), "未知状态")
assert.strictEqual(labels.businessCodeLabel("INV_TRANSFER"), "调拨审批")
assert.strictEqual(labels.businessCodeLabel("FUTURE_BUSINESS"), "其他审批业务")
assert.strictEqual(labels.businessSubtypeLabel("store_return"), "门店返仓")
assert.strictEqual(labels.validationIssueLabel("NODE_EMPTY"), "审批节点为空")
assert.strictEqual(labels.validationIssueLabel("FUTURE_ISSUE"), "其他配置问题")

const decorativeFiles = [
  "src/views/oa/index.vue",
  "src/views/oa/signTask/index.vue",
  "src/views/oa/salary/index.vue",
  "src/views/oa/fixedAsset/repair/index.vue",
  "src/views/oa/laborContract/index.vue",
  "src/views/oa/signPackage/index.vue",
  "src/views/oa/purchase/index.vue",
  "src/views/oa/attendance/index.vue",
  "src/views/oa/fixedAsset/config/index.vue",
  "src/views/drive/components/quota/DriveQuotaCenterDrawer.vue",
  "src/views/hr/components/HrProfileDetailDrawer.vue"
]
const decorativeSource = decorativeFiles.map(readUi).join("\n")
for (const text of [
  "OA WORKSPACE", "SIGNING TASKS", "PAYROLL", "ASSET REPORT", "CONTRACT ARCHIVE",
  "SIGNING ASSETS", "FIXED ASSETS", "CAPACITY &amp; POLICY", "EMPLOYEE ·"
]) {
  assert.ok(!decorativeSource.includes(text), `visible decorative English must be localized: ${text}`)
}

console.log("displayEnumLocalization tests passed")
