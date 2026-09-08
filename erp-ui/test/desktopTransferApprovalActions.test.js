const assert = require("assert")
const fs = require("fs")
const path = require("path")
const { buildTransferApprovalPayload } = require("../src/views/mobile/feature/mobileActionPayloads")

const source = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/index.vue"),
  "utf8"
)

assert.ok(
  source.includes('approve: () => this.openTransferApproval(row, "approve")') &&
    source.includes('reject: () => this.openTransferApproval(row, "reject")') &&
    source.includes("transferPrimaryAction") &&
    source.includes("transferMoreActions"),
  "a submitted transfer row must expose separate approve and reject actions through the compact action menu"
)
assert.ok(
  source.includes('value="approve"') && source.includes('value="reject"'),
  "the desktop approval dialog must let the director confirm the selected decision"
)
assert.ok(
  source.includes("v-model=\"transferApprovalForm.comment\"") &&
    source.includes("submitTransferApproval"),
  "the desktop approval dialog must collect an optional approval opinion or a required rejection reason"
)
assert.ok(source.includes('if (action !== "approve" && !reason)') &&
  source.includes('return "可填写调拨审批意见"'),
"desktop approval must allow an empty approve opinion while keeping reject and return reasons mandatory")
assert.ok(
  source.includes(':required="transferApprovalForm.action !== \'approve\'"'),
  "transfer approve comments must not retain a misleading required marker for either engine"
)
assert.ok(
  /approveTransfer\(row\)\s*\{[\s\S]*?row\.status\s*!==\s*["']submitted["'][\s\S]*?return this\.openDetail\(row\.transferId\)/.test(source),
  "opening a focused transfer approval must re-check state and open the full detail before any decision UI"
)
assert.ok(
  source.includes('aria-label="待审批调拨单摘要"') &&
    source.includes("transferApprovalReview.toDeptName") &&
    source.includes("transferApprovalReview.fromDeptName") &&
    source.includes("transferApprovalReview.totalQuantity"),
  "the desktop approval confirmation must repeat the core document summary"
)
assert.ok(
  source.includes("formatLocalTime(detail.submittedTime)") &&
    source.includes("formatLocalTime(detail.approvedTime)") &&
    source.includes("formatLocalTime(detail.archivedTime)") &&
    source.includes("formatLocalTime(scope.row.shippedTime)") &&
    source.includes("formatLocalTime(scope.row.receivedTime)"),
  "desktop transfer detail timestamps must use the same readable local format as the list"
)
assert.ok(
  source.includes("buildTransferApprovalPayload") &&
    /approveTransfer\(\s*payload\s*\)/.test(source),
  "desktop transfer approval must use the validated shared payload"
)
assert.ok(
  source.includes("returnAfterTodoAction") && source.includes("result && result.returned ? result : this.getList()"),
  "a transfer approval opened from unified todo must return there, while ordinary business-list approvals still refresh in place"
)

assert.deepStrictEqual(buildTransferApprovalPayload("9007199254740999", {
  taskId: "task-7",
  action: "approve",
  comment: "  同意调拨  "
}), {
  transferId: "9007199254740999",
  taskId: "task-7",
  action: "approve",
  comment: "同意调拨"
})
assert.deepStrictEqual(buildTransferApprovalPayload("18", {
  action: "reject",
  comment: "库存安排不合理"
}), {
  transferId: "18",
  taskId: undefined,
  action: "reject",
  comment: "库存安排不合理"
})
assert.deepStrictEqual(buildTransferApprovalPayload("19", {
  action: "approve",
  comment: " "
}), {
  transferId: "19",
  taskId: undefined,
  action: "approve",
  comment: ""
})
assert.throws(
  () => buildTransferApprovalPayload("18", { action: "reject", comment: " " }),
  /审批意见不能为空/,
  "reject must never be submitted without a reason"
)

console.log("desktopTransferApprovalActions tests passed")
