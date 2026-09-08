const assert = require("assert")
const fs = require("fs")
const path = require("path")

const pricingPath = path.resolve(__dirname, "../src/views/mobile/feature/mobileLineItemPricing.js")
assert.ok(fs.existsSync(pricingPath), "mobile material selection should share a feature-aware price resolver")

const { resolveMobileLineItemDefaultPrice } = require(pricingPath)

const product = {
  salesPrice: 128,
  salePrice500g: 118,
  purchasePrice: 72,
  costPrice: 68
}

assert.strictEqual(
  resolveMobileLineItemDefaultPrice(product, "product", "purchase"),
  72,
  "purchase forms must prefer the purchase price instead of silently submitting the sales price"
)

assert.strictEqual(
  resolveMobileLineItemDefaultPrice(product, "product", "sales"),
  118,
  "sales forms should prefer the catalog sales price"
)

assert.strictEqual(
  resolveMobileLineItemDefaultPrice({ purchasePrice: 45, guidePrice1: 88 }, "gift", "purchase"),
  45,
  "gift purchases should prefer a purchase price when the catalog exposes one"
)

assert.strictEqual(
  resolveMobileLineItemDefaultPrice({ costPrice: 33, salesPrice: 66 }, "product", "transfer"),
  33,
  "transfer forms should use reference cost rather than a customer-facing sales price"
)

const featurePageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/index.vue"),
  "utf8"
)
const lineItemsSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/mobile/feature/components/MobileLineItemsEditor.vue"),
  "utf8"
)

assert.ok(
  featurePageSource.includes("featureKey: this.featureKey") &&
    lineItemsSource.includes("resolveMobileLineItemDefaultPrice"),
  "the existing line-item editor should resolve prices using its current business feature"
)

console.log("mobileLineItemPricing tests passed")
