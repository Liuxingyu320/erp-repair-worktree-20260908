const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")

const requiredViews = [
  "src/views/hr/employee/index.vue",
  "src/views/hr/onboarding/index.vue",
  "src/views/hr/completeness/index.vue",
  "src/views/hr/importExport/index.vue",
  "src/views/hr/positionConfig/index.vue"
]

for (const view of requiredViews) {
  assert.ok(fs.existsSync(path.join(rootDir, view)), `${view} should exist for the HR management menu route`)
}

const employeeApiPath = path.join(rootDir, "src/api/hr/employee.js")
const onboardingApiPath = path.join(rootDir, "src/api/hr/onboarding.js")
const completenessApiPath = path.join(rootDir, "src/api/hr/completeness.js")
const detailDrawerPath = path.join(rootDir, "src/views/hr/components/HrProfileDetailDrawer.vue")
const onboardingSqlPath = path.resolve(rootDir, "../sql/erp_user_hr_onboarding_20260710.sql")
const dockerOnboardingSqlPath = path.resolve(rootDir, "../docker/mysql/db/erp_user_hr_onboarding_20260710.sql")
assert.ok(fs.existsSync(employeeApiPath), "HR employee API wrapper should exist")
assert.ok(fs.existsSync(onboardingApiPath), "HR onboarding API wrapper should exist")
assert.ok(fs.existsSync(completenessApiPath), "HR completeness API wrapper should exist")
assert.ok(fs.existsSync(detailDrawerPath), "HR profile detail drawer should exist")
assert.ok(fs.existsSync(onboardingSqlPath), "HR onboarding SQL should exist")
assert.ok(fs.existsSync(dockerOnboardingSqlPath), "Docker HR onboarding SQL should exist")

const employeeApiSource = fs.readFileSync(employeeApiPath, "utf8")
const onboardingApiSource = fs.readFileSync(onboardingApiPath, "utf8")
const completenessApiSource = fs.readFileSync(completenessApiPath, "utf8")
const detailDrawerSource = fs.readFileSync(detailDrawerPath, "utf8")
const onboardingSql = fs.readFileSync(onboardingSqlPath, "utf8")
const dockerOnboardingSql = fs.readFileSync(dockerOnboardingSqlPath, "utf8")
assert.ok(employeeApiSource.includes("/system/hr/employee/list"), "employee API should own employee list")
assert.ok(employeeApiSource.includes("/system/hr/employee/summary"), "employee API should own employee summary")
assert.ok(employeeApiSource.includes("method: \"patch\""), "employee edit should use the canonical PATCH endpoint")
assert.ok(!employeeApiSource.includes("/system/hr/onboarding"), "employee API should not own onboarding calls")
assert.ok(!employeeApiSource.includes("/system/hr/completeness"), "employee API should not own completeness calls")
assert.ok(!employeeApiSource.includes("/importData"), "employee API should not present the legacy direct user import as onboarding")
assert.ok(onboardingApiSource.includes("/system/hr/onboarding/list"), "onboarding API should own onboarding list")
assert.ok(onboardingApiSource.includes("/system/hr/onboarding/import/preview"), "onboarding API should own preview-first imports")
assert.ok(completenessApiSource.includes("/system/hr/completeness/employees"), "completeness API should own employee completeness")
assert.ok(completenessApiSource.includes("/system/hr/completeness/departments"), "completeness API should own department completeness")
assert.ok(completenessApiSource.includes("/system/hr/completeness/summary"), "completeness API should own server totals")
for (const endpoint of ["/system/hr/onboarding/config/list", "/system/hr/onboarding/config/options", "/disable"]) {
  assert.ok(onboardingApiSource.includes(endpoint), `onboarding API should expose ${endpoint}`)
}
assert.ok(detailDrawerSource.includes("v-hasPermi=\"['hr:employee:edit']\""), "HR edit action should be hidden without edit permission")
assert.ok(onboardingSql.includes("hr:employee:edit"), "main HR onboarding SQL should create the employee edit permission")
assert.ok(onboardingSql.includes("hr/onboarding/index"), "main HR onboarding SQL should register the onboarding component")
assert.ok(onboardingSql.includes("hr/completeness/index"), "main HR onboarding SQL should register the completeness component")
assert.strictEqual(dockerOnboardingSql, onboardingSql, "Docker HR onboarding SQL should mirror the main migration")

const employeeView = fs.readFileSync(path.join(rootDir, "src/views/hr/employee/index.vue"), "utf8")
const onboardingView = fs.readFileSync(path.join(rootDir, "src/views/hr/onboarding/index.vue"), "utf8")
const completenessView = fs.readFileSync(path.join(rootDir, "src/views/hr/completeness/index.vue"), "utf8")
const importExportView = fs.readFileSync(path.join(rootDir, "src/views/hr/importExport/index.vue"), "utf8")
const positionConfigView = fs.readFileSync(path.join(rootDir, "src/views/hr/positionConfig/index.vue"), "utf8")

assert.ok(employeeView.includes("员工档案"), "employee page should render the HR employee profile title")
assert.ok(onboardingView.includes("入职管理"), "onboarding page should render the HR onboarding title")
assert.ok(onboardingView.includes('name: "HrOnboarding"'), "onboarding component name should match the menu keep-alive name")
assert.ok(onboardingSql.includes("'hr/onboarding/index', 'HrOnboarding'"), "menu SQL should register the same onboarding component name")
assert.ok(completenessView.includes("资料完整度"), "completeness page should render the HR completeness title")
assert.ok(importExportView.includes("人事导入导出"), "import/export page should render the HR import/export title")
assert.ok(positionConfigView.includes("岗位入职配置"), "position config page should render its title")
assert.ok(employeeView.includes('mode="employee"'), "employee page should use employee workbench mode")
assert.ok(onboardingView.includes("hr-onboarding-workbench"), "onboarding page should use the dedicated list/detail workbench")
assert.ok(completenessView.includes("账号待配置"), "completeness page should expose the account-risk tab")
assert.ok(completenessView.includes('accountConfigurationStatus: "MISSING"'), "account-risk tab must query the server")
assert.ok(!completenessView.includes("filteredRows"), "completeness rows must not be filtered in the browser")
assert.ok(importExportView.includes('mode="importExport"'), "import/export page should use import/export workbench mode")
assert.ok(onboardingSql.includes("'hr/positionConfig/index', 'HrPositionConfig'"), "menu SQL should register position config")
assert.ok(onboardingSql.includes("'hr:onboarding:config'"), "menu SQL should use the onboarding config permission")

console.log("hrPersonnelRoutes tests passed")
