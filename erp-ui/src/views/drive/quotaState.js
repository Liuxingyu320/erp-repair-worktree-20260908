const GIB = 1024 ** 3

function bytesToGiB(bytes) {
  const value = Number(bytes || 0) / GIB
  return Number.isFinite(value) ? Number(value.toFixed(3)) : 0
}

function gibToBytes(value, allowZero) {
  const text = String(value == null ? '' : value).trim()
  if (!/^\d+(\.\d{1,3})?$/.test(text)) return null
  const gib = Number(text)
  if (!Number.isFinite(gib) || gib < 0 || (!allowZero && gib === 0)) return null
  const bytes = Math.round(gib * GIB)
  return Number.isSafeInteger(bytes) ? bytes : null
}

function quotaLevel(usedBytes, quotaBytes) {
  const used = Math.max(0, Number(usedBytes || 0))
  const quota = Math.max(0, Number(quotaBytes || 0))
  if (!quota) return used ? 'danger' : 'normal'
  const percent = used * 100 / quota
  if (percent >= 100) return 'danger'
  if (percent >= 85) return 'warning'
  if (percent >= 70) return 'attention'
  return 'normal'
}

function impactSummary(impact) {
  if (!impact) return ''
  const delta = Number(impact.deltaBytes || 0)
  const direction = delta > 0 ? '增加' : delta < 0 ? '减少' : '不变'
  return `影响 ${Number(impact.affectedCount || 0)} 个对象，逻辑分配${direction} ${Math.abs(delta)} 字节`
}

module.exports = { GIB, bytesToGiB, gibToBytes, impactSummary, quotaLevel }
