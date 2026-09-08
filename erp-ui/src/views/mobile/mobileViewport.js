let activeConsumers = 0
let listenersAttached = false
let scheduledFrame = null

function finiteNumber(value, fallback = 0) {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

function calculateMobileViewport(source = {}) {
  const innerHeight = Math.max(0, finiteNumber(source.innerHeight))
  const visualViewport = source.visualViewport || null
  const height = Math.max(0, finiteNumber(visualViewport && visualViewport.height, innerHeight))
  const offsetTop = Math.max(0, finiteNumber(visualViewport && visualViewport.offsetTop))
  const keyboardInset = Math.max(0, innerHeight - height - offsetTop)

  return {
    height: Math.round(height),
    offsetTop: Math.round(offsetTop),
    keyboardInset: Math.round(keyboardInset)
  }
}

function getWindow() {
  return typeof window !== "undefined" ? window : null
}

function getDocumentRoot() {
  return typeof document !== "undefined" ? document.documentElement : null
}

function applyMobileViewport(windowObject = getWindow(), root = getDocumentRoot()) {
  if (!windowObject || !root || !root.style) return null

  const viewport = calculateMobileViewport(windowObject)
  root.style.setProperty("--mobile-viewport-height", viewport.height + "px")
  root.style.setProperty("--mobile-viewport-offset-top", viewport.offsetTop + "px")
  root.style.setProperty("--mobile-keyboard-inset", viewport.keyboardInset + "px")
  return viewport
}

function scheduleMobileViewportSync() {
  const windowObject = getWindow()
  if (!windowObject || scheduledFrame !== null) return

  const requestFrame = typeof windowObject.requestAnimationFrame === "function"
    ? windowObject.requestAnimationFrame.bind(windowObject)
    : callback => windowObject.setTimeout(callback, 16)

  scheduledFrame = requestFrame(() => {
    scheduledFrame = null
    applyMobileViewport(windowObject)
  })
}

function attachMobileViewportListeners(windowObject) {
  if (!windowObject || listenersAttached) return
  windowObject.addEventListener("resize", scheduleMobileViewportSync, { passive: true })
  windowObject.addEventListener("orientationchange", scheduleMobileViewportSync, { passive: true })
  if (windowObject.visualViewport && typeof windowObject.visualViewport.addEventListener === "function") {
    windowObject.visualViewport.addEventListener("resize", scheduleMobileViewportSync, { passive: true })
    windowObject.visualViewport.addEventListener("scroll", scheduleMobileViewportSync, { passive: true })
  }
  listenersAttached = true
}

function detachMobileViewportListeners(windowObject) {
  if (!windowObject || !listenersAttached) return
  windowObject.removeEventListener("resize", scheduleMobileViewportSync)
  windowObject.removeEventListener("orientationchange", scheduleMobileViewportSync)
  if (windowObject.visualViewport && typeof windowObject.visualViewport.removeEventListener === "function") {
    windowObject.visualViewport.removeEventListener("resize", scheduleMobileViewportSync)
    windowObject.visualViewport.removeEventListener("scroll", scheduleMobileViewportSync)
  }
  listenersAttached = false
}

function cancelScheduledMobileViewportSync(windowObject) {
  if (!windowObject || scheduledFrame === null) return
  if (typeof windowObject.cancelAnimationFrame === "function") {
    windowObject.cancelAnimationFrame(scheduledFrame)
  } else {
    windowObject.clearTimeout(scheduledFrame)
  }
  scheduledFrame = null
}

function startMobileViewportSync() {
  const windowObject = getWindow()
  if (!windowObject) return false
  activeConsumers += 1
  if (activeConsumers === 1) attachMobileViewportListeners(windowObject)
  applyMobileViewport(windowObject)
  return true
}

function stopMobileViewportSync() {
  const windowObject = getWindow()
  activeConsumers = Math.max(0, activeConsumers - 1)
  if (activeConsumers > 0 || !windowObject) return false
  detachMobileViewportListeners(windowObject)
  cancelScheduledMobileViewportSync(windowObject)
  return true
}

function resetMobileViewportForTest() {
  const windowObject = getWindow()
  activeConsumers = 0
  if (windowObject) {
    detachMobileViewportListeners(windowObject)
    cancelScheduledMobileViewportSync(windowObject)
  } else {
    listenersAttached = false
    scheduledFrame = null
  }
}

module.exports = {
  calculateMobileViewport,
  applyMobileViewport,
  startMobileViewportSync,
  stopMobileViewportSync,
  resetMobileViewportForTest
}
