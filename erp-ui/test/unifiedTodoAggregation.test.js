const assert = require("assert")
const { spawnSync } = require("child_process")
const path = require("path")
const { pathToFileURL } = require("url")

const PAGINATION_PROBE_TIMEOUT_MS = 5000

const {
  aggregateSummaries,
  exactBusinessId,
  sortTodos,
  fetchExactTodoPage,
  TODO_SORT_LABEL
} = require("../src/utils/todoAggregator")

assert.strictEqual(TODO_SORT_LABEL, "紧急优先 · 同级按最早创建",
  "the visible todo sort explanation must stay colocated with the authoritative comparator")

const todo = (source, priority, id, createdTime, suffix = "handle") => ({
  source,
  priority,
  businessId: id,
  createdTime,
  todoKey: `${source}:TYPE:${id}:${suffix}`
})

const summaries = aggregateSummaries([
  {
    source: "inventory",
    summary: {
      total: 4,
      approval: 1,
      execution: 1,
      returned: 0,
      risk: 2,
      personal: 0,
      typeCounts: { INV_OUT_OF_STOCK: 2 },
      recent: [todo("inventory", "urgent", 1, "2026-07-10 08:00:00", "view_risk")]
    }
  },
  {
    source: "oa",
    value: {
      data: {
        total: 3,
        approval: 2,
        execution: 1,
        returned: 0,
        risk: 0,
        personal: 0,
        typeCounts: { OA_PURCHASE_APPROVAL: 2 },
        recent: [todo("oa", "important", 2, "2026-07-09 08:00:00", "approve")]
      }
    }
  },
  {
    source: "system",
    data: {
      total: 2,
      approval: 0,
      execution: 0,
      returned: 1,
      risk: 0,
      personal: 1,
      typeCounts: { HR_PROFILE_INCOMPLETE: 1 },
      recent: [
        todo("system", "normal", 3, "2026-07-08 08:00:00", "complete_profile"),
        todo("inventory", "urgent", 1, "2026-07-10 08:00:00", "view_risk")
      ]
    }
  }
])

assert.deepStrictEqual(summaries.counts, {
  total: 9,
  approval: 3,
  execution: 2,
  returned: 1,
  risk: 2,
  personal: 1
})
assert.deepStrictEqual(summaries.typeCounts, {
  INV_OUT_OF_STOCK: 2,
  OA_PURCHASE_APPROVAL: 2,
  HR_PROFILE_INCOMPLETE: 1
})
assert.deepStrictEqual(summaries.recent.map(item => item.todoKey), [
  "inventory:TYPE:1:view_risk",
  "oa:TYPE:2:approve",
  "system:TYPE:3:complete_profile"
])

const sorted = sortTodos([
  todo("system", "normal", "3", "2026-07-08T08:00:00Z"),
  todo("inventory", "urgent", "11", null),
  todo("oa", "important", "2", "2026-07-09T08:00:00Z"),
  todo("inventory", "urgent", "10", "2026-07-10T08:00:00Z"),
  todo("inventory", "urgent", "2", "2026-07-10T08:00:00Z")
])
assert.deepStrictEqual(sorted.map(item => item.businessId), ["2", "10", "11", "2", "3"])

const longIds = sortTodos([
  todo("inventory", "normal", "10000000000000000", "2026-07-10T08:00:00Z"),
  todo("inventory", "normal", "9999999999999999", "2026-07-10T08:00:00Z")
])
assert.deepStrictEqual(longIds.map(item => item.businessId), [
  "9999999999999999",
  "10000000000000000"
], "business IDs above Number.MAX_SAFE_INTEGER must retain exact integer ordering")

const lossyLongItems = [
  {
    ...todo("inventory", "normal", JSON.parse('{"value":9999999999999999}').value, "2026-07-10T08:00:00Z"),
    todoKey: "inventory:TYPE:9999999999999999:handle",
    routeParams: { businessId: "9999999999999999" }
  },
  {
    ...todo("inventory", "normal", JSON.parse('{"value":10000000000000000}').value, "2026-07-10T08:00:00Z"),
    todoKey: "inventory:TYPE:10000000000000000:handle"
  }
]
assert.strictEqual(lossyLongItems[0].businessId, lossyLongItems[1].businessId, "fixture must demonstrate JSON Number precision loss")
assert.deepStrictEqual(sortTodos(lossyLongItems).map(exactBusinessId), [
  "9999999999999999",
  "10000000000000000"
], "routeParams/todoKey strings must restore exact business ID ordering after JSON Number precision loss")

async function testExactPaging() {
  const [aggregatorImport, resolverImport, navigatorImport] = await Promise.all([
    import(pathToFileURL(path.resolve(__dirname, "../src/utils/todoAggregator.js")).href),
    import(pathToFileURL(path.resolve(__dirname, "../src/utils/todoRouteResolver.js")).href),
    import(pathToFileURL(path.resolve(__dirname, "../src/utils/todoNavigator.js")).href)
  ])
  assert.strictEqual(typeof aggregatorImport.fetchExactTodoPage, "function", "production aggregator must support named ES import")
  assert.strictEqual(typeof resolverImport.resolveTodoRoute, "function", "production resolver must support named ES import")
  assert.strictEqual(typeof navigatorImport.navigateTodo, "function", "production navigator must support named ES import")

  const invalidQueries = [
    { pageNum: 0, pageSize: 10 },
    { pageNum: Infinity, pageSize: 10 },
    { pageNum: "1", pageSize: 10 },
    { pageNum: 1.5, pageSize: 10 },
    { pageNum: Number.MAX_SAFE_INTEGER + 1, pageSize: 10 },
    { pageNum: 1, pageSize: 0 },
    { pageNum: 1, pageSize: 101 },
    { pageNum: 1, pageSize: 1.5 },
    { pageNum: 1001, pageSize: 100 }
  ]
  for (const invalid of invalidQueries) {
    await assert.rejects(
      () => fetchExactTodoPage({
        sources: ["inventory"],
        ...invalid,
        fetchProviderPage: async () => { throw new Error("invalid input must not fetch") }
      }),
      error => error instanceof RangeError || error instanceof TypeError,
      `unsafe pagination input must be rejected: ${JSON.stringify(invalid)}`
    )
  }

  const providerRows = {
    inventory: Array.from({ length: 135 }, (_, index) =>
      todo("inventory", index < 3 ? "urgent" : "normal", index + 1, `2026-07-10T08:${String(index % 60).padStart(2, "0")}:00Z`)),
    oa: [todo("oa", "important", 1, "2026-07-09T08:00:00Z")],
    system: []
  }
  Object.keys(providerRows).forEach(source => {
    providerRows[source] = sortTodos(providerRows[source])
  })
  const calls = []
  const result = await fetchExactTodoPage({
    sources: ["inventory", "oa", "system"],
    pageNum: 11,
    pageSize: 10,
    fetchProviderPage: async (source, params) => {
      calls.push({ source, ...params })
      const rows = providerRows[source]
      const start = (params.pageNum - 1) * params.pageSize
      return { rows: rows.slice(start, start + params.pageSize), total: rows.length }
    }
  })

  const expected = sortTodos(providerRows.inventory.concat(providerRows.oa)).slice(100, 110)
  assert.deepStrictEqual(result.rows.map(item => item.todoKey), expected.map(item => item.todoKey))
  assert.strictEqual(result.total, 136)
  assert.deepStrictEqual(result.failures, [])
  assert.ok(calls.every(call => call.pageSize <= 100), "provider page size must never exceed 100")
  assert.ok(calls.some(call => call.source === "inventory" && call.pageNum === 2), "late global pages must load a second provider prefix page")

  const duplicate = todo("inventory", "urgent", 1, "2026-07-10T08:00:00Z")
  const partial = await fetchExactTodoPage({
    sources: ["inventory", "oa", "system"],
    pageNum: 1,
    pageSize: 3,
    fetchProviderPage: async source => {
      if (source === "oa") {
        throw new Error("oa unavailable")
      }
      if (source === "inventory") {
        return { rows: [duplicate, duplicate], total: 2 }
      }
      return { rows: [], total: 0 }
    }
  })
  assert.deepStrictEqual(partial.rows.map(item => item.todoKey), [duplicate.todoKey])
  assert.strictEqual(partial.failures.length, 1)
  assert.strictEqual(partial.failures[0].source, "oa")
  assert.strictEqual(partial.total, null, "provider failure or duplicate keys make global total unknown")
  assert.strictEqual(partial.estimatedTotal, 2)

  const missingTotal = await fetchExactTodoPage({
    sources: ["system"],
    pageNum: 1,
    pageSize: 3,
    fetchProviderPage: async () => ({
      rows: [
        todo("system", "normal", 1, "2026-07-10T08:00:00Z"),
        todo("system", "normal", 2, "2026-07-10T08:01:00Z")
      ]
    })
  })
  assert.strictEqual(missingTotal.total, null, "missing provider total must never be presented as exact zero/prefix length")
  assert.strictEqual(missingTotal.estimatedTotal, 2)
  assert.strictEqual(missingTotal.providerTotals.system, null)

  const sharedKey = todo("inventory", "normal", 7, "2026-07-10T08:00:00Z")
  const globalDuplicate = await fetchExactTodoPage({
    sources: ["inventory", "oa"],
    pageNum: 1,
    pageSize: 2,
    fetchProviderPage: async source => ({
      rows: [{ ...sharedKey, source }],
      total: 1
    })
  })
  assert.strictEqual(globalDuplicate.rows.length, 1)
  assert.strictEqual(globalDuplicate.duplicatesObserved, true)
  assert.strictEqual(globalDuplicate.total, null, "cross-provider duplicate keys make global total unknown")
  assert.strictEqual(globalDuplicate.estimatedTotal, 2)

  let emptyRecoveryCalls = 0
  const recoveredAfterEmpty = await fetchExactTodoPage({
    sources: ["inventory"],
    pageNum: 1,
    pageSize: 3,
    fetchProviderPage: async () => {
      emptyRecoveryCalls += 1
      return emptyRecoveryCalls === 1
        ? { rows: [], total: 4 }
        : { rows: [todo("inventory", "normal", 1, "2026-07-10T08:00:00Z")], total: 4 }
    }
  })
  assert.strictEqual(emptyRecoveryCalls, 2, "one known-total empty page may recover on the next page")
  assert.strictEqual(recoveredAfterEmpty.failures.length, 0)

  let shortPageCalls = 0
  const shortPageRows = Array.from({ length: 5 }, (_, index) =>
    todo("inventory", "normal", index + 1, `2026-07-10T08:0${index}:00Z`))
  const afterShortPage = await fetchExactTodoPage({
    sources: ["inventory"],
    pageNum: 1,
    pageSize: 3,
    fetchProviderPage: async (source, params) => {
      shortPageCalls += 1
      return params.pageNum === 1
        ? { rows: shortPageRows.slice(0, 2), total: 5 }
        : { rows: shortPageRows.slice(2), total: 5 }
    }
  })
  assert.strictEqual(shortPageCalls, 2, "a known-total short page must continue until the reported provider total is exhausted")
  assert.deepStrictEqual(afterShortPage.rows.map(item => item.businessId), [1, 2, 3])

  let repeatedShortCalls = 0
  const repeatedShort = await fetchExactTodoPage({
    sources: ["inventory"],
    pageNum: 1,
    pageSize: 3,
    fetchProviderPage: async () => {
      repeatedShortCalls += 1
      if (repeatedShortCalls > 3) {
        throw new Error("pagination did not terminate")
      }
      return { rows: shortPageRows.slice(0, 2) }
    }
  })
  assert.strictEqual(repeatedShortCalls, 1, "a total-less short page is exhausted")
  assert.strictEqual(repeatedShort.failures.length, 0)

  let repeatedFullCalls = 0
  const repeatedFullPage = Array.from({ length: 100 }, (_, index) =>
    todo("inventory", "normal", index + 1, `2026-07-10T08:${String(index % 60).padStart(2, "0")}:00Z`))
  const repeatedFull = await fetchExactTodoPage({
    sources: ["inventory"],
    pageNum: 11,
    pageSize: 10,
    fetchProviderPage: async () => {
      repeatedFullCalls += 1
      if (repeatedFullCalls > 3) {
        throw new Error("pagination did not terminate")
      }
      return { rows: repeatedFullPage }
    }
  })
  assert.strictEqual(repeatedFullCalls, 2, "a repeated full page with no unique progress must terminate")
  assert.strictEqual(repeatedFull.failures.length, 0)

  let knownTotalRepeatCalls = 0
  const knownTotalRepeat = await fetchExactTodoPage({
    sources: ["inventory"],
    pageNum: 11,
    pageSize: 10,
    fetchProviderPage: async () => {
      knownTotalRepeatCalls += 1
      if (knownTotalRepeatCalls > 3) {
        throw new Error("known-total pagination exceeded its maximum page count")
      }
      return { rows: repeatedFullPage, total: 250 }
    }
  })
  assert.strictEqual(knownTotalRepeatCalls, 3, "known-total duplicate pages continue only through ceil(total/pageSize)")
  assert.strictEqual(knownTotalRepeat.failures[0].error.code, "PAGINATION_STALLED")
  assert.strictEqual(knownTotalRepeat.total, null)

  let totalLessUniqueCalls = 0
  const totalLessUniqueRows = Array.from({ length: 110 }, (_, index) =>
    todo("system", "normal", index + 1, `2026-07-10T09:${String(index % 60).padStart(2, "0")}:00Z`))
  const totalLessUnique = await fetchExactTodoPage({
    sources: ["system"],
    pageNum: 11,
    pageSize: 10,
    fetchProviderPage: async (source, params) => {
      totalLessUniqueCalls += 1
      const start = (params.pageNum - 1) * params.pageSize
      return { rows: totalLessUniqueRows.slice(start, start + params.pageSize) }
    }
  })
  assert.strictEqual(totalLessUniqueCalls, 2)
  assert.strictEqual(totalLessUnique.rows.length, 10, "unique total-less full pages must still reach requiredEnd")

  const aggregatorPath = path.resolve(__dirname, "../src/utils/todoAggregator.js")
  const growingTotalProbe = spawnSync(process.execPath, ["-e", `
    const { fetchExactTodoPage } = require(${JSON.stringify(aggregatorPath)})
    const repeatedRows = Array.from({ length: 100 }, (_, index) => ({
      source: "inventory",
      type: "TYPE",
      priority: "normal",
      businessId: index + 1,
      createdTime: "2026-07-10T08:00:00Z",
      todoKey: "inventory:TYPE:" + (index + 1) + ":handle"
    }))
    let calls = 0
    fetchExactTodoPage({
      sources: ["inventory"],
      pageNum: 11,
      pageSize: 10,
      fetchProviderPage: async (source, params) => {
        calls += 1
        return { rows: repeatedRows, total: (params.pageNum + 1) * 100 }
      }
    }).then(result => {
      if (calls !== 2) {
        throw new Error("expected first total snapshot to stop at 2 pages, got " + calls)
      }
      if (result.providerTotals.inventory !== 200) {
        throw new Error("expected frozen provider total 200, got " + result.providerTotals.inventory)
      }
    }).catch(error => {
      console.error(error)
      process.exitCode = 1
    })
  `], { timeout: PAGINATION_PROBE_TIMEOUT_MS, encoding: "utf8" })
  assert.strictEqual(
    growingTotalProbe.status,
    0,
    `growing provider totals must not move the pagination ceiling: ${growingTotalProbe.stderr || growingTotalProbe.error || "timed out"}`
  )

  const maliciousTotalProbe = spawnSync(process.execPath, ["-e", `
    const { fetchExactTodoPage } = require(${JSON.stringify(aggregatorPath)})
    let calls = 0
    fetchExactTodoPage({
      sources: ["inventory"],
      pageNum: 1,
      pageSize: 10,
      fetchProviderPage: async () => {
        calls += 1
        return { rows: [], total: 1000000000 }
      }
    }).then(result => {
      if (calls !== 2) throw new Error("expected 2 requests before stall, got " + calls)
      if (result.total !== null) throw new Error("stalled pagination total must be unknown")
      if (result.estimatedTotal !== 1000000000) throw new Error("estimated total must retain provider estimate")
      if (!result.failures[0] || result.failures[0].error.code !== "PAGINATION_STALLED") {
        throw new Error("missing explicit pagination stalled failure")
      }
    }).catch(error => {
      console.error(error)
      process.exitCode = 1
    })
  `], { timeout: PAGINATION_PROBE_TIMEOUT_MS, encoding: "utf8" })
  assert.strictEqual(
    maliciousTotalProbe.status,
    0,
    `malicious provider totals must stall safely: ${maliciousTotalProbe.stderr || maliciousTotalProbe.error || "timed out"}`
  )
}

testExactPaging().then(() => {
  console.log("unifiedTodoAggregation tests passed")
}).catch(error => {
  console.error(error)
  process.exitCode = 1
})
