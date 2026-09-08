const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const uiRoot = path.resolve(__dirname, '..')

function readUi(relativePath) {
  return fs.readFileSync(path.resolve(uiRoot, relativePath), 'utf8')
}

function loadPolicy() {
  const source = readUi('src/utils/desktopContextPolicy.js')
  const sandbox = { module: { exports: {} }, exports: {} }
  sandbox.exports = sandbox.module.exports
  vm.runInNewContext(
    source.replace(
      /\bexport\s+function\s+requiresInventoryContext/,
      'function requiresInventoryContext'
    ) + '\nmodule.exports = { requiresInventoryContext }',
    sandbox,
    { filename: 'desktopContextPolicy.js' }
  )
  return sandbox.module.exports.requiresInventoryContext
}

const permissionSource = readUi('src/permission.js')
const loginSource = readUi('src/views/login.vue')

assert(
  permissionSource.includes("import { requiresInventoryContext } from '@/utils/desktopContextPolicy'"),
  'The navigation guard should use the shared desktop context policy.'
)
assert(
  !permissionSource.includes('inventoryContextPrefixes'),
  'The navigation guard should not maintain a second prefix list.'
)
assert(
  permissionSource.includes("query: { redirect: to.fullPath }"),
  'Context selection should preserve the complete requested path.'
)
assert(
  loginSource.includes("import { requiresInventoryContext } from '@/utils/desktopContextPolicy'") &&
    loginSource.includes('isSafeInternalRedirect(redirect)') &&
    loginSource.includes('!requiresInventoryContext(redirect)'),
  'Desktop login should preserve safe context-free deep links instead of forcing shop selection.'
)

const requiresInventoryContext = loadPolicy()

;[
  '/inventory/purchase',
  '/inventory/purchaseReturn',
  '/inventory/purchase/detail/12?tab=receive',
  '/cangku/purchase',
  '/cangku/purchase/detail/12',
  '/cangku/purchaseReturn',
  '/cangku/purchaseReturn/detail/12?tab=items#row-3',
  '/cangku/stock',
  '/cangku/transfer',
  '/cangku/transfer-records/detail/8',
  '/mobile/inventory'
].forEach(route => {
  assert.strictEqual(
    requiresInventoryContext(route),
    true,
    `${route} should require a validated organization context.`
  )
})

;[
  '/oa/purchase',
  '/select-shop',
  '/cangku/purchaser',
  '/cangku/purchase-report',
  '/mobile-office',
  '/system/user',
  ''
].forEach(route => {
  assert.strictEqual(
    requiresInventoryContext(route),
    false,
    `${route || '(empty path)'} should not be classified as an inventory-context route.`
  )
})

console.log('desktopContextPolicy assertions passed')
