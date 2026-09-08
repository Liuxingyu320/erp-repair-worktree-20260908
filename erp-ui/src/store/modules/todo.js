import { fetchTodoList, fetchTodoSummary } from '@/api/workbench/todo'
import { aggregateSummaries, fetchExactTodoPage } from '@/utils/todoAggregator'
import { sanitizeRouteParams } from '@/utils/todoRouteParams'
import { getSelectedDeptContext } from '@/utils/shopContext'
import { addTodoRefreshListener, removeTodoRefreshListener } from '@/utils/todoRefreshEvents'

// 技术 provider 与用户看到的业务来源必须分开。approval provider 返回的
// source 仍是 inventory、oa 或 system，不能显示为第四个业务系统。
export const TODO_SOURCES = Object.freeze(['approval', 'inventory', 'oa', 'system'])
export const TODO_BUSINESS_SOURCES = Object.freeze(['inventory', 'oa', 'system'])
const TODO_PRIORITIES = Object.freeze(['', 'urgent', 'important', 'normal'])
const POLL_INTERVAL_MS = 60000
const MAX_LIST_CACHE_ENTRIES = 100
const USE_CACHED_PROVIDER_PAGE = 'USE_CACHED_PROVIDER_PAGE'
const todoRuntime = new WeakMap()

function createTodoRuntime(state) {
  return {
    refreshPromise: null,
    pollTimer: null,
    visibilityListener: null,
    deptChangeListener: null,
    listCacheLru: new Map(Object.keys(state.listCache || {}).map(key => [key, true]))
  }
}

function runtimeFor(state) {
  let runtime = todoRuntime.get(state)
  if (!runtime) {
    runtime = createTodoRuntime(state)
    todoRuntime.set(state, runtime)
  }
  return runtime
}

function existingRuntime(state) {
  return todoRuntime.get(state)
}

function touchCacheKey(runtime, key) {
  runtime.listCacheLru.delete(key)
  runtime.listCacheLru.set(key, true)
}

function createProviderState() {
  return { summary: null, error: null, lastSuccessAt: null, stale: false }
}

export function createTodoState() {
  return {
    providers: {
      approval: createProviderState(),
      inventory: createProviderState(),
      oa: createProviderState(),
      system: createProviderState()
    },
    listCache: {},
    summaryLoading: false,
    pageLoading: false,
    pageRequestId: 0,
    contextVersion: 0,
    refreshQueued: false,
    started: false
  }
}

function positiveInteger(value, fallback) {
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : fallback
}

function normalizedString(value) {
  return value === undefined || value === null ? '' : String(value).trim()
}

function normalizedPriority(value) {
  const priority = normalizedString(value)
  if (priority === 'all') return ''
  return TODO_PRIORITIES.includes(priority) ? priority : ''
}

function currentContextDeptId() {
  const context = getSelectedDeptContext() || {}
  return normalizedString(context.deptId)
}

function currentSummaryContext() {
  const context = getSelectedDeptContext() || {}
  return {
    contextDeptId: normalizedString(context.deptId),
    scopeMode: 'actionable'
  }
}

function normalizeSources(sources) {
  const candidates = sources === undefined ? TODO_SOURCES : Array.isArray(sources) ? sources : [sources]
  return Array.from(new Set(candidates.map(normalizedString).filter(source => TODO_SOURCES.includes(source))))
}

function normalizeTodoQuery(query = {}, pageOverrides = {}) {
  return {
    category: normalizedString(query.category),
    scopeMode: 'actionable',
    keyword: normalizedString(query.keyword),
    businessSource: normalizedString(query.businessSource),
    priority: normalizedPriority(query.priority),
    pageNum: positiveInteger(pageOverrides.pageNum === undefined ? query.pageNum : pageOverrides.pageNum, 1),
    pageSize: positiveInteger(pageOverrides.pageSize === undefined ? query.pageSize : pageOverrides.pageSize, 10),
    contextDeptId: normalizedString(query.contextDeptId === undefined ? currentContextDeptId() : query.contextDeptId)
  }
}

export function buildTodoListCacheKey(source, query = {}) {
  const normalized = normalizeTodoQuery(query)
  return JSON.stringify([
    normalizedString(source),
    normalized.category,
    normalized.scopeMode,
    normalized.keyword,
    normalized.businessSource,
    normalized.priority,
    normalized.pageNum,
    normalized.pageSize,
    normalized.contextDeptId
  ])
}

function unwrapData(response) {
  let value = response
  if (value && value.data && typeof value.data === 'object' && !Array.isArray(value.data)) {
    value = value.data
    if (value.data && typeof value.data === 'object' && !Array.isArray(value.data) &&
      !Array.isArray(value.rows) && value.approval === undefined) {
      value = value.data
    }
  }
  return value && typeof value === 'object' ? value : {}
}

function stableTodoKey(item) {
  const source = normalizedString(item && item.source).toLowerCase()
  const type = normalizedString(item && (item.todoType || item.type))
  const routeParams = item && item.routeParams && typeof item.routeParams === 'object' ? item.routeParams : {}
  const itemBusinessId = normalizedString(item && item.businessId)
  const businessId = itemBusinessId || normalizedString(routeParams.businessId)
  const action = normalizedString(item && (item.action || routeParams.action))
  return source && type && businessId && action
    ? [source, type, businessId, action].join(':')
    : normalizedString(item && item.todoKey)
}

function isSafeRouteScalar(value) {
  const type = typeof value
  return value === null || type === 'string' || type === 'boolean' || (type === 'number' && Number.isFinite(value))
}

function stabilizeTodoItem(item) {
  if (!item || typeof item !== 'object' || Array.isArray(item)) return item
  const sanitized = sanitizeRouteParams(item.routeParams)
  if (!sanitized.ok) return item
  const routeParams = { ...sanitized.params }
  const approvalTaskId = item.approvalTaskId === undefined ? item.taskId : item.approvalTaskId
  const approvalInstanceId = item.approvalInstanceId === undefined ? item.instanceId : item.approvalInstanceId
  if (routeParams.approvalTaskId === undefined && approvalTaskId !== undefined && approvalTaskId !== null && isSafeRouteScalar(approvalTaskId)) {
    routeParams.approvalTaskId = approvalTaskId
  }
  if (routeParams.approvalInstanceId === undefined && approvalInstanceId !== undefined && approvalInstanceId !== null && isSafeRouteScalar(approvalInstanceId)) {
    routeParams.approvalInstanceId = approvalInstanceId
  }
  const todoType = normalizedString(item.todoType || item.type)
  return {
    ...item,
    source: normalizedString(item.source).toLowerCase(),
    type: todoType,
    todoType,
    todoKey: stableTodoKey(item),
    routeParams: Object.freeze(routeParams)
  }
}

function stabilizeTodoSummary(summary) {
  if (!summary || typeof summary !== 'object' || Array.isArray(summary)) return summary
  const stabilized = { ...summary }
  if (Array.isArray(summary.recent)) {
    stabilized.recent = summary.recent.map(stabilizeTodoItem)
  }
  if (Array.isArray(summary.recentItems)) {
    stabilized.recentItems = summary.recentItems.map(stabilizeTodoItem)
  }
  return stabilized
}

function unwrapListPage(response) {
  const value = unwrapData(response)
  const rows = (Array.isArray(value.rows) ? value.rows : Array.isArray(value.list) ? value.list : [])
    .map(stabilizeTodoItem)
  const totalNumber = Number(value.total)
  return {
    rows,
    total: Number.isFinite(totalNumber) && totalNumber >= 0 ? totalNumber : null
  }
}

function providerResults(state) {
  return TODO_SOURCES
    .filter(source => state.providers[source].summary !== null)
    .map(source => ({ source, summary: state.providers[source].summary }))
}

function aggregateKnownProviders(state) {
  return aggregateSummaries(providerResults(state))
}

function normalizeTodoError(error) {
  const message = error && typeof error.message === 'string' && error.message.trim()
    ? error.message.trim()
    : typeof error === 'string' && error.trim()
      ? error.trim()
      : 'Todo provider request failed'
  const normalized = { message }
  const code = error && (typeof error.code === 'string' || typeof error.code === 'number') ? error.code : null
  const responseStatus = error && error.response && error.response.status
  const response = error && error.response && typeof error.response === 'object' ? error.response : {}
  const responseData = response.data && typeof response.data === 'object' ? response.data : {}
  const responseHeaders = response.headers && typeof response.headers === 'object' ? response.headers : {}
  const responseHeader = name => responseHeaders[name] ||
    (typeof responseHeaders.get === 'function' ? responseHeaders.get(name) : '')
  const requestId = error && error.requestId ||
    responseData.requestId ||
    responseData.traceId ||
    responseHeader('x-request-id') ||
    responseHeader('x-trace-id')
  const explicitStatus = error && (typeof error.status === 'string' || typeof error.status === 'number')
    ? error.status
    : null
  const numericCode = Number(code)
  const numericResponseStatus = Number(responseStatus)
  const status = explicitStatus !== null
    ? explicitStatus
    : Number.isFinite(numericResponseStatus) && numericResponseStatus >= 400
      ? responseStatus
      : Number.isFinite(numericCode) && numericCode >= 400
        ? code
        : (typeof responseStatus === 'string' || typeof responseStatus === 'number') ? responseStatus : null
  if (code !== null) normalized.code = code
  if (status !== null) normalized.status = status
  if (requestId !== undefined && requestId !== null && String(requestId).trim()) {
    normalized.requestId = String(requestId).trim()
  }
  return normalized
}

function isDocumentVisible() {
  return typeof document === 'undefined' || document.visibilityState !== 'hidden'
}

const todo = {
  namespaced: true,

  state: createTodoState(),

  mutations: {
    SET_PROVIDER_SUCCESS(state, { source, summary, at }) {
      state.providers[source].summary = summary
      state.providers[source].error = null
      state.providers[source].lastSuccessAt = at
      state.providers[source].stale = false
    },
    SET_PROVIDER_FAILURE(state, { source, error }) {
      state.providers[source].error = normalizeTodoError(error)
      state.providers[source].stale = state.providers[source].summary !== null
    },
    SET_LIST_CACHE_BATCH(state, entries) {
      if (!entries || entries.length === 0) return
      const runtime = runtimeFor(state)
      const nextCache = { ...state.listCache }
      entries.forEach(({ key, entry }) => {
        nextCache[key] = entry
        touchCacheKey(runtime, key)
      })
      while (runtime.listCacheLru.size > MAX_LIST_CACHE_ENTRIES) {
        const oldestKey = runtime.listCacheLru.keys().next().value
        runtime.listCacheLru.delete(oldestKey)
        delete nextCache[oldestKey]
      }
      state.listCache = nextCache
    },
    CLEAR_LIST_CACHE(state) {
      state.listCache = {}
      const runtime = existingRuntime(state)
      if (runtime) runtime.listCacheLru.clear()
    },
    SET_SUMMARY_LOADING(state, loading) {
      state.summaryLoading = loading
    },
    BEGIN_PAGE_REQUEST(state) {
      state.pageRequestId += 1
      state.pageLoading = true
    },
    END_PAGE_REQUEST(state, requestId) {
      if (state.pageRequestId === requestId) state.pageLoading = false
    },
    INCREMENT_CONTEXT_VERSION(state) {
      state.contextVersion += 1
      state.pageRequestId += 1
      state.pageLoading = false
    },
    SET_REFRESH_QUEUED(state, queued) {
      state.refreshQueued = queued
    },
    SET_STARTED(state, started) {
      state.started = started
    },
    RESET_TODO_DATA(state) {
      TODO_SOURCES.forEach(source => {
        state.providers[source] = createProviderState()
      })
      state.listCache = {}
      state.summaryLoading = false
      state.pageLoading = false
      state.refreshQueued = false
      state.started = false
    }
  },

  getters: {
    todoCounts(state) {
      return aggregateKnownProviders(state).counts
    },
    todoTotal(state) {
      return aggregateKnownProviders(state).counts.total
    },
    todoRecent(state) {
      return aggregateKnownProviders(state).recent
    },
    todoProviderStates(state) {
      return TODO_SOURCES.reduce((result, source) => {
        const provider = state.providers[source]
        const unknown = provider.summary === null && !!provider.error
        result[source] = {
          summary: provider.summary,
          error: provider.error,
          lastSuccessAt: provider.lastSuccessAt,
          stale: provider.stale,
          unknown,
          status: unknown ? 'unknown' : provider.stale ? 'stale' : provider.summary === null ? 'pending' : 'fresh'
        }
        return result
      }, {})
    },
    todoPartialFailure(state) {
      return TODO_SOURCES.some(source => {
        const provider = state.providers[source]
        return provider.stale || !!provider.error
      })
    }
  },

  actions: {
    start({ state, commit, dispatch }) {
      const runtime = runtimeFor(state)
      if (state.started) {
        return runtime.refreshPromise || Promise.resolve()
      }
      commit('SET_STARTED', true)

      const startPolling = () => {
        if (runtime.pollTimer !== null || !isDocumentVisible() || existingRuntime(state) !== runtime) return
        runtime.pollTimer = setInterval(() => {
          dispatch('refreshSummaries')
        }, POLL_INTERVAL_MS)
      }
      const stopPolling = () => {
        if (runtime.pollTimer !== null) {
          clearInterval(runtime.pollTimer)
          runtime.pollTimer = null
        }
      }
      const visibilityListener = () => {
        if (!isDocumentVisible()) {
          stopPolling()
          return
        }
        dispatch('refreshSummaries')
        startPolling()
      }
      const deptChangeListener = () => dispatch('contextChanged')

      if (typeof document !== 'undefined' && typeof document.addEventListener === 'function') {
        document.addEventListener('visibilitychange', visibilityListener)
        runtime.visibilityListener = visibilityListener
      }
      addTodoRefreshListener(deptChangeListener)
      runtime.deptChangeListener = deptChangeListener
      startPolling()
      return dispatch('refreshSummaries')
    },

    stop({ state, commit }) {
      const runtime = existingRuntime(state)
      commit('INCREMENT_CONTEXT_VERSION')
      if (runtime && runtime.pollTimer !== null) clearInterval(runtime.pollTimer)
      if (runtime && runtime.visibilityListener &&
        typeof document !== 'undefined' && typeof document.removeEventListener === 'function') {
        document.removeEventListener('visibilitychange', runtime.visibilityListener)
      }
      if (runtime && runtime.deptChangeListener) removeTodoRefreshListener(runtime.deptChangeListener)
      commit('CLEAR_LIST_CACHE')
      commit('RESET_TODO_DATA')
      if (runtime) {
        runtime.refreshPromise = null
        runtime.pollTimer = null
        runtime.visibilityListener = null
        runtime.deptChangeListener = null
        runtime.listCacheLru.clear()
        todoRuntime.delete(state)
      }
      return Promise.resolve()
    },

    contextChanged({ state, commit, dispatch }) {
      commit('INCREMENT_CONTEXT_VERSION')
      const runtime = existingRuntime(state)
      if (runtime && runtime.refreshPromise) {
        commit('SET_REFRESH_QUEUED', true)
        return runtime.refreshPromise
      }
      return state.started ? dispatch('refreshSummaries') : Promise.resolve()
    },

    refreshSummaries({ state, commit, dispatch }) {
      const runtime = runtimeFor(state)
      if (runtime.refreshPromise) return runtime.refreshPromise

      const capturedVersion = state.contextVersion
      const summaryContext = currentSummaryContext()
      commit('SET_SUMMARY_LOADING', true)
      const request = Promise.allSettled(TODO_SOURCES.map(source =>
        fetchTodoSummary(source, summaryContext)
      )).then(results => {
        if (capturedVersion !== state.contextVersion) return
        const at = Date.now()
        results.forEach((result, index) => {
          const source = TODO_SOURCES[index]
          if (result.status === 'fulfilled') {
            commit('SET_PROVIDER_SUCCESS', { source, summary: stabilizeTodoSummary(unwrapData(result.value)), at })
          } else {
            commit('SET_PROVIDER_FAILURE', { source, error: result.reason })
          }
        })
      })

      let lockedPromise
      lockedPromise = request.finally(() => {
        if (existingRuntime(state) !== runtime || runtime.refreshPromise !== lockedPromise) return
        runtime.refreshPromise = null
        commit('SET_SUMMARY_LOADING', false)
        if (state.refreshQueued) {
          commit('SET_REFRESH_QUEUED', false)
          if (state.started) return dispatch('refreshSummaries')
        }
      })
      runtime.refreshPromise = lockedPromise
      return lockedPromise
    },

    async refreshProviderSummary({ state, commit }, requestedSource) {
      const source = normalizeSources([requestedSource])[0]
      if (!source) throw new TypeError('Unknown todo provider')
      const capturedVersion = state.contextVersion
      const summaryContext = currentSummaryContext()
      try {
        const response = await fetchTodoSummary(source, summaryContext)
        if (capturedVersion !== state.contextVersion) return { discarded: true }
        const summary = stabilizeTodoSummary(unwrapData(response))
        commit('SET_PROVIDER_SUCCESS', { source, summary, at: Date.now() })
        return { source, summary }
      } catch (error) {
        if (capturedVersion === state.contextVersion) {
          commit('SET_PROVIDER_FAILURE', { source, error })
        }
        throw error
      }
    },

    async refreshPage({ state, commit }, query = {}) {
      const sources = normalizeSources(query.providers === undefined ? query.sources : query.providers)
      const networkSources = query.networkSources === undefined
        ? sources
        : normalizeSources(query.networkSources).filter(source => sources.includes(source))
      const normalized = normalizeTodoQuery({ ...query, contextDeptId: currentContextDeptId() }, {
        pageNum: query.pageNum,
        pageSize: query.pageSize
      })
      const capturedVersion = state.contextVersion
      const stagedEntries = new Map()
      const staleSources = new Set()
      const unknownSources = new Set()
      const sourcesWithExactCache = new Set()
      const providerFailures = new Map()
      commit('BEGIN_PAGE_REQUEST')
      const pageRequestId = state.pageRequestId

      try {
        const result = await fetchExactTodoPage({
          sources,
          pageNum: normalized.pageNum,
          pageSize: normalized.pageSize,
          fetchProviderPage: async (source, page) => {
            const backendQuery = normalizeTodoQuery(normalized, page)
            const key = buildTodoListCacheKey(source, backendQuery)
            if (!networkSources.includes(source)) {
              const cached = stagedEntries.get(key) || state.listCache[key]
              if (!cached) {
                const cacheMiss = new Error(`No cached todo page available for provider ${source}`)
                cacheMiss.code = USE_CACHED_PROVIDER_PAGE
                providerFailures.set(source, normalizeTodoError(cacheMiss))
                unknownSources.add(source)
                throw cacheMiss
              }
              stagedEntries.set(key, cached)
              if (cached.stale) {
                staleSources.add(source)
                if (cached.error) providerFailures.set(source, normalizeTodoError(cached.error))
              }
              return { rows: cached.rows, total: cached.total }
            }
            try {
              const response = await fetchTodoList(source, {
                category: backendQuery.category,
                scopeMode: backendQuery.scopeMode,
                keyword: backendQuery.keyword,
                source: backendQuery.businessSource || undefined,
                priority: backendQuery.priority,
                pageNum: backendQuery.pageNum,
                pageSize: backendQuery.pageSize
              })
              const unwrapped = unwrapListPage(response)
              const expectedBusinessSource = normalizedString(backendQuery.businessSource).toLowerCase()
              const value = {
                ...unwrapped,
                rows: unwrapped.rows
                  .filter(row => {
                    const businessSource = normalizedString(row && row.source).toLowerCase()
                    if (source === 'approval' && !TODO_BUSINESS_SOURCES.includes(businessSource)) return false
                    return !expectedBusinessSource || businessSource === expectedBusinessSource
                  })
                  .map(row => ({ ...row, provider: source }))
              }
              stagedEntries.set(key, {
                rows: value.rows,
                total: value.total,
                lastSuccessAt: Date.now(),
                stale: false,
                error: null
              })
              return value
            } catch (error) {
              const normalizedError = normalizeTodoError(error)
              providerFailures.set(source, normalizedError)
              const cached = stagedEntries.get(key) || state.listCache[key]
              if (!cached) {
                if (!sourcesWithExactCache.has(source) && !staleSources.has(source)) unknownSources.add(source)
                throw error
              }
              sourcesWithExactCache.add(source)
              staleSources.add(source)
              unknownSources.delete(source)
              stagedEntries.set(key, {
                rows: cached.rows,
                total: cached.total,
                lastSuccessAt: cached.lastSuccessAt,
                stale: true,
                error: normalizedError
              })
              return { rows: cached.rows, total: cached.total }
            }
          }
        })

        if (capturedVersion !== state.contextVersion || pageRequestId !== state.pageRequestId) {
          return {
            rows: [], total: null, estimatedTotal: 0, staleSources: [], unknownSources: [], failures: [], discarded: true
          }
        }
        commit('SET_LIST_CACHE_BATCH', Array.from(stagedEntries, ([key, entry]) => ({ key, entry })))
        result.failures.forEach(failure => {
          if (!sourcesWithExactCache.has(failure.source) && !staleSources.has(failure.source)) {
            unknownSources.add(failure.source)
          }
          if (!providerFailures.has(failure.source)) {
            providerFailures.set(failure.source, normalizeTodoError(failure.error))
          }
        })
        const failures = Array.from(providerFailures, ([source, error]) => ({ source, error }))
        const isPartial = staleSources.size > 0 || unknownSources.size > 0 || failures.length > 0
        return {
          ...result,
          total: isPartial ? null : result.total,
          staleSources: Array.from(staleSources),
          unknownSources: Array.from(unknownSources),
          failures
        }
      } finally {
        commit('END_PAGE_REQUEST', pageRequestId)
      }
    },

    invalidateAfterMutation({ state, commit, dispatch }) {
      commit('CLEAR_LIST_CACHE')
      const runtime = existingRuntime(state)
      if (runtime && runtime.refreshPromise) commit('SET_REFRESH_QUEUED', true)
      return dispatch('refreshSummaries')
    }
  }
}

export default todo
