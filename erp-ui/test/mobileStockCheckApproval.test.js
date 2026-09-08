const assert = require("assert")
const fs = require("fs")
const path = require("path")
const { getMobileFeatureActions } = require("../src/views/mobile/feature/featureActions")
const { mapMobileFeatureRows } = require("../src/views/mobile/feature/featureMapper")
const {
  MOBILE_ACTION_PERMISSIONS
} = require("../src/views/mobile/feature/mobileFeaturePolicy")

const root = path.resolve(__dirname, "..")
const actionSource = fs.readFileSync(path.resolve(root, "src/views/mobile/feature/featureActions.js"), "utf8")
const actionServiceSource = fs.readFileSync(path.resolve(root, "src/views/mobile/feature/featureActionService.js"), "utf8")
const runtimeSource = fs.readFileSync(path.resolve(root, "src/views/mobile/feature/featureActionRuntime.js"), "utf8")
const featureServiceSource = fs.readFileSync(path.resolve(root, "src/views/mobile/feature/featureService.js"), "utf8")
const statusPolicySource = fs.readFileSync(path.resolve(root, "src/views/mobile/feature/mobileFeatureStatusPolicy.js"), "utf8")
const detailFieldsPolicySource = fs.readFileSync(path.resolve(root, "src/views/mobile/feature/mobileFeatureDetailFieldsPolicy.js"), "utf8")
const detailSectionsPolicySource = fs.readFileSync(path.resolve(root, "src/views/mobile/feature/mobileFeatureDetailSectionsPolicy.js"), "utf8")
const dialogSource = fs.readFileSync(path.resolve(root, "src/views/mobile/feature/components/MobileActionDialog.vue"), "utf8")
const featurePageSource = fs.readFileSync(path.resolve(root, "src/views/mobile/feature/index.vue"), "utf8")
const routeSource = fs.readFileSync(path.resolve(root, "src/views/mobile/mobileRouteDefinitions.js"), "utf8")
const workbenchServiceSource = fs.readFileSync(path.resolve(root, "src/views/mobile/inventory/workbenchService.js"), "utf8")

assert.deepStrictEqual(
  getMobileFeatureActions("stockCheck", { _statusKey: "draft", _raw: { checkId: 1 } }).map(item => item.id),
  ["saveStockCheckInput", "submitStockCheck", "deleteStockCheckDraft", "cancelStockCheck"],
  "draft stock checks should separate save, submit, deletion, and cancellation"
)

assert.deepStrictEqual(
  getMobileFeatureActions("stockCheck", { _statusKey: "rejected", _raw: { checkId: 2 } }).map(item => item.id),
  ["saveStockCheckInput", "submitStockCheck", "cancelStockCheck"],
  "rejected stock checks should stay editable and resubmittable"
)

assert.deepStrictEqual(
  getMobileFeatureActions("stockCheck", { _statusKey: "returned", _raw: { checkId: 20 } }).map(item => item.id),
  ["saveStockCheckInput", "submitStockCheck", "cancelStockCheck"],
  "returned native-approval stock checks should stay editable and resubmittable"
)

assert.deepStrictEqual(
  getMobileFeatureActions("stockCheck", { _statusKey: "pending_approval", _raw: { checkId: 3 } }).map(item => item.id),
  ["approveStockCheck", "rejectStockCheck", "cancelStockCheck"],
  "pending stock checks should expose approval actions before permission filtering"
)

assert.deepStrictEqual(
  getMobileFeatureActions("stockCheck", { _statusKey: "invalidated", _raw: { checkId: 4 } }).map(item => item.id),
  ["restartStockCheck", "cancelStockCheck"],
  "invalidated stock checks should expose restart"
)

assert.ok(
  !actionSource.includes("confirmStockCheck") &&
    !actionServiceSource.includes("confirmStockCheck") &&
    !runtimeSource.includes("confirmStockCheck") &&
    !featurePageSource.includes("confirmStockCheck"),
  "mobile stock checks must not retain a direct-confirm action"
)

assert.ok(
  runtimeSource.includes("saveStockCheckInput") &&
    actionServiceSource.includes("inputStockCheck") &&
    actionServiceSource.includes("submitStockCheck") &&
    actionServiceSource.includes("approveStockCheck") &&
    actionServiceSource.includes("rejectStockCheck") &&
    actionServiceSource.includes("restartStockCheck") &&
    runtimeSource.includes("approvalInstanceId"),
  "mobile action runtime should call every approval workflow API with the current instance"
)

assert.ok(
  featureServiceSource.includes("listStockCheckApprovalTodos") &&
    featureServiceSource.includes("approvalTodo") &&
    featureServiceSource.includes("getStockCheckApprovalTrack"),
  "mobile stock check todo and detail loading should use approval endpoints"
)

const mapped = mapMobileFeatureRows("stockCheck", [{
  checkId: 8,
  checkNo: "CK-8",
  status: "rejected",
  lastRejectReason: "差异说明不完整",
  lastRejectedBy: "ops",
  approvalTrack: [{ roundNo: 1, status: "rejected", tasks: [{ approverName: "ops", selfApproved: "1" }] }]
}])[0]

assert.strictEqual(mapped.status, "已驳回（可修改）")
assert.ok(mapped._detailFields.some(field => field.label === "驳回原因" && field.value === "差异说明不完整"))
assert.ok(mapped._detailSections.some(section => section.title === "审批轨迹" && section.rows.some(row => row.extra.includes("自审"))))

assert.ok(
    statusPolicySource.includes('pending_approval: "待审批"') &&
    statusPolicySource.includes('returned: "已退回（可修改）"') &&
    statusPolicySource.includes('invalidated: "库存已变化（需重盘）"') &&
    detailFieldsPolicySource.includes("lastRejectReason") &&
    detailSectionsPolicySource.includes("selfApproved"),
  "mobile mapper should expose approval statuses, rejection, and self approval"
)

assert.ok(
  detailSectionsPolicySource.includes("detailSnapshotItems") &&
    detailSectionsPolicySource.includes("invalidDetailSnapshotItems") &&
    detailSectionsPolicySource.includes("adjustmentResultItems") &&
    detailSectionsPolicySource.includes("提交冻结") &&
    detailSectionsPolicySource.includes("失效证据") &&
    detailSectionsPolicySource.includes("调整结果"),
  "mobile approval history should expose each round's frozen, invalidation, and adjustment evidence"
)

assert.ok(
  dialogSource.includes("approveStockCheck") &&
    dialogSource.includes("rejectStockCheck") &&
    dialogSource.includes("submitStockCheck") &&
    dialogSource.includes("stockDifferenceRows") &&
    dialogSource.includes("action.acceptsComment") &&
    dialogSource.includes("处理意见不能为空"),
  "mobile approval dialog should show differences and enforce rejection comments"
)

assert.ok(
  dialogSource.includes("resolveStockCheckInputQuantity") &&
    dialogSource.includes("必须录入实盘数量") &&
    dialogSource.includes('rawQuantity === ""') &&
    dialogSource.includes("this.isStockCheckQuantityAction() || row.quantity > 0") &&
    !/resolveRemaining\(row\)[\s\S]*?bookQuantity[\s\S]*?isStockCheckQuantityAction/.test(dialogSource),
  "mobile stock check input must preserve saved zeroes but leave uncounted rows blank"
)

assert.ok(
  MOBILE_ACTION_PERMISSIONS.approveStockCheck.includes("inv:stockCheck:approve") &&
    MOBILE_ACTION_PERMISSIONS.rejectStockCheck.includes("inv:stockCheck:approve"),
  "mobile approve and reject actions should be permission guarded"
)

assert.ok(
  routeSource.includes("featureKey: 'stockCheck',\n        allowedDeptTypes: ['STORE', 'WAREHOUSE']") &&
    routeSource.includes("{ label: '已退回', icon: 'return', query: { status: 'returned' } }") &&
    routeSource.includes("approvalTodo: true }, permissions: ['inv:stockCheck:approve']") &&
    workbenchServiceSource.includes('status: "pending_approval"') &&
    !workbenchServiceSource.includes('status: "checking"'),
  "mobile route and workbench should expose both organizations and pending approval"
)

console.log("mobileStockCheckApproval tests passed")
