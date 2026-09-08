const assert = require("assert")
const fs = require("fs")
const path = require("path")

const commonSource = fs.readFileSync(
  path.resolve(__dirname, "../src/assets/styles/common.scss"),
  "utf8"
)
const treePanelSource = fs.readFileSync(
  path.resolve(__dirname, "../src/components/TreePanel/index.vue"),
  "utf8"
)
const userPageSource = fs.readFileSync(
  path.resolve(__dirname, "../src/views/system/user/index.vue"),
  "utf8"
)

const manageWrap = commonSource.match(/\.tree-sidebar-manage-wrap\s*\{([\s\S]*?)\n\}/)
assert.ok(manageWrap, "tree sidebar manage wrapper styles should exist")
assert.ok(
  manageWrap[1].includes("height: calc(100vh - 130px);") &&
    manageWrap[1].includes("min-height: 0;"),
  "tree sidebar pages should use a fixed viewport height instead of being stretched by long trees"
)

const sidebarContent = commonSource.match(/\.tree-sidebar-content\s*\{([\s\S]*?)\.content-inner\s*\{([\s\S]*?)\n  \}/)
assert.ok(sidebarContent, "tree sidebar content styles should exist")
assert.ok(
  sidebarContent[1].includes("height: 100%;") &&
    sidebarContent[1].includes("min-height: 0;") &&
    sidebarContent[2].includes("min-height: 0;") &&
    sidebarContent[2].includes("overflow-y: auto;"),
  "right side content should stay inside the viewport and scroll internally"
)

const treeSidebar = treePanelSource.match(/\.tree-sidebar\s*\{([\s\S]*?)\n\}/)
const treeWrap = treePanelSource.match(/\.tree-wrap\s*\{([\s\S]*?)\n\s+\.tree-sidebar\.resizing/)
assert.ok(treeSidebar && treeWrap, "tree panel layout styles should exist")
assert.ok(
  treeSidebar[1].includes("height: 100%;") &&
    treeSidebar[1].includes("min-height: 0;") &&
    treeWrap[1].includes("min-height: 0;") &&
    treeWrap[1].includes("overflow-y: auto;"),
  "left tree should keep its own scrollbar instead of stretching the whole page"
)

assert.ok(
  /\.content-inner\s*>\s*\*\s*\{[^}]*flex-shrink:\s*0;/.test(userPageSource),
  "user page cards should keep their natural height so the right content can overflow and scroll"
)

console.log("treeSidebarFixedViewportLayout tests passed")
