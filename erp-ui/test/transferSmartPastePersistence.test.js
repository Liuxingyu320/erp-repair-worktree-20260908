const assert = require("assert")
const fs = require("fs")
const path = require("path")

const repoRoot = path.resolve(__dirname, "../..")
const read = relativePath => fs.readFileSync(path.join(repoRoot, relativePath), "utf8")

const transferPage = read("erp-ui/src/views/inventory/transfer/index.vue")
const smartPasteDialog = read("erp-ui/src/views/inventory/transfer/components/TransferSmartPasteDialog.vue")
const recordPage = read("erp-ui/src/views/inventory/transfer/records.vue")
const domain = read("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java")
const mapper = read("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferOrderMapper.xml")
const service = read("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java")
const migrationName = "erp_inventory_transfer_delivery_contact_20260805.sql"
const sourceMigration = read(`sql/${migrationName}`)
const dockerMigration = read(`docker/mysql/db/${migrationName}`)
const bootstrap = read("docker/mysql/bootstrap-files.list")

assert.ok(
  transferPage.includes("智能粘贴清单") &&
    transferPage.includes("TransferSmartPasteDialog: () => import") &&
    transferPage.includes('v-if="smartPasteOpen"') &&
    smartPasteDialog.includes("parseAndMatch") &&
    smartPasteDialog.includes("matchTransferPasteItems") &&
    smartPasteDialog.includes("请先判断所有同名或相似物料"),
  "transfer request should expose the guarded smart-paste matching flow"
)

assert.ok(
  smartPasteDialog.includes("收件人：示例收件人 电话：138****0000") &&
    smartPasteDialog.includes("收货地址：示例省示例市示例区示例路 1 号"),
  "desktop smart-paste placeholder should use clearly fictional recipient details"
)

for (const field of ["recipientName", "recipientPhone", "shippingAddress"]) {
  assert.ok(transferPage.includes(`form.${field}`), `request form should bind ${field}`)
  assert.ok(domain.includes(`String ${field}`), `transfer domain should persist ${field}`)
  assert.ok(mapper.includes(`property="${field}"`), `transfer mapper should map ${field}`)
}

assert.ok(
  transferPage.includes("detail.recipientName") &&
    transferPage.includes("detail.recipientPhone") &&
    transferPage.includes("detail.shippingAddress") &&
    recordPage.includes("detail.shippingAddress"),
  "processing and record details should display delivery contact information"
)

assert.ok(
  service.includes("draft.setRecipientName(parent.getRecipientName())") &&
    service.includes("draft.setRecipientPhone(parent.getRecipientPhone())") &&
    service.includes("draft.setShippingAddress(parent.getShippingAddress())"),
  "cross-store remainder drafts should preserve delivery contact information"
)

assert.strictEqual(sourceMigration, dockerMigration, "source and Docker migration copies must be byte-identical")
for (const column of ["recipient_name", "recipient_phone", "shipping_address"]) {
  assert.ok(sourceMigration.includes(`column_name = '${column}'`), `migration should guard ${column}`)
  assert.ok(mapper.includes(column), `mapper should persist ${column}`)
}
assert.strictEqual(
  bootstrap.split(/\r?\n/).filter(line => line.trim() === migrationName).length,
  1,
  "delivery contact migration should appear exactly once in MySQL bootstrap"
)

console.log("transfer smart paste persistence tests passed")
