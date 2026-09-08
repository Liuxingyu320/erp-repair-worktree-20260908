# Transfer Shortage Request Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let stores submit warehouse replenishment transfer requests even when the selected warehouse currently has no stock for the requested product.

**Architecture:** Keep the existing transfer order data model and backend stock rules. Add a second frontend product-source path in `erp-ui/src/views/inventory/transfer/index.vue`: current stock picker for in-stock items, plus a product catalog picker for shortage demand. Product shortage state is derived in the frontend by querying existing stock APIs for the selected source warehouse; backend delivery remains the hard inventory gate.

**Tech Stack:** Vue 2, Element UI, existing `ProductSelect` and `WarehouseSelect` components, Node assert-based frontend tests, Java/JUnit 5 service tests.

---

## File Structure

- Create: `erp-ui/test/transferShortageRequestUx.test.js`
  - Static frontend contract test for the new shortage request UI and helper methods.
- Modify: `erp-ui/src/views/inventory/transfer/index.vue`
  - Add the product catalog picker dialog.
  - Add shortage/availability display for transfer details.
  - Add availability refresh helpers using existing `listStock`.
  - Preserve existing stock picker behavior.
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java`
  - Add backend regression tests proving submit does not require source warehouse stock and delivery still blocks when stock is insufficient.
- No database migration.
- No backend production-code change expected.

---

### Task 1: Add Frontend Contract Test For Shortage Request UI

**Files:**
- Create: `erp-ui/test/transferShortageRequestUx.test.js`
- Test command: `node erp-ui/test/transferShortageRequestUx.test.js`

- [ ] **Step 1: Write the failing test**

Create `erp-ui/test/transferShortageRequestUx.test.js` with this content:

```js
const assert = require("assert")
const fs = require("fs")
const path = require("path")

const transferPage = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/index.vue"),
  "utf8"
)

assert.ok(
  transferPage.includes("从仓库库存选择"),
  "transfer request form should keep the warehouse-stock picker entry"
)

assert.ok(
  transferPage.includes("从商品档案选择"),
  "transfer request form should add a product-catalog picker entry for shortage demand"
)

assert.ok(
  transferPage.includes('<el-dialog title="选择商品档案"'),
  "transfer request form should provide a product catalog dialog"
)

assert.ok(
  transferPage.includes(':keyword-search="true"') &&
    transferPage.includes(':show-context-meta="true"'),
  "product catalog picker should use keyword search and product context metadata"
)

assert.ok(
  transferPage.includes("productPickerOpen") &&
    transferPage.includes("openProductPicker") &&
    transferPage.includes("confirmProductSelection") &&
    transferPage.includes("buildDetailFromProduct"),
  "transfer page should have dedicated product-catalog picker state and handlers"
)

assert.ok(
  transferPage.includes("仓库缺货") &&
    transferPage.includes("isDetailShortage") &&
    transferPage.includes("refreshDetailAvailability"),
  "transfer detail rows should display derived shortage status from warehouse availability"
)

assert.ok(
  transferPage.includes("refreshAllDetailAvailability") &&
    transferPage.includes("onWarehouseSelected"),
  "changing the source warehouse should refresh detail availability without dropping demand"
)

assert.ok(
  !transferPage.includes('stockStatus: "available",\\n        transferSource: true\\n      }\\n      listStock'),
  "single-product availability lookup should not force stockStatus=available because zero-stock records must be detectable"
)

console.log("transferShortageRequestUx tests passed")
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
node erp-ui/test/transferShortageRequestUx.test.js
```

Expected: FAIL with an assertion about missing `从商品档案选择` or missing product picker state.

- [ ] **Step 3: Do not implement in this task**

Leave production code unchanged in this task. The failure proves the test is checking the missing feature.

- [ ] **Step 4: Commit**

```bash
git add erp-ui/test/transferShortageRequestUx.test.js
git commit -m "test: cover transfer shortage request ui"
```

---

### Task 2: Add Product Catalog Picker And Shortage Availability UI

**Files:**
- Modify: `erp-ui/src/views/inventory/transfer/index.vue`
- Test: `erp-ui/test/transferShortageRequestUx.test.js`
- Regression tests: `erp-ui/test/productPickerSearchUx.test.js`, `erp-ui/test/transferStockPickerPrivacy.test.js`

- [ ] **Step 1: Update the detail availability column**

Replace the existing `仓库可用` column template:

```vue
<template slot-scope="scope">{{ formatQuantity(scope.row.availableQuantity) }}</template>
```

with:

```vue
<template slot-scope="scope">
  <el-tag v-if="isDetailShortage(scope.row)" type="danger" size="mini">仓库缺货</el-tag>
  <span v-else-if="isDetailAvailabilityPending(scope.row)" class="availability-muted">库存待确认</span>
  <span v-else>{{ formatQuantity(scope.row.availableQuantity) }}</span>
</template>
```

- [ ] **Step 2: Replace the single add button with two clear entry points**

Replace:

```vue
<el-button type="primary" size="mini" icon="el-icon-plus" plain class="mt10" @click="addDetail">添加明细</el-button>
```

with:

```vue
<div class="detail-actions mt10">
  <el-button type="primary" size="mini" icon="el-icon-plus" plain @click="openStockPicker">从仓库库存选择</el-button>
  <el-button type="warning" size="mini" icon="el-icon-goods" plain @click="openProductPicker">从商品档案选择</el-button>
</div>
```

- [ ] **Step 3: Add the product catalog dialog**

Insert this dialog after the existing `选择要货库存` dialog:

```vue
<el-dialog title="选择商品档案" :visible.sync="productPickerOpen" width="560px" append-to-body :close-on-click-modal="false">
  <el-form label-width="86px" size="small">
    <el-form-item label="商品">
      <product-select
        v-model="productPickerForm.productId"
        placeholder="搜索商品名称/编码/规格"
        width="100%"
        :keyword-search="true"
        :show-context-meta="true"
        @selected="onCatalogProductSelected"
      />
    </el-form-item>
  </el-form>
  <div class="product-picker-tip">
    选择商品后可提交要货需求；仓库当前无库存时会标记为仓库缺货，待采购入库或库存调整后再发货。
  </div>
  <div slot="footer">
    <el-button @click="productPickerOpen = false">取消</el-button>
    <el-button type="primary" :disabled="!productPickerForm.productId" @click="confirmProductSelection">加入明细</el-button>
  </div>
</el-dialog>
```

- [ ] **Step 4: Add picker state**

In `data()`, add these fields near the existing picker state:

```js
productPickerOpen: false,
productPickerForm: { productId: undefined, product: null },
```

- [ ] **Step 5: Add product catalog picker methods**

Add these methods near the current stock picker methods:

```js
openProductPicker() {
  if (!this.ensureStoreContext()) return
  if (!this.form.fromWarehouseId) {
    this.$modal.msgError("请先选择要货仓库")
    return
  }
  this.productPickerForm = { productId: undefined, product: null }
  this.productPickerOpen = true
},
onCatalogProductSelected(product) {
  this.productPickerForm.product = product
},
confirmProductSelection() {
  const product = this.productPickerForm.product
  if (!product || !product.productId) {
    this.$modal.msgError("请选择要货商品")
    return
  }
  if (this.form.details.some(item => item.productId && String(item.productId) === String(product.productId))) {
    this.$modal.msgError("该商品已在要货明细中")
    return
  }
  const detail = this.buildDetailFromProduct(product)
  const emptyIndex = this.form.details.findIndex(item => !item.productId)
  if (emptyIndex >= 0) {
    this.form.details.splice(emptyIndex, 1, detail)
  } else {
    this.form.details.push(detail)
  }
  this.productPickerOpen = false
  this.refreshDetailAvailability(detail)
},
buildDetailFromProduct(product) {
  return {
    productId: product.productId,
    productName: product.productName || "",
    productCode: product.productCode || "",
    quantity: 1,
    deliveredQuantity: 0,
    receivedQuantity: 0,
    availableQuantity: undefined,
    availabilityStatus: "pending",
    unit: product.unit || "",
    spec: product.spec || "",
    grade: product.grade || ""
  }
},
```

- [ ] **Step 6: Add availability helpers**

Add these methods near `stockAvailableQuantity`:

```js
refreshAllDetailAvailability() {
  const details = (this.form.details || []).filter(item => item.productId)
  return Promise.all(details.map(item => this.refreshDetailAvailability(item)))
},
refreshDetailAvailability(detail) {
  if (!detail || !detail.productId || !this.form.fromWarehouseId) {
    return Promise.resolve()
  }
  this.$set(detail, "availabilityStatus", "pending")
  const query = {
    pageNum: 1,
    pageSize: 1,
    productId: detail.productId,
    shopDeptId: this.form.fromDeptId || this.form.fromWarehouseId,
    warehouseId: this.form.fromWarehouseId,
    transferSource: true
  }
  return listStock(query).then(res => {
    const stock = (res.rows || [])[0]
    this.$set(detail, "availableQuantity", stock ? this.stockAvailableQuantity(stock) : 0)
    this.$set(detail, "availabilityStatus", "loaded")
  }).catch(() => {
    this.$set(detail, "availableQuantity", undefined)
    this.$set(detail, "availabilityStatus", "unknown")
  })
},
isDetailShortage(row) {
  return row && row.availabilityStatus === "loaded" && this.toNumber(row.availableQuantity) <= 0
},
isDetailAvailabilityPending(row) {
  return row && (row.availabilityStatus === "pending" || row.availabilityStatus === "unknown")
},
```

This lookup intentionally does not pass `stockStatus: "available"` so zero-stock records are not filtered out.

- [ ] **Step 7: Preserve details when changing warehouse and refresh availability**

In `onWarehouseSelected(warehouse)`, after:

```js
this.selectedStockRows = []
```

add:

```js
this.refreshAllDetailAvailability()
```

Do not clear `this.form.details` in `onWarehouseSelected`.

- [ ] **Step 8: Refresh availability when opening an existing draft**

In `openForm(row)`, after:

```js
this.open = true
this.$nextTick(() => this.$refs.formRef && this.$refs.formRef.clearValidate())
```

add:

```js
this.refreshAllDetailAvailability()
```

Use the same addition only in the edit branch that loads `getTransferDetail`; do not add it to the new blank-form branch.

- [ ] **Step 9: Ensure stock-picked rows are marked loaded**

In `buildDetailFromStock(row)`, add:

```js
availabilityStatus: "loaded",
```

next to `availableQuantity`.

- [ ] **Step 10: Add scoped styles**

Add these styles near `.mt10`:

```scss
.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.availability-muted,
.product-picker-tip {
  color: #909399;
  font-size: 12px;
}

.product-picker-tip {
  line-height: 1.5;
  padding-left: 86px;
}
```

- [ ] **Step 11: Run the frontend feature test**

Run:

```bash
node erp-ui/test/transferShortageRequestUx.test.js
```

Expected: PASS and prints `transferShortageRequestUx tests passed`.

- [ ] **Step 12: Run frontend regressions**

Run:

```bash
node erp-ui/test/productPickerSearchUx.test.js
node erp-ui/test/transferStockPickerPrivacy.test.js
```

Expected: both PASS.

- [ ] **Step 13: Commit**

```bash
git add erp-ui/src/views/inventory/transfer/index.vue erp-ui/test/transferShortageRequestUx.test.js
git commit -m "feat: allow shortage products in transfer requests"
```

---

### Task 3: Add Backend Regression Tests For Submit-Time And Delivery-Time Stock Rules

**Files:**
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java`
- Test command: `mvn -f erp-modules/pom.xml -pl erp-inventory -Dtest=InvTransferServiceImplTest test`

- [ ] **Step 1: Add backend contract tests**

Add these tests after `shouldRejectUnauthorizedSourceWarehouseWhenSavingReplenishmentTransfer`:

```java
@Test
@DisplayName("门店补货提交不要求来源仓库已有库存")
void shouldSubmitWarehouseReplenishmentEvenWhenSourceWarehouseHasNoStock()
{
    SecurityContextHolder.setUserId("1");
    SecurityContextHolder.setUserName("admin");
    FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
    FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
    FakeStockMapper stockMapper = new FakeStockMapper();
    stockMapper.sourceStock = null;
    InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
            new FakeStatusLogMapper(), new FakeTransferShipmentMapper(), new FakeTransferShipmentDetailMapper(), stockMapper);

    InvTransferOrder submitted = service.submitTransfer(
            warehouseReplenishmentOrder(202L), Collections.singletonList(detail()), 202L);

    assertThat(submitted.getStatus()).isEqualTo(InvStatusConstants.SUBMITTED);
    assertThat(detailMapper.details).hasSize(1);
    assertThat(detailMapper.details.get(0).getProductId()).isEqualTo(1001L);
}

@Test
@DisplayName("来源仓库库存不足时补货调拨不能发货")
void shouldRejectWarehouseReplenishmentDeliveryWhenSourceStockIsMissing()
{
    SecurityContextHolder.setUserId("1");
    SecurityContextHolder.setUserName("admin");
    FakeTransferOrderMapper orderMapper = new FakeTransferOrderMapper();
    FakeTransferDetailMapper detailMapper = new FakeTransferDetailMapper();
    FakeTransferShipmentMapper shipmentMapper = new FakeTransferShipmentMapper();
    FakeTransferShipmentDetailMapper shipmentDetailMapper = new FakeTransferShipmentDetailMapper();
    FakeStockMapper stockMapper = new FakeStockMapper();
    InvTransferServiceImpl service = transferService(orderMapper, detailMapper,
            new FakeStatusLogMapper(), shipmentMapper, shipmentDetailMapper, stockMapper);
    orderMapper.stored = persistedWarehouseTransfer(InvStatusConstants.APPROVED);
    detailMapper.details.add(persistedDetail());
    stockMapper.sourceStock = null;

    assertThatThrownBy(() -> service.deliverTransfer(900L, 301L))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库存不足");
    assertThat(orderMapper.stored.getStatus()).isEqualTo(InvStatusConstants.APPROVED);
    assertThat(detailMapper.details.get(0).getDeliveredQuantity()).isEqualByComparingTo("0");
    assertThat(shipmentDetailMapper.details).isEmpty();
}
```

- [ ] **Step 2: Run backend test**

Run:

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -Dtest=InvTransferServiceImplTest test
```

Expected: PASS. These tests document the existing intended backend boundary: submit accepts demand without source stock, delivery rejects missing stock and does not advance delivered quantities or order status.

- [ ] **Step 3: Commit**

```bash
git add erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java
git commit -m "test: cover transfer shortage stock rules"
```

---

### Task 4: Final Verification

**Files:**
- Verify only; no file edits expected.

- [ ] **Step 1: Run all targeted frontend tests**

```bash
node erp-ui/test/transferShortageRequestUx.test.js
node erp-ui/test/productPickerSearchUx.test.js
node erp-ui/test/transferStockPickerPrivacy.test.js
```

Expected: all PASS.

- [ ] **Step 2: Run targeted backend test**

```bash
mvn -f erp-modules/pom.xml -pl erp-inventory -Dtest=InvTransferServiceImplTest test
```

Expected: PASS.

- [ ] **Step 3: Inspect changed files**

```bash
git diff --stat
git diff -- erp-ui/src/views/inventory/transfer/index.vue erp-ui/test/transferShortageRequestUx.test.js erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java
```

Expected: changes are limited to the transfer page, the new frontend test, and transfer service tests.

- [ ] **Step 4: Final commit if any verification-only fix was required**

If Task 4 required any code or test change, commit only those files:

```bash
git add erp-ui/src/views/inventory/transfer/index.vue erp-ui/test/transferShortageRequestUx.test.js erp-modules/erp-inventory/src/test/java/com/erp/inventory/service/impl/InvTransferServiceImplTest.java
git commit -m "fix: stabilize transfer shortage request flow"
```

If Task 4 made no changes, skip this commit.
