const assert = require('assert')
const { createPurchaseReceiveRecovery } = require('../src/utils/purchaseReceiveRecovery')
const copy = value => value == null ? value : JSON.parse(JSON.stringify(value))
function memoryStore() {
  const records = new Map()
  return { records,
    async read(key) { return copy(records.get(key) || null) },
    async all() { return copy([...records.values()]) },
    async reserve(key, candidate, head) {
      const current = records.get(key)
      if ((current ? current.requestId : null) === head && (!current || current.phase === 'ACKED')) records.set(key, copy({ key, ...candidate }))
      return copy(records.get(key) || null)
    },
    async settle(key, id, result) {
      const current = records.get(key)
      if (current && current.requestId === id && current.phase === 'ACTIVE') records.set(key, copy({ ...current, phase: 'SETTLED', result }))
      return copy(records.get(key) || null)
    },
    async acknowledge(key, id, guard) {
      if (guard) guard()
      const current = records.get(key)
      if (current && current.requestId === id && current.phase === 'SETTLED') records.set(key, copy({ ...current, phase: 'ACKED' }))
      return copy(records.get(key) || null)
    }
  }
}
const body = extra => ({ warehouseId: 9, arrivedTime: '2026-09-12 09:00:00', supplierBatchNo: 'A', deliveryNoteNo: 'D', remark: '原备注', items: [{ detailId: 101, receiveQuantity: 2 }], ...extra })
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const flush = () => new Promise(resolve => setImmediate(resolve))
let nextId = 0
function fixture(store = memoryStore()) {
  const state = { actor: 7, dept: 9, allowed: true }, calls = [], batches = new Map()
  function response(orderId, payload, id) {
    if (!batches.has(id)) batches.set(id, { requestId: id, purchaseOrderId: Number(orderId), warehouseId: payload.warehouseId,
      receiptBatchId: 800 + batches.size, batchNo: 'B' + batches.size, receivedQuantity: payload.items.reduce((n, item) => n + item.receiveQuantity, 0) })
    return { code: 200, data: copy(batches.get(id)) }
  }
  const deps = { storage: store, context: () => state, createId: () => 'receive:test-' + ++nextId,
    transport: async (...args) => { calls.push(copy(args)); return response(...args) } }
  return { store, state, calls, batches, response, deps, service: createPurchaseReceiveRecovery(deps),
    newService: () => createPurchaseReceiveRecovery(deps) }
}
const cases = []
const test = (name, run) => cases.push({ name, run })
const runNew = (service, payload = body(), more = {}) => service.run({ orderId: 31, payload, observedRequestId: null, ...more })

test('first transport follows durable reservation; next arrival has a new ID', async () => {
  const h = fixture()
  const transport = h.deps.transport
  h.deps.transport = async (...args) => {
    const stored = [...h.store.records.values()][0]
    assert.equal(stored.phase, 'ACTIVE'); assert.equal(stored.requestId, args[2]); assert.deepStrictEqual(stored.payload, args[1])
    return transport(...args)
  }
  const service = h.newService(), first = await runNew(service)
  assert.equal((await service.list()).length, 1)
  assert.equal(await service.acknowledge(first), true)
  assert.equal((await service.list()).length, 0)
  const head = await service.inspect(31)
  const second = await runNew(service, body({ remark: '第二批' }), head)
  assert.notEqual(first.data.requestId, second.data.requestId); assert.equal(h.batches.size, 2)
})
test('lost response survives reopen and ignores changed payload or latest remaining quantity', async () => {
  const h = fixture(); let lose = true
  h.deps.transport = async (...args) => { h.calls.push(copy(args)); const result = h.response(...args); if (lose) { lose = false; throw new Error('timeout after commit') } return result }
  const service = h.newService()
  await assert.rejects(runNew(service), /timeout/)
  const reopened = h.newService()
  const result = await reopened.run({ orderId: 31, recoveryOnly: true, buildPayload: () => { throw new Error('must not query zero remaining') } })
  assert.deepStrictEqual(h.calls[0], h.calls[1]); assert.equal(h.batches.size, 1); assert.equal(result.purchaseReceiveRecovery.recovered, true)
})
for (const error of [new Error('timeout'), { response: { status: 500 } }, { response: { status: 403 } }, { response: { status: 200, data: { code: 500, receiveOutcome: 'REJECTED' } } }]) {
  test('no current backend exception safely clears the command: ' + JSON.stringify(error), async () => {
    const h = fixture(); h.deps.transport = async () => { throw error }; const service = h.newService()
    await assert.rejects(runNew(service)); assert.equal((await service.list()).length, 1); assert.equal([...h.store.records.values()][0].phase, 'ACTIVE')
  })
}
for (const method of ['all', 'read', 'reserve']) {
  test('storage ' + method + ' failure sends nothing', async () => {
    const h = fixture(); h.store[method] = async () => { throw new Error('disk failure') }
    await assert.rejects(runNew(h.service), /存储/); assert.equal(h.calls.length, 0)
  })
}
test('settlement storage failure retains ACTIVE after the committed write', async () => {
  const h = fixture(); h.store.settle = async () => { throw new Error('disk full') }
  await assert.rejects(runNew(h.service), /存储/); assert.equal(h.calls.length, 1); assert.equal([...h.store.records.values()][0].phase, 'ACTIVE')
})
test('corrupt local record blocks a new command', async () => {
  const h = fixture(); h.store.records.set('7:9:31', { key: '7:9:31', version: 1, requestId: 'broken' })
  await assert.rejects(runNew(h.service), /损坏/); assert.equal(h.calls.length, 0)
})
for (const mismatch of [{ requestId: 'wrong:request' }, { purchaseOrderId: 32 }, { warehouseId: 8 }, { receiptBatchId: null }, { receivedQuantity: 3 }]) {
  test('mismatched result remains pending: ' + JSON.stringify(mismatch), async () => {
    const h = fixture(); h.deps.transport = async (...args) => ({ data: { ...h.response(...args).data, ...mismatch } }); const service = h.newService()
    await assert.rejects(runNew(service), /无法确认/); assert.equal((await service.list()).length, 1)
  })
}
test('missing captured generation does not invent a new command', async () => {
  const h = fixture(); await assert.rejects(h.service.run({ orderId: 31, payload: body() }), /重新打开/); assert.equal(h.calls.length, 0)
})
test('context changes during fresh-detail read stop reservation and transport', async () => {
  const h = fixture(), pending = deferred()
  const action = runNew(h.service, undefined, { buildPayload: () => pending.promise })
  await flush(); h.state.dept = 10; pending.resolve(body())
  await assert.rejects(action, /变化/); assert.equal(h.calls.length, 0); assert.equal(h.store.records.size, 0)
})
test('context changes after dispatch preserve original ACTIVE record', async () => {
  const h = fixture(), pending = deferred(); h.deps.transport = (...args) => { h.calls.push(args); return pending.promise }
  const service = h.newService(), action = runNew(service)
  await flush(); h.state.actor = 8; pending.resolve(h.response(...h.calls[0])); await assert.rejects(action, /变化/)
  assert.equal([...h.store.records.values()][0].phase, 'ACTIVE'); assert.equal((await service.list()).length, 0)
})
test('permission removal prevents writes', async () => {
  const h = fixture(); h.state.allowed = false
  await assert.rejects(runNew(h.service), /权限/); assert.equal(h.calls.length, 0)
})
test('same page double click is rejected while original request runs', async () => {
  const h = fixture(), pending = deferred(); h.deps.transport = (...args) => { h.calls.push(args); return pending.promise }
  const service = h.newService(), action = runNew(service); await flush()
  await assert.rejects(runNew(service), /正在处理/); pending.resolve(h.response(...h.calls[0])); await action
  assert.equal(h.calls.length, 1)
})
test('two windows share one command; reverse completion and acknowledgements are safe', async () => {
  const h = fixture(), requests = []
  h.deps.transport = (...args) => { const deferredResponse = deferred(); requests.push({ args, ...deferredResponse }); return deferredResponse.promise }
  const a = h.newService(), b = h.newService(), first = runNew(a), second = runNew(b, body({ remark: '窗口B新输入' }))
  await flush(); assert.equal(requests.length, 2); assert.deepStrictEqual(requests[0].args, requests[1].args)
  requests[1].resolve(h.response(...requests[1].args)); const resultB = await second; await b.acknowledge(resultB)
  requests[0].resolve(h.response(...requests[0].args)); const resultA = await first; await a.acknowledge(resultA)
  assert.equal(h.batches.size, 1); assert.equal([...h.store.records.values()][0].phase, 'ACKED')
})
test('late old dialog after another window completed cannot submit a second arrival', async () => {
  const h = fixture(), b = h.newService(), first = await runNew(h.service); await h.service.acknowledge(first)
  const late = await runNew(b, body({ remark: '迟到的新输入' }), { buildPayload: () => { throw new Error('must not recalculate') } })
  assert.equal(h.calls.length, 1); assert.equal(late.data.requestId, first.data.requestId); assert.equal(late.purchaseReceiveRecovery.recovered, true)
})
test('late atomic reservation sees an already acknowledged newer generation', async () => {
  const h = fixture(), build = deferred(), a = h.newService(), b = h.newService()
  const first = runNew(a, undefined, { buildPayload: () => build.promise }); await flush()
  const second = await runNew(b); await b.acknowledge(second); build.resolve(body({ remark: 'A late' }))
  const resultA = await first; assert.equal(h.calls.length, 1); assert.equal(resultA.data.requestId, second.data.requestId)
})
test('old acknowledgement cannot erase a newer command', async () => {
  const h = fixture(), a = h.service, b = h.newService(), firstA = await runNew(a)
  const firstB = await b.run({ orderId: 31, recoveryOnly: true }); await b.acknowledge(firstB)
  await runNew(b, body({ remark: '第二笔' }), await b.inspect(31))
  const before = [...h.store.records.values()][0]; assert.equal(await a.acknowledge(firstA), false)
  assert.deepStrictEqual([...h.store.records.values()][0], before)
})
test('ack failure keeps SETTLED recoverable without another transport', async () => {
  const h = fixture(), first = await runNew(h.service); h.store.acknowledge = async () => { throw new Error('disk') }
  assert.equal(await h.service.acknowledge(first), false)
  const recovered = await h.newService().run({ orderId: 31, recoveryOnly: true }); assert.equal(h.calls.length, 1); assert.equal(recovered.data.requestId, first.data.requestId)
})
test('recovery-only missing command never creates one', async () => {
  const h = fixture(); await assert.rejects(h.service.run({ orderId: 31, recoveryOnly: true })); assert.equal(h.calls.length, 0)
})
async function main() {
  for (const test of cases) {
    let timer
    try { await Promise.race([test.run(), new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('scenario timed out')), 4000) })]) }
    catch (error) { error.message = test.name + ': ' + error.message; throw error }
    finally { clearTimeout(timer) }
  }
  console.log('purchaseReceiveRecovery: ' + cases.length + ' cases passed; 0 failed; 0 skipped (atomic adapter unit tests)')
}
if (require.main === module) main().catch(error => { console.error(error); process.exitCode = 1 })
module.exports = { memoryStore, fixture, body, deferred, flush }
