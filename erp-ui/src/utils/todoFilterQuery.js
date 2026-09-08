export const TODO_CATEGORY_VALUES = Object.freeze([
  'all',
  'approval',
  'execution',
  'returned',
  'risk',
  'personal'
])

export const TODO_SOURCE_VALUES = Object.freeze(['all', 'inventory', 'oa', 'system'])
export const TODO_SCOPE_VALUES = Object.freeze(['actionable'])
export const TODO_PRIORITY_VALUES = Object.freeze(['all', 'urgent', 'important', 'normal'])
export const TODO_PAGE_SIZES = Object.freeze([10, 20, 30, 50, 100])
export const MAX_TODO_PREFIX = 100000

function firstQueryValue(value) {
  return Array.isArray(value) ? value[0] : value
}

function replaceUnpairedSurrogates(text) {
  let safeText = ''
  for (let index = 0; index < text.length; index += 1) {
    const codeUnit = text.charCodeAt(index)
    if (codeUnit >= 0xD800 && codeUnit <= 0xDBFF) {
      const nextCodeUnit = text.charCodeAt(index + 1)
      if (nextCodeUnit >= 0xDC00 && nextCodeUnit <= 0xDFFF) {
        safeText += text.slice(index, index + 2)
        index += 1
      } else {
        safeText += '\uFFFD'
      }
    } else if (codeUnit >= 0xDC00 && codeUnit <= 0xDFFF) {
      safeText += '\uFFFD'
    } else {
      safeText += text[index]
    }
  }
  return safeText
}

function cleanText(value, maxLength = 100) {
  const firstValue = firstQueryValue(value)
  if (firstValue === undefined || firstValue === null) return ''
  const safeText = replaceUnpairedSurrogates(String(firstValue).trim())
  return Array.from(safeText).slice(0, maxLength).join('')
}

function allowedValue(value, allowed, fallback) {
  const normalized = cleanText(value)
  return allowed.includes(normalized) ? normalized : fallback
}

function positiveInteger(value, fallback) {
  const number = Number(firstQueryValue(value))
  return Number.isSafeInteger(number) && number > 0 ? number : fallback
}

export function normalizeTodoFilterQuery(query = {}, options = {}) {
  const includePagination = options.includePagination === true
  const normalized = {
    category: allowedValue(query.category, TODO_CATEGORY_VALUES, 'all'),
    source: allowedValue(query.source, TODO_SOURCE_VALUES, 'all'),
    scopeMode: 'actionable',
    keyword: cleanText(query.keyword),
    priority: allowedValue(query.priority, TODO_PRIORITY_VALUES, 'all')
  }

  if (!includePagination) return normalized

  const requestedPageSize = positiveInteger(query.pageSize, 10)
  const pageSize = TODO_PAGE_SIZES.includes(requestedPageSize) ? requestedPageSize : 10
  const maxPageNum = Math.max(1, Math.floor(MAX_TODO_PREFIX / pageSize))
  normalized.pageNum = Math.min(positiveInteger(query.pageNum, 1), maxPageNum)
  normalized.pageSize = pageSize
  return normalized
}

export function buildTodoFilterQuery(filters = {}, options = {}) {
  const normalized = normalizeTodoFilterQuery({
    category: filters.category,
    source: filters.source,
    scope: filters.scopeMode,
    keyword: filters.keyword,
    priority: filters.priority,
    pageNum: filters.pageNum,
    pageSize: filters.pageSize
  }, options)
  const query = {
    category: normalized.category,
    source: normalized.source,
    scope: normalized.scopeMode,
    priority: normalized.priority
  }

  if (normalized.keyword) query.keyword = normalized.keyword
  if (options.includePagination === true) {
    query.pageNum = String(normalized.pageNum)
    query.pageSize = String(normalized.pageSize)
  }
  return query
}

export function todoFilterStateEquals(left, right, options = {}) {
  return JSON.stringify(buildTodoFilterQuery(left, options)) ===
    JSON.stringify(buildTodoFilterQuery(right, options))
}

function exactQueryEquals(query, canonicalQuery) {
  if (!query || typeof query !== 'object' || Array.isArray(query)) return false
  const queryKeys = Object.keys(query).sort()
  const canonicalKeys = Object.keys(canonicalQuery).sort()
  if (queryKeys.length !== canonicalKeys.length) return false
  return queryKeys.every((key, index) =>
    key === canonicalKeys[index] && query[key] === canonicalQuery[key]
  )
}

export function todoRouteQueryMatches(query, filters, options = {}) {
  return exactQueryEquals(query, buildTodoFilterQuery(filters, options))
}

export function hasActiveTodoFilters(filters = {}, options = {}) {
  const current = normalizeTodoFilterQuery({
    category: filters.category,
    source: filters.source,
    scope: filters.scopeMode,
    keyword: filters.keyword,
    priority: filters.priority
  }, options)
  const defaults = normalizeTodoFilterQuery({}, options)
  return current.category !== defaults.category ||
    current.source !== defaults.source ||
    current.scopeMode !== defaults.scopeMode ||
    current.keyword !== defaults.keyword ||
    current.priority !== defaults.priority
}
