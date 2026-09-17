// Memory only, scoped to actor, login revision and organization. Never store prices or contact details.
const scopes = new Map()
function rememberSalesChoice(scope, kind, value) {
  if (!scope || !value || !value.id) return
  const key = scope + ':' + kind, values = scopes.get(key) || []
  const entry = { id: String(value.id), type: value.type || '', label: value.label || '' }
  scopes.set(key, [entry, ...values.filter(row => row.id !== entry.id || row.type !== entry.type)].slice(0, 8))
  if (scopes.size > 20) scopes.delete(scopes.keys().next().value)
}
function recentSalesChoices(scope, kind) { return (scopes.get(scope + ':' + kind) || []).map(row => ({ ...row })) }
module.exports = { rememberSalesChoice, recentSalesChoices }
