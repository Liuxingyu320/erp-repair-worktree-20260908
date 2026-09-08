import request from '@/utils/request'

export function getMobileOptions(type, query, config) {
  return request(Object.assign({
    url: '/inventory/mobile/options/' + type,
    method: 'get',
    params: query
  }, config))
}

export function getMobileWorkbenchSummary(query) {
  return request({
    url: '/inventory/mobile/workbench/summary',
    method: 'get',
    params: query
  })
}

export function listTransferApprovalTodos(query) {
  return request({
    url: '/inventory/mobile/transfer-approval/todo',
    method: 'get',
    params: query
  })
}
