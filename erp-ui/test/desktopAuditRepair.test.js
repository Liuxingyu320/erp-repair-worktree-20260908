const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..", "..")

function read(relativePath) {
  return fs.readFileSync(path.resolve(root, relativePath), "utf8")
}

const sqlSource = read("sql/erp_multi_account_store_audit_remediation_20260620.sql")
const productSource = read("erp-ui/src/views/inventory/product/index.vue")
const supplierApiSource = read("erp-ui/src/api/inventory/supplier.js")
const userSource = read("erp-ui/src/views/system/user/index.vue")
const selectShopSource = read("erp-ui/src/views/select-shop/index.vue")
const transferSource = read("erp-ui/src/views/inventory/transfer/index.vue")
const stockSource = read("erp-ui/src/views/inventory/stock/index.vue")
const deliveryNoticeSource = read("erp-ui/src/views/inventory/deliveryNotice/index.vue")

;[
  "inv:stock:adjust",
  "inv:transfer:deliver",
  "inv:transfer:receive",
  "inv:deliveryNotice:deliver",
  "oa:salary:config",
  "oa:laborContract:list",
  "oa:laborContract:query",
  "oa:laborContract:add",
  "oa:laborContract:send",
  "oa:laborContract:void"
].forEach(permission => {
  assert.ok(
    sqlSource.includes(`'${permission}'`),
    `store-manager remediation SQL should remove ${permission} from dz`
  )
})

const productCreated = productSource.match(/created\(\)\s*\{([\s\S]*?)\n\s*\},/)
assert.ok(productCreated, "product page should keep a created hook")
assert.ok(
  !productCreated[1].includes("this.loadSuppliers()"),
  "product list initialization should not call the supplier API for read-only store users"
)
assert.ok(
  productSource.includes("canMaintainProduct") &&
    productSource.includes("ensureSupplierOptions") &&
    productSource.includes("listSupplier(") &&
    supplierApiSource.includes("export function listSupplier(query, config)") &&
    supplierApiSource.includes("Object.assign"),
  "supplier options should be lazy-loaded only for product maintenance flows and support silent permission errors"
)
assert.ok(
  /listProduct\(this\.queryParams,\s*\{\s*silentError:\s*true\s*\}\)/.test(productSource) &&
    productSource.includes("商品列表加载失败"),
  "product list should convert request failures into an empty state and friendly message instead of an unhandled page error"
)

assert.ok(
  userSource.includes("hasUnknownSetupStatus(row)") &&
    userSource.includes("配置状态待同步") &&
    userSource.includes("setupStatusType(row)") &&
    userSource.includes("return \"info\""),
  "user setup status should show an unknown/syncing state when backend count fields are missing"
)

assert.ok(
  selectShopSource.includes("isValidInventoryDeptType(this.selectedDeptType)") &&
    selectShopSource.includes("请选择具体门店或仓库进入业务工作台") &&
    selectShopSource.indexOf("isValidInventoryDeptType(this.selectedDeptType)") < selectShopSource.indexOf("setSelectedDept("),
  "select-shop confirm should only persist a concrete store or warehouse business context"
)

assert.ok(
  !transferSource.includes("组织ID：") &&
    transferSource.includes("请确认当前门店后提交要货"),
  "transfer create form should not expose raw organization IDs"
)
assert.ok(
  !stockSource.includes("ID: {{ adjustInventoryDeptId }}") &&
    stockSource.includes("adjustInventoryDeptHint"),
  "stock adjustment dialog should show a business hint instead of raw internal IDs"
)
assert.ok(
  !deliveryNoticeSource.includes("\"组织ID \" + row.shopDeptId") &&
    deliveryNoticeSource.includes("未关联组织名称"),
  "delivery notice organization fallback should avoid displaying internal organization IDs"
)

console.log("desktopAuditRepair tests passed")
