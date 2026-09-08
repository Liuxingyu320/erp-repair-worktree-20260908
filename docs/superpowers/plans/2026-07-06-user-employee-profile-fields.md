# User Employee Profile Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add full employee profile fields to user management, including storage, user create/edit/detail, list/search, import/export, and employee import script support.

**Architecture:** Keep `sys_user` as the account table and add `sys_user_profile` as a one-to-one employee profile table. `SysUser` owns a `profile` object plus a small set of derived display fields for organization and position. The system module writes profile data transactionally with user data; frontend forms bind profile fields under `form.profile`.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML, RuoYi-style Excel annotations, Vue 2, Element UI, Node source tests, Maven/JUnit.

---

## File Structure

- Create `sql/erp_user_employee_profile_20260706.sql`: database migration for `sys_user_profile`.
- Create `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`: employee profile domain and Excel field declarations.
- Modify `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java`: add `profile`, derived display fields, search fields, and employee Excel bridge getters/setters.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java`: MyBatis mapper interface for profile upsert/query.
- Create `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`: profile SQL mappings.
- Modify `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`: join profile and post data for list/detail/search.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java`: insert/update/import profile data and synchronize departure status.
- Modify `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java`: service-level failing tests for profile insert/update/import.
- Create `erp-ui/test/userEmployeeProfileFields.test.js`: frontend/source contract test for user employee profile UI.
- Modify `erp-ui/test/exportUx.test.js`: assert user Excel columns expose employee fields.
- Modify `erp-ui/src/views/system/user/index.vue`: list/search and grouped form fields.
- Modify `erp-ui/src/views/system/user/view.vue`: full grouped detail drawer.
- Modify `scripts/erp_employee_importer.py`: write profile fields instead of structure in `remark`.
- Modify `scripts/test_employee_importer.py`: assert profile upsert behavior.

## Task 1: Database And Domain Contract

**Files:**
- Create: `sql/erp_user_employee_profile_20260706.sql`
- Create: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUserProfile.java`
- Modify: `erp-api/erp-api-system/src/main/java/com/erp/system/api/domain/SysUser.java`
- Test: `erp-ui/test/exportUx.test.js`

- [ ] **Step 1: Write failing source test for Excel/profile contract**

Add assertions to `erp-ui/test/exportUx.test.js`:

```js
assert.ok(
  userDomainSource.includes("private SysUserProfile profile") &&
    userDomainSource.includes("@Excel(name = \"工号\"") &&
    userDomainSource.includes("@Excel(name = \"员工状态\"") &&
    userDomainSource.includes("@Excel(name = \"银行卡号\""),
  "user exports should include the employee profile object and key employee profile columns"
)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node erp-ui/test/exportUx.test.js`

Expected: FAIL with message `user exports should include the employee profile object and key employee profile columns`.

- [ ] **Step 3: Add migration and domain classes**

Create `SysUserProfile` with date/string fields matching `sys_user_profile`, Excel annotations for imported/exported profile columns, and simple getters/setters.

Modify `SysUser` to add:

```java
private SysUserProfile profile;
private String employeeNo;
private String companyName;
private String deptLevel1Name;
private String deptLevel2Name;
private String deptLevel3Name;
private String storeName;
private String positionNames;
private String workYears;
private String companyYears;
```

Add Excel bridge getters/setters for every required import/export column so the existing `ExcelUtil<SysUser>` can still be used.

- [ ] **Step 4: Run contract test**

Run: `node erp-ui/test/exportUx.test.js`

Expected: PASS.

## Task 2: Mapper Contract And Profile Persistence

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysUserProfileMapper.java`
- Create: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserProfileMapper.xml`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysUserMapper.xml`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java`

- [ ] **Step 1: Write failing mapper SQL test**

Add a test to `SysUserServiceImplTest` that reads `SysUserMapper.xml` and `SysUserProfileMapper.xml` as strings and asserts:

```java
assertThat(userXml).contains("left join sys_user_profile p on p.user_id = u.user_id");
assertThat(userXml).contains("employee_status");
assertThat(profileXml).contains("insert into sys_user_profile");
assertThat(profileXml).contains("on duplicate key update");
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f erp-modules/pom.xml -pl erp-system -Dtest=SysUserServiceImplTest test`

Expected: FAIL because mapper XML does not contain profile joins or profile upsert SQL.

- [ ] **Step 3: Implement mapper interface and XML**

Create `SysUserProfileMapper` with:

```java
SysUserProfile selectUserProfileByUserId(Long userId);
SysUserProfile selectUserProfileByEmployeeNo(String employeeNo);
int insertUserProfile(SysUserProfile profile);
int updateUserProfile(SysUserProfile profile);
int upsertUserProfile(SysUserProfile profile);
```

Create XML resultMap and insert/update/upsert statements.

Update `SysUserMapper.xml` resultMap with `association property="profile"` mappings and add profile columns to `selectUserList` and `selectUserVo`.

- [ ] **Step 4: Run mapper test**

Run: `mvn -f erp-modules/pom.xml -pl erp-system -Dtest=SysUserServiceImplTest test`

Expected: PASS.

## Task 3: User Service Profile Writes

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserServiceImpl.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserServiceImplTest.java`

- [ ] **Step 1: Write failing insert/update/import tests**

Add tests:

```java
@Test
@DisplayName("新增用户时应同时保存员工档案")
void insertUserShouldSaveEmployeeProfile()

@Test
@DisplayName("修改用户时应更新员工档案并在离职时停用账号")
void updateUserShouldUpsertProfileAndDisableDepartedUser()

@Test
@DisplayName("导入用户时应按工号匹配并更新员工档案")
void importUserShouldMatchByEmployeeNoAndUpdateProfile()
```

Each proxy mapper should assert `upsertUserProfile` receives the expected `userId`, `employeeNo`, `employeeCategory`, and `entryDate`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -f erp-modules/pom.xml -pl erp-system -Dtest=SysUserServiceImplTest test`

Expected: FAIL because `SysUserServiceImpl` does not inject or call `SysUserProfileMapper`.

- [ ] **Step 3: Implement service behavior**

Inject `SysUserProfileMapper`. Add helpers:

```java
private void saveUserProfile(SysUser user, boolean newRecord)
private boolean isDeparted(SysUser user)
private SysUser findImportTarget(SysUser user)
```

Call `saveUserProfile` from `insertUser`, `updateUser`, and `importUser`; if `employeeStatus` is `离职`, set `user.status = "1"` before updating `sys_user`.

- [ ] **Step 4: Run service tests**

Run: `mvn -f erp-modules/pom.xml -pl erp-system -Dtest=SysUserServiceImplTest test`

Expected: PASS.

## Task 4: Frontend User Management UI

**Files:**
- Create: `erp-ui/test/userEmployeeProfileFields.test.js`
- Modify: `erp-ui/src/views/system/user/index.vue`
- Modify: `erp-ui/src/views/system/user/view.vue`

- [ ] **Step 1: Write failing frontend source test**

Create `erp-ui/test/userEmployeeProfileFields.test.js` with assertions for:

```js
assert.ok(userSource.includes('label="工号"') && userSource.includes('form.profile.employeeNo'))
assert.ok(userSource.includes('label="员工状态"') && userSource.includes('queryParams.employeeStatus'))
assert.ok(userSource.includes('label="开户银行"') && userSource.includes('form.profile.bankName'))
assert.ok(viewSource.includes('证件号码') && viewSource.includes('银行卡号') && viewSource.includes('employeeProfileGroups'))
```

- [ ] **Step 2: Run frontend test to verify it fails**

Run: `node erp-ui/test/userEmployeeProfileFields.test.js`

Expected: FAIL because user page and detail drawer do not expose employee profile fields.

- [ ] **Step 3: Implement user list/search/form/detail**

Modify `index.vue`:

- Add search fields for `employeeNo`, `employeeStatus`, `employeeCategory`, `entryDate`.
- Add columns for employee name, employee number, company, store, position, job grade, employee status, category, entry date.
- Initialize `form.profile` in `reset()`.
- Add grouped `<el-tabs>` sections for account, organization, personal, education, employment, contact/bank fields.

Modify `view.vue`:

- Add `employeeProfileGroups` computed list and render grouped fields.
- Display profile values via `info.profile`.

- [ ] **Step 4: Run frontend test**

Run: `node erp-ui/test/userEmployeeProfileFields.test.js`

Expected: PASS.

## Task 5: Employee Import Script Compatibility

**Files:**
- Modify: `scripts/erp_employee_importer.py`
- Modify: `scripts/test_employee_importer.py`

- [ ] **Step 1: Write failing script test**

Add assertions that `get_or_create_user` executes a `sys_user_profile` insert statement with an `on duplicate key update` clause and that `remark` no longer contains `员工类型:` or `入职:`.

- [ ] **Step 2: Run script test to verify it fails**

Run: `python3 scripts/test_employee_importer.py`

Expected: FAIL because profile upsert is not emitted yet.

- [ ] **Step 3: Implement profile upsert**

After obtaining `user_id`, execute:

```sql
insert into sys_user_profile(user_id, employee_category, entry_date, update_by, update_time, create_by, create_time)
values({user_id}, {employee_category}, {entry_date}, {IMPORT_BY}, now(), {IMPORT_BY}, now())
on duplicate key update employee_category=values(employee_category), entry_date=values(entry_date), update_by=values(update_by), update_time=now()
```

Keep `remark` limited to source/import audit text.

- [ ] **Step 4: Run script test**

Run: `python3 scripts/test_employee_importer.py`

Expected: PASS.

## Task 6: Final Verification

**Files:**
- All modified files.

- [ ] **Step 1: Run backend focused tests**

Run: `mvn -f erp-modules/pom.xml -pl erp-system -Dtest=SysUserServiceImplTest test`

Expected: PASS.

- [ ] **Step 2: Run frontend focused tests**

Run:

```bash
node erp-ui/test/userEmployeeProfileFields.test.js
node erp-ui/test/exportUx.test.js
node erp-ui/test/systemManagementUx.test.js
```

Expected: PASS.

- [ ] **Step 3: Run script tests**

Run: `python3 scripts/test_employee_importer.py`

Expected: PASS.

- [ ] **Step 4: Review changed files**

Run: `git status --short` and `git diff --stat`.

Expected: changes are limited to the employee profile implementation files.
