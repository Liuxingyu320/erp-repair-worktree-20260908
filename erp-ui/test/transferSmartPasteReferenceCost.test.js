const assert = require("assert")
const fs = require("fs")
const path = require("path")

const repoRoot = path.resolve(__dirname, "../..")
const read = relativePath => fs.readFileSync(path.join(repoRoot, relativePath), "utf8")

const stockDomain = read("erp-modules/erp-inventory/src/main/java/com/erp/inventory/domain/InvStock.java")
const stockMapper = read("erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockMapper.xml")

assert.ok(
  stockDomain.includes("BigDecimal referenceCostPrice") &&
    stockDomain.includes("getReferenceCostPrice()") &&
    stockDomain.includes("setReferenceCostPrice(BigDecimal referenceCostPrice)"),
  "stock API rows should expose archive reference cost separately from moving weighted cost"
)
assert.ok(
  stockMapper.includes('property="referenceCostPrice" column="reference_cost_price"') &&
    stockMapper.includes("when 'oe' then oe.cost_price") &&
    stockMapper.includes("when 'gift' then gift.cost_price") &&
    stockMapper.includes("else p.cost_price") &&
    stockMapper.includes("end as reference_cost_price"),
  "stock queries should derive reference cost from the matching item archive"
)

console.log("transfer smart paste reference cost tests passed")
