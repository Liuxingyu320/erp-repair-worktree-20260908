# Single Company Store Permission Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the current single-company, multi-store, multi-warehouse permission model so users cannot read, assign, approve, or operate data outside their authorized store scope.

**Architecture:** Do not introduce multi-tenancy or `tenant_id`. Keep the existing department tree, `sys_user_shop`, `@DataScope`, and inventory `InvBaseService` model, but close known authorization gaps and document the backend rule that all store-sensitive operations must validate scope on the server. Use focused JUnit regression tests before each backend change.

**Tech Stack:** Spring Boot Java services, MyBatis XML mappers, RuoYi-style permission annotations, JUnit 5, AssertJ, Mockito, Vue 2 frontend configuration review.

---

## Scope Decisions

- Keep the product as single-company internal ERP.
- Treat `dept_type = STORE` and `dept_type = WAREHOUSE` as the operational boundary.
- Do not add multi-tenant tables, columns, or frontend tenant switching.
- Prefer backend permission checks over frontend-only restrictions.
- Fix high-risk authorization bugs first, then improve consistency and deployment safety.

## File Structure

- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java` handles user authorization read/write endpoints.
- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java` handles role-to-user authorization endpoints.
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java` should become the source of truth for role authorization write checks.
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysRoleServiceImplTest.java` should cover role authorization write boundaries.
- `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserControllerAuthRoleScopeTest.java` should cover the missing `authRole/{userId}` read boundary.
- `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalRuleMapper.xml` filters approval rules before Java matching.
- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java` validates rule matching and rule data.
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java` should cover approval rule scope matching.
- `docs/superpowers/plans/2026-06-12-single-company-store-permission-hardening.md` tracks this implementation.

---

### Task 1: Close Role Authorization Write Boundary

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysRoleServiceImplTest.java`
- Review: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysRoleController.java`

- [ ] **Step 1: Add failing service tests for target user scope**

Add tests proving `deleteAuthUser`, `deleteAuthUsers`, and `insertAuthUsers` call user data scope checks before changing `sys_user_role`.

Expected behavior:
- `insertAuthUsers(20L, new Long[] { 101L, 202L })` checks role `20L`.
- It checks user `101L` and `202L`.
- If user `202L` is out of scope, `batchUserRole` is not called.
- `deleteAuthUser(new SysUserRole(101L, 20L))` and `deleteAuthUsers(20L, new Long[] { 101L })` follow the same rule.

Use the existing proxy style in `SysRoleServiceImplTest`. Add a fake `ISysUserService` field through `ReflectionTestUtils`.

- [ ] **Step 2: Run the failing role service tests**

Run:

```bash
cd erp-modules
mvn -pl erp-system -Dtest=SysRoleServiceImplTest test
```

Expected before implementation: the new tests fail because `SysRoleServiceImpl` writes role-user relations without calling `checkUserDataScope`.

- [ ] **Step 3: Implement service-level authorization**

In `SysRoleServiceImpl`, inject `ISysUserService`:

```java
@Autowired
private ISysUserService userService;
```

Add a private helper:

```java
private void checkAuthUserScope(Long roleId, Long... userIds)
{
    if (roleId == null)
    {
        throw new ServiceException("角色ID不能为空");
    }
    if (userIds == null || userIds.length == 0)
    {
        throw new ServiceException("用户ID不能为空");
    }
    checkRoleDataScope(roleId);
    for (Long userId : userIds)
    {
        if (userId == null)
        {
            throw new ServiceException("用户ID不能为空");
        }
        userService.checkUserDataScope(userId);
    }
}
```

Use it in the three write methods:

```java
@Override
public int deleteAuthUser(SysUserRole userRole)
{
    if (userRole == null)
    {
        throw new ServiceException("授权关系不能为空");
    }
    checkAuthUserScope(userRole.getRoleId(), userRole.getUserId());
    return userRoleMapper.deleteUserRoleInfo(userRole);
}

@Override
public int deleteAuthUsers(Long roleId, Long[] userIds)
{
    checkAuthUserScope(roleId, userIds);
    return userRoleMapper.deleteUserRoleInfos(roleId, userIds);
}

@Override
public int insertAuthUsers(Long roleId, Long[] userIds)
{
    checkAuthUserScope(roleId, userIds);
    List<SysUserRole> list = new ArrayList<SysUserRole>();
    for (Long userId : userIds)
    {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        list.add(ur);
    }
    return userRoleMapper.batchUserRole(list);
}
```

- [ ] **Step 4: Run targeted role tests**

Run:

```bash
cd erp-modules
mvn -pl erp-system -Dtest=SysRoleServiceImplTest test
```

Expected after implementation: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysRoleServiceImplTest.java
git commit -m "fix(system): enforce user scope on role authorization"
```

---

### Task 2: Close User Auth Role Read Boundary

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java`
- Create: `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserControllerAuthRoleScopeTest.java`

- [ ] **Step 1: Add failing controller test**

Create `SysUserControllerAuthRoleScopeTest` and verify `authRole(202L)` calls `userService.checkUserDataScope(202L)` before `selectUserById`.

Test behavior:
- Fake `ISysUserService.checkUserDataScope(202L)` throws `ServiceException("没有权限访问用户数据！")`.
- `authRole(202L)` propagates the exception.
- Fake `selectUserById` is not called after the exception.

- [ ] **Step 2: Run the failing controller test**

Run:

```bash
cd erp-modules
mvn -pl erp-system -Dtest=SysUserControllerAuthRoleScopeTest test
```

Expected before implementation: the test fails because `authRole` reads the user before checking scope.

- [ ] **Step 3: Add the missing scope check**

Change `SysUserController.authRole` to check scope first:

```java
@RequiresPermissions("system:user:query")
@GetMapping("/authRole/{userId}")
public AjaxResult authRole(@PathVariable("userId") Long userId)
{
    userService.checkUserDataScope(userId);
    AjaxResult ajax = AjaxResult.success();
    SysUser user = userService.selectUserById(userId);
    List<SysRole> roles = roleService.selectRolesByUserId(userId);
    ajax.put("user", user);
    ajax.put("roles", filterVisibleRoles(roles, isCurrentOrTargetAdmin(user, roles)));
    return ajax;
}
```

- [ ] **Step 4: Run targeted system controller tests**

Run:

```bash
cd erp-modules
mvn -pl erp-system -Dtest=SysUserControllerAuthRoleScopeTest,SysShopDeptFilterControllerTest test
```

Expected after implementation: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java erp-modules/erp-system/src/test/java/com/erp/system/controller/SysUserControllerAuthRoleScopeTest.java
git commit -m "fix(system): check user scope before reading auth roles"
```

---

### Task 3: Tighten Transfer Approval Rule Matching

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalRuleMapper.xml`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java`
- Review: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImpl.java`

- [ ] **Step 1: Add failing tests for unrelated area and region rules**

Extend `InvTransferApprovalRuleServiceImplTest`:
- A transfer from `101L` to `102L` must not match a `scope_type = "area"` rule with `scope_id = 999L`.
- The same transfer must not match a `scope_type = "region"` rule with `scope_id = 999L`.
- It should match a `scope_type = "dept"` rule with `scope_id = 101L`.
- It should match a `scope_type = "to_dept"` rule with `scope_id = 102L`.

Use existing `rule(...)`, `transfer(...)`, and fake mapper helpers. Add helper setters for `scopeType` and `scopeId`.

- [ ] **Step 2: Run approval rule service tests**

Run:

```bash
cd erp-modules
mvn -pl erp-inventory -Dtest=InvTransferApprovalRuleServiceImplTest test
```

Expected before XML fix: Java-only fake mapper tests may pass, proving Java matching is stricter than SQL prefilter. This is acceptable; the failing coverage for SQL must be handled in Step 3 by mapper text assertion.

- [ ] **Step 3: Add mapper SQL binding assertion**

Create or extend an inventory mapper binding test to assert `InvTransferApprovalRuleMapper.xml` no longer contains the broad clause:

```sql
or scope_type in ('region', 'area')
```

The assertion should read the mapper XML resource and fail while the broad clause exists.

- [ ] **Step 4: Remove the broad SQL match**

In `InvTransferApprovalRuleMapper.xml`, replace the scope block with exact `scope_id` matching only:

```xml
and (
    scope_type = 'all'
    <if test="fromDeptId != null">
        or (scope_type in ('from_dept', 'dept', 'region', 'area') and scope_id = #{fromDeptId})
    </if>
    <if test="toDeptId != null">
        or (scope_type in ('to_dept', 'dept', 'region', 'area') and scope_id = #{toDeptId})
    </if>
)
```

This keeps current exact-match semantics and removes accidental global matching for every `region` or `area` rule.

- [ ] **Step 5: Run targeted inventory tests**

Run:

```bash
cd erp-modules
mvn -pl erp-inventory -Dtest=InvTransferApprovalRuleServiceImplTest,InventoryMapperBindingTest test
```

Expected after implementation: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalRuleMapper.xml erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferApprovalRuleServiceImplTest.java erp-modules/erp-inventory/src/test/java/com/erp/inventory/mapper/InventoryMapperBindingTest.java
git commit -m "fix(inventory): tighten transfer approval rule scope matching"
```

---

### Task 4: Define Store Permission Contract For New Work

**Files:**
- Create: `docs/store-permission-contract.md`
- Modify: `docs/project-index.md`

- [ ] **Step 1: Write the contract document**

Create `docs/store-permission-contract.md` with these sections:

```markdown
# Store Permission Contract

## Product Scope

This ERP is a single-company system. Store and warehouse boundaries are represented by department nodes, not tenants.

## Backend Rules

1. Every store-sensitive query must apply backend scope, either through `@DataScope` or an explicit service-level store/warehouse check.
2. Frontend `Dept-NumId` is only a selected context hint. It is not authorization.
3. Mutating endpoints must validate both the acting user scope and the target entity scope.
4. Role-user authorization must validate both role scope and target user scope.
5. Inventory operations must resolve and validate selected store/warehouse through `InvBaseService`.
6. Admin bypass is only for system maintenance and must not be used for normal store operation testing.

## Test Rules

1. Add at least one cross-store rejection test for every new store-sensitive mutation.
2. Add one read-boundary test when a new endpoint accepts userId, deptId, shopDeptId, warehouseDeptId, fromDeptId, or toDeptId.
3. Tests must prove mapper writes are not called after scope rejection.
```

- [ ] **Step 2: Link the contract from project index**

Add `docs/store-permission-contract.md` to `docs/project-index.md` under backend or permission documentation.

- [ ] **Step 3: Commit**

```bash
git add docs/store-permission-contract.md docs/project-index.md
git commit -m "docs: define single-company store permission contract"
```

---

### Task 5: Production Configuration Hardening Checklist

**Files:**
- Create: `docs/production-security-checklist.md`
- Review: `erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/JwtUtils.java`
- Review: `erp-common/erp-common-core/src/main/java/com/erp/common/core/constant/TokenConstants.java`
- Review: module `bootstrap.yml` and `application-dev.yml` files

- [ ] **Step 1: Document required production settings**

Create `docs/production-security-checklist.md` with this checklist:

```markdown
# Production Security Checklist

- [ ] Set an external JWT secret through environment or deployment config. Do not rely on `TokenConstants.SECRET`.
- [ ] Use non-root MySQL accounts with strong passwords.
- [ ] Require Redis authentication in production.
- [ ] Replace MinIO default `minioadmin` credentials.
- [ ] Expose only the gateway to users. Do not expose module service ports directly.
- [ ] Restrict Nacos, Sentinel, Monitor, Redis, MySQL, and MinIO to internal networks.
- [ ] Use a non-admin account for normal store operation testing.
- [ ] Keep one break-glass admin account with separate credentials and audit expectations.
```

- [ ] **Step 2: Decide whether to fail fast on missing JWT secret**

For production profile, change JWT initialization so missing external secret throws an exception. Keep the current fallback only for local development.

Implementation pattern:

```java
if (isProductionProfile() && StringUtils.isEmpty(configuredSecret))
{
    throw new IllegalStateException("生产环境必须配置外部JWT密钥");
}
```

Place this behind a profile/environment check so current local dev startup remains usable.

- [ ] **Step 3: Run JWT utility tests**

Run:

```bash
cd erp-common
mvn -pl erp-common-core -Dtest=JwtUtilsTest test
```

Expected after implementation: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add docs/production-security-checklist.md erp-common/erp-common-core/src/main/java/com/erp/common/core/utils/JwtUtils.java erp-common/erp-common-core/src/test/java/com/erp/common/core/utils/JwtUtilsTest.java
git commit -m "chore(security): document production configuration requirements"
```

---

### Task 6: Final Regression

**Files:**
- Verify all files changed in Tasks 1-5.

- [ ] **Step 1: Run targeted backend tests**

Run:

```bash
cd erp-modules
mvn -pl erp-system,erp-inventory -Dtest=SysRoleServiceImplTest,SysUserControllerAuthRoleScopeTest,SysShopDeptFilterControllerTest,InvTransferApprovalRuleServiceImplTest,InventoryMapperBindingTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run common JWT tests if Task 5 code is implemented**

Run:

```bash
cd erp-common
mvn -pl erp-common-core -Dtest=JwtUtilsTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Review changed files**

Run:

```bash
git diff --stat
git diff -- erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserController.java
git diff -- erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysRoleServiceImpl.java
git diff -- erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvTransferApprovalRuleMapper.xml
```

Expected:
- Role authorization writes validate role scope and target user scope.
- `authRole/{userId}` validates user scope before reading.
- Transfer approval rule SQL no longer matches all `region` and `area` rules.
- Documentation states this is single-company store-scope hardening, not multi-tenancy.

- [ ] **Step 4: Commit remaining verified changes**

```bash
git status --short
git add docs/superpowers/plans/2026-06-12-single-company-store-permission-hardening.md
git commit -m "docs: plan single-company store permission hardening"
```

