const assert = require("assert")
const fs = require("fs")
const path = require("path")

const stockSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/stock/index.vue"),
  "utf8"
)
const deptApiSource = fs.readFileSync(
  path.resolve(__dirname, "../src/api/system/dept.js"),
  "utf8"
)

assert.ok(
  stockSource.includes("showWarehouseStoreFilter"),
  "warehouse stock page should expose a computed flag for the store filter"
)

assert.ok(
  stockSource.includes("queryParams.shopDeptId"),
  "warehouse stock page should bind the selected store to queryParams.shopDeptId"
)

assert.ok(
  stockSource.includes("visibleStoreOptions"),
  "warehouse stock page should build store options from visible departments"
)

assert.ok(
  stockSource.includes("handleStoreFilterChange"),
  "warehouse stock page should reload data when the store filter changes"
)

assert.ok(
  deptApiSource.includes("export function listVisibleStoreDept(query)") &&
    deptApiSource.includes("url: '/system/dept/visible-store-list'"),
  "department API should expose visible stores under the selected warehouse scope"
)

assert.ok(
  stockSource.includes("listVisibleStoreDept") &&
    stockSource.includes("loadVisibleStoreOptions") &&
    stockSource.includes("scopeDeptId: this.currentDeptId"),
  "warehouse stock page should load store filter options from the warehouse-visible store endpoint"
)

assert.ok(
  stockSource.includes("showScopeToggle") &&
    stockSource.includes("currentScopeLabel") &&
    stockSource.includes("managedScopeLabel"),
  "stock page should use a two-button scope toggle for warehouse and store entries"
)

assert.ok(
  stockSource.includes('return this.isWarehouseStockEntry ? "可见库存" : "管理门店"'),
  "warehouse stock entry should keep the original visible-stock label while store entry uses managed stores"
)

assert.ok(
  stockSource.includes("当前门店") &&
    stockSource.includes("管理门店") &&
    stockSource.includes("this.queryParams.shopDeptId = this.currentDeptId"),
  "store stock entry should default to the login store and expose a managed-store scope button"
)

assert.ok(
  stockSource.includes("this.queryParams.shopDeptId = this.allStoreOptionValue") &&
    stockSource.includes("this.queryParams.ownOnly === false"),
  "managed-store scope should query all authorized stores by default"
)

assert.ok(
  stockSource.includes("showManagedStoreFilter") &&
    stockSource.includes("loadAuthorizedStoreOptions") &&
    stockSource.includes('label="全部管理门店"'),
  "store managed scope should render a selectable authorized-store dropdown"
)

assert.ok(
  stockSource.includes("const matchedStore = this.storeOptions.find") &&
    stockSource.includes("this.queryParams.shopDeptId = matchedStore.deptId"),
  "managed-store filter should reuse the matching option id so Element UI can render the store label"
)

const normalizeStoreFilterMatch = stockSource.match(
  /    normalizeStoreFilter\(\) \{([\s\S]*?)\n    \},\n    openAdjust\(row\)/
)
assert.ok(normalizeStoreFilterMatch, "managed-store normalization method should be extractable for regression coverage")

const normalizeStoreFilter = new Function(normalizeStoreFilterMatch[1])
const managedStoreContext = {
  showWarehouseStoreFilter: false,
  showStoreStockFilter: true,
  queryParams: { ownOnly: false, shopDeptId: "1268" },
  storeOptions: [{ deptId: 1268, deptName: "测试门店" }],
  allStoreOptionValue: 0,
  currentDeptId: "1268",
  hasValue(value) {
    return value !== undefined && value !== null && value !== ""
  }
}

normalizeStoreFilter.call(managedStoreContext)
assert.strictEqual(
  managedStoreContext.queryParams.shopDeptId,
  1268,
  "a sessionStorage string id should be canonicalized to the numeric option id instead of rendering as raw text"
)

const warehouseStoreContext = {
  showWarehouseStoreFilter: true,
  showStoreStockFilter: false,
  queryParams: { ownOnly: false, shopDeptId: "1268" },
  storeOptions: [{ deptId: 1268, deptName: "测试门店" }],
  allStoreOptionValue: 0,
  currentDeptId: "1245",
  hasValue: managedStoreContext.hasValue
}

normalizeStoreFilter.call(warehouseStoreContext)
assert.strictEqual(
  warehouseStoreContext.queryParams.shopDeptId,
  1268,
  "warehouse-visible store filters should also reuse the exact option id type"
)
