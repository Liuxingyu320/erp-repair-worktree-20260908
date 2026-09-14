const assert = require("assert")
const fs = require("fs")
const path = require("path")
const {
  shouldClearEntitySelection
} = require("../src/views/mobile/feature/mobileEntitySelection")

const serviceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/mobileEntityService.js"),
  "utf8"
)

;[
  "listProduct",
  "listOe",
  "listGift",
  "listCustomer",
  "listSupplier",
  "getSupplierProducts",
  "categoryTree",
  "shopTree",
  "listDept",
  "listWarehouseDept",
  "listStock",
  "listSales",
  "listPurchase"
].forEach(apiName => {
  assert.ok(serviceSource.includes(apiName), `mobile entity service should reuse ${apiName}`)
})

assert.ok(
  serviceSource.includes("giftCategoryTree") &&
    serviceSource.includes("fetchItemCategoryOptions") &&
    serviceSource.includes('itemType === "gift"'),
  "mobile material category options should use the gift category tree after gift is selected"
)

assert.ok(
  serviceSource.includes("STORE") &&
    serviceSource.includes("WAREHOUSE") &&
    serviceSource.includes("fetchMobileEntityOptions") &&
    serviceSource.includes("fetchAuthorizedDeptOptions") &&
    serviceSource.includes("supplierProducts") &&
    serviceSource.includes("mapProductOption") &&
    serviceSource.includes("mapDeptOption"),
  "mobile entity service should expose product/customer/supplier/category/dept/store/warehouse options"
)

assert.ok(
  serviceSource.includes('entity === "oe"') &&
    serviceSource.includes('entity === "gift"') &&
    serviceSource.includes("mapOeOption") &&
    serviceSource.includes("mapGiftOption") &&
    serviceSource.includes("itemType") &&
    serviceSource.includes("itemId") &&
    serviceSource.includes("itemCode") &&
    serviceSource.includes("itemName"),
  "mobile material pickers should reuse product/OE/gift APIs and normalize every option to generic item fields"
)

assert.ok(
  serviceSource.includes("listOe(createQuery(keyword, options)") &&
    serviceSource.includes("listGift(createQuery(keyword, options)"),
  "mobile OE and gift searches should use the backend keyword field so both names and codes match"
)

assert.ok(
  serviceSource.includes("scopeDeptId") &&
    serviceSource.includes("fetchPurposeWarehouseOptions") &&
    serviceSource.includes("options.field.purpose") &&
    serviceSource.includes("replenishmentStock") &&
    serviceSource.includes("transferSource") &&
    serviceSource.includes("itemType: options.itemType") &&
    serviceSource.includes("availableQuantity") &&
    serviceSource.includes("categoryId: options.categoryId"),
  "mobile sales/replenishment warehouse options should use purpose-scoped desktop warehouse contracts, including source-stock filters"
)

assert.ok(
  serviceSource.includes('entity === "transferSourceStock"') &&
    serviceSource.includes("filterDeptOptions") &&
    serviceSource.includes("excludeContextDept"),
  "typed cross-store forms should query source-store inventory and remove the current target store from source options"
)

const replenishmentStockLoader = serviceSource.match(
  /function fetchReplenishmentStockOptions\(keyword, options\) \{[\s\S]*?(?=\nfunction fetchStockOptions)/
)
assert.ok(
  replenishmentStockLoader &&
    replenishmentStockLoader[0].includes("fetchReplenishmentStockRows(query, keyword)") &&
    replenishmentStockLoader[0].includes("mapReplenishmentStockOption") &&
    !replenishmentStockLoader[0].includes("fetchReplenishmentStockRowsByProductIds") &&
    !replenishmentStockLoader[0].includes("listProduct"),
  "mobile replenishment and transfer-source stock searches should use source listStock results without a target-store product-list fallback"
)

assert.ok(
  serviceSource.includes("MAX_MOBILE_OPTION_KEYWORD_LENGTH") &&
    serviceSource.includes("normalizeMobileOptionKeyword") &&
    serviceSource.includes("slice(0, MAX_MOBILE_OPTION_KEYWORD_LENGTH)") &&
    serviceSource.includes("createCompactQuery(normalizedKeyword, options)"),
  "mobile compact option searches should trim and cap keywords before building GET query strings"
)

const stockOptionMapper = serviceSource.match(/function mapReplenishmentStockOption\(row\) \{[\s\S]*?\n\}/)
assert.ok(stockOptionMapper, "mobile replenishment stock options should have a dedicated mapper")

assert.ok(
  !/meta:\s*\[[\s\S]*hasValue\(availableQuantity\)[\s\S]*Number\.isFinite\(costPrice\)/.test(stockOptionMapper[0]),
  "mobile replenishment stock option meta should not duplicate available quantity and reference cost already shown in the card"
)

const pickerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileEntityPicker.vue"),
  "utf8"
)

const lineItemsEditorSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileLineItemsEditor.vue"),
  "utf8"
)

assert.ok(
  pickerSource.includes("fetchMobileEntityOptions") &&
    pickerSource.includes("$emit(\"input\"") &&
    pickerSource.includes("$emit(\"select\"") &&
    pickerSource.includes("type=\"search\""),
  "mobile entity picker should search options and emit selected ids"
)

assert.ok(
  pickerSource.includes("field") &&
    pickerSource.includes("formData") &&
    pickerSource.includes("emptyText"),
  "mobile entity picker should receive field/form context"
)

assert.ok(
  pickerSource.includes("hydrateKeywordFromOptions") &&
    pickerSource.includes("String(option.value) === String(this.value)") &&
    pickerSource.includes("fallbackLabel") &&
    pickerSource.includes("fallbackLabelKey") &&
    pickerSource.includes("font-size: 16px"),
  "mobile entity picker should hydrate existing ids or persisted fallback labels and keep search inputs at 16px to avoid iOS edit-form zoom"
)

assert.strictEqual(
  shouldClearEntitySelection(8, "仓库B", "仓库A"),
  true,
  "typing a different label after selecting an entity must invalidate the old id"
)
assert.strictEqual(
  shouldClearEntitySelection(8, "仓库A", "仓库A"),
  false,
  "an unchanged selected label must preserve the selected id"
)
assert.strictEqual(
  shouldClearEntitySelection("", "仓库B", ""),
  false,
  "searching before any selection must not emit a redundant clear"
)
assert.ok(
  pickerSource.includes("shouldClearEntitySelection") &&
    pickerSource.includes('this.$emit("input", "")') &&
    pickerSource.includes('this.$emit("selection-cleared")'),
  "mobile entity picker must clear the selected id as soon as the visible keyword no longer matches it"
)

assert.ok(
  pickerSource.includes("quickCreate") &&
    pickerSource.includes("MobileQuickCustomerForm") &&
    pickerSource.match(/<mobile-quick-customer-form[\s\S]*?\/>/)[0].includes("@select") === false,
  "mobile customer picker should own quick creation and continue emitting the existing input/select contract"
)

assert.ok(
  pickerSource.includes(":maxlength=\"maxKeywordLength\"") &&
    lineItemsEditorSource.includes(":maxlength=\"maxKeywordLength\""),
  "mobile entity search inputs should cap pasted keywords before search requests are built"
)

assert.ok(
  lineItemsEditorSource.includes("pickerCategoryId") &&
    lineItemsEditorSource.includes("pickerItemType") &&
    lineItemsEditorSource.includes("handlePickerItemTypeChange") &&
    lineItemsEditorSource.includes("请先选择物料类型") &&
    lineItemsEditorSource.includes("itemType: this.pickerItemType") &&
    lineItemsEditorSource.includes("handlePickerCategoryChange") &&
    lineItemsEditorSource.includes(">分类<") &&
    lineItemsEditorSource.includes('当前仓库暂无可用" + this.pickerItemTypeLabel + "库存'),
  "mobile replenishment stock picker should require an item type before category filtering, search, and stock selection"
)

assert.ok(
  !pickerSource.includes("handleScanClick") &&
    !pickerSource.includes("扫码") &&
    !lineItemsEditorSource.includes("handleScanClick") &&
    !lineItemsEditorSource.includes("扫码"),
  "mobile pickers should use text search only and expose no fake scan control"
)

const categoryLoader = lineItemsEditorSource.match(/loadPickerCategories\(\) \{[\s\S]*?\n    \},\n    handlePickerCategoryChange/)
assert.ok(
  categoryLoader && categoryLoader[0].includes("itemType: this.pickerItemType"),
  "mobile replenishment should load categories for the selected material type"
)

assert.ok(
  lineItemsEditorSource.includes("pickerCategoryEmptyText") &&
    lineItemsEditorSource.includes('暂无" + this.pickerItemTypeLabel + "分类'),
  "mobile replenishment should explain when the selected material type has no categories"
)

assert.ok(
  lineItemsEditorSource.includes("pickerError") &&
    lineItemsEditorSource.includes('role="alert"') &&
    lineItemsEditorSource.includes("mobileErrorMessage(error, \"来源库存加载失败，请检查网络后重试\")") &&
    lineItemsEditorSource.includes("!pickerError && pickerOptions.length === 0") &&
    lineItemsEditorSource.includes("重新加载来源库存"),
  "mobile source-stock loading errors should remain visible and distinct from a successful empty inventory result"
)

assert.ok(
  lineItemsEditorSource.includes("handleItemTypeChange") &&
    lineItemsEditorSource.includes("resolveItemPickerEntity") &&
    lineItemsEditorSource.includes("canUseEntityPicker") &&
    lineItemsEditorSource.includes("请先选择物料类型") &&
    lineItemsEditorSource.includes(":key=\"itemField.key + ':' + resolveItemPickerEntity(row, itemField)\"") &&
    lineItemsEditorSource.includes(":key=\"stockOptionKey(option)\"") &&
    lineItemsEditorSource.includes("stockOptionKey") &&
    lineItemsEditorSource.includes("isAllowedItemType") &&
    lineItemsEditorSource.includes("itemType") &&
    lineItemsEditorSource.includes("itemId") &&
    lineItemsEditorSource.includes("itemCode") &&
    lineItemsEditorSource.includes("itemName"),
  "mobile line-item editing should switch catalogs by item type and key stock selections by type plus id"
)
