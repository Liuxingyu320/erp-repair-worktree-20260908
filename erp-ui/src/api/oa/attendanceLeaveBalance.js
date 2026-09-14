import request from '@/utils/request'

const base = '/oa/attendance-v2/leave/balance'
const silentRead = options => options && options.silentError === true ? { silentError: true } : {}
export function getMyLeaveBalance(leaveTypeId, options) {
  return request({ url: `${base}/my`, method: 'get', params: { leaveTypeId }, ...silentRead(options) })
}
export function recalculateMyLeaveBalance(leaveTypeId) {
  return request({ url: `${base}/my/recalculate`, method: 'post', data: { leaveTypeId } })
}
export function getEmployeeLeaveBalance(userId, leaveTypeId, options) {
  return request({ url: `${base}/employees/${userId}`, method: 'get', params: { leaveTypeId }, ...silentRead(options) })
}
export function getEmployeeLeaveBalanceLedger(userId, leaveTypeId, beforeId, options) {
  return request({ url: `${base}/employees/${userId}/ledger`, method: 'get', params: { leaveTypeId, beforeId }, ...silentRead(options) })
}
export function recalculateEmployeeLeaveBalance(userId, leaveTypeId) {
  return request({ url: `${base}/employees/${userId}/recalculate`, method: 'post', data: { leaveTypeId } })
}
export function adjustEmployeeLeaveBalance(userId, data) {
  return request({ url: `${base}/employees/${userId}/adjustments`, method: 'post', data })
}
export function listLeaveBalanceRules(options) { return request({ url: `${base}/rules`, method: 'get', ...silentRead(options) }) }
export function getLeaveBalanceRule(ruleId, options) { return request({ url: `${base}/rules/${ruleId}`, method: 'get', ...silentRead(options) }) }
export function createLeaveBalanceRule(data) { return request({ url: `${base}/rules`, method: 'post', data }) }
export function updateLeaveBalanceRule(ruleId, data) { return request({ url: `${base}/rules/${ruleId}/draft`, method: 'put', data }) }
export function publishLeaveBalanceRule(ruleId, rowVersion) { return request({ url: `${base}/rules/${ruleId}/publish`, method: 'post', data: { rowVersion } }) }
export function listLeaveBalanceLocations(options) { return request({ url: `${base}/locations`, method: 'get', ...silentRead(options) }) }
export function createLeaveBalanceLocation(data) { return request({ url: `${base}/locations`, method: 'post', data }) }
export function updateLeaveBalanceLocation(mappingId, data) { return request({ url: `${base}/locations/${mappingId}`, method: 'put', data }) }
