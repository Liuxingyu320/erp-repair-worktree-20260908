const assert = require("assert")
const path = require("path")

const actions = require(path.resolve(__dirname, "../src/utils/todoApprovalActions.js"))

function legacy(overrides = {}) {
  return {
    todoKey: "inventory:INV_TRANSFER_APPROVAL:701:approve",
    source: "inventory",
    type: "INV_TRANSFER_APPROVAL",
    category: "approval",
    businessId: 701,
    businessNo: "TF701",
    requiredPermission: "inv:transfer:approve",
    routeParams: {
      businessId: "701",
      approvalEngine: "LEGACY",
      contextDeptId: "88"
    },
    ...overrides
  }
}

function native(overrides = {}) {
  return legacy({
    source: "approval",
    todoKey: "approval:INV_TRANSFER_APPROVAL:702:approve",
    businessId: 702,
    businessNo: "TF702",
    routeParams: {
      businessId: "702",
      approvalEngine: "NATIVE",
      approvalTaskId: "9002",
      contextDeptId: "89"
    },
    ...overrides
  })
}

const allowed = { enabled: true, permissions: ["inv:transfer:approve"], staleSources: [] }
assert.strictEqual(actions.canQuickApproveTodo(legacy(), allowed), true)
assert.strictEqual(actions.canQuickApproveTodo(native(), allowed), true)
assert.strictEqual(actions.canQuickApproveTodo(legacy(), { ...allowed, enabled: false }), false)
assert.strictEqual(actions.canQuickApproveTodo(legacy(), { ...allowed, staleSources: ["inventory"] }), false)
assert.strictEqual(actions.canQuickApproveTodo(legacy(), { ...allowed, permissions: [] }), false)
assert.strictEqual(actions.canQuickApproveTodo(legacy({ routeType: "reject" }), allowed), false,
  "quick and batch approval must reject any explicitly non-approve action")
assert.strictEqual(actions.canQuickApproveTodo(native({ routeParams: {
  businessId: "702", approvalEngine: "NATIVE", contextDeptId: "89"
} }), allowed), false, "native approval must fail closed without an exact task")

const deterministic = actions.createTodoApprovalRequestId("TODOQ", "todo-701", { now: 1000, random: 0.25 })
assert.match(deterministic, /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/)
assert.throws(() => actions.validatedRequestId("invalid request id"), /格式无效/)
assert.strictEqual(actions.classifyTodoApprovalError({ code: 403, response: { status: 200 } }).errorKind, "FORBIDDEN",
  "an application-level authorization code returned over HTTP 200 must remain forbidden")

async function verifyActions() {
  let previewConfig
  const preview = await actions.loadTodoApprovalPreview(legacy(), {
    getTransferDetail(transferId, config) {
      previewConfig = { transferId, config }
      return Promise.resolve({ data: { transferId: 701, status: "submitted" } })
    }
  })
  assert.strictEqual(preview.status, "submitted")
  assert.strictEqual(previewConfig.config.inventoryDeptId, "88")
  await assert.rejects(() => actions.loadTodoApprovalPreview(legacy(), {
    getTransferDetail: () => Promise.resolve({ data: { transferId: 701, status: "approved" } })
  }), /状态已变化/)
  await assert.rejects(() => actions.loadTodoApprovalPreview(legacy(), {
    getTransferDetail: () => Promise.resolve({ data: { status: "submitted" } })
  }), /不匹配/)
  await assert.rejects(() => actions.executeTodoApproval(legacy({ routeType: "return" }), {
    approveTransfer: () => Promise.resolve()
  }), /仅支持通过操作/)

  let nativeCall
  const nativeResult = await actions.executeTodoApproval(native(), {
    approveApprovalTask(taskId, payload, config) {
      nativeCall = { taskId, payload, config }
      return Promise.resolve({ data: { ok: true } })
    }
  }, { requestId: "TODOQ:native-9002", comment: "同意" })
  assert.strictEqual(nativeResult.status, "SUCCESS")
  assert.deepStrictEqual(nativeCall, {
    taskId: "9002",
    payload: { requestId: "TODOQ:native-9002", reason: "同意" },
    config: {}
  })

  let legacyCall
  await actions.executeTodoApproval(legacy(), {
    approveTransfer(payload, config) {
      legacyCall = { payload, config }
      return Promise.resolve({ code: 200 })
    }
  }, { requestId: "TODOQ:legacy-701", comment: "" })
  assert.deepStrictEqual(legacyCall.payload, { transferId: "701", action: "approve", comment: "" })
  assert.strictEqual(legacyCall.config.inventoryDeptId, "88")
  assert.strictEqual(legacyCall.config.headers["X-Request-Id"], "TODOQ:legacy-701")

  let active = 0
  let peak = 0
  const rows = Array.from({ length: 6 }, (_, index) => legacy({
    todoKey: `legacy-${index}`,
    businessId: 800 + index,
    routeParams: { businessId: String(800 + index), approvalEngine: "LEGACY", contextDeptId: "88" }
  }))
  const items = actions.createBatchApprovalItems(rows, { batchId: "batch-1", now: 1000, random: 0.2 })
  rows[0].routeParams.contextDeptId = "999"
  assert.strictEqual(items[0].row.routeParams.contextDeptId, "88",
    "batch items must retain an independent route-context snapshot")
  const results = await actions.executeTodoApprovalBatch(items, {
    approveTransfer(payload) {
      active += 1
      peak = Math.max(peak, active)
      return new Promise((resolve, reject) => setImmediate(() => {
        active -= 1
        if (String(payload.transferId) === "802") {
          const error = new Error("状态已变化")
          error.code = 409
          reject(error)
        } else {
          resolve({ code: 200 })
        }
      }))
    }
  }, { concurrency: 3 })
  assert.ok(peak <= 3, "batch execution must cap write concurrency at three")
  assert.strictEqual(results.filter(item => item.status === "SUCCESS").length, 5)
  assert.strictEqual(results[2].errorKind, "CONFLICT")
  assert.strictEqual(new Set(items.map(item => item.requestId)).size, items.length,
    "every batch item must have its own stable request id")

  assert.throws(() => actions.createBatchApprovalItems(Array.from({ length: 21 }, () => legacy())), /最多.*20/)
}

verifyActions().then(() => {
  console.log("unified todo approval action tests passed")
}).catch(error => {
  console.error(error)
  process.exit(1)
})
