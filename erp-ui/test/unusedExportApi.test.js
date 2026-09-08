const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")

function source(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
}

const unusedExportApis = [
  ["src/api/oa/purchase.js", ["exportMyPurchases", "exportTodoPurchases", "exportDonePurchases"]],
  ["src/api/inventory/customer.js", ["exportCustomer"]],
  ["src/api/inventory/deliveryNotice.js", ["exportDeliveryNotice"]],
  ["src/api/inventory/product.js", ["exportProduct"]],
  ["src/api/inventory/purchase.js", ["exportPurchase"]],
  ["src/api/inventory/purchaseReturn.js", ["exportPurchaseReturn"]],
  ["src/api/inventory/sales.js", ["exportSales"]],
  ["src/api/inventory/salesReturn.js", ["exportSalesReturn"]],
  ["src/api/inventory/stock.js", ["exportStock", "exportStockLog"]],
  ["src/api/inventory/stockCheck.js", ["exportStockCheck"]],
  ["src/api/inventory/supplier.js", ["exportSupplier"]],
  ["src/api/inventory/transfer.js", ["exportTransfer", "exportTransferRecords"]]
]

for (const [filePath, functionNames] of unusedExportApis) {
  const fileSource = source(filePath)
  for (const functionName of functionNames) {
    assert.ok(
      !fileSource.includes(`export function ${functionName}`),
      `${filePath} should not keep unused ${functionName}; export downloads use the shared this.download helper`
    )
  }
}

console.log("unusedExportApi tests passed")
