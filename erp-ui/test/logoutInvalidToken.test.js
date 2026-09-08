const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const root = path.resolve(__dirname, "..")

function esm(defaultValue, named = {}) {
  return Object.assign({ __esModule: true, default: defaultValue }, named)
}

function transformModule(relativePath) {
  const filename = path.resolve(root, relativePath)
  const source = fs.readFileSync(filename, "utf8")
  return {
    filename,
    code: babel.transformSync(source, {
      filename,
      babelrc: false,
      configFile: false,
      sourceType: "module",
      plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
    }).code
  }
}

function loadLoginApi(request) {
  const transformed = transformModule("src/api/login.js")
  const module = { exports: {} }
  vm.runInNewContext(transformed.code, {
    module,
    exports: module.exports,
    require(specifier) {
      if (specifier === "@/utils/request") return esm(request)
      if (specifier === "@/utils/sessionMode") {
        return esm(undefined, { isCookiePreferredSession: () => false })
      }
      throw new Error(`unexpected login-api dependency: ${specifier}`)
    }
  }, { filename: transformed.filename })
  return module.exports
}

function loadUserStore(runtime) {
  const transformed = transformModule("src/store/modules/user.js")
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
      login: () => Promise.resolve({ data: { access_token: "new-token", expires_in: 3600 } }),
      logout: token => {
        runtime.logoutTokens.push(token)
        runtime.cleanupOrder.push("logout")
        return Promise.reject(Object.assign(new Error("invalid token"), {
          response: { status: 401 }
        }))
      },
      getInfo: () => Promise.resolve({}),
      refreshToken: () => Promise.resolve({ data: 3600 })
    }),
    "@/utils/auth": esm(undefined, {
      getToken: () => runtime.cookieToken,
      setToken: token => { runtime.cookieToken = token },
      setExpiresIn: value => { runtime.cookieExpiresIn = value },
      removeToken: () => {
        runtime.cookieToken = ""
        runtime.removeTokenCalls += 1
      },
      removeExpiresIn: () => {
        runtime.cookieExpiresIn = ""
        runtime.removeExpiresInCalls += 1
      }
    }),
    "@/utils/shopContext": esm(undefined, { clearSelectedDept() {} }),
    "@/utils/signScopeContext": esm(undefined, {
      clearSelectedSignScope() { runtime.signScopeClearCalls += 1 }
    }),
    "@/utils/passwordResetReminder": esm(undefined, {
      getPasswordResetRoute: () => "/profile",
      resetPasswordResetReminderState() {},
      setPendingPasswordResetReminder() {},
      showPendingPasswordResetReminderIfReady() {}
    }),
    "@/utils/validate": esm(undefined, {
      isEmpty: value => value === undefined || value === null || value === ""
    }),
    "@/utils/mobileHrQueueState": esm(undefined, {
      clearMobileHrQueueStateCache() { runtime.queueClearCalls += 1 }
    }),
    "@/assets/images/profile.jpg": esm("profile.jpg"),
    "@/services/lazyPushRegistration": esm({
      initialize: () => Promise.resolve(),
      disable: token => {
        runtime.pushDisableCalls += 1
        runtime.pushDisableTokens.push(token)
        runtime.cleanupOrder.push("push-disable")
        return Promise.resolve()
      }
    }),
    "@/utils/sessionMode": esm(undefined, {
      isCookiePreferredSession: () => false,
      setWebSessionStatus() {},
      subscribeWebSessionStatus() { return () => {} }
    })
  }
  vm.runInNewContext(transformed.code, {
    module,
    exports: module.exports,
    require(specifier) {
      if (Object.prototype.hasOwnProperty.call(modules, specifier)) return modules[specifier]
      throw new Error(`unexpected user-store dependency: ${specifier}`)
    },
    Promise,
    Object,
    Array,
    String,
    Error,
    setTimeout,
    clearTimeout
  }, { filename: transformed.filename })
  return module.exports.default || module.exports
}

function createHarness(userModule) {
  const state = JSON.parse(JSON.stringify(userModule.state))
  Object.assign(state, {
    token: "stale-token",
    expires_in: 7200,
    id: 7,
    deptId: 11,
    name: "old-user",
    nickName: "Old User",
    avatar: "old.png",
    roles: ["admin"],
    permissions: ["*:*:*"],
    profileCompletionRequired: true,
    profileMissingFields: ["mobile"]
  })
  return {
    state,
    context: {
      state,
      commit(type, payload) {
        userModule.mutations[type](state, payload)
      }
    }
  }
}

async function run() {
  const requestConfigs = []
  const loginApi = loadLoginApi(config => {
    requestConfigs.push(config)
    return Promise.resolve({ ok: true })
  })
  await loginApi.logout()
  const signal = { test: true }
  await loginApi.logout("captured-token", { signal })
  await loginApi.logout("")
  assert.deepStrictEqual(JSON.parse(JSON.stringify(requestConfigs[0])), {
    url: "/auth/logout",
    method: "delete",
    silentError: true,
    suppressSessionExpiry: true
  }, "logout must make a silent DELETE request so an invalid token does not surface a stale-session error")
  assert.strictEqual(requestConfigs[1].headers.isToken, false)
  assert.strictEqual(requestConfigs[1].headers.Authorization, "Bearer captured-token")
  assert.strictEqual(requestConfigs[1].signal, signal)
  assert.strictEqual(requestConfigs[2].headers.isToken, false)
  assert.strictEqual(requestConfigs[2].headers.Authorization, undefined)

  const runtime = {
    cookieToken: "stale-token",
    cookieExpiresIn: 7200,
    removeTokenCalls: 0,
    removeExpiresInCalls: 0,
    logoutTokens: [],
    dispatches: [],
    pushDisableCalls: 0,
    pushDisableTokens: [],
    cleanupOrder: [],
    signScopeClearCalls: 0,
    queueClearCalls: 0
  }
  const userModule = loadUserStore(runtime)
  const { state, context } = createHarness(userModule)

  await assert.doesNotReject(() => userModule.actions.LogOut(context),
    "invalid-token rejection from the backend must not reject local logout")
  assert.deepStrictEqual(runtime.logoutTokens, ["stale-token"],
    "backend logout should receive the token captured before local teardown")
  assert.strictEqual(runtime.cookieToken, "")
  assert.strictEqual(runtime.cookieExpiresIn, "")
  assert.strictEqual(runtime.removeTokenCalls, 1)
  assert.strictEqual(runtime.removeExpiresInCalls, 1)
  assert.strictEqual(state.token, "")
  assert.strictEqual(state.expires_in, "")
  assert.strictEqual(state.id, "")
  assert.strictEqual(state.deptId, "")
  assert.strictEqual(state.name, "")
  assert.strictEqual(state.nickName, "")
  assert.strictEqual(state.avatar, "")
  assert.deepStrictEqual(Array.from(state.roles), [])
  assert.deepStrictEqual(Array.from(state.permissions), [])
  assert.strictEqual(state.profileCompletionRequired, false)
  assert.deepStrictEqual(Array.from(state.profileMissingFields), [])
  assert.ok(runtime.dispatches.includes("todo/stop"))
  assert.strictEqual(runtime.pushDisableCalls, 1)
  assert.deepStrictEqual(runtime.pushDisableTokens, ["stale-token"])
  assert.deepStrictEqual(runtime.cleanupOrder, ["push-disable", "logout"],
    "device-token cleanup must start before remote logout")
  assert.strictEqual(runtime.signScopeClearCalls, 1)

  console.log("logout invalid token tests passed")
}

run().catch(error => {
  console.error(error)
  process.exit(1)
})
