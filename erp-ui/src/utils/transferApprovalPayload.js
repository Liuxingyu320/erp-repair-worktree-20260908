function buildTransferApprovalPayload(transferId, options) {
  const source = options || {}
  const payload = {
    transferId,
    taskId: source.taskId,
    action: normalizeAction(source.action || "approve"),
    comment: source.comment === undefined || source.comment === null ? "" : String(source.comment).trim()
  }
  if (payload.action !== "approve" && !payload.comment) {
    throw new Error("审批意见不能为空")
  }
  return payload
}

function normalizeAction(action) {
  const normalized = String(action || "").trim()
  if (normalized !== "approve" && normalized !== "reject") {
    throw new Error("审批动作必须为approve或reject")
  }
  return normalized
}

module.exports = { buildTransferApprovalPayload }
