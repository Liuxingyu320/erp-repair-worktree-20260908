const assert = require("assert")
const {
  canReceivePurchase,
  canQualityCheckPurchase,
  canCancelPurchase
} = require("../src/views/inventory/purchase/purchaseActionRules")

assert.strictEqual(
  typeof canCancelPurchase,
  "function",
  "purchase action rules should expose cancel visibility rule"
)

assert.strictEqual(
  canReceivePurchase({ status: "submitted", qcStatus: "pending", remainingQuantity: 3 }),
  true,
  "pending inspection of a previous batch must not prevent another arrival"
)

assert.strictEqual(
  canQualityCheckPurchase({ status: "submitted", qcStatus: "pending" }),
  true,
  "pending quality check purchase should show quality check action"
)

assert.strictEqual(
  canReceivePurchase({ status: "submitted", qcStatus: "rejected" }),
  true,
  "rejected purchase should allow receive again"
)

assert.strictEqual(
  canReceivePurchase({ status: "submitted", qcStatus: "passed" }),
  true,
  "accepted but partially received purchase should allow next receive"
)

assert.strictEqual(
  canReceivePurchase({ status: "submitted", qcStatus: "passed", remainingQuantity: 0 }),
  false,
  "fully received purchase should not show receive action even if status is still submitted"
)

assert.strictEqual(
  canReceivePurchase({ status: "submitted", qcStatus: "passed", remainingQuantity: 3 }),
  true,
  "purchase with remaining quantity should show receive action after accepted quality check"
)

assert.strictEqual(
  canReceivePurchase({ status: "received", qcStatus: "passed" }),
  false,
  "received purchase should not show receive action"
)

assert.strictEqual(
  canCancelPurchase({ status: "submitted", qcStatus: "pending" }),
  false,
  "pending quality check purchase should not show cancel action"
)

assert.strictEqual(
  canCancelPurchase({ status: "submitted", receivedQuantity: 1 }),
  false,
  "purchase with received quantity should not show cancel action"
)

assert.strictEqual(
  canCancelPurchase({ status: "draft" }),
  true,
  "unreceived draft purchase should show cancel action"
)

assert.strictEqual(
  canCancelPurchase({ status: "submitted" }),
  true,
  "unreceived submitted purchase should show cancel action"
)

for (const row of [
  { status: "submitted", qcStatus: "pending", remainingQuantity: 0 },
  { status: "received", qcStatus: "pending", remainingQuantity: 3 },
  { status: "draft", qcStatus: "pending", remainingQuantity: 3 },
  { status: "cancelled", qcStatus: "pending", remainingQuantity: 3 }
]) assert.strictEqual(canReceivePurchase(row), false, "zero remaining or a non-submitted order must not receive")
console.log("purchase action rules regression passed")
