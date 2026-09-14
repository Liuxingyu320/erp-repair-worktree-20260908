const LONG_MAX = '9223372036854775807'
function exactId(value, zero = false) {
  if (typeof value === 'number' && !Number.isSafeInteger(value)) throw Error('编号精度无法确认，请刷新后重试')
  const text = String(value == null ? '' : value)
  if (!(zero ? /^(0|[1-9]\d{0,18})$/ : /^[1-9]\d{0,18}$/).test(text) || text.length === 19 && text > LONG_MAX) throw Error('编号或版本无效，请刷新后重试')
  return text
}
function decimal(value, signed = false) {
  const text = String(value == null ? '' : value).trim()
  if (!(signed ? /^-?(0|[1-9]\d*)(\.\d{1,6})?$/ : /^(0|[1-9]\d*)(\.\d{1,6})?$/).test(text)) throw Error('请填写最多六位小数的数值')
  return text
}
function scaled(value) {
  const text = decimal(value, true), negative = text.startsWith('-'), parts = text.replace('-', '').split('.')
  const number = BigInt(parts[0]) * BigInt(1000000) + BigInt((parts[1] || '').padEnd(6, '0'))
  return negative ? -number : number
}
function unitLabel(unit) { return { DAYS: '天', HOURS: '小时', MINUTES: '分钟' }[unit] || '' }
function formatUnits(value, unit, minutesPerDay) {
  if (value == null || !unitLabel(unit)) return '待核对'
  try {
    if (!/^-?\d+$/.test(String(value)) || typeof value === 'number' && !Number.isSafeInteger(value)) return '待核对'
    const denominator = unit === 'DAYS' ? scaled(minutesPerDay) : BigInt(unit === 'HOURS' ? 60000000 : 1000000)
    if (denominator <= BigInt(0)) return '待核对'
    const numerator = BigInt(String(value)) * BigInt(1000000), quotient = numerator / denominator
    const sign = quotient < BigInt(0) ? '-' : '', positive = quotient < BigInt(0) ? -quotient : quotient
    const fraction = String(positive % BigInt(1000000)).padStart(6, '0').replace(/0+$/, '')
    return (numerator % denominator === BigInt(0) ? '' : '约 ') + sign + String(positive / BigInt(1000000)) + (fraction ? '.' + fraction : '') + ' ' + unitLabel(unit)
  } catch (_) { return '待核对' }
}
function dataOf(response) {
  if (!response || response.code != null && Number(response.code) !== 200 || !Object.hasOwn(response, 'data') || response.data == null) throw Error('服务端结果无法确认，请重试读取')
  return response.data
}
function listOf(response) { const rows = dataOf(response); if (!Array.isArray(rows)) throw Error('列表结果无法确认'); return rows }
function clone(value) { return JSON.parse(JSON.stringify(value)) }
function requestId() {
  const crypto = typeof window !== 'undefined' && window.crypto
  if (!crypto) throw Error('当前环境无法生成安全请求标识')
  if (typeof crypto.randomUUID === 'function') return 'leave-balance:' + crypto.randomUUID()
  if (typeof crypto.getRandomValues !== 'function') throw Error('当前环境无法生成安全请求标识')
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return 'leave-balance:' + [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-')
}
function permission(vm, action) { return !!(vm.$auth && vm.$auth.hasPermi && vm.$auth.hasPermi('oa:attendance:leave:balance:' + action)) }
function attemptKey(scope, action) { return 'erp:leave-balance:attempt:' + encodeURIComponent(scope) + ':' + action }
function persistAttempt(scope, action, body) {
  const storage = typeof sessionStorage === 'undefined' ? null : sessionStorage
  if (!storage) throw Error('无法保存操作凭据，请检查浏览器存储后重试')
  if (body.scope != null && body.scope !== scope) throw Error('账号或组织已变化，请切回原操作页面核对')
  const key = attemptKey(scope, action), value = JSON.stringify({ ...body, scope })
  storage.setItem(key, value); if (storage.getItem(key) !== value) throw Error('操作凭据保存失败')
}
function restoreAttempt(scope, action) {
  try { const raw = sessionStorage.getItem(attemptKey(scope, action)); if (!raw) return null; const value = JSON.parse(raw); return value && (value.scope == null || value.scope === scope) ? { ...value, scope } : null } catch (_) { return null }
}
function clearAttempt(scope, action) { try { sessionStorage.removeItem(attemptKey(scope, action)) } catch (_) { /* Successful request remains safely repeatable. */ } }
function definiteRejection(error) {
  const response = error && error.response, payload = response && response.data
  // These mutation controllers return the service result directly. A structured business
  // rejection precedes a commit; generic/internal, transport and unreadable results stay unknown.
  return !!(response && response.status === 200 && payload && payload.code != null && Number(payload.code) !== 200 && payload.msg &&
    payload.msg !== '系统处理失败，请稍后重试或联系管理员')
}
module.exports = { exactId, decimal, scaled, unitLabel, formatUnits, dataOf, listOf, clone, requestId, permission, persistAttempt, restoreAttempt, clearAttempt, definiteRejection }
