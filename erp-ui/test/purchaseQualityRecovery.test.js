const assert = require("node:assert/strict")
const { test } = require("node:test")
const { createPurchaseQualityRecovery } = require("../src/utils/purchaseQualityRecovery")

function fixture() {
  const values = new Map(), calls = []
  const ctx = { actor: 7, dept: 20 }
  let serial = 0, fail = true
  const options = {
    context: () => ctx,
    storage: () => ({ getItem: k => values.get(k), setItem: (k, v) => values.set(k, v), removeItem: k => values.delete(k) }),
    createId: () => "qc-test-" + (++serial),
    confirmRecovery: async () => true,
    transport: async (id, data, dept) => {
      calls.push({ id, data: JSON.parse(JSON.stringify(data)), dept })
      if (fail) throw new Error("lost response")
      return { code: 200 }
    }
  }
  return { options, calls, values, ctx, succeed: () => { fail = false } }
}
const payload = { receiptBatchId: 5, items: [{ batchDetailId: 6, acceptedQuantity: 20 }] }

test("lost response survives recreation and replays the same request identity and body", async () => {
  const f = fixture()
  await assert.rejects(createPurchaseQualityRecovery(f.options).submit(10, payload), /lost/)
  f.succeed()
  await createPurchaseQualityRecovery(f.options).submit(10, payload)
  assert.deepEqual(f.calls[1], f.calls[0])
  assert.equal(f.values.size, 0)
})
test("changed input reconciles the previous body and never submits new quantities", async () => {
  const f = fixture(), recovery = createPurchaseQualityRecovery(f.options)
  await assert.rejects(recovery.submit(10, payload))
  f.succeed()
  await assert.rejects(recovery.submit(10, { ...payload, items: [{ acceptedQuantity: 40 }] }), /本次修改尚未提交/)
  assert.deepEqual(f.calls[1], f.calls[0])
})
test("reopening pending batches can recover even after the original batch is completed", async () => {
  const f = fixture()
  await assert.rejects(createPurchaseQualityRecovery(f.options).submit(10, payload))
  f.succeed()
  await createPurchaseQualityRecovery(f.options).recover(10)
  assert.equal(f.calls[1].data.requestId, f.calls[0].data.requestId)
})
test("an explicit rolled-back business rejection permits corrected input", async () => {
  const f = fixture()
  f.options.transport = async () => { throw { response: { data: { qualityCheckOutcome: "REJECTED" } } } }
  const recovery = createPurchaseQualityRecovery(f.options)
  await assert.rejects(recovery.submit(10, payload))
  assert.equal(f.values.size, 0)
  f.options.transport = async (id, data) => { f.calls.push(data); return { code: 200 } }
  await recovery.submit(10, payload)
  assert.equal(f.calls[0].requestId, "qc-test-2")
})
test("no storage or a changed warehouse prevents dispatch", async () => {
  const f = fixture()
  f.options.storage = () => null
  await assert.rejects(createPurchaseQualityRecovery(f.options).submit(10, payload), /本地存储/)
  assert.equal(f.calls.length, 0)
  const g = fixture()
  const promise = createPurchaseQualityRecovery(g.options).submit(10, payload)
  g.ctx.dept = 30
  await assert.rejects(promise, /仓库已变化/)
  assert.equal(g.calls.length, 0)
})

test("legacy whole-order recovery keeps its endpoint kind and original body", async () => {
  const f = fixture(), calls = []
  let lost = true
  f.options.transport = async (id, data, dept, kind) => {
    calls.push({ id, data, dept, kind })
    if (lost) throw new Error("lost response")
    return { code: 200 }
  }
  await assert.rejects(createPurchaseQualityRecovery(f.options).submit(10, { qcResult: "passed" }, "legacy"))
  lost = false
  await createPurchaseQualityRecovery(f.options).recover(10)
  assert.deepEqual(calls[1], calls[0])
  assert.equal(calls[1].kind, "legacy")
})
