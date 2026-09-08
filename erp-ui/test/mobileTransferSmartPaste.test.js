const assert = require("assert")
const fs = require("fs")
const path = require("path")

const { getMobileTransferFormConfig } = require("../src/views/mobile/feature/mobileFormConfigs")
const { buildMobileFormPayload } = require("../src/views/mobile/feature/mobileFormPayloads")
const {
  applyMobileTransferSmartPasteRecipient,
  mergeMobileTransferSmartPasteDetails,
  smartPasteCandidateToDetail
} = require("../src/views/mobile/feature/mobileTransferSmartPaste")
const { buildMobileDetailFields, buildMobileReviewFields } = require("../src/views/mobile/feature/mobileFeatureDetailFieldsPolicy")

const recipientKeys = ["recipientName", "recipientPhone", "shippingAddress"]

;["warehouse", "cross_store", "store_return"].forEach(transferType => {
  const config = getMobileTransferFormConfig(transferType)
  const fields = config.fields || []
  const detailsField = fields.find(field => field.key === "details")

  assert.deepStrictEqual(
    fields.filter(field => recipientKeys.includes(field.key)).map(field => field.key),
    recipientKeys,
    `${transferType} mobile transfer form should expose recipient, phone, and shipping address fields`
  )
  assert.ok(detailsField && detailsField.smartPaste === true,
    `${transferType} mobile transfer details should expose the smart-paste entry`)
})

const merged = mergeMobileTransferSmartPasteDetails([
  { itemType: "product", itemId: 10, itemName: "蜂蜜", quantity: 2, unit: "瓶" }
], [
  {
    candidate: {
      itemType: "product",
      itemId: 10,
      itemName: "蜂蜜",
      itemCode: "P010",
      unit: "瓶",
      availableQuantity: 100,
      source: "stock"
    },
    quantity: 20,
    requestedUnit: "瓶"
  },
  {
    candidate: {
      itemType: "gift",
      itemId: 30,
      itemName: "雪莲马蹄",
      itemCode: "G030",
      unit: "箱",
      costPrice: 88,
      availableQuantity: 8,
      source: "stock"
    },
    quantity: 2,
    requestedUnit: "箱"
  }
])

assert.strictEqual(merged.addedCount, 1)
assert.strictEqual(merged.mergedCount, 1)
assert.strictEqual(merged.details[0].quantity, 22, "smart paste should merge duplicate material quantities")
assert.deepStrictEqual(merged.details[1], {
  itemType: "gift",
  itemId: 30,
  itemName: "雪莲马蹄",
  itemCode: "G030",
  quantity: 2,
  availableQuantity: 8,
  availabilityStatus: "loaded",
  unit: "箱",
  spec: "",
  grade: "",
  remark: "",
  costPrice: 88
})

const archiveUnitDetail = smartPasteCandidateToDetail({
  itemType: "product",
  itemId: 20,
  itemName: "百香果",
  itemCode: "P020",
  unit: "3斤",
  spec: "70-80g/个"
}, 30, "瓶")
assert.strictEqual(archiveUnitDetail.unit, "3斤", "pasted units must never replace the product archive unit")

const correctedArchiveUnit = mergeMobileTransferSmartPasteDetails([
  { itemType: "product", itemId: 20, itemName: "百香果", quantity: 2, unit: "瓶" }
], [{
  candidate: { itemType: "product", itemId: 20, itemName: "百香果", unit: "3斤" },
  quantity: 1,
  requestedUnit: "瓶"
}])
assert.strictEqual(correctedArchiveUnit.details[0].unit, "3斤", "merging must restore the product archive unit")

const recipient = {
  recipientName: "示例收件人",
  recipientPhone: "13800000000",
  shippingAddress: "示例省示例市示例区示例路 1 号"
}
assert.deepStrictEqual(
  applyMobileTransferSmartPasteRecipient({ remark: "加急" }, recipient),
  Object.assign({ remark: "加急" }, recipient),
  "parsed recipient details should fill the mobile transfer form"
)

const payload = buildMobileFormPayload(getMobileTransferFormConfig("warehouse"), Object.assign({
  fromDeptId: 101,
  toDeptId: 202,
  transferType: "warehouse",
  details: merged.details
}, recipient), { submitAction: "save", selectedDeptId: 202, selectedDeptType: "STORE" })
recipientKeys.forEach(key => {
  assert.strictEqual(payload[key], recipient[key], `${key} should be included in the mobile transfer payload`)
})

const detailLabels = buildMobileDetailFields("transfer", Object.assign({
  transferType: "warehouse",
  details: merged.details
}, recipient), {}, {}).map(field => field.label)
recipientKeys.map(key => ({ recipientName: "收件人", recipientPhone: "联系电话", shippingAddress: "收货地址" })[key])
  .forEach(label => assert.ok(detailLabels.includes(label), `mobile transfer detail should show ${label}`))

const reviewLabels = buildMobileReviewFields("transfer", Object.assign({
  transferType: "warehouse",
  details: merged.details
}, recipient), {}).map(field => field.label)
assert.ok(reviewLabels.includes("收货地址"), "mobile transfer action review should show the shipping address")

const componentRoot = path.resolve(__dirname, "../src/views/mobile/feature/components")
const lineEditorSource = fs.readFileSync(path.join(componentRoot, "MobileLineItemsEditor.vue"), "utf8")
const formSheetSource = fs.readFileSync(path.join(componentRoot, "MobileFormSheet.vue"), "utf8")
const smartPasteSource = fs.readFileSync(path.join(componentRoot, "MobileTransferSmartPasteSheet.vue"), "utf8")
const desktopSmartPasteSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/components/TransferSmartPasteDialog.vue"),
  "utf8"
)
const desktopTransferSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/index.vue"),
  "utf8"
)

assert.ok(
  lineEditorSource.includes("粘贴清单智能匹配") &&
    lineEditorSource.includes("MobileTransferSmartPasteSheet") &&
    lineEditorSource.includes('@apply="applySmartPaste"'),
  "the actual mobile line-item editor should launch the dedicated smart-paste sheet"
)
assert.ok(
  formSheetSource.includes('@smart-paste-recipient="applySmartPasteRecipient"') &&
    formSheetSource.includes("applyMobileTransferSmartPasteRecipient"),
  "the actual mobile form sheet should receive parsed delivery contacts"
)
assert.ok(
  smartPasteSource.includes('@paste="handlePaste"') &&
    smartPasteSource.includes("所有物料（包括唯一同名）都必须由你手动选择") &&
    smartPasteSource.includes('aria-label="收货信息"'),
  "the dedicated mobile sheet should require manual selection for every candidate"
)
assert.ok(
  smartPasteSource.includes("收件人：示例收件人 电话：138****0000") &&
    smartPasteSource.includes("收货地址：示例省示例市示例区示例路 1 号"),
  "the mobile smart-paste placeholder should use clearly fictional recipient details"
)
assert.ok(
  smartPasteSource.includes('class="smart-paste-quantity-control"') &&
    smartPasteSource.includes('@click="adjustQuantity(row, -1)"') &&
    smartPasteSource.includes('@click="adjustQuantity(row, 1)"') &&
    smartPasteSource.includes("数量必须大于 0") &&
    smartPasteSource.includes("formatReferenceCostPrice(candidate.referenceCostPrice)") &&
    smartPasteSource.includes('class="smart-paste-reference-subtotal"') &&
    smartPasteSource.includes("referenceSubtotalText(row)"),
  "the mobile smart-paste sheet should support direct quantity editing and show a live reference subtotal"
)
assert.ok(
  smartPasteSource.includes("选择后仍可改选") &&
    smartPasteSource.includes('v-if="row.candidates.length"') &&
    !smartPasteSource.includes("smart-paste-locked-material") &&
    smartPasteSource.includes("<span>{{ displayUnit(row) }}</span>") &&
    smartPasteSource.includes("数量已按档案单位计算"),
  "the mobile smart-paste sheet should keep selection editable and display the archive unit"
)
assert.ok(
  desktopSmartPasteSource.includes("<el-input-number") &&
    desktopSmartPasteSource.includes("调整数量") &&
    desktopSmartPasteSource.includes("数量必须大于 0") &&
    desktopSmartPasteSource.includes("formatReferenceCostPrice(candidate.referenceCostPrice)") &&
    desktopSmartPasteSource.includes('class="smart-paste-reference-subtotal"') &&
    desktopSmartPasteSource.includes("referenceSubtotalText(scope.row)"),
  "the desktop smart-paste dialog should support direct quantity editing and show a live reference subtotal"
)
assert.ok(
  desktopSmartPasteSource.includes("所有物料（包括唯一同名）都必须由你手动选择") &&
    desktopSmartPasteSource.includes('v-if="scope.row.candidates.length"') &&
    !desktopSmartPasteSource.includes("smart-paste-locked-material") &&
    desktopSmartPasteSource.includes("displayUnit(scope.row)") &&
    desktopSmartPasteSource.includes("数量已按档案单位计算"),
  "the desktop smart-paste dialog should keep selection editable and display the archive unit"
)
assert.ok(
  desktopTransferSource.includes(':value="scope.row.unit"') &&
    desktopTransferSource.includes(':value="scope.row.spec"') &&
    !desktopTransferSource.includes('v-model="scope.row.unit"') &&
    !desktopTransferSource.includes('v-model="scope.row.spec"') &&
    !desktopTransferSource.includes("detail.unit = row.requestedUnit"),
  "desktop transfer details should keep archive unit and spec read-only"
)

;[
  ["mobile", smartPasteSource],
  ["desktop", desktopSmartPasteSource]
].forEach(([client, source]) => {
  assert.ok(
    source.includes('import { listStock } from "@/api/inventory/stock"') &&
      source.includes("transferSource: true") &&
      source.includes("仅匹配所选补货仓库可用库存") &&
      !source.includes('from "@/api/inventory/product"') &&
      !source.includes('from "@/api/inventory/gift"') &&
      !source.includes("catalogCandidate("),
    `${client} replenishment smart paste should build candidates only from source-scoped inventory`
  )
  assert.ok(
    source.includes("loadCandidateGroup") &&
      source.includes("sourceInventoryErrorMessage") &&
      source.includes("来源库存加载失败：") &&
      source.includes("所选来源组织没有可匹配的可用库存"),
    `${client} smart paste should distinguish source-load failures from a successful empty result`
  )
  assert.ok(
    source.includes("loadCandidatePool().then(pool =>") &&
      source.includes("this.loadWarnings = pool.warnings") &&
      !source.includes("this.loadWarnings.push("),
    `${client} smart paste should commit warnings only after the active request token is verified`
  )
})

console.log("mobileTransferSmartPaste tests passed")
