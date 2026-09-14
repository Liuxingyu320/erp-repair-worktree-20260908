function normalizePurchaseId(value) {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw === "number" && !Number.isSafeInteger(raw)) return ""
  const id = raw == null ? "" : String(raw).trim()
  return /^[1-9]\d{0,18}$/.test(id) ? id : ""
}

function purchaseRouteTarget(query = {}, explicitPurchaseId) {
  const routeId = normalizePurchaseId(query.purchaseId || query.businessId)
  const purchaseId = explicitPurchaseId === undefined ? routeId : normalizePurchaseId(explicitPurchaseId)
  const sameTarget = Boolean(purchaseId) && purchaseId === routeId
  return Object.freeze({ purchaseId,
    taskId: sameTarget ? normalizePurchaseId(query.approvalTaskId) : "",
    instanceId: sameTarget ? normalizePurchaseId(query.approvalInstanceId) : "" })
}

function resolvePurchaseInstanceId(purchase, target) {
  if (!purchase || !target || !target.purchaseId || normalizePurchaseId(purchase.purchaseId) !== target.purchaseId) {
    throw new Error("采购申请与当前入口不一致，请重新打开")
  }
  const instanceId = normalizePurchaseId(purchase.approvalInstanceId)
  if (target.instanceId && target.instanceId !== instanceId) {
    throw new Error("采购申请的审批轮次已变化，请从待办重新打开")
  }
  if (target.taskId && !instanceId) throw new Error("当前采购申请没有可处理的审批任务")
  return instanceId
}

function validatePurchaseApproval(purchase, approval, target) {
  const instanceId = resolvePurchaseInstanceId(purchase, target)
  if (!instanceId) return null
  const instance = approval && approval.instance
  if (!instance || normalizePurchaseId(instance.instanceId || instance.id) !== instanceId ||
      instance.businessCode !== "OA_PURCHASE" || normalizePurchaseId(instance.businessId) !== target.purchaseId) {
    throw new Error("审批轨迹与采购申请不一致，请重新加载")
  }
  if (!target.taskId) return null
  const tasks = approval.tasks || approval.taskList || []
  const task = Array.isArray(tasks) && tasks.find(value => normalizePurchaseId(value.taskId || value.id) === target.taskId)
  if (!task || normalizePurchaseId(task.instanceId) !== instanceId) {
    throw new Error("审批任务与当前采购申请不一致，请从待办重新打开")
  }
  return task
}

function canActOnPurchase(purchase, approval, target) {
  try {
    const task = validatePurchaseApproval(purchase, approval, target)
    return Boolean(task) && String(task.taskStatus || task.status).toUpperCase() === "PENDING" &&
      String(approval.instance.status).toUpperCase() === "RUNNING"
  } catch (error) { return false }
}

module.exports = { normalizePurchaseId, purchaseRouteTarget, resolvePurchaseInstanceId, validatePurchaseApproval, canActOnPurchase }
