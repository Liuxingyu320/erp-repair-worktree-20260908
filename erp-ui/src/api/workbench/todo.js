import request from '@/utils/request'

export const PROVIDERS = Object.freeze({
  approval: '/approval/todo',
  inventory: '/inventory/todo',
  oa: '/oa/todo',
  system: '/system/todo'
})

function fetchFromProvider(source, endpoint, params) {
  const provider = PROVIDERS[source]
  if (!provider) {
    return Promise.reject(new Error(`Unknown todo provider: ${source}`))
  }
  return request({
    url: `${provider}/${endpoint}`,
    method: 'get',
    params,
    silentError: true
  })
}

export function fetchTodoSummary(source, params) {
  return fetchFromProvider(source, 'summary', params)
}

export function fetchTodoList(source, params) {
  return fetchFromProvider(source, 'list', params)
}
