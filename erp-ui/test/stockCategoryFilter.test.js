const assert = require("assert")
const fs = require("fs")
const path = require("path")

const stockSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/stock/index.vue"),
  "utf8"
)

const stockDomainSource = fs.readFileSync(
  path.resolve(__dirname, "../../erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStock.java"),
  "utf8"
)

const stockMapperSource = fs.readFileSync(
  path.resolve(__dirname, "../../erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockMapper.xml"),
  "utf8"
)

assert.ok(
  stockSource.includes('class="stock-layout"') && stockSource.includes('class="category-panel"'),
  "stock page should render a left category panel"
)

assert.ok(
  stockSource.includes('ref="categoryTree"') && stockSource.includes('@node-click="handleCategoryClick"'),
  "stock page should use a clickable category tree"
)

assert.ok(
  stockSource.includes('class="search-card mb12"') &&
    stockSource.includes('class="metric-row"') &&
    stockSource.includes('class="table-card"') &&
    stockSource.includes('class="card-actions"'),
  "stock page should use the same category/search/metric/table structure as product management"
)

assert.ok(
  stockSource.includes("flex: 0 0 240px") &&
    stockSource.includes("border: none !important") &&
    !stockSource.includes('class="stock-dashboard"') &&
    !stockSource.includes('class="stock-metric"'),
  "stock category layout should match product management styling instead of the old standalone metric card layout"
)

assert.ok(
  stockSource.includes('import { categoryTree } from "@/api/inventory/category"'),
  "stock page should load inventory category tree data"
)

assert.ok(
  stockSource.includes(`v-hasPermi="['inv:category:list', 'inv:category:tree']"`) ||
    stockSource.includes(`v-hasPermi="['inv:category:list','inv:category:tree']"`),
  "stock page category refresh should allow category management or readonly category tree permission"
)

assert.ok(
  stockSource.includes("categoryLoadError") &&
    stockSource.includes("分类加载失败，请联系管理员授权"),
  "stock page should show a category tree load failure message instead of silently showing only all inventory"
)

assert.ok(
  stockSource.includes("categoryId: undefined") && stockSource.includes("handleCategoryClick(data)"),
  "stock query params should include categoryId and update it from category clicks"
)

assert.ok(
  stockDomainSource.includes("private Long categoryId"),
  "InvStock should accept categoryId as a query property"
)

assert.ok(
  stockMapperSource.includes("p.category_id") && stockMapperSource.includes("find_in_set(#{categoryId}, c.ancestors)"),
  "stock mapper should filter stock rows by selected category and its descendants"
)
