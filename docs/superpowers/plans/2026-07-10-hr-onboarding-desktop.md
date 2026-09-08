# HR Onboarding Desktop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved desktop HR experience: a refined employee-master list, a 40/60 onboarding workbench, four-step data completion, transactional confirmation, preview-first import, server-side completeness, and position onboarding configuration.

**Architecture:** Desktop HR uses dedicated API wrappers and components. The onboarding page owns only UI orchestration; every state, missing field, completeness value, and allowed action comes from the backend implemented by `2026-07-10-hr-onboarding-core-backend.md`.

**Tech Stack:** Vue 2.6, Element UI 2.15, existing request/download helpers, SCSS, Node-based source-contract tests, Node 24 for supported builds.

---

## Prerequisites and working-tree warning

- Complete the backend plan first.
- `erp-ui/src/api/hr/employee.js`, `erp-ui/src/views/hr/**`, `erp-ui/test/hrPersonnelRoutes.test.js`, and `erp-ui/test/hrWorkbenchUx.test.js` currently exist as untracked working-tree files. Preserve them and make each task's commit include every intended HR file explicitly.
- Do not delete the six existing `erp-ui/test/* 2.js` duplicate copies without user approval. They currently make `scripts/run-node-tests.cjs` stop before selected tests. Use direct Node test execution during tasks; resolve the duplicate-copy blocker before the final standard runner.
- Use Node 24 for build commands:

```bash
export PATH="/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
node -v  # expected v24.x
```

## File structure

API:

- Modify `erp-ui/src/api/hr/employee.js` — formal employee only.
- Create `erp-ui/src/api/hr/onboarding.js` — shared desktop/mobile onboarding, actions, import, and options.
- Create `erp-ui/src/api/hr/completeness.js`.

Onboarding desktop:

- Replace `erp-ui/src/views/hr/onboarding/index.vue`.
- Create `erp-ui/src/views/hr/onboarding/onboardingFieldConfig.js`.
- Create `erp-ui/src/views/hr/onboarding/components/HrOnboardingListPane.vue`.
- Create `erp-ui/src/views/hr/onboarding/components/HrOnboardingDetailPane.vue`.
- Create `erp-ui/src/views/hr/onboarding/components/HrOnboardingCreateDialog.vue`.
- Create `erp-ui/src/views/hr/onboarding/components/HrOnboardingEditDrawer.vue`.
- Create `erp-ui/src/views/hr/onboarding/components/HrOnboardingConfirmDialog.vue`.
- Create `erp-ui/src/views/hr/onboarding/components/HrOnboardingImportDialog.vue`.

Employee master and support pages:

- Modify `erp-ui/src/views/hr/components/HrEmployeeList.vue`.
- Modify `erp-ui/src/views/hr/components/hrFieldConfig.js`.
- Modify `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`.
- Modify `erp-ui/src/views/hr/components/HrProfileEditDrawer.vue`.
- Create `erp-ui/src/views/hr/components/HrSensitiveFieldValue.vue`.
- Replace `erp-ui/src/views/hr/completeness/index.vue`.
- Modify `erp-ui/src/views/hr/importExport/index.vue`.
- Create `erp-ui/src/views/hr/positionConfig/index.vue`.

Tests:

- Modify `erp-ui/test/hrPersonnelRoutes.test.js`.
- Modify `erp-ui/test/hrWorkbenchUx.test.js`.
- Create `erp-ui/test/hrOnboardingApi.test.js`.
- Create `erp-ui/test/hrOnboardingWorkbench.test.js`.
- Create `erp-ui/test/hrOnboardingImport.test.js`.
- Create `erp-ui/test/hrEmployeeMasterFields.test.js`.
- Create `erp-ui/test/hrMenuPermissions.test.js`.

## Task 1: Split HR API clients

**Files:**
- Modify: `erp-ui/src/api/hr/employee.js`
- Create: `erp-ui/src/api/hr/onboarding.js`
- Create: `erp-ui/src/api/hr/completeness.js`
- Test: `erp-ui/test/hrOnboardingApi.test.js`
- Modify: `erp-ui/test/hrPersonnelRoutes.test.js`

- [ ] **Step 1: Write the failing API contract test**

```js
const assert = require("assert")
const fs = require("fs")
const path = require("path")
const root = path.resolve(__dirname, "..")
const onboarding = fs.readFileSync(path.join(root, "src/api/hr/onboarding.js"), "utf8")

for (const fragment of [
  "/system/hr/onboarding/list",
  "/system/hr/onboarding/summary",
  "/ready",
  "/return-to-draft",
  "/conflicts",
  "/confirm",
  "/cancel",
  "/restore",
  "/system/hr/onboarding/import/preview",
  "/errors",
  "/system/hr/onboarding/form-options"
]) assert.ok(onboarding.includes(fragment), `missing ${fragment}`)

assert.ok(onboarding.includes("timeout: 60000"), "preview import needs an explicit timeout")
assert.ok(onboarding.includes('HR_ONBOARDING_TEMPLATE_URL = "system/hr/onboarding/import/template"'))
console.log("hrOnboardingApi tests passed")
```

- [ ] **Step 2: Run it and verify failure**

```bash
node erp-ui/test/hrOnboardingApi.test.js
```

Expected: FAIL because `onboarding.js` is missing.

- [ ] **Step 3: Implement dedicated wrappers**

Use this stable request shape:

```js
import request from "@/utils/request"

export const listHrOnboarding = params => request({ url: "/system/hr/onboarding/list", method: "get", params })
export const getHrOnboardingSummary = params => request({ url: "/system/hr/onboarding/summary", method: "get", params })
export const getHrOnboarding = id => request({ url: `/system/hr/onboarding/${id}`, method: "get" })
export const createHrOnboarding = data => request({ url: "/system/hr/onboarding", method: "post", data, silentError: true })
export const updateHrOnboarding = (id, data) => request({ url: `/system/hr/onboarding/${id}`, method: "put", data, silentError: true })
export const markHrOnboardingReady = (id, data) => request({ url: `/system/hr/onboarding/${id}/ready`, method: "post", data, silentError: true })
export const returnHrOnboardingToDraft = (id, data) => request({ url: `/system/hr/onboarding/${id}/return-to-draft`, method: "post", data, silentError: true })
export const getHrOnboardingConflicts = id => request({ url: `/system/hr/onboarding/${id}/conflicts`, method: "get" })
export const confirmHrOnboarding = (id, data) => request({ url: `/system/hr/onboarding/${id}/confirm`, method: "post", data, silentError: true })
export const cancelHrOnboarding = (id, data) => request({ url: `/system/hr/onboarding/${id}/cancel`, method: "post", data, silentError: true })
export const restoreHrOnboarding = (id, data) => request({ url: `/system/hr/onboarding/${id}/restore`, method: "post", data, silentError: true })
export const getHrOnboardingFormOptions = params => request({ url: "/system/hr/onboarding/form-options", method: "get", params })
export const previewHrOnboardingImport = data => request({ url: "/system/hr/onboarding/import/preview", method: "post", data, timeout: 60000, silentError: true })
export const getHrOnboardingImportBatch = id => request({ url: `/system/hr/onboarding/import/${id}`, method: "get" })
export const confirmHrOnboardingImport = (id, data) => request({ url: `/system/hr/onboarding/import/${id}/confirm`, method: "post", data, timeout: 60000, silentError: true })
export const HR_ONBOARDING_TEMPLATE_URL = "system/hr/onboarding/import/template"
export const hrOnboardingErrorRowsUrl = id => `system/hr/onboarding/import/${id}/errors`
```

Move completeness calls to `completeness.js`. Keep `employee.js` limited to employee list/detail/update/summary/options/derived-preview/reveal/export. Use `POST /system/hr/employee/{userId}/sensitive/reveal` with `{ fieldKey }` and the download action `system/hr/employee/export-sensitive`. Do not keep the old direct user import under an onboarding name. Update `hrPersonnelRoutes.test.js` in this same task so it asserts the new module boundaries instead of the old `employee.js` exports.

- [ ] **Step 4: Run the test and commit**

Run:

```bash
node erp-ui/test/hrOnboardingApi.test.js
node erp-ui/test/hrPersonnelRoutes.test.js
```

Expected: PASS.

Commit with:

```bash
git add erp-ui/src/api/hr/employee.js erp-ui/src/api/hr/onboarding.js \
  erp-ui/src/api/hr/completeness.js erp-ui/test/hrOnboardingApi.test.js \
  erp-ui/test/hrPersonnelRoutes.test.js
git commit -m "feat: split hr desktop api clients"
```

## Task 2: Harden the employee master UI

**Files:**
- Modify: `HrEmployeeList.vue`
- Modify: `hrFieldConfig.js`
- Modify: `HrProfileDetailDrawer.vue`
- Modify: `HrProfileEditDrawer.vue`
- Create: `HrSensitiveFieldValue.vue`
- Test: `erp-ui/test/hrEmployeeMasterFields.test.js`
- Modify: `erp-ui/test/hrWorkbenchUx.test.js`

- [ ] **Step 1: Write failing field-boundary tests**

Assert:

```js
assert.ok(config.includes('["employeeNo", "工号"]'))
assert.ok(config.includes('["remark", "备注"]'))
assert.ok(config.includes('title: "数据记录"'))
assert.ok(config.includes("READ_ONLY_DERIVED_FIELDS"))
assert.ok(!component.includes("filteredRows"), "server-paged employees must not be filtered again")
assert.ok(component.includes("getHrEmployeeSummary"))
assert.ok(!component.includes('prop="idNumber"'))
assert.ok(!component.includes('prop="bankAccount"'))
assert.ok(!component.includes('prop="registeredResidence"'))
assert.ok(!component.includes('prop="currentAddress"'))
assert.ok(detail.includes("HrSensitiveFieldValue"))
assert.ok(edit.includes("directSupervisorUserId"))
assert.ok(edit.includes("dirtySensitiveFields"))
assert.ok(edit.includes("buildEmployeeUpdatePayload"))
assert.ok(edit.includes('field.key === "employeeNo"'))
assert.ok(edit.includes("disabled"))
```

- [ ] **Step 2: Run and verify failure**

```bash
node erp-ui/test/hrEmployeeMasterFields.test.js
```

Expected: FAIL on the current field config and client-side counts.

- [ ] **Step 3: Correct field configuration**

Add visible `employeeNo` and `remark`. Employee number is display-only for confirmed/formal employees and is never included in the employee edit payload; remark remains editable. Remove ID number, bank account, registered-residence, and current-address columns from the default employee list rather than merely masking those columns. Rename `附件与记录` to `数据记录`. Define:

```js
export const READ_ONLY_DERIVED_FIELDS = [
  "companyName", "deptLevel1Name", "deptLevel2Name", "deptLevel3Name",
  "storeName", "positionNames", "departmentSupervisor", "workYears",
  "companyYears", "contractTerm"
]
```

Replace hardcoded `allow-create` HR enums with options returned by the backend. Organization/post edits submit `deptId`, `postIds`, and `directSupervisorUserId`.

Keep masked values separate from the editable model. `buildEmployeeUpdatePayload` omits untouched phone/ID/bank/address fields and sends a sensitive field only after that exact control becomes dirty. Never submit `****`, mask bullets, or a masked address back to the API. Employee number is read-only for confirmed employees; remark remains editable.

- [ ] **Step 4: Fix pagination and task metrics**

Render server-returned `rows` directly. Send keyword, status, category, organization, completeness, and account filters to the backend. Load task metrics from `getHrEmployeeSummary`; do not calculate company totals from the current 10-row page.

Change `profileRawValue` to preserve `0` and `false`:

```js
const profile = this.profile(row)
if (profile[key] !== undefined && profile[key] !== null) return profile[key]
return row ? row[key] : undefined
```

- [ ] **Step 5: Add sensitive value rendering**

`HrSensitiveFieldValue.vue` accepts `masked-value`, `field`, and `employee-id`. It shows the masked value by default and calls the audited reveal API only after an explicit click guarded by `hr:employee:sensitive:view`. Never cache revealed values in Vuex or local storage.

- [ ] **Step 6: Run tests and commit**

```bash
node erp-ui/test/hrEmployeeMasterFields.test.js
node erp-ui/test/hrWorkbenchUx.test.js
```

Expected: PASS.

Commit with `git commit -m "feat: harden hr employee master ui"`.

## Task 3: Build the onboarding list/detail workbench

**Files:**
- Replace: `erp-ui/src/views/hr/onboarding/index.vue`
- Create: `HrOnboardingListPane.vue`
- Create: `HrOnboardingDetailPane.vue`
- Create: `onboardingFieldConfig.js`
- Test: `erp-ui/test/hrOnboardingWorkbench.test.js`

- [ ] **Step 1: Write the failing workbench test**

```js
assert.ok(index.includes("hr-onboarding-workbench"))
assert.ok(index.includes("HrOnboardingListPane"))
assert.ok(index.includes("HrOnboardingDetailPane"))
assert.ok(!index.includes('default-employee-status="待入职"'))
assert.ok(list.includes("DRAFT") && list.includes("READY") && list.includes("CONFIRMED") && list.includes("CANCELLED"))
for (const key of ["keyword", "expectedEntryDateFrom", "expectedEntryDateTo", "targetDeptId", "targetStoreId", "employeeCategory", "ownerUserId"]) assert.ok(list.includes(key))
assert.ok(detail.includes("allowedActions"))
assert.ok(detail.includes("missingOnboardingFields"))
assert.ok(detail.includes("missingProfileFields"))
assert.ok(index.includes("empty-selection"))
assert.ok(index.includes("新建入职"))
```

- [ ] **Step 2: Run and verify failure**

```bash
node erp-ui/test/hrOnboardingWorkbench.test.js
```

Expected: FAIL because the current onboarding page is an employee-status wrapper.

- [ ] **Step 3: Implement the 40/60 shell**

Use a CSS grid:

```scss
.hr-onboarding-workbench {
  display: grid;
  grid-template-columns: minmax(360px, 40%) minmax(520px, 60%);
  min-height: calc(100vh - 180px);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}
```

`index.vue` owns query state, selected onboarding ID, loading/error state, and dialogs. The list pane owns four status tabs, server search across name/phone/post/organization, date range, company/organization, store, employee category, and owner filters, server pagination, and selected row. The detail pane owns summary, two completeness values, grouped missing fields, operation history, account-configuration risks, and a sticky action footer. No selection, empty result, loading, request error, and retry are separate rendered states; the no-selection state includes a permission-guarded `新建入职` entry.

- [ ] **Step 4: Consume backend action rules**

Render buttons only when both the permission directive and `detail.allowedActions` allow them. Map `EDIT → hr:onboarding:edit`, `MARK_READY → hr:onboarding:ready`, `RETURN_TO_DRAFT → hr:onboarding:return`, `CONFIRM → hr:onboarding:confirm`, `CANCEL → hr:onboarding:cancel`, and `RESTORE → hr:onboarding:restore`; ignore unknown values. Do not infer readiness from percentages in JavaScript.

- [ ] **Step 5: Run tests and commit**

Run the workbench test. Expected: PASS.

Commit with `git commit -m "feat: add hr onboarding desktop workbench"`.

## Task 4: Create, edit, and confirm flows

**Files:**
- Create: `HrOnboardingCreateDialog.vue`
- Create: `HrOnboardingEditDrawer.vue`
- Create: `HrOnboardingConfirmDialog.vue`
- Modify: `onboardingFieldConfig.js`
- Modify: `onboarding/index.vue`
- Test: `erp-ui/test/hrOnboardingWorkbench.test.js`

- [ ] **Step 1: Extend failing tests for the three flows**

Assert quick-create contains the seven create fields; the edit drawer contains four named steps; confirm contains actual-entry date, conflict action, `version`, and an idempotency key; each component maps backend field errors to visible form items. Assert every bind candidate renders `name`, `phoneNumberMasked`, and `departmentLabel` before selection. Assert create, edit, ready, return, confirm, cancel, and restore all send `version` where applicable, and untouched masked fields are absent from update payloads.

- [ ] **Step 2: Implement quick create**

Use exactly:

```js
const createModel = {
  employeeName: "",
  phoneNumber: "",
  expectedEntryDate: "",
  targetDeptId: null,
  targetPostId: null,
  employeeCategory: "",
  ownerUserId: null
}
```

On success, close the dialog, reload the active list, select the returned `onboardingId`, and open the detail pane.

- [ ] **Step 3: Implement the four-step edit drawer**

Use steps `基础信息`, `组织岗位`, `身份与紧急联系人`, and `用工合同与社保`. `onboardingFieldConfig.js` defines labels, control types, option sources, and backend keys only; it does not define required-state logic. Derived company/department/store/post/supervisor labels are read-only and the payload sends only their source IDs. Save draft on any step and include `version`. Track sensitive-field dirtiness separately; omit untouched masked phone/ID/bank/address values and reject any local masking placeholder before submit. Use `fieldErrors` from the API to activate the first erroneous step and scroll to the exact field.

- [ ] **Step 4: Implement confirmation and state actions**

Generate a UUID idempotency key in the dialog when it opens. Load `getHrOnboardingConflicts(id)` and render each candidate's name, masked phone, and department label before allowing `BIND_EXISTING`; never render a raw phone, ID number, bank account, or detailed address. Prefill an import-approved preferred candidate only if it is still present and eligible.

```js
{
  version: detail.version,
  actualEntryDate,
  conflictAction,
  bindUserId,
  idempotencyKey
}
```

Show the one-time password exactly once after new-account success. A replay with `oneTimePassword=null` shows account success plus the normal reset-password path; it never suggests the password can be recovered. Never store or place it in downloadable content. Ready, edit, return-to-draft, cancel, restore, and confirm include `version`.

- [ ] **Step 5: Run tests and commit**

Run `hrOnboardingWorkbench.test.js`. Expected: PASS.

Commit with `git commit -m "feat: add hr onboarding desktop actions"`.

## Task 5: Preview-first import

**Files:**
- Create: `HrOnboardingImportDialog.vue`
- Modify: `erp-ui/src/views/hr/onboarding/index.vue`
- Modify: `erp-ui/src/views/hr/importExport/index.vue`
- Test: `erp-ui/test/hrOnboardingImport.test.js`

- [ ] **Step 1: Write the failing import-flow test**

Assert the dialog includes `previewHrOnboardingImport`, `batchId`, `version`, `rowId`, the five categories `IMPORTABLE / WARNING / INVALID / POSSIBLE_DUPLICATE / BINDABLE_ACCOUNT`, per-row decision/`bindUserId`, selected-row confirm, template download, error-row download, and plain text error rendering. Assert it does not import `ExcelImportDialog` and does not contain `dangerouslyUseHTMLString`.

- [ ] **Step 2: Implement upload and preview**

Build `FormData`, call the preview API with 60-second timeout, and store the returned batch/version. `INVALID` rows are disabled; warning/duplicate rows require explicit continue/skip, and bindable rows require selection of one masked candidate `bindUserId`.

- [ ] **Step 3: Implement confirm and result views**

Submit batch `version` and only selected valid row decisions shaped as `{ rowId, decision, bindUserId }`. Show success/failure totals and per-row text. Use standard download helper for template and failed rows.

- [ ] **Step 4: Reuse the dialog from two entry points**

Open the same component from onboarding and import/export pages. Mark the old formal employee direct import as a transitional capability; do not present it as the new onboarding import.

- [ ] **Step 5: Run tests and commit**

Run `node erp-ui/test/hrOnboardingImport.test.js`. Expected: PASS.

Commit with `git commit -m "feat: preview hr onboarding imports in desktop"`.

## Task 6: Completeness and position configuration pages

**Files:**
- Replace: `erp-ui/src/views/hr/completeness/index.vue`
- Create: `erp-ui/src/views/hr/positionConfig/index.vue`
- Modify: `erp-ui/src/api/hr/completeness.js`
- Modify: `erp-ui/src/api/hr/onboarding.js`
- Test: `erp-ui/test/hrPersonnelRoutes.test.js`
- Test: `erp-ui/test/hrMenuPermissions.test.js`

- [ ] **Step 1: Write failing page/permission tests**

Assert completeness calls `/summary`, `/employees`, and `/departments`, includes `accountConfigurationStatus: "MISSING"`, and renders `账号待配置`; position config calls `/system/hr/onboarding/config`; menu SQL contains component `hr/positionConfig/index` and `hr:onboarding:config`; the onboarding page uses only onboarding permissions.

- [ ] **Step 2: Build completeness views**

Provide employee, department, and `账号待配置` risk tabs using server totals. Display onboarding completion, profile completion, post-entry due date, overdue state, account-configuration risk code/status, and a link to the employee, onboarding record, or position configuration. The risk tab queries `accountConfigurationStatus=MISSING` from the server and is visually prominent without using alarmist decoration. Do not filter server-paged rows in the browser.

- [ ] **Step 3: Build position configuration**

Add list/detail/create/update/disable/options wrappers under `/system/hr/onboarding/config`. Support post/category pair, role selection, data-scope strategy, default contract/social/probation values, grade, enable-account flag, status, and version conflict handling. For contract, social, and probation, render an explicit `REQUIRED / OPTIONAL / NOT_APPLICABLE` selector; `NOT_APPLICABLE` is a saved mode, never an empty value. Use the config-options endpoint for dictionaries and active role/post options, not system-management permissions.

- [ ] **Step 4: Run tests and commit**

Run the two direct Node tests. Expected: PASS.

Commit with `git commit -m "feat: add hr completeness and onboarding config ui"`.

## Task 7: Desktop verification and visual QA

**Files:**
- Modify: `erp-ui/test/hrPersonnelRoutes.test.js`
- Modify: `erp-ui/test/hrWorkbenchUx.test.js`
- Create/update: all HR tests listed in this plan

- [ ] **Step 1: Run every HR test directly**

```bash
for test in \
  hrPersonnelRoutes.test.js \
  hrWorkbenchUx.test.js \
  hrOnboardingApi.test.js \
  hrOnboardingWorkbench.test.js \
  hrOnboardingImport.test.js \
  hrEmployeeMasterFields.test.js \
  hrMenuPermissions.test.js; do
  node "erp-ui/test/$test" || exit 1
done
```

Expected: every file prints its pass message.

- [ ] **Step 2: Audit the standard runner blocker**

```bash
find erp-ui/test -maxdepth 1 -name '* 2.js' -print
```

If duplicates remain, stop and ask the user whether to delete or retain them. Do not remove them as part of this feature. After the user-approved resolution, run:

```bash
npm --prefix erp-ui run test -- \
  hrPersonnelRoutes.test.js hrWorkbenchUx.test.js hrOnboardingApi.test.js \
  hrOnboardingWorkbench.test.js hrOnboardingImport.test.js \
  hrEmployeeMasterFields.test.js hrMenuPermissions.test.js
```

Expected: PASS.

- [ ] **Step 3: Build with supported Node**

```bash
export PATH="/Users/liuxingyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin:$PATH"
npm --prefix erp-ui run build:prod
```

Expected: production build completes without errors.

- [ ] **Step 4: Visual QA**

With the local gateway running on `127.0.0.1:8080`, start the UI:

```bash
npm --prefix erp-ui run dev -- --port 1025
```

Use browser control against `http://127.0.0.1:1025` and a dedicated QA account; do not put credentials in source or the plan. Capture onboarding, employee, completeness, account-risk queue, import preview, create, edit, confirm, empty, loading, and error states at 1440×1024, 1366×768, and 1280×720 under `docs/audit-screenshots/hr-onboarding-desktop-20260710/`. Verify no clipped text, nested-card clutter, hidden sticky actions, or sensitive values. The screenshot directory is QA evidence and is not staged by the feature commits unless the user explicitly requests it.

- [ ] **Step 5: Commit verification updates**

```bash
git add erp-ui/test/hrPersonnelRoutes.test.js \
  erp-ui/test/hrWorkbenchUx.test.js \
  erp-ui/test/hrOnboardingApi.test.js \
  erp-ui/test/hrOnboardingWorkbench.test.js \
  erp-ui/test/hrOnboardingImport.test.js \
  erp-ui/test/hrEmployeeMasterFields.test.js \
  erp-ui/test/hrMenuPermissions.test.js
git commit -m "test: verify hr desktop experience"
```

## Desktop plan acceptance

- Employee counts and completeness come from backend aggregates, not the current page.
- Employee number and remark are visible; derived fields are read-only.
- Onboarding is a dedicated 40/60 workbench with four server-backed states.
- Create, edit, confirm, cancel, restore, and import flows use backend errors/actions.
- Import is preview-first and renders all spreadsheet content as text.
- Sensitive fields are masked by default and explicit reveal is audited.
- The approved visual hierarchy remains recognizable as the existing ERP.
