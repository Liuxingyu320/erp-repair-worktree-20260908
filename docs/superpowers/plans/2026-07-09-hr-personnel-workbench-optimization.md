# HR Personnel Workbench Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing first-pass HR pages into a usable HR workbench with spacious task metrics, searchable employee summaries, grouped detail drawers, and safer field layering.

**Architecture:** Keep the current backend HR endpoints and `SysUser + SysUserProfile` response shape. Refactor the Vue HR frontend into a small field config module, a main workbench/list component, and a grouped detail drawer so list, summary, and detail responsibilities are separated. Add static contract tests before implementation to lock the UX structure and sensitive-field rules.

**Tech Stack:** Vue 2, Element UI, existing `@/utils/request`, existing `ExcelImportDialog`, Node static tests through `erp-ui/scripts/run-node-tests.cjs`, Maven source-contract tests for backend controllers.

---

## File Structure

- Modify `erp-ui/src/views/hr/components/HrEmployeeList.vue`
  - Main workbench container.
  - Renders top task queue cards, spacious search bar, advanced filters, column settings, employee summary rows, pagination, import/export actions, and opens the detail drawer.
- Create `erp-ui/src/views/hr/components/hrFieldConfig.js`
  - Single source of truth for HR task cards, default list fields, optional columns, sensitive fields, completeness fields, and detail groups.
- Create `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`
  - Receives `visible`, `detail`, and helper functions from parent.
  - Shows summary, missing fields, quick actions, and full grouped profile details.
- Modify page wrappers:
  - `erp-ui/src/views/hr/employee/index.vue`
  - `erp-ui/src/views/hr/onboarding/index.vue`
  - `erp-ui/src/views/hr/completeness/index.vue`
  - `erp-ui/src/views/hr/importExport/index.vue`
  - Add mode labels and page-specific props without duplicating layout code.
- Modify `erp-ui/test/hrPersonnelRoutes.test.js`
  - Keep route/API coverage.
  - Add assertions that wrappers pass the right workbench modes.
- Create `erp-ui/test/hrWorkbenchUx.test.js`
  - Static UX contract for task queue, advanced filters, column settings, summary rows, grouped drawer, and sensitive-field exclusion from default list.

## Task 1: Frontend UX Contract Tests

**Files:**
- Modify: `erp-ui/test/hrPersonnelRoutes.test.js`
- Create: `erp-ui/test/hrWorkbenchUx.test.js`

- [ ] **Step 1: Add failing route-mode assertions**

Append these assertions to `erp-ui/test/hrPersonnelRoutes.test.js`:

```js
assert.ok(employeeView.includes('mode="employee"'), "employee page should use employee workbench mode")
assert.ok(onboardingView.includes('mode="onboarding"'), "onboarding page should use onboarding workbench mode")
assert.ok(completenessView.includes('mode="completeness"'), "completeness page should use completeness workbench mode")
assert.ok(importExportView.includes('mode="importExport"'), "import/export page should use import/export workbench mode")
```

- [ ] **Step 2: Create the failing workbench UX test**

Create `erp-ui/test/hrWorkbenchUx.test.js`:

```js
const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const componentPath = path.join(rootDir, "src/views/hr/components/HrEmployeeList.vue")
const drawerPath = path.join(rootDir, "src/views/hr/components/HrProfileDetailDrawer.vue")
const configPath = path.join(rootDir, "src/views/hr/components/hrFieldConfig.js")

assert.ok(fs.existsSync(componentPath), "HR workbench component should exist")
assert.ok(fs.existsSync(drawerPath), "HR profile drawer component should exist")
assert.ok(fs.existsSync(configPath), "HR field config should exist")

const component = fs.readFileSync(componentPath, "utf8")
const drawer = fs.readFileSync(drawerPath, "utf8")
const config = fs.readFileSync(configPath, "utf8")

assert.ok(component.includes("hr-task-strip"), "workbench should render a top task queue strip")
assert.ok(component.includes("hr-smart-search"), "workbench should render a smart search area")
assert.ok(component.includes("advancedFilterOpen"), "workbench should support advanced filters")
assert.ok(component.includes("columnSettingOpen"), "workbench should support column settings")
assert.ok(component.includes("hr-employee-row"), "workbench should render two-line employee summary rows")
assert.ok(component.includes("HrProfileDetailDrawer"), "workbench should use the grouped profile detail drawer")
assert.ok(component.includes("filteredRows"), "workbench should filter rows client-side for task queues and advanced filters")

assert.ok(config.includes("HR_TASKS"), "config should define HR task queue cards")
assert.ok(config.includes("DEFAULT_LIST_FIELDS"), "config should define default list fields")
assert.ok(config.includes("OPTIONAL_LIST_FIELDS"), "config should define optional list fields")
assert.ok(config.includes("SENSITIVE_FIELDS"), "config should define sensitive fields")
assert.ok(config.includes("DETAIL_GROUPS"), "config should define grouped detail fields")

for (const label of ["基础信息", "组织岗位", "身份户籍", "教育信息", "用工合同", "社保户籍", "联系人与银行", "附件与记录"]) {
  assert.ok(config.includes(label), `detail groups should include ${label}`)
  assert.ok(drawer.includes(label), `drawer should render ${label}`)
}

const defaultListSection = config.slice(config.indexOf("DEFAULT_LIST_FIELDS"), config.indexOf("OPTIONAL_LIST_FIELDS"))
for (const sensitive of ["idNumber", "bankAccount", "registeredResidence", "currentAddress"]) {
  assert.ok(!defaultListSection.includes(sensitive), `${sensitive} should not be a default list field`)
}

assert.ok(drawer.includes("缺失资料"), "drawer should show missing profile fields")
assert.ok(drawer.includes("详细资料"), "drawer should offer grouped full details")
assert.ok(drawer.includes("编辑档案"), "drawer should expose edit entry point")

console.log("hrWorkbenchUx tests passed")
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```bash
npm --prefix erp-ui run test -- hrPersonnelRoutes.test.js hrWorkbenchUx.test.js
```

Expected: FAIL because wrapper modes, `hrFieldConfig.js`, and `HrProfileDetailDrawer.vue` do not exist yet.

## Task 2: Field Configuration Module

**Files:**
- Create: `erp-ui/src/views/hr/components/hrFieldConfig.js`

- [ ] **Step 1: Implement field config**

Create `hrFieldConfig.js` with:

```js
export const HR_TASKS = [
  { key: "all", label: "全部员工", countKey: "total", type: "primary" },
  { key: "incomplete", label: "待补资料", countKey: "incomplete", type: "warning" },
  { key: "onboarding", label: "待确认入职", countKey: "onboarding", type: "success" },
  { key: "contractDue", label: "合同将到期", countKey: "contractDue", type: "danger" },
  { key: "offboarding", label: "离职待处理", countKey: "offboarding", type: "info" }
]

export const DEFAULT_LIST_FIELDS = [
  "nickName",
  "employeeNo",
  "phonenumber",
  "companyName",
  "storeName",
  "positionNames",
  "jobGrade",
  "employeeStatus",
  "employeeCategory",
  "entryDate",
  "completion",
  "accountStatus",
  "riskTags"
]

export const OPTIONAL_LIST_FIELDS = [
  { key: "departmentSupervisor", label: "部门主管" },
  { key: "directSupervisor", label: "直属主管" },
  { key: "contractStartDate", label: "合同起始日" },
  { key: "contractEndDate", label: "合同到期日" },
  { key: "contractType", label: "合同类型" },
  { key: "socialType", label: "社保类型" },
  { key: "socialSecurityLocation", label: "社保缴纳地" },
  { key: "housingFundLocation", label: "公积金缴纳地" },
  { key: "legalEntity", label: "法人单位" },
  { key: "recruitmentChannel", label: "招聘渠道" },
  { key: "leaveDate", label: "离职时间" }
]

export const SENSITIVE_FIELDS = [
  "idNumber",
  "bankAccount",
  "registeredResidence",
  "currentAddress"
]

export const COMPLETENESS_FIELDS = [
  ["employeeNo", "工号"],
  ["phonenumber", "手机号"],
  ["idType", "证件类型"],
  ["idNumber", "证件号码"],
  ["entryDate", "入职日期"],
  ["contractType", "合同类型"],
  ["socialType", "社保类型"],
  ["contractStartDate", "合同起始"],
  ["contractEndDate", "合同到期"],
  ["emergencyContact", "紧急联系人"],
  ["emergencyContactPhone", "紧急联系人电话"],
  ["bankName", "开户银行"],
  ["bankAccount", "银行卡号"]
]

export const DETAIL_GROUPS = [
  { title: "基础信息", fields: [["nickName", "姓名"], ["phonenumber", "手机号"], ["sex", "性别"], ["birthDate", "出生日期"], ["ethnicity", "民族"], ["maritalStatus", "婚姻状况"], ["nationality", "国籍"], ["foreignNationalFlag", "是否外籍"], ["healthStatus", "健康状况"], ["politicalStatus", "政治面貌"]] },
  { title: "组织岗位", fields: [["companyName", "所属公司"], ["deptLevel1Name", "1级部门"], ["deptLevel2Name", "2级部门"], ["deptLevel3Name", "3级部门"], ["storeName", "4级门店"], ["positionNames", "职位"], ["jobGrade", "职级"], ["departmentSupervisor", "部门主管"], ["directSupervisor", "直属主管"], ["legalEntity", "法人单位"]] },
  { title: "身份户籍", fields: [["idType", "证件类型"], ["idNumber", "证件号码"], ["bloodType", "血型"], ["registeredResidence", "户口所在地"], ["currentAddress", "现居住地址"]] },
  { title: "教育信息", fields: [["firstEducation", "第一学历"], ["firstDegree", "第一学位"], ["firstGraduationDate", "毕业时间"], ["firstGraduationSchool", "第一学历毕业学校"], ["firstMajor", "第一学历专业"], ["highestEducation", "最高学历"], ["highestDegree", "最高学位"], ["highestGraduationDate", "最高学历毕业时间"], ["highestGraduationSchool", "最高学历毕业学校"], ["highestMajor", "最高学历专业"]] },
  { title: "用工合同", fields: [["employeeStatus", "员工状态"], ["employeeCategory", "人员类别"], ["recruitmentChannel", "招聘渠道"], ["workStartDate", "参加工作时间"], ["workYears", "工龄"], ["entryDate", "入职时间"], ["probationPeriod", "试用期"], ["plannedRegularizationDate", "计划转正日期"], ["actualRegularizationDate", "实际转正日期"], ["companyYears", "司龄"], ["currentPositionStartDate", "本岗位任职日期"], ["contractStartDate", "合同起始日"], ["contractEndDate", "合同到期日"], ["contractType", "合同类型"], ["contractTerm", "合同期限"], ["renewalCount", "续签次数"], ["workLocation", "工作所在地"], ["workCityLevel", "城市级别"], ["attendanceMethod", "考勤方式"], ["leaveDate", "离职时间"]] },
  { title: "社保户籍", fields: [["householdType", "户口性质"], ["socialType", "社保类型"], ["socialSecurityLocation", "社保缴纳地"], ["housingFundLocation", "公积金缴纳地"]] },
  { title: "联系人与银行", fields: [["officePhone", "办公电话"], ["email", "邮箱"], ["emergencyContact", "紧急联系人"], ["emergencyContactRelation", "与紧急联系人关系"], ["emergencyContactPhone", "紧急联系人电话"], ["bankName", "开户银行"], ["bankAccount", "银行卡号"]] },
  { title: "附件与记录", fields: [["profileSource", "资料来源"], ["lastProfileUpdateBy", "最后更新人"], ["lastProfileUpdateTime", "最后更新时间"], ["createBy", "创建人"], ["createTime", "创建时间"], ["updateBy", "更新人"], ["updateTime", "更新时间"]] }
]
```

- [ ] **Step 2: Run UX test and verify remaining failure**

Run:

```bash
npm --prefix erp-ui run test -- hrWorkbenchUx.test.js
```

Expected: FAIL because the drawer and main component do not use the config yet.

## Task 3: Grouped Detail Drawer

**Files:**
- Create: `erp-ui/src/views/hr/components/HrProfileDetailDrawer.vue`

- [ ] **Step 1: Implement drawer component**

Create `HrProfileDetailDrawer.vue` with a Vue 2 component that:

- Accepts `visible`, `detail`, `missing-fields`, `completion-percent`, `profile-value`, `profile-raw-value`, and `dept-name` props.
- Emits `update:visible`, `edit`, `export`, and `refresh`.
- Renders summary cards, missing profile fields, quick actions, and `DETAIL_GROUPS` inside `el-tabs`.

Use these stable strings so tests can enforce structure: `缺失资料`, `详细资料`, `编辑档案`, and every `DETAIL_GROUPS` title.

- [ ] **Step 2: Run UX test and verify remaining failure**

Run:

```bash
npm --prefix erp-ui run test -- hrWorkbenchUx.test.js
```

Expected: FAIL because the main workbench has not been refactored yet.

## Task 4: Workbench Layout Refactor

**Files:**
- Modify: `erp-ui/src/views/hr/components/HrEmployeeList.vue`

- [ ] **Step 1: Replace default table-first layout with workbench layout**

Modify `HrEmployeeList.vue` to:

- Import `HrProfileDetailDrawer`.
- Import `HR_TASKS`, `OPTIONAL_LIST_FIELDS`, and `COMPLETENESS_FIELDS`.
- Render `hr-task-strip` top metric buttons.
- Render `hr-smart-search` as the primary search area.
- Render advanced filters behind `advancedFilterOpen`.
- Render column settings behind `columnSettingOpen`.
- Render summary rows with class `hr-employee-row`.
- Use computed `filteredRows` for active task and client-side filters.
- Preserve pagination and existing backend API calls.

- [ ] **Step 2: Preserve existing behavior**

Keep these existing functions or equivalents:

- `listRequest`
- `getList`
- `handleQuery`
- `resetQuery`
- `profile`
- `profileRawValue`
- `profileValue`
- `missingFields`
- `completionPercent`
- `openDetail`
- `handleExport`
- `handleTemplate`
- `handleImport`

- [ ] **Step 3: Run tests**

Run:

```bash
npm --prefix erp-ui run test -- hrWorkbenchUx.test.js
```

Expected: PASS.

## Task 5: Page Modes and Page-Specific Duties

**Files:**
- Modify: `erp-ui/src/views/hr/employee/index.vue`
- Modify: `erp-ui/src/views/hr/onboarding/index.vue`
- Modify: `erp-ui/src/views/hr/completeness/index.vue`
- Modify: `erp-ui/src/views/hr/importExport/index.vue`
- Modify: `erp-ui/test/hrPersonnelRoutes.test.js`

- [ ] **Step 1: Add explicit modes**

Set wrappers to pass:

```vue
mode="employee"
mode="onboarding"
mode="completeness"
mode="importExport"
```

- [ ] **Step 2: Tune wrapper props**

Use these page-specific props:

- Employee: `show-completeness`, `show-export`.
- Onboarding: `default-employee-status="待入职"`, `show-completeness`, `show-export`.
- Completeness: `show-completeness`, `show-export`, no import controls.
- Import/export: `show-completeness`, `show-import`, `show-template`, `show-export`.

- [ ] **Step 3: Run route and UX tests**

Run:

```bash
npm --prefix erp-ui run test -- hrPersonnelRoutes.test.js hrWorkbenchUx.test.js
```

Expected: PASS.

## Task 6: Full Verification

**Files:**
- No production edits unless tests expose defects.

- [ ] **Step 1: Run frontend HR tests**

Run:

```bash
npm --prefix erp-ui run test -- hrPersonnelRoutes.test.js hrWorkbenchUx.test.js
```

Expected: PASS.

- [ ] **Step 2: Run production frontend build**

Run:

```bash
npm --prefix erp-ui run build:prod
```

Expected: PASS with `DONE Build complete`.

- [ ] **Step 3: Run backend HR controller contract**

Run:

```bash
mvn -pl erp-modules/erp-system -am -Dtest=HrPersonnelControllerSourceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 4: Runtime smoke check**

If local services are still running, open `http://localhost:1025/` and verify the HR pages compile without frontend overlay errors. If browser automation is available, capture a screenshot of the employee page after login; if not, rely on the production build and static contract tests.
