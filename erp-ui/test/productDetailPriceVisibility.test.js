const assert = require("assert")
const fs = require("fs")
const path = require("path")

const source = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/product/index.vue"),
  "utf8"
)

const tableMatch = source.match(/<el-table ref="productTable"[\s\S]*?<\/el-table>/)
assert.ok(tableMatch, "product page should render a product table")

const tableSource = tableMatch[0]

assert.ok(
  !tableSource.includes('label="售价250g"'),
  "product table should not display the 250g sale price column"
)

assert.ok(
  !tableSource.includes('label="售价500g"'),
  "product table should not display the 500g sale price column"
)

assert.ok(
  tableSource.includes("@click=\"openDetail(scope.row)\""),
  "product table should provide a detail action for each row"
)

assert.ok(
  source.includes("readonly-form") && source.includes("formatMoney(form.salePrice250g)") && source.includes("displaySalePrice500g()"),
  "product detail mode should render 250g and 500g sale prices"
)

assert.ok(
  source.includes("displaySalePrice500g") && source.includes("form.salesPrice"),
  "product detail mode should fall back to salesPrice for older 500g sale price data"
)

assert.ok(
  source.includes("canViewCostFields()") &&
    source.includes('this.$auth.hasPermi("inv:cost:view")'),
  "product page should keep inv:cost:view as the purchase-price permission gate"
)

assert.ok(
  /<el-table-column v-if="canViewCostFields" label="采购价"/.test(tableSource) &&
    /<el-table-column label="成本价" prop="costPrice"/.test(tableSource),
  "product table should always render cost price while keeping purchase price permission-gated"
)

assert.ok(
  /v-if="canViewCostFields"[\s\S]*采购参考价/.test(source) &&
    /<div class="detail-item">\s*<span class="detail-label">参考成本价<\/span>/.test(source),
  "product detail drawer should always show reference cost while keeping purchase reference price permission-gated"
)

assert.ok(
  source.includes("stripHiddenCostFields(payload)") &&
    source.includes("delete payload.purchasePrice") &&
    !source.includes("delete payload.costPrice"),
  "product save payload should keep visible cost price while stripping hidden purchase price"
)

assert.ok(
  source.includes("confirmExportAction") &&
    source.includes("sensitiveFields: this.canViewCostFields ? ['采购参考价'] : []"),
  "product export should use the shared confirmation and only treat purchase price as permission-sensitive"
)
