const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

function readUi(relativePath) {
  return fs.readFileSync(path.join(uiRoot, relativePath), "utf8")
}

function readRepo(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), "utf8")
}

function loadSessionMode({
  native = false,
  mode = "cookie",
  csrf = "csrf-value",
  storageBehavior = "normal"
} = {}) {
  const filename = path.join(uiRoot, "src/utils/sessionMode.js")
  const transformed = babel.transformSync(fs.readFileSync(filename, "utf8"), {
    filename,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const values = new Map()
  const channels = []
  let currentCsrf = csrf
  class BroadcastChannel {
    constructor() {
      this.messages = []
      channels.push(this)
    }
    postMessage(message) { this.messages.push(message) }
  }
  const sessionStorage = {
      getItem: key => values.has(key) ? values.get(key) : null,
      setItem: (key, value) => values.set(key, value)
  }
  if (storageBehavior === "get-throw") {
    sessionStorage.getItem = () => {
      throw new Error("SecurityError: sessionStorage.getItem denied")
    }
  }
  const window = {
    BroadcastChannel
  }
  if (storageBehavior === "getter-throw") {
    Object.defineProperty(window, "sessionStorage", {
      configurable: true,
      get() {
        throw new Error("SecurityError: sessionStorage getter denied")
      }
    })
  } else {
    window.sessionStorage = sessionStorage
  }
  const module = { exports: {} }
  vm.runInNewContext(transformed, {
    module,
    exports: module.exports,
    require(id) {
      if (id === "@capacitor/core") {
        return { Capacitor: { isNativePlatform: () => native } }
      }
      if (id === "js-cookie") {
        return { __esModule: true, default: { get: () => currentCsrf } }
      }
      throw new Error(`unexpected dependency: ${id}`)
    },
    process: { env: { VUE_APP_WEB_SESSION_MODE: mode } },
    window
  }, { filename })
  module.exports.__channel = channels[0]
  module.exports.__setCsrf = value => { currentCsrf = value }
  return module.exports
}

const web = loadSessionMode()
assert.strictEqual(web.isCookiePreferredSession(), true)
assert.strictEqual(web.shouldUseSessionCredentials(), true)
assert.strictEqual(web.getWebSessionStatus(), "unknown")
assert.deepStrictEqual(
  JSON.parse(JSON.stringify(web.buildSessionAuthHeaders("must-not-leak"))),
  { "X-XSRF-TOKEN": "csrf-value" },
  "cookie-preferred Web must send CSRF without exposing Authorization"
)
const plainHeaders = { authorization: "Bearer caller-leak", Other: "kept" }
assert.strictEqual(web.applySessionAuthHeaders(plainHeaders, "must-not-leak"), plainHeaders,
  "session headers must be refreshed in place")
assert.deepStrictEqual(
  JSON.parse(JSON.stringify(plainHeaders)),
  { Other: "kept", "X-XSRF-TOKEN": "csrf-value" },
  "cookie-preferred requests must strip a caller-provided Bearer header"
)
const axiosHeaderValues = new Map([
  ["Authorization", "Bearer axios-leak"],
  ["Other", "kept"]
])
const axiosHeaders = {
  delete(name) {
    for (const key of axiosHeaderValues.keys()) {
      if (key.toLowerCase() === name.toLowerCase()) axiosHeaderValues.delete(key)
    }
  },
  set(name, value) { axiosHeaderValues.set(name, value) }
}
web.applySessionAuthHeaders(axiosHeaders, "must-not-leak")
assert.strictEqual(axiosHeaderValues.has("Authorization"), false,
  "AxiosHeaders Authorization must also be stripped")
assert.strictEqual(axiosHeaderValues.get("X-XSRF-TOKEN"), "csrf-value")
web.__setCsrf("rotated-csrf")
web.applySessionAuthHeaders(plainHeaders, "must-not-leak")
assert.strictEqual(plainHeaders["X-XSRF-TOKEN"], "rotated-csrf",
  "an existing upload headers object must receive the latest rotated CSRF value")
web.setWebSessionStatus("authenticated")
assert.strictEqual(web.getWebSessionStatus(), "authenticated")
assert.strictEqual(web.hasSessionCandidate(""), true)
const receivedStatuses = []
web.subscribeWebSessionStatus(status => receivedStatuses.push(status))
web.__channel.onmessage({ data: { type: "session-status", status: "anonymous" } })
assert.deepStrictEqual(receivedStatuses, ["anonymous"])
assert.strictEqual(web.getWebSessionStatus(), "anonymous")
assert.strictEqual(web.__channel.messages.length, 1,
  "a received cross-tab status must not be broadcast again")
assert.ok(!JSON.stringify(web.__channel.messages).includes("must-not-leak"),
  "cross-tab synchronization must never carry a JWT")

for (const storageBehavior of ["getter-throw", "get-throw"]) {
  const storageDenied = loadSessionMode({ storageBehavior })
  assert.doesNotThrow(() => storageDenied.getWebSessionStatus(),
    `${storageBehavior} must not abort Cookie session discovery`)
  assert.strictEqual(storageDenied.getWebSessionStatus(), "unknown",
    `${storageBehavior} must fail closed to unknown`)
  assert.deepStrictEqual(
    JSON.parse(JSON.stringify(storageDenied.buildSessionAuthHeaders("persisted-jwt-must-not-leak"))),
    { "X-XSRF-TOKEN": "csrf-value" },
    `${storageBehavior} must not fall back to a persisted Bearer token`
  )
}

const native = loadSessionMode({ native: true })
assert.strictEqual(native.isCookiePreferredSession(), false)
assert.deepStrictEqual(
  JSON.parse(JSON.stringify(native.buildSessionAuthHeaders("native-token"))),
  { Authorization: "Bearer native-token" },
  "Capacitor Native must remain on Bearer even when Web cookie mode is built"
)
const nativeHeaders = { Authorization: "Bearer caller-token" }
native.applySessionAuthHeaders(nativeHeaders, "native-token")
assert.deepStrictEqual(
  JSON.parse(JSON.stringify(nativeHeaders)),
  { Authorization: "Bearer native-token" },
  "native requests must retain the Bearer transport after header sanitization"
)

const requestSource = readUi("src/utils/request.js")
const permissionSource = readUi("src/permission.js")
const userSource = readUi("src/store/modules/user.js")
const loginApiSource = readUi("src/api/login.js")
const credentialChangeSource = readUi("src/views/credential/change-password.vue")
const uploadSources = [
  readUi("src/components/FileUpload/index.vue"),
  readUi("src/components/ImageUpload/index.vue"),
  readUi("src/components/Editor/index.vue"),
  readUi("src/components/ExcelImportDialog/index.vue")
]
const gatewayConfig = readRepo("erp-gateway/src/main/resources/bootstrap.yml")
const authConfig = readRepo("erp-auth/src/main/resources/bootstrap.yml")
const authFilter = readRepo("erp-gateway/src/main/java/com/erp/gateway/filter/AuthFilter.java")
const csrfFilter = readRepo("erp-gateway/src/main/java/com/erp/gateway/filter/CsrfFilter.java")

assert.ok(requestSource.includes("withCredentials: shouldUseSessionCredentials()"))
assert.ok(requestSource.includes("xsrfCookieName: 'XSRF-TOKEN'"))
assert.ok(requestSource.includes("createSingleFlight") &&
  requestSource.includes("recoverCsrfSession().then(() => service.request(originalConfig))"),
  "concurrent CSRF 403 responses must share one recovery before each request retries once")
assert.ok(
  /if \(isCookiePreferredSession\(\) \|\| !isToken\) \{\s*config\.headers = applySessionAuthHeaders\(config\.headers \|\| \{\}, getToken\(\)\)/.test(requestSource),
  "cookie requests must sanitize caller headers before adding the explicit CSRF header"
)
assert.ok(uploadSources.every(source =>
  source.includes("applySessionAuthHeaders(this.headers, getToken())") &&
  !source.includes("this.headers = buildSessionAuthHeaders(getToken())")
), "all upload entry points must refresh the bound headers object in place")
assert.ok(requestSource.includes("getSelectedInventoryDeptId()") &&
  !/if \(getToken\(\).*?getSelectedInventoryDeptId/s.test(requestSource),
  "scope headers must not depend on a script-readable JWT")
assert.ok(loginApiSource.includes("'X-ERP-Session-Preference': 'cookie'"))
assert.ok(userSource.includes("isCookiePreferredSession()") &&
  userSource.includes("setWebSessionStatus('authenticated')"))
assert.ok(
  /Login[\s\S]*?setWebSessionStatus\('authenticated'\)[\s\S]*?GetInfo[\s\S]*?setWebSessionStatus\('authenticated', false\)/.test(userSource),
  "explicit login may notify peer tabs once, but an ordinary GetInfo probe must not ping-pong broadcasts"
)
assert.ok(userSource.includes("SyncWebSessionStatus") &&
  userSource.includes("subscribeWebSessionStatus"))
assert.ok(
  requestSource.includes("revokeExpiredCookieSession = createSingleFlight") &&
    requestSource.includes("suppressSessionExpiry: true") &&
    requestSource.includes("void revokeExpiredCookieSession()"),
  "a downstream 401 must best-effort revoke the still-valid cookie session without recursive expiry handling"
)
assert.ok(
  credentialChangeSource.includes("dispatch('LogOut').then(") &&
    !credentialChangeSource.includes("dispatch('LogOut').finally("),
  "cookie logout callers must navigate away only after the server confirms revocation"
)
assert.ok(
  /catch \(err\) \{\s*if \(!isAuthenticationError\(err\)\)/.test(permissionSource),
  "GetInfo 401 must not trigger a second logout or competing error message"
)
assert.ok(authConfig.includes("mode: bearer") && authConfig.includes("mode: dual"))
assert.ok(gatewayConfig.includes("mode: bearer") && gatewayConfig.includes("mode: dual"))
assert.ok(!gatewayConfig.includes("- /csrf") && !gatewayConfig.includes("- /auth/logout"))
assert.ok(!gatewayConfig.includes("AddResponseHeader=Access-Control-Allow-Origin,*"))
assert.ok(
  gatewayConfig.includes("globalcors:") &&
    gatewayConfig.includes("add-to-simple-url-handler-mapping: true") &&
    !gatewayConfig.includes("\n  webflux:\n    cors:"),
  "Gateway 5 CORS must use spring.cloud.gateway.server.webflux.globalcors"
)
assert.ok(authFilter.includes("双凭证不一致") &&
  authFilter.includes("SecurityConstants.AUTH_SOURCE_COOKIE"))
assert.ok(csrfFilter.includes("CacheConstants.CSRF_TOKEN_KEY") &&
  csrfFilter.includes("MessageDigest.isEqual"))

console.log("httpOnlySessionMigration tests passed")
