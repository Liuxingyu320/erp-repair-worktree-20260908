import request from '@/utils/request'

// 库存报表汇总
export function getReportSummary(query, options) {
  return request({ url: '/inventory/report/summary', method: 'get', params: query, silentError: options && options.silentError === true })
}

// 低库存预警列表
export function listStockWarning(query, options) {
  return request({ url: '/inventory/report/stock-warning', method: 'get', params: query, silentError: options && options.silentError === true })
}

// 仅返回当前组织报表允许查看的物料标识；不依赖主数据管理权限。
export function listReportItemOptions(query) {
  return request({ url: '/inventory/report/item-options', method: 'get', params: query, silentError: true })
}
