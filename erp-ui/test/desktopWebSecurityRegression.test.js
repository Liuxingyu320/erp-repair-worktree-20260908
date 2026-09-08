const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")
const babel = require("../node_modules/@babel/core")

const uiRoot = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")

function loadModule(relativePath, dependencies = {}) {
  const absolutePath = path.resolve(uiRoot, relativePath)
  const transformed = babel.transformSync(read(relativePath), {
    filename: absolutePath,
    babelrc: false,
    configFile: false,
    sourceType: "module",
    plugins: [require.resolve("../node_modules/@babel/plugin-transform-modules-commonjs")]
  }).code
  const moduleRef = { exports: {} }
  vm.runInNewContext(transformed, {
    module: moduleRef,
    exports: moduleRef.exports,
    require(request) {
      if (Object.prototype.hasOwnProperty.call(dependencies, request)) return dependencies[request]
      throw new Error(`unexpected dependency: ${request}`)
    },
    URL,
    console
  }, { filename: absolutePath })
  return moduleRef.exports.default || moduleRef.exports
}

const { authCookieOptions } = require("../src/utils/authCookiePolicy")
assert.deepStrictEqual(
  authCookieOptions("http:"),
  { path: "/", sameSite: "Lax" },
  "HTTP development must retain a usable cookie while setting an explicit SameSite policy"
)
assert.deepStrictEqual(
  authCookieOptions("https:"),
  { path: "/", sameSite: "Lax", secure: true },
  "HTTPS sessions must set Secure plus an explicit root path and SameSite policy"
)

const authSource = read("src/utils/auth.js")
for (const operation of ["Cookies.set(TokenKey, token, authCookieOptions())", "Cookies.remove(TokenKey, authCookieOptions())",
  "Cookies.set(ExpiresInKey, time, authCookieOptions())", "Cookies.remove(ExpiresInKey, authCookieOptions())"]) {
  assert.ok(authSource.includes(operation), `auth cookie operation must use the shared policy: ${operation}`)
}
const cookieCalls = []
const cookieRuntime = {
  get(name) {
    cookieCalls.push(["get", name])
    return undefined
  },
  set(name, value, options) {
    cookieCalls.push(["set", name, value, options])
    return value
  },
  remove(name, options) {
    cookieCalls.push(["remove", name, options])
  }
}
const authModule = loadModule("src/utils/auth.js", {
  "js-cookie": { __esModule: true, default: cookieRuntime },
  "./authCookiePolicy": {
    authCookieOptions() {
      return { path: "/", sameSite: "Lax", secure: true }
    }
  }
})
authModule.setToken("opaque-session-placeholder")
authModule.setExpiresIn(60)
authModule.removeToken()
authModule.removeExpiresIn()
assert.deepStrictEqual(
  cookieCalls.filter(call => call[0] !== "get").map(call => call[call.length - 1]),
  [
    { path: "/", sameSite: "Lax", secure: true },
    { path: "/", sameSite: "Lax", secure: true },
    { path: "/", sameSite: "Lax", secure: true },
    { path: "/", sameSite: "Lax", secure: true }
  ],
  "setting and clearing both session cookies must use identical attributes"
)

const {
  assertTrustedRequestBaseUrl,
  assertTrustedRequestUrl,
  safeTrustedApiUrl
} = require("../src/utils/requestSecurity")
assert.strictEqual(assertTrustedRequestUrl("/hr/employee/list", "/dev-api", "https://erp.example"), "/hr/employee/list")
assert.strictEqual(
  assertTrustedRequestUrl("https://api.example/hr/list", "https://api.example/service", "https://erp.example"),
  "https://api.example/hr/list"
)
for (const value of [
  "https://attacker.example/collect",
  "//attacker.example/collect",
  "javascript:alert(1)",
  "data:text/html,unsafe",
  "https://user:password@api.example/path",
  "\\\\attacker.example\\collect",
  "/\\attacker.example/collect"
]) {
  assert.throws(
    () => assertTrustedRequestUrl(value, "https://api.example/service", "https://erp.example"),
    /Blocked/,
    `${value} must not receive the authenticated API request`
  )
}
assert.strictEqual(
  safeTrustedApiUrl("/dev-api", "/file/upload", "https://erp.example"),
  "/dev-api/file/upload"
)
assert.strictEqual(
  safeTrustedApiUrl("/dev-api", "https://attacker.example/upload", "https://erp.example"),
  "",
  "upload components must fail closed before attaching bearer headers to an external action"
)
assert.strictEqual(
  assertTrustedRequestBaseUrl("/alternate-api", "/dev-api", "https://erp.example"),
  "/alternate-api",
  "a same-origin base path override may remain compatible"
)
assert.throws(
  () => assertTrustedRequestBaseUrl("https://attacker.example", "/dev-api", "https://erp.example"),
  /Blocked/,
  "an untrusted baseURL override must not redirect a relative authenticated request"
)
const requestSource = read("src/utils/request.js")
assert.ok(
    requestSource.includes("assertTrustedRequestBaseUrl(config.baseURL, service.defaults.baseURL)") &&
    requestSource.includes("assertTrustedRequestUrl(config.url, service.defaults.baseURL)") &&
    requestSource.indexOf("assertTrustedRequestUrl(config.url") <
      requestSource.indexOf("config.headers = applySessionAuthHeaders"),
  "request URL trust must be checked before the bearer token is attached"
)

const { sanitizeFileUrl, sanitizeFrameUrl } = require("../src/utils/urlSecurity")
for (const value of ["/profile/document.pdf", "files/document.pdf", "https://files.example/document.pdf"]) {
  assert.strictEqual(sanitizeFileUrl(value), value)
  assert.strictEqual(sanitizeFrameUrl(value), value)
}
for (const value of [
  "javascript:alert(1)",
  "data:text/html,<script>unsafe</script>",
  "blob:https://attacker.example/id",
  "//attacker.example/frame",
  "https://user:password@files.example/document.pdf"
]) {
  assert.strictEqual(sanitizeFileUrl(value), "")
  assert.strictEqual(sanitizeFrameUrl(value), "")
}

const store = { getters: {} }
const storeDependency = { __esModule: true, default: store }
const authPlugin = loadModule("src/plugins/auth.js", { "@/store": storeDependency })
assert.strictEqual(authPlugin.hasPermi("hr:employee:list"), false)
assert.strictEqual(authPlugin.hasRole("admin"), false)
assert.strictEqual(authPlugin.hasPermiOr([]), false)
assert.strictEqual(authPlugin.hasPermiAnd([]), false, "an empty all-of permission set must fail closed")
assert.strictEqual(authPlugin.hasRoleOr(null), false)
assert.strictEqual(authPlugin.hasRoleAnd([]), false, "an empty all-of role set must fail closed")
store.getters.permissions = ["hr:employee:list"]
store.getters.roles = ["hr"]
assert.strictEqual(authPlugin.hasPermi("hr:employee:list"), true)
assert.strictEqual(authPlugin.hasPermiAnd(["hr:employee:list"]), true)
assert.strictEqual(authPlugin.hasRole("hr"), true)

function removableElement() {
  const state = { removed: false }
  const element = {}
  element.parentNode = {
    removeChild(target) {
      assert.strictEqual(target, element)
      state.removed = true
    }
  }
  return { element, state }
}

store.getters = {}
for (const [relativePath, value] of [
  ["src/directive/permission/hasPermi.js", ["hr:employee:edit"]],
  ["src/directive/permission/hasRole.js", ["admin"]],
  ["src/directive/permission/hasPermi.js", []],
  ["src/directive/permission/hasRole.js", null]
]) {
  const directive = loadModule(relativePath, { "@/store": storeDependency })
  const { element, state } = removableElement()
  assert.doesNotThrow(() => directive.inserted(element, { value }, {}))
  assert.strictEqual(state.removed, true, `${relativePath} must remove controls when authorization context is unusable`)
}

const fileUploadSource = read("src/components/FileUpload/index.vue")
const imageUploadSource = read("src/components/ImageUpload/index.vue")
const editorSource = read("src/components/Editor/index.vue")
const excelImportSource = read("src/components/ExcelImportDialog/index.vue")
const innerLinkSource = read("src/layout/components/InnerLink/index.vue")
const settingsSource = read("src/layout/components/Settings/index.vue")
const validateSource = read("src/utils/validate.js")
assert.ok(fileUploadSource.includes("safeFileHref(file)") && fileUploadSource.includes('rel="noopener noreferrer"'))
for (const source of [fileUploadSource, imageUploadSource, editorSource, excelImportSource]) {
  assert.ok(source.includes("safeTrustedApiUrl"), "bearer-authenticated upload actions must use the trusted API URL gate")
}
assert.ok(imageUploadSource.includes("sanitizeFileUrl(res.data.url)"))
assert.ok(editorSource.includes("sanitizeFileUrl(res.data.url)"))
assert.ok(innerLinkSource.includes('v-if="safeSrc"') && innerLinkSource.includes("sanitizeFrameUrl(this.src)"))
assert.ok(!settingsSource.includes('setTimeout("window.location.reload()"'))
assert.ok(validateSource.includes("/^https?:\\/\\//i.test(url.trim())"))

console.log("desktopWebSecurityRegression tests passed")
