const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const view = fs.readFileSync(path.resolve(uiRoot, "src/views/inventory/purchase/index.vue"), "utf8")
const api = fs.readFileSync(path.resolve(uiRoot, "src/api/inventory/purchase.js"), "utf8")
const mobileDialog = fs.readFileSync(path.resolve(uiRoot, "src/views/mobile/feature/components/MobileActionDialog.vue"), "utf8")
const mobileRuntime = fs.readFileSync(path.resolve(uiRoot, "src/views/mobile/feature/featureActionRuntime.js"), "utf8")
const migration = fs.readFileSync(path.resolve(repoRoot, "sql/erp_inventory_receipt_quality_20260713.sql"), "utf8")

assert.ok(
  view.includes("供应商批次") && view.includes("送货单号") && view.includes("到货时间"),
  "receipt dialog should capture the supplier batch, delivery note and arrival time"
)
assert.ok(
  view.includes("合格+拒收+让步数量必须等于本次检验数量") &&
    view.includes("有拒收或让步数量，请填写原因") &&
    view.includes("attachmentUrls"),
  "line QC should enforce quantity conservation, reasons and evidence"
)
assert.ok(
  view.includes("qcLegacyMode") && view.includes("历史待检数据"),
  "legacy pending receipts should retain an explicit whole-order fallback"
)
assert.ok(
  api.includes("/receipt-batches/pending") && api.includes("/qc/batch/"),
  "purchase API should expose pending receipt batches and batch QC"
)
assert.ok(
  mobileDialog.includes('qcResult: ""') &&
    !mobileDialog.includes('qcResult: "passed"') &&
    mobileDialog.includes("qualityRows") &&
    mobileDialog.includes("合格+拒收+让步必须等于本次检验") &&
    mobileRuntime.includes("qualityCheckPurchaseBatch") &&
    !mobileRuntime.includes('actionPayload.qcResult || "passed"'),
  "mobile QC should require line details and must never silently default to passed"
)
assert.ok(
  migration.includes("CREATE TABLE IF NOT EXISTS inv_receipt_batch") &&
    migration.includes("CREATE TABLE IF NOT EXISTS inv_quality_inspection") &&
    migration.includes("quality_inspection_id"),
  "migration should persist receipt and immutable inspection facts"
)

console.log("warehouseReceiptQualityUx tests passed")
