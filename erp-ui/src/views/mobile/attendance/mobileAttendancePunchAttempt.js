const STORAGE_PREFIX = 'erp:mobile:attendance-v2-punch:v2'
const LEGACY_STORAGE_PREFIX = 'erp:mobile:attendance-v2-punch:v1'
const LEGACY_ACK_PREFIX = 'erp:mobile:attendance-v2-punch:legacy-ack:v1'
const { withAttendancePunchLease } = require('./mobileAttendancePunchLease')

function text(value) {
  return value === undefined || value === null ? '' : String(value).trim()
}

function positiveId(value) {
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
}

function defaultStorage() {
  try {
    return typeof window !== 'undefined' && window.localStorage ? window.localStorage : null
  } catch (error) {
    return null
  }
}

function storageKey(owner) {
  return [STORAGE_PREFIX, positiveId(owner && owner.userId), positiveId(owner && owner.orgId)]
    .map(value => encodeURIComponent(value || 0))
    .join(':')
}

function attemptStorageKey(owner, requestId) {
  return `${storageKey(owner)}:${encodeURIComponent(text(requestId))}`
}

function legacyV1StorageKey(owner) {
  return [LEGACY_STORAGE_PREFIX, positiveId(owner && owner.userId), positiveId(owner && owner.orgId)]
    .map(value => encodeURIComponent(value || 0))
    .join(':')
}

function legacyAcknowledgementKey(owner, requestId) {
  return [LEGACY_ACK_PREFIX, positiveId(owner && owner.userId), positiveId(owner && owner.orgId), text(requestId)]
    .map(value => encodeURIComponent(value || 0))
    .join(':')
}

function legacyAttemptAcknowledged(storage, attempt) {
  const raw = storage.getItem(legacyAcknowledgementKey(attempt, attempt.requestId))
  if (raw === null) return false
  const value = JSON.parse(raw)
  return value && value.schema === 1 && value.userId === attempt.userId && value.orgId === attempt.orgId &&
    value.requestId === attempt.requestId
}

function writeLegacyAcknowledgement(storage, attempt) {
  const acknowledgement = JSON.stringify({
    schema: 1,
    userId: attempt.userId,
    orgId: attempt.orgId,
    requestId: attempt.requestId
  })
  const key = legacyAcknowledgementKey(attempt, attempt.requestId)
  storage.setItem(key, acknowledgement)
  return storage.getItem(key) === acknowledgement
}

function attemptStorageKeys(storage, owner) {
  const ownerKey = storageKey(owner)
  const keys = []
  if (storage.getItem(ownerKey) !== null) keys.push(ownerKey)
  const legacyV1Key = legacyV1StorageKey(owner)
  if (storage.getItem(legacyV1Key) !== null) keys.push(legacyV1Key)
  if (Number.isSafeInteger(Number(storage.length)) && typeof storage.key === 'function') {
    const prefix = `${ownerKey}:`
    for (let index = 0; index < Number(storage.length); index += 1) {
      const key = storage.key(index)
      if (typeof key === 'string' && key.startsWith(prefix)) keys.push(key)
    }
  }
  return Array.from(new Set(keys))
}

function createRequestId(owner, options = {}) {
  if (typeof options.requestId === 'string' && options.requestId.trim()) return options.requestId.trim()
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `attendance-${crypto.randomUUID()}`
  }
  const now = Number(typeof options.now === 'function' ? options.now() : options.now || Date.now())
  const random = Number(typeof options.random === 'function' ? options.random() : options.random || Math.random())
  return `attendance-${positiveId(owner && owner.userId) || 0}-${now.toString(36)}-${Math.floor(random * 0x100000000).toString(16).padStart(8, '0')}`
}

function normalizeAttempt(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const attempt = {
    schema: Number(value.schema),
    status: text(value.status),
    outcome: text(value.outcome),
    userId: positiveId(value.userId),
    orgId: positiveId(value.orgId),
    scheduleId: positiveId(value.scheduleId),
    punchType: text(value.punchType).toUpperCase(),
    punchSlotKey: text(value.punchSlotKey),
    requestId: text(value.requestId),
    challengeToken: text(value.challengeToken),
    challengeExpiresAt: text(value.challengeExpiresAt),
    startedAt: Number(value.startedAt)
  }
  if (attempt.schema !== 2 || !['pending', 'settled'].includes(attempt.status) ||
    !attempt.userId || !attempt.orgId || !attempt.scheduleId || !['IN', 'OUT'].includes(attempt.punchType) ||
    !/^[A-Za-z0-9][A-Za-z0-9._:-]{7,63}$/.test(attempt.requestId) ||
    !/^[A-Za-z0-9_-]{16,256}$/.test(attempt.challengeToken) ||
    !attempt.challengeExpiresAt || !Number.isFinite(Date.parse(attempt.challengeExpiresAt)) ||
    !Number.isFinite(attempt.startedAt) || attempt.startedAt <= 0) return null
  if (attempt.status === 'pending' && attempt.outcome) return null
  if (attempt.status === 'settled' && !['accepted', 'not-accepted'].includes(attempt.outcome)) return null
  return attempt
}

function normalizeLegacyAttempt(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const attempt = {
    schema: Number(value.schema),
    status: text(value.status),
    outcome: text(value.outcome),
    userId: positiveId(value.userId),
    orgId: positiveId(value.orgId),
    scheduleId: positiveId(value.scheduleId),
    punchType: text(value.punchType).toUpperCase(),
    punchSlotKey: text(value.punchSlotKey),
    requestId: text(value.requestId),
    challengeToken: '',
    challengeExpiresAt: '',
    startedAt: Number(value.startedAt)
  }
  if (attempt.schema !== 1 || !['pending', 'settled'].includes(attempt.status) ||
    !attempt.userId || !attempt.orgId || !attempt.scheduleId || !['IN', 'OUT'].includes(attempt.punchType) ||
    !/^[A-Za-z0-9][A-Za-z0-9._:-]{7,63}$/.test(attempt.requestId) ||
    !Number.isFinite(attempt.startedAt) || attempt.startedAt <= 0) return null
  if (attempt.status === 'pending' && attempt.outcome) return null
  if (attempt.status === 'settled' && !['accepted', 'rejected'].includes(attempt.outcome)) return null
  return attempt
}

function sameAttemptIdentity(left, right) {
  return !!left && !!right && left.userId === right.userId && left.orgId === right.orgId &&
    left.scheduleId === right.scheduleId && left.punchType === right.punchType &&
    left.punchSlotKey === right.punchSlotKey && left.requestId === right.requestId &&
    left.startedAt === right.startedAt
}

function sameAttempt(left, right) {
  const a = normalizeAttempt(left)
  const b = normalizeAttempt(right)
  return !!a && !!b && JSON.stringify(a) === JSON.stringify(b)
}

function readAttendancePunchAttempt(owner, options = {}) {
  const storage = options.storage || defaultStorage()
  const key = storageKey(owner)
  if (!storage || !positiveId(owner && owner.userId) || !positiveId(owner && owner.orgId)) {
    return { status: 'error', error: '考勤打卡追踪存储或身份上下文不可用', key }
  }
  try {
    const keys = attemptStorageKeys(storage, owner)
    if (!keys.length) return { status: 'absent', key, attemptCount: 0 }
    const attemptsByRequest = new Map()
    for (const candidateKey of keys) {
      const legacyV1Key = candidateKey === legacyV1StorageKey(owner)
      const value = legacyV1Key
        ? normalizeLegacyAttempt(JSON.parse(storage.getItem(candidateKey)))
        : normalizeAttempt(JSON.parse(storage.getItem(candidateKey)))
      const legacyKey = candidateKey === key || legacyV1Key
      if (!value || value.userId !== positiveId(owner.userId) || value.orgId !== positiveId(owner.orgId) ||
        (!legacyKey && candidateKey !== attemptStorageKey(value, value.requestId))) {
        return { status: 'error', error: '考勤打卡追踪记录损坏或身份不匹配', key }
      }
      if (legacyKey && legacyAttemptAcknowledged(storage, value)) continue
      if (legacyV1Key && value.status === 'settled') {
        // v1 never persisted the one-time challenge, so a cross-day accepted
        // or explicitly rejected tombstone cannot be re-queried safely. It is
        // acknowledged only to retire the obsolete client-side lock; it is
        // never used to display success. Server slot/state locks remain the
        // authority if a user has altered local storage.
        if (!writeLegacyAcknowledgement(storage, value)) {
          return { status: 'error', error: '升级前的打卡追踪记录无法安全迁移', key }
        }
        continue
      }
      const existing = attemptsByRequest.get(value.requestId)
      if (existing) {
        const sameSchema = existing.schema === value.schema
        if ((sameSchema && JSON.stringify(existing) !== JSON.stringify(value)) ||
          (!sameSchema && !sameAttemptIdentity(existing, value))) {
          return { status: 'error', error: '同一打卡请求存在冲突的追踪记录', key }
        }
        if (existing.schema > value.schema) continue
      }
      attemptsByRequest.set(value.requestId, value)
    }
    const attempts = Array.from(attemptsByRequest.values()).sort((left, right) =>
      left.startedAt - right.startedAt || left.requestId.localeCompare(right.requestId))
    if (!attempts.length) return { status: 'absent', key, attemptCount: 0 }
    const value = attempts[0]
    // A locally persisted settlement is only a crash-recovery hint. Local
    // storage is not authoritative and may be stale or user-modified, so every
    // reload must re-confirm it from /today or /punch/status before unlocking
    // or displaying success.
    const exposed = value.status === 'settled'
      ? { ...value, status: 'pending', outcome: '' }
      : value
    return {
      status: exposed.status,
      value: exposed,
      key: exposed.schema === 1 ? legacyV1StorageKey(exposed) : attemptStorageKey(exposed, exposed.requestId),
      attemptCount: attempts.length
    }
  } catch (error) {
    return { status: 'error', error: '考勤打卡追踪记录读取失败', key }
  }
}

function writeVerified(attempt, options = {}) {
  const storage = options.storage || defaultStorage()
  const value = normalizeAttempt(attempt)
  if (!storage || !value) throw new Error('考勤打卡追踪内容无效')
  const key = attemptStorageKey(value, value.requestId)
  storage.setItem(key, JSON.stringify(value))
  const roundTrip = normalizeAttempt(JSON.parse(storage.getItem(key)))
  if (!sameAttempt(value, roundTrip)) throw new Error('考勤打卡追踪记录校验失败')
  return value
}

function writeLegacyInterlock(attempt, options = {}) {
  const storage = options.storage || defaultStorage()
  const value = normalizeAttempt(attempt)
  if (!storage || !value) throw new Error('旧版本打卡互操作标记内容无效')
  const key = legacyV1StorageKey(value)
  const existingRaw = storage.getItem(key)
  if (existingRaw !== null) {
    const existing = normalizeLegacyAttempt(JSON.parse(existingRaw))
    if (!existing || (!legacyAttemptAcknowledged(storage, existing) && existing.requestId !== value.requestId)) {
      throw new Error('检测到旧版本标签页正在处理另一条打卡请求')
    }
    if (existing.requestId === value.requestId && !sameAttemptIdentity(existing, value)) {
      throw new Error('旧版本标签页中的同 requestId 打卡上下文不一致')
    }
  }
  const mirror = {
    schema: 1,
    status: 'pending',
    outcome: '',
    userId: value.userId,
    orgId: value.orgId,
    scheduleId: value.scheduleId,
    punchType: value.punchType,
    punchSlotKey: value.punchSlotKey,
    requestId: value.requestId,
    startedAt: value.startedAt
  }
  storage.setItem(key, JSON.stringify(mirror))
  const roundTrip = normalizeLegacyAttempt(JSON.parse(storage.getItem(key)))
  if (!roundTrip || !sameAttemptIdentity(value, roundTrip)) throw new Error('旧版本打卡互操作标记校验失败')
  return roundTrip
}

function legacyInterlockMatches(attempt, options = {}) {
  const storage = options.storage || defaultStorage()
  const value = normalizeAttempt(attempt)
  if (!storage || !value) return false
  try {
    const mirror = normalizeLegacyAttempt(JSON.parse(storage.getItem(legacyV1StorageKey(value))))
    return !!mirror && mirror.status === 'pending' && sameAttemptIdentity(value, mirror)
  } catch (error) {
    return false
  }
}

function clearAttendancePunchAttempt(owner, options = {}) {
  const storage = options.storage || defaultStorage()
  const expectedRequestId = text(options.expectedRequestId)
  if (!storage || !expectedRequestId) return false
  try {
    const requestKey = attemptStorageKey(owner, expectedRequestId)
    const requestRaw = storage.getItem(requestKey)
    let requestAttempt = null
    if (requestRaw !== null) {
      requestAttempt = normalizeAttempt(JSON.parse(requestRaw))
      if (!requestAttempt || requestAttempt.userId !== positiveId(owner && owner.userId) ||
        requestAttempt.orgId !== positiveId(owner && owner.orgId) || requestAttempt.requestId !== expectedRequestId) return false
    }
    const legacyKey = storageKey(owner)
    const legacyRaw = storage.getItem(legacyKey)
    let legacyAttempt = null
    if (legacyRaw !== null) {
      legacyAttempt = normalizeAttempt(JSON.parse(legacyRaw))
      if (!legacyAttempt || legacyAttempt.userId !== positiveId(owner && owner.userId) ||
        legacyAttempt.orgId !== positiveId(owner && owner.orgId)) return false
    }
    if (requestAttempt) {
      storage.removeItem(requestKey)
      if (storage.getItem(requestKey) !== null) return false
    }
    if (legacyAttempt && legacyAttempt.requestId === expectedRequestId) {
      if (!writeLegacyAcknowledgement(storage, legacyAttempt)) return false
    }
    const legacyV1Key = legacyV1StorageKey(owner)
    const legacyV1Raw = storage.getItem(legacyV1Key)
    if (legacyV1Raw !== null) {
      const legacyV1Attempt = normalizeLegacyAttempt(JSON.parse(legacyV1Raw))
      if (!legacyV1Attempt || legacyV1Attempt.userId !== positiveId(owner && owner.userId) ||
        legacyV1Attempt.orgId !== positiveId(owner && owner.orgId)) return false
      if (legacyV1Attempt.requestId === expectedRequestId) {
        if (!writeLegacyAcknowledgement(storage, legacyV1Attempt)) return false
      }
    }
    return true
  } catch (error) {
    return false
  }
}

function explicitRejection(error) {
  const status = Number(error && error.response && error.response.status || error && error.status)
  return [400, 401, 403, 404, 409, 412, 422].includes(status)
}

function acceptedPunchResponse(response, attempt) {
  const payload = response && response.data && typeof response.data === 'object' ? response.data : response || {}
  const event = payload.event
  return payload.success === true && positiveId(payload.evidenceId) !== null &&
    exactAcceptedEvent(event, attempt) && positiveId(event.punchEventId) !== null &&
    text(event.verificationStatus).toUpperCase() === 'ACCEPTED' && Boolean(text(event.resolvedAddress))
}

function createAttempt(input, options = {}) {
  const now = Number(typeof options.now === 'function' ? options.now() : options.now || Date.now())
  return normalizeAttempt({
    schema: 2,
    status: 'pending',
    outcome: '',
    userId: input.owner && input.owner.userId,
    orgId: input.owner && input.owner.orgId,
    scheduleId: input.scheduleId,
    punchType: input.punchType,
    punchSlotKey: input.punchSlotKey,
    requestId: input.requestId || createRequestId(input.owner, options),
    challengeToken: input.challengeToken,
    challengeExpiresAt: input.challengeExpiresAt,
    startedAt: now
  })
}

async function runAttendancePunch(input, dependencies = {}, options = {}) {
  const leaseRunner = options.withLease || withAttendancePunchLease
  let prepared
  try {
    prepared = await leaseRunner(input.owner, () => {
      const existing = readAttendancePunchAttempt(input.owner, options)
      if (existing.status === 'error') {
        return { result: { status: 'blocked', dispatched: 0, message: existing.error } }
      }
      if (existing.status === 'pending') {
        return { result: { status: 'outcome-unknown', dispatched: 0, attempt: existing.value, message: '上次打卡结果仍待核对，已阻止重复打卡' } }
      }
      if (typeof dependencies.isCurrent === 'function' && !dependencies.isCurrent()) {
        return { result: { status: 'blocked', dispatched: 0, message: '身份、门店或页面上下文已变化，本次未提交' } }
      }
      const attempt = createAttempt(input, options)
      if (!attempt) return { result: { status: 'blocked', dispatched: 0, message: '打卡追踪上下文不完整' } }
      try {
        writeVerified(attempt, options)
        writeLegacyInterlock(attempt, options)
      } catch (error) {
        const current = readAttendancePunchAttempt(input.owner, options)
        return {
          result: current.status === 'pending'
            ? { status: 'outcome-unknown', dispatched: 0, attempt: current.value, message: error.message || '打卡追踪准备失败，已保持冻结' }
            : { status: 'blocked', dispatched: 0, message: error.message || '打卡追踪准备失败' }
        }
      }
      const persisted = readAttendancePunchAttempt(input.owner, options)
      if (persisted.status !== 'pending' || !persisted.value || persisted.value.requestId !== attempt.requestId || persisted.attemptCount !== 1) {
        return {
          result: {
            status: 'outcome-unknown',
            dispatched: 0,
            attempt,
            message: '检测到并发打卡请求，已阻止网络提交并保持冻结'
          }
        }
      }
      return { attempt }
    }, options)
  } catch (error) {
    return { status: 'blocked', dispatched: 0, message: error.message || '无法建立安全的跨标签打卡锁' }
  }
  if (prepared.result) return prepared.result
  const attempt = prepared.attempt
  if (typeof dependencies.isCurrent === 'function' && !dependencies.isCurrent()) {
    return {
      status: 'outcome-unknown',
      dispatched: 0,
      attempt,
      message: '身份、门店或页面上下文在提交前已变化，已阻止网络提交并保持冻结'
    }
  }
  if (!legacyInterlockMatches(attempt, options)) {
    return {
      status: 'outcome-unknown',
      dispatched: 0,
      attempt,
      message: '旧版本标签页中的打卡互操作标记已变化，已阻止网络提交并保持冻结'
    }
  }
  try {
    const response = await dependencies.execute(attempt)
    const acceptedByCaller = typeof dependencies.accepted !== 'function' || dependencies.accepted(response, attempt)
    if (!acceptedPunchResponse(response, attempt) || !acceptedByCaller) {
      return { status: 'outcome-unknown', dispatched: 1, attempt, response, message: '打卡响应不完整，已冻结重复打卡' }
    }
    const settled = writeVerified({ ...attempt, status: 'settled', outcome: 'accepted' }, options)
    return { status: 'accepted', dispatched: 1, attempt: settled, response }
  } catch (error) {
    if (explicitRejection(error)) {
      try {
        const settled = writeVerified({ ...attempt, status: 'settled', outcome: 'not-accepted' }, options)
        return { status: 'not-accepted', dispatched: 1, attempt: settled, error }
      } catch (storageError) {
        return { status: 'outcome-unknown', dispatched: 1, attempt, error: storageError, message: '服务端已拒绝，但本地追踪结算失败，已继续冻结' }
      }
    }
    return { status: 'outcome-unknown', dispatched: 1, attempt, error, message: '打卡响应丢失或超时，已冻结重复打卡' }
  }
}

function exactAcceptedEvent(event, attempt, context) {
  return !!event && text(event.clientRequestId) === attempt.requestId &&
    positiveId(event.scheduleId || context && context.schedule && context.schedule.scheduleId) === attempt.scheduleId &&
    text(event.punchType).toUpperCase() === attempt.punchType &&
    text(event.punchSlotKey) === attempt.punchSlotKey
}

function reconcileAttendancePunch(today, owner, options = {}) {
  const current = readAttendancePunchAttempt(owner, options)
  if (current.status !== 'pending') return current
  const attempt = current.value
  const event = today && today.latestPunch
  if (!exactAcceptedEvent(event, attempt, today) || positiveId(event && event.punchEventId) === null ||
    text(event && event.verificationStatus).toUpperCase() !== 'ACCEPTED' || !text(event && event.resolvedAddress) ||
    positiveId(today && today.latestEvidenceId) === null) return current
  try {
    if (attempt.schema === 1) {
      return { status: 'settled', value: { ...attempt, status: 'settled', outcome: 'accepted' } }
    }
    return { status: 'settled', value: writeVerified({ ...attempt, status: 'settled', outcome: 'accepted' }, options) }
  } catch (error) {
    return { status: 'error', value: attempt, error: '已核对到打卡记录，但本地追踪结算失败' }
  }
}

function reconcileAttendancePunchStatus(response, owner, options = {}) {
  const current = readAttendancePunchAttempt(owner, options)
  if (current.status !== 'pending') return current
  const payload = response && response.data && typeof response.data === 'object' ? response.data : response || {}
  const status = text(payload.status).toUpperCase()
  const attempt = current.value
  if (attempt.schema === 1) {
    return { status: 'error', value: attempt, error: '升级前的打卡追踪缺少一次性凭证，只能通过今日服务端记录核对' }
  }
  if (text(payload.clientRequestId) !== attempt.requestId) {
    return { status: 'error', value: attempt, error: '打卡核对响应与原请求不匹配' }
  }
  if (status === 'PENDING') return current
  if (status === 'ACCEPTED') {
    if (!exactAcceptedEvent(payload.event, attempt, payload) || positiveId(payload.event && payload.event.punchEventId) === null ||
      text(payload.event && payload.event.verificationStatus).toUpperCase() !== 'ACCEPTED' ||
      !text(payload.event && payload.event.resolvedAddress) || positiveId(payload.evidenceId) === null) {
      return { status: 'error', value: attempt, error: '服务端打卡记录与原请求上下文不匹配' }
    }
    try {
      return { status: 'settled', value: writeVerified({ ...attempt, status: 'settled', outcome: 'accepted' }, options), response: payload }
    } catch (error) {
      return { status: 'error', value: attempt, error: '已核对到打卡记录，但本地追踪结算失败' }
    }
  }
  if (status === 'NOT_ACCEPTED') {
    try {
      return { status: 'settled', value: writeVerified({ ...attempt, status: 'settled', outcome: 'not-accepted' }, options), response: payload }
    } catch (error) {
      return { status: 'error', value: attempt, error: '未受理结果已确认，但本地追踪结算失败' }
    }
  }
  return { status: 'error', value: attempt, error: '服务端未返回可识别的打卡核对状态' }
}

module.exports = {
  STORAGE_PREFIX,
  LEGACY_STORAGE_PREFIX,
  acceptedPunchResponse,
  clearAttendancePunchAttempt,
  createAttempt,
  createRequestId,
  normalizeAttempt,
  readAttendancePunchAttempt,
  reconcileAttendancePunch,
  reconcileAttendancePunchStatus,
  runAttendancePunch,
  sameAttempt,
  attemptStorageKey,
  legacyAcknowledgementKey,
  legacyV1StorageKey,
  normalizeLegacyAttempt,
  storageKey,
  writeVerified
}
