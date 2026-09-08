import request from '@/utils/request'

const BASE_URL = '/approval/tasks'

function executeApprovalTask(taskId, action, data, config = {}) {
  return request(Object.assign({
    url: `${BASE_URL}/${taskId}/${action}`,
    method: 'post',
    data,
    silentError: true
  }, config))
}

export function approveApprovalTask(taskId, data = {}, config = {}) {
  return executeApprovalTask(taskId, 'approve', data, config)
}

export function returnApprovalTask(taskId, data, config = {}) {
  return executeApprovalTask(taskId, 'return', data, config)
}

export function rejectApprovalTask(taskId, data, config = {}) {
  return executeApprovalTask(taskId, 'reject', data, config)
}
