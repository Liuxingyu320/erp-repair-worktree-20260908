# HR Small Usability Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce daily HR friction with correct summary-card filtering, actionable missing-field guidance, unified employee statuses, explicit retry states, safe import results, and user-scoped non-sensitive list preferences.

**Architecture:** Keep the current HR pages and APIs. Add small pure helpers for field resolution, status normalization, import-result parsing, and preference persistence so each behavior is testable without a browser. Server form options remain the source for status values, and UI state distinguishes loading failures from legitimate empty results.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, JUnit 5, AssertJ, Vue 2, Element UI, Node.js contract tests, browser `localStorage`.

---

## Preconditions and delivery order

- Approved design: `docs/superpowers/specs/2026-07-13-hr-completeness-small-usability-fixes-design.md`.
- Complete `docs/superpowers/plans/2026-07-13-hr-completeness-consistency.md` first. This plan consumes the server-owned completeness counts and `missingProfileFields` added there.
- Do not create a new HR menu, workflow, bulk editor, approval process, or reporting module.
- Keep masked values, keywords, names, phone numbers, and all other search content out of persisted preferences.
- Do not modify unrelated dirty files or generated JARs.

## File responsibility map

- `hrFieldConfig.js`: labels plus actionable/derived/workflow resolution for missing fields.
- `HrEmployeeStatusCatalog`: canonical status values and the single legacy `在职 -> 正式` mapping.
- `importResult.js`: converts legacy HTML-like import messages into safe plain-text rows and a downloadable failure CSV.
- `hrEmployeePreferences.js`: validates and scopes only the allowed non-sensitive list preferences.
- Vue pages: render and route state; they do not invent status catalogs or parse unsafe HTML.

### Task 1: Make completeness summary cards and department rows open the correct queue

**Files:**
- Modify: `erp-ui/src/views/hr/completeness/index.vue`
- Modify: `erp-ui/src/views/hr/components/hrFieldConfig.js`
- Modify: `erp-ui/test/hrWorkbenchUx.test.js`

- [ ] **Step 1: Add failing summary-card and department-drill tests**

Extend `hrWorkbenchUx.test.js` with source assertions and a VM test:

```javascript
assert.ok(completenessSource.includes("switchSummary('incomplete')"))
assert.ok(completenessSource.includes("switchSummary('all')"))
assert.ok(!completenessSource.includes("@click=\"switchTab('employees')\""))
assert.ok(completenessSource.includes("openDepartment(row)"))
assert.ok(completenessSource.includes("missingProfileLabels"))

const vm = createVm(completenessComponent)
vm.query = { pageNum: 3, pageSize: 10, keyword: "", completenessStatus: undefined, deptId: undefined }
vm.switchSummary("incomplete")
assert.strictEqual(vm.query.pageNum, 1)
assert.strictEqual(vm.query.completenessStatus, "INCOMPLETE")
vm.switchSummary("all")
assert.strictEqual(vm.query.completenessStatus, undefined)
vm.openDepartment({ deptId: 28 })
assert.strictEqual(vm.activeTab, "employees")
assert.strictEqual(vm.query.deptId, 28)
assert.strictEqual(vm.query.completenessStatus, "INCOMPLETE")
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs hrWorkbenchUx.test.js
```

Expected: assertions fail because both employee summary cards currently call `switchTab('employees')`, the department row has no drill-down, and missing keys are not rendered in the queue.

- [ ] **Step 3: Replace card click behavior with explicit filters**

Use buttons only for filterable metrics:

```html
<button class="summary-card" type="button" @click="switchSummary('all')">
  <span>员工总数</span><strong>{{ summary.totalEmployeeCount || 0 }}</strong>
</button>
<button class="summary-card" type="button" @click="switchSummary('incomplete')">
  <span>资料待补齐</span><strong>{{ summary.incompleteEmployeeCount || 0 }}</strong>
</button>
<button class="summary-card summary-card--risk" type="button" @click="switchTab('accountRisk')">
  <span>账号待配置</span><strong>{{ summary.accountConfigurationRiskCount || 0 }}</strong>
</button>
<div class="summary-card">
  <span>员工平均档案覆盖度</span><strong>{{ summary.averageProfileCompletionPercent || 0 }}%</strong>
</div>
```

Add:

```javascript
switchSummary(type) {
  this.activeTab = "employees"
  this.query.pageNum = 1
  this.query.completenessStatus = type === "incomplete" ? "INCOMPLETE" : undefined
  return this.loadData()
},
openDepartment(row) {
  const deptId = Number(row && row.deptId)
  if (!Number.isSafeInteger(deptId) || deptId <= 0) return Promise.resolve(null)
  this.activeTab = "employees"
  this.query = { ...this.query, pageNum: 1, deptId, completenessStatus: "INCOMPLETE" }
  return this.loadData()
}
```

Make the department name/count an accessible button that calls `openDepartment(row)`; do not make the average metric clickable.

- [ ] **Step 4: Show the first three server missing fields in employee rows**

Export from `hrFieldConfig.js`:

```javascript
export function missingProfileLabels(row) {
  const keys = Array.isArray(row && row.missingProfileFields) ? row.missingProfileFields : []
  const labels = keys.map(profileFieldLabel)
  return { visible: labels.slice(0, 3), remaining: Math.max(0, labels.length - 3) }
}
```

Render `visible` as small tags and `remaining` as `另有 N 项`. Do not infer missing fields from falsy list values.

- [ ] **Step 5: Rerun the focused test**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs hrWorkbenchUx.test.js
```

Expected: the two summary filters, non-clickable average, department drill-down, and three-label missing summary assertions pass.

- [ ] **Step 6: Commit the navigation improvement**

```bash
git add erp-ui/src/views/hr/completeness/index.vue \
  erp-ui/src/views/hr/components/hrFieldConfig.js erp-ui/test/hrWorkbenchUx.test.js
git commit -m "feat: streamline HR completeness queues"
```

### Task 2: Distinguish employee loading failures from valid empty results

**Files:**
- Modify: `erp-ui/src/views/hr/components/HrEmployeeList.vue`
- Modify: `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`
- Modify: `erp-ui/test/hrEmployeeMasterFields.test.js`

- [ ] **Step 1: Add failing list and detail error-state tests**

Add a rejected-list VM test:

```javascript
const vm = createEmployeeListVm({
  listHrEmployees: () => Promise.reject(new Error("network down"))
})
vm.rows = [{ userId: 1, employeeName: "旧数据" }]
await vm.getList()
assert.strictEqual(vm.listError, "员工档案加载失败，请重试")
assert.deepStrictEqual(vm.rows, [])
assert.strictEqual(vm.listEmptyText, "加载失败")
await vm.retryList()
assert.strictEqual(vm.queryParams.pageNum, 1)
```

Add source assertions for a visible `重试` button and for the copy `档案覆盖度`; reject the old ambiguous `资料完整度` label in `HrEmployeeList.vue` and `HrProfileDetailDrawer.vue`.

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs hrEmployeeMasterFields.test.js
```

Expected: `listError`, `listEmptyText`, and `retryList` are absent; the current catch block silently looks like an empty business result.

- [ ] **Step 3: Add explicit list error state with stale-request protection**

Add to data:

```javascript
listError: "",
```

Add:

```javascript
listEmptyText() {
  return this.listError ? "加载失败" : "暂无员工档案"
}
```

At the start of `getList`, clear `listError`. In its catch block, keep the request-sequence check and use:

```javascript
this.pendingRouteEmployeeId = undefined
this.rows = []
this.total = 0
this.listError = (error && error.response && error.response.data && error.response.data.msg)
  || "员工档案加载失败，请重试"
```

Add:

```javascript
retryList() {
  this.queryParams.pageNum = 1
  return this.getList()
}
```

Render an `el-alert` with the error and a `重试` button above the table. Bind the table's `empty-text` to `listEmptyText`. Do not clear the editor form when save fails.

- [ ] **Step 4: Clarify coverage labels and counts**

Replace visible `资料完整度` copy with `档案覆盖度`. Next to every employee progress display, render:

```html
<span>已填 {{ row.profileCompletedFieldCount || 0 }}/适用 {{ row.profileApplicableFieldCount || 0 }}</span>
<span>{{ row.profileNotApplicableFieldCount || 0 }} 项不适用</span>
```

The detail drawer uses the same count fields and retains the server missing list.

- [ ] **Step 5: Run the focused test**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs hrEmployeeMasterFields.test.js
```

Expected: a failed request displays an actionable error, retry starts from page 1, empty data remains a separate state, and coverage wording is unambiguous.

- [ ] **Step 6: Commit the error-state change**

```bash
git add erp-ui/src/views/hr/components/HrEmployeeList.vue \
  erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue \
  erp-ui/test/hrEmployeeMasterFields.test.js
git commit -m "fix: show retryable HR employee errors"
```

### Task 3: Add missing-only editing, group counts, and non-editable guidance

**Files:**
- Modify: `erp-ui/src/views/hr/components/hrFieldConfig.js`
- Modify: `erp-ui/src/views/hr/components/HrProfileEditDrawer.vue`
- Modify: `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`
- Modify: `erp-ui/src/views/hr/components/HrEmployeeList.vue`
- Modify: `erp-ui/test/hrEmployeeMasterFields.test.js`

- [ ] **Step 1: Add failing pure-helper tests**

Import and test the planned resolver:

```javascript
assert.deepStrictEqual(resolveMissingProfileFields([
  "bankAccount", "employeeNo", "companyName", "unknownLegacyField"
]), {
  actionable: ["bankAccount"],
  workflow: [{ key: "employeeNo", source: "通过入职确认或工号生成规则处理" }],
  derived: [{ key: "companyName", source: "通过组织岗位配置或系统派生处理" }],
  unknown: ["unknownLegacyField"]
})
```

Add a VM assertion that opening an editor with `missingFields: ["firstEducation", "bankAccount"]` enables missing-only mode, reports one missing item in the education group and one in the contacts/bank group, and selects the first group containing an actionable missing field.

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs hrEmployeeMasterFields.test.js
```

Expected: the resolver, prop, switch, badges, and first-missing navigation do not exist.

- [ ] **Step 3: Add explicit resolution metadata**

In `hrFieldConfig.js`, add:

```javascript
const WORKFLOW_MISSING_SOURCES = {
  employeeNo: "通过入职确认或工号生成规则处理",
  employeeStatus: "通过员工状态流程处理"
}
const DERIVED_MISSING_SOURCES = {
  companyName: "通过组织岗位配置或系统派生处理",
  deptLevel1Name: "通过组织岗位配置或系统派生处理",
  deptLevel2Name: "通过组织岗位配置或系统派生处理",
  deptLevel3Name: "通过组织岗位配置或系统派生处理",
  storeName: "通过组织岗位配置或系统派生处理",
  positionName: "通过组织岗位配置或系统派生处理",
  positionNames: "通过组织岗位配置或系统派生处理",
  postNames: "通过组织岗位配置或系统派生处理",
  departmentSupervisor: "通过组织岗位配置或系统派生处理",
  workYears: "通过基础日期或系统派生处理",
  companyYears: "通过基础日期或系统派生处理",
  contractTerm: "通过合同日期或系统派生处理"
}

export function resolveMissingProfileFields(keys) {
  const result = { actionable: [], workflow: [], derived: [], unknown: [] }
  ;(Array.isArray(keys) ? keys : []).forEach(rawKey => {
    const key = canonicalFieldKey(rawKey)
    if (WORKFLOW_MISSING_SOURCES[key]) result.workflow.push({ key, source: WORKFLOW_MISSING_SOURCES[key] })
    else if (DERIVED_MISSING_SOURCES[key]) result.derived.push({ key, source: DERIVED_MISSING_SOURCES[key] })
    else if (FIELD_META[key]) result.actionable.push(key)
    else result.unknown.push(rawKey)
  })
  return result
}
```

`actionable` still needs the editor's `editableFields` set before display; fields disabled by server metadata must be moved to a read-only explanation rather than rendered as inputs.

- [ ] **Step 4: Add the missing-only editor contract**

Add the prop:

```javascript
missingFields: { type: Array, default: () => [] }
```

Add `missingOnly`, compute `actionableMissingKeys`, `groupMissingCount(group)`, and `visibleGroupFields(group)`. The switch label is `仅看缺失项`; each tab label appends `（N）` only when `N > 0`.

When the drawer opens:

```javascript
this.missingOnly = this.actionableMissingKeys.length > 0
const first = DETAIL_GROUPS.find(group => this.groupMissingCount(group) > 0)
this.activeGroup = first ? first.title : DETAIL_GROUPS[0].title
```

When `missingOnly` is true and no editable missing field exists in a group, hide that group's form fields. Keep form model values intact so toggling the switch never loses edits.

- [ ] **Step 5: Show read-only handling sources and pass missing keys into the editor**

Pass `detail.missingProfileFields` from `HrEmployeeList.vue` to the edit drawer. In both detail and edit drawers, render workflow/derived missing fields in a compact `需要通过其他入口处理` section with the resolver-provided source. Unknown historical keys render as `待确认字段：` followed by the literal server key, and do not create an editable input.

- [ ] **Step 6: Run the focused test**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs hrEmployeeMasterFields.test.js
```

Expected: missing-only mode, per-group counts, auto-navigation, and workflow/derived guidance pass without changing saved values when the switch toggles.

- [ ] **Step 7: Commit the missing-field guidance**

```bash
git add erp-ui/src/views/hr/components/hrFieldConfig.js \
  erp-ui/src/views/hr/components/HrProfileEditDrawer.vue \
  erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue \
  erp-ui/src/views/hr/components/HrEmployeeList.vue \
  erp-ui/test/hrEmployeeMasterFields.test.js
git commit -m "feat: guide HR users through missing fields"
```

### Task 4: Unify employee status values at the server write and read boundaries

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeStatusCatalog.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeStatusCatalogTest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/SigningProfileNormalizer.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/SigningProfileNormalizerTest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeFieldRegistry.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java:1171`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java:109-116,298-301`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml:259,396`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeMapperBindingTest.java`
- Modify: `erp-ui/src/views/system/user/index.vue`
- Modify: `erp-ui/test/hrEmployeeMasterFields.test.js`

- [ ] **Step 1: Add failing catalog and normalizer tests**

```java
class HrEmployeeStatusCatalogTest
{
    @Test void exposesCanonicalValuesAndMapsOnlyTheKnownLegacyValue()
    {
        assertThat(HrEmployeeStatusCatalog.values()).containsExactly("待入职","试用","正式","待离职","离职","停薪留职");
        assertThat(HrEmployeeStatusCatalog.normalizeForRead("在职")).isEqualTo("正式");
        assertThat(HrEmployeeStatusCatalog.normalizeForWrite(" 在职 ")).isEqualTo("正式");
        assertThatThrownBy(() -> HrEmployeeStatusCatalog.normalizeForWrite("临时状态"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("员工状态不受支持");
    }
}
```

In `SigningProfileNormalizerTest`, assert `profile.setEmployeeStatus("在职")` becomes `正式` and an unknown nonblank value throws.

- [ ] **Step 2: Add failing SQL and frontend assertions**

For both system-user and HR employee queries, require canonical `正式` to match historical `在职` rows:

```java
assertThat(boundSql.getSql()).contains("p.employee_status in ('正式','在职')");
```

In `hrEmployeeMasterFields.test.js`, assert the system user page imports `getHrEmployeeFormOptions`, has no hard-coded `employeeStatusOptions: [`, the two employee-status selects do not contain `allow-create`, and unknown historical values render the text `状态待规范` through `employeeStatusNeedsNormalization`.

- [ ] **Step 3: Run focused backend and frontend tests and verify they fail**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeStatusCatalogTest,SigningProfileNormalizerTest,HrEmployeeFieldRegistryTest,HrEmployeeMapperBindingTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs hrEmployeeMasterFields.test.js
```

Expected: catalog compilation fails, status queries use equality, and the system user page still permits arbitrary values.

- [ ] **Step 4: Add the canonical catalog**

```java
package com.erp.system.support;

import java.util.List;
import com.erp.common.core.exception.ServiceException;

public final class HrEmployeeStatusCatalog
{
    private static final List<String> VALUES=List.of("待入职","试用","正式","待离职","离职","停薪留职");
    private HrEmployeeStatusCatalog(){}
    public static List<String> values(){return VALUES;}
    public static String normalizeForRead(String value)
    {
        String normalized=trim(value);
        return "在职".equals(normalized)?"正式":normalized;
    }
    public static String normalizeForWrite(String value)
    {
        String normalized=normalizeForRead(value);
        if(normalized==null||VALUES.contains(normalized))return normalized;
        throw new ServiceException("员工状态不受支持: "+normalized);
    }
    private static String trim(String value)
    {if(value==null)return null;String result=value.trim();return result.isEmpty()?null:result;}
}
```

- [ ] **Step 5: Apply the catalog to writes, coverage, output, and lifecycle creation**

At the start of `SigningProfileNormalizer.normalize`, call the catalog at the existing write boundary:

```java
profile.setEmployeeStatus(HrEmployeeStatusCatalog.normalizeForWrite(profile.getEmployeeStatus()));
```

Use `HrEmployeeStatusCatalog.normalizeForRead` in `HrEmployeeFieldRegistry.isRequiredForCompleteness` and when `HrEmployeeProfileServiceImpl` maps employee status to list/detail DTOs. Replace `optionValues("试用","正式","离职")` with `optionValues(HrEmployeeStatusCatalog.values().toArray(String[]::new))`. Change the lifecycle-created default at line 1171 from `在职` to `正式`.

In `SysUserServiceImpl`, normalize only the status on system-user reads after derivation:

```java
private SysUser normalizeEmployeeStatusForRead(SysUser user)
{
    if(user!=null&&user.getProfile()!=null)
        user.getProfile().setEmployeeStatus(
                HrEmployeeStatusCatalog.normalizeForRead(user.getProfile().getEmployeeStatus()));
    return user;
}
```

Call it for every element returned by `selectUserList` and around the result of `selectUserById`. Do not apply the write validator to read paths, so an unknown historical value remains visible for cleanup rather than breaking the page.

For query compatibility, replace each direct employee-status equality in `SysUserMapper.xml` with:

```xml
<if test="employeeStatus != null and employeeStatus != ''">
    <choose>
        <when test="employeeStatus == '正式'">and p.employee_status in ('正式','在职')</when>
        <otherwise>and p.employee_status = #{employeeStatus}</otherwise>
    </choose>
</if>
```

Do not run a bulk database rewrite in this change.

- [ ] **Step 6: Load the same status options in the system user page**

Import `getHrEmployeeFormOptions` from `@/api/hr/employee`. During page creation, load `response.data.enumOptions.employeeStatus`, normalize object/string options to strings, and replace the hard-coded list. Track `employeeStatusOptionsLoaded` so an async request does not temporarily flag every status. Remove `allow-create` from the query and form employee-status selects only; unrelated extensible dictionary fields remain unchanged.

Add:

```javascript
employeeStatusNeedsNormalization(value) {
  return this.employeeStatusOptionsLoaded && Boolean(value) &&
    !this.employeeStatusOptions.some(item => item === value || item.value === value)
}
```

For an unknown historical value, append a warning tag `状态待规范` in the table and disable the form's employee-status select while showing the original value as plain text. Canonical values remain selectable according to the existing page permission and lifecycle behavior.

- [ ] **Step 7: Run focused tests**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeStatusCatalogTest,SigningProfileNormalizerTest,HrEmployeeFieldRegistryTest,HrEmployeeMapperBindingTest,SysUserServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
cd erp-ui && node scripts/run-node-tests.cjs hrEmployeeMasterFields.test.js hrMenuPermissions.test.js
```

Expected: writes accept only canonical values, `在职` reads and filters as `正式`, and both employee pages use the server options without free creation.

- [ ] **Step 8: Commit status unification**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeStatusCatalog.java \
  erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeStatusCatalogTest.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/SigningProfileNormalizer.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/SigningProfileNormalizerTest.java \
  erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeFieldRegistry.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrEmployeeProfileServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/HrLifecycleServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeeMapperBindingTest.java \
  erp-ui/src/views/system/user/index.vue erp-ui/test/hrEmployeeMasterFields.test.js
git commit -m "fix: unify employee status values"
```

### Task 5: Render legacy import results as safe structured plain text

**Files:**
- Create: `erp-ui/src/utils/importResult.js`
- Modify: `erp-ui/src/components/ExcelImportDialog/index.vue`
- Create: `erp-ui/test/excelImportDialogSafety.test.js`

- [ ] **Step 1: Write failing parser and component contract tests**

```javascript
const assert = require("assert")
const { parseLegacyImportResult, failureCsv } = requireModule("src/utils/importResult.js")

const result = parseLegacyImportResult("成功导入 2 条<br/>第 3 行：手机号错误<br><script>alert(1)</script>第 4 行：工号重复")
assert.strictEqual(result.successCount, 2)
assert.strictEqual(result.failureCount, 2)
assert.deepStrictEqual(result.failures.map(item => item.line), [3, 4])
assert.ok(!result.failures.some(item => item.reason.includes("<script>")))
assert.ok(failureCsv(result.failures).includes('3,"手机号错误"'))
assert.ok(!dialogSource.includes("dangerouslyUseHTMLString"))
assert.ok(dialogSource.includes("parseLegacyImportResult"))
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs excelImportDialogSafety.test.js
```

Expected: the utility does not exist and the component inserts `response.msg` as trusted HTML.

- [ ] **Step 3: Add the pure legacy-result parser**

Implement and export:

```javascript
export function plainImportLines(message) {
  return String(message || "")
    .replace(/<br\s*\/?\s*>/gi, "\n")
    .replace(/<[^>]*>/g, "")
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
}

export function parseLegacyImportResult(message) {
  const lines = plainImportLines(message)
  const success = lines.join(" ").match(/成功(?:导入)?\s*(\d+)\s*条/)
  const failures = lines.map(text => {
    const match = text.match(/第\s*(\d+)\s*行[：:]?\s*(.*)/)
    return match ? { line: Number(match[1]), reason: match[2] || "导入失败" } : null
  }).filter(Boolean)
  return { successCount: success ? Number(success[1]) : 0, failureCount: failures.length, failures, lines }
}

export function failureCsv(failures) {
  const quote = value => `"${String(value == null ? "" : value).replace(/"/g, '""')}"`
  return ["行号,原因", ...(failures || []).map(item => `${item.line},${quote(item.reason)}`)].join("\r\n")
}
```

The parser is a compatibility boundary, not a general HTML sanitizer. It deliberately discards every tag and renders only text.

- [ ] **Step 4: Replace the unsafe alert with a small result dialog**

Add `resultVisible`, `importResult`, and `showImportResult(response.msg)`. Render success/failure counts and at most the first 20 failure rows using normal Vue interpolation. If failures exceed 20, show `其余 N 条请下载失败清单`.

Implement `downloadFailures` with a UTF-8 BOM `Blob`, an object URL, a temporary anchor, and `URL.revokeObjectURL`. The filename is generated with `this.exportFileName("员工导入失败清单")`. Never use `v-html` or `dangerouslyUseHTMLString`.

- [ ] **Step 5: Run the safety test**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs excelImportDialogSafety.test.js
```

Expected: script-like input appears only as plain text, counts and line reasons are structured, and the failure CSV contains the same rows.

- [ ] **Step 6: Commit the safe import result**

```bash
git add erp-ui/src/utils/importResult.js erp-ui/src/components/ExcelImportDialog/index.vue \
  erp-ui/test/excelImportDialogSafety.test.js
git commit -m "fix: render import results as plain text"
```

### Task 6: Persist only safe employee-list preferences per user and permission fingerprint

**Files:**
- Create: `erp-ui/src/utils/hrEmployeePreferences.js`
- Modify: `erp-ui/src/views/hr/components/HrEmployeeList.vue`
- Create: `erp-ui/test/hrEmployeePreferences.test.js`

- [ ] **Step 1: Write failing preference boundary tests**

```javascript
const allowedColumns = ["contractEndDate", "legalEntity"]
const saved = sanitizeHrEmployeePreferences({
  advancedFilterOpen: true,
  selectedOptionalColumns: ["contractEndDate", "phoneNumber", "unknown"],
  pageSize: 50,
  keyword: "张三",
  employeeName: "张三",
  phoneNumber: "13800138000"
}, allowedColumns)
assert.deepStrictEqual(saved, {
  advancedFilterOpen: true,
  selectedOptionalColumns: ["contractEndDate"],
  pageSize: 50
})
assert.notStrictEqual(preferenceKey(7, ["hr:employee:list"]), preferenceKey(8, ["hr:employee:list"]))
assert.notStrictEqual(preferenceKey(7, ["hr:employee:list"]), preferenceKey(7, ["hr:employee:export"]))
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs hrEmployeePreferences.test.js
```

Expected: the preference module does not exist.

- [ ] **Step 3: Implement the allowlisted preference store**

Export `preferenceKey`, `sanitizeHrEmployeePreferences`, `loadHrEmployeePreferences`, and `saveHrEmployeePreferences`. The key format is:

```javascript
`erp:hr-employee-preferences:v1:${Number(userId) || 0}:${permissionFingerprint(permissions)}`
```

The permission fingerprint is a deterministic string produced from sorted permission codes with a 32-bit FNV-1a implementation in this module. The persisted JSON contains exactly:

```javascript
{
  advancedFilterOpen: Boolean(value.advancedFilterOpen),
  selectedOptionalColumns: uniqueAllowedColumns,
  pageSize: [10, 20, 30, 50, 100].includes(Number(value.pageSize)) ? Number(value.pageSize) : 10
}
```

On malformed JSON, remove that key and return defaults. Never accept arbitrary keys from storage.

- [ ] **Step 4: Integrate preferences without persisting business filters**

In `HrEmployeeList.vue`, use `this.$store.getters.id` and `this.$store.getters.permissions`. Before the first `getList`, load preferences and apply only `advancedFilterOpen`, `selectedOptionalColumns`, and `queryParams.pageSize`.

Watch those three values and save the sanitized object. Do not persist `queryParams.keyword`, `deptId`, employee status/category, todo filters, user ID, names, or any masked/sensitive values. A changed account or permission list naturally uses a different key.

- [ ] **Step 5: Run preference and employee tests**

Run:

```bash
cd erp-ui && node scripts/run-node-tests.cjs hrEmployeePreferences.test.js hrEmployeeMasterFields.test.js
```

Expected: only allowlisted preferences survive, account/permission scopes differ, malformed storage is ignored, and the first list request uses the restored page size.

- [ ] **Step 6: Commit preference persistence**

```bash
git add erp-ui/src/utils/hrEmployeePreferences.js \
  erp-ui/src/views/hr/components/HrEmployeeList.vue \
  erp-ui/test/hrEmployeePreferences.test.js
git commit -m "feat: remember safe HR list preferences"
```

### Task 7: Run the batch-two verification gate

**Files:**
- Test only; do not change production files unless a failure is directly caused by this batch.

- [ ] **Step 1: Run the status backend regression set**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeStatusCatalogTest,SigningProfileNormalizerTest,HrEmployeeFieldRegistryTest,HrEmployeeProfileServiceImplTest,HrEmployeeMapperBindingTest,SysUserServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: BUILD SUCCESS with zero failures and zero errors.

- [ ] **Step 2: Run the focused frontend contracts**

```bash
cd erp-ui && node scripts/run-node-tests.cjs \
  hrWorkbenchUx.test.js hrEmployeeMasterFields.test.js hrMenuPermissions.test.js \
  excelImportDialogSafety.test.js hrEmployeePreferences.test.js
```

Expected: five files run with zero failures.

- [ ] **Step 3: Run the complete frontend suite**

```bash
cd erp-ui && npm test
```

Expected: every Node contract test passes.

- [ ] **Step 4: Build the production frontend**

```bash
cd erp-ui && npm run build:prod
```

Expected: production build exits 0; existing bundle-size warnings are acceptable, compilation errors are not.

- [ ] **Step 5: Perform the desktop acceptance sweep**

With an HR test account:

```text
1. Click 资料待补齐 -> the first list request contains completenessStatus=INCOMPLETE.
2. Click 员工总数 -> the completeness filter is absent.
3. Verify 员工平均档案覆盖度 is not clickable.
4. Open an incomplete employee -> counts and the first three missing labels match the API.
5. Toggle 仅看缺失项 -> only editable missing inputs remain; derived/workflow items show their source.
6. Simulate a failed list request -> error and 重试 appear instead of 暂无员工档案.
7. Import a file with failed rows -> result is plain text and a CSV failure list can be downloaded.
8. Change columns/page size, reload -> those preferences return; search text does not.
```

Expected: all eight checks pass without opening a new module or exposing an unmasked value.

- [ ] **Step 6: Close the verification gate without a generic commit**

If a directly related repair was needed, return to the owning task, rerun that task's focused test, and amend its commit. Do not create a broad verification-only commit.
