const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")
const uiSource = relativePath => fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
const repoSource = relativePath => fs.readFileSync(path.resolve(repoRoot, relativePath), "utf8")

const sales = uiSource("src/views/inventory/sales/index.vue")
const purchase = uiSource("src/views/inventory/purchase/index.vue")
const salesReturn = uiSource("src/views/inventory/salesReturn/index.vue")
const purchaseReturn = uiSource("src/views/inventory/purchaseReturn/index.vue")
const stock = uiSource("src/views/inventory/stock/index.vue")
const stockLog = uiSource("src/views/inventory/stock/log.vue")
const stockCheck = uiSource("src/views/inventory/stockCheck/index.vue")
const deliveryNotice = uiSource("src/views/inventory/deliveryNotice/index.vue")
const workbench = uiSource("src/views/index.vue")
const reportMapper = repoSource("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvReportMapper.xml")
const salesReturnService = repoSource("erp-modules/erp-inventory/src/main/java/com/erp/inventory/service/impl/InvSalesReturnServiceImpl.java")
const migration = repoSource("sql/erp_inventory_order_party_reference_20260713.sql")

assert.ok(
  reportMapper.includes("sales_amount - report_amounts.sales_cost") &&
    reportMapper.includes("received_quantity") &&
    reportMapper.includes("delivered_quantity") &&
    reportMapper.includes("sales_return_cost") &&
    reportMapper.includes("business_type in ('sales', 'outbound')"),
  "reporting should use actual receipts/deliveries, returns, and inventory-log cost"
)

assert.ok(
  salesReturnService.includes("resolveOriginalOutboundCost") &&
    salesReturnService.includes('"sales".equals(businessType)') &&
    salesReturnService.includes('"outbound".equals(businessType)') &&
    reportMapper.includes("sales_return_cost"),
  "sales returns should reverse the original outbound cost while report cost remains traceable to stock logs"
)

assert.ok(
  sales.includes('v-model="form.customerId"') &&
    sales.includes("listCustomer") &&
    sales.includes('prop="orderDate"') &&
    sales.includes("dateRange") &&
    sales.includes(":min=\"0.01\"") &&
    sales.includes("售价必须大于0"),
  "desktop sales should select a customer master, require a date, support useful filters, and reject zero prices"
)

assert.ok(
  purchase.includes('v-model="form.supplierId"') &&
    purchase.includes("listSupplier") &&
    purchase.includes('prop="orderDate"') &&
    purchase.includes(':disabled="!selectedItemType || !productSelection.length"') &&
    purchase.includes("请先选择供应商"),
  "purchase entry should select a supplier master before enabling product selection"
)

for (const source of [salesReturn, purchaseReturn]) {
  assert.ok(
    source.includes('prop="returnDate"') &&
      source.includes('disabled placeholder="选择原') &&
      source.includes("defaultReturnDate") &&
      (source.includes("Math.min(1") || source.includes("quantity: 0")),
    "returns should lock source-party snapshots, require/default the date, and avoid defaulting to the full return quantity"
  )
}

assert.ok(
  stock.includes("canAdjustCurrentScope") &&
    stock.includes("可见/管理库存范围为只读") &&
    stock.includes("openStockLog") &&
    stockLog.includes('prop="createBy"') &&
    stockLog.includes("日志范围已切回当前组织"),
  "cross-organization stock should be read-only and stock logs should explain their scope and operator"
)

assert.ok(
  stockCheck.includes("盘点范围") &&
    stockCheck.includes("按商品分类") &&
    stockCheck.includes("指定商品") &&
    stockCheck.includes("盘亏数量") &&
    stockCheck.includes("盘盈数量") &&
    !stockCheck.includes("卖出数量"),
  "stock counts should support partial ranges and use profit/loss terminology"
)

assert.ok(
  deliveryNotice.includes("销售门店") &&
    deliveryNotice.includes("发货仓库") &&
    deliveryNotice.includes("deliveryScopeDescription") &&
    workbench.includes("查看商品资料") &&
    workbench.includes("查询商品、编码和规格"),
  "organization labels and store workbench entry text should match the action users can actually perform"
)

assert.ok(
  migration.includes("customer_id") &&
    migration.includes("supplier_id") &&
    migration.includes("HAVING COUNT(*) = 1") &&
    migration.includes("ON DELETE SET NULL"),
  "order-party migration should retain historical names and only backfill unambiguous master references"
)

console.log("inventoryAuditRemediation tests passed")
