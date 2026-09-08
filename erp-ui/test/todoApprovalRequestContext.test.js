const assert = require("assert")
const path = require("path")

const {
  applyInventoryDeptRequestContext
} = require(path.resolve(__dirname, "../src/utils/requestInventoryContext.js"))

const explicit = {
  url: "/inventory/transfer/701",
  headers: {},
  inventoryDeptId: "88"
}
applyInventoryDeptRequestContext(explicit, "10")
assert.strictEqual(explicit.headers["Dept-NumId"], "88",
  "a todo-scoped inventory request must prefer its explicit organization")
assert.strictEqual(Object.prototype.hasOwnProperty.call(explicit, "inventoryDeptId"), false,
  "the internal organization override must be removed before transport")

const fallback = { url: "/inventory/transfer/701", headers: {} }
applyInventoryDeptRequestContext(fallback, 10)
assert.strictEqual(fallback.headers["Dept-NumId"], "10",
  "ordinary requests must retain the selected inventory organization")

const legacyNonInventory = { url: "/oa/purchase/list", headers: {} }
applyInventoryDeptRequestContext(legacyNonInventory, 10)
assert.strictEqual(legacyNonInventory.headers["Dept-NumId"], "10",
  "the established fallback header behavior must remain backward compatible")

assert.throws(() => applyInventoryDeptRequestContext({
  url: "/oa/purchase/list",
  headers: {},
  inventoryDeptId: 88
}, 10), /库存请求/, "an explicit inventory override must not leak into another service")

assert.throws(() => applyInventoryDeptRequestContext({
  url: "/inventory/transfer/701",
  headers: {},
  inventoryDeptId: "not-a-dept"
}, 10), /正整数/, "an invalid explicit organization must fail closed")

console.log("todo approval request context tests passed")
