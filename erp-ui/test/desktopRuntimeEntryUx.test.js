const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const uiRoot = path.resolve(__dirname, "..")
const readUi = relativePath => fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")

function loadSearchSelection() {
  const source = readUi("src/utils/searchSelection.js")
  const sandbox = { module: { exports: {} }, exports: {} }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(
    source.replace(/\bexport\s+function\s+getFirstSelectableIndex/, "function getFirstSelectableIndex") +
      "\nmodule.exports = { getFirstSelectableIndex }",
    sandbox,
    { filename: "searchSelection.js" }
  )
  return sandbox.module.exports
}

const searchSource = readUi("src/components/HeaderSearch/index.vue")

assert.ok(
  searchSource.includes("<button") &&
    searchSource.includes('aria-label="搜索菜单"') &&
    searchSource.includes('type="button"') &&
    searchSource.includes('@click.stop="click"'),
  "header search should expose a semantic button that preserves the existing click action"
)
assert.ok(
  searchSource.includes("getFirstSelectableIndex(this.options)") &&
    !searchSource.includes('@mouseleave="activeIndex = -1"'),
  "search results should keep a valid first selection so Enter works without an ArrowDown precondition"
)

const { getFirstSelectableIndex } = loadSearchSelection()
assert.strictEqual(getFirstSelectableIndex([]), -1)
assert.strictEqual(getFirstSelectableIndex(null), -1)
assert.strictEqual(getFirstSelectableIndex([{ path: "/system/notice" }]), 0)
assert.strictEqual(getFirstSelectableIndex([{ path: "/a" }, { path: "/b" }]), 0)

const loginSource = readUi("src/views/login.vue")

assert.strictEqual(
  (loginSource.match(/placeholder="请输入图中算式结果"/g) || []).length,
  2,
  "desktop and mobile login inputs should both explain that the math result is required"
)
assert.ok(
  loginSource.includes('message: "请输入图中算式结果"'),
  "captcha validation should repeat the same actionable instruction"
)
assert.strictEqual(
  (loginSource.match(/alt="验证码(?:算式，点击刷新|，点击可刷新)"/g) || []).length,
  2,
  "both captcha images should expose useful alternative text"
)
assert.strictEqual(
  (loginSource.match(/aria-label="刷新验证码"/g) || []).length,
  2,
  "both captcha refresh controls should have an accessible name"
)
assert.ok(
  (loginSource.match(/<button type="button" aria-label="刷新验证码"/g) || []).length === 2 ||
    ((loginSource.match(/@keydown\.enter\.prevent="getCode"/g) || []).length === 2 &&
      (loginSource.match(/@keydown\.space\.prevent="getCode"/g) || []).length === 2),
  "both captcha refresh controls should use native buttons or explicitly support Enter and Space"
)
assert.ok(
  loginSource.indexOf('this.loginForm.code = ""') < loginSource.indexOf("getCodeImg().then"),
  "refreshing the captcha should clear the stale answer before requesting a new challenge"
)

const noticeSource = readUi("src/layout/components/HeaderNotice/index.vue")

assert.ok(
  noticeSource.includes("<button") &&
    noticeSource.includes('type="button"') &&
    noticeSource.includes(':aria-label="noticeAriaLabel"') &&
    noticeSource.includes(':aria-expanded="noticeVisible"') &&
    noticeSource.includes('aria-haspopup="dialog"'),
  "the notice trigger should be a semantic button that reports its popover state"
)
assert.ok(
  noticeSource.includes('@click.stop="onNoticeActivate"') &&
    noticeSource.includes('@blur="onNoticeLeave"') &&
    !noticeSource.includes('@focus="onNoticeEnter"'),
  "native button activation should open the notice popover without reopening it on focus return"
)
assert.ok(
  noticeSource.includes("noticeAriaLabel()") &&
    noticeSource.includes('`通知公告，${this.noticeUnreadCount} 条未读`'),
  "the notice button should announce the unread count without relying on the visual badge"
)
assert.ok(
  noticeSource.includes("popper.setAttribute('role', 'dialog')") &&
    noticeSource.includes("trigger.setAttribute('aria-controls', popper.id)") &&
    noticeSource.includes("trigger.removeAttribute('aria-describedby')"),
  "the interactive notice popover should expose dialog semantics that match the trigger"
)
assert.ok(
  noticeSource.includes("popper.addEventListener('focusin'") &&
    noticeSource.includes("popper.contains(nextTarget)") &&
    noticeSource.includes("trigger.contains(nextTarget)") &&
    noticeSource.includes("onNoticeActivate()") &&
    noticeSource.includes("this.openNotice(true)") &&
    noticeSource.includes("if (focusPopover) popper.focus()") &&
    noticeSource.includes("this.$refs.noticeTrigger.focus()"),
  "keyboard activation should move focus into the notice dialog and Escape should return it to the closed trigger"
)
assert.ok(
  /<button[\s\S]*?type="button"[\s\S]*?class="notice-mark-all"/.test(noticeSource) &&
    noticeSource.includes('<button v-for="item in noticeList"') &&
    !noticeSource.includes('<span class="notice-mark-all"') &&
    !noticeSource.includes('<div v-for="item in noticeList"'),
  "notice actions and rows should be native keyboard-operable buttons"
)

console.log("desktopRuntimeEntryUx search tests passed")
