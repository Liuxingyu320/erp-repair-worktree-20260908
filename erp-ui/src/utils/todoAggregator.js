const CATEGORIES = ["approval", "execution", "returned", "risk", "personal"]
const PRIORITY_WEIGHT = Object.freeze({ urgent: 0, important: 1, normal: 2 })
const TODO_SORT_LABEL = "紧急优先 · 同级按最早创建"
const MAX_GLOBAL_PAGE_SIZE = 100
const MAX_REQUIRED_END = 100000
const MAX_PROVIDER_REQUESTS = 1000
const MAX_CONSECUTIVE_NO_PROGRESS = 2

function toCount(value) {
  const count = Number(value)
  return Number.isFinite(count) && count > 0 ? count : 0
}

function unwrapSummary(entry) {
  let value = entry && Object.prototype.hasOwnProperty.call(entry, "summary") ? entry.summary : entry
  if (value && Object.prototype.hasOwnProperty.call(value, "value")) {
    value = value.value
  }
  if (value && value.data && typeof value.data === "object") {
    value = value.data
    if (value.data && typeof value.data === "object" && !Array.isArray(value.data)) {
      value = value.data
    }
  }
  return value && typeof value === "object" ? value : {}
}

function todoKey(item) {
  return item && item.todoKey !== undefined && item.todoKey !== null
    ? String(item.todoKey).trim()
    : ""
}

function exactBusinessId(item) {
  if (!item) {
    return ""
  }
  const routeBusinessId = item.routeParams && item.routeParams.businessId
  if (routeBusinessId !== undefined && routeBusinessId !== null && String(routeBusinessId).trim() !== "") {
    return String(routeBusinessId).trim()
  }
  const keyParts = todoKey(item).split(":")
  if (keyParts.length === 4 && /^[+-]?\d+$/.test(keyParts[2])) {
    return keyParts[2]
  }
  return item.businessId === undefined || item.businessId === null
    ? ""
    : String(item.businessId).trim()
}

function deduplicateTodos(items) {
  const seen = new Set()
  const result = []
  ;(items || []).forEach(item => {
    const key = todoKey(item)
    if (key && seen.has(key)) {
      return
    }
    if (key) {
      seen.add(key)
    }
    result.push(item)
  })
  return result
}

function deduplicateTodosWithMetadata(items) {
  const seen = new Set()
  const rows = []
  let duplicatesObserved = false
  ;(items || []).forEach(item => {
    const key = todoKey(item)
    if (key && seen.has(key)) {
      duplicatesObserved = true
      return
    }
    if (key) {
      seen.add(key)
    }
    rows.push(item)
  })
  return { rows, duplicatesObserved }
}

function timeValue(value) {
  if (value === undefined || value === null || value === "") {
    return Number.POSITIVE_INFINITY
  }
  const timestamp = Date.parse(String(value).replace(" ", "T"))
  return Number.isFinite(timestamp) ? timestamp : Number.POSITIVE_INFINITY
}

function compareBusinessId(left, right) {
  const leftValue = left === undefined || left === null ? "" : String(left)
  const rightValue = right === undefined || right === null ? "" : String(right)
  const leftInteger = normalizeInteger(leftValue)
  const rightInteger = normalizeInteger(rightValue)
  if (leftInteger && rightInteger) {
    if (leftInteger.negative !== rightInteger.negative) {
      return leftInteger.negative ? -1 : 1
    }
    if (leftInteger.digits.length !== rightInteger.digits.length) {
      const lengthOrder = leftInteger.digits.length < rightInteger.digits.length ? -1 : 1
      return leftInteger.negative ? -lengthOrder : lengthOrder
    }
    const digitsOrder = leftInteger.digits.localeCompare(rightInteger.digits)
    return leftInteger.negative ? -digitsOrder : digitsOrder
  }
  return leftValue.localeCompare(rightValue, undefined, { numeric: true })
}

function normalizeInteger(value) {
  const match = String(value).trim().match(/^([+-]?)(\d+)$/)
  if (!match) {
    return null
  }
  const digits = match[2].replace(/^0+(?=\d)/, "")
  return {
    digits,
    negative: match[1] === "-" && digits !== "0"
  }
}

function compareTodos(left, right) {
  const priority = (PRIORITY_WEIGHT[left && left.priority] ?? PRIORITY_WEIGHT.normal) -
    (PRIORITY_WEIGHT[right && right.priority] ?? PRIORITY_WEIGHT.normal)
  if (priority !== 0) {
    return priority
  }
  const createdTime = timeValue(left && left.createdTime) - timeValue(right && right.createdTime)
  if (createdTime !== 0 && Number.isFinite(createdTime)) {
    return createdTime
  }
  if (timeValue(left && left.createdTime) !== timeValue(right && right.createdTime)) {
    return timeValue(left && left.createdTime) === Number.POSITIVE_INFINITY ? 1 : -1
  }
  const businessId = compareBusinessId(exactBusinessId(left), exactBusinessId(right))
  if (businessId !== 0) {
    return businessId
  }
  return todoKey(left).localeCompare(todoKey(right))
}

function sortTodos(items) {
  return (items || []).slice().sort(compareTodos)
}

function aggregateSummaries(results) {
  const counts = { total: 0, approval: 0, execution: 0, returned: 0, risk: 0, personal: 0 }
  const typeCounts = {}
  const recent = []

  ;(results || []).forEach(entry => {
    if (entry && entry.status === "rejected") {
      return
    }
    const summary = unwrapSummary(entry)
    CATEGORIES.forEach(category => {
      counts[category] += toCount(summary[category] ?? (summary.counts && summary.counts[category]))
    })
    const summaryTypeCounts = summary.typeCounts || {}
    Object.keys(summaryTypeCounts).forEach(type => {
      typeCounts[type] = (typeCounts[type] || 0) + toCount(summaryTypeCounts[type])
    })
    if (Array.isArray(summary.recent)) {
      recent.push(...summary.recent)
    }
  })
  counts.total = CATEGORIES.reduce((sum, category) => sum + counts[category], 0)

  return {
    counts,
    typeCounts,
    recent: sortTodos(deduplicateTodos(recent))
  }
}

function unwrapPage(response) {
  let value = response
  if (value && value.data && typeof value.data === "object" && !Array.isArray(value.data)) {
    value = value.data
    if (value.data && typeof value.data === "object" && !Array.isArray(value.data) && !Array.isArray(value.rows)) {
      value = value.data
    }
  }
  const rows = value && Array.isArray(value.rows)
    ? value.rows
    : value && Array.isArray(value.list)
      ? value.list
      : []
  const totalValue = value && Number(value.total)
  return {
    rows,
    total: Number.isFinite(totalValue) && totalValue >= 0 ? totalValue : null
  }
}

async function loadProviderPrefix(source, requiredEnd, fetchProviderPage) {
  const pageSize = Math.max(1, Math.min(MAX_GLOBAL_PAGE_SIZE, requiredEnd))
  const prefix = []
  const seen = new Set()
  let pageNum = 1
  let total = null
  let failure = null
  let requestCount = 0
  let consecutiveNoProgress = 0
  let duplicatesObserved = false

  while (prefix.length < requiredEnd) {
    if (requestCount >= MAX_PROVIDER_REQUESTS) {
      failure = paginationFailure(
        "PAGINATION_BUDGET_EXCEEDED",
        `Todo pagination request budget exceeded for provider ${source}`
      )
      break
    }
    const prefixLengthBeforePage = prefix.length
    let page
    try {
      requestCount += 1
      page = unwrapPage(await fetchProviderPage(source, { pageNum, pageSize }))
    } catch (error) {
      failure = error
      break
    }
    if (total === null && page.total !== null) {
      total = page.total
    }
    page.rows.forEach(item => {
      const key = todoKey(item)
      if (key && seen.has(key)) {
        duplicatesObserved = true
        return
      }
      if (key) {
        seen.add(key)
      }
      prefix.push(item)
    })

    const shortPage = page.rows.length < pageSize
    const noUniqueProgress = prefix.length === prefixLengthBeforePage
    consecutiveNoProgress = noUniqueProgress ? consecutiveNoProgress + 1 : 0
    if (total !== null && consecutiveNoProgress >= MAX_CONSECUTIVE_NO_PROGRESS) {
      failure = paginationFailure(
        "PAGINATION_STALLED",
        `Todo pagination stalled for provider ${source}`
      )
      break
    }
    const knownTotalExhausted = total !== null && (
      prefix.length >= total ||
      pageNum >= Math.ceil(total / pageSize)
    )
    const unknownTotalExhausted = total === null && (shortPage || noUniqueProgress)
    if (knownTotalExhausted || unknownTotalExhausted) {
      break
    }
    pageNum += 1
  }

  return {
    source,
    rows: sortTodos(prefix).slice(0, requiredEnd),
    total,
    failure,
    duplicatesObserved,
    requestCount
  }
}

function paginationFailure(code, message) {
  const error = new Error(message)
  error.code = code
  return error
}

function positiveSafeInteger(value, name, defaultValue) {
  const candidate = value === undefined ? defaultValue : value
  if (!Number.isSafeInteger(candidate) || candidate <= 0) {
    throw new RangeError(`${name} must be a positive safe integer`)
  }
  return candidate
}

async function fetchExactTodoPage(options) {
  const sourceList = Array.from(new Set((options && options.sources) || []))
  const pageNum = positiveSafeInteger(options && options.pageNum, "pageNum", 1)
  const pageSize = positiveSafeInteger(options && options.pageSize, "pageSize", 10)
  if (pageSize > MAX_GLOBAL_PAGE_SIZE) {
    throw new RangeError(`pageSize must not exceed ${MAX_GLOBAL_PAGE_SIZE}`)
  }
  if (pageNum > Math.floor(MAX_REQUIRED_END / pageSize)) {
    throw new RangeError(`requested todo prefix must not exceed ${MAX_REQUIRED_END}`)
  }
  const fetchProviderPage = options && options.fetchProviderPage
  if (typeof fetchProviderPage !== "function") {
    throw new TypeError("fetchProviderPage must be a function")
  }
  const requiredEnd = pageNum * pageSize
  const prefixes = await Promise.all(sourceList.map(source =>
    loadProviderPrefix(source, requiredEnd, fetchProviderPage)))
  const failures = prefixes
    .filter(result => result.failure)
    .map(result => ({ source: result.source, error: result.failure }))
  const providerTotals = {}
  prefixes.forEach(result => {
    providerTotals[result.source] = result.total
  })
  const estimatedTotal = prefixes.reduce((sum, result) =>
    sum + (result.total === null ? result.rows.length : result.total), 0)
  const mergedResult = deduplicateTodosWithMetadata(prefixes.flatMap(result => result.rows))
  const duplicatesObserved = mergedResult.duplicatesObserved ||
    prefixes.some(result => result.duplicatesObserved)
  const exactTotalAvailable = failures.length === 0 &&
    prefixes.every(result => result.total !== null) &&
    !duplicatesObserved
  const total = exactTotalAvailable ? estimatedTotal : null
  const merged = sortTodos(mergedResult.rows)
  const start = (pageNum - 1) * pageSize

  return {
    rows: merged.slice(start, requiredEnd),
    total,
    estimatedTotal,
    providerTotals,
    failures,
    duplicatesObserved
  }
}

module.exports = {
  CATEGORIES,
  MAX_GLOBAL_PAGE_SIZE,
  MAX_PROVIDER_REQUESTS,
  MAX_REQUIRED_END,
  PRIORITY_WEIGHT,
  TODO_SORT_LABEL,
  aggregateSummaries,
  compareTodos,
  deduplicateTodos,
  deduplicateTodosWithMetadata,
  exactBusinessId,
  fetchExactTodoPage,
  sortTodos
}
