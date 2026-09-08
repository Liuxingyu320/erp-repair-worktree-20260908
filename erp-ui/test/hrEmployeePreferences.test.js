const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const source = fs.readFileSync(path.join(root, "src/utils/hrEmployeePreferences.js"), "utf8")
  .replace(/export function /g, "function ")
const sandbox = { module: { exports: {} }, exports: {} }
sandbox.exports = sandbox.module.exports
vm.runInNewContext(`${source}\nmodule.exports = { preferenceKey, sanitizeHrEmployeePreferences, loadHrEmployeePreferences, saveHrEmployeePreferences }`, sandbox)
const {
  preferenceKey,
  sanitizeHrEmployeePreferences,
  loadHrEmployeePreferences,
  saveHrEmployeePreferences
} = sandbox.module.exports

const allowedColumns = ["contractEndDate", "legalEntity"]
const saved = sanitizeHrEmployeePreferences({
  advancedFilterOpen: true,
  selectedOptionalColumns: ["contractEndDate", "phoneNumber", "unknown"],
  pageSize: 50,
  keyword: "张三",
  employeeName: "张三",
  phoneNumber: "13800138000"
}, allowedColumns)
assert.deepStrictEqual(JSON.parse(JSON.stringify(saved)), {
  advancedFilterOpen: true,
  selectedOptionalColumns: ["contractEndDate"],
  pageSize: 50
})
assert.notStrictEqual(preferenceKey(7, ["hr:employee:list"]), preferenceKey(8, ["hr:employee:list"]))
assert.notStrictEqual(preferenceKey(7, ["hr:employee:list"]), preferenceKey(7, ["hr:employee:export"]))

const values = new Map()
const storage = {
  getItem(key) { return values.has(key) ? values.get(key) : null },
  setItem(key, value) { values.set(key, value) },
  removeItem(key) { values.delete(key) }
}
saveHrEmployeePreferences(storage, 7, ["hr:employee:list"], saved, allowedColumns)
assert.deepStrictEqual(JSON.parse(JSON.stringify(
  loadHrEmployeePreferences(storage, 7, ["hr:employee:list"], allowedColumns)
)), JSON.parse(JSON.stringify(saved)))

const key = preferenceKey(7, ["hr:employee:list"])
storage.setItem(key, "{broken")
assert.deepStrictEqual(JSON.parse(JSON.stringify(
  loadHrEmployeePreferences(storage, 7, ["hr:employee:list"], allowedColumns)
)), { advancedFilterOpen: false, selectedOptionalColumns: [], pageSize: 10 })
assert.strictEqual(storage.getItem(key), null)

console.log("hrEmployeePreferences tests passed")
