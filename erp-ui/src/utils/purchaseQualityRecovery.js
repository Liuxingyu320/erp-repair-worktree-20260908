function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical)
  if (value && typeof value === "object") {
    return Object.keys(value).sort().reduce((out, key) => {
      if (key !== "requestId" && value[key] !== undefined) out[key] = canonical(value[key])
      return out
    }, {})
  }
  return value
}

// One pending operation per actor, warehouse and purchase order survives reloads.
// Unknown outcomes always replay the saved request; new edits never replace it.
function createPurchaseQualityRecovery(options) {
  const flights = new Map()
  const context = orderId => {
    const current = options.context()
    if (!current.actor || !current.dept || !orderId) throw new Error("请先登录并选择质检仓库")
    return { actor: String(current.actor), dept: String(current.dept), orderId: String(orderId) }
  }
  const keyOf = ctx => "erp.purchase-quality.v1:" + [ctx.actor, ctx.dept, ctx.orderId].join(":")
  const storage = () => {
    const target = options.storage()
    if (!target) throw new Error("当前浏览器无法保留质检操作，请启用本地存储后重试")
    return target
  }
  const read = ctx => {
    const raw = storage().getItem(keyOf(ctx))
    if (!raw) return null
    const saved = JSON.parse(raw)
    if (!saved || !saved.payload || !saved.payload.requestId) throw new Error("质检恢复记录异常，请联系管理员核对上次结果")
    return saved
  }
  const sameContext = ctx => {
    const latest = context(ctx.orderId)
    if (latest.actor !== ctx.actor || latest.dept !== ctx.dept) throw new Error("账号或仓库已变化，请重新打开质检")
  }
  const send = (ctx, saved) => {
    const key = keyOf(ctx)
    if (flights.has(key)) return flights.get(key)
    sameContext(ctx)
    const promise = Promise.resolve().then(() => {
      sameContext(ctx)
      return options.transport(ctx.orderId, saved.payload, ctx.dept, saved.kind || "batch")
    }).then(result => {
      const latest = read(ctx)
      if (latest && latest.payload.requestId === saved.payload.requestId) storage().removeItem(key)
      return result
    }).catch(error => {
      const data = error && (error.data || (error.response && error.response.data))
      if (data && data.qualityCheckOutcome === "REJECTED") {
        const latest = read(ctx)
        if (latest && latest.payload.requestId === saved.payload.requestId) storage().removeItem(key)
      }
      throw error
    }).finally(() => flights.delete(key))
    flights.set(key, promise)
    return promise
  }
  return {
    async submit(orderId, data, kind = "batch") {
      const ctx = context(orderId)
      const payload = canonical(data)
      let saved = read(ctx)
      if (saved && ((saved.kind || "batch") !== kind || JSON.stringify(canonical(saved.payload)) !== JSON.stringify(payload))) {
        if (!await options.confirmRecovery()) throw new Error("上次质检结果待核对，本次修改尚未提交")
        await send(ctx, saved)
        throw new Error("上次质检已确认，本次修改尚未提交，请重新打开质检核对剩余数量")
      }
      if (!saved) {
        saved = { kind, payload: Object.assign(payload, { requestId: options.createId() }) }
        storage().setItem(keyOf(ctx), JSON.stringify(saved))
      }
      return send(ctx, saved)
    },
    async recover(orderId) {
      const ctx = context(orderId)
      const saved = read(ctx)
      if (!saved) return
      if (!await options.confirmRecovery()) throw new Error("请先核对上次质检结果")
      await send(ctx, saved)
    }
  }
}

module.exports = { createPurchaseQualityRecovery }
