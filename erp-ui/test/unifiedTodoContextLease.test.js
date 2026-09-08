const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  TODO_CONTEXT_LEASE_KEY,
  TODO_CONTEXT_LEASE_VERSION,
  beginTodoContextLease,
  readTodoContextLease,
  restoreTodoContextLeaseForRoute,
  rollbackTodoContextLease
} = require("../src/utils/todoContextLease")

function createStorage() {
  const values = new Map()
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null
    },
    setItem(key, value) {
      values.set(key, String(value))
    },
    removeItem(key) {
      values.delete(key)
    }
  }
}

function createContextHarness(initialContext) {
  let context = initialContext
  return {
    get context() {
      return context
    },
    options(storage, now) {
      return {
        storage,
        now,
        getCurrentContext: () => context,
        setSelectedDept: (deptId, deptName, deptType) => {
          context = { deptId: String(deptId), deptName, deptType }
        },
        clearSelectedDept: () => {
          context = { deptId: null, deptName: null, deptType: "" }
        }
      }
    },
    switchTo(nextContext) {
      context = nextContext
    }
  }
}

const sourceRoute = { path: "/workbench/todo", fullPath: "/workbench/todo?category=approval" }
const origin = { deptId: 1, deptName: "Test Store", deptType: "STORE" }
const target = { deptId: 2, deptName: "北京柏悦", deptType: "WAREHOUSE" }
const third = { deptId: 3, deptName: "用户主动选择", deptType: "STORE" }

{
  const storage = createStorage()
  const harness = createContextHarness(origin)
  const started = beginTodoContextLease({
    storage,
    now: 1000,
    originContext: harness.context,
    targetContext: target,
    returnRoute: sourceRoute
  })
  assert.strictEqual(started.ok, true, "a valid cross-organization jump should create a lease")
  harness.switchTo(target)

  const unrelated = restoreTodoContextLeaseForRoute({ path: "/inventory/transfer" }, harness.options(storage, 1500))
  assert.strictEqual(unrelated.restored, false, "the target business route must retain its temporary organization")
  assert.ok(storage.getItem(TODO_CONTEXT_LEASE_KEY), "the lease must remain until the source route is revisited")

  const restored = restoreTodoContextLeaseForRoute(sourceRoute, harness.options(storage, 2000))
  assert.strictEqual(restored.restored, true)
  assert.deepStrictEqual(harness.context, { deptId: "1", deptName: "Test Store", deptType: "STORE" })
  assert.strictEqual(storage.getItem(TODO_CONTEXT_LEASE_KEY), null,
    "a successful source-route restoration should consume the lease")
}

{
  const storage = createStorage()
  const harness = createContextHarness({ deptId: null, deptName: null, deptType: "" })
  assert.strictEqual(beginTodoContextLease({
    storage,
    now: 1000,
    originContext: harness.context,
    targetContext: target,
    returnRoute: { path: "/mobile/todo", fullPath: "/mobile/todo" }
  }).ok, true)
  harness.switchTo(target)
  const restored = restoreTodoContextLeaseForRoute(
    { path: "/mobile/todo" },
    harness.options(storage, 2000)
  )
  assert.strictEqual(restored.restored, true)
  assert.deepStrictEqual(harness.context, { deptId: null, deptName: null, deptType: "" },
    "a source page with no organization should restore to an empty organization context")
}

{
  const storage = createStorage()
  const harness = createContextHarness(origin)
  beginTodoContextLease({ storage, now: 1000, originContext: origin, targetContext: target, returnRoute: sourceRoute })
  harness.switchTo(third)
  const restored = restoreTodoContextLeaseForRoute(sourceRoute, harness.options(storage, 2000))
  assert.strictEqual(restored.restored, false)
  assert.strictEqual(restored.reason, "context_changed")
  assert.deepStrictEqual(harness.context, third,
    "returning to the source must not overwrite an organization the user selected explicitly")
  assert.strictEqual(storage.getItem(TODO_CONTEXT_LEASE_KEY), null)
}

{
  const storage = createStorage()
  const harness = createContextHarness(origin)
  beginTodoContextLease({ storage, now: 1000, originContext: origin, targetContext: target, returnRoute: sourceRoute })
  harness.switchTo(target)
  const rolledBack = rollbackTodoContextLease(harness.options(storage, 1500))
  assert.strictEqual(rolledBack.restored, true, "a failed route transition should immediately restore the origin")
  assert.deepStrictEqual(harness.context, { deptId: "1", deptName: "Test Store", deptType: "STORE" })
  assert.strictEqual(storage.getItem(TODO_CONTEXT_LEASE_KEY), null)
}

{
  const storage = createStorage()
  beginTodoContextLease({ storage, now: 1000, originContext: origin, targetContext: target, returnRoute: sourceRoute, ttlMs: 50 })
  const expired = readTodoContextLease({ storage, now: 1051 })
  assert.strictEqual(expired.lease, null)
  assert.strictEqual(expired.reason, "expired")
  assert.strictEqual(storage.getItem(TODO_CONTEXT_LEASE_KEY), null, "expired leases should be removed without a delayed restore")

  storage.setItem(TODO_CONTEXT_LEASE_KEY, "not-json")
  const corrupt = readTodoContextLease({ storage, now: 2000 })
  assert.strictEqual(corrupt.lease, null)
  assert.strictEqual(corrupt.reason, "invalid")
  assert.strictEqual(storage.getItem(TODO_CONTEXT_LEASE_KEY), null)

  storage.setItem(TODO_CONTEXT_LEASE_KEY, JSON.stringify({ version: TODO_CONTEXT_LEASE_VERSION + 1 }))
  const wrongVersion = readTodoContextLease({ storage, now: 2000 })
  assert.strictEqual(wrongVersion.lease, null)
  assert.strictEqual(wrongVersion.reason, "invalid")
  assert.strictEqual(storage.getItem(TODO_CONTEXT_LEASE_KEY), null)
}

{
  const storage = createStorage()
  const harness = createContextHarness(origin)
  const first = beginTodoContextLease({
    storage, now: 1000, originContext: origin,
    targetContext: target, returnRoute: sourceRoute
  })
  harness.switchTo(target)
  const nextTarget = { deptId: 4, deptName: "上海门店", deptType: "STORE" }
  const chained = beginTodoContextLease({
    storage,
    now: 1500,
    originContext: harness.context,
    targetContext: nextTarget,
    returnRoute: { path: "/inventory/transfer", fullPath: "/inventory/transfer" }
  })
  assert.strictEqual(chained.ok, true)
  assert.deepStrictEqual(chained.lease.origin, {
    hasContext: true,
    deptId: "1",
    deptName: "Test Store",
    deptType: "STORE"
  }, "consecutive temporary jumps should retain the first origin instead of forming a lease chain")
  assert.deepStrictEqual(chained.lease.returnRoute, sourceRoute,
    "consecutive temporary jumps should still return to the first source route")
  assert.strictEqual(chained.lease.target.deptId, "4")
  assert.notStrictEqual(chained.lease.leaseId, first.lease.leaseId,
    "each navigation must own a distinct lease revision")
  harness.switchTo(nextTarget)
  const staleRollback = rollbackTodoContextLease({
    ...harness.options(storage, 1600),
    expectedLeaseId: first.lease.leaseId
  })
  assert.strictEqual(staleRollback.reason, "lease_replaced")
  assert.deepStrictEqual(harness.context, nextTarget,
    "an older navigation must not roll back the current lease")
}

{
  const storage = createStorage()
  storage.setItem = () => { throw new Error("quota unavailable") }
  const failed = beginTodoContextLease({
    storage,
    now: 1000,
    originContext: origin,
    targetContext: target,
    returnRoute: sourceRoute
  })
  assert.deepStrictEqual(failed, { ok: false, reason: "storage_unavailable" },
    "cross-organization navigation must stop when the origin cannot be stored safely")
}

{
  const permissionSource = fs.readFileSync(path.resolve(__dirname, "../src/permission.js"), "utf8")
  const restoreCall = permissionSource.indexOf("restoreTodoContextLeaseForRoute(to")
  const contextGate = permissionSource.indexOf("shouldSelectShop(to.path)")
  assert.ok(restoreCall > -1 && contextGate > restoreCall,
    "the authenticated route guard should restore a matching lease before organization-required redirects")
  assert.ok(permissionSource.includes("clearTodoContextLease()"),
    "logging out or navigating without a token should discard stale context leases")
}

console.log("unifiedTodoContextLease tests passed")
