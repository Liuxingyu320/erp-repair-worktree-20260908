const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const uiRoot = path.resolve(__dirname, "..")
const repoRoot = path.resolve(uiRoot, "..")

function readUi(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), "utf8")
}

function readRepo(relativePath) {
  return fs.readFileSync(path.resolve(repoRoot, relativePath), "utf8")
}

function assertIncludes(source, needle, message) {
  assert.ok(source.includes(needle), `${message}\nExpected source to include: ${needle}`)
}

function loadShopContext() {
  const source = readUi("src/utils/shopContext.js")
  const sandbox = {
    module: { exports: {} },
    exports: {},
    sessionStorage: {
      getItem: () => null,
      setItem: () => {},
      removeItem: () => {}
    }
  }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(
    source.replace(/\bexport\s+/g, "") +
      `
module.exports = {
  findBusinessDeptNodes,
  findUniqueBusinessDept,
  isValidInventoryDeptType
}
`,
    sandbox,
    { filename: "shopContext.js" }
  )
  return sandbox.module.exports
}

const { findBusinessDeptNodes, findUniqueBusinessDept } = loadShopContext()

const oneStoreTree = [
  {
    deptId: 1,
    deptName: "总部",
    deptType: "GROUP",
    children: [
      { deptId: 2, deptName: "市场部", deptType: "COMPANY" },
      { deptId: 3, deptName: "西湖店", deptType: "STORE" }
    ]
  }
]

assert.strictEqual(
  JSON.stringify(findBusinessDeptNodes(oneStoreTree).map(item => item.deptId)),
  JSON.stringify([3]),
  "business context helpers should ignore company/group nodes and collect only store or warehouse nodes"
)
assert.strictEqual(
  findUniqueBusinessDept(oneStoreTree).deptName,
  "西湖店",
  "a single authorized store or warehouse should be auto-selectable after login"
)
assert.strictEqual(
  findUniqueBusinessDept([{ deptId: 1, deptType: "GROUP", children: [{ deptId: 2, deptType: "STORE" }, { deptId: 3, deptType: "WAREHOUSE" }] }]),
  null,
  "multiple business nodes must not be auto-selected silently"
)

const selectShopSource = readUi("src/views/select-shop/index.vue")
assertIncludes(selectShopSource, "applyAutoBusinessDept", "select-shop should apply a unique store/warehouse automatically after the tree loads")
assertIncludes(selectShopSource, "公司/组织仅用于展开下级", "select-shop should tell users company/group nodes are not operation contexts")
assertIncludes(selectShopSource, "请选择具体门店或仓库", "select-shop confirmation should reject company/group nodes")

const permissionSource = readUi("src/permission.js")
assertIncludes(permissionSource, "isRootEntryPath(path) && !hasValidatedSelectedDeptContext()", "desktop home should send users to organization selection when no valid business context exists")

const emptyStateSource = readUi("src/utils/businessEmptyState.js")
assertIncludes(emptyStateSource, "getBusinessEmptyText", "business pages should share explicit empty-state copy")
assertIncludes(emptyStateSource, "missingContext", "empty states should distinguish missing organization context")
assertIncludes(emptyStateSource, "missingBaseline", "empty states should explain missing baseline or acceptance data")

;[
  "src/views/inventory/sales/index.vue",
  "src/views/inventory/deliveryNotice/index.vue",
  "src/views/inventory/purchaseReturn/index.vue",
  "src/views/inventory/salesReturn/index.vue",
  "src/views/oa/purchase/index.vue",
  "src/views/oa/salary/index.vue",
  "src/views/oa/signPackage/index.vue",
  "src/views/oa/fixedAsset/config/index.vue",
  "src/views/oa/fixedAsset/repair/index.vue"
].forEach(file => {
  const source = readUi(file)
  assertIncludes(source, "getBusinessEmptyText", `${file} should use the shared business empty-state text`)
  assertIncludes(source, ":empty-text=", `${file} should pass explicit empty text to its main table`)
})

const fixedAssetConfig = readUi("src/views/oa/fixedAsset/config/index.vue")
assertIncludes(fixedAssetConfig, ":disabled=\"assetActionDisabled\" @click=\"handleExport\"", "fixed asset config should require a shop filter before exporting")
assertIncludes(fixedAssetConfig, "固定资产基础配置为空", "fixed asset config should explain the missing setup state")

const fixedAssetRepair = readUi("src/views/oa/fixedAsset/repair/index.vue")
assertIncludes(fixedAssetRepair, "repairActionDisabled", "fixed asset repair should disable report actions until a shop and asset configuration are ready")
assertIncludes(fixedAssetRepair, "请先切换到门店并维护固定资产配置", "fixed asset repair should explain why reporting is blocked")

const fileLocalConfig = readRepo("erp-modules/erp-file/src/main/resources/application-local.yml")
assertIncludes(fileLocalConfig, "prefix: /file", "file service local profile should define file.prefix")
assertIncludes(fileLocalConfig, "domain: ${FILE_DOMAIN:http://localhost:8080}", "local file URLs should retain gateway default while allowing an explicit device-accessible FILE_DOMAIN")

const fileBootstrap = readRepo("erp-modules/erp-file/src/main/resources/bootstrap.yml")
assertIncludes(fileBootstrap, "on-profile: local", "file service bootstrap should have a local profile block")
assertIncludes(fileBootstrap, "discovery:\n        enabled: false", "file service local profile should not require Nacos discovery")
assertIncludes(fileBootstrap, "config:\n        enabled: false", "file service local profile should not require Nacos config")

const innerLinkSource = readUi("src/layout/components/InnerLink/index.vue")
assertIncludes(innerLinkSource, "external-service-unavailable", "external monitor iframe should show a clear unavailable state")
assertIncludes(innerLinkSource, "openExternal", "external monitor iframe should offer opening the original service URL")
assertIncludes(innerLinkSource, "服务未启动或地址未配置", "external monitor iframe should not leave users on a blank broken iframe")

console.log("desktopAuditFollowupFixes assertions passed")
