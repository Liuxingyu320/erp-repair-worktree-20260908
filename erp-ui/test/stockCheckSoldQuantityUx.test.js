const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const source = fs.readFileSync(path.resolve(uiRoot, "src/views/inventory/stockCheck/index.vue"), "utf8")
const stockCheckDomainSource = fs.readFileSync(path.resolve(repoRoot, "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockCheck.java"), "utf8")
const stockCheckDetailDomainSource = fs.readFileSync(path.resolve(repoRoot, "erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStockCheckDetail.java"), "utf8")
const stockCheckServiceSource = fs.readFileSync(path.resolve(repoRoot, "erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvStockCheckServiceImpl.java"), "utf8")
const stockCheckDetailMapperSource = fs.readFileSync(path.resolve(repoRoot, "erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckDetailMapper.xml"), "utf8")
const stockCheckDetailMapperJavaSource = fs.readFileSync(path.resolve(repoRoot, "erp-modules/erp-inventory/src/main/java/com/erp/inventory/mapper/InvStockCheckDetailMapper.java"), "utf8")

assert.ok(
  source.includes('label="盘前库存"') &&
    source.includes('label="初盘数量"') &&
    source.includes('label="盘亏数量"') &&
    source.includes('label="盘点差异"'),
  "stock check page should use inventory profit/loss terminology"
)

assert.ok(
  source.includes("lossQuantity(") &&
    source.includes("profitQuantity(") &&
    source.includes("buildStockCheckSummary("),
  "stock check page should calculate loss and profit from book and actual stock"
)

assert.ok(
  source.includes("提交盘点并进入差异审批") &&
    source.includes("盘亏数量：") &&
    source.includes("盘盈数量："),
  "stock check submission summary should explain both loss and profit"
)

assert.ok(
  source.includes('import { categoryTree } from "@/api/inventory/category"') &&
    source.includes('import { getProduct } from "@/api/inventory/product"') &&
    source.includes("categoryOptions") &&
    source.includes("productCategoryCache") &&
    source.includes("loadCategoryOptions") &&
    source.includes("flattenCategories"),
  "stock check detail dialogs should load product category options for filtering"
)

assert.ok(
  source.includes("formDetailCategoryId") &&
    source.includes("detailDetailCategoryId") &&
    source.includes("filteredFormDetails") &&
    source.includes("filteredDetailDetails") &&
    source.includes("filterDetailsByCategory") &&
    source.includes(":data=\"filteredFormDetails\"") &&
    source.includes(":data=\"filteredDetailDetails\""),
  "stock check detail tables should support category filtering after the draft is created"
)

assert.ok(
  source.includes("detail-total-banner") &&
    source.includes("总共 {{ filteredFormDetails.length }} 个商品") &&
    source.includes("总共 {{ filteredDetailDetails.length }} 个商品") &&
    !source.includes("detail-filter-count") &&
    !source.includes("check-summary-strip"),
  "stock check detail dialogs should show one prominent total count instead of ratio text or summary cards"
)

assert.ok(
  source.includes("details: this.form.details.map(item => ({"),
  "stock check input submit should keep submitting all detail rows, not only the filtered view"
)

assert.ok(
  source.includes("hydrateDetailCategories(this.form.details)") &&
    source.includes("hydrateDetailCategories(this.detail.details)") &&
    source.includes("getProduct(productId)") &&
    source.includes("applyProductCategoryMetadata") &&
    source.includes("hasDetailCategoryMetadata"),
  "stock check detail filtering should hydrate missing category metadata from product details"
)

assert.ok(
  source.includes('require("./stockCheckDetailRules")') &&
    source.includes("filterStockCheckDetailsByCategory") &&
    source.includes("filterDetailsByCategory(details, categoryId)") &&
    source.includes("this.categoryOptions"),
  "stock check detail category filtering should delegate to the directly tested detail rules"
)

assert.ok(
  source.includes("盘点范围") &&
    source.includes("按商品分类") &&
    source.includes("指定商品") &&
    source.includes("抽盘") &&
    source.includes("createScope") &&
    source.includes("categoryCheckProductIds") &&
    source.includes("selectedCheckProductIds") &&
    source.includes("sampleSize") &&
    source.includes("details: productIds.map(productId => ({ productId }))") &&
    stockCheckDomainSource.includes("private Long categoryId") &&
    stockCheckDomainSource.includes("private String checkScope") &&
    stockCheckServiceSource.includes("filterSnapshotRows") &&
    stockCheckServiceSource.includes("matchesCategory"),
  "stock check create flow should persist and enforce all/category/selected/sample scopes"
)

assert.ok(
  stockCheckDetailDomainSource.includes("private Long categoryId") &&
    stockCheckDetailDomainSource.includes("private String categoryName") &&
    stockCheckDetailDomainSource.includes("private String categoryFullPath") &&
    stockCheckDetailMapperSource.includes("p.category_id as category_id") &&
    stockCheckDetailMapperSource.includes("c.category_name as category_name") &&
    stockCheckDetailMapperSource.includes("category_full_path") &&
    !stockCheckDetailMapperJavaSource.includes('@Param("categoryId") Long categoryId') &&
    !stockCheckDetailMapperSource.includes("find_in_set(#{categoryId}, c.ancestors)") &&
    !stockCheckDetailMapperSource.includes('collection="productIds"'),
  "stock check detail rows should expose product category metadata for client-side filtering"
)

assert.ok(
  source.includes('import { listStock } from "@/api/inventory/stock"') &&
    source.includes("searchCheckStockOptions") &&
    source.includes("loadCreateCategoryProducts") &&
    !source.includes("detailStatusFilter") &&
    !source.includes("stockCheckRowClassName"),
  "stock check range selection should reuse scoped stock search without unrelated review filters"
)

assert.ok(
  source.includes("盘亏") &&
    source.includes("盘盈") &&
    !source.includes("卖出数量") &&
    !source.includes('label="差异类型"'),
  "stock check page should not mislabel unexplained inventory variance as sales"
)

assert.ok(
  source.includes("盲盘已开启") &&
    source.includes("复盘阈值") &&
    source.includes("盘点人") &&
    source.includes("截止时间") &&
    source.includes("needsRecount") &&
    source.includes("recountQty: item.recountQty") &&
    stockCheckDomainSource.includes("private String blindCheck") &&
    stockCheckDomainSource.includes("private BigDecimal recountThreshold") &&
    stockCheckDetailDomainSource.includes("private String recountRequired") &&
    stockCheckDetailDomainSource.includes("private BigDecimal recountQty") &&
    stockCheckServiceSource.includes("requiresRecount") &&
    stockCheckDetailMapperSource.includes("recount_required"),
  "stock check input should support blind counting and mandatory recounts"
)

console.log("stockCheckSoldQuantityUx tests passed")
