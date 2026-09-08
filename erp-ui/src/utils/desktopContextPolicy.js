const inventoryContextRoots = [
  '/inventory',
  '/cangku/stock',
  '/cangku/transfer',
  '/cangku/transfer-records',
  '/cangku/purchase',
  '/cangku/purchaseReturn',
  '/mobile'
]

function normalizeRoutePath(value) {
  const path = String(value == null ? '' : value).split('#')[0].split('?')[0]
  if (!path || path.charAt(0) !== '/') return ''
  return path.length > 1 ? path.replace(/\/+$/, '') : path
}

function isRootOrChildPath(path, root) {
  return path === root || path.indexOf(root + '/') === 0
}

export function requiresInventoryContext(value) {
  const path = normalizeRoutePath(value)
  return !!path && inventoryContextRoots.some(root => isRootOrChildPath(path, root))
}
