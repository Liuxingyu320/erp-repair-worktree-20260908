import request from '@/utils/request'
import { isCookiePreferredSession } from '@/utils/sessionMode'

// 登录方法
export function login(username, password, code, uuid) {
  return request({
    url: '/auth/login',
    headers: {
      isToken: false,
      repeatSubmit: false,
      ...(isCookiePreferredSession() ? { 'X-ERP-Session-Preference': 'cookie' } : {})
    },
    method: 'post',
    data: { username, password, code, uuid }
  })
}

// 注册方法
export function register(data) {
  return request({
    url: '/auth/register',
    headers: {
      isToken: false
    },
    method: 'post',
    data: data
  })
}

// 获取注册密码策略
export function getPasswordPolicy() {
  return request({
    url: '/auth/passwordPolicy',
    headers: {
      isToken: false
    },
    method: 'get',
    silentError: true
  })
}

// 解锁屏幕
export function unlockScreen(password) {
  return request({
    url: '/auth/unlockscreen',
    headers: {
      repeatSubmit: false
    },
    method: 'post',
    data: { password }
  })
}

// 刷新方法
export function refreshToken() {
  return request({
    url: '/auth/refresh',
    method: 'post'
  })
}

// 获取用户详细信息
export function getInfo() {
  return request({
    url: '/system/user/getInfo',
    method: 'get'
  })
}

// 退出方法
export function logout(authToken, options = {}) {
  const authorizedRequest = arguments.length > 0
    ? {
      headers: {
        isToken: false,
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {})
      }
    }
    : {}
  return request({
    url: '/auth/logout',
    method: 'delete',
    silentError: true,
    suppressSessionExpiry: true,
    ...authorizedRequest,
    ...(options.signal ? { signal: options.signal } : {})
  })
}

// 获取验证码
export function getCodeImg() {
  return request({
    url: '/code',
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000,
    silentError: true
  })
}
