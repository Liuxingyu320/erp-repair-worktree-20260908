const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const purchase = readUi("src/views/inventory/purchase/index.vue")
const transfer = readUi("src/views/inventory/transfer/index.vue")
const records = readUi("src/views/inventory/transfer/records.vue")
const transferOrder = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvTransferOrder.java")
const navigationSql = readRepo("sql/erp_inventory_warehouse_navigation_20260713.sql")
const navigationRollbackSql = readRepo("sql/erp_inventory_warehouse_navigation_rollback_20260713.sql")

assert.ok(
  purchase.includes("purchasePrimaryAction") &&
    purchase.includes("purchaseMoreActions") &&
    purchase.includes('label="操作" width="190" fixed="right"') &&
    purchase.includes("compact-row-actions"),
  "purchase list should keep one primary action and move low-frequency actions into More"
)

assert.ok(
  transfer.includes("transferPrimaryAction") &&
    transfer.includes("transferMoreActions") &&
    transfer.includes('label="操作" width="200" fixed="right"') &&
    !transfer.includes('label="操作" width="390" fixed="right"'),
  "transfer list should not reserve a 390px fixed action column"
)

assert.ok(
  records.includes("formatLocalTime") &&
    records.includes('label="操作" width="100" fixed="right"') &&
    records.includes('{y}-{m}-{d} {h}:{i}:{s}') &&
    transfer.includes("formatLocalTime"),
  "transfer list and records should render consistent local timestamps"
)

assert.ok(
  transferOrder.includes('@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")'),
  "transfer API dates should have an explicit local serialization contract"
)

assert.ok(
  navigationSql.includes("仓储作业") &&
    navigationSql.includes("门店库存") &&
    navigationSql.includes("仓库库存") &&
    navigationSql.includes("库存盘点入口") &&
    navigationSql.includes("库存报表入口") &&
    navigationSql.includes("sys_role_menu_warehouse_nav_backup_20260713") &&
    navigationRollbackSql.includes("sys_role_menu_warehouse_nav_backup_20260713"),
  "warehouse navigation migration should consolidate routes and provide an exact role-menu rollback"
)

console.log("warehouseOperationsUx tests passed")
