import request from '@/utils/request'

// 查询采购退货列表
export function listPurchaseReturn(query) {
  return request({ url: '/inventory/purchaseReturn/list', method: 'get', params: query })
}

// 查询我的采购退货单
export function listMyPurchaseReturn(query) {
  return request({ url: '/inventory/purchaseReturn/my', method: 'get', params: query })
}

// 查询采购退货详情
export function getPurchaseReturn(returnId) {
  return request({ url: '/inventory/purchaseReturn/' + returnId, method: 'get' })
}

// 保存草稿
export function savePurchaseReturn(data) {
  return request({ url: '/inventory/purchaseReturn/save', method: 'post', data: data })
}

// 提交
export function submitPurchaseReturn(data) {
  return request({ url: '/inventory/purchaseReturn/submit', method: 'post', data: data })
}

// 确认退货
export function confirmPurchaseReturn(returnId) {
  return request({ url: '/inventory/purchaseReturn/confirm/' + returnId, method: 'post' })
}

// 取消
export function cancelPurchaseReturn(returnId) {
  return request({ url: '/inventory/purchaseReturn/' + returnId, method: 'delete' })
}
