const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const routerSource = readUi("src/router/index.js")
const permissionSource = readUi("src/permission.js")
const mobileInventorySource = readUi("src/views/mobile/inventory/index.vue")
const workbenchServiceSource = readUi("src/views/mobile/inventory/workbenchService.js")
const mobileFeatureSource = readUi("src/views/mobile/feature/index.vue")
const mobileFeatureActionsSource = readUi("src/views/mobile/feature/featureActions.js")
const sqlPath = path.resolve(repoRoot, "sql/erp_ux_p0_permission_hardening_20260613.sql")
const loadDataSource = mobileFeatureSource.slice(
  mobileFeatureSource.indexOf("loadData(options = {})"),
  mobileFeatureSource.indexOf("loadMore()")
)
const handleActionSource = mobileFeatureSource.slice(
  mobileFeatureSource.indexOf("handleAction(action)"),
  mobileFeatureSource.indexOf("getTopActionKey(action)")
)
const openItemSource = mobileFeatureSource.slice(
  mobileFeatureSource.indexOf("openItem(item)"),
  mobileFeatureSource.indexOf("closeItem()")
)

assert.ok(
  routerSource.includes("path: '/inventory/stock-log'") &&
    routerSource.includes("permissions: ['inv:stock:log']") &&
    routerSource.includes("@/views/inventory/stock/log") &&
    routerSource.includes("activeMenu: '/inventory/stock'"),
  "desktop stock log should have a hidden dynamic route instead of relying on a missing menu page"
)

assert.ok(
  !permissionSource.includes("isAllowedLocalPreview") &&
    !permissionSource.includes("isMobileRoutePreview") &&
    !permissionSource.includes("isMobileInventoryDemo") &&
    !permissionSource.includes("query.preview === '1'") &&
    !permissionSource.includes("query.demo === '1'"),
  "mobile preview/demo routes should not bypass authentication in shipped route guards"
)

assert.ok(
  mobileInventorySource.includes("createEmptyWorkbenchData") &&
    mobileInventorySource.includes("this.data = createEmptyWorkbenchData(context.selectedDeptName)") &&
    mobileInventorySource.includes("this.data = createEmptyWorkbenchData(context.selectedDeptName, this.errorMessage)"),
  "mobile inventory should not render local sample metrics while real data is loading or failed"
)

assert.ok(
  workbenchServiceSource.includes("getMobileWorkbenchSummary(createWorkbenchSummaryQuery") &&
    workbenchServiceSource.includes("selectedDeptId") &&
    workbenchServiceSource.includes("selectedDeptType"),
  "mobile workbench summary request should carry the selected organization context"
)

assert.ok(
  !mobileFeatureSource.includes("isPreviewMode") &&
    !mobileFeatureSource.includes("isPreviewLocked") &&
    !mobileFeatureSource.includes("演示/预览数据") &&
    !mobileFeatureSource.includes("预览模式不允许执行该操作") &&
    !mobileFeatureSource.includes("seedItems") &&
    loadDataSource.includes("if (!this.isFeatureAllowed())") &&
    loadDataSource.indexOf("if (!this.isFeatureAllowed())") < loadDataSource.indexOf("fetchMobileFeatureData") &&
    !handleActionSource.includes("this.isPreviewLocked()") &&
    openItemSource.includes("if (!this.featureKey"),
  "mobile feature pages should use live data and permissions instead of preview seed data branches"
)

assert.ok(
  mobileFeatureActionsSource.includes("按数量收货") &&
    mobileFeatureActionsSource.includes("按数量发货") &&
    mobileFeatureActionsSource.includes("提交盘点") &&
    mobileFeatureActionsSource.includes("通过后将立即调整库存") &&
    mobileFeatureActionsSource.includes("审批驳回") &&
    mobileFeatureActionsSource.includes("确认销售退货入库") &&
    mobileFeatureActionsSource.includes("确认采购退货出库"),
  "mobile high-risk inventory actions should name the business direction and inventory impact"
)

assert.ok(fs.existsSync(sqlPath), "P0 permission hardening SQL should be committed")
const sqlSource = readRepo("sql/erp_ux_p0_permission_hardening_20260613.sql")

;[
  "inv:stock:log",
  "inventory/stock/log",
  "inv:product:list",
  "inv:product:query",
  "inv:category:list",
  "inv:category:query",
  "inv:audit:list"
].forEach(token => {
  assert.ok(sqlSource.includes(token), "permission hardening SQL should include " + token)
})

console.log("p0UxHardening tests passed")
