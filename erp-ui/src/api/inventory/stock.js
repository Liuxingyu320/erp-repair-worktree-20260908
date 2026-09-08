import request from '@/utils/request'

// 库存列表
export function listStock(query, config) {
  return request(Object.assign({ url: '/inventory/stock/list', method: 'get', params: query }, config))
}

// 库存汇总
export function getStockSummary(query) {
  return request({ url: '/inventory/stock/summary', method: 'get', params: query })
}

// 库存详情
export function getStock(stockId) {
  return request({ url: '/inventory/stock/' + stockId, method: 'get' })
}

// 库存调整
export function adjustStock(data) {
  return request({ url: '/inventory/stock/adjust', method: 'post', data: data })
}

// 库存变动日志
export function listStockLog(query) {
  return request({ url: '/inventory/stock/log/list', method: 'get', params: query })
}
