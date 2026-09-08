const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(__dirname, "../..")
const readUi = (file) => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = (file) => fs.readFileSync(path.resolve(repoRoot, file), "utf8")
const { canReceiveTransfer } = require("../src/views/inventory/transfer/transferActionRules")
const { getWorkbenchActionDetails } = require("../src/views/mobile/components/mobileWorkbenchPolicy")

const featureActionsSource = readUi("src/views/mobile/feature/featureActions.js")
const featureServiceSource = readUi("src/views/mobile/feature/featureService.js")
const mobileRouteSource = readUi("src/views/mobile/mobileRouteDefinitions.js")
const transferControllerSource = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvTransferController.java")

assert.strictEqual(
  canReceiveTransfer(
    {
      status: "partial_delivered",
      transferType: "warehouse",
      fromDeptId: 30,
      toDeptId: 20
    },
    {
      currentDeptId: 20,
      storeContext: true,
      warehouseContext: false
    }
  ),
  true,
  "desktop transfer rules should allow the target store to receive pending batches while the order is partial_delivered"
)

const mobileCanReceive = featureActionsSource.match(/function canReceiveTransfer\(row, status, context\) \{[\s\S]*?\n\}/)
assert.ok(mobileCanReceive, "mobile transfer actions should define canReceiveTransfer")
assert.ok(
  mobileCanReceive[0].includes('"partial_delivered"') || mobileCanReceive[0].includes("'partial_delivered'"),
  "mobile transfer actions should expose receiving for partial_delivered transfer orders"
)

assert.ok(
  featureServiceSource.includes('statusGroup === "receivable"') &&
    featureServiceSource.includes('["partial_delivered", "delivered", "partial_received"]'),
  "mobile transfer list queries should expand the receivable status group to partial-delivered, delivered, and partial-received orders"
)

assert.ok(
  mobileRouteSource.includes("statusGroup: 'receivable'") &&
    getWorkbenchActionDetails("WAREHOUSE", "调拨收货").query.statusGroup === "receivable",
  "mobile transfer receive filters should route to the receivable status group instead of only delivered orders"
)

assert.ok(
  transferControllerSource.includes("RECEIVABLE_STATUSES") &&
    transferControllerSource.includes('"receivable"') &&
    transferControllerSource.includes('"partial_delivered", "delivered", "partial_received"'),
  "transfer controller should natively resolve the receivable status group for list and export filters"
)

const storeReceiveSql = "sql/erp_inventory_store_transfer_receive_permission_20260703.sql"
const dockerStoreReceiveSql = "docker/mysql/db/erp_inventory_store_transfer_receive_permission_20260703.sql"
assert.ok(fs.existsSync(path.resolve(repoRoot, storeReceiveSql)), "store transfer receive permission SQL should exist")
assert.ok(fs.existsSync(path.resolve(repoRoot, dockerStoreReceiveSql)), "docker store transfer receive permission SQL should exist")

const permissionSql = readRepo(storeReceiveSql)
assert.ok(
  permissionSql.includes("inv:transfer:receive") &&
    /role_key\s*=\s*'dz'|role_key\s+IN\s*\([^)]*'dz'/.test(permissionSql),
  "store transfer receive permission SQL should restore inv:transfer:receive to the store manager role"
)

console.log("transferPartialDeliveryReceive tests passed")
