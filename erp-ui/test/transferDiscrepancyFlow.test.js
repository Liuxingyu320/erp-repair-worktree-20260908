const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const page = readUi("src/views/inventory/transfer/index.vue")
const api = readUi("src/api/inventory/transfer.js")
const service = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java")
const receiptProcessor = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferReceiptProcessor.java")
const discrepancyProcessor = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferDiscrepancyProcessor.java")
const dispositionMapper = readRepo("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDiscrepancyDispositionMapper.xml")
const dispositionMigration = readRepo("sql/erp_inventory_transfer_discrepancy_disposition_20260805.sql")
const mobileDialog = readUi("src/views/mobile/feature/components/MobileActionDialog.vue")
const mobileRuntime = readUi("src/views/mobile/feature/featureActionRuntime.js")
const controller = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java")
const decisions = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferDiscrepancyDecisions.java")
const mapper = readRepo("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferDiscrepancyMapper.xml")
const todoMapper = readRepo("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTodoMapper.xml")
const migration = readRepo("sql/erp_inventory_transfer_discrepancy_20260713.sql")

assert.ok(
  page.includes("rejectedQuantity") && page.includes("damagedQuantity") &&
    page.includes("discrepancyNote") && page.includes("attachmentRefs") &&
    page.includes("调拨差异处理") && page.includes("discrepancyForm.items") &&
    page.includes("冻结成本") && page.includes("附件要求"),
  "receiving UI should classify rejection/damage and capture evidence before discrepancy handling"
)
;["RESHIP", "RETURN_SOURCE", "ACCEPT_ACTUAL", "PENDING_QC", "WRITE_OFF"].forEach(decision => {
  assert.ok(page.includes(decision), `desktop discrepancy dialog should expose ${decision}`)
  assert.ok(decisions.includes(`"${decision}"`), `backend decision policy should support ${decision}`)
})
assert.ok(
  api.includes("/discrepancies") && api.includes("/resolve"),
  "transfer API should expose scoped discrepancy reads and resolution"
)
assert.ok(
  controller.includes('inv:transfer:discrepancy:handle') &&
    controller.includes('PostMapping("/discrepancies/{discrepancyId}/resolve")'),
  "discrepancy resolution should have its own permission boundary"
)
assert.ok(
  service.includes("receiptProcessor().receiveTransferShipment") &&
    receiptProcessor.includes("shortageQty") && receiptProcessor.includes("rejectedQty") &&
    receiptProcessor.includes("damagedQty") && receiptProcessor.includes("insertDiscrepancy") &&
    receiptProcessor.includes("batchInsertDetails") &&
    receiptProcessor.includes("InvStatusConstants.DISCREPANCY"),
  "shortage, rejection, or damage should create a real discrepancy and stop normal completion"
)
assert.ok(
  service.includes("discrepancyProcessor().resolveTransferDiscrepancy") &&
    discrepancyProcessor.includes("selectByIdForUpdate") &&
    discrepancyProcessor.includes("request.getVersion()") &&
    discrepancyProcessor.includes("returnRejectedStockToSource") &&
    discrepancyProcessor.includes("releaseDiscrepancyQuantityForReship") &&
    !discrepancyProcessor.includes("syncOeDemandProgress"),
  "resolution should be locked/versioned, reconcile inventory, and stay independent of the retired OE replenishment chain"
)
assert.ok(
  discrepancyProcessor.includes("requestId") &&
    discrepancyProcessor.includes("selectByRequestId") &&
    discrepancyProcessor.includes("selectLatestByDiscrepancyId") &&
    discrepancyProcessor.includes("requireFrozenCost") &&
    discrepancyProcessor.includes("NO_STOCK_CHANGE") &&
    discrepancyProcessor.includes("PENDING_QC"),
  "resolution must be request-idempotent, cost-frozen, and explicit about stock/no-stock and QC states"
)
assert.ok(
  mobileDialog.includes("逐项差异处置") && mobileDialog.includes("discrepancyRows") &&
    mobileDialog.includes("discrepancyRequestId") && mobileRuntime.includes("requestId") &&
    mobileRuntime.includes("请逐项填写差异类别处置"),
  "mobile discrepancy handling should submit requestId and one decision per detail/category"
)
assert.ok(
  mapper.includes("where discrepancy_id = #{discrepancyId} and version = #{version}") &&
    todoMapper.includes("handle_discrepancy") &&
    todoMapper.includes("inv:transfer:discrepancy:handle"),
  "optimistic resolution and unified discrepancy todos should be persisted"
)
assert.ok(
  migration.includes("CREATE TABLE IF NOT EXISTS inv_transfer_discrepancy") &&
    migration.includes("CREATE TABLE IF NOT EXISTS inv_transfer_discrepancy_detail"),
  "migration should create discrepancy header and detail audit tables"
)
assert.ok(
  dispositionMapper.includes("insertDisposition") &&
    dispositionMapper.includes("selectByRequestId") &&
    dispositionMigration.includes("uk_inv_transfer_disposition_request_category") &&
    dispositionMigration.includes("cost_price") && dispositionMigration.includes("inventory_impact"),
  "a repeat-safe disposition mapper and frozen-cost ledger must back the resolution flow"
)

console.log("transferDiscrepancyFlow tests passed")
