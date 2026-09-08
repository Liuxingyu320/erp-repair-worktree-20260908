const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const babel = require('../node_modules/@babel/core')
const compiler = require('../node_modules/vue-template-compiler')

const componentPath = path.resolve(__dirname, '../src/views/system/shop/index.vue')
const source = fs.readFileSync(componentPath, 'utf8')

function loadComponent() {
  const descriptor = compiler.parseComponent(source)
  const transformed = babel.transformSync(descriptor.script.content, {
    filename: componentPath,
    babelrc: false,
    configFile: false,
    sourceType: 'module',
    plugins: [require.resolve('../node_modules/@babel/plugin-transform-modules-commonjs')]
  }).code
  const module = { exports: {} }
  const emptyApi = () => Promise.resolve({})
  const dependencies = {
    '@/api/system/post': { __esModule: true, optionselectPost: emptyApi },
    '@/api/system/userShop': {
      __esModule: true,
      batchUserShop: emptyApi,
      getUserShop: emptyApi,
      listUserShopUsers: emptyApi,
      shopTree: emptyApi,
      previewUserShop: emptyApi,
      updateUserShop: emptyApi
    },
    '@/views/system/user/view': { __esModule: true, default: {} },
    '@/components/ResizableSplitPane': { __esModule: true, default: {} }
  }
  vm.runInNewContext(transformed, {
    module,
    exports: module.exports,
    require(request) {
      if (Object.prototype.hasOwnProperty.call(dependencies, request)) return dependencies[request]
      throw new Error(`unexpected dependency: ${request}`)
    },
    Promise,
    Set,
    Boolean,
    Number,
    String,
    Array,
    Object
  }, { filename: componentPath })
  return module.exports.default || module.exports
}

const component = loadComponent()
const computedNames = Object.keys(component.computed || {})
;[
  'shopLoading',
  'managementUxV2Enabled',
  'canSaveShopScope',
  'directAuthorizationCount',
  'inheritedAuthorizationCount',
  'shopScopeChanges',
  'shopScopeChangeSummary'
].forEach(name => {
  assert.ok(computedNames.includes(name), `runtime component should register computed property ${name}`)
})
let requestedPermissions = []
assert.strictEqual(component.computed.canSaveShopScope.call({
  $auth: {
    hasPermiAnd(permissions) {
      requestedPermissions = Array.from(permissions)
      return true
    }
  }
}), true, 'authorized users should retain the organization-scope save action at runtime')
assert.deepStrictEqual(requestedPermissions, ['system:userShop:query', 'system:userShop:edit'])

assert.ok(source.includes('editableShopIds'), 'user shop page should consume editableShopIds from the backend')
assert.ok(source.includes('preservedShopIds'), 'user shop page should keep out-of-scope preserved shop ids in state')
assert.ok(source.includes('outOfScopeCount'), 'user shop page should surface hidden out-of-scope authorization count')
assert.ok(source.includes('preservedShopCount'), 'user shop page should render preserved shop count state')
assert.ok(
  /const editableShopIds = response\.editableShopIds \|\| response\.shopIds \|\| \[\]/.test(source),
  'tree selection should be driven by editableShopIds instead of all shopIds'
)
assert.ok(
  /this\.selectedShopIds = this\.normalizeShopIds\(editableShopIds\)/.test(source),
  'selectedShopIds should track only editable checked keys'
)
assert.ok(
  source.includes('previewUserShop(userId, checkedKeys)') &&
    source.includes('updateUserShop(userId, this.previewedShopIds, this.scopePreview.scopeVersion)') &&
    source.includes('applyAuthoritativeShopScope(response.data || {})'),
  'save should preview first, carry scopeVersion, and adopt the authoritative response'
)
assert.ok(
  /const fullShopIds = this\.normalizeShopIds\(\[\.\.\.normalizedIds, \.\.\.preservedIds\]\)/.test(source),
  'after save the list cache should merge authoritative editable ids with preserved ids'
)
assert.ok(
  source.includes('USER_SHOP_SCOPE_CONFLICT') &&
    source.includes('已为你重新加载最新结果') &&
    source.includes('this.loadCurrentUserShop(this.currentUser)'),
  'concurrent shop-scope changes should never overwrite silently and must reload latest state'
)
assert.ok(
  source.includes('node.deptType === "GROUP"') &&
    source.includes('node.deptType === "COMPANY"') &&
    source.includes('node.deptType === "STORE"') &&
    source.includes('node.deptType === "WAREHOUSE"'),
  'authorization tree should allow company/group nodes as scope roots while still allowing stores and warehouses'
)
assert.ok(
  source.includes('勾选公司/组织/门店/仓库，授权后自动包含下级业务组织') &&
    source.includes('请输入公司、组织、门店或仓库名称') &&
    source.includes('暂无组织，请先在部门管理中维护组织类型'),
  'authorization page copy should explain inherited organization scope instead of only direct shop binding'
)

console.log('userShopScopeUx tests passed')
