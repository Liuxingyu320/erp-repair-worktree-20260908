const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const uiRoot = path.resolve(__dirname, "..")
const componentPath = path.resolve(uiRoot, "src/views/oa/signTask/SignTaskDetailDrawer.vue")
const apiPath = path.resolve(uiRoot, "src/api/oa/signTask.js")
const componentSource = fs.readFileSync(componentPath, "utf8")
const apiSource = fs.readFileSync(apiPath, "utf8")

assert.ok(apiSource.includes("export function resolveSignTaskException"))
assert.ok(apiSource.includes("'/oa/signTask/' + taskId + '/resolve'"))
assert.ok(/resolveSignTaskException[\s\S]*method:\s*'post'[\s\S]*silentError:\s*true/.test(apiSource),
  "exception resolution should use the existing POST endpoint and render its error locally")

;[
  "当前责任人",
  "签署截止",
  "冻结文档版本",
  "公司主体就绪",
  "合同印章就绪",
  "最近通知状态",
  "最近通知时间",
  "终态时间",
  "终态原因分类",
  "处置状态",
  "处置原因分类",
  "失败原因分类",
  "已重试 / 下次重试",
  "替代目标任务"
].forEach(label => assert.ok(componentSource.includes(label), `task drawer should expose ${label}`))

;[
  "openResolution('CLOSE')",
  "openResolution('REISSUE')",
  "openResolution('EXTEND')",
  "oa:signTask:resolveRefusal",
  "oa:signTask:resolveExpiry",
  "replacementPlanVersionId",
  "extensionDays",
  "expectedVersion",
  "documentVersion"
].forEach(contract => assert.ok(componentSource.includes(contract), `resolution UI should include ${contract}`))

assert.ok(componentSource.includes("this.task.resolutionStatus === 'OPEN'"),
  "only newly opened terminal exceptions should expose close/reissue actions")
assert.ok(componentSource.includes("EMPLOYEE_DEADLINE_STATUSES.includes(this.task.status)"),
  "deadline extension should remain a pre-expiry action")
assert.ok(componentSource.includes("Number(this.task.planVersionId)"),
  "reissue should default to the current frozen published plan version")
assert.ok(componentSource.includes("已过期记录不能原地延期"),
  "expired immutable records must direct HR to close or reissue")
assert.ok(/finalDocumentVersion\s*\|\|[\s\S]*detail\.documentVersion/.test(componentSource),
  "final-stage refusal must submit the frozen final document version")
assert.ok(componentSource.includes("Object.freeze(this.buildResolutionPayload())") &&
  componentSource.includes("使用原请求号重试"),
"an uncertain response should freeze the complete payload and expose stable replay")
assert.ok(componentSource.includes("resolutionBaselineDeadline") &&
  componentSource.includes("currentDeadline === baselineDeadline + extensionDays") &&
  componentSource.includes("task.resolutionReasonCode"),
"extension outcome checks must require the deadline and resolution fingerprint, not only a version increase")
assert.ok(componentSource.includes("signTaskStatusLabel(status)"),
  "the drawer should reuse the shared task status dictionary")
assert.ok(componentSource.includes("latestNotificationBusinessKey") &&
  componentSource.includes("notificationRetryBusinessKey"),
"the drawer should expose and requeue the latest DEAD notification even without a todo deep link")

function loadComponent(resolveOverride) {
  const scriptMatch = componentSource.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch)
  const script = scriptMatch[1]
    .replace(/^import(?:[\s\S]*?)\s+from\s+['"][^'"]+['"]\s*$/gm, "")
    .replace(/export default/, "module.exports =")
  const identity = value => value || "-"
  const sandbox = {
    module: { exports: {} },
    exports: {},
    cancelSignTask: () => Promise.resolve({}),
    getSignTask: () => Promise.resolve({ data: {} }),
    revalidateSignTask: () => Promise.resolve({}),
    resolveSignTaskException: resolveOverride,
    retrySignTask: () => Promise.resolve({}),
    retrySignTaskNotification: () => Promise.resolve({}),
    sendSignTask: () => Promise.resolve({}),
    downloadSignPackageDocument: () => Promise.resolve({}),
    verifySignPackage: () => Promise.resolve({ data: {} }),
    checkPermi: () => true,
    templateTypeLabel: identity,
    signDictionaryLabel: identity,
    signTaskReasonLabel: identity,
    signTaskStatusLabel: identity,
    require(request) {
      if (request === "@/utils/signScenario") return { signScenarioLabel: identity }
      if (request === "@/utils/signDisplayText") {
        return {
          signBusinessNumberLabel: identity,
          signBusinessText: (value, fallback) => value || fallback || "-",
          signTaskNumberLabel: identity,
          signVersionLabel: identity
        }
      }
      if (request === "@/utils/protectedFileBlob") {
        return { normalizeProtectedFileBlob(blob) { return Promise.resolve(blob) } }
      }
      throw new Error(`Unexpected require: ${request}`)
    },
    crypto: { randomUUID: () => "generated-request-id" },
    window: { open: () => null },
    URL: { createObjectURL: () => "blob:test", revokeObjectURL() {} },
    setTimeout() {},
    Promise,
    Object,
    Array,
    String,
    Number,
    Boolean,
    Math,
    RegExp,
    Date
  }
  vm.runInNewContext(script, sandbox, { filename: componentPath })
  return sandbox.module.exports
}

;(async () => {
  const payloads = []
  let attempts = 0
  const component = loadComponent((taskId, payload) => {
    payloads.push({ taskId, payload })
    attempts += 1
    return attempts === 1
      ? Promise.reject(new Error("处置响应中断"))
      : Promise.resolve({ data: { task: { taskId: "10", status: "NEW" } } })
  })
  const messages = []
  const updates = []
  const context = {
    ...component.data(),
    ...component.methods,
    resolutionOpen: true,
    resolutionTaskId: "9",
    resolutionAction: "REISSUE",
    resolutionRequestId: "stable-resolution-request",
    resolutionForm: {
      documentVersion: "final-document-v3",
      expectedVersion: 8,
      reasonCode: "DOCUMENT_CORRECTED",
      reasonDetail: "已修正合同主体和薪资信息",
      extensionDays: null,
      replacementPlanVersionId: 55
    },
    $modal: {
      msgWarning(message) { messages.push(message) },
      msgError(message) { messages.push(message) },
      msgSuccess(message) { messages.push(message) }
    },
    $emit(event, payload) { updates.push({ event, payload }) },
    refreshTodo: () => Promise.resolve(),
    loadDetail: () => Promise.resolve("refreshed")
  }

  assert.strictEqual(await context.submitResolution(), null)
  assert.strictEqual(context.resolutionSubmitting, false)
  assert.ok(context.resolutionError.includes("响应中断"))
  assert.strictEqual(context.resolutionRequestId, "stable-resolution-request")
  assert.strictEqual(context.resolutionPayloadSnapshot.requestId, "stable-resolution-request")

  // A frozen uncertain request must ignore any accidental local form mutation on replay.
  context.resolutionForm.reasonDetail = "不应进入重试请求的变更"
  context.resolutionForm.replacementPlanVersionId = 99
  assert.strictEqual(await context.submitResolution(), "refreshed")
  assert.deepStrictEqual(payloads.map(item => item.taskId), ["9", "9"])
  assert.deepStrictEqual(payloads.map(item => item.payload.requestId), [
    "stable-resolution-request",
    "stable-resolution-request"
  ])
  assert.deepStrictEqual(payloads.map(item => item.payload.reasonDetail), [
    "已修正合同主体和薪资信息",
    "已修正合同主体和薪资信息"
  ])
  assert.deepStrictEqual(payloads.map(item => item.payload.replacementPlanVersionId), [55, 55])
  assert.strictEqual(updates.some(item => item.event === "updated"), true)
  assert.strictEqual(context.resolutionRequestId, "")
  assert.strictEqual(messages.includes("替代任务和新签约包已创建"), true)

  const extensionOutcomeContext = {
    ...component.data(),
    ...component.methods,
    resolutionAction: "EXTEND",
    resolutionBaselineDeadline: "2026-07-17 23:59:59",
    resolutionPayloadSnapshot: Object.freeze({
      expectedVersion: 8,
      reasonCode: "BUSINESS_APPROVED",
      reasonDetail: "延期已批准",
      extensionDays: 3
    })
  }
  const matchingExtension = {
    task: {
      version: 9,
      signDeadline: "2026-07-20 23:59:59",
      resolvedTime: "2026-07-17 10:30:00",
      resolutionReasonCode: "BUSINESS_APPROVED",
      resolutionReasonDetail: "延期已批准"
    }
  }
  assert.strictEqual(extensionOutcomeContext.resolutionOutcomeConfirmed({
    task: { ...matchingExtension.task, signDeadline: "2026-07-17 23:59:59" }
  }), false, "an unrelated employee-view version increment must not confirm an extension")
  assert.strictEqual(extensionOutcomeContext.resolutionOutcomeConfirmed({
    task: { ...matchingExtension.task, resolutionReasonCode: "OTHER_EXTENSION" }
  }), false, "a different resolution must not confirm the uncertain request")
  assert.strictEqual(extensionOutcomeContext.resolutionOutcomeConfirmed(matchingExtension), true)

  const pendingTaskReplay = Object.freeze({
    taskId: "9",
    actionKey: "SEND",
    payload: Object.freeze({ requestId: "stable-task-request" })
  })
  const pendingNotificationReplay = Object.freeze({
    taskId: "9",
    businessKey: "SIGN_SENT:9",
    payload: Object.freeze({ requestId: "stable-notification-request" })
  })
  const uncertainSwitchContext = {
    ...component.data(),
    ...component.methods,
    visible: false,
    task: { taskId: "9" },
    canExtendResolution: true,
    resolutionOpen: true,
    resolutionError: "延期响应中断",
    resolutionTaskId: "9",
    resolutionAction: "EXTEND",
    resolutionRequestId: "stable-extension-request",
    resolutionPayloadSnapshot: Object.freeze({
      requestId: "stable-extension-request",
      action: "EXTEND",
      expectedVersion: 8,
      extensionDays: 3,
      reasonCode: "BUSINESS_APPROVED",
      reasonDetail: "延期已批准"
    }),
    resolutionBaselineDeadline: "2026-07-17 23:59:59",
    taskMutationReplay: pendingTaskReplay,
    taskMutationError: "发送响应中断",
    notificationMutationReplay: pendingNotificationReplay,
    notificationMutationError: "通知响应中断",
    $modal: { msgWarning(message) { messages.push(message) } },
    loadDetail: () => Promise.resolve()
  }
  component.watch.taskId.call(uncertainSwitchContext, "10", "9")
  assert.strictEqual(uncertainSwitchContext.resolutionOpen, false)
  assert.strictEqual(uncertainSwitchContext.resolutionRequestId, "stable-extension-request")
  assert.strictEqual(uncertainSwitchContext.resolutionBaselineDeadline, "2026-07-17 23:59:59")
  assert.strictEqual(uncertainSwitchContext.taskMutationReplay, pendingTaskReplay)
  assert.strictEqual(uncertainSwitchContext.notificationMutationReplay, pendingNotificationReplay)

  // Returning to the original task must reopen the same frozen envelope, even if the
  // request was still in flight when the user first switched away.
  uncertainSwitchContext.resolutionError = ""
  component.watch.taskId.call(uncertainSwitchContext, "9", "10")
  assert.strictEqual(uncertainSwitchContext.openResolution("EXTEND"), true)
  assert.strictEqual(uncertainSwitchContext.resolutionRequestId, "stable-extension-request")
  assert.strictEqual(uncertainSwitchContext.resolutionPayloadSnapshot.requestId,
    "stable-extension-request")

  const taskPayloads = []
  let taskAttempts = 0
  const taskApi = (taskId, payload) => {
    taskPayloads.push({ taskId, payload })
    taskAttempts += 1
    return taskAttempts === 1
      ? Promise.reject(new Error("发送响应中断"))
      : Promise.resolve({ data: { task: { taskId: "9", status: "PENDING_SIGN" } } })
  }
  const actionContext = {
    ...component.data(),
    ...component.methods,
    task: { taskId: "9", status: "READY_TO_SEND" },
    taskId: "9",
    visible: true,
    $modal: {
      msgWarning(message) { messages.push(message) },
      msgError(message) { messages.push(message) },
      msgSuccess(message) { messages.push(message) }
    },
    $emit() {},
    refreshTodo: () => Promise.resolve(),
    loadDetail: () => Promise.resolve("task-refreshed")
  }

  assert.strictEqual(await actionContext.runStableTaskAction(
    "SEND", taskApi, "MANUAL_SEND", "经办人直接发送", "发送操作已完成"
  ), null)
  const frozenTaskReplay = actionContext.taskMutationReplay
  assert.ok(frozenTaskReplay)
  assert.ok(actionContext.taskMutationError.includes("发送响应中断"))
  assert.strictEqual(await actionContext.runStableTaskAction(
    "SEND", taskApi, "MANUAL_SEND", "经办人直接发送", "发送操作已完成"
  ), "task-refreshed")
  assert.deepStrictEqual(taskPayloads.map(item => item.taskId), ["9", "9"])
  assert.deepStrictEqual(taskPayloads.map(item => item.payload.requestId), [
    frozenTaskReplay.payload.requestId,
    frozenTaskReplay.payload.requestId
  ])
  assert.deepStrictEqual(taskPayloads.map(item => item.payload.reasonCode), ["MANUAL_SEND", "MANUAL_SEND"])
  assert.strictEqual(actionContext.taskMutationReplay, null)

  console.log("sign task exception resolution tests passed")
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
