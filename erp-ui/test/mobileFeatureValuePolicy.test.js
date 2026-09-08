const assert = require("assert")

const {
  canViewCost,
  firstNonZeroValue,
  firstText,
  firstValue,
  formatDateText,
  formatDateTimeText,
  formatMoneyText,
  formatOptionalQuantity,
  formatQuantity,
  formatSignedQuantity,
  formatTimeText,
  hasValue,
  isStoreContext,
  joinDetail,
  normalizeContextType,
  resolveProductReferenceCost,
  resolveStockReferenceCost,
  safeText,
  stockLocationText,
  sumValues,
  toNumber,
  unitText
} = require("../src/views/mobile/feature/mobileFeatureValuePolicy")

assert.strictEqual(hasValue(0), true)
assert.strictEqual(hasValue(false), true)
assert.strictEqual(hasValue("  "), false)
assert.strictEqual(safeText("  龙井  ", ""), "龙井")
assert.strictEqual(safeText(null, "未填写"), "未填写")

assert.strictEqual(firstValue({ primary: 0, fallback: 7 }, ["primary", "fallback"]), 0)
assert.strictEqual(firstText({ primary: "  A-01  " }, ["primary"]), "A-01")
assert.strictEqual(firstNonZeroValue({ first: 0, second: "42.5" }, ["first", "second"]), "42.5")
assert.strictEqual(firstNonZeroValue({ first: 0, second: "0" }, ["first", "second"]), 0)

assert.strictEqual(joinDetail(["湖滨店", "", null, " 3 件 "]), "湖滨店 · 3 件")
assert.strictEqual(joinDetail(["", null]), "-")
assert.strictEqual(formatMoneyText("12.5"), "¥12.50")
assert.strictEqual(formatMoneyText("待核价"), "待核价")
assert.strictEqual(formatMoneyText(null), "")
assert.strictEqual(formatQuantity("12.50"), "12.5")
assert.strictEqual(formatQuantity("invalid"), "0")
assert.strictEqual(formatSignedQuantity(3), "+3")
assert.strictEqual(formatSignedQuantity(-1.5), "-1.5")
assert.strictEqual(formatOptionalQuantity(0, "盒"), "0 盒")
assert.strictEqual(formatOptionalQuantity(null, "盒"), "")
assert.strictEqual(unitText("盒"), " 盒")

assert.strictEqual(formatDateText("2026-07-31 09:08:07"), "2026-07-31")
assert.strictEqual(formatDateTimeText("2026-07-31 09:08:07"), "2026-07-31 09:08")
assert.strictEqual(formatDateTimeText("2026-07-31"), "2026-07-31")
assert.strictEqual(formatTimeText("09:08:07"), "09:08")

assert.strictEqual(sumValues({ a: "1.2", b: 2, ignored: "x" }, ["a", "b", "ignored"]), 3.2)
assert.strictEqual(sumValues({}, ["missing"]), "")
assert.strictEqual(toNumber("2.5"), 2.5)
assert.strictEqual(toNumber("x"), null)

assert.strictEqual(normalizeContextType({ selectedDeptType: " store " }), "STORE")
assert.strictEqual(isStoreContext({ selectedDeptType: "store" }), true)
assert.strictEqual(canViewCost({ permissions: ["inv:cost:view"] }), true)
assert.strictEqual(canViewCost({ permissions: ["*:*:*"] }), true)
assert.strictEqual(canViewCost({ permissions: ["inv:stock:list"] }), false)

assert.strictEqual(resolveProductReferenceCost({ costPrice: 0, referenceCostPrice: 36 }), 36)
assert.strictEqual(resolveProductReferenceCost({ costPrice: 0, referenceCostPrice: 0 }), 0)
assert.strictEqual(resolveStockReferenceCost({ costPrice: 0, referenceCostPrice: 36 }), 0)
assert.strictEqual(stockLocationText({ locationCode: "A-01", locationName: "一号货架" }), "A-01 / 一号货架")

console.log("mobile feature value policy tests passed")
