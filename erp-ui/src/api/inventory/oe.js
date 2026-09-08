import request from '@/utils/request'

export function listOe(query, config) {
  return request(Object.assign({ url: '/inventory/oe/list', method: 'get', params: query }, config))
}

export function getOe(oeItemId) {
  return request({ url: '/inventory/oe/' + oeItemId, method: 'get' })
}

export function getOePurchaseReferencePolicy() {
  return request({ url: '/inventory/oe/purchase-reference/policy', method: 'get' })
}

export function addOe(data) {
  return request({ url: '/inventory/oe', method: 'post', data })
}

export function updateOe(data) {
  return request({ url: '/inventory/oe/update', method: 'post', data })
}

export function delOe(oeItemIds) {
  return request({ url: '/inventory/oe/' + oeItemIds, method: 'delete' })
}

export function importOeData(data) {
  return request({ url: '/inventory/oe/importData', method: 'post', headers: { 'Content-Type': 'multipart/form-data' }, data })
}

export function importOeTemplate() {
  return request({ url: '/inventory/oe/importTemplate', method: 'post', responseType: 'blob' })
}

export function oeCategoryTree(config) {
  return request(Object.assign({ url: '/inventory/oe/category/tree', method: 'get' }, config))
}

export function getOeCategory(categoryId) {
  return request({ url: '/inventory/oe/category/' + categoryId, method: 'get' })
}

export function addOeCategory(data) {
  return request({ url: '/inventory/oe/category', method: 'post', data })
}

export function updateOeCategory(data) {
  return request({ url: '/inventory/oe/category/update', method: 'post', data })
}

export function delOeCategory(categoryId) {
  return request({ url: '/inventory/oe/category/' + categoryId, method: 'delete' })
}
