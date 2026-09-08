const assert = require("assert")
const fs = require("fs")
const path = require("path")

const rootDir = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(rootDir, relativePath), "utf8")

const entrySource = read("src/layout/index.vue")
const mainSource = read("src/main.js")
const headerSource = read("src/components/SystemPageHeader/index.vue")
const visualSource = read("src/assets/styles/system-management.scss")

assert.ok(
  entrySource.includes("@/assets/styles/system-management.scss") &&
    mainSource.includes("Vue.component('SystemPageHeader'") &&
    mainSource.includes("@/components/SystemPageHeader"),
  "the lazy desktop layout and global component registry should expose the system management visual layer and page header"
)

assert.ok(
  headerSource.includes('<section class="system-page-heading"') &&
    headerSource.includes(":aria-labelledby=\"headingId\"") &&
    headerSource.includes("<h1 :id=\"headingId\">") &&
    headerSource.includes("@media (max-width: 768px)"),
  "the shared page header should provide semantic, responsive page hierarchy"
)

;[
  ".system-management-page",
  ".search-card",
  "[class*=\"-toolbar-card\"]",
  ".table-card",
  ".tree-sidebar-manage-wrap.system-management-page",
  "@media screen and (min-width: 992px) and (max-width: 1366px)",
  "@media (prefers-reduced-motion: reduce)"
].forEach(contract => {
  assert.ok(visualSource.includes(contract), `system management visual styles should include ${contract}`)
})

;[
  "src/views/system/user/index.vue",
  "src/views/system/role/index.vue",
  "src/views/system/menu/index.vue",
  "src/views/system/dept/index.vue",
  "src/views/system/post/index.vue",
  "src/views/system/dict/index.vue",
  "src/views/system/config/index.vue",
  "src/views/system/notice/index.vue",
  "src/views/system/operlog/index.vue",
  "src/views/system/logininfor/index.vue",
  "src/views/system/shop/index.vue",
  "src/views/system/salary/index.vue",
  "src/views/system/dict/data.vue",
  "src/views/system/role/authUser.vue",
  "src/views/system/user/authRole.vue"
].forEach(relativePath => {
  const source = read(relativePath)
  assert.ok(
    source.includes("system-management-page") && source.includes("<system-page-header"),
    `${relativePath} should use the shared system management page hierarchy`
  )
})

console.log("system management visual refresh tests passed")
