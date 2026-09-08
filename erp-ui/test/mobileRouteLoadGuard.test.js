const assert = require("assert")

const {
  createMobileRouteLoadGuard
} = require("../src/views/mobile/feature/mobileRouteLoadGuard")

const guard = createMobileRouteLoadGuard()
const salesLoad = guard.begin("sales")
const stockLoad = guard.begin("stock")

assert.strictEqual(
  guard.isCurrent(salesLoad, "stock"),
  false,
  "a response started for the previous route must not update the current feature"
)
assert.strictEqual(
  guard.isCurrent(stockLoad, "stock"),
  true,
  "the newest response for the active feature should remain valid"
)

guard.invalidate()

assert.strictEqual(
  guard.isCurrent(stockLoad, "stock"),
  false,
  "route cleanup should invalidate every in-flight response"
)
