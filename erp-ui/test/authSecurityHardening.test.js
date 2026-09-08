const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

function readUi(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
}

function readRepo(relativePath) {
  return fs.readFileSync(path.resolve(repoRoot, relativePath), "utf8")
}

const registerSource = readUi("src/views/register.vue")
const loginSource = readUi("src/views/login.vue")
const passwordRuleSource = readUi("src/utils/passwordRule.js")
const loginApiSource = readUi("src/api/login.js")
const userSource = readUi("src/views/system/user/index.vue")
const roleSource = readUi("src/views/system/role/index.vue")
const userConstantsSource = readRepo("erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/UserConstants.java")
const authLoginServiceSource = readRepo("erp-auth/src/main/java/com/erp/auth/service/SysLoginService.java")
const systemUserControllerSource = readRepo("erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java")
const profileControllerSource = readRepo("erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java")
const credentialServiceSource = readRepo("erp-modules/erp-system/src/main/java/com/erp/system/service/credential/UserCredentialProvisioningService.java")
const reversiblePasswordCryptoPath = path.resolve(uiRoot, "src/utils/jsencrypt.js")
const packageJson = JSON.parse(readUi("package.json"))

assert.ok(
  !loginSource.includes('username: "admin"') &&
    !loginSource.includes('password: "admin123"') &&
    loginSource.includes('username: ""') &&
    loginSource.includes('password: ""'),
  "login page should not hard-code default admin credentials"
)

assert.ok(
  loginSource.includes("isSavedAdminCredential") &&
    loginSource.includes("clearSavedLogin()") &&
    loginSource.includes('username === "admin"'),
  "login page should clear saved admin credentials instead of pre-filling them"
)

assert.ok(
  !registerSource.includes("dangerouslyUseHTMLString") &&
    registerSource.includes("this.$createElement") &&
    registerSource.includes("注册成功"),
  "register success alert should render escaped text instead of interpolating username into HTML"
)

assert.ok(
  passwordRuleSource.includes("export const PWD_RULES") &&
    passwordRuleSource.includes("const rule = PWD_RULES[this.pwdChrType] || PWD_RULES['0']") &&
    !passwordRuleSource.includes("pwdPromptValidator() {\n      const rule = PWD_RULES['0']") &&
    !passwordRuleSource.includes("registerPwdValidator() {\n      const rule = PWD_RULES['0']"),
  "frontend password validators should use the configured password character policy"
)

assert.ok(
  loginApiSource.includes("export function getPasswordPolicy") &&
    registerSource.includes("getPasswordPolicy") &&
    registerSource.includes("this.pwdChrType = response.data || '0'"),
  "register page should load the same password policy used by backend registration"
)

assert.ok(
  !userSource.includes("'确认要\"' + text + '\"\"'") &&
    userSource.includes("确认要将用户「") &&
    userSource.includes("row.userName + '」' + text"),
  "user status change confirmation should not include duplicated quote marks"
)

assert.ok(
  !roleSource.includes("'确认要\"' + text + '\"\"'") &&
    roleSource.includes("确认要将角色「") &&
    roleSource.includes("row.roleName + '」' + text"),
  "role status change confirmation should not include duplicated quote marks"
)

assert.ok(
  userConstantsSource.includes("getPasswordPolicyError") &&
    userConstantsSource.includes("密码必须同时包含字母、数字和特殊字符"),
  "backend should expose a common password policy validator"
)

assert.ok(
  authLoginServiceSource.includes("getPasswordPolicy()") &&
    authLoginServiceSource.includes("UserConstants.getPasswordPolicyError(password, getPasswordPolicy())"),
  "auth registration should enforce configured password policy before encrypting the password"
)

assert.ok(
  systemUserControllerSource.includes("SysUserManageUpdateRequest") &&
    systemUserControllerSource.includes("insertUserWithTemporaryCredential") &&
    profileControllerSource.includes("UserConstants.getPasswordPolicyError(newPassword, getSysAccountChrtype())"),
  "administrator user creation should reject client credentials and self-service password update should enforce policy"
)

assert.ok(
  systemUserControllerSource.includes("selectUserManageDetail(userId)") &&
    systemUserControllerSource.includes("sanitizeCurrentUserForResponse(user)") &&
    systemUserControllerSource.includes("setPassword(null)"),
  "system user management responses should use fixed safe DTOs and current-user responses should remove hashes"
)

assert.ok(
  !fs.existsSync(reversiblePasswordCryptoPath) &&
    !loginSource.includes("@/utils/jsencrypt") &&
    !loginSource.includes('Cookies.get("password")') &&
    !loginSource.includes('Cookies.set("password"') &&
    !loginSource.includes("encrypt(this.loginForm.password)") &&
    !loginSource.includes("decrypt(password)"),
  "login page should not ship reversible password crypto or persist the account password in cookies"
)

assert.ok(
  packageJson.scripts &&
    packageJson.scripts["prebuild:prod"].startsWith("node scripts/scan-frontend-secrets.cjs") &&
    packageJson.scripts["prebuild:prod"].includes("node scripts/validate-release-build.cjs"),
  "production builds should scan secrets and validate release metadata before bundling"
)

console.log("authSecurityHardening tests passed")
