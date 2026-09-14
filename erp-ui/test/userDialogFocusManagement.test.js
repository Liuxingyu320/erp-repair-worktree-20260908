const assert = require("assert")
const fs = require("fs")
const path = require("path")

const source = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/user/index.vue"),
  "utf8"
)

function assertIncludes(needle, message) {
  assert.ok(source.includes(needle), `${message}\nExpected source to include: ${needle}`)
}

assertIncludes('ref="addUserButton"', "add-user trigger should be available for focus restoration")
assertIncludes('aria-haspopup="dialog"', "add-user trigger should announce that it opens a dialog")
assertIncludes(':aria-expanded="open ? \'true\' : \'false\'"', "add-user trigger should expose dialog visibility")
assertIncludes('@opened="handleUserDialogOpened"', "dialog opening should start focus management")
assertIncludes('@closed="handleUserDialogClosed"', "dialog closing should restore focus")
assertIncludes('ref="userDialogInitialInput"', "dialog should identify the first logical form field")
assertIncludes('placeholder="请输入姓名" maxlength="30" autofocus', "the name field should be the initial focus target")
assertIncludes('document.addEventListener("keydown", this.handleUserDialogDocumentKeydown, true)', "dialog should trap Tab before background controls receive it")
assertIncludes('if (!dialog.contains(active))', "escaped focus should be recovered into the dialog")
assertIncludes('event.shiftKey && active === first', "Shift+Tab should wrap from the first dialog control")
assertIncludes('!event.shiftKey && active === last', "Tab should wrap from the last dialog control")
assertIncludes(':close-on-press-escape="true"', "Escape should remain an explicit close path")
assertIncludes('trigger && document.contains(trigger)', "closing should restore a still-mounted trigger")
assertIncludes('this.captureUserDialogTrigger("addUserButton")', "add flow should capture its invoking button before the async option request")
assertIncludes("this.removeUserDialogFocusGuard()", "component teardown should remove the document focus guard")

const handleAddStart = source.indexOf("handleAdd()")
const handleAddEnd = source.indexOf("handleUpdate(", handleAddStart)
const handleAdd = handleAddStart >= 0 && handleAddEnd > handleAddStart
  ? source.slice(handleAddStart, handleAddEnd)
  : ""
const captureIndex = handleAdd.indexOf('this.captureUserDialogTrigger("addUserButton")')
const requestMatch = handleAdd.match(/\bgetUser\s*\(/)
assert.ok(
  captureIndex >= 0 && requestMatch && requestMatch.index > captureIndex,
  "the trigger must be captured before loading dialog options"
)

console.log("userDialogFocusManagement tests passed")
