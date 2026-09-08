const { isPersistentTransferCommand, createPersistentCommandRequestId } = require("./persistentCommand")
const PREFIX = "erp:transfer-command:v2:"

function canonical(value) {
  if (value === undefined) return "null"
  if (value === null || typeof value !== "object") return JSON.stringify(value)
  if (Array.isArray(value)) return "[" + value.map(canonical).join(",") + "]"
  return "{" + Object.keys(value).filter(key => value[key] !== undefined).sort()
    .map(key => JSON.stringify(key) + ":" + canonical(value[key])).join(",") + "}"
}

function resource(config) {
  const path = config.url
  const body = config.data || {}
  if (/\/(save|submit|approve)$/.test(path)) return "transfer:" + (body.transferId || "new")
  let match = path.match(/\/discrepancies\/([^/]+)\//)
  if (match) return "discrepancy:" + match[1]
  match = path.match(/\/(?:shipment\/receive|shipments)\/([^/]+)/)
  if (match) return "shipment:" + match[1]
  match = path.match(/\/transfer\/(?:deliver\/|receive\/|delete\/)?([^/]+)/)
  return "transfer:" + (match ? match[1] : path)
}

function commandError(message, code = "TRANSFER_COMMAND_PENDING") {
  const error = new Error(message)
  error.code = code
  error.notified = false
  return error
}

function explicitId(config) {
  const headers = config.headers || {}
  const key = Object.keys(headers).find(name => name.toLowerCase() === "x-request-id")
  return key && headers[key]
}

function confirmedRejection(error) {
  const response = error && error.response
  // Even an application error can occur after commit (for example during response rendering).
  return response && Number(response.status) === 200 && response.data &&
    response.data.code != null && Number(response.data.code) !== 200
}

function createTransferCommandRecovery({ storage, context, transport, status,
  confirmRecovery, consumeRecovered, newId = createPersistentCommandRequestId }) {
  const running = new Set()
  const receipts = new WeakMap()
  const getStorage = () => {
    try {
      const target = storage()
      if (!target) throw new Error()
      return target
    } catch (_) {
      throw commandError("浏览器无法保存调拨操作，请启用本地存储后重试", "TRANSFER_STORAGE_UNAVAILABLE")
    }
  }
  const read = key => {
    try {
      const raw = getStorage().getItem(key)
      return raw == null ? null : JSON.parse(raw)
    } catch (error) {
      if (error.code) throw error
      throw commandError("调拨恢复记录不可读，已阻止重复提交，请联系管理员核对", "TRANSFER_STORAGE_INVALID")
    }
  }
  const write = (key, record) => {
    const encoded = JSON.stringify(record)
    try {
      const target = getStorage()
      target.setItem(key, encoded)
      if (target.getItem(key) !== encoded) throw new Error()
    } catch (_) {
      throw commandError("调拨操作记录未能保存，暂不能继续提交", "TRANSFER_STORAGE_UNAVAILABLE")
    }
  }
  const scopeOf = config => {
    const current = context()
    const actor = String(current.actor || "")
    const dept = String(config.inventoryDeptId !== undefined ? config.inventoryDeptId : (current.dept || ""))
    if (!/^[1-9]\d*$/.test(actor) || !/^[1-9]\d*$/.test(dept))
      throw commandError("请重新登录并选择业务组织", "TRANSFER_CONTEXT_CHANGED")
    return { actor, dept, selectedDept: String(current.dept || "") }
  }
  const assertContext = (config, captured) => {
    if (canonical(scopeOf(config)) !== canonical(captured))
      throw commandError("账号或业务组织已变化，请回到原操作确认结果", "TRANSFER_CONTEXT_CHANGED")
  }
  const removeExact = (key, requestId) => {
    const existing = read(key)
    if (!existing || existing.requestId !== requestId) return false
    try { getStorage().removeItem(key) } catch (_) { return false }
    return read(key) == null
  }

  async function run(config) {
    if (!isPersistentTransferCommand(config) || explicitId(config)) return transport(config)
    const scope = scopeOf(config)
    const key = PREFIX + scope.actor + ":" + scope.dept + ":" + encodeURIComponent(resource(config))
    if (running.has(key)) throw commandError("该调拨操作正在处理，请稍候")
    running.add(key)
    let record
    let recoveringChangedIntent = false
    let mutationDispatched = false
    try {
      record = read(key)
      const payload = JSON.parse(canonical(config.data))
      const intent = canonical({ method: config.method, url: config.url, payload })
      if (record) {
        if (record.version !== 2 || record.actor !== scope.actor || record.dept !== scope.dept
            || !/^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/.test(record.requestId || "")
            || !["ACTIVE", "SETTLED"].includes(record.phase)
            || record.intent !== canonical({ method: record.method, url: record.url, payload: record.payload }))
          throw commandError("调拨恢复记录不完整，已阻止重复提交", "TRANSFER_STORAGE_INVALID")
        recoveringChangedIntent = record.intent !== intent
        if (recoveringChangedIntent && (!confirmRecovery || !consumeRecovered))
          throw commandError("上一笔调拨结果尚未确认，请先用原内容重试确认，再进行修改")
        const result = await status(record.requestId, scope.dept)
        assertContext(config, scope)
        if (!result || !["SUCCEEDED", "NOT_FOUND"].includes(result.status))
          throw commandError("上一笔调拨仍在处理中，请稍后查询")
        if (recoveringChangedIntent && !(await confirmRecovery(record)))
          throw commandError("上一笔调拨结果尚未确认，本次修改尚未提交")
      } else {
        record = { version: 2, actor: scope.actor, dept: scope.dept,
          method: config.method, url: config.url, payload, intent,
          requestId: newId(), phase: "ACTIVE" }
        write(key, record)
      }
      assertContext(config, scope)
      mutationDispatched = true
      const result = await transport(Object.assign({}, config, {
        url: record.url,
        method: record.method,
        data: record.payload,
        inventoryDeptId: scope.dept,
        __transferCommandScope: scope,
        headers: Object.assign({}, config.headers || {}, {
          "X-Request-Id": record.requestId, repeatSubmit: false
        })
      }))
      mutationDispatched = false
      const currentRecord = read(key)
      if (!currentRecord || currentRecord.requestId !== record.requestId)
        throw commandError("原调拨操作已由另一页面确认，请刷新查看", "TRANSFER_CONTEXT_CHANGED")
      // Keep completion durable until the caller has updated its visible page.
      record.phase = "SETTLED"
      write(key, record)
      assertContext(config, scope)
      if (!result || typeof result !== "object")
        throw commandError("调拨结果格式异常，请查询原操作结果")
      if (recoveringChangedIntent) {
        await consumeRecovered(result, record)
        assertContext(config, scope)
        removeExact(key, record.requestId)
        const confirmed = commandError("上一笔调拨结果已确认，本次修改尚未提交，请核对单据后再操作", "TRANSFER_ORIGINAL_CONFIRMED")
        confirmed.notified = true
        throw confirmed
      }
      receipts.set(result, { key, requestId: record.requestId, scope, config })
      return result
    } catch (error) {
      if (record && mutationDispatched && confirmedRejection(error)) {
        try {
          const outcome = await status(record.requestId, scope.dept)
          assertContext(config, scope)
          // Only release a completed rejection after the server confirms no committed command.
          if (outcome && outcome.status === "NOT_FOUND") removeExact(key, record.requestId)
        } catch (_) { /* Preserve the original command whenever reconciliation is uncertain. */ }
      }
      throw error
    } finally {
      running.delete(key)
    }
  }

  function acknowledge(result) {
    if (!result || typeof result !== "object") return
    const receipt = receipts.get(result)
    if (!receipt) return
    try {
      assertContext(receipt.config, receipt.scope)
      const record = read(receipt.key)
      if (record && record.phase === "SETTLED") removeExact(receipt.key, receipt.requestId)
    } catch (_) {
      // Leaving the settled record permits safe recovery; never release another owner.
    }
    receipts.delete(result)
  }

  return { run, acknowledge, assertContext }
}
module.exports = { createTransferCommandRecovery, canonical, confirmedRejection }
