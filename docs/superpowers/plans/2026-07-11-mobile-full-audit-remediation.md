# Mobile Full Audit Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the validated mobile feature runtime, remove narrow-screen obstruction, repair interaction semantics, and verify the Capacitor shells.

**Architecture:** Recover the two regressed orchestration/configuration files from the validated local references, while retaining currently passing form, mapper, and inventory modules. Add focused source-contract tests for the remaining layout and touch gaps, then verify browser routes and native build gates.

**Tech Stack:** Vue 2, Vue Router 3, SCSS, Node assertion tests, Capacitor 8, Android Gradle, Xcode Simulator.

---

### Task 1: Restore the mobile runtime contract

**Files:**
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Test: `erp-ui/test/mobileAccessibilityVisuals.test.js`
- Test: `erp-ui/test/mobileAndroidAuditRegression.test.js`
- Test: `erp-ui/test/mobileFeatureCompleteness.test.js`
- Test: `erp-ui/test/mobileFeatureComponentSplit.test.js`
- Test: `erp-ui/test/mobileFirstScreenHierarchy.test.js`
- Test: `erp-ui/test/mobileFormAccessibility.test.js`
- Test: `erp-ui/test/mobileOaFeatureCompleteness.test.js`
- Test: `erp-ui/test/mobileWorkbenchTaskRouting.test.js`

- [ ] **Step 1: Confirm the existing failures**

Run each listed test with `node test/<name>.test.js`. Expected failures include missing `resolveMobileListState`, `routeLoadGuard`, structured `validationError`, attendance record forwarding, and short-screen layout contracts.

- [ ] **Step 2: Restore the validated runtime**

Use `src/views/mobile/feature/index 3.vue` as the complete local reference. The restored active file must contain these contracts:

```vue
<button
  v-for="item in displayItems"
  :key="item.code"
  class="list-row"
  type="button"
  @click="openItem(item)"
>
```

```js
const { createMobileRouteLoadGuard } = require("./mobileRouteLoadGuard")
const { releaseAllMobileOverlays } = require("./components/mobileOverlayStack")
```

```scss
@media (max-width: 360px) and (max-height: 620px) {
  .feature-stage { padding-top: 12px; }
  .list-panel { margin-top: 6px; padding-top: 10px; }
}
```

- [ ] **Step 3: Re-run the focused tests**

Expected: every Task 1 test exits 0.

### Task 2: Restore route, context, profile, and production-data boundaries

**Files:**
- Modify: `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
- Test: `erp-ui/test/mobileAccessBoundary.test.js`
- Test: `erp-ui/test/mobileContextProfiles.test.js`
- Test: `erp-ui/test/mobileFeatureRouteCoverage.test.js`
- Test: `erp-ui/test/mobileProductionDataIsolation.test.js`
- Test: `erp-ui/test/mobileProfileMaintenance.test.js`
- Test: `erp-ui/test/mobileSystemFeatureCompleteness.test.js`

- [ ] **Step 1: Confirm the existing failures**

Expected: failures show lost `allowedDeptTypes`, production `seedItems`, and the generic `/mobile/profile` component.

- [ ] **Step 2: Restore the validated route definition**

Use `src/views/mobile/mobileRouteDefinitions 4.js` as the local reference. The active route must include:

```js
{
  path: '/mobile/profile',
  component: () => import('@/views/mobile/profile/index'),
  hidden: true,
  meta: { title: '手机个人资料' }
}
```

and must not contain any `seedItems` key.

- [ ] **Step 3: Re-run the focused tests**

Expected: every Task 2 test exits 0.

### Task 3: Repair stock-check and attendance action contracts

**Files:**
- Modify: `erp-ui/src/views/mobile/feature/featureActions.js`
- Modify only if required by the failing contract: `erp-ui/src/views/mobile/feature/featureActionRuntime.js`
- Test: `erp-ui/test/mobileStockCheckApproval.test.js`
- Test: `erp-ui/test/mobileOaFeatureCompleteness.test.js`

- [ ] **Step 1: Keep the current tests red**

Expected failures: stock checks expose an obsolete direct-confirm action; attendance checkout does not pass the current open record ID.

- [ ] **Step 2: Implement the minimal business correction**

Stock-check actions must expose submit/approve/reject/restart according to status and permission, never a direct `confirmStockCheck` shortcut. Attendance checkout must call the runtime with the selected open record:

```js
return runAction("checkOutAttendance", {
  recordId: currentOpenAttendance.recordId
})
```

- [ ] **Step 3: Re-run both tests**

Expected: both exit 0 without changing unrelated action behavior.

### Task 4: Add regression coverage for remaining visible gaps

**Files:**
- Modify: `erp-ui/test/mobileAccessibilityVisuals.test.js`
- Modify: `erp-ui/test/laborContractModule.test.js`
- Modify: `erp-ui/test/signPackageModule.test.js`
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Modify: `erp-ui/src/views/mobile/contract/index.vue`
- Modify: `erp-ui/src/views/mobile/signPackage/index.vue`

- [ ] **Step 1: Write failing assertions**

```js
assert.ok(
  !/@media \(max-width: 360px\)[\s\S]*?\.feature-actions\s*\{[^}]*grid-template-columns:\s*1fr/s.test(featureSource),
  "320px feature actions must not collapse to one column behind the fixed navigation"
)
```

```js
assert.ok(/\.back-button[\s\S]*?min-width:\s*44px/.test(source))
assert.ok(/\.refresh-button[\s\S]*?min-height:\s*44px/.test(source))
```

Run the three tests. Expected: fail on the current 38×36 / 34×34 controls or one-column rule.

- [ ] **Step 2: Apply the minimum layout and target changes**

```scss
@media (max-width: 360px) {
  .feature-actions { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

.search-row input { height: 44px; }

.back-button,
.refresh-button {
  min-width: 44px;
  min-height: 44px;
}
```

- [ ] **Step 3: Re-run the three tests**

Expected: all exit 0.

### Task 5: Verify production and native shells

**Files:**
- Verify: `erp-ui/capacitor.config.ts`
- Verify: `erp-ui/android/`
- Verify: `erp-ui/ios/`
- Verify: `erp-ui/scripts/verify-capacitor-sync.cjs`

- [ ] **Step 1: Run the non-copy mobile suite**

Run every `test/mobile*.test.js` whose filename has no numbered copy suffix. Expected: 38 passed, 0 failed.

- [ ] **Step 2: Run the production build**

Run `npm run build:prod`. Expected: secret scan and Vue production build exit 0.

- [ ] **Step 3: Run Capacitor verification**

Run `npm run app:verify`. Expected: tests and generated native configuration checks exit 0. If the repository-level duplicate-file gate still blocks, run its underlying non-copy tests plus `node scripts/verify-capacitor-sync.cjs` and report that exact pre-existing blocker.

- [ ] **Step 4: Build native targets**

Run Android debug assemble and an iOS simulator build. Expected: both builds exit 0. Boot the available iPhone simulator and launch the app; Android runtime is only claimed when an AVD/device exists.

### Task 6: Browser regression and audit handoff

**Files:**
- Create: `docs/mobile-full-audit-20260711.md`
- Create: `docs/superpowers/verification/2026-07-11-mobile-full-remediation.md`

- [ ] **Step 1: Repeat the route matrix**

Capture the same 320×568 and 390×844 routes and key forms. Expected: no horizontal overflow, no primary action covered by `.bottom-nav`, and no new console errors.

- [ ] **Step 2: Compare before and after evidence**

Use the original screenshots under `current-run/` and the new screenshots under `after/` at identical sizes. Record every fixed item and any device-only verification limit.

- [ ] **Step 3: Write the final audit and verification reports**

The reports must list all flow steps, severity, health, screenshots, automated test counts, build results, native results, and remaining external data cleanup.
