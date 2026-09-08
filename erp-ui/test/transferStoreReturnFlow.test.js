const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const page = readUi("src/views/inventory/transfer/index.vue")
const mobileConfig = readUi("src/views/mobile/feature/mobileFormConfigs.js")
const mobilePayloads = readUi("src/views/mobile/feature/mobileFormPayloads.js")
const types = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/constant/InvTransferTypes.java")
const service = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferServiceImpl.java")
const directionPolicy = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferDirectionPolicy.java")
const receiptProcessor = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferReceiptProcessor.java")
const migration = readRepo("sql/erp_inventory_store_return_20260713.sql")

assert.ok(
  page.includes("openForm(null, 'store_return')") && page.includes("门店返仓") &&
    page.includes("returnReasonCode") && page.includes("returnReasonText"),
  "desktop transfer entry should create a store-return order with structured reasons"
)
assert.ok(
  page.includes('value="NORMAL"') && page.includes('value="DAMAGED"') &&
    page.includes('value="PENDING_QC"') && page.includes('allowedItemTypes: ["product", "gift"]'),
  "return rows should capture goods condition and exclude OE materials"
)
assert.ok(
  mobileConfig.includes('{ label: "门店返仓", value: "store_return" }') &&
    mobileConfig.includes('const TRANSFER_ITEM_TYPES = ["product", "gift"]') &&
    mobileConfig.includes('fixedTransferType: "store_return"') &&
    mobileConfig.includes('pickerEntity: "transferSourceStock"') &&
    mobilePayloads.includes("config.fixedTransferType"),
  "mobile transfer form should use a fixed store-return direction, source stock, and exclude OE"
)
assert.ok(
  types.includes('STORE_RETURN = "store_return"') &&
    !types.includes("InvItemTypes.OE") &&
    types.includes('case STORE_RETURN -> "store_return"'),
  "central transfer strategy should exclude retired OE replenishment items and define store-return stock logs"
)
assert.ok(
  service.includes("directionPolicy().validateCreation(order, selectedDeptId,") &&
    service.includes("directionPolicy().validateDelivery(order, selectedShopDeptId)") &&
    service.includes("receiptProcessor().receiveTransferShipment") &&
    receiptProcessor.includes("directionPolicy.validateReceipt(locked, selectedShopDeptId)") &&
    directionPolicy.includes("门店才能发起返仓") && directionPolicy.includes("返仓来源必须为门店") &&
    directionPolicy.includes("返仓目标必须为仓库") && directionPolicy.includes("只能由来源门店发货") &&
    directionPolicy.includes("只能由目标仓库收货"),
  "backend should make store-to-warehouse direction impossible to bypass"
)
assert.ok(
  migration.includes("transfer_type IN ('all', 'store_return')") &&
    migration.includes("return_reason_code") && migration.includes("goods_condition"),
  "store-return migration should reuse all-type approval rules and add reason/condition snapshots"
)

console.log("transferStoreReturnFlow tests passed")
