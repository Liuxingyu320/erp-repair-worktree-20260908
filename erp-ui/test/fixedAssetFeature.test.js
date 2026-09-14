const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

function read() {
  return fs.readFileSync(path.join.apply(path, arguments), "utf8")
}

function countIncludes(source, text) {
  return source.split(text).length - 1
}

const { MOBILE_ROUTES, getMobileQuickActions } = require("../src/views/mobile/mobileNavigation")
const { mapMobileFeatureRows } = require("../src/views/mobile/feature/featureMapper")
const { getMobileFeatureActions } = require("../src/views/mobile/feature/featureActions")

const apiSource = read(uiRoot, "src/api/oa/fixedAsset.js")
const configPage = read(uiRoot, "src/views/oa/fixedAsset/config/index.vue")
const configBatchRecovery = read(uiRoot, "src/mixins/fixedAssetConfigBatch.js")
const configService = read(repoRoot, "erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java")
const atomicConfigMethod = configService.slice(configService.indexOf("public OaFixedAssetConfigSnapshot saveConfigBatch"), configService.indexOf("public List<OaFixedAssetConfig> selectConfigList"))
const repairPage = read(uiRoot, "src/views/oa/fixedAsset/repair/index.vue")
const mobileRoutes = read(uiRoot, "src/views/mobile/mobileRouteDefinitions.js")
const mobileService = read(uiRoot, "src/views/mobile/feature/featureService.js")
const mobileForms = read(uiRoot, "src/views/mobile/feature/mobileFormConfigs.js")
const menuSql = read(repoRoot, "sql/erp_oa_fixed_asset_20260619.sql")

assert.ok(apiSource.includes("listFixedAssetConfigs"), "fixed asset API should expose config list")
assert.ok(!apiSource.includes("approveFixedAssetException"), "fixed asset API should not expose the retired over-quota exception approval")
assert.ok(apiSource.includes("submitFixedAssetRepair"), "fixed asset API should expose repair submit")
assert.ok(apiSource.includes("precheckFixedAssetRepair"), "fixed asset API should expose quota precheck")
assert.ok(
  apiSource.includes("submitFixedAssetRepairBatch") &&
    apiSource.includes("/oa/fixedAsset/repair/submit/batch"),
  "fixed asset API should expose batch repair submit for multi-asset reports"
)
assert.ok(!apiSource.includes("confirmFixedAssetRepair"), "fixed asset API should not expose historical exception confirmation")
assert.ok(
  !apiSource.includes("ReplenishmentOutbox") &&
    !apiSource.includes("replenishment-outbox"),
  "fixed asset API should not expose the retired standalone OE replenishment outbox"
)

assert.ok(configPage.includes("年度申报比例"), "config page should edit annual repair ratio")
assert.ok(configPage.includes("固定资产明细"), "config page should show asset config rows")
assert.ok(!configPage.includes("异常批准"), "config page should not include the retired exception approval workspace")
assert.ok(configPage.includes("当前可用额度"), "config page should show quota summary")
assert.ok(configPage.includes("自行购买且无需上报"), "config page should explain the over-quota self-purchase rule")
assert.ok(
  !configPage.includes("补货投递运维") &&
    !configPage.includes("fixedAsset:outbox") &&
    !configPage.includes("OA_FIXED_ASSET_REPLENISHMENT_DEAD"),
  "fixed asset config should not expose the retired standalone OE replenishment operations workbench"
)
assert.ok(
  configPage.includes("添加店铺固定资产明细"),
  "config page should describe the action as adding store fixed asset details, not creating a master asset"
)
assert.ok(
  !configPage.includes(":disabled=\"assetActionDisabled\" @click=\"openConfigForm()\"") &&
    configPage.includes("icon=\"el-icon-plus\" @click=\"openConfigForm()\""),
  "config page add action should open the dialog without requiring an external shop filter because the dialog selects the shop"
)
assert.ok(
  configPage.includes(":disabled=\"assetActionDisabled\" @click=\"handleExport\""),
  "config page export should still require an external shop filter because export has no in-dialog shop selector"
)
assert.ok(
  !configPage.includes("openExceptionForm") &&
    !configPage.includes("approveFixedAssetException") &&
    !configPage.includes("exceptionForm") &&
    !configPage.includes("未来已透支额度"),
  "config page should remove every exception-approval and future-overdraft entry"
)
assert.strictEqual(
  countIncludes(configPage, ":remote-method=\"searchOeItems\""),
  1,
  "only the fixed-asset detail dialog should use the global OE remote search"
)
assert.ok(
  configPage.includes(":data=\"storeConfigRows\"") &&
    configPage.includes("<el-drawer") &&
    configPage.includes("detailDrawerOpen") &&
    configPage.includes("activeStoreConfig.details || []") &&
    configPage.includes("groupFixedAssetConfigs"),
  "config page should render one store-level config row and show fixed asset detail rows in a drawer"
)
assert.ok(
  configPage.includes(":with-header=\"false\"") &&
    configPage.includes("drawer-header") &&
    configPage.includes("drawer-metrics") &&
    configPage.includes("drawer-table-card") &&
    configPage.includes(".asset-detail-drawer ::v-deep .el-drawer__body"),
  "fixed asset detail drawer should use a custom polished layout instead of the default cramped drawer styling"
)
assert.ok(
  configPage.includes("prop=\"shopDeptName\"") &&
    configPage.includes("prop=\"assetTotalAmount\"") &&
    configPage.includes("prop=\"availableQuotaAmount\"") &&
    configPage.includes("剩余可报销金额"),
  "config page store-level list should show store name, fixed asset total amount, and remaining reimbursable amount"
)
assert.ok(
  !configPage.includes("type=\"expand\""),
  "config page should not render fixed asset details as an inline table expansion"
)
assert.ok(
  configPage.includes("multiple") &&
    configPage.includes("v-model=\"selectedOeItemIds\"") &&
    configPage.includes("selectedAssetRows"),
  "config page add dialog should support selecting multiple OE items and editing their detail rows"
)
assert.ok(
  configPage.includes("@input=\"handleConfigShopChange\"") &&
    configPage.includes("applyStoreConfigToForm") &&
    configPage.includes("return this.changeConfigShop(shopDeptId)") && configBatchRecovery.includes("getFixedAssetConfigSnapshot(shop)"),
  "config page add dialog should load an existing store config when the shop is selected inside the dialog"
)
assert.ok(
  configPage.includes("placeholder=\"请选择OE器皿，可输入名称或编码搜索\"") &&
    configPage.includes("@visible-change=\"handleOeDropdownVisible\"") &&
    configPage.includes("loadSelectableOeOptions"),
  "config page add dialog should behave like a selectable OE dropdown with searchable filtering, not a free-text field"
)
assert.ok(
  !configPage.includes("v-model=\"form.assetUnitPrice\"") &&
    !configPage.includes("v-model=\"form.assetAmount\""),
  "asset unit price and amount should be derived from OE price and quantity, not manually filled"
)
assert.ok(
  configPage.includes("return this.saveConfigBatch()") &&
    configBatchRecovery.includes("saveFixedAssetConfigBatch(command.payload)") &&
    apiSource.includes("/oa/fixedAsset/config/batch-save") &&
    configPage.includes("row.assetAmount = Number((qty * price).toFixed(2))") &&
    atomicConfigMethod.includes("row.setAssetAmount(resolveAssetAmount(row))") &&
    countIncludes(atomicConfigMethod, "rebuildQuota(shop,currentYear(),ratio)") === 1 &&
    !configPage.includes("runAssetRowRequestsSequentially"),
  "config page must retain calculated amounts while one atomic batch computes the authoritative amount and rebuilds quota once"
)
assert.strictEqual(
  countIncludes(configPage, ":append-to-body=\"true\""),
  2,
  "config page shop selectors should render dropdowns outside cards/dialogs so store names are not clipped"
)
assert.strictEqual(
  countIncludes(configPage, ":z-index=\"3000\""),
  2,
  "config page shop selector dropdowns should render above summary cards and tables"
)
assert.ok(
  configPage.includes(".dept-select {\n  width: 320px;"),
  "config page shop selector should be wide enough for long store names"
)

assert.ok(repairPage.includes("固定资产维修上报"), "repair page should render repair title")
assert.ok(
  repairPage.includes("getSelectedDeptContext") &&
    repairPage.includes("currentStoreName") &&
    repairPage.includes("当前门店") &&
    !repairPage.includes("<treeselect") &&
    !repairPage.includes("shopOptions") &&
    !repairPage.includes("shopTree") &&
    !repairPage.includes("handleRepairShopChange") &&
    !repairPage.includes("isStoreContext"),
  "repair page should bind to the current store and never ask users to choose a shop"
)
assert.ok(
  !repairPage.includes("预计维修金额") &&
    !repairPage.includes("v-model=\"form.estimatedRepairAmount\"") &&
    repairPage.includes("坏掉数量") &&
    repairPage.includes("repairUsageAmount") &&
    repairPage.includes("repairQuotaExceeded") &&
    repairPage.includes("当前可用额度不足"),
  "repair page should report fixed asset damage quantity and system-computed quota usage instead of collecting a repair amount"
)
assert.ok(
  repairPage.includes("repairAssetRows") &&
    repairPage.includes("addRepairAssetRow") &&
    repairPage.includes("removeRepairAssetRow") &&
    repairPage.includes("submitFixedAssetRepairBatch") &&
    !repairPage.includes("v-model=\"form.oeItemId\""),
  "repair page submit dialog should support multiple fixed asset rows instead of a single asset selector"
)
assert.ok(repairPage.includes("历史待确认（只读）"), "repair page should label historical exception rows as read-only")
assert.ok(!repairPage.includes("confirmFixedAssetRepair") && !repairPage.includes("confirmRepair"), "repair page should not confirm retired exception approvals")
assert.strictEqual(
  countIncludes(repairPage, ":append-to-body=\"true\""),
  0,
  "repair page should not render shop selectors"
)
assert.strictEqual(
  countIncludes(repairPage, ":z-index=\"3000\""),
  0,
  "repair page should not render shop selector dropdown z-index overrides"
)
assert.ok(
  repairPage.includes("import ImageUpload") &&
    repairPage.includes("components: { ImageUpload, ImageGallery }") &&
    (/<image-upload\s[^>]*v-model="form.imageUrls"/.test(repairPage)) &&
    !repairPage.includes("<el-input v-model=\"form.imageUrls\""),
  "repair page should use the shared image upload component for repair attachments"
)
assert.ok(
  repairPage.includes("prop=\"applicantName\"") &&
    repairPage.includes("prop=\"approvedBy\"") &&
    repairPage.includes("prop=\"approvedTime\"") &&
    repairPage.includes("{{ detail.applicantName || '-' }}") &&
    repairPage.includes("{{ detail.approvedBy || '-' }}") &&
    repairPage.includes("{{ detail.approvedTime || '-' }}"),
  "repair page should show applicant and approval metadata in list and detail"
)

assert.strictEqual(MOBILE_ROUTES.fixedAssetRepair, "/mobile/fixed-asset-repair")
assert.ok(
  mobileRoutes.includes("featureKey: 'fixedAssetRepair'") &&
    mobileRoutes.includes("allowedDeptTypes: ['STORE']"),
  "mobile fixed asset repair should be registered as a store-only mobile route"
)
assert.ok(
  getMobileQuickActions("STORE").some(action => action.path === "/mobile/fixed-asset-repair"),
  "store mobile workbench should expose fixed asset repair quick action"
)
assert.ok(
  mobileService.includes("listFixedAssetRepairs") &&
    mobileService.includes("getFixedAssetRepair"),
  "mobile service should reuse fixed asset repair list and detail APIs"
)
assert.ok(
  mobileForms.includes("fixedAssetRepair") &&
    !mobileForms.includes("estimatedRepairAmount") &&
    !mobileForms.includes('featureFlag: "oaOeReplenishment"') &&
    mobileForms.includes("repairQuantity") &&
    mobileForms.includes("faultDescription"),
  "mobile fixed asset reporting should remain available without the retired OE replenishment feature flag"
)
assert.ok(
  !repairPage.includes("oaOeReplenishment") &&
    !repairPage.includes("OE 补货链路已停用"),
  "fixed asset reporting should not be gated by the retired replenishment channel"
)

assert.deepStrictEqual(
  mapMobileFeatureRows("fixedAssetRepair", [{
    repairId: 8,
    oeItemName: "收银机",
    repairQuantity: 2,
    faultDescription: "茶杯裂开",
    shopDeptName: "测试门店",
    status: "pending_confirm"
  }]),
  [{
    title: "收银机",
    code: "FA-8",
    imageUrls: [],
    detail: "茶杯裂开 · 数量 2 · 测试门店",
    status: "历史待确认（只读）"
  }],
  "mobile fixed asset rows should expose damaged item quantity instead of repair amount and quota"
)

assert.deepStrictEqual(
  getMobileFeatureActions("fixedAssetRepair", { _statusKey: "pending_confirm", _raw: { repairId: 8 } }).map(action => action.id),
  [],
  "historical pending fixed asset exception rows should remain read-only"
)

assert.ok(menuSql.includes("固定资产管理"), "SQL should add fixed asset management menu")
assert.ok(menuSql.includes("oa:fixedAsset:config:list"), "SQL should add config list permission")
assert.ok(menuSql.includes("oa:fixedAsset:repair:list"), "SQL should add repair list permission")
assert.ok(menuSql.includes("repair_quantity"), "SQL should store fixed asset damaged quantity")
assert.ok(menuSql.includes("oa_fixed_asset_quota_month"), "SQL should create monthly fixed asset quota snapshots")
