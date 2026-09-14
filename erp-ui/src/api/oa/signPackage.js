import request from '@/utils/request'

const PDF_OPERATION_TIMEOUT = 300000

export function listSignScopeOptions() {
  return request({
    url: '/oa/signPackage/scope/options',
    method: 'get',
    silentError: true
  })
}

export function listSignPackages(params) {
  return request({
    url: '/oa/signPackage/list',
    method: 'get',
    params,
    silentError: true
  })
}

export function getSignPackage(packageId) {
  return request({
    url: '/oa/signPackage/' + packageId,
    method: 'get',
    silentError: true
  })
}

export function verifySignPackage(packageId, includeTechnical = false) {
  return request({
    url: '/oa/signPackage/' + packageId + '/verify',
    method: 'get',
    params: { includeTechnical }
  })
}

export function createSignPackage(data) {
  return request({
    url: '/oa/signPackage',
    method: 'post',
    data
  })
}

export function updateSignPackage(packageId, data) {
  return request({
    url: '/oa/signPackage/' + packageId,
    method: 'put',
    data
  })
}

export function sendSignPackage(packageId) {
  return request({
    url: '/oa/signPackage/' + packageId + '/send',
    method: 'post',
    timeout: PDF_OPERATION_TIMEOUT
  })
}

export function getSignPackageCompanyOptions(packageId, legalEntityId) {
  return request({
    url: '/oa/signPackage/' + packageId + '/company-options',
    method: 'get',
    params: legalEntityId ? { legalEntityId } : undefined
  })
}

export function finalizeSignPackage(packageId, data) {
  return request({
    url: '/oa/signPackage/' + packageId + '/finalize',
    method: 'post',
    data,
    timeout: PDF_OPERATION_TIMEOUT
  })
}

export function listCompanySeals(legalEntityId, activeOnly = false) {
  return request({
    url: '/oa/signPackage/company/seals',
    method: 'get',
    params: { legalEntityId, activeOnly }
  })
}

export function saveCompanySeal(data) {
  return request({
    url: '/oa/signPackage/company/seals',
    method: 'post',
    data
  })
}

export function voidSignPackage(packageId, data) {
  return request({
    url: '/oa/signPackage/' + packageId + '/void',
    method: 'post',
    data
  })
}

export function listSignTemplates(params) {
  return request({
    url: '/oa/signPackage/template/list',
    method: 'get',
    params,
    silentError: true
  })
}

export function previewSignTemplateFile(templateId) {
  return request({
    url: '/oa/signPackage/template/' + templateId + '/preview',
    method: 'get',
    responseType: 'blob',
    silentError: true
  })
}

export function downloadSignTemplateFile(templateId) {
  return request({
    url: '/oa/signPackage/template/' + templateId + '/file',
    method: 'get',
    responseType: 'blob',
    silentError: true
  })
}

export function listSignTemplateTypes() {
  return request({
    url: '/oa/signPackage/template/types',
    method: 'get',
    silentError: true
  })
}

export function saveSignTemplate(data) {
  return request({
    url: '/oa/signPackage/template',
    method: 'post',
    data
  })
}

export function listSignPlans(params) {
  return request({
    url: '/oa/signPackage/plan/list',
    method: 'get',
    params,
    silentError: true
  })
}

export function getSignPlan(planId) {
  return request({
    url: '/oa/signPackage/plan/' + planId,
    method: 'get'
  })
}

export function saveSignPlan(data) {
  return request({
    url: '/oa/signPackage/plan',
    method: 'post',
    data
  })
}

export function updateSignPlan(data) {
  return request({
    url: '/oa/signPackage/plan',
    method: 'put',
    data
  })
}

export function previewPublishSignPlan(planId) {
  return request({ url: '/oa/signPackage/plan/' + planId + '/publish-preview', method: 'get', silentError: true })
}

export function publishSignPlan(planId, data) {
  return request({
    url: '/oa/signPackage/plan/' + planId + '/publish',
    method: 'post',
    data,
    silentError: true
  })
}

export function matchSignTemplates(data) {
  return request({
    url: '/oa/signPackage/template/match',
    method: 'post',
    data,
    silentError: true
  })
}

export function previewSignPackageBatch(data) {
  return request({
    url: '/oa/signPackage/batch/preview',
    method: 'post',
    data,
    silentError: true
  })
}

export function createSignPackageDrafts(data) {
  return request({
    url: '/oa/signPackage/batch/createDrafts',
    method: 'post',
    data
  })
}

export function listMySignPackages(params) {
  return request({
    url: '/oa/signPackage/mobile/list',
    method: 'get',
    params
  })
}

export function getMySignPackage(packageId) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId,
    method: 'get'
  })
}

export function confirmSignPackageDocumentRead(packageId, documentId, data) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/read/' + documentId,
    method: 'post',
    data
  })
}

export function confirmSignPackageFinalDocumentRead(packageId, documentId, data) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/final-read/' + documentId,
    method: 'post',
    data
  })
}

export function signMySignPackage(packageId, data) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/sign',
    method: 'post',
    data,
    timeout: PDF_OPERATION_TIMEOUT
  })
}

export function refuseMySignPackage(packageId, data) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/refuse',
    method: 'post',
    data
  })
}

export function confirmMyFinalSignPackage(packageId, data) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/final-confirm',
    method: 'post',
    data,
    timeout: PDF_OPERATION_TIMEOUT
  })
}

export function downloadMySignPackageDocument(packageId, documentId) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/file',
    method: 'get',
    responseType: 'blob'
  })
}

export function getMySignPackageDocumentPreview(packageId, documentId, kind) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/preview',
    method: 'get',
    params: { kind: kind || 'review' }
  })
}

export function downloadMySignPackageDocumentPreviewPage(packageId, documentId, pageNumber, kind) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/preview/' + pageNumber,
    method: 'get',
    params: { kind: kind || 'review' },
    responseType: 'blob'
  })
}

export function downloadMySignedSignPackageDocument(packageId, documentId) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/signed-file',
    method: 'get',
    responseType: 'blob'
  })
}

export function downloadMyFinalSignPackageDocument(packageId, documentId) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/final-file',
    method: 'get',
    responseType: 'blob'
  })
}

export function downloadMyFinalSignPackageDocumentExport(packageId, documentId) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/final-file/export',
    method: 'get',
    responseType: 'blob',
    timeout: PDF_OPERATION_TIMEOUT,
    silentError: true
  })
}

export function downloadMySignPackageCertificate(packageId, documentId) {
  return request({
    url: '/oa/signPackage/mobile/' + packageId + '/documents/' + documentId + '/certificate',
    method: 'get',
    responseType: 'blob'
  })
}

export function downloadSignPackageDocument(packageId, documentId) {
  return request({
    url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/file',
    method: 'get',
    responseType: 'blob'
  })
}

export function downloadSignedSignPackageDocument(packageId, documentId) {
  return request({
    url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/signed-file',
    method: 'get',
    responseType: 'blob'
  })
}

export function downloadFinalSignPackageDocument(packageId, documentId) {
  return request({
    url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/final-file',
    method: 'get',
    responseType: 'blob'
  })
}

export function downloadFinalSignPackageDocumentExport(packageId, documentId) {
  return request({
    url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/final-file/export',
    method: 'get',
    responseType: 'blob',
    timeout: PDF_OPERATION_TIMEOUT,
    silentError: true
  })
}

export function downloadSignPackageCertificate(packageId, documentId) {
  return request({
    url: '/oa/signPackage/' + packageId + '/documents/' + documentId + '/certificate',
    method: 'get',
    responseType: 'blob'
  })
}
