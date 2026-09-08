const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const entrySource = fs.readFileSync(path.join(rootDir, "src/layout/index.vue"), "utf8")
const appMainSource = fs.readFileSync(path.join(rootDir, "src/layout/components/AppMain.vue"), "utf8")
const visualSystemPath = path.join(rootDir, "src/assets/styles/desktop-inventory.scss")

assert.ok(fs.existsSync(visualSystemPath), "the inventory desktop visual stylesheet should exist")

const visualSystemSource = fs.readFileSync(visualSystemPath, "utf8")

assert.ok(
  entrySource.includes("@/assets/styles/desktop-inventory.scss"),
  "the lazy desktop layout should load the inventory visual layer"
)

assert.ok(
  appMainSource.includes("'inventory-module': isInventoryRoute") &&
    /\^\\\/inventory\(\?:\\\/\|\$\)/.test(appMainSource),
  "AppMain should scope the inventory visual layer by route"
)

assert.ok(
  visualSystemSource.includes("@media screen and (min-width: 992px)") &&
    visualSystemSource.includes(".app-wrapper:not(.mobile) .app-main.inventory-module"),
  "inventory styling should remain inside the desktop inventory shell"
)

;[
  ".search-card",
  ".query-form",
  ".metric-card",
  ".transfer-metric",
  ".el-table",
  ".category-card",
  ".customer-card",
  ".flow-step"
].forEach(selector => {
  assert.ok(visualSystemSource.includes(selector), `inventory visual layer should style ${selector}`)
})

;[
  "src/views/inventory/sales/index.vue",
  "src/views/inventory/salesReturn/index.vue",
  "src/views/inventory/deliveryNotice/index.vue",
  "src/views/inventory/report/index.vue"
].forEach(file => {
  const source = fs.readFileSync(path.join(rootDir, file), "utf8")
  assert.ok(source.includes("<inventory-page-hero"), `${file} should expose a business page hero`)
})

assert.ok(
  /@media \(prefers-reduced-motion:\s*reduce\)/.test(visualSystemSource),
  "inventory visual transitions should respect reduced-motion preferences"
)

console.log("inventoryVisualSystem tests passed")
