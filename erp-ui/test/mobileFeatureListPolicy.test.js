const assert = require("assert")

const {
  buildApprovalRouteContext,
  buildFeatureExportQuery,
  buildFeatureRequestQuery,
  buildRouteFeatureQuery,
  cleanComparableQuery,
  firstQueryValue,
  isSameFeatureQuery,
  normalizeManagedStoreOptions,
  normalizeManagedStoreSelection,
  normalizeSearchKeyword,
  resolveActionLabelForQuery,
  resolveActiveFilterLabel,
  resolveManagedStoreName,
  resolveRouteNotice
} = require("../src/views/mobile/feature/mobileFeatureListPolicy")

assert.strictEqual(firstQueryValue(["first", "second"]), "first")
assert.strictEqual(firstQueryValue("single"), "single")
assert.deepStrictEqual(buildApprovalRouteContext({
  approvalTaskId: ["task-1", "task-2"],
  approvalInstanceId: "instance-1"
}), {
  approvalTaskId: "task-1",
  approvalInstanceId: "instance-1"
})

assert.deepStrictEqual(buildRouteFeatureQuery("purchase", {
  preview: "1",
  redirect: "/mobile/store",
  mobileRedirectReason: "context-mismatch",
  mobileRedirectMessage: "已切换",
  mobileRedirectFrom: "/mobile/purchase",
  todoType: "INV_PURCHASE_QC",
  businessId: "9001",
  approvalTaskId: ["task-9", "ignored"],
  empty: " ",
  keepZero: 0
}), {
  businessId: "9001",
  approvalTaskId: "task-9",
  keepZero: 0,
  orderId: "9001"
})

assert.strictEqual(normalizeSearchKeyword(null), "")
assert.strictEqual(normalizeSearchKeyword("  茶叶  "), "茶叶")

const featureQuery = { status: "draft", untouched: "yes" }
assert.deepStrictEqual(buildFeatureRequestQuery({
  featureQuery,
  searchKeyword: "  PO-001 ",
  activeSearchFieldConfig: { key: "orderNo" },
  activeStockStatus: "low"
}), {
  status: "draft",
  untouched: "yes",
  orderNo: "PO-001",
  stockStatus: "low"
})
assert.deepStrictEqual(featureQuery, { status: "draft", untouched: "yes" })
assert.deepStrictEqual(buildFeatureRequestQuery({
  featureQuery: { status: "draft" },
  searchKeyword: "ignored",
  activeSearchFieldConfig: null,
  activeStockStatus: ""
}), {
  status: "draft"
})

assert.deepStrictEqual(cleanComparableQuery({
  blank: " ",
  missing: null,
  zero: 0,
  disabled: false,
  status: "draft"
}), {
  zero: 0,
  disabled: false,
  status: "draft"
})
assert.strictEqual(isSameFeatureQuery(
  { status: "draft", page: 1, empty: "" },
  { page: "1", status: "draft" }
), true)
assert.strictEqual(isSameFeatureQuery({ status: "draft" }, { status: "pending" }), false)

const quickActions = [
  { label: "全部", query: {} },
  { label: "草稿", query: { status: "draft" } },
  { label: "刷新", behavior: "refresh" }
]
assert.strictEqual(resolveActionLabelForQuery(quickActions, { status: "draft", unused: "" }), "草稿")
assert.strictEqual(resolveActionLabelForQuery(quickActions, { status: "pending" }), "")

const managedStores = normalizeManagedStoreOptions([
  {
    deptId: 10,
    deptName: "总部",
    deptType: "COMPANY",
    children: [
      { deptId: 20, deptName: "一店", deptType: "store" },
      {
        deptId: 30,
        deptName: "一区",
        deptType: "REGION",
        children: [
          { deptId: "20", deptName: "重复一店", deptType: "STORE" },
          { deptId: 21, deptName: "", deptType: "STORE" },
          { deptId: 22, deptName: "一仓", deptType: "WAREHOUSE" }
        ]
      }
    ]
  }
])
assert.deepStrictEqual(managedStores, [
  { deptId: 20, deptName: "一店" },
  { deptId: 21, deptName: "未命名门店" }
])
assert.strictEqual(normalizeManagedStoreSelection(0, managedStores, 0), 0)
assert.strictEqual(normalizeManagedStoreSelection("20", managedStores, 0), "20")
assert.strictEqual(normalizeManagedStoreSelection(999, managedStores, 0), 0)
assert.strictEqual(resolveManagedStoreName({
  showManagedStoreSelect: false,
  managedStoreId: 20,
  managedStoreOptions: managedStores,
  allManagedStoreValue: 0
}), "")
assert.strictEqual(resolveManagedStoreName({
  showManagedStoreSelect: true,
  managedStoreId: 0,
  managedStoreOptions: managedStores,
  allManagedStoreValue: 0
}), "全部管理门店")
assert.strictEqual(resolveManagedStoreName({
  showManagedStoreSelect: true,
  managedStoreId: "20",
  managedStoreOptions: managedStores,
  allManagedStoreValue: 0
}), "一店")
assert.strictEqual(resolveManagedStoreName({
  showManagedStoreSelect: true,
  managedStoreId: 999,
  managedStoreOptions: managedStores,
  allManagedStoreValue: 0
}), "管理门店")

const requestQuery = { status: "draft" }
assert.deepStrictEqual(buildFeatureExportQuery({
  requestQuery,
  showManagedStoreSelect: true,
  managedStoreId: 20,
  allManagedStoreValue: 0,
  exportConfig: { contextScoped: true },
  selectedDeptId: 99,
  selectedDeptType: "WAREHOUSE"
}), {
  status: "draft",
  ownOnly: false,
  shopDeptId: 20
})
assert.deepStrictEqual(buildFeatureExportQuery({
  requestQuery,
  showManagedStoreSelect: false,
  managedStoreId: 0,
  allManagedStoreValue: 0,
  exportConfig: { contextScoped: true },
  selectedDeptId: 99,
  selectedDeptType: "WAREHOUSE"
}), {
  status: "draft",
  shopDeptId: 99,
  warehouseId: 99
})
assert.deepStrictEqual(requestQuery, { status: "draft" })

assert.deepStrictEqual(resolveRouteNotice({
  mobileRedirectReason: ["unavailable", "ignored"],
  mobileRedirectMessage: ["暂未开放", "ignored"]
}), {
  title: "移动端暂未开放",
  message: "暂未开放"
})
assert.deepStrictEqual(resolveRouteNotice({
  mobileRedirectReason: "context-mismatch"
}), {
  title: "已切换到当前组织",
  message: ""
})
assert.deepStrictEqual(resolveRouteNotice({}), {
  title: "移动端提示",
  message: ""
})

assert.strictEqual(resolveActiveFilterLabel({
  activeActionLabel: "草稿",
  resolvedActionLabel: "全部",
  selectedManagedStoreName: "一店",
  activeStockStatusLabel: "低库存",
  searchKeyword: " 茶叶 ",
  activeSearchFieldLabel: "商品"
}), "草稿 / 一店 / 低库存 / 商品：茶叶")
assert.strictEqual(resolveActiveFilterLabel({
  activeActionLabel: "",
  resolvedActionLabel: "全部",
  searchKeyword: "",
  activeSearchFieldLabel: "商品"
}), "全部")

console.log("mobile feature list policy tests passed")
