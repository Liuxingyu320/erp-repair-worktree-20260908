const assert = require("assert")
const { createTransferCommandRecovery } = require("../src/utils/transferCommandRecovery")
function memory() {
  const data = new Map()
  return { data, getItem: key => data.has(key) ? data.get(key) : null,
    setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) }
}
function harness(options = {}) {
  const store = options.store || memory()
  const current = options.current || { actor: "11", dept: "201" }
  const events = []
  let sequence = 0
  const manager = createTransferCommandRecovery({
    storage: () => store, context: () => current,
    confirmRecovery: options.confirmRecovery, consumeRecovered: options.consumeRecovered,
    newId: () => "transfer:test-" + (++sequence),
    transport: async config => {
      events.push(["write", config.headers && config.headers["X-Request-Id"], config.data, config.url])
      return options.transport ? options.transport(config) : { code: 200, data: { transferId: 8 } }
    },
    status: async id => {
      events.push(["status", id])
      return options.status ? options.status(id) : { status: "SUCCEEDED" }
    }
  })
  return { manager, store, current, events }
}
const draft = (quantity = 2) => ({ url: "/inventory/transfer/save", method: "post",
  data: { details: [{ productId: 1, quantity }] } })
async function main() {
  let fail = true
  const h = harness({ transport: () => {
    if (fail) { fail = false; throw new Error("timeout after committed write") }
    return { code: 200, data: { transferId: 8 } }
  } })
  await assert.rejects(h.manager.run(draft()), /timeout/)
  const success = await h.manager.run(draft())
  assert.deepStrictEqual(h.events.map(event => event[0]), ["write", "status", "write"])
  assert.strictEqual(h.events[0][1], h.events[2][1], "timeout retry replays the original id")
  assert.strictEqual(h.store.data.size, 1, "success is durable until visible consumption")
  h.manager.acknowledge(success)
  assert.strictEqual(h.store.data.size, 0)
  const second = await h.manager.run(draft())
  assert.notStrictEqual(h.events[3][1], h.events[0][1], "acknowledged new intent gets a new id")
  h.manager.acknowledge(second)

  const blocked = harness({ transport: () => { throw new Error("offline") } })
  await assert.rejects(blocked.manager.run(draft()), /offline/)
  await assert.rejects(blocked.manager.run(draft(3)), /原内容/)
  await assert.rejects(blocked.manager.run(Object.assign(draft(), { url: "/inventory/transfer/submit" })), /原内容/)
  assert.strictEqual(blocked.events.length, 1, "unknown save also fences submit on the same draft")

  const reloaded = harness({ store: blocked.store })
  await reloaded.manager.run(draft())
  assert.strictEqual(reloaded.events[0][0], "status", "reload queries before replay")
  assert.strictEqual(reloaded.events[1][1], blocked.events[0][1])

  const deniedStatus = harness({ store: blocked.store, status: () => {
    const error = new Error("status rejected")
    error.response = { status: 200, data: { code: 500, msg: "scope unavailable" } }
    throw error
  } })
  await assert.rejects(deniedStatus.manager.run(draft()), /status rejected/)
  assert.strictEqual(blocked.store.data.size, 1, "failed status lookup must never release a pending mutation")

  const unavailable = harness({ store: { getItem: () => null,
    setItem: () => { throw new Error("quota") } } })
  await assert.rejects(unavailable.manager.run(draft()), /未能保存/)
  assert.strictEqual(unavailable.events.length, 0)

  const corruptStore = memory()
  corruptStore.setItem("erp:transfer-command:v2:11:201:transfer%3Anew", "{broken")
  const corrupt = harness({ store: corruptStore })
  await assert.rejects(corrupt.manager.run(draft()), /不可读/)
  assert.strictEqual(corrupt.events.length, 0)

  let releaseStatus
  const lease = harness({ store: blocked.store, status: () => new Promise(resolve => { releaseStatus = resolve }) })
  const changing = lease.manager.run(draft())
  lease.current.dept = "202"
  releaseStatus({ status: "SUCCEEDED" })
  await assert.rejects(changing, /业务组织已变化/)
  assert.strictEqual(lease.events.filter(event => event[0] === "write").length, 0)

  const rejected = harness({ status: () => ({ status: "NOT_FOUND" }), transport: () => {
    const error = new Error("quantity invalid")
    error.response = { status: 200, data: { code: 500, msg: "quantity invalid" } }
    throw error
  } })
  await assert.rejects(rejected.manager.run(draft()), /quantity invalid/)
  assert.strictEqual(rejected.store.data.size, 0, "transactional business rejection permits correction")

  const afterCommit = harness({ transport: () => {
    const error = new Error("response rendering failed after commit")
    error.response = { status: 200, data: { code: 500 } }
    throw error
  } })
  await assert.rejects(afterCommit.manager.run(draft()), /after commit/)
  assert.strictEqual(afterCommit.store.data.size, 1, "post-commit error must preserve the original id")
  await assert.rejects(afterCommit.manager.run(draft(3)), /原内容/)

  const badDept = harness()
  await assert.rejects(badDept.manager.run(Object.assign(draft(), { inventoryDeptId: 0 })), /业务组织/)
  assert.strictEqual(badDept.events.length, 0)

  let resolveWrite
  const concurrent = harness({ transport: () => new Promise(resolve => { resolveWrite = resolve }) })
  const first = concurrent.manager.run(draft())
  await assert.rejects(concurrent.manager.run(draft()), /正在处理/)
  resolveWrite({ code: 200 })
  concurrent.manager.acknowledge(await first)
  assert.strictEqual(concurrent.events.length, 1)

  const pending = harness({ store: blocked.store, status: () => ({ status: "PENDING" }) })
  await assert.rejects(pending.manager.run(draft()), /仍在处理中/)
  assert.strictEqual(pending.events.length, 1)

  const changedRecovery = harness({ store: blocked.store, confirmRecovery: async () => true,
    consumeRecovered: async result => { assert.strictEqual(result.data.transferId, 8) } })
  await assert.rejects(changedRecovery.manager.run(Object.assign(draft(3), { url: '/inventory/transfer/submit' })),
    error => error.code === 'TRANSFER_ORIGINAL_CONFIRMED' && error.notified === true)
  assert.strictEqual(changedRecovery.events[1][3], '/inventory/transfer/save', 'reconcile the original action, never submit the new action')
  assert.strictEqual(changedRecovery.events[1][2].details[0].quantity, 2)
  assert.strictEqual(changedRecovery.store.data.size, 0, 'release only after visible acknowledgment')

  const interruptedStore = memory()
  const interrupted = harness({ store: interruptedStore, transport: () => { throw Error('timeout') } })
  await assert.rejects(interrupted.manager.run(draft()), /timeout/)
  const lostAcknowledgment = harness({ store: interruptedStore, confirmRecovery: async () => true,
    consumeRecovered: async () => { throw Error('page closed before acknowledgment') } })
  await assert.rejects(lostAcknowledgment.manager.run(draft(4)), /page closed/)
  assert.strictEqual(interruptedStore.data.size, 1)
  console.log("transfer command recovery: 14 scenario groups passed")
}
main().catch(error => { console.error(error); process.exitCode = 1 })
