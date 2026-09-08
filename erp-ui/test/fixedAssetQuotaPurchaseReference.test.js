const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")
const readRepo = file => fs.readFileSync(path.resolve(repoRoot, file), "utf8")

const page = readUi("src/views/oa/fixedAsset/repair/index.vue")
const api = readUi("src/api/oa/fixedAsset.js")
const service = readRepo("erp-modules/erp-oa/src/main/java/com/erp/oa/service/impl/OaFixedAssetServiceImpl.java")
const quotaMapper = readRepo("erp-modules/erp-oa/src/main/resources/mapper/oa/OaFixedAssetQuotaMapper.xml")
const oeService = readRepo("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvOeServiceImpl.java")
const oePage = readUi("src/views/inventory/oe/index.vue")
const mobileRuntime = readUi("src/views/mobile/feature/featureActionRuntime.js")
const mobileForm = readUi("src/views/mobile/feature/components/MobileFormSheet.vue")
const referenceSql = readRepo("sql/erp_inventory_oe_purchase_reference_20260713.sql")

assert.ok(
  api.includes("precheckFixedAssetRepair") && api.includes("submitFixedAssetRepairBatch") &&
    !api.includes("approveFixedAssetException") && !api.includes("confirmFixedAssetRepair"),
  "fixed asset API should precheck/batch submit without retired exception approval endpoints"
)
assert.ok(
  page.includes("purchaseReferenceUrl") && page.includes("purchaseReferenceNote") &&
    page.includes("itemDescription") && page.includes("imageUrl") &&
    page.includes("自行购买且无需上报"),
  "over-quota UI should preserve the same-item description, image, purchase reference, and self-purchase guidance"
)
assert.ok(
  page.includes("/^https:\\/\\//i") &&
    page.includes('window.open(url, "_blank", "noopener,noreferrer")'),
  "purchase references should open only HTTPS pages without opener/referrer access"
)
assert.ok(
  service.includes("lockQuotaForSubmission") &&
    service.includes("selectQuotaForUpdate") &&
    service.includes('"FIXED_ASSET_QUOTA_EXCEEDED"') &&
    service.includes("insertLedger") && !service.includes("enqueueReplenishment"),
  "quota submission should lock/recheck before writing the fixed-asset report and ledger without a duplicate replenishment workflow"
)
assert.ok(
  service.includes('"FIXED_ASSET_EXCEPTION_APPROVAL_DISABLED"') &&
    !service.includes("insertLedger(repair, year, TYPE_SPECIAL_EXTRA"),
  "backend must reject the retired over-quota bypass"
)
assert.ok(
  quotaMapper.includes('<select id="selectQuotaForUpdate"') &&
    quotaMapper.includes("for update") && quotaMapper.includes("insert ignore into oa_fixed_asset_quota"),
  "quota mapper should safely create and lock the store/year row"
)
assert.ok(
  oeService.includes('"https".equalsIgnoreCase(uri.getScheme())') &&
    oeService.includes("purchaseReferenceAllowedHosts") &&
    oeService.includes("getPurchaseReferenceTouched") &&
    oeService.includes("countActiveFixedAssetConfigByOeItemId"),
  "OE master data should enforce HTTPS and the configured host allowlist"
)
assert.ok(
  oePage.includes("同款购买链接") && oePage.includes("购买说明") &&
    oePage.includes("purchaseReferenceUpdatedBy") &&
    oePage.includes("getOePurchaseReferencePolicy") &&
    oePage.includes("purchaseReferenceStatus"),
  "warehouse OE maintenance should expose purchase reference editing, audit, policy, and completeness status"
)
assert.ok(
  service.includes("FIXED_ASSET_PURCHASE_REFERENCE_INCOMPLETE") &&
    service.includes("missingPurchaseReferenceFields"),
  "active fixed-asset configuration should reject incomplete OE purchase reference data"
)
assert.ok(
  mobileRuntime.includes('call("precheckFixedAssetRepair", payload)') &&
    mobileRuntime.indexOf('call("precheckFixedAssetRepair", payload)') < mobileRuntime.indexOf('call("submitFixedAssetRepair", payload)') &&
    mobileForm.includes("fixedAssetPrecheck") && mobileForm.includes("submissionBlocked") &&
    mobileForm.includes('rel="noopener noreferrer"'),
  "mobile repair should precheck quota and render a blocked safe purchase-reference state"
)
assert.ok(
  referenceSql.includes("purchase_reference_url") &&
    referenceSql.includes("INVALID_SCHEME") && referenceSql.includes("MISSING_REFERENCE"),
  "purchase reference migration should include a pre-release completeness report"
)

console.log("fixedAssetQuotaPurchaseReference tests passed")
