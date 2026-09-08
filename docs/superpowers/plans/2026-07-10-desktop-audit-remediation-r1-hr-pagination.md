# Desktop Audit Remediation R1 HR Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make HR workbench search, advanced filters, task queues, totals, and pagination operate over the complete authorized result set instead of filtering only the current server page.

**Architecture:** Bind HR-only request fields into `SysUser.params` before PageHelper starts, apply those fields in the existing authorized user SQL, and add one aggregate stats query using the same data-scope boundary. The Vue workbench sends its active task to the server, renders returned rows directly, and obtains task-card counts from the stats endpoint.

**Tech Stack:** Java 17, Spring MVC, MyBatis XML, PageHelper, Vue 2, Element UI, JUnit 5, Node contract/behavior tests, Maven.

---

## Task 1: Bind and validate HR query parameters before pagination

**Files:**

- Create: `erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeQuerySupport.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeQuerySupportTest.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrCompletenessController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java`

- [ ] **Step 1: Write failing support tests**

Cover trimmed `keyword`/`deptKeyword`, allowed completeness/account/task values, ignored unknown enum values, and null-safe requests.

```java
MockHttpServletRequest request = new MockHttpServletRequest();
request.setParameter("keyword", "  张三  ");
request.setParameter("hrTask", "contractDue");
SysUser user = new SysUser();

HrEmployeeQuerySupport.apply(user, request);

assertThat(user.getParams())
    .containsEntry("hrKeyword", "张三")
    .containsEntry("hrTask", "contractDue");
```

- [ ] **Step 2: Run and verify RED**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeQuerySupportTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails because the support class does not exist.

- [ ] **Step 3: Implement the allowlisted binder**

Map request names to internal SQL parameter names:

- `keyword` → `hrKeyword`
- `deptKeyword` → `hrDeptKeyword`
- `completenessStatus` → `hrCompletenessStatus` (`complete`, `incomplete`, `below80`)
- `accountStatus` → `hrAccountStatus` (`enabled`, `disabled`, `unprofiled`)
- `hrTask` → `hrTask` (`all`, `incomplete`, `onboarding`, `contractDue`, `offboarding`)

Never copy arbitrary request keys into `params`, because `${params.dataScope}` shares the same map.

- [ ] **Step 4: Apply query binding before `startPage()` in all three list controllers**

```java
shopDeptFilterSupport.applyTo(user, request);
HrEmployeeQuerySupport.apply(user, request);
startPage();
```

- [ ] **Step 5: Re-run support and controller source tests**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeQuerySupportTest,HrPersonnelControllerSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Task 2: Apply HR filters in SQL and return accurate task stats

**Files:**

- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/HrEmployeeTaskStats.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserMapper.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeePaginationSourceTest.java`
- Modify: the three HR controllers

- [ ] **Step 1: Write a failing mapper/service/controller contract test**

The test must assert:

1. list SQL contains `params.hrKeyword`, `params.hrDeptKeyword`, `params.hrCompletenessStatus`, `params.hrAccountStatus`, and `params.hrTask`;
2. keyword searches name, login, phone, and employee number;
3. department keyword searches department and profile organization levels;
4. completeness and task filters are in SQL, not Java post-filtering;
5. `selectHrEmployeeTaskStats` exists in mapper/service and the service method has `@DataScope(deptAlias = "d", userAlias = "u")`;
6. all three controllers expose `/stats` with their own list permission.

- [ ] **Step 2: Run and verify RED**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeePaginationSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Add reusable SQL fragments**

Create a 13-field completeness expression for employee number, phone, ID type/number, entry date, contract type/start/end, social type, emergency contact/phone, bank name/account.

Use server rules:

- `complete`: 13 filled fields
- `incomplete`: fewer than 13
- `below80`: fewer than 11 filled fields
- `contractDue`: contract end date from today through 30 days
- `offboarding`: employee status is `离职`/`待离职` while account status is not disabled

Split the SQL into common HR filters and active-task filter. List queries include both; stats include common filters but intentionally exclude the active task so every card remains comparable.

- [ ] **Step 4: Add the data-scoped aggregate stats query**

Return a typed object with `total`, `incomplete`, `onboarding`, `contractDue`, and `offboarding`. The stats query must reuse the same department filter and `${params.dataScope}` boundary as the list.

- [ ] **Step 5: Add `/stats` to all HR controllers**

Apply shop scope and query binding exactly as the list does, but do not call `startPage()` for the aggregate query.

- [ ] **Step 6: Validate XML and run backend tests**

```bash
xmllint --noout erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml
mvn -pl erp-modules/erp-system -am \
  -Dtest=HrEmployeeQuerySupportTest,HrEmployeePaginationSourceTest,HrPersonnelControllerSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Task 3: Remove current-page filtering from the Vue workbench

**Files:**

- Modify: `erp-ui/src/api/hr/employee.js`
- Create: `erp-ui/src/views/hr/components/hrQuery.js`
- Modify: `erp-ui/src/views/hr/components/HrEmployeeList.vue`
- Modify: `erp-ui/test/hrWorkbenchUx.test.js`
- Create: `erp-ui/test/hrPaginationUx.test.js`

- [ ] **Step 1: Write failing query behavior and source tests**

Load `hrQuery.js` with `vm` and test:

```js
assert.deepStrictEqual(
  normalize(buildHrListParams({ pageNum: 3, keyword: '张三' }, 'incomplete')),
  { pageNum: 3, keyword: '张三', hrTask: 'incomplete' }
)
assert.ok(!Object.prototype.hasOwnProperty.call(buildHrListParams({ pageNum: 1 }, 'all'), 'hrTask'))
```

Source contracts:

- template loops over `displayRows`, not `filteredRows`;
- list header displays `total`, not `filteredRows.length`;
- setting a task resets `pageNum` and calls `getList()`;
- API defines employee/onboarding/completeness stats calls;
- export uses the same server query builder;
- `matchesLocalFilters` and `matchesActiveTask` are removed.

- [ ] **Step 2: Run and verify RED**

```bash
cd erp-ui
node test/hrPaginationUx.test.js
node test/hrWorkbenchUx.test.js
```

- [ ] **Step 3: Implement shared query helpers and stats APIs**

`buildHrListParams` adds `hrTask` only when it is not `all`. `normalizeHrTaskStats` returns numeric zero defaults and uses list total only as a fallback when stats are unavailable.

- [ ] **Step 4: Render server rows directly**

Use `displayRows() { return this.rows }`. Replace all current-page filtering and counts. The header must say `共 {{ total }} 条`.

- [ ] **Step 5: Reload server data on task selection**

```js
setActiveTask(task) {
  if (this.activeTask === task) return
  this.activeTask = task
  this.queryParams.pageNum = 1
  this.getList()
}
```

- [ ] **Step 6: Fetch list and stats with graceful stats fallback**

The list request remains authoritative. A stats failure must not hide a valid list response; retain last/fallback counts and let the normal request interceptor report the error.

- [ ] **Step 7: Run frontend tests and production build**

```bash
cd erp-ui
node test/hrPaginationUx.test.js
node test/hrPersonnelRoutes.test.js
node test/hrWorkbenchUx.test.js
npm run build:prod
```

## Task 4: Verification and commit

- [ ] **Step 1: Run focused combined regression**

```bash
cd erp-ui
node --test \
  test/desktopContextPolicy.test.js \
  test/desktopSecurityBoundary.test.js \
  test/hrPaginationUx.test.js \
  test/hrPersonnelRoutes.test.js \
  test/hrWorkbenchUx.test.js
```

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=HtmlSanitizerTest,SysNoticeServiceImplTest,HrEmployeeQuerySupportTest,HrEmployeePaginationSourceTest,HrPersonnelControllerSourceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 2: Inspect the final diff**

```bash
git diff --check
git status --short
```

- [ ] **Step 3: Commit the pagination fix**

```bash
git add docs/superpowers/plans/2026-07-10-desktop-audit-remediation-r1-hr-pagination.md \
  erp-modules/erp-system/src/main/java/com/erp/system/support/HrEmployeeQuerySupport.java \
  erp-modules/erp-system/src/main/java/com/erp/system/domain/HrEmployeeTaskStats.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrEmployeeProfileController.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrCompletenessController.java \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/HrOnboardingController.java \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserMapper.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserService.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml \
  erp-modules/erp-system/src/test/java/com/erp/system/support/HrEmployeeQuerySupportTest.java \
  erp-modules/erp-system/src/test/java/com/erp/system/mapper/HrEmployeePaginationSourceTest.java \
  erp-ui/src/api/hr/employee.js \
  erp-ui/src/views/hr/components/hrQuery.js \
  erp-ui/src/views/hr/components/HrEmployeeList.vue \
  erp-ui/test/hrWorkbenchUx.test.js \
  erp-ui/test/hrPaginationUx.test.js
git commit -m "fix: paginate hr workbench on server results"
```
