import request from '@/utils/request'

// 查询操作日志列表
export function list(query) {
  return request({
    url: '/system/operlog/list',
    method: 'get',
    params: query
  })
}

// 查询操作日志脱敏详情
export function getOperlog(operId) {
  return request({
    url: '/system/operlog/' + operId,
    method: 'get'
  })
}

// 删除操作日志
export function delOperlog(operId) {
  return request({
    url: '/system/operlog/' + operId,
    method: 'delete'
  })
}
