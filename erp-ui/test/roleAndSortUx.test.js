const assert = require("assert")
const fs = require("fs")
const path = require("path")

const read = relativePath => fs.readFileSync(path.resolve(__dirname, relativePath), "utf8")
const roleSource = read("../src/views/system/role/index.vue")
const wizardSource = read("../src/views/system/role/components/RoleWizard.vue")
const deptSource = read("../src/views/system/dept/index.vue")
const menuSource = read("../src/views/system/menu/index.vue")
const guardSource = read("../src/mixins/pendingSortGuard.js")
const deptApiSource = read("../src/api/system/dept.js")
const menuApiSource = read("../src/api/system/menu.js")

assert.ok(
  roleSource.includes("<role-wizard") &&
    roleSource.includes("handleRoleCreated") &&
    roleSource.includes("去添加成员") &&
    wizardSource.includes("新增角色向导") &&
    wizardSource.includes("本部门及以下") &&
    wizardSource.includes('dataScope: "4"'),
  "role creation should use a four-step wizard with an explicit safe data-scope default and member follow-up"
)

assert.ok(
  roleSource.includes("runSystemListRequest") &&
    roleSource.includes("加载失败") &&
    roleSource.includes("silentError: true"),
  "role list failures must release the spinner and offer retry"
)

assert.ok(
  wizardSource.includes("目录 {{ menuStats.directory }}") &&
    wizardSource.includes("菜单 {{ menuStats.menu }}") &&
    wizardSource.includes("按钮 {{ menuStats.button }}") &&
    wizardSource.includes("父子联动") &&
    wizardSource.includes("确认创建"),
  "role wizard should show accurate permission-type summaries before creation"
)

for (const source of [deptSource, menuSource]) {
  assert.ok(
    source.includes("pendingSortGuard") &&
      source.includes("hasPendingSortChanges") &&
      source.includes("pendingSortCount") &&
      source.includes("buildSortChangeRequest") &&
      source.includes("handleSortSaveFailure"),
    "department and menu pages should share pending-sort protection and optimistic request handling"
  )
}

assert.ok(
  guardSource.includes("beforeRouteLeave") &&
    guardSource.includes("beforeunload") &&
    guardSource.includes("handleSortRefresh") &&
    guardSource.includes("originalOrders") &&
    guardSource.includes("expectedOrderNum") &&
    guardSource.includes("newOrderNum") &&
    guardSource.includes("SORT_CONFLICT"),
  "pending sort guard should cover route, tab/reload, refresh and optimistic concurrency conflicts"
)

assert.ok(
  !deptSource.includes("changedDeptIds.join") &&
    !menuSource.includes("changedMenuIds.join") &&
    deptApiSource.includes("silentError: true") &&
    menuApiSource.includes("silentError: true"),
  "sort APIs should use validated arrays and let the page present one actionable conflict message"
)

console.log("roleAndSortUx tests passed")
