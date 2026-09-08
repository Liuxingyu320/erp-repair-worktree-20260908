const assert = require("assert")
const fs = require("fs")
const path = require("path")

const transferPage = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/index.vue"),
  "utf8"
)

assert.ok(
  transferPage.includes("从仓库库存选择"),
  "transfer request form should keep the warehouse-stock picker entry"
)

assert.ok(
  transferPage.includes("从物料档案选择"),
  "transfer request form should add an item-catalog picker entry for shortage demand"
)

assert.ok(
  transferPage.includes('<el-dialog title="选择要货物料"'),
  "transfer request form should provide an item catalog dialog"
)

assert.ok(
  transferPage.includes("<inventory-item-select") &&
    transferPage.includes('["product", "gift"]'),
  "item catalog picker should support products and gifts without exposing OE"
)

assert.ok(
  transferPage.includes("productPickerOpen") &&
    transferPage.includes("openProductPicker") &&
    transferPage.includes("confirmProductSelection") &&
    transferPage.includes("buildDetailFromProduct"),
  "transfer page should have dedicated product-catalog picker state and handlers"
)

assert.ok(
  transferPage.includes("仓库缺货") &&
    transferPage.includes("isDetailShortage") &&
    transferPage.includes("refreshDetailAvailability"),
  "transfer detail rows should display derived shortage status from warehouse availability"
)

assert.ok(
  transferPage.includes("refreshAllDetailAvailability") &&
    transferPage.includes("onWarehouseSelected"),
  "changing the source warehouse should refresh detail availability without dropping demand"
)

assert.ok(
  transferPage.includes("refreshAllDetailAvailability({ failOnError: !sourceReselectionRequired })") &&
    transferPage.includes("来源库存复查失败，无法安全编辑草稿") &&
    transferPage.includes("listStock(query, { silentError: true })") &&
    transferPage.includes("if (options.failOnError) throw error") &&
    transferPage.includes('this.$set(detail, "availabilityStatus", "unknown")'),
  "desktop draft editing should open only after a successful source-availability recheck and preserve failed checks as errors"
)

assert.ok(
  transferPage.includes("detailAvailabilityRequestTokens: new WeakMap()") &&
    transferPage.includes("this.detailAvailabilityRequestTokens.set(detail, requestId)") &&
    transferPage.includes("this.detailAvailabilityRequestTokens.get(detail) === requestId") &&
    transferPage.includes("invalidateDetailAvailabilityRequests"),
  "desktop availability checks should reject stale A-to-B-to-A responses with a per-detail request token"
)

assert.ok(
  !transferPage.includes('stockStatus: "available",\n        transferSource: true\n      }\n      listStock'),
  "single-product availability lookup should not force stockStatus=available because zero-stock records must be detectable"
)

console.log("transferShortageRequestUx tests passed")
