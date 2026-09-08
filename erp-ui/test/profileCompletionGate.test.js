const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const {
  PROFILE_COMPLETION_PATH,
  getProfileCompletionErrorFields,
  isProfileCompletionPath,
  isProfileCompletionRequiredError,
  resolveProfileCompletionRedirect
} = require("../src/utils/profileCompletion")

const readSource = relativePath => fs.readFileSync(path.resolve(__dirname, relativePath), "utf8")

assert.strictEqual(PROFILE_COMPLETION_PATH, "/complete-profile")
assert.strictEqual(isProfileCompletionPath("/complete-profile?redirect=%2Findex"), true)
assert.strictEqual(
  resolveProfileCompletionRedirect("/select-shop?redirect=%2Findex", true),
  "/complete-profile?redirect=%2Fselect-shop%3Fredirect%3D%252Findex"
)
assert.strictEqual(resolveProfileCompletionRedirect("/complete-profile", true), "")
assert.strictEqual(resolveProfileCompletionRedirect("/index", false), "")
assert.strictEqual(
  isProfileCompletionRequiredError({ response: { data: { businessCode: "PROFILE_COMPLETION_REQUIRED" } } }),
  true
)
assert.deepStrictEqual(
  getProfileCompletionErrorFields({ data: { profileMissingFields: [{ key: "nickName", label: "姓名" }] } }),
  [{ key: "nickName", label: "姓名" }]
)

const gettersSource = readSource("../src/store/getters.js")
const routerSource = readSource("../src/router/index.js")
const permissionSource = readSource("../src/permission.js")

function deferred() {
  let resolve
  let reject
  const promise = new Promise((onResolve, onReject) => {
    resolve = onResolve
    reject = onReject
  })
  return { promise, resolve, reject }
}

function esm(defaultValue, named = {}) {
  return Object.assign({ __esModule: true, default: defaultValue }, named)
}

function loadProfileUserModule(runtime) {
  const filename = path.resolve(__dirname, "../src/store/modules/user.js")
  const transformed = babel.transformSync(fs.readFileSync(filename, "utf8"), {
    filename,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const module = { exports: {} }
  const modules = {
    "@/store": esm({
      dispatch(type) {
        runtime.dispatches.push(type)
        return Promise.resolve()
      }
    }),
    "@/router": esm({
      currentRoute: { path: "/" },
      push: () => Promise.resolve(),
      replace: () => Promise.resolve()
    }),
    "@/plugins/cache": esm({ session: { set() {} } }),
    "@/plugins/element-services": esm(undefined, {
      MessageBox: { confirm: () => Promise.reject(new Error("dismissed")) }
    }),
    "@/api/login": esm(undefined, {
      login: () => runtime.loginGate.promise,
      logout: () => Promise.resolve(),
      getInfo: () => Promise.resolve(runtime.getInfoResponse),
      refreshToken: () => Promise.resolve({ data: 3600 })
    }),
    "@/utils/auth": esm(undefined, {
      getToken: () => runtime.token,
      setToken: token => { runtime.token = token },
      setExpiresIn: expiresIn => { runtime.expiresIn = expiresIn },
      removeToken: () => { runtime.token = "" },
      removeExpiresIn: () => { runtime.expiresIn = "" }
    }),
    "@/utils/shopContext": esm(undefined, { clearSelectedDept() {} }),
    "@/utils/signScopeContext": esm(undefined, { clearSelectedSignScope() {} }),
    "@/utils/passwordResetReminder": esm(undefined, {
      getPasswordResetRoute: () => "/profile",
      resetPasswordResetReminderState() {},
      setPendingPasswordResetReminder() {},
      showPendingPasswordResetReminderIfReady() {}
    }),
    "@/utils/validate": esm(undefined, {
      isEmpty: value => value === undefined || value === null || value === ""
    }),
    "@/utils/mobileHrQueueState": esm(undefined, { clearMobileHrQueueStateCache() {} }),
    "@/assets/images/profile.jpg": esm("profile.jpg"),
    "@/services/lazyPushRegistration": esm({
      initialize: () => Promise.resolve(),
      disable: () => Promise.resolve()
    }),
    "@/utils/sessionMode": esm(undefined, {
      isCookiePreferredSession: () => false,
      setWebSessionStatus() {},
      subscribeWebSessionStatus() { return () => {} }
    })
  }
  vm.runInNewContext(transformed, {
    module,
    exports: module.exports,
    require(specifier) {
      if (Object.prototype.hasOwnProperty.call(modules, specifier)) return modules[specifier]
      throw new Error(`unexpected profile user-store dependency: ${specifier}`)
    },
    Promise,
    Object,
    Array,
    String,
    Error,
    setTimeout,
    clearTimeout
  }, { filename })
  return module.exports.default || module.exports
}

async function testProfileCompletionStoreLifecycle() {
  const runtime = {
    token: "old-token",
    expiresIn: 7200,
    loginGate: deferred(),
    dispatches: [],
    getInfoResponse: {
      user: {
        userId: 7,
        deptId: 11,
        userName: "tester",
        nickName: "Tester",
        avatar: ""
      },
      roles: ["employee"],
      permissions: [],
      profileCompletionRequired: true,
      profileMissingFields: [{ key: "nickName", label: "姓名" }],
      credentialState: "ACTIVE"
    }
  }
  const userModule = loadProfileUserModule(runtime)
  const state = JSON.parse(JSON.stringify(userModule.state))
  const context = {
    state,
    commit(type, payload) {
      userModule.mutations[type](state, payload)
    }
  }
  const markIncomplete = () => {
    userModule.mutations.SET_PROFILE_COMPLETION_REQUIRED(state, true)
    userModule.mutations.SET_PROFILE_MISSING_FIELDS(state, ["nickName"])
  }
  const assertReset = label => {
    assert.strictEqual(state.profileCompletionRequired, false, `${label} must clear the completion gate`)
    assert.deepStrictEqual(Array.from(state.profileMissingFields), [],
      `${label} must clear fields inherited from the previous session`)
  }

  markIncomplete()
  const loginPromise = userModule.actions.Login(context, {
    username: " tester ",
    password: "secret",
    code: "1234",
    uuid: "captcha"
  })
  assertReset("login start")
  runtime.loginGate.resolve({ data: { access_token: "new-token", expires_in: 3600 } })
  await loginPromise

  await userModule.actions.GetInfo(context)
  assert.strictEqual(state.profileCompletionRequired, true,
    "GetInfo must apply the server's completion requirement")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(state.profileMissingFields)),
    [{ key: "nickName", label: "姓名" }],
    "GetInfo must preserve the server's structured missing-field details")

  await userModule.actions.LogOut(context)
  assertReset("logout")

  markIncomplete()
  await userModule.actions.FedLogOut(context)
  assertReset("front-end logout")
}

assert.ok(
  gettersSource.includes("profileCompletionRequired: state => state.user.profileCompletionRequired") &&
    gettersSource.includes("profileMissingFields: state => state.user.profileMissingFields"),
  "Vuex getters should expose profile-completion state"
)

const completionRouteIndex = routerSource.indexOf("path: '/complete-profile'")
const shopRouteIndex = routerSource.indexOf("path: '/select-shop'")
assert.ok(completionRouteIndex > -1 && completionRouteIndex < shopRouteIndex, "completion should be a constant route before select-shop")
assert.ok(
  routerSource.includes("component: () => import('@/views/profile-completion/index')"),
  "completion route should load the dedicated page"
)

const guardCalls = []
let guardCursor = 0
while ((guardCursor = permissionSource.indexOf("getProfileCompletionGuardRedirect(to)", guardCursor)) > -1) {
  guardCalls.push(guardCursor)
  guardCursor += 1
}
assert.strictEqual(guardCalls.length, 2, "both authenticated routing branches should run the completion gate")
guardCalls.forEach((guardIndex, index) => {
  const nextGuardIndex = guardCalls[index + 1] || permissionSource.length
  const branchSource = permissionSource.slice(guardIndex, nextGuardIndex)
  const mobileIndex = branchSource.indexOf("getNormalizedMobileRedirectInfo(to.path)")
  const shopIndex = branchSource.indexOf("shouldSelectShop(to.path)")
  assert.ok(mobileIndex > 0 && shopIndex > mobileIndex, "completion should run before mobile normalization and shop selection")
})
assert.ok(
  permissionSource.includes("clearSelectedDept()") &&
    permissionSource.includes("resolveProfileCompletionRedirect(to.fullPath || to.path"),
  "incomplete redirects should clear selected context and preserve the requested full path"
)

const { PROFILE_COMPLETION_FIELDS, PROFILE_COMPLETION_FIELD_KEYS } = require(
  "../src/views/profile-completion/profileCompletionFields"
)
const {
  normalizeProfileCompletionPayload,
  validateProfileCompletion
} = require("../src/views/profile-completion/profileCompletionValidation")

assert.deepStrictEqual(
  PROFILE_COMPLETION_FIELD_KEYS,
  [
    "nickName", "phonenumber", "sex", "idType", "idNumber",
    "registeredResidence", "currentAddress"
  ],
  "field metadata should preserve the backend's 7-field core order"
)
assert.strictEqual(PROFILE_COMPLETION_FIELDS.length, 7)
assert.ok(PROFILE_COMPLETION_FIELDS.every(field => field.label && field.type), "every required field should have UI metadata")
assert.ok(!PROFILE_COMPLETION_FIELD_KEYS.some(key => ["birthDate", "maritalStatus", "ethnicity"].includes(key)),
  "supplemental personal fields must not re-enter the login gate")

assert.strictEqual(validateProfileCompletion({ sex: "2" }).sex, "请选择性别")
assert.strictEqual(validateProfileCompletion({ birthDate: "2999-01-01" }).birthDate, undefined,
  "an optional birth date must not block the login form")
assert.strictEqual(
  validateProfileCompletion({ idType: "居民身份证", idNumber: "11010119900101123X" }).idNumber,
  "请输入正确的居民身份证号码"
)
assert.deepStrictEqual(
  validateProfileCompletion(
    { phonenumber: "invalid-hidden-value", nickName: "张三" },
    ["nickName"]
  ),
  {},
  "validation should not trap users on a non-editable field that the backend did not mark missing"
)
assert.deepStrictEqual(
  normalizeProfileCompletionPayload(
    { nickName: " 张三 ", phonenumber: "138 0000 0001" },
    ["nickName", "phonenumber"]
  ),
  { nickName: "张三", phonenumber: "13800000001" },
  "submit payload should trim text and normalize phone digits"
)

const userApiSource = readSource("../src/api/system/user.js")
const completionPageSource = readSource("../src/views/profile-completion/index.vue")
const selectShopSource = readSource("../src/views/select-shop/index.vue")

assert.ok(
  userApiSource.includes("export function getProfileCompletion()") &&
    userApiSource.includes("export function updateProfileCompletion(data)") &&
    userApiSource.split("url: '/system/user/profile/completion'").length - 1 === 2 &&
    userApiSource.includes("method: 'get'") &&
    userApiSource.includes("method: 'put'"),
  "user API should expose profile-completion GET and PUT calls"
)
assert.ok(
  completionPageSource.includes("入职资料补全") &&
    completionPageSource.includes("completionProgress") &&
    completionPageSource.includes("missingFieldCount") &&
    completionPageSource.includes("is-mobile-profile") &&
    completionPageSource.includes("is-desktop-profile"),
  "completion page should show progress and responsive desktop/mobile states"
)
assert.ok(
  completionPageSource.includes("保存并继续") &&
    completionPageSource.includes("退出登录") &&
    !completionPageSource.includes("进入系统") &&
    !completionPageSource.includes("进入工作台") &&
    !completionPageSource.includes("选择店铺"),
  "completion page should not expose a bypass action before successful save"
)
assert.ok(
  completionPageSource.includes(':error="fieldErrors[field.key]"') &&
    completionPageSource.includes("request-error") &&
    completionPageSource.includes("completedDisplayValues") &&
    completionPageSource.includes("masked-sensitive-section") &&
    completionPageSource.includes("readonlySummary") &&
    completionPageSource.includes("readonly-summary-section"),
  "completion page should show field errors, request errors, masked sensitive data, and HR summary"
)
const saveIndex = completionPageSource.indexOf("updateProfileCompletion(payload)")
const refreshIndex = completionPageSource.indexOf("this.$store.dispatch('GetInfo')", saveIndex)
const applySavedCompletionIndex = completionPageSource.indexOf("this.applyCompletion(completion)", refreshIndex)
const shopIndexAfterSave = completionPageSource.indexOf("path: '/select-shop'", refreshIndex)
assert.ok(saveIndex > -1 && refreshIndex > saveIndex && shopIndexAfterSave > refreshIndex, "save should refresh GetInfo before routing to select-shop")
assert.ok(
  applySavedCompletionIndex > refreshIndex,
  "a failed post-save GetInfo refresh should preserve the editable form for retry"
)

assert.ok(
  selectShopSource.includes("isProfileCompletionRequiredError(error)") &&
    selectShopSource.includes("getProfileCompletionErrorFields(error)") &&
    selectShopSource.includes("SET_PROFILE_COMPLETION_REQUIRED") &&
    selectShopSource.includes("clearSelectedDept()") &&
    selectShopSource.includes("path: PROFILE_COMPLETION_PATH"),
  "select-shop should clear context, refresh gate state, and return to completion on backend conflict"
)

testProfileCompletionStoreLifecycle()
  .then(() => console.log("profileCompletionGate tests passed"))
  .catch(error => {
    console.error(error)
    process.exit(1)
  })
