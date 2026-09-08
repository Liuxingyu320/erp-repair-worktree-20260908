'use strict'

const TECHNICAL_ERROR_PATTERN =
  /(?:sql(?:syntax|state)?|exception|stack\s*trace|java\.|org\.spring|com\.erp|\/users\/|\/var\/|\.java:\d+|\bat\s+\w+[.$]|network error|failed to fetch)/i
const AUTH_ERROR_PATTERN =
  /(?:令牌|token).*(?:为空|无效|过期)|登录状态.*(?:失效|过期)/i

function errorBody(error) {
  if (!error || typeof error !== 'object') return {}
  if (error.response && error.response.data && typeof error.response.data === 'object') {
    return error.response.data
  }
  if (error.data && typeof error.data === 'object') return error.data
  return error
}

function errorStatus(error, body) {
  const values = [
    error && error.response && error.response.status,
    body && body.code,
    error && error.code
  ]
  const status = values.map(Number).find(value => Number.isInteger(value) && value >= 100)
  return status || 0
}

function mobileHrErrorMessage(error, fallback) {
  const defaultMessage = fallback || '数据加载失败，请稍后重试'
  if (!error) return defaultMessage

  const body = errorBody(error)
  const candidate = typeof error === 'string'
    ? error
    : body.msg || body.message || error.msg || error.message
  const message = String(candidate || '').replace(/\s+/g, ' ').trim()
  const status = errorStatus(error, body)

  if (status === 401 || AUTH_ERROR_PATTERN.test(message)) {
    return '登录状态已失效，请重新登录'
  }
  if (status === 403) {
    return '当前账号无权执行此操作'
  }
  if (status >= 500) {
    return '服务暂时不可用，请稍后重试'
  }
  if (!message) return defaultMessage
  if (message.length > 120 || TECHNICAL_ERROR_PATTERN.test(message)) {
    return defaultMessage
  }
  return message
}

module.exports = {
  mobileHrErrorMessage
}
