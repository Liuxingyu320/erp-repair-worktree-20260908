import request from '@/utils/request'

// 查询发货通知列表
export function listDeliveryNotice(query) {
  return request({ url: '/inventory/deliveryNotice/list', method: 'get', params: query })
}

// 查询发货通知详情
export function getDeliveryNotice(noticeId) {
  return request({ url: '/inventory/deliveryNotice/' + noticeId, method: 'get' })
}

// 生成发货通知
export function createDeliveryNotice(salesOrderId) {
  return request({ url: '/inventory/deliveryNotice/create/' + salesOrderId, method: 'post' })
}

// 执行发货
export function deliverDeliveryNotice(noticeId, data) {
  return request({ url: '/inventory/deliveryNotice/deliver/' + noticeId, method: 'post', data: data })
}

// 取消发货通知
export function cancelDeliveryNotice(noticeId) {
  return request({ url: '/inventory/deliveryNotice/' + noticeId, method: 'delete' })
}
