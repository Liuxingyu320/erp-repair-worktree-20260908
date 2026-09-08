const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const componentPath = path.join(rootDir, "src/views/hr/components/HrEmployeeList.vue")
const drawerPath = path.join(rootDir, "src/views/hr/components/HrProfileDetailDrawer.vue")
const editDrawerPath = path.join(rootDir, "src/views/hr/components/HrProfileEditDrawer.vue")
const configPath = path.join(rootDir, "src/views/hr/components/hrFieldConfig.js")
const completenessPath = path.join(rootDir, "src/views/hr/completeness/index.vue")

assert.ok(fs.existsSync(componentPath), "HR workbench component should exist")
assert.ok(fs.existsSync(drawerPath), "HR profile drawer component should exist")
assert.ok(fs.existsSync(editDrawerPath), "HR profile edit drawer component should exist")
assert.ok(fs.existsSync(configPath), "HR field config should exist")

const component = fs.readFileSync(componentPath, "utf8")
const drawer = fs.readFileSync(drawerPath, "utf8")
const editDrawer = fs.readFileSync(editDrawerPath, "utf8")
const config = fs.readFileSync(configPath, "utf8")
const completenessSource = fs.readFileSync(completenessPath, "utf8")

assert.ok(component.includes("hr-task-strip"), "workbench should render a top task queue strip")
assert.ok(component.includes("hr-smart-search"), "workbench should render a smart search area")
assert.ok(component.includes("advancedFilterOpen"), "workbench should support advanced filters")
assert.ok(component.includes("columnSettingOpen"), "workbench should support column settings")
assert.ok(component.includes("hr-employee-row"), "workbench should render two-line employee summary rows")
assert.ok(component.includes("HrProfileDetailDrawer"), "workbench should use the grouped profile detail drawer")
assert.ok(component.includes("HrProfileEditDrawer"), "workbench should use the grouped profile edit drawer")
assert.ok(component.includes("updateHrEmployee"), "workbench should save employee profile edits through the HR endpoint")
assert.ok(component.includes("handleEditProfile"), "workbench should open the grouped edit drawer from the detail drawer")
assert.ok(component.includes("handleSaveProfile"), "workbench should refresh data after saving grouped profile edits")
assert.ok(!component.includes("filteredRows"), "workbench should render server-paged rows directly")
assert.ok(component.includes("getHrEmployeeSummary"), "workbench task counts should use the backend summary")

assert.ok(config.includes("HR_TASKS"), "config should define HR task queue cards")
assert.ok(config.includes("DEFAULT_LIST_FIELDS"), "config should define default list fields")
assert.ok(config.includes("OPTIONAL_LIST_FIELDS"), "config should define optional list fields")
assert.ok(config.includes("SENSITIVE_FIELDS"), "config should define sensitive fields")
assert.ok(config.includes("DETAIL_GROUPS"), "config should define grouped detail fields")

for (const label of ["基础信息", "组织岗位", "身份户籍", "教育信息", "用工合同", "社保公积金", "联系人与银行", "数据记录"]) {
  assert.ok(config.includes(label), `detail groups should include ${label}`)
  assert.ok(drawer.includes(label), `drawer should render ${label}`)
  assert.ok(editDrawer.includes(label), `edit drawer should render ${label}`)
}

const defaultListSection = config.slice(config.indexOf("DEFAULT_LIST_FIELDS"), config.indexOf("OPTIONAL_LIST_FIELDS"))
for (const sensitive of ["idNumber", "bankAccount", "registeredResidence", "currentAddress"]) {
  assert.ok(!defaultListSection.includes(sensitive), `${sensitive} should not be a default list field`)
}

assert.ok(drawer.includes("缺失资料"), "drawer should show missing profile fields")
assert.ok(drawer.includes("missingFieldGroups"), "missing fields should be grouped into readable categories")
assert.ok(drawer.includes("系统自动生成项"), "drawer should distinguish derived system fields from editable gaps")
assert.ok(drawer.includes("organizationPath"), "work summary should collapse duplicate organization names")
assert.ok(drawer.includes("详细资料"), "drawer should offer grouped full details")
assert.ok(drawer.includes("编辑档案"), "drawer should expose edit entry point")
assert.ok(editDrawer.includes("保存档案"), "edit drawer should expose a save action")
assert.ok(editDrawer.includes("必填项未完整"), "edit drawer should explain required-field validation")
assert.ok(editDrawer.includes("DETAIL_GROUPS"), "edit drawer should reuse the shared field grouping config")

assert.ok(completenessSource.includes("switchSummary('incomplete')"))
assert.ok(completenessSource.includes("switchSummary('all')"))
assert.ok(!completenessSource.includes("@click=\"switchTab('employees')\""))
assert.ok(completenessSource.includes("openDepartment(scope.row)"))
assert.ok(completenessSource.includes("missingProfileLabels"))

function loadSfc(source, globals = {}) {
  const script = source.match(/<script>([\s\S]*?)<\/script>/)
  const transformed = script[1]
    .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")
  const sandbox = { module: { exports: {} }, exports: {}, ...globals }
  sandbox.exports = sandbox.module.exports
  require("vm").runInNewContext(transformed, sandbox)
  return sandbox.module.exports
}

const completenessComponent = loadSfc(completenessSource, {
  getHrCompletenessDepartments: () => Promise.resolve({ data: [] }),
  getHrCompletenessSummary: () => Promise.resolve({ data: {} }),
  listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 }),
  missingProfileLabels: () => ({ visible: [], remaining: 0 })
})
const vm = {
  ...completenessComponent.data.call({ $route: { query: {} } }),
  $route: { query: {} },
  $store: { getters: { permissions: [] } },
  $message: { error() {} }
}
Object.entries(completenessComponent.methods).forEach(([name, method]) => { vm[name] = method.bind(vm) })
vm.loadData = () => Promise.resolve()
vm.query = { pageNum: 3, pageSize: 10, keyword: "", completenessStatus: undefined, deptId: undefined }
vm.switchSummary("incomplete")
assert.strictEqual(vm.query.pageNum, 1)
assert.strictEqual(vm.query.completenessStatus, "INCOMPLETE")
vm.switchSummary("all")
assert.strictEqual(vm.query.completenessStatus, undefined)
vm.openDepartment({ deptId: 28 })
assert.strictEqual(vm.activeTab, "employees")
assert.strictEqual(vm.query.deptId, 28)
assert.strictEqual(vm.query.completenessStatus, "INCOMPLETE")

console.log("hrWorkbenchUx tests passed")
