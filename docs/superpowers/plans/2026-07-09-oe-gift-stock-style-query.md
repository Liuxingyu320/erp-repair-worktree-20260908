# OE/Gift Stock-Style Query Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert desktop OE/gift maintenance and readonly query pages to a stock-management-style layout, and keep mobile OE/gift as query-only stock-like catalog views.

**Architecture:** Update the two existing Vue 2 catalog pages in place so maintenance and readonly routes keep sharing the same component through `readonlyMode`. Reuse the stock page layout concepts locally in OE/gift pages instead of introducing a shared cross-module component. Update mobile feature route/search/mapper config for OE/gift only.

**Tech Stack:** Vue 2, Element UI, SCSS, Node-based frontend static tests.

---

### Task 1: Failing Static Tests For Desktop Layout And Mobile Query-Only Behavior

**Files:**
- Modify: `erp-ui/test/oeGiftManagement.test.js`

- [ ] **Step 1: Add desktop stock-style assertions**

Add assertions that `erp-ui/src/views/inventory/oe/index.vue` and `erp-ui/src/views/inventory/gift/index.vue` include:

```js
assert.ok(oePage.includes("catalog-layout"), "OE page should use stock-style catalog layout")
assert.ok(oePage.includes("category-panel"), "OE page should render a left category panel")
assert.ok(oePage.includes("metric-row"), "OE page should render metric cards")
assert.ok(oePage.includes("catalog-tabs"), "OE page should render status tabs")
assert.ok(oePage.includes("detailOpen"), "OE detail should use a stock-style detail dialog")
assert.ok(oePage.includes("el-image"), "OE detail should preview the image")

assert.ok(giftPage.includes("catalog-layout"), "gift page should use stock-style catalog layout")
assert.ok(giftPage.includes("category-panel"), "gift page should render a left category panel")
assert.ok(giftPage.includes("metric-row"), "gift page should render metric cards")
assert.ok(giftPage.includes("catalog-tabs"), "gift page should render status tabs")
assert.ok(giftPage.includes("detailOpen"), "gift detail should use a stock-style detail dialog")
assert.ok(giftPage.includes("el-image"), "gift detail should preview the image")
```

- [ ] **Step 2: Add mobile query-only assertions**

Add assertions that mobile route config contains停用快捷筛选 and does not introduce create/edit/import behaviors for OE/gift:

```js
assert.ok(mobileRoutes.includes("停用OE"), "mobile OE route should expose disabled OE quick filter")
assert.ok(mobileRoutes.includes("停用礼盒"), "mobile gift route should expose disabled gift quick filter")
assert.ok(!mobileRoutes.includes("新增OE"), "mobile OE should stay query-only")
assert.ok(!mobileRoutes.includes("新增礼盒"), "mobile gift should stay query-only")
assert.ok(!mobileRoutes.includes("导入OE"), "mobile OE should not expose import")
assert.ok(!mobileRoutes.includes("导入礼盒"), "mobile gift should not expose import")
```

- [ ] **Step 3: Run the target test and verify RED**

Run:

```bash
cd erp-ui
npm test -- oeGiftManagement.test.js
```

Expected: FAIL because the OE/gift pages do not yet contain the stock-style layout markers and mobile routes do not yet contain停用 filters.

### Task 2: Desktop OE Page Stock-Style Layout

**Files:**
- Modify: `erp-ui/src/views/inventory/oe/index.vue`
- Test: `erp-ui/test/oeGiftManagement.test.js`

- [ ] **Step 1: Replace OE template structure**

Change the OE page root from simple cards to:

```vue
<div class="app-container catalog-page oe-catalog-page">
  <div class="catalog-layout" :class="{ 'is-category-collapsed': categoryCollapsed }">
    <aside class="category-panel">...</aside>
    <main class="catalog-main">...</main>
  </div>
  <el-dialog title="OE详情" :visible.sync="detailOpen" width="760px" append-to-body>...</el-dialog>
</div>
```

Keep the existing edit drawer and import dialog for maintenance mode.

- [ ] **Step 2: Add OE state and computed metrics**

Add data fields:

```js
categoryCollapsed: false,
categoryKeyword: "",
categoryLoading: false,
categoryLoadError: "",
categoryTreeData: [],
categoryProps: { children: "children", label: "categoryName" },
selectedCategory: null,
detailOpen: false,
detailOe: null
```

Add computed values for `selectedCategoryName`, `metricTotal`, `metricEnabled`, `metricDisabled`, and `metricPriced`.

- [ ] **Step 3: Add OE category tree methods**

Add `getDefaultCategoryTree`, `toggleCategoryPanel`, `filterCategoryNode`, `handleCategoryClick`, `selectCategoryRoot`, and update `loadCategories` to populate both tree data and flattened options.

- [ ] **Step 4: Add OE status tab and detail dialog behavior**

Add `handleStatusTabChange`, `openDetailDialog`, `imageUrl`, `statusLabel`, `statusTagType`, and change row click/detail buttons to open the detail dialog while edit continues using the drawer.

- [ ] **Step 5: Run the OE/gift management test**

Run:

```bash
cd erp-ui
npm test -- oeGiftManagement.test.js
```

Expected: still FAIL until the gift page and mobile route config are updated.

### Task 3: Desktop Gift Page Stock-Style Layout

**Files:**
- Modify: `erp-ui/src/views/inventory/gift/index.vue`
- Test: `erp-ui/test/oeGiftManagement.test.js`

- [ ] **Step 1: Replace gift template structure**

Use the same `catalog-layout`, `category-panel`, `catalog-main`, `metric-row`, `catalog-tabs`, and `detailOpen` dialog structure as OE, but with gift labels and fields.

- [ ] **Step 2: Add gift state and computed metrics**

Add data fields matching OE and computed values for `selectedCategoryName`, `metricTotal`, `metricEnabled`, `metricDisabled`, and `metricPriced`.

- [ ] **Step 3: Add gift category tree and status tab methods**

Add the same category methods as OE, calling `giftCategoryTree`, and make status tabs set `queryParams.status`.

- [ ] **Step 4: Add gift detail image preview**

Use an `el-dialog` with `el-image` preview and `el-descriptions` fields for礼盒编码、产品名称、上线分类、等级、规格、补货单位、指导售价1、指导售价2、产品描述、状态、备注.

- [ ] **Step 5: Run the OE/gift management test**

Run:

```bash
cd erp-ui
npm test -- oeGiftManagement.test.js
```

Expected: still FAIL until mobile route config is updated.

### Task 4: Mobile OE/Gift Query-Only Route Polish

**Files:**
- Modify: `erp-ui/src/views/mobile/mobileRouteDefinitions.js`
- Modify: `erp-ui/src/views/mobile/feature/featureMapper.js`
- Test: `erp-ui/test/oeGiftManagement.test.js`
- Test: `erp-ui/test/mobileFeatureMapper.test.js`
- Test: `erp-ui/test/mobileFeatureRouteCoverage.test.js`

- [ ] **Step 1: Update mobile route quick filters**

Change OE actions to:

```js
actions: [
  { label: '启用OE', icon: 'cube', query: { status: '0' } },
  { label: '停用OE', icon: 'warning', query: { status: '1' } },
  { label: '全部OE', icon: 'search', query: {} }
]
```

Change gift actions to:

```js
actions: [
  { label: '启用礼盒', icon: 'document', query: { status: '0' } },
  { label: '停用礼盒', icon: 'warning', query: { status: '1' } },
  { label: '全部礼盒', icon: 'search', query: {} }
]
```

- [ ] **Step 2: Ensure mapper remains query-display only**

Keep OE/gift row mapping as display-only and do not add form config or action payloads for OE/gift maintenance.

- [ ] **Step 3: Run target tests and verify GREEN**

Run:

```bash
cd erp-ui
npm test -- oeGiftManagement.test.js mobileFeatureMapper.test.js mobileFeatureRouteCoverage.test.js
```

Expected: PASS.

### Task 5: Final Frontend Verification

**Files:**
- All touched files.

- [ ] **Step 1: Run focused frontend tests**

Run:

```bash
cd erp-ui
npm test -- oeGiftManagement.test.js mobileFeatureMapper.test.js mobileFeatureRouteCoverage.test.js stockCategoryFilter.test.js
```

Expected: PASS.

- [ ] **Step 2: Inspect git diff**

Run:

```bash
git diff -- erp-ui/src/views/inventory/oe/index.vue erp-ui/src/views/inventory/gift/index.vue erp-ui/src/views/mobile/mobileRouteDefinitions.js erp-ui/test/oeGiftManagement.test.js
```

Expected: diff only contains OE/gift page layout, mobile OE/gift route filters, and targeted tests.

- [ ] **Step 3: Commit implementation**

Run:

```bash
git add docs/superpowers/plans/2026-07-09-oe-gift-stock-style-query.md erp-ui/src/views/inventory/oe/index.vue erp-ui/src/views/inventory/gift/index.vue erp-ui/src/views/mobile/mobileRouteDefinitions.js erp-ui/test/oeGiftManagement.test.js
git commit -m "feat: align oe gift query pages with stock layout"
```
