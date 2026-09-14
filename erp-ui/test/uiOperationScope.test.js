const assert = require("node:assert/strict")
const test = require("node:test")
const { createUiOperationScope } = require("../src/utils/uiOperationScope")

test("new work invalidates only its own lane and rejects foreign tokens", () => {
  const scope = createUiOperationScope(() => ({ actor: "1", session: 2, dept: "3" }))
  const oldRead = scope.begin("read", { id: "9007199254740993" })
  const save = scope.begin("save", { id: "9007199254740993" })
  const newRead = scope.begin("read", { id: "9007199254740995" })
  assert.equal(scope.isCurrent(oldRead), false)
  assert.equal(scope.isCurrent(save), true)
  assert.equal(scope.isCurrent(newRead, { id: "9007199254740995" }), true)
  assert.equal(scope.isCurrent(newRead, { id: "9007199254740993" }), false)
  assert.equal(scope.isCurrent({ lane: "read" }), false)
  assert.equal(createUiOperationScope().isCurrent(newRead), false)
})

test("snapshots do not alias mutable context or target and key ordering is irrelevant", () => {
  let context = { actor: "1", query: { status: "pending", page: 1 } }
  const identity = { id: "A" }
  const scope = createUiOperationScope(() => context)
  const token = scope.begin("list", identity)
  context = { query: { page: 1, status: "pending" }, actor: "1" }
  assert.equal(scope.isCurrent(token, identity), true)
  identity.id = "B"
  assert.equal(scope.isCurrent(token, identity), false)
  context.query.status = "approved"
  assert.equal(scope.isCurrent(token), false)
})

test("identity-change invalidation prevents organization A to B to A resurrection", () => {
  let context = { actor: "1", session: 1, dept: "A" }
  const scope = createUiOperationScope(() => context)
  const old = scope.begin("detail")
  context.dept = "B"
  scope.invalidate()
  context.dept = "A"
  assert.equal(scope.isCurrent(old), false)
  const fresh = scope.begin("detail")
  assert.equal(scope.isCurrent(fresh), true)
  context.session += 1
  assert.equal(scope.isCurrent(fresh), false)
})

test("deactivation and activation never revive old or inactive tokens", () => {
  const scope = createUiOperationScope()
  const first = scope.begin("detail")
  scope.activate() // Vue keep-alive's first activation must not discard created() work.
  assert.equal(scope.isCurrent(first), true)
  scope.deactivate()
  const inactive = scope.begin("detail")
  scope.activate()
  assert.equal(scope.isCurrent(first), false)
  assert.equal(scope.isCurrent(inactive), false)
  assert.equal(scope.isCurrent(scope.begin("detail")), true)
})

test("invalid context fails closed, including cyclic values and throwing storage getters", () => {
  const cyclic = {}; cyclic.self = cyclic
  for (const read of [() => cyclic, () => ({ n: NaN }), () => ({ n: Infinity }),
    () => ({ callback() {} }), () => new Date(), () => { throw new Error("storage denied") }]) {
    const scope = createUiOperationScope(read)
    assert.equal(scope.isCurrent(scope.begin("read")), false)
  }
  const scope = createUiOperationScope()
  assert.equal(scope.isCurrent(scope.begin("read", cyclic)), false)
})

test("obsolete success, catch and finally leave the newer operation unchanged", async () => {
  const scope = createUiOperationScope()
  const state = { value: "", error: "", loading: false }
  let settleA, settleB
  const run = promise => {
    const token = scope.begin("read")
    state.loading = true
    return promise.then(value => {
      if (scope.isCurrent(token)) state.value = value
    }).catch(error => {
      if (scope.isCurrent(token)) state.error = error.message
    }).finally(() => {
      if (scope.isCurrent(token)) state.loading = false
    })
  }
  const a = run(new Promise((resolve, reject) => { settleA = reject }))
  const b = run(new Promise(resolve => { settleB = resolve }))
  settleA(new Error("obsolete")); await a
  assert.deepEqual(state, { value: "", error: "", loading: true })
  settleB("B"); await b
  assert.deepEqual(state, { value: "B", error: "", loading: false })
})
