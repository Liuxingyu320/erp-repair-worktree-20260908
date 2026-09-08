# User Profile Auto-Derived Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make organization hierarchy, position, department supervisor, work years, and company years read-only values derived from authoritative department, post, and date data throughout user management.

**Architecture:** Add one Spring service that owns all derivation rules and can enrich one user, a user list, or an unsaved preview payload. User queries and exports pass through that service, profile persistence stops writing derived columns, and the Vue form calls a permission-checked preview endpoint while rendering the fields as read-only.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, JUnit 5, AssertJ, Vue 2, Element UI, Axios, Node assertion tests, Maven.

---

## File map

- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileDerivationService.java`: the only component that resolves organization paths, positions, supervisors, and year/month durations.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java`: deterministic rule tests using a fixed date.
- Modify `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`: expose non-persistent derivation warnings in API responses.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserService.java`: expose the preview operation.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java`: enrich list/detail results and delegate preview calculation.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java`: add the permission-checked read-only preview endpoint.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserDerivedPreviewControllerTest.java`: verify scope validation occurs before preview.
- Modify `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`: stop insert/update writes to derived columns while retaining legacy reads.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserProfileDerivedWriteBoundaryTest.java`: protect the persistence boundary.
- Modify `erp-ui/src/api/system/user.js`: expose the preview API.
- Modify `erp-ui/src/views/system/user/index.vue`: render derived fields read-only, debounce preview calls, discard stale responses, and show warnings.
- Create `erp-ui/test/userProfileAutoDerivedFields.test.js`: protect the frontend contract.

The committed baseline does not contain the uncommitted HR workbench files present in the original working directory. The shared `ISysUserService.selectUserList` and `selectUserById` enrichment implemented here automatically supplies derived values to those callers after integration; this branch will not absorb or rewrite the user's unrelated uncommitted HR files.

### Task 1: Derived response model and pure derivation rules

**Files:**
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileDerivationService.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java`

- [ ] **Step 1: Write failing organization-path tests**

Create tests that construct real `SysDept`, `SysPost`, `SysUser`, and `SysUserProfile` objects and call a package-visible deterministic method:

```java
SysUserProfile result = service.derive(user, departments, posts, LocalDate.of(2026, 7, 10));

assertThat(result.getCompanyName()).isEqualTo("星河公司");
assertThat(result.getDeptLevel1Name()).isEqualTo("华东区");
assertThat(result.getDeptLevel2Name()).isEqualTo("上海区");
assertThat(result.getDeptLevel3Name()).isEqualTo("浦东区");
assertThat(result.getStoreName()).isEqualTo("陆家嘴店");
assertThat(result.getDepartmentSupervisor()).isEqualTo("门店店长");
assertThat(result.getDerivedWarnings()).isEmpty();
```

Add separate tests for a short hierarchy, a store ancestor above the selected department, a warehouse leaf, a missing company, a broken ancestor chain, and more than three ordinary nodes before a store. Invalid structures must clear legacy organization strings and return `组织结构待修复`.

- [ ] **Step 2: Run the new test and verify RED**

Run:

```bash
mvn -pl erp-modules/erp-system -am -Dtest=SysUserProfileDerivationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because `SysUserProfileDerivationService` and `derivedWarnings` do not exist.

- [ ] **Step 3: Add warning response fields and the minimal organization resolver**

Add a lazily initialized response-only list to `SysUserProfile`:

```java
private List<String> derivedWarnings = new ArrayList<>();

public List<String> getDerivedWarnings()
{
    return derivedWarnings;
}

public void setDerivedWarnings(List<String> derivedWarnings)
{
    this.derivedWarnings = derivedWarnings == null ? new ArrayList<>() : derivedWarnings;
}
```

Implement path construction from `ancestors` plus `deptId`, verify every parent link, ignore the synthetic ancestor `0`, find the nearest `COMPANY` and `STORE`, treat all non-`GROUP`/`COMPANY`/`STORE`/`WAREHOUSE` nodes before the store as ordinary departments, and search upward for the nearest nonblank leader. Clear all derived organization fields before every calculation so stored legacy text can never be used as fallback.

- [ ] **Step 4: Run the organization tests and verify GREEN**

Run the command from Step 2.

Expected: all organization-path cases pass.

- [ ] **Step 5: Write failing position and duration tests**

Add tests that assert active selected posts are sorted by `postSort`, then `postName`, and joined with `、`; disabled and unselected posts are excluded. Add fixed-date assertions:

```java
assertThat(result.getPositionNames()).isEqualTo("店长、培训师");
assertThat(result.getWorkYears()).isEqualTo("6年6个月");
assertThat(result.getCompanyYears()).isEqualTo("3年3个月");
```

Cover empty dates, future dates, and an employee whose company years stop at a valid leave date.

- [ ] **Step 6: Run the test and verify RED**

Run the command from Step 2.

Expected: failures show position and duration values are missing.

- [ ] **Step 7: Implement minimal position and duration derivation**

Use `Period.between(start, end)` and format only complete years/months as `X年Y个月`. For `employeeStatus == "离职"`, use `leaveDate` as the company-years endpoint when present; otherwise use the supplied current date. Future or reversed dates return no duration and add `档案日期异常` once.

- [ ] **Step 8: Run the derivation tests and verify GREEN**

Run the command from Step 2.

Expected: all derivation tests pass.

- [ ] **Step 9: Commit Task 1**

```bash
git add erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileDerivationService.java erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileDerivationServiceTest.java
git commit -m "feat: derive user profile display fields"
```

### Task 2: User service enrichment and persistence write boundary

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserProfileDerivedWriteBoundaryTest.java`

- [ ] **Step 1: Write failing service enrichment tests**

Inject a proxy `SysUserProfileDerivationService` dependency or a real derivation service with mapper proxies, then assert:

```java
List<SysUser> result = userService.selectUserList(new SysUser());
assertThat(result.get(0).getProfile().getCompanyName()).isEqualTo("星河公司");

SysUser detail = userService.selectUserById(9L);
assertThat(detail.getProfile().getPositionNames()).isEqualTo("店长、培训师");
```

Also assert list enrichment calls the department mapper once for the whole list, not once per row.

- [ ] **Step 2: Run the service tests and verify RED**

```bash
mvn -pl erp-modules/erp-system -am -Dtest=SysUserServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: new assertions fail because list/detail results still expose stored values.

- [ ] **Step 3: Wire derivation into service reads**

Change `selectUserList` to enrich the returned collection in one batch and `selectUserById` to enrich one user. For list rows, reuse the existing aggregated `SysUser.postNames`; for one-user detail, load posts once with `postMapper.selectPostsByUserName(userName)`. Do not enrich the login-only `selectUserByUserName` path.

Add the interface method:

```java
SysUserProfile previewDerivedProfile(SysUser user);
```

and delegate it to the derivation service.

- [ ] **Step 4: Run the service tests and verify GREEN**

Run the command from Step 2.

Expected: all `SysUserServiceImplTest` tests pass.

- [ ] **Step 5: Write the failing profile mapper boundary test**

Read `SysUserProfileMapper.xml`, isolate the insert and update statements, and assert that these write fragments do not contain:

```text
company_name
dept_level1_name
dept_level2_name
dept_level3_name
store_name
position_names
department_supervisor
work_years
company_years
```

The test must separately assert that the select fragment still reads those columns for backward-compatible audits.

- [ ] **Step 6: Run the mapper test and verify RED**

```bash
mvn -pl erp-modules/erp-system -am -Dtest=SysUserProfileDerivedWriteBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failure because current insert/update SQL writes all nine derived columns.

- [ ] **Step 7: Remove derived columns from insert/update SQL only**

Keep the result map and select columns unchanged. Remove each derived column and matching parameter from `insertUserProfile`, and remove each derived assignment from `updateUserProfile`. Leave human-maintained fields in their current order.

- [ ] **Step 8: Run mapper and service tests and verify GREEN**

```bash
mvn -pl erp-modules/erp-system -am -Dtest=SysUserProfileDerivedWriteBoundaryTest,SysUserServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass.

- [ ] **Step 9: Commit Task 2**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserService.java erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java erp-modules/erp-system/src/test/java/com/erp/system/mapper/SysUserProfileDerivedWriteBoundaryTest.java
git commit -m "feat: apply derived fields across user reads"
```

### Task 3: Permission-checked preview endpoint

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserDerivedPreviewControllerTest.java`

- [ ] **Step 1: Write a failing controller order test**

Instantiate `SysUserController`, inject proxy services, and record calls. Calling `previewDerivedProfile(request)` must produce:

```java
assertThat(events).containsExactly("checkDeptDataScope:202", "previewDerivedProfile:202");
```

When `checkDeptDataScope` throws, assert that the preview service is never invoked.

- [ ] **Step 2: Run the controller test and verify RED**

```bash
mvn -pl erp-modules/erp-system -am -Dtest=SysUserDerivedPreviewControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because the controller endpoint does not exist.

- [ ] **Step 3: Add the preview endpoint**

Add:

```java
@RequiresPermissions("system:user:edit")
@PostMapping("/derived-preview")
public AjaxResult previewDerivedProfile(@RequestBody SysUser user)
{
    if (StringUtils.isNotNull(user.getDeptId()))
    {
        deptService.checkDeptDataScope(user.getDeptId());
    }
    return success(userService.previewDerivedProfile(user));
}
```

The endpoint performs no writes and returns the `SysUserProfile` result as `data`.

- [ ] **Step 4: Run the controller test and verify GREEN**

Run the command from Step 2.

Expected: both success-order and denied-scope tests pass.

- [ ] **Step 5: Commit Task 3**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserDerivedPreviewControllerTest.java
git commit -m "feat: add user derived profile preview"
```

### Task 4: User-management read-only preview UI

**Files:**
- Modify: `erp-ui/src/api/system/user.js`
- Modify: `erp-ui/src/views/system/user/index.vue`
- Create: `erp-ui/test/userProfileAutoDerivedFields.test.js`

- [ ] **Step 1: Write a failing frontend contract test**

Read the API and Vue source and assert all of the following strings/contracts exist:

```javascript
assert.ok(apiSource.includes("/system/user/derived-preview"))
assert.ok(userSource.includes("previewUserDerivedProfile"))
assert.ok(userSource.includes("scheduleDerivedPreview"))
assert.ok(userSource.includes("derivedPreviewSequence"))
assert.ok(userSource.includes(":disabled=\"true\""))
assert.ok(userSource.includes("根据归属部门自动匹配"))
assert.ok(userSource.includes("根据已选岗位自动匹配"))
assert.ok(userSource.includes("根据日期自动计算"))
```

Also assert the nine derived field inputs no longer use `placeholder="请输入..."`.

- [ ] **Step 2: Run the frontend test and verify RED**

```bash
npm test -- userProfileAutoDerivedFields.test.js
```

Expected: failure because the preview API and read-only behavior are absent.

- [ ] **Step 3: Add the API and read-only controls**

Export:

```javascript
export function previewUserDerivedProfile(data) {
  return request({
    url: "/system/user/derived-preview",
    method: "post",
    data
  })
}
```

Replace the organization, position, supervisor, work-years, and company-years text inputs with disabled inputs and small source hints. Keep human-maintained fields editable. Add an `el-alert` that joins and displays `form.profile.derivedWarnings` when nonempty.

- [ ] **Step 4: Add debounced preview with stale-response protection**

Track `derivedPreviewTimer` and `derivedPreviewSequence` in component state. Watch `form.deptId`, `form.postIds`, `form.profile.workStartDate`, `form.profile.entryDate`, `form.profile.leaveDate`, and `form.profile.employeeStatus`. After 250 ms, send only:

```javascript
{
  deptId: this.form.deptId,
  postIds: this.form.postIds,
  profile: {
    workStartDate: profile.workStartDate,
    entryDate: profile.entryDate,
    leaveDate: profile.leaveDate,
    employeeStatus: profile.employeeStatus
  }
}
```

Increment the sequence for each request and ignore responses whose sequence is not current. Clear the timer and invalidate pending responses when the dialog closes or the component is destroyed.

- [ ] **Step 5: Run the frontend test and verify GREEN**

Run the command from Step 2.

Expected: the new contract test passes.

- [ ] **Step 6: Run related user-management frontend tests**

```bash
npm test -- userProfileAutoDerivedFields.test.js userPostDisplaySearch.test.js userManagementScopeDisplay.test.js systemManagementUx.test.js userDeptTreeSearchUx.test.js
```

Expected: 5 test files run, 0 fail.

- [ ] **Step 7: Commit Task 4**

```bash
git add erp-ui/src/api/system/user.js erp-ui/src/views/system/user/index.vue erp-ui/test/userProfileAutoDerivedFields.test.js
git commit -m "feat: preview derived user profile fields"
```

### Task 5: Full verification and target-branch integration

**Files:**
- Verify all files changed in Tasks 1–4.
- Preserve unrelated dirty files in `/Users/liuxingyu/Desktop/备份/ERP-NEW`.

- [ ] **Step 1: Run the complete backend system-module reactor tests**

```bash
mvn -pl erp-modules/erp-system -am test -DskipTests=false
```

Expected: reactor build succeeds with zero test failures.

- [ ] **Step 2: Run the complete frontend node suite and classify only known environment exclusions**

```bash
npm test
```

Expected in this isolated worktree: all feature and ordinary frontend tests pass; the two already-recorded baseline tests may still fail only because `application-local.yml` and generated Android files are deliberately not tracked in Git. No new failure is acceptable.

- [ ] **Step 3: Run the production frontend build**

Reuse the installed dependency tree from the original workspace if necessary, then run:

```bash
npm run build:prod
```

Expected: exit code 0.

- [ ] **Step 4: Verify diff scope and commit state**

```bash
git status --short
git log --oneline 3166118..HEAD
git diff --check 3166118..HEAD
```

Expected: no uncommitted source changes, only the planned commits are present, and `git diff --check` reports no whitespace errors.

- [ ] **Step 5: Integrate into `6月13号` without staging unrelated working-tree changes**

The target worktree contains unrelated user changes. First inspect overlapping paths. If a normal merge is safe, merge the feature branch. If Git refuses because an overlapping target file is dirty, apply the feature diff to the working tree and index the feature patch only, leaving pre-existing user changes unstaged. Verify the staged patch contains only this feature before committing.

```bash
git diff --cached --name-status
git diff --cached --check
git commit -m "feat: derive user profile fields automatically"
```

- [ ] **Step 6: Re-run focused tests from the integrated target branch**

Run the derivation, service, controller, mapper, and frontend contract tests against `/Users/liuxingyu/Desktop/备份/ERP-NEW`.

Expected: all focused tests pass while pre-existing unrelated working-tree changes remain present and unstaged.
