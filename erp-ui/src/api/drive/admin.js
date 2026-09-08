import request from '@/utils/request'

const base = '/file/drive/admin'

export function getDriveAdminStatus() {
  return request({ url: base + '/status', method: 'get', silentError: true })
}

export function getDriveQuotaOverview() {
  return request({ url: base + '/quota/overview', method: 'get', silentError: true })
}

export function getDriveCapacity() {
  return request({ url: base + '/quota/capacity', method: 'get', silentError: true })
}

export function updateDriveCapacity(data) {
  return request({ url: base + '/quota/capacity', method: 'put', data, silentError: true })
}

export function listDrivePersonalPolicies() {
  return request({ url: base + '/quota/personal-policies', method: 'get', silentError: true })
}

export function listDriveQuotaPosts() {
  return request({ url: base + '/quota/posts', method: 'get', silentError: true })
}

export function listDriveOrganizationTypeRules() {
  return request({ url: base + '/organizations/type-rules', method: 'get', silentError: true })
}

export function saveDriveOrganizationTypeRule(deptType, data) {
  return request({
    url: base + '/organizations/type-rules/' + encodeURIComponent(deptType),
    method: 'put',
    data,
    silentError: true
  })
}

export function saveDrivePersonalPolicy(subjectType, subjectId, data) {
  return request({
    url: base + '/quota/personal-policies/' + encodeURIComponent(subjectType) + '/' + subjectId,
    method: 'put',
    data,
    silentError: true
  })
}

export function deleteDrivePersonalPolicy(subjectType, subjectId, data) {
  return request({
    url: base + '/quota/personal-policies/' + encodeURIComponent(subjectType) + '/' + subjectId,
    method: 'delete',
    data,
    silentError: true
  })
}

export function listDriveQuotaUsers(params) {
  return request({ url: base + '/quota/users', method: 'get', params, silentError: true })
}

export function listDriveOrganizations() {
  return request({ url: base + '/organizations', method: 'get', silentError: true })
}

export function saveDriveOrganization(deptId, data) {
  return request({
    url: base + '/organizations/' + deptId,
    method: 'put',
    data,
    silentError: true
  })
}

export function saveDriveOrganizationsBatch(data) {
  return request({ url: base + '/organizations/batch', method: 'post', data, silentError: true })
}

export function previewDriveQuotaImpact(data) {
  return request({ url: base + '/quota/impact', method: 'post', data, silentError: true })
}

export function reconcileDriveOrganizations() {
  return request({ url: base + '/organizations/reconcile', method: 'post', silentError: true })
}
