# Product Picker Search UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve all product selection entry points so users can find products by business-visible fields without using SKU.

**Architecture:** Add a `keyword` field to the existing product list contract and keep the existing `/inventory/product/list` permissions, paging, organization scope, and price redaction. Desktop product selectors, purchase product dialog, fixed-asset product selectors, and mobile product pickers reuse that rule while keeping their existing save flows.

**Tech Stack:** Java 17/Spring Boot/MyBatis, Vue 2, Element UI, Node static UI tests, JUnit 5.

---

### Task 1: Backend Product Keyword Contract

**Files:**
- Modify: `erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvProduct.java`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvProductMapper.xml`
- Modify: `erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvMobileMapper.xml`
- Modify: `erp-modules/erp-inventory/src/test/java/com/erp/inventory/controller/InvProductControllerTest.java`

- [ ] **Step 1: Write the failing backend contract test**

Add this test to `InvProductControllerTest`:

```java
@Test
@DisplayName("商品列表关键字只匹配业务可见字段")
void productListKeywordShouldSearchVisibleProductFieldsOnly() throws Exception
{
    String domainSource = Files.readString(Path.of("src/main/java/com/erp/inventory/domain/InvProduct.java"),
            StandardCharsets.UTF_8);
    String mapperSource = Files.readString(Path.of("src/main/resources/mapper/inventory/InvProductMapper.xml"),
            StandardCharsets.UTF_8);
    String mobileMapperSource = Files.readString(Path.of("src/main/resources/mapper/inventory/InvMobileMapper.xml"),
            StandardCharsets.UTF_8);

    assertThat(domainSource).contains("private String keyword;");
    assertThat(domainSource).contains("public String getKeyword()");
    assertThat(domainSource).contains("public void setKeyword(String keyword)");
    assertThat(mapperSource).contains("keyword != null and keyword != ''");
    assertThat(mapperSource).contains("p.product_name like concat('%', #{keyword}, '%')");
    assertThat(mapperSource).contains("p.product_code like concat('%', #{keyword}, '%')");
    assertThat(mapperSource).contains("p.supplier_name like concat('%', #{keyword}, '%')");
    assertThat(mapperSource).contains("p.spec like concat('%', #{keyword}, '%')");
    assertThat(mapperSource).doesNotContain("p.sku like concat('%', #{keyword}, '%')");
    assertThat(mobileMapperSource).contains("p.supplier_name like concat('%', #{keyword}, '%')");
    assertThat(mobileMapperSource).contains("p.spec like concat('%', #{keyword}, '%')");
    assertThat(mobileMapperSource).doesNotContain("p.sku like concat('%', #{keyword}, '%')");
}
```

- [ ] **Step 2: Run the backend contract test and verify it fails**

Run:

```bash
cd erp-modules && mvn -pl erp-inventory -Dtest=InvProductControllerTest test
```

Expected: FAIL because `keyword` is not yet defined or mapped.

- [ ] **Step 3: Add the minimal backend keyword implementation**

Add this field and accessors to `InvProduct.java`:

```java
private String keyword;

public String getKeyword() { return keyword; }
public void setKeyword(String keyword) { this.keyword = keyword; }
```

Add this block to `selectInvProductList` in `InvProductMapper.xml` after `where p.del_flag = '0'`:

```xml
<if test="keyword != null and keyword != ''">
    and (
        p.product_name like concat('%', #{keyword}, '%')
        or p.product_code like concat('%', #{keyword}, '%')
        or p.supplier_name like concat('%', #{keyword}, '%')
        or p.spec like concat('%', #{keyword}, '%')
    )
</if>
```

Update `selectProductOptions` in `InvMobileMapper.xml` so mobile product options also match supplier and spec, and no longer match `p.sku`:

```xml
or p.supplier_name like concat('%', #{keyword}, '%')
or p.spec like concat('%', #{keyword}, '%')
```

- [ ] **Step 4: Run the backend contract test and verify it passes**

Run:

```bash
cd erp-modules && mvn -pl erp-inventory -Dtest=InvProductControllerTest test
```

Expected: PASS.

### Task 2: Frontend Product Picker Contract Test

**Files:**
- Create: `erp-ui/test/productPickerSearchUx.test.js`

- [ ] **Step 1: Write the failing frontend contract test**

Create `productPickerSearchUx.test.js` with:

```js
const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")

const productSelect = readUi("src/views/inventory/components/ProductSelect.vue")
const purchasePage = readUi("src/views/inventory/purchase/index.vue")
const fixedAssetConfig = readUi("src/views/oa/fixedAsset/config/index.vue")
const mobilePicker = readUi("src/views/mobile/feature/components/MobileEntityPicker.vue")
const mobileEntityService = readUi("src/views/mobile/feature/mobileEntityService.js")

assert.ok(productSelect.includes("keyword: keyword"), "desktop product select should send keyword")
assert.ok(productSelect.includes("商品名称/编码/供应商/规格"), "desktop product select should explain supported fields")
assert.ok(productSelect.includes("product.categoryFullPath || product.categoryName"), "desktop product select should display product category context")
assert.ok(!productSelect.includes("sku"), "desktop product select should not expose SKU")

assert.ok(purchasePage.includes("productQuery.keyword"), "purchase picker should use keyword search")
assert.ok(purchasePage.includes("categoryTree"), "purchase picker should load category filters")
assert.ok(purchasePage.includes("categoryOptions"), "purchase picker should expose category options")
assert.ok(purchasePage.includes("商品名称/编码/供应商/规格"), "purchase picker should explain supported fields")
assert.ok(!purchasePage.includes("productQuery.productName"), "purchase picker should not keep the old product-name-only query")

assert.ok(fixedAssetConfig.includes("keyword: keyword"), "fixed asset product search should use keyword")
assert.ok(fixedAssetConfig.includes("productOptionLabel"), "fixed asset product options should show richer context")
assert.ok(!fixedAssetConfig.includes("sku"), "fixed asset product search should not expose SKU")

assert.ok(mobilePicker.includes("搜索商品名称/编码/供应商/规格"), "mobile product picker should explain supported fields")
assert.ok(mobileEntityService.includes("keyword ? { keyword } : {}"), "mobile product picker fallback should use keyword")
assert.ok(!mobileEntityService.includes("sku"), "mobile product picker service should not expose SKU")

console.log("productPickerSearchUx tests passed")
```

- [ ] **Step 2: Run the frontend contract test and verify it fails**

Run:

```bash
node erp-ui/test/productPickerSearchUx.test.js
```

Expected: FAIL because the UI still uses older product-name-only selectors.

### Task 3: Desktop Common Product Select

**Files:**
- Modify: `erp-ui/src/views/inventory/components/ProductSelect.vue`

- [ ] **Step 1: Implement keyword search and richer option display**

Update `ProductSelect.vue` so the remote search uses:

```js
const params = keyword
  ? { pageNum: 1, pageSize: 30, status: "0", keyword: keyword }
  : { pageNum: 1, pageSize: 30, status: "0" }
listProduct(params)
```

Update the placeholder to:

```js
placeholder: { type: String, default: "搜索商品名称/编码/供应商/规格" }
```

Render options with product name, code, category, spec, unit, and supplier. Do not render or search `sku`.

- [ ] **Step 2: Run the frontend contract test**

Run:

```bash
node erp-ui/test/productPickerSearchUx.test.js
```

Expected: still FAIL until purchase, fixed asset, and mobile tasks are complete.

### Task 4: Purchase Product Picker Dialog

**Files:**
- Modify: `erp-ui/src/views/inventory/purchase/index.vue`

- [ ] **Step 1: Implement purchase dialog keyword and category filters**

Import:

```js
import { categoryTree } from "@/api/inventory/category"
```

Change `productQuery` to:

```js
productQuery: { pageNum: 1, pageSize: 20, keyword: undefined, supplierName: undefined, categoryId: undefined, status: "0" }
```

Add category state and helpers:

```js
categoryOptions: [],
categoryProps: { value: "categoryId", label: "categoryName", children: "children", checkStrictly: true, emitPath: false },
loadProductCategories() {
  return categoryTree().then(res => {
    this.categoryOptions = this.decorateProductCategories(res.data || [])
  }).catch(() => {
    this.categoryOptions = []
  })
},
decorateProductCategories(list) {
  return (list || []).map(item => Object.assign({}, item, {
    children: item.children && item.children.length ? this.decorateProductCategories(item.children) : undefined
  }))
}
```

Update the dialog form to include keyword, category, and supplier filters. Update the table to include category context. Keep existing supplier validation and quantity-increment behavior.

- [ ] **Step 2: Run the frontend contract test**

Run:

```bash
node erp-ui/test/productPickerSearchUx.test.js
```

Expected: still FAIL until fixed asset and mobile tasks are complete.

### Task 5: Fixed Asset Product Selectors

**Files:**
- Modify: `erp-ui/src/views/oa/fixedAsset/config/index.vue`

- [ ] **Step 1: Implement keyword search and richer option labels**

Change `searchProducts(keyword)` to call:

```js
listProduct({ pageNum: 1, pageSize: 30, keyword: keyword, status: "0" })
```

Add:

```js
productOptionLabel(product) {
  return [product.productName, product.productCode, product.categoryFullPath || product.categoryName, product.spec, product.supplierName]
    .filter(item => item !== undefined && item !== null && String(item).trim() !== "")
    .join(" / ")
}
```

Use `productOptionLabel(item)` in both fixed asset product option lists.

- [ ] **Step 2: Run the frontend contract test**

Run:

```bash
node erp-ui/test/productPickerSearchUx.test.js
```

Expected: still FAIL until mobile task is complete.

### Task 6: Mobile Product Picker

**Files:**
- Modify: `erp-ui/src/views/mobile/feature/components/MobileEntityPicker.vue`
- Modify: `erp-ui/src/views/mobile/feature/mobileEntityService.js`

- [ ] **Step 1: Improve mobile product picker text and fallback query**

In `MobileEntityPicker.vue`, use this computed placeholder for products:

```js
if (this.entity === "product") {
  return "搜索商品名称/编码/供应商/规格"
}
return "搜索" + (this.label || "数据")
```

In `mobileEntityService.js`, change the product fallback query to:

```js
return listProduct(Object.assign(createQuery("", options), keyword ? { keyword } : {}))
  .then(response => normalizeRows(response).map(mapProductOption))
```

Do not add SKU to labels, meta text, or query payload.

- [ ] **Step 2: Run the frontend contract test and verify it passes**

Run:

```bash
node erp-ui/test/productPickerSearchUx.test.js
```

Expected: PASS.

### Task 7: Focused Regression Verification

**Files:**
- Test only

- [ ] **Step 1: Run related frontend tests**

Run:

```bash
node erp-ui/test/productPickerSearchUx.test.js
node erp-ui/test/fixedAssetFeature.test.js
node erp-ui/test/mobileEntityPickerService.test.js
node erp-ui/test/inventoryBusinessUx.test.js
```

Expected: all PASS.

- [ ] **Step 2: Run related backend tests**

Run:

```bash
cd erp-modules && mvn -pl erp-inventory -Dtest=InvProductControllerTest test
```

Expected: PASS.

## Self-Review

Spec coverage:

- Unified keyword search is covered by Tasks 1, 3, 4, 5, and 6.
- Purchase dialog filters and richer table context are covered by Task 4.
- Fixed asset and mobile selectors are covered by Tasks 5 and 6.
- SKU exclusion is covered by Tasks 1, 2, 3, 5, and 6.
- Existing permissions and price redaction remain on `/inventory/product/list`, covered by using the existing controller path in Task 1.

Placeholder scan:

- The plan contains concrete file paths, commands, and code snippets for each implementation step.

Type consistency:

- The shared query property is `keyword`.
- Category fields use existing `categoryId`, `categoryName`, and `categoryFullPath`.
- No task introduces SKU search or display.
