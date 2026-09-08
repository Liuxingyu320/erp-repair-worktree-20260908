const assert = require("assert")

const {
  createMobileDialogFocusManager,
  resetMobileDialogFocusStackForTest
} = require("../src/views/mobile/feature/components/mobileDialogFocus")

function createFakeDocument() {
  const listeners = new Set()
  const documentRef = {
    activeElement: null,
    defaultView: {
      getComputedStyle(element) {
        return element.computedStyle || { display: "block", visibility: "visible" }
      }
    },
    addEventListener(type, listener) {
      if (type === "keydown") listeners.add(listener)
    },
    removeEventListener(type, listener) {
      if (type === "keydown") listeners.delete(listener)
    },
    dispatchKey(event) {
      Array.from(listeners).forEach(listener => listener(event))
    },
    listenerCount() {
      return listeners.size
    }
  }
  return documentRef
}

function createElement(documentRef, name, options = {}) {
  const element = {
    name,
    ownerDocument: documentRef,
    isConnected: options.isConnected !== false,
    disabled: !!options.disabled,
    hidden: !!options.hidden,
    parentElement: options.parentElement || null,
    computedStyle: options.computedStyle || { display: "block", visibility: "visible" },
    tabIndex: options.tabIndex === undefined ? 0 : options.tabIndex,
    focusCalls: 0,
    focus() {
      this.focusCalls += 1
      if (options.focusSucceeds !== false) documentRef.activeElement = this
    },
    getAttribute(attribute) {
      if (attribute === "aria-hidden") return options.ariaHidden ? "true" : null
      if (attribute === "tabindex") return String(this.tabIndex)
      return null
    }
  }
  return element
}

function createContainer(documentRef, name, focusables = [], options = {}) {
  const container = createElement(documentRef, name, Object.assign({ tabIndex: -1 }, options))
  container.focusables = focusables
  container.querySelectorAll = () => container.focusables
  container.contains = element => element === container || container.focusables.indexOf(element) > -1
  container.setAttribute = (attribute, value) => {
    if (attribute === "tabindex") container.tabIndex = Number(value)
  }
  return container
}

function createKeyEvent(key, shiftKey = false) {
  return {
    key,
    shiftKey,
    defaultPrevented: false,
    propagationStopped: false,
    preventDefault() {
      this.defaultPrevented = true
    },
    stopPropagation() {
      this.propagationStopped = true
    }
  }
}

function createManager(documentRef, container, initialFocus, onEscape) {
  return createMobileDialogFocusManager({
    documentRef,
    getContainer: () => container,
    getInitialFocus: () => initialFocus,
    onEscape
  })
}

resetMobileDialogFocusStackForTest()

{
  const documentRef = createFakeDocument()
  const hiddenAncestor = createElement(documentRef, "hidden-ancestor", { hidden: true })
  const ariaHiddenAncestor = createElement(documentRef, "aria-hidden-ancestor", { ariaHidden: true })
  const displayHiddenAncestor = createElement(documentRef, "display-hidden-ancestor", {
    computedStyle: { display: "none", visibility: "visible" }
  })
  const hiddenNative = createElement(documentRef, "hidden-native", { hidden: true })
  const hiddenByAncestor = createElement(documentRef, "hidden-by-ancestor", { parentElement: hiddenAncestor })
  const hiddenByAriaAncestor = createElement(documentRef, "hidden-by-aria-ancestor", {
    parentElement: ariaHiddenAncestor
  })
  const hiddenByDisplay = createElement(documentRef, "hidden-by-display", {
    computedStyle: { display: "none", visibility: "visible" }
  })
  const visibleFirst = createElement(documentRef, "visible-first")
  const visibleLast = createElement(documentRef, "visible-last")
  const hiddenByDisplayAncestor = createElement(documentRef, "hidden-by-display-ancestor", {
    parentElement: displayHiddenAncestor
  })
  const hiddenByVisibility = createElement(documentRef, "hidden-by-visibility", {
    computedStyle: { display: "block", visibility: "hidden" }
  })
  const hiddenTail = createElement(documentRef, "hidden-tail", {
    computedStyle: { display: "block", visibility: "collapse" }
  })
  const container = createContainer(documentRef, "visibility-dialog", [
    hiddenNative,
    hiddenByAncestor,
    hiddenByAriaAncestor,
    hiddenByDisplay,
    visibleFirst,
    visibleLast,
    hiddenByDisplayAncestor,
    hiddenByVisibility,
    hiddenTail
  ])
  documentRef.activeElement = createElement(documentRef, "trigger")
  const manager = createManager(documentRef, container, visibleFirst, () => {})
  manager.activate()

  documentRef.activeElement = visibleLast
  const forwardTab = createKeyEvent("Tab")
  documentRef.dispatchKey(forwardTab)
  assert.strictEqual(documentRef.activeElement, visibleFirst, "Tab should wrap after the final visible control")
  assert.strictEqual(forwardTab.defaultPrevented, true, "a hidden tail must not let Tab escape the dialog")

  documentRef.activeElement = visibleFirst
  const backwardTab = createKeyEvent("Tab", true)
  documentRef.dispatchKey(backwardTab)
  assert.strictEqual(documentRef.activeElement, visibleLast, "Shift+Tab should wrap before the first visible control")
  assert.strictEqual(hiddenTail.focusCalls, 0, "hidden controls should never become focus trap boundaries")
  manager.deactivate()
}

{
  const documentRef = createFakeDocument()
  const trigger = createElement(documentRef, "trigger")
  const first = createElement(documentRef, "first")
  const last = createElement(documentRef, "last")
  const container = createContainer(documentRef, "dialog", [first, last])
  documentRef.activeElement = trigger
  const manager = createManager(documentRef, container, first, () => {})

  assert.strictEqual(manager.activate(), true, "activating should report verified initial focus")
  assert.strictEqual(documentRef.activeElement, first, "activating should move focus into the dialog")
  assert.strictEqual(documentRef.listenerCount(), 1, "activating should attach one keydown listener")

  documentRef.activeElement = last
  assert.strictEqual(manager.activate(), true, "repeated activation should keep reporting an active focus manager")
  assert.strictEqual(documentRef.activeElement, last, "repeated activation should not reset focus inside an active dialog")
  assert.strictEqual(documentRef.listenerCount(), 1, "repeated activation should not duplicate the keydown listener")

  documentRef.activeElement = last
  const forwardTab = createKeyEvent("Tab")
  documentRef.dispatchKey(forwardTab)
  assert.strictEqual(documentRef.activeElement, first, "Tab on the final control should wrap to the first control")
  assert.strictEqual(forwardTab.defaultPrevented, true, "wrapped forward Tab should prevent browser focus escape")

  documentRef.activeElement = first
  const backwardTab = createKeyEvent("Tab", true)
  documentRef.dispatchKey(backwardTab)
  assert.strictEqual(documentRef.activeElement, last, "Shift+Tab on the first control should wrap to the final control")
  assert.strictEqual(backwardTab.defaultPrevented, true, "wrapped backward Tab should prevent browser focus escape")

  manager.deactivate()
  assert.strictEqual(documentRef.listenerCount(), 0, "deactivating should remove the keydown listener")
  assert.strictEqual(documentRef.activeElement, trigger, "deactivating should restore the opening trigger")
}

{
  const documentRef = createFakeDocument()
  const trigger = createElement(documentRef, "trigger")
  const failedInitial = createElement(documentRef, "failed-initial", { focusSucceeds: false })
  const container = createContainer(documentRef, "empty-dialog", [])
  documentRef.activeElement = trigger
  const manager = createManager(documentRef, container, failedInitial, () => {})

  assert.strictEqual(manager.activate(), true, "a failed initial target should fall back to verified container focus")
  assert.strictEqual(documentRef.activeElement, container, "an empty dialog should focus its focusable container")
  const tab = createKeyEvent("Tab")
  documentRef.dispatchKey(tab)
  assert.strictEqual(tab.defaultPrevented, true, "Tab in an empty dialog should always be prevented")
  assert.strictEqual(documentRef.activeElement, container, "Tab in an empty dialog should retain container focus")

  trigger.isConnected = false
  manager.deactivate()
  assert.strictEqual(trigger.focusCalls, 0, "a disconnected trigger should not receive restored focus")
}

{
  const documentRef = createFakeDocument()
  const ambientFocus = createElement(documentRef, "ambient-focus")
  const explicitTrigger = createElement(documentRef, "explicit-trigger")
  const initial = createElement(documentRef, "initial")
  const container = createContainer(documentRef, "explicit-trigger-dialog", [initial])
  documentRef.activeElement = ambientFocus
  const manager = createManager(documentRef, container, initial, () => {})

  manager.activate(explicitTrigger)
  manager.deactivate()
  assert.strictEqual(
    documentRef.activeElement,
    explicitTrigger,
    "an explicit opening trigger should take precedence over document.activeElement for restoration"
  )
}

{
  const documentRef = createFakeDocument()
  const trigger = createElement(documentRef, "trigger")
  const failedInitial = createElement(documentRef, "failed-initial", { focusSucceeds: false })
  const failedContainer = createContainer(documentRef, "failed-container", [], { focusSucceeds: false })
  documentRef.activeElement = trigger
  const manager = createManager(documentRef, failedContainer, failedInitial, () => {})

  assert.strictEqual(manager.activate(), false, "activation should not report success when every focus attempt fails")
  resetMobileDialogFocusStackForTest()
  assert.strictEqual(documentRef.listenerCount(), 0, "the test reset hook should remove listeners from active managers")
  assert.strictEqual(documentRef.activeElement, trigger, "the test reset hook should not move focus while cleaning shared state")
}

{
  const documentRef = createFakeDocument()
  const parentTrigger = createElement(documentRef, "parent-trigger")
  const parentButton = createElement(documentRef, "parent-button")
  const parentContainer = createContainer(documentRef, "parent", [parentButton])
  let parentEscapeCount = 0
  let ghostChildEscapeCount = 0

  documentRef.activeElement = parentTrigger
  const parentManager = createManager(documentRef, parentContainer, parentButton, () => { parentEscapeCount += 1 })
  parentManager.activate()
  const ghostChildManager = createManager(documentRef, null, null, () => { ghostChildEscapeCount += 1 })

  assert.strictEqual(ghostChildManager.activate(), false, "a dialog without a mounted container should not activate")
  assert.strictEqual(documentRef.listenerCount(), 1, "a missing child container should not register a ghost listener")
  documentRef.dispatchKey(createKeyEvent("Escape"))
  assert.strictEqual(parentEscapeCount, 1, "the existing parent should remain the topmost Escape owner")
  assert.strictEqual(ghostChildEscapeCount, 0, "a child without a container should never handle keyboard input")
  parentManager.deactivate()
}

{
  const documentRef = createFakeDocument()
  const parentTrigger = createElement(documentRef, "parent-trigger")
  const parentButton = createElement(documentRef, "parent-button")
  const childTrigger = createElement(documentRef, "child-trigger")
  const childButton = createElement(documentRef, "child-button")
  const parentContainer = createContainer(documentRef, "parent", [parentButton, childTrigger])
  const childContainer = createContainer(documentRef, "child", [childButton])
  let childEscapeCount = 0

  documentRef.activeElement = parentTrigger
  const parentManager = createManager(documentRef, parentContainer, parentButton, () => {})
  parentManager.activate()
  documentRef.activeElement = childTrigger
  const childManager = createManager(documentRef, childContainer, childButton, () => { childEscapeCount += 1 })
  childManager.activate()

  parentManager.deactivate()
  assert.strictEqual(
    documentRef.activeElement,
    childButton,
    "deactivating a covered parent should preserve focus in the topmost child"
  )
  assert.strictEqual(parentTrigger.focusCalls, 0, "a covered parent should not restore its trigger ahead of the child")
  documentRef.dispatchKey(createKeyEvent("Escape"))
  assert.strictEqual(childEscapeCount, 1, "the child should remain the active keyboard owner after its parent deactivates")
  childManager.deactivate()
}

{
  const documentRef = createFakeDocument()
  const parentTrigger = createElement(documentRef, "parent-trigger")
  const parentButton = createElement(documentRef, "parent-button")
  const childTrigger = createElement(documentRef, "child-trigger")
  const childButton = createElement(documentRef, "child-button")
  const parentContainer = createContainer(documentRef, "parent", [parentButton, childTrigger])
  const childContainer = createContainer(documentRef, "child", [childButton])
  let parentEscapeCount = 0
  let childEscapeCount = 0

  documentRef.activeElement = parentTrigger
  const parentManager = createManager(documentRef, parentContainer, parentButton, () => { parentEscapeCount += 1 })
  parentManager.activate()
  documentRef.activeElement = childTrigger
  const childManager = createManager(documentRef, childContainer, childButton, () => { childEscapeCount += 1 })
  childManager.activate()

  const childEscape = createKeyEvent("Escape")
  documentRef.dispatchKey(childEscape)
  assert.strictEqual(childEscapeCount, 1, "only the topmost nested dialog should handle Escape")
  assert.strictEqual(parentEscapeCount, 0, "a covered parent dialog should ignore Escape")
  assert.strictEqual(childEscape.defaultPrevented, true, "Escape should prevent the browser default")
  assert.strictEqual(childEscape.propagationStopped, true, "Escape should stop propagation before requesting close")

  childManager.deactivate()
  assert.strictEqual(documentRef.activeElement, childTrigger, "closing a child should restore its trigger inside the parent")
  const parentEscape = createKeyEvent("Escape")
  documentRef.dispatchKey(parentEscape)
  assert.strictEqual(parentEscapeCount, 1, "the parent should resume Escape handling after the child closes")

  parentManager.deactivate()
  const inactiveEscape = createKeyEvent("Escape")
  documentRef.dispatchKey(inactiveEscape)
  assert.strictEqual(parentEscapeCount, 1, "an inactive manager should not handle later keyboard events")
}

resetMobileDialogFocusStackForTest()

console.log("mobileDialogFocus tests passed")
