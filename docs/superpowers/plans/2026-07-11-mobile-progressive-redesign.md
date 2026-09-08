# Mobile Progressive Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task in the current workspace. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modernize the existing mobile ERP pages in place, remove every reachable-content obstruction, and unify the visual system without replacing routes, APIs, permissions, data mapping, or business workflows.

**Architecture:** Add one shared mobile token/layout layer and one shared viewport/overlay contract, then progressively connect the existing page components to those foundations. Preserve the configuration-driven feature runtime and existing business components; template changes are limited to scroll/body/footer boundaries, shared navigation styling, and information hierarchy.

**Tech Stack:** Vue 2, Vue Router 3, SCSS, CommonJS source-contract tests, Element UI, Capacitor WebView.

**Workspace note:** The working tree already contains a large set of user changes. Do not create commits or reset files during this plan; use focused diffs and `git diff --check` as checkpoints.

---

### Task 1: Shared mobile visual and safe-area foundation

**Files:**
- Create: `erp-ui/src/views/mobile/styles/mobileSystem.scss`
- Modify: `erp-ui/src/assets/styles/index.scss`
- Modify: `erp-ui/public/index.html`
- Create: `erp-ui/test/mobileProgressiveRedesign.test.js`

- [ ] **Step 1: Write the failing source-contract test**

Create assertions that require the shared style file, its global import, `viewport-fit=cover`, the dynamic viewport variables, the opaque navigation contract, the 44px target baseline, and the reduced-motion rule:

```js
const assert = require("assert")
const fs = require("fs")
const path = require("path")
const root = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.join(root, file), "utf8")

const tokens = read("src/views/mobile/styles/mobileSystem.scss")
const globalStyles = read("src/assets/styles/index.scss")
const html = read("public/index.html")

assert.ok(globalStyles.includes("mobileSystem.scss"))
assert.ok(html.includes("viewport-fit=cover"))
assert.ok(tokens.includes("--mobile-viewport-height"))
assert.ok(tokens.includes("--mobile-bottom-nav-height"))
assert.ok(tokens.includes("--mobile-safe-bottom"))
assert.ok(tokens.includes("min-height: 44px"))
assert.ok(tokens.includes("prefers-reduced-motion"))
assert.ok(!/\.mobile-system-bottom-nav[\s\S]*backdrop-filter/.test(tokens))
```

- [ ] **Step 2: Run the test and verify red**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js`<br/>
Expected: fail because `mobileSystem.scss` does not exist.

- [ ] **Step 3: Add the shared token/layout layer**

Define `:root` variables for warm neutral surfaces, ink text, jade primary, danger/warning, 8/12/16px radii, 44px controls, 68px navigation, safe-area values, and `--mobile-viewport-height: 100dvh`. Add reusable classes for page roots, scroll roots, top bars, continuous panels, buttons, status text, bottom navigation, focus-visible, and reduced motion. Import it once from `src/assets/styles/index.scss` and change the viewport meta to:

```html
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
```

- [ ] **Step 4: Run foundation checks**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && git diff --check`<br/>
Expected: pass with no whitespace errors.

### Task 2: Dynamic viewport and actual scroll-root locking

**Files:**
- Create: `erp-ui/src/views/mobile/mobileViewport.js`
- Modify: `erp-ui/src/views/mobile/feature/components/mobileOverlayStack.js`
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Modify: `erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue`
- Modify: `erp-ui/src/views/mobile/inventory/index.vue`
- Modify: `erp-ui/src/views/mobile/profile/index.vue`
- Test: `erp-ui/test/mobileProgressiveRedesign.test.js`
- Test: `erp-ui/test/mobileReplenishmentOverlayRegression.test.js`

- [ ] **Step 1: Add failing viewport and scroll-root assertions**

Require `startMobileViewportSync`/`stopMobileViewportSync`, CSS properties `--mobile-viewport-height` and `--mobile-keyboard-inset`, a `data-mobile-scroll-root` marker on each main page scroller, and overlay-stack preservation/restoration of that element's `scrollTop` and `overflow`.

- [ ] **Step 2: Run the two tests and verify red**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && node test/mobileReplenishmentOverlayRegression.test.js`<br/>
Expected: the new dynamic viewport assertions fail while the existing replenishment checks remain green.

- [ ] **Step 3: Implement viewport synchronization**

Export a small reference-counted utility:

```js
function applyMobileViewport(root = document.documentElement) {
  const viewport = window.visualViewport
  const height = viewport ? viewport.height : window.innerHeight
  const offsetTop = viewport ? viewport.offsetTop : 0
  const keyboardInset = Math.max(0, window.innerHeight - height - offsetTop)
  root.style.setProperty("--mobile-viewport-height", `${Math.round(height)}px`)
  root.style.setProperty("--mobile-viewport-offset-top", `${Math.round(offsetTop)}px`)
  root.style.setProperty("--mobile-keyboard-inset", `${Math.round(keyboardInset)}px`)
}
```

Export `applyMobileViewport`, `startMobileViewportSync`, and `stopMobileViewportSync`. Listen to `visualViewport.resize`, `visualViewport.scroll`, `window.resize`, and `orientationchange`; schedule updates with `requestAnimationFrame`; remove listeners when the final consumer stops.

- [ ] **Step 4: Lock the real page scroller**

Mark the existing main scroll elements with `data-mobile-scroll-root`. When the first overlay opens, locate the visible scroll root, save its `scrollTop`, `overflow`, and `overscrollBehavior`, set `overflow: hidden`, and restore them after the final overlay closes. Keep the existing body lock as a WebView fallback.

- [ ] **Step 5: Re-run focused tests**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && node test/mobileReplenishmentOverlayRegression.test.js`<br/>
Expected: both pass.

### Task 3: Portalled overlays with one internal scroller

**Files:**
- Create: `erp-ui/src/views/mobile/feature/components/mobileOverlayPortal.js`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileActionDialog.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileQuickCustomerForm.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/mobileSheet.scss`
- Test: `erp-ui/test/mobileProgressiveRedesign.test.js`
- Test: `erp-ui/test/mobileFormAccessibility.test.js`
- Test: `erp-ui/test/mobileReplenishmentOverlayRegression.test.js`

- [ ] **Step 1: Add failing overlay structure assertions**

Require action and quick-customer masks to call `portalMobileOverlay`, require `.action-dialog-body`/`.quick-customer-body` to own `overflow-y: auto`, require their footers to be outside that scrolling body, and reject `touch-action: none` on form bodies.

- [ ] **Step 2: Verify the regression test fails for the current action dialog**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js`<br/>
Expected: fail because the action form has no dedicated scrolling body and quick-customer remains inside the entity-picker stacking context.

- [ ] **Step 3: Add a reversible body portal helper**

Implement `portalMobileOverlay(node)` returning the original parent and next sibling, plus `restoreMobileOverlay(node, anchor)` for component teardown. Do not clone the Vue-managed node.

- [ ] **Step 4: Restructure the action and quick-customer forms**

Use this invariant in both components:

```html
<article class="mobile-system-sheet">
  <header class="mobile-system-sheet__header">...</header>
  <div class="mobile-system-sheet__body">...</div>
  <footer class="mobile-system-sheet__footer">...</footer>
</article>
```

The article is a bounded flex column using `max-height: calc(var(--mobile-viewport-height) - var(--mobile-safe-top) - var(--mobile-safe-bottom) - 24px)`. Only `__body` scrolls. Portal on open after `$nextTick`, restore before destruction, and retain the current focus managers.

- [ ] **Step 5: Run overlay and accessibility checks**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && node test/mobileFormAccessibility.test.js && node test/mobileReplenishmentOverlayRegression.test.js`<br/>
Expected: all pass.

### Task 4: Progressive redesign of the configuration-driven business page

**Files:**
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/mobileSheet.scss`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileDetailSheet.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileLineItemsEditor.vue`
- Test: `erp-ui/test/mobileProgressiveRedesign.test.js`
- Test: `erp-ui/test/mobileFirstScreenHierarchy.test.js`
- Test: `erp-ui/test/mobileFormAccessibility.test.js`
- Test: `erp-ui/test/mobileReplenishmentOverlayRegression.test.js`

- [ ] **Step 1: Add failing visual-structure contracts**

Require the feature root and stage to use shared page/scroll classes, a compact header, opaque continuous list panel, unified bottom navigation class, `overflow-wrap: anywhere` for long values, and bottom padding based on `--mobile-bottom-nav-total` rather than page-specific magic numbers.

- [ ] **Step 2: Run the focused tests and capture the expected failures**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && node test/mobileFirstScreenHierarchy.test.js`<br/>
Expected: the new shared-class and token assertions fail.

- [ ] **Step 3: Restyle the existing feature template in place**

Keep all existing directives, events, permissions and components. Replace decorative glass/photo styling with shared warm surfaces, 16px page gutters, 8–12px radii, hairline row separators, compact filters, and a non-transparent 68px navigation. Lists remain buttons and use a continuous grouped surface; action buttons remain visible according to the current action mapper.

- [ ] **Step 4: Normalize detail, form and product picker layout**

Keep current props/events. Use a fixed header, one scrollable body, and an internal sticky footer. Change quantity/product rows to `minmax(0, 1fr)` grids, wrap long codes, and use `scroll-padding-bottom` derived from the footer and keyboard inset.

- [ ] **Step 5: Re-run all feature checks**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && node test/mobileFirstScreenHierarchy.test.js && node test/mobileFormAccessibility.test.js && node test/mobileReplenishmentOverlayRegression.test.js`<br/>
Expected: all pass.

### Task 5: Task-first workbench and inventory alignment

**Files:**
- Modify: `erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue`
- Modify: `erp-ui/src/views/mobile/inventory/index.vue`
- Test: `erp-ui/test/mobileProgressiveRedesign.test.js`
- Test: `erp-ui/test/mobileInventoryWorkbench.test.js`
- Test: `erp-ui/test/mobileWorkbenchTaskRouting.test.js`

- [ ] **Step 1: Add failing hierarchy assertions**

Assert that priority tasks precede quick actions in the rendered template, the photo background/`backdrop-filter` rules are absent from workbench content, task rows use the continuous-panel treatment, and both workbench implementations use the shared bottom navigation class.

- [ ] **Step 2: Run tests and verify the new assertions fail**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && node test/mobileInventoryWorkbench.test.js && node test/mobileWorkbenchTaskRouting.test.js`

- [ ] **Step 3: Reorder existing sections without changing data flow**

Keep `loadWorkbench`, permission filtering, task routing and action arrays unchanged. Place compact overview first, priority tasks second, quick actions third, and secondary modules last. Remove decorative background image and glass blur; use the shared surface and divider tokens.

- [ ] **Step 4: Re-run workbench checks**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && node test/mobileInventoryWorkbench.test.js && node test/mobileWorkbenchTaskRouting.test.js`<br/>
Expected: all pass.

### Task 6: Profile, contract and signing-page alignment

**Files:**
- Modify: `erp-ui/src/views/mobile/profile/index.vue`
- Modify: `erp-ui/src/views/mobile/contract/index.vue`
- Modify: `erp-ui/src/views/mobile/signPackage/index.vue`
- Test: `erp-ui/test/mobileProgressiveRedesign.test.js`
- Test: `erp-ui/test/mobileProfileMaintenance.test.js`
- Test: `erp-ui/test/laborContractModule.test.js`
- Test: `erp-ui/test/signPackageModule.test.js`

- [ ] **Step 1: Add failing shared-shell and signing-panel assertions**

Require the three pages to use shared tokens and focus styles; require profile navigation to use the centralized active-route resolver; require contract back/refresh controls to remain at least 44px; require the signing panel to use dynamic viewport max-height, a dedicated scroll body, and safe-area/keyboard-aware footer spacing.

- [ ] **Step 2: Run tests and verify red**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && node test/mobileProfileMaintenance.test.js && node test/laborContractModule.test.js && node test/signPackageModule.test.js`

- [ ] **Step 3: Apply the shared visual system in the existing templates**

Do not change profile fetch/update methods, contract loading, PDF rendering, signing validation or signing requests. Replace the page-local blue/indigo/green systems with shared neutral/jade variables; use the shared top and bottom bars; convert signing panel magic `padding-bottom` values to dynamic variables and one internal scrolling body.

- [ ] **Step 4: Re-run focused tests**

Run: `cd erp-ui && node test/mobileProgressiveRedesign.test.js && node test/mobileProfileMaintenance.test.js && node test/laborContractModule.test.js && node test/signPackageModule.test.js`<br/>
Expected: all pass.

### Task 7: Complete draft edit/delete contracts without changing APIs

**Files:**
- Modify: `erp-ui/src/views/mobile/feature/mobileFormPayloads.js`
- Modify: `erp-ui/src/views/mobile/feature/mobileFormConfigs.js`
- Modify: `erp-ui/src/views/mobile/feature/featureActions.js`
- Modify: `erp-ui/src/views/mobile/feature/featureActionService.js`
- Modify: `erp-ui/src/views/mobile/feature/featureActionRuntime.js`
- Modify: `erp-ui/src/api/inventory/transfer.js`
- Test: `erp-ui/test/mobileReplenishmentDraftWorkflow.test.js`
- Test: `erp-ui/test/mobileFeatureFormConfig.test.js`
- Test: `erp-ui/test/mobileFormPayloads.test.js`
- Test: `erp-ui/test/mobileFeatureActions.test.js`
- Test: `erp-ui/test/mobileFeatureActionRuntime.test.js`

- [ ] **Step 1: Extend tests with backend-shaped draft fixtures**

Fixtures must use backend field names such as `unitPrice`, nested detail identifiers, warehouse/store/customer/supplier identifiers, and draft statuses. Assert edit hydration preserves item, quantity and price; assert delete is exposed only for draft plus permission; assert delete refreshes the list and closes detail.

- [ ] **Step 2: Run the draft suite and record exact red cases**

Run: `cd erp-ui && node test/mobileReplenishmentDraftWorkflow.test.js && node test/mobileFeatureFormConfig.test.js && node test/mobileFormPayloads.test.js && node test/mobileFeatureActions.test.js && node test/mobileFeatureActionRuntime.test.js`

- [ ] **Step 3: Implement only the failing adapters/actions**

Use field fallback order `unitPrice -> price -> referenceCostPrice`, preserve identifiers from both detail and parent models, and register draft delete actions only where the matching API wrapper already exists or can call an existing backend endpoint. Do not add optimistic success: wait for the request, then close and refresh.

- [ ] **Step 4: Re-run the draft suite**

Run: `cd erp-ui && node test/mobileReplenishmentDraftWorkflow.test.js && node test/mobileFeatureFormConfig.test.js && node test/mobileFormPayloads.test.js && node test/mobileFeatureActions.test.js && node test/mobileFeatureActionRuntime.test.js`<br/>
Expected: all pass.

### Task 8: Full verification and visual regression

**Files:**
- Modify: `docs/superpowers/verification/2026-07-11-mobile-full-remediation.md`
- Create after an authenticated capture succeeds: `docs/audit-screenshots/mobile-progressive-redesign-20260711/`

- [ ] **Step 1: Run all non-copy mobile tests**

Run a shell loop over `test/mobile*.test.js` excluding filenames with numbered-copy suffixes. Expected: all current non-copy mobile tests pass. Report any historical unrelated failure by exact filename and assertion.

- [ ] **Step 2: Run the production build**

Run: `cd erp-ui && npm run build:prod`<br/>
Expected: exit 0.

- [ ] **Step 3: Run same-size mobile visual checks**

At 320×568, 375×667 and 390×844, inspect workbench, replenishment list, draft detail, edit form, product picker, profile, contract and signing panel. For each state confirm no horizontal overflow, last action reachable, keyboard avoidance, no bottom-nav overlap, and no console errors. Do not bypass authentication or fabricate backend data.

- [ ] **Step 4: Update verification evidence**

Record exact test/build outputs, visual states inspected, screenshots produced, and any external authentication/device limit. Run `git diff --check` as the final source checkpoint.
