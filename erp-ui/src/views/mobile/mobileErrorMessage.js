'use strict'

const TECHNICAL_ERROR_PATTERN =
  /(?:sql(?:syntax|state)?|exception|stack\s*trace|java\.|org\.spring|com\.erp|\/users\/|\/var\/|\.java:\d+|\bat\s+\w+[.$]|network error|failed to fetch|econn(?:reset|refused|aborted)|timeout of \d+ms)/i
const AUTH_ERROR_PATTERN =
  /(?:(?:令牌|token).*(?:为空|无效|过期)|(?:会话|登录状态).*(?:失效|无效|过期)|无效的会话)/i
const PERMISSION_ERROR_PATTERN =
  /(?:没有权限|无权限|无权|未授权|notpermission|forbidden)/i
const TRANSFER_ITEM_SCOPE_ERROR_PATTERN = /无权调拨该物料/i
const TRANSFER_SOURCE_RELATION_ERROR_PATTERN =
  /(?:当前门店不允许向该仓库要货|补货来源仓库物料范围无效|补货供货关系无效)/i

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
    error && error.status,
    error && error.code
  ]
  const status = values.map(Number).find(value => Number.isInteger(value) && value >= 100 && value <= 599)
  return status || 0
}

function mobileErrorMessage(error, fallback) {
  const defaultMessage = String(fallback || '操作失败，请稍后重试').trim()
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
  if (TRANSFER_ITEM_SCOPE_ERROR_PATTERN.test(message)) {
    return '所选商品不属于当前门店可要货范围，请重新选择'
  }
  if (TRANSFER_SOURCE_RELATION_ERROR_PATTERN.test(message)) {
    return message
  }
  if (status === 403 || PERMISSION_ERROR_PATTERN.test(message)) {
    return '当前账号无权执行此操作'
  }
  if (!message || status >= 500 || message.length > 120 || TECHNICAL_ERROR_PATTERN.test(message)) {
    return defaultMessage
  }
  return message
}

module.exports = {
  mobileErrorMessage
}
