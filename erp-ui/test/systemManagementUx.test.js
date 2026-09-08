const assert = require("assert")
const fs = require("fs")
const path = require("path")

const userSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/user/index.vue"),
  "utf8"
)

assert.ok(
  !userSource.includes('getHrEmployeeFormOptions') &&
    userSource.includes('response.employeeStatusOptions') &&
    userSource.includes('this.employeeStatusOptionsLoaded = this.employeeStatusOptions.length > 0'),
  'system-user rendering should consume its own safe status options without calling an HR-only API'
)
const userViewSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/user/view.vue"),
  "utf8"
)
const signingProfileOptionsSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/hr/components/signingProfileOptions.js"),
  "utf8"
)
const authUserSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/role/authUser.vue"),
  "utf8"
)
const authRoleSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/user/authRole.vue"),
  "utf8"
)
const roleSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/role/index.vue"),
  "utf8"
)
const userReviewSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/user/components/UserFormReview.vue"),
  "utf8"
)
const userFilterSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/user/components/UserFilterDrawer.vue"),
  "utf8"
)
const supervisorSelectSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/user/components/SupervisorUserSelect.vue"),
  "utf8"
)
const shopSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/shop/index.vue"),
  "utf8"
)
const salarySource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/salary/index.vue"),
  "utf8"
)
const operlogSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/operlog/index.vue"),
  "utf8"
)
const configSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/config/index.vue"),
  "utf8"
)
const dictSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/dict/index.vue"),
  "utf8"
)
const rightToolbarSource = fs.readFileSync(
  path.resolve(__dirname, "../src/components/RightToolbar/index.vue"),
  "utf8"
)

assert.ok(
  userSource.includes("system:user:resetPwd"),
  "password reset must use its dedicated high-risk permission"
)

assert.ok(
  userSource.includes("openPiiExport") &&
    userSource.includes("handlePiiExport") &&
    userSource.includes("system:user:pii:export") &&
    userSource.includes("piiExportReasonCode") &&
    userSource.includes("piiExportConfirmed") &&
    userSource.includes("system/user/export-sensitive"),
  "sensitive user export should require both permissions, a controlled reason and explicit confirmation"
)

assert.ok(
  configSource.includes("hasPermiAnd(['system:config:edit', 'system:config:query'])") &&
    configSource.includes("v-hasPermi=\"['system:config:refresh']\"") &&
    configSource.includes("v-hasPermi=\"['system:config:remove']\"") &&
    !configSource.includes("@click=\"handleDelete\"\n          v-hasPermi=\"['system:config:refresh']\"") &&
    !configSource.includes("@click=\"handleRefreshCache\"\n          v-hasPermi=\"['system:config:remove']\""),
  "config detail, delete and cache refresh controls should align with dedicated backend permissions"
)

assert.ok(
  userSource.includes("v-hasPermi=\"['system:user:resetPwd']\"") &&
    userSource.includes("command=\"handleResetPwd\""),
  "reset password and its containing More menu should only be visible with system:user:resetPwd"
)

assert.ok(
  userSource.includes(":title=\"isDefaultPasswordCredential ? '新用户默认密码' : '一次性临时密码'\"") &&
    userSource.includes("isDefaultPasswordCredential()") &&
    userSource.includes(':value="temporaryCredential.temporaryPassword"') &&
    userSource.includes(":aria-label=\"isDefaultPasswordCredential ? '新用户默认密码' : '一次性临时密码'\"") &&
    userSource.includes('v-clipboard:copy="temporaryCredential.temporaryPassword"') &&
    userSource.includes('handleCredentialCopySuccess') &&
    userSource.includes('handleCredentialCopyError') &&
    userSource.includes('handleCredentialClosed') &&
    userSource.includes('this.temporaryCredential.temporaryPassword = ""') &&
    !userSource.includes('v-html'),
  "temporary password should be visibly text-bound, copyable, and cleared after the one-time dialog closes"
)

assert.ok(
  !authUserSource.includes("system:role:add") &&
    !authUserSource.includes("system:role:remove") &&
    authUserSource.includes("v-hasPermi=\"['system:role:authUser']\"") &&
    !authUserSource.includes('prop="phonenumber"') &&
    !authUserSource.includes('prop="email"') &&
    authUserSource.includes('prop="dept.deptName"') &&
    roleSource.includes("v-hasPermi=\"['system:role:authUser']\"") &&
    roleSource.includes(":disabled=\"!$auth.hasPermi('system:role:edit')\""),
  "role user assignment should use its dedicated permission while role status remains edit-only"
)

assert.ok(
  configSource.includes("v-hasPermi=\"['system:config:refresh']\"") &&
    dictSource.includes("v-hasPermi=\"['system:dict:refresh']\""),
  "config and dictionary cache refresh should not reuse delete permissions"
)

assert.ok(
  shopSource.includes("hasUnsavedShopScopeChanges") &&
    shopSource.includes("confirmDiscardUnsavedShopScope") &&
    shopSource.includes("切换用户将丢失未保存选择") &&
    shopSource.includes("selectedShopIds"),
  "shop authorization should confirm before switching users when checked shops differ from selectedShopIds"
)

assert.ok(
  shopSource.includes("directAuthorizationCount") &&
    shopSource.includes("inheritedAuthorizationCount") &&
    shopSource.includes("shopScopeChanges") &&
    shopSource.includes("beforeRouteLeave") &&
    shopSource.includes("selected-user-card") &&
    shopSource.includes("<resizable-split-pane") &&
    shopSource.includes('storage-key="system-user-shop-split-v2"'),
  "organization authorization should show direct/inherited/preserved counts, diff confirmation, adjustable width, and leave protection"
)

assert.ok(
  shopSource.includes("batchUserShop") &&
    shopSource.includes("listUserShopUsers") &&
    !shopSource.includes('import { listUser }') &&
    !shopSource.includes('prop="phonenumber"') &&
    !shopSource.includes("Promise.all(users.map(row => {\n        return getUserShop"),
  "shop authorization should use a minimal scoped user list and a batch scope API"
)

assert.ok(
  shopSource.includes('v-if="canSaveShopScope"') &&
    shopSource.includes('hasPermiAnd(["system:userShop:query", "system:userShop:edit"])') &&
    shopSource.includes("@click=\"submitShopScope\""),
  "shop authorization save action should be hidden unless the user has query and edit permissions"
)

assert.ok(
    shopSource.includes("用户组织授权") &&
    shopSource.includes("授权步骤") &&
    shopSource.includes("选择用户") &&
    shopSource.includes("勾选公司/组织/门店/仓库") &&
    shopSource.includes("保存授权"),
  "shop authorization page should show a clear user organization authorization flow"
)

assert.ok(
  shopSource.includes("empty-authorization-state") &&
    shopSource.includes("请先从左侧用户列表选择一个用户"),
  "shop authorization tree should show an empty state before a user is selected"
)

assert.ok(
  shopSource.includes("用户详情") &&
    !shopSource.includes("@click.stop=\"handleViewData(scope.row)\">详情</el-button>"),
  "shop authorization user action should be named 用户详情 instead of the vague 详情"
)

assert.ok(
  operlogSource.includes("system:operlog:detail") &&
    operlogSource.includes("getOperlog(row.operId)") &&
    !operlogSource.includes("this.detailRow = row"),
  "operation log detail should require its dedicated permission and fetch a sanitized detail contract"
)

assert.ok(
  salarySource.includes("salaryUserOptions") &&
    salarySource.includes("salaryShopTree") &&
    salarySource.includes("getUserSalaryBatch") &&
    salarySource.includes("saveUserSalary") &&
    !salarySource.includes("getRoleSalaryBatch") &&
    !salarySource.includes("saveRoleSalary"),
  "salary configuration should bind salary schemes to users instead of roles"
)

assert.ok(
  salarySource.includes("@change=\"handleFormShopChange\"") &&
    salarySource.includes("loadSalaryUsers({ shopDeptId: this.ruleForm.shopDeptId })") &&
    salarySource.includes("filteredUserOptions"),
  "salary user binding should reload and filter employee options when accounting shop changes"
)

assert.ok(
  salarySource.includes("previewAndConfirm") &&
    salarySource.includes("currentVersion") &&
    salarySource.includes("handleShowRevisions") &&
    salarySource.includes("handleRollbackRevision") &&
    salarySource.includes("canEmergencyCorrection") &&
    salarySource.includes('hasPermi("system:salary:emergency")') &&
    salarySource.includes("expectedVersion: row.version") &&
    salarySource.includes("changeReason"),
  "salary changes should preview impact, carry optimistic versions, preserve revisions, and gate emergency corrections"
)

assert.ok(
  userSource.includes("getUserDeleteConfirmText") &&
    userSource.includes("确认删除用户「") &&
    userSource.includes("确认删除选中的"),
  "user delete confirmation should show user-friendly names or selected count instead of only ids"
)

assert.ok(
  userSource.includes("employee-profile-tabs") &&
    userSource.includes("emptyProfile()") &&
    userSource.includes("form.profile.employeeNo") &&
    userSource.includes("form.profile.employeeStatus") &&
    userSource.includes("form.profile.bankAccount") &&
    userSource.includes("form.profile.legalEntity"),
  "user editor should expose the employee profile fields requested by HR"
)

for (const label of ["工号", "所属公司", "员工状态", "证件号码", "最高学历", "紧急联系人", "现合同到期日", "银行卡号", "法人单位"]) {
  assert.ok(
    userSource.includes(label) && userViewSource.includes(label),
    `user editor and detail drawer should include ${label}`
  )
}

assert.ok(
  userSource.includes("queryParams.employeeNo") &&
    userSource.includes("queryParams.employeeStatus") &&
    userSource.includes("queryParams.legalEntity") &&
    userSource.includes("handleEmployeeStatusChange"),
  "user list should support employee profile filters and disable accounts when employee status is set to 离职"
)

assert.ok(
  userViewSource.includes("profileSections") &&
    userViewSource.includes("profileValue(key)") &&
    userViewSource.includes("员工档案"),
  "user detail drawer should render employee profile sections from the backend profile object"
)

assert.ok(
  roleSource.includes("getRoleDeleteConfirmText") &&
    roleSource.includes("确认删除角色「") &&
    roleSource.includes("确认删除选中的"),
  "role delete confirmation should show role-friendly names or selected count instead of only ids"
)

assert.ok(
  authUserSource.includes("确认取消用户「") &&
    authUserSource.includes("确认取消选中的") &&
    authUserSource.includes("个用户的当前角色授权"),
  "role authorization cancellation should explain the user count and authorization impact"
)

assert.ok(
  userSource.includes("contractTypeOptions") &&
    userSource.includes("CONTRACT_TYPE_OPTIONS") &&
    signingProfileOptionsSource.includes('value: "LABOR_CONTRACT", label: "劳动合同"') &&
    signingProfileOptionsSource.includes('value: "SERVICE_CONTRACT", label: "劳务协议"') &&
    userSource.includes("socialTypeOptions") &&
    userSource.includes("SOCIAL_TYPE_OPTIONS") &&
    signingProfileOptionsSource.includes('value: "SOCIAL_INSURED", label: "缴纳社保"') &&
    signingProfileOptionsSource.includes('value: "SOCIAL_UNINSURED", label: "无需缴纳"'),
  "user profile form should expose shared machine-coded contract and social type options for automatic signing"
)

assert.ok(
  userSource.includes("getUserPii") &&
    userSource.includes("updateUserPii") &&
    userSource.includes("buildUserManagePayload") &&
    userSource.includes("buildPiiPayload") &&
    userSource.includes("captureUserPiiSnapshot") &&
    userSource.includes("buildUserPiiPatch") &&
    userSource.includes("账号已创建、PII 未保存"),
  "user management should split fixed account writes from audited, changed-field-only PII writes and expose retry state"
)

assert.ok(
  userSource.includes("key=\"contractType\"") &&
    userSource.includes("profileValue(scope.row, 'contractType')") &&
    userSource.includes("contractType: { label: '合同类型'") &&
    userSource.includes("key=\"socialType\"") &&
    userSource.includes("profileValue(scope.row, 'socialType')") &&
    userSource.includes("socialType: { label: '社保类型'"),
  "user list should provide optional contract type and social type columns"
)

assert.ok(
  userSource.includes("contractType: undefined") &&
    userSource.includes("socialType: undefined"),
  "empty user profile should initialize contract type and social type fields"
)

assert.ok(
  userViewSource.includes("{ label: '合同类型', key: 'contractType' }") &&
    userViewSource.includes("{ label: '社保类型', key: 'socialType' }"),
  "user detail drawer should display contract type and social type"
)

assert.ok(
  rightToolbarSource.includes('aria-label="刷新列表"') &&
    rightToolbarSource.includes(':aria-label="showSearch ? \'隐藏搜索条件\' : \'显示搜索条件\'"') &&
    rightToolbarSource.includes('aria-label="设置显示列"') &&
    rightToolbarSource.includes("min-width: 44px") &&
    rightToolbarSource.includes(":focus-visible"),
  "icon-only right toolbar actions need accessible names, keyboard focus, and 44px targets"
)
