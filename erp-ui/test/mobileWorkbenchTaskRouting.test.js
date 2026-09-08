const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const read = (file) => fs.readFileSync(path.resolve(root, file), "utf8")

const workbenchShellSource = read("src/views/mobile/components/MobileWorkbenchShell.vue")
const inventoryWorkbenchSource = read("src/views/mobile/inventory/index.vue")
const mobileTodoSource = read("src/views/mobile/todo/index.vue")
const featurePageSource = read("src/views/mobile/feature/index.vue")
const featureServiceSource = read("src/views/mobile/feature/featureService.js")
const { mobileRouteDefinitions } = require("../src/views/mobile/mobileRouteDefinitions")
const { hasAnyMobilePermission, getMobileRouteRequiredPermissions } = require("../src/views/mobile/mobileNavigation")
const { getWorkbenchActionDetails } = require("../src/views/mobile/components/mobileWorkbenchPolicy")

const transferRoute = mobileRouteDefinitions.find(route => route.path === "/mobile/transfer")
const transferFeature = transferRoute && transferRoute.meta && transferRoute.meta.mobileFeature
const transferActions = transferFeature && Array.isArray(transferFeature.actions) ? transferFeature.actions : []
const transferRecordsPermissions = getWorkbenchActionDetails("WAREHOUSE", "调拨记录").permissions

assert.deepStrictEqual(transferRecordsPermissions, ["inv:transfer:records"],
  "warehouse transfer records must use the same dedicated permission as mobile routing")
assert.deepStrictEqual(getMobileRouteRequiredPermissions("/mobile/transfer-records"), ["inv:transfer:records"])
assert.strictEqual(hasAnyMobilePermission(transferRecordsPermissions, ["inv:transfer:records"]), true,
  "the transfer records workbench item must be visible with the records permission")
assert.strictEqual(hasAnyMobilePermission(transferRecordsPermissions, ["inv:transfer:list"]), false,
  "the transfer records workbench item must stay hidden with only the transfer list permission")

assert.ok(
  workbenchShellSource.includes("todoRecent") &&
    workbenchShellSource.includes("todoCounts") &&
    workbenchShellSource.includes('"/mobile/todo"'),
  "mobile workbench should use the unified Todo Store instead of transfer-only counters"
)

assert.ok(
  /<button[\s\S]*?v-for="item in todoItems"[\s\S]*?class="todo-row"[\s\S]*?type="button"/.test(workbenchShellSource),
  "shared shell todos should use native buttons for keyboard and assistive-technology support"
)

assert.ok(
  /<button[\s\S]*?v-for="item in todoCards"[\s\S]*?class="priority-row"[\s\S]*?type="button"/.test(inventoryWorkbenchSource) &&
    inventoryWorkbenchSource.includes('@click="openTodo(item)"'),
  "inventory workbench todo rows must be semantic buttons with keyboard support"
)

assert.ok(
  /审批\s*\{\{\s*countFor\(["']approval["']\)\s*\}\}/.test(workbenchShellSource) &&
    /执行\s*\{\{\s*countFor\(["']execution["']\)\s*\}\}/.test(workbenchShellSource) &&
    /退回\s*\{\{\s*countFor\(["']returned["']\)\s*\}\}/.test(workbenchShellSource) &&
    /风险\s*\{\{\s*countFor\(["']risk["']\)\s*\}\}/.test(workbenchShellSource) &&
    /个人\s*\{\{\s*countFor\(["']personal["']\)\s*\}\}/.test(workbenchShellSource) &&
    workbenchShellSource.includes("countFor(category)"),
  "mobile shell must visibly render every unified todo category from todoCounts"
)

assert.ok(
  /审批\s*\{\{\s*todoCount\(["']approval["']\)\s*\}\}/.test(inventoryWorkbenchSource) &&
    /执行\s*\{\{\s*todoCount\(["']execution["']\)\s*\}\}/.test(inventoryWorkbenchSource) &&
    /退回\s*\{\{\s*todoCount\(["']returned["']\)\s*\}\}/.test(inventoryWorkbenchSource) &&
    /风险\s*\{\{\s*todoCount\(["']risk["']\)\s*\}\}/.test(inventoryWorkbenchSource) &&
    /个人\s*\{\{\s*todoCount\(["']personal["']\)\s*\}\}/.test(inventoryWorkbenchSource),
  "inventory mobile workbench must visibly render every unified todo category"
)

for (const [name, source] of [
  ["shared mobile shell", workbenchShellSource],
  ["inventory mobile workbench", inventoryWorkbenchSource]
]) {
  assert.ok(
    source.includes("beginTodoContextLease") &&
      source.includes("rollbackTodoContextLease") &&
      source.includes("getReturnRoute:") &&
      source.includes("showContextNotice:") &&
      source.includes("clearSelectedDept"),
    `${name} must preserve and restore organization context when opening a cross-organization todo`
  )
}

assert.ok(
  mobileTodoSource.includes('@click="goBack"') &&
    !mobileTodoSource.includes('@click="$router.back()"') &&
    mobileTodoSource.includes("returnPath") &&
    mobileTodoSource.includes('"/mobile/store"') &&
    mobileTodoSource.includes('"/mobile/warehouse"') &&
    mobileTodoSource.includes('"/mobile/mine"'),
  "mobile todo back navigation must use an internal return path with a safe mobile fallback"
)

assert.ok(
  workbenchShellSource.includes("openRoute") &&
    workbenchShellSource.includes("openTodo") &&
    workbenchShellSource.includes("navigateTodo") &&
    workbenchShellSource.includes("query: action.query"),
  "mobile workbench should preserve quick-action params and safely navigate todo cards"
)

assert.ok(
  featurePageSource.includes("buildRouteFeatureQuery()") &&
    featurePageSource.includes("this.$route.query") &&
    featurePageSource.includes("Object.assign({}, this.feature.defaultQuery || {}, this.buildRouteFeatureQuery())") &&
    featurePageSource.includes('"$route.fullPath"'),
  "mobile feature page should merge route query into the active list query and reload once for each path or query change"
)

assert.ok(
  featureServiceSource.includes("normalizeTransferListQuery") &&
    featureServiceSource.includes('direction === "receive"') &&
    /\.toDeptId\s*=\s*source\.selectedDeptId/.test(featureServiceSource) &&
    featureServiceSource.includes('direction === "deliver"') &&
    /\.fromDeptId\s*=\s*source\.selectedDeptId/.test(featureServiceSource) &&
    featureServiceSource.includes('statusGroup === "deliverable"') &&
    featureServiceSource.includes('["approved", "reserved", "partial_delivered"]') &&
    featureServiceSource.includes('statusGroup === "receivable"') &&
    featureServiceSource.includes('["partial_delivered", "delivered", "partial_received"]') &&
    /delete\s+\w+\.direction/.test(featureServiceSource) &&
    /delete\s+\w+\.warehouseId/.test(featureServiceSource),
  "mobile transfer list queries should convert receive/deliver direction and transfer status groups"
)

assert.ok(
  transferActions.some(action => action.label === "待收货" && action.query && action.query.statusGroup === "receivable" && action.query.direction === "receive") &&
    transferActions.some(action => action.label === "待发货" && action.query && action.query.statusGroup === "deliverable" && action.query.direction === "deliver"),
  "mobile transfer route should expose separate receive and deliver quick filters"
)

const transferApprovalRoute = mobileRouteDefinitions.find(route => route.path === "/mobile/transfer-approval")
const transferApprovalFeature = transferApprovalRoute && transferApprovalRoute.meta && transferApprovalRoute.meta.mobileFeature

assert.ok(
  transferApprovalFeature &&
    transferApprovalFeature.featureKey === "transferApproval" &&
    transferApprovalFeature.defaultQuery &&
    transferApprovalFeature.defaultQuery.status === "submitted",
  "mobile transfer approval route should register a dedicated submitted-task approval center"
)

console.log("mobileWorkbenchTaskRouting tests passed")
