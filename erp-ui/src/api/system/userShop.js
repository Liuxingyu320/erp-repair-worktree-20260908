import request from '@/utils/request'

// 查询当前数据范围内的最小用户候选列表
export function listUserShopUsers(query) {
  return request({
    url: '/system/user/shop/users',
    method: 'get',
    params: query
  })
}

// 查询可配置店铺树
export function shopTree(config) {
  return request(Object.assign({
    url: '/system/user/shop/tree',
    method: 'get'
  }, config))
}

// 查询用户已配置店铺
export function getUserShop(userId) {
  return request({
    url: '/system/user/shop/' + userId,
    method: 'get'
  })
}

// 批量查询用户已配置店铺
export function batchUserShop(userIds) {
  return request({
    url: '/system/user/shop/batch',
    method: 'get',
    params: { userIds: (userIds || []).join(',') }
  })
}

// 预览用户组织授权差异并取得并发版本
export function previewUserShop(userId, shopDeptIds) {
  return request({
    url: '/system/user/shop/' + userId + '/preview',
    method: 'post',
    data: { shopDeptIds }
  })
}

// 保存已预览且版本未变化的用户组织授权
export function updateUserShop(userId, shopDeptIds, scopeVersion) {
  return request({
    url: '/system/user/shop/' + userId,
    method: 'put',
    data: { shopDeptIds, scopeVersion }
  })
}
