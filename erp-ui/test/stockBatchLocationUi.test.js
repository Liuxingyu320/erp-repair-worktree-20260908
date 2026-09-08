const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")

function source(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
}

const stockSource = source("src/views/inventory/stock/index.vue")
const stockLogSource = source("src/views/inventory/stock/log.vue")

;["batchNo", "expiryDate", "serialNo", "locationCode", "locationName"].forEach(field => {
  assert.ok(
    stockSource.includes(`queryParams.${field}`) || stockSource.includes(`adjustForm.${field}`) || stockSource.includes(`detailStock.${field}`),
    `stock page should expose ${field} in filters, adjustment, or detail display`
  )
  assert.ok(
    stockLogSource.includes(`queryParams.${field}`) || stockLogSource.includes(`scope.row.${field}`),
    `stock log page should expose ${field} in filters or table display`
  )
})

assert.ok(
  stockSource.includes("adjustForm.batchNo") &&
    stockSource.includes("adjustForm.expiryDate") &&
    stockSource.includes("adjustForm.serialNo") &&
    stockSource.includes("adjustForm.locationCode") &&
    stockSource.includes("adjustForm.locationName"),
  "stock adjustment dialog should submit batch, expiry, serial, and location metadata"
)

assert.ok(
  !stockSource.includes("scope.row.batchNo") &&
    !stockSource.includes("scope.row.expiryDate") &&
    !stockSource.includes("scope.row.serialNo") &&
    !stockSource.includes("stockLocation(scope.row)"),
  "stock list should hide batch, expiry, serial, and location metadata until a row is clicked"
)

assert.ok(
  stockSource.includes("@row-click=\"openStockDetail\"") &&
    stockSource.includes("detailStock.batchNo") &&
    stockSource.includes("detailStock.expiryDate") &&
    stockSource.includes("detailStock.serialNo") &&
    stockSource.includes("stockLocation(detailStock)"),
  "stock row click detail should display batch, expiry, serial, and location metadata"
)

assert.ok(
  stockLogSource.includes("stockLocation(scope.row)"),
  "stock log table should still render a composed location value"
)

console.log("stockBatchLocationUi tests passed")
