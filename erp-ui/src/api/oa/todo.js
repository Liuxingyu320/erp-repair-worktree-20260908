import request from '@/utils/request'

export function getTodoSummary() {
  return request({
    url: '/oa/todo/summary',
    method: 'get',
    silentError: true
  })
}

export function listTodos(params) {
  return request({
    url: '/oa/todo/list',
    method: 'get',
    params,
    silentError: true
  })
}
