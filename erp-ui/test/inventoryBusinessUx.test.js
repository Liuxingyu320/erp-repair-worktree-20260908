const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const selectShopSource = readUi("src/views/select-shop/index.vue")
const stockSource = readUi("src/views/inventory/stock/index.vue")
const purchaseSource = readUi("src/views/inventory/purchase/index.vue")
const transferSource = readUi("src/views/inventory/transfer/index.vue")

assert.ok(
  selectShopSource.includes("库存、采购、调拨建议选择门店或仓库") &&
    selectShopSource.includes("getSelectionBusinessHint") &&
    selectShopSource.includes("hasBusinessChildren"),
  "organization selection should explain business impact and make expandable parents obvious"
)

assert.ok(
  stockSource.includes("stockEmptyText") &&
    stockSource.includes("当前仓库暂无可用库存") &&
    stockSource.includes("stockContextAlert"),
  "stock page should explain invalid context and empty inventory next steps"
)

assert.ok(
  purchaseSource.includes("purchaseContextAlert") &&
    purchaseSource.includes("采购单只能在仓库上下文中创建、收货和质检") &&
    purchaseSource.includes(":empty-text=\"purchaseEmptyText\""),
  "purchase page should explain why warehouse-only actions are unavailable"
)

assert.ok(
  transferSource.includes("transferContextAlert") &&
    transferSource.includes("请先选择要货仓库") &&
    transferSource.includes(":empty-text=\"stockPickerEmptyText\""),
  "transfer page should explain store/warehouse requirements and empty source stock"
)

const permissionSql = readRepo("sql/erp_inventory_permission_alignment_20260613.sql")
;[
  "inv:sales:submit",
  "inv:purchase:submit",
  "inv:purchase:qc",
  "inv:product:import",
  "inv:report:list",
  "inv:sales:export",
  "inv:purchase:export"
].forEach(permission => {
  assert.ok(permissionSql.includes(permission), "permission migration should include " + permission)
})

console.log("inventoryBusinessUx tests passed")
