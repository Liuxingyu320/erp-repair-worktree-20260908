const DEPT_ID_KEY = "selected_dept_id"
const DEPT_NAME_KEY = "selected_dept_name"
const DEPT_TYPE_KEY = "selected_dept_type"
const SELECTED_DEPT_VALIDATED_KEY = "selected_dept_validated"

export const DEPT_TYPE_STORE = "STORE"
export const DEPT_TYPE_WAREHOUSE = "WAREHOUSE"
export const DEPT_TYPE_COMPANY = "COMPANY"
export const DEPT_TYPE_GROUP = "GROUP"

export function normalizeDeptType(deptType) {
  return deptType ? String(deptType).trim().toUpperCase() : ""
}

export function isValidInventoryDeptType(deptType) {
  const normalizedType = normalizeDeptType(deptType)
  return normalizedType === DEPT_TYPE_STORE || normalizedType === DEPT_TYPE_WAREHOUSE
}

export function hasValidInventoryDeptContext(context = {}) {
  const source = context || {}
  const hasDeptId = source.deptId !== undefined && source.deptId !== null &&
    String(source.deptId).trim() !== ""
  return hasDeptId && isValidInventoryDeptType(source.deptType)
}

export function findBusinessDeptNodes(nodes = []) {
  return (nodes || []).reduce((result, item) => {
    if (!item) {
      return result
    }
    if (isValidInventoryDeptType(item.deptType)) {
      result.push(item)
    }
    return result.concat(findBusinessDeptNodes(item.children || []))
  }, [])
}

export function findUniqueBusinessDept(nodes = []) {
  const businessNodes = findBusinessDeptNodes(nodes)
  return businessNodes.length === 1 ? businessNodes[0] : null
}

export function findDeptNodeById(nodes = [], deptId) {
  const targetId = deptId === undefined || deptId === null ? "" : String(deptId).trim()
  if (!targetId) {
    return null
  }
  for (const item of nodes || []) {
    if (!item) {
      continue
    }
    if (String(item.deptId) === targetId) {
      return item
    }
    const child = findDeptNodeById(item.children || [], targetId)
    if (child) {
      return child
    }
  }
  return null
}

export function isSelectableDeptType(deptType) {
  const normalizedType = normalizeDeptType(deptType)
  return normalizedType === DEPT_TYPE_STORE ||
    normalizedType === DEPT_TYPE_WAREHOUSE ||
    normalizedType === DEPT_TYPE_COMPANY ||
    normalizedType === DEPT_TYPE_GROUP
}

function getSessionStorage() {
  if (typeof sessionStorage === "undefined") {
    return null
  }
  try {
    return sessionStorage
  } catch (e) {
    return null
  }
}

function emitSelectedDeptChanged(detail) {
  if (typeof window === "undefined" || !window || typeof window.dispatchEvent !== "function") {
    return
  }
  const event = typeof CustomEvent === "function"
    ? new CustomEvent("erp:dept-changed", { detail })
    : { type: "erp:dept-changed", detail }
  window.dispatchEvent(event)
}

export function getSelectedDeptId() {
  const storage = getSessionStorage()
  return storage ? storage.getItem(DEPT_ID_KEY) : null
}

export function getSelectedDeptName() {
  const storage = getSessionStorage()
  return storage ? storage.getItem(DEPT_NAME_KEY) : null
}

export function getSelectedDeptType() {
  const storage = getSessionStorage()
  return storage ? storage.getItem(DEPT_TYPE_KEY) : null
}

export function hasSelectedDeptContext() {
  const context = getSelectedDeptContext()
  return !!context.deptId && isSelectableDeptType(context.deptType)
}

export function hasSelectedInventoryDeptContext() {
  return hasValidInventoryDeptContext(getSelectedDeptContext())
}

export function markSelectedDeptValidated() {
  if (!hasSelectedInventoryDeptContext()) {
    return
  }
  const storage = getSessionStorage()
  storage && storage.setItem(SELECTED_DEPT_VALIDATED_KEY, "1")
}

export function invalidateSelectedDeptValidation() {
  const storage = getSessionStorage()
  storage && storage.removeItem(SELECTED_DEPT_VALIDATED_KEY)
}

export function isSelectedDeptValidated() {
  const storage = getSessionStorage()
  return !!storage && storage.getItem(SELECTED_DEPT_VALIDATED_KEY) === "1"
}

export function hasValidatedSelectedDeptContext() {
  return hasSelectedInventoryDeptContext() && isSelectedDeptValidated()
}

export function isSelectedStore() {
  return normalizeDeptType(getSelectedDeptType()) === DEPT_TYPE_STORE
}

export function isSelectedWarehouse() {
  return normalizeDeptType(getSelectedDeptType()) === DEPT_TYPE_WAREHOUSE
}

export function getSelectedDeptContext() {
  const deptType = normalizeDeptType(getSelectedDeptType())
  return {
    deptId: getSelectedDeptId(),
    deptName: getSelectedDeptName(),
    deptType,
    isStore: deptType === DEPT_TYPE_STORE,
    isWarehouse: deptType === DEPT_TYPE_WAREHOUSE,
    isCompany: deptType === DEPT_TYPE_COMPANY,
    isGroup: deptType === DEPT_TYPE_GROUP,
    isInventory: isValidInventoryDeptType(deptType)
  }
}

export function formatInventoryDeptLabel(source, options = {}) {
  const fallback = options.fallback || "-"
  if (!source) {
    return fallback
  }
  const context = Object.assign({}, source)
  const deptType = normalizeDeptType(context.deptType)
  const isStore = context.isStore || deptType === DEPT_TYPE_STORE
  const isWarehouse = context.isWarehouse ||
    deptType === DEPT_TYPE_WAREHOUSE ||
    (!isStore && !deptType && !context.shopDeptName && !!context.warehouseName)
  const id = context.deptId || context.warehouseId || context.shopDeptId
  let name = context.deptName || context.warehouseName || context.shopDeptName || context.name
  if (context.shopDeptName && context.warehouseName && context.shopDeptName !== context.warehouseName) {
    name = context.shopDeptName + " / " + context.warehouseName
  }
  if (!name) {
    return id && options.showId ? "组织 ID " + id : fallback
  }
  const prefix = isWarehouse ? "仓库" : isStore ? "门店" : "组织"
  const idText = id && options.showId ? "（ID " + id + "）" : ""
  return prefix + "：" + name + idText
}

export function getSelectedInventoryDeptId() {
  return hasSelectedInventoryDeptContext() ? getSelectedDeptId() : null
}

export function setSelectedDept(deptId, deptName = "", deptType = "") {
  if (deptId === undefined || deptId === null || deptId === "") {
    return
  }
  const storage = getSessionStorage()
  if (!storage) {
    return
  }
  storage.setItem(DEPT_ID_KEY, String(deptId))
  storage.setItem(DEPT_NAME_KEY, deptName || "")
  const normalizedType = normalizeDeptType(deptType)
  if (normalizedType) {
    storage.setItem(DEPT_TYPE_KEY, normalizedType)
  } else {
    storage.removeItem(DEPT_TYPE_KEY)
  }
  if (isValidInventoryDeptType(normalizedType)) {
    markSelectedDeptValidated()
  } else {
    invalidateSelectedDeptValidation()
  }
  emitSelectedDeptChanged({
    deptId: String(deptId),
    name: deptName || "",
    type: normalizedType
  })
}

export function clearSelectedDept() {
  const storage = getSessionStorage()
  if (!storage) {
    return
  }
  storage.removeItem(DEPT_ID_KEY)
  storage.removeItem(DEPT_NAME_KEY)
  storage.removeItem(DEPT_TYPE_KEY)
  storage.removeItem(SELECTED_DEPT_VALIDATED_KEY)
  emitSelectedDeptChanged({ deptId: null, name: "", type: "" })
}
