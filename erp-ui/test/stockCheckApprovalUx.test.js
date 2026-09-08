const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const apiSource = fs.readFileSync(path.resolve(uiRoot, "src/api/inventory/stockCheck.js"), "utf8")
const viewSource = fs.readFileSync(path.resolve(uiRoot, "src/views/inventory/stockCheck/index.vue"), "utf8")

assert.ok(
  apiSource.includes("export function submitStockCheck") &&
    apiSource.includes("/stockCheck/submit/") &&
    apiSource.includes("export function restartStockCheck") &&
    apiSource.includes("/stockCheck/restart/") &&
    apiSource.includes("export function listStockCheckApprovalTodos") &&
    apiSource.includes("/stockCheck/approval/todo") &&
    apiSource.includes("export function approveStockCheck") &&
    apiSource.includes("/approve") &&
    apiSource.includes("export function rejectStockCheck") &&
    apiSource.includes("/reject") &&
    apiSource.includes("export function getStockCheckApprovalTrack") &&
    apiSource.includes("/approval-track"),
  "stock check API should expose submit, restart, todo, approval, rejection, and track calls"
)

assert.ok(
  !apiSource.includes("confirmStockCheck") &&
    !apiSource.includes("/stockCheck/confirm/") &&
    !viewSource.includes("confirmStockCheck") &&
    !viewSource.includes("canConfirm"),
  "desktop stock checks must not retain the direct-confirm bypass"
)

assert.ok(
  viewSource.includes("待我审批") &&
    viewSource.includes("loadApprovalTodos") &&
    viewSource.includes("listStockCheckApprovalTodos") &&
    viewSource.includes("todoMode"),
  "desktop stock checks should have an approval-permission guarded todo filter"
)

assert.ok(
  viewSource.includes('{ label: "待审批", value: "pending_approval" }') &&
    viewSource.includes('{ label: "已退回（可修改）", value: "returned" }') &&
    viewSource.includes('{ label: "已驳回（可修改）", value: "rejected" }') &&
    viewSource.includes('{ label: "库存已变化（需重盘）", value: "invalidated" }'),
  "desktop stock checks should expose the approval state labels"
)

assert.ok(
  viewSource.includes("保存实盘") &&
    viewSource.includes("提交盘点") &&
    viewSource.includes("saveActualQty") &&
    viewSource.includes("submitActualQty"),
  "saving actual quantities and submitting a stock check should be separate actions"
)

assert.ok(
  viewSource.includes("lastRejectReason") &&
    viewSource.includes("lastRejectedBy") &&
    viewSource.includes("已驳回（可修改）") &&
    viewSource.includes("修改后重新提交") &&
    viewSource.includes('["draft", "rejected", "returned"].includes(row.status)'),
  "rejected and returned checks should show the visible result and remain resubmittable"
)

assert.ok(
  viewSource.includes("inv:stockCheck:approve") &&
    viewSource.includes("openApproval") &&
    viewSource.includes("approveStockCheck") &&
    viewSource.includes("rejectStockCheck") &&
    viewSource.includes("approvalForm.instanceId") &&
    viewSource.includes("approvalForm.comment"),
  "approve and reject actions should be permission guarded and carry the current instance"
)

assert.ok(
  viewSource.includes("approvalTrack") &&
    viewSource.includes("getStockCheckApprovalTrack") &&
    viewSource.includes("selfApproved") &&
    viewSource.includes("自审"),
  "desktop details should render approval rounds and self-approval markers"
)

assert.ok(
  viewSource.includes("detailSnapshotRows") &&
    viewSource.includes("invalidSnapshotRows") &&
    viewSource.includes("adjustmentResultRows") &&
    viewSource.includes("提交冻结明细") &&
    viewSource.includes("库存失效证据") &&
    viewSource.includes("库存调整结果"),
  "desktop approval history should render each round's frozen, invalidation, and adjustment evidence"
)

assert.ok(
  viewSource.includes("restartStockCheck") &&
    viewSource.includes('v-if="canRestart(scope.row)"') &&
    viewSource.includes('row.status === "invalidated"') &&
    viewSource.includes("canActAsCounter"),
  "only the assigned counter or an explicit manager should restart invalidated checks"
)

assert.ok(
  apiSource.includes("listStockCheckCounterCandidates") &&
    apiSource.includes("assignStockCheck") &&
    viewSource.includes("counterUserId") &&
    viewSource.includes("调整指派") &&
    !viewSource.includes("不填则为当前用户") &&
    viewSource.includes("counterName: this.form.counterName") === false,
  "desktop stock checks should select a real eligible counter and separate assignment from input"
)

console.log("stockCheckApprovalUx tests passed")
