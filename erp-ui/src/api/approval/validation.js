import request from '@/utils/request'

const BASE_URL = '/approval/validation'

export function listApprovalValidationRuns(params, config = {}) {
  return request({
    url: `${BASE_URL}/runs`,
    method: 'get',
    params,
    silentError: config.silentError === true
  })
}

export function getApprovalValidationRun(runId, config = {}) {
  return request({
    url: `${BASE_URL}/runs/${runId}`,
    method: 'get',
    silentError: config.silentError === true
  })
}

export function runApprovalValidation(data) {
  return request({ url: `${BASE_URL}/run`, method: 'post', data, silentError: true })
}
