const READY_PUNCH_TYPES = new Set(['IN', 'OUT'])
const CONTINUOUS_PUNCH_MODES = new Set(['', 'CONTINUOUS', 'FIRST_LAST', 'TWO_PUNCH', 'SHIFT_BOUNDARY'])

const LOCK_COPY = Object.freeze({
  FEATURE_DISABLED: '考勤功能尚未启用',
  NO_SCHEDULE: '今日没有已发布排班',
  NO_PUBLISHED_SCHEDULE: '今日没有已发布排班',
  OUTSIDE_WINDOW: '当前不在允许打卡时间内',
  OUTSIDE_PUNCH_WINDOW: '当前不在允许打卡时间内',
  BETWEEN_SEGMENTS: '当前为工作段之间的休息时间',
  ON_BREAK: '当前为休息时间',
  RESTING: '当前为休息时间',
  ON_LEAVE: '当前时段已批准请假，无需打卡',
  COMPLETED: '今日打卡已完成',
  WRONG_STORE: '当前门店与排班不一致',
  NOT_ACTIVE_EMPLOYEE: '当前账号不在有效考勤员工范围内'
})

const ADDRESS_ERROR_COPY = Object.freeze({
  ATTENDANCE_ADDRESS_RESOLVER_UNAVAILABLE: '定位地址服务暂不可用，本次未打卡，请稍后重试。',
  ATTENDANCE_ADDRESS_RESOLUTION_FAILED: '暂时无法将当前位置解析为可读地址，本次未打卡，请到定位信号较好的位置重试。',
  ATTENDANCE_ADDRESS_INVALID: '定位地址结果无效，本次未打卡，请重新定位后重试。'
})

const CORRECTION_ERROR_COPY = Object.freeze({
  CORRECTION_TARGET_COVERED_BY_APPROVED_LEAVE: '该卡位已由批准请假覆盖，无需补卡。',
  CORRECTION_TARGET_REQUIRES_REMAINING_WORK_CONFIRMATION: '该工作段两端已请假，不能补录原卡；剩余工作请由管理者核验。',
  CORRECTION_TIME_OUTSIDE_PUNCH_WINDOW: '申请时间不在该卡位的有效打卡窗口内。'
})

const PUNCH_ERROR_COPY = Object.freeze({
  PUNCH_DAY_RESULT_ALREADY_SETTLED: '该考勤日已完成结算，本次未打卡；如需处理请联系管理者显式重算。'
})

const LEAVE_ERROR_COPY = Object.freeze({
  LEAVE_NO_SCHEDULED_WORK: '所选时间只覆盖排班休息段，没有需要请假的工作时段，请调整请假时间。'
})

function dataOf(response) {
  if (!response || typeof response !== 'object') return response || null
  return response.data && typeof response.data === 'object' ? response.data : response
}

function attendanceErrorText(error, fallback) {
  const responseData = error && error.response && error.response.data
  const signal = [
    error && error.businessCode,
    error && error.code,
    error && error.message,
    responseData && responseData.businessCode,
    responseData && responseData.code,
    responseData && responseData.msg
  ]
    .filter(Boolean)
    .join(' ')
  if (/BUSINESS_FEATURE_DISABLED|FEATURE_DISABLED/i.test(signal)) return LOCK_COPY.FEATURE_DISABLED
  if (/EMPLOYEE_NOT_ACTIVE_IN_SHOP|NOT_ACTIVE_EMPLOYEE/i.test(signal)) return LOCK_COPY.NOT_ACTIVE_EMPLOYEE
  const addressCode = Object.keys(ADDRESS_ERROR_COPY).find(code => signal.indexOf(code) > -1)
  if (addressCode) return ADDRESS_ERROR_COPY[addressCode]
  const correctionCode = Object.keys(CORRECTION_ERROR_COPY).find(code => signal.indexOf(code) > -1)
  if (correctionCode) return CORRECTION_ERROR_COPY[correctionCode]
  const punchCode = Object.keys(PUNCH_ERROR_COPY).find(code => signal.indexOf(code) > -1)
  if (punchCode) return PUNCH_ERROR_COPY[punchCode]
  const leaveCode = Object.keys(LEAVE_ERROR_COPY).find(code => signal.indexOf(code) > -1)
  if (leaveCode) return LEAVE_ERROR_COPY[leaveCode]
  return error && error.message ? error.message : fallback
}

function scheduleIdOf(schedule) {
  const source = schedule || {}
  return source.scheduleId !== undefined && source.scheduleId !== null
    ? source.scheduleId
    : source.id
}

function punchTimeText(value) {
  const text = String(value || '').trim()
  if (!text) return ''
  const timeMatch = text.match(/(?:T|\s)(\d{2}:\d{2})/) || text.match(/^(\d{2}:\d{2})/)
  return timeMatch ? timeMatch[1] : ''
}

function normalizePunchSlot(value) {
  if (!value || typeof value !== 'object') return null
  const punchSlotKey = String(value.punchSlotKey || value.slotKey || '').trim()
  const punchType = String(value.punchType || '').toUpperCase()
  const order = Number(value.segmentOrder)
  return Object.assign({}, value, {
    punchSlotKey,
    punchType,
    segmentOrder: Number.isInteger(order) && order > 0 ? order : null,
    segmentLabel: String(value.segmentLabel || '').trim(),
    startAt: value.startAt || '',
    endAt: value.endAt || '',
    opensAt: value.opensAt || '',
    closesAt: value.closesAt || ''
  })
}

function segmentLabelOf(slot) {
  const source = slot || {}
  const explicit = String(source.segmentLabel || '').trim()
  if (explicit) return explicit
  const order = Number(source.segmentOrder)
  if (order === 1) return '上午'
  if (order === 2) return '下午'
  return Number.isInteger(order) && order > 0 ? `第${order}工作段` : '本次'
}

function actionSegmentLabelOf(slot) {
  return segmentLabelOf(slot).replace(/(?:工作段|班次)$/u, '') || '本次'
}

function punchSlotLabel(slot) {
  const source = slot || {}
  const typeLabel = String(source.punchType || '').toUpperCase() === 'OUT' ? '下班' : '上班'
  return `${actionSegmentLabelOf(source)}${typeLabel}卡`
}

function isSegmentPunchContext(context) {
  const source = context || {}
  const mode = String(source.punchModeSnapshot || '').toUpperCase()
  if (CONTINUOUS_PUNCH_MODES.has(mode)) return false
  return true
}

function restMessage(context) {
  const source = context || {}
  const signal = `${source.state || ''} ${source.lockReason || ''}`.toUpperCase()
  if (!/(BETWEEN.*SEGMENTS|ON_BREAK|RESTING|MIDDAY_BREAK)/.test(signal)) return ''
  const next = source.nextPunchSlot || null
  const resumesAt = next ? punchTimeText(next.startAt || next.opensAt) : ''
  return resumesAt ? `休息中，${resumesAt}继续上班` : '休息中，请按下一工作段时间继续上班'
}

function normalizeTodayContext(response) {
  const source = dataOf(response) || {}
  const schedule = source.schedule || null
  const punchModeSnapshot = String(source.punchModeSnapshot || (schedule && schedule.punchModeSnapshot) || 'CONTINUOUS').toUpperCase()
  const punchSlotsSource = Array.isArray(source.punchSlots)
    ? source.punchSlots
    : (schedule && Array.isArray(schedule.punchSlots) ? schedule.punchSlots : [])
  const punchSlots = punchSlotsSource.map(normalizePunchSlot).filter(Boolean)
  const nextPunchSlot = normalizePunchSlot(source.nextPunchSlot)
  return {
    state: String(source.state || 'UNKNOWN').toUpperCase(),
    canPunch: source.canPunch === true,
    lockReason: source.lockReason || '',
    allowedPunchType: String(source.allowedPunchType || (nextPunchSlot && nextPunchSlot.punchType) || '').toUpperCase(),
    serverTime: source.serverTime || '',
    schedule,
    punchModeSnapshot,
    nextPunchSlot,
    punchSlots,
    latestPunch: source.latestPunch || null,
    latestEvidenceId: source.latestEvidenceId === undefined || source.latestEvidenceId === null
      ? null
      : source.latestEvidenceId,
    dayResult: source.dayResult || null
  }
}

function lockReasonText(context) {
  const source = context || {}
  const breakCopy = restMessage(source)
  if (breakCopy) return breakCopy
  const reason = String(source.lockReason || '').trim()
  if (reason && LOCK_COPY[reason.toUpperCase()]) return LOCK_COPY[reason.toUpperCase()]
  if (reason) return reason
  if (!source.schedule) return LOCK_COPY.NO_PUBLISHED_SCHEDULE
  return '当前暂不能打卡'
}

function resolvePunchGate(context) {
  const source = context || {}
  if (!source.schedule) return { ready: false, reason: lockReasonText(source) }
  if (source.canPunch !== true) return { ready: false, reason: lockReasonText(source) }
  const scheduleId = scheduleIdOf(source.schedule)
  if (scheduleId === undefined || scheduleId === null || String(scheduleId).trim() === '') {
    return { ready: false, reason: '排班数据不完整，请联系管理者重新发布' }
  }
  const nextSlot = source.nextPunchSlot || null
  const punchType = String((nextSlot && nextSlot.punchType) || source.allowedPunchType || '').toUpperCase()
  if (!READY_PUNCH_TYPES.has(punchType)) {
    return { ready: false, reason: '服务端未返回有效的打卡类型' }
  }
  if (isSegmentPunchContext(source) && (!nextSlot || !nextSlot.punchSlotKey)) {
    return { ready: false, reason: '分段班次未返回有效的打卡时段，请刷新后重试' }
  }
  const gate = { ready: true, reason: '', scheduleId, punchType }
  if (nextSlot && nextSlot.punchSlotKey) {
    gate.punchSlotKey = nextSlot.punchSlotKey
    gate.segmentOrder = nextSlot.segmentOrder
    gate.segmentLabel = segmentLabelOf(nextSlot)
    gate.opensAt = nextSlot.opensAt
    gate.closesAt = nextSlot.closesAt
  }
  return gate
}

function validateLocation(position) {
  const coords = position && position.coords ? position.coords : position
  const latitude = Number(coords && coords.latitude)
  const longitude = Number(coords && coords.longitude)
  const accuracyMeters = Number(coords && coords.accuracy)
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
    !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
    !Number.isFinite(accuracyMeters) || accuracyMeters <= 0) {
    return { ok: false, reason: '未获取到有效定位，请开启精确位置后重试' }
  }

  // 精度上限由服务端全局策略统一裁决。前端只拒绝无效定位，不做地点距离计算、不展示坐标。
  return { ok: true, latitude, longitude, accuracyMeters }
}

function validatePhoto(file, options) {
  const source = options || {}
  const maxBytes = Number(source.maxBytes) || 5 * 1024 * 1024
  if (!file) return { ok: false, reason: '未拍摄现场照片' }
  if (!/^image\/(jpeg|png)$/i.test(String(file.type || ''))) {
    return { ok: false, reason: '只能上传 JPG 或 PNG 现场照片' }
  }
  if (!Number.isFinite(Number(file.size)) || Number(file.size) <= 0) {
    return { ok: false, reason: '照片文件为空，请重新拍摄' }
  }
  if (Number(file.size) > maxBytes) {
    return { ok: false, reason: '照片超过 5MB，请降低相机分辨率后重试' }
  }
  return { ok: true }
}

module.exports = {
  ADDRESS_ERROR_COPY,
  LOCK_COPY,
  attendanceErrorText,
  dataOf,
  lockReasonText,
  actionSegmentLabelOf,
  isSegmentPunchContext,
  normalizeTodayContext,
  normalizePunchSlot,
  punchSlotLabel,
  punchTimeText,
  resolvePunchGate,
  restMessage,
  scheduleIdOf,
  segmentLabelOf,
  validateLocation,
  validatePhoto
}
