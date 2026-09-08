const MOBILE_USER_AGENT_PATTERN = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini|Mobile/i
const MOBILE_ROUTE_PREFIX = "/mobile/"

function getDefaultWindow() {
  return typeof window !== "undefined" ? window : null
}

function getDefaultNavigator() {
  return typeof navigator !== "undefined" ? navigator : null
}

function isNativeMobileRuntime(windowObject) {
  const capacitor = windowObject && windowObject.Capacitor
  if (!capacitor) return false

  try {
    if (typeof capacitor.isNativePlatform === "function" && capacitor.isNativePlatform()) {
      return true
    }
    if (typeof capacitor.getPlatform === "function") {
      return ["android", "ios"].includes(String(capacitor.getPlatform()).toLowerCase())
    }
  } catch (error) {
    return false
  }
  return false
}

function isIPadDesktopUserAgent(navigatorObject, userAgent) {
  const platform = String(navigatorObject && navigatorObject.platform || "")
  const touchPoints = Number(navigatorObject && navigatorObject.maxTouchPoints || 0)
  return touchPoints > 1 &&
    (platform === "MacIntel" || /Macintosh/i.test(userAgent))
}

/**
 * Resolve the stable client experience from device identity, not window width.
 *
 * Desktop browser zoom, split-screen windows and touch-enabled laptops must not
 * switch authentication or shop-selection navigation into the phone web app.
 */
function isMobileClient(options) {
  const settings = options || {}
  const windowObject = Object.prototype.hasOwnProperty.call(settings, "windowObject")
    ? settings.windowObject
    : getDefaultWindow()
  const navigatorObject = Object.prototype.hasOwnProperty.call(settings, "navigatorObject")
    ? settings.navigatorObject
    : getDefaultNavigator()

  if (isNativeMobileRuntime(windowObject)) return true
  if (!navigatorObject) return false

  if (navigatorObject.userAgentData &&
    typeof navigatorObject.userAgentData.mobile === "boolean") {
    // Client Hints is the browser's explicit form-factor signal. In
    // particular, `false` must win over stray "Mobile" tokens injected by
    // embedded desktop browsers or enterprise wrappers.
    return navigatorObject.userAgentData.mobile
  }

  const userAgent = String(navigatorObject.userAgent || "")
  return MOBILE_USER_AGENT_PATTERN.test(userAgent) ||
    isIPadDesktopUserAgent(navigatorObject, userAgent)
}

function isMobileRoutePath(value) {
  const path = String(value || "").split(/[?#]/)[0]
  return path === "/mobile" || path.indexOf(MOBILE_ROUTE_PREFIX) === 0
}

module.exports = {
  isMobileClient,
  isMobileRoutePath
}
