const assert = require("assert")
const fs = require("fs")
const path = require("path")

const source = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/index.vue"),
  "utf8"
)

const pickerMatch = source.match(/<el-dialog :title="isReturnForm \? '选择返仓库存' : \(isCrossStoreForm \? '选择调出店库存' : '选择要货库存'\)"[\s\S]*?<\/el-dialog>/)
assert.ok(pickerMatch, "transfer page should keep a dedicated stock picker dialog")

assert.ok(
  !pickerMatch[0].includes('label="当前库存"'),
  "store replenishment stock picker should not expose warehouse current inventory beyond available quantity"
)

assert.ok(
  pickerMatch[0].includes('label="参考成本价"') &&
    pickerMatch[0].includes("formatMoney(scope.row.costPrice)"),
  "store replenishment stock picker should show reference cost"
)

assert.ok(
  pickerMatch[0].includes('custom-class="stock-picker-dialog"') &&
    pickerMatch[0].includes('width="1120px"'),
  "stock picker dialog should be wide enough to show reference cost without horizontal scrolling on desktop"
)

assert.ok(
  !pickerMatch[0].includes('v-if="canViewCostFields" label="参考成本价"'),
  "reference cost in the transfer stock picker should not be hidden by the product cost permission gate"
)

assert.ok(
  source.includes("row.unit || product.unit") &&
    source.includes("row.spec || product.spec") &&
    source.includes("row.grade || product.grade"),
  "stock picker detail payload should use stock-row product fields and avoid requiring extra product-detail calls"
)

console.log("transferStockPickerPrivacy tests passed")
