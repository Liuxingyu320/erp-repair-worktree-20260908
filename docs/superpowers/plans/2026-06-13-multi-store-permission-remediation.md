# Multi Store Permission Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining authorization, scope, cache, and deployment gaps in the current single-company, multi-store, multi-warehouse, multi-user ERP permission model.

**Architecture:** Keep the product as a single-company ERP. Do not introduce `tenant_id` or multi-company tenancy in this remediation. Treat `sys_dept` nodes with `dept_type = STORE` and `dept_type = WAREHOUSE` as the operational boundary, and treat frontend `Dept-NumId` as context only, never as authorization.

**Tech Stack:** Spring Boot Java services, MyBatis XML mappers, RuoYi-style security annotations, JUnit 5, AssertJ, reflection-based unit tests, Docker Compose.

---

## Execution Status

Implemented in commits `c4deca4` through `2516f62`, with final verification on 2026-06-13. The checkbox steps below are preserved as the original execution plan, not as current open work.

## Current Source Corrections

- `SysDeptServiceImpl.selectWarehouseList()` already filters non-admin users through `userShopService.selectAuthorizedShopTree(...)`. Do not reclassify `/dept/warehouse-list` as an unconditional full warehouse leak. The remaining work is explicit login annotation and regression coverage.
- File upload/delete, JWT secret loading, legacy `userId = 1` admin fallback, and frontend `sessionStorage` shop context have already been partially hardened. Do not reopen them unless a new failing test proves a current regression.
- The highest-risk current backend gap is `InvProductCategoryServiceImpl.selectCategoryTree(...)`: it reads Redis before validating the selected shop/warehouse scope.

## Scope Decisions

- Fix server-side authorization first; frontend filtering is secondary.
- Preserve existing shared catalog semantics: a selected shop/warehouse may see categories/products from its related department chain, but must not use a sibling or unrelated department's category tree or parent category.
- Preserve admin maintenance behavior, but write tests for non-admin users first.
- Keep changes focused. Do not refactor every inventory service in this pass.

## File Structure

- `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductCategoryServiceImpl.java` validates category tree scope and category parent scope.
- `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvProductCatalogScopeTest.java` covers forged selected shop/warehouse and cross-store category parent behavior.
- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserShopController.java` passes acting-user context into shop assignment.
- `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserShopService.java` exposes a scoped shop-assignment API.
- `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserShopServiceImpl.java` enforces acting-user scope before writing `sys_user_shop`.
- `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserShopServiceImplTest.java` covers assignment boundaries.
- `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java` declares login boundaries for helper endpoints.
- `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysControllerAuthBoundaryTest.java` covers system helper endpoint annotations.
- `erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java` protects generator column metadata.
- `erp-modules/erp-gen/pom.xml` adds test support if generator controller tests are added.
- `erp-modules/erp-gen/src/test/java/com/erp/gen/controller/GenControllerAuthBoundaryTest.java` covers generator endpoint annotations.
- `docs/store-permission-contract.md` records backend permission rules.
- `docs/store-scope-authorization-matrix.md` records endpoint-to-scope rules.
- `docker/docker-compose.yml` and `docker/erp/modules/*/dockerfile` define deployable services.

---

### Task 1: Fix Inventory Category Tree Cache Bypass

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductCategoryServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvProductCatalogScopeTest.java`

- [ ] **Step 1: Add failing cache-bypass test**

Add this test to `InvProductCatalogScopeTest`:

```java
@Test
@DisplayName("分类树缓存命中前必须先校验门店授权")
void shouldValidateScopeBeforeReturningCachedCategoryTree()
{
    SecurityContextHolder.setUserId("2");
    SecurityContextHolder.setUserName("normal");
    FakeCategoryMapper categoryMapper = new FakeCategoryMapper();
    FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
    deptScopeMapper.allowed = false;
    FakeRedisService redisService = new FakeRedisService();
    InvProductCategory cachedCategory = new InvProductCategory();
    cachedCategory.setCategoryId(99L);
    cachedCategory.setShopDeptId(999L);
    redisService.cached = List.of(cachedCategory);
    InvProductCategoryServiceImpl service = categoryService(categoryMapper, deptScopeMapper, redisService);

    assertThatThrownBy(() -> service.selectCategoryTree(999L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("当前用户无权选择该店铺");
    assertThat(categoryMapper.lastListQuery).isNull();
}
```

Add this overload near the existing `categoryService(...)` helper:

```java
private InvProductCategoryServiceImpl categoryService(FakeCategoryMapper categoryMapper,
        FakeDeptScopeMapper deptScopeMapper, FakeRedisService redisService)
{
    InvProductCategoryServiceImpl service = new InvProductCategoryServiceImpl();
    ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
    ReflectionTestUtils.setField(service, "deptScopeMapper", deptScopeMapper);
    ReflectionTestUtils.setField(service, "redisService", redisService);
    return service;
}
```

Change `FakeDeptScopeMapper` so tests can deny user-shop scope:

```java
private boolean allowed = true;

@Override
public int countUserShopScope(Long userId, Long deptId)
{
    return allowed ? 1 : 0;
}
```

- [ ] **Step 2: Run the failing inventory test**

Run:

```bash
cd erp-modules
mvn -pl erp-inventory -Dtest=InvProductCatalogScopeTest#shouldValidateScopeBeforeReturningCachedCategoryTree test
```

Expected before implementation: test fails because `selectCategoryTree(...)` returns the cached value before calling `appendShopScope(...)`.

- [ ] **Step 3: Move scope validation before Redis read**

Change `selectCategoryTree(...)` in `InvProductCategoryServiceImpl` to validate first:

```java
@Override
public List<InvProductCategory> selectCategoryTree(Long selectedShopDeptId)
{
    InvProductCategory query = new InvProductCategory();
    appendShopScope(query, selectedShopDeptId);
    String cacheKey = CacheConstants.INV_CATEGORY_KEY + "tree:" + selectedShopDeptId;
    List<InvProductCategory> cached = redisService.getCacheObject(cacheKey);
    if (cached != null)
    {
        return cached;
    }
    List<InvProductCategory> all = categoryMapper.selectInvProductCategoryList(query);
    List<InvProductCategory> tree = buildTree(all);
    redisService.setCacheObject(cacheKey, tree, CacheConstants.EXPIRATION, TimeUnit.MINUTES);
    return tree;
}
```

- [ ] **Step 4: Add failing cross-store parent test**

Add this test to `InvProductCatalogScopeTest`:

```java
@Test
@DisplayName("新增分类不能挂到无关门店的父分类下")
void shouldRejectUnrelatedParentCategoryWhenCreatingCategory()
{
    SecurityContextHolder.setUserId("2");
    SecurityContextHolder.setUserName("normal");
    FakeCategoryMapper categoryMapper = new FakeCategoryMapper();
    categoryMapper.storedCategoryShopDeptId = 103L;
    InvProductCategoryServiceImpl service = categoryService(categoryMapper);
    InvProductCategory category = new InvProductCategory();
    category.setCategoryName("越权子分类");
    category.setParentId(8L);

    assertThatThrownBy(() -> service.saveCategory(category, 104L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无权访问该店铺分类");
    assertThat(categoryMapper.insertCalls).isZero();
}
```

Extend `FakeCategoryMapper`:

```java
private Long storedCategoryShopDeptId = 100L;
private int insertCalls;

@Override
public InvProductCategory selectInvProductCategoryById(Long categoryId)
{
    InvProductCategory category = new InvProductCategory();
    category.setCategoryId(categoryId);
    category.setCategoryName("测试分类");
    category.setCategoryCode("CS");
    category.setShopDeptId(storedCategoryShopDeptId);
    category.setStatus("0");
    category.setAncestors("0");
    return category;
}

@Override
public int insertInvProductCategory(InvProductCategory category)
{
    insertCalls++;
    category.setCategoryId(category.getCategoryId() == null ? 900L + insertCalls : category.getCategoryId());
    return 0;
}
```

- [ ] **Step 5: Validate parent through scoped category lookup**

In `saveCategory(...)`, replace the raw parent lookup:

```java
InvProductCategory parent = categoryMapper.selectInvProductCategoryById(category.getParentId());
```

with scoped lookup:

```java
InvProductCategory parent = assertAndGetScopedCategory(category.getParentId(), selectedShopDeptId);
```

Keep the existing ancestor assignment:

```java
category.setAncestors(parent.getAncestors() + "," + parent.getCategoryId());
```

- [ ] **Step 6: Run inventory catalog tests**

Run:

```bash
cd erp-modules
mvn -pl erp-inventory -Dtest=InvProductCatalogScopeTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductCategoryServiceImpl.java erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvProductCatalogScopeTest.java
git commit -m "fix(inventory): validate category scope before cache reads"
```

---

### Task 2: Enforce Acting-User Scope When Assigning Shops

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserShopController.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserShopService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserShopServiceImpl.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserShopServiceImplTest.java`

- [ ] **Step 1: Add failing assignment-scope test**

Add this test to `SysUserShopServiceImplTest`:

```java
@Test
@DisplayName("普通操作人不能给用户分配自己无权访问的门店")
void normalOperatorShouldNotAssignUnboundShop()
{
    SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
        if ("countUserShopScope".equals(method))
        {
            assertThat(args[0]).isEqualTo(9L);
            return Long.valueOf(202L).equals(args[1]) ? 1 : 0;
        }
        if ("deleteUserShopByUserId".equals(method))
        {
            return 1;
        }
        throw unexpected(method);
    });
    SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
        if ("countNormalShopByIds".equals(method))
        {
            return 2;
        }
        throw unexpected(method);
    });
    SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

    assertThatThrownBy(() -> userShopService.saveUserShops(20L, new Long[] { 202L, 999L }, "operator", 9L, false))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无权分配该店铺或仓库");
}
```

Add a second test proving admin can still assign any normal store:

```java
@Test
@DisplayName("管理员可分配全部正常店铺和仓库")
void adminOperatorShouldAssignAnyNormalShop()
{
    SysUserShopMapper userShopMapper = mapper(SysUserShopMapper.class, (method, args) -> {
        if ("deleteUserShopByUserId".equals(method))
        {
            return 1;
        }
        if ("batchUserShop".equals(method))
        {
            return 1;
        }
        throw unexpected(method);
    });
    SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
        if ("countNormalShopByIds".equals(method))
        {
            return 2;
        }
        throw unexpected(method);
    });
    SysUserShopServiceImpl userShopService = newService(deptMapper, userShopMapper);

    assertThatCode(() -> userShopService.saveUserShops(20L, new Long[] { 202L, 999L }, "admin", 1L, true))
            .doesNotThrowAnyException();
}
```

- [ ] **Step 2: Run the failing system test**

Run:

```bash
cd erp-modules
mvn -pl erp-system -Dtest=SysUserShopServiceImplTest test
```

Expected before implementation: compile or test failure because the scoped overload does not exist.

- [ ] **Step 3: Add scoped service API**

Update `ISysUserShopService`:

```java
int saveUserShops(Long userId, Long[] shopDeptIds, String operName);

int saveUserShops(Long userId, Long[] shopDeptIds, String operName, Long operatorUserId, boolean operatorAdmin);
```

Update `SysUserShopServiceImpl` by keeping the existing method as an admin-compatible internal path:

```java
@Override
public int saveUserShops(Long userId, Long[] shopDeptIds, String operName)
{
    return saveUserShops(userId, shopDeptIds, operName, null, true);
}
```

Add the scoped overload:

```java
@Override
@Transactional(rollbackFor = Exception.class)
public int saveUserShops(Long userId, Long[] shopDeptIds, String operName, Long operatorUserId, boolean operatorAdmin)
{
    if (userId == null)
    {
        throw new ServiceException("用户ID不能为空");
    }
    List<Long> uniqueDeptIds = normalizeDeptIds(shopDeptIds);
    if (!uniqueDeptIds.isEmpty())
    {
        int validShopCount = deptMapper.countNormalShopByIds(uniqueDeptIds);
        if (validShopCount != uniqueDeptIds.size())
        {
            throw new ServiceException("只能选择正常状态的店铺或仓库");
        }
        checkAssignableShopScope(operatorUserId, operatorAdmin, uniqueDeptIds);
    }
    userShopMapper.deleteUserShopByUserId(userId);
    if (uniqueDeptIds.isEmpty())
    {
        return 1;
    }
    List<SysUserShop> bindings = new ArrayList<SysUserShop>();
    for (int i = 0; i < uniqueDeptIds.size(); i++)
    {
        SysUserShop userShop = new SysUserShop();
        userShop.setUserId(userId);
        userShop.setDeptId(uniqueDeptIds.get(i));
        userShop.setIsDefault(i == 0 ? UserConstants.YES : "N");
        userShop.setCreateBy(operName);
        bindings.add(userShop);
    }
    return userShopMapper.batchUserShop(bindings);
}

private void checkAssignableShopScope(Long operatorUserId, boolean operatorAdmin, List<Long> deptIds)
{
    if (operatorAdmin)
    {
        return;
    }
    if (operatorUserId == null)
    {
        throw new ServiceException("操作人ID不能为空");
    }
    for (Long deptId : deptIds)
    {
        if (userShopMapper.countUserShopScope(operatorUserId, deptId) <= 0)
        {
            throw new ServiceException("无权分配该店铺或仓库");
        }
    }
}
```

- [ ] **Step 4: Pass acting-user context from controller**

Update `SysUserShopController.save(...)`:

```java
@RequiresPermissions("system:userShop:edit")
@Log(title = "店铺配置", businessType = BusinessType.GRANT)
@PutMapping("/{userId}")
public AjaxResult save(@PathVariable Long userId, @RequestBody Long[] shopDeptIds)
{
    userService.checkUserDataScope(userId);
    return toAjax(userShopService.saveUserShops(
            userId,
            shopDeptIds,
            SecurityUtils.getUsername(),
            SecurityUtils.getUserId(),
            SecurityUtils.isAdmin()));
}
```

- [ ] **Step 5: Restrict assignable tree to acting-user scope**

Change `SysUserShopController.tree()`:

```java
@RequiresPermissions("system:userShop:list")
@GetMapping("/tree")
public AjaxResult tree()
{
    return success(userShopService.selectAuthorizedShopTree(SecurityUtils.getUserId(), SecurityUtils.isAdmin()));
}
```

This prevents non-admin users with `system:userShop:list` from seeing every store/warehouse in the assignment UI.

- [ ] **Step 6: Run system shop-scope tests**

Run:

```bash
cd erp-modules
mvn -pl erp-system -Dtest=SysUserShopServiceImplTest,SysDeptServiceImplTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/controller/SysUserShopController.java erp-modules/erp-system/src/main/java/com/erp/system/service/ISysUserShopService.java erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserShopServiceImpl.java erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysUserShopServiceImplTest.java
git commit -m "fix(system): enforce operator scope for shop assignment"
```

---

### Task 3: Add Explicit Endpoint Permission Boundaries

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java`
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/controller/SysControllerAuthBoundaryTest.java`
- Modify: `erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java`
- Modify: `erp-modules/erp-gen/pom.xml`
- Create: `erp-modules/erp-gen/src/test/java/com/erp/gen/controller/GenControllerAuthBoundaryTest.java`

- [ ] **Step 1: Add system helper endpoint annotation tests**

Extend `SysControllerAuthBoundaryTest.helperReadEndpointsShouldRequireLogin()`:

```java
assertRequiresLogin(SysDeptController.class.getMethod("shopTree"));
assertRequiresLogin(SysDeptController.class.getMethod("warehouseList"));
```

- [ ] **Step 2: Add `@RequiresLogin` to system helper endpoints**

Import:

```java
import com.erp.common.security.annotation.RequiresLogin;
```

Annotate:

```java
@RequiresLogin
@GetMapping("/shop-tree")
public AjaxResult shopTree()
```

```java
@RequiresLogin
@GetMapping("/warehouse-list")
public AjaxResult warehouseList()
```

- [ ] **Step 3: Add generator controller test support**

Add to `erp-modules/erp-gen/pom.xml` dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Add generator auth boundary test**

Create `erp-modules/erp-gen/src/test/java/com/erp/gen/controller/GenControllerAuthBoundaryTest.java`:

```java
package com.erp.gen.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import com.erp.common.security.annotation.RequiresPermissions;

@DisplayName("代码生成控制器权限边界")
class GenControllerAuthBoundaryTest
{
    @Test
    @DisplayName("字段列表接口必须要求代码生成查询权限并绑定路径参数")
    void columnListShouldRequireQueryPermissionAndPathVariable() throws Exception
    {
        Method method = GenController.class.getMethod("columnList", Long.class);

        RequiresPermissions permissions = method.getAnnotation(RequiresPermissions.class);
        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("tool:gen:query");
        assertThat(method.getParameters()[0].getAnnotation(PathVariable.class)).isNotNull();
    }
}
```

- [ ] **Step 5: Protect generator column endpoint**

Change `GenController.columnList(...)`:

```java
@RequiresPermissions("tool:gen:query")
@GetMapping(value = "/column/{tableId}")
public TableDataInfo columnList(@PathVariable Long tableId)
{
    TableDataInfo dataInfo = new TableDataInfo();
    List<GenTableColumn> list = genTableColumnService.selectGenTableColumnListByTableId(tableId);
    dataInfo.setRows(list);
    dataInfo.setTotal(list.size());
    return dataInfo;
}
```

- [ ] **Step 6: Run targeted boundary tests**

Run:

```bash
cd erp-modules
mvn -pl erp-system -Dtest=SysControllerAuthBoundaryTest test
mvn -pl erp-gen -Dtest=GenControllerAuthBoundaryTest test
```

Expected: both commands return `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java erp-modules/erp-system/src/test/java/com/erp/system/controller/SysControllerAuthBoundaryTest.java erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java erp-modules/erp-gen/pom.xml erp-modules/erp-gen/src/test/java/com/erp/gen/controller/GenControllerAuthBoundaryTest.java
git commit -m "fix(security): declare helper endpoint auth boundaries"
```

---

### Task 4: Document the Store Scope Contract

**Files:**
- Modify: `docs/store-permission-contract.md`
- Create: `docs/store-scope-authorization-matrix.md`
- Modify: `docs/project-index.md`

- [ ] **Step 1: Extend the backend rules**

Append this section to `docs/store-permission-contract.md`:

```markdown
## Assignment Rules

1. A non-admin operator may only assign store or warehouse nodes already present in the operator's own `sys_user_shop` scope.
2. Admin users may assign any enabled `STORE` or `WAREHOUSE` node.
3. Assignment UI trees must use the acting user's assignable scope, not the all-shop tree.
4. Clearing a user's shop scope is allowed only after the target user passes `checkUserDataScope`.

## Cache Rules

1. Store-sensitive caches must be read only after server-side scope validation.
2. Cache keys may use selected department context, but authorization must not depend on the cache key.
3. A cached object must never be returned before validating the current user and selected department.
```

- [ ] **Step 2: Create authorization matrix**

Create `docs/store-scope-authorization-matrix.md`:

```markdown
# Store Scope Authorization Matrix

| Area | Endpoint/Method | Required Auth | Store Scope Rule | Regression Test |
| --- | --- | --- | --- | --- |
| Category tree | `InvProductCategoryServiceImpl.selectCategoryTree` | login + selected context | validate `Dept-NumId` before Redis read | `InvProductCatalogScopeTest.shouldValidateScopeBeforeReturningCachedCategoryTree` |
| Category create | `InvProductCategoryServiceImpl.saveCategory` | inventory category write permission | selected shop required; parent category must be in related scope | `InvProductCatalogScopeTest.shouldRejectUnrelatedParentCategoryWhenCreatingCategory` |
| User shop assignment | `SysUserShopController.save` | `system:userShop:edit` | target user data scope + acting user shop scope | `SysUserShopServiceImplTest.normalOperatorShouldNotAssignUnboundShop` |
| Assignable tree | `SysUserShopController.tree` | `system:userShop:list` | admin sees all; non-admin sees own authorized tree | `SysUserShopServiceImplTest.normalUserShouldOnlySeeBoundShops` |
| Shop tree | `SysDeptController.shopTree` | login | service returns authorized shop tree | `SysControllerAuthBoundaryTest.helperReadEndpointsShouldRequireLogin` |
| Warehouse list | `SysDeptController.warehouseList` | login | admin sees all enabled warehouses; non-admin sees authorized tree warehouses | `SysDeptServiceImplTest.normalUserWarehouseListShouldOnlyReturnAuthorizedWarehouses` |
| Generator columns | `GenController.columnList` | `tool:gen:query` | no store scope; protect schema metadata | `GenControllerAuthBoundaryTest.columnListShouldRequireQueryPermissionAndPathVariable` |
```

- [ ] **Step 3: Link matrix from project index**

Add a short bullet to `docs/project-index.md` under documentation or security notes:

```markdown
- `docs/store-scope-authorization-matrix.md` records the current single-company store/warehouse permission boundary and the regression tests that protect it.
```

- [ ] **Step 4: Commit**

```bash
git add docs/store-permission-contract.md docs/store-scope-authorization-matrix.md docs/project-index.md
git commit -m "docs: record store scope authorization contract"
```

---

### Task 5: Complete Deployment Coverage for OA and Inventory

**Files:**
- Modify: `docker/docker-compose.yml`
- Create: `docker/erp/modules/inventory/dockerfile`
- Create: `docker/erp/modules/inventory/jar/readme.txt`
- Create: `docker/erp/modules/oa/dockerfile`
- Create: `docker/erp/modules/oa/jar/readme.txt`
- Modify: `docs/production-security-checklist.md`

- [ ] **Step 1: Add Inventory dockerfile**

Create `docker/erp/modules/inventory/dockerfile`:

```dockerfile
FROM openjdk:17
MAINTAINER erp
VOLUME /home/erp
RUN mkdir -p /home/erp
WORKDIR /home/erp
COPY ./jar/erp-modules-inventory.jar /home/erp/erp-modules-inventory.jar
ENTRYPOINT ["java","-jar","erp-modules-inventory.jar"]
```

Create `docker/erp/modules/inventory/jar/readme.txt`:

```text
Put erp-modules-inventory.jar in this directory before building the Docker image.
```

- [ ] **Step 2: Add OA dockerfile**

Create `docker/erp/modules/oa/dockerfile`:

```dockerfile
FROM openjdk:17
MAINTAINER erp
VOLUME /home/erp
RUN mkdir -p /home/erp
WORKDIR /home/erp
COPY ./jar/erp-modules-oa.jar /home/erp/erp-modules-oa.jar
ENTRYPOINT ["java","-jar","erp-modules-oa.jar"]
```

Create `docker/erp/modules/oa/jar/readme.txt`:

```text
Put erp-modules-oa.jar in this directory before building the Docker image.
```

- [ ] **Step 3: Add compose services**

Append services to `docker/docker-compose.yml` after `erp-modules-job`:

```yaml
  erp-modules-oa:
    container_name: erp-modules-oa
    build:
      context: ./erp/modules/oa
      dockerfile: dockerfile
    ports:
      - "127.0.0.1:${OA_PORT:-9204}:9204"
    depends_on:
      - erp-redis
      - erp-mysql
    links:
      - erp-redis
      - erp-mysql
  erp-modules-inventory:
    container_name: erp-modules-inventory
    build:
      context: ./erp/modules/inventory
      dockerfile: dockerfile
    ports:
      - "127.0.0.1:${INVENTORY_PORT:-9205}:9205"
    depends_on:
      - erp-redis
      - erp-mysql
    links:
      - erp-redis
      - erp-mysql
```

- [ ] **Step 4: Add production checklist entries**

Append to `docs/production-security-checklist.md`:

```markdown
## Service Exposure

- [ ] Only `erp-nginx` or `erp-gateway` is reachable from untrusted networks.
- [ ] Module ports `9200-9300` are bound to localhost or private network only.
- [ ] `from-source` internal headers are never accepted from public traffic.
- [ ] OA and Inventory services are included in deployment automation.

## Secrets and Developer Defaults

- [ ] `ERP_JWT_SECRET` or equivalent token secret is set in production.
- [ ] MySQL, Redis, Nacos, Druid, MinIO, and monitor credentials are not using sample values.
- [ ] API docs are disabled or access-controlled in production.
```

- [ ] **Step 5: Validate compose syntax**

Run:

```bash
cd docker
docker compose config
```

Expected: compose config renders without YAML errors.

- [ ] **Step 6: Commit**

```bash
git add docker/docker-compose.yml docker/erp/modules/inventory/dockerfile docker/erp/modules/inventory/jar/readme.txt docker/erp/modules/oa/dockerfile docker/erp/modules/oa/jar/readme.txt docs/production-security-checklist.md
git commit -m "chore(docker): include oa and inventory services"
```

---

### Task 6: Final Verification

**Files:**
- Review: all files modified by Tasks 1-5

- [ ] **Step 1: Run targeted backend tests**

Run:

```bash
cd erp-modules
mvn -pl erp-inventory -Dtest=InvProductCatalogScopeTest test
mvn -pl erp-system -Dtest=SysUserShopServiceImplTest,SysDeptServiceImplTest,SysControllerAuthBoundaryTest test
mvn -pl erp-gen -Dtest=GenControllerAuthBoundaryTest test
```

Expected: all commands return `BUILD SUCCESS`.

- [ ] **Step 2: Run broader module tests**

Run:

```bash
cd erp-modules
mvn -pl erp-system,erp-inventory,erp-gen test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Search for remaining unauthenticated helper routes**

Run:

```bash
rg -n '@(GetMapping|PostMapping|PutMapping|DeleteMapping)' erp-modules erp-auth | head -200
```

Review any controller method that has no `@RequiresLogin`, `@RequiresPermissions`, or explicit public/auth reason. Do not change auth endpoints such as login/logout/register in this pass unless a concrete route is misclassified.

- [ ] **Step 4: Check git diff**

Run:

```bash
git diff --stat
git diff -- erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvProductCategoryServiceImpl.java
git diff -- erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysUserShopServiceImpl.java
git diff -- erp-modules/erp-gen/src/main/java/com/erp/gen/controller/GenController.java
```

Expected: diffs are limited to scope validation, endpoint annotations, tests, docs, and deployment coverage.

- [ ] **Step 5: Commit final verification notes if needed**

Only commit if verification produces doc or config adjustments:

```bash
git add docs/store-scope-authorization-matrix.md docs/production-security-checklist.md
git commit -m "docs: update permission remediation verification notes"
```

---

## Self-Review

- Spec coverage: category cache bypass, user shop assignment, endpoint permissions, deployment gaps, and store-scope documentation are covered.
- Placeholder scan: this plan avoids open-ended placeholder work; every task has concrete files, code snippets, commands, and expected outcomes.
- Type consistency: new `saveUserShops(...)` overload uses `Long operatorUserId` and `boolean operatorAdmin` consistently in interface, implementation, controller, and tests.
- Risk note: this plan does not implement multi-company tenancy. That is a separate data-model project requiring `company_id`/tenant boundaries and migration design.
