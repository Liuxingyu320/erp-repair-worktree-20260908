// A page owns the meaning of its context (actor/session, organization and target).
// This utility only tracks lifetimes; it never sends, cancels or retries requests.
function snapshot(value, ancestors = new Set()) {
  if (value === undefined) return ["undefined"]
  if (value === null) return ["null"]
  const type = typeof value
  if (type === "string" || type === "boolean") return [type, value]
  if (type === "number" && Number.isFinite(value)) return [type, value]
  if (type === "bigint") return [type, String(value)]
  if (type !== "object" || ancestors.has(value)) throw new Error("Invalid operation context")
  ancestors.add(value)
  let result
  if (Array.isArray(value)) {
    result = ["array", value.map(item => snapshot(item, ancestors))]
  } else {
    if (Object.prototype.toString.call(value) !== "[object Object]") {
      throw new Error("Operation context must contain plain data")
    }
    result = ["object", Object.keys(value).sort().map(key => [key, snapshot(value[key], ancestors)])]
  }
  ancestors.delete(value)
  return result
}

function contextKey(value) {
  return JSON.stringify(snapshot(value))
}

/**
 * readContext must return plain immutable identity values, not a Vue component.
 * Include the authenticated actor/session revision and selected organization.
 * A component must invalidate on identity changes (including A -> B -> A),
 * dialog close and disposal. Watching only the final context is insufficient.
 *
 * begin(lane, identity?) gives one request/confirmation its own token.
 * isCurrent(token, identity?) guards success, failure, finally, and every
 * continuation after validation/confirmation. Passing identity also compares
 * the current business target with the one supplied to begin.
 *
 * Identity is copied as a comparison key; request payloads belong to callers.
 * No credentials, payloads or tokens are persisted by this helper.
 */
function createUiOperationScope(readContext = () => null) {
  let generation = 0
  let active = true
  const sequences = new Map()
  const records = new WeakMap()

  function readKey() {
    try { return { valid: true, key: contextKey(readContext()) } }
    catch (error) { return { valid: false, key: "" } }
  }

  function invalidate(lane) {
    if (lane === undefined) {
      generation += 1
      sequences.clear()
    } else {
      sequences.set(lane, (sequences.get(lane) || 0) + 1)
    }
  }

  return {
    begin(lane = "default", identity = null) {
      const sequence = (sequences.get(lane) || 0) + 1
      sequences.set(lane, sequence)
      const context = readKey()
      let identityKey
      try { identityKey = contextKey(identity) }
      catch (error) { context.valid = false }
      const token = Object.freeze({ lane })
      records.set(token, { generation, sequence, context, identityKey, active })
      return token
    },
    isCurrent(token, identity) {
      const record = token && records.get(token)
      if (!record || !active || !record.active || !record.context.valid ||
          record.generation !== generation || record.sequence !== sequences.get(token.lane)) return false
      const context = readKey()
      if (!context.valid || context.key !== record.context.key) return false
      if (arguments.length > 1) {
        try { if (contextKey(identity) !== record.identityKey) return false }
        catch (error) { return false }
      }
      return true
    },
    invalidate,
    deactivate() {
      active = false
      invalidate()
    },
    activate() {
      if (!active) invalidate()
      active = true
    }
  }
}

module.exports = { createUiOperationScope }
