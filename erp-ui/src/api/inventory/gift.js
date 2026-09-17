import request from '@/utils/request'

export function listGift(query, config) {
  return request(Object.assign({ url: '/inventory/gift/list', method: 'get', params: query }, config))
}

export function getGift(giftId, options) {
  return request({ url: '/inventory/gift/' + giftId, method: 'get', silentError: options && options.silentError === true })
}

export function addGift(data) {
  return request({ url: '/inventory/gift', method: 'post', data })
}

export function updateGift(data) {
  return request({ url: '/inventory/gift/update', method: 'post', data })
}

export function delGift(giftIds) {
  return request({ url: '/inventory/gift/' + giftIds, method: 'delete' })
}

export function importGiftData(data) {
  return request({ url: '/inventory/gift/importData', method: 'post', headers: { 'Content-Type': 'multipart/form-data' }, data })
}

export function importGiftTemplate() {
  return request({ url: '/inventory/gift/importTemplate', method: 'post', responseType: 'blob' })
}

export function giftCategoryTree(config) {
  return request(Object.assign({ url: '/inventory/gift/category/tree', method: 'get' }, config))
}

export function getGiftCategory(categoryId) {
  return request({ url: '/inventory/gift/category/' + categoryId, method: 'get' })
}

export function addGiftCategory(data) {
  return request({ url: '/inventory/gift/category', method: 'post', data })
}

export function updateGiftCategory(data) {
  return request({ url: '/inventory/gift/category/update', method: 'post', data })
}

export function delGiftCategory(categoryId) {
  return request({ url: '/inventory/gift/category/' + categoryId, method: 'delete' })
}
