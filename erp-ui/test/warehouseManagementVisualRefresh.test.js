const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const read = file => fs.readFileSync(path.join(rootDir, file), "utf8")

const heroPath = path.join(rootDir, "src/components/InventoryPageHero/index.vue")
assert.ok(fs.existsSync(heroPath), "the shared warehouse page hero should exist")

const heroSource = fs.readFileSync(heroPath, "utf8")
const pluginSource = read("src/plugins/index.js")

assert.ok(
  pluginSource.includes("Vue.component('InventoryPageHero'") &&
    pluginSource.includes('webpackChunkName: "chunk-inventory-ui"'),
  "the warehouse page hero should be loaded as a shared async component"
)

;[
  "getSelectedDeptContext",
  'class="inventory-page-hero__meta"',
  'class="inventory-page-hero__feature-list"',
  ".inventory-page-hero--blue",
  ".inventory-page-hero--teal",
  ".inventory-page-hero--amber",
  ".inventory-page-hero--rose",
  ".inventory-page-hero--slate",
  "@media (max-width: 767px)"
].forEach(contract => {
  assert.ok(heroSource.includes(contract), `warehouse hero should include ${contract}`)
})

const warehousePages = [
  "src/views/inventory/product/index.vue",
  "src/views/inventory/category/index.vue",
  "src/views/inventory/purchase/index.vue",
  "src/views/inventory/stock/index.vue",
  "src/views/inventory/transfer/index.vue",
  "src/views/inventory/supplier/index.vue",
  "src/views/inventory/purchaseReturn/index.vue",
  "src/views/inventory/transfer/records.vue",
  "src/views/inventory/oe/category/index.vue",
  "src/views/inventory/oe/index.vue",
  "src/views/inventory/gift/category/index.vue",
  "src/views/inventory/gift/index.vue"
]

warehousePages.forEach(file => {
  assert.ok(
    read(file).includes("<inventory-page-hero"),
    `${file} should use the shared warehouse page hero`
  )
})

console.log("warehouse management visual refresh tests passed")
