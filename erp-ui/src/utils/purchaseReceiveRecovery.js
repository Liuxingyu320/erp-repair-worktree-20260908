const ID = /^[1-9]\d*$/
const REQUEST_ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/
const clone = value => JSON.parse(JSON.stringify(value))
function canonical(value) {
  if (value === null || typeof value !== "object") return JSON.stringify(value)
  if (Array.isArray(value)) return "[" + value.map(canonical).join(",") + "]"
  return "{" + Object.keys(value).sort().map(key => JSON.stringify(key) + ":" + canonical(value[key])).join(",") + "}"
}
function receiveError(message, code = "PURCHASE_RECEIVE_PENDING") {
  return Object.assign(new Error(message), { code })
}

// One readwrite transaction chooses the command shared by every tab. Keep ACKED
// generations so a late confirmation from an older dialog cannot start a new receipt.
function createIndexedDbReceiveStore(indexedDB, name = "erp-purchase-receive-v1") {
  let opening
  function database() {
    if (!opening) opening = new Promise((resolve, reject) => {
      if (!indexedDB) { reject(new Error("IndexedDB unavailable")); return }
      const request = indexedDB.open(name, 1)
      request.onupgradeneeded = () => request.result.createObjectStore("commands", { keyPath: "key" })
      request.onerror = () => reject(request.error)
      request.onblocked = () => reject(new Error("IndexedDB blocked"))
      request.onsuccess = () => {
        request.result.onversionchange = () => { request.result.close(); opening = null }
        resolve(request.result)
      }
    }).catch(error => { opening = null; throw error })
    return opening
  }
  async function transaction(mode, operation) {
    const db = await database()
    return new Promise((resolve, reject) => {
      const tx = db.transaction("commands", mode), store = tx.objectStore("commands")
      let result
      tx.oncomplete = () => resolve(result)
      tx.onerror = tx.onabort = () => reject(tx.error || new Error("IndexedDB transaction failed"))
      try { operation(store, value => { result = value }) } catch (error) { tx.abort(); reject(error) }
    })
  }
  function mutate(key, update) {
    return transaction("readwrite", (store, done) => {
      const request = store.get(key)
      request.onsuccess = () => {
        try {
          const current = request.result || null, next = update(current)
          if (next !== current) store.put(next)
          done(next)
        } catch (_) { store.transaction.abort() }
      }
    })
  }
  return {
    read: key => transaction("readonly", (store, done) => { const request = store.get(key); request.onsuccess = () => done(request.result || null) }),
    all: () => transaction("readonly", (store, done) => { const request = store.getAll(); request.onsuccess = () => done(request.result) }),
    reserve: (key, candidate, expectedHead) => mutate(key, current => {
      if ((current ? current.requestId : null) !== expectedHead || (current && current.phase !== "ACKED")) return current
      return Object.assign({ key }, candidate)
    }),
    settle: (key, requestId, result) => mutate(key, current => current && current.requestId === requestId && current.phase === "ACTIVE"
      ? Object.assign({}, current, { phase: "SETTLED", result }) : current),
    redact: (key, requestId) => mutate(key, current => current && current.requestId === requestId && current.phase === "ACKED"
      ? Object.assign({}, current, { payload: null, result: null, fingerprint: null }) : current),
    acknowledge: (key, requestId, guard, options = {}) => mutate(key, current => {
      // Context may have changed while the database transaction was queued.
      if (guard) guard()
      return current && current.requestId === requestId && current.phase === "SETTLED"
        ? Object.assign({}, current, { phase: "ACKED" }, options.redact ? { payload: null, result: null, fingerprint: null } : {}) : current
    })
  }
}

function createPurchaseReceiveRecovery({ storage, context, transport, createId }) {
  const running = new Set(), receipts = new WeakMap()
  function capture() {
    const value = context() || {}, scope = { actor: String(value.actor || ""), dept: String(value.dept || "") }
    if (!ID.test(scope.actor) || !ID.test(scope.dept) || value.allowed === false)
      throw receiveError("请使用有收货权限的账号并选择原业务组织", "PURCHASE_RECEIVE_CONTEXT_CHANGED")
    return scope
  }
  function assertCurrent(scope, isCurrent) {
    if (canonical(capture()) !== canonical(scope) || (isCurrent && !isCurrent()))
      throw receiveError("账号、组织或收货窗口已变化，请返回原操作核对", "PURCHASE_RECEIVE_CONTEXT_CHANGED")
  }
  function keyOf(scope, orderId) {
    if (!ID.test(String(orderId || ""))) throw receiveError("采购单身份无效")
    return scope.actor + ":" + scope.dept + ":" + orderId
  }
  async function stored(method, ...args) {
    try { return await storage[method](...args) } catch (_) {
      throw receiveError("浏览器无法保存或读取收货记录，已停止发送；请恢复本地存储后核对上一笔", "PURCHASE_RECEIVE_STORAGE_UNAVAILABLE")
    }
  }
  function validatePayload(payload, scope) {
    if (!payload || String(payload.warehouseId) !== scope.dept || !String(payload.arrivedTime || "").trim()
        || !Array.isArray(payload.items) || !payload.items.length)
      throw receiveError("收货内容不完整，请检查仓库、到货时间与数量")
    const seen = new Set()
    payload.items.forEach(item => {
      if (!item || !ID.test(String(item.detailId || "")) || seen.has(String(item.detailId))
          || !Number.isFinite(Number(item.receiveQuantity)) || Number(item.receiveQuantity) <= 0)
        throw receiveError("收货明细或数量无效")
      seen.add(String(item.detailId))
    })
    return payload
  }
  function validateResult(response, record) {
    const data = response && response.data
    if ((response && response.code != null && Number(response.code) !== 200) || !data || data.requestId !== record.requestId || String(data.purchaseOrderId) !== record.orderId
        || String(data.warehouseId) !== record.dept || !ID.test(String(data.receiptBatchId || ""))
        || !String(data.batchNo || "").trim() || !Number.isFinite(Number(data.receivedQuantity)) || Number(data.receivedQuantity) <= 0)
      throw receiveError("收货结果身份无法确认，原操作已保留，请核对上一笔")
    const quantity = record.payload.items.reduce((sum, item) => sum + Number(item.receiveQuantity), 0)
    if (Math.abs(Number(data.receivedQuantity) - quantity) > 0.000001)
      throw receiveError("收货结果数量无法确认，原操作已保留，请核对上一笔")
    return { requestId: data.requestId, purchaseOrderId: data.purchaseOrderId, warehouseId: data.warehouseId,
      receiptBatchId: data.receiptBatchId, batchNo: data.batchNo, arrivedTime: data.arrivedTime, receivedQuantity: data.receivedQuantity }
  }
  function validateRecord(record, key, scope) {
    if (!record) return null
    try {
      if (record.version !== 1 || record.actor !== scope.actor || record.dept !== scope.dept || record.key !== key
          || keyOf(scope, record.orderId) !== key || !REQUEST_ID.test(record.requestId || "")
          || !["ACTIVE", "SETTLED", "ACKED"].includes(record.phase) || record.fingerprint !== canonical(record.payload)) throw new Error()
      validatePayload(record.payload, scope)
      if (record.phase !== "ACTIVE") validateResult({ data: record.result }, record)
      return record
    } catch (_) { throw receiveError("收货恢复记录损坏，已阻止新收货，请保留记录并联系管理员核对", "PURCHASE_RECEIVE_STORAGE_INVALID") }
  }
  async function list(scope = capture()) {
    assertCurrent(scope)
    const records = await stored("all")
    assertCurrent(scope)
    const prefix = scope.actor + ":" + scope.dept + ":"
    return records.filter(record => String(record.key || "").indexOf(prefix) === 0)
      .map(record => validateRecord(record, record.key, scope)).filter(record => record.phase !== "ACKED")
  }
  async function inspect(orderId, scope = capture()) {
    assertCurrent(scope)
    const key = keyOf(scope, orderId), record = validateRecord(await stored("read", key), key, scope)
    assertCurrent(scope)
    return { observedRequestId: record ? record.requestId : null, pending: record && record.phase !== "ACKED" ? record : null }
  }
  async function run({ orderId, payload, buildPayload, scope = capture(), isCurrent, recoveryOnly = false, requestId, observedRequestId }) {
    scope = clone(scope)
    const key = keyOf(scope, orderId)
    if (running.has(key)) throw receiveError("该笔收货正在处理，请稍候")
    running.add(key)
    try {
      assertCurrent(scope, isCurrent)
      await list(scope)
      let record = validateRecord(await stored("read", key), key, scope)
      assertCurrent(scope, isCurrent)
      if (requestId && (!record || record.requestId !== requestId)) throw receiveError("该笔收货已由另一窗口核对，请刷新恢复列表")
      if (!recoveryOnly && observedRequestId === undefined) throw receiveError("请重新打开收货窗口确认原操作身份")
      let recovered = !!record && (record.phase !== "ACKED" || record.requestId !== observedRequestId || recoveryOnly)
      if (!recovered) {
        if (recoveryOnly || (record ? record.requestId : null) !== observedRequestId)
          throw receiveError("原收货记录已变化，请重新核对")
        const body = validatePayload(clone(buildPayload ? await buildPayload() : payload), scope)
        assertCurrent(scope, isCurrent)
        const id = createId()
        if (!REQUEST_ID.test(id || "")) throw receiveError("无法生成安全收货标识，未发送请求")
        const candidate = { version: 1, actor: scope.actor, dept: scope.dept, orderId: String(orderId),
          requestId: id, payload: body, fingerprint: canonical(body), phase: "ACTIVE" }
        record = validateRecord(await stored("reserve", key, candidate, observedRequestId), key, scope)
        if (!record) throw receiveError("原收货记录已变化，请重新核对")
        recovered = record.requestId !== id
      }
      assertCurrent(scope, isCurrent)
      let result = record.result
      if (record.phase === "ACTIVE") {
        // Current receive errors carry no safe rollback marker: retain every unknown result.
        const response = await transport(record.orderId, clone(record.payload), record.requestId, clone(scope))
        assertCurrent(scope, isCurrent)
        result = validateResult(response, record)
        const saved = validateRecord(await stored("settle", key, record.requestId, result), key, scope)
        if (!saved || saved.requestId !== record.requestId) throw receiveError("上一笔已由另一窗口核对，请刷新查看")
      }
      assertCurrent(scope, isCurrent)
      const response = { code: 200, data: clone(result), purchaseReceiveRecovery: { recovered, requestId: record.requestId } }
      receipts.set(response, { key, scope, requestId: record.requestId })
      return response
    } finally { running.delete(key) }
  }
  async function acknowledge(response) {
    const receipt = receipts.get(response)
    if (!receipt) return false
    try {
      assertCurrent(receipt.scope)
      const record = await stored("acknowledge", receipt.key, receipt.requestId, () => assertCurrent(receipt.scope))
      return !!record && record.requestId === receipt.requestId && record.phase === "ACKED"
    } catch (_) { return false } finally { receipts.delete(response) }
  }
  return { capture, assertCurrent, list, inspect, run, acknowledge }
}

let browserRecovery
function getPurchaseReceiveRecovery() {
  if (!browserRecovery) browserRecovery = createPurchaseReceiveRecovery({
    storage: createIndexedDbReceiveStore(typeof indexedDB === "undefined" ? null : indexedDB),
    context: () => {
      const store = require("@/store").default
      const { getSelectedInventoryDeptId } = require("@/utils/shopContext")
      const { checkPermi } = require("@/utils/permission")
      return { actor: store.getters.id, dept: getSelectedInventoryDeptId(), allowed: checkPermi(["inv:purchase:receive"]) }
    },
    createId: () => {
      if (typeof crypto === "undefined" || !crypto.getRandomValues) throw receiveError("浏览器无法生成安全收货标识")
      return "receive:" + Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, "0")).join("")
    },
    transport: (orderId, payload, requestId, scope) => require("@/api/inventory/purchase").receivePurchase(orderId, payload, requestId, scope)
  })
  return browserRecovery
}
function purchaseReceiveResultMessage(response) {
  const result = response.data
  const prefix = response.purchaseReceiveRecovery.recovered ? "上一笔收货已核对，本次新输入尚未提交。" : "收货成功。"
  return prefix + "收货批次：" + result.batchNo + "（" + result.receiptBatchId + "）"
}
module.exports = { createIndexedDbReceiveStore, createPurchaseReceiveRecovery, getPurchaseReceiveRecovery, purchaseReceiveResultMessage }
