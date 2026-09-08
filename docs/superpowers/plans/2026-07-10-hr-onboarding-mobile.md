# HR Onboarding Mobile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an HR mobile workbench, onboarding queue, onboarding detail/actions, and a resilient four-step mobile form using the same backend rules as desktop.

**Architecture:** Mobile HR is a dedicated page group, not another configuration of the generic `mobile/feature/index.vue`. It reuses `src/api/hr/onboarding.js`, adds route-aware permission matching and context-independent HR navigation, and never calls the sensitive reveal API.

**Tech Stack:** Vue 2.6, existing mobile glass-surface design system, Vue Router 3, Capacitor 8, Node source-contract tests, Android Gradle, Xcode.

---

## Prerequisites

- Complete the backend plan and desktop Task 1 API wrapper first.
- Keep the existing store/warehouse navigation behavior for non-HR users.
- The standard frontend runner is currently blocked by existing `* 2.js` duplicates; use direct Node tests until the user approves their resolution.
- Use the bundled Node 24 runtime for npm/build commands.

## File structure

Create:

- `erp-ui/src/views/mobile/hr/index.vue` — HR mobile home and today's tasks.
- `erp-ui/src/views/mobile/hr/onboarding/index.vue` — all onboarding queue.
- `erp-ui/src/views/mobile/hr/onboarding/detail.vue` — one onboarding record and actions.
- `erp-ui/src/views/mobile/hr/onboarding/form.vue` — create/edit route orchestration.
- `erp-ui/src/views/mobile/hr/components/MobileHrShell.vue` — mobile page shell, title/back, safe area, role-adapted bottom navigation.
- `erp-ui/src/views/mobile/hr/components/MobileOnboardingList.vue` — reusable continuous task list.
- `erp-ui/src/views/mobile/hr/components/MobileOnboardingStepForm.vue` — pure four-step form component.
- `erp-ui/src/views/mobile/hr/mobileOnboardingForm.js` — field groups, payload normalization, error-to-step mapping.
- `erp-ui/test/mobileHrOnboarding.test.js`.
- `erp-ui/test/mobileHrOnboardingForm.test.js`.
- `scripts/mobile-hr-onboarding-qa.cjs`.
- `erp-ui/src/utils/apiBaseUrl.js` — pure native/web API-base resolver.

Modify:

- `erp-ui/src/views/mobile/mobileRouteDefinitions.js`.
- `erp-ui/src/views/mobile/mobileNavigation.js`.
- `erp-ui/src/permission.js`.
- `erp-ui/src/utils/request.js`.
- `erp-ui/test/mobileAccessBoundary.test.js`.
- `erp-ui/test/mobileFeatureRouteCoverage.test.js`.
- `erp-ui/test/mobileContextProfiles.test.js`.
- `erp-ui/test/mobileAccessibilityVisuals.test.js`.

## Task 1: Dynamic HR routes and access policy

**Files:**
- Modify: `mobileRouteDefinitions.js`
- Modify: `mobileNavigation.js`
- Modify: `erp-ui/src/permission.js`
- Modify: `erp-ui/test/mobileAccessBoundary.test.js`
- Modify: `erp-ui/test/mobileFeatureRouteCoverage.test.js`

- [ ] **Step 1: Write failing route/access tests**

Add assertions for:

```js
assert.strictEqual(getMobileRouteFeature("/mobile/hr").featureKey, "hrWorkbench")
assert.strictEqual(getMobileRouteFeature("/mobile/hr/onboarding").featureKey, "hrOnboardingList")
assert.strictEqual(getMobileRouteFeature("/mobile/hr/onboarding/123").featureKey, "hrOnboardingDetail")
assert.strictEqual(getMobileRouteFeature("/mobile/hr/onboarding/123/edit").featureKey, "hrOnboardingEdit")
assert.strictEqual(getMobileRouteAccessDecision("/mobile/hr", "", ["hr:onboarding:workbench"]).path, "")
assert.strictEqual(getMobileHomePath("", ["hr:onboarding:workbench"]), "/mobile/hr")
assert.deepStrictEqual(getMobileRouteRequiredPermissions("/mobile/hr/onboarding/123"), ["hr:onboarding:query"])
assert.strictEqual(isMobileRouteAllowedForPermissions("/mobile/hr", []), false)
assert.strictEqual(isMobileRouteAllowedForPermissions("/mobile/hr/onboarding", []), false)
assert.strictEqual(isMobileRouteAllowedForPermissions("/mobile/hr/onboarding/123", []), false)
assert.strictEqual(isMobileRouteAllowedForPermissions("/mobile/hr/onboarding/123/edit", []), false)
```

Also assert list-only supervisors keep the store/warehouse home and receive a visible onboarding quick action.

- [ ] **Step 2: Run and verify failure**

```bash
node erp-ui/test/mobileAccessBoundary.test.js
node erp-ui/test/mobileFeatureRouteCoverage.test.js
```

Expected: FAIL because HR routes and dynamic matching do not exist.

- [ ] **Step 3: Register five routes**

Add:

```js
{
  path: "/mobile/hr",
  component: () => import("@/views/mobile/hr/index"),
  hidden: true,
  meta: { title: "人事工作台", mobileFeature: { featureKey: "hrWorkbench", permissions: ["hr:onboarding:workbench"], requiresBusinessContext: false } }
},
{
  path: "/mobile/hr/onboarding",
  component: () => import("@/views/mobile/hr/onboarding/index"),
  hidden: true,
  meta: { title: "入职管理", mobileFeature: { featureKey: "hrOnboardingList", permissions: ["hr:onboarding:list"], requiresBusinessContext: false } }
},
{
  path: "/mobile/hr/onboarding/create",
  component: () => import("@/views/mobile/hr/onboarding/form"),
  hidden: true,
  meta: { title: "新建入职", mobileFeature: { featureKey: "hrOnboardingAdd", permissions: ["hr:onboarding:add"], requiresBusinessContext: false } }
},
{
  path: "/mobile/hr/onboarding/:id(\\d+)",
  routeMatcher: /^\/mobile\/hr\/onboarding\/\d+$/,
  component: () => import("@/views/mobile/hr/onboarding/detail"),
  hidden: true,
  meta: { title: "入职详情", mobileFeature: { featureKey: "hrOnboardingDetail", permissions: ["hr:onboarding:query"], requiresBusinessContext: false } }
},
{
  path: "/mobile/hr/onboarding/:id(\\d+)/edit",
  routeMatcher: /^\/mobile\/hr\/onboarding\/\d+\/edit$/,
  component: () => import("@/views/mobile/hr/onboarding/form"),
  hidden: true,
  meta: { title: "补充资料", mobileFeature: { featureKey: "hrOnboardingEdit", permissions: ["hr:onboarding:edit"], requiresBusinessContext: false } }
}
```

- [ ] **Step 4: Fix route matching and context selection**

Use one matcher everywhere:

```js
function getMobileRouteDefinition(path) {
  const normalized = normalizeMobilePath(path)
  return mobileRouteDefinitions.find(item =>
    normalizeMobilePath(item.path) === normalized ||
    (item.routeMatcher && item.routeMatcher.test(normalized))
  ) || null
}
```

Make `getMobileRouteFeature`, `getMobileRouteRequiredPermissions`, `isMobileContextOptionalPath`, and `getMobileRouteAccessDecision` call this function. Update `permission.js.shouldSelectShop()` so `requiresBusinessContext === false` bypasses store selection.

`getMobileRouteRequiredPermissions()` must return `definition.meta.mobileFeature.permissions` when present, falling back to `PERMISSIONS[featureKey]` only for legacy routes. An unknown non-self-service feature with no permission mapping is denied, never treated as public. Add all five HR keys to the route coverage test.

- [ ] **Step 5: Add permission-aware mobile home/navigation**

Change `getMobileHomePath(deptType, permissions)` to return `/mobile/hr` when permissions contain `hr:onboarding:workbench`. Update every caller in `permission.js` and navigation helpers to pass `store.getters.permissions`. HR bottom navigation is `工作台 / 人事 / 待办 / 我的`. Users with only `hr:onboarding:list` retain their existing business bottom navigation and receive an `入职任务` quick action; detail navigation remains separately guarded by `hr:onboarding:query`.

Never switch by role name.

- [ ] **Step 6: Run tests and commit**

Run the two direct tests. Expected: PASS.

Commit with `git commit -m "feat: add mobile hr route access"`.

## Task 2: Shared mobile shell and list primitives

**Files:**
- Create: `MobileHrShell.vue`
- Create: `MobileOnboardingList.vue`
- Create: `erp-ui/test/mobileHrOnboarding.test.js`

- [ ] **Step 1: Write the failing shell/list test**

```js
assert.ok(shell.includes("env(safe-area-inset-bottom)"))
assert.ok(shell.includes("max-width: 430px"))
assert.ok(list.includes("expectedEntryDate"))
assert.ok(list.includes("missingOnboardingFields"))
assert.ok(list.includes("phoneNumberMasked"))
assert.ok(!list.includes("idNumber"))
assert.ok(!list.includes("bankAccount"))
```

- [ ] **Step 2: Run and verify failure**

```bash
node erp-ui/test/mobileHrOnboarding.test.js
```

Expected: FAIL because components are missing.

- [ ] **Step 3: Implement the mobile shell**

Match the existing mobile palette and rounded surfaces. Expose `title`, `back`, `loading`, and `error` props plus header/default/footer slots. Use the permission-aware navigation from `mobileNavigation.js`; do not duplicate nav definitions in the component.

- [ ] **Step 4: Implement the continuous list**

Accept `items`, `selectedStatus`, `loading`, and `hasMore`; emit `select`, `load-more`, and `refresh`. Each row shows name, masked phone, target role/organization, expected entry date, status, and missing count. Use separators and restrained surfaces instead of one heavy card per row.

- [ ] **Step 5: Run and commit**

Run `mobileHrOnboarding.test.js`. Expected: PASS.

Commit with `git commit -m "feat: add mobile hr shell and list"`.

## Task 3: HR mobile workbench and all-onboarding queue

**Files:**
- Create: `erp-ui/src/views/mobile/hr/index.vue`
- Create: `erp-ui/src/views/mobile/hr/onboarding/index.vue`
- Modify: `mobileHrOnboarding.test.js`

- [ ] **Step 1: Add failing page-contract assertions**

Assert workbench uses only `getHrOnboardingSummary`, renders `todayArrivalCount`, `pendingConfirmCount`, `todayTasks`, and permission-guards links to all/create. Assert each task row renders the backend-provided current action. Assert queue uses four server states; sends `keyword`, expected-date range, `targetDeptId`, `targetStoreId`, `employeeCategory`, and `ownerUserId`; paginates/refreshes; and preserves filters when returning from detail.

- [ ] **Step 2: Implement the workbench**

Load `/summary` once; it already returns scoped masked `todayTasks` under `hr:onboarding:workbench`, so this page does not call the list API. Show only `今日到岗`, `待确认`, and `今日待办`; no charts or unrelated HR metrics. Each task shows its current allowed operation. `新建入职` is primary only with add permission; `全部入职` is secondary only with list permission.

- [ ] **Step 3: Implement the queue**

Use `DRAFT`, `READY`, `CONFIRMED`, and `CANCELLED` tabs. Debounce keyword search across name/phone/post/organization. The touch-friendly filter sheet includes expected-date range, organization/store, employee category, and owner, and passes every filter to the backend. Restore status, filters, query, and scroll position from route query/session memory, but never store PII records in local storage.

- [ ] **Step 4: Run and commit**

Run `mobileHrOnboarding.test.js`. Expected: PASS.

Commit with `git commit -m "feat: add mobile hr workbench"`.

## Task 4: Onboarding detail and state actions

**Files:**
- Create: `erp-ui/src/views/mobile/hr/onboarding/detail.vue`
- Modify: `mobileHrOnboarding.test.js`

- [ ] **Step 1: Add failing detail/action assertions**

Assert detail shows two completeness values, per-group missing counts/fields, masked data, operation log, account risk, and reads `allowedActions`. Cover `MARK_READY`, `RETURN_TO_DRAFT`, `CONFIRM`, `CANCEL`, and `RESTORE` request paths separately. Assert every bind candidate renders `name`, `phoneNumberMasked`, and `departmentLabel` before selection. Assert incomplete records do not render an enabled confirm button, cancel requires a reason, and every write sends the current `version`.

- [ ] **Step 2: Implement detail loading**

Load by numeric route ID, reject missing/invalid IDs, and render loading/error/retry states. Display only masked DTO values. Do not import or call the desktop sensitive reveal API.

- [ ] **Step 3: Implement action footer**

For DRAFT, primary is `补充资料`; for READY and allowed confirm, primary is `确认入职`; secondary actions are shown in an action sheet. Guard each action with both permission and `allowedActions`.

Map `EDIT → hr:onboarding:edit`, `MARK_READY → hr:onboarding:ready`, `RETURN_TO_DRAFT → hr:onboarding:return`, `CONFIRM → hr:onboarding:confirm`, `CANCEL → hr:onboarding:cancel`, and `RESTORE → hr:onboarding:restore`; ignore unknown values.

Confirm first loads the masked conflict preview and shows each candidate's name, masked phone, and department label before selection; raw phone, ID number, bank account, and detailed address are forbidden. It prefills an import-approved preferred candidate only when still eligible, then sends actual date, selected decision/candidate, version, and a fresh idempotency key. Show a one-time password in a modal exactly once when returned, without copying it automatically or persisting it. On an idempotent replay with no password, show the linked-account result and direct the HR user to the normal reset-password flow instead of implying the password can be recovered.

- [ ] **Step 4: Run and commit**

Run `mobileHrOnboarding.test.js`. Expected: PASS.

Commit with `git commit -m "feat: add mobile hr onboarding detail"`.

## Task 5: Four-step create/edit form

**Files:**
- Create: `erp-ui/src/views/mobile/hr/onboarding/form.vue`
- Create: `MobileOnboardingStepForm.vue`
- Create: `mobileOnboardingForm.js`
- Create: `erp-ui/test/mobileHrOnboardingForm.test.js`

- [ ] **Step 1: Write failing form tests**

```js
assert.deepStrictEqual(STEP_KEYS, ["basic", "organization", "identityContact", "employment"])
assert.strictEqual(fieldStep("idNumber"), "identityContact")
assert.strictEqual(fieldStep("contractType"), "employment")
assert.strictEqual(fieldStep("targetPostId"), "organization")
assert.strictEqual(fieldControl("expectedEntryDate"), "date-picker")
assert.strictEqual(fieldControl("directSupervisorUserId"), "option-sheet")
assert.strictEqual(normalizePayload({ version: "0" }).version, 0)
```

Also assert backend field errors activate the correct step, each step shows its backend missing count, failed requests retain the model, edit always sends `version`, and untouched masked sensitive fields are omitted from the payload.

- [ ] **Step 2: Run and verify failure**

```bash
node erp-ui/test/mobileHrOnboardingForm.test.js
```

Expected: FAIL because the form module is missing.

- [ ] **Step 3: Implement field configuration and pure form component**

The four steps are `基础信息`, `组织岗位`, `身份与紧急联系人`, and `用工合同与社保`. The module owns labels, controls, option keys, payload normalization, and error-to-step mapping only. Dates, organization, post, supervisor, and dictionary fields use touch-friendly picker/sheet controls rather than free-text inputs. Derived labels are read-only and payloads contain only source IDs. Required/state rules remain server-owned.

`MobileOnboardingStepForm.vue` receives `model`, `step`, `errors`, `missing-counts`, and `options`; it emits `input`, `sensitive-dirty`, `back`, `next`, and `save`. It never calls APIs.

- [ ] **Step 4: Implement create/edit route orchestration**

Create mode starts with the seven minimal fields. Edit mode loads masked detail and form options, keeps sensitive-field dirtiness separate, always submits `version`, omits untouched phone/ID/bank/address properties, and locally rejects masking placeholders. Preserve the in-memory model after timeout/network failure and expose retry. Show field errors inline and focus the first error. First phase has no offline submission or background sync.

- [ ] **Step 5: Add mobile accessibility behavior**

Inputs and buttons are at least 44×44 CSS pixels. The sticky footer accounts for keyboard and safe-area insets. Each field has a real label, error text is associated with the control, and step changes move focus to the step heading.

- [ ] **Step 6: Run and commit**

Run the form and page tests. Expected: PASS.

Commit with `git commit -m "feat: add mobile hr onboarding form"`.

## Task 6: Mobile regression, native build, and real API smoke

**Files:**
- Create: `scripts/mobile-hr-onboarding-qa.cjs`
- Create: `erp-ui/src/utils/apiBaseUrl.js`
- Modify: `erp-ui/src/utils/request.js`
- Modify: mobile regression tests listed in the file structure

- [ ] **Step 1: Extend access and visual contracts**

Add regression assertions for HR home, supervisor shortcut, no-permission direct navigation, dynamic IDs, no shop redirect for HR, and safe-area CSS. Mobile form source may contain field identifiers, but rendered detail/list fixtures and snapshots must contain only `*Masked` values or `已填写`—never full ID, bank, or detailed-address values.

- [ ] **Step 2: Run mobile tests directly**

```bash
for test in \
  mobileHrOnboarding.test.js \
  mobileHrOnboardingForm.test.js \
  mobileAccessBoundary.test.js \
  mobileFeatureRouteCoverage.test.js \
  mobileContextProfiles.test.js \
  mobileAccessibilityVisuals.test.js \
  mobileAppShell.test.js \
  mobileNativeProductionHardening.test.js; do
  node "erp-ui/test/$test" || exit 1
done
```

Expected: PASS.

- [ ] **Step 3: Resolve the standard-runner prerequisite**

Run `find erp-ui/test -maxdepth 1 -name '* 2.js' -print`. If it prints files, stop and ask the user whether those pre-existing duplicate copies should be moved outside `erp-ui/test` or deliberately retained; do not delete them without approval. After the duplicates are resolved, run:

```bash
npm --prefix erp-ui run test -- \
  mobileHrOnboarding.test.js \
  mobileHrOnboardingForm.test.js \
  mobileAccessBoundary.test.js \
  mobileFeatureRouteCoverage.test.js \
  mobileContextProfiles.test.js \
  mobileAccessibilityVisuals.test.js \
  mobileAppShell.test.js \
  mobileNativeProductionHardening.test.js
```

Expected: 8 tests run, 0 failed.

- [ ] **Step 4: Build and sync with Node 24**

First add and test `resolveApiBaseUrl({ basePath, nativeOrigin, isNative, production })`. Web keeps the relative `VUE_APP_BASE_API`. A production native runtime requires an absolute HTTPS `VUE_APP_NATIVE_API_ORIGIN` and resolves `/prod-api` against it; missing or non-HTTPS origin throws `NATIVE_API_ORIGIN_REQUIRED` before any request. `request.js` passes `Capacitor.isNativePlatform()` into this resolver. Extend `mobileNativeProductionHardening.test.js` with web-relative, native-HTTPS, missing-origin, and insecure-origin cases.

Then run:

```bash
export PATH="/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
: "${VUE_APP_NATIVE_API_ORIGIN:?set the production HTTPS API origin before native sync}"
case "$VUE_APP_NATIVE_API_ORIGIN" in https://*) ;; *) echo "VUE_APP_NATIVE_API_ORIGIN must use https" >&2; exit 1;; esac
npm --prefix erp-ui run build:prod
npm --prefix erp-ui run app:sync
npm --prefix erp-ui run app:verify
```

Expected: build, Capacitor sync, and verification pass. These commands modify `dist` and native public assets; inspect and commit only intended generated files.

- [ ] **Step 5: Build native shells**

```bash
(cd erp-ui/android && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew assembleDebug)
```

```bash
xcodebuild -project erp-ui/ios/App/App.xcodeproj -scheme App \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

Expected: Android and iOS debug builds pass.

- [ ] **Step 6: Run real-flow QA**

`scripts/mobile-hr-onboarding-qa.cjs` captures workbench, all four statuses, detail, create, edit, validation, confirmation, cancel, empty/loading/error states at widths 390, 375, 360, and 320. Verify keyboard and sticky actions do not hide fields.

Run it against the real HTTPS origin and write its JSON/Markdown report plus screenshots under `artifacts/mobile-hr-onboarding/`:

```bash
: "${VUE_APP_NATIVE_API_ORIGIN:?set the production HTTPS API origin}"
API_ORIGIN="$VUE_APP_NATIVE_API_ORIGIN" node scripts/mobile-hr-onboarding-qa.cjs
```

On simulator/device, verify the packaged app can call the real API. A successful native compile is insufficient because relative `/prod-api` may resolve to `capacitor://localhost/prod-api`; fix the production API origin before declaring native HR complete.

- [ ] **Step 7: Commit verification work**

```bash
git add erp-ui/src/utils/apiBaseUrl.js \
  erp-ui/src/utils/request.js \
  erp-ui/test/mobileHrOnboarding.test.js \
  erp-ui/test/mobileHrOnboardingForm.test.js \
  erp-ui/test/mobileAccessBoundary.test.js \
  erp-ui/test/mobileFeatureRouteCoverage.test.js \
  erp-ui/test/mobileContextProfiles.test.js \
  erp-ui/test/mobileAccessibilityVisuals.test.js \
  erp-ui/test/mobileAppShell.test.js \
  erp-ui/test/mobileNativeProductionHardening.test.js \
  scripts/mobile-hr-onboarding-qa.cjs
git commit -m "test: verify mobile hr onboarding"
```

## Mobile plan acceptance

- HR accounts reach `/mobile/hr` without selecting a store/warehouse.
- Supervisors keep their business workbench and receive a scoped onboarding shortcut.
- Dynamic detail/edit routes enforce correct permissions.
- Workbench, queue, detail, actions, and four-step form use shared backend DTOs.
- No mobile code path reveals full ID, bank, or detailed address values.
- Forms survive network failure in memory without offline background writes.
- 320–390px layouts, safe areas, keyboard, Capacitor sync, and native API origin are verified.
