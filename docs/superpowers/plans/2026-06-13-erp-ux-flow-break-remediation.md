# ERP UX Flow Break Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the verified "UI allows selection/action, backend rejects later" breaks for warehouse selection, sales product selection, and transfer write actions.

**Architecture:** Keep backend authorization as the source of truth, but make frontend option lists and action handlers consume the same business scope earlier. Warehouse options become purpose-scoped instead of one global all-warehouse list. Sales product validation aligns with the existing related-catalog product query policy. Transfer write calls get local, action-specific error handling while suppressing duplicate global messages.

**Tech Stack:** Spring Boot Java services/controllers, MyBatis XML mappers, Vue 2 + Element UI, existing Node source-assertion UI tests, JUnit 5 + AssertJ service tests.

---

## File Structure

- Modify: `docs/ux-flow-break-report.md`
  - Replace the old mixed report with only verified issues and a short "cleared false positives" section.
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysDeptService.java`
  - Add a purpose-aware warehouse list method.
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java`
  - Implement purpose-scoped warehouse option logic.
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysDeptMapper.java`
  - Add scoped warehouse query mapper method.
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysDeptMapper.xml`
  - Add `selectWarehouseListByScopeRoots`.
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java`
  - Accept `purpose` and `scopeDeptId` query params on `/warehouse-list`.
- Modify: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysDeptServiceImplTest.java`
  - Replace tests that assert normal users see all warehouses with tests for scoped behavior.
- Modify: `erp-ui/src/api/system/dept.js`
  - Let `listWarehouseDept` accept query params.
- Modify: `erp-ui/src/views/inventory/components/WarehouseSelect.vue`
  - Pass purpose and scope to the backend; keep client filtering as defensive display filtering.
- Modify: `erp-ui/src/views/inventory/deliveryNotice/index.vue`
  - Use `purpose="deliverySource"` for发货仓库.
- Modify: `erp-ui/src/views/inventory/transfer/index.vue`
  - Use `purpose="replenishmentSource"` for要货仓库; add transfer write error handling.
- Modify: `erp-ui/src/views/inventory/purchase/index.vue`
  - Use `purpose="currentWarehouse"` for disabled current warehouse display.
- Modify: `erp-ui/src/views/mobile/feature/mobileEntityService.js`
  - Remove the unscoped `listWarehouseDept()` fallback or call it with scoped params.
- Modify: `erp-ui/test/warehouseSelectScope.test.js`
  - Add source-level assertions for scoped warehouse calls and page purposes.
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java`
  - Change sales product validation to `assertRelatedShopVisible`.
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvSalesServiceImplTest.java`
  - Add tests for ancestor catalog product allowed and unrelated product rejected.
- Modify: `erp-ui/src/api/inventory/transfer.js`
  - Let transfer write helpers accept optional request config.
- Modify: `erp-ui/test/transferActionErrorHandling.test.js`
  - Add source-level assertions for silent write calls and local catch handling.

---

### Task 1: Correct the UX Report

**Files:**
- Modify: `docs/ux-flow-break-report.md`

- [ ] **Step 1: Replace the report content with verified findings**

Use this exact structure:

```markdown
# ERP 流程断裂型 UX 问题报告

> 当前版本只保留已在代码中验证的问题，剔除了采购、盘点、调拨按钮、OA 模块的误判项。

## 一、已证实问题

### 1.1 仓库下拉仍依赖全量仓库接口

**证据：**
- `SysDeptServiceImpl.selectWarehouseList(Long userId, boolean admin)` 直接返回 `deptMapper.selectWarehouseList()`。
- `WarehouseSelect.vue` 在 `loadWarehouses()` 中调用 `listWarehouseDept()`。

**风险：** 业务选择器先拿到全量仓库，再由前端按页面上下文过滤；接口层仍暴露全部启用仓库，且不同页面无法表达“发货仓库/要货来源仓库/当前仓库”的不同范围语义。

**修复方向：** `/system/dept/warehouse-list` 增加 `purpose` 和 `scopeDeptId`，由后端返回场景化仓库选项。

### 1.2 销售商品列表和保存校验范围不一致

**证据：**
- `InvProductServiceImpl.selectProductList()` 使用 `appendRelatedShopScope()`，范围为当前组织、祖先、子孙。
- `InvSalesServiceImpl.loadProducts()` 使用 `assertShopVisible()`，范围为当前组织、子孙。
- `InvPurchaseServiceImpl` 已使用 `assertRelatedShopVisible()`，采购不属于此问题。

**真实断裂：** 祖先组织共享商品在销售商品下拉中可见，但销售保存时被 `assertShopVisible()` 拒绝。

**修复方向：** 销售保存商品校验改为 `assertRelatedShopVisible()`，与商品下拉和采购保存一致。

### 1.3 调拨写操作缺少本地业务化错误处理

**证据：**
- `transfer/index.vue` 的保存、提交、审批、发货、收货、取消等写操作主要只有 `.then()` 或 `.finally()`。
- 发货/收货已有 `.finally()` 恢复 loading，但缺少本地 `.catch()` 给出当前动作语义。

**修复方向：** 调拨写接口支持 `silentError`，页面 `.catch()` 中展示“保存/提交/审批/发货/收货/取消”对应的业务错误提示。

## 二、已排除误判

| 原判断 | 当前结论 |
|---|---|
| 采购保存同样窄校验 | 采购已使用 `assertRelatedShopVisible()` |
| 盘点列表可见但详情无权 | 列表和详情都使用当前组织及子孙语义 |
| 发货通知页面直接调 `listWarehouseDept()` | 当前通过 `WarehouseSelect` 调用，但组件内部仍调接口 |
| 调拨按钮只判状态 | 当前 `canShip/canReceive` 已判上下文和来源/目标 |
| OA 列表详情范围不一致 | 当前列表和详情范围语义一致，暂不列入修复 |

## 三、实施优先级

| 优先级 | 修复项 |
|---|---|
| P0 | 仓库选项场景化接口 |
| P0 | 调拨写操作本地错误处理 |
| P1 | 销售商品范围对齐 |
```

- [ ] **Step 2: Verify no cleared false positives remain**

Run:

```bash
rg -n "采购同样的问题|盘点单列表可见|按钮启用判断只检查|OA 审批采购单|合同/考勤/工资" docs/ux-flow-break-report.md
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add docs/ux-flow-break-report.md
git commit -m "docs: correct verified UX flow break report"
```

---

### Task 2: Add Purpose-Scoped Warehouse Options

**Files:**
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/ISysDeptService.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysDeptMapper.java`
- Modify: `erp-modules/erp-system/src/main/resources/mapper/system/SysDeptMapper.xml`
- Modify: `erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java`
- Test: `erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysDeptServiceImplTest.java`

- [ ] **Step 1: Write failing service tests**

In `SysDeptServiceImplTest`, replace `normalUserWarehouseListShouldReturnAllEnabledWarehouses` and `normalUserWithoutWarehouseScopeShouldStillSeeEnabledWarehouseList` with these tests:

```java
@Test
@DisplayName("普通用户默认仓库列表只返回授权组织范围内仓库")
void normalUserWarehouseListShouldReturnAuthorizedScopeWarehouses()
{
    List<Long> authorizedDeptIds = Collections.singletonList(201L);
    List<SysDept> scopedWarehouses = Collections.singletonList(warehouse(301L, 201L, "授权店仓"));
    ISysUserShopService userShopService = mapper(ISysUserShopService.class, (method, args) -> {
        if ("selectShopDeptIdsByUserId".equals(method))
        {
            return authorizedDeptIds;
        }
        throw unexpected(method);
    });
    SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
        if ("selectWarehouseListByScopeRoots".equals(method))
        {
            assertThat((List<Long>) args[0]).containsExactly(201L);
            return scopedWarehouses;
        }
        throw unexpected(method);
    });
    SysDeptServiceImpl deptService = new SysDeptServiceImpl();
    ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);
    ReflectionTestUtils.setField(deptService, "userShopService", userShopService);

    List<SysDept> result = deptService.selectWarehouseList(2L, false, null, null);

    assertThat(result).extracting(SysDept::getDeptId).containsExactly(301L);
}

@Test
@DisplayName("发货仓库按指定销售门店范围过滤")
void deliveryWarehouseListShouldUseSelectedShopScope()
{
    List<SysDept> scopedWarehouses = Collections.singletonList(warehouse(302L, 202L, "门店发货仓"));
    ISysUserShopService userShopService = mapper(ISysUserShopService.class, (method, args) -> {
        if ("checkUserShopScope".equals(method))
        {
            assertThat(args).containsExactly(2L, 202L, false);
            return null;
        }
        throw unexpected(method);
    });
    SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
        if ("selectWarehouseListByScopeRoots".equals(method))
        {
            assertThat((List<Long>) args[0]).containsExactly(202L);
            return scopedWarehouses;
        }
        throw unexpected(method);
    });
    SysDeptServiceImpl deptService = new SysDeptServiceImpl();
    ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);
    ReflectionTestUtils.setField(deptService, "userShopService", userShopService);

    List<SysDept> result = deptService.selectWarehouseList(2L, false, "deliverySource", 202L);

    assertThat(result).extracting(SysDept::getDeptId).containsExactly(302L);
}

@Test
@DisplayName("门店要货来源仓库显式使用补货来源场景")
void replenishmentSourceWarehouseListShouldKeepEnabledWarehousesAfterContextCheck()
{
    List<SysDept> warehouses = Arrays.asList(
            warehouse(301L, 100L, "总仓"),
            warehouse(302L, 100L, "区域仓"));
    ISysUserShopService userShopService = mapper(ISysUserShopService.class, (method, args) -> {
        if ("checkUserShopScope".equals(method))
        {
            assertThat(args).containsExactly(2L, 201L, false);
            return null;
        }
        throw unexpected(method);
    });
    SysDeptMapper deptMapper = mapper(SysDeptMapper.class, (method, args) -> {
        if ("selectWarehouseList".equals(method))
        {
            return warehouses;
        }
        throw unexpected(method);
    });
    SysDeptServiceImpl deptService = new SysDeptServiceImpl();
    ReflectionTestUtils.setField(deptService, "deptMapper", deptMapper);
    ReflectionTestUtils.setField(deptService, "userShopService", userShopService);

    List<SysDept> result = deptService.selectWarehouseList(2L, false, "replenishmentSource", 201L);

    assertThat(result).extracting(SysDept::getDeptId).containsExactly(301L, 302L);
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
mvn -pl erp-modules/erp-system -Dtest=SysDeptServiceImplTest test
```

Expected: FAIL because `selectWarehouseList(Long, boolean, String, Long)` and `selectWarehouseListByScopeRoots` do not exist.

- [ ] **Step 3: Add service interface method**

In `ISysDeptService.java`, add:

```java
public List<SysDept> selectWarehouseList(String purpose, Long scopeDeptId);
```

- [ ] **Step 4: Add mapper method**

In `SysDeptMapper.java`, add:

```java
public List<SysDept> selectWarehouseListByScopeRoots(@Param("deptIds") List<Long> deptIds);
```

- [ ] **Step 5: Add scoped mapper SQL**

In `SysDeptMapper.xml`, add this after `selectWarehouseList`:

```xml
<select id="selectWarehouseListByScopeRoots" resultMap="SysDeptResult">
    <include refid="selectDeptVo"/>
    where d.del_flag = '0'
      and d.status = '0'
      and d.dept_type = 'WAREHOUSE'
      <choose>
          <when test="deptIds != null and deptIds.size() > 0">
              and exists (
                  select 1
                  from sys_dept root
                  where root.dept_id in
                  <foreach collection="deptIds" item="deptId" open="(" separator="," close=")">
                      #{deptId}
                  </foreach>
                    and root.del_flag = '0'
                    and root.status = '0'
                    and (d.dept_id = root.dept_id or find_in_set(root.dept_id, d.ancestors))
              )
          </when>
          <otherwise>
              and 1 = 0
          </otherwise>
      </choose>
    order by d.parent_id, d.order_num
</select>
```

- [ ] **Step 6: Implement purpose-scoped service logic**

In `SysDeptServiceImpl.java`, replace the existing overload with:

```java
@Override
public List<SysDept> selectWarehouseList()
{
    return selectWarehouseList(null, null);
}

@Override
public List<SysDept> selectWarehouseList(String purpose, Long scopeDeptId)
{
    return selectWarehouseList(SecurityUtils.getUserId(), SecurityUtils.isAdmin(), purpose, scopeDeptId);
}

List<SysDept> selectWarehouseList(Long userId, boolean admin, String purpose, Long scopeDeptId)
{
    String normalizedPurpose = purpose == null ? "" : purpose.trim();
    Long selectedDeptId = scopeDeptId == null || scopeDeptId == 0 ? null : scopeDeptId;

    if ("replenishmentSource".equals(normalizedPurpose))
    {
        if (!admin && selectedDeptId != null)
        {
            userShopService.checkUserShopScope(userId, selectedDeptId, false);
        }
        return deptMapper.selectWarehouseList();
    }

    if (selectedDeptId != null)
    {
        if (!admin)
        {
            userShopService.checkUserShopScope(userId, selectedDeptId, false);
        }
        return deptMapper.selectWarehouseListByScopeRoots(Collections.singletonList(selectedDeptId));
    }

    if (admin)
    {
        return deptMapper.selectWarehouseList();
    }

    List<Long> authorizedDeptIds = userShopService.selectShopDeptIdsByUserId(userId);
    return deptMapper.selectWarehouseListByScopeRoots(authorizedDeptIds);
}
```

Add imports:

```java
import java.util.Collections;
```

- [ ] **Step 7: Wire controller query params**

In `SysDeptController.java`, change `warehouseList()` to:

```java
@RequiresLogin
@GetMapping("/warehouse-list")
public AjaxResult warehouseList(String purpose, Long scopeDeptId)
{
    return success(deptService.selectWarehouseList(purpose, scopeDeptId));
}
```

- [ ] **Step 8: Run backend tests**

Run:

```bash
mvn -pl erp-modules/erp-system -Dtest=SysDeptServiceImplTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add erp-modules/erp-system/src/main/java/com/erp/system/service/ISysDeptService.java \
  erp-modules/erp-system/src/main/java/com/erp/system/service/impl/SysDeptServiceImpl.java \
  erp-modules/erp-system/src/main/java/com/erp/system/mapper/SysDeptMapper.java \
  erp-modules/erp-system/src/main/resources/mapper/system/SysDeptMapper.xml \
  erp-modules/erp-system/src/main/java/com/erp/system/controller/SysDeptController.java \
  erp-modules/erp-system/src/test/java/com/erp/system/service/impl/SysDeptServiceImplTest.java
git commit -m "fix(system): scope warehouse option lists by purpose"
```

---

### Task 3: Move WarehouseSelect to Purpose-Scoped Calls

**Files:**
- Modify: `erp-ui/src/api/system/dept.js`
- Modify: `erp-ui/src/views/inventory/components/WarehouseSelect.vue`
- Modify: `erp-ui/src/views/inventory/deliveryNotice/index.vue`
- Modify: `erp-ui/src/views/inventory/transfer/index.vue`
- Modify: `erp-ui/src/views/inventory/purchase/index.vue`
- Modify: `erp-ui/src/views/mobile/feature/mobileEntityService.js`
- Test: `erp-ui/test/warehouseSelectScope.test.js`

- [ ] **Step 1: Write failing UI source test**

Create `erp-ui/test/warehouseSelectScope.test.js`:

```js
const fs = require("fs")
const path = require("path")
const assert = require("assert")

function source(file) {
  return fs.readFileSync(path.join(__dirname, "..", file), "utf8")
}

const deptApi = source("src/api/system/dept.js")
assert(
  deptApi.includes("export function listWarehouseDept(query)") &&
    deptApi.includes("params: query"),
  "listWarehouseDept should accept scoped query params"
)

const warehouseSelect = source("src/views/inventory/components/WarehouseSelect.vue")
assert(
  warehouseSelect.includes("purpose: { type: String, default: \"authorized\" }") &&
    warehouseSelect.includes("listWarehouseDept({") &&
    warehouseSelect.includes("purpose: this.purpose") &&
    warehouseSelect.includes("scopeDeptId: this.shopDeptId"),
  "WarehouseSelect should pass purpose and scopeDeptId to backend warehouse options"
)

const deliveryNotice = source("src/views/inventory/deliveryNotice/index.vue")
assert(
  deliveryNotice.includes('purpose="deliverySource"') &&
    deliveryNotice.includes(":shop-dept-id=\"deliverDetail.shopDeptId\""),
  "delivery notice should request deliverySource warehouse options scoped to the sales shop"
)

const transfer = source("src/views/inventory/transfer/index.vue")
assert(
  transfer.includes('purpose="replenishmentSource"'),
  "transfer replenishment should explicitly request replenishmentSource warehouse options"
)

const purchase = source("src/views/inventory/purchase/index.vue")
assert(
  purchase.includes('purpose="currentWarehouse"'),
  "purchase receive warehouse display should request currentWarehouse options"
)

const mobileEntityService = source("src/views/mobile/feature/mobileEntityService.js")
assert(
  !mobileEntityService.includes("return listWarehouseDept().then"),
  "mobile warehouse fallback must not call unscoped listWarehouseDept()"
)
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
node erp-ui/test/warehouseSelectScope.test.js
```

Expected: FAIL on `listWarehouseDept should accept scoped query params`.

- [ ] **Step 3: Update system dept API helper**

In `erp-ui/src/api/system/dept.js`, replace `listWarehouseDept` with:

```js
export function listWarehouseDept(query) {
  return request({
    url: '/system/dept/warehouse-list',
    method: 'get',
    params: query
  })
}
```

- [ ] **Step 4: Update WarehouseSelect props and request**

In `WarehouseSelect.vue`, add prop:

```js
purpose: { type: String, default: "authorized" },
```

Change the request to:

```js
listWarehouseDept({
  purpose: this.purpose,
  scopeDeptId: this.shopDeptId
}).then(res => {
  this.warehouses = this.flattenDeptList(res.data || res.rows || res || [])
    .filter(item => item && item.deptType === "WAREHOUSE" && this.isEnabledWarehouse(item))
  this.loaded = true
}).finally(() => {
  this.loading = false
})
```

- [ ] **Step 5: Update page usages**

In `deliveryNotice/index.vue`, add:

```vue
purpose="deliverySource"
```

to the existing `WarehouseSelect`.

In `transfer/index.vue`, add:

```vue
purpose="replenishmentSource"
```

to the existing `WarehouseSelect`.

In `purchase/index.vue`, add:

```vue
purpose="currentWarehouse"
```

to the existing `warehouse-select`.

- [ ] **Step 6: Remove mobile unscoped fallback**

In `mobileEntityService.js`, replace:

```js
if (deptType === "WAREHOUSE") {
  return listWarehouseDept().then(response => normalizeRows(response).map(mapDeptOption))
}
```

with:

```js
if (deptType === "WAREHOUSE") {
  return listWarehouseDept({ purpose: "authorized" }).then(response => normalizeRows(response).map(mapDeptOption))
}
```

- [ ] **Step 7: Run UI test**

Run:

```bash
node erp-ui/test/warehouseSelectScope.test.js
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add erp-ui/src/api/system/dept.js \
  erp-ui/src/views/inventory/components/WarehouseSelect.vue \
  erp-ui/src/views/inventory/deliveryNotice/index.vue \
  erp-ui/src/views/inventory/transfer/index.vue \
  erp-ui/src/views/inventory/purchase/index.vue \
  erp-ui/src/views/mobile/feature/mobileEntityService.js \
  erp-ui/test/warehouseSelectScope.test.js
git commit -m "fix(ui): request scoped warehouse options"
```

---

### Task 4: Align Sales Product Validation With Product Query Scope

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvSalesServiceImplTest.java`

- [ ] **Step 1: Write failing service tests**

Add these tests after `shouldApplyCatalogProductAndRecalculateSalesAmount`:

```java
@Test
@DisplayName("销售保存允许使用上级共享商品目录")
void shouldAllowAncestorCatalogProductWhenSavingSalesDraft()
{
    loginAsAdmin();
    FakeSalesOrderMapper orderMapper = new FakeSalesOrderMapper();
    FakeSalesDetailMapper detailMapper = new FakeSalesDetailMapper();
    FakeProductMapper productMapper = new FakeProductMapper();
    InvProduct sharedProduct = product();
    sharedProduct.setShopDeptId(100L);
    productMapper.products.put(1001L, sharedProduct);
    FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
    deptScopeMapper.relatedDeptIds.put(201L, java.util.Arrays.asList(100L, 201L));
    deptScopeMapper.countDeptInScopeResult = 0;
    InvSalesServiceImpl service = salesService(orderMapper, detailMapper, productMapper, deptScopeMapper);

    InvSalesOrder saved = service.saveDraft(salesOrder(), Collections.singletonList(salesDetail()), 201L);

    assertThat(saved.getTotalAmount()).isEqualByComparingTo("25.00");
    assertThat(detailMapper.details).hasSize(1);
}

@Test
@DisplayName("销售保存拒绝不在相关组织范围内的商品")
void shouldRejectUnrelatedCatalogProductWhenSavingSalesDraft()
{
    loginAsAdmin();
    FakeProductMapper productMapper = new FakeProductMapper();
    InvProduct unrelatedProduct = product();
    unrelatedProduct.setShopDeptId(999L);
    productMapper.products.put(1001L, unrelatedProduct);
    FakeDeptScopeMapper deptScopeMapper = new FakeDeptScopeMapper();
    deptScopeMapper.relatedDeptIds.put(201L, java.util.Arrays.asList(100L, 201L));
    InvSalesServiceImpl service = salesService(new FakeSalesOrderMapper(), new FakeSalesDetailMapper(),
            productMapper, deptScopeMapper);

    assertThatThrownBy(() -> service.saveDraft(salesOrder(), Collections.singletonList(salesDetail()), 201L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("无权使用该店铺商品");
}
```

Update `FakeDeptScopeMapper` fields:

```java
private final Map<Long, List<Long>> relatedDeptIds = new HashMap<>();
private int countDeptInScopeResult = 1;
```

Update `selectRelatedDeptIds`:

```java
public List<Long> selectRelatedDeptIds(Long deptId)
{
    return relatedDeptIds.getOrDefault(deptId, Collections.singletonList(deptId));
}
```

Update `countDeptInScope`:

```java
public int countDeptInScope(Long scopeDeptId, Long targetDeptId)
{
    return countDeptInScopeResult;
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
mvn -pl erp-modules/erp-inventory -Dtest=InvSalesServiceImplTest test
```

Expected: FAIL on `shouldAllowAncestorCatalogProductWhenSavingSalesDraft` because `assertShopVisible` rejects the ancestor product.

- [ ] **Step 3: Implement minimal backend fix**

In `InvSalesServiceImpl.java`, replace:

```java
assertShopVisible(product.getShopDeptId(), selectedShopDeptId, "无权使用该店铺商品");
```

with:

```java
assertRelatedShopVisible(product.getShopDeptId(), selectedShopDeptId, "无权使用该店铺商品");
```

- [ ] **Step 4: Run test**

Run:

```bash
mvn -pl erp-modules/erp-inventory -Dtest=InvSalesServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java \
  erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvSalesServiceImplTest.java
git commit -m "fix(inventory): align sales product scope validation"
```

---

### Task 5: Add Local Error Handling For Transfer Writes

**Files:**
- Modify: `erp-ui/src/api/inventory/transfer.js`
- Modify: `erp-ui/src/views/inventory/transfer/index.vue`
- Test: `erp-ui/test/transferActionErrorHandling.test.js`

- [ ] **Step 1: Write failing UI source test**

Create `erp-ui/test/transferActionErrorHandling.test.js`:

```js
const fs = require("fs")
const path = require("path")
const assert = require("assert")

function source(file) {
  return fs.readFileSync(path.join(__dirname, "..", file), "utf8")
}

const api = source("src/api/inventory/transfer.js")
;["saveTransfer", "submitTransfer", "approveTransfer", "deliverTransfer", "receiveShipment", "cancelTransfer"].forEach(name => {
  assert(
    api.includes(`export function ${name}`) && api.includes("config = {}"),
    `${name} should accept optional request config`
  )
})

const page = source("src/views/inventory/transfer/index.vue")
assert(
  page.includes("handleActionError(error, fallback)") &&
    page.includes("const message = error && error.message ? error.message : fallback"),
  "transfer page should centralize local action error messages"
)

;[
  "保存草稿失败",
  "提交失败",
  "审批失败",
  "发货失败",
  "收货失败",
  "取消失败"
].forEach(message => {
  assert(page.includes(message), `transfer page should include local fallback: ${message}`)
})

assert(
  page.includes("{ silentError: true }") &&
    page.includes(".catch(error =>"),
  "transfer writes should suppress duplicate global errors and handle failures locally"
)
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
node erp-ui/test/transferActionErrorHandling.test.js
```

Expected: FAIL because transfer API helpers do not accept config and page lacks `handleActionError`.

- [ ] **Step 3: Add optional config to transfer API writes**

In `erp-ui/src/api/inventory/transfer.js`, add:

```js
function withConfig(base, config = {}) {
  return Object.assign({}, base, config)
}
```

Replace write helpers with:

```js
export function saveTransfer(data, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/save', method: 'post', data: data }, config))
}

export function submitTransfer(data, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/submit', method: 'post', data: data }, config))
}

export function approveTransfer(data, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/approve', method: 'post', data }, config))
}

export function deliverTransfer(transferId, data, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/deliver/' + transferId, method: 'post', data }, config))
}

export function receiveTransfer(transferId, data, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/receive/' + transferId, method: 'post', data }, config))
}

export function receiveShipment(shipmentId, data, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/shipment/receive/' + shipmentId, method: 'post', data }, config))
}

export function cancelTransfer(transferId, config = {}) {
  return request(withConfig({ url: '/inventory/transfer/' + transferId, method: 'delete' }, config))
}
```

- [ ] **Step 4: Add page error helper**

In `transfer/index.vue` methods, add:

```js
handleActionError(error, fallback) {
  const message = error && error.message ? error.message : fallback
  this.$modal.msgError(message)
},
```

- [ ] **Step 5: Add catch handlers to writes**

Change save draft/submit block:

```js
api(payload, { silentError: true }).then(() => {
  this.$modal.msgSuccess(submitAfter ? "提交成功" : "已保存草稿")
  this.open = false
  this.getList()
}).catch(error => {
  this.handleActionError(error, submitAfter ? "提交失败，请确认当前门店仍有权提交该要货单" : "保存草稿失败，请确认当前门店仍有权编辑该要货单")
})
```

Change row submit:

```js
getTransferDetail(row.transferId).then(res => {
  submitTransfer(res.data, { silentError: true }).then(() => {
    this.$modal.msgSuccess("提交成功")
    this.getList()
  }).catch(error => {
    this.handleActionError(error, "提交失败，请确认当前门店仍有权提交该要货单")
  })
}).catch(error => {
  this.handleActionError(error, "获取要货单详情失败")
})
```

Change approve:

```js
approveTransfer({ transferId: row.transferId, action: "approve" }, { silentError: true }).then(() => {
  this.$modal.msgSuccess("审批通过")
  this.getList()
}).catch(error => {
  this.handleActionError(error, "审批失败，请确认当前组织仍有权审批该要货单")
})
```

Change shipment submit:

```js
deliverTransfer(this.shipmentDetail.transferId, { items }, { silentError: true }).then(() => {
  this.$modal.msgSuccess("发货成功")
  this.shipmentOpen = false
  this.getList()
}).catch(error => {
  this.handleActionError(error, "发货失败，请确认当前仓库仍有权操作该要货单")
}).finally(() => {
  this.actionLoading = false
})
```

Change receipt submit:

```js
receiveShipment(this.receiptForm.shipmentId, { items }, { silentError: true }).then(() => {
  this.$modal.msgSuccess("收货成功")
  this.receiptOpen = false
  this.getList()
}).catch(error => {
  this.handleActionError(error, "收货失败，请确认当前门店仍有权收货该要货单")
}).finally(() => {
  this.actionLoading = false
})
```

Change cancel:

```js
cancelTransfer(row.transferId, { silentError: true }).then(() => {
  this.$modal.msgSuccess("已取消")
  this.getList()
}).catch(error => {
  this.handleActionError(error, "取消失败，请确认当前门店仍有权取消该要货单")
})
```

- [ ] **Step 6: Run UI source test**

Run:

```bash
node erp-ui/test/transferActionErrorHandling.test.js
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add erp-ui/src/api/inventory/transfer.js \
  erp-ui/src/views/inventory/transfer/index.vue \
  erp-ui/test/transferActionErrorHandling.test.js
git commit -m "fix(ui): add local transfer action error handling"
```

---

### Task 6: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused backend tests**

Run:

```bash
mvn -pl erp-modules/erp-system -Dtest=SysDeptServiceImplTest test
mvn -pl erp-modules/erp-inventory -Dtest=InvSalesServiceImplTest test
```

Expected: both PASS.

- [ ] **Step 2: Run focused UI tests**

Run:

```bash
node erp-ui/test/warehouseSelectScope.test.js
node erp-ui/test/transferActionErrorHandling.test.js
```

Expected: both PASS.

- [ ] **Step 3: Confirm report no longer contains false positives**

Run:

```bash
rg -n "采购同样的问题|盘点单列表可见|按钮启用判断只检查|OA 审批采购单|合同/考勤/工资" docs/ux-flow-break-report.md
```

Expected: no output.

- [ ] **Step 4: Confirm no unscoped UI warehouse calls remain**

Run:

```bash
rg -n "listWarehouseDept\\(\\)" erp-ui/src
```

Expected: no output.

- [ ] **Step 5: Review git diff**

Run:

```bash
git diff --stat
git diff -- docs/ux-flow-break-report.md erp-modules/erp-system erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesServiceImpl.java erp-ui/src/api/system/dept.js erp-ui/src/views/inventory/components/WarehouseSelect.vue erp-ui/src/views/inventory/transfer/index.vue
```

Expected: changes limited to the planned files.

---

## Self-Review

**Spec coverage:** The plan covers all verified issues: warehouse all-list leakage and UI mismatch, sales product query/save mismatch, and transfer writes lacking local error handling. It also explicitly removes false positives from the audit report.

**Placeholder scan:** No `TBD`, `TODO`, "similar to", or generic "add tests" placeholders remain.

**Type consistency:** New warehouse purpose names are consistent across backend and frontend: `authorized`, `deliverySource`, `replenishmentSource`, and `currentWarehouse`. Transfer API config signature uses `config = {}` consistently.
