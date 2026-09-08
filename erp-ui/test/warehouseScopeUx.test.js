const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const deptApiSource = readUi("src/api/system/dept.js")
const shopContextSource = readUi("src/utils/shopContext.js")
const warehouseSelectSource = readUi("src/views/inventory/components/WarehouseSelect.vue")
const deliveryNoticeSource = readUi("src/views/inventory/deliveryNotice/index.vue")
const stockCheckSource = readUi("src/views/inventory/stockCheck/index.vue")
const transferSource = readUi("src/views/inventory/transfer/index.vue")
const transferApiSource = readUi("src/api/inventory/transfer.js")
const userSource = readUi("src/views/system/user/index.vue")
const deptControllerSource = readRepo("erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java")
const deptServiceSource = readRepo("erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java")
const userMapperXml = readRepo("erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml")

assert.ok(
  deptApiSource.includes("export function listWarehouseDept(query") &&
    deptApiSource.includes("params: query"),
  "warehouse API should accept purpose/scopeDeptId query params while keeping the same endpoint"
)

assert.ok(
  warehouseSelectSource.includes("purpose:") &&
    warehouseSelectSource.includes("scopeDeptId:") &&
    warehouseSelectSource.includes("autoload:") &&
    warehouseSelectSource.includes('this.$emit("loaded"'),
  "WarehouseSelect should support purpose, scopeDeptId, autoload, and loaded event for scoped warehouse UX"
)

assert.ok(
  deliveryNoticeSource.includes('purpose="deliverySource"') &&
    deliveryNoticeSource.includes(':scope-dept-id="deliverDetail.shopDeptId"') &&
    deliveryNoticeSource.includes('ref="deliverWarehouseSelect"') &&
    deliveryNoticeSource.includes("onDeliverWarehousesLoaded"),
  "delivery notice should load deliverySource warehouses for the sales store before the user fills shipment quantities"
)

assert.ok(
  transferSource.includes('purpose="replenishmentSource"') &&
    transferSource.includes('purpose="returnTarget"') &&
    transferSource.includes(':scope-dept-id="currentShopDeptId"') &&
    transferSource.includes("handleTransferActionError") &&
    transferSource.includes("保存要货单失败") &&
    transferSource.includes("确认收货失败"),
  "transfer page should keep replenishment source and return target warehouse purposes separate"
)

assert.ok(
  transferSource.includes("getTransferApproveConfirmMessage") &&
    transferSource.includes("getTransferCancelConfirmMessage") &&
    transferSource.includes("getTransferExportConfirmMessage") &&
    transferSource.includes("要货门店") &&
    transferSource.includes("本次导出包含当前查询结果"),
  "transfer confirmations and exports should describe organization, order, quantity, and export range"
)

assert.ok(
  deliveryNoticeSource.includes("getDeliveryNoticeCancelConfirmMessage") &&
    deliveryNoticeSource.includes("getDeliveryNoticeExportConfirmMessage") &&
    deliveryNoticeSource.includes("getDeliveryNoticeDeliverConfirmMessage") &&
    deliveryNoticeSource.includes("本次发货将扣减所选仓库库存") &&
    deliveryNoticeSource.includes("本次导出包含当前查询结果"),
  "delivery notice confirmations and exports should describe stock impact and export range"
)

assert.ok(
  shopContextSource.includes("export function formatInventoryDeptLabel") &&
    shopContextSource.includes("showId"),
  "shop context utilities should expose a reusable inventory organization label formatter"
)

assert.ok(
  stockCheckSource.includes("formatInventoryDeptLabel") &&
    stockCheckSource.includes("inventoryOrgLabel(scope.row)") &&
    !stockCheckSource.includes('label="店铺ID"') &&
    !stockCheckSource.includes('label="仓库ID"'),
  "stock check list and detail should show readable inventory organization labels instead of raw shop/warehouse ID columns"
)

assert.ok(
  transferSource.includes("formatInventoryDeptLabel") &&
    transferSource.includes("currentShopLabel") &&
    !transferSource.includes("ID {{ currentShopDeptId"),
  "transfer request form should show the selected store name/path first and keep IDs out of the primary field"
)

assert.ok(
  ["saveTransfer", "submitTransfer", "approveTransfer", "deliverTransfer", "receiveTransfer", "receiveShipment", "cancelTransfer"]
    .every(name => transferApiSource.includes(`export function ${name}`)) &&
    (transferApiSource.match(/silentError: true/g) || []).length >= 7,
  "all transfer write APIs should silence global errors so the page can show business-specific messages"
)

assert.ok(
  deptControllerSource.includes("@RequestParam(value = \"purpose\"") &&
    deptControllerSource.includes("@RequestParam(value = \"scopeDeptId\""),
  "warehouse-list endpoint should accept purpose and scopeDeptId query params"
)

assert.ok(
  deptServiceSource.includes("PURPOSE_DELIVERY_SOURCE") &&
    deptServiceSource.includes("PURPOSE_REPLENISHMENT_SOURCE") &&
    deptServiceSource.includes("PURPOSE_CURRENT_WAREHOUSE"),
  "department service should implement named warehouse-list purposes instead of leaving filtering to each page"
)

assert.ok(
  userSource.includes("setupStatus") &&
    userSource.includes("配置状态") &&
    userSource.includes("未分配角色") &&
    userSource.includes("未授权管理范围"),
  "user list should expose role/scope setup status so new users cannot be left in an invisible half-configured state"
)

assert.ok(
  userMapperXml.includes("role_count") &&
    userMapperXml.includes("shop_scope_count") &&
    userMapperXml.includes("setup_status") &&
    userMapperXml.includes("missingRole") &&
    userMapperXml.includes("missingShopScope"),
  "user list SQL should return role/shop counts and setupStatus for filtering and display"
)

console.log("warehouseScopeUx tests passed")
