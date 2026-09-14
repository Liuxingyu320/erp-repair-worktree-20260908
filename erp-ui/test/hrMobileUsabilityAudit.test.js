const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = relative => fs.readFileSync(path.join(root, relative), "utf8")

function loadSfc(relative, globals = {}) {
  const source = read(relative)
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(match, `${relative} must contain a script block`)
  const script = match[1]
    .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")
  const sandbox = { module: { exports: {} }, exports: {},
    getSelectedDeptId: () => "10",
    require(id) { return require(path.resolve(root, "src", id.slice(2))) }, ...globals }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(script, sandbox, { filename: relative })
  return { component: sandbox.module.exports, source }
}

function bind(component, initial = {}) {
  const base = { ...initial }
  const target = { ...base, ...(component.data ? component.data.call(base) : {}), ...initial }
  Object.entries(component.methods || {}).forEach(([name, method]) => { target[name] = method.bind(target) })
  return target
}

const completeness = loadSfc("src/views/mobile/hr/completeness/index.vue", {
  listHrCompletenessEmployees: () => Promise.resolve({ rows: [], total: 0 }),
  profileFieldLabel: value => `字段${value}`
})
assert.ok(completeness.source.includes("查看全部"), "mobile completeness should collapse long missing-field lists")
assert.ok(completeness.source.includes("@click.stop=\"toggleMissing(row)\""), "expanding gaps must not navigate away")
assert.ok(completeness.source.includes('aria-expanded="isExpanded(row) ? \'true\' : \'false\'"'), "the disclosure state must be accessible")
assert.ok(completeness.source.includes('点击“去补资料”'), "the hint should match the explicit card action")

const completenessTarget = bind(completeness.component, {
  $set(target, key, value) { target[key] = value }
})
const incompleteRow = { userId: 9, missingProfileFields: ["A", "B", "C", "D", "E"] }
assert.deepStrictEqual(Array.from(completenessTarget.visibleMissingFields(incompleteRow)), ["字段A", "字段B", "字段C"])
assert.strictEqual(completenessTarget.remainingMissingCount(incompleteRow), 2)
completenessTarget.toggleMissing(incompleteRow)
assert.deepStrictEqual(Array.from(completenessTarget.visibleMissingFields(incompleteRow)), ["字段A", "字段B", "字段C", "字段D", "字段E"])
assert.strictEqual(completenessTarget.remainingMissingCount(incompleteRow), 0)
const namedIncompleteRow = { userId: 10, employeeName: "林晓", missingProfileFields: ["A", "B", "C", "D"] }
assert.strictEqual(completenessTarget.employeeAriaName(namedIncompleteRow), "林晓")
assert.strictEqual(completenessTarget.missingToggleAriaLabel(namedIncompleteRow), "林晓：展开缺失项，共 4 项")
completenessTarget.toggleMissing(namedIncompleteRow)
assert.strictEqual(completenessTarget.missingToggleAriaLabel(namedIncompleteRow), "林晓：收起缺失项，共 4 项")
assert.strictEqual(completenessTarget.employeeAriaName({ employeeName: "  " }), "该员工")
assert.ok(completeness.source.includes(':aria-label="`去补资料：${employeeAriaName(row)}`"'),
  "the supplement action should identify its employee")
assert.ok(completeness.source.includes(':aria-label="missingToggleAriaLabel(row)"'),
  "the missing-field disclosure should identify its employee, action and total")

const employee = loadSfc("src/views/mobile/hr/employee/index.vue", {
  listHrEmployees: () => Promise.resolve({ rows: [], total: 0 }),
  getHrEmployee: () => Promise.resolve({ data: null }),
  updateHrEmployee: () => Promise.resolve(),
  changeUserStatus: () => Promise.resolve(),
  getSelectedDeptContext: () => ({ deptName: "测试门店" }),
  MobileHrProfileEditor: {},
  mobileHrErrorMessage: (error, fallback) => fallback
})
assert.ok(employee.source.includes('role="search"'), "mobile employee archive should expose a search landmark")
assert.ok(employee.source.includes('aria-label="搜索员工档案"'), "mobile employee search needs an accessible name")
assert.strictEqual(employee.component.computed.contextLabel.call({ $route: { query: {} } }), "数据范围：当前权限")
assert.strictEqual(employee.component.computed.contextLabel.call({ $route: { query: { deptId: 1138 } } }), "数据范围：测试门店")
const query = employee.component.methods.query.call({
  pageNum: 1, pageSize: 10, keyword: "罗心怡", contractDue: false, offboardAccountOnly: false,
  $route: { query: {} }
})
assert.strictEqual(query.keyword, "罗心怡")
const employeeTarget = bind(employee.component)
assert.strictEqual(employeeTarget.employeeAriaName({ employeeName: "罗心怡" }), "罗心怡")
assert.strictEqual(employeeTarget.employeeAriaName({}), "该员工")
assert.ok(employee.source.includes(':aria-label="`编辑资料：${employeeAriaName(row)}`"'),
  "the edit action should identify its employee")
assert.ok(employee.source.includes(':aria-label="`停用账号：${employeeAriaName(row)}`"'),
  "the disable-account action should identify its employee")

const detailDrawer = read("src/views/hr/components/HrProfileDetailDrawer.vue")
const editDrawer = read("src/views/hr/components/HrProfileEditDrawer.vue")
for (const drawer of [detailDrawer, editDrawer]) {
  assert.ok(drawer.includes('@opened="syncDrawerAccessibility"'))
  assert.ok(drawer.includes('removeAttribute("aria-labelledby")'), "duplicate Element UI drawer title ids must not override the correct title")
}

console.log("hrMobileUsabilityAudit tests passed")
