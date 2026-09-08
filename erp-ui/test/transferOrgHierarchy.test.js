const assert = require("assert")

function loadHelper() {
  const fs = require("fs")
  const path = require("path")
  const source = fs.readFileSync(
    path.resolve(__dirname, "../src/views/inventory/transfer/transferOrgHierarchy.js"),
    "utf8"
  )
  const transformed = source
    .replace(/export function /g, "function ")
    .concat("\nmodule.exports = { buildTransferOrgHierarchy, buildTransferVisibleStoreOptions, findTransferOrgAncestorPath, resolveTransferOrgAncestorPath }\n")
  const module = { exports: {} }
  new Function("module", "exports", transformed)(module, module.exports)
  return module.exports
}

const {
  buildTransferOrgHierarchy,
  buildTransferVisibleStoreOptions,
  findTransferOrgAncestorPath,
  resolveTransferOrgAncestorPath
} = loadHelper()

const hierarchy = buildTransferOrgHierarchy([
  {
    deptId: 1,
    deptName: "金英灵域",
    deptType: "GROUP",
    children: [{
      deptId: 2,
      deptName: "浙江区域",
      deptType: "COMPANY",
      children: [{
        deptId: 3,
        deptName: "浙江一区",
        deptType: "COMPANY",
        children: [{ deptId: 4, deptName: "杭州店", deptType: "STORE" }]
      }]
    }]
  },
  { deptId: 9, deptName: "孤立仓", deptType: "WAREHOUSE" }
])

assert.strictEqual(
  findTransferOrgAncestorPath(hierarchy, 4),
  "金英灵域 / 浙江区域 / 浙江一区",
  "target organization path should exclude the target store itself"
)
assert.strictEqual(hierarchy.stores.length, 1, "only STORE nodes should be filter options")
assert.strictEqual(hierarchy.stores[0].label, "金英灵域 / 浙江区域 / 浙江一区 / 杭州店")
assert.strictEqual(findTransferOrgAncestorPath(hierarchy, 999), "", "unknown history nodes should degrade safely")
assert.deepStrictEqual(buildTransferOrgHierarchy(null).stores, [])
assert.strictEqual(
  resolveTransferOrgAncestorPath({ toDeptId: 999, toDeptHierarchy: "后端安全路径 / 浙江一区" }, buildTransferOrgHierarchy(null)),
  "后端安全路径 / 浙江一区",
  "backend hierarchy must work when the target store is absent from the authorized tree"
)
assert.strictEqual(
  resolveTransferOrgAncestorPath({ toDeptId: 4, toDeptHierarchy: " " }, hierarchy),
  "金英灵域 / 浙江区域 / 浙江一区",
  "tree path remains a compatibility fallback"
)
const visibleStoreOptions = buildTransferVisibleStoreOptions(
  [{ deptId: 4, deptName: "杭州店", deptType: "STORE" }],
  hierarchy
)
assert.strictEqual(visibleStoreOptions.length, 1)
assert.strictEqual(visibleStoreOptions[0].label, "金英灵域 / 浙江区域 / 浙江一区 / 杭州店")
assert.deepStrictEqual(
  buildTransferVisibleStoreOptions([{ deptId: 8, deptName: "孤立店", deptType: "STORE" }]).map(item => item.label),
  ["孤立店"],
  "flat visible-store responses must degrade to the store name"
)

console.log("transferOrgHierarchy tests passed")
