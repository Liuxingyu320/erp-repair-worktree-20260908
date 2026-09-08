const assert = require("assert")
const fs = require("fs")
const path = require("path")
const { mapMobileFeatureRows } = require("../src/views/mobile/feature/featureMapper")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

function read() {
  return fs.readFileSync(path.join.apply(path, arguments), "utf8")
}

const oeApi = read(uiRoot, "src/api/inventory/oe.js")
const giftApi = read(uiRoot, "src/api/inventory/gift.js")
const oePage = read(uiRoot, "src/views/inventory/oe/index.vue")
const giftPage = read(uiRoot, "src/views/inventory/gift/index.vue")
const oeCategoryPage = read(uiRoot, "src/views/inventory/oe/category/index.vue")
const giftCategoryPage = read(uiRoot, "src/views/inventory/gift/category/index.vue")
const fixedAssetConfigPage = read(uiRoot, "src/views/oa/fixedAsset/config/index.vue")
const fixedAssetRepairPage = read(uiRoot, "src/views/oa/fixedAsset/repair/index.vue")
const mobileRoutes = read(uiRoot, "src/views/mobile/mobileRouteDefinitions.js")
const mobileNavigation = read(uiRoot, "src/views/mobile/mobileNavigation.js")
const mobileService = read(uiRoot, "src/views/mobile/feature/featureService.js")
const mobileMapper = read(uiRoot, "src/views/mobile/feature/featureMapper.js")
const mobileSearch = read(uiRoot, "src/views/mobile/feature/featureSearchConfigs.js")
const mobileExport = read(uiRoot, "src/views/mobile/feature/mobileExportConfigs.js")
const mobileEntity = read(uiRoot, "src/views/mobile/feature/mobileEntityService.js")
const mobileForms = read(uiRoot, "src/views/mobile/feature/mobileFormConfigs.js")
const mobilePayloads = read(uiRoot, "src/views/mobile/feature/mobileFormPayloads.js")
const sql = read(repoRoot, "sql/erp_inventory_oe_gift_management_20260706.sql")

assert.ok(oeApi.includes("/inventory/oe/list"), "OE API should expose list endpoint")
assert.ok(oeApi.includes("/inventory/oe/category/tree"), "OE API should expose category tree endpoint")
assert.ok(oeApi.includes("importOeData"), "OE API should expose import function")
assert.ok(giftApi.includes("/inventory/gift/list"), "gift API should expose list endpoint")
assert.ok(giftApi.includes("/inventory/gift/category/tree"), "gift API should expose category tree endpoint")
assert.ok(giftApi.includes("importGiftData"), "gift API should expose import function")

assert.ok(oeCategoryPage.includes("OE分类"), "OE category page should render category title")
assert.ok(giftCategoryPage.includes("礼盒分类"), "gift category page should render category title")
assert.ok(oePage.includes("OE管理") && oePage.includes("OE资料查询"), "OE page should support maintenance and readonly titles")
assert.ok(oePage.includes("成本价") && oePage.includes("供应商电话"), "OE page should show cost and supplier phone")
assert.ok(oePage.includes("readonlyMode") && oePage.includes("v-if=\"!readonlyMode\""), "OE readonly mode should hide write actions")
assert.ok(oePage.includes("catalog-layout"), "OE page should use stock-style catalog layout")
assert.ok(oePage.includes("category-panel"), "OE page should render a left category panel")
assert.ok(oePage.includes("metric-row"), "OE page should render metric cards")
assert.ok(oePage.includes("catalog-tabs"), "OE page should render status tabs")
assert.ok(oePage.includes("detailOpen"), "OE detail should use a stock-style detail dialog")
assert.ok(oePage.includes("el-image"), "OE detail should preview the image")
assert.ok(giftPage.includes("礼盒管理") && giftPage.includes("礼盒资料查询"), "gift page should support maintenance and readonly titles")
assert.ok(giftPage.includes("指导售价1") && giftPage.includes("指导售价2"), "gift page should show guide prices")
assert.ok(giftPage.includes("参考成本价") && giftPage.includes("供应商"), "gift page should show cost and supplier")
assert.ok(giftPage.includes("readonlyMode") && giftPage.includes("v-if=\"!readonlyMode\""), "gift readonly mode should hide write actions")
assert.ok(giftPage.includes("catalog-layout"), "gift page should use stock-style catalog layout")
assert.ok(giftPage.includes("category-panel"), "gift page should render a left category panel")
assert.ok(giftPage.includes("metric-row"), "gift page should render metric cards")
assert.ok(giftPage.includes("catalog-tabs"), "gift page should render status tabs")
assert.ok(giftPage.includes("detailOpen"), "gift detail should use a stock-style detail dialog")
assert.ok(giftPage.includes("el-image"), "gift detail should preview the image")

assert.ok(fixedAssetConfigPage.includes("oeItemId"), "fixed asset config page should save OE item id")
assert.ok(fixedAssetConfigPage.includes("listOe"), "fixed asset config page should search OE catalog")
assert.ok(!fixedAssetConfigPage.includes("listProduct"), "fixed asset config page should not search product catalog")
assert.ok(fixedAssetRepairPage.includes("oeItemId"), "fixed asset repair page should submit OE item id")
assert.ok(fixedAssetRepairPage.includes("oeItemName"), "fixed asset repair page should show OE item name")
assert.ok(!fixedAssetRepairPage.includes("productId"), "fixed asset repair page should not use product id")

assert.ok(mobileRoutes.includes("path: '/mobile/oe'") && mobileRoutes.includes("featureKey: 'oe'"), "mobile OE route should be registered")
assert.ok(mobileRoutes.includes("path: '/mobile/gift'") && mobileRoutes.includes("featureKey: 'gift'"), "mobile gift route should be registered")
assert.ok(mobileRoutes.includes("停用OE"), "mobile OE route should expose disabled OE quick filter")
assert.ok(mobileRoutes.includes("停用礼盒"), "mobile gift route should expose disabled gift quick filter")
assert.ok(!mobileRoutes.includes("新增OE"), "mobile OE should stay query-only")
assert.ok(!mobileRoutes.includes("新增礼盒"), "mobile gift should stay query-only")
assert.ok(!mobileRoutes.includes("导入OE"), "mobile OE should not expose import")
assert.ok(!mobileRoutes.includes("导入礼盒"), "mobile gift should not expose import")
assert.ok(mobileNavigation.includes("oe: [\"inv:oe:list\"]"), "mobile navigation should define OE permission")
assert.ok(mobileNavigation.includes("gift: [\"inv:gift:list\"]"), "mobile navigation should define gift permission")
assert.ok(mobileNavigation.includes("OE资料") && mobileNavigation.includes("礼盒资料"), "mobile workbench should expose OE and gift entries")
assert.ok(mobileService.includes("listOe") && mobileService.includes("getOe"), "mobile service should load OE list and detail")
assert.ok(mobileService.includes("listGift") && mobileService.includes("getGift"), "mobile service should load gift list and detail")
assert.ok(mobileMapper.includes("mapOeRow") && mobileMapper.includes("mapGiftRow"), "mobile mapper should render OE and gift rows")
assert.ok(mobileMapper.includes("成本价") && mobileMapper.includes("指导售价1") && mobileMapper.includes("指导售价2"), "mobile mapper should show OE/gift prices")
assert.ok(mobileMapper.includes("isStoreContext(options)"), "mobile OE mapper should split store and warehouse visibility")
assert.ok(mobileSearch.includes("oe:") && mobileSearch.includes("gift:"), "mobile search config should include OE and gift")
assert.ok(mobileExport.includes("inventory/oe/export") && mobileExport.includes("inventory/gift/export"), "mobile export config should include OE and gift")
assert.ok(mobileEntity.includes("fixedAssetOe") && mobileEntity.includes("listFixedAssetConfigs"), "mobile entity service should load fixed asset OE options")
assert.ok(mobileForms.includes("oeItemId") && mobileForms.includes("entity: \"fixedAssetOe\""), "mobile fixed asset form should use OE picker")
assert.ok(mobilePayloads.includes("oeItemId") && !mobilePayloads.includes("productId: form.productId"), "mobile fixed asset payload should use OE item id")

assert.ok(sql.includes("inv:oe:list") && sql.includes("OE资料查询"), "SQL should add OE list and readonly menu")
assert.ok(sql.includes("inv:gift:list") && sql.includes("礼盒资料查询"), "SQL should add gift list and readonly menu")

const oeRow = {
  oeItemId: 3,
  oeItemCode: "OE-001",
  oeItemName: "茶具套装",
  categoryName: "器皿",
  costPrice: 120,
  supplierName: "春山供应商",
  supplierPhone: "13800000000",
  status: "0"
}
const warehouseOe = mapMobileFeatureRows("oe", [oeRow], { selectedDeptType: "WAREHOUSE" })[0]
const storeOe = mapMobileFeatureRows("oe", [oeRow], { selectedDeptType: "STORE" })[0]
assert.ok(warehouseOe.detail.includes("春山供应商"), "warehouse mobile OE row should show supplier")
assert.ok(!storeOe.detail.includes("春山供应商"), "store mobile OE row should hide supplier")
assert.ok(
  warehouseOe._detailFields.some(field => field.label === "供应商") &&
    warehouseOe._detailFields.some(field => field.label === "供应商电话"),
  "warehouse mobile OE detail should show supplier fields"
)
assert.ok(
  !storeOe._detailFields.some(field => field.label === "供应商" || field.label === "供应商电话"),
  "store mobile OE detail should hide supplier fields"
)
