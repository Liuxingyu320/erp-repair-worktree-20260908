import request from '@/utils/request'

// 查询薪资方案列表
export function listSalaryScheme(query) {
  return request({
    url: '/system/salaryConfig/list',
    method: 'get',
    params: query
  })
}

// 查询薪资方案详细
export function getSalaryScheme(schemeId) {
  return request({
    url: '/system/salaryConfig/' + schemeId,
    method: 'get'
  })
}

// 查询可用薪资方案选项
export function salarySchemeOptions(config) {
  return request({
    url: '/system/salaryConfig/options',
    method: 'get',
    ...config
  })
}

// 查询薪资配置可绑定员工
export function salaryUserOptions(query) {
  return request({
    url: '/system/salaryConfig/users',
    method: 'get',
    params: query
  })
}

// 查询薪资配置可用核算门店树
export function salaryShopTree() {
  return request({
    url: '/system/salaryConfig/shopTree',
    method: 'get'
  })
}

// 新增薪资方案
export function addSalaryScheme(data) {
  return request({
    url: '/system/salaryConfig',
    method: 'post',
    data
  })
}

// 修改薪资方案
export function updateSalaryScheme(data) {
  return request({
    url: '/system/salaryConfig',
    method: 'put',
    data
  })
}

// 删除薪资方案
export function delSalaryScheme(schemeId, params) {
  return request({
    url: '/system/salaryConfig/' + schemeId,
    method: 'delete',
    params
  })
}

// 保存前只读影响预览
export function salaryImpactPreview(data) {
  return request({
    url: '/system/salaryConfig/impact-preview',
    method: 'post',
    data
  })
}

// 查询薪资方案修订历史
export function listSalaryRevisions(schemeId) {
  return request({
    url: '/system/salaryConfig/' + schemeId + '/revisions',
    method: 'get'
  })
}

// 将历史修订恢复成一个新版本
export function rollbackSalaryRevision(revisionId, data) {
  return request({
    url: '/system/salaryConfig/revision/' + revisionId + '/rollback',
    method: 'post',
    data
  })
}

// 查询薪资方案档位
export function listSalaryItems(schemeId) {
  return request({
    url: '/system/salaryConfig/' + schemeId + '/items',
    method: 'get'
  })
}

// 新增薪资档位
export function addSalaryItem(data) {
  return request({
    url: '/system/salaryConfig/item',
    method: 'post',
    data
  })
}

// 修改薪资档位
export function updateSalaryItem(data) {
  return request({
    url: '/system/salaryConfig/item',
    method: 'put',
    data
  })
}

// 删除薪资档位
export function delSalaryItem(itemId, params) {
  return request({
    url: '/system/salaryConfig/item/' + itemId,
    method: 'delete',
    params
  })
}

// 查询角色薪资绑定
export function getRoleSalary(roleId) {
  return request({
    url: '/system/salaryConfig/role/' + roleId,
    method: 'get'
  })
}

// 批量查询角色薪资绑定
export function getRoleSalaryBatch(roleIds) {
  return request({
    url: '/system/salaryConfig/role/batch',
    method: 'get',
    params: { roleIds: (roleIds || []).join(',') }
  })
}

// 保存角色薪资绑定
export function saveRoleSalary(roleId, data) {
  return request({
    url: '/system/salaryConfig/role/' + roleId,
    method: 'put',
    data
  })
}

// 查询用户薪资绑定
export function getUserSalary(userId) {
  return request({
    url: '/system/salaryConfig/user/' + userId,
    method: 'get'
  })
}

// 批量查询用户薪资绑定
export function getUserSalaryBatch(userIds) {
  return request({
    url: '/system/salaryConfig/user/batch',
    method: 'get',
    params: { userIds: (userIds || []).join(',') }
  })
}

// 保存用户薪资绑定
export function saveUserSalary(userId, data) {
  return request({
    url: '/system/salaryConfig/user/' + userId,
    method: 'put',
    data
  })
}
