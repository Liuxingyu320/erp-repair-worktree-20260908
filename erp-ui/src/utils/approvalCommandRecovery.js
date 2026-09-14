const ACTION_TYPES = { approve: 'APPROVE', return: 'RETURN', reject: 'REJECT' }
const id = value => value == null ? '' : String(value)
const reason = value => String(value || '').trim()
function createApprovalCommand(target, action, comment, actorId, requestId) {
  if (!target || !ACTION_TYPES[action] || !target.businessCode || ![target.businessId,target.taskId,target.instanceId,actorId].every(v => /^[1-9]\d{0,18}$/.test(id(v)))) throw Error('审批目标不完整，请重新打开待办')
  return Object.freeze({ ...target, businessId:id(target.businessId),taskId:id(target.taskId),instanceId:id(target.instanceId),operatorUserId:id(actorId),action,reason:reason(comment),requestId })
}
function unwrapApprovalDetail(response) {
  const data = response && response.data
  return data && data.data !== undefined ? data.data : data || {}
}
function matchesApprovalCommand(detail, command) {
  if (!detail || !command) return false
  const instance = detail.instance || {}
  if (id(instance.instanceId) !== command.instanceId || id(instance.businessId) !== command.businessId || instance.businessCode !== command.businessCode) return false
  return (Array.isArray(detail.actions) ? detail.actions : []).some(log =>
    id(log.instanceId) === command.instanceId && id(log.taskId) === command.taskId &&
    id(log.operatorUserId) === command.operatorUserId && log.actionType === ACTION_TYPES[command.action] &&
    log.requestId === command.requestId && reason(log.actionReason) === command.reason)
}
function approvalFailureKind(error) {
  const status = Number(error && error.response && error.response.status)
  const code = Number(error && error.code)
  if (status === 401 || code === 401 || error && error.notified) return 'handled'
  if (!status || status >= 500) return 'unknown'
  return 'rejected'
}
function approvalMessage(error) { return error && error.response && error.response.data && error.response.data.msg || error && error.message || '审批失败，请重新核对任务状态与权限' }
function newApprovalRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return 'approval-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}
module.exports = { createApprovalCommand, unwrapApprovalDetail, matchesApprovalCommand, approvalFailureKind, approvalMessage, newApprovalRequestId }
