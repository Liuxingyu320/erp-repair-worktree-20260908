const INTERNAL_TEMPLATE_TYPES = Object.freeze([
  "ONBOARD_APPLICATION_FORM",
  "ONBOARD_BACKGROUND_CHECK",
  "ONBOARD_ARCHIVE_CATALOG"
])

const LABOR_EMPLOYMENT_TEMPLATE_TYPES = Object.freeze([
  "ONBOARD_LABOR_CONTRACT",
  "ONBOARD_OFFER_NOTICE",
  "ONBOARD_HANDBOOK",
  "ONBOARD_COMMITMENT",
  "ONBOARD_POST_DUTY",
  "ONBOARD_HANDBOOK_RECEIPT",
  "ONBOARD_SALARY_CONFIRM",
  "ONBOARD_CONFIDENTIAL_NONCOMPETE",
  "RENEWAL_LABOR_CONTRACT"
])

const SERVICE_EMPLOYMENT_TEMPLATE_TYPES = Object.freeze([
  "ONBOARD_SERVICE_CONTRACT",
  "ONBOARD_SERVICE_RECEIPT",
  "RENEWAL_SERVICE_CONTRACT"
])

const POST_LEVEL_SCOPED_TEMPLATE_TYPES = Object.freeze([
  "ONBOARD_POST_DUTY",
  "REGULARIZE_POST_DUTY",
  "TRANSFER_POST_DUTY"
])

const SALARY_VERSION_TEMPLATE_TYPES = Object.freeze([
  "ONBOARD_SALARY_CONFIRM",
  "REGULARIZE_SALARY_CONFIRM",
  "TRANSFER_SALARY_CONFIRM",
  "RENEWAL_SALARY_CONFIRM"
])

function isInternalTemplateType(templateType) {
  return INTERNAL_TEMPLATE_TYPES.includes(templateType)
}

function isLaborEmploymentTemplateType(templateType) {
  return LABOR_EMPLOYMENT_TEMPLATE_TYPES.includes(templateType)
}

function isServiceEmploymentTemplateType(templateType) {
  return SERVICE_EMPLOYMENT_TEMPLATE_TYPES.includes(templateType)
}

function isPostLevelScopedTemplateType(templateType) {
  return POST_LEVEL_SCOPED_TEMPLATE_TYPES.includes(templateType)
}

function isSalaryVersionTemplateType(templateType) {
  return SALARY_VERSION_TEMPLATE_TYPES.includes(templateType)
}

function decorateTemplateTypeOption(option) {
  return Object.assign({
    employeeVisible: !isInternalTemplateType(option.code),
    readConfirmationRequired: option.employeeSignRequired || option.code === "ONBOARD_HANDBOOK"
  }, option)
}

const DEFAULT_TEMPLATE_TYPE_OPTIONS = [
  {
    code: "ONBOARD_LABOR_CONTRACT",
    label: "劳动合同",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "employeePhone", "employeeAddress", "contractStartDate", "contractEndDate", "probationStartDate", "probationEndDate", "postName", "baseSalary", "signDate"]
  },
  {
    code: "ONBOARD_SERVICE_CONTRACT",
    label: "劳务合同",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "employeePhone", "employeeAddress", "servicePersonType", "contractStartDate", "contractEndDate", "postName", "baseSalary", "signDate"]
  },
  {
    code: "ONBOARD_SERVICE_RECEIPT",
    label: "劳务合同签收单",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "servicePersonType", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal", "insuranceType", "signDate"]
  },
  {
    code: "ONBOARD_COMMITMENT",
    label: "入职承诺书",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "signDate"]
  },
  {
    code: "ONBOARD_POST_DUTY",
    label: "岗位职责确认书",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "postName", "postLevel", "signDate"]
  },
  {
    code: "ONBOARD_HANDBOOK_RECEIPT",
    label: "员工手册签收确认书",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "signDate"]
  },
  {
    code: "ONBOARD_HANDBOOK",
    label: "员工手册",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: false,
    requiredPlaceholders: []
  },
  {
    code: "ONBOARD_SALARY_CONFIRM",
    label: "薪酬结构确认书",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal", "signDate"]
  },
  {
    code: "ONBOARD_APPLICATION_FORM",
    label: "应聘登记表",
    scenario: "onboard",
    fileFormat: "xlsx",
    employeeSignRequired: false,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "employeePhone", "entryDate", "postName"]
  },
  {
    code: "ONBOARD_BACKGROUND_CHECK",
    label: "背调报告",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: false,
    requiredPlaceholders: ["employeeName", "postName"]
  },
  {
    code: "ONBOARD_ARCHIVE_CATALOG",
    label: "员工档案目录",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: false,
    requiredPlaceholders: ["employeeName", "employeeDeptName", "entryDate", "postName"]
  },
  {
    code: "ONBOARD_OFFER_NOTICE",
    label: "录用通知书",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "employeePhone", "employeeAddress", "employeeDeptName", "postName", "entryDate", "probationStartDate", "probationEndDate", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal", "signDate"]
  },
  {
    code: "ONBOARD_CONFIDENTIAL_NONCOMPETE",
    label: "保密与竞业限制协议",
    scenario: "onboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "employeePhone", "employeeAddress", "postName", "postLevel", "contractStartDate", "signDate"]
  },
  {
    code: "REGULARIZE_CONFIRMATION",
    label: "转正确认书",
    scenario: "regularize",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "actualRegularizationDate", "postName", "postLevel", "signDate"]
  },
  {
    code: "REGULARIZE_POST_DUTY",
    label: "转正岗位职责确认书",
    scenario: "regularize",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "postName", "postLevel", "actualRegularizationDate", "signDate"]
  },
  {
    code: "REGULARIZE_SALARY_CONFIRM",
    label: "转正薪资确认书",
    scenario: "regularize",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "actualRegularizationDate", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal", "salaryVersion", "signDate"]
  },
  {
    code: "TRANSFER_CONFIRMATION",
    label: "调岗确认书",
    scenario: "transfer",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "transferEffectiveDate", "beforeDeptName", "afterDeptName", "beforePostName", "afterPostName", "signDate"]
  },
  {
    code: "TRANSFER_POST_DUTY",
    label: "调岗岗位职责确认书",
    scenario: "transfer",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "afterDeptName", "afterPostName", "postLevel", "transferEffectiveDate", "signDate"]
  },
  {
    code: "TRANSFER_SALARY_CONFIRM",
    label: "调岗薪资确认书",
    scenario: "transfer",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "transferEffectiveDate", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal", "salaryVersion", "signDate"]
  },
  {
    code: "RENEWAL_LABOR_CONTRACT",
    label: "劳动合同续签协议",
    scenario: "renewal",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "employeePhone", "employeeAddress", "companyName", "contractStartDate", "contractEndDate", "postName", "baseSalary", "signDate"]
  },
  {
    code: "RENEWAL_SERVICE_CONTRACT",
    label: "劳务协议续签书",
    scenario: "renewal",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "employeePhone", "employeeAddress", "companyName", "servicePersonType", "contractStartDate", "contractEndDate", "postName", "baseSalary", "signDate"]
  },
  {
    code: "RENEWAL_SALARY_CONFIRM",
    label: "续签薪酬确认书",
    scenario: "renewal",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "contractStartDate", "contractEndDate", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary", "salaryTotal", "salaryVersion", "signDate"]
  },
  {
    code: "OFFBOARD_CONFIRMATION",
    label: "离职确认书",
    scenario: "offboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "entryDate", "leaveDate", "offboardingType", "leaveReason", "signDate"]
  },
  {
    code: "OFFBOARD_HANDOVER",
    label: "离职交接确认书",
    scenario: "offboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "leaveDate", "postName", "assetHandoverStatus", "signDate"]
  },
  {
    code: "OFFBOARD_SETTLEMENT",
    label: "离职结算确认书",
    scenario: "offboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "leaveDate", "salarySettlementStatus", "compensationAmount", "compensationNote", "signDate"]
  },
  {
    code: "OFFBOARD_CONFIDENTIALITY_NONCOMPETE",
    label: "离职保密与竞业确认书",
    scenario: "offboard",
    fileFormat: "docx",
    employeeSignRequired: true,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "leaveDate", "nonCompeteDecision", "signDate"]
  },
  {
    code: "OFFBOARD_TERMINATION_NOTICE",
    label: "解除终止通知书",
    scenario: "offboard",
    fileFormat: "docx",
    employeeSignRequired: false,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "entryDate", "leaveDate", "offboardingType", "leaveReason", "companyName", "signDate"]
  },
  {
    code: "OFFBOARD_LEAVE_CERTIFICATE",
    label: "离职证明",
    scenario: "offboard",
    fileFormat: "docx",
    employeeSignRequired: false,
    requiredPlaceholders: ["employeeName", "employeeIdCard", "entryDate", "leaveDate", "postName", "companyName", "signDate"]
  }
].map(decorateTemplateTypeOption)

module.exports = {
  DEFAULT_TEMPLATE_TYPE_OPTIONS,
  decorateTemplateTypeOption,
  isInternalTemplateType,
  isLaborEmploymentTemplateType,
  isPostLevelScopedTemplateType,
  isSalaryVersionTemplateType,
  isServiceEmploymentTemplateType
}
