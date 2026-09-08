const assert = require("assert")

const {
  buildEditableQuantityItems,
  filterDeliveryNoticeRowsByWarehouse,
  buildTransferReceiptItems,
  buildTransferApprovalPayload,
  buildStockCheckInputPayload,
  resolveStockCheckInputQuantity,
  selectPendingShipment
} = require("../src/views/mobile/feature/mobileActionPayloads")

assert.deepStrictEqual(
  filterDeliveryNoticeRowsByWarehouse([
    { detailId: 1, warehouseId: 8 },
    { detailId: 2, warehouseId: 9 },
    { detailId: 3 }
  ], 8),
  [{ detailId: 1, warehouseId: 8 }],
  "outbound payloads should fail closed and retain only rows assigned to the selected warehouse"
)

assert.deepStrictEqual(
  filterDeliveryNoticeRowsByWarehouse([{ detailId: 1, warehouseId: 8 }], ""),
  [],
  "outbound payloads should not expose rows when the warehouse context is missing"
)

assert.deepStrictEqual(
  buildEditableQuantityItems([
    { detailId: 1, quantity: 5, receivedQuantity: 2 },
    { detailId: 2, quantity: 3, receivedQuantity: 3 }
  ], {
    quantityKey: "receiveQuantity",
    totalKeys: ["quantity"],
    doneKeys: ["receivedQuantity"],
    inputItems: [{ detailId: 1, quantity: 2 }]
  }),
  [{ detailId: 1, receiveQuantity: 2 }],
  "quantity action payloads should use explicit user-entered quantities when provided"
)

assert.deepStrictEqual(
  buildEditableQuantityItems([
    { detailId: 1, noticeQty: 5, deliveredQty: 2 },
    { detailId: 2, noticeQty: 3, deliveredQty: 0 }
  ], {
    quantityKey: "deliverQuantity",
    totalKeys: ["noticeQty"],
    doneKeys: ["deliveredQty"],
    allowAllRemaining: true
  }),
  [
    { detailId: 1, deliverQuantity: 3 },
    { detailId: 2, deliverQuantity: 3 }
  ],
  "quantity action payloads should default to safe remaining quantities only when the dialog explicitly authorizes all remaining rows"
)

assert.deepStrictEqual(
  buildEditableQuantityItems([
    { detailId: 1, noticeQty: 5, deliveredQty: 2 },
    { detailId: 2, noticeQty: 3, deliveredQty: 0 }
  ], {
    quantityKey: "deliverQuantity",
    totalKeys: ["noticeQty"],
    doneKeys: ["deliveredQty"]
  }),
  [],
  "quantity action payloads should not silently process all rows when the mobile dialog did not submit item rows"
)

assert.throws(
  () => buildEditableQuantityItems([
    { detailId: 1, quantity: 5, receivedQuantity: 2 }
  ], {
    quantityKey: "receiveQuantity",
    totalKeys: ["quantity"],
    doneKeys: ["receivedQuantity"],
    inputItems: [{ detailId: 1, quantity: 4 }]
  }),
  /不能大于剩余数量/,
  "quantity action payloads should reject quantities above the remaining amount"
)

assert.deepStrictEqual(
  buildTransferReceiptItems([
    { transferDetailId: 9, shippedQuantity: 10, receivedQuantity: 2 }
  ], [
    {
      detailId: 9,
      receiveQuantity: 5,
      rejectedQuantity: 1,
      damagedQuantity: 1,
      discrepancyNote: "短少1件，另有拒收和残损"
    }
  ]),
  [{
    detailId: 9,
    receiveQuantity: 5,
    rejectedQuantity: 1,
    damagedQuantity: 1,
    discrepancyNote: "短少1件，另有拒收和残损"
  }],
  "transfer receipt payloads should preserve accepted, rejected, damaged, and discrepancy evidence"
)

assert.throws(
  () => buildTransferReceiptItems([
    { transferDetailId: 9, shippedQuantity: 10, receivedQuantity: 2 }
  ], [
    { detailId: 9, receiveQuantity: 7 }
  ]),
  /请填写差异说明/,
  "a mobile transfer shortage must be explained before it reaches the backend"
)

assert.deepStrictEqual(
  buildTransferApprovalPayload(2001, { taskId: "TF-TASK-1", action: "reject", comment: "库存不足" }),
  {
    transferId: 2001,
    taskId: "TF-TASK-1",
    action: "reject",
    comment: "库存不足"
  },
  "transfer approval payload should support rejection and comments"
)

assert.deepStrictEqual(
  buildTransferApprovalPayload(2002, { taskId: "TF-TASK-2", action: "approve", comment: " " }),
  {
    transferId: 2002,
    taskId: "TF-TASK-2",
    action: "approve",
    comment: ""
  },
  "approving a transfer should allow an empty optional opinion"
)

const selectedShipment = selectPendingShipment({
  shipments: [
    { shipmentId: 1, status: "pending_receive" },
    { shipmentId: 2, status: "pending_receive" }
  ]
}, { shipmentId: 2 })

assert.strictEqual(
  selectedShipment.shipmentId,
  2,
  "transfer receiving should allow choosing a pending shipment instead of always using the first one"
)

assert.deepStrictEqual(
  buildStockCheckInputPayload([
    { detailId: 1, productId: 10, bookQuantity: 8 },
    { detailId: 2, productId: 11, bookQuantity: 6 }
  ], [
    { detailId: 1, actualQuantity: 7 },
    { detailId: 2, actualQuantity: 6 }
  ]),
  [
    { detailId: 1, actualQty: 7 },
    { detailId: 2, actualQty: 6 }
  ],
  "stock-check input payload should use the backend actualQty field"
)

assert.strictEqual(
  resolveStockCheckInputQuantity({ detailId: 1, bookQty: 8 }),
  "",
  "stock-check input must not copy the book quantity into an uncounted row"
)
assert.strictEqual(
  resolveStockCheckInputQuantity({ detailId: 1, bookQty: 8, actualQty: 0 }),
  0,
  "stock-check input must preserve an explicitly counted zero"
)
