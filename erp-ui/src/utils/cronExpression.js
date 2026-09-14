// Bounded, local Quartz-style preview. Server CronUtils remains the save authority.
const KEYS = ['second', 'min', 'hour', 'day', 'month', 'week', 'year']
const LABELS = ['秒', '分钟', '小时', '日', '月', '星期', '年']
const BOUNDS = [[0, 59], [0, 59], [0, 23], [1, 31], [1, 12], [1, 7], [1970, 2199]]
const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']
const WEEKS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']
function normalizeNames(text, index) {
  const names = index === 4 ? MONTHS : index === 5 ? WEEKS : []
  return text.replace(/[A-Z]{3}/g, name => names.includes(name) ? String(names.indexOf(name) + 1) : name)
}
function integer(text, min, max) {
  if (!/^\d+$/.test(String(text))) throw new Error('必须使用整数')
  const value = Number(text)
  if (!Number.isSafeInteger(value) || value < min || value > max) throw new Error(`数值应在 ${min} 至 ${max} 之间`)
  return value
}
function expandField(rule, min, max) {
  if (!Number.isSafeInteger(min) || !Number.isSafeInteger(max) || max < min || max - min > 1000) throw new Error('预览范围超限')
  if (typeof rule !== 'string' || rule.length > 256) throw new Error('规则无效')
  const values = new Set()
  for (const item of rule.split(',')) {
    const pair = item.split('/')
    if (pair.length > 2) throw new Error('步长格式无效')
    const step = pair.length === 2 ? integer(pair[1], 1, 2147483647) : 1
    const range = pair[0].split('-')
    let start, end
    if (pair[0] === '*') { start = min; end = max }
    else if (range.length === 2) { start = integer(range[0], min, max); end = integer(range[1], min, max) }
    else if (range.length === 1) { start = integer(range[0], min, max); end = pair.length === 2 ? max : start }
    else throw new Error('范围格式无效')
    // Quartz wrap-around ranges are preserved but not approximated by this preview.
    if (end < start) throw new Error('跨界范围请使用文本编辑并以服务端结果为准')
    const count = Math.floor((end - start) / step) + 1
    if (!Number.isSafeInteger(count) || count > 1001) throw new Error('预览枚举超限')
    for (let i = 0; i < count; i++) values.add(start + i * step)
  }
  return [...values].sort((a, b) => a - b)
}
function parseCronExpression(expression) {
  const original = typeof expression === 'string' ? expression : ''
  const fields = original.trim().split(/\s+/)
  if (original.length > 256 || ![6, 7].includes(fields.length)) return { valid: false, error: 'Cron 表达式应为 6 或 7 个字段', original }
  const result = { valid: true, error: '', original, fields: [...fields], values: {}, model: {}, editable: true, previewable: true }
  if (fields.length === 6) fields.push('')
  for (let i = 0; i < fields.length; i++) {
    const raw = fields[i], canonical = normalizeNames(raw.toUpperCase(), i)
    result.model[KEYS[i]] = raw
    try {
      if (i === 6 && raw === '') { result.values.year = null; continue }
      if (canonical === '?' && [3, 5].includes(i)) { result.values[KEYS[i]] = null; continue }
      if (i === 3 && /^(L|LW|L-\d+(?:W)?|\d+W)$/.test(canonical)) {
        if (/^L-/.test(canonical)) integer(canonical.match(/\d+/)[0], 0, 30)
        else if (/^\d/.test(canonical)) integer(canonical.slice(0, -1), 1, 31)
        result.values.day = canonical
        if (canonical !== 'L' && !/^\d+W$/.test(canonical)) result.editable = false
        continue
      }
      if (i === 5 && /^(\d+#[1-5]|\d+L|L)$/.test(canonical)) {
        if (canonical !== 'L') integer(canonical.match(/^\d+/)[0], 1, 7)
        result.values.week = canonical === 'L' ? [7] : canonical
        if (raw !== canonical || canonical === 'L') result.editable = false
        continue
      }
      if (raw !== canonical || /\*\//.test(raw) || (/[-/]/.test(raw) && raw.includes(',')) || /-.*\//.test(raw)) result.editable = false
      const [min, max] = BOUNDS[i]
      const simpleRange = /^(\d+)-(\d+)$/.exec(canonical)
      const simpleStep = /^(\d+)\/(\d+)$/.exec(canonical)
      const visualMax = i === 6 ? 2099 : max
      if (simpleRange && (Number(simpleRange[1]) >= visualMax || Number(simpleRange[2]) <= Number(simpleRange[1]))) result.editable = false
      if (simpleStep && (i === 5 || Number(simpleStep[1]) >= visualMax || Number(simpleStep[2]) > visualMax - Number(simpleStep[1]))) result.editable = false
      // Preserve legal historical wrap ranges; never silently rewrite them.
      const wrap = /^(\d+)-(\d+)$/.exec(canonical)
      if (wrap && Number(wrap[1]) > Number(wrap[2])) {
        integer(wrap[1], min, max); integer(wrap[2], min, max)
        result.editable = false; result.previewable = false; result.values[KEYS[i]] = null; continue
      }
      result.values[KEYS[i]] = expandField(canonical, min, max)
      if (i === 6 && result.values.year.some(year => year > 2099)) result.editable = false
    } catch (error) { return { ...result, valid: false, error: `${LABELS[i]}字段：${error.message}` } }
  }
  if ((fields[3] === '?') === (fields[5] === '?')) return { ...result, valid: false, error: '日和星期应且仅应有一个使用 ?' }
  return result
}
function serializeCronModel(model) { return KEYS.map(key => model[key] == null ? '' : String(model[key])).join(' ').trim() }
function daysInMonth(year, month) { return new Date(year, month, 0).getDate() }
function nearestWeekday(year, month, day, last) {
  const dow = new Date(year, month - 1, day).getDay()
  if (dow === 6) return day === 1 ? 3 : day - 1
  if (dow === 0) return day === last ? day - 2 : day + 1
  return day
}
function matchesDay(values, year, month, day) {
  const last = daysInMonth(year, month), dow = new Date(year, month - 1, day).getDay() + 1
  if (Array.isArray(values.day)) return values.day.includes(day)
  if (typeof values.day === 'string') {
    const rule = values.day
    let expected = rule.startsWith('L') ? last - (Number((rule.match(/\d+/) || [0])[0])) : Number(rule.slice(0, -1))
    if (expected < 1 || expected > last) return false
    if (rule.endsWith('W')) expected = nearestWeekday(year, month, expected, last)
    return day === expected
  }
  if (Array.isArray(values.week)) return values.week.includes(dow)
  if (typeof values.week === 'string') {
    const [weekday, occurrence] = values.week.split('#').map(Number)
    if (values.week.endsWith('L')) return dow === Number(values.week.slice(0, -1)) && day + 7 > last
    return dow === weekday && Math.floor((day - 1) / 7) + 1 === occurrence
  }
  return true
}
function previewCronExpression(expression, from = new Date(), count = 5) {
  const parsed = parseCronExpression(expression)
  if (!parsed.valid) return { times: [], error: parsed.error }
  if (!parsed.previewable) return { times: [], error: '此规则保留原文；暂不支持本地预览，请以服务端执行计划为准' }
  if (!(from instanceof Date) || !Number.isFinite(from.getTime())) return { times: [], error: '预览起始时间无效' }
  const values = parsed.values, times = [], requested = Math.min(5, Math.max(1, Number(count) || 5))
  const cursor = new Date(from.getFullYear(), from.getMonth(), from.getDate()), stopYear = Math.min(2199, from.getFullYear() + 100)
  let candidates = 0
  for (let days = 0; days < 36601 && cursor.getFullYear() <= stopYear; days++, cursor.setDate(cursor.getDate() + 1)) {
    const year = cursor.getFullYear(), month = cursor.getMonth() + 1, day = cursor.getDate()
    if ((values.year && !values.year.includes(year)) || !values.month.includes(month) || !matchesDay(values, year, month, day)) continue
    for (const hour of values.hour) for (const minute of values.min) for (const second of values.second) {
      if (++candidates > 100000) return { times, error: '预览计算已达上限，请以服务端执行计划为准' }
      const time = new Date(year, month - 1, day, hour, minute, second)
      if (time.getHours() !== hour || time.getMinutes() !== minute || time <= from) continue
      times.push(time)
      if (times.length === requested) return { times, error: '' }
    }
  }
  return { times, error: times.length ? '未来 100 年预览范围内仅找到以上执行时间' : '未来 100 年预览范围内没有执行时间' }
}
module.exports = { parseCronExpression, serializeCronModel, expandField, previewCronExpression }
