const TODO_TRANSFER_APPROVAL = "INV_TRANSFER_APPROVAL"
const ENGINE_LEGACY = "LEGACY"
const ENGINE_NATIVE = "NATIVE"
const DEFAULT_BATCH_CONCURRENCY = 3
const MAX_BATCH_ITEMS = 20
let requestSequence = 0

function normalizedString(value) {
  return value === undefined || value === null ? "" : String(value).trim()
}

function positiveId(value) {
  const normalized = normalizedString(value)
  return /^\d+$/.test(normalized) && Number(normalized) > 0 ? normalized : ""
}

function routeParams(row) {
  const value = row && row.routeParams
  return value && typeof value === "object" && !Array.isArray(value) ? value : {}
}

function approvalEngine(row) {
  return normalizedString(routeParams(row).approvalEngine || row && row.approvalEngine).toUpperCase()
}

function approvalAction(row) {
  const params = routeParams(row)
  return normalizedString(row && (row.routeType || row.action) || params.action || "approve").toLowerCase()
}

function resolveTodoApprovalTarget(row) {
  const params = routeParams(row)
  const engine = approvalEngine(row)
  const transferId = positiveId(row && row.businessId || params.businessId)
  const taskId = positiveId(params.approvalTaskId || row && (row.approvalTaskId || row.taskId))
  const contextDeptId = positiveId(params.contextDeptId || row && (row.contextDeptId || row.deptId))
  return { engine, transferId, taskId, contextDeptId }
}

function hasPermission(requiredPermission, permissions) {
  if (!requiredPermission) return true
  const values = permissions instanceof Set ? permissions : new Set(Array.isArray(permissions) ? permissions : [])
  return values.has("*:*:*") || values.has(requiredPermission)
}

function isStaleTodo(row, staleSources) {
  const source = normalizedString(row && (row.provider || row.source)).toLowerCase()
  return Array.isArray(staleSources) && staleSources.map(value => normalizedString(value).toLowerCase()).includes(source)
}

function quickApprovalEligibility(row, options = {}) {
  if (options.enabled !== true) return { eligible: false, reason: "feature_disabled" }
  if (!row || typeof row !== "object") return { eligible: false, reason: "invalid_item" }
  const type = normalizedString(row.todoType || row.type).toUpperCase()
  if (type !== TODO_TRANSFER_APPROVAL || normalizedString(row.category).toLowerCase() !== "approval") {
    return { eligible: false, reason: "unsupported_type" }
  }
  if (approvalAction(row) !== "approve") return { eligible: false, reason: "unsupported_action" }
  if (isStaleTodo(row, options.staleSources)) return { eligible: false, reason: "stale" }
  if (!hasPermission(row.requiredPermission, options.permissions)) {
    return { eligible: false, reason: "permission_changed" }
  }
  const target = resolveTodoApprovalTarget(row)
  if (!target.transferId || !target.contextDeptId) return { eligible: false, reason: "context_missing", target }
  if (target.engine === ENGINE_NATIVE && !target.taskId) {
    return { eligible: false, reason: "task_missing", target }
  }
  if (target.engine !== ENGINE_NATIVE && target.engine !== ENGINE_LEGACY) {
    return { eligible: false, reason: "engine_unknown", target }
  }
  return { eligible: true, reason: "", target }
}

function canQuickApproveTodo(row, options = {}) {
  return quickApprovalEligibility(row, options).eligible
}

function canBatchApproveTodo(row, options = {}) {
  return quickApprovalEligibility(row, {
    ...options,
    enabled: options.enabled === true
  }).eligible
}

function hashValue(value) {
  const source = normalizedString(value)
  let hash = 0x811c9dc5
  for (let index = 0; index < source.length; index += 1) {
    hash ^= source.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193)
  }
  return (hash >>> 0).toString(36)
}

function safeRequestPart(value, fallback) {
  const normalized = normalizedString(value).replace(/[^A-Za-z0-9._:-]/g, "-")
  return (normalized || fallback).slice(0, 64)
}

function validatedRequestId(value) {
  const normalized = normalizedString(value)
  if (!normalized || normalized.length > 128 || !/^[A-Za-z0-9._:-]+$/.test(normalized)) {
    throw new Error("审批请求标识格式无效")
  }
  return normalized
}

function snapshotApprovalRow(row) {
  const source = row && typeof row === "object" ? row : {}
  return {
    ...source,
    routeParams: { ...routeParams(source) }
  }
}

function createTodoApprovalRequestId(prefix, stableKey, options = {}) {
  requestSequence = (requestSequence + 1) % Number.MAX_SAFE_INTEGER
  const nowValue = typeof options.now === "function" ? options.now() : options.now
  const now = Number.isFinite(Number(nowValue)) ? Number(nowValue) : Date.now()
  const randomValue = typeof options.random === "function" ? options.random() : options.random
  const random = Number.isFinite(Number(randomValue)) ? Number(randomValue) : Math.random()
  const randomPart = Math.abs(Math.floor(random * 0x100000000)).toString(36)
  const value = [
    safeRequestPart(prefix, "TODOQ"),
    Math.max(0, now).toString(36),
    requestSequence.toString(36),
    randomPart,
    hashValue(stableKey)
  ].join(":")
  return value.slice(0, 128)
}

function normalizeComment(value) {
  const comment = normalizedString(value)
  if (comment.length > 500) throw new Error("审批意见不能超过500个字符")
  return comment
}

function responseData(response) {
  return response && Object.prototype.hasOwnProperty.call(response, "data") ? response.data : response
}

async function loadTodoApprovalPreview(row, dependencies = {}) {
  if (typeof dependencies.getTransferDetail !== "function") {
    throw new Error("调拨详情服务不可用")
  }
  const target = resolveTodoApprovalTarget(row)
  if (!target.transferId || !target.contextDeptId) {
    throw new Error("待办缺少有效调拨或组织上下文")
  }
  const response = await dependencies.getTransferDetail(target.transferId, {
    inventoryDeptId: target.contextDeptId,
    silentError: true
  })
  const detail = responseData(response)
  if (!detail || typeof detail !== "object") throw new Error("调拨详情为空，请刷新后重试")
  const detailTransferId = positiveId(detail.transferId || detail.id)
  if (!detailTransferId || detailTransferId !== target.transferId) throw new Error("调拨详情与待办不匹配")
  const detailStatus = normalizedString(detail.status).toLowerCase()
  if (detailStatus !== "submitted") throw new Error("调拨审批状态已变化，请刷新待办")
  return detail
}

async function executeTodoApproval(row, dependencies = {}, options = {}) {
  if (approvalAction(row) !== "approve") throw new Error("快速与批量审批仅支持通过操作")
  const target = resolveTodoApprovalTarget(row)
  if (!target.transferId || !target.contextDeptId) throw new Error("待办缺少有效调拨或组织上下文")
  if (target.engine === ENGINE_NATIVE && !target.taskId) throw new Error("统一审批任务不存在")
  if (target.engine !== ENGINE_NATIVE && target.engine !== ENGINE_LEGACY) throw new Error("审批引擎不可用")
  const requestId = validatedRequestId(normalizedString(options.requestId) ||
    createTodoApprovalRequestId(
      options.requestPrefix || "TODOQ",
      row && row.todoKey || target.transferId
    ))
  const comment = normalizeComment(options.comment)
  const requestConfig = options.suppressTodoMutationRefresh === true
    ? { suppressTodoMutationRefresh: true }
    : {}
  let response
  if (target.engine === ENGINE_NATIVE) {
    if (typeof dependencies.approveApprovalTask !== "function") throw new Error("统一审批服务不可用")
    response = await dependencies.approveApprovalTask(target.taskId, {
      requestId,
      reason: comment
    }, requestConfig)
  } else {
    if (typeof dependencies.approveTransfer !== "function") throw new Error("调拨审批服务不可用")
    response = await dependencies.approveTransfer({
      transferId: target.transferId,
      action: "approve",
      comment
    }, {
      ...requestConfig,
      inventoryDeptId: target.contextDeptId,
      headers: { "X-Request-Id": requestId }
    })
  }
  return { status: "SUCCESS", requestId, target, response }
}

function errorStatus(error) {
  const responseStatus = Number(error && error.response && error.response.status)
  if (Number.isFinite(responseStatus) && responseStatus >= 400) return responseStatus
  const applicationStatus = Number(error && error.code)
  if (Number.isFinite(applicationStatus) && applicationStatus >= 400) return applicationStatus
  return Number.isFinite(responseStatus) ? responseStatus : 0
}

function classifyTodoApprovalError(error) {
  const status = errorStatus(error)
  const businessCode = normalizedString(error && (error.businessCode ||
    error.response && error.response.data && error.response.data.businessCode))
  let kind = "UNKNOWN"
  if (status === 401) kind = "SESSION"
  else if (status === 403) kind = "FORBIDDEN"
  else if (status === 409) kind = "CONFLICT"
  const messages = {
    SESSION: "登录状态已失效",
    FORBIDDEN: "审批权限或候选关系已变化",
    CONFLICT: "审批任务状态已变化",
    UNKNOWN: "审批结果未知，请刷新后确认"
  }
  return {
    status: "FAILED",
    errorKind: kind,
    statusCode: status,
    businessCode,
    message: messages[kind]
  }
}

function createBatchApprovalItems(rows, options = {}) {
  const source = Array.isArray(rows) ? rows : []
  if (!source.length) throw new Error("请至少选择一条待审批事项")
  const maxItems = Number(options.maxItems) > 0 ? Number(options.maxItems) : MAX_BATCH_ITEMS
  if (source.length > maxItems) throw new Error(`单次最多批量处理${maxItems}条`)
  const batchId = safeRequestPart(options.batchId || createTodoApprovalRequestId("TODOB", "batch", options), "batch")
  return source.map((row, index) => {
    const snapshot = snapshotApprovalRow(row)
    return {
      row: snapshot,
      todoKey: normalizedString(row && row.todoKey),
      businessNo: normalizedString(row && (row.businessNo || row.title)),
      requestId: createTodoApprovalRequestId(`TODOB:${batchId}:${index + 1}`, row && row.todoKey || index, options),
      status: "PENDING",
      errorKind: "",
      message: ""
    }
  })
}

async function executeTodoApprovalBatch(items, dependencies = {}, options = {}) {
  const source = Array.isArray(items) ? items : []
  if (!source.length) return []
  if (source.length > MAX_BATCH_ITEMS) throw new Error(`单次最多批量处理${MAX_BATCH_ITEMS}条`)
  const concurrency = Math.max(1, Math.min(
    Number(options.concurrency) || DEFAULT_BATCH_CONCURRENCY,
    DEFAULT_BATCH_CONCURRENCY,
    source.length
  ))
  const results = new Array(source.length)
  let cursor = 0
  let completed = 0
  let stopScheduling = false
  let stopReason = ""

  async function worker() {
    while (!stopScheduling) {
      if (typeof options.shouldStop === "function" && options.shouldStop()) {
        stopScheduling = true
        stopReason = "USER_CANCELED"
        return
      }
      const index = cursor
      cursor += 1
      if (index >= source.length) return
      const item = source[index]
      try {
        const actionResult = await executeTodoApproval(item.row, dependencies, {
          requestId: item.requestId,
          comment: options.comment,
          suppressTodoMutationRefresh: true,
          requestPrefix: "TODOB"
        })
        results[index] = {
          ...item,
          status: "SUCCESS",
          requestId: actionResult.requestId,
          target: actionResult.target
        }
      } catch (error) {
        const failure = classifyTodoApprovalError(error)
        results[index] = { ...item, ...failure }
        if (failure.errorKind === "SESSION") {
          stopScheduling = true
          stopReason = "SESSION"
        }
      }
      completed += 1
      if (typeof options.onProgress === "function") {
        try { options.onProgress(results[index], completed, source.length) } catch (error) { /* presentation callback */ }
      }
    }
  }

  await Promise.all(Array.from({ length: concurrency }, () => worker()))
  for (let index = 0; index < source.length; index += 1) {
    if (!results[index]) {
      results[index] = {
        ...source[index],
        status: "CANCELED",
        errorKind: stopReason || "USER_CANCELED",
        message: stopReason === "SESSION" ? "登录状态已失效，未开始处理" : "用户已停止，未开始处理"
      }
    }
  }
  return results
}

module.exports = {
  DEFAULT_BATCH_CONCURRENCY,
  ENGINE_LEGACY,
  ENGINE_NATIVE,
  MAX_BATCH_ITEMS,
  TODO_TRANSFER_APPROVAL,
  approvalAction,
  approvalEngine,
  canBatchApproveTodo,
  canQuickApproveTodo,
  classifyTodoApprovalError,
  createBatchApprovalItems,
  createTodoApprovalRequestId,
  executeTodoApproval,
  executeTodoApprovalBatch,
  loadTodoApprovalPreview,
  quickApprovalEligibility,
  resolveTodoApprovalTarget,
  snapshotApprovalRow,
  validatedRequestId
}
