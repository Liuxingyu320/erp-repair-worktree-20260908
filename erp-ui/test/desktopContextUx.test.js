const assert = require('assert')
const fs = require('fs')
const path = require('path')

const rootDir = path.resolve(__dirname, '..')

function readSource(relativePath) {
  return fs.readFileSync(path.join(rootDir, relativePath), 'utf8')
}

function assertIncludes(source, needle, message) {
  assert(
    source.includes(needle),
    `${message}\nExpected source to include: ${needle}`
  )
}

function assertBefore(source, first, second, message) {
  const firstIndex = source.indexOf(first)
  const secondIndex = source.indexOf(second)
  assert(firstIndex !== -1, `${message}\nMissing first marker: ${first}`)
  assert(secondIndex !== -1, `${message}\nMissing second marker: ${second}`)
  assert(
    firstIndex < secondIndex,
    `${message}\nExpected "${first}" to appear before "${second}"`
  )
}

const homeSource = readSource('src/views/index.vue')
assertIncludes(homeSource, 'resolveContextRoute', 'Home quick links should resolve routes from the selected desktop organization context.')
assertIncludes(homeSource, 'isWarehouseContext', 'Home page should detect warehouse context before building shortcuts.')
assertIncludes(homeSource, 'warehousePath: "/cangku/stock"', 'Warehouse users should be sent to the warehouse stock page.')
assertIncludes(homeSource, 'storePath: "/inventory/stock"', 'Store users should keep the store stock entry.')
assertIncludes(homeSource, 'warehousePath: "/cangku/purchase"', 'Warehouse users should see the warehouse purchase flow instead of sales shortcuts.')
assertIncludes(homeSource, 'storePath: "/inventory/sales"', 'Store users should keep the sales flow shortcut.')

const salesSource = readSource('src/views/inventory/sales/index.vue')
assertIncludes(salesSource, 'isSelectedStore', 'Sales page should use shop context helpers to detect invalid warehouse context.')
assertIncludes(salesSource, 'isStoreContext', 'Sales page should expose a store-context computed flag.')
assertIncludes(salesSource, ':disabled="!isStoreContext"', 'New sales action should be disabled outside store context.')
assertIncludes(salesSource, 'ensureStoreContext', 'Sales write flows should guard against warehouse context.')
assertIncludes(salesSource, '销售单需要选择门店后操作', 'Sales page should explain the context mismatch before users enter a form.')
assertBefore(
  salesSource,
  'if (!this.ensureStoreContext())',
  'this.resetForm()',
  'Opening a new sales form should check context before resetting/opening the dialog.'
)

const stockSource = readSource('src/views/inventory/stock/index.vue')
assertIncludes(stockSource, 'getAdjustConfirmMessage', 'Stock adjustment should build a human-readable impact summary.')
assertIncludes(stockSource, '确认写入库存调整', 'Stock adjustment should show a deliberate confirmation title.')
assertIncludes(stockSource, '不可直接撤回', 'Stock adjustment confirmation should mention the operation cannot be directly undone.')
assertBefore(
  stockSource,
  'this.$modal.confirm(this.getAdjustConfirmMessage(payload)',
  'adjustStock(payload)',
  'Stock adjustment API call should happen only after second confirmation.'
)

const permissionSource = readSource('src/store/modules/permission.js')
assertIncludes(permissionSource, 'filterDuplicateDynamicRoutes', 'Permission module should filter frontend fallback routes already provided by backend menus.')
assertIncludes(permissionSource, 'collectRouteIdentities', 'Permission module should collect backend route identities before adding fallback routes.')
assertBefore(
  permissionSource,
  'const asyncRoutes = filterDuplicateDynamicRoutes(filterDynamicRoutes(dynamicRoutes), rewriteRoutes)',
  'router.addRoutes(asyncRoutes)',
  'Static fallback routes should be deduped before router.addRoutes(asyncRoutes).'
)

console.log('desktopContextUx assertions passed')
