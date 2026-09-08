import request from '@/utils/request'

// 查询供应商列表
export function listSupplier(query, config) {
  return request(Object.assign({ url: '/inventory/supplier/list', method: 'get', params: query }, config))
}

// 查询供应商详情
export function getSupplier(supplierId) {
  return request({ url: '/inventory/supplier/' + supplierId, method: 'get' })
}

// 查询供应商供货商品
export function getSupplierProducts(supplierId, config) {
  return request(Object.assign({ url: '/inventory/supplier/' + supplierId + '/products', method: 'get' }, config))
}

// 新增供应商
export function addSupplier(data) {
  return request({ url: '/inventory/supplier', method: 'post', data: data })
}

// 修改供应商
export function updateSupplier(data) {
  return request({ url: '/inventory/supplier/update', method: 'post', data: data })
}

// 删除供应商
export function delSupplier(supplierIds) {
  return request({ url: '/inventory/supplier/' + supplierIds, method: 'delete' })
}
