const assert = require("assert")
const path = require("path")
const fs = require("fs")

const pageState = require("../src/views/mobile/mobilePageState")
const {
  resolveMobilePageState,
  resolveMobilePageStateActionLabel,
  resolveMobileStatusTone,
  isBlockingPageState,
  PAGE_STATE_PRIORITY
} = pageState

assert.ok(Array.isArray(PAGE_STATE_PRIORITY) && PAGE_STATE_PRIORITY[0] === "session-expired")

const session = resolveMobilePageState({
  errorMessage: "登录状态已过期",
  loading: true,
  itemCount: 0,
  hasActiveFilters: true
})
assert.strictEqual(session.type, "session-expired")
assert.strictEqual(session.actionId, "relogin")
assert.ok(isBlockingPageState(session))

const permission = resolveMobilePageState({
  errorType: "permission",
  errorMessage: "没有权限"
})
assert.strictEqual(permission.type, "permission-error")

const context = resolveMobilePageState({
  requireContext: true,
  contextId: ""
})
assert.strictEqual(context.type, "context-required")

const network = resolveMobilePageState({
  errorMessage: "网络中断"
})
assert.strictEqual(network.type, "network-error")

const loading = resolveMobilePageState({ loading: true })
assert.strictEqual(loading.type, "loading")
assert.strictEqual(loading.actionId, "")

const filtered = resolveMobilePageState({ itemCount: 0, hasActiveFilters: true })
assert.strictEqual(filtered.type, "filtered-empty")
assert.strictEqual(resolveMobilePageStateActionLabel(filtered), "清除筛选")

const empty = resolveMobilePageState({ itemCount: 0, listTitle: "库存", canCreate: true })
assert.strictEqual(empty.type, "empty")
assert.strictEqual(empty.actionId, "create")

assert.strictEqual(resolveMobilePageState({ itemCount: 2 }), null)

assert.strictEqual(resolveMobileStatusTone("待审批"), "pending")
assert.strictEqual(resolveMobileStatusTone("已通过"), "success")
assert.strictEqual(resolveMobileStatusTone("驳回"), "danger")
assert.strictEqual(resolveMobileStatusTone("草稿"), "draft")

const tokenSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/styles/mobileSystem.scss"),
  "utf8"
)
;[
  "--mobile-color-success",
  ".mobile-data-state",
  ".mobile-status-chip",
  ".mobile-bottom-action-bar",
  ".glass-card",
  "--mobile-font-title"
].forEach(token => {
  assert.ok(tokenSource.includes(token), `mobile system should define ${token}`)
})

const legacyAssetsPath = path.resolve(__dirname, "../src/assets/styles/mobile-system.scss")
const legacyViewsPath = path.resolve(__dirname, "../src/views/styles/mobileSystem.scss")
assert.ok(!fs.existsSync(legacyAssetsPath), "legacy assets mobile-system.scss should be retired")
assert.ok(!fs.existsSync(legacyViewsPath), "legacy views mobileSystem.scss should be retired")

const indexSource = fs.readFileSync(
  path.resolve(__dirname, "../src/assets/styles/index.scss"),
  "utf8"
)
assert.ok(
  indexSource.includes("views/mobile/styles/mobileSystem.scss"),
  "global styles should import the single tea-green token source"
)
assert.ok(
  !indexSource.includes("assets/styles/mobile-system.scss"),
  "global styles must not import the legacy blue glass system"
)

console.log("mobilePageState.test.js passed")
