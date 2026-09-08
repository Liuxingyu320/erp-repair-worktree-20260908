const assert = require("assert")
const fs = require("fs")
const path = require("path")

const uiRoot = path.resolve(__dirname, "..")
const readUi = file => fs.readFileSync(path.resolve(uiRoot, file), "utf8")

const productionEntrypoints = {
  "src/views/select-shop/index.vue": readUi("src/views/select-shop/index.vue"),
  "src/views/mobile/mobileRouteDefinitions.js": readUi("src/views/mobile/mobileRouteDefinitions.js"),
  "src/views/mobile/feature/index.vue": readUi("src/views/mobile/feature/index.vue"),
  "src/views/mobile/inventory/index.vue": readUi("src/views/mobile/inventory/index.vue"),
  "src/views/mobile/feature/featureMapper.js": readUi("src/views/mobile/feature/featureMapper.js"),
  "src/permission.js": readUi("src/permission.js")
}

const forbiddenTokens = [
  "previewDeptTree",
  "云岫茶室",
  "青炉茶社",
  "SO-MOBILE",
  "PO-MOBILE",
  "MOBILE-READY",
  "演示数据",
  "本地示例数据",
  "to.query.preview === '1'",
  "to.query.demo === '1'"
]

Object.entries(productionEntrypoints).forEach(([file, source]) => {
  forbiddenTokens.forEach(token => {
    assert.ok(
      !source.includes(token),
      `${file} should not ship production-visible test data token: ${token}`
    )
  })
})

assert.ok(
  !productionEntrypoints["src/views/mobile/mobileRouteDefinitions.js"].includes("seedItems:"),
  "mobile route definitions should not carry preview seed item arrays"
)

assert.ok(
  !productionEntrypoints["src/views/mobile/feature/index.vue"].includes("this.feature.seedItems"),
  "mobile feature pages should not render route seed items"
)

assert.ok(
  !productionEntrypoints["src/permission.js"].includes("isAllowedLocalPreview"),
  "route guard should not contain local preview bypasses"
)

console.log("mobile production data isolation tests passed")
