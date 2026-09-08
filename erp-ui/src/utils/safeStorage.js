function hasStorageMethod(storage, method) {
  try {
    return !!storage && typeof storage[method] === "function"
  } catch (error) {
    return false
  }
}

export function getSafeLocalStorage() {
  try {
    return typeof localStorage === "undefined" ? null : localStorage
  } catch (error) {
    return null
  }
}

export function safeRemoveStorageValue(storage, key) {
  if (!hasStorageMethod(storage, "removeItem")) return false
  try {
    storage.removeItem(key)
    return true
  } catch (error) {
    return false
  }
}

export function safeSetStorageValue(storage, key, value) {
  if (!hasStorageMethod(storage, "setItem")) return false
  try {
    storage.setItem(key, value)
    return true
  } catch (error) {
    return false
  }
}

export function readStorageValue(storage, key, options = {}) {
  const defaultValue = options.defaultValue
  if (!hasStorageMethod(storage, "getItem")) return defaultValue

  let rawValue
  try {
    rawValue = storage.getItem(key)
  } catch (error) {
    return defaultValue
  }
  if (rawValue === null || rawValue === undefined) return defaultValue

  let value = rawValue
  try {
    if (typeof options.parse === "function") value = options.parse(rawValue)
  } catch (error) {
    safeRemoveStorageValue(storage, key)
    return defaultValue
  }

  if (typeof options.validate === "function") {
    let valid = false
    try {
      valid = options.validate(value) === true
    } catch (error) {
      valid = false
    }
    if (!valid) {
      safeRemoveStorageValue(storage, key)
      return defaultValue
    }
  }
  return value
}

export function readJsonStorageValue(storage, key, defaultValue, validate) {
  return readStorageValue(storage, key, {
    defaultValue,
    parse: JSON.parse,
    validate
  })
}

export function readStringStorageValue(storage, key, defaultValue, validate) {
  return readStorageValue(storage, key, {
    defaultValue,
    validate: value => typeof value === "string" &&
      (typeof validate !== "function" || validate(value))
  })
}

export function isPlainStorageObject(value) {
  return Object.prototype.toString.call(value) === "[object Object]"
}
