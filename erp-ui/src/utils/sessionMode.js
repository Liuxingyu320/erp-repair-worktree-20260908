import { Capacitor } from '@capacitor/core'
import Cookies from 'js-cookie'

const SESSION_STATUS_KEY = 'erp-web-session-status'
const SESSION_CHANNEL_NAME = 'erp-web-session'
const COOKIE_MODE = 'cookie'
const VALID_STATUS = ['unknown', 'authenticated', 'anonymous']
const statusListeners = new Set()

const isBrowser = () => typeof window !== 'undefined'

export function isCookiePreferredSession() {
  return !Capacitor.isNativePlatform() &&
    String(process.env.VUE_APP_WEB_SESSION_MODE || 'bearer').toLowerCase() === COOKIE_MODE
}

export function shouldUseSessionCredentials() {
  return isCookiePreferredSession()
}

export function buildSessionAuthHeaders(token) {
  if (isCookiePreferredSession()) {
    const csrfToken = Cookies.get('XSRF-TOKEN')
    return csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}
  }
  if (!token) return {}
  return { Authorization: `Bearer ${token}` }
}

function deleteHeader(headers, name) {
  if (headers && typeof headers.delete === 'function') {
    headers.delete(name)
    return
  }
  Object.keys(headers || {}).forEach(key => {
    if (key.toLowerCase() === name.toLowerCase()) {
      delete headers[key]
    }
  })
}

function setHeader(headers, name, value) {
  if (headers && typeof headers.set === 'function') {
    headers.set(name, value)
  } else {
    headers[name] = value
  }
}

// 原位刷新认证头，保证 el-upload 在 before-upload 同一事件循环中仍读取
// 它已持有的 headers 对象；同时阻止调用方在 Cookie 会话中手工夹带 Bearer。
export function applySessionAuthHeaders(headers, token) {
  const target = headers || {}
  if (isCookiePreferredSession()) {
    deleteHeader(target, 'Authorization')
    deleteHeader(target, 'X-XSRF-TOKEN')
    const csrfToken = Cookies.get('XSRF-TOKEN')
    if (csrfToken) setHeader(target, 'X-XSRF-TOKEN', csrfToken)
    return target
  }

  deleteHeader(target, 'X-XSRF-TOKEN')
  deleteHeader(target, 'Authorization')
  if (token) setHeader(target, 'Authorization', `Bearer ${token}`)
  return target
}

export function getWebSessionStatus() {
  if (!isCookiePreferredSession()) return 'anonymous'
  if (!isBrowser()) return 'unknown'
  try {
    const storage = window.sessionStorage
    if (!storage || typeof storage.getItem !== 'function') return 'unknown'
    const status = storage.getItem(SESSION_STATUS_KEY)
    return VALID_STATUS.includes(status) ? status : 'unknown'
  } catch (e) {
    // Cookie mode must stay in unknown discovery when Web Storage is unavailable.
    return 'unknown'
  }
}

export function hasSessionCandidate(token) {
  return !!token || (isCookiePreferredSession() && getWebSessionStatus() !== 'anonymous')
}

export function setWebSessionStatus(status, broadcast = true) {
  if (!isCookiePreferredSession() || !VALID_STATUS.includes(status) || !isBrowser()) return
  try {
    window.sessionStorage.setItem(SESSION_STATUS_KEY, status)
  } catch (e) {
    // 浏览器禁用存储时保持内存外的 unknown 探测，不退回持久化 JWT。
  }
  if (broadcast && sessionChannel) {
    sessionChannel.postMessage({ type: 'session-status', status })
  }
}

export function subscribeWebSessionStatus(listener) {
  if (typeof listener !== 'function') return () => {}
  statusListeners.add(listener)
  return () => statusListeners.delete(listener)
}

function notifyStatusListeners(status) {
  statusListeners.forEach(listener => {
    try {
      listener(status)
    } catch (e) {
      // 一个标签内的可选监听器失败不能阻断其他会话清理监听器。
    }
  })
}

let sessionChannel = null
if (isBrowser() && typeof window.BroadcastChannel === 'function') {
  sessionChannel = new window.BroadcastChannel(SESSION_CHANNEL_NAME)
  sessionChannel.onmessage = event => {
    const status = event && event.data && event.data.status
    if (event && event.data && event.data.type === 'session-status' && VALID_STATUS.includes(status)) {
      setWebSessionStatus(status, false)
      notifyStatusListeners(status)
    }
  }
}
