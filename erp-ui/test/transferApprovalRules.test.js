const assert = require("assert")
const fs = require("fs")
const path = require("path")

const viewPath = path.resolve(__dirname, "../src/views/inventory/transfer/rules.vue")
const apiPath = path.resolve(__dirname, "../src/api/inventory/transferApprovalRule.js")
const source = fs.readFileSync(viewPath, "utf8")
const apiSource = fs.readFileSync(apiPath, "utf8")

function loadRulesComponent() {
  const scriptMatch = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, "transfer approval rules page should contain a script block")

  const module = { exports: {} }
  const script = scriptMatch[1]
    .replace(/import[\s\S]*?from\s+["'][^"']+["']\s*/g, "")
    .replace(/import\s+["'][^"']+["']\s*/g, "")
    .replace("export default", "module.exports =")

  new Function("module", "Treeselect", script)(module, {})
  return module.exports
}

function createVm(component) {
  const vm = {}
  Object.keys(component.methods).forEach(name => {
    vm[name] = component.methods[name]
  })
  Object.assign(vm, component.data.call(vm))
  return vm
}

const component = loadRulesComponent()
const vm = createVm(component)
vm.configMode = "advanced"

assert.deepStrictEqual(
  vm.form.nodes.map(node => node.nodeRole),
  ["level4_highest", "level3_highest", "operations_director", "general_manager"],
  "new rules should always contain the exact four system approval roles"
)

assert.deepStrictEqual(
  vm.form.nodes.map(node => node.postCode),
  ["", "", "yyzj", "zjl"],
  "operations director and general manager should use stable post codes"
)

vm.form.approvalMode = "quorum"
vm.form.requiredCount = 7
vm.form.allowSelfApprove = "1"
vm.form.nodes = [{ nodeRole: "post", postCode: "OTHER" }]

const payload = vm.buildSubmitForm.call(vm)

assert.strictEqual(payload.approvalMode, "all_nodes")
assert.strictEqual(payload.requiredCount, 0)
assert.strictEqual(payload.allowSelfApprove, "0")
assert.deepStrictEqual(
  payload.nodes.map(node => node.nodeRole),
  ["level4_highest", "level3_highest", "operations_director", "general_manager"]
)
assert.ok(payload.nodes.every(node => node.approvalMode === "any_one"))
assert.ok(payload.nodes.every(node => node.requiredCount === 1))

assert.ok(!source.includes("添加审批岗位"))
assert.ok(!source.includes("moveNode("))
assert.ok(!source.includes("deleteNode("))
assert.ok(!source.includes("允许自审"))
assert.ok(source.includes("候选人预览"))
assert.ok(source.includes("高于店长、低于运营总监和总经理"))
assert.ok(source.includes("按目标门店负责范围匹配运营总监，固定必审"))
assert.ok(source.includes("previewTransferApprovalCandidates"))
assert.ok(source.includes("<el-drawer"), "rule editing should use a step drawer")
assert.ok(source.includes(':title="drawerTitle"'), "rule drawer should expose its dynamic accessible title")
assert.ok(source.includes('id="transfer-approval-rule-drawer-title"'), "rule drawer should use a unique title id")
assert.ok(source.includes('drawer.setAttribute("aria-labelledby", "transfer-approval-rule-drawer-title")'), "rule drawer should not resolve another drawer's duplicate title id")
assert.ok(source.includes("<el-steps"), "rule editing should expose explicit steps")
assert.ok(source.includes("校验与模拟"))
assert.ok(source.includes("blockingIssues"), "blocking validation issues should be visible")
assert.ok(source.includes("warningAcknowledged"), "warnings should require explicit acknowledgement")
assert.ok(source.includes("candidateSource"), "simulation should explain candidate sources")
assert.ok(source.includes("validationTargetDeptId"), "save should carry the validated target store")

assert.ok(apiSource.includes("export function previewTransferApprovalCandidates"))
assert.ok(apiSource.includes("/candidate-preview"))
assert.ok(apiSource.includes("params: { targetDeptId }"))
assert.ok(apiSource.includes("export function validateTransferApprovalRule"))
assert.ok(apiSource.includes("/rule/validate"))
assert.ok(apiSource.includes("delTransferApprovalRule(ruleId, expectedVersion)"))
assert.ok(apiSource.includes("params: { expectedVersion }"))
assert.ok(source.includes("delTransferApprovalRule(row.ruleId, row.version)"))
