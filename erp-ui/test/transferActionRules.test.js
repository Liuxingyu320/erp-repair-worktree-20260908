const assert = require("assert")
const {
  canCancelTransfer,
  canConfirmTransferSource,
  canEditTransfer,
  canHandleTransferDiscrepancy,
  canReceiveTransfer,
  canShipTransfer,
  canShipTransferForContext,
  hasApprovedDeliveryState,
  mergeTransferDetailForAction,
  resolveTransferMoreActions,
  resolveTransferPrimaryAction
} = require(
  "../src/views/inventory/transfer/transferActionRules"
)

assert.strictEqual(
  canShipTransfer({ status: "approved" }),
  false,
  "an approved document without an approval summary must be blocked"
)
assert.strictEqual(
  canShipTransfer({ status: "approved", approvalSummary: { state: "partial" } }),
  false,
  "an anomalous approval summary must be blocked"
)
assert.strictEqual(
  canShipTransfer({ status: "approved", approvalSummary: { state: "approved" } }),
  true,
  "a fully approved document may be shipped"
)
assert.strictEqual(
  canShipTransfer({ status: "partial_delivered", approvalSummary: { state: "auto_approved" } }),
  true,
  "an auto-approved document may continue a partial shipment"
)
assert.strictEqual(
  hasApprovedDeliveryState({ approvalSummary: { state: "in_progress" } }),
  false,
  "in-progress approval must never be treated as deliverable"
)
const listApprovalSummary = { state: "approved", summaryText: "审批已通过" }
assert.deepStrictEqual(
  mergeTransferDetailForAction(
    { transferId: 900, details: [] },
    { transferId: 900, approvalSummary: listApprovalSummary }
  ).approvalSummary,
  listApprovalSummary,
  "shipment detail should preserve the approved list summary when an older detail response omits it"
)
const detailApprovalSummary = { state: "auto_approved", summaryText: "自动通过" }
assert.deepStrictEqual(
  mergeTransferDetailForAction(
    { transferId: 900, approvalSummary: detailApprovalSummary },
    { transferId: 900, approvalSummary: listApprovalSummary }
  ).approvalSummary,
  detailApprovalSummary,
  "the canonical detail approval summary should take precedence when present"
)
assert.strictEqual(
  canShipTransfer({
    status: "approved",
    transferType: "cross_store",
    sourceConfirmStatus: "PENDING",
    approvalSummary: { state: "approved" }
  }),
  false,
  "manual cross-store transfers must not ship before the source store confirms"
)
assert.strictEqual(
  canShipTransfer({
    status: "approved",
    transferType: "cross_store",
    sourceConfirmStatus: "PARTIAL",
    approvalSummary: { state: "approved" }
  }),
  true,
  "the source store may ship the quantity retained by a partial confirmation"
)
assert.strictEqual(
  canShipTransfer({
    status: "approved",
    transferType: "cross_store",
    sourceBusinessType: "sales_delivery",
    approvalSummary: { state: "approved" }
  }),
  true,
  "automatic cross-store sales delivery must not be forced through the manual confirmation flow"
)

const sourceStoreContext = {
  currentDeptId: 10,
  storeContext: true,
  warehouseContext: false
}
const targetStoreContext = {
  currentDeptId: 20,
  storeContext: true,
  warehouseContext: false
}
const sourceWarehouseContext = {
  currentDeptId: 30,
  storeContext: false,
  warehouseContext: true
}
const targetWarehouseContext = {
  currentDeptId: 30,
  storeContext: false,
  warehouseContext: true
}

const crossStorePending = {
  status: "approved",
  transferType: "cross_store",
  fromDeptId: 10,
  toDeptId: 20,
  sourceConfirmStatus: "PENDING",
  approvalSummary: { state: "approved" }
}
assert.strictEqual(
  canConfirmTransferSource(crossStorePending, sourceStoreContext),
  true,
  "only the selected source store may confirm a manual cross-store transfer"
)
assert.strictEqual(
  canConfirmTransferSource(crossStorePending, targetStoreContext),
  false,
  "the requesting store must not confirm on behalf of the source store"
)
assert.strictEqual(
  canShipTransferForContext(crossStorePending, sourceStoreContext),
  false,
  "a manual cross-store transfer must remain blocked until source confirmation"
)

const crossStoreConfirmed = Object.assign({}, crossStorePending, {
  sourceConfirmStatus: "CONFIRMED"
})
assert.strictEqual(
  canShipTransferForContext(crossStoreConfirmed, sourceStoreContext),
  true,
  "the confirmed source store may ship a cross-store transfer"
)
assert.strictEqual(
  canShipTransferForContext(crossStoreConfirmed, targetStoreContext),
  false,
  "the target store must not ship a cross-store transfer"
)
assert.strictEqual(
  canShipTransferForContext(crossStoreConfirmed, sourceWarehouseContext),
  false,
  "a warehouse context must not impersonate the source store"
)

const storeReturn = {
  status: "approved",
  transferType: "store_return",
  fromDeptId: 10,
  toDeptId: 30,
  approvalSummary: { state: "approved" }
}
assert.strictEqual(
  canShipTransferForContext(storeReturn, sourceStoreContext),
  true,
  "the returning store may ship an approved store return"
)
assert.strictEqual(
  canReceiveTransfer(
    Object.assign({}, storeReturn, { status: "partial_delivered" }),
    targetWarehouseContext
  ),
  true,
  "the target warehouse may receive each delivered store-return batch"
)

const replenishment = {
  status: "approved",
  transferType: "warehouse",
  fromDeptId: 30,
  toDeptId: 20,
  approvalSummary: { state: "approved" }
}
assert.strictEqual(
  canShipTransferForContext(replenishment, sourceWarehouseContext),
  true,
  "the source warehouse may ship an approved replenishment"
)
assert.strictEqual(
  canReceiveTransfer(
    Object.assign({}, replenishment, { status: "partial_delivered" }),
    targetStoreContext
  ),
  true,
  "the target store may receive a partially delivered replenishment"
)

const crossStoreDraft = {
  status: "draft",
  transferType: "cross_store",
  fromDeptId: 10,
  toDeptId: 20
}
assert.strictEqual(
  canEditTransfer(crossStoreDraft, targetStoreContext),
  true,
  "the requesting store may edit its cross-store draft"
)
assert.strictEqual(
  canEditTransfer(crossStoreDraft, sourceStoreContext),
  false,
  "the selected source store must not edit the requesting store's draft"
)
assert.strictEqual(
  canCancelTransfer(crossStoreDraft, targetStoreContext),
  true,
  "the requesting store may cancel its draft"
)
assert.strictEqual(
  canEditTransfer(
    Object.assign({}, storeReturn, { status: "draft" }),
    sourceStoreContext
  ),
  true,
  "the returning store owns its return draft"
)

const discrepancy = {
  status: "discrepancy",
  transferType: "warehouse",
  fromDeptId: 30,
  toDeptId: 20
}
assert.strictEqual(
  canHandleTransferDiscrepancy(discrepancy, targetStoreContext),
  true,
  "the target organization may handle its receipt discrepancy"
)
assert.strictEqual(
  canHandleTransferDiscrepancy(discrepancy, sourceWarehouseContext),
  false,
  "the source organization must not handle the target's receipt discrepancy"
)

const submitted = {
  status: "submitted",
  transferType: "warehouse",
  fromDeptId: 30,
  toDeptId: 20
}
assert.deepStrictEqual(
  resolveTransferPrimaryAction(submitted, targetStoreContext),
  {
    key: "approve",
    label: "审批通过",
    permission: "inv:transfer:approve"
  },
  "legacy submitted transfers should keep approval as the primary action"
)
assert.deepStrictEqual(
  resolveTransferMoreActions(submitted, targetStoreContext).map(action => action.key),
  ["detail", "reject", "cancel"],
  "legacy approval should keep reject and owner cancellation outside the primary action"
)

const nativeWithoutTaskContext = Object.assign({}, targetStoreContext, {
  nativeApproval: true,
  unifiedTaskAvailable: false
})
assert.strictEqual(
  resolveTransferPrimaryAction(submitted, nativeWithoutTaskContext).key,
  "detail",
  "native approval without an authorized pending task must fail closed to detail"
)
assert.deepStrictEqual(
  resolveTransferMoreActions(submitted, nativeWithoutTaskContext),
  [],
  "native approval without an authorized task must expose no mutating fallback action"
)

const nativeTaskContext = Object.assign({}, targetStoreContext, {
  nativeApproval: true,
  unifiedTaskAvailable: true,
  withdrawAllowed: true
})
assert.strictEqual(
  resolveTransferPrimaryAction(submitted, nativeTaskContext).key,
  "approve",
  "an authorized native task may expose approval"
)
assert.deepStrictEqual(
  resolveTransferMoreActions(submitted, nativeTaskContext),
  [
    { key: "detail", label: "详情", permission: "inv:transfer:query", danger: false },
    { key: "return", label: "退回修改", permission: "inv:transfer:approve", danger: false },
    { key: "reject", label: "审批拒绝", permission: "inv:transfer:approve", danger: true },
    { key: "withdraw", label: "撤回审批", permission: "inv:transfer:submit", danger: true }
  ],
  "native approval should distinguish return, reject, and applicant withdrawal without exposing cancellation"
)

console.log("transferActionRules tests passed")
