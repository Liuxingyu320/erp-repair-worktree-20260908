const PURCHASE_STAGE_LABELS = {
  draft: "草稿",
  pending_receive: "待收货",
  partial_received: "部分收货",
  pending_qc: "待质检",
  qc_rejected: "质检拒收",
  pending_putaway: "待上架",
  completed: "已完成",
  cancelled: "已取消"
}

function numberValue(value) {
  const result = Number(value)
  return Number.isFinite(result) ? result : 0
}

function resolvePurchaseBusinessStage(row) {
  if (!row) return ""
  if (row.businessStage && PURCHASE_STAGE_LABELS[row.businessStage]) return row.businessStage
  if (row.status === "cancelled") return "cancelled"
  if (row.status === "draft") return "draft"
  if (row.qcStatus === "pending") return "pending_qc"
  if (["rejected", "reject"].includes(row.qcStatus)) return "qc_rejected"
  if (["received", "completed"].includes(row.status)) return "completed"
  if (numberValue(row.receivedQuantity) > 0 && numberValue(row.remainingQuantity) > 0) {
    return "partial_received"
  }
  return "pending_receive"
}

function purchaseBusinessStageLabel(row) {
  const stage = resolvePurchaseBusinessStage(row)
  return PURCHASE_STAGE_LABELS[stage] || "未知阶段"
}

function purchaseBusinessStageTone(row) {
  const stage = resolvePurchaseBusinessStage(row)
  if (["completed"].includes(stage)) return "success"
  if (["pending_qc", "partial_received", "pending_putaway"].includes(stage)) return "warning"
  if (["qc_rejected", "cancelled"].includes(stage)) return "danger"
  if (stage === "draft") return "info"
  return "primary"
}

module.exports = {
  PURCHASE_STAGE_LABELS,
  resolvePurchaseBusinessStage,
  purchaseBusinessStageLabel,
  purchaseBusinessStageTone
}
