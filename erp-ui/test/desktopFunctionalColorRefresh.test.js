const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(rootDir, relativePath), "utf8")

const refinement = read("src/assets/styles/desktop-apple-refinement.scss")
const sidebarItem = read("src/layout/components/Sidebar/SidebarItem.vue")
const topBar = read("src/layout/components/TopBar/index.vue")
const userPage = read("src/views/system/user/index.vue")
const selectShop = read("src/views/select-shop/index.vue")

;[
  "--erp-interactive: #1473e6",
  "--erp-accent-sage: #178a58",
  "--erp-accent-amber: #c47400",
  "--erp-accent-rose: #cf3f5a",
  "--erp-accent-violet: #7456d8"
].forEach(contract => {
  assert.ok(refinement.includes(contract), `desktop functional palette should define ${contract}`)
})

assert.ok(
  sidebarItem.includes("getMenuTone") &&
    sidebarItem.includes("menu-tone--") &&
    topBar.includes("menu-tone--violet") &&
    refinement.includes("--menu-tone") &&
    refinement.includes("--toolbar-tone"),
  "desktop navigation should carry functional color from route category into menu and toolbar capsules"
)

assert.ok(
  refinement.includes("Pure-white desktop surface pass") &&
    refinement.includes(".top-nav-shell .top-toolbar") &&
    refinement.includes(".top-nav-shell .top-menu-row") &&
    refinement.includes("background: #ffffff !important") &&
    refinement.includes(".top-toolbar::after"),
  "desktop navigation should use true white chrome without a page-sized color wash"
)

assert.ok(
  refinement.includes("--erp-canvas: #ffffff") &&
    refinement.includes(".table-card .el-table__body tr:nth-child(even)") &&
    selectShop.includes("background: #ffffff"),
  "large desktop canvas, organization tree, table rows, and shop-selection surfaces should stay white instead of gray"
)

assert.ok(
  userPage.includes("user-identity-marker") &&
    userPage.includes("userIdentityInitial") &&
    userPage.includes("userIdentityTone") &&
    userPage.includes("user-row-action is-edit") &&
    userPage.includes("user-row-action is-delete") &&
    userPage.includes("user-row-action is-more") &&
    userPage.includes("color: #1473e6") &&
    userPage.includes("color: #cf3f5a") &&
    userPage.includes("color: #7456d8"),
  "the user table should expose deterministic identity markers and differentiated row actions"
)

assert.ok(
  selectShop.includes("context-tree-icon") &&
    selectShop.includes("getDeptTypeIconClass") &&
    selectShop.includes("selection-summary__icon") &&
    selectShop.includes("desktop-shop-card-enter") &&
    selectShop.includes("prefers-reduced-motion: reduce"),
  "organization selection should distinguish organization types and keep its motion accessible"
)

assert.ok(
  refinement.includes("transition: color 180ms") &&
    refinement.includes("prefers-reduced-motion: reduce"),
  "functional color interactions should remain brief and respect reduced-motion preferences"
)

console.log("desktop functional color refresh tests passed")
