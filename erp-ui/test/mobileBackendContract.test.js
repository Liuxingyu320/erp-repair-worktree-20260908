const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const read = (file) => fs.readFileSync(path.resolve(root, file), "utf8")

const inventoryApi = read("src/api/inventory/mobile.js")
const systemApi = read("src/api/system/mobile.js")
const entityService = read("src/views/mobile/feature/mobileEntityService.js")
const featureServiceSource = read("src/views/mobile/feature/featureService.js")
const featureActionRuntimeSource = read("src/views/mobile/feature/featureActionRuntime.js")
const mobileActionDialogSource = read("src/views/mobile/feature/components/MobileActionDialog.vue")
const workbenchService = read("src/views/mobile/inventory/workbenchService.js")
const featurePage = read("src/views/mobile/feature/index.vue")
const systemMobileController = fs.readFileSync(
  path.resolve(root, "../erp-modules/erp-system/src/main/java/com/erp/system/controller/SysMobileController.java"),
  "utf8"
)
const deliveryNoticeDomain = fs.readFileSync(
  path.resolve(root, "../erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvDeliveryNotice.java"),
  "utf8"
)
const deliveryNoticeMapper = fs.readFileSync(
  path.resolve(root, "../erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvDeliveryNoticeMapper.xml"),
  "utf8"
)
const deliveryNoticeDetailDomain = fs.readFileSync(
  path.resolve(root, "../erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvDeliveryNoticeDetail.java"),
  "utf8"
)
const deliveryNoticeDetailMapper = fs.readFileSync(
  path.resolve(root, "../erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvDeliveryNoticeDetailMapper.xml"),
  "utf8"
)
const receiveRequestDto = fs.readFileSync(
  path.resolve(root, "../erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/dto/InvReceiveRequest.java"),
  "utf8"
)
const purchaseController = fs.readFileSync(
  path.resolve(root, "../erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvPurchaseController.java"),
  "utf8"
)

assert.ok(
  inventoryApi.includes("/inventory/mobile/options/") &&
    inventoryApi.includes("/inventory/mobile/workbench/summary") &&
    inventoryApi.includes("/inventory/mobile/transfer-approval/todo") &&
    inventoryApi.includes("getMobileOptions") &&
    inventoryApi.includes("getMobileWorkbenchSummary") &&
    inventoryApi.includes("listTransferApprovalTodos"),
  "inventory mobile api should expose backend options, workbench summary, and transfer approval todo endpoints"
)

assert.ok(
  systemApi.includes("/system/mobile/profile") &&
    systemApi.includes("/system/mobile/options/warehouses") &&
    systemApi.includes("getMobileProfile") &&
    systemApi.includes("getMobileWarehouseOptions"),
  "system mobile api should expose profile and warehouse options endpoints"
)

assert.ok(
  entityService.includes("getMobileOptions") &&
    entityService.includes("getMobileWarehouseOptions"),
  "mobile entity picker should prefer compact backend options"
)

assert.ok(
  workbenchService.includes("getMobileWorkbenchSummary") &&
    workbenchService.includes("applyBackendSummary"),
  "mobile workbench should use backend summary statistics"
)

assert.ok(
  featureServiceSource.includes("listTransferApprovalTodos") &&
    featureServiceSource.includes('featureKey === "transferApproval"'),
  "mobile transfer approval feature should load candidate transfer approval todos from the backend"
)

assert.ok(
  featureServiceSource.includes("getTransferApprovalTrack") &&
    featureServiceSource.includes("resolveTransferDetailWithTrack") &&
    featureServiceSource.includes('featureKey === "replenishment"') &&
    featureServiceSource.includes('featureKey === "transferRecords"'),
  "mobile transfer sender and record details should load the shared approval track"
)

assert.ok(
  !featurePage.includes("getMobileProfile") &&
    !featurePage.includes("serverMobileAdminActions"),
  "mobile mine page should not load backend admin entries into the phone workbench"
)

;[
  "/mobile/system-user",
  "/mobile/system-role",
  "/mobile/system-post",
  "/mobile/system-dept",
  "/mobile/system-menu",
  "/mobile/user-shop",
  "/mobile/system-config",
  "/mobile/system-dict-type",
  "/mobile/monitor-job",
  "/mobile/monitor-online",
  "/mobile/salary-scheme",
  "/mobile/transfer-rules"
].forEach(routePath => {
  assert.ok(
    !systemMobileController.includes(routePath),
    `mobile profile backend should not return blocked admin entry ${routePath}`
  )
})

assert.ok(
  deliveryNoticeDomain.includes("private String statusGroup") &&
    deliveryNoticeMapper.includes("statusGroup == 'deliverable'") &&
    deliveryNoticeMapper.includes("n.status in ('pending', 'delivering')") &&
    deliveryNoticeMapper.includes("n.warehouse_id = #{warehouseId}"),
  "mobile outbound delivery notice list should support deliverable status groups and warehouse filtering"
)

assert.ok(
  deliveryNoticeDetailDomain.includes("private Long warehouseId") &&
    deliveryNoticeDetailMapper.includes('property="warehouseId" column="warehouse_id"') &&
    deliveryNoticeDetailMapper.includes("d.warehouse_id") &&
    deliveryNoticeDetailMapper.includes("left join sys_dept warehouse_dept on warehouse_dept.dept_id = d.warehouse_id") &&
    !deliveryNoticeDetailMapper.includes("left join inv_sales_detail sd on sd.detail_id = d.sales_detail_id") &&
    featureActionRuntimeSource.includes("filterDeliveryNoticeRowsByWarehouse") &&
    mobileActionDialogSource.includes("filterDeliveryNoticeRowsByWarehouse"),
  "mobile multi-warehouse outbound should expose and enforce the frozen warehouse snapshot on every notice row"
)

;["warehouseId", "arrivedTime"].forEach(fieldName => {
  assert.ok(
    new RegExp(
      `@NotNull\\([^\\n]*\\)\\s*` +
      `(?:@[A-Za-z0-9_.]+\\([^\\n]*\\)\\s*)*` +
      `private\\s+[A-Za-z0-9_<>?, ]+\\s+${fieldName};`
    ).test(receiveRequestDto),
    `purchase receive backend DTO should declare ${fieldName} as required`
  )
  assert.ok(
    featureActionRuntimeSource.includes(fieldName),
    `mobile purchase receive runtime should forward backend-required field ${fieldName}`
  )
})

assert.ok(
  purchaseController.includes("@Validated @RequestBody InvReceiveRequest") &&
    mobileActionDialogSource.includes('v-model="arrivedTime"') &&
    mobileActionDialogSource.includes('type="datetime-local"') &&
    mobileActionDialogSource.includes("required") &&
    mobileActionDialogSource.includes('v-model.trim="supplierBatchNo"') &&
    mobileActionDialogSource.includes('v-model.trim="deliveryNoteNo"') &&
    mobileActionDialogSource.includes('v-model.trim="receiveRemark"') &&
    featureActionRuntimeSource.includes('"supplierBatchNo"') &&
    featureActionRuntimeSource.includes('"deliveryNoteNo"') &&
    featureActionRuntimeSource.includes('"remark"'),
  "mobile purchase receiving should collect and forward every desktop/backend receipt metadata field"
)

console.log("mobileBackendContract tests passed")
