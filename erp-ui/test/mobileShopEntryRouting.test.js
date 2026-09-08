const assert = require("assert")
const fs = require("fs")
const path = require("path")

const {
  resolveNoBusinessContextRedirect,
  resolveShopEntryRedirect
} = require("../src/views/select-shop/shopEntryRouting")

assert.strictEqual(
  resolveShopEntryRedirect({
    isMobileViewport: true,
    deptType: "STORE",
    requestedRedirect: "/mobile/profile"
  }),
  "/mobile/store",
  "enter workbench should always open the selected store workbench on mobile"
)

assert.strictEqual(
  resolveShopEntryRedirect({
    isMobileViewport: true,
    deptType: "STORE",
    permissions: ["hr:onboarding:workbench"],
    requestedRedirect: "/mobile/store"
  }),
  "/mobile/hr",
  "mobile shop selection should enter the HR homepage when account permissions select it"
)

assert.strictEqual(
  resolveShopEntryRedirect({
    isMobileViewport: true,
    deptType: "WAREHOUSE",
    requestedRedirect: "/mobile/sales"
  }),
  "/mobile/warehouse",
  "enter workbench should always open the selected warehouse workbench on mobile"
)

assert.strictEqual(
  resolveShopEntryRedirect({
    isMobileViewport: false,
    deptType: "STORE",
    requestedRedirect: "/user/profile"
  }),
  "/user/profile",
  "desktop selection should retain an internal requested redirect"
)

assert.strictEqual(
  resolveShopEntryRedirect({
    isMobileViewport: false,
    deptType: "STORE",
    requestedRedirect: "/mobile/store?from=stale-session"
  }),
  "/",
  "desktop selection should discard a stale mobile redirect"
)

;["https://evil.example", "//evil.example", "javascript:alert(1)", ""].forEach(requestedRedirect => {
  assert.strictEqual(
    resolveShopEntryRedirect({ isMobileViewport: false, deptType: "STORE", requestedRedirect }),
    "/",
    `desktop selection should reject unsafe redirect ${requestedRedirect}`
  )
})

assert.strictEqual(
  resolveNoBusinessContextRedirect({
    isMobileViewport: false,
    businessDeptCount: 0,
    requestedRedirect: "/system/user",
    requestedRedirectRequiresContext: false,
    permissions: ["system:user:list"]
  }),
  "/system/user",
  "a desktop administrator deep link should bypass an empty organization selector"
)
assert.strictEqual(
  resolveNoBusinessContextRedirect({
    isMobileViewport: false,
    businessDeptCount: 0,
    requestedRedirect: "/",
    permissions: ["hr:employee:list"]
  }),
  "/hr/employee",
  "an HR-only account should receive an authorized context-free default"
)
assert.strictEqual(
  resolveNoBusinessContextRedirect({
    isMobileViewport: false,
    businessDeptCount: 0,
    requestedRedirect: "/inventory/stock",
    requestedRedirectRequiresContext: true,
    permissions: ["system:user:list"]
  }),
  "/system/user",
  "an inventory deep link without organizations should fail over to an authorized admin page"
)
assert.strictEqual(
  resolveNoBusinessContextRedirect({
    isMobileViewport: true,
    businessDeptCount: 0,
    permissions: ["hr:employee:list"]
  }),
  "",
  "mobile business entry should keep its explicit no-organization state"
)
assert.strictEqual(
  resolveNoBusinessContextRedirect({
    isMobileViewport: false,
    businessDeptCount: 1,
    permissions: ["system:user:list"]
  }),
  "",
  "an account with a business organization should retain the selector"
)

const selectShopSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/select-shop/index.vue"),
  "utf8"
)

assert.match(
  selectShopSource,
  /import\s+\{\s*requiresInventoryContext\s*\}\s+from\s+["']@\/utils\/desktopContextPolicy["']/,
  "shop selection should import the inventory-context policy used after the tree loads"
)
assert.match(
  selectShopSource,
  /const\s+\{\s*resolveNoBusinessContextRedirect\s*,\s*resolveShopEntryRedirect\s*\}\s*=\s*require\(["']\.\/shopEntryRouting["']\)/,
  "shop selection should import both routing helpers used after the tree loads"
)

assert.ok(
  selectShopSource.includes("resolveShopEntryRedirect") &&
    selectShopSource.includes("resolveNoBusinessContextRedirect") &&
    selectShopSource.includes("当前账号无需选择业务组织") &&
    selectShopSource.includes("permissions: this.userPermissions") &&
    !selectShopSource.includes("isMobileHomeRedirect") &&
    selectShopSource.includes("进入工作台"),
  "shop selection should delegate the mobile CTA to the deterministic entry helper"
)

console.log("mobileShopEntryRouting tests passed")
