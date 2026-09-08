const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const file = path.resolve(__dirname, "../src/utils/signDictionary.js")
let source = fs.readFileSync(file, "utf8")
source = source
  .replace(/export function /g, "function ")
  .replace(/export \{[\s\S]*?\}\s*$/, "")

const sandbox = { module: { exports: {} } }
vm.runInNewContext(`${source}\nmodule.exports = { signTaskReasonLabel }`, sandbox, { filename: file })
const { signTaskReasonLabel } = sandbox.module.exports

assert.strictEqual(signTaskReasonLabel("MANUAL_SIGN_EXCEL_IMPORT"), "签约名单导入")
assert.strictEqual(signTaskReasonLabel("FINAL_CONTRACT_GENERATED"), "最终合同已生成")
assert.strictEqual(signTaskReasonLabel("业务人工处理"), "业务人工处理")
assert.strictEqual(signTaskReasonLabel("UNREGISTERED_INTERNAL_CODE"), "业务原因已记录")
assert.strictEqual(signTaskReasonLabel(null), "-")

const drawer = fs.readFileSync(
  path.resolve(__dirname, "../src/views/oa/signTask/SignTaskDetailDrawer.vue"), "utf8")
assert.ok(drawer.includes("reasonLabel(scope.row.reasonCode)"))
assert.ok(!drawer.includes('label="原因分类" prop="reasonCode"'))

console.log("sign task reason localization tests passed")
