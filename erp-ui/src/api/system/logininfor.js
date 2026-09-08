import request from '@/utils/request'

// 查询登录日志列表
export function list(query) {
  return request({
    url: '/system/logininfor/list',
    method: 'get',
    params: query
  })
}

// 删除登录日志
export function delLogininfor(infoId) {
  return request({
    url: '/system/logininfor/' + infoId,
    method: 'delete'
  })
}

// 查询用户当前锁定状态
export function getLoginLockState(userName) {
  return request({
    url: '/system/logininfor/lock-state',
    method: 'get',
    params: { userName }
  })
}

// 解锁用户登录状态（改变状态只允许 POST）
export function unlockLogininfor(userName) {
  return request({
    url: '/system/logininfor/' + encodeURIComponent(userName) + '/unlock',
    method: 'post'
  })
}
