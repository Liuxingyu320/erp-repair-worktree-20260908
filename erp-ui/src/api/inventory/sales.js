import request from '@/utils/request'

// 保存草稿
export function saveSales(data) {
  return request({ url: '/inventory/sales/save', method: 'post', data: data })
}

// 提交销售单
export function submitSales(data) {
  return request({ url: '/inventory/sales/submit', method: 'post', data: data })
}

// 销售单列表（全部）
export function listSales(query, config) {
  return request(Object.assign({ url: '/inventory/sales/list', method: 'get', params: query }, config))
}

// 我的销售单
export function listMySales(query) {
  return request({ url: '/inventory/sales/my', method: 'get', params: query })
}

// 销售单详情
export function getSalesDetail(orderId) {
  return request({ url: '/inventory/sales/' + orderId, method: 'get' })
}

// 取消销售单
export function cancelSales(orderId) {
  return request({ url: '/inventory/sales/' + orderId, method: 'delete' })
}
