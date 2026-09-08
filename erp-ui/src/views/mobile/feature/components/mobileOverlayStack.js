const BODY_LOCK_CLASS = "mobile-overlay-open"
const BODY_STYLE_KEYS = ["position", "top", "left", "right", "width", "overflow"]

let activeOverlayClasses = []
let lockedScrollTop = 0
let savedBodyStyles = null
let savedScrollRootState = null

function mountMobileOverlay(overlayClass) {
  lockMobileBody(overlayClass)
}

function releaseMobileOverlay(overlayClass) {
  const body = getBody()
  if (!body) return

  const wasActive = activeOverlayClasses.indexOf(overlayClass) > -1
  activeOverlayClasses = activeOverlayClasses.filter(name => name !== overlayClass)
  removeBodyClass(body, overlayClass)

  if (!wasActive) return
  if (activeOverlayClasses.length > 0) return

  removeBodyClass(body, BODY_LOCK_CLASS)
  restoreMobileScrollRoot()
  restoreBodyStyles(body)
  restoreScrollPosition()
}

function releaseAllMobileOverlays() {
  const body = getBody()
  if (!body) return

  const wasLocked = activeOverlayClasses.length > 0 || hasBodyClass(body, BODY_LOCK_CLASS)
  activeOverlayClasses.slice().forEach(name => removeBodyClass(body, name))
  activeOverlayClasses = []
  removeBodyClass(body, BODY_LOCK_CLASS)

  if (!wasLocked) return
  restoreMobileScrollRoot()
  restoreBodyStyles(body)
  restoreScrollPosition()
}

function getMobileOverlayLockState() {
  return {
    locked: activeOverlayClasses.length > 0,
    activeClasses: activeOverlayClasses.slice(),
    scrollTop: lockedScrollTop,
    scrollRootTop: savedScrollRootState ? savedScrollRootState.scrollTop : 0
  }
}

function resetMobileOverlayLockForTest() {
  activeOverlayClasses = []
  lockedScrollTop = 0
  savedBodyStyles = null
  savedScrollRootState = null
}

function lockMobileBody(overlayClass) {
  const body = getBody()
  if (!body) return

  if (activeOverlayClasses.length === 0) {
    lockedScrollTop = getCurrentScrollTop()
    savedBodyStyles = readBodyStyles(body)
    lockVisibleMobileScrollRoot()
    writeBodyLockStyles(body)
    addBodyClass(body, BODY_LOCK_CLASS)
  }

  if (overlayClass && activeOverlayClasses.indexOf(overlayClass) === -1) {
    activeOverlayClasses.push(overlayClass)
  }
  addBodyClass(body, overlayClass)
}

function findVisibleMobileScrollRoot() {
  if (typeof document === "undefined" || typeof document.querySelectorAll !== "function") return null
  const roots = Array.prototype.slice.call(document.querySelectorAll("[data-mobile-scroll-root]"))
  if (!roots.length) return null

  const windowObject = typeof window !== "undefined" ? window : null
  return roots.find(scrollRoot => {
    if (!scrollRoot) return false
    const computedStyle = windowObject && typeof windowObject.getComputedStyle === "function"
      ? windowObject.getComputedStyle(scrollRoot)
      : null
    if (computedStyle && (computedStyle.display === "none" || computedStyle.visibility === "hidden")) return false
    if (typeof scrollRoot.getBoundingClientRect !== "function") return true
    const rect = scrollRoot.getBoundingClientRect()
    return rect.width > 0 && rect.height > 0
  }) || roots[0]
}

function lockVisibleMobileScrollRoot() {
  const scrollRoot = findVisibleMobileScrollRoot()
  if (!scrollRoot || !scrollRoot.style) return

  savedScrollRootState = {
    scrollRoot,
    scrollTop: Number(scrollRoot.scrollTop || 0),
    overflow: scrollRoot.style.overflow || "",
    overflowY: scrollRoot.style.overflowY || "",
    overscrollBehavior: scrollRoot.style.overscrollBehavior || ""
  }
  scrollRoot.style.overflow = "hidden"
  scrollRoot.style.overflowY = "hidden"
  scrollRoot.style.overscrollBehavior = "none"
}

function restoreMobileScrollRoot() {
  if (!savedScrollRootState) return
  const scrollRoot = savedScrollRootState.scrollRoot
  if (scrollRoot && scrollRoot.style) {
    scrollRoot.style.overflow = savedScrollRootState.overflow
    scrollRoot.style.overflowY = savedScrollRootState.overflowY
    scrollRoot.style.overscrollBehavior = savedScrollRootState.overscrollBehavior
    scrollRoot.scrollTop = savedScrollRootState.scrollTop
  }
  savedScrollRootState = null
}

function getBody() {
  if (typeof document === "undefined" || !document.body) return null
  return document.body
}

function getCurrentScrollTop() {
  if (typeof window !== "undefined" && Number.isFinite(Number(window.pageYOffset))) {
    return Number(window.pageYOffset)
  }
  if (typeof document !== "undefined" && document.documentElement) {
    return Number(document.documentElement.scrollTop || 0)
  }
  return 0
}

function restoreScrollPosition() {
  if (typeof window !== "undefined" && typeof window.scrollTo === "function") {
    window.scrollTo(0, lockedScrollTop)
  }
  lockedScrollTop = 0
}

function readBodyStyles(body) {
  const style = body && body.style ? body.style : {}
  return BODY_STYLE_KEYS.reduce((styles, key) => {
    styles[key] = style[key] || ""
    return styles
  }, {})
}

function writeBodyLockStyles(body) {
  if (!body || !body.style) return
  body.style.position = "fixed"
  body.style.top = "-" + lockedScrollTop + "px"
  body.style.left = "0"
  body.style.right = "0"
  body.style.width = "100%"
  body.style.overflow = "hidden"
}

function restoreBodyStyles(body) {
  if (!body || !body.style || !savedBodyStyles) return
  BODY_STYLE_KEYS.forEach(key => {
    body.style[key] = savedBodyStyles[key] || ""
  })
  savedBodyStyles = null
}

function addBodyClass(body, className) {
  if (!body || !className || !body.classList) return
  body.classList.add(className)
}

function removeBodyClass(body, className) {
  if (!body || !className || !body.classList) return
  body.classList.remove(className)
}

function hasBodyClass(body, className) {
  return !!(body && className && body.classList && body.classList.contains(className))
}

module.exports = {
  mountMobileOverlay,
  releaseMobileOverlay,
  releaseAllMobileOverlays,
  getMobileOverlayLockState,
  resetMobileOverlayLockForTest,
  findVisibleMobileScrollRoot
}
