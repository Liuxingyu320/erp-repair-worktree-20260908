import request from '@/utils/request'

export function getMobileProfile() {
  return request({
    url: '/system/mobile/profile',
    method: 'get'
  })
}

export function getMobileWarehouseOptions(query, config) {
  return request(Object.assign({
    url: '/system/mobile/options/warehouses',
    method: 'get',
    params: query
  }, config))
}
