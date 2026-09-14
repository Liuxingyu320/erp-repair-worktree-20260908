const assert = require("assert")
const fs = require("fs")
const path = require("path")

const read = relativePath => fs.readFileSync(path.resolve(__dirname, relativePath), "utf8")
const noticeSource = read("../src/views/system/notice/index.vue")
const noticeApiSource = read("../src/api/system/notice.js")
const readUsersSource = read("../src/views/system/notice/ReadUsers.vue")
const headerDetailSource = read("../src/layout/components/HeaderNotice/DetailView.vue")
const userStoreSource = read("../src/store/modules/user.js")
const toolbarSource = read("../src/components/RightToolbar/index.vue")
const editorSource = read("../src/components/Editor/index.vue")
const loginSource = read("../src/views/login.vue")
const roleSource = read("../src/views/system/role/index.vue")

assert.ok(
  userStoreSource.includes("noticeWorkflow: false") &&
    noticeSource.includes("workflowEnabled") &&
    noticeSource.includes('hasPermi("system:notice:publish")') &&
    noticeSource.includes("公告发布工作流尚未开放"),
  "notice publishing must be capability-gated, permission-gated and fail closed"
)

for (const lifecycle of ["DRAFT", "SCHEDULED", "PUBLISHED", "OFFLINE"]) {
  assert.ok(noticeSource.includes(lifecycle), `notice UI must expose lifecycle ${lifecycle}`)
}
assert.ok(
  !noticeSource.includes("queryParams.status") &&
    noticeSource.includes("只允许删除草稿") &&
    noticeSource.includes("已发布内容不能原地覆盖，只能创建新版本") &&
    noticeSource.includes("创建新版本"),
  "notice lifecycle UI must not fall back to legacy status broadcasting or mutable published content"
)

assert.ok(
  noticeSource.includes("保存只会生成草稿，不会向任何人广播") &&
    noticeSource.includes("草稿保存成功，尚未发布") &&
    noticeSource.includes("发布确认") &&
    noticeSource.includes("预计接收") &&
    noticeSource.includes("当前版本") &&
    noticeSource.includes("立即发布") &&
    noticeSource.includes("计划发布"),
  "draft saving and explicit publishing must be clearly separated and confirmed"
)

assert.ok(
  noticeSource.includes("buildAudienceRules") &&
    noticeSource.includes("previewNoticeAudience") &&
    noticeSource.includes("已跨规则去重") &&
    noticeSource.includes("包含所选组织的下级部门/门店") &&
    noticeSource.includes("发布时只选取启用账号并固化接收人快照"),
  "audience selection should share the backend preview and explain immutable recipient snapshots"
)

assert.ok(
  noticeSource.includes("safePreviewHtml") &&
    noticeSource.includes("sanitizeNoticeHtml") &&
    !noticeSource.includes('v-html="form.noticeContent"') &&
    noticeSource.includes("还没有公告草稿") &&
    noticeSource.includes("页面右上角的“通知公告”铃铛"),
  "notice preview must be sanitized and the empty state must explain where published notices appear"
)

for (const endpoint of [
  "/system/notice/inbox/",
  "/audience-preview",
  "/audience-options",
  "/publish",
  "/cancel-schedule",
  "/offline",
  "/new-version",
]) {
  assert.ok(noticeApiSource.includes(endpoint), `notice API is missing ${endpoint}`)
}
assert.ok(
  headerDetailSource.includes("getInboxNotice") &&
    !headerDetailSource.includes("getNotice("),
  "header notice detail must use the recipient-scoped inbox endpoint"
)
assert.ok(
  readUsersSource.includes("总体已读 <strong>{{ readCount }}</strong> / {{ recipientCount }} 人") &&
    readUsersSource.includes("筛选结果 {{ total }} 人") &&
    readUsersSource.includes('aria-label="筛选已读用户"') &&
    readUsersSource.includes("focusSearch"),
  "read statistics must use recipient cardinality and provide an accessible focused search"
)

assert.ok(
  loginSource.includes('for="desktop-login-username"') &&
    loginSource.includes('for="desktop-login-password"') &&
    loginSource.includes('for="mobile-login-username"') &&
    loginSource.includes('for="mobile-login-password"'),
  "desktop and mobile login fields must have visible associated labels"
)
assert.ok(
  toolbarSource.includes('aria-label="刷新列表"') &&
    toolbarSource.includes("隐藏搜索条件") &&
    editorSource.includes('setAttribute("role", "textbox")') &&
    editorSource.includes('setAttribute("role", "toolbar")') &&
    editorSource.includes("${this.ariaLabel}工具栏"),
  "icon-only controls and the rich text editor must expose stable accessible names"
)
assert.ok(
  roleSource.includes(":aria-label=") &&
    roleSource.includes("scope.row.status === '0' ? '停用' : '启用'") &&
    roleSource.includes("restoreAddButtonFocus") &&
    noticeSource.includes("focusNoticeTitle") &&
    noticeSource.includes("restoreTriggerFocus"),
  "role switches and modal workflows must expose names and restore keyboard focus"
)
assert.ok(
  noticeSource.includes('label="搜索公告标题"') &&
    noticeSource.includes('label="公告草稿标题"') &&
    noticeSource.includes("发布公告：${scope.row.noticeTitle}"),
  "notice fields and repeated publish actions must have distinct accessible names"
)

console.log("systemNoticeWorkflowUx tests passed")
