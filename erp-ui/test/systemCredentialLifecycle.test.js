const assert = require('assert')
const fs = require('fs')
const path = require('path')

const ui = relative => fs.readFileSync(path.resolve(__dirname, '..', relative), 'utf8')
const repo = relative => fs.readFileSync(path.resolve(__dirname, '../..', relative), 'utf8')

const router = ui('src/router/index.js')
const permission = ui('src/permission.js')
const userStore = ui('src/store/modules/user.js')
const request = ui('src/utils/request.js')
const forcedChange = ui('src/views/credential/change-password.vue')
const userPage = ui('src/views/system/user/index.vue')
const importDialog = ui('src/components/ExcelImportDialog/index.vue')
const userController = repo('erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java')
const profileController = repo('erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java')
const userMapper = repo('erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml')

assert.ok(
  router.includes("path: '/credential/change-password'") &&
    permission.includes("const CREDENTIAL_CHANGE_PATH = '/credential/change-password'") &&
    permission.includes('store.getters.credentialState'),
  'required password changes should use a dedicated constant route guarded before business navigation'
)

assert.ok(
  userStore.includes("commit('SET_CREDENTIAL_STATE', res.credentialState)") &&
    userStore.includes('const credentialRestricted = isCredentialChangeRequired(res.credentialState)') &&
    userStore.indexOf('const credentialRestricted = isCredentialChangeRequired(res.credentialState)') <
      userStore.indexOf("store.dispatch('todo/start')"),
  'GetInfo should persist the explicit credential state before starting business-side integrations'
)

assert.ok(
  request.includes('code === 428') &&
    request.includes("store.dispatch('EnforceCredentialChange'") &&
    userStore.includes("router.replace('/credential/change-password')"),
  'API precondition failures should route silently to the mandatory password page'
)

assert.ok(
  forcedChange.includes('修改密码并继续') &&
    forcedChange.includes("this.$store.dispatch('GetInfo')") &&
    forcedChange.includes('info.profileCompletionRequired === true') &&
    forcedChange.includes("this.$modal.msgSuccess('密码修改成功，请继续补全入职资料')") &&
    forcedChange.includes("path: '/complete-profile'") &&
    forcedChange.includes("query: { redirect: '/select-shop' }") &&
    forcedChange.includes("this.$router.replace('/select-shop')") &&
    !forcedChange.includes('取消'),
  'the mandatory flow should refresh credential state, explain profile completion when needed, and expose no cancel bypass'
)

assert.ok(
  userPage.includes('生成临时密码') &&
    userPage.includes('v-clipboard:copy="temporaryCredential.temporaryPassword"') &&
    userPage.includes('this.temporaryCredential.temporaryPassword = ""'),
  'user creation and reset should default to generated one-time credentials with explicit copy handling'
)

assert.strictEqual(
  (userPage.match(/^    showTemporaryCredential\s*\(/gm) || []).length,
  1,
  'user management must have exactly one temporary-credential response handler'
)
assert.ok(
  !userPage.includes('credentialDialogVisible') &&
    !userPage.includes('credentialResult') &&
    !userPage.includes('credentialAfterClose'),
  'the obsolete credential-dialog state must not coexist with the active one-time credential flow'
)

assert.ok(
  importDialog.includes('下载一次性临时密码清单') &&
    importDialog.includes('item.temporaryPassword = ""') &&
    importDialog.includes('Cache-Control') === false,
  'import credentials should be downloadable once and cleared from browser memory; cache control belongs to the API'
)

assert.ok(
  userController.includes('preventCredentialResponseCaching(response)') &&
    userController.includes('isSaveResponseData = false') &&
    userController.includes('@AllowPasswordChangeRequired') &&
    userController.includes('@AllowsTemporaryCredential') &&
    profileController.includes('@AllowPasswordChangeRequired') &&
    profileController.includes('@AllowsTemporaryCredential') &&
    userMapper.includes("must_change_password = '0'") &&
    userMapper.includes("credential_state = 'ACTIVE'"),
  'backend responses and logs should protect one-time credentials and self-change should clear the explicit state'
)

console.log('systemCredentialLifecycle tests passed')
