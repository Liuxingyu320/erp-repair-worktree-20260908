const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = relative => fs.readFileSync(path.join(root, relative), "utf8")
const panelSource = read("src/views/hr/completeness/components/HrMasterDataIssues.vue")
const pageSource = read("src/views/hr/completeness/index.vue")
const mobileSource = read("src/views/mobile/hr/completeness/index.vue")
const apiSource = read("src/api/hr/completeness.js")

function loadSfc(source, globals = {}) {
  const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/import\s+[\s\S]*?\s+from\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")
  const sandbox = { module: { exports: {} }, exports: {}, Promise, Date, ...globals }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(script, sandbox)
  return sandbox.module.exports
}

for (const endpoint of ["master-data/summary", "master-data/issues", "master-data/codes"]) {
  assert.ok(apiSource.includes(endpoint), `master-data API must expose ${endpoint}`)
}
assert.ok(panelSource.includes("当前为观察模式"), "governance queue must explain the non-enforcing observation mode")
assert.ok(panelSource.includes("主数据门禁已开启"), "governance queue must explain the enforcing mode accurately")
assert.ok(panelSource.includes("去重受影响员工") && panelSource.includes("affectedEmployeeCount"),
  "governance summary must label the unique affected employee metric")
assert.ok(panelSource.includes("问题覆盖人次") && panelSource.includes("affectedOccurrenceCount"),
  "governance summary must keep issue occurrences separate from unique employees")
assert.ok(panelSource.includes("affectedOnly"), "governance queue must support focusing on affected employees")
assert.ok(panelSource.includes("hr:masterData:export"), "governance export must be permission-gated")
assert.ok(pageSource.includes("requiredCompletionPercent"), "desktop completeness must prefer the required metric")
assert.ok(pageSource.includes("coveragePercent"), "desktop completeness must retain the coverage metric")
assert.ok(pageSource.includes('completenessMetric: "REQUIRED"'), "desktop filters must explicitly request the required metric")
assert.ok(mobileSource.includes("missingRequiredFields"), "mobile queue must prioritize missing required fields")
assert.ok(mobileSource.includes('completenessMetric: "REQUIRED"'), "mobile todo queue must explicitly request the required metric")
assert.ok(mobileSource.includes("主数据健康摘要"), "mobile HR must expose the read-only master-data summary")
assert.ok(mobileSource.includes("getHrMasterDataSummary"), "mobile HR must load the master-data summary through the API")

const panel = loadSfc(panelSource, {
  getHrMasterDataSummary: () => Promise.resolve({ data: {} }),
  listHrMasterDataIssues: () => Promise.resolve({ rows: [], total: 0 }),
  getHrMasterDataIssueCodes: () => Promise.resolve({ data: [] })
})
const target = { permissions: ["hr:masterData:list", "system:dept:edit"], $router: { push(url) { target.pushed = url } } }
Object.entries(panel.methods).forEach(([name, method]) => { target[name] = method.bind(target) })
const safeRow = { actionUrl: "/system/dept?deptId=8" }
assert.strictEqual(target.canOpenAction(safeRow), true)
target.openAction(safeRow)
assert.strictEqual(target.pushed, safeRow.actionUrl)
assert.strictEqual(target.canOpenAction({ actionUrl: "//evil.example" }), false)
assert.strictEqual(target.canOpenAction({ actionUrl: "/system/config?configKey=x" }), false,
  "action links must also respect the target module permission")
target.permissions = ["hr:masterData:list", "system:dept:list"]
assert.strictEqual(target.canOpenAction(safeRow), false, "read-only target permission must not expose a misleading action")

console.log("hrMasterDataGovernance tests passed")
