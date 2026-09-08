const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const shopContextSource = fs.readFileSync(
  path.resolve(__dirname, "../src/utils/shopContext.js"),
  "utf8"
)
const permissionSource = fs.readFileSync(
  path.resolve(__dirname, "../src/permission.js"),
  "utf8"
)
const desktopContextPolicySource = fs.readFileSync(
  path.resolve(__dirname, "../src/utils/desktopContextPolicy.js"),
  "utf8"
)
const requestSource = fs.readFileSync(
  path.resolve(__dirname, "../src/utils/request.js"),
  "utf8"
)
const requestInventoryContextSource = fs.readFileSync(
  path.resolve(__dirname, "../src/utils/requestInventoryContext.js"),
  "utf8"
)
const selectShopSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/select-shop/index.vue"),
  "utf8"
)
const navbarSource = fs.readFileSync(
  path.resolve(__dirname, "../src/layout/components/Navbar.vue"),
  "utf8"
)
const deliveryNoticeSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/deliveryNotice/index.vue"),
  "utf8"
)
const transferSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/index.vue"),
  "utf8"
)
const purchaseSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/purchase/index.vue"),
  "utf8"
)
const salesSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/sales/index.vue"),
  "utf8"
)
const purchaseReturnSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/purchaseReturn/index.vue"),
  "utf8"
)
const salesReturnSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/salesReturn/index.vue"),
  "utf8"
)

function createMemoryStorage(initialState = {}) {
  const store = Object.assign({}, initialState)
  return {
    getItem(key) {
      return Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null
    },
    setItem(key, value) {
      store[key] = String(value)
    },
    removeItem(key) {
      delete store[key]
    },
    dump() {
      return Object.assign({}, store)
    }
  }
}

function loadShopContext({ localStorage, sessionStorage } = {}) {
  const sandbox = {
    module: { exports: {} },
    exports: {},
    localStorage: localStorage || createMemoryStorage(),
    sessionStorage: sessionStorage || createMemoryStorage()
  }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(
    shopContextSource.replace(/\bexport\s+/g, "") +
      `
module.exports = {
  getSelectedDeptId,
  getSelectedDeptName,
  getSelectedDeptType,
  getSelectedDeptContext,
  getSelectedInventoryDeptId,
  hasSelectedDeptContext,
  hasSelectedInventoryDeptContext,
  hasValidInventoryDeptContext: typeof hasValidInventoryDeptContext === "function"
    ? hasValidInventoryDeptContext
    : undefined,
  findDeptNodeById,
  hasValidatedSelectedDeptContext,
  isSelectableDeptType,
  setSelectedDept,
  clearSelectedDept,
  markSelectedDeptValidated,
  invalidateSelectedDeptValidation,
  isSelectedDeptValidated
}
`,
    sandbox,
    { filename: "shopContext.js" }
  )
  return {
    api: sandbox.module.exports,
    localStorage: sandbox.localStorage,
    sessionStorage: sandbox.sessionStorage
  }
}

{
  const { api } = loadShopContext()

  assert.strictEqual(typeof api.hasValidInventoryDeptContext, "function",
    "shop context should export a pure inventory department predicate")
  assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: "12", deptType: "STORE" }), true)
  assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: 9, deptType: "warehouse" }), true)
  assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: "", deptType: "STORE" }), false)
  assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: "   ", deptType: "WAREHOUSE" }), false)
  assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: "12", deptType: "COMPANY" }), false)
  assert.strictEqual(api.hasValidInventoryDeptContext({ deptId: "12", deptType: "" }), false)
}

{
  const { api } = loadShopContext()
  const tree = [{
    deptId: 1,
    children: [{ deptId: 1270, children: [] }, { deptId: 1271, children: [] }]
  }]

  assert.strictEqual(api.findDeptNodeById(tree, "1271").deptId, 1271,
    "department lookup should resolve a nested authorized node using normalized ids")
  assert.strictEqual(api.findDeptNodeById(tree, "1272"), null,
    "department lookup should reject a cached department missing from the authorized tree")
}

{
  const { api, localStorage, sessionStorage } = loadShopContext()

  api.setSelectedDept(12, "A 店", "store")

  assert.deepStrictEqual(
    sessionStorage.dump(),
    {
      selected_dept_id: "12",
      selected_dept_name: "A 店",
      selected_dept_type: "STORE",
      selected_dept_validated: "1"
    },
    "setSelectedDept should write selected department context and validation to sessionStorage"
  )
  assert.deepStrictEqual(
    localStorage.dump(),
    {},
    "setSelectedDept should not write selected department context to localStorage"
  )
}

{
  const { api, sessionStorage } = loadShopContext()

  api.setSelectedDept(101, "深圳总公司", "company")

  assert.deepStrictEqual(
    sessionStorage.dump(),
    {
      selected_dept_id: "101",
      selected_dept_name: "深圳总公司",
      selected_dept_type: "COMPANY"
    },
    "company selection should be persisted as a login context without inventory validation"
  )
  assert.strictEqual(api.hasSelectedDeptContext(), true, "company selection should count as a selected login context")
  assert.strictEqual(api.hasSelectedInventoryDeptContext(), false, "company selection should not count as inventory context")
  assert.strictEqual(api.hasValidatedSelectedDeptContext(), false, "company selection should not validate inventory routes")
  assert.strictEqual(api.getSelectedInventoryDeptId(), null, "company selection should not be sent as Dept-NumId")
  assert.strictEqual(api.isSelectableDeptType("GROUP"), true, "group nodes should be selectable login contexts")
  assert.strictEqual(api.isSelectableDeptType("COMPANY"), true, "company nodes should be selectable login contexts")
}

{
  const { api } = loadShopContext({
    localStorage: createMemoryStorage({
      selected_dept_id: "99",
      selected_dept_name: "B 店",
      selected_dept_type: "WAREHOUSE"
    }),
    sessionStorage: createMemoryStorage({
      selected_dept_id: "12",
      selected_dept_name: "A 店",
      selected_dept_type: "STORE",
      selected_dept_validated: "1"
    })
  })

  assert.strictEqual(api.getSelectedDeptId(), "12", "selected department id should be read from sessionStorage")
  assert.strictEqual(api.getSelectedDeptName(), "A 店", "selected department name should be read from sessionStorage")
  assert.strictEqual(api.getSelectedDeptType(), "STORE", "selected department type should be read from sessionStorage")
  const context = api.getSelectedDeptContext()
  assert.strictEqual(context.deptId, "12", "selected department context id should prefer the current tab session")
  assert.strictEqual(context.deptName, "A 店", "selected department context name should prefer the current tab session")
  assert.strictEqual(context.deptType, "STORE", "selected department context type should prefer the current tab session")
  assert.strictEqual(context.isStore, true, "selected department context should identify the selected store")
  assert.strictEqual(context.isWarehouse, false, "selected department context should not identify a store as a warehouse")
  assert.strictEqual(api.isSelectedDeptValidated(), true, "validation marker should be read from sessionStorage")
}

{
  const localStorage = createMemoryStorage({
    selected_dept_id: "99",
    selected_dept_name: "B 店",
    selected_dept_type: "WAREHOUSE"
  })
  const sessionStorage = createMemoryStorage({
    selected_dept_id: "12",
    selected_dept_name: "A 店",
    selected_dept_type: "STORE",
    selected_dept_validated: "1"
  })
  const { api } = loadShopContext({ localStorage, sessionStorage })

  api.clearSelectedDept()

  assert.deepStrictEqual(sessionStorage.dump(), {}, "clearSelectedDept should clear only the current tab session")
  assert.deepStrictEqual(
    localStorage.dump(),
    {
      selected_dept_id: "99",
      selected_dept_name: "B 店",
      selected_dept_type: "WAREHOUSE"
    },
    "clearSelectedDept should not clear another tab's localStorage-era context"
  )
}

const policySandbox = { module: { exports: {} }, exports: {} }
policySandbox.exports = policySandbox.module.exports
vm.runInNewContext(
  desktopContextPolicySource.replace(
    /\bexport\s+function\s+requiresInventoryContext/,
    "function requiresInventoryContext"
  ) + "\nmodule.exports = { requiresInventoryContext }",
  policySandbox,
  { filename: "desktopContextPolicy.js" }
)
const isInventoryContextRoute = policySandbox.module.exports.requiresInventoryContext

assert.ok(
  shopContextSource.includes("SELECTED_DEPT_VALIDATED_KEY") &&
    shopContextSource.includes("markSelectedDeptValidated") &&
    shopContextSource.includes("hasValidatedSelectedDeptContext"),
  "shop context should track when a local department selection has been validated against the authorized tree"
)

assert.ok(
  permissionSource.includes("hasValidatedSelectedDeptContext") &&
    permissionSource.includes("!hasValidatedSelectedDeptContext()") &&
    permissionSource.includes("hasSelectedDeptContext") &&
    permissionSource.includes("!hasSelectedDeptContext()"),
  "permission guard should require a validated shop context instead of trusting stale localStorage only"
)

assert.ok(
  requestSource.includes("getSelectedInventoryDeptId") &&
    requestSource.includes("getSelectedDeptType") &&
    requestSource.includes("applyInventoryDeptRequestContext(config, selectedDeptId, getSelectedDeptType())") &&
    requestInventoryContextSource.includes('target.headers["Dept-NumId"] = deptId') &&
    requestInventoryContextSource.includes("normalizePositiveDeptId(selectedDeptId)") &&
    requestInventoryContextSource.includes('selectedType === "STORE"'),
  "request interceptor should send attendance Dept-NumId only for selected store contexts"
)

assert.ok(
  isInventoryContextRoute("/inventory/deliveryNotice") &&
    isInventoryContextRoute("/inventory/salesReturn"),
  "desktop inventory context guard should cover all /inventory/ routes, including delivery notices and returns"
)

assert.ok(
  selectShopSource.includes("handleInvalidCachedDept") &&
    selectShopSource.includes("clearSelectedDept()") &&
    selectShopSource.includes("当前选择已不在授权范围内，请重新选择"),
  "shop selection should clear and explain a cached department that is no longer in the authorized tree"
)

assert.ok(
  navbarSource.includes("validateCurrentDeptContext") &&
    navbarSource.includes("listShopTree({ silentError: true })") &&
    navbarSource.includes("if (!context.deptId)") &&
    navbarSource.includes("findDeptNodeById(nodes, requestedDeptId)") &&
    navbarSource.includes("String(current.deptId || '') !== requestedDeptId") &&
    navbarSource.includes("setSelectedDept(authorizedDept.deptId, authorizedName, authorizedType)") &&
    navbarSource.includes("clearSelectedDept()") &&
    navbarSource.includes(".catch(() => true)"),
  "the global header should clear a cached organization that no longer exists in the authorized tree"
)

assert.ok(
  selectShopSource.includes("isSelectableDeptType") &&
    selectShopSource.includes("isValidInventoryDeptType(this.selectedDeptType)") &&
    selectShopSource.includes("请选择具体门店或仓库进入业务工作台"),
  "shop selection should keep company/group nodes browsable but require a store or warehouse as the final business context"
)

assert.ok(
  selectShopSource.includes("const applied = this.applySelectedDept") &&
    selectShopSource.includes("if (!applied)") &&
    selectShopSource.includes("return false") &&
    selectShopSource.includes("return true"),
  "shop selection should know whether applying the cached department succeeded"
)

assert.ok(
  !deliveryNoticeSource.includes("店铺ID") &&
    deliveryNoticeSource.includes("销售门店") &&
    deliveryNoticeSource.includes("发货仓库") &&
    deliveryNoticeSource.includes("salesShopLabel") &&
    deliveryNoticeSource.includes("warehouseLabel") &&
    deliveryNoticeSource.includes("shopDeptName") &&
    deliveryNoticeSource.includes("未关联组织名称") &&
    !deliveryNoticeSource.includes("\"组织ID \" + row.shopDeptId"),
  "delivery notices should distinguish the sales shop from the delivery warehouse and avoid leaking raw organization ids"
)

assert.ok(
  transferSource.includes("请将本批数量分为验收入库、拒收、残损和短少") &&
    transferSource.includes("<el-input-number v-model=\"scope.row.receiveQuantity\"") &&
    transferSource.includes("scope.row.rejectedQuantity") &&
    transferSource.includes("scope.row.damagedQuantity") &&
    transferSource.includes("scope.row.discrepancyNote") &&
    transferSource.includes("scope.row.attachmentRefs") &&
    transferSource.includes("差异单已生成"),
  "transfer receipt should classify accepted, rejected, damaged, and shortage quantities and capture discrepancy evidence"
)

assert.ok(
  !purchaseSource.includes("dangerouslyUseHTMLString") &&
    purchaseSource.includes("detailOpen") &&
    purchaseSource.includes("采购单详情") &&
    purchaseSource.includes(":data=\"detailOrder.details\""),
  "purchase order details should use a structured Element UI dialog with a detail table instead of HTML alert strings"
)

assert.ok(
  !salesSource.includes("dangerouslyUseHTMLString") &&
    salesSource.includes("detailOpen") &&
    salesSource.includes("销售单详情") &&
    salesSource.includes(":data=\"detailOrder.details\""),
  "sales order details should use a structured Element UI dialog with a detail table instead of HTML alert strings"
)

assert.ok(
  purchaseReturnSource.includes("purchaseDetailId: item.detailId"),
  "purchase return details should carry the original purchase detail id for duplicate-product source rows"
)

assert.ok(
  salesReturnSource.includes("isSelectedStore") &&
    salesReturnSource.includes("ensureStoreContext") &&
    salesReturnSource.includes("salesDetailId: item.detailId"),
  "sales returns should require store context and carry the original sales detail id"
)
