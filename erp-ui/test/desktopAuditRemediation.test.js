const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const uiRoot = path.resolve(__dirname, "..")

function readUi(file) {
  return fs.readFileSync(path.resolve(uiRoot, file), "utf8")
}

const shopContextSource = readUi("src/utils/shopContext.js")
const selectShopSource = readUi("src/views/select-shop/index.vue")
const reportSource = readUi("src/views/inventory/report/index.vue")
const salarySource = readUi("src/views/oa/salary/index.vue")
const attendanceSource = readUi("src/views/oa/attendance/index.vue")
const purchaseSource = readUi("src/views/inventory/purchase/index.vue")
const transferSource = readUi("src/views/inventory/transfer/index.vue")

function loadShopContext() {
  const sandbox = {
    module: { exports: {} },
    exports: {}
  }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(
    shopContextSource.replace(/\bexport\s+/g, "") +
      `
module.exports = {
  formatInventoryDeptLabel
}
`,
    sandbox,
    { filename: "shopContext.js" }
  )
  return sandbox.module.exports
}

const { formatInventoryDeptLabel } = loadShopContext()

assert.strictEqual(
  formatInventoryDeptLabel(
    { deptId: 108, deptName: "市场部门", deptType: "STORE", warehouseName: "市场部门" },
    { showId: true }
  ),
  "门店：市场部门（ID 108）",
  "readable organization labels should not misclassify a STORE row as a warehouse just because warehouseName is present"
)

assert.ok(
  selectShopSource.includes("businessDeptCount") &&
    selectShopSource.includes("countBusinessDeptNodes") &&
    selectShopSource.includes("业务组织可选"),
  "select-shop should count only store/warehouse business nodes in the prominent selectable count"
)

assert.ok(
  reportSource.includes("canViewCostMetrics") &&
    reportSource.includes('v-if="canViewCostMetrics"') &&
    reportSource.includes('hasPermi("inv:cost:view")'),
  "inventory report should hide cost, purchase amount, and gross margin metrics unless inv:cost:view is granted"
)

assert.ok(
  !salarySource.includes('@click="doCalculate"') && salarySource.includes('<legacy-salary-notice') &&
    salarySource.includes(`v-hasPermi="['oa:salary:export']"`),
  "retired salary page must only offer history and permission-controlled export"
)

assert.ok(
    attendanceSource.includes("<shift-management") &&
    attendanceSource.includes("<attendance-site-management") &&
    attendanceSource.includes("<weekly-schedule") &&
    attendanceSource.includes("<day-result-management") &&
    attendanceSource.includes("旧网页按钮打卡已下线") &&
    !attendanceSource.includes("handleCheckIn") &&
    !attendanceSource.includes("handleCheckOut"),
  "desktop attendance must configure shifts, scoped geofences and published schedules without restoring WEB punch actions"
)

assert.ok(
  purchaseSource.includes("getReceiveConfirmMessage") &&
    purchaseSource.includes("this.$modal.confirm(this.getReceiveConfirmMessage(items))") &&
    purchaseSource.includes("getQualityCheckConfirmMessage") &&
    purchaseSource.includes("this.$modal.confirm(this.getQualityCheckConfirmMessage())"),
  "purchase receive and quality check should show a final impact summary before calling write APIs"
)

assert.ok(
  transferSource.includes("getShipmentConfirmMessage") &&
    transferSource.includes("this.$modal.confirm(this.getShipmentConfirmMessage(items))") &&
    transferSource.includes("getReceiptConfirmMessage") &&
    transferSource.includes("this.$modal.confirm(this.getReceiptConfirmMessage(items))"),
  "transfer shipment and receipt should show a final impact summary before calling write APIs"
)

console.log("desktopAuditRemediation tests passed")
