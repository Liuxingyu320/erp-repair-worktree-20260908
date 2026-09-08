const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  MOBILE_ROUTES,
  getMobileQuickActions
} = require("../src/views/mobile/mobileNavigation")

const {
  mapMobileFeatureRows
} = require("../src/views/mobile/feature/featureMapper")

const routerSource = fs.readFileSync(
  path.resolve(__dirname, "../src/router/index.js"),
  "utf8"
)
const mobileRouteSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/mobileRouteDefinitions.js"),
  "utf8"
)
const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const detailSheetSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileDetailSheet.vue"),
  "utf8"
)
const mobileExportConfigsSource = fs.existsSync(path.resolve(__dirname, "../src/views/mobile/feature/mobileExportConfigs.js"))
  ? fs.readFileSync(path.resolve(__dirname, "../src/views/mobile/feature/mobileExportConfigs.js"), "utf8")
  : ""
const featureServiceSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureService.js"),
  "utf8"
)
const featureMapperSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/featureMapper.js"),
  "utf8"
)

const missingDesktopReferenceRoutes = [
  ["product", "/mobile/product"],
  ["customer", "/mobile/customer"],
  ["supplier", "/mobile/supplier"],
  ["category", "/mobile/category"],
  ["stockLog", "/mobile/stock-log"],
  ["transferRecords", "/mobile/transfer-records"]
]

missingDesktopReferenceRoutes.forEach(([key, routePath]) => {
  assert.strictEqual(
    MOBILE_ROUTES[key],
    routePath,
    `${key} should have a dedicated phone-web route`
  )
  assert.ok(
    mobileRouteSource.includes(`path: '${routePath}'`) &&
      mobileRouteSource.includes(`featureKey: '${key}'`),
    `${routePath} should be registered as a mobile feature route`
  )
})

assert.ok(
  routerSource.includes("mobileRouteDefinitions"),
  "router should mount the shared mobile route definition list"
)

assert.ok(
  getMobileQuickActions("STORE").some(item => item.path === MOBILE_ROUTES.product) &&
    !getMobileQuickActions("STORE").some(item => item.path === MOBILE_ROUTES.customer),
  "store mobile workbench should expose product lookup but keep customer lookup out of quick actions"
)

assert.ok(
  getMobileQuickActions("WAREHOUSE").some(item => item.path === MOBILE_ROUTES.supplier) &&
    !getMobileQuickActions("WAREHOUSE").some(item => item.path === MOBILE_ROUTES.stockLog) &&
    !getMobileQuickActions("WAREHOUSE").some(item => item.path === MOBILE_ROUTES.transferRecords),
  "warehouse mobile workbench should expose supplier lookup but keep stock-log and transfer-record routes out of quick actions"
)

assert.ok(
  featureServiceSource.includes("listCustomer") &&
    featureServiceSource.includes("listSupplier") &&
    featureServiceSource.includes("categoryTree") &&
    featureServiceSource.includes("listStockLog") &&
    featureServiceSource.includes("listTransferRecords") &&
    featureServiceSource.includes("fetchMobileFeatureDetail"),
  "mobile feature service should call the same desktop inventory APIs for missing reference and record modules"
)

const replenishmentListBranch = featureServiceSource.match(/if \(featureKey === "replenishment"\) \{[\s\S]*?\n  \}/)
assert.ok(
  replenishmentListBranch &&
    replenishmentListBranch[0].includes("listTransferProcessing(normalizeTransferListQuery(query, options))") &&
    !replenishmentListBranch[0].includes("stockScope"),
  "mobile replenishment list should reuse the desktop transfer-processing API with the same mobile direction query normalization as transfer pages"
)

assert.ok(
  featureServiceSource.includes('if (featureKey === "replenishment" && id !== undefined)') &&
    featureServiceSource.includes("resolveDetail(getTransferDetail(id))"),
  "mobile replenishment detail should load transfer details by transferId instead of stock details by stockId"
)

assert.ok(
  featurePageSource.includes("fetchMobileFeatureDetail") &&
    detailSheetSource.includes("detail-section-list") &&
    featurePageSource.includes("selectedDetailSections") &&
    featurePageSource.includes("detailLoading"),
  "mobile feature detail sheet should load full backend details and render business detail sections"
)

assert.ok(
  featurePageSource.includes("mobileMapperContext()") &&
    featurePageSource.includes("const mapperContext = this.mobileMapperContext()") &&
    featurePageSource.includes("mapMobileFeatureRows(requestFeatureKey, result.rows, mapperContext)") &&
    featurePageSource.includes("mapMobileFeatureRows(this.featureKey, [detailRow], this.mobileMapperContext())"),
  "mobile feature page should snapshot selected context for list responses and pass current context into detail mappers"
)

assert.ok(
  featurePageSource.includes("MOBILE_EXPORT_CONFIG") &&
    featurePageSource.includes("mobileExportConfigs") &&
    !featurePageSource.includes("const MOBILE_EXPORT_CONFIG = {") &&
    featurePageSource.includes("featureActionItems") &&
    featurePageSource.includes("handleExportAction") &&
    featurePageSource.includes("this.download") &&
    featurePageSource.includes("this.exportFileName"),
  "mobile feature page should expose a shared export action for modules that have desktop export endpoints"
)

assert.ok(
  !featurePageSource.includes("product-import") &&
    !featurePageSource.includes("handleProductImportFile") &&
    !featurePageSource.includes("importProductData") &&
    !featurePageSource.includes("inventory/product/importTemplate"),
  "mobile product page should keep product import and template workflows disabled for read-only lookup"
)

assert.ok(
  mobileExportConfigsSource.includes("inventory/sales/export") &&
    mobileExportConfigsSource.includes("inventory/stock/log/export") &&
    mobileExportConfigsSource.includes("oa/purchase/export/my") &&
    mobileExportConfigsSource.includes("system/user/export") &&
    mobileExportConfigsSource.includes("schedule/job/export"),
  "mobile export config should reuse representative desktop inventory, OA, system, and monitor export endpoints"
)

const featureStageCss = featurePageSource.match(/\.feature-stage \{[\s\S]*?\}/)
assert.ok(featureStageCss, "mobile feature page should define feature-stage styles")
assert.ok(
  !featureStageCss[0].includes("z-index: 1"),
  "mobile detail overlay should not be trapped below the fixed bottom navigation by the feature-stage stacking context"
)

assert.ok(
  featureMapperSource.includes("mapProductRow") &&
    featureMapperSource.includes("mapCustomerRow") &&
    featureMapperSource.includes("mapSupplierRow") &&
    featureMapperSource.includes("mapCategoryRow") &&
    featureMapperSource.includes("mapStockLogRow") &&
    featureMapperSource.includes("buildMobileDetailSections"),
  "mobile feature mapper should cover desktop reference modules and detail sections"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("product", [{
    productCode: "TEA-001",
    productName: "明前龙井",
    categoryName: "绿茶",
    supplierName: "春山茶业",
    salePrice500g: 268,
    costPrice: 128,
    status: "0"
  }]),
  [{
    title: "明前龙井",
    code: "TEA-001",
    detail: "绿茶 · 春山茶业",
    status: "正常"
  }],
  "mobile product rows should expose desktop product lookup fields while hiding reference cost by default"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("product", [{
    productCode: "TEA-001",
    productName: "明前龙井",
    categoryName: "绿茶",
    supplierName: "春山茶业",
    salePrice500g: 268,
    costPrice: 128,
    status: "0"
  }], { permissions: ["inv:cost:view"] }),
  [{
    title: "明前龙井",
    code: "TEA-001",
    detail: "绿茶 · 春山茶业 · 参考成本价 ¥128.00",
    status: "正常"
  }],
  "mobile product rows should show reference cost only for roles with inv:cost:view"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("customer", [{
    customerCode: "CU-001",
    customerName: "云岫茶室",
    contactPhone: "13800000000",
    teaPreferences: "岩茶",
    cautions: "忌浓茶",
    status: "0"
  }]),
  [{
    title: "云岫茶室",
    code: "CU-001",
    detail: "13800000000 · 岩茶 · 忌浓茶",
    status: "正常"
  }],
  "mobile customer rows should expose store service-card fields without legacy customer levels"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("stockLog", [{
    productName: "明前龙井",
    productCode: "TEA-001",
    movementType: "sales_out",
    businessNo: "SO-001",
    changeQuantity: -2,
    afterQuantity: 18
  }]),
  [{
    title: "明前龙井",
    code: "TEA-001",
    detail: "销售出库 · SO-001 · 变动 -2",
    status: "结余 18"
  }],
  "mobile stock-log rows should expose desktop inventory movement fields"
)

const purchaseDetailItem = mapMobileFeatureRows("purchase", [{
  orderId: 88,
  orderNo: "PO-DETAIL",
  supplierName: "春山茶业",
  totalAmount: 100,
  status: "submitted",
  details: [
    { productName: "龙井", productCode: "TEA-001", quantity: 5, receivedQuantity: 2, unit: "盒" }
  ]
}])[0]

assert.deepStrictEqual(
  purchaseDetailItem._detailSections,
  [{
    title: "商品明细",
    rows: [
      { title: "龙井", meta: "TEA-001", value: "5 盒", extra: "已收 2 盒" }
    ]
  }],
  "mobile detail sheets should show order item details from backend detail APIs"
)
