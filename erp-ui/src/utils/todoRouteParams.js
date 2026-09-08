const FORBIDDEN_QUERY_KEYS = new Set(["__proto__", "prototype", "constructor"])

function isPlainRouteParamRecord(value) {
  const prototype = Object.getPrototypeOf(value)
  if (prototype === null) return true
  // Objects created in a VM/iframe have a different Object.prototype identity.
  // Validate the prototype shape instead of comparing it with this realm's
  // Object.prototype so otherwise-safe cached todo parameters remain usable.
  if (Object.getPrototypeOf(prototype) !== null) return false
  const constructor = Object.getOwnPropertyDescriptor(prototype, "constructor")
  return !!constructor && Object.prototype.hasOwnProperty.call(constructor, "value") &&
    typeof constructor.value === "function" && constructor.value.name === "Object"
}

function sanitizeRouteParams(routeParams) {
  if (routeParams === undefined || routeParams === null) {
    return { ok: true, params: {} }
  }
  if (typeof routeParams !== "object" || Array.isArray(routeParams)) {
    return { ok: false }
  }
  if (!isPlainRouteParamRecord(routeParams)) {
    return { ok: false }
  }
  const params = {}
  const keys = Object.keys(routeParams)
  for (const key of keys) {
    if (FORBIDDEN_QUERY_KEYS.has(key)) {
      return { ok: false }
    }
    const descriptor = Object.getOwnPropertyDescriptor(routeParams, key)
    if (!descriptor || !Object.prototype.hasOwnProperty.call(descriptor, "value")) {
      return { ok: false }
    }
    const value = descriptor.value
    const valueType = typeof value
    if (value !== null && valueType !== "string" && valueType !== "number" && valueType !== "boolean") {
      return { ok: false }
    }
    if (valueType === "number" && !Number.isFinite(value)) {
      return { ok: false }
    }
    params[key] = value
  }
  return { ok: true, params }
}

module.exports = {
  sanitizeRouteParams
}
