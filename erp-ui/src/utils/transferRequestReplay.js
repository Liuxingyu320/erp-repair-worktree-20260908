const { canonical } = require("./transferCommandRecovery")
const { isPersistentTransferCommand, applyPersistentCommandRequestId } = require("./persistentCommand")
const { normalizePositiveDeptId } = require("./requestInventoryContext")

// Axios preserves class instances while cloning ordinary config objects.
// The permit contains no data; its private record survives only in this client.
class ReplayPermit {}

function header(headers, name) {
  if (headers && typeof headers.get === "function") return headers.get(name)
  const key = Object.keys(headers || {}).find(key => key.toLowerCase() === name.toLowerCase())
  return key ? headers[key] : undefined
}

function body(data) {
  if (typeof data === "string") {
    try { return JSON.parse(data) } catch (_) { return data }
  }
  return data == null ? null : data
}

function changed() {
  const error = new Error("调拨请求的账号、组织或内容已变化，请核对原操作后重试")
  error.code = "TRANSFER_REPLAY_CHANGED"
  error.notified = false
  return error
}

function createTransferRequestReplay({ context, resolveAdapter, baseURL }) {
  const records = new WeakMap()
  const owner = () => {
    const current = context()
    return {
      actor: String(current.actor || ""),
      selectedDept: String(current.dept || ""),
      revision: Number(current.revision || 0),
      token: String(current.token || "")
    }
  }
  const fingerprint = config => canonical({
    method: String(config.method || "get").toLowerCase(),
    url: config.url,
    baseURL: config.baseURL || baseURL,
    data: body(config.data),
    params: config.params || null,
    requestId: String(header(config.headers, "X-Request-Id") || "")
  })

  function validate(config) {
    const record = records.get(config.__transferReplayPermit)
    if (!record || canonical(owner()) !== canonical(record.owner)
        || fingerprint(config) !== record.fingerprint
        || config.adapter !== record.adapter
        || config.inventoryDeptId !== undefined
          && normalizePositiveDeptId(config.inventoryDeptId) !== record.dept)
      throw changed()
    return record
  }

  function restore(config) {
    if (!config.__transferReplayPermit) {
      if (isPersistentTransferCommand(config)) throw changed()
      return
    }
    const record = validate(config)
    // requestInventoryContext consumes this field on every interceptor pass.
    config.inventoryDeptId = record.dept
  }

  function prepare(config) {
    if (config.__transferReplayPermit) {
      restore(config)
      return config
    }
    if (!isPersistentTransferCommand(config)) return config
    const captured = owner()
    const dept = normalizePositiveDeptId(config.inventoryDeptId === undefined
      ? captured.selectedDept : config.inventoryDeptId)
    if (!/^[1-9]\d*$/.test(captured.actor) || !dept) throw changed()
    const prepared = Object.assign({}, config, {
      data: JSON.parse(canonical(body(config.data))),
      params: config.params ? JSON.parse(canonical(config.params)) : config.params,
      headers: Object.assign({}, config.headers || {}, { repeatSubmit: false }),
      __transferReplayPermit: new ReplayPermit()
    })
    applyPersistentCommandRequestId(prepared)
    const delegate = resolveAdapter(config.adapter)
    const record = { owner: captured, dept, fingerprint: fingerprint(prepared) }
    record.adapter = dispatch => {
      validate(dispatch)
      // This runs after Axios transforms, immediately before the real adapter.
      if (String(header(dispatch.headers, "Dept-NumId") || "") !== record.dept) throw changed()
      return delegate(dispatch)
    }
    prepared.adapter = record.adapter
    records.set(prepared.__transferReplayPermit, record)
    return prepared
  }

  return { prepare, restore }
}

module.exports = { createTransferRequestReplay }
