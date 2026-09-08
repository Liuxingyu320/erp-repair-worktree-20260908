const assert = require("assert")
const fs = require("fs")
const path = require("path")

const source = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/supplier/index.vue"),
  "utf8"
)

assert.ok(
  source.includes("getSupplierProducts"),
  "supplier page should fetch products for the selected supplier"
)

assert.ok(
  source.includes("@click=\"openDetail(scope.row)\""),
  "supplier table should provide a detail action for each supplier"
)

assert.ok(
  source.includes("supplierProductList"),
  "supplier detail should keep a product list in component state"
)

assert.ok(
  source.includes('label="供货商品"') && source.includes('prop="productName"'),
  "supplier detail should render a supplied-product table"
)

const dialogs = source.match(/<el-dialog\b[^>]*>/g) || []

assert.strictEqual(
  dialogs.length,
  2,
  "supplier page should keep one detail dialog and one form dialog"
)

assert.ok(
  dialogs.every(dialog => dialog.includes("append-to-body")),
  "supplier dialogs should escape the isolated app-main stacking context"
)

assert.ok(
  dialogs.every(dialog => dialog.includes('class="scrollbar"')),
  "supplier dialogs should keep their body scrollable on short viewports"
)
