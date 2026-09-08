const assert = require("assert")
const fs = require("fs")
const path = require("path")

const { getMobileFeatureActions } = require("../src/views/mobile/feature/featureActions")
const { createMobileActionRuntime } = require("../src/views/mobile/feature/featureActionRuntime")

const draft = {
  _statusKey: "draft",
  _raw: {
    transferId: 144,
    fromDeptId: 1245,
    toDeptId: 1268,
    details: [{ itemType: "product", itemId: 182, itemName: "仓库QA茶样", quantity: 1 }]
  }
}

assert.deepStrictEqual(
  getMobileFeatureActions("replenishment", draft, { selectedDeptType: "STORE", selectedDeptId: 1268 }).map(action => action.id),
  ["editReplenishment", "submitReplenishment", "deleteReplenishmentDraft"],
  "a store replenishment draft should expose edit, submit, and real draft deletion"
)

{
  const calls = []
  const runtime = createMobileActionRuntime({
    deleteTransferDraft(transferId) {
      calls.push(transferId)
      return Promise.resolve({ code: 200 })
    }
  })

  runtime.runMobileFeatureAction("replenishment", "deleteReplenishmentDraft", draft)
    .then(() => {
      assert.deepStrictEqual(calls, [144], "draft deletion should call the dedicated transfer draft endpoint")
    })
    .catch(error => {
      setImmediate(() => { throw error })
    })
}

const componentDir = path.resolve(__dirname, "../src/views/mobile/feature/components")
const detailSheetSource = fs.readFileSync(path.join(componentDir, "MobileDetailSheet.vue"), "utf8")
const featurePageSource = fs.readFileSync(path.resolve(componentDir, "../index.vue"), "utf8")
const actionServiceSource = fs.readFileSync(path.resolve(componentDir, "../featureActionService.js"), "utf8")
const transferApiSource = fs.readFileSync(path.resolve(__dirname, "../src/api/inventory/transfer.js"), "utf8")

assert.ok(
  /body\.mobile-detail-sheet-open\s+\.bottom-nav[\s\S]*?visibility:\s*hidden;[\s\S]*?pointer-events:\s*none;/.test(detailSheetSource) &&
    !/body\.mobile-detail-sheet-open\s*\{[\s\S]*?touch-action:\s*none;[\s\S]*?\}/.test(detailSheetSource),
  "the detail sheet must keep vertical touch scrolling and remove the covering bottom navigation"
)

assert.ok(
  featurePageSource.includes("selectedItemEditSource") &&
    featurePageSource.includes("this.selectedItemEditSource = cloneMobileEditFormSource(detailRow)") &&
    featurePageSource.includes("openHydratedMobileEditForm(action)") &&
    featurePageSource.includes("fetchMobileFeatureDetail(") &&
    featurePageSource.includes("refreshMobileTransferAvailability") &&
    featurePageSource.includes("listStock(query, { silentError: true })") &&
    featurePageSource.includes('String(hydratedItem.sourceConfirmStatus || "").toUpperCase() === "RESELECT_REQUIRED"') &&
    featurePageSource.includes('availabilityStatus: "unknown"') &&
    featurePageSource.includes("来源库存复查失败，无法安全编辑草稿") &&
    featurePageSource.includes("this.$nextTick(() => this.openMobileForm(action, hydratedItem, refreshedData))"),
  "editing should retain the full detail response and recheck source availability before restoring a draft form"
)

assert.ok(
  featurePageSource.includes("const snapshot = snapshotMobileFormSheet(this.formSheet)") &&
    featurePageSource.includes("if (snapshot.open) this.formSheet = restoreMobileFormSheet(snapshot)"),
  "choosing continue editing should restore the mobile draft form snapshot instead of clearing it"
)

assert.ok(
  transferApiSource.includes("export function deleteTransferDraft") &&
    transferApiSource.includes("/inventory/transfer/delete/") &&
    actionServiceSource.includes("deleteTransferDraft"),
  "the mobile action service should wire the dedicated transfer draft deletion API"
)

console.log("mobile replenishment draft workflow checks passed")
