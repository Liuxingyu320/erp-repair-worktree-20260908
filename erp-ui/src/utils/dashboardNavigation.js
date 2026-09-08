function toPathSet(paths) {
  return paths instanceof Set ? paths : new Set(Array.isArray(paths) ? paths : [])
}

function visibleDashboardEntries(entries, availablePaths) {
  const paths = toPathSet(availablePaths)
  return (entries || []).filter(entry => {
    if (!entry) return false
    if (entry.always === true) return true
    return !!entry.path && paths.has(entry.path)
  })
}

module.exports = { visibleDashboardEntries }
