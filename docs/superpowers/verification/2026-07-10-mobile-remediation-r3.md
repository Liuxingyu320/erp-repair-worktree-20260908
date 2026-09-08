# Mobile Remediation R3 Verification

**Date:** 2026-07-10
**Branch:** `codex/mobile-remediation`
**R3 base:** `4896f34b`
**Verified code HEAD:** `60e02f27`
**Status:** R3 implementation and verification gates are complete. The full suite passes when the two user-owned local fixtures are supplied read-only; the tracked branch intentionally does not add those untracked files. Four low-severity Vue 2 dependency findings remain.

## Delivered scope

- First-screen hierarchy now prioritizes todo/list state and the first business record.
- Quick actions are capped at four primary entries; lower-frequency and excess entries remain available under “更多操作”.
- Mobile forms expose structured validation targets, one final primary action, a compact single-row footer, label associations, error summary focus and keyboard clearance.
- Custom sheets/dialogs share focus entry, Tab/Shift+Tab trapping, Escape handling, nested-dialog ownership and opener restoration.
- Login, registration and organization selection expose correct autocomplete, input names, captcha refresh semantics and 44px targets.
- Narrow layouts retain the existing glass design system while allowing business text to wrap and preventing horizontal overflow.
- Browser QA found and fixed one additional 320×568 regression: the fixed bottom navigation previously covered the list heading. The short-height media rule now keeps the heading immediately above the navigation.

## Automated verification

### Focused R3 and adjacent regression suite

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

Result: **11 run, 0 failed**.

### Production build

```bash
npm run build:prod
```

Result: **exit 0**. The frontend secret scan passed and the production bundle completed. The existing asset-size and entrypoint-size warnings remain; no build error was introduced.

### Production dependency audit

```bash
npm run audit:prod
```

Result: **exit 0**; **0 moderate, 0 high, 0 critical**. NPM reports four low-severity findings from the Vue 2 dependency chain for `GHSA-5j4c-8p2g-v4jx`. Its proposed `npm audit fix --force` would introduce a breaking dependency change, so it was not applied automatically.

### Full frontend suite

```bash
npm test
```

The tracked worktree alone reports **93 run, 2 failed**. Both failures are the unchanged, pre-existing missing-fixture baseline:

1. `desktopAuditFollowupFixes.test.js` cannot read the untracked `erp-modules/erp-file/src/main/resources/application-local.yml`.
2. `mobileAppShell.test.js` cannot find the generated/untracked Android shell, beginning with `erp-ui/android/settings.gradle`.

The original user workspace contains both fixtures. A second full-suite run exposed only the nine exact required files to the isolated worktree as temporary read-only symbolic links, without reading or printing the local configuration contents:

```bash
cd erp-ui
npm test
```

Result with those local fixtures present: **93 run, 0 failed**. An exit trap removed every temporary link immediately after the run; no fixture was copied, changed, staged or committed, and the worktree returned clean.

## In-app browser QA (complete matrix)

The existing in-app browser was used against the local Vue development server with preview-mode organization and seeded business data. No business mutation was submitted.

For states that the read-only preview route does not expose, the browser's local developer capability temporarily changed only the isolated tab's Vue state: permissions were granted in-memory, and list state was set to true-empty, filtered-empty, permission-error or network-error. A reload removed every injected value before the tab was finalized. No source file, local storage, credential, backend record or external system was changed.

### 390×844

- Sales first screen showed the list heading and both realistic records; the first record occupied y=597–686 while the fixed navigation began at y=766.
- Document and body scroll widths were 390px: no horizontal overflow.
- The store workbench todo title occupied y=126–151 and the network-failure recovery card y=161–298, well above the bottom navigation at y=774.
- Search input, explicit clear-search button and query controls were operable; clearing reset the native search input to an empty value.
- True-empty rendered “暂无销售待处理” with “刷新”; clicking it restored two seeded records. Filtered-empty rendered “没有匹配结果” with “清空搜索和筛选”; clicking it cleared the query and restored two records.
- Permission-error rendered “无法访问当前列表” with one enabled “切换组织” action. Network-error rendered “列表加载失败” with “重试”; clicking it restored two records.
- The authorized sales create form measured 53px for the complete sticky footer and 44px for the action row. Footer top to viewport bottom, including the sheet's bottom safe clearance, measured 82px—below the 112px budget.
- Empty “保存并提交” did not call the save API; it set `aria-invalid="true"` and moved focus to the first required control, “搜索并选择客户”.

### 320×568

- Before the final fix, the sales list heading ended below the navigation start and was visibly covered.
- After `60e02f27`, the list heading occupied y=465–490 and the navigation began at y=490, so the complete heading and count remain visible at initial scroll position.
- Document and body scroll widths were 320px on the feature page: no horizontal overflow.
- The workbench todo title occupied y=122–148 and its failure/retry state y=158–294, above the navigation at y=498.
- Touch targets inspected in the rendered page remained at least 44px; the captcha refresh button measured 92×48px.
- The authorized form retained one 44px action row with 58px / 94px / 94px button widths. Its footer remained 53px and its total bottom zone 82px; document and body widths stayed at 320px.
- Empty final submit again focused “搜索并选择客户” with `aria-invalid="true"` while the footer stayed at y=486–539, clear of the 568px viewport bottom.

### Flow and accessibility findings

- A five-action replenishment route rendered four primary actions plus “更多操作 1 项”; expanding it exposed the preserved fifth action “待收货”.
- Workbench no-context and network-failure states exposed “选择组织” and “重试” recovery actions. Populated, true-empty, filtered-empty, permission-error and network-error feature states all exposed their specified actions; refresh, clear-filter and retry recovery were exercised successfully.
- The authorized create form rendered one tertiary cancel action, one secondary draft action and one primary final action. Runtime measurements and invalid-focus behavior matched the automated form contracts.
- Opening the first sales record focused the dialog heading. With the dialog open, Tab from the final control returned to “关闭”, Shift+Tab returned to “切换上下文”, Escape closed the dialog, and focus returned to the exact originating sales record.
- The login captcha is a real named button and refreshed with Enter while retaining focus. Username/password/code rendered `username`, `current-password` and `one-time-code` autocomplete tokens.
- Console error log was empty. The intentionally submitted empty login validation emitted only Element UI `async-validator` warnings and made no request.

The final cross-task review found no code-level Critical or Important issue. Its one Important verification gap was the incomplete browser matrix; the follow-up browser run above closes that gap without treating automated source contracts as browser evidence.

## Commits and rollback

R3 code commits, in order:

- `4d46584c` — first-screen hierarchy, action overflow and actionable states
- `6ad7a044` — form accessibility and compact action hierarchy
- `6aa66f93` — dialog focus lifecycle
- `26f89970` — authentication and narrow-layout hardening
- `60e02f27` — 320×568 list/navigation overlap regression fix

Rollback is frontend-only: revert the R3 commits above (and the verification commit if desired). R3 adds no backend migration, API contract change or generated native shell file.
