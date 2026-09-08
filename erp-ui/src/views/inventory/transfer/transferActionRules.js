const DELIVERABLE_STATUSES = ["approved", "reserved", "partial_delivered"]
const DELIVERABLE_APPROVAL_STATES = ["approved", "auto_approved"]
const RECEIVABLE_STATUSES = ["partial_delivered", "delivered", "partial_received"]

const ACTIONS = Object.freeze({
  approve: Object.freeze({ key: "approve", label: "审批通过", permission: "inv:transfer:approve" }),
  cancel: Object.freeze({ key: "cancel", label: "取消调拨", permission: "inv:transfer:remove" }),
  detail: Object.freeze({ key: "detail", label: "详情", permission: "inv:transfer:query" }),
  discrepancy: Object.freeze({ key: "discrepancy", label: "处理差异", permission: "inv:transfer:discrepancy:handle" }),
  edit: Object.freeze({ key: "edit", label: "编辑", permission: "inv:transfer:edit" }),
  receive: Object.freeze({ key: "receive", label: "确认收货", permission: "inv:transfer:receive" }),
  rejectLegacy: Object.freeze({ key: "reject", label: "审批驳回", permission: "inv:transfer:approve" }),
  rejectNative: Object.freeze({ key: "reject", label: "审批拒绝", permission: "inv:transfer:approve" }),
  return: Object.freeze({ key: "return", label: "退回修改", permission: "inv:transfer:approve" }),
  ship: Object.freeze({ key: "ship", label: "发货", permission: "inv:transfer:deliver" }),
  sourceConfirm: Object.freeze({ key: "sourceConfirm", label: "调出店确认", permission: "inv:transfer:deliver" }),
  submit: Object.freeze({ key: "submit", label: "提交", permission: "inv:transfer:submit" }),
  withdraw: Object.freeze({ key: "withdraw", label: "撤回审批", permission: "inv:transfer:submit" })
})

function hasApprovedDeliveryState(row) {
  const state = row && row.approvalSummary && row.approvalSummary.state
  return DELIVERABLE_APPROVAL_STATES.includes(state)
}

function mergeTransferDetailForAction(detail, sourceRow) {
  const merged = Object.assign({}, detail || {})
  if (!merged.approvalSummary && sourceRow && sourceRow.approvalSummary) {
    merged.approvalSummary = sourceRow.approvalSummary
  }
  return merged
}

function hasRequiredSourceConfirmation(row) {
  if (!row || row.transferType !== "cross_store" || row.sourceBusinessType === "sales_delivery") {
    return true
  }
  return ["CONFIRMED", "PARTIAL"].includes(String(row.sourceConfirmStatus || "").toUpperCase())
}

function canShipTransfer(row) {
  return Boolean(row) &&
    DELIVERABLE_STATUSES.includes(row.status) &&
    hasApprovedDeliveryState(row) &&
    hasRequiredSourceConfirmation(row)
}

function sourceDeptId(row) {
  return row ? (row.fromDeptId || row.fromWarehouseId) : undefined
}

function targetDeptId(row) {
  return row ? (row.toDeptId || row.toWarehouseId) : undefined
}

function matchesCurrentDept(deptId, context) {
  return Boolean(context && context.currentDeptId) &&
    String(deptId) === String(context.currentDeptId)
}

function isCurrentSource(row, context) {
  return matchesCurrentDept(sourceDeptId(row), context)
}

function isCurrentTarget(row, context) {
  return matchesCurrentDept(targetDeptId(row), context)
}

function canShipTransferForContext(row, context) {
  if (!canShipTransfer(row)) return false
  if (row.transferType === "cross_store" || row.transferType === "store_return") {
    return Boolean(context && context.storeContext) && isCurrentSource(row, context)
  }
  return Boolean(context && context.warehouseContext) && isCurrentSource(row, context)
}

function canConfirmTransferSource(row, context) {
  if (!row || row.transferType !== "cross_store" || row.status !== "approved") return false
  if (String(row.sourceBusinessType || "") === "sales_delivery") return false
  const sourceStatus = String(row.sourceConfirmStatus || "PENDING").toUpperCase()
  return ["NOT_STARTED", "PENDING"].includes(sourceStatus) &&
    Boolean(context && context.storeContext) &&
    isCurrentSource(row, context)
}

function canReceiveTransfer(row, context) {
  if (!row || !RECEIVABLE_STATUSES.includes(row.status)) return false
  if (row.transferType === "store_return") {
    return Boolean(context && context.warehouseContext) && isCurrentTarget(row, context)
  }
  return Boolean(context && context.storeContext) && isCurrentTarget(row, context)
}

function canEditTransfer(row, context) {
  if (!row || row.status !== "draft" || !context || !context.storeContext) return false
  if (row.transferType === "store_return") {
    return isCurrentSource(row, context)
  }
  return ["warehouse", "cross_store"].includes(row.transferType || "warehouse") &&
    isCurrentTarget(row, context)
}

function canCancelTransfer(row, context) {
  if (!row || !["draft", "submitted"].includes(row.status) || !context || !context.storeContext) {
    return false
  }
  return row.transferType === "store_return"
    ? isCurrentSource(row, context)
    : isCurrentTarget(row, context)
}

function canHandleTransferDiscrepancy(row, context) {
  return Boolean(row) && row.status === "discrepancy" && isCurrentTarget(row, context)
}

function resolveTransferPrimaryAction(row, context) {
  if (canHandleTransferDiscrepancy(row, context)) return ACTIONS.discrepancy
  if (canConfirmTransferSource(row, context)) return ACTIONS.sourceConfirm
  if (canShipTransferForContext(row, context)) return ACTIONS.ship
  if (canReceiveTransfer(row, context)) return ACTIONS.receive
  if (row && row.status === "submitted") {
    if (context && context.nativeApproval && !context.unifiedTaskAvailable) {
      return ACTIONS.detail
    }
    return ACTIONS.approve
  }
  if (canEditTransfer(row, context)) return ACTIONS.submit
  return ACTIONS.detail
}

function actionItem(action, danger) {
  return Object.assign({}, action, { danger: Boolean(danger) })
}

function resolveTransferMoreActions(row, context) {
  const primaryKey = resolveTransferPrimaryAction(row, context).key
  const actions = []
  const add = (action, danger) => {
    if (action.key !== primaryKey) actions.push(actionItem(action, danger))
  }

  add(ACTIONS.detail)
  if (canEditTransfer(row, context)) add(ACTIONS.edit)
  if (canEditTransfer(row, context)) add(ACTIONS.submit)
  if (row && row.status === "submitted") {
    const nativeApproval = Boolean(context && context.nativeApproval)
    if (!nativeApproval || Boolean(context && context.unifiedTaskAvailable)) {
      add(ACTIONS.approve)
      if (nativeApproval) add(ACTIONS.return)
      add(nativeApproval ? ACTIONS.rejectNative : ACTIONS.rejectLegacy, true)
    }
  }
  if (canConfirmTransferSource(row, context)) add(ACTIONS.sourceConfirm)
  if (canShipTransferForContext(row, context)) add(ACTIONS.ship)
  if (canReceiveTransfer(row, context)) add(ACTIONS.receive)
  if (canHandleTransferDiscrepancy(row, context)) add(ACTIONS.discrepancy)
  if (context && context.withdrawAllowed) add(ACTIONS.withdraw, true)
  if (canCancelTransfer(row, context) && !(row.status === "submitted" && context && context.nativeApproval)) {
    add(ACTIONS.cancel, true)
  }
  return actions
}

module.exports = {
  canCancelTransfer,
  canConfirmTransferSource,
  canEditTransfer,
  canHandleTransferDiscrepancy,
  canReceiveTransfer,
  canShipTransfer,
  canShipTransferForContext,
  hasApprovedDeliveryState,
  hasRequiredSourceConfirmation,
  mergeTransferDetailForAction,
  resolveTransferMoreActions,
  resolveTransferPrimaryAction
}
