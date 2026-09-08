const assert = require("assert")
const fs = require("fs")
const path = require("path")

const root = path.resolve(__dirname, "..")
const treePanelSource = fs.readFileSync(path.join(root, "src/components/TreePanel/index.vue"), "utf8")
const treeSelectSource = fs.readFileSync(
  path.resolve(root, "../erp-modules/erp-system/src/main/java/com/erp/system/domain/vo/TreeSelect.java"),
  "utf8"
)

assert(
  treeSelectSource.includes("private String deptType") &&
    treeSelectSource.includes("this.deptType = dept.getDeptType()") &&
    treeSelectSource.includes("getDeptType()"),
  "department tree API should expose deptType to the frontend"
)

assert(
  treePanelSource.includes("nodeIconClass(data)") &&
    treePanelSource.includes('deptType === "GROUP" || deptType === "COMPANY"') &&
    treePanelSource.includes('return "el-icon-folder"'),
  "tree panel should render group/company nodes as folder icons even when they have no children"
)

assert(
  treePanelSource.includes('deptType === "WAREHOUSE"') &&
    treePanelSource.includes('return "el-icon-box"'),
  "tree panel should render warehouse nodes with a warehouse-specific icon"
)

console.log("treePanelDeptTypeIcon.test.js passed")
