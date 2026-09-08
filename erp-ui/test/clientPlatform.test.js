const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  isMobileClient,
  isMobileRoutePath
} = require("../src/utils/clientPlatform")

const narrowDesktopWindow = {
  matchMedia() {
    return { matches: true }
  }
}

assert.strictEqual(
  isMobileClient({
    windowObject: narrowDesktopWindow,
    navigatorObject: {
      userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140 Safari/537.36",
      platform: "Win32",
      maxTouchPoints: 0
    }
  }),
  false,
  "a narrow desktop browser must retain the desktop experience"
)

assert.strictEqual(
  isMobileClient({
    windowObject: narrowDesktopWindow,
    navigatorObject: {
      userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140 Safari/537.36",
      platform: "Win32",
      maxTouchPoints: 10
    }
  }),
  false,
  "a touch-enabled Windows laptop must not be treated as a phone"
)

assert.strictEqual(
  isMobileClient({
    windowObject: narrowDesktopWindow,
    navigatorObject: {
      userAgent: "Enterprise Desktop Shell Mobile Compatibility Mode",
      userAgentData: { mobile: false },
      platform: "Win32",
      maxTouchPoints: 10
    }
  }),
  false,
  "an explicit desktop Client Hint must override misleading mobile user-agent text"
)

assert.strictEqual(
  isMobileClient({
    windowObject: {},
    navigatorObject: {
      userAgent: "Mozilla/5.0 (Linux; Android 16; Pixel 10) AppleWebKit/537.36 Mobile"
    }
  }),
  true,
  "an Android phone should use the mobile experience"
)

assert.strictEqual(
  isMobileClient({
    windowObject: {},
    navigatorObject: {
      userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15",
      platform: "MacIntel",
      maxTouchPoints: 5
    }
  }),
  true,
  "an iPad using its desktop-style user agent should still use the mobile experience"
)

assert.strictEqual(
  isMobileClient({
    windowObject: {
      Capacitor: {
        isNativePlatform: () => true,
        getPlatform: () => "ios"
      }
    },
    navigatorObject: {
      userAgent: "ERP Native WebView"
    }
  }),
  true,
  "a native Capacitor app should always use the mobile experience"
)

assert.strictEqual(isMobileRoutePath("/mobile/store?tab=today"), true)
assert.strictEqual(isMobileRoutePath("/mobile"), true)
assert.strictEqual(isMobileRoutePath("/mobile-store"), false)
assert.strictEqual(isMobileRoutePath("/inventory/stock"), false)

const uiRoot = path.resolve(__dirname, "..")
const readUi = relativePath => fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
const entrySources = [
  "src/permission.js",
  "src/views/login.vue",
  "src/views/select-shop/index.vue",
  "src/views/profile-completion/index.vue",
  "src/utils/passwordResetReminder.js"
]

entrySources.forEach(relativePath => {
  assert.ok(
    readUi(relativePath).includes("isMobileClient"),
    `${relativePath} should share the stable client-platform decision`
  )
})

const permissionSource = readUi("src/permission.js")
assert.ok(
  permissionSource.includes("getClientBoundaryRedirect") &&
    permissionSource.includes("!isMobileViewport() && isMobileRoutePath(path)") &&
    permissionSource.includes("return { path: '/', replace: true }"),
  "the global guard should keep stale or direct mobile routes out of the desktop client"
)

console.log("clientPlatform tests passed")
