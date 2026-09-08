const assert = require("assert")
const fs = require("fs")
const path = require("path")

const source = fs.readFileSync(path.resolve(__dirname, "../src/views/system/dept/index.vue"), "utf8")
const selectShopSource = fs.readFileSync(path.resolve(__dirname, "../src/views/select-shop/index.vue"), "utf8")

assert.ok(
  source.includes("deptTypeLabel(scope.row.deptType, scope.row)"),
  "department type labels should use the current hierarchy row"
)
assert.ok(
  source.includes('{ label: "公司 / 组织", value: "COMPANY"') &&
    source.includes('return this.isNestedOrganization(row) ? "组织" : "公司"'),
  "the compatibility COMPANY type should distinguish company anchors from nested organizations"
)
assert.ok(
  source.includes("ancestorIds.length > 1") &&
    source.includes("下级区域、运营单元和职能部门显示为组织") &&
    source.includes("新增时选此项可继续建立下一级组织") &&
    source.includes("新增下级") &&
    source.includes("的下级组织`"),
  "nested organization labeling should be documented in the editor"
)
assert.ok(
  selectShopSource.includes("this.getDeptTypeLabel(data.deptType, data, node)") &&
    selectShopSource.includes('return this.isNestedOrganization(data, node) ? "组织" : "公司"'),
  "organization selection should use the same hierarchy-aware company label"
)
assert.ok(
  selectShopSource.includes('return parentType !== "GROUP"') &&
    selectShopSource.includes('return "is-org"') &&
    selectShopSource.includes('GROUP: "集团"'),
  "nested company-compatible nodes should render as organizations in the selection tree"
)

console.log("department organization type label tests passed")
