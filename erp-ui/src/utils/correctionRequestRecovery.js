// Recovery records contain identifiers and a digest only; the reason and punch details stay in memory.
const FIELDS = ['scheduleId', 'correctionType', 'targetPunchType', 'targetPunchSlotKey', 'targetScheduleSegmentSnapshotId', 'originalPunchEventId', 'requestedPunchTime', 'reason']
const businessOf = row => FIELDS.reduce((value, key) => {
  const raw = row && row[key]
  value[key] = key === 'requestedPunchTime' ? String(raw || '').replace(' ', 'T').slice(0, 16)
    : key === 'reason' ? String(raw || '').trim() : String(raw == null ? '' : raw)
  return value
}, {})
const sameBusiness = (a, b) => JSON.stringify(businessOf(a)) === JSON.stringify(businessOf(b))
const newClientRequestId = () => {
  const api = typeof window !== 'undefined' && window.crypto
  if (api && api.randomUUID) return `correction:${api.randomUUID()}`
  return `correction:${Date.now()}:${Math.random().toString(36).slice(2, 14)}`
}
async function fingerprint(row) {
  const api = typeof window !== 'undefined' && window.crypto
  if (!api || !api.subtle || typeof TextEncoder === 'undefined') return null
  const digest = await api.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(businessOf(row))))
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
}
const storageKey = context => `erp:correction-recovery:${encodeURIComponent(JSON.stringify(context))}`
function read(context) {
  try {
    const raw = typeof window !== 'undefined' && window.sessionStorage.getItem(storageKey(context))
    if (!raw) return null
    const value = JSON.parse(raw)
    if (!value || !['CREATE', 'UPDATE', 'SUBMIT'].includes(value.stage) || !/^[A-Za-z0-9][A-Za-z0-9._:-]{7,63}$/.test(value.clientRequestId || '') ||
      value.id != null && !/^[1-9]\d{0,18}$/.test(String(value.id)) || value.payloadHash != null && !/^[a-f0-9]{64}$/.test(value.payloadHash)) return null
    return { stage: value.stage, clientRequestId: value.clientRequestId, id: value.id || null, payloadHash: value.payloadHash || null,
      baseVersion: value.baseVersion, shouldSubmit: value.shouldSubmit === true, restored: true }
  } catch (_) { return null }
}
function write(context, attempt) {
  try {
    if (typeof window === 'undefined') return false
    const key = storageKey(context)
    if (!attempt) window.sessionStorage.removeItem(key)
    else window.sessionStorage.setItem(key, JSON.stringify({ stage: attempt.stage, clientRequestId: attempt.clientRequestId,
      id: attempt.id || null, payloadHash: attempt.payloadHash || null, baseVersion: attempt.baseVersion,
      shouldSubmit: attempt.shouldSubmit === true }))
    return true
  } catch (_) { return false }
}
module.exports = { businessOf, sameBusiness, newClientRequestId, fingerprint, read, write }
