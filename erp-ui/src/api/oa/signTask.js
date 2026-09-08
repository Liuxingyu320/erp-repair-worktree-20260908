import request from '@/utils/request'

const PDF_OPERATION_TIMEOUT = 300000

export function listSignTasks(params) {
  return request({
    url: '/oa/signTask/list',
    method: 'get',
    params,
    silentError: true
  })
}

export function getSignTaskCapabilities() {
  return request({
    url: '/oa/signTask/capabilities',
    method: 'get',
    silentError: true
  })
}

export function getSignTaskMetrics() {
  return request({
    url: '/oa/signTask/metrics',
    method: 'get',
    silentError: true
  })
}

export function listSignTaskNotificationFailures() {
  return request({
    url: '/oa/signTask/notification/failures',
    method: 'get',
    silentError: true
  })
}

export function getSignTask(taskId) {
  return request({
    url: '/oa/signTask/' + taskId,
    method: 'get',
    silentError: true
  })
}

export function revalidateSignTask(taskId, data) {
  return request({
    url: '/oa/signTask/' + taskId + '/revalidate',
    method: 'post',
    data,
    timeout: PDF_OPERATION_TIMEOUT
  })
}

export function sendSignTask(taskId, data) {
  return request({
    url: '/oa/signTask/' + taskId + '/send',
    method: 'post',
    data
  })
}

export function retrySignTask(taskId, data) {
  return request({
    url: '/oa/signTask/' + taskId + '/retry',
    method: 'post',
    data,
    timeout: PDF_OPERATION_TIMEOUT
  })
}

export function retrySignTaskNotification(taskId, data) {
  return request({
    url: '/oa/signTask/' + taskId + '/notification/retry',
    method: 'post',
    data
  })
}

export function cancelSignTask(taskId, data) {
  return request({
    url: '/oa/signTask/' + taskId + '/cancel',
    method: 'post',
    data
  })
}

export function resolveSignTaskException(taskId, data) {
  return request({
    url: '/oa/signTask/' + taskId + '/resolve',
    method: 'post',
    data,
    silentError: true
  })
}

export function previewOnboardSignImport(file, employeeIds) {
  const data = new FormData()
  const normalizedEmployeeIds = (employeeIds || []).map(employeeId => String(employeeId))
  data.append('file', file)
  data.append('employeeIds', JSON.stringify(normalizedEmployeeIds))
  data.append('matchMode', normalizedEmployeeIds.length ? 'MANUAL_SELECTED' : 'EXCEL_PHONE_NAME')
  return request({
    url: '/oa/signTask/onboard/import/preview',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
    silentError: true
  })
}

export function getOnboardSignImportBatch(batchId) {
  return request({
    url: '/oa/signTask/onboard/import/' + batchId,
    method: 'get',
    silentError: true
  })
}

export function updateOnboardSignImportRow(batchId, rowId, data) {
  return request({
    url: '/oa/signTask/onboard/import/' + batchId + '/rows/' + rowId,
    method: 'put',
    data,
    silentError: true
  })
}

export function sendOnboardSignDataRequests(batchId, data) {
  return request({
    url: '/oa/signTask/onboard/import/' + batchId + '/data-request/send',
    method: 'post',
    data,
    silentError: true
  })
}

export function reviewOnboardSignDataRequest(requestId, data) {
  return request({
    url: '/oa/signTask/onboard/data-request/' + requestId + '/review',
    method: 'post',
    data,
    silentError: true
  })
}

export function generateOnboardSignImport(batchId, data) {
  return request({
    url: '/oa/signTask/onboard/import/' + batchId + '/generate',
    method: 'post',
    data,
    timeout: PDF_OPERATION_TIMEOUT,
    silentError: true
  })
}

export function listMyOnboardSignDataRequests() {
  return request({
    url: '/oa/signTask/onboard/data-request/mine',
    method: 'get',
    silentError: true
  })
}

export function getOnboardSignDataRequest(requestId) {
  return request({
    url: '/oa/signTask/onboard/data-request/' + requestId,
    method: 'get',
    silentError: true
  })
}

export function submitOnboardSignDataRequest(requestId, data) {
  return request({
    url: '/oa/signTask/onboard/data-request/' + requestId + '/submit',
    method: 'post',
    data,
    silentError: true
  })
}

export function sendSignTaskBatch(data) {
  return request({
    url: '/oa/signTask/batch/send',
    method: 'post',
    data,
    silentError: true
  })
}

export function previewSignTaskBatchFinalize(data) {
  return request({
    url: '/oa/signTask/batch/finalize/preview',
    method: 'post',
    data,
    silentError: true
  })
}

export function finalizeSignTaskBatch(data) {
  return request({
    url: '/oa/signTask/batch/finalize',
    method: 'post',
    data,
    timeout: PDF_OPERATION_TIMEOUT,
    silentError: true
  })
}

export function listOnboardSignCompanyWork() {
  return request({
    url: '/oa/signTask/onboard/company-work',
    method: 'get',
    silentError: true
  })
}

export function getOnboardSignCompanyWorkOptions(legalEntityId) {
  return request({
    url: '/oa/signTask/onboard/company-work/options',
    method: 'get',
    params: legalEntityId ? { legalEntityId } : undefined,
    silentError: true
  })
}

export function previewOnboardSignCompanyWork(data) {
  return request({
    url: '/oa/signTask/onboard/company-work/preview',
    method: 'post',
    data,
    silentError: true
  })
}

export function executeOnboardSignCompanyWork(data) {
  return request({
    url: '/oa/signTask/onboard/company-work/execute',
    method: 'post',
    data,
    timeout: PDF_OPERATION_TIMEOUT,
    silentError: true
  })
}

export function deleteSignTasksBatch(data) {
  return request({
    url: '/oa/signTask/batch/delete',
    method: 'post',
    data,
    silentError: true
  })
}

// Amounts come from the persisted Excel row, never from the browser payload.
export function archiveOnboardSignSalary(batchId, rows) {
  return request({
    url: '/system/hr/employee/onboard-salary/archive',
    method: 'post',
    data: { batchId, rows: rows.map(row => ({ rowId: row.rowId, version: row.version })) },
    silentError: true
  })
}
