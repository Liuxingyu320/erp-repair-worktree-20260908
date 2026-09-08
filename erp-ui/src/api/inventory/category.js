import request from '@/utils/request'

// 查询分类树
export function categoryTree(config) {
  return request(Object.assign({ url: '/inventory/category/tree', method: 'get' }, config))
}

// 查询分类详情
export function getCategory(categoryId) {
  return request({ url: '/inventory/category/' + categoryId, method: 'get' })
}

// 新增分类
export function addCategory(data) {
  return request({ url: '/inventory/category', method: 'post', data: data })
}

// 修改分类
export function updateCategory(data) {
  return request({ url: '/inventory/category/update', method: 'post', data: data })
}

// 删除分类
export function delCategory(categoryId) {
  return request({ url: '/inventory/category/' + categoryId, method: 'delete' })
}
