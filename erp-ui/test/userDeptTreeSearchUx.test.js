const assert = require("assert")
const fs = require("fs")
const path = require("path")

const readUi = file => fs.readFileSync(path.resolve(__dirname, "../src", file), "utf8")

const userSource = readUi("views/system/user/index.vue")
const treePanelSource = readUi("components/TreePanel/index.vue")

assert.ok(
  userSource.includes(':defaultExpandAll="false"') &&
    userSource.includes(':default-expanded-keys="deptDefaultExpandedKeys"'),
  "user management tree should not expand every store by default and should provide curated expanded keys"
)

assert.ok(
  userSource.includes(':filter-method="filterDeptNode"') &&
    userSource.includes("decorateDeptTree") &&
    userSource.includes("deptSearchText") &&
    userSource.includes("getDefaultExpandedDeptKeys"),
  "user management tree should support department path search and default expansion calculation"
)

assert.ok(
  treePanelSource.includes("searchHitCount") &&
    treePanelSource.includes("tree-search-summary") &&
    treePanelSource.includes("expandSearchMatchedNodes") &&
    treePanelSource.includes("applyDefaultExpandedKeys"),
  "tree panel should show search match count and expand matched paths"
)

assert.ok(
  treePanelSource.includes("defaultExpandedKeys") &&
    treePanelSource.includes("treeData") &&
    treePanelSource.includes("updateSearchHitCount"),
  "tree panel should react when async tree data or default expanded keys change"
)

console.log("userDeptTreeSearchUx tests passed")
