# HR Onboarding Reference Fidelity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `/hr/onboarding` faithfully match the supplied desktop reference while preserving all existing backend-driven behavior and privacy controls.

**Architecture:** Keep `index.vue` as the data/orchestration owner, `HrOnboardingListPane.vue` as the server-filtered master list, and `HrOnboardingDetailPane.vue` as the selected-record presentation. Add only small pure mapping helpers to the existing onboarding field-config module when visual state derivation needs executable tests.

**Tech Stack:** Vue 2, Element UI, SCSS, existing HR onboarding REST APIs, Node contract tests, Codex in-app browser.

---

### Task 1: Lock the reference layout and default-selection behavior

**Files:**
- Modify: `erp-ui/test/hrOnboardingWorkbench.test.js`
- Modify: `erp-ui/src/views/hr/onboarding/index.vue`

- [ ] **Step 1: Write failing executable and source-contract tests**

Add assertions that require `autoSelectFirstRow`, a compact title action group, reference-layout class names, and selection of the first list record when there is no deep link:

```js
assert.ok(index.includes("hr-onboarding-heading__actions"))
assert.ok(index.includes("autoSelectFirstRow"))
assert.ok(index.includes("hr-onboarding-workbench--reference"))

const rows = [{ onboardingId: 41 }, { onboardingId: 42 }]
const target = bind(loadIndex({
  listHrOnboarding: () => Promise.resolve({ rows, total: 2 }),
  getHrOnboarding: id => Promise.resolve({ data: { onboardingId: id } }),
  getHrOnboardingFormOptions: () => Promise.resolve({ data: {} }),
  checkPermi: () => true
}))
await target.loadList()
assert.strictEqual(target.selectedOnboardingId, 41)
```

- [ ] **Step 2: Run the test and confirm RED**

Run:

```bash
node erp-ui/test/hrOnboardingWorkbench.test.js
```

Expected: fail because the reference classes and `autoSelectFirstRow` do not exist.

- [ ] **Step 3: Implement orchestration and page shell**

In `index.vue`, group the Import/Create buttons under `hr-onboarding-heading__actions`, remove the explanatory subtitle, add the reference modifier class to the workbench, and after list loading resolve selection in this order:

```js
if (routeRow) return this.selectOnboarding(routeRow)
if (this.canSelect && this.rows.length && !this.selectedOnboardingId) {
  return this.autoSelectFirstRow()
}
```

`autoSelectFirstRow()` must call `selectOnboarding(this.rows[0])`, never fetch raw data directly.

- [ ] **Step 4: Run the test and confirm GREEN**

Run `node erp-ui/test/hrOnboardingWorkbench.test.js`.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/hr/onboarding/index.vue erp-ui/test/hrOnboardingWorkbench.test.js
git commit -m "feat: align hr onboarding workbench shell"
```

### Task 2: Rebuild the master list to match the reference

**Files:**
- Modify: `erp-ui/src/views/hr/onboarding/components/HrOnboardingListPane.vue`
- Modify: `erp-ui/test/hrOnboardingWorkbench.test.js`

- [ ] **Step 1: Write failing list visual-contract tests**

Require status count badges, a one-line search row, popover filters, visible result count, card arrow, fixed pagination footer, and the reference labels:

```js
for (const token of [
  "status-tab-label", "status-count", "filter-trigger", "result-count",
  "onboarding-row__arrow", "onboarding-list-footer", "待补资料", "待到岗", "已入职", "已取消"
]) assert.ok(list.includes(token), `missing reference list token: ${token}`)
```

- [ ] **Step 2: Run the test and confirm RED**

Run `node erp-ui/test/hrOnboardingWorkbench.test.js`.

- [ ] **Step 3: Implement the compact list**

- Render custom tab labels with count badges.
- Keep only keyword input and filter icon in the main row.
- Move date/organization/store/category/owner fields into `el-popover` and retain the existing `submitFilters()` payload.
- Render `共 {{ total }} 条`, compact cards, selected border/background, and an `el-icon-arrow-right`.
- Keep list loading/error/empty states and the permission alert functional.
- Pin pagination to the bottom and keep `pageSize` selectable.

- [ ] **Step 4: Run the test and confirm GREEN**

Run `node erp-ui/test/hrOnboardingWorkbench.test.js`.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/hr/onboarding/components/HrOnboardingListPane.vue erp-ui/test/hrOnboardingWorkbench.test.js
git commit -m "feat: recreate hr onboarding master list"
```

### Task 3: Rebuild the detail pane and action footer

**Files:**
- Modify: `erp-ui/src/views/hr/onboarding/components/HrOnboardingDetailPane.vue`
- Modify: `erp-ui/src/views/hr/onboarding/onboardingFieldConfig.js`
- Modify: `erp-ui/test/hrOnboardingWorkbench.test.js`

- [ ] **Step 1: Write failing detail and pure-state tests**

Require the identity hero, three facts, five-step progress, grouped missing rows, missing badges, and primary confirmation footer. Add a pure `onboardingStage(status)` test:

```js
assert.strictEqual(config.onboardingStage("DRAFT"), 2)
assert.strictEqual(config.onboardingStage("READY"), 3)
assert.strictEqual(config.onboardingStage("CONFIRMED"), 5)
assert.strictEqual(config.onboardingStage("CANCELLED"), 1)
for (const token of [
  "detail-identity", "detail-facts", "onboarding-stage-track",
  "missing-material-row", "missing-badge", "primary-confirm-action"
]) assert.ok(detail.includes(token), `missing reference detail token: ${token}`)
```

- [ ] **Step 2: Run the test and confirm RED**

Run `node erp-ui/test/hrOnboardingWorkbench.test.js`.

- [ ] **Step 3: Implement the reference detail hierarchy**

- Render a circular initials avatar using text and existing UI styling; use Element UI icons for phone, mail, ID, date, organization, post and material groups.
- Use only `phoneNumberMasked`, `idNumberMasked` and safe detail fields.
- Render percentage and five-step track in one card using `onboardingStage(status)`.
- Flatten existing backend missing-field groups into visual material rows without dropping unknown fields.
- Preserve risk and timeline sections below the reference-first content.
- Render all allowed secondary actions and a distinct `CONFIRM` button on the right; disabled when the service does not allow confirmation.

- [ ] **Step 4: Run targeted tests and production build**

```bash
npm --prefix erp-ui run test -- hrOnboardingWorkbench.test.js hrOnboardingApi.test.js hrPersonnelRoutes.test.js mobileAccessBoundary.test.js
npm --prefix erp-ui run build:prod
```

Expected: all selected tests pass and the production build completes.

- [ ] **Step 5: Commit**

```bash
git add erp-ui/src/views/hr/onboarding/components/HrOnboardingDetailPane.vue \
  erp-ui/src/views/hr/onboarding/onboardingFieldConfig.js \
  erp-ui/test/hrOnboardingWorkbench.test.js
git commit -m "feat: recreate hr onboarding detail workspace"
```

### Task 4: Visual QA against the supplied screenshot

**Files:**
- Create: `design-qa.md`
- Create: `docs/audit-screenshots/hr-onboarding-reference-20260712/*.png` (QA evidence, do not stage)

- [ ] **Step 1: Run the existing local UI and services**

Use the current worktree UI on port 1025 and the existing gateway/system services. Confirm `/hr/onboarding` loads through a real signed-in session.

- [ ] **Step 2: Capture equivalent states**

Capture the selected-record state at 1488×1058, then 1440×1024, 1366×768 and 1280×720. Also capture empty, loading and error states.

- [ ] **Step 3: Compare reference and implementation**

Open the supplied reference and the 1488×1058 implementation capture together. Record P0/P1/P2/P3 differences in `design-qa.md` for layout ratio, first-screen content, typography, border/radius, spacing, selected card, progress track, grouped missing data and fixed action placement.

- [ ] **Step 4: Fix all P0/P1/P2 findings and repeat**

Repeat capture and comparison until `design-qa.md` contains:

```text
final result: passed
```

- [ ] **Step 5: Run final verification and commit tracked QA report**

```bash
npm --prefix erp-ui run test -- hrOnboardingWorkbench.test.js hrOnboardingApi.test.js hrPersonnelRoutes.test.js mobileAccessBoundary.test.js
npm --prefix erp-ui run build:prod
git diff --check
git add design-qa.md
git commit -m "test: verify hr onboarding reference fidelity"
```
