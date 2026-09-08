const STORE = "STORE"

function asNodes(payload) {
  if (Array.isArray(payload)) return payload
  if (payload && Array.isArray(payload.data)) return payload.data
  if (payload && Array.isArray(payload.rows)) return payload.rows
  return []
}

function nodeId(node) {
  if (!node || typeof node !== "object") return ""
  const value = node.deptId === undefined ? node.id : node.deptId
  return value === undefined || value === null ? "" : String(value).trim()
}

function nodeName(node) {
  if (!node || typeof node !== "object") return ""
  const value = node.deptName === undefined ? node.label : node.deptName
  return value === undefined || value === null ? "" : String(value).trim()
}

function nodeType(node) {
  if (!node || typeof node !== "object") return ""
  const value = node.deptType === undefined ? node.type : node.deptType
  return value === undefined || value === null ? "" : String(value).trim().toUpperCase()
}

/**
 * Build one read-only hierarchy snapshot from the already-authorized shop tree.
 * The current node is intentionally excluded from ancestorPath.
 */
export function buildTransferOrgHierarchy(payload) {
  const byId = Object.create(null)
  const stores = []
  const storeIds = Object.create(null)

  function visit(nodes, ancestors) {
    ;(nodes || []).forEach(node => {
      if (!node || typeof node !== "object") return
      const id = nodeId(node)
      const name = nodeName(node)
      const type = nodeType(node)
      const ancestorPath = (ancestors || []).filter(Boolean)
      const entry = {
        deptId: node.deptId === undefined ? node.id : node.deptId,
        deptName: name,
        deptType: type,
        ancestorPath,
        ancestorPathText: ancestorPath.join(" / "),
        fullPathText: ancestorPath.concat(name).filter(Boolean).join(" / ")
      }
      if (id && !byId[id]) byId[id] = entry
      if (id && type === STORE && !storeIds[id]) {
        storeIds[id] = true
        stores.push(Object.assign({}, entry, { label: entry.fullPathText || name }))
      }
      const children = Array.isArray(node.children) ? node.children : []
      visit(children, name ? ancestorPath.concat(name) : ancestorPath)
    })
  }

  visit(asNodes(payload), [])
  return { byId, stores }
}

export function findTransferOrgAncestorPath(hierarchy, deptId) {
  const key = deptId === undefined || deptId === null ? "" : String(deptId).trim()
  if (!key || !hierarchy || !hierarchy.byId) return ""
  const node = hierarchy.byId[key]
  return node && node.ancestorPathText ? node.ancestorPathText : ""
}

/**
 * Normalize the warehouse-scoped visible-store response into the same option
 * shape used by the authorized shop tree. The endpoint may return a flat list,
 * so an already-known tree entry is used only to enrich its label.
 */
export function buildTransferVisibleStoreOptions(payload, hierarchy) {
  const visibleHierarchy = buildTransferOrgHierarchy(payload)
  const knownById = hierarchy && hierarchy.byId ? hierarchy.byId : {}
  return visibleHierarchy.stores.map(store => {
    const key = String(store.deptId)
    const known = knownById[key]
    const enriched = known ? Object.assign({}, store, {
      ancestorPath: known.ancestorPath,
      ancestorPathText: known.ancestorPathText,
      fullPathText: known.fullPathText
    }) : store
    return Object.assign({}, enriched, {
      label: enriched.fullPathText || enriched.deptName || store.label
    })
  })
}

/**
 * Prefer the backend path attached to an already-authorized transfer row.
 * The tree is only a compatibility fallback for older rows or narrow scopes.
 */
export function resolveTransferOrgAncestorPath(row, hierarchy) {
  const backendPath = row && row.toDeptHierarchy
  if (backendPath !== undefined && backendPath !== null && String(backendPath).trim()) {
    return String(backendPath).trim()
  }
  return findTransferOrgAncestorPath(hierarchy, row && row.toDeptId)
}
