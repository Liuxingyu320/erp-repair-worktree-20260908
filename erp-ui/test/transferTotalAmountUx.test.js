const assert = require("assert")
const fs = require("fs")
const path = require("path")

const transferSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/index.vue"),
  "utf8"
)
const recordsSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/inventory/transfer/records.vue"),
  "utf8"
)
const repoRoot = path.resolve(__dirname, "../..")
const migrationPaths = [
  path.resolve(repoRoot, "sql/erp_inventory_transfer_reference_amount_20260701.sql"),
  path.resolve(repoRoot, "docker/mysql/db/erp_inventory_transfer_reference_amount_20260701.sql")
]

assert.ok(
  transferSource.includes('label="小计"') &&
    transferSource.includes("lineAmount(scope.row)") &&
    transferSource.includes("formTotalAmount"),
  "desktop replenishment request form should show line subtotals and the reference total"
)

assert.ok(
  transferSource.includes("totalAmount: this.formTotalAmount") &&
    transferSource.includes("costPrice: item.costPrice") &&
    transferSource.includes("amount: this.lineAmount(item)"),
  "desktop replenishment request payload should submit reference cost and amount snapshots"
)

assert.ok(
  transferSource.includes('label="参考总价"') &&
    transferSource.includes("formatMoney(detail.totalAmount)") &&
    transferSource.includes('label="参考成本价"') &&
    transferSource.includes('label="小计"'),
  "desktop processing transfer detail should display reference total, reference cost, and line subtotal"
)

assert.ok(
  transferSource.includes('label="参考总价" prop="totalAmount" width="120" align="right"') &&
    transferSource.includes(":summary-method=\"getDetailSummary\"") &&
    transferSource.includes("detailTotalAmount(detail)"),
  "desktop processing transfer list and detail table should make the reference total immediately visible"
)

assert.ok(
  recordsSource.includes('label="参考总价"') &&
    recordsSource.includes("formatMoney(detail.totalAmount)") &&
    recordsSource.includes('label="参考成本价"') &&
    recordsSource.includes('label="小计"') &&
    recordsSource.includes("lineAmount(scope.row)"),
  "desktop transfer record detail should display reference total, reference cost, and line subtotal"
)

assert.ok(
  recordsSource.includes('label="参考总价" prop="totalAmount" width="120" align="right"') &&
    recordsSource.includes(":summary-method=\"getDetailSummary\"") &&
    recordsSource.includes("detailTotalAmount(detail)"),
  "desktop transfer record list and detail table should make the reference total immediately visible"
)

migrationPaths.forEach((migrationPath) => {
  const migrationSource = fs.readFileSync(migrationPath, "utf8")
  const addCostPriceIndex = migrationSource.indexOf("ADD COLUMN cost_price")
  const addAmountIndex = migrationSource.indexOf("ADD COLUMN amount")
  const updateDetailIndex = migrationSource.indexOf("SET d.cost_price")
  const updateOrderIndex = migrationSource.indexOf("SET o.total_quantity")

  assert.ok(addCostPriceIndex !== -1, `${migrationPath} should add cost_price`)
  assert.ok(addAmountIndex !== -1, `${migrationPath} should add amount`)
  assert.ok(updateDetailIndex !== -1, `${migrationPath} should backfill detail amounts`)
  assert.ok(updateOrderIndex !== -1, `${migrationPath} should backfill order totals`)
  assert.ok(
    addCostPriceIndex < updateDetailIndex && addAmountIndex < updateDetailIndex,
    `${migrationPath} should add detail amount columns before backfilling them`
  )
  assert.ok(
    updateDetailIndex < updateOrderIndex,
    `${migrationPath} should backfill detail amounts before recomputing order totals`
  )
})

console.log("transferTotalAmountUx tests passed")
