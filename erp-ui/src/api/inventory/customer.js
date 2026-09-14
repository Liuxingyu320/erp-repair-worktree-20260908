import request from '@/utils/request'

// 查询客户列表
export function listCustomer(query, config) {
  return request(Object.assign({ url: '/inventory/customer/list', method: 'get', params: query }, config))
}

// 查询客户详情
export function getCustomer(customerId) {
  return request({ url: '/inventory/customer/' + customerId, method: 'get' })
}

// 新增客户
export function addCustomer(data) {
  return request({ url: '/inventory/customer', method: 'post', data: data })
}

// 修改客户
export function updateCustomer(data) {
  return request({ url: '/inventory/customer/update', method: 'post', data: data })
}

// 删除客户
export function delCustomer(customerIds) {
  return request({ url: '/inventory/customer/' + customerIds, method: 'delete' })
}

export function listCustomerOptions(keyword) {
  return request({ url: '/inventory/customer/options', method: 'get', params: { keyword } })
}

export function listCustomerServiceCards(query) {
  return request({ url: '/inventory/customer/service-card/list', method: 'get', params: query })
}

export function getCustomerServiceCardCapabilities() {
  return request({
    url: '/inventory/customer/service-card/capabilities',
    method: 'get',
    silentError: true
  })
}

export function listCustomerServiceAudits(query) {
  return request({ url: '/inventory/customer/service-card/audit', method: 'get', params: query })
}

export function getCustomerServiceCardOpsSummary() {
  return request({ url: '/inventory/customer/service-card/ops-summary', method: 'get' })
}

export function getCustomerServiceCard(customerId) {
  return request({ url: '/inventory/customer/service-card/' + customerId, method: 'get' })
}

export function getCustomerServiceCardPhoto(customerId, mode = 'preview') {
  return request({
    url: '/inventory/customer/service-card/' + customerId + '/photo',
    method: 'get',
    params: { mode },
    responseType: 'blob',
    timeout: 0,
    silentError: true
  }).then(validateCustomerPhotoBlob)
}

export async function validateCustomerPhotoBlob(blob) {
  if (!blob || typeof blob !== 'object') {
    throw new Error('客户照片返回内容无效')
  }
  const contentType = String(blob.type || '').toLowerCase()
  if (contentType.includes('application/json') || contentType.startsWith('text/')) {
    let message = '客户照片暂时不可用'
    try {
      const payload = JSON.parse(await blob.text())
      message = payload && payload.msg ? payload.msg : message
    } catch (error) {
      // Keep the safe fallback for malformed JSON/text error bodies.
    }
    throw new Error(message)
  }
  return blob
}

export function createCustomerServiceCard(data) {
  return request({ url: '/inventory/customer/service-card', method: 'post', data, silentError: true })
}

export function updateCustomerServiceCard(customerId, data) {
  return request({ url: '/inventory/customer/service-card/' + customerId, method: 'put', data, silentError: true })
}

export function addCustomerServiceRecord(customerId, data) {
  return request({ url: '/inventory/customer/service-card/' + customerId + '/records', method: 'post', data, silentError: true })
}

export function archiveCustomerServiceCard(customerId, data) {
  return request({ url: '/inventory/customer/service-card/' + customerId + '/archive', method: 'post', data, silentError: true })
}

// Append-only service history, cursor-bound to the first page snapshot.
export function listCustomerServiceRecords(customerId, params) {
  return request({ url: '/inventory/customer/service-card/' + customerId + '/records', method: 'get', params, silentError: true })
}
