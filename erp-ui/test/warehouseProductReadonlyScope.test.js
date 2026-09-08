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
const readonlyLabels = [
  "序号",
  "上线分类",
  "产品类别名称",
  "等级",
  "规格",
  "产品描述",
  "补货单位",
  "参考成本价"
]

readonlyLabels.forEach(label => {
  assert.ok(
    tableSource.includes(`label="${label}"`),
    `readonly product table should include ${label}`
  )
})

assert.ok(
  source.includes('getSelectedDeptContext') &&
    source.includes('selectedDeptContext()') &&
    source.includes('canOperateWarehouseProduct()') &&
    source.includes('return this.selectedDeptContext.isWarehouse'),
  "product page should use the selected warehouse context as the warehouse operation gate"
)

assert.ok(
  tableSource.includes('v-if="isProductReadonlyMode"') &&
    tableSource.includes('v-else') &&
    tableSource.includes('v-if="canOperateWarehouseProduct" label="操作"'),
  "product page should switch between readonly columns and warehouse operation columns"
)

;["新增商品", "导入", "导出", "下载模板"].forEach(label => {
  const buttonPattern = new RegExp(`v-if="canOperateWarehouseProduct"[\\s\\S]{0,180}>${label}</el-button>`)
  assert.ok(buttonPattern.test(source), `${label} button should require warehouse operation scope`)
})

assert.ok(
  source.includes('ensureWarehouseProductOperation') &&
    source.includes('没有仓库权限，仅可查看商品资料') &&
    source.includes('openForm(row)') &&
    source.includes('handleDelete(row)') &&
    source.includes('handleExport()') &&
    source.includes('doImport()'),
  "product write and export handlers should guard non-warehouse users"
)

console.log("warehouseProductReadonlyScope tests passed")
