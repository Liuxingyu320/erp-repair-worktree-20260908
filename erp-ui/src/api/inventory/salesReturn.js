import request from '@/utils/request'

// 查询销售退货列表
export function listSalesReturn(query) {
  return request({ url: '/inventory/salesReturn/list', method: 'get', params: query })
}

// 查询我的销售退货单
export function listMySalesReturn(query) {
  return request({ url: '/inventory/salesReturn/my', method: 'get', params: query })
}

// 查询销售退货详情
export function getSalesReturn(returnId) {
  return request({ url: '/inventory/salesReturn/' + returnId, method: 'get' })
}

// 保存草稿
export function saveSalesReturn(data) {
  return request({ url: '/inventory/salesReturn/save', method: 'post', data: data })
}

// 提交
export function submitSalesReturn(data) {
  return request({ url: '/inventory/salesReturn/submit', method: 'post', data: data })
}

// 确认退货
export function confirmSalesReturn(returnId) {
  return request({ url: '/inventory/salesReturn/confirm/' + returnId, method: 'post' })
}

// 取消
export function cancelSalesReturn(returnId) {
  return request({ url: '/inventory/salesReturn/' + returnId, method: 'delete' })
}

export function getSalesReturnDraft(returnId, config) {
  return request(Object.assign({ url: '/inventory/salesReturn/draft/' + returnId, method: 'get' }, config && config.silentError === true ? { silentError: true } : {}))
}
export function submitSalesReturnDraft(returnId) {
  return request({ url: '/inventory/salesReturn/submit/' + returnId, method: 'post' })
}
export function listSalesReturnSourceOrders(query, config) {
  return request(Object.assign({ url: '/inventory/salesReturn/source-orders', method: 'get', params: query }, config && config.silentError === true ? { silentError: true } : {}))
}
export function getSalesReturnSourceOrder(orderId, config) {
  return request(Object.assign({ url: '/inventory/salesReturn/source-orders/' + orderId, method: 'get' }, config && config.silentError === true ? { silentError: true } : {}))
}

export function getSalesReturnActionContext(id, config) {
  return request(Object.assign({ url: '/inventory/salesReturn/action-context/' + id, method: 'get' }, config && config.silentError === true ? { silentError: true } : {}))
}
