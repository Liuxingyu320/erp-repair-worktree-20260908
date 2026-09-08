const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(rootDir, relativePath), "utf8")

const refinement = read("src/assets/styles/desktop-apple-refinement.scss")
const layout = read("src/layout/index.vue")
const home = read("src/views/index.vue")
const selectShop = read("src/views/select-shop/index.vue")
const systemPageHeader = read("src/components/SystemPageHeader/index.vue")
const menuItem = read("src/layout/components/Sidebar/Item.vue")

assert.ok(
  layout.includes("desktop-apple-refinement.scss"),
  "the desktop layout should load the selected Apple-like refinement after module styles"
)

;[
  "--erp-canvas: #ffffff",
  "--erp-surface: #ffffff",
  "--erp-text: #25282c",
  "--erp-text-secondary: #555b61",
  "--erp-border: #dce7ef",
  "--erp-primary: #34383d",
  "--erp-interactive: #1473e6",
  "--erp-interactive-soft: #eef6ff"
].forEach(contract => {
  assert.ok(refinement.includes(contract), `desktop refinement should define ${contract}`)
})

assert.ok(
  refinement.includes("@media screen and (min-width: 992px)") &&
    refinement.includes("#app .app-wrapper:not(.mobile)"),
  "the refinement should remain isolated from the mobile application"
)

assert.ok(
  refinement.includes('url("~@/assets/images/desktop-login-tea-room-bg.jpg")') &&
    refinement.includes(".desktop-home-page .home-hero") &&
    refinement.includes(".desktop-home-page .quick-grid"),
  "the selected dashboard should retain its approved interior image and existing content regions"
)

assert.ok(
  home.includes("class=\"content-card home-hero\"") &&
    home.includes("class=\"summary-grid\"") &&
    home.includes("class=\"home-columns\""),
  "the existing dashboard information architecture should remain intact"
)

assert.ok(
    selectShop.includes("Desktop-only refinement aligned with the selected BossERP dashboard") &&
    selectShop.includes("background: #ffffff") &&
    selectShop.includes("box-shadow: inset 3px 0 0 #1473e6"),
  "desktop organization selection should use a bright canvas with clear functional accents"
)

assert.ok(
  refinement.includes(".el-button--default:not(.is-disabled)") &&
    refinement.includes("border-color: #b9b9bf") &&
    refinement.includes(".el-button.is-disabled") &&
    refinement.includes(".el-button--danger.is-plain"),
  "desktop buttons should keep readable borders and distinguish default, disabled, and semantic actions"
)

assert.ok(
  refinement.includes(".summary-icon.teal") &&
    refinement.includes(".summary-icon.blue") &&
    refinement.includes(".summary-icon.amber") &&
    refinement.includes(".quick-link--rose .quick-icon"),
  "dashboard decoration should stay limited to semantic icon and affordance accents"
)

assert.ok(
  systemPageHeader.includes("resolvedTone") &&
    systemPageHeader.includes("system-page-heading--violet") &&
    systemPageHeader.includes("system-page-heading--sage") &&
    systemPageHeader.includes("system-page-heading--amber") &&
    systemPageHeader.includes("system-page-heading--rose"),
  "system page headings should derive a restrained semantic tone instead of rendering every module icon in one color"
)

assert.ok(
  menuItem.includes("resolveIconTone") &&
    menuItem.includes("menu-icon-tone--") &&
    refinement.includes(".menu-icon-tone--violet") &&
    refinement.includes(".menu-icon-tone--sage") &&
    refinement.includes(".menu-icon-tone--amber") &&
    refinement.includes(".menu-icon-tone--rose"),
  "desktop navigation icons should expose quiet category colors while preserving one visual system"
)

assert.ok(
  refinement.includes(".tree-node .el-icon-folder") &&
    refinement.includes(".tree-node .el-icon-box") &&
    refinement.includes(".el-tag--success") &&
    refinement.includes(".el-tag--warning") &&
    refinement.includes(".el-tag--danger") &&
    refinement.includes(".el-button--text .el-icon-delete"),
  "tree nodes, statuses, and row actions should retain semantic differentiation"
)

assert.ok(
  !refinement.includes("#0b6b53") &&
    !refinement.includes("#4f46e5") &&
    !refinement.includes("#7c3aed"),
  "the desktop refinement should not reintroduce the former saturated brand accents"
)

console.log("desktop Apple refinement tests passed")
