const assert = require("assert")
const {
  resolvePurchaseBusinessStage,
  purchaseBusinessStageLabel
} = require("../src/utils/purchaseBusinessStage")

assert.strictEqual(
  resolvePurchaseBusinessStage({ status: "submitted", qcStatus: "pending" }),
  "pending_qc"
)
assert.strictEqual(
  purchaseBusinessStageLabel({ status: "submitted", qcStatus: "pending" }),
  "待质检"
)
assert.strictEqual(
  purchaseBusinessStageLabel({ status: "submitted", receivedQuantity: 3, remainingQuantity: 7 }),
  "部分收货"
)
assert.strictEqual(
  purchaseBusinessStageLabel({ status: "submitted", receivedQuantity: 0, remainingQuantity: 10 }),
  "待收货"
)
assert.strictEqual(
  purchaseBusinessStageLabel({ status: "received", qcStatus: "passed" }),
  "已完成"
)

console.log("purchaseBusinessStage tests passed")
