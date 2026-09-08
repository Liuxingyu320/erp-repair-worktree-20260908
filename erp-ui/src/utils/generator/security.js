const IDENTIFIER_PATTERN = /^[A-Za-z_$][A-Za-z0-9_$]*$/

export function assertSafeIdentifier(value, label = '标识符') {
  if (typeof value !== 'string' || !IDENTIFIER_PATTERN.test(value)) {
    throw new TypeError(`${label}必须是安全的 JavaScript 标识符`)
  }
  return value
}

export function escapeTemplateAttribute(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

export function escapeTemplateText(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/{/g, "{{'{'}}")
}

export function serializeTemplateExpression(value) {
  const serialized = JSON.stringify(value)
  return escapeTemplateAttribute(serialized === undefined ? 'null' : serialized)
}
