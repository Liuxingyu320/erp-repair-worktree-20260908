export const STEP_KEYS = ["basic", "organization", "identityContact", "employment"]
export const STEP_LABELS = ["基础", "任职", "资料", "确认"]
export const STEP_FULL_LABELS = ["基础信息", "组织岗位", "身份与紧急联系人", "用工合同与社保"]

const fields = [
  { key: "employeeName", label: "姓名", step: "basic", control: "text", create: true },
  { key: "phoneNumber", label: "手机号", step: "basic", control: "sensitive-input", create: true, sensitive: true },
  { key: "expectedEntryDate", label: "预计入职日期", step: "basic", control: "date-picker", create: true },
  { key: "employeeCategory", label: "人员类别", step: "basic", control: "option-sheet", options: "employeeCategories", create: true },
  { key: "sex", label: "性别", step: "basic", control: "option-sheet", options: "sexOptions" },
  { key: "birthDate", label: "出生日期", step: "basic", control: "date-picker" },
  { key: "maritalStatus", label: "婚姻状况", step: "basic", control: "option-sheet", options: "maritalStatuses" },
  { key: "ethnicity", label: "民族", step: "basic", control: "option-sheet", options: "ethnicities" },
  { key: "remark", label: "备注", step: "basic", control: "textarea" },

  { key: "targetDeptId", label: "目标组织", step: "organization", control: "option-sheet", options: "organizations", create: true, numeric: true },
  { key: "ownerUserId", label: "入职负责人", step: "organization", control: "owner-picker", options: "owners", create: true, numeric: true },
  { key: "targetStoreId", label: "目标门店", step: "organization", control: "option-sheet", options: "stores", numeric: true },
  { key: "targetPostId", label: "目标岗位", step: "organization", control: "option-sheet", options: "posts", create: true, numeric: true },
  { key: "directSupervisorUserId", label: "直属主管", step: "organization", control: "owner-picker", options: "supervisors", numeric: true },
  { key: "jobGrade", label: "职级", step: "organization", control: "text" },

  { key: "idType", label: "证件类型", step: "identityContact", control: "option-sheet", options: "idTypes" },
  { key: "idNumber", label: "证件号码", step: "identityContact", control: "sensitive-input", sensitive: true },
  { key: "registeredResidence", label: "户口所在地", step: "identityContact", control: "sensitive-textarea", sensitive: true },
  { key: "currentAddress", label: "现居住地址", step: "identityContact", control: "sensitive-textarea", sensitive: true },
  { key: "emergencyContact", label: "紧急联系人", step: "identityContact", control: "text" },
  { key: "emergencyContactRelation", label: "与紧急联系人关系", step: "identityContact", control: "text" },
  { key: "emergencyContactPhone", label: "紧急联系人电话", step: "identityContact", control: "sensitive-input", sensitive: true },

  { key: "workLocation", label: "工作所在地", step: "employment", control: "text" },
  { key: "workCityLevel", label: "城市级别", step: "employment", control: "option-sheet", options: "workCityLevels" },
  { key: "bankName", label: "开户银行", step: "employment", control: "text" },
  { key: "bankAccount", label: "银行卡号", step: "employment", control: "sensitive-input", sensitive: true },
  { key: "contractType", label: "合同类型", step: "employment", control: "option-sheet", options: "contractTypes" },
  { key: "socialType", label: "社保类型", step: "employment", control: "option-sheet", options: "socialTypes" },
  { key: "probationPeriod", label: "试用期", step: "employment", control: "option-sheet", options: "probationPeriods" },
  { key: "legalEntity", label: "法人单位", step: "employment", control: "text" }
]

const fieldMap = fields.reduce((result, field) => {
  result[field.key] = field
  return result
}, {})

export const FORM_STEPS = STEP_KEYS.map((key, index) => ({
  key,
  label: STEP_LABELS[index],
  fields: fields.filter(field => field.step === key)
}))

export const CREATE_FIELDS = fields.filter(field => field.create).map(field => field.key)
export const SENSITIVE_FIELDS = fields.filter(field => field.sensitive).map(field => field.key)

export const MASKED_FIELD_KEYS = {
  phoneNumber: "phoneNumberMasked",
  idNumber: "idNumberMasked",
  registeredResidence: "registeredResidenceMasked",
  currentAddress: "currentAddressMasked",
  emergencyContactPhone: "emergencyContactPhoneMasked",
  bankAccount: "bankAccountMasked"
}

const MASK_PATTERN = /[*＊•●○◯◎◉◌◍◦∙]/

export function fieldStep(key) {
  return fieldMap[key] ? fieldMap[key].step : "basic"
}

export function fieldControl(key) {
  return fieldMap[key] ? fieldMap[key].control : "text"
}

export function fieldOptionKey(key) {
  return fieldMap[key] ? fieldMap[key].options || "" : ""
}

export function isMaskPlaceholder(value) {
  if (typeof value !== "string") return false
  const normalized = value.trim()
  return normalized === "已填写" || normalized === "未填写" || MASK_PATTERN.test(normalized)
}

function normalizedNumber(value) {
  if (value === "" || value === null || value === undefined) return value
  const number = Number(value)
  return Number.isFinite(number) ? number : value
}

function normalizedFieldValue(config, value) {
  return config.numeric ? normalizedNumber(value) : value
}

export function normalizePayload(model, settings = {}) {
  const source = model && typeof model === "object" ? model : {}
  const mode = settings.mode === "create" ? "create" : "edit"
  const dirty = settings.dirtySensitiveFields || {}
  const original = settings.originalModel && typeof settings.originalModel === "object"
    ? settings.originalModel
    : null
  const allowed = mode === "create" ? CREATE_FIELDS : fields.map(field => field.key)
  const payload = {}
  const fieldErrors = {}

  if (mode === "edit" && Object.prototype.hasOwnProperty.call(source, "version")) {
    payload.version = normalizedNumber(source.version)
  }

  allowed.forEach(key => {
    const config = fieldMap[key]
    if (!config || !Object.prototype.hasOwnProperty.call(source, key)) return
    if (mode === "edit" && config.sensitive && !dirty[key]) return
    const value = source[key]
    if (config.sensitive && isMaskPlaceholder(value)) {
      fieldErrors[key] = "不能提交脱敏占位符，请输入完整的新值或撤销修改。"
      return
    }
    const normalizedValue = normalizedFieldValue(config, value)
    if (mode === "edit" && !config.sensitive && original
      && Object.prototype.hasOwnProperty.call(original, key)
      && normalizedValue === normalizedFieldValue(config, original[key])) {
      return
    }
    payload[key] = normalizedValue
  })

  if (Object.keys(fieldErrors).length) {
    const error = new Error("MASK_PLACEHOLDER_REJECTED")
    error.fieldErrors = fieldErrors
    throw error
  }
  return payload
}

export function errorsByStep(fieldErrors) {
  const result = STEP_KEYS.reduce((groups, key) => {
    groups[key] = {}
    return groups
  }, {})
  const errors = fieldErrors && typeof fieldErrors === "object" ? fieldErrors : {}
  Object.keys(errors).forEach(key => {
    result[fieldStep(key)][key] = errors[key]
  })
  return result
}

export function firstErrorLocation(fieldErrors) {
  const errors = fieldErrors && typeof fieldErrors === "object" ? fieldErrors : {}
  for (let stepIndex = 0; stepIndex < FORM_STEPS.length; stepIndex += 1) {
    const field = FORM_STEPS[stepIndex].fields.find(item => errors[item.key])
    if (field) return { stepIndex, stepKey: FORM_STEPS[stepIndex].key, fieldKey: field.key }
  }
  return null
}

function missingGroupStep(group) {
  const normalized = String(group || "").toLowerCase()
  if (/identity|contact|address|emergency|证件|地址|联系人/.test(normalized)) return "identityContact"
  if (/employment|contract|social|bank|用工|合同|社保|银行/.test(normalized)) return "employment"
  if (/organization|position|post|dept|store|组织|岗位|部门|门店/.test(normalized)) return "organization"
  return "basic"
}

export function missingCountsByStep(missingFields) {
  const counts = STEP_KEYS.reduce((result, key) => {
    result[key] = 0
    return result
  }, {})
  const source = missingFields && typeof missingFields === "object" ? missingFields : {}
  Object.keys(source).forEach(group => {
    const entries = Array.isArray(source[group]) ? source[group] : []
    entries.forEach(entry => {
      const key = typeof entry === "string" ? entry : entry && (entry.key || entry.field)
      const step = key && fieldMap[key] ? fieldStep(key) : missingGroupStep(group)
      counts[step] += 1
    })
  })
  return counts
}

export function optionLabel(option) {
  if (!option || typeof option !== "object") return String(option || "")
  return option.label || option.name || option.employeeName || ""
}

export function optionValue(option) {
  if (!option || typeof option !== "object") return option
  if (Object.prototype.hasOwnProperty.call(option, "value")) return option.value
  return option.id !== undefined ? option.id : option.userId
}
