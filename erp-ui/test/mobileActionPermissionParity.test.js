const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const {
  MOBILE_ACTION_PERMISSIONS
} = require("../src/views/mobile/feature/mobileFeaturePolicy")
const desktopJobPage = readUi("src/views/monitor/job/index.vue")
const desktopOnlinePage = readUi("src/views/monitor/online/index.vue")
const desktopSalesPage = readUi("src/views/inventory/sales/index.vue")
const jobController = readRepo("erp-modules/erp-job/src/main/java/com/erp/job/controller/SysJobController.java")
const onlineController = readRepo("erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserOnlineController.java")
const deliveryController = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/controller/InvDeliveryNoticeController.java")

function mobilePermissions(actionId) {
  const permissions = MOBILE_ACTION_PERMISSIONS[actionId]
  assert.ok(Array.isArray(permissions), `mobile permission map should declare ${actionId}`)
  return permissions
}

;[
  ["runMonitorJob", "monitor:job:changeStatus"],
  ["pauseMonitorJob", "monitor:job:changeStatus"],
  ["resumeMonitorJob", "monitor:job:changeStatus"],
  ["forceLogoutOnline", "monitor:online:forceLogout"],
  ["createDeliveryNotice", "inv:deliveryNotice:add"],
  ["handleTransferDiscrepancy", "inv:transfer:discrepancy:handle"]
].forEach(([actionId, permission]) => {
  assert.deepStrictEqual(
    mobilePermissions(actionId),
    [permission],
    `${actionId} should use the same single permission as desktop and backend`
  )
})

assert.ok(
  /<el-switch[\s\S]{0,500}?v-hasPermi="\['monitor:job:changeStatus'\]"[\s\S]{0,100}?<\/el-switch>/.test(desktopJobPage) &&
    /command="handleRun"[\s\S]{0,250}?v-hasPermi="\['monitor:job:changeStatus'\]"/.test(desktopJobPage) &&
    jobController.includes('@RequiresPermissions("monitor:job:changeStatus")') &&
    jobController.includes('@PutMapping("/changeStatus")') &&
    jobController.includes('@PutMapping("/run")'),
  "scheduler run and status changes should share monitor:job:changeStatus across mobile, desktop, and backend"
)

assert.ok(
  desktopOnlinePage.includes("v-hasPermi=\"['monitor:online:forceLogout']\"") &&
    onlineController.includes('@RequiresPermissions("monitor:online:forceLogout")') &&
    onlineController.includes('@DeleteMapping("/{tokenId}")'),
  "online-user force logout should share monitor:online:forceLogout across mobile, desktop, and backend"
)

assert.ok(
  desktopSalesPage.includes("v-hasPermi=\"['inv:deliveryNotice:add']\"") &&
    deliveryController.includes('@RequiresPermissions("inv:deliveryNotice:add")') &&
    deliveryController.includes('@PostMapping("/create/{salesOrderId}")') &&
    !mobilePermissions("createDeliveryNotice").includes("inv:sales:deliver"),
  "delivery-notice creation should use only inv:deliveryNotice:add across mobile, desktop, and backend"
)

console.log("mobileActionPermissionParity tests passed")
