const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")

function readFile(relativePath) {
  const absolutePath = path.join(rootDir, relativePath)
  return fs.existsSync(absolutePath) ? fs.readFileSync(absolutePath, "utf8") : ""
}

const mobileSystemPath = "src/views/mobile/styles/mobileSystem.scss"
const mobileSystemSource = readFile(mobileSystemPath)
const globalStylesSource = readFile("src/assets/styles/index.scss")
const publicHtmlSource = readFile("public/index.html")

assert.ok(
  fs.existsSync(path.join(rootDir, mobileSystemPath)),
  "the existing mobile pages should share one progressive design token and layout layer"
)

assert.ok(
  globalStylesSource.includes("mobileSystem.scss"),
  "the shared mobile system should be loaded once by the existing global stylesheet"
)

assert.ok(
  /content=["'][^"']*viewport-fit=cover/.test(publicHtmlSource),
  "the mobile viewport should expose iOS safe-area insets"
)

;[
  "--mobile-viewport-height",
  "--mobile-keyboard-inset",
  "--mobile-bottom-nav-height",
  "--mobile-bottom-nav-total",
  "--mobile-safe-top",
  "--mobile-safe-bottom",
  "--mobile-color-primary",
  "--mobile-color-danger",
  "--mobile-control-height",
  "--mobile-primary-control-height",
  "--mobile-radius-sm",
  "--mobile-radius-md",
  "--mobile-radius-lg"
].forEach(token => {
  assert.ok(mobileSystemSource.includes(token), `shared mobile styles should define ${token}`)
})

assert.ok(
  /\.mobile-system-control[\s\S]*?min-height:\s*44px/.test(mobileSystemSource),
  "shared mobile controls should keep a 44px touch baseline"
)

assert.ok(
  /:where\(\.mobile-system-page, \.mobile-system-sheet\) button[\s\S]*?min-width:\s*44px[\s\S]*?min-height:\s*var\(--mobile-control-height\)/.test(mobileSystemSource) &&
    /input:not\(\[type="checkbox"\]\)[\s\S]*?font-size:\s*16px/.test(mobileSystemSource),
  "every migrated mobile page should inherit touch-sized buttons and non-zooming form controls"
)

assert.ok(
  /\.mobile-button--primary[\s\S]*?min-height:\s*var\(--mobile-primary-control-height\)/.test(mobileSystemSource) &&
    /\.mobile-icon-button[\s\S]*?width:\s*var\(--mobile-control-height\)[\s\S]*?height:\s*var\(--mobile-control-height\)/.test(mobileSystemSource) &&
    /\.mobile-state--error[\s\S]*?var\(--mobile-color-danger-soft\)/.test(mobileSystemSource),
  "the shared layer should expose consistent primary, icon-button, and recoverable error-state primitives"
)

assert.ok(
  /\.mobile-system-scroll[\s\S]*?padding-bottom:\s*calc\(var\(--mobile-bottom-nav-total\)/.test(mobileSystemSource),
  "the shared mobile scroller should reserve bottom-navigation and safe-area space"
)

assert.ok(
  /\.mobile-system-bottom-nav[\s\S]*?background:\s*var\(--mobile-color-surface\)/.test(mobileSystemSource),
  "the shared mobile navigation should be opaque"
)

assert.ok(
  !/\.mobile-system-bottom-nav[\s\S]*?backdrop-filter/.test(mobileSystemSource),
  "the shared mobile navigation should not rely on blur"
)

assert.ok(
  /:focus-visible/.test(mobileSystemSource),
  "the shared mobile system should expose a visible keyboard focus treatment"
)

assert.ok(
  /@media\s*\(prefers-reduced-motion:\s*reduce\)/.test(mobileSystemSource),
  "the shared mobile system should respect reduced-motion preferences"
)

const mobileViewportPath = "src/views/mobile/mobileViewport.js"
const mobileViewportSource = readFile(mobileViewportPath)
const overlayStackSource = readFile("src/views/mobile/feature/components/mobileOverlayStack.js")

assert.ok(
  fs.existsSync(path.join(rootDir, mobileViewportPath)),
  "the existing mobile pages should share dynamic visual-viewport synchronization"
)

;[
  "calculateMobileViewport",
  "applyMobileViewport",
  "startMobileViewportSync",
  "stopMobileViewportSync"
].forEach(exportName => {
  assert.ok(mobileViewportSource.includes(exportName), `mobile viewport utility should expose ${exportName}`)
})

assert.ok(
  mobileViewportSource.includes("visualViewport") &&
    mobileViewportSource.includes("--mobile-keyboard-inset") &&
    mobileViewportSource.includes("orientationchange"),
  "dynamic viewport synchronization should track keyboard, viewport scroll, resize, and orientation"
)

if (mobileViewportSource) {
  const { calculateMobileViewport } = require(path.join(rootDir, mobileViewportPath))
  assert.deepStrictEqual(
    calculateMobileViewport({
      innerHeight: 844,
      visualViewport: { height: 520, offsetTop: 44 }
    }),
    { height: 520, offsetTop: 44, keyboardInset: 280 },
    "keyboard inset should account for both visual viewport height and top offset"
  )
}

;[
  "src/views/mobile/feature/index.vue",
  "src/views/mobile/components/MobileWorkbenchShell.vue",
  "src/views/mobile/inventory/index.vue",
  "src/views/mobile/profile/index.vue"
].forEach(relativePath => {
  const source = readFile(relativePath)
  assert.ok(source.includes("data-mobile-scroll-root"), `${relativePath} should identify its real page scroller`)
  assert.ok(source.includes("startMobileViewportSync"), `${relativePath} should start dynamic viewport synchronization`)
  assert.ok(source.includes("stopMobileViewportSync"), `${relativePath} should stop dynamic viewport synchronization`)
})

assert.ok(
  overlayStackSource.includes("findVisibleMobileScrollRoot") &&
    overlayStackSource.includes("data-mobile-scroll-root") &&
    overlayStackSource.includes("savedScrollRootState"),
  "the overlay stack should lock the visible mobile page scroller in addition to the document body"
)

assert.ok(
  overlayStackSource.includes('scrollRoot.style.overflow = "hidden"') &&
    overlayStackSource.includes("scrollRoot.scrollTop = savedScrollRootState.scrollTop"),
  "the overlay stack should preserve and restore the real scroller position"
)

const overlayPortalPath = "src/views/mobile/feature/components/mobileOverlayPortal.js"
const overlayPortalSource = readFile(overlayPortalPath)
const actionDialogSource = readFile("src/views/mobile/feature/components/MobileActionDialog.vue")
const quickCustomerSource = readFile("src/views/mobile/feature/components/MobileQuickCustomerForm.vue")
const mobileSheetSource = readFile("src/views/mobile/feature/components/mobileSheet.scss")

assert.ok(
  fs.existsSync(path.join(rootDir, overlayPortalPath)),
  "nested mobile dialogs should share a reversible portal instead of remaining inside clipped sheets"
)

assert.ok(
  overlayPortalSource.includes("portalMobileOverlay") && overlayPortalSource.includes("restoreMobileOverlay"),
  "the mobile overlay portal should support both mounting and restoring Vue-managed nodes"
)

if (overlayPortalSource) {
  const { portalMobileOverlay, restoreMobileOverlay } = require(path.join(rootDir, overlayPortalPath))
  const parent = {
    appended: null,
    inserted: null,
    appendChild(node) {
      this.appended = node
      node.parentNode = this
    },
    insertBefore(node, sibling) {
      this.inserted = { node, sibling }
      node.parentNode = this
    }
  }
  const body = {
    appendChild(node) {
      node.parentNode = this
    }
  }
  const sibling = { parentNode: parent }
  const node = { parentNode: parent, nextSibling: sibling }
  const anchor = portalMobileOverlay(node, body)
  assert.strictEqual(node.parentNode, body, "portal should move the existing node to the body host")
  assert.strictEqual(restoreMobileOverlay(node, anchor), true, "portal should restore the original mount point")
  assert.deepStrictEqual(parent.inserted, { node, sibling })
}

assert.ok(
  actionDialogSource.includes("portalMobileOverlay") &&
    actionDialogSource.includes("action-dialog-body") &&
    actionDialogSource.includes("action-dialog-footer"),
  "long action dialogs should escape the feature sheet and separate their scroll body from actions"
)

assert.ok(
  quickCustomerSource.includes("portalMobileOverlay") &&
    quickCustomerSource.includes("quick-customer-body") &&
    quickCustomerSource.includes("quick-customer-footer"),
  "quick customer creation should escape the entity picker and keep its actions outside the scroll body"
)

assert.ok(
  /\.action-dialog-body\s*\{[\s\S]*?overflow-y:\s*auto/.test(mobileSheetSource),
  "the action dialog content should be its only vertical scroller"
)

assert.ok(
  /\.quick-customer-body\s*\{[\s\S]*?overflow-y:\s*auto/.test(quickCustomerSource),
  "the quick-customer fields should be its only vertical scroller"
)

assert.ok(
  !/body\.mobile-action-dialog-open\s*\{[\s\S]*?touch-action:\s*none/.test(actionDialogSource),
  "the action dialog should not disable touch scrolling on its entire body"
)

const featurePageSource = readFile("src/views/mobile/feature/index.vue")
const workbenchShellSource = readFile("src/views/mobile/components/MobileWorkbenchShell.vue")
const detailSheetSource = readFile("src/views/mobile/feature/components/MobileDetailSheet.vue")
const formSheetSource = readFile("src/views/mobile/feature/components/MobileFormSheet.vue")
const lineItemsSource = readFile("src/views/mobile/feature/components/MobileLineItemsEditor.vue")

;[
  'class="feature-header mobile-system-topbar"',
  'class="feature-subtitle mobile-system-muted"',
  'class="glass-panel search-panel mobile-system-panel"',
  'class="glass-panel list-panel mobile-system-panel mobile-system-panel--continuous"',
  'class="bottom-nav mobile-system-bottom-nav"'
].forEach(contract => {
  assert.ok(featurePageSource.includes(contract), `the existing feature page should adopt ${contract}`)
})

assert.ok(
  featurePageSource.includes("padding-bottom: calc(var(--mobile-bottom-nav-total)") &&
    featurePageSource.includes("background-image: none") &&
    featurePageSource.includes("backdrop-filter: none"),
  "the feature page should reserve the shared navigation space and remove decorative glass/photo styling"
)

assert.ok(
  /\.shop-pill\s*\{[\s\S]*?min-height:\s*var\(--mobile-control-height\)/.test(featurePageSource) &&
    /\.selector-pill\s*\{[\s\S]*?min-height:\s*var\(--mobile-control-height\)/.test(workbenchShellSource),
  "shared organization selectors should no longer create sub-44px controls across reused routes"
)

assert.ok(
  /\.list-row h3,[\s\S]*?\.list-row p[\s\S]*?overflow-wrap:\s*anywhere/.test(featurePageSource),
  "long business titles, codes, and summaries should wrap inside their list column"
)

assert.ok(
  detailSheetSource.includes("detail-sheet-body") &&
    detailSheetSource.includes("detail-sheet-footer") &&
    detailSheetSource.includes("mobile-system-sheet__body"),
  "document details should use one internal scroller above a reachable footer"
)

assert.ok(
  formSheetSource.includes("mobile-system-sheet") &&
    formSheetSource.includes("mobile-system-sheet__header") &&
    formSheetSource.includes("mobile-system-sheet__body") &&
    formSheetSource.includes("mobile-system-sheet__footer"),
  "existing mobile forms should adopt the shared header/body/footer sheet contract"
)

assert.ok(
  /\.glass-panel\s*\{[\s\S]*?background:\s*var\(--mobile-color-surface/.test(mobileSheetSource) &&
    /\.glass-panel\s*\{[\s\S]*?backdrop-filter:\s*none/.test(mobileSheetSource),
  "shared business sheets should use an opaque enterprise surface instead of glass blur"
)

assert.ok(
  !mobileSheetSource.includes("linear-gradient") &&
    /@media \(max-width: 390px\)[\s\S]*?\.detail-sheet\s*\{\s*padding:\s*0;/.test(mobileSheetSource),
  "mobile detail and action sheets should stay opaque, restrained, and edge-aligned at phone widths"
)

assert.ok(
  /\.stock-option-main strong,[\s\S]*?overflow-wrap:\s*anywhere/.test(lineItemsSource),
  "long selected-product names and codes should remain inside the current form width"
)

const workbenchSource = readFile("src/views/mobile/components/MobileWorkbenchShell.vue")
const inventoryWorkbenchSource = readFile("src/views/mobile/inventory/index.vue")

;[
  'class="title-row mobile-system-topbar"',
  'class="glass-card todo-card mobile-system-panel mobile-system-panel--continuous"',
  'glass-card hero-card mobile-system-panel',
  'class="bottom-nav mobile-system-bottom-nav"'
].forEach(contract => {
  assert.ok(workbenchSource.includes(contract), `the existing workbench should adopt ${contract}`)
})

;[
  'class="title-row mobile-system-topbar"',
  'glass-card hero-card mobile-system-panel',
  'class="glass-card priority-card mobile-system-panel mobile-system-panel--continuous"',
  'class="bottom-nav mobile-system-bottom-nav"'
].forEach(contract => {
  assert.ok(inventoryWorkbenchSource.includes(contract), `the inventory workbench should adopt ${contract}`)
})

;[
  [workbenchSource, "main workbench"],
  [inventoryWorkbenchSource, "inventory workbench"]
].forEach(([source, label]) => {
  assert.ok(
    source.includes("background-image: none") &&
      source.includes("backdrop-filter: none") &&
      source.includes("padding-bottom: calc(var(--mobile-bottom-nav-total)"),
    `${label} should remove photo/glass styling and reserve the shared navigation space`
  )
})

const workbenchRedesignMarker = "/* Progressive mobile redesign: align the workbench with the shared feature shell. */"
const workbenchRedesignIndex = workbenchSource.lastIndexOf(workbenchRedesignMarker)
const workbenchRedesignSource = workbenchRedesignIndex >= 0
  ? workbenchSource.slice(workbenchRedesignIndex)
  : ""

function getLastRuleBody(source, selector) {
  const marker = `${selector} {`
  const start = source.lastIndexOf(marker)
  if (start < 0) return ""
  const bodyStart = start + marker.length
  const end = source.indexOf("}", bodyStart)
  return end < 0 ? "" : source.slice(bodyStart, end)
}

const workbenchShellRule = getLastRuleBody(workbenchRedesignSource, ".mobile-shell")
const workbenchContentRule = getLastRuleBody(workbenchRedesignSource, ".content-stage")
const workbenchHeaderRule = getLastRuleBody(workbenchRedesignSource, ".title-row")
const workbenchHeaderCopyRule = getLastRuleBody(workbenchRedesignSource, ".title-row > div:first-child")
const workbenchHeaderActionsRule = getLastRuleBody(workbenchRedesignSource, ".header-actions")
const workbenchTitleRule = getLastRuleBody(workbenchRedesignSource, "h1")
const workbenchQuickGridRule = getLastRuleBody(workbenchRedesignSource, ".quick-grid")
const workbenchQuickActionRule = getLastRuleBody(workbenchRedesignSource, ".glass-action")
const workbenchWarningMarkRule = getLastRuleBody(workbenchRedesignSource, ".todo-mark.amber")
const workbenchDangerMarkRule = getLastRuleBody(workbenchRedesignSource, ".todo-mark.red")
const workbenchBottomNavRule = getLastRuleBody(workbenchRedesignSource, ".bottom-nav")

assert.ok(
  workbenchRedesignSource &&
    workbenchShellRule.includes("width: min(100%, 480px);") &&
    workbenchShellRule.includes("box-shadow: none;"),
  "the store and warehouse workbench should use the same 480px shadow-free shell as feature pages"
)

assert.ok(
  workbenchContentRule.includes("padding: 0 16px;") &&
    workbenchContentRule.includes("padding-bottom: calc(var(--mobile-bottom-nav-total) + 16px);") &&
    workbenchContentRule.includes("scroll-padding-bottom: calc(var(--mobile-bottom-nav-total) + var(--mobile-keyboard-inset) + 16px);") &&
    workbenchHeaderRule.includes("margin: 0 -16px 14px;") &&
    workbenchHeaderRule.includes("background: var(--mobile-color-surface);") &&
    workbenchHeaderCopyRule.includes("flex: 1 1 auto;") &&
    workbenchHeaderActionsRule.includes("flex: 0 0 auto;") &&
    workbenchTitleRule.includes("font-size: 24px;"),
  "the workbench content and top bar should match the feature page spacing and title scale"
)

assert.ok(
  workbenchQuickGridRule.includes("grid-template-columns: repeat(2, minmax(0, 1fr));") &&
    workbenchQuickActionRule.includes("min-height: 56px;") &&
    workbenchQuickActionRule.includes("border-radius: var(--mobile-radius-md);"),
  "workbench shortcuts should use the same compact two-column action treatment as feature pages"
)

assert.ok(
  workbenchWarningMarkRule.includes("color: var(--mobile-color-warning);") &&
    workbenchWarningMarkRule.includes("background: var(--mobile-color-warning-soft);") &&
    workbenchDangerMarkRule.includes("color: var(--mobile-color-danger);") &&
    workbenchDangerMarkRule.includes("background: var(--mobile-color-danger-soft);"),
  "workbench todo icons should preserve warning and risk semantics after the flat visual redesign"
)

assert.ok(
  workbenchBottomNavRule.includes("z-index: 80;") &&
    workbenchBottomNavRule.includes("bottom: 0;") &&
    workbenchBottomNavRule.includes("left: 0;") &&
    workbenchBottomNavRule.includes("width: 100%;") &&
    workbenchBottomNavRule.includes("min-height: var(--mobile-bottom-nav-total);") &&
    workbenchBottomNavRule.includes("padding: 6px var(--mobile-safe-right) var(--mobile-safe-bottom) var(--mobile-safe-left);") &&
    workbenchBottomNavRule.includes("transform: none;") &&
    workbenchBottomNavRule.includes("border-radius: 0;"),
  "the workbench navigation should be the same edge-aligned bottom bar as feature pages"
)

assert.ok(
  !workbenchRedesignSource.includes("linear-gradient") && !workbenchRedesignSource.includes("url("),
  "the final workbench redesign layer should not reintroduce decorative glass gradients or photo assets"
)

const mobileNavigationSource = readFile("src/views/mobile/mobileNavigation.js")
const profileSource = readFile("src/views/mobile/profile/index.vue")
const contractSource = readFile("src/views/mobile/contract/index.vue")
const signPackageSource = readFile("src/views/mobile/signPackage/index.vue")

assert.ok(
  mobileNavigationSource.includes("isMobileBottomNavItemActive"),
  "bottom-navigation active state should be resolved centrally for nested self-service pages"
)

if (mobileNavigationSource.includes("isMobileBottomNavItemActive")) {
  const { MOBILE_ROUTES, isMobileBottomNavItemActive } = require(path.join(rootDir, "src/views/mobile/mobileNavigation.js"))
  assert.strictEqual(isMobileBottomNavItemActive(MOBILE_ROUTES.mine, MOBILE_ROUTES.profile), true)
  assert.strictEqual(isMobileBottomNavItemActive(MOBILE_ROUTES.stock, MOBILE_ROUTES.profile), false)
}

;[
  'class="profile-header mobile-system-topbar"',
  'class="profile-card profile-summary mobile-system-panel"',
  'class="mobile-profile-bottom-nav mobile-system-bottom-nav"',
  "isBottomNavItemActive(item)"
].forEach(contract => {
  assert.ok(profileSource.includes(contract), `the existing profile page should adopt ${contract}`)
})

assert.ok(
  /padding-bottom:\s*calc\(var\(--mobile-bottom-nav-total(?:,\s*[^)]+)?\)/.test(profileSource) &&
    profileSource.includes("backdrop-filter: none"),
  "profile should share the opaque navigation and page-spacing system"
)

assert.ok(
  contractSource.includes("mobile-contract-page mobile-system-page") &&
    contractSource.includes("data-mobile-scroll-root") &&
    contractSource.includes("startMobileViewportSync") &&
    contractSource.includes("stopMobileViewportSync") &&
    contractSource.includes("var(--mobile-color-primary"),
  "the existing contract page should use shared viewport, scrolling, and color contracts"
)

assert.ok(
  signPackageSource.includes("mobile-system-page") &&
    signPackageSource.includes("data-mobile-scroll-root") &&
    signPackageSource.includes("startMobileViewportSync") &&
    signPackageSource.includes("stopMobileViewportSync") &&
    signPackageSource.includes("sign-panel-body") &&
    signPackageSource.includes("sign-panel-footer"),
  "the signing page should use the shared viewport and an internally scrollable signing panel"
)

assert.ok(
  !signPackageSource.includes("324px") &&
    signPackageSource.includes("max-height: calc(var(--mobile-viewport-height") &&
    signPackageSource.includes("bottom: var(--mobile-keyboard-inset"),
  "the signing panel should follow the visual viewport instead of fixed expanded-height padding"
)

console.log("mobile progressive redesign foundation checks passed")
