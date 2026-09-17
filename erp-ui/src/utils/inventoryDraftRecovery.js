const { createIndexedDbReceiveStore } = require('./purchaseReceiveRecovery')
const clone = value => JSON.parse(JSON.stringify(value))
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical)
  if (value && typeof value === 'object') return Object.keys(value).sort().reduce((out, key) => { if (value[key] !== undefined) out[key] = canonical(value[key]); return out }, {})
  return value
}

// The shared IndexedDB transaction selects one command across tabs and save/submit.
// Unknown results retain the immutable payload until an explicit recovery succeeds.
function createInventoryDraftRecovery({ storage, context, transport, createId, contextRevision = () => 0 }) {
  const flights = new Map()
  function capture(feature) {
    const value = context(feature) || {}
    if (!/^[1-9]\d*$/.test(String(value.actor || '')) || !/^[1-9]\d*$/.test(String(value.dept || ''))) throw new Error('请先登录并选择业务组织')
    return { actor: String(value.actor), dept: String(value.dept) }
  }
  function assertCurrent(feature, scope, revision = contextRevision()) {
    if (JSON.stringify(capture(feature)) !== JSON.stringify(scope) || revision !== contextRevision()) throw new Error('账号或组织已变化，请回到原组织核对上次操作')
  }
  const keyOf = (feature, scope, id) => [scope.actor, scope.dept, feature, id || 'new'].join(':')
  async function acknowledge(record, guard) {
    // The receipt and its private payload are finalized in one storage transaction.
    await storage.acknowledge(record.key, record.requestId, guard, { redact: true })
  }
  async function send(record) {
    const revision = contextRevision()
    assertCurrent(record.feature, record.scope, revision)
    if (flights.has(record.key)) return flights.get(record.key)
    const task = Promise.resolve().then(async () => {
      let response = record.result
      if (record.phase === 'ACTIVE') {
        assertCurrent(record.feature, record.scope, revision)
        try { response = await transport(record, () => assertCurrent(record.feature, record.scope, revision)) } catch (error) {
          const data = error && (error.data || (error.response && error.response.data))
          if (data && data.draftOutcome === 'REJECTED') {
            await storage.settle(record.key, record.requestId, { rejected: true })
            await acknowledge(record)
          }
          throw error
        }
        const idKey = record.feature === 'purchase' ? 'orderId' : 'returnId'
        if (!response || !response.data || !response.data[idKey] || response.data.version == null) throw new Error('保存结果不完整，原操作已保留，请核对上次保存')
        await storage.settle(record.key, record.requestId, response)
      }
      // Only acknowledge in the same authenticated context. A late response remains recoverable.
      assertCurrent(record.feature, record.scope, revision)
      if (response && response.rejected) { await acknowledge(record); throw new Error('上次操作未保存，可以修改内容后重试') }
      await acknowledge(record, () => assertCurrent(record.feature, record.scope, revision))
      return response
    }).finally(() => flights.delete(record.key))
    flights.set(record.key, task)
    return task
  }
  return {
    async pending(feature) {
      const scope = capture(feature)
      return (await storage.all()).filter(record => record.feature === feature && record.scope.actor === scope.actor && record.scope.dept === scope.dept && record.phase !== 'ACKED')
    },
    async recover(record) {
      const current = await storage.read(record.key)
      if (!current || current.requestId !== record.requestId || current.phase === 'ACKED') throw new Error('该操作已核对，请刷新列表查看结果')
      return send(current)
    },
    async submit(feature, action, data) {
      const revision = contextRevision(), scope = capture(feature), payload = clone(data), id = payload[feature === 'purchase' ? 'orderId' : 'returnId']
      const key = keyOf(feature, scope, id), fingerprint = JSON.stringify(canonical(payload))
      const head = await storage.read(key)
      assertCurrent(feature, scope, revision)
      const candidate = { feature, scope, payload, action, fingerprint, phase: 'ACTIVE', requestId: createId() }
      const record = await storage.reserve(key, candidate, head ? head.requestId : null)
      if (!record || record.phase === 'ACKED') throw new Error('操作状态已变化，请重新核对草稿')
      if (record.fingerprint !== fingerprint || record.action !== action) throw Object.assign(new Error('上次保存结果尚未确认；请先点击“核对上次保存”，当前修改已保留'), { pendingDraft: record })
      assertCurrent(feature, scope, revision)
      return send(record)
    }
  }
}
module.exports = { createInventoryDraftRecovery, createIndexedDbReceiveStore }
