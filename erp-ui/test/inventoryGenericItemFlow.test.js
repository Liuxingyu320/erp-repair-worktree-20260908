const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")

const itemSelect = read("src/views/inventory/components/InventoryItemSelect.vue")
const purchase = read("src/views/inventory/purchase/index.vue")
const sales = read("src/views/inventory/sales/index.vue")
const transfer = read("src/views/inventory/transfer/index.vue")
const stock = read("src/views/inventory/stock/index.vue")

assert.ok(itemSelect.includes('from "@/api/inventory/product"'), "generic selector should load products")
assert.ok(itemSelect.includes('from "@/api/inventory/oe"'), "generic selector should load OE items")
assert.ok(itemSelect.includes('from "@/api/inventory/gift"'), "generic selector should load gift boxes")
assert.ok(itemSelect.includes("normalizeItem"), "generic selector should normalize catalog fields")

assert.ok(purchase.includes('["product", "oe", "gift"]'), "purchase should allow product, OE and gift")
assert.ok(purchase.includes("itemType"), "purchase details should submit item type")
assert.ok(purchase.includes("itemId"), "purchase details should submit item id")

assert.ok(sales.includes('["product", "gift"]'), "sales should allow product and gift only")
assert.ok(!sales.includes('["product", "oe", "gift"]'), "sales must not expose OE")
assert.ok(sales.includes("itemType") && sales.includes("itemId"), "sales should submit generic item identity")

assert.ok(transfer.includes('["product", "gift"]'), "transfer should allow product and gift only")
assert.ok(transfer.includes("itemType") && transfer.includes("itemId"), "transfer should submit generic item identity")

assert.ok(stock.includes('["product", "oe", "gift"]'), "stock adjustment should support all inventory item types")
assert.ok(stock.includes("itemType") && stock.includes("itemId"), "stock adjustment should use generic item identity")

console.log("inventoryGenericItemFlow tests passed")
