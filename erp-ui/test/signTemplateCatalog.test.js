const assert = require("assert")

const {
  DEFAULT_TEMPLATE_TYPE_OPTIONS,
  decorateTemplateTypeOption,
  isInternalTemplateType,
  isLaborEmploymentTemplateType,
  isPostLevelScopedTemplateType,
  isSalaryVersionTemplateType,
  isServiceEmploymentTemplateType
} = require("../src/views/oa/signPackage/signTemplateCatalog")

const optionByCode = new Map(DEFAULT_TEMPLATE_TYPE_OPTIONS.map(option => [option.code, option]))

assert.strictEqual(DEFAULT_TEMPLATE_TYPE_OPTIONS.length, 28,
  "the fallback catalog should retain every supported signing template type")
assert.strictEqual(optionByCode.size, DEFAULT_TEMPLATE_TYPE_OPTIONS.length,
  "template codes must remain unique")

;[
  "RENEWAL_LABOR_CONTRACT",
  "RENEWAL_SERVICE_CONTRACT",
  "RENEWAL_SALARY_CONFIRM"
].forEach(code => {
  assert.strictEqual(optionByCode.get(code).scenario, "renewal",
    `${code} should remain available to renewal plans`)
})

;[
  "ONBOARD_APPLICATION_FORM",
  "ONBOARD_BACKGROUND_CHECK",
  "ONBOARD_ARCHIVE_CATALOG"
].forEach(code => {
  assert.strictEqual(isInternalTemplateType(code), true)
  assert.strictEqual(optionByCode.get(code).employeeVisible, false,
    `${code} should remain hidden from employees`)
})

assert.strictEqual(optionByCode.get("ONBOARD_HANDBOOK").employeeSignRequired, false)
assert.strictEqual(optionByCode.get("ONBOARD_HANDBOOK").readConfirmationRequired, true,
  "the handbook should still require reading without requiring a signature")

assert.deepStrictEqual(
  optionByCode.get("ONBOARD_SALARY_CONFIRM").requiredPlaceholders,
  [
    "employeeName",
    "employeeIdCard",
    "baseSalary",
    "postSalary",
    "fieldAllowance",
    "performanceSalary",
    "salaryTotal",
    "signDate"
  ],
  "salary confirmation placeholders should match imported salary structure files"
)

;[
  "ONBOARD_LABOR_CONTRACT",
  "ONBOARD_OFFER_NOTICE",
  "ONBOARD_COMMITMENT",
  "ONBOARD_POST_DUTY",
  "ONBOARD_HANDBOOK_RECEIPT",
  "ONBOARD_SALARY_CONFIRM",
  "ONBOARD_CONFIDENTIAL_NONCOMPETE",
  "RENEWAL_LABOR_CONTRACT"
].forEach(code => assert.strictEqual(isLaborEmploymentTemplateType(code), true))
assert.strictEqual(isLaborEmploymentTemplateType("ONBOARD_SERVICE_CONTRACT"), false)

;[
  "ONBOARD_SERVICE_CONTRACT",
  "ONBOARD_SERVICE_RECEIPT",
  "RENEWAL_SERVICE_CONTRACT"
].forEach(code => assert.strictEqual(isServiceEmploymentTemplateType(code), true))
assert.strictEqual(isServiceEmploymentTemplateType("ONBOARD_LABOR_CONTRACT"), false)

;[
  "ONBOARD_POST_DUTY",
  "REGULARIZE_POST_DUTY",
  "TRANSFER_POST_DUTY"
].forEach(code => assert.strictEqual(isPostLevelScopedTemplateType(code), true))
assert.strictEqual(isPostLevelScopedTemplateType("ONBOARD_COMMITMENT"), false)

;[
  "ONBOARD_SALARY_CONFIRM",
  "REGULARIZE_SALARY_CONFIRM",
  "TRANSFER_SALARY_CONFIRM",
  "RENEWAL_SALARY_CONFIRM"
].forEach(code => assert.strictEqual(isSalaryVersionTemplateType(code), true))
assert.strictEqual(isSalaryVersionTemplateType("ONBOARD_LABOR_CONTRACT"), false)

const serverOption = decorateTemplateTypeOption({
  code: "ONBOARD_ARCHIVE_CATALOG",
  employeeSignRequired: false,
  employeeVisible: true,
  readConfirmationRequired: true
})
assert.strictEqual(serverOption.employeeVisible, true,
  "explicit server metadata should continue to override fallback defaults")
assert.strictEqual(serverOption.readConfirmationRequired, true)

console.log("sign template catalog tests passed")
