const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const { resolveTodoRoute, sanitizeRouteParams } = require("../src/utils/todoRouteResolver")

const todoStorePath = path.resolve(__dirname, "../src/store/modules/todo.js")
assert.ok(fs.existsSync(todoStorePath), "the namespaced resilient todo store must exist")

function loadTodoStore(overrides = {}) {
  let source = fs.readFileSync(todoStorePath, "utf8")
  source = source
    .replace(/import\s+\{[\s\S]*?\}\s+from\s+['"][^'"]+['"]\s*/g, "")
    .replace(/export\s+(const|function)\s+/g, "$1 ")
    .replace(/export default todo\s*$/, "module.exports = { todo, createTodoState, buildTodoListCacheKey }")
  const aggregate = require("../src/utils/todoAggregator")
  const context = {
    module: { exports: {} },
    exports: {},
    Promise,
    Date,
    Error,
    JSON,
    Object,
    Array,
    Set,
    Number,
    String,
    setInterval: overrides.setInterval || setInterval,
    clearInterval: overrides.clearInterval || clearInterval,
    document: overrides.document,
    fetchTodoSummary: overrides.fetchTodoSummary || (async () => ({ data: {} })),
    fetchTodoList: overrides.fetchTodoList || (async () => ({ rows: [], total: 0 })),
    aggregateSummaries: aggregate.aggregateSummaries,
    fetchExactTodoPage: aggregate.fetchExactTodoPage,
    sanitizeRouteParams,
    getSelectedDeptContext: overrides.getSelectedDeptContext || (() => ({})),
    hasValidInventoryDeptContext: overrides.hasValidInventoryDeptContext || (context => {
      const source = context || {}
      const hasDeptId = source.deptId !== undefined && source.deptId !== null && String(source.deptId).trim() !== ""
      const deptType = source.deptType ? String(source.deptType).trim().toUpperCase() : ""
      return hasDeptId && (deptType === "STORE" || deptType === "WAREHOUSE")
    }),
    addTodoRefreshListener: overrides.addTodoRefreshListener || (() => {}),
    removeTodoRefreshListener: overrides.removeTodoRefreshListener || (() => {})
  }
  vm.runInNewContext(source, context, { filename: todoStorePath })
  return context.module.exports
}

function observeLikeVue(value, seen = new Set()) {
  if (!value || typeof value !== "object" || seen.has(value) || !Object.isExtensible(value)) return
  seen.add(value)
  Object.keys(value).forEach(key => {
    const current = value[key]
    observeLikeVue(current, seen)
    Object.defineProperty(value, key, {
      enumerable: true,
      configurable: true,
      get() { return current },
      set() {}
    })
  })
}

function navigableTransferTodo(id) {
  return {
    todoKey: `inventory:INV_TRANSFER_APPROVAL:${id}:approve`,
    source: "inventory",
    type: "INV_TRANSFER_APPROVAL",
    category: "approval",
    businessId: String(id),
    deptId: "20",
    deptName: "华东仓",
    deptType: "WAREHOUSE",
    requiredPermission: "inv:transfer:approve",
    routeParams: {
      businessId: String(id),
      contextDeptId: "20",
      contextDeptName: "华东仓",
      contextDeptType: "WAREHOUSE",
      scopeMode: "actionable"
    }
  }
}

async function testRouteParamsRemainNavigableAfterVueObservation() {
  const loaded = loadTodoStore({
    getSelectedDeptContext: () => ({ deptId: "20" }),
    fetchTodoSummary: async source => ({
      data: {
        ...summary(source === "inventory" ? 1 : 0, source),
        recent: source === "inventory" ? [navigableTransferTodo(41)] : []
      }
    }),
    fetchTodoList: async () => ({ rows: [navigableTransferTodo(42)], total: 1 })
  })
  const { state, context } = createHarness(loaded)
  await context.dispatch("refreshSummaries")
  const page = await context.dispatch("refreshPage", {
    sources: ["inventory"],
    category: "approval",
    scopeMode: "actionable",
    pageNum: 1,
    pageSize: 10
  })

  const currentContext = { deptId: "20", deptName: "华东仓", deptType: "WAREHOUSE" }
  const options = {
    platform: "mobile",
    permissions: ["inv:transfer:approve"],
    availableRouteSet: new Set(["/mobile/transfer-approval"]),
    currentContext
  }
  const recent = state.providers.inventory.summary.recent[0]
  const listed = page.rows[0]
  observeLikeVue(recent)
  observeLikeVue(listed)

  const recentResolution = resolveTodoRoute(recent, options)
  const listedResolution = resolveTodoRoute(listed, options)
  assert.strictEqual(recentResolution.ok, true,
    `summary todo route params must survive Vue observation (${recentResolution.reason || "ok"})`)
  assert.strictEqual(listedResolution.ok, true,
    `list todo route params must survive Vue observation (${listedResolution.reason || "ok"})`)
}

function createHarness(loaded) {
  const { todo, createTodoState } = loaded
  const state = createTodoState()
  const rootDispatches = []
  const context = {
    state,
    commit(type, payload) {
      assert.ok(todo.mutations[type], `unknown mutation ${type}`)
      return todo.mutations[type](state, payload)
    },
    dispatch(type, payload, options) {
      const localType = type.startsWith("todo/") ? type.slice(5) : type
      if (todo.actions[localType]) {
        return todo.actions[localType](context, payload)
      }
      rootDispatches.push({ type, payload, options })
      return Promise.resolve()
    },
    getters: {},
    rootState: {}
  }
  Object.keys(todo.getters).forEach(name => {
    Object.defineProperty(context.getters, name, {
      enumerable: true,
      get: () => todo.getters[name](state, context.getters)
    })
  })
  return { todo, state, context, rootDispatches }
}

function deferred() {
  let resolve
  const promise = new Promise(onResolve => { resolve = onResolve })
  return { promise, resolve }
}

const summary = (approval, label) => ({
  approval,
  execution: 0,
  returned: 0,
  risk: 0,
  personal: 0,
  typeCounts: { [label]: approval },
  recent: approval ? [{ todoKey: `${label}:TYPE:1:approve`, priority: "normal", createdTime: "2026-07-10 08:00:00" }] : []
})

async function testSummaryScopeMatchesSelectedContext() {
  let selectedContext = { deptId: "1176", deptName: "北京柏悦", deptType: "STORE" }
  const calls = []
  const loaded = loadTodoStore({
    getSelectedDeptContext: () => selectedContext,
    fetchTodoSummary: async (source, params) => {
      calls.push({ source, ...params })
      return { data: summary(0, source) }
    }
  })
  const { context } = createHarness(loaded)

  await context.dispatch("refreshSummaries")
  assert.strictEqual(calls.length, 4)
  assert.ok(calls.every(call => call.contextDeptId === "1176" && call.scopeMode === "actionable"),
    "summary providers must use actionable scope while preserving the selected context")

  calls.length = 0
  selectedContext = { deptId: "", deptName: "总部", deptType: "COMPANY" }
  await context.dispatch("refreshSummaries")
  assert.strictEqual(calls.length, 4)
  assert.ok(calls.every(call => call.contextDeptId === "" && call.scopeMode === "actionable"),
    "summary providers must keep actionable scope when no store or warehouse is selected")
}

async function testIndependentProviderStateAndPartialFailure() {
  let round = 0
  const loaded = loadTodoStore({
    fetchTodoSummary: async source => {
      if (round === 0) {
        if (source === "system") throw new Error("system unavailable")
        return { data: summary(source === "approval" ? 0 : source === "inventory" ? 2 : 3, source) }
      }
      if (source === "approval") return { data: summary(0, source) }
      if (source === "oa") throw new Error("oa unavailable")
      if (source === "system") throw new Error("system still unavailable")
      return { data: summary(4, source) }
    }
  })
  const { todo, state, context } = createHarness(loaded)

  assert.strictEqual(todo.namespaced, true)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(state.providers)), {
    approval: { summary: null, error: null, lastSuccessAt: null, stale: false },
    inventory: { summary: null, error: null, lastSuccessAt: null, stale: false },
    oa: { summary: null, error: null, lastSuccessAt: null, stale: false },
    system: { summary: null, error: null, lastSuccessAt: null, stale: false }
  })
  assert.deepStrictEqual(JSON.parse(JSON.stringify(state.listCache)), {})
  assert.notStrictEqual(state.providers.inventory, state.providers.oa, "provider records must not share references")

  await context.dispatch("refreshSummaries")
  assert.strictEqual(state.providers.inventory.summary.approval, 2)
  assert.strictEqual(state.providers.oa.summary.approval, 3)
  assert.strictEqual(state.providers.system.summary, null, "a first failure must stay unknown, never fake zero")
  assert.strictEqual(todo.getters.todoTotal(state), 5)
  assert.strictEqual(todo.getters.todoProviderStates(state).system.unknown, true)
  assert.strictEqual(todo.getters.todoPartialFailure(state), true)

  round = 1
  await context.dispatch("refreshSummaries")
  assert.strictEqual(state.providers.inventory.summary.approval, 4)
  assert.strictEqual(state.providers.oa.summary.approval, 3, "a failed refresh must retain the last successful summary")
  assert.strictEqual(state.providers.oa.stale, true)
  assert.ok(state.providers.oa.error)
  assert.strictEqual(todo.getters.todoProviderStates(state).oa.unknown, false)
  assert.strictEqual(todo.getters.todoProviderStates(state).system.unknown, true)
  assert.strictEqual(todo.getters.todoTotal(state), 7)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(todo.getters.todoCounts(state))), {
    total: 7,
    approval: 7,
    execution: 0,
    returned: 0,
    risk: 0,
    personal: 0
  })
  assert.strictEqual(todo.getters.todoRecent(state).length, 2)
}

async function testExactCacheFallbackAndIsolation() {
  let deptId = "10"
  let fail = false
  const calls = []
  const loaded = loadTodoStore({
    getSelectedDeptContext: () => ({ deptId }),
    fetchTodoList: async (source, params) => {
      calls.push({ source, ...params })
      if (fail) throw new Error(`${source} unavailable`)
      return {
        rows: [{
          todoKey: `${source}:TYPE:${deptId}:handle`,
          source,
          businessId: deptId,
          priority: "normal",
          createdTime: "2026-07-10 08:00:00"
        }],
        total: 1
      }
    }
  })
  const { buildTodoListCacheKey } = loaded
  const { state, context } = createHarness(loaded)
  const query = { sources: ["inventory"], category: " approval ", scopeMode: " current ", keyword: "  ABC  ", pageNum: 1, pageSize: 10 }

  const fresh = await context.dispatch("refreshPage", query)
  assert.strictEqual(fresh.total, 1)
  assert.deepStrictEqual(fresh.staleSources, [])
  assert.deepStrictEqual(fresh.unknownSources, [])
  assert.ok(calls.every(call => call.scopeMode === "actionable"),
    "page requests must ignore caller-selected legacy scopes and always use actionable")
  const key = buildTodoListCacheKey("inventory", {
    category: "approval",
    scopeMode: "current",
    keyword: "ABC",
    pageNum: 1,
    pageSize: 10,
    contextDeptId: "10"
  })
  assert.ok(state.listCache[key])
  assert.deepStrictEqual(Object.keys(state.listCache[key]).sort(), ["error", "lastSuccessAt", "rows", "stale", "total"])

  fail = true
  const stale = await context.dispatch("refreshPage", query)
  assert.strictEqual(stale.rows.length, 1)
  assert.deepStrictEqual(stale.staleSources, ["inventory"])
  assert.deepStrictEqual(stale.unknownSources, [])
  assert.strictEqual(stale.total, null, "stale provider data must not be presented as an exact live total")
  assert.strictEqual(state.listCache[key].stale, true)

  deptId = "20"
  const isolated = await context.dispatch("refreshPage", query)
  assert.deepStrictEqual(isolated.rows, [], "another organization must never reuse the old cache entry")
  assert.deepStrictEqual(isolated.unknownSources, ["inventory"])
  assert.strictEqual(isolated.total, null)

  deptId = "10"
  const otherFilter = await context.dispatch("refreshPage", { ...query, keyword: "different" })
  assert.deepStrictEqual(otherFilter.rows, [], "another filter must never reuse cached rows")
  assert.deepStrictEqual(otherFilter.unknownSources, ["inventory"])
  assert.ok(calls.every(call => Number.isSafeInteger(call.pageNum) && call.pageNum > 0))
}

async function testPriorityRequestAndExactCacheIsolation() {
  let fail = false
  const calls = []
  const loaded = loadTodoStore({
    getSelectedDeptContext: () => ({ deptId: "10" }),
    fetchTodoList: async (source, params) => {
      calls.push({ source, ...params })
      if (fail) throw new Error(`${source} unavailable`)
      return {
        rows: [{
          todoKey: `${source}:TYPE:${params.priority || "all"}:handle`,
          source,
          priority: params.priority || "normal",
          createdTime: "2026-07-10 08:00:00"
        }],
        total: 1
      }
    }
  })
  const { buildTodoListCacheKey } = loaded
  const { state, context } = createHarness(loaded)
  const base = { sources: ["inventory"], category: "risk", pageNum: 1, pageSize: 10 }

  await context.dispatch("refreshPage", { ...base, priority: "urgent" })
  assert.strictEqual(calls.at(-1).priority, "urgent", "selected priority must reach the provider request")
  const urgentKey = buildTodoListCacheKey("inventory", {
    category: "risk", priority: "urgent", pageNum: 1, pageSize: 10, contextDeptId: "10"
  })
  const importantKey = buildTodoListCacheKey("inventory", {
    category: "risk", priority: "important", pageNum: 1, pageSize: 10, contextDeptId: "10"
  })
  const allKey = buildTodoListCacheKey("inventory", {
    category: "risk", priority: "", pageNum: 1, pageSize: 10, contextDeptId: "10"
  })
  assert.notStrictEqual(urgentKey, importantKey)
  assert.notStrictEqual(urgentKey, allKey)
  assert.ok(state.listCache[urgentKey])

  fail = true
  const urgentFallback = await context.dispatch("refreshPage", { ...base, priority: "urgent" })
  assert.deepStrictEqual(urgentFallback.staleSources, ["inventory"])
  assert.strictEqual(urgentFallback.rows.length, 1)

  const importantFailure = await context.dispatch("refreshPage", { ...base, priority: "important" })
  assert.deepStrictEqual(importantFailure.staleSources, [])
  assert.deepStrictEqual(importantFailure.unknownSources, ["inventory"])
  assert.deepStrictEqual(importantFailure.rows, [], "important must not reuse urgent cache rows")

  const allFailure = await context.dispatch("refreshPage", { ...base, priority: "all" })
  assert.strictEqual(calls.at(-1).priority, "", "the all priority must be sent as an empty server filter")
  assert.deepStrictEqual(allFailure.unknownSources, ["inventory"])
  assert.deepStrictEqual(allFailure.rows, [], "all must not reuse urgent cache rows")
}

async function testContextChangeDiscardsPageWithoutStuckLoading() {
  let deptId = "10"
  const gate = deferred()
  const loaded = loadTodoStore({
    getSelectedDeptContext: () => ({ deptId }),
    fetchTodoList: async source => {
      await gate.promise
      return { rows: [{ todoKey: `${source}:TYPE:10:handle`, source }], total: 1 }
    }
  })
  const { state, context } = createHarness(loaded)
  const pending = context.dispatch("refreshPage", { sources: ["inventory"], pageNum: 1, pageSize: 10 })
  deptId = "20"
  context.commit("INCREMENT_CONTEXT_VERSION")
  gate.resolve()
  const result = await pending
  assert.strictEqual(result.discarded, true)
  assert.deepStrictEqual(JSON.parse(JSON.stringify(state.listCache)), {}, "old-context pages must never populate cache")
  assert.strictEqual(state.pageLoading, false, "discarded pages must not leave loading stuck")
}

async function testLatestPageRequestOwnsLoadingAcrossContextAndStop() {
  let deptId = "10"
  const gates = [deferred(), deferred(), deferred(), deferred()]
  let call = 0
  const loaded = loadTodoStore({
    getSelectedDeptContext: () => ({ deptId }),
    fetchTodoList: async source => {
      const current = call++
      await gates[current].promise
      return { rows: [{ todoKey: `${source}:TYPE:${current}:handle`, source }], total: 1 }
    }
  })
  const { state, context } = createHarness(loaded)
  const query = { sources: ["inventory"], pageNum: 1, pageSize: 10 }

  const oldContextPage = context.dispatch("refreshPage", query)
  const oldContextToken = state.pageRequestId
  deptId = "20"
  context.commit("INCREMENT_CONTEXT_VERSION")
  assert.ok(state.pageRequestId > oldContextToken, "context invalidation must advance the page request token")
  const newContextPage = context.dispatch("refreshPage", query)
  gates[0].resolve()
  assert.strictEqual((await oldContextPage).discarded, true)
  assert.strictEqual(state.pageLoading, true, "an old discarded request must not clear a newer request's loading state")
  gates[1].resolve()
  await newContextPage
  assert.strictEqual(state.pageLoading, false)

  const beforeStopPage = context.dispatch("refreshPage", query)
  const beforeStopToken = state.pageRequestId
  await context.dispatch("stop")
  assert.ok(state.pageRequestId > beforeStopToken, "stop must advance, not reset, the monotonic page request token")
  deptId = "30"
  const afterStopPage = context.dispatch("refreshPage", query)
  gates[2].resolve()
  assert.strictEqual((await beforeStopPage).discarded, true)
  assert.strictEqual(state.pageLoading, true, "stop must invalidate old tokens without letting them clear a later request")
  gates[3].resolve()
  await afterStopPage
  assert.strictEqual(state.pageLoading, false)
}

async function testSameContextOlderPageCannotOverwriteLatest() {
  const gates = [deferred(), deferred()]
  let call = 0
  const loaded = loadTodoStore({
    getSelectedDeptContext: () => ({ deptId: "10" }),
    fetchTodoList: async source => {
      const current = call++
      await gates[current].promise
      return {
        rows: [{
          todoKey: `${source}:TYPE:${current + 1}:handle`,
          source,
          businessId: current + 1,
          priority: "normal",
          createdTime: `2026-07-10 08:00:0${current}`
        }],
        total: 1
      }
    }
  })
  const { buildTodoListCacheKey } = loaded
  const { state, context } = createHarness(loaded)
  const query = { sources: ["inventory"], category: "approval", pageNum: 1, pageSize: 10 }
  const cacheKey = buildTodoListCacheKey("inventory", {
    category: "approval", pageNum: 1, pageSize: 10, contextDeptId: "10"
  })

  const older = context.dispatch("refreshPage", query)
  const latest = context.dispatch("refreshPage", query)
  gates[1].resolve()
  const latestResult = await latest
  assert.strictEqual(latestResult.rows[0].businessId, 2)
  assert.strictEqual(state.listCache[cacheKey].rows[0].businessId, 2)

  gates[0].resolve()
  const olderResult = await older
  assert.strictEqual(olderResult.discarded, true, "a same-context response older than the latest page request must be discarded")
  assert.strictEqual(olderResult.rows.length, 0)
  assert.strictEqual(state.listCache[cacheKey].rows[0].businessId, 2, "an older response must not overwrite the latest cache")
}

async function testBackendPrefixCacheDoesNotBecomeUnknown() {
  let firstPartialAttempt = true
  const backendCalls = []
  const loaded = loadTodoStore({
    getSelectedDeptContext: () => ({ deptId: "10" }),
    fetchTodoList: async (source, params) => {
      backendCalls.push({ source, ...params })
      if (firstPartialAttempt && params.pageNum === 1) {
        return {
          rows: Array.from({ length: 10 }, (_, index) => ({
            todoKey: `${source}:TYPE:${index + 1}:handle`,
            source,
            businessId: index + 1,
            priority: "normal",
            createdTime: "2026-07-10 08:00:00"
          })),
          total: 30
        }
      }
      throw new Error(`${source} unavailable`)
    }
  })
  const { buildTodoListCacheKey } = loaded
  const { state, context } = createHarness(loaded)
  const query = { sources: ["inventory"], category: "approval", pageNum: 2, pageSize: 10 }

  await context.dispatch("refreshPage", query)
  const actualBackendKey = buildTodoListCacheKey("inventory", {
    category: "approval",
    pageNum: 1,
    pageSize: 20,
    contextDeptId: "10"
  })
  assert.ok(state.listCache[actualBackendKey], "the cache key must use the actual provider prefix page, not the global page")
  assert.ok(backendCalls.some(entry => entry.pageNum === 1 && entry.pageSize === 20))

  firstPartialAttempt = false
  const fallback = await context.dispatch("refreshPage", query)
  assert.deepStrictEqual(fallback.staleSources, ["inventory"])
  assert.deepStrictEqual(fallback.unknownSources, [], "a provider with an exact backend-page cache is stale, not wholly unknown")
  assert.strictEqual(fallback.total, null)
}

function circularAxiosError() {
  const error = new Error("provider unavailable")
  error.code = "ECONNRESET"
  error.config = { headers: { Authorization: "Bearer must-not-leak" } }
  error.request = { transport: "xhr" }
  error.response = { status: 503, data: { secret: "server-detail" } }
  error.self = error
  return error
}

async function testStateAndErrorsRemainSerializable() {
  const providerError = circularAxiosError()
  let listFails = false
  const loaded = loadTodoStore({
    fetchTodoSummary: async source => {
      if (source === "inventory") throw providerError
      return { data: summary(0, source) }
    },
    fetchTodoList: async source => {
      if (listFails) throw circularAxiosError()
      return { rows: [{ todoKey: `${source}:TYPE:1:handle`, source }], total: 1 }
    }
  })
  const { state, context } = createHarness(loaded)
  ;["refreshPromise", "pollTimer", "visibilityListener", "deptChangeListener"].forEach(key => {
    assert.strictEqual(Object.prototype.hasOwnProperty.call(state, key), false, `${key} belongs in runtime, not Vuex state`)
  })

  await context.dispatch("refreshSummaries")
  await context.dispatch("refreshPage", { sources: ["inventory"], keyword: "safe", pageNum: 1, pageSize: 10 })
  listFails = true
  await context.dispatch("refreshPage", { sources: ["inventory"], keyword: "safe", pageNum: 1, pageSize: 10 })

  let serialized
  assert.doesNotThrow(() => { serialized = JSON.stringify(state) })
  assert.ok(!serialized.includes("Authorization"))
  assert.ok(!serialized.includes("must-not-leak"))
  assert.deepStrictEqual(JSON.parse(JSON.stringify(state.providers.inventory.error)), {
    message: "provider unavailable",
    code: "ECONNRESET",
    status: 503
  })
  const cacheEntry = Object.values(state.listCache)[0]
  assert.deepStrictEqual(JSON.parse(JSON.stringify(cacheEntry.error)), {
    message: "provider unavailable",
    code: "ECONNRESET",
    status: 503
  })
}

async function testListCacheLruBoundAndBatchCommit() {
  let failKeyword = null
  const loaded = loadTodoStore({
    getSelectedDeptContext: () => ({ deptId: "10" }),
    fetchTodoList: async (source, params) => {
      if (params.keyword === failKeyword) throw new Error("cached fallback")
      if (params.keyword === "batch") {
        const start = (params.pageNum - 1) * params.pageSize
        const count = Math.max(0, Math.min(params.pageSize, 110 - start))
        return {
          rows: Array.from({ length: count }, (_, index) => ({
            todoKey: `${source}:TYPE:${start + index + 1}:handle`,
            source,
            businessId: start + index + 1,
            priority: "normal",
            createdTime: "2026-07-10 08:00:00"
          })),
          total: 110
        }
      }
      return {
        rows: [{
          todoKey: `${source}:TYPE:${params.keyword || params.pageNum}:handle`,
          source,
          businessId: params.keyword || params.pageNum,
          priority: "normal",
          createdTime: "2026-07-10 08:00:00"
        }],
        total: 1
      }
    }
  })
  const { buildTodoListCacheKey } = loaded
  const { state, context } = createHarness(loaded)
  const queryFor = keyword => ({ sources: ["inventory"], keyword, pageNum: 1, pageSize: 10 })
  for (let index = 0; index < 100; index += 1) {
    await context.dispatch("refreshPage", queryFor(`cache-${index}`))
  }
  assert.strictEqual(Object.keys(state.listCache).length, 100)

  failKeyword = "cache-0"
  const touched = await context.dispatch("refreshPage", queryFor("cache-0"))
  assert.deepStrictEqual(touched.staleSources, ["inventory"])
  failKeyword = null
  await context.dispatch("refreshPage", queryFor("cache-100"))
  assert.strictEqual(Object.keys(state.listCache).length, 100, "todo list cache must stay bounded at 100 backend pages")
  const key0 = buildTodoListCacheKey("inventory", { keyword: "cache-0", pageNum: 1, pageSize: 10, contextDeptId: "10" })
  const key1 = buildTodoListCacheKey("inventory", { keyword: "cache-1", pageNum: 1, pageSize: 10, contextDeptId: "10" })
  assert.ok(state.listCache[key0], "a recently hit cache key must survive eviction")
  assert.strictEqual(state.listCache[key1], undefined, "the least recently used key must be evicted")

  let batchCommits = 0
  const originalCommit = context.commit
  context.commit = (type, payload) => {
    if (type === "SET_LIST_CACHE_BATCH") batchCommits += 1
    return originalCommit(type, payload)
  }
  await context.dispatch("refreshPage", { sources: ["inventory"], keyword: "batch", pageNum: 11, pageSize: 10 })
  assert.strictEqual(batchCommits, 1, "all staged provider pages must enter Vuex through one batch mutation")
}

async function testTargetedProviderRetryReusesHealthyCaches() {
  let approvalListFails = false
  let approvalSummaryFails = true
  const listCalls = []
  const summaryCalls = []
  const loaded = loadTodoStore({
    fetchTodoList: async source => {
      listCalls.push(source)
      if (source === "approval" && approvalListFails) {
        const error = new Error("approval list unavailable")
        error.response = { status: 503, data: { requestId: "approval-list-request" } }
        throw error
      }
      return {
        rows: [{
          todoKey: `${source}:TYPE:${source}:handle`,
          source: source === "approval" ? "oa" : source,
          businessId: source,
          priority: "normal",
          createdTime: "2026-07-10 08:00:00"
        }],
        total: 1
      }
    },
    fetchTodoSummary: async source => {
      summaryCalls.push(source)
      if (source === "approval" && approvalSummaryFails) {
        const error = new Error("approval summary unavailable")
        error.response = { status: 503, data: { requestId: "approval-summary-request" } }
        throw error
      }
      return { data: summary(1, source) }
    }
  })
  const { state, context } = createHarness(loaded)
  const query = { providers: ["approval", "inventory"], pageNum: 1, pageSize: 10 }

  const initial = await context.dispatch("refreshPage", query)
  assert.strictEqual(initial.total, 2)
  listCalls.length = 0

  approvalListFails = true
  const degraded = await context.dispatch("refreshPage", {
    ...query,
    networkSources: ["approval"]
  })
  assert.deepStrictEqual(listCalls, ["approval"],
    "a targeted retry must not refetch healthy providers with an exact current cache")
  assert.strictEqual(degraded.rows.length, 2,
    "healthy and failed-provider cached rows must remain available during a targeted retry")
  assert.deepStrictEqual(degraded.staleSources, ["approval"])
  assert.strictEqual(degraded.total, null, "a failed provider must keep the aggregate total unknown")
  assert.strictEqual(degraded.failures[0].error.requestId, "approval-list-request")

  listCalls.length = 0
  approvalListFails = false
  const recovered = await context.dispatch("refreshPage", {
    ...query,
    networkSources: ["approval"]
  })
  assert.deepStrictEqual(listCalls, ["approval"])
  assert.deepStrictEqual(recovered.staleSources, [])
  assert.deepStrictEqual(recovered.unknownSources, [])
  assert.strictEqual(recovered.total, 2)

  await assert.rejects(
    context.dispatch("refreshProviderSummary", "approval"),
    /approval summary unavailable/
  )
  assert.deepStrictEqual(summaryCalls, ["approval"],
    "provider diagnosis retry must probe only the selected summary source")
  assert.strictEqual(state.providers.approval.error.requestId, "approval-summary-request")
  approvalSummaryFails = false
  await context.dispatch("refreshProviderSummary", "approval")
  assert.strictEqual(state.providers.approval.error, null)
  assert.ok(state.providers.approval.lastSuccessAt)
}

async function testMutationQueuesRefreshBehindInflightSummary() {
  const gates = [deferred(), deferred(), deferred(), deferred()]
  let calls = 0
  const loaded = loadTodoStore({
    fetchTodoSummary: async () => {
      const call = calls++
      if (call < 4) await gates[call].promise
      return { data: summary(1, `source-${call}`) }
    }
  })
  const { state, context } = createHarness(loaded)
  state.started = true
  const first = context.dispatch("refreshSummaries")
  const invalidated = context.dispatch("invalidateAfterMutation")
  assert.strictEqual(state.refreshQueued, true, "a mutation must queue a fresh summary behind an in-flight one")
  gates.forEach(gate => gate.resolve())
  await Promise.all([first, invalidated])
  assert.strictEqual(calls, 8)
}

Promise.resolve()
  .then(testRouteParamsRemainNavigableAfterVueObservation)
  .then(testSummaryScopeMatchesSelectedContext)
  .then(testListCacheLruBoundAndBatchCommit)
  .then(testStateAndErrorsRemainSerializable)
  .then(testIndependentProviderStateAndPartialFailure)
  .then(testExactCacheFallbackAndIsolation)
  .then(testTargetedProviderRetryReusesHealthyCaches)
  .then(testContextChangeDiscardsPageWithoutStuckLoading)
  .then(testBackendPrefixCacheDoesNotBecomeUnknown)
  .then(testLatestPageRequestOwnsLoadingAcrossContextAndStop)
  .then(testSameContextOlderPageCannotOverwriteLatest)
  .then(testMutationQueuesRefreshBehindInflightSummary)
  .then(() => console.log("unifiedTodoStore tests passed"))
  .catch(error => {
    console.error(error)
    process.exitCode = 1
  })
