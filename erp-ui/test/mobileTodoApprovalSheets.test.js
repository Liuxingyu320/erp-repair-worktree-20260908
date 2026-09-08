const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const { compile } = require("vue-template-compiler")

const uiRoot = path.resolve(__dirname, "..")
const desktopQuickDialogPath = "src/views/workbench/todo/components/TodoQuickApproveDialog.vue"
const quickSheetPath = "src/views/mobile/todo/components/MobileTodoQuickApproveSheet.vue"
const batchSheetPath = "src/views/mobile/todo/components/MobileTodoBatchApproveSheet.vue"
const sharedSheetStylePath = "src/views/mobile/feature/components/mobileSheet.scss"

function read(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
}

function loadVueComponent(relativePath) {
  const source = read(relativePath)
  const scriptMatch = source.match(/<script>([\s\S]*?)<\/script>/)
  assert.ok(scriptMatch, `${relativePath} must contain a script block`)

  const script = scriptMatch[1]
    .replace(/import[\s\S]*?from\s*["'][^"']+["']\s*;?/g, "")
    .replace(/export default/, "module.exports =")
  const sandbox = {
    module: { exports: {} },
    exports: {},
    createMobileDialogFocusManager: options => ({
      options,
      activate: () => true,
      deactivate: () => true
    }),
    mountMobileOverlay: () => {},
    releaseMobileOverlay: () => {},
    Promise,
    Object,
    Array,
    String,
    Number,
    Math
  }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(script, sandbox, { filename: relativePath })
  return sandbox.module.exports
}

function propDefaults(component) {
  return Object.entries(component.props || {}).reduce((values, [name, contract]) => {
    if (!Object.prototype.hasOwnProperty.call(contract, "default")) return values
    values[name] = typeof contract.default === "function" && contract.type !== Function
      ? contract.default()
      : contract.default
    return values
  }, {})
}

function bindComponent(component, initial = {}) {
  const emitted = []
  const base = {
    ...propDefaults(component),
    ...initial,
    $refs: initial.$refs || {},
    $emit(name, payload) { emitted.push({ name, payload }) },
    $nextTick(callback) { if (typeof callback === "function") callback() }
  }
  const target = {
    ...base,
    ...(component.data ? component.data.call(base) : {}),
    ...initial,
    emitted
  }
  Object.entries(component.methods || {}).forEach(([name, method]) => {
    target[name] = method.bind(target)
  })
  Object.entries(component.computed || {}).forEach(([name, computed]) => {
    const getter = typeof computed === "function" ? computed : computed.get
    if (getter) Object.defineProperty(target, name, { configurable: true, get: getter.bind(target) })
  })
  return target
}

function plain(value) {
  return JSON.parse(JSON.stringify(value))
}

function assertDialogContract(source, relativePath) {
  const templateMatch = source.match(/<template>([\s\S]*?)<\/template>\s*<script>/)
  assert.ok(templateMatch, `${relativePath} must contain a template block`)
  const compiled = compile(templateMatch[1])
  assert.deepStrictEqual(compiled.errors, [], `${relativePath} template must compile without errors`)
  assert.ok(source.includes('role="dialog"'), `${relativePath} must expose a dialog role`)
  assert.ok(source.includes('aria-modal="true"'), `${relativePath} must identify itself as modal`)
  assert.ok(source.includes("aria-labelledby"), `${relativePath} must have an accessible title`)
  assert.ok(source.includes("mobileSheet.scss"), `${relativePath} must reuse the shared mobile sheet layout`)
  assert.ok(/footer button[\s\S]*?min-height:\s*48px/.test(source),
    `${relativePath} footer actions must retain 48px touch targets`)
}

const desktopQuickSource = read(desktopQuickDialogPath)
const quickSource = read(quickSheetPath)
const batchSource = read(batchSheetPath)
const sharedSheetStyle = read(sharedSheetStylePath)
const quickComponent = loadVueComponent(quickSheetPath)
const batchComponent = loadVueComponent(batchSheetPath)

assertDialogContract(quickSource, quickSheetPath)
assertDialogContract(batchSource, batchSheetPath)
assert.ok(
  /--mobile-safe-bottom,\s*env\(safe-area-inset-bottom,\s*0px\)/.test(sharedSheetStyle),
  "the shared phone sheet must protect footer actions with the bottom safe-area inset"
)
assert.ok(/\.detail-head button[\s\S]*?width:\s*44px[\s\S]*?height:\s*44px/.test(sharedSheetStyle),
  "the shared sheet close action must retain a 44px touch target")

for (const wording of ["通过意见（选填）", "通过并处理下一条", "用原请求号重试"]) {
  assert.ok(quickSource.includes(wording), `quick approval sheet must contain ${wording}`)
}
assert.ok(quickSource.includes('@click="confirm(true)"') && quickSource.includes("this.$emit('retry'"),
  "quick approval must expose continuous processing and an explicit retry event")
assert.ok(quickSource.includes("maxlength=\"500\"") && !quickSource.includes("required"),
  "quick approval opinion must remain optional and bounded")

{
  const desktopDetailsMatch = desktopQuickSource.match(
    /<section class="quick-approve-section" aria-labelledby="quick-details-heading">([\s\S]*?)<\/section>/
  )
  assert.ok(desktopDetailsMatch, "desktop quick approval must remain the fact source for transfer line-item fields")
  const desktopDetailLabels = Array.from(
    desktopDetailsMatch[1].matchAll(/<el-table-column[^>]*\blabel="([^"]+)"/g),
    match => match[1]
  ).filter(label => label !== "序号")
  assert.deepStrictEqual(desktopDetailLabels, [
    "物料",
    "编码",
    "类型",
    "规格 / 等级",
    "申请数量",
    "单位",
    "参考成本价",
    "小计"
  ], "the desktop transfer-detail contract must not drift silently")

  for (const label of desktopDetailLabels) {
    assert.ok(quickSource.includes(label),
      `mobile quick approval must expose the desktop transfer-detail field: ${label}`)
  }
  for (const field of [
    "itemName", "productName", "itemCode", "productCode", "itemType",
    "spec", "grade", "quantity", "unit", "costPrice", "amount"
  ]) {
    assert.ok(quickSource.includes(field),
      `mobile quick approval must preserve the desktop line-item value/fallback: ${field}`)
  }
  assert.ok(
    /v-for="\([^\"]+\) in detailRows"/.test(quickSource) ||
      /v-for="[^\"]+ in detailRows"/.test(quickSource),
    "mobile quick approval must render every transfer line item rather than only an aggregate"
  )
  assert.ok(quickSource.includes("暂无调拨明细"),
    "mobile quick approval must explain when the fresh preview contains no line items")
  assert.ok(
    /\.mobile-todo-quick-detail[^\{]*\{[\s\S]*?max-height:\s*[^;]+;[\s\S]*?overflow-y:\s*auto/.test(quickSource) ||
      /\.mobile-todo-quick-detail[^\{]*\{[\s\S]*?overflow-y:\s*auto[\s\S]*?max-height:\s*[^;]+;/.test(quickSource),
    "mobile quick approval must keep long transfer-detail lists vertically scrollable"
  )
  assert.ok(/class="mobile-todo-quick-detail-list"[\s\S]*?tabindex="0"/.test(quickSource),
    "the independently scrollable goods list must be keyboard reachable")
  assert.ok(/class="mobile-todo-quick-detail-list"[\s\S]*?role="list"/.test(quickSource),
    "the visually reset ordered list must preserve explicit list semantics in Safari")
  assert.ok(quickSource.includes(".mobile-todo-quick-detail-list:focus-visible"),
    "the keyboard-reachable goods list must have a visible focus indicator")
}

{
  const quick = bindComponent(quickComponent, {
    visible: true,
    row: { businessNo: "TF-CARD-1" },
    preview: {
      orderNo: "TF-20260820001",
      fromDeptName: "上海一店",
      toDeptName: "杭州仓",
      details: [{ quantity: 2 }, { quantity: 3 }],
      totalAmount: 12.3,
      approvalSummary: { currentNodeName: "仓库主管审批" },
      approvalWarnings: [" 库存将被重新校验 ", "", null]
    },
    nextAvailable: true,
    retryAvailable: false
  })

  assert.strictEqual(quick.canSubmit, true, "a freshly loaded preview should enable quick approval")
  assert.strictEqual(quick.transferNo, "TF-20260820001")
  assert.strictEqual(quick.directionText, "上海一店 → 杭州仓")
  assert.strictEqual(quick.quantityText, "5（2 项）")
  assert.strictEqual(quick.amountText, "¥12.30")
  assert.strictEqual(quick.approvalNode, "仓库主管审批")
  assert.deepStrictEqual(plain(quick.warnings), ["库存将被重新校验"])
  assert.deepStrictEqual(plain(quick.detailRows), plain(quick.preview.details),
    "mobile quick approval must expose every fresh preview line item")

  quick.confirm(false)
  quick.confirm(true)
  assert.deepStrictEqual(plain(quick.emitted), [
    { name: "confirm", payload: { comment: "", continueNext: false } },
    { name: "confirm", payload: { comment: "", continueNext: true } }
  ], "an empty optional opinion and the continuous-processing choice must be emitted exactly")

  quick.retry()
  assert.strictEqual(quick.emitted.length, 2, "retry must stay disabled until the parent marks it available")
  quick.retryAvailable = true
  quick.comment = "已复核"
  quick.retry()
  assert.deepStrictEqual(plain(quick.emitted[2]), {
    name: "retry",
    payload: { comment: "已复核" }
  }, "retry must send the current opinion through the dedicated original-request-id path")

  quickComponent.watch["row.todoKey"].call(quick)
  assert.strictEqual(quick.comment, "",
    "continuous processing must clear the previous item's optional opinion")

  quick.submitting = true
  quick.requestClose()
  assert.strictEqual(quick.emitted.length, 3, "the quick sheet must not close while submitting")
  quick.submitting = false
  quick.requestClose()
  assert.deepStrictEqual(plain(quick.emitted[3]), { name: "close" })
}

{
  const noDetails = bindComponent(quickComponent, {
    preview: { orderNo: "TF-NO-DETAILS" }
  })
  assert.deepStrictEqual(plain(noDetails.detailRows), [],
    "a fresh preview without details must degrade to an empty line-item list")
  assert.strictEqual(noDetails.canSubmit, false,
    "an approval preview without goods must fail closed")
  noDetails.confirm(false)
  assert.deepStrictEqual(plain(noDetails.emitted), [],
    "an approval preview without goods must not emit a confirm action")
}

{
  const desktopParity = bindComponent(quickComponent, {
    preview: {
      orderNo: "TF-DESKTOP-PARITY",
      totalAmount: null,
      details: [
        {
          detailId: 1,
          itemType: "product",
          itemName: "大米",
          itemCode: "RM-01",
          quantity: 2,
          costPrice: 18.5,
          unit: "袋",
          spec: "5kg"
        },
        {
          detailId: 2,
          itemType: "gift",
          productName: "礼盒",
          productCode: "GF-02",
          quantity: 3,
          amount: 120,
          unit: "盒",
          grade: "A"
        }
      ]
    }
  })

  assert.strictEqual(desktopParity.detailRows.length, 2)
  assert.strictEqual(desktopParity.detailQuantityTotal, 5)
  assert.strictEqual(desktopParity.detailTotal, 157)
  assert.strictEqual(desktopParity.quantityText, "5（2 项）")
  assert.strictEqual(desktopParity.amountText, "¥157.00")
  assert.strictEqual(desktopParity.itemTypeLabel("gift"), "礼盒")
  assert.strictEqual(desktopParity.specGrade(desktopParity.detailRows[0]), "5kg")
  assert.strictEqual(desktopParity.specGrade(desktopParity.detailRows[1]), "A")
  assert.strictEqual(desktopParity.lineAmount(desktopParity.detailRows[0]), 37)
  assert.strictEqual(desktopParity.lineAmount(desktopParity.detailRows[1]), 120)
  assert.strictEqual(desktopParity.money(null), "无权限或暂无数据")
  assert.strictEqual(desktopParity.detailRowKey(desktopParity.detailRows[0], 0), 1)
}

{
  const noAmount = bindComponent(quickComponent, { preview: { transferId: 1, status: "submitted" } })
  assert.strictEqual(noAmount.amountText, "", "a missing amount must not be rendered as zero")
}

{
  const missingPreview = bindComponent(quickComponent, {
    visible: true,
    row: { businessNo: "TF-CARD-2" },
    preview: null,
    loading: false,
    errorMessage: ""
  })
  missingPreview.confirm(true)
  assert.strictEqual(missingPreview.canSubmit, false)
  assert.deepStrictEqual(plain(missingPreview.emitted), [],
    "quick approval must fail closed before a fresh detail preview is available")
}

{
  const permissionChanged = bindComponent(quickComponent, {
    visible: true,
    eligible: false,
    retryAvailable: true,
    errorMessage: "审批结果暂时未知；可使用原请求号安全重试。",
    preview: { transferId: 1, status: "submitted" }
  })
  assert.strictEqual(permissionChanged.canSubmit, false)
  assert.ok(permissionChanged.effectiveErrorMessage.includes("权限"),
    "a post-open eligibility change must override an older unknown-result retry message")
  permissionChanged.confirm(false)
  permissionChanged.retry()
  assert.deepStrictEqual(plain(permissionChanged.emitted), [])
}

for (const wording of [
  "统一通过意见（选填）",
  "停止未开始项",
  "重试未成功项",
  "部分失败不会撤销已经成功的审批"
]) assert.ok(batchSource.includes(wording), `batch approval sheet must contain ${wording}`)
assert.ok(batchSource.includes('@click="$emit(\'stop\')"'),
  "batch approval must emit a stop event without pretending in-flight requests were canceled")
assert.ok(batchSource.includes("maxlength=\"500\"") && !batchSource.includes("required"),
  "the unified batch approval opinion must remain optional and bounded")
assert.ok(batchSource.includes("results.length") && batchSource.includes("resultLabel(item)"),
  "batch approval must switch from requested items to per-item results")

{
  const items = [
    { todoKey: "todo-1", businessNo: "TF-1", row: { fromDeptName: "门店一", toDeptName: "仓库" } },
    { todoKey: "todo-2", businessNo: "TF-2", row: { routeParams: { fromDeptName: "门店二", toDeptName: "仓库" } } }
  ]
  const batch = bindComponent(batchComponent, {
    visible: true,
    items,
    processed: 1,
    total: 2,
    results: []
  })

  assert.deepStrictEqual(plain(batch.displayItems), plain(items))
  assert.strictEqual(batch.progressPercentage, 50)
  assert.strictEqual(batch.canRetry, false)
  assert.strictEqual(batch.direction(items[0].row), "门店一 → 仓库")
  assert.strictEqual(batch.direction(items[1].row), "门店二 → 仓库")

  batch.confirm()
  assert.deepStrictEqual(plain(batch.emitted[0]), {
    name: "confirm",
    payload: { comment: "" }
  }, "batch approval must accept an empty optional opinion")

  batch.executing = true
  batch.requestClose()
  batch.confirm()
  assert.strictEqual(batch.emitted.length, 1, "the batch sheet must block close and duplicate confirm while executing")
}

{
  const partialResults = [
    { todoKey: "todo-1", status: "SUCCESS", row: {}, message: "" },
    { todoKey: "todo-2", status: "ALREADY_HANDLED", row: {}, message: "" },
    { todoKey: "todo-3", status: "FAILED", errorKind: "BUSINESS", row: {}, message: "库存已变化" },
    { todoKey: "todo-4", status: "FAILED", errorKind: "SESSION", row: {}, message: "登录已失效" },
    { todoKey: "todo-5", status: "CANCELED", errorKind: "USER_CANCELED", row: {}, message: "已停止" }
  ]
  const batch = bindComponent(batchComponent, {
    visible: true,
    items: [{ todoKey: "stale-input" }],
    processed: 7,
    total: 5,
    results: partialResults,
    comment: "批量复核"
  })

  assert.deepStrictEqual(plain(batch.displayItems), plain(partialResults),
    "partial batch results must replace the pre-submit item list")
  assert.strictEqual(batch.progressPercentage, 100, "batch progress must cap at 100 percent")
  assert.strictEqual(batch.canRetry, true,
    "business failures and user-stopped items must be retryable even when session failures are not")
  assert.deepStrictEqual(partialResults.map(item => batch.resultTone(item.status)),
    ["success", "success", "danger", "danger", "muted"])
  assert.deepStrictEqual(partialResults.map(item => batch.resultLabel(item)),
    ["通过", "已处理", "库存已变化", "登录已失效", "已停止"])

  batch.retry()
  assert.deepStrictEqual(plain(batch.emitted[0]), {
    name: "retry",
    payload: { comment: "批量复核" }
  }, "batch retry must preserve the unified opinion for the remaining items")
  batch.requestClose()
  assert.deepStrictEqual(plain(batch.emitted[1]), { name: "close" })
}

{
  const sessionOnly = bindComponent(batchComponent, {
    results: [
      { status: "FAILED", errorKind: "SESSION", message: "登录已失效" },
      { status: "FAILED", errorKind: "FORBIDDEN", message: "权限已变化" },
      { status: "FAILED", errorKind: "VALIDATION", message: "资格已变化" }
    ]
  })
  sessionOnly.retry()
  assert.strictEqual(sessionOnly.canRetry, false)
  assert.deepStrictEqual(plain(sessionOnly.emitted), [],
    "session, permission, and pre-submit validation failures must not expose an unsafe retry action")
}

console.log("mobileTodoApprovalSheets tests passed")
