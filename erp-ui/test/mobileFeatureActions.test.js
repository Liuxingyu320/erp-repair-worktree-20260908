const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  getMobileFeatureActions,
  buildAllRemainingItems,
  getMobileActionTargetId,
  partitionMobileDetailActions
} = require("../src/views/mobile/feature/featureActions")

assert.deepStrictEqual(
  getMobileFeatureActions("sales", { _statusKey: "draft", _raw: { orderId: 10 } }).map(action => action.id),
  ["editSales", "submitSales", "cancelSales"],
  "draft mobile sales orders should allow editing before submit or cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("sales", { _statusKey: "submitted", _raw: { orderId: 11 } }).map(action => action.id),
  ["createDeliveryNotice", "cancelSales"],
  "submitted mobile sales orders should allow delivery notice generation and cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("purchase", { _statusKey: "draft", _raw: { orderId: 11 } }).map(action => action.id),
  ["editPurchase", "submitPurchase", "deletePurchaseDraft", "cancelPurchase"],
  "draft mobile purchase orders should allow editing, submitting, permanent deletion, or cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("purchase", { _statusKey: "submitted", _raw: { orderId: 12, qcStatus: "pending" } }).map(action => action.id),
  ["qualityCheckPurchase"],
  "purchase orders pending quality check should expose the line-level mobile QC action before receiving"
)

assert.deepStrictEqual(
  getMobileFeatureActions("purchase", { _statusKey: "submitted", _raw: { orderId: 13, qcStatus: "passed", remainingQuantity: 4 } }).map(action => action.id),
  ["receivePurchaseAll"],
  "purchase orders with accepted QC and remaining quantity should expose all-receive on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("salesReturn", { _statusKey: "draft", _raw: { returnId: 13 } }).map(action => action.id),
  ["editSalesReturn", "submitSalesReturn", "cancelSalesReturn"],
  "draft mobile sales returns should allow editing before submit or cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("salesReturn", { _statusKey: "submitted", _raw: { returnId: 14 } }).map(action => action.id),
  ["confirmSalesReturn", "cancelSalesReturn"],
  "submitted mobile return orders should allow confirmation and cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("purchaseReturn", { _statusKey: "draft", _raw: { returnId: 14 } }).map(action => action.id),
  ["editPurchaseReturn", "submitPurchaseReturn", "cancelPurchaseReturn"],
  "draft mobile purchase returns should allow editing before submit or cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("purchaseReturn", { _statusKey: "submitted", _raw: { returnId: 15 } }).map(action => action.id),
  ["confirmPurchaseReturn", "cancelPurchaseReturn"],
  "submitted mobile purchase return orders should allow confirmation and cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("outbound", { _statusKey: "pending", _raw: { noticeId: 16 } }).map(action => action.id),
  ["deliverDeliveryNoticeAll", "cancelDeliveryNotice"],
  "pending outbound notices should expose all-deliver and cancellation on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("outbound", { _statusKey: "submitted", _raw: { noticeId: 160 } }).map(action => action.id),
  [],
  "unsupported legacy notice statuses should not expose a delivery action that the backend rejects"
)

assert.deepStrictEqual(
  getMobileFeatureActions("stockCheck", { _statusKey: "draft", _raw: { checkId: 17 } }).map(action => action.id),
  ["saveStockCheckInput", "submitStockCheck", "deleteStockCheckDraft", "cancelStockCheck"],
  "draft stock checks should separate saving, submission, permanent deletion, and cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("stockCheck", { _statusKey: "returned", _raw: { checkId: 170 } }).map(action => action.id),
  ["saveStockCheckInput", "submitStockCheck", "cancelStockCheck"],
  "returned native-approval stock checks should remain editable and resubmittable"
)

assert.deepStrictEqual(
  getMobileFeatureActions("stockCheck", { _statusKey: "pending_approval", _raw: { checkId: 18 } }).map(action => action.id),
  ["approveStockCheck", "rejectStockCheck", "cancelStockCheck"],
  "pending stock checks should expose approval and cancellation actions"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", {
    _statusKey: "draft",
    _raw: { transferId: 17, transferType: "cross_store", fromDeptId: 88, toDeptId: 99 }
  }, { selectedDeptType: "STORE", selectedDeptId: 99 }).map(action => action.id),
  ["editTransfer", "submitTransferDraft", "deleteTransferDraft", "cancelTransfer"],
  "target-store cross-store drafts should expose edit, submit, permanent deletion, and cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", {
    _statusKey: "draft",
    _raw: { transferId: 170, transferType: "cross_store", fromDeptId: 88, toDeptId: 99 }
  }, { selectedDeptType: "STORE", selectedDeptId: 88 }).map(action => action.id),
  [],
  "source-store cross-store drafts should not expose edit, submit, deletion, or cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", {
    _statusKey: "draft",
    _raw: { transferId: 171, transferType: "cross_store", fromDeptId: 88, toDeptId: 99 }
  }, { selectedDeptType: "WAREHOUSE", selectedDeptId: 88 }).map(action => action.id),
  [],
  "warehouse processing context should not expose manual transfer draft actions"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", { _statusKey: "submitted", _raw: { transferId: 18 } }, { selectedDeptType: "WAREHOUSE" }).map(action => action.id),
  ["approveTransfer", "rejectTransfer"],
  "submitted transfers should expose warehouse approval and rejection on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transferApproval", { _statusKey: "submitted", _raw: { transferId: 180, taskId: 880 } }, { selectedDeptType: "STORE" }).map(action => action.id),
  ["approveTransfer", "rejectTransfer"],
  "mobile transfer approval center should expose approval actions for store-context candidate tasks"
)

{
  const approvalActions = getMobileFeatureActions(
    "transferApproval",
    { _statusKey: "submitted", _raw: { transferId: 180, taskId: 880 } },
    { selectedDeptType: "STORE" }
  )
  const approve = approvalActions.find(action => action.id === "approveTransfer")
  const reject = approvalActions.find(action => action.id === "rejectTransfer")
  assert.ok(approve && approve.acceptsComment === true && approve.requiresComment !== true,
    "mobile transfer approval comments must be optional when approving")
  assert.ok(reject && reject.requiresComment === true,
    "mobile transfer rejection must continue requiring a reason")
}

{
  const approvalGroups = partitionMobileDetailActions(
    getMobileFeatureActions("transferApproval", {
      _statusKey: "submitted",
      _raw: { transferId: 180, taskId: 880 }
    }, { selectedDeptType: "STORE" })
  )
  assert.deepStrictEqual(
    approvalGroups.primary.map(action => action.id),
    ["approveTransfer", "rejectTransfer"],
    "transfer approval and rejection should both stay visible in the fixed primary action area"
  )
  assert.deepStrictEqual(approvalGroups.secondary, [],
    "transfer approval should not demote either decision to the secondary action area")
}

{
  const draftGroups = partitionMobileDetailActions(
    getMobileFeatureActions("transfer", {
      _statusKey: "draft",
      _raw: { transferId: 17, transferType: "cross_store", fromDeptId: 88, toDeptId: 99 }
    }, { selectedDeptType: "STORE", selectedDeptId: 99 })
  )
  assert.deepStrictEqual(
    draftGroups.primary.map(action => action.id),
    ["editTransfer", "submitTransferDraft"],
    "draft edit and submit should be the two fixed primary actions"
  )
  assert.deepStrictEqual(
    draftGroups.secondary.map(action => action.id),
    ["deleteTransferDraft", "cancelTransfer"],
    "destructive draft actions should stay under other actions"
  )
}

assert.deepStrictEqual(
  partitionMobileDetailActions([
    { id: "one", surface: "primary" },
    { id: "two", surface: "primary" },
    { id: "three", surface: "primary" }
  ]),
  {
    primary: [{ id: "one", surface: "primary" }, { id: "two", surface: "primary" }],
    secondary: [{ id: "three", surface: "primary" }]
  },
  "the fixed mobile detail footer should never contain more than two actions"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", { _statusKey: "approved", _raw: { transferId: 19 } }, { selectedDeptType: "WAREHOUSE" }).map(action => action.id),
  ["deliverTransferAll"],
  "approved transfers should expose all-deliver for warehouse users on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", { _statusKey: "approved", _raw: { transferId: 190, fromDeptId: 88, toDeptId: 99 } }, { selectedDeptType: "WAREHOUSE", selectedDeptId: 99 }).map(action => action.id),
  [],
  "approved transfers should not expose deliver when the current warehouse is not the transfer source"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", { _statusKey: "approved", _raw: { transferId: 191, fromWarehouseId: 88, toWarehouseId: 99 } }, { selectedDeptType: "WAREHOUSE", selectedDeptId: 88 }).map(action => action.id),
  ["deliverTransferAll"],
  "approved transfers should expose deliver when the current warehouse is the transfer source warehouse"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", {
    _statusKey: "approved",
    _raw: {
      transferId: 192,
      transferType: "cross_store",
      sourceConfirmStatus: "PENDING",
      fromDeptId: 88,
      toDeptId: 99
    }
  }, { selectedDeptType: "STORE", selectedDeptId: 88 }).map(action => action.id),
  ["confirmTransferSource"],
  "an approved manual cross-store transfer should require confirmation by its source store"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", {
    _statusKey: "approved",
    _raw: {
      transferId: 193,
      transferType: "cross_store",
      sourceConfirmStatus: "PENDING",
      fromDeptId: 88,
      toDeptId: 99
    }
  }, { selectedDeptType: "STORE", selectedDeptId: 99 }).map(action => action.id),
  [],
  "the target store must not confirm or ship its own manual cross-store request"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", {
    _statusKey: "approved",
    _raw: {
      transferId: 194,
      transferType: "cross_store",
      sourceConfirmStatus: "PARTIAL",
      fromDeptId: 88,
      toDeptId: 99
    }
  }, { selectedDeptType: "STORE", selectedDeptId: 88 }).map(action => action.id),
  ["deliverTransferAll"],
  "after partial confirmation the source store should ship only the retained quantities"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", { _statusKey: "delivered", _raw: { transferId: 20 } }, { selectedDeptType: "STORE" }).map(action => action.id),
  ["receiveTransferShipment"],
  "delivered transfers should expose shipment receiving for store users on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", { _statusKey: "delivered", _raw: { transferId: 200, fromDeptId: 88, toDeptId: 99 } }, { selectedDeptType: "WAREHOUSE", selectedDeptId: 99 }).map(action => action.id),
  ["receiveTransferShipment"],
  "delivered transfers should expose receiving when the current warehouse is the transfer target"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", { _statusKey: "delivered", _raw: { transferId: 201, fromDeptId: 88, toDeptId: 99 } }, { selectedDeptType: "WAREHOUSE", selectedDeptId: 88 }).map(action => action.id),
  [],
  "delivered transfers should not expose receiving when the current warehouse is only the transfer source"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", {
    _statusKey: "discrepancy",
    _raw: { transferId: 202, fromDeptId: 88, toDeptId: 99 }
  }, { selectedDeptType: "STORE", selectedDeptId: 99 }).map(action => action.id),
  ["handleTransferDiscrepancy"],
  "the target organization must be able to process a transfer discrepancy on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transfer", {
    _statusKey: "discrepancy",
    _raw: { transferId: 203, fromDeptId: 88, toDeptId: 99 }
  }, { selectedDeptType: "STORE", selectedDeptId: 88 }).map(action => action.id),
  [],
  "the source organization must not process a discrepancy reserved for the receiving organization"
)

assert.deepStrictEqual(
  getMobileFeatureActions("replenishment", { _statusKey: "draft", _raw: { transferId: 21 } }, { selectedDeptType: "STORE" }).map(action => action.id),
  ["editReplenishment", "submitReplenishment", "deleteReplenishmentDraft"],
  "draft mobile replenishment requests should expose edit, submit, and delete actions for store users"
)

assert.deepStrictEqual(
  getMobileFeatureActions("replenishment", { _statusKey: "draft", _raw: { transferId: 22 } }, {}).map(action => action.id),
  ["editReplenishment", "submitReplenishment", "deleteReplenishmentDraft"],
  "draft mobile replenishment requests should still expose all draft actions when the page already restricts the route to stores but context type is temporarily missing"
)

assert.deepStrictEqual(
  getMobileFeatureActions("replenishment", {
    _statusKey: "submitted",
    _raw: { transferId: 220, approvalEngine: "LEGACY" }
  }, { selectedDeptType: "STORE" }).map(action => action.id),
  ["approveTransfer", "rejectTransfer"],
  "submitted replenishment requests should expose legacy approval decisions from their mobile detail sheet"
)

assert.deepStrictEqual(
  getMobileFeatureActions("replenishment", {
    _statusKey: "submitted",
    _raw: {
      transferId: 221,
      approvalEngine: "NATIVE",
      approvalTaskId: 8221,
      unifiedApprovalDetail: {
        instance: { status: "RUNNING" },
        tasks: [{ taskId: 8221, taskStatus: "PENDING" }]
      }
    }
  }, { selectedDeptType: "STORE" }).map(action => action.id),
  ["approveTransfer", "returnTransfer", "rejectTransfer"],
  "submitted replenishment requests should expose all supported native approval decisions for the current pending task"
)

assert.deepStrictEqual(
  getMobileFeatureActions("replenishment", { _statusKey: "delivered", _raw: { transferId: 23, fromDeptId: 8, toDeptId: 9 } }, { selectedDeptType: "STORE", selectedDeptId: 9 }).map(action => action.id),
  ["receiveTransferShipment"],
  "delivered mobile replenishment requests should expose receiving for the target store"
)

assert.deepStrictEqual(
  getMobileFeatureActions("replenishment", { _statusKey: "delivered", _raw: { transferId: 24, fromDeptId: 8, toDeptId: 9 } }, { selectedDeptType: "STORE", selectedDeptId: 8 }).map(action => action.id),
  [],
  "delivered mobile replenishment requests should not expose receiving outside the target store"
)

assert.deepStrictEqual(
  getMobileFeatureActions("monitorJob", { _statusKey: "0", _raw: { jobId: 22, jobGroup: "DEFAULT" } }).map(action => action.id),
  ["runMonitorJob", "pauseMonitorJob"],
  "enabled scheduler jobs should expose run-once and pause actions on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("monitorJob", { _statusKey: "1", _raw: { jobId: 23, jobGroup: "DEFAULT" } }).map(action => action.id),
  ["runMonitorJob", "resumeMonitorJob"],
  "paused scheduler jobs should expose run-once and resume actions on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("monitorOnline", { _raw: { tokenId: "TOKEN-24", userName: "admin" } }).map(action => action.id),
  ["forceLogoutOnline"],
  "online users should expose the desktop force-logout action on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("monitorOnline", { _raw: { tokenId: "TOKEN-SELF", userName: "admin", currentSession: true } }).map(action => action.id),
  [],
  "the current online session must not expose force logout on mobile"
)

assert.deepStrictEqual(
  getMobileFeatureActions("customer", { _statusKey: "0", _raw: { customerId: 31 } }).map(action => action.id),
  ["addCustomerServiceRecord", "editCustomer"],
  "active mobile service cards should expose recording and editing without legacy delete actions"
)

assert.deepStrictEqual(
  getMobileFeatureActions("supplier", { _statusKey: "0", _raw: { supplierId: 32 } }).map(action => action.id),
  [],
  "mobile suppliers should be read-only"
)

assert.deepStrictEqual(
  getMobileFeatureActions("product", { _statusKey: "0", _raw: { productId: 33 } }).map(action => action.id),
  [],
  "mobile products should be read-only"
)

assert.deepStrictEqual(
  getMobileFeatureActions("category", { _statusKey: "0", _raw: { categoryId: 34 } }).map(action => action.id),
  [],
  "mobile categories should not expose edit or delete actions"
)

assert.deepStrictEqual(
  getMobileFeatureActions("salaryScheme", { _statusKey: "0", _raw: { schemeId: 35 } }).map(action => action.id),
  [],
  "mobile salary schemes should not expose edit or delete actions"
)

assert.deepStrictEqual(
  getMobileFeatureActions("transferRules", { _statusKey: "0", _raw: { ruleId: 36 } }).map(action => action.id),
  [],
  "mobile transfer approval rules should not expose edit or delete actions"
)

assert.strictEqual(
  getMobileActionTargetId("purchaseReturn", { _raw: { returnId: 21 } }),
  21,
  "mobile action target id should use the module-specific backend id"
)

assert.strictEqual(
  getMobileActionTargetId("replenishment", { _raw: { transferId: 72 } }),
  72,
  "mobile replenishment detail actions should use transferId as their backend target id"
)

assert.strictEqual(
  getMobileActionTargetId("transferApproval", { _raw: { transferId: 73, taskId: 873 } }),
  73,
  "mobile transfer approval actions should approve the underlying transfer order"
)

assert.deepStrictEqual(
  buildAllRemainingItems([
    { detailId: 1, quantity: 5, deliveredQuantity: 2 },
    { detailId: 2, quantity: 3, deliveredQuantity: 3 },
    { detailId: 3, noticeQty: 4, deliveredQty: 1 }
  ], {
    totalKeys: ["quantity", "noticeQty"],
    doneKeys: ["deliveredQuantity", "deliveredQty"],
    quantityKey: "deliverQuantity"
  }),
  [
    { detailId: 1, deliverQuantity: 3 },
    { detailId: 3, deliverQuantity: 3 }
  ],
  "mobile all-remaining helper should create safe positive quantity payloads only"
)

const mapperSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureMapper.js"),
  "utf8"
)
const pageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const detailSheetSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileDetailSheet.vue"),
  "utf8"
)
const serviceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureActionService.js"),
  "utf8"
)
const runtimeSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureActionRuntime.js"),
  "utf8"
)
const {
  MOBILE_FORM_EDIT_PERMISSIONS
} = require("../src/views/mobile/feature/mobileFeaturePolicy")

assert.ok(
  mapperSource.includes("attachMobileActionMeta"),
  "mobile feature mapper should preserve backend row ids and statuses for detail actions"
)

assert.ok(
  detailSheetSource.includes("detail-primary-footer") &&
    detailSheetSource.includes("primaryActions.length") &&
    detailSheetSource.includes("secondaryActions.length") &&
    pageSource.includes(":primary-actions=\"detailPrimaryActions\"") &&
    pageSource.includes(":secondary-actions=\"detailSecondaryActions\"") &&
    pageSource.includes("detailActions") &&
    pageSource.includes("partitionMobileDetailActions") &&
    pageSource.includes("handleDetailAction") &&
    pageSource.includes("MOBILE_FORM_CONFIG") &&
    pageSource.includes("openMobileForm"),
  "mobile feature detail sheet should render executable business actions and lightweight business edit forms"
)

assert.ok(
  MOBILE_FORM_EDIT_PERMISSIONS.replenishment.includes("inv:transfer:add") &&
    MOBILE_FORM_EDIT_PERMISSIONS.replenishment.includes("inv:transfer:edit") &&
    MOBILE_FORM_EDIT_PERMISSIONS.transfer.includes("inv:transfer:add") &&
    MOBILE_FORM_EDIT_PERMISSIONS.transfer.includes("inv:transfer:edit"),
  "transfer drafts should remain editable for either backend save permission"
)

assert.ok(
  serviceSource.includes("createDeliveryNotice") &&
    serviceSource.includes("deleteDraftPurchase") &&
    serviceSource.includes("deleteStockCheck") &&
    serviceSource.includes("confirmSalesReturn") &&
    serviceSource.includes("confirmPurchaseReturn") &&
    serviceSource.includes("deliverDeliveryNotice") &&
    serviceSource.includes("submitStockCheck") &&
    serviceSource.includes("approveStockCheck") &&
    serviceSource.includes("rejectStockCheck") &&
    serviceSource.includes("approveTransfer") &&
    serviceSource.includes("receiveShipment") &&
    serviceSource.includes("resolveTransferDiscrepancy") &&
    serviceSource.includes("runJob") &&
    serviceSource.includes("changeJobStatus") &&
    serviceSource.includes("forceLogout"),
  "mobile action service should reuse existing desktop inventory and monitor APIs for enabled row actions"
)

;[
  "addCustomer",
  "updateCustomer",
  "delCustomer",
  "addSupplier",
  "updateSupplier",
  "delSupplier",
  "addProduct",
  "updateProduct",
  "delProduct",
  "addCategory",
  "updateCategory",
  "delCategory",
  "addSalaryScheme",
  "updateSalaryScheme",
  "delSalaryScheme",
  "addTransferApprovalRule",
  "updateTransferApprovalRule",
  "delTransferApprovalRule"
].forEach(apiName => {
  const disabledApi = new RegExp("\\b" + apiName + "\\b")
  assert.ok(
    !disabledApi.test(serviceSource) && !disabledApi.test(runtimeSource),
    `mobile action runtime should not retain disabled master/config write api ${apiName}`
  )
})
