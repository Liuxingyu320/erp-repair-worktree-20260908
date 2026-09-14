const assert = require("assert")
const fs = require("fs")
const path = require("path")
const vm = require("vm")

const root = path.resolve(__dirname, "..")
const read = relativePath => fs.readFileSync(path.join(root, relativePath), "utf8")
const readApi = name => read(`src/api/hr/${name}.js`)
const onboarding = readApi("onboarding")
const employee = readApi("employee")
const completeness = readApi("completeness")
const legacyImport = readApi("legacyEmployeeImport")
const employeeList = read("src/views/hr/components/HrEmployeeList.vue")

function exportBlock(source, name) {
  const marker = `export const ${name} =`
  const start = source.indexOf(marker)
  assert.ok(start >= 0, `${name} should be exported`)
  const next = source.indexOf("\nexport const ", start + marker.length)
  return source.slice(start, next < 0 ? source.length : next)
}

function assertOperation(source, name, fragments) {
  const block = exportBlock(source, name)
  const requestStart = Math.max(block.indexOf("request({"), block.indexOf("recordRequest(id, {"))
  assert.ok(requestStart >= 0, `${name} should call request with an explicit config`)
  const requestConfig = block.slice(requestStart)
  for (const fragment of fragments) {
    assert.ok(requestConfig.includes(fragment.replace("${id}", "{id}")) || requestConfig.includes(fragment), `${name} request config should include ${fragment}`)
  }
}

function namedImportBlock(source, modulePath) {
  const escapedPath = modulePath.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
  const match = source.match(new RegExp(`import\\s*{([\\s\\S]*?)}\\s*from "${escapedPath}"`))
  assert.ok(match, `consumer should import from ${modulePath}`)
  return match[1]
}

assertOperation(onboarding, "listHrOnboarding", ["/system/hr/onboarding/list", 'method: "get"', "params"])
assertOperation(onboarding, "getHrOnboardingSummary", ["/system/hr/onboarding/summary", 'method: "get"', "params"])
assertOperation(onboarding, "getHrOnboarding", ["/system/hr/onboarding/${id}", 'method: "get"'])
assertOperation(onboarding, "createHrOnboarding", ["/system/hr/onboarding", 'method: "post"', "data", "silentError: true"])
assertOperation(onboarding, "updateHrOnboarding", ["/system/hr/onboarding/${id}", 'method: "put"', "data", "silentError: true"])
assertOperation(onboarding, "markHrOnboardingReady", ["/system/hr/onboarding/${id}/ready", 'method: "post"', "data", "silentError: true"])
assertOperation(onboarding, "returnHrOnboardingToDraft", ["/system/hr/onboarding/${id}/return-to-draft", 'method: "post"', "data", "silentError: true"])
assertOperation(onboarding, "getHrOnboardingConflicts", ["/system/hr/onboarding/${id}/conflicts", 'method: "get"'])
assertOperation(onboarding, "confirmHrOnboarding", ["/system/hr/onboarding/${id}/confirm", 'method: "post"', "data", "silentError: true"])
assertOperation(onboarding, "cancelHrOnboarding", ["/system/hr/onboarding/${id}/cancel", 'method: "post"', "data", "silentError: true"])
assertOperation(onboarding, "restoreHrOnboarding", ["/system/hr/onboarding/${id}/restore", 'method: "post"', "data", "silentError: true"])
assertOperation(onboarding, "getHrOnboardingFormOptions", ["/system/hr/onboarding/form-options", 'method: "get"', "params"])
assertOperation(onboarding, "getHrOnboardingOwnerOptions", ["/system/hr/onboarding/owner-options", 'method: "get"', "params"])
assertOperation(onboarding, "previewHrOnboardingImport", [
  "/system/hr/onboarding/import/preview", 'method: "post"', "data", "timeout: 60000", "silentError: true",
  'headers: { "Content-Type": "multipart/form-data" }'
])
assertOperation(onboarding, "getHrOnboardingImportBatch", ["/system/hr/onboarding/import/${id}", 'method: "get"'])
assertOperation(onboarding, "confirmHrOnboardingImport", ["/system/hr/onboarding/import/${id}/confirm", 'method: "post"', "data", "timeout: 60000", "silentError: true"])
assertOperation(onboarding, "downloadHrOnboardingTemplate", ["/system/hr/onboarding/import/template", 'method: "get"', 'responseType: "blob"'])
assertOperation(onboarding, "downloadHrOnboardingErrorRows", ["/system/hr/onboarding/import/${id}/errors", 'method: "get"', 'responseType: "blob"'])

assert.ok(onboarding.includes('HR_ONBOARDING_TEMPLATE_URL = "system/hr/onboarding/import/template"'))
assert.ok(onboarding.includes("`system/hr/onboarding/import/${id}/errors`"))

let capturedPreviewConfig
const onboardingRuntimeSource = onboarding
  .replace(/import request from ["'][^"']+["']/, "")
  .replace(/export const /g, "const ")
const onboardingSandbox = {
  module: { exports: {} },
  require: id => { if (id === "@/utils/positiveDecimalId") return require("../src/utils/positiveDecimalId"); throw Error(id) },
  request(config) { capturedPreviewConfig = config; return config }
}
vm.runInNewContext(`${onboardingRuntimeSource}\nmodule.exports = { previewHrOnboardingImport }`, onboardingSandbox, {
  filename: "src/api/hr/onboarding.js"
})

const formData = new FormData()
formData.append("file", new Blob(["xlsx"]), "people.xlsx")
onboardingSandbox.module.exports.previewHrOnboardingImport(formData)
assert.strictEqual(capturedPreviewConfig.data, formData, "preview wrapper must pass the original FormData object to request")

const axios = require(path.join(root, "node_modules/axios"))
const previewHeaders = new axios.AxiosHeaders(capturedPreviewConfig.headers)
const previewTransformContext = { headers: previewHeaders, formSerializer: {} }
let transformed = capturedPreviewConfig.data
for (const transform of axios.defaults.transformRequest) {
  transformed = transform.call(previewTransformContext, transformed, previewHeaders)
}
assert.strictEqual(transformed, formData, "Axios 1.x must preserve onboarding FormData instead of JSON-stringifying it")
assert.strictEqual(previewHeaders.getContentType(), "multipart/form-data", "preview must override the global JSON content type")

for (const fragment of [
  "/system/hr/employee/list",
  "/system/hr/employee/summary",
  "/system/hr/employee/form-options",
  "/derived-preview",
  "/sensitive/reveal",
  "system/hr/employee/export"
]) assert.ok(employee.includes(fragment), `employee API missing ${fragment}`)

assertOperation(employee, "updateHrEmployee", ["/system/hr/employee/${userId}", 'method: "patch"', "data", "silentError: true"])
assertOperation(employee, "revealHrEmployeeSensitiveField", ["/system/hr/employee/${userId}/sensitive/reveal", 'method: "post"', "data: { fieldKey }"])
assertOperation(employee, "exportHrEmployeeSensitive", ["/system/hr/employee/export-sensitive", 'method: "post"', "data", 'responseType: "blob"'])
assert.ok(exportBlock(employee, "updateHrEmployee").includes("updateHrEmployee = (userId, data)"), "employee update wrapper should accept userId separately")
assert.ok(exportBlock(employee, "revealHrEmployeeSensitiveField").includes("revealHrEmployeeSensitiveField = (userId, fieldKey)"), "sensitive reveal wrapper should accept one field key")
assert.ok(!employee.includes("HR_SENSITIVE_EXPORT_ACTION"), "sensitive export should not expose a form-post download action")

for (const forbidden of [
  "/system/hr/onboarding",
  "/system/hr/completeness",
  "/importData",
  "/importTemplate"
]) assert.ok(!employee.includes(forbidden), `employee API should not include ${forbidden}`)

for (const fragment of [
  "/system/hr/completeness/summary",
  "/system/hr/completeness/employees",
  "/system/hr/completeness/departments"
]) assert.ok(completeness.includes(fragment), `completeness API missing ${fragment}`)

for (const fragment of [
  "LEGACY_HR_EMPLOYEE_IMPORT_ACTION",
  "LEGACY_HR_EMPLOYEE_IMPORT_TEMPLATE_ACTION",
  "/system/hr/employee/importData",
  "/system/hr/employee/importTemplate"
]) assert.ok(legacyImport.includes(fragment), `legacy employee import API missing ${fragment}`)
assert.ok(!legacyImport.includes("onboarding"), "legacy employee import must not be described as onboarding import")

const employeeImports = namedImportBlock(employeeList, "@/api/hr/employee")
const onboardingImports = namedImportBlock(employeeList, "@/api/hr/onboarding")
const completenessImports = namedImportBlock(employeeList, "@/api/hr/completeness")
const legacyImports = namedImportBlock(employeeList, "@/api/hr/legacyEmployeeImport")
assert.ok(onboardingImports.includes("listHrOnboarding"), "consumer should import onboarding list from onboarding API")
assert.ok(completenessImports.includes("listHrCompletenessEmployees"), "consumer should import completeness list from completeness API")
assert.ok(legacyImports.includes("LEGACY_HR_EMPLOYEE_IMPORT_ACTION"), "consumer should import the transitional employee import explicitly")
assert.ok(!employeeImports.includes("listHrOnboarding"), "consumer should not import onboarding from employee API")
assert.ok(!employeeImports.includes("listHrCompleteness"), "consumer should not import completeness from employee API")
assert.ok(employeeList.includes("updateHrEmployee(employeeId, frozenPayload)"), "employee save should pass the user id separately")

console.log("hrOnboardingApi tests passed")
