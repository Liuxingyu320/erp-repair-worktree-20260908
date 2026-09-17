import request from '@/utils/request'
import { inventoryDraftRecovery } from './draftRecovery'

// 查询销售退货列表
export function listSalesReturn(query, options) {
  return request({ url: '/inventory/salesReturn/list', method: 'get', silentError: options && options.silentError === true, params: query })
}

// 查询我的销售退货单
export function listMySalesReturn(query) {
  return request({ url: '/inventory/salesReturn/my', method: 'get', params: query })
}

// 查询销售退货详情
export function getSalesReturn(returnId, options) {
  return request({ url: '/inventory/salesReturn/' + returnId, method: 'get', silentError: options && options.silentError === true })
}

// 保存草稿
export function saveSalesReturn(data) {
  return inventoryDraftRecovery.submit('salesReturn', 'save', data)
}

// 提交
export function submitSalesReturn(data) {
  return inventoryDraftRecovery.submit('salesReturn', 'submit', data)
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
export function submitSalesReturnDraft(returnId, version) {
  return request({ url: '/inventory/salesReturn/submit/' + returnId, method: 'post', params: { version } })
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
