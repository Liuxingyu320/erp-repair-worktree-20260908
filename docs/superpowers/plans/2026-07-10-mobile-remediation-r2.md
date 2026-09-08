# Mobile Remediation R2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成手机端核心业务完整性整改：移除伪扫码入口、允许销售表单快速新建客户、提供手机个人资料维护、固定组织选择跳转语义，并交付只读 QA 数据审计与隔离门禁。

**Architecture:** 选择器保留通用查询职责，客户快速创建由独立轻量表单和纯函数模块处理；个人资料使用独立移动路由页面复用现有本人资料 API；组织选择的“进入工作台”在移动视口始终按组织类型落到工作台。QA 治理只增加只读 SQL、环境门禁和操作清单，不执行生产删除。

**Tech Stack:** Vue 2.6、Vue Router 3、Element UI、Axios、Node assert tests、SCSS、MySQL 只读 SQL、Shell 环境门禁。

---

### Task 1: Remove Fake Scan Controls

**Files:**
- Modify: `erp-ui/test/mobileEntityPickerService.test.js`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileLineItemsEditor.vue`

- [x] **Step 1: Write the failing source contract**

Replace the old scan-friendly assertions with explicit non-scan requirements:

```js
assert.ok(
  !pickerSource.includes("handleScanClick") &&
    !pickerSource.includes("扫码") &&
    !lineItemsEditorSource.includes("handleScanClick") &&
    !lineItemsEditorSource.includes("扫码"),
  "mobile pickers should use text search only and expose no fake scan control"
)
```

- [x] **Step 2: Run and verify RED**

Run:

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileEntityPickerService.test.js
```

Expected: FAIL because both components still render `扫码` buttons and placeholder handlers.

- [x] **Step 3: Remove scan-only state, handlers, markup and grid columns**

`MobileEntityPicker.vue` keeps one search input plus one search button:

```vue
<button type="button" @click="searchOptions">{{ loading ? "搜索中" : "搜索" }}</button>
```

`MobileLineItemsEditor.vue` keeps category filtering and text search for product name, code or barcode:

```vue
<div class="stock-picker-search">
  <input v-model.trim="pickerKeyword" type="search" placeholder="搜索商品名称/编码/条码">
  <button type="button" @click="loadPickerOptions">{{ pickerLoading ? "搜索中" : "搜索" }}</button>
</div>
```

- [x] **Step 4: Run and verify GREEN**

Run the Step 2 command. Expected: `1 run, 0 failed`.

- [x] **Step 5: Commit**

```bash
git add erp-ui/test/mobileEntityPickerService.test.js erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue erp-ui/src/views/mobile/feature/components/MobileLineItemsEditor.vue
git commit -m "fix: remove unsupported mobile scan controls"
```

### Task 2: Quick Customer Creation Inside Sales

**Files:**
- Create: `erp-ui/src/views/mobile/feature/mobileQuickCustomer.js`
- Create: `erp-ui/src/views/mobile/feature/components/MobileQuickCustomerForm.vue`
- Create: `erp-ui/test/mobileQuickCustomer.test.js`
- Modify: `erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue`
- Modify: `erp-ui/src/views/mobile/feature/mobileFormConfigs.js`
- Modify: `erp-ui/test/mobileEntityPickerService.test.js`

- [x] **Step 1: Write failing pure behavior and source tests**

Test payload normalization and created-option mapping:

```js
const {
  createCustomerDraft,
  normalizeQuickCustomerPayload,
  mapCreatedCustomerOption
} = require("../src/views/mobile/feature/mobileQuickCustomer")

assert.deepStrictEqual(createCustomerDraft(" 柏悦 "), {
  customerName: "柏悦",
  contactPerson: "",
  contactPhone: ""
})
assert.deepStrictEqual(normalizeQuickCustomerPayload({
  customerName: " 柏悦 ", contactPerson: " 林店长 ", contactPhone: " 13800000000 "
}), {
  customerName: "柏悦", contactPerson: "林店长", contactPhone: "13800000000",
  customerLevel: "普通客户", status: "0"
})
assert.strictEqual(mapCreatedCustomerOption({ customerId: 9, customerName: "柏悦" }).value, 9)
```

Source assertions require `inv:customer:add`, separate loading/empty/error states, `addCustomer`, and a `MobileQuickCustomerForm` that emits a selected customer without replacing the sales draft.

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileQuickCustomer.test.js test/mobileEntityPickerService.test.js
```

Expected: FAIL because the helper and quick-create component do not exist.

- [x] **Step 3: Implement the pure customer helper**

The module exposes deterministic functions and never performs network writes:

```js
function normalizeQuickCustomerPayload(draft) {
  const value = draft || {}
  return {
    customerName: String(value.customerName || "").trim(),
    contactPerson: String(value.contactPerson || "").trim(),
    contactPhone: String(value.contactPhone || "").trim(),
    customerLevel: "普通客户",
    status: "0"
  }
}
```

`mapCreatedCustomerOption` requires a returned `customerId` and carries the original row so `MobileFormSheet` can populate `customerName` while preserving all other form fields.

- [x] **Step 4: Implement the independent quick-create form**

The dialog contains only customer name, contact and phone. It receives `value`, `saving` and `error`, emits `input`, `submit` and `close`, and uses `mobileOverlayStack` with the class `mobile-quick-customer-open`.

- [x] **Step 5: Integrate permission, loading and error branches**

For customer fields configured with `quickCreate: true`:

```js
canQuickCreateCustomer() {
  return this.entity === "customer" && this.field.quickCreate === true &&
    this.hasAnyPermission(["inv:customer:add"])
}
```

On submit, call `addCustomer(normalizeQuickCustomerPayload(draft))`; map `response.data`, prepend it to options, emit `input` and `select`, then close. A failed request keeps both the customer draft and parent sales draft. A failed list request renders “加载客户失败” plus “重新加载”; a true empty result renders either “新建客户” or the no-permission guidance.

- [x] **Step 6: Run and verify GREEN**

Run the Step 2 command. Expected: `2 run, 0 failed`.

- [x] **Step 7: Commit**

```bash
git add erp-ui/src/views/mobile/feature erp-ui/test/mobileQuickCustomer.test.js erp-ui/test/mobileEntityPickerService.test.js
git commit -m "feat: create customers inside mobile sales"
```

### Task 3: Dedicated Mobile Profile, Avatar and Password

**Files:**
- Create: `erp-ui/src/views/mobile/profile/index.vue`
- Create: `erp-ui/src/views/mobile/profile/mobileProfileValidation.js`
- Create: `erp-ui/test/mobileProfileMaintenance.test.js`
- Modify: `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
- Modify: `erp-ui/src/utils/passwordResetReminder.js`
- Modify: `erp-ui/test/mobileSystemFeatureCompleteness.test.js`
- Modify: `erp-ui/test/passwordResetShopSelection.test.js`

- [x] **Step 1: Write failing validation and route tests**

Test profile and password validation as pure functions:

```js
assert.deepStrictEqual(validateMobileProfile({ nickName: "", phonenumber: "1", email: "bad" }), {
  nickName: "用户昵称不能为空",
  phonenumber: "请输入正确的手机号码",
  email: "请输入正确的邮箱地址"
})
assert.strictEqual(validateMobilePassword({ oldPassword: "a", newPassword: "12345678", confirmPassword: "87654321" }, "0").confirmPassword, "两次输入的密码不一致")
```

Source tests require the mobile route to import `@/views/mobile/profile/index`, all four profile APIs, `mode=reset-password`, `resetPasswordResetReminderState`, `avatarfile`, and password autocomplete values. They reject routing a mobile viewport to `/user/profile`.

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileProfileMaintenance.test.js test/mobileSystemFeatureCompleteness.test.js test/passwordResetShopSelection.test.js
```

Expected: FAIL because the route still uses the generic feature page and the reset helper targets desktop.

- [x] **Step 3: Implement pure validation**

Reuse the password policy shapes from `passwordRule.js`: length 8–20, type pattern selected by `pwrChrtype`, no illegal characters for type `0`, and exact confirmation matching. Profile validation checks required nickname, mainland mobile format and email format.

- [x] **Step 4: Implement the mobile profile page**

The page loads `getUserProfile`, edits only `nickName`, `phonenumber`, `email` and `sex`, uploads image files as `avatarfile`, and updates password using `updateUserPwd`. Buttons have independent loading states; API errors remain visible; successful password change clears reminder state and all password inputs. `?mode=reset-password` opens the password section directly.

- [x] **Step 5: Route mobile password reminders correctly**

`getPasswordResetRedirect()` returns `/mobile/profile?mode=reset-password` when `window.matchMedia("(max-width: 768px)").matches`, otherwise the existing desktop URL. With no organization selected, `/select-shop?redirect=<same target>` remains the first stop.

- [x] **Step 6: Run and verify GREEN**

Run the Step 2 command. Expected: `3 run, 0 failed`.

- [x] **Step 7: Commit**

```bash
git add erp-ui/src/views/mobile/profile erp-ui/src/views/mobile/mobileRouteDefinitions.js erp-ui/src/utils/passwordResetReminder.js erp-ui/test/mobileProfileMaintenance.test.js erp-ui/test/mobileSystemFeatureCompleteness.test.js erp-ui/test/passwordResetShopSelection.test.js
git commit -m "feat: maintain profile on mobile"
```

### Task 4: Make “Enter Workbench” Deterministic

**Files:**
- Create: `erp-ui/test/mobileShopEntryRouting.test.js`
- Modify: `erp-ui/src/views/select-shop/index.vue`
- Modify: `erp-ui/test/passwordResetShopSelection.test.js`

- [x] **Step 1: Write the failing routing contract**

Extract and test a small exported helper:

```js
assert.strictEqual(resolveShopEntryRedirect({
  isMobileViewport: true,
  deptType: "STORE",
  requestedRedirect: "/mobile/profile"
}), "/mobile/store")
assert.strictEqual(resolveShopEntryRedirect({
  isMobileViewport: true,
  deptType: "WAREHOUSE",
  requestedRedirect: "/mobile/sales"
}), "/mobile/warehouse")
```

Desktop keeps its requested redirect. Invalid strings fall back to the desktop home or organization-specific mobile home.

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileShopEntryRouting.test.js test/passwordResetShopSelection.test.js
```

Expected: FAIL because mobile non-home redirects currently survive.

- [x] **Step 3: Add and integrate the route helper**

Create `src/views/select-shop/shopEntryRouting.js` with `resolveShopEntryRedirect`. `getConfirmRedirect()` delegates to it. Password-reset confirmation remains a separate explicit branch and can still open the mobile reset page.

- [x] **Step 4: Run and verify GREEN**

Run the Step 2 command. Expected: `2 run, 0 failed`.

- [x] **Step 5: Commit**

```bash
git add erp-ui/src/views/select-shop erp-ui/test/mobileShopEntryRouting.test.js erp-ui/test/passwordResetShopSelection.test.js
git commit -m "fix: enter the selected mobile workbench"
```

### Task 5: Read-only QA Data Governance

**Files:**
- Create: `sql/erp_mobile_qa_data_audit_20260710.sql`
- Create: `scripts/qa/require-isolated-qa-env.sh`
- Create: `docs/mobile-qa-data-governance.md`
- Create: `erp-ui/test/mobileQaDataGovernance.test.js`

- [x] **Step 1: Write a failing safety contract**

The test requires the SQL and guard files, checks for `SELECT` inventory queries and the markers `qa_`, `qa-`, `test_`, `test-`, and rejects destructive SQL tokens:

```js
assert.ok(!/\b(delete|update|insert|truncate|drop|alter|replace)\b/i.test(sqlWithoutComments))
assert.ok(guardSource.includes("ERP_QA_RUN_ID") && guardSource.includes("ERP_QA_DATABASE"))
```

- [x] **Step 2: Run and verify RED**

```bash
cd erp-ui
node scripts/run-node-tests.cjs test/mobileQaDataGovernance.test.js
```

Expected: FAIL because the governed artifacts do not exist.

- [x] **Step 3: Add the read-only inventory SQL**

The SQL contains only `SELECT` statements over known user, customer and business document tables. Each result exposes the source table, stable row ID, identifying label/code, creator, creation time and organization ID where available. It only identifies candidates; it does not generate executable cleanup statements.

- [x] **Step 4: Add the isolated-QA environment guard and runbook**

The shell guard requires non-empty `ERP_QA_RUN_ID`, `ERP_QA_DATABASE` and `ERP_QA_ALLOWED_ORG_ID`, rejects database names `erp`, `erp_prod`, `production` and `prod`, and exports no credentials. The runbook requires export/backup, relationship review, human approval, transactional cleanup in a separately reviewed script, rollback evidence, and a post-clean audit.

- [x] **Step 5: Run and verify GREEN**

Run the Step 2 command and execute the guard once with missing variables (expected non-zero) and once with explicit safe fixture variables (expected zero).

- [x] **Step 6: Commit**

```bash
git add sql/erp_mobile_qa_data_audit_20260710.sql scripts/qa/require-isolated-qa-env.sh docs/mobile-qa-data-governance.md erp-ui/test/mobileQaDataGovernance.test.js
git commit -m "chore: govern mobile qa data"
```

### Task 6: R2 Verification

**Files:**
- Modify: `docs/superpowers/plans/2026-07-10-mobile-remediation-r2.md`

- [x] **Step 1: Run focused R2 tests**

```bash
cd erp-ui
node scripts/run-node-tests.cjs \
  test/mobileEntityPickerService.test.js \
  test/mobileQuickCustomer.test.js \
  test/mobileProfileMaintenance.test.js \
  test/mobileSystemFeatureCompleteness.test.js \
  test/passwordResetShopSelection.test.js \
  test/mobileShopEntryRouting.test.js \
  test/mobileQaDataGovernance.test.js
```

Expected: 7 files, 0 failures.

- [x] **Step 2: Run the production build and dependency audit**

```bash
cd erp-ui
npm run build:prod
npm run audit:prod
```

Expected: secret scan and build exit 0; dependency audit contains no moderate, high or critical vulnerability.

- [x] **Step 3: Run the full frontend suite**

```bash
cd erp-ui
npm test
```

Expected in the isolated worktree: only the two documented baseline fixture failures may remain. Any new failure blocks completion.

- [x] **Step 4: Run browser flow without persistent writes**

At 390×844 verify: sales form with an existing customer, customer empty/error/permission branches without submitting a real customer, no scan controls, mobile profile section switching, selected organization landing at its workbench, and browser console errors empty.

- [x] **Step 5: Review scope and record exact evidence**

```bash
git status --short
git diff --check 4e6c3215..HEAD
git log --oneline --decorate 4e6c3215..HEAD
```

Check completed boxes and append exact test/build/browser results to this plan.

- [x] **Step 6: Commit the verification record**

```bash
git add docs/superpowers/plans/2026-07-10-mobile-remediation-r2.md
git commit -m "docs: record mobile remediation r2 verification"
```

## Verification Record

Recorded on 2026-07-10 in worktree `/Users/liuxingyu/.config/superpowers/worktrees/ERP-NEW/mobile-remediation`.

- Focused R2 regression: 7 files run, 0 failed.
- Production build: frontend secret scan passed and Vue production build completed with no compile error. The same two asset/entrypoint size warning categories remain.
- Production dependency audit: 4 low, 0 moderate, 0 high, 0 critical. The low findings are the separately documented Vue 2 advisory chain.
- Full frontend suite: 88 files run, 2 failed. Both failures are unchanged clean-worktree fixtures:
  - `desktopAuditFollowupFixes.test.js` requires untracked local `erp-modules/erp-file/src/main/resources/application-local.yml`.
  - `mobileAppShell.test.js` requires generated/untracked `erp-ui/android/settings.gradle` and related native shell files.
- QA data safety gate:
  - Missing required variables exited 2 and identified the missing `ERP_QA_RUN_ID`.
  - Explicit isolated fixture values exited 0.
  - The committed audit artifact contains only read-only candidate queries; no cleanup statement was generated or executed.
- Browser regression at 390×844:
  - Dedicated mobile profile rendered the existing default avatar, profile fields, reset-password section and safe autocomplete values without horizontal overflow.
  - Preview mode disabled profile, password and hidden avatar-file writes while preserving section switching and `?mode=reset-password`.
  - A stale profile redirect landed at `/mobile/store?preview=1` after choosing a store; a stale sales redirect landed at `/mobile/warehouse?preview=1` after choosing a warehouse.
  - A local non-persistent mock API exposed the real sales form without touching a database. Empty customer state showed `新建客户`; the nested form contained only customer name, contact and phone.
  - A simulated incomplete create response kept the customer draft and both dialogs open, displayed a visible error, and submitted no customer or sales record to a real service.
  - Visible scan-control count stayed 0. After closing both dialogs, body classes were empty, open dialog count was 0 and body width equalled the 390px viewport.
  - Captured browser console errors were empty. The local mock session was logged out, temporary services were stopped, tabs were finalized and the temporary mock file was removed.
- Scope review: `git diff --check 4e6c3215..HEAD` passed. Changed paths are limited to the R2 plan, mobile pickers/forms/profile/shop-entry code, QA governance artifacts and related tests.
