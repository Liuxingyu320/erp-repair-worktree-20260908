const assert = require("assert")
const fs = require("fs")
const path = require("path")

const loginSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/login.vue"),
  "utf8"
)
const registerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/register.vue"),
  "utf8"
)
const selectShopSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/select-shop/index.vue"),
  "utf8"
)
const profileCompletionSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/profile-completion/index.vue"),
  "utf8"
)
const unauthorizedSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/error/401.vue"),
  "utf8"
)
const notFoundSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/error/404.vue"),
  "utf8"
)
const lockSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/lock.vue"),
  "utf8"
)
const permissionSource = fs.readFileSync(
  path.resolve(__dirname, "../src/permission.js"),
  "utf8"
)
const loginApiSource = fs.readFileSync(
  path.resolve(__dirname, "../src/api/login.js"),
  "utf8"
)
const requestSource = fs.readFileSync(
  path.resolve(__dirname, "../src/utils/request.js"),
  "utf8"
)

assert.ok(
  loginSource.includes("mobile-auth-shell"),
  "login page should render inside the approved phone-web page shell"
)

assert.ok(
  loginSource.includes("记住账号") &&
    !loginSource.includes("记住密码") &&
    loginSource.includes('Cookies.remove("password")'),
  "login persistence copy should accurately state that only the account name is remembered"
)

assert.ok(
  loginSource.includes("mobile-auth-page is-mobile-web mobile-system-page") &&
    registerSource.includes("register mobile-system-page") &&
    selectShopSource.includes("mobile-shop-page is-mobile-web mobile-system-page"),
  "all mobile authentication and organization entry surfaces should inherit the shared control system"
)

assert.ok(
  loginSource.includes("desktop-login-shell") &&
    loginSource.includes("desktop-login-copy") &&
    loginSource.includes("desktop-login-workspace-neutral-v3.jpg"),
  "login page should keep a separate refined desktop web login layout"
)

assert.ok(
  loginSource.includes("isMobileViewport") &&
    loginSource.includes("v-if=\"!isMobileViewport\"") &&
    loginSource.includes("v-else class=\"mobile-auth-shell"),
  "login page should switch between desktop and phone-web layouts by viewport"
)

assert.ok(
  loginSource.includes("auth-layout") &&
    loginSource.includes("auth-card-stack") &&
    loginSource.includes("tea-room-backdrop"),
  "login page should use the refined centered auth layout and shared tea-room backdrop"
)

assert.ok(
  !loginSource.includes("browser-chrome") &&
    !loginSource.includes("status-row") &&
    !loginSource.includes("address-bar"),
  "login page should not draw a fake phone status bar or browser address bar"
)

assert.ok(
  !loginSource.includes("entry-strip") &&
    !loginSource.includes("销售") &&
    !loginSource.includes("开单") &&
    !loginSource.includes("采购") &&
    !loginSource.includes("入库") &&
    !loginSource.includes("库存") &&
    !loginSource.includes("盘点"),
  "login page should not show business shortcut actions"
)

assert.ok(
  loginSource.includes("mobile-inventory-tea-room-bg.jpg"),
  "login page should use the tea-room visual asset"
)

assert.ok(
  loginSource.includes("getMobileDefaultRedirect"),
  "login page should default mobile users into the mobile inventory flow"
)

assert.ok(
  loginSource.includes("getPostLoginRoute") &&
    loginSource.includes("normalizePostLoginRedirect") &&
    loginSource.includes("isMobileRoutePath(redirect)") &&
    loginSource.includes('path: "/select-shop"') &&
    loginSource.includes("!this.isMobileViewport") &&
    loginSource.includes("!requiresInventoryContext(redirect)") &&
    loginSource.includes("this.$router.push(this.getPostLoginRoute())"),
  "login page should normalize device-specific redirects and send authenticated users to shop selection"
)

assert.ok(
  !loginSource.includes("isPreviewMode"),
  "login page should not ship a query-string preview mode in the production login surface"
)

assert.ok(
  loginSource.includes('this.$store.dispatch("Login", this.loginForm)'),
  "login page should keep the existing Vuex login action"
)

assert.ok(
  loginSource.includes("getCodeImg"),
  "login page should keep the captcha API integration"
)

assert.ok(
  (loginSource.match(/autocapitalize="off"/g) || []).length >= 3 &&
    (loginSource.match(/autocorrect="off"/g) || []).length >= 3 &&
    loginSource.includes(":spellcheck=\"false\""),
  "login page inputs should disable iOS auto-capitalization/autocorrect for credentials"
)

assert.ok(
  (loginSource.match(/autocomplete="username"/g) || []).length === 2 &&
    (loginSource.match(/autocomplete="current-password"/g) || []).length === 2 &&
    (loginSource.match(/autocomplete="one-time-code"/g) || []).length === 2 &&
    !loginSource.includes('auto-complete="off"'),
  "desktop and mobile login should expose password-manager and one-time-code semantics"
)

assert.ok(
  (loginSource.match(/(?:^|\s)label="账号"/g) || []).length === 2 &&
    (loginSource.match(/(?:^|\s)label="密码"/g) || []).length === 2 &&
    (loginSource.match(/(?:^|\s)label="验证码"/g) || []).length === 2,
  "desktop and mobile login should pass accessible labels through Element Input's label prop"
)

assert.ok(
  loginSource.includes('<button type="button" aria-label="刷新验证码" @click="getCode"') &&
    loginSource.includes('alt="验证码，点击可刷新"'),
  "login captcha refresh should be keyboard-operable and named"
)

assert.ok(
  registerSource.includes('autocomplete="username"') &&
    (registerSource.match(/autocomplete="new-password"/g) || []).length === 2 &&
    registerSource.includes('autocomplete="one-time-code"') &&
    registerSource.includes('<button type="button" aria-label="刷新验证码" @click="getCode"'),
  "registration should expose correct autocomplete and keyboard-operable captcha semantics"
)

assert.ok(
  (registerSource.match(/autocapitalize="off"/g) || []).length === 4 &&
    (registerSource.match(/autocorrect="off"/g) || []).length === 4 &&
    (registerSource.match(/:spellcheck="false"/g) || []).length === 4,
  "registration credentials and captcha should disable unwanted mobile keyboard transformations"
)

assert.ok(
  /(?:^|\s)label="账号"/.test(registerSource) &&
    /(?:^|\s)label="密码"/.test(registerSource) &&
    /(?:^|\s)label="确认密码"/.test(registerSource) &&
    /(?:^|\s)label="验证码"/.test(registerSource),
  "registration should pass accessible labels through Element Input's label prop"
)

assert.ok(
  loginApiSource.includes("silentError: true"),
  "captcha API should be allowed to fail quietly so login preview does not show a global 500 toast"
)

assert.ok(
  requestSource.includes("shouldSilenceError") &&
    requestSource.includes("config.silentError"),
  "request wrapper should support silencing recoverable resource errors"
)

assert.ok(
  selectShopSource.includes("mobile-shop-shell"),
  "shop selection page should render inside the approved phone-web page shell"
)

assert.ok(
  selectShopSource.includes("desktop-shop-shell") &&
    selectShopSource.includes("desktop-shop-card") &&
    selectShopSource.includes("isMobileViewport"),
  "shop selection page should keep a separate desktop web layout"
)

assert.ok(
  selectShopSource.includes("shop-layout") &&
    selectShopSource.includes("shop-card-stack") &&
    selectShopSource.includes("tea-room-backdrop"),
  "shop selection page should use the refined shop layout and shared tea-room backdrop"
)

assert.ok(
  selectShopSource.includes(".selected-panel.selection-summary") &&
    selectShopSource.includes("display: block") &&
    selectShopSource.includes(".footer-actions ::v-deep .el-button + .el-button") &&
    selectShopSource.includes("margin-left: 0"),
  "mobile organization selection should stack its summary above a contained three-button action grid"
)

assert.ok(
  selectShopSource.includes("desktop-shop-visual") &&
    selectShopSource.includes("desktop-login-tea-room-bg.jpg") &&
    selectShopSource.includes("shop-brand-mark") &&
    selectShopSource.includes("refresh-icon-btn") &&
    selectShopSource.includes("selection-summary"),
  "shop selection page should use the optimized tea-room glass selection experience"
)

assert.ok(
  selectShopSource.includes('aria-label="刷新组织列表"') &&
    selectShopSource.includes('title="刷新组织列表"'),
  "shop selection search and icon-only refresh controls should have accessible names"
)

assert.ok(
  (selectShopSource.match(/(?:^|\s)label="搜索组织"/g) || []).length === 2,
  "shop search should pass its accessible name through Element Input's label prop"
)

assert.ok(
  !selectShopSource.includes("browser-chrome") &&
    !selectShopSource.includes("status-row") &&
    !selectShopSource.includes("address-bar"),
  "shop selection page should not draw a fake phone status bar or browser address bar"
)

assert.ok(
  !selectShopSource.includes("previewDeptTree") &&
    !selectShopSource.includes("云岫茶室") &&
    !selectShopSource.includes("青炉茶社"),
  "shop selection page should not ship hard-coded preview organizations"
)

assert.ok(
  selectShopSource.includes("listShopTree({ silentError: true })") &&
    selectShopSource.includes("isProfileCompletionRequiredError(error)"),
  "shop selection should keep the backend tree API while handling profile-completion conflicts locally"
)

assert.ok(
  selectShopSource.includes("setSelectedDept(this.selectedDeptId, this.selectedDeptName, this.selectedDeptType)"),
  "shop selection page should persist the selected shop context"
)

assert.ok(
  selectShopSource.includes("getFallbackRedirect"),
  "shop selection page should default mobile users into the mobile inventory workbench"
)

assert.ok(
  !permissionSource.includes("isShopPreview") &&
    !permissionSource.includes("to.path === '/select-shop' && to.query && to.query.preview === '1'"),
  "permission guard should not allow unauthenticated shop preview access"
)

assert.ok(
  !permissionSource.includes("isMobileRoutePreview") &&
    !permissionSource.includes("to.query.preview === '1'"),
  "permission guard should not allow unauthenticated phone-web preview routes"
)

assert.ok(
  !permissionSource.includes("isMobileInventoryDemo") &&
    !permissionSource.includes("to.query.demo === '1'"),
  "permission guard should not allow the mobile inventory demo route to bypass authentication"
)

assert.ok(
  !permissionSource.includes("to.path === '/login' && to.query && to.query.preview === '1'"),
  "permission guard should not special-case login preview mode"
)

assert.ok(
  !selectShopSource.includes("getPreviewRedirect") &&
    !selectShopSource.includes('preview: "1"') &&
    !selectShopSource.includes("this.isPreviewMode"),
  "shop confirmation should not preserve or propagate preview mode"
)

assert.ok(
  profileCompletionSource.includes("is-mobile-profile mobile-system-page") &&
    profileCompletionSource.includes('safeMessage("读取资料失败，请稍后重试")') &&
    profileCompletionSource.includes('safeMessage("保存失败，请稍后重试")') &&
    !profileCompletionSource.includes("error.message"),
  "profile completion should use the shared mobile controls and keep internal request details out of user messages"
)

assert.ok(
  unauthorizedSource.includes("err-page-container mobile-system-page") &&
    unauthorizedSource.includes('icon="el-icon-arrow-left"') &&
    unauthorizedSource.includes('icon="el-icon-s-home"') &&
    unauthorizedSource.includes("min-height: 100dvh") &&
    unauthorizedSource.includes("safe-area-inset-bottom"),
  "401 recovery should be responsive, safe-area aware, and provide both back and home actions"
)

assert.ok(
  notFoundSource.includes("http404-container mobile-system-page") &&
    notFoundSource.includes("返回上一页") &&
    notFoundSource.includes("return-home") &&
    notFoundSource.includes("prefers-reduced-motion: reduce") &&
    notFoundSource.includes("min-height: 100dvh"),
  "404 recovery should be responsive, motion-aware, and provide clear recovery actions"
)

assert.ok(
  lockSource.includes("lock-container mobile-system-page") &&
    lockSource.includes('autocomplete="current-password"') &&
    lockSource.includes('aria-label="解锁系统"') &&
    lockSource.includes("this.showError('解锁失败，请检查密码后重试')") &&
    !lockSource.includes("err.message") &&
    !lockSource.includes("err.toString") &&
    !lockSource.includes(">🔒<") &&
    !lockSource.includes(">→<"),
  "lock screen should use accessible controls, safe errors, and library icons"
)

assert.ok(
  lockSource.includes("return window.innerWidth > 768 && !reduceMotion") &&
    lockSource.includes("Math.min(40") &&
    !lockSource.includes("const count = 80") &&
    lockSource.includes("window.removeEventListener('resize', this.resizeHandler)") &&
    lockSource.includes("this.stopParticleAnimation()"),
  "lock screen should disable particles on phones and reduced-motion devices and clean up desktop animation work"
)

assert.ok(
  permissionSource.includes("path.indexOf('/mobile/') === 0 || isMobileViewport()") &&
    permissionSource.includes("shouldSelectShop(to.path)") &&
    permissionSource.includes("normalizeMobileRedirect") &&
    permissionSource.includes("getMobileEntryPath"),
  "store selection and root entry redirects should route phone users into the phone-web flow"
)
