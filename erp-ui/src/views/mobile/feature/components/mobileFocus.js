function focusElementAndVerify(target) {
  if (!target || typeof target.focus !== "function") return false

  try {
    target.focus({ preventScroll: true })
  } catch (error) {
    try {
      target.focus()
    } catch (fallbackError) {
      return false
    }
  }

  const ownerDocument = target.ownerDocument
  if (!ownerDocument || !("activeElement" in ownerDocument)) return true
  return ownerDocument.activeElement === target
}

function runFocusWithFallback(focusTarget, focusFallback) {
  if (typeof focusTarget === "function" && focusTarget()) return true
  return typeof focusFallback === "function" ? !!focusFallback() : false
}

module.exports = {
  focusElementAndVerify,
  runFocusWithFallback
}
