const assert = require("assert")
const {
  DEFAULT_TODO_ACTION_RETURN_TTL_MS,
  TODO_ACTION_RETURN_KEY,
  beginTodoActionReturn,
  clearTodoActionReturn,
  consumeTodoActionReturn,
  readTodoActionReturn,
  returnAfterTodoAction
} = require("../src/utils/todoActionReturn")

function createStorage() {
  const values = new Map()
  return {
    getItem(key) { return values.has(key) ? values.get(key) : null },
    setItem(key, value) { values.set(key, String(value)) },
    removeItem(key) { values.delete(key) }
  }
}

async function run() {
  const storage = createStorage()
  const started = beginTodoActionReturn({
    storage,
    now: 1000,
    returnRoute: { path: "/workbench/todo", fullPath: "/workbench/todo?category=approval&pageNum=3" },
    todoKey: "inventory:INV_TRANSFER_APPROVAL:1",
    nextTodoKey: "approval:INV_TRANSFER_APPROVAL:2",
    todoType: "INV_TRANSFER_APPROVAL",
    businessId: "701",
    scrollTop: 480
  })
  assert.strictEqual(started.ok, true)
  assert.deepStrictEqual(readTodoActionReturn({ storage, now: 1001 }).context, started.context)

  const replacements = []
  const returned = await returnAfterTodoAction({
    storage,
    now: 1002,
    todoType: "INV_TRANSFER_APPROVAL",
    businessId: "701",
    router: { replace(location) { replacements.push(location); return Promise.resolve() } }
  })
  assert.strictEqual(returned.returned, true)
  assert.deepStrictEqual(replacements, ["/workbench/todo?category=approval&pageNum=3"])
  assert.ok(storage.getItem(TODO_ACTION_RETURN_KEY), "return navigation must keep focus state until the todo page consumes it")

  const consumed = consumeTodoActionReturn({ storage, now: 1003 })
  assert.strictEqual(consumed.context.nextTodoKey, "approval:INV_TRANSFER_APPROVAL:2")
  assert.strictEqual(storage.getItem(TODO_ACTION_RETURN_KEY), null)

  beginTodoActionReturn({
    storage,
    now: 1500,
    returnRoute: { path: "/workbench/todo", fullPath: "/workbench/todo?pageNum=2" },
    todoKey: "inventory:INV_TRANSFER_APPROVAL:701",
    todoType: "INV_TRANSFER_APPROVAL",
    businessId: "701"
  })
  const mismatchReplacements = []
  const mismatch = await returnAfterTodoAction({
    storage,
    now: 1501,
    todoType: "INV_TRANSFER_APPROVAL",
    businessId: "999",
    router: { replace(location) { mismatchReplacements.push(location); return Promise.resolve() } }
  })
  assert.strictEqual(mismatch.returned, false)
  assert.strictEqual(mismatch.reason, "target_mismatch")
  assert.deepStrictEqual(mismatchReplacements, [], "a stale return context must not navigate another transfer")
  assert.strictEqual(storage.getItem(TODO_ACTION_RETURN_KEY), null)

  assert.strictEqual(beginTodoActionReturn({
    storage,
    returnRoute: { path: "/inventory/transfer", fullPath: "/inventory/transfer" },
    todoKey: "unsafe",
    todoType: "INV_TRANSFER_APPROVAL",
    businessId: "701"
  }).ok, false, "only the internal unified todo route may be stored")
  assert.strictEqual(beginTodoActionReturn({
    storage,
    returnRoute: { path: "/workbench/todo", fullPath: "https://evil.invalid" },
    todoKey: "unsafe",
    todoType: "INV_TRANSFER_APPROVAL",
    businessId: "701"
  }).ok, false, "external return URLs must be rejected")

  beginTodoActionReturn({
    storage,
    now: 2000,
    returnRoute: { path: "/workbench/todo", fullPath: "/workbench/todo" },
    todoKey: "expired",
    todoType: "INV_TRANSFER_APPROVAL",
    businessId: "701"
  })
  const expired = readTodoActionReturn({ storage, now: 2000 + DEFAULT_TODO_ACTION_RETURN_TTL_MS })
  assert.strictEqual(expired.context, null)
  assert.strictEqual(expired.reason, "expired")
  assert.strictEqual(storage.getItem(TODO_ACTION_RETURN_KEY), null)

  beginTodoActionReturn({
    storage,
    now: 3000,
    returnRoute: { path: "/workbench/todo", fullPath: "/workbench/todo" },
    todoKey: "clear-me",
    todoType: "INV_TRANSFER_APPROVAL",
    businessId: "701"
  })
  assert.strictEqual(clearTodoActionReturn({ storage }).ok, true)
}

run().then(() => console.log("unified todo action return tests passed"))
