const assert = require("assert")
const fs = require("fs")
const path = require("path")
const actions = require("../src/utils/todoApprovalActions")

function row(index) {
  const native = index % 2 === 1
  return {
    todoKey: `${native ? "approval" : "inventory"}:INV_TRANSFER_APPROVAL:${index}`,
    provider: native ? "approval" : "inventory",
    source: native ? "approval" : "inventory",
    type: "INV_TRANSFER_APPROVAL",
    category: "approval",
    businessId: String(1000 + index),
    businessNo: `TF${1000 + index}`,
    requiredPermission: "inv:transfer:approve",
    routeParams: {
      businessId: String(1000 + index),
      contextDeptId: String(80 + index),
      approvalEngine: native ? "NATIVE" : "LEGACY",
      ...(native ? { approvalTaskId: String(9000 + index) } : {})
    }
  }
}

function delayed(work) {
  return new Promise((resolve, reject) => setImmediate(() => {
    try { resolve(work()) } catch (error) { reject(error) }
  }))
}

async function run() {
  const rows = Array.from({ length: 12 }, (_, index) => row(index))
  const items = actions.createBatchApprovalItems(rows, { batchId: "acceptance-batch", now: 1000, random: 0.42 })
  const requestIds = items.map(item => item.requestId)
  let active = 0
  let peak = 0
  const calls = []

  function outcome(index) {
    if (index === 9) {
      const error = new Error("forbidden")
      error.response = { status: 403 }
      throw error
    }
    if (index === 10) {
      const error = new Error("conflict")
      error.response = { status: 409 }
      throw error
    }
    if (index === 11) throw new Error("network unavailable")
    return { data: { ok: true } }
  }

  const dependencies = {
    approveTransfer(payload, config) {
      const index = Number(payload.transferId) - 1000
      calls.push({ index, engine: "LEGACY", requestId: config.headers["X-Request-Id"], config })
      active += 1
      peak = Math.max(peak, active)
      return delayed(() => outcome(index)).finally(() => { active -= 1 })
    },
    approveApprovalTask(taskId, payload, config) {
      const index = Number(taskId) - 9000
      calls.push({ index, engine: "NATIVE", requestId: payload.requestId, config })
      active += 1
      peak = Math.max(peak, active)
      return delayed(() => outcome(index)).finally(() => { active -= 1 })
    }
  }

  const progress = []
  const results = await actions.executeTodoApprovalBatch(items, dependencies, {
    concurrency: 99,
    comment: "同意",
    onProgress(item, completed, total) { progress.push({ completed, total, status: item.status }) }
  })

  assert.ok(peak <= 3, "even an unsafe requested concurrency must be capped at three")
  assert.strictEqual(results.filter(item => item.status === "SUCCESS").length, 9)
  assert.strictEqual(results.filter(item => item.status === "FAILED").length, 3)
  assert.strictEqual(results[9].errorKind, "FORBIDDEN")
  assert.strictEqual(results[10].errorKind, "CONFLICT")
  assert.strictEqual(results[11].errorKind, "UNKNOWN")
  assert.strictEqual(progress.at(-1).completed, 12)
  assert.ok(calls.some(call => call.engine === "LEGACY") && calls.some(call => call.engine === "NATIVE"),
    "one batch may safely mix legacy and native approval engines")
  assert.ok(calls.every(call => call.config.suppressTodoMutationRefresh === true),
    "each item must suppress response-interceptor refresh so the batch can refresh once at the end")
  assert.deepStrictEqual(calls.sort((a, b) => a.index - b.index).map(call => call.requestId), requestIds,
    "each transport must receive the immutable request id created with its snapshot")

  const retryItems = results.filter(item => item.status === "FAILED")
  const retriedRequestIds = []
  await actions.executeTodoApprovalBatch(retryItems, {
    approveTransfer(payload, config) {
      retriedRequestIds.push(config.headers["X-Request-Id"])
      return Promise.resolve()
    },
    approveApprovalTask(taskId, payload) {
      retriedRequestIds.push(payload.requestId)
      return Promise.resolve()
    }
  })
  assert.deepStrictEqual(retriedRequestIds.sort(), retryItems.map(item => item.requestId).sort(),
    "retrying failed items must reuse, not regenerate, their request ids")

  let sessionCalls = 0
  const sessionItems = actions.createBatchApprovalItems(Array.from({ length: 6 }, (_, index) => row(index)), {
    batchId: "expired-session", now: 2000, random: 0.5
  })
  const sessionResults = await actions.executeTodoApprovalBatch(sessionItems, {
    approveTransfer() {
      sessionCalls += 1
      const error = new Error("session expired")
      error.response = { status: 401 }
      return Promise.reject(error)
    },
    approveApprovalTask() {
      sessionCalls += 1
      const error = new Error("session expired")
      error.response = { status: 401 }
      return Promise.reject(error)
    }
  }, { concurrency: 1 })
  assert.strictEqual(sessionCalls, 1)
  assert.strictEqual(sessionResults.filter(item => item.status === "CANCELED").length, 5,
    "a session failure must stop all not-yet-started items")

  let userCanceledCalls = 0
  const stoppedResults = await actions.executeTodoApprovalBatch(sessionItems, {
    approveTransfer() { userCanceledCalls += 1; return Promise.resolve() },
    approveApprovalTask() { userCanceledCalls += 1; return Promise.resolve() }
  }, { shouldStop: () => true })
  assert.strictEqual(userCanceledCalls, 0)
  assert.ok(stoppedResults.every(item => item.status === "CANCELED" && item.errorKind === "USER_CANCELED"),
    "a user stop request must cancel every item that has not started")

  const page = fs.readFileSync(path.resolve(__dirname, "../src/views/workbench/todo/index.vue"), "utf8")
  const method = /async runBatchApproval\([\s\S]*?\n\s{4}\},\n\s{4}submitBatchApproval/.exec(page)
  assert.ok(method && (method[0].match(/todo\/invalidateAfterMutation/g) || []).length === 1 &&
    (method[0].match(/await this\.loadPage/g) || []).length === 1,
  "one batch execution must converge with exactly one explicit invalidation and one page refresh")
  assert.ok(method && method[0].includes("await this.loadPage()") && !method[0].includes("loadPage({ networkSources:"),
    "a batch mutation must reload every selected provider after clearing the aggregate page cache")

  const mobilePage = fs.readFileSync(path.resolve(__dirname, "../src/views/mobile/todo/index.vue"), "utf8")
  const mobileMethod = /async runBatchApproval\([\s\S]*?\n\s{4}\},\n\s{4}stopBatchApproval/.exec(mobilePage)
  assert.ok(mobileMethod && (mobileMethod[0].match(/todo\/invalidateAfterMutation/g) || []).length === 1 &&
    (mobileMethod[0].match(/await this\.reloadLoadedTodoPages\(\)/g) || []).length === 1,
  "one mobile batch must converge with exactly one explicit invalidation and one all-loaded-pages refresh")
}

run().then(() => console.log("unified todo batch approval tests passed")).catch(error => {
  console.error(error)
  process.exit(1)
})
