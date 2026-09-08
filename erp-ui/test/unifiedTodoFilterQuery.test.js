const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const file = path.resolve(__dirname, "../src/utils/todoFilterQuery.js")
assert.ok(fs.existsSync(file), "shared todo filter query utility must exist")

let source = fs.readFileSync(file, "utf8")
  .replace(/export\s+const\s+/g, "const ")
  .replace(/export\s+function\s+/g, "function ")

source += `
module.exports = {
  TODO_CATEGORY_VALUES,
  TODO_SOURCE_VALUES,
  TODO_SCOPE_VALUES,
  TODO_PRIORITY_VALUES,
  TODO_PAGE_SIZES,
  MAX_TODO_PREFIX,
  normalizeTodoFilterQuery,
  buildTodoFilterQuery,
  todoFilterStateEquals,
  todoRouteQueryMatches,
  hasActiveTodoFilters
}`

const sandbox = { module: { exports: {} }, exports: {}, Object, Array, String, Number, JSON }
vm.runInNewContext(source, sandbox, { filename: file })
const api = sandbox.module.exports
const plain = value => JSON.parse(JSON.stringify(value))

assert.deepStrictEqual(plain(api.TODO_CATEGORY_VALUES), [
  "all", "approval", "execution", "returned", "risk", "personal"
])
assert.deepStrictEqual(plain(api.TODO_SOURCE_VALUES), ["all", "inventory", "oa", "system"])
assert.deepStrictEqual(plain(api.TODO_SCOPE_VALUES), ["actionable"])
assert.deepStrictEqual(plain(api.TODO_PRIORITY_VALUES), ["all", "urgent", "important", "normal"])
assert.deepStrictEqual(plain(api.TODO_PAGE_SIZES), [10, 20, 30, 50, 100])
assert.strictEqual(api.MAX_TODO_PREFIX, 100000)

assert.deepStrictEqual(plain(api.normalizeTodoFilterQuery({}, { hasCurrentOrgContext: true })), {
  category: "all",
  source: "all",
  scopeMode: "actionable",
  keyword: "",
  priority: "all"
})

assert.deepStrictEqual(plain(api.normalizeTodoFilterQuery(
  { category: "bad", source: "bad", scope: "current_org", keyword: ["  needle  ", "ignored"], priority: "high" },
  { hasCurrentOrgContext: false }
)), {
  category: "all",
  source: "all",
  scopeMode: "actionable",
  keyword: "needle",
  priority: "all"
})

assert.deepStrictEqual(plain(api.normalizeTodoFilterQuery({
  category: ["approval", "risk"],
  source: ["oa", "system"],
  scope: ["all_authorized", "current_org"],
  priority: ["urgent", "normal"]
}, { hasCurrentOrgContext: true })), {
  category: "approval",
  source: "oa",
  scopeMode: "actionable",
  keyword: "",
  priority: "urgent"
})

const longKeyword = `  ${"字".repeat(120)}  `
assert.strictEqual(
  api.normalizeTodoFilterQuery({ keyword: longKeyword }, { hasCurrentOrgContext: true }).keyword,
  "字".repeat(100)
)
assert.strictEqual(
  api.normalizeTodoFilterQuery({ keyword: [] }, { hasCurrentOrgContext: true }).keyword,
  ""
)
assert.strictEqual(
  api.normalizeTodoFilterQuery({ keyword: [null] }, { hasCurrentOrgContext: true }).keyword,
  ""
)
assert.strictEqual(
  api.normalizeTodoFilterQuery({ keyword: [undefined] }, { hasCurrentOrgContext: true }).keyword,
  ""
)

const emojiBoundaryKeyword = `${"a".repeat(99)}😀trailing`
const normalizedEmojiBoundary = api.normalizeTodoFilterQuery({ keyword: emojiBoundaryKeyword }, {
  hasCurrentOrgContext: true
}).keyword
assert.strictEqual(normalizedEmojiBoundary, `${"a".repeat(99)}😀`)
assert.strictEqual(Array.from(normalizedEmojiBoundary).length, 100)
assert.doesNotThrow(() => encodeURIComponent(normalizedEmojiBoundary))
const builtEmojiBoundary = api.buildTodoFilterQuery({
  category: "all",
  source: "all",
  scopeMode: "current_org",
  keyword: emojiBoundaryKeyword
}, { hasCurrentOrgContext: true })
assert.strictEqual(builtEmojiBoundary.keyword, normalizedEmojiBoundary)
assert.strictEqual(builtEmojiBoundary.scope, "actionable")
assert.doesNotThrow(() => encodeURIComponent(builtEmojiBoundary.keyword))

for (const [unsafeKeyword, safeKeyword] of [
  ["\uD83D", "\uFFFD"],
  ["\uDC00", "\uFFFD"],
  [`before\uD83Dafter`, "before\uFFFDafter"],
  [`before\uDC00after`, "before\uFFFDafter"]
]) {
  const normalized = api.normalizeTodoFilterQuery({ keyword: unsafeKeyword }, {
    hasCurrentOrgContext: true
  }).keyword
  assert.strictEqual(normalized, safeKeyword)
  assert.doesNotThrow(() => encodeURIComponent(normalized))
}

assert.deepStrictEqual(plain(api.normalizeTodoFilterQuery(
  { category: "approval", source: "oa", scope: "all_authorized", priority: "important", pageNum: "3", pageSize: "20" },
  { hasCurrentOrgContext: true, includePagination: true }
)), {
  category: "approval",
  source: "oa",
  scopeMode: "actionable",
  keyword: "",
  priority: "important",
  pageNum: 3,
  pageSize: 20
})

assert.deepStrictEqual(plain(api.normalizeTodoFilterQuery(
  { pageNum: "999999", pageSize: "100" },
  { hasCurrentOrgContext: true, includePagination: true }
)), {
  category: "all",
  source: "all",
  scopeMode: "actionable",
  keyword: "",
  priority: "all",
  pageNum: 1000,
  pageSize: 100
})

for (const pageSize of api.TODO_PAGE_SIZES) {
  const expectedMaxPage = Math.floor(api.MAX_TODO_PREFIX / pageSize)
  const normalized = api.normalizeTodoFilterQuery({
    pageNum: String(expectedMaxPage + 100),
    pageSize: String(pageSize)
  }, { hasCurrentOrgContext: true, includePagination: true })
  assert.strictEqual(normalized.pageNum, expectedMaxPage, `pageSize ${pageSize} must cap the prefix`)
  assert.strictEqual(normalized.pageSize, pageSize)
}

for (const pageNum of ["0", "-2", "1.5", "NaN", ""] ) {
  const normalized = api.normalizeTodoFilterQuery({ pageNum, pageSize: "20" }, {
    hasCurrentOrgContext: true,
    includePagination: true
  })
  assert.strictEqual(normalized.pageNum, 1, `invalid pageNum ${pageNum} must fall back to one`)
}

for (const pageSize of ["0", "-10", "1.5", "15", "not-a-number"]) {
  const normalized = api.normalizeTodoFilterQuery({ pageNum: "999999", pageSize }, {
    hasCurrentOrgContext: true,
    includePagination: true
  })
  assert.strictEqual(normalized.pageSize, 10, `invalid pageSize ${pageSize} must fall back to ten`)
  assert.strictEqual(normalized.pageNum, 10000)
}

const filters = {
  category: "risk",
  source: "inventory",
  scopeMode: "current_org",
  keyword: "缺货",
  priority: "urgent",
  pageNum: 2,
  pageSize: 10
}

assert.deepStrictEqual(plain(api.buildTodoFilterQuery(filters, {
  hasCurrentOrgContext: true,
  includePagination: true
})), {
  category: "risk",
  source: "inventory",
  scope: "actionable",
  priority: "urgent",
  keyword: "缺货",
  pageNum: "2",
  pageSize: "10"
})

assert.deepStrictEqual(plain(api.buildTodoFilterQuery({
  category: "all",
  source: "all",
  scopeMode: "current_org",
  keyword: "   ",
  priority: "all"
}, { hasCurrentOrgContext: true })), {
  category: "all",
  source: "all",
  scope: "actionable",
  priority: "all"
})

assert.strictEqual(api.todoRouteQueryMatches({
  category: "risk",
  source: "inventory",
  scope: "actionable",
  priority: "urgent",
  keyword: "缺货",
  pageNum: "2",
  pageSize: "10"
}, filters, { hasCurrentOrgContext: true, includePagination: true }), true)

assert.strictEqual(api.todoRouteQueryMatches({
  pageSize: "10",
  pageNum: "2",
  keyword: "缺货",
  scope: "actionable",
  priority: "urgent",
  source: "inventory",
  category: "risk"
}, filters, { hasCurrentOrgContext: true, includePagination: true }), true,
"query key order must not affect canonical matching")

assert.strictEqual(api.todoRouteQueryMatches({
  category: ["risk", "approval"],
  source: "inventory",
  scope: "actionable",
  priority: "urgent",
  keyword: "缺货",
  pageNum: "2",
  pageSize: "10"
}, filters, { hasCurrentOrgContext: true, includePagination: true }), false,
"a raw array must not match a canonical scalar")

assert.strictEqual(api.todoRouteQueryMatches({
  category: ["risk"],
  source: "inventory",
  scope: "actionable",
  priority: "urgent",
  keyword: "缺货",
  pageNum: "2",
  pageSize: "10"
}, filters, { hasCurrentOrgContext: true, includePagination: true }), false,
"even a single-value raw array is not canonical")

assert.strictEqual(api.todoRouteQueryMatches({
  category: "risk",
  source: "inventory",
  scope: "actionable",
  priority: "urgent",
  keyword: "缺货",
  pageNum: 2,
  pageSize: "10"
}, filters, { hasCurrentOrgContext: true, includePagination: true }), false,
"a raw number must not match the canonical page string")

assert.strictEqual(api.todoRouteQueryMatches({
  category: "risk",
  source: "inventory",
  scope: "actionable",
  priority: "urgent",
  keyword: "缺货",
  pageNum: "2",
  pageSize: "10",
  extra: ""
}, filters, { hasCurrentOrgContext: true, includePagination: true }), false,
"an empty unknown query key is still non-canonical")

assert.strictEqual(api.todoRouteQueryMatches({
  category: "all",
  source: "all",
  scope: "actionable",
  priority: "all",
  keyword: ""
}, { category: "all", source: "all", scopeMode: "current_org", keyword: "", priority: "all" }, {
  hasCurrentOrgContext: true
}), false, "an empty keyword must be omitted from the canonical URL")

assert.strictEqual(api.todoRouteQueryMatches({
  category: "all",
  source: "all",
  scope: "all_authorized",
  priority: "all"
}, { category: "all", source: "all", scopeMode: "all_authorized", keyword: "", priority: "all" }, {
  hasCurrentOrgContext: false
}), false, "a legacy raw scope must be replaced by the canonical actionable URL")

assert.strictEqual(api.todoRouteQueryMatches({
  category: "risk",
  source: "inventory",
  scope: "current_org",
  priority: "urgent",
  keyword: "缺货",
  pageNum: "2",
  pageSize: "10",
  extra: "unknown"
}, filters, { hasCurrentOrgContext: true, includePagination: true }), false)

assert.strictEqual(api.todoRouteQueryMatches({
  category: " risk ",
  source: "inventory",
  scope: "current_org",
  priority: "urgent",
  keyword: "缺货",
  pageNum: "02",
  pageSize: "10"
}, filters, { hasCurrentOrgContext: true, includePagination: true }), false)

assert.strictEqual(api.todoFilterStateEquals(filters, { ...filters }, {
  hasCurrentOrgContext: true,
  includePagination: true
}), true)

assert.strictEqual(api.todoFilterStateEquals(
  { ...filters, pageNum: "2", pageSize: "10" },
  { ...filters, pageNum: 2, pageSize: 10 },
  { hasCurrentOrgContext: true, includePagination: true }
), true)

const defaults = { category: "all", source: "all", scopeMode: "actionable", keyword: "", priority: "all" }
assert.strictEqual(api.hasActiveTodoFilters(defaults, { hasCurrentOrgContext: true }), false)
assert.strictEqual(api.hasActiveTodoFilters({ ...defaults, pageNum: 9 }, {
  hasCurrentOrgContext: true,
  includePagination: true
}), false, "pagination alone is not an active filter")

for (const active of [
  { ...defaults, category: "approval" },
  { ...defaults, source: "oa" },
  { ...defaults, keyword: "needle" },
  { ...defaults, priority: "important" }
]) {
  assert.strictEqual(api.hasActiveTodoFilters(active, { hasCurrentOrgContext: true }), true)
}
assert.strictEqual(api.hasActiveTodoFilters({ ...defaults, scopeMode: "current_org" }, {
  hasCurrentOrgContext: true
}), false, "legacy scope input is canonicalized and is not a user-selectable active filter")
assert.strictEqual(api.hasActiveTodoFilters({ ...defaults, scopeMode: "all_authorized" }, {
  hasCurrentOrgContext: false
}), false, "all-authorized legacy scope cannot restore the old public queue semantics")

console.log("unifiedTodoFilterQuery tests passed")
