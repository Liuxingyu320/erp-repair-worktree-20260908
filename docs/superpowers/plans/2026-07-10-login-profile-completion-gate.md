# Login Profile Completion Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require every non-admin account to complete the 15 employee-owned onboarding fields before the user can open the store/warehouse selector.

**Architecture:** Add one backend service as the authoritative completeness calculator and self-service writer. Expose the result through login info and dedicated profile-completion endpoints, and gate the shop-tree endpoint with the same service. On the Vue 2 client, persist completion state in Vuex, run the completion redirect before current shop-selection routing, and provide one responsive full-screen form.

**Tech Stack:** Java 17, Spring Boot, MyBatis, JUnit 5, AssertJ, Vue 2, Vuex, Vue Router, Element UI, Node assertion tests.

---

## File Structure

### Backend

- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysProfileCompletionFieldVo.java`: missing-field key and label contract.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysProfileCompletionRequest.java`: allowlisted self-service payload containing only 15 personal fields.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysProfileCompletionVo.java`: completion state, missing fields, editable values, masked completed values, and read-only HR summary.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserProfileCompletionService.java`: evaluate and save contract.
- Create `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileCompletionServiceImpl.java`: validation, merge, persistence, masking, and admin exemption.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java`: add current-user completion GET/PUT endpoints and refresh token cache after save.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java`: publish completion state from `getInfo`.
- Modify `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java`: reject shop-tree access for incomplete non-admin users.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileCompletionServiceImplTest.java`: service behavior and validation tests.
- Modify `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysProfileControllerTest.java`: authenticated endpoint contract tests.
- Create `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysProfileCompletionControllerTest.java`: login-info and shop-tree gate tests.

### Frontend

- Create `erp-ui/src/utils/profileCompletion.js`: route/error helpers independent of Vue.
- Create `erp-ui/src/views/profile-completion/profileCompletionFields.js`: 15-field UI metadata and select options.
- Create `erp-ui/src/views/profile-completion/profileCompletionValidation.js`: client validation matching backend rules.
- Create `erp-ui/src/views/profile-completion/index.vue`: responsive completion page.
- Modify `erp-ui/src/api/system/user.js`: completion GET/PUT API functions.
- Modify `erp-ui/src/store/modules/user.js`: completion state, mutations, and `GetInfo` population/reset.
- Modify `erp-ui/src/store/getters.js`: completion getters.
- Modify `erp-ui/src/router/index.js`: constant `/complete-profile` route.
- Modify `erp-ui/src/permission.js`: run completion gate before mobile normalization and shop selection.
- Modify `erp-ui/src/views/select-shop/index.vue`: handle backend `PROFILE_COMPLETION_REQUIRED` by clearing context and returning to completion.
- Create `erp-ui/test/profileCompletionGate.test.js`: validation, route helper, API, Vuex, route, page, and selector fallback assertions.

## Task 1: Backend Completion Contract and Service

**Files:**
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysProfileCompletionFieldVo.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysProfileCompletionRequest.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/SysProfileCompletionVo.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserProfileCompletionService.java`
- Create: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserProfileCompletionServiceImpl.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserProfileCompletionServiceImplTest.java`

- [ ] **Step 1: Write the failing completeness tests**

Cover these cases with mapper/service proxies or Mockito following existing system-service tests:

```java
@Test
void nonAdminWithoutProfileShouldRequireAllFields()
{
    SysProfileCompletionVo result = service.evaluate(20L);
    assertThat(result.isCompletionRequired()).isTrue();
    assertThat(result.getMissingFields()).extracting(SysProfileCompletionFieldVo::getKey)
        .containsExactly("nickName", "phonenumber", "sex", "birthDate", "idType", "idNumber",
            "registeredResidence", "currentAddress", "maritalStatus", "ethnicity",
            "emergencyContact", "emergencyContactRelation", "emergencyContactPhone",
            "bankName", "bankAccount");
}

@Test
void adminShouldAlwaysBeComplete()
{
    assertThat(service.evaluate(1L).isCompletionRequired()).isFalse();
}

@Test
void completeUserShouldPass()
{
    assertThat(service.evaluate(20L).isCompletionRequired()).isFalse();
}

@Test
void saveShouldMergeOnlyAllowlistedFieldsAndCreateMissingProfile()
{
    SysProfileCompletionVo result = service.save(20L, completeRequest(), "employee");
    assertThat(result.isCompletionRequired()).isFalse();
    assertThat(insertedProfile.getUserId()).isEqualTo(20L);
    assertThat(updatedUser.getDeptId()).isNull();
    assertThat(updatedUser.getStatus()).isNull();
}
```

Add validation tests for duplicate phone, unknown sex, future birth date, invalid resident ID checksum, incomplete emergency-contact trio, and non-numeric bank account.

- [ ] **Step 2: Run the new service test and confirm failure**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysUserProfileCompletionServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because the completion DTOs and service do not exist.

- [ ] **Step 3: Add request and response contracts**

`SysProfileCompletionRequest` must expose only:

```java
private String nickName;
private String phonenumber;
private String sex;
private Date birthDate;
private String idType;
private String idNumber;
private String registeredResidence;
private String currentAddress;
private String maritalStatus;
private String ethnicity;
private String emergencyContact;
private String emergencyContactRelation;
private String emergencyContactPhone;
private String bankName;
private String bankAccount;
```

`SysProfileCompletionVo` must contain:

```java
private boolean completionRequired;
private List<SysProfileCompletionFieldVo> missingFields = new ArrayList<>();
private SysProfileCompletionRequest values;
private Map<String, String> completedDisplayValues = new LinkedHashMap<>();
private Map<String, String> readonlySummary = new LinkedHashMap<>();
```

- [ ] **Step 4: Implement authoritative evaluate and save behavior**

The service contract is:

```java
public interface ISysUserProfileCompletionService
{
    SysProfileCompletionVo evaluate(Long userId);

    SysProfileCompletionVo save(Long userId, SysProfileCompletionRequest request, String operator);
}
```

Implementation rules:

1. Return complete immediately for `UserConstants.isAdmin(userId)`.
2. Load `SysUser` and persisted `SysUserProfile` separately; derived organization fields must not be persisted.
3. Trim text before validation.
4. Compute all 15 missing keys in fixed UI order.
5. Mask existing ID and bank values in `completedDisplayValues`; do not place complete sensitive values in `values`.
6. During save, merge non-null request values into current base user/profile values so omitted complete fields are preserved.
7. Validate the merged object atomically before any update.
8. Update only `nick_name`, `phonenumber`, `sex` in `sys_user`.
9. Insert a profile when absent; otherwise update the fully merged persisted profile so existing HR fields are not nulled by `SysUserProfileMapper.updateUserProfile`.
10. Set audit fields to the current username and never log request contents.

Use `ServiceException` for field validation failures with user-facing messages. For phone uniqueness, build a `SysUser` with current `userId` and merged phone, then call `checkPhoneUnique`.

- [ ] **Step 5: Run service tests and confirm pass**

Run the Task 1 Maven command again.

Expected: `SysUserProfileCompletionServiceImplTest` passes with zero failures.

## Task 2: Backend Endpoints and Shop-Tree Gate

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysProfileController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysProfileControllerTest.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysProfileCompletionControllerTest.java`

- [ ] **Step 1: Add failing controller tests**

Required assertions:

```java
assertRequiresLogin(SysProfileController.class.getMethod("profileCompletion"));
assertRequiresLogin(SysProfileController.class.getMethod(
    "updateProfileCompletion", SysProfileCompletionRequest.class));
```

Controller behavior tests must prove:

- `getInfo` includes `profileCompletionRequired` and `profileMissingFields`.
- `profileCompletion` evaluates the current `SecurityUtils.getUserId()`.
- `updateProfileCompletion` saves as the current user and refreshes the token cache.
- `shopTree` returns normal data when complete.
- `shopTree` returns code `409`, `businessCode=PROFILE_COMPLETION_REQUIRED`, and missing fields when incomplete.
- `admin` continues through the same service result without rejection.

- [ ] **Step 2: Run controller tests and confirm failure**

Run:

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysProfileControllerTest,SysProfileCompletionControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: failure because endpoints and controller dependencies are absent.

- [ ] **Step 3: Add current-user completion endpoints**

Add to `SysProfileController`:

```java
@RequiresLogin
@GetMapping("/completion")
public AjaxResult profileCompletion()
{
    return success(profileCompletionService.evaluate(SecurityUtils.getUserId()));
}

@RequiresLogin
@PutMapping("/completion")
public AjaxResult updateProfileCompletion(@RequestBody SysProfileCompletionRequest request)
{
    LoginUser loginUser = SecurityUtils.getLoginUser();
    SysProfileCompletionVo result = profileCompletionService.save(
        loginUser.getUserid(), request, loginUser.getUsername());
    loginUser.setSysUser(userService.selectUserByUserName(loginUser.getUsername()));
    tokenService.setLoginUser(loginUser);
    return success(result);
}
```

The endpoint path becomes `/system/user/profile/completion` through the gateway prefix.

- [ ] **Step 4: Publish completion state from login info**

In `SysUserController.getInfo`, evaluate the current user and add:

```java
SysProfileCompletionVo completion = profileCompletionService.evaluate(user.getUserId());
ajax.put("profileCompletionRequired", completion.isCompletionRequired());
ajax.put("profileMissingFields", completion.getMissingFields());
```

- [ ] **Step 5: Gate the shop-tree endpoint**

Before `deptService.selectShopTree`:

```java
SysProfileCompletionVo completion = profileCompletionService.evaluate(SecurityUtils.getUserId());
if (completion.isCompletionRequired())
{
    AjaxResult result = AjaxResult.error(HttpStatus.CONFLICT, "请先补全入职资料");
    result.put("businessCode", "PROFILE_COMPLETION_REQUIRED");
    result.put("profileMissingFields", completion.getMissingFields());
    return result;
}
```

- [ ] **Step 6: Run controller and service tests**

Run the Task 2 Maven command plus the Task 1 service test.

Expected: all selected backend tests pass.

## Task 3: Frontend Completion State and Route Guard

**Files:**
- Create: `erp-ui/src/utils/profileCompletion.js`
- Modify: `erp-ui/src/store/modules/user.js`
- Modify: `erp-ui/src/store/getters.js`
- Modify: `erp-ui/src/router/index.js`
- Modify: `erp-ui/src/permission.js`
- Test: `erp-ui/test/profileCompletionGate.test.js`

- [ ] **Step 1: Write failing route/state tests**

The pure helper tests must assert:

```js
assert.strictEqual(
  resolveProfileCompletionRedirect("/select-shop?redirect=%2Findex", true),
  "/complete-profile?redirect=%2Fselect-shop%3Fredirect%3D%252Findex"
)
assert.strictEqual(resolveProfileCompletionRedirect("/complete-profile", true), "")
assert.strictEqual(resolveProfileCompletionRedirect("/index", false), "")
```

Source assertions must verify:

- Vuex has `profileCompletionRequired` and `profileMissingFields`.
- `GetInfo` commits both values.
- logout resets both values.
- `/complete-profile` is a constant route.
- the completion check appears before `getNormalizedMobileRedirectInfo` and `shouldSelectShop` in both initial-route and subsequent-route branches.
- incomplete redirect calls `clearSelectedDept()`.

- [ ] **Step 2: Run the new frontend test and confirm failure**

Run:

```bash
cd erp-ui
npm test -- profileCompletionGate.test.js
```

Expected: module-not-found or assertion failure because completion files/state are absent.

- [ ] **Step 3: Implement pure route and API error helpers**

`profileCompletion.js` exports:

```js
const PROFILE_COMPLETION_PATH = "/complete-profile"

function isProfileCompletionPath(path) {
  return String(path || "").split("?")[0] === PROFILE_COMPLETION_PATH
}

function resolveProfileCompletionRedirect(fullPath, required) {
  if (!required || isProfileCompletionPath(fullPath)) return ""
  return `${PROFILE_COMPLETION_PATH}?redirect=${encodeURIComponent(fullPath || "/")}`
}

function responseData(error) {
  return (error && error.response && error.response.data) || (error && error.data) || error || {}
}

function isProfileCompletionRequiredError(error) {
  return responseData(error).businessCode === "PROFILE_COMPLETION_REQUIRED"
}

function getProfileCompletionErrorFields(error) {
  const fields = responseData(error).profileMissingFields
  return Array.isArray(fields) ? fields : []
}

module.exports = {
  PROFILE_COMPLETION_PATH,
  isProfileCompletionPath,
  resolveProfileCompletionRedirect,
  isProfileCompletionRequiredError,
  getProfileCompletionErrorFields
}
```

- [ ] **Step 4: Add Vuex state and getters**

Add mutations `SET_PROFILE_COMPLETION_REQUIRED` and `SET_PROFILE_MISSING_FIELDS`. `GetInfo` must default missing response values to `false` and `[]`. Login, logout, and frontend logout reset them.

- [ ] **Step 5: Add route and guard ordering**

Add `/complete-profile` immediately before `/select-shop` in `constantRoutes`.

In `permission.js`, create one helper that:

1. Reads `store.getters.profileCompletionRequired`.
2. Returns no redirect for `/complete-profile` while incomplete.
3. Redirects complete users away from `/complete-profile` to `/select-shop`.
4. Clears selected context before redirecting incomplete users.
5. Preserves `to.fullPath` in the completion page query.

Call it immediately after routes are available and before mobile or shop routing in both router branches.

- [ ] **Step 6: Run frontend state/guard test**

Run the Task 3 frontend command.

Expected: route/state assertions pass.

## Task 4: Responsive Completion Page and Validation

**Files:**
- Create: `erp-ui/src/views/profile-completion/profileCompletionFields.js`
- Create: `erp-ui/src/views/profile-completion/profileCompletionValidation.js`
- Create: `erp-ui/src/views/profile-completion/index.vue`
- Modify: `erp-ui/src/api/system/user.js`
- Modify: `erp-ui/src/views/select-shop/index.vue`
- Test: `erp-ui/test/profileCompletionGate.test.js`

- [ ] **Step 1: Add failing validation and page tests**

Test the validator with all 15 keys and these failures:

```js
assert.strictEqual(validateProfileCompletion({ sex: "2" }).sex, "请选择性别")
assert.strictEqual(validateProfileCompletion({ birthDate: "2999-01-01" }).birthDate, "出生日期不能晚于今天")
assert.strictEqual(validateProfileCompletion({ idType: "居民身份证", idNumber: "11010119900101123X" }).idNumber, "请输入正确的居民身份证号码")
assert.strictEqual(validateProfileCompletion({ bankAccount: "ABC" }).bankAccount, "银行卡号应为 12–30 位数字")
```

Source assertions must verify the page has:

- progress and missing-field count;
- responsive desktop/mobile class names;
- only “保存并继续”和“退出登录” primary actions;
- no workbench/store-entry action;
- field-level errors and request error alert;
- masked completed sensitive-value display;
- readonly HR summary;
- save → `GetInfo` → `/select-shop` sequence.

API assertions must verify `/system/user/profile/completion` GET and PUT functions.

Selector assertions must verify `PROFILE_COMPLETION_REQUIRED` handling clears context and routes to completion.

- [ ] **Step 2: Run the test and confirm failure**

Run:

```bash
cd erp-ui
npm test -- profileCompletionGate.test.js
```

Expected: validation/page/API assertions fail.

- [ ] **Step 3: Define shared field metadata and validation**

Field metadata must preserve the backend key order and provide labels, input type, autocomplete, maxlength, and select options. Use selects for sex, ID type, marital status, and common ethnicity values with `allow-create` only where existing UI patterns permit it.

The validator must trim values, normalize phone/bank values, check required fields, validate resident ID checksum, and return an object keyed by field.

- [ ] **Step 4: Add completion API functions**

```js
export function getProfileCompletion() {
  return request({ url: "/system/user/profile/completion", method: "get" })
}

export function updateProfileCompletion(data) {
  return request({ url: "/system/user/profile/completion", method: "put", data })
}
```

- [ ] **Step 5: Build the responsive page**

The page must:

1. Load current completion data on create.
2. Render only missing fields as editable controls.
3. Show completed fields and HR summary separately.
4. Preserve form values across request errors.
5. Submit the normalized allowlisted payload.
6. Dispatch `GetInfo` after successful save.
7. Stay put if the refreshed store still says incomplete.
8. Clear selected context and route to `/select-shop` only after completion.
9. Offer logout from all states.

- [ ] **Step 6: Add select-shop fallback**

In `fetchDeptTree().catch`, detect the completion business error before generic handling:

```js
if (isProfileCompletionRequiredError(error)) {
  clearSelectedDept()
  this.$router.replace({ path: PROFILE_COMPLETION_PATH, query: { redirect: this.$route.fullPath } })
  return
}
```

- [ ] **Step 7: Run the completion frontend test**

Run the Task 4 frontend command.

Expected: all `profileCompletionGate.test.js` assertions pass.

## Task 5: Focused Regression Verification

**Files:**
- Verify all files changed in Tasks 1–4.

- [ ] **Step 1: Run focused backend tests**

```bash
mvn -pl erp-modules/erp-system -am \
  -Dtest=SysUserProfileCompletionServiceImplTest,SysProfileControllerTest,SysProfileCompletionControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: zero failures and zero errors.

- [ ] **Step 2: Run completion and routing frontend tests**

```bash
cd erp-ui
npm test -- \
  profileCompletionGate.test.js \
  mobileAccessBoundary.test.js \
  mobileShopEntryRouting.test.js \
  shopContextUx.test.js \
  passwordResetShopSelection.test.js
```

Expected: all selected Node tests pass.

- [ ] **Step 3: Run the complete frontend Node suite**

```bash
cd erp-ui
npm test
```

Expected: all discovered frontend tests pass. If an unrelated pre-existing test fails, record it without changing unrelated features.

- [ ] **Step 4: Build the frontend production bundle**

```bash
cd erp-ui
npm run build:prod
```

Expected: production build completes with no compilation errors.

- [ ] **Step 5: Verify the system module package**

```bash
mvn -pl erp-modules/erp-system -am -DskipTests package
```

Expected: Maven reactor finishes with `BUILD SUCCESS`.

- [ ] **Step 6: Review the final diff against the approved spec**

Confirm:

- exactly 15 employee-owned fields block login;
- admin user ID 1 is exempt;
- missing organization/HR fields never block;
- users without profiles are blocked and can create their own profile;
- no endpoint can update department, store, role, post, account status, employee status, employee number, or entry date;
- route ordering is completion → mobile normalization → store selection;
- shop-tree fallback is enforced by the backend;
- no sensitive values appear in logs or test output.

Do not create a commit unless the user explicitly requests one.
