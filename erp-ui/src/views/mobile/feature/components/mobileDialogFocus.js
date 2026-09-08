const { focusElementAndVerify } = require("./mobileFocus")

const FOCUSABLE_SELECTOR = [
  "a[href]",
  "area[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "iframe",
  "object",
  "embed",
  "[contenteditable='true']",
  "[tabindex]:not([tabindex='-1'])"
].join(",")

let activeManagers = []

function createMobileDialogFocusManager(options) {
  const settings = options || {}
  const documentRef = settings.documentRef || (typeof document !== "undefined" ? document : null)
  let active = false
  let trigger = null

  function getContainer() {
    return typeof settings.getContainer === "function" ? settings.getContainer() : null
  }

  function isElementHidden(element) {
    const view = documentRef && documentRef.defaultView
    let current = element
    while (current) {
      if (current.hidden) return true
      if (typeof current.getAttribute === "function") {
        if (current.getAttribute("hidden") !== null) return true
        if (current.getAttribute("aria-hidden") === "true") return true
      }
      if (view && typeof view.getComputedStyle === "function") {
        let style = null
        try {
          style = view.getComputedStyle(current)
        } catch (error) {
          style = null
        }
        const display = style && String(style.display || "").toLowerCase()
        const visibility = style && String(style.visibility || "").toLowerCase()
        if (display === "none" || visibility === "hidden" || visibility === "collapse") return true
      }
      current = current.parentElement || null
    }
    return false
  }

  function getFocusableElements(container) {
    if (!container || typeof container.querySelectorAll !== "function") return []
    return Array.prototype.slice.call(container.querySelectorAll(FOCUSABLE_SELECTOR)).filter(element => {
      if (!element || element.disabled || element.isConnected === false) return false
      if (isElementHidden(element)) return false
      return Number(element.tabIndex) >= 0
    })
  }

  function ensureContainerFocusable(container) {
    if (!container) return
    const hasTabIndex = typeof container.getAttribute === "function" && container.getAttribute("tabindex") !== null
    if (!hasTabIndex && typeof container.setAttribute === "function") container.setAttribute("tabindex", "-1")
  }

  function focusInside(container) {
    container = container || getContainer()
    if (!container) return false
    ensureContainerFocusable(container)
    const initial = typeof settings.getInitialFocus === "function" ? settings.getInitialFocus() : null
    const focusables = getFocusableElements(container)
    const candidates = [initial, focusables[0], container].filter((candidate, index, items) => {
      return candidate && items.indexOf(candidate) === index
    })
    return candidates.some(focusElementAndVerify)
  }

  function isTopmost() {
    return active && activeManagers[activeManagers.length - 1] === manager
  }

  function handleKeydown(event) {
    if (!isTopmost() || !event) return

    if (event.key === "Escape" || event.key === "Esc") {
      if (typeof event.preventDefault === "function") event.preventDefault()
      if (typeof event.stopPropagation === "function") event.stopPropagation()
      if (typeof settings.onEscape === "function") settings.onEscape(event)
      return
    }

    if (event.key !== "Tab") return
    const container = getContainer()
    if (!container) return
    const focusables = getFocusableElements(container)
    if (focusables.length === 0) {
      if (typeof event.preventDefault === "function") event.preventDefault()
      ensureContainerFocusable(container)
      focusElementAndVerify(container)
      return
    }

    const first = focusables[0]
    const last = focusables[focusables.length - 1]
    const current = documentRef && documentRef.activeElement
    const focusEscaped = !current || (typeof container.contains === "function" && !container.contains(current))
    const wrapsBackward = !!event.shiftKey && (current === first || focusEscaped)
    const wrapsForward = !event.shiftKey && (current === last || focusEscaped)
    if (!wrapsBackward && !wrapsForward) return

    if (typeof event.preventDefault === "function") event.preventDefault()
    focusElementAndVerify(wrapsBackward ? last : first)
  }

  function removeFromStack() {
    activeManagers = activeManagers.filter(item => item !== manager)
  }

  function removeListener() {
    if (documentRef && typeof documentRef.removeEventListener === "function") {
      documentRef.removeEventListener("keydown", handleKeydown, true)
    }
  }

  function canRestoreFocus(target) {
    if (!target || typeof target.focus !== "function") return false
    if ("isConnected" in target) return target.isConnected === true
    const root = documentRef && documentRef.documentElement
    return !root || typeof root.contains !== "function" || root.contains(target)
  }

  const manager = {
    activate(explicitTrigger) {
      if (!documentRef) return false
      if (active) return true
      const container = getContainer()
      if (!container) return false
      trigger = explicitTrigger || documentRef.activeElement || null
      active = true
      activeManagers.push(manager)
      if (typeof documentRef.addEventListener === "function") {
        documentRef.addEventListener("keydown", handleKeydown, true)
      }
      return focusInside(container)
    },
    deactivate() {
      if (!active) return false
      const wasTopmost = isTopmost()
      active = false
      removeListener()
      removeFromStack()
      const restoreTarget = trigger
      trigger = null
      return wasTopmost && canRestoreFocus(restoreTarget) ? focusElementAndVerify(restoreTarget) : false
    },
    _resetForTest() {
      active = false
      trigger = null
      removeListener()
    }
  }

  return manager
}

function resetMobileDialogFocusStackForTest() {
  activeManagers.slice().forEach(manager => manager._resetForTest())
  activeManagers = []
}

module.exports = {
  createMobileDialogFocusManager,
  resetMobileDialogFocusStackForTest
}
