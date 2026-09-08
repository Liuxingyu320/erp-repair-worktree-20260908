const MAX_PATTERN_LENGTH = 512
const ALLOWED_FLAGS = new Set(['i', 'm', 's', 'u'])

export function serializeScriptValue(value) {
  const serialized = JSON.stringify(value)
  if (serialized === undefined) return 'undefined'
  return serialized
    .replace(/</g, '\\u003c')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029')
}

export function serializeScriptString(value) {
  return serializeScriptValue(String(value))
}

function parseFlags(flags) {
  const seen = new Set()
  for (const flag of flags) {
    if (!ALLOWED_FLAGS.has(flag)) throw new SyntaxError(`不支持的正则标志: ${flag}`)
    if (seen.has(flag)) throw new SyntaxError(`正则标志不能重复: ${flag}`)
    seen.add(flag)
  }
  return flags
}

function parseLiteral(value) {
  let escaped = false
  let inCharacterClass = false
  for (let index = 1; index < value.length; index += 1) {
    const character = value[index]
    if (escaped) {
      escaped = false
      continue
    }
    if (character === '\\') {
      escaped = true
      continue
    }
    if (character === '[' && !inCharacterClass) {
      inCharacterClass = true
      continue
    }
    if (character === ']' && inCharacterClass) {
      inCharacterClass = false
      continue
    }
    if (character === '/' && !inCharacterClass) {
      return {
        source: value.slice(1, index),
        flags: parseFlags(value.slice(index + 1))
      }
    }
  }
  throw new SyntaxError('正则字面量缺少闭合斜杠')
}

export function parseRegExpPattern(value) {
  if (value === null || value === undefined) return null
  if (typeof value !== 'string') throw new TypeError('正则配置必须是字符串')
  if (value.length > MAX_PATTERN_LENGTH) throw new RangeError(`正则配置不能超过 ${MAX_PATTERN_LENGTH} 个字符`)
  const normalized = value.trim()
  if (!normalized) return null
  const parsed = normalized.startsWith('/')
    ? parseLiteral(normalized)
    : { source: normalized, flags: '' }
  if (!parsed.source) throw new SyntaxError('正则内容不能为空')
  new RegExp(parsed.source, parsed.flags)
  return parsed
}

export function serializeRegExpPattern(value) {
  const parsed = parseRegExpPattern(value)
  if (!parsed) return null
  return `new RegExp(${serializeScriptString(parsed.source)}, ${serializeScriptString(parsed.flags)})`
}
