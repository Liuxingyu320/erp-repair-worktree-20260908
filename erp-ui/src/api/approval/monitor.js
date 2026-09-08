import request from '@/utils/request'

const BASE_URL = '/approval'

export function listApprovalInstances(params, config = {}) {
  return request({
    url: `${BASE_URL}/instances`,
    method: 'get',
    params,
    silentError: config.silentError === true
  })
}

export function getApprovalInstance(instanceId, config = {}) {
  return request({
    url: `${BASE_URL}/instances/${instanceId}`,
    method: 'get',
    silentError: config.silentError === true
  })
}

export function listLegacyApprovalTemplates(config = {}) {
  return request({
    url: `${BASE_URL}/legacy/templates`,
    method: 'get',
    silentError: config.silentError === true
  })
}

export function listLegacyApprovalInstances(params, config = {}) {
  return request({
    url: `${BASE_URL}/legacy/instances`,
    method: 'get',
    params,
    silentError: config.silentError === true
  })
}

export function getLegacyApprovalInstance(businessCode, legacyInstanceId, config = {}) {
  return request({
    url: `${BASE_URL}/legacy/instances/${encodeURIComponent(businessCode)}/${legacyInstanceId}`,
    method: 'get',
    silentError: config.silentError === true
  })
}

export function terminateApprovalInstance(instanceId, data) {
  return request({ url: `${BASE_URL}/instances/${instanceId}/terminate`, method: 'post', data, silentError: true })
}

export function reassignApprovalTask(taskId, data) {
  return request({ url: `${BASE_URL}/tasks/${taskId}/reassign`, method: 'post', data, silentError: true })
}

export function replayApprovalCallback(outboxId, data) {
  return request({ url: `${BASE_URL}/callbacks/${outboxId}/replay`, method: 'post', data, silentError: true })
}
