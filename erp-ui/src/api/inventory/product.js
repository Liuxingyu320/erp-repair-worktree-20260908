import request from '@/utils/request'

// 查询商品列表
export function listProduct(query, config) {
  return request(Object.assign({ url: '/inventory/product/list', method: 'get', params: query }, config))
}

// 查询商品详情
export function getProduct(productId) {
  return request({ url: '/inventory/product/' + productId, method: 'get' })
}

// 新增商品
export function addProduct(data) {
  return request({ url: '/inventory/product', method: 'post', data: data })
}

// 修改商品
export function updateProduct(data) {
  return request({ url: '/inventory/product/update', method: 'post', data: data })
}

// 删除商品
export function delProduct(productIds) {
  return request({ url: '/inventory/product/' + productIds, method: 'delete' })
}

// 导入商品
export function importData(data) {
  return request({ url: '/inventory/product/importData', method: 'post', headers: { 'Content-Type': 'multipart/form-data' }, data: data })
}

// 下载导入模板
export function importTemplate() {
  return request({ url: '/inventory/product/importTemplate', method: 'post', responseType: 'blob' })
}
