const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const file = path.resolve(__dirname, "../src/views/workbench/todo/components/TodoQuickApproveDialog.vue")
const source = fs.readFileSync(file, "utf8")
const scriptMatch = source.match(/<script>([\s\S]*?)<\/script>/)
assert.ok(scriptMatch, "quick approval dialog script must exist")

const sandbox = { module: { exports: {} }, exports: {}, Number, String, Array, Object }
vm.runInNewContext(scriptMatch[1].replace(/export default/, "module.exports ="), sandbox, { filename: file })
const component = sandbox.module.exports

function createHarness(preview = {}, mode = "detail") {
  const vmInstance = {
    preview,
    mode,
    row: { businessNo: "TF-FALLBACK" },
    comment: "",
    ...component.methods
  }
  for (const [name, getter] of Object.entries(component.computed)) {
    Object.defineProperty(vmInstance, name, { enumerable: true, get: () => getter.call(vmInstance) })
  }
  return vmInstance
}

const harness = createHarness({
  transferId: 701,
  orderNo: "TF701",
  status: "submitted",
  transferType: "warehouse",
  totalAmount: null,
  approvalWarnings: ["库存价格仅供审批参考", ""],
  approvalSummary: {
    roundNo: 2,
    currentNodeOrder: 2,
    totalNodeCount: 3,
    currentNodeName: "门店要货审批",
    currentCandidateDisplayNames: ["王总", "李总"]
  },
  details: [
    { detailId: 1, itemType: "product", itemName: "大米", itemCode: "RM-01", quantity: 2, costPrice: 18.5, unit: "袋", spec: "5kg" },
    { detailId: 2, itemType: "gift", itemName: "礼盒", itemCode: "GF-02", quantity: 3, amount: 120, unit: "盒", grade: "A" }
  ]
})

assert.strictEqual(harness.detailRows.length, 2)
assert.strictEqual(harness.showFullDetails, true)
assert.strictEqual(harness.dialogTitle, "调拨审批详情")
assert.strictEqual(harness.detailTotal, 157)
assert.strictEqual(harness.currentNode, "门店要货审批")
assert.strictEqual(harness.approvalRound, "第 2 轮")
assert.strictEqual(harness.approvalProgress, "第 2 / 3 节点")
assert.strictEqual(harness.approvalCandidates, "王总、李总")
assert.deepStrictEqual(Array.from(harness.approvalWarnings), ["库存价格仅供审批参考"])
assert.strictEqual(harness.statusLabel("submitted"), "待审批")
assert.strictEqual(harness.transferTypeLabel("warehouse"), "门店要货")
assert.strictEqual(harness.itemTypeLabel("gift"), "礼盒")
assert.strictEqual(harness.specGrade(harness.detailRows[0]), "5kg")
assert.strictEqual(harness.lineAmount(harness.detailRows[0]), 37)
assert.strictEqual(harness.lineAmount(harness.detailRows[1]), 120)

const summary = harness.detailSummary({
  columns: [
    { property: undefined },
    { property: undefined },
    { property: "quantity" },
    { property: "amount" }
  ]
})
assert.strictEqual(summary[0], "合计")
assert.strictEqual(summary[2], "5")
assert.strictEqual(summary[3], "¥157.00")

assert.ok(!source.includes("查看完整详情"), "complete details must not require a second navigation")
const quickHarness = createHarness(harness.preview, "quick")
assert.strictEqual(quickHarness.showFullDetails, false)
assert.strictEqual(quickHarness.dialogTitle, "确认通过调拨审批")
assert.ok(source.includes("如需核对物料、收货信息和审批进度，请从“查看审批”进入。"))

console.log("todo quick approval in-place detail tests passed")
