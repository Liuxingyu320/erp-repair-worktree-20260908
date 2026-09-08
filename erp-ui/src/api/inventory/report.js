import request from '@/utils/request'

// 库存报表汇总
export function getReportSummary(query) {
  return request({ url: '/inventory/report/summary', method: 'get', params: query })
}

// 低库存预警列表
export function listStockWarning(query) {
  return request({ url: '/inventory/report/stock-warning', method: 'get', params: query })
}
