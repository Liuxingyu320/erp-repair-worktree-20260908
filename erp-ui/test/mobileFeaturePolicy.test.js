const assert = require("assert")

const {
  FEATURE_ACTION_FALLBACKS,
  MOBILE_ACTION_PERMISSIONS,
  MOBILE_FORM_CREATE_PERMISSIONS,
  MOBILE_FORM_EDIT_PERMISSIONS,
  MOBILE_FORM_SUBMIT_PERMISSIONS,
  applyContextualActionLabels,
  applyContextualMobileCopy,
  featureDefaults
} = require("../src/views/mobile/feature/mobileFeaturePolicy")

const feature = {
  title: "库存",
  contextCopy: {
    store: { title: "门店库存" },
    warehouse: { title: "仓库库存" }
  }
}

const warehouseFeature = applyContextualMobileCopy(feature, "warehouse")
assert.strictEqual(warehouseFeature.title, "仓库库存")
assert.notStrictEqual(warehouseFeature, feature)
assert.strictEqual(applyContextualMobileCopy(feature, "company"), feature)

const actions = [
  { label: "调拨", contextualLabel: "transfer" },
  { label: "查询" }
]
const warehouseActions = applyContextualActionLabels(
  actions,
  { transfer: { store: "门店调拨", warehouse: "仓库调拨" } },
  "warehouse"
)
assert.strictEqual(warehouseActions[0].label, "仓库调拨")
assert.strictEqual(warehouseActions[1], actions[1])

assert.strictEqual(featureDefaults.title, "手机工作台")
assert.deepStrictEqual(FEATURE_ACTION_FALLBACKS.salary[0], {
  label: "计算本月",
  icon: "trend",
  actionId: "calculateSalary"
})
assert.deepStrictEqual(MOBILE_FORM_CREATE_PERMISSIONS.transfer, ["inv:transfer:add"])
assert.deepStrictEqual(MOBILE_FORM_EDIT_PERMISSIONS.transfer, [
  "inv:transfer:add",
  "inv:transfer:edit"
])
assert.deepStrictEqual(MOBILE_FORM_SUBMIT_PERMISSIONS.transfer.submit, [
  "inv:transfer:submit"
])
assert.deepStrictEqual(MOBILE_ACTION_PERMISSIONS.calculateSalary, [
  "oa:salary:calculate"
])
