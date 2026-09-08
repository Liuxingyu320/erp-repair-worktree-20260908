const FIXED_REGION_SELECTOR = '.el-table__fixed, .el-table__fixed-right'
const FOCUSABLE_SELECTOR = 'a[href], button, input, select, textarea, [tabindex], [role="button"], [role="switch"], [role="checkbox"]'

function tableLabel(binding) {
  return typeof binding.value === 'string' && binding.value.trim()
    ? binding.value.trim()
    : '数据列表'
}

function markFixedRegions(el) {
  el.querySelectorAll(FIXED_REGION_SELECTOR).forEach(region => {
    region.setAttribute('aria-hidden', 'true')
    region.setAttribute('role', 'presentation')
    region.querySelectorAll('table').forEach(table => {
      table.setAttribute('role', 'presentation')
    })
    region.querySelectorAll(FOCUSABLE_SELECTOR).forEach(control => {
      control.setAttribute('tabindex', '-1')
    })
  })
}

function isInsideFixedRegion(control) {
  return Boolean(control.closest(FIXED_REGION_SELECTOR))
}

function labelSelectionControls(el, label) {
  el
    .querySelectorAll('.el-table__header-wrapper th.el-table-column--selection input[type="checkbox"]')
    .forEach(control => {
      if (!isInsideFixedRegion(control)) {
        control.setAttribute('aria-label', `选择当前页全部${label}行`)
      }
    })

  el.querySelectorAll('.el-table__body-wrapper tbody > tr').forEach((row, index) => {
    row
      .querySelectorAll('td.el-table-column--selection input[type="checkbox"]')
      .forEach(control => {
        if (!isInsideFixedRegion(control)) {
          control.setAttribute('aria-label', `选择${label}第${index + 1}行`)
        }
      })
  })
}

function syncSwitchControlNames(el) {
  el.querySelectorAll('.el-switch[aria-label]').forEach(control => {
    const input = control.querySelector('input[type="checkbox"]')
    if (input && !isInsideFixedRegion(input)) {
      input.setAttribute('aria-label', control.getAttribute('aria-label'))
    }
  })
}

function applyAccessibility(el, binding) {
  const label = tableLabel(binding)
  el.setAttribute('role', 'region')
  el.setAttribute('aria-label', label)
  markFixedRegions(el)
  labelSelectionControls(el, label)
  syncSwitchControlNames(el)
}

function scheduleApply(el, binding) {
  if (el.__accessibleTableFrame) {
    cancelAnimationFrame(el.__accessibleTableFrame)
  }
  el.__accessibleTableFrame = requestAnimationFrame(() => {
    el.__accessibleTableFrame = null
    applyAccessibility(el, binding)
  })
}

export default {
  inserted(el, binding) {
    scheduleApply(el, binding)
    if (typeof MutationObserver === 'undefined') return
    el.__accessibleTableObserver = new MutationObserver(() => {
      scheduleApply(el, binding)
    })
    el.__accessibleTableObserver.observe(el, {
      childList: true,
      subtree: true
    })
  },
  componentUpdated(el, binding) {
    scheduleApply(el, binding)
  },
  unbind(el) {
    if (el.__accessibleTableObserver) {
      el.__accessibleTableObserver.disconnect()
      delete el.__accessibleTableObserver
    }
    if (el.__accessibleTableFrame) {
      cancelAnimationFrame(el.__accessibleTableFrame)
      delete el.__accessibleTableFrame
    }
  }
}
