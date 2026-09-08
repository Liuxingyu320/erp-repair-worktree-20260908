# Mobile Remediation R3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成手机端 R3 体验整改：把待办、筛选和首条业务数据提前到首屏；把快捷动作收敛到最多 4 个并提供“更多”；重做表单主次动作、错误定位与键盘避让；补齐弹层焦点闭环、认证页验证码语义、自动填充、触控尺寸和窄屏响应式。

**Architecture:** 用纯函数统一快捷动作分组和列表状态，页面只负责渲染与调用既有业务处理函数；用结构化表单校验结果保留旧字符串 API，同时把首个错误字段传给表单；用共享 `mobileDialogFocus` 管理弹层首焦点、Tab 循环、Escape 和关闭后焦点恢复；继续沿用现有玻璃卡片、颜色、字体和移动 overlay 栈，不新增视觉语言或伪资产。

**Tech Stack:** Vue 2.6、Vue Router 3、Element UI、Node assert tests、SCSS、现有移动 API/权限模型。

---

### Task 1: First-screen Hierarchy, Action Overflow and Actionable List States

**Files:**
- Create: `erp-ui/src/views/mobile/mobileExperience.js`
- Create: `erp-ui/test/mobileFirstScreenHierarchy.test.js`
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Modify: `erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue`
- Modify: `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
- Modify: `erp-ui/src/views/mobile/mobileNavigation.js`
- Modify: `erp-ui/test/mobileContextProfiles.test.js`
- Modify: `erp-ui/test/mobileFeatureRouteCoverage.test.js`

- [x] **Step 1: Write failing pure behavior and source contracts**

Test `partitionMobileActions(actions, 4)` with explicit `placement: "more"`, permission-filtered input, more than four primary actions, and order preservation. Test `resolveMobileListState()` for loading, permission error, network error, filtered empty and true empty, including an action identifier for every non-loading empty/error state.

Source assertions require:

- workbench todo DOM before context/hero and positive-count todos before zero-count rows;
- feature filters/search/list DOM before shortcut sections;
- `primaryFeatureActions`/`moreFeatureActions` and `primaryQuickActions`/`moreQuickActions`;
- no one-column shortcut stack at 320px;
- real list/todo buttons;
- retry, clear-filter and switch-context actions without duplicating route/action execution code.

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileFirstScreenHierarchy.test.js test/mobileContextProfiles.test.js test/mobileFeatureRouteCoverage.test.js
```

Expected: FAIL because the helper and primary/overflow/actionable-state rendering do not exist.

- [x] **Step 3: Implement deterministic action partitioning**

`partitionMobileActions` must:

- keep at most four primary actions;
- always move `placement: "more"` actions to overflow;
- preserve configured order within both groups;
- move excess primary actions into overflow;
- never remove permissions or alter route/action payloads.

Mark computed export plus return, history, configuration/reference and other low-frequency actions as `placement: "more"`. Keep the existing full action arrays and route coverage; only their presentation changes.

- [x] **Step 4: Reorder workbench and feature first screens**

Workbench order becomes header/notices → todo state/list → compact context/hero → quick actions → board. Feature order becomes header/notices → compact feature identity → key scope/status/search → list state/first row → shortcuts. Keep the existing design tokens, backgrounds and handlers.

For workbench summary:

- expose loading and failure instead of silently converting failure into zero counts;
- show positive-count todos first after a successful summary;
- show a true no-todo state when all relevant counts are zero;
- retry through the existing summary loader.

- [x] **Step 5: Add structured, actionable list states**

`resolveMobileListState` returns a stable kind, title, description and action id. The feature page maps actions to the existing `refresh`, clear filter/search, `goSelectShop`, create-form or workbench navigation methods. Loading/error messages use `aria-live`; permission is not rendered as no data.

- [x] **Step 6: Run and verify GREEN**

Run the Step 2 command. Expected: all three targeted files pass.

- [x] **Step 7: Commit**

```bash
git add erp-ui/src/views/mobile erp-ui/test/mobileFirstScreenHierarchy.test.js erp-ui/test/mobileContextProfiles.test.js erp-ui/test/mobileFeatureRouteCoverage.test.js
git commit -m "feat: prioritize mobile work and list states"
```

### Task 2: Form Action Hierarchy, Structured Validation and Error Focus

**Files:**
- Create: `erp-ui/test/mobileFormAccessibility.test.js`
- Modify: `erp-ui/src/views/mobile/feature/mobileValidation.js`
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileLineItemsEditor.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/mobileSheet.scss`
- Modify: `erp-ui/test/mobileFeatureSubmitModes.test.js`
- Modify: `erp-ui/test/mobileFormPayloads.test.js`

- [x] **Step 1: Write failing validation and form contracts**

Add tests for `getMobileFormValidationError(config, data)` returning:

```js
{
  message: "商品明细第1行数量必须大于0",
  fieldKey: "details",
  rowIndex: 0,
  itemFieldKey: "quantity"
}
```

Keep `validateMobileForm()` returning only the message for backward compatibility. Source tests require stable field IDs, real `label` associations or `aria-labelledby` groups, `aria-required`, `aria-invalid`, an assertive error summary, and a callable focus path to the first invalid native/custom field.

Submit-mode tests require:

- final `submit` action, or the final/only available mode, as the only `.primary` control;
- `save` as `.secondary` when final submit exists;
- cancel as `.tertiary`;
- Enter to use the primary final action;
- a single horizontal action row whose normal iPhone safe-area height remains below 112px.

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileFormAccessibility.test.js test/mobileFeatureSubmitModes.test.js test/mobileFormPayloads.test.js
```

Expected: FAIL because validation only returns a string and every submit mode is currently primary.

- [x] **Step 3: Add structured validation without breaking existing callers**

Export `getMobileFormValidationError`; make `validateMobileForm` a compatibility wrapper. Include field key, zero-based row index and item field key for line-item errors. In the feature parent store `validationError`, pass it into `MobileFormSheet`, clear stale validation state on edits, and preserve server errors without discarding the draft.

- [x] **Step 4: Associate labels and focus the first error**

Generate stable control IDs from field keys. Native controls use `label[for]`; entity picker, line items and image upload use named groups and forward accessible names where appropriate. Required-when fields must expose their current required state. The error summary sits above the action row, uses `role="alert"`/`aria-live="assertive"`, and a summary action plus automatic validation handling focuses the first invalid control or the summary fallback.

- [x] **Step 5: Rebuild the footer and keyboard clearance**

Render one row: weak cancel, optional secondary draft, one primary final action. Move error summary outside the measured footer. Use 44–48px targets, safe-area padding owned by the footer, reduced body/scroll padding, `scroll-margin-bottom`, and `visualViewport` resize/scroll handling to re-scroll the active field after keyboard animation.

- [x] **Step 6: Run and verify GREEN**

Run the Step 2 command. Expected: all three targeted files pass.

- [x] **Step 7: Commit**

```bash
git add erp-ui/src/views/mobile/feature erp-ui/test/mobileFormAccessibility.test.js erp-ui/test/mobileFeatureSubmitModes.test.js erp-ui/test/mobileFormPayloads.test.js
git commit -m "feat: make mobile forms accessible and compact"
```

### Task 3: Dialog Focus Lifecycle, Live Regions and Touch Semantics

**Files:**
- Create: `erp-ui/src/views/mobile/feature/components/mobileDialogFocus.js`
- Create: `erp-ui/test/mobileDialogFocus.test.js`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileFormSheet.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileDetailSheet.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileActionDialog.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileConfirmDialog.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileQuickCustomerForm.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileLineItemsEditor.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/mobileSheet.scss`
- Modify: `erp-ui/test/mobileFeatureComponentSplit.test.js`
- Modify: `erp-ui/test/mobileQuickCustomer.test.js`

- [x] **Step 1: Write failing fake-DOM behavior tests**

Test a shared manager with a fake container/document:

- activation captures the connected trigger and focuses the requested first control;
- Tab on the last focusable element wraps to the first;
- Shift+Tab on the first wraps to the last;
- Escape invokes the supplied close callback;
- deactivation removes listeners and restores the connected trigger;
- nested managers only handle the topmost active dialog.

Source contracts require all listed custom dialogs/sheets to activate and release the manager through their existing open watchers and overlay locks.

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileDialogFocus.test.js test/mobileFeatureComponentSplit.test.js test/mobileQuickCustomer.test.js
```

Expected: FAIL because the shared focus manager does not exist.

- [x] **Step 3: Implement and integrate the shared focus manager**

The helper must not own body scroll classes; it composes with `mobileOverlayStack`. Each sheet/dialog keeps its existing close/cancel behavior, focuses its heading or first meaningful field after `nextTick`, traps Tab only while topmost, handles Escape when safe, and restores the opener if it is still connected. Nested quick-customer, stock-picker and confirm dialogs must return to their invoking control.

- [x] **Step 4: Add live-region and input names**

Loading/success messages use polite status regions; validation/request errors use alert/assertive regions. Add labels to action quantity inputs and stock-picker search. Replace pointer-only stock option cards with keyboard-operable, non-nested-control structure. Raise close, selection, deletion and quantity targets to at least 44px.

- [x] **Step 5: Run and verify GREEN**

Run the Step 2 command. Expected: all three targeted files pass.

- [x] **Step 6: Commit**

```bash
git add erp-ui/src/views/mobile/feature/components erp-ui/test/mobileDialogFocus.test.js erp-ui/test/mobileFeatureComponentSplit.test.js erp-ui/test/mobileQuickCustomer.test.js
git commit -m "feat: close the mobile dialog focus loop"
```

### Task 4: Authentication Semantics and Narrow-screen Visual Hardening

**Files:**
- Create: `erp-ui/test/mobileAccessibilityVisuals.test.js`
- Modify: `erp-ui/src/views/login.vue`
- Modify: `erp-ui/src/views/register.vue`
- Modify: `erp-ui/src/views/select-shop/index.vue`
- Modify: `erp-ui/src/views/mobile/feature/index.vue`
- Modify: `erp-ui/src/views/mobile/components/MobileWorkbenchShell.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/mobileSheet.scss`
- Modify: `erp-ui/test/mobileAuthEntryPages.test.js`

- [x] **Step 1: Write failing authentication and responsive source contracts**

Require username fields to use `autocomplete="username"`, login passwords `current-password`, registration passwords `new-password`, and verification codes a one-time-code hint. Both login and registration captcha images must live inside real 44px buttons, have a refresh accessible name and meaningful image `alt` text. Shop search and icon-only refresh controls require accessible names.

Responsive contracts require:

- registration width constrained by the viewport, `100dvh` and safe-area padding;
- no critical 11–12px body copy;
- key list/hero/context text can wrap instead of forced ellipsis;
- all compact scope/search/close controls at least 44px;
- 320px/390px grids avoid overlap and horizontal overflow.

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileAccessibilityVisuals.test.js test/mobileAuthEntryPages.test.js
```

Expected: FAIL because authentication inputs disable autocomplete and captcha refresh uses clickable `div`/`img` elements.

- [x] **Step 3: Fix auth and organization-selection semantics**

Use the existing Element UI inputs and visual styles. Add accessible input names and correct autocomplete tokens. Replace captcha click wrappers with `button type="button"`; keep the same `getCode` handler and API behavior. Add names to search and icon-only refresh controls without changing their routing or data logic.

- [x] **Step 4: Harden narrow-screen typography and controls**

Adjust only existing selectors/tokens: darken secondary text, use 13–14px readable body copy, allow key business text to wrap, make registration responsive, and raise undersized controls to 44px. Preserve the existing glass visual system, backgrounds and safe-area bottom navigation.

- [x] **Step 5: Run and verify GREEN**

Run the Step 2 command. Expected: both targeted files pass.

- [x] **Step 6: Commit**

```bash
git add erp-ui/src/views/login.vue erp-ui/src/views/register.vue erp-ui/src/views/select-shop/index.vue erp-ui/src/views/mobile erp-ui/test/mobileAccessibilityVisuals.test.js erp-ui/test/mobileAuthEntryPages.test.js
git commit -m "fix: harden mobile accessibility and narrow layouts"
```

### Task 5: Integrated Verification, Browser QA and R3 Evidence

**Files:**
- Create: `docs/superpowers/verification/2026-07-10-mobile-remediation-r3.md`
- Modify: plan checkboxes in this file

- [x] **Step 1: Run focused R3 and adjacent regression tests**

```bash
cd erp-ui
node scripts/run-node-tests.cjs \
  test/mobileFirstScreenHierarchy.test.js \
  test/mobileFormAccessibility.test.js \
  test/mobileDialogFocus.test.js \
  test/mobileAccessibilityVisuals.test.js \
  test/mobileContextProfiles.test.js \
  test/mobileFeatureComponentSplit.test.js \
  test/mobileFeatureSubmitModes.test.js \
  test/mobileQuickCustomer.test.js \
  test/mobileAuthEntryPages.test.js \
  test/mobileOverlayStack.test.js \
  test/mobileFeatureRouteCoverage.test.js
```

- [x] **Step 2: Build and audit production dependencies**

```bash
npm run build:prod
npm run audit:prod
```

Build must exit 0. Production audit must contain no moderate, high or critical vulnerabilities.

- [x] **Step 3: Run the full frontend suite and classify only known fixtures**

```bash
npm test
```

Do not fabricate missing untracked fixtures. If the two pre-existing fixture failures remain unchanged, document them explicitly:

- missing `erp-modules/erp-file/src/main/resources/application-local.yml` in `desktopAuditFollowupFixes.test.js`;
- missing generated Android shell files such as `erp-ui/android/settings.gradle` in `mobileAppShell.test.js`.

Any other failure belongs to R3 and must be fixed before completion.

- [x] **Step 4: Browser QA at both target viewports**

Using the in-app browser, verify 390×844 and 320×568 with realistic mock data and no writes:

- workbench first todo/list state is visible;
- feature page list title/state is visible at 320 and first record/clear empty state at 390;
- “更多” preserves overflow actions;
- populated, true-empty, filtered-empty, permission and network-failure states have the correct recovery action;
- form footer computed height including safe area is at most 112px;
- invalid submit focuses the first error; Tab loops; close restores the opener;
- captcha refresh is keyboard operable;
- no horizontal scroll, overlap or console errors.

Compare the rendered result against the existing page at identical viewports and record visible regressions/fixes in the verification note.

- [x] **Step 5: Record evidence and commit**

Include exact commands, pass/fail counts, audit result, viewport findings, console result, known baseline failures and rollback scope.

```bash
git add docs/superpowers/plans/2026-07-10-mobile-remediation-r3.md docs/superpowers/verification/2026-07-10-mobile-remediation-r3.md
git commit -m "docs: record mobile remediation r3 verification"
```

## Review Gates

After every implementation task:

1. Inspect the diff and rerun the task's focused tests independently.
2. Run a specification-compliance review against this task and `docs/superpowers/specs/2026-07-10-mobile-remediation-design.md` §8.
3. Run a separate code-quality review only after specification compliance passes.
4. Resolve all critical and important findings, rerun tests, and re-review before starting the next task.

After Task 5, run one final cross-task review from the commit before R3 through the current HEAD.
