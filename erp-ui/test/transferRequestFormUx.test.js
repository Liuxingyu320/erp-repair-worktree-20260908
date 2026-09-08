const assert = require("assert")
const fs = require("fs")
const path = require("path")

const transferSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/index.vue"),
  "utf8"
)

const formMatch = transferSource.match(/<el-dialog :title="formDialogTitle"[\s\S]*?<el-dialog :title="isReturnForm \? '选择返仓库存' : \(isCrossStoreForm \? '选择调出店库存' : '选择要货库存'\)"/)
assert.ok(formMatch, "transfer page should keep a dedicated create-request dialog before the stock picker")
const formSource = formMatch[0]

assert.ok(
  formSource.includes('label="参考成本价"') &&
    formSource.includes("formatMoney(scope.row.costPrice)"),
  "selected request details should keep the reference cost visible after stock rows are added"
)

assert.ok(
  formSource.includes('label="编码" width="160"') &&
    formSource.includes('class-name="transfer-code-column"') &&
    formSource.includes(':title="displayValue(scope.row.itemCode || scope.row.productCode)"'),
  "request detail item codes should have enough width and expose the full code on hover"
)

assert.ok(
  formSource.includes('class="transfer-readonly-input"') &&
    transferSource.includes("::v-deep .transfer-readonly-input .el-input__inner"),
  "readonly transfer fields should use a readable disabled-input treatment"
)

assert.ok(
  transferSource.includes("costPrice: undefined") &&
    transferSource.includes("costPrice: row.costPrice") &&
    transferSource.includes("costPrice: product.costPrice"),
  "transfer details should preserve reference cost from stock and catalog selections"
)

assert.ok(
  transferSource.includes("showSubmitResult(response)") &&
    transferSource.includes("approvalWarnings") &&
    transferSource.includes("this.$modal.msgWarning") &&
    transferSource.includes("提交成功；"),
  "successful transfer submission should show non-blocking hierarchy approval warnings"
)

assert.ok(
  (transferSource.match(/showSubmitResult\(response\)/g) || []).length >= 3,
  "both create-and-submit and draft-submit flows should use the shared submit result handler"
)

assert.ok(
  transferSource.includes("giftCategoryTree") &&
    transferSource.includes('itemType === "gift" ? giftCategoryTree() : categoryTree()'),
  "desktop replenishment should switch between product and gift category trees"
)

assert.ok(
  transferSource.includes(':disabled="stockCategoryLoading || !stockQuery.itemType"') &&
    !transferSource.includes("stockQuery.itemType !== 'product'") &&
    transferSource.includes("loadStockCategories(this.stockQuery.itemType)"),
  "desktop gift category picker should be enabled and reload for the selected material type"
)

assert.ok(
  transferSource.includes("openCatalogFromStockPicker") &&
    transferSource.includes("从礼盒档案选择") &&
    transferSource.includes("暂无可用礼盒库存"),
  "desktop empty gift stock should link to the gift catalog and explain the prerequisite"
)

assert.ok(
  transferSource.includes("异店调货由当前调入店发起") &&
    transferSource.includes("authorizedSourceStores") &&
    transferSource.includes("listShopTree()") &&
    transferSource.includes("请选择有权限的调出门店"),
  "desktop manual cross-store creation should be target-store initiated and only offer authorized source stores"
)

assert.ok(
  transferSource.includes("调出店确认异店调货") &&
    transferSource.includes("confirmTransferSource") &&
    transferSource.includes("余量会自动生成草稿，退回调入店") &&
    transferSource.includes('sourceConfirmStatus || "").toUpperCase() === "RESELECT_REQUIRED"'),
  "desktop cross-store flow should require source confirmation and force returned quantities to choose another source"
)

console.log("transferRequestFormUx tests passed")
