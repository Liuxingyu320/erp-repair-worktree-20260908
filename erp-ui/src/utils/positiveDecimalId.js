// Keep database Long identifiers exact through routes, requests and UI comparisons.
// A rounded number cannot be recovered by converting it back to a string.
function normalizePositiveDecimalId(value) {
  if (typeof value !== 'string' && typeof value !== 'number') return ''
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return ''
  const text = String(value).trim()
  if (!/^\d+$/.test(text)) return ''
  const id = text.replace(/^0+/, '')
  const maxLong = '9223372036854775807'
  return id && (id.length < maxLong.length || (id.length === maxLong.length && id <= maxLong)) ? id : ''
}
module.exports = { normalizePositiveDecimalId }
