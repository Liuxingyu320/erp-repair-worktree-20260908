const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")

function readUi(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
}

function assertIncludes(source, needle, message) {
  assert.ok(source.includes(needle), `${message}\nExpected source to include: ${needle}`)
}

const publicIndex = readUi("public/index.html")
assertIncludes(publicIndex, "width=device-width, initial-scale=1, viewport-fit=cover", "desktop viewport should preserve responsive sizing")
assert.ok(!publicIndex.includes("maximum-scale"), "desktop viewport must not cap browser zoom")
assert.ok(!publicIndex.includes("user-scalable=no"), "desktop viewport must not disable user zoom")

const globalStyles = readUi("src/assets/styles/index.scss")
const desktopStyles = readUi("src/assets/styles/desktop-system.scss")
assertIncludes(globalStyles, "a:focus-visible", "links should keep a visible keyboard focus indicator")
assert.ok(!globalStyles.includes("div:focus {\n  outline: none"), "generic focused containers must not have their outline suppressed")
assertIncludes(desktopStyles, '[role="separator"]:focus-visible', "keyboard-resizable controls should receive the desktop focus ring")
assertIncludes(desktopStyles, "@media screen and (min-width: 992px) and (max-height: 820px)", "low-height desktop screens should use the compact vertical rhythm")

const treePanel = readUi("src/components/TreePanel/index.vue")
assertIncludes(treePanel, 'role="separator"', "tree resize handle should expose separator semantics")
assertIncludes(treePanel, 'aria-label="调整组织树宽度"', "tree resize handle should have an accessible name")
assertIncludes(treePanel, '@keydown="handleResizeKeydown"', "tree resize handle should support keyboard resizing")
assertIncludes(treePanel, 'aria-label="刷新组织树"', "tree refresh icon should have an accessible name")
assertIncludes(treePanel, "handleResizeKeydown(e)", "tree panel should implement Arrow/Home/End width controls")
assert.ok(
  (treePanel.match(/<button/g) || []).length >= 3,
  "tree expand, refresh, and collapse controls should be native buttons"
)

const toolbar = readUi("src/components/RightToolbar/index.vue")
assertIncludes(toolbar, 'role="toolbar"', "table utility controls should expose toolbar semantics")
assertIncludes(toolbar, 'aria-label="表格工具栏"', "table toolbar should have an accessible name")
assertIncludes(toolbar, ':aria-pressed="showSearch ? \'true\' : \'false\'"', "search visibility button should expose its current state")
assertIncludes(toolbar, 'aria-haspopup="menu"', "column chooser should expose its popup behavior")

const tableDirective = readUi("src/directive/accessibility/table.js")
const shellDirective = readUi("src/directive/accessibility/shell.js")
const directiveIndex = readUi("src/directive/index.js")
const appSource = readUi("src/App.vue")
assertIncludes(shellDirective, "['.el-pagination .btn-prev', '上一页']", "pagination previous buttons should receive an accessible name")
assertIncludes(shellDirective, "['.el-pagination .btn-next', '下一页']", "pagination next buttons should receive an accessible name")
assertIncludes(appSource, "v-accessible-shell", "the persistent app shell should repair dynamically rendered pagination controls")
assertIncludes(directiveIndex, "Vue.directive('accessibleShell', accessibleShell)", "accessible shell directive should be globally registered")
assertIncludes(tableDirective, "FIXED_REGION_SELECTOR", "accessible table directive should target Element UI fixed clones")
assertIncludes(tableDirective, "region.setAttribute('aria-hidden', 'true')", "fixed table clones should be hidden from assistive technology")
assertIncludes(tableDirective, "control.setAttribute('tabindex', '-1')", "fixed clone controls should be removed from duplicate Tab order")
assertIncludes(tableDirective, "th.el-table-column--selection", "selection naming must not capture unrelated checkbox controls")
assertIncludes(tableDirective, "syncSwitchControlNames", "Element UI switch inputs should inherit row-aware accessible names")
assertIncludes(tableDirective, "labelSelectionControls", "selection checkboxes should receive row-aware names")
assertIncludes(tableDirective, "MutationObserver", "table semantics should be restored after Element UI rerenders")
assertIncludes(directiveIndex, "Vue.directive('accessibleTable', accessibleTable)", "accessible table directive should be globally registered")

const dataState = readUi("src/components/DataState/index.vue")
const mainSource = readUi("src/main.js")
assertIncludes(dataState, ':role="stateRole"', "shared data states should announce empty/loading/error semantics")
assertIncludes(dataState, 'return this.type === "error" ? "alert" : "status"', "error states should use alert semantics")
assertIncludes(mainSource, "Vue.component('DataState'", "shared data state should be available to desktop list pages")

const stockSource = readUi("src/views/inventory/stock/index.vue")
assertIncludes(stockSource, "v-accessible-table=\"'库存列表'\"", "inventory table should suppress duplicate fixed-table semantics")
assertIncludes(stockSource, 'element-loading-text="正在加载库存数据"', "inventory loading should explain what is in progress")
assertIncludes(stockSource, ":type=\"stockLoadError ? 'error' : 'empty'\"", "inventory should distinguish load failure from a valid empty result")
assertIncludes(stockSource, "stockLoadError = \"暂时无法加载库存列表", "inventory request failure should produce an actionable page-level state")

const signTaskSource = readUi("src/views/oa/signTask/index.vue")
assertIncludes(signTaskSource, "v-accessible-table=\"'签约任务列表'\"", "sign task table should suppress duplicate fixed-table semantics")
assertIncludes(signTaskSource, 'element-loading-text="正在加载签约任务"', "sign task loading should explain what is in progress")
assertIncludes(signTaskSource, ":type=\"listError ? 'error' : 'empty'\"", "sign task should distinguish load failure from a valid empty result")
assertIncludes(signTaskSource, "listError = '暂时无法加载签约任务", "sign task request failure should produce an actionable page-level state")

const userSource = readUi("src/views/system/user/index.vue")
assertIncludes(userSource, "v-accessible-table=\"'用户列表'\"", "user management should remove duplicate fixed-table semantics")
assertIncludes(userSource, ":aria-label=\"`${scope.row.userName || '用户'}的账号状态`\"", "user status switches should announce their row context")
assertIncludes(userSource, 'ref="addUserButton"', "add-user focus should have a stable return target")
assertIncludes(userSource, '@opened="handleUserDialogOpened"', "user dialog should move focus after its opening transition")
assertIncludes(userSource, '@closed="handleUserDialogClosed"', "user dialog should restore focus after closing")
assertIncludes(userSource, 'ref="userDialogInitialInput"', "user dialog should expose its first logical form field")
assertIncludes(userSource, 'document.addEventListener("keydown", this.handleUserDialogDocumentKeydown, true)', "user dialog should enforce a capture-phase Tab boundary")
assertIncludes(userSource, 'if (!dialog.contains(active))', "user dialog should recover focus that escapes to the background")
assertIncludes(userSource, 'event.shiftKey && active === first', "user dialog should wrap Shift+Tab at its first control")
assertIncludes(userSource, '!event.shiftKey && active === last', "user dialog should wrap Tab at its last control")
assertIncludes(userSource, 'trigger && document.contains(trigger)', "user dialog should return focus to the invoking control")

;[
  "src/views/system/role/index.vue",
  "src/views/system/post/index.vue",
  "src/views/system/operlog/index.vue",
  "src/views/system/logininfor/index.vue",
  "src/views/inventory/category/index.vue",
  "src/views/inventory/supplier/index.vue",
  "src/views/hr/completeness/index.vue"
].forEach(file => {
  assertIncludes(readUi(file), "v-accessible-table", `${file} should apply shared table accessibility behavior`)
})

const homeSource = readUi("src/views/index.vue")
assertIncludes(homeSource, "@media (min-width: 992px) and (max-height: 820px)", "desktop home should compact only for low-height PC viewports")
assertIncludes(homeSource, "padding: 22px 28px !important", "desktop home hero should recover vertical space at 1366x768")

console.log("desktopUiUxAccessibility tests passed")
