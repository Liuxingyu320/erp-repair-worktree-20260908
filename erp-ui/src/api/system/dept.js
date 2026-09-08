import request from '@/utils/request'

// 查询部门列表
export function listDept(query, config) {
  return request(Object.assign({
    url: '/system/dept/list',
    method: 'get',
    params: query
  }, config))
}

// 查询当前数据范围内可设置为部门负责人的启用在职员工
export function listDeptLeaderOptions(query) {
  return request({
    url: '/system/dept/leader-options',
    method: 'get',
    params: query
  })
}

// 查询店铺/仓库上下文树（选店页面专用）
export function listShopTree(config) {
  return request(Object.assign({
    url: '/system/dept/shop-tree',
    method: 'get'
  }, config))
}

// 查询启用仓库列表（业务页面选择仓库目标）
export function listWarehouseDept(query, config) {
  return request(Object.assign({
    url: '/system/dept/warehouse-list',
    method: 'get',
    params: query
  }, config))
}

// 查询当前业务组织范围内的可见门店列表（仓库库存筛选使用）
export function listVisibleStoreDept(query) {
  return request({
    url: '/system/dept/visible-store-list',
    method: 'get',
    params: query
  })
}

// 查询部门列表（排除节点）
export function listDeptExcludeChild(deptId) {
  return request({
    url: '/system/dept/list/exclude/' + deptId,
    method: 'get'
  })
}

// 查询部门详细
export function getDept(deptId) {
  return request({
    url: '/system/dept/' + deptId,
    method: 'get'
  })
}

// 新增部门
export function addDept(data) {
  return request({
    url: '/system/dept',
    method: 'post',
    data: data
  })
}

// 修改部门
export function updateDept(data) {
  return request({
    url: '/system/dept',
    method: 'put',
    data: data
  })
}

// 保存部门排序
export function updateDeptSort(data) {
  return request({
    url: '/system/dept/updateSort',
    method: 'put',
    data: data,
    silentError: true
  })
}

// 删除部门
export function delDept(deptId) {
  return request({
    url: '/system/dept/' + deptId,
    method: 'delete'
  })
}
