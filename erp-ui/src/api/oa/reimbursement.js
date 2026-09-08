import request from '@/utils/request'

const BASE_URL = '/oa/reimbursement'

export function getReimbursementAvailability() {
  return request({
    url: `${BASE_URL}/availability`,
    method: 'get',
    silentError: true
  })
}

export function listMyReimbursements(params) {
  return request({ url: `${BASE_URL}/my`, method: 'get', params })
}

export function listFinanceReimbursements(params) {
  return request({ url: `${BASE_URL}/finance`, method: 'get', params })
}

export function getReimbursement(reimbursementId) {
  return request({
    url: `${BASE_URL}/${reimbursementId}`,
    method: 'get'
  })
}

export function saveReimbursement(data) {
  return request({
    url: `${BASE_URL}/save`,
    method: 'post',
    data,
    silentError: true
  })
}

export function submitReimbursement(data) {
  return request({
    url: `${BASE_URL}/submit`,
    method: 'post',
    data,
    silentError: true
  })
}

export function withdrawReimbursement(reimbursementId, data) {
  return request({
    url: `${BASE_URL}/${reimbursementId}/withdraw`,
    method: 'post',
    data,
    silentError: true
  })
}

export function uploadReimbursementInvoice(
  reimbursementId,
  file,
  onUploadProgress,
  signal
) {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: `${BASE_URL}/${reimbursementId}/invoices`,
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data,
    timeout: 0,
    silentError: true,
    onUploadProgress,
    signal
  })
}

export function deleteReimbursementInvoice(
  reimbursementId,
  invoiceId,
  expectedRowVersion
) {
  const params = expectedRowVersion == null
    ? undefined : { expectedRowVersion }
  return request({
    url: `${BASE_URL}/${reimbursementId}/invoices/${invoiceId}`,
    method: 'delete',
    params,
    silentError: true
  })
}

export function recognizeReimbursementInvoice(
  reimbursementId,
  invoiceId,
  engine = 'auto'
) {
  return request({
    url: `${BASE_URL}/${reimbursementId}/invoices/${invoiceId}/recognize`,
    method: 'post',
    data: { engine },
    timeout: 0,
    silentError: true
  })
}

export function updateReimbursementInvoiceRecognition(
  reimbursementId,
  invoiceId,
  data
) {
  return request({
    url: `${BASE_URL}/${reimbursementId}/invoices/${invoiceId}/recognition`,
    method: 'put',
    data,
    silentError: true
  })
}

export function getReimbursementInvoice(
  reimbursementId,
  invoiceId,
  mode = 'preview'
) {
  return request({
    url: `${BASE_URL}/${reimbursementId}/invoices/${invoiceId}/content`,
    method: 'get',
    params: { mode },
    responseType: 'blob',
    timeout: 0,
    silentError: true
  })
}

export function createReimbursementExport(reimbursementIds) {
  return request({
    url: `${BASE_URL}/finance/exports`,
    method: 'post',
    data: { reimbursementIds },
    timeout: 0,
    silentError: true
  })
}

export function downloadReimbursementExport(batchId) {
  return request({
    url: `${BASE_URL}/finance/exports/${batchId}/download`,
    method: 'get',
    responseType: 'blob',
    timeout: 0,
    silentError: true
  })
}
