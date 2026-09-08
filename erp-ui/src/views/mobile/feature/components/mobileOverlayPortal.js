function portalMobileOverlay(node, host) {
  const targetHost = host || (
    typeof document !== "undefined" && document.body ? document.body : null
  )
  if (!node || !targetHost || !node.parentNode || node.parentNode === targetHost) return null

  const anchor = {
    node,
    parent: node.parentNode,
    nextSibling: node.nextSibling || null
  }
  targetHost.appendChild(node)
  return anchor
}

function restoreMobileOverlay(node, anchor) {
  const targetNode = node || (anchor && anchor.node)
  const parent = anchor && anchor.parent
  if (!targetNode || !parent) return false
  if (targetNode.parentNode === parent) return true

  const nextSibling = anchor.nextSibling
  if (nextSibling && nextSibling.parentNode === parent && typeof parent.insertBefore === "function") {
    parent.insertBefore(targetNode, nextSibling)
  } else if (typeof parent.appendChild === "function") {
    parent.appendChild(targetNode)
  } else {
    return false
  }
  return true
}

module.exports = {
  portalMobileOverlay,
  restoreMobileOverlay
}
