import request from '@/utils/request'

const BASE_URL = '/approval'

export function listApprovalTemplates(params, config = {}) {
  return request({
    url: `${BASE_URL}/templates`,
    method: 'get',
    params,
    silentError: config.silentError === true
  })
}

export function getApprovalTemplate(templateId, config = {}) {
  return request({
    url: `${BASE_URL}/templates/${templateId}`,
    method: 'get',
    silentError: config.silentError === true
  })
}

export function listApprovalRules(params, config = {}) {
  return request({
    url: `${BASE_URL}/rules`,
    method: 'get',
    params,
    silentError: config.silentError === true
  })
}

export function getApprovalRule(ruleId, config = {}) {
  return request({
    url: `${BASE_URL}/rules/${ruleId}`,
    method: 'get',
    silentError: config.silentError === true
  })
}

export function createApprovalRule(data) {
  return request({ url: `${BASE_URL}/rules`, method: 'post', data, silentError: true })
}

export function updateApprovalRule(ruleId, data) {
  return request({ url: `${BASE_URL}/rules/${ruleId}`, method: 'put', data, silentError: true })
}

export function createApprovalRuleDraft(ruleId, data = {}) {
  return request({ url: `${BASE_URL}/rules/${ruleId}/drafts`, method: 'post', data, silentError: true })
}

export function updateApprovalVersion(versionId, data) {
  return request({ url: `${BASE_URL}/versions/${versionId}`, method: 'put', data, silentError: true })
}

export function publishApprovalVersion(versionId, data = {}) {
  return request({ url: `${BASE_URL}/versions/${versionId}/publish`, method: 'post', data, silentError: true })
}

export function disableApprovalRule(ruleId, data = {}) {
  return request({ url: `${BASE_URL}/rules/${ruleId}/disable`, method: 'post', data, silentError: true })
}

export function previewApprovalCandidates(versionId, data) {
  return request({ url: `${BASE_URL}/versions/${versionId}/preview`, method: 'post', data, silentError: true })
}
