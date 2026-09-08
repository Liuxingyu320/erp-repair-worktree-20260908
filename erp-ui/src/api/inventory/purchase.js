import request from '@/utils/request'

// 保存草稿
export function savePurchase(data) {
  return request({ url: '/inventory/purchase/save', method: 'post', data: data })
}

// 提交采购单
export function submitPurchase(data) {
  return request({ url: '/inventory/purchase/submit', method: 'post', data: data })
}

// 采购单列表（全部）
export function listPurchase(query, config) {
  return request(Object.assign({ url: '/inventory/purchase/list', method: 'get', params: query }, config))
}

// 我的采购单
export function listMyPurchases(query) {
  return request({ url: '/inventory/purchase/my', method: 'get', params: query })
}

// 采购单详情
export function getPurchaseDetail(orderId) {
  return request({ url: '/inventory/purchase/' + orderId, method: 'get' })
}

// 收货入库
export function receivePurchase(orderId, data) {
  return request({ url: '/inventory/purchase/receive/' + orderId, method: 'post', data: data })
}

// 采购质检
export function qualityCheckPurchase(orderId, data) {
  return request({ url: '/inventory/purchase/qc/' + orderId, method: 'post', data: data })
}

// 待检收货批次
export function listPendingReceiptBatches(orderId) {
  return request({ url: '/inventory/purchase/' + orderId + '/receipt-batches/pending', method: 'get' })
}

// 收货批次逐行质检
export function qualityCheckPurchaseBatch(orderId, data) {
  return request({ url: '/inventory/purchase/qc/batch/' + orderId, method: 'post', data: data })
}

// 取消采购单
export function cancelPurchase(orderId) {
  return request({ url: '/inventory/purchase/' + orderId, method: 'delete' })
}

// 删除草稿采购单
export function deleteDraftPurchase(orderId) {
  return request({ url: '/inventory/purchase/delete/' + orderId, method: 'delete' })
}
