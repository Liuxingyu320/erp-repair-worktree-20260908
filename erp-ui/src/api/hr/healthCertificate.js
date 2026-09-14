import request from '@/utils/request'

export const getHealthCertificateCapability = () => request({
  url: '/system/hr/health-certificate/capability', method: 'get'
})

export const getMyHealthCertificates = () => request({
  url: '/system/hr/health-certificate/me', method: 'get'
})

export const saveMyHealthCertificateDraft = data => request({
  url: '/system/hr/health-certificate/me/draft', method: 'post', data, silentError: true
})

export const submitMyHealthCertificate = data => request({
  url: '/system/hr/health-certificate/me/submit', method: 'post', data, silentError: true
})

export const withdrawMyHealthCertificate = certificateId => request({
  url: `/system/hr/health-certificate/me/${certificateId}/withdraw`, method: 'post', silentError: true
})

export const listHealthCertificates = params => request({
  url: '/system/hr/health-certificate/list', method: 'get', params
})

export const getHealthCertificateOpsSummary = params => request({
  url: '/system/hr/health-certificate/ops-summary', method: 'get', params
})

export const getEmployeeHealthCertificates = userId => request({
  url: `/system/hr/health-certificate/employee/${userId}`, method: 'get'
})

export const reviewHealthCertificate = (certificateId, data) => request({
  url: `/system/hr/health-certificate/${certificateId}/review`, method: 'post', data, silentError: true
})

export const getHealthCertificateAttachment = (certificateId, mode = 'preview') => request({
  url: `/system/hr/health-certificate/${certificateId}/attachment`,
  method: 'get',
  params: { mode },
  responseType: 'blob',
  timeout: 0,
  silentError: true
})

export const listHealthCertificateApprovalStartOutboxes = params => request({
  url: '/system/hr/health-certificate/approval-start-outbox/list', method: 'get', params, silentError: true
})

export const replayHealthCertificateApprovalStart = (outboxId, version) => request({
  url: `/system/hr/health-certificate/approval-start-outbox/${outboxId}/replay`, method: 'post', data: { version }, silentError: true
})
