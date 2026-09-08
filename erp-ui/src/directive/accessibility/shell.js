const PAGINATION_BUTTONS = [
  ['.el-pagination .btn-prev', '上一页'],
  ['.el-pagination .btn-next', '下一页']
]

function applyAccessibility(el) {
  PAGINATION_BUTTONS.forEach(([selector, label]) => {
    el.querySelectorAll(selector).forEach(button => {
      if (!button.getAttribute('aria-label')) {
        button.setAttribute('aria-label', label)
      }
    })
  })
}

function scheduleApply(el) {
  if (el.__accessibleShellFrame) {
    cancelAnimationFrame(el.__accessibleShellFrame)
  }
  el.__accessibleShellFrame = requestAnimationFrame(() => {
    el.__accessibleShellFrame = null
    applyAccessibility(el)
  })
}

function installInputModalityTracking(el) {
  if (typeof document === 'undefined' || typeof window === 'undefined') return

  const root = document.documentElement
  const setPointerModality = () => root.setAttribute('data-input-modality', 'pointer')
  const setKeyboardModality = () => root.setAttribute('data-input-modality', 'keyboard')

  // Default to the motion-safe option until a real pointer interaction occurs.
  setKeyboardModality()
  window.addEventListener('pointerdown', setPointerModality, true)
  window.addEventListener('keydown', setKeyboardModality, true)
  el.__accessibleShellInputModality = {
    setPointerModality,
    setKeyboardModality
  }
}

function removeInputModalityTracking(el) {
  if (typeof window === 'undefined' || !el.__accessibleShellInputModality) return
  const { setPointerModality, setKeyboardModality } = el.__accessibleShellInputModality
  window.removeEventListener('pointerdown', setPointerModality, true)
  window.removeEventListener('keydown', setKeyboardModality, true)
  delete el.__accessibleShellInputModality
}

export default {
  inserted(el) {
    scheduleApply(el)
    installInputModalityTracking(el)
    if (typeof MutationObserver === 'undefined') return
    el.__accessibleShellObserver = new MutationObserver(() => {
      scheduleApply(el)
    })
    el.__accessibleShellObserver.observe(el, {
      childList: true,
      subtree: true
    })
  },
  componentUpdated(el) {
    scheduleApply(el)
  },
  unbind(el) {
    removeInputModalityTracking(el)
    if (el.__accessibleShellObserver) {
      el.__accessibleShellObserver.disconnect()
      delete el.__accessibleShellObserver
    }
    if (el.__accessibleShellFrame) {
      cancelAnimationFrame(el.__accessibleShellFrame)
      delete el.__accessibleShellFrame
    }
  }
}
