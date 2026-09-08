# Fixed Asset Monthly Quota Damage Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 固定资产维修上报改成“选择坏掉的 OE 器皿 + 数量 + 破损说明”，额度由系统按 OE 单价和数量自动计算，并按月释放、可累计、不可透支未来月份。

**Architecture:** 后端保留现有固定资产配置、年度额度和额度流水表，新增月度额度快照表来解决“固定资产配置中途变化不回溯历史月份”的问题。维修上报和异常批准都不再接受用户输入金额，只接受 OE、数量和说明，服务端统一计算占用额度并检查当前累计可用额度。前端桌面端和移动端都只展示可选择资产、数量、破损说明、附件和额度不足提示。

**Tech Stack:** Spring Boot / MyBatis / Java service tests, MySQL SQL migrations, Vue 2 / Element UI desktop pages, existing mobile feature framework, Node assertion tests.

---

## Business Rules Locked By This Plan

- 领导先配置每个店铺的固定资产 OE 明细、店铺拥有数量、年度申报比例。
- 固定资产总金额 = `OE 单价 * 店铺拥有数量`。
- 年度可申报额度 = `固定资产总金额 * 年度申报比例`。
- 每月释放额度 = `年度可申报额度 / 12`。
- 额度按自然月释放，可以累计未使用额度。
- 固定资产配置变动只影响当前自然月及未来月份，不回溯已经过去的月份。
- 门店上报不能填写金额，只能选择本店铺已配置固定资产 OE、填写坏掉数量、破损说明和图片附件。
- 系统自动计算本次占用额度 = `该店铺固定资产配置中的 OE 单价 * 坏掉数量`。
- 如果本次占用额度大于当前累计可用额度，则不能提交。
- “异常批准”入口保留并优化：不允许透支未来额度，不填写金额；管理端只能辅助创建/批准一条同样受额度约束的异常破损记录。
- 当前工作区已有一部分暂停前的半成品改动，其中把维修金额改成 0 的后端逻辑是错误方向，执行计划时必须改成“系统计算占用额度”。

## File Map

- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetRepair.java`
  - Add repair quantity field.
  - Remove user-facing amount validation from repair amount.
  - Keep existing `estimatedRepairAmount` as compatibility storage for system-calculated quota usage amount.
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetQuota.java`
  - No structural change expected; remains annual current summary.
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetQuotaMonth.java`
  - Monthly quota snapshot domain.
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaFixedAssetQuotaMonthMapper.java`
  - Select/upsert/sum monthly quota snapshots.
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetQuotaMonthMapper.xml`
  - MyBatis mapper for monthly snapshots.
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/vo/OaFixedAssetQuotaSummary.java`
  - Keep current fields, compute released quota from monthly snapshots.
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java`
  - Rework quota rebuild to maintain monthly snapshots.
  - Rework repair submit and exception approval to calculate amount from OE unit price and quantity.
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetRepairMapper.xml`
  - Read/write `repair_quantity`.
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetQuotaLedgerMapper.xml`
  - Existing sum can remain, but tests must prove ledger amount is system-calculated.
- Modify: `sql/erp_oa_fixed_asset_20260619.sql`
  - Add new monthly snapshot table and repair quantity column for new installs.
- Create: `sql/erp_fixed_asset_monthly_quota_20260709.sql`
  - Idempotent migration for existing databases.
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaFixedAssetServiceImplTest.java`
  - Backend behavior tests.
- Modify: `erp-ui/src/views/oa/fixedAsset/config/index.vue`
  - Keep store-level config list and drawer.
  - Optimize exception approval dialog to use OE + quantity +破损说明, no amount input.
- Modify: `erp-ui/src/views/oa/fixedAsset/repair/index.vue`
  - Repair report form uses OE + quantity +破损说明 + attachments.
  - Remove manual amount input.
  - Disable submit when computed quota usage exceeds available.
- Modify: `erp-ui/src/views/mobile/feature/mobileFormConfigs.js`
  - Mobile repair form uses `repairQuantity`, no `estimatedRepairAmount` input.
- Modify: `erp-ui/src/views/mobile/feature/featureMapper.js`
  - Mobile cards/details show damaged item context, quantity, description, status.
- Modify: `erp-ui/test/fixedAssetFeature.test.js`
  - Static frontend/mobile contract assertions.
- Modify: `erp-ui/test/productPickerSearchUx.test.js`
  - Keep fixed asset OE search assertions aligned with configured-asset selection.
- Modify if needed: `erp-ui/test/mobileFeatureFormConfig.test.js`
  - Assert image upload and quantity field remain valid.

---

### Task 1: Write Failing Backend Tests For Monthly Snapshot And Computed Quota Usage

**Files:**
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaFixedAssetServiceImplTest.java`

- [ ] **Step 1: Add tests for system-calculated repair usage**

Add tests with these exact expectations:

```java
@Test
@DisplayName("维修上报按OE单价和坏掉数量自动计算占用额度")
void shouldCalculateRepairUsageFromAssetPriceAndQuantity()
{
    loginAsStoreUser();
    FakeFixedAssetMappers mappers = seededMappers();
    mappers.configMapper.assetUnitPrice = new BigDecimal("1000.00");
    mappers.ledgerMapper.usedAmount = BigDecimal.ZERO;
    OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

    OaFixedAssetRepair repair = repair(201L);
    repair.setRepairQuantity(new BigDecimal("2.00"));
    OaFixedAssetRepair saved = service.submitRepair(repair, 201L);

    assertThat(saved.getEstimatedRepairAmount()).isEqualByComparingTo("2000.00");
    assertThat(saved.getRepairQuantity()).isEqualByComparingTo("2.00");
    assertThat(mappers.ledgerMapper.ledgers).hasSize(1);
    assertThat(mappers.ledgerMapper.ledgers.get(0).getAmount()).isEqualByComparingTo("2000.00");
    assertThat(mappers.ledgerMapper.ledgers.get(0).getMovementType()).isEqualTo("normal_submit");
}

@Test
@DisplayName("维修上报超过当前累计可用额度时拒绝")
void shouldRejectRepairWhenComputedUsageExceedsAvailableQuota()
{
    loginAsStoreUser();
    FakeFixedAssetMappers mappers = seededMappers();
    mappers.configMapper.assetUnitPrice = new BigDecimal("1000.00");
    mappers.ledgerMapper.usedAmount = new BigDecimal("5500.00");
    OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

    OaFixedAssetRepair repair = repair(201L);
    repair.setRepairQuantity(new BigDecimal("2.00"));

    assertThatThrownBy(() -> service.submitRepair(repair, 201L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("当前可用额度不足");
    assertThat(mappers.repairMapper.repairs).isEmpty();
    assertThat(mappers.ledgerMapper.ledgers).isEmpty();
}
```

- [ ] **Step 2: Add tests for monthly snapshot behavior**

Add a fake monthly quota mapper and tests with these exact expectations:

```java
@Test
@DisplayName("固定资产配置变动只更新当前月和未来月额度快照")
void shouldOnlyUpdateCurrentAndFutureMonthlyQuotaSnapshotsWhenConfigChanges()
{
    loginAsAdmin();
    FakeFixedAssetMappers mappers = seededMappers();
    mappers.monthMapper.putMonthlyQuota(201L, 2026, 1, new BigDecimal("83.33"));
    mappers.monthMapper.putMonthlyQuota(201L, 2026, 2, new BigDecimal("83.33"));
    OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

    OaFixedAssetConfig config = new OaFixedAssetConfig();
    config.setShopDeptId(201L);
    config.setOeItemId(1001L);
    config.setAssetQuantity(new BigDecimal("4.00"));
    config.setAnnualRepairRatio(new BigDecimal("20.00"));

    service.saveConfig(config, 201L);

    assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 1)).isEqualByComparingTo("83.33");
    assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 2)).isEqualByComparingTo("83.33");
    assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 3)).isEqualByComparingTo("66.67");
    assertThat(mappers.monthMapper.monthlyQuota(201L, 2026, 12)).isEqualByComparingTo("66.67");
}

@Test
@DisplayName("当前可用额度等于已释放月份快照累计减去已上报占用")
void shouldCalculateAvailableQuotaFromMonthlySnapshotsAndUsedLedger()
{
    loginAsStoreUser();
    FakeFixedAssetMappers mappers = seededMappers();
    mappers.monthMapper.putMonthlyQuota(201L, 2026, 1, new BigDecimal("83.33"));
    mappers.monthMapper.putMonthlyQuota(201L, 2026, 2, new BigDecimal("83.33"));
    mappers.monthMapper.putMonthlyQuota(201L, 2026, 3, new BigDecimal("66.67"));
    mappers.ledgerMapper.usedAmount = new BigDecimal("100.00");
    OaFixedAssetServiceImpl service = service(mappers, "2026-03-15T00:00:00Z");

    OaFixedAssetQuotaSummary summary = service.getQuotaSummary(201L, 2026, 201L);

    assertThat(summary.getReleasedQuotaAmount()).isEqualByComparingTo("233.33");
    assertThat(summary.getAvailableQuotaAmount()).isEqualByComparingTo("133.33");
}
```

- [ ] **Step 3: Run tests to verify RED**

Run:

```bash
mvn -pl erp-modules/erp-oa -Dtest=OaFixedAssetServiceImplTest test
```

Expected:

- Fails because `repairQuantity`, monthly mapper, and monthly snapshot methods do not exist yet, or because service still sets amount to zero from the paused partial edit.

---

### Task 2: Add Database And Mapper Support For Monthly Snapshots And Repair Quantity

**Files:**
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetQuotaMonth.java`
- Create: `erp-modules/erp-oa/src/main/java/com/erp/oa/mapper/OaFixedAssetQuotaMonthMapper.java`
- Create: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetQuotaMonthMapper.xml`
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaFixedAssetRepair.java`
- Modify: `erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetRepairMapper.xml`
- Modify: `sql/erp_oa_fixed_asset_20260619.sql`
- Create: `sql/erp_fixed_asset_monthly_quota_20260709.sql`

- [ ] **Step 1: Add `repairQuantity` to repair domain**

`OaFixedAssetRepair` should contain:

```java
@NotNull(message = "坏掉数量不能为空")
@DecimalMin(value = "0.01", message = "坏掉数量必须大于0")
@Excel(name = "坏掉数量")
private BigDecimal repairQuantity;
```

Keep `estimatedRepairAmount`, but remove `@NotNull` and `@DecimalMin` from it because users do not submit that value. Set its Excel label to:

```java
@Excel(name = "系统计算占用额度")
private BigDecimal estimatedRepairAmount;
```

- [ ] **Step 2: Add mapper columns for `repair_quantity`**

Update `OaFixedAssetRepairMapper.xml`:

```xml
<result property="repairQuantity" column="repair_quantity"/>
```

Add `r.repair_quantity` to `baseColumns`.

Add `repair_quantity` to insert columns and `#{repairQuantity}` to insert values.

Add update mapping:

```xml
<if test="repairQuantity != null">repair_quantity = #{repairQuantity},</if>
```

- [ ] **Step 3: Create monthly snapshot domain**

Create `OaFixedAssetQuotaMonth.java` with fields:

```java
private Long monthId;
private Long shopDeptId;
private String shopDeptName;
private Integer quotaYear;
private Integer quotaMonth;
private BigDecimal annualRepairRatio;
private BigDecimal assetTotalAmount;
private BigDecimal monthlyQuotaAmount;
```

Include getters and setters for every field.

- [ ] **Step 4: Create monthly snapshot mapper interface**

Create methods:

```java
OaFixedAssetQuotaMonth selectMonthQuota(@Param("shopDeptId") Long shopDeptId,
        @Param("quotaYear") Integer quotaYear, @Param("quotaMonth") Integer quotaMonth);

BigDecimal sumReleasedQuotaAmount(@Param("shopDeptId") Long shopDeptId,
        @Param("quotaYear") Integer quotaYear, @Param("throughMonth") Integer throughMonth);

int insertMonthQuota(OaFixedAssetQuotaMonth monthQuota);

int updateMonthQuota(OaFixedAssetQuotaMonth monthQuota);
```

- [ ] **Step 5: Create monthly snapshot mapper XML**

Use table `oa_fixed_asset_quota_month`. `sumReleasedQuotaAmount` must sum `monthly_quota_amount` for `quota_month <= throughMonth`.

- [ ] **Step 6: Add SQL migration**

`sql/erp_fixed_asset_monthly_quota_20260709.sql` must be idempotent:

```sql
ALTER TABLE oa_fixed_asset_repair
    ADD COLUMN repair_quantity decimal(16,2) NOT NULL DEFAULT 1.00 COMMENT '坏掉数量' AFTER oe_item_name;

CREATE TABLE IF NOT EXISTS oa_fixed_asset_quota_month (
    month_id bigint(20) NOT NULL AUTO_INCREMENT COMMENT '月度额度ID',
    shop_dept_id bigint(20) NOT NULL COMMENT '店铺部门ID',
    quota_year int NOT NULL COMMENT '额度年份',
    quota_month int NOT NULL COMMENT '额度月份（1-12）',
    annual_repair_ratio decimal(8,2) NOT NULL DEFAULT 20.00 COMMENT '年度申报比例快照',
    asset_total_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '固定资产总金额快照',
    monthly_quota_amount decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '月度释放额度快照',
    create_by varchar(64) DEFAULT '' COMMENT '创建者',
    create_time datetime DEFAULT NULL COMMENT '创建时间',
    update_by varchar(64) DEFAULT '' COMMENT '更新者',
    update_time datetime DEFAULT NULL COMMENT '更新时间',
    remark varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (month_id),
    UNIQUE KEY uk_oa_fixed_asset_quota_month (shop_dept_id, quota_year, quota_month),
    KEY idx_oa_fixed_asset_quota_month_shop_year (shop_dept_id, quota_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA固定资产月度额度快照';
```

For MySQL versions that do not support repeated `ADD COLUMN` safely, wrap the `ALTER` in the project’s existing drift-repair pattern using `information_schema.COLUMNS`.

- [ ] **Step 7: Run RED/GREEN check**

Run:

```bash
mvn -pl erp-modules/erp-oa -Dtest=OaFixedAssetServiceImplTest test
```

Expected after this task:

- Compilation gets past domain/mapper symbols.
- Tests may still fail because service logic is not implemented.

---

### Task 3: Implement Backend Quota Snapshot And Submit Logic

**Files:**
- Modify: `erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java`
- Modify: `erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/OaFixedAssetServiceImplTest.java`

- [ ] **Step 1: Inject monthly mapper**

Add:

```java
@Autowired
private OaFixedAssetQuotaMonthMapper monthMapper;
```

Update tests to set `monthMapper` through `ReflectionTestUtils`.

- [ ] **Step 2: Replace current summary released amount calculation**

In `getQuotaSummary`, calculate:

```java
int releasedMonthCount = currentReleasedMonthCount(year);
ensureMonthlyQuotaSnapshots(targetShopDeptId, year, releasedMonthCount, null);
BigDecimal usedAmount = money(ledgerMapper.sumUsedQuotaAmount(targetShopDeptId, year));
BigDecimal releasedAmount = money(monthMapper.sumReleasedQuotaAmount(targetShopDeptId, year, releasedMonthCount));
BigDecimal availableAmount = releasedAmount.subtract(usedAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
BigDecimal futureAdvanceAmount = usedAmount.subtract(releasedAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
```

- [ ] **Step 3: Implement snapshot refresh for current/future months**

Add method:

```java
private void refreshCurrentAndFutureMonthlyQuotaSnapshots(Long shopDeptId, Integer quotaYear, BigDecimal annualRatio)
{
    int startMonth = currentReleasedMonthCount(quotaYear);
    BigDecimal ratio = resolveAnnualRatio(shopDeptId, quotaYear, annualRatio);
    BigDecimal assetTotal = money(configMapper.sumAssetAmountByShop(shopDeptId));
    BigDecimal monthlyQuota = assetTotal.multiply(ratio)
            .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
            .divide(TWELVE, 2, RoundingMode.HALF_UP);
    for (int month = startMonth; month <= 12; month++)
    {
        upsertMonthQuota(shopDeptId, quotaYear, month, ratio, assetTotal, monthlyQuota);
    }
}
```

Use current month as the first changed month. Past months remain untouched.

- [ ] **Step 4: Ensure missing historical snapshots are initialized**

Add method:

```java
private void ensureMonthlyQuotaSnapshots(Long shopDeptId, Integer quotaYear, int throughMonth, BigDecimal annualRatio)
{
    BigDecimal ratio = resolveAnnualRatio(shopDeptId, quotaYear, annualRatio);
    BigDecimal assetTotal = money(configMapper.sumAssetAmountByShop(shopDeptId));
    BigDecimal monthlyQuota = assetTotal.multiply(ratio)
            .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
            .divide(TWELVE, 2, RoundingMode.HALF_UP);
    for (int month = 1; month <= throughMonth; month++)
    {
        if (monthMapper.selectMonthQuota(shopDeptId, quotaYear, month) == null)
        {
            upsertMonthQuota(shopDeptId, quotaYear, month, ratio, assetTotal, monthlyQuota);
        }
    }
}
```

This is for first-time deployment/backfill only. Once a month snapshot exists, it is not overwritten by later config changes unless it is current or future month through `refreshCurrentAndFutureMonthlyQuotaSnapshots`.

- [ ] **Step 5: Rework `submitRepair`**

Rules:

```java
BigDecimal quantity = repair.getRepairQuantity() == null ? BigDecimal.ONE : money(repair.getRepairQuantity());
if (quantity.compareTo(BigDecimal.ZERO) <= 0) throw new ServiceException("坏掉数量必须大于0");
if (assetConfig.getAssetQuantity() != null && quantity.compareTo(assetConfig.getAssetQuantity()) > 0)
    throw new ServiceException("坏掉数量不能超过店铺配置数量");
BigDecimal usageAmount = money(assetConfig.getAssetUnitPrice()).multiply(quantity).setScale(2, RoundingMode.HALF_UP);
OaFixedAssetQuotaSummary summary = getQuotaSummary(shopDeptId, currentYear(), selectedShopDeptId);
if (usageAmount.compareTo(summary.getAvailableQuotaAmount()) > 0)
    throw new ServiceException("当前可用额度不足，不能上报该固定资产破损");
repair.setRepairQuantity(quantity);
repair.setEstimatedRepairAmount(usageAmount);
insertLedger(repair, year, TYPE_NORMAL_SUBMIT, usageAmount);
```

- [ ] **Step 6: Rework `approveExceptionRepair`**

Keep endpoint and permission. Optimize behavior:

- Default `exceptionType` to `special_extra` if empty for compatibility.
- Use same quantity and quota check as normal submit.
- Do not allow `advance_future_months` to bypass quota. If request sends `advance_future_months`, treat it as `special_extra` or reject with `固定资产异常批准不支持透支未来额度`.
- Insert ledger only if usage fits current available quota.
- Status remains `pending_confirm` so store must confirm.

- [ ] **Step 7: Run backend tests**

Run:

```bash
mvn -pl erp-modules/erp-oa -Dtest=OaFixedAssetServiceImplTest test
```

Expected:

- All fixed asset service tests pass.

---

### Task 4: Update Desktop Fixed Asset Config Page And Exception Approval

**Files:**
- Modify: `erp-ui/src/views/oa/fixedAsset/config/index.vue`
- Modify: `erp-ui/test/fixedAssetFeature.test.js`
- Modify: `erp-ui/test/productPickerSearchUx.test.js`

- [ ] **Step 1: Keep main config page store-level**

Main list remains:

- 店铺
- 固定资产总金额
- 剩余可申报额度
- 操作：查看明细、维护明细、删除配置

Drawer remains detail-only.

- [ ] **Step 2: Update exception approval dialog**

Dialog fields:

- 店铺
- 固定资产明细：select from `listFixedAssetConfigs({ shopDeptId, status: "0" })`
- 坏掉数量：`el-input-number`, min 1, precision 0 or 2 matching config
- 破损说明
- 备注

No amount input. No “透支未来额度”. No “特殊追加额度” selector.

Computed state:

```js
selectedExceptionAsset() {
  return this.exceptionAssetOptions.find(item => item.oeItemId === this.exceptionForm.oeItemId) || {}
},
exceptionUsageAmount() {
  return Number(this.selectedExceptionAsset.assetUnitPrice || 0) * Number(this.exceptionForm.repairQuantity || 0)
},
exceptionQuotaExceeded() {
  return this.exceptionUsageAmount > Number(this.exceptionQuota.availableQuotaAmount || 0)
}
```

The dialog may show a read-only helper text:

```text
当前数量将占用额度 ¥xxx，当前可用额度 ¥yyy
```

This is display-only. It is not a user-entered amount.

- [ ] **Step 3: Disable impossible options**

For each configured asset option:

```js
:disabled="Number(item.assetUnitPrice || 0) > Number(exceptionQuota.availableQuotaAmount || 0)"
```

If selected quantity makes usage exceed quota, disable save button and show:

```text
当前可用额度不足，不能生成异常上报记录。
```

- [ ] **Step 4: Payload**

When approving exception, submit:

```js
{
  shopDeptId: this.exceptionForm.shopDeptId,
  oeItemId: this.exceptionForm.oeItemId,
  oeItemName: this.exceptionForm.oeItemName,
  repairQuantity: this.exceptionForm.repairQuantity,
  estimatedRepairAmount: undefined,
  exceptionType: "special_extra",
  faultDescription: this.exceptionForm.faultDescription,
  remark: this.exceptionForm.remark
}
```

Do not submit user-entered amount because no such input exists.

- [ ] **Step 5: Tests**

Update `fixedAssetFeature.test.js` assertions:

- Includes `repairQuantity`.
- Includes `exceptionUsageAmount`.
- Does not include `v-model="exceptionForm.estimatedRepairAmount"`.
- Does not include `advance_future_months`.
- Includes quota exceeded disabled state.

Run:

```bash
cd erp-ui
node test/fixedAssetFeature.test.js
node test/productPickerSearchUx.test.js
```

Expected:

- Both tests pass.

---

### Task 5: Update Desktop Repair Reporting Page

**Files:**
- Modify: `erp-ui/src/views/oa/fixedAsset/repair/index.vue`
- Modify: `erp-ui/test/fixedAssetFeature.test.js`
- Modify: `erp-ui/test/oeGiftManagement.test.js`

- [ ] **Step 1: Repair form fields**

Form fields:

- 店铺
- 固定资产：select configured OE for selected store
- 坏掉数量
- 破损说明
- 图片/附件
- 备注

Remove manual amount input. Keep amount only as computed helper text if needed:

```text
当前数量将占用额度 ¥xxx，当前可用额度 ¥yyy
```

- [ ] **Step 2: Asset option availability**

When `loadAssets()` returns configured assets, each option should include:

- `assetQuantity`
- `assetUnitPrice`
- `assetAmount`

Disable an asset option when single-unit price exceeds current available quota:

```vue
<el-option
  v-for="item in assetOptions"
  :key="item.oeItemId"
  :label="item.oeItemName + ' / ' + item.oeItemCode"
  :value="item.oeItemId"
  :disabled="Number(item.assetUnitPrice || 0) > Number(quota.availableQuotaAmount || 0)"
/>
```

- [ ] **Step 3: Quantity max and submit disabled state**

Compute:

```js
selectedRepairAsset() {
  return this.assetOptions.find(item => item.oeItemId === this.form.oeItemId) || {}
},
repairUsageAmount() {
  return Number(this.selectedRepairAsset.assetUnitPrice || 0) * Number(this.form.repairQuantity || 0)
},
repairQuotaExceeded() {
  return this.repairUsageAmount > Number(this.quota.availableQuotaAmount || 0)
}
```

Before submitting:

```js
if (this.repairQuotaExceeded) {
  this.$modal.msgWarning("当前可用额度不足，不能上报该固定资产破损")
  return
}
```

- [ ] **Step 4: List and detail**

List should show:

- 店铺
- 固定资产
- 坏掉数量
- 破损说明
- 异常批准
- 状态
- 申请人/批准人/批准时间

Do not show `预计维修金额` as an editable or primary business field. If a usage amount column is needed later, label it `系统占用额度`.

- [ ] **Step 5: Tests**

Update assertions:

- `repairPage.includes("坏掉数量")`
- `repairPage.includes("repairUsageAmount")`
- `!repairPage.includes("v-model=\"form.estimatedRepairAmount\"")`
- `repairPage.includes("当前可用额度不足")`

Run:

```bash
cd erp-ui
node test/fixedAssetFeature.test.js
node test/oeGiftManagement.test.js
```

Expected:

- Both tests pass.

---

### Task 6: Update Mobile Fixed Asset Repair Flow

**Files:**
- Modify: `erp-ui/src/views/mobile/feature/mobileFormConfigs.js`
- Modify: `erp-ui/src/views/mobile/feature/featureMapper.js`
- Modify: `erp-ui/src/views/mobile/feature/featureService.js` if fixed asset OE picker must return unit price/available quota metadata.
- Modify: `erp-ui/test/fixedAssetFeature.test.js`
- Modify: `erp-ui/test/mobileFeatureFormConfig.test.js`

- [ ] **Step 1: Mobile form fields**

For `fixedAssetRepair`, use fields:

```js
fields: [
  { key: "oeItemId", label: "固定资产OE", type: "entity-picker", entity: "fixedAssetOe", required: true },
  { key: "repairQuantity", label: "坏掉数量", type: "number", required: true },
  { key: "faultDescription", label: "破损说明", type: "textarea", required: true },
  { key: "imageUrls", label: "图片/附件", type: "image-upload", limit: 5, fileSize: 5, accept: "image/*", capture: "environment" },
  { key: "remark", label: "备注", type: "textarea" }
]
```

- [ ] **Step 2: Mobile card mapping**

`mapFixedAssetRepairRow` detail should be:

```js
detail: joinDetail([
  firstText(row, ["faultDescription"]),
  firstValue(row, ["repairQuantity"]) ? "数量 " + formatQuantity(firstValue(row, ["repairQuantity"])) : "",
  firstText(row, ["shopDeptName", "shopName"])
])
```

- [ ] **Step 3: Mobile detail mapping**

Fields:

- 店铺
- 固定资产
- 坏掉数量
- 异常批准
- 批准类型
- 破损说明
- 备注
- 状态
- 创建时间
- 上报时间

No `预计维修金额` and no `上报时可用额度`.

- [ ] **Step 4: Tests**

Update `fixedAssetFeature.test.js` mobile expectation:

```js
assert.deepStrictEqual(
  mapMobileFeatureRows("fixedAssetRepair", [{
    repairId: 8,
    oeItemName: "茶杯",
    repairQuantity: 2,
    faultDescription: "杯口裂开",
    shopDeptName: "测试门店",
    status: "pending_confirm"
  }]),
  [{
    title: "茶杯",
    code: "FA-8",
    detail: "杯口裂开 · 数量 2 · 测试门店",
    status: "待确认上报"
  }]
)
```

Run:

```bash
cd erp-ui
node test/fixedAssetFeature.test.js
node test/mobileFeatureFormConfig.test.js
```

Expected:

- Both tests pass.

---

### Task 7: Roll Up SQL, Backend, Frontend, And Build Verification

**Files:**
- All files from previous tasks.

- [ ] **Step 1: Run backend targeted tests**

Run:

```bash
mvn -pl erp-modules/erp-oa -Dtest=OaFixedAssetServiceImplTest test
```

Expected:

- `BUILD SUCCESS`

- [ ] **Step 2: Run frontend targeted tests**

Run:

```bash
cd erp-ui
node test/fixedAssetFeature.test.js
node test/productPickerSearchUx.test.js
node test/desktopAuditFollowupFixes.test.js
node test/oeGiftManagement.test.js
node test/mobileFeatureFormConfig.test.js
```

Expected:

- All assertion scripts pass.

- [ ] **Step 3: Run production frontend build**

Run:

```bash
cd erp-ui
NODE_OPTIONS=--openssl-legacy-provider npm run build:prod
```

Expected:

- `DONE  Compiled successfully`
- `DONE  Build complete`

- [ ] **Step 4: Manual browser checks**

Check `/system/fixed-asset/config`:

- Right top `添加店铺固定资产明细` opens without selecting external shop.
- Dialog selects shop internally.
- Store list shows 店铺、固定资产总金额、剩余可申报额度.
- Drawer shows fixed asset details.
- `异常批准` opens optimized dialog with 店铺、固定资产明细、坏掉数量、破损说明、备注.
- No editable amount field.
- If quantity exceeds current available quota, submit is disabled or warns clearly.

Check `/oa/fixed-asset/repair` or current route for repair page:

- New report form has 店铺、固定资产、坏掉数量、破损说明、图片/附件、备注.
- No editable amount field.
- Asset option/quantity cannot submit over current available quota.

- [ ] **Step 5: Data migration verification**

On local database after applying `sql/erp_fixed_asset_monthly_quota_20260709.sql`, verify:

```sql
SHOW COLUMNS FROM oa_fixed_asset_repair LIKE 'repair_quantity';
SHOW TABLES LIKE 'oa_fixed_asset_quota_month';
```

Expected:

- `repair_quantity` exists.
- `oa_fixed_asset_quota_month` exists.

---

## Acceptance Criteria

- 固定资产配置页每个店铺只显示一行配置汇总，明细在抽屉查看。
- 添加固定资产明细不要求先筛选店铺，弹窗内选择店铺。
- 固定资产配置变动从当前月开始影响额度，不重算历史月份。
- 未使用额度按已释放月份快照累计保留。
- 维修上报不出现可编辑金额字段。
- 维修上报可以选择坏掉数量。
- 本次占用额度由服务端按配置单价和数量计算。
- 超出当前累计可用额度不能提交。
- 异常批准入口保留，不允许透支未来月份额度，不填写金额。
- 移动端固定资产报修同样不填写金额，支持数量、破损说明、图片。
- 后端测试、前端断言测试和生产构建通过。

## Self-Review

- Spec coverage: Covers leadership configuration, annual ratio, monthly release, cumulative unused quota, no future overdraft, quantity-based damage reporting, system-calculated quota usage, exception approval optimization, desktop and mobile.
- Placeholder scan: No TBD/TODO/fill-later placeholders remain.
- Type consistency: Uses `repairQuantity` for user-entered damaged quantity and keeps `estimatedRepairAmount` only as compatibility storage for system-calculated quota usage amount.
