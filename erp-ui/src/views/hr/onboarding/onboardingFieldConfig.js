export const ONBOARDING_STATUS_TABS = [
  { value: "DRAFT", label: "待补资料" },
  { value: "READY", label: "待到岗" },
  { value: "CONFIRMED", label: "已入职" },
  { value: "CANCELLED", label: "已取消" }
]

export const ONBOARDING_STATUS_META = {
  DRAFT: { label: "待补资料", type: "info" },
  READY: { label: "待到岗", type: "warning" },
  CONFIRMED: { label: "已入职", type: "success" },
  CANCELLED: { label: "已取消", type: "danger" }
}

export const ONBOARDING_ACTIONS = [
  { key: "EDIT", label: "编辑资料", permission: "hr:onboarding:edit", type: "primary", plain: true },
  { key: "MARK_READY", label: "标记资料就绪", permission: "hr:onboarding:ready", type: "primary" },
  { key: "RETURN_TO_DRAFT", label: "退回草稿", permission: "hr:onboarding:return", type: "warning", plain: true },
  { key: "CONFIRM", label: "确认入职", permission: "hr:onboarding:confirm", type: "success" },
  { key: "CANCEL", label: "取消入职", permission: "hr:onboarding:cancel", type: "danger", plain: true },
  { key: "RESTORE", label: "恢复入职", permission: "hr:onboarding:restore", type: "primary" }
]

export function visibleOnboardingActions(allowedActions) {
  const allowed = new Set(Array.isArray(allowedActions) ? allowedActions : [])
  return ONBOARDING_ACTIONS.filter(action => allowed.has(action.key))
}

export function missingFieldGroups(value) {
  if (!value || typeof value !== "object") return []
  return Object.keys(value).map(group => ({
    group,
    fields: Array.isArray(value[group]) ? value[group] : []
  })).filter(item => item.fields.length)
}

function missingFieldIdentity(field) {
  if (field && typeof field === "object") return field.key || field.label || ""
  return String(field || "")
}

export function postEntryMissingFieldGroups(profileValue, onboardingValue) {
  const currentFields = new Set(missingFieldGroups(onboardingValue)
    .reduce((fields, group) => fields.concat(group.fields), [])
    .map(missingFieldIdentity)
    .filter(Boolean))
  return missingFieldGroups(profileValue).map(group => ({
    ...group,
    fields: group.fields.filter(field => !currentFields.has(missingFieldIdentity(field)))
  })).filter(group => group.fields.length)
}

export function statusMeta(status) {
  return ONBOARDING_STATUS_META[status] || { label: status ? "未知入职状态" : "未知", type: "info" }
}

export function onboardingStage(status) {
  if (status === "CONFIRMED") return 5
  if (status === "READY") return 3
  if (status === "DRAFT") return 2
  return 1
}

export function employeeInitial(value) {
  const name = String(value || "").trim()
  const cjk = name.match(/[\u3400-\u9fff]/u)
  if (cjk) return cjk[0]
  const letter = name.match(/[A-Za-z]/)
  return letter ? letter[0].toUpperCase() : "员"
}

export function missingGroupLabel(value) {
  const labels = {
    IDENTITY: "身份资料",
    ORGANIZATION: "组织岗位",
    EMPLOYMENT: "合同社保",
    CONTRACT: "合同社保",
    SOCIAL: "合同社保"
  }
  return labels[value] || "其他资料"
}

export const ACCOUNT_RISK_LABELS = {
  ACCOUNT_ALREADY_BOUND: "手机号已绑定账号",
  DUPLICATE_ACTIVE_ONBOARDING: "存在重复的有效入职单",
  ACCOUNT_CONFIGURATION_FAILED: "账号或权限配置失败",
  ROLE_CONFIGURATION_MISSING: "岗位角色配置缺失",
  DATA_SCOPE_CONFIGURATION_MISSING: "数据权限配置缺失",
  ACCOUNT_CONFIGURATION_MISSING: "账号或权限配置缺失"
}

export const READINESS_BLOCKER_LABELS = {
  DERIVED_COMPANY_MISSING: "目标组织无法识别所属公司，请检查组织层级配置",
  DERIVED_DEPARTMENT_SUPERVISOR_MISSING: "入职资料已保存；目标组织未配置部门负责人，请由管理员在组织管理中补充后刷新",
  DERIVED_STORE_MISSING: "目标门店信息未能自动生成，请重新选择目标门店",
  DERIVED_POSITION_MISSING: "目标岗位信息未能自动生成，请重新选择目标岗位",
  STATUS_NOT_READY: "当前入职单尚未进入待确认状态",
  ACTUAL_ENTRY_DATE_REQUIRED: "请填写实际入职日期"
}

export const QUICK_CREATE_FIELDS = [
  { key: "employeeName", label: "姓名", control: "input" },
  { key: "phoneNumber", label: "手机号", control: "input" },
  { key: "expectedEntryDate", label: "预计入职日期", control: "date" },
  { key: "targetDeptId", label: "目标组织", control: "select", optionSource: "organizations" },
  { key: "targetPostId", label: "目标岗位", control: "select", optionSource: "posts" },
  { key: "employeeCategory", label: "人员类别", control: "select", optionSource: "employeeCategories" },
  { key: "ownerUserId", label: "入职负责人", control: "select", optionSource: "owners" }
]

export const EDIT_STEPS = [
  {
    title: "基础信息",
    fields: [
      { key: "employeeName", label: "姓名", control: "input" },
      { key: "phoneNumber", label: "手机号", control: "sensitive" },
      { key: "expectedEntryDate", label: "预计入职日期", control: "date" },
      { key: "employeeCategory", label: "人员类别", control: "select", optionSource: "employeeCategories" },
      { key: "ownerUserId", label: "入职负责人", control: "select", optionSource: "owners" },
      { key: "sex", label: "性别", control: "select", optionSource: "sexOptions" },
      { key: "birthDate", label: "出生日期", control: "date" },
      { key: "maritalStatus", label: "婚姻状况", control: "select", optionSource: "maritalStatuses" },
      { key: "ethnicity", label: "民族", control: "select", optionSource: "ethnicities" },
      { key: "remark", label: "备注", control: "textarea" }
    ]
  },
  {
    title: "组织岗位",
    fields: [
      { key: "targetDeptId", label: "目标组织", control: "select", optionSource: "organizations" },
      { key: "targetStoreId", label: "目标门店", control: "select", optionSource: "stores" },
      { key: "targetPostId", label: "目标岗位", control: "select", optionSource: "posts" },
      { key: "directSupervisorUserId", label: "直属主管", control: "select", optionSource: "supervisors" },
      { key: "jobGrade", label: "职级", control: "input" }
    ]
  },
  {
    title: "身份与紧急联系人",
    fields: [
      { key: "idType", label: "证件类型", control: "select", optionSource: "idTypes" },
      { key: "idNumber", label: "证件号码", control: "sensitive" },
      { key: "registeredResidence", label: "户口所在地", control: "sensitive" },
      { key: "currentAddress", label: "现居住地址", control: "sensitive" },
      { key: "emergencyContact", label: "紧急联系人", control: "input" },
      { key: "emergencyContactRelation", label: "与紧急联系人关系", control: "input" },
      { key: "emergencyContactPhone", label: "紧急联系人电话", control: "sensitive" }
    ]
  },
  {
    title: "用工合同与社保",
    fields: [
      { key: "workLocation", label: "工作所在地", control: "input" },
      { key: "workCityLevel", label: "城市级别", control: "select", optionSource: "workCityLevels" },
      { key: "bankName", label: "开户银行", control: "input" },
      { key: "bankAccount", label: "银行卡号", control: "sensitive" },
      { key: "contractType", label: "合同类型", control: "select", optionSource: "contractTypes" },
      { key: "socialType", label: "社保类型", control: "select", optionSource: "socialTypes" },
      { key: "probationPeriod", label: "试用期", control: "select", optionSource: "probationPeriods" },
      { key: "legalEntity", label: "法人单位", control: "input" }
    ]
  }
]

export const DERIVED_ONBOARDING_FIELDS = [
  { key: "companyName", label: "所属公司" },
  { key: "deptLevel1Name", label: "一级部门" },
  { key: "deptLevel2Name", label: "二级部门" },
  { key: "deptLevel3Name", label: "三级部门" },
  { key: "storeName", label: "门店" },
  { key: "positionName", label: "岗位" },
  { key: "departmentSupervisor", label: "部门主管" }
]

export const SENSITIVE_ONBOARDING_FIELDS = {
  phoneNumber: "phoneNumberMasked",
  idNumber: "idNumberMasked",
  registeredResidence: "registeredResidenceMasked",
  currentAddress: "currentAddressMasked",
  emergencyContactPhone: "emergencyContactPhoneMasked",
  bankAccount: "bankAccountMasked"
}

const OPERATION_FIELD_LABELS = {
  employeeName: "姓名",
  phoneNumber: "手机号",
  email: "邮箱",
  expectedEntryDate: "预计入职日期",
  actualEntryDate: "实际入职日期",
  targetDeptId: "目标组织",
  targetStoreId: "目标门店",
  targetPostId: "目标岗位",
  employeeCategory: "人员类别",
  ownerUserId: "入职负责人",
  directSupervisorUserId: "直属主管",
  jobGrade: "职级",
  idType: "证件类型",
  idNumber: "证件号码",
  registeredResidence: "户口所在地",
  currentAddress: "现居住地址",
  emergencyContact: "紧急联系人",
  emergencyContactRelation: "紧急联系人关系",
  emergencyContactPhone: "紧急联系人电话",
  workLocation: "工作所在地",
  workCityLevel: "城市级别",
  bankName: "开户银行",
  bankAccount: "银行卡号",
  contractType: "合同类型",
  socialType: "社保类型",
  probationPeriod: "试用期",
  legalEntity: "法人单位",
  remark: "备注",
  status: "入职状态",
  employeeNo: "工号",
  linkedUserId: "员工档案",
  accountConfigurationStatus: "账号配置",
  sourceRowNumber: "导入来源"
}

function operationChangedKeys(log) {
  if (Array.isArray(log && log.changedFieldKeys)) return log.changedFieldKeys.filter(Boolean)
  const summary = String(log && log.summary || "")
  const match = summary.match(/更新字段\s*[:：]\s*(.*)$/)
  return match && match[1] ? match[1].split(",").map(item => item.trim()).filter(Boolean) : []
}

function operationFieldLabels(keys) {
  const labels = keys.map(key => OPERATION_FIELD_LABELS[key] || "其他资料")
  return Array.from(new Set(labels))
}

function operationTime(value) {
  if (!value) return "时间未记录"
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return "时间未记录"
  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).formatToParts(date).reduce((result, part) => {
    if (part.type !== "literal") result[part.type] = part.value
    return result
  }, {})
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`
}

function transitionTitle(fromStatus, toStatus) {
  if (toStatus === "CONFIRMED") return "确认入职"
  if (toStatus === "READY") return "标记资料就绪"
  if (toStatus === "CANCELLED") return "取消入职"
  if (fromStatus === "CANCELLED" && toStatus === "DRAFT") return "恢复入职"
  if (toStatus === "DRAFT") return "退回待补资料"
  return "更新入职状态"
}

export function presentOnboardingLog(log) {
  if (!log || typeof log !== "object") return null
  const type = String(log.operationType || "").toUpperCase()
  const fromStatus = log.fromStatus
  const toStatus = log.toStatus
  const base = {
    operator: log.operatorName || "系统",
    time: operationTime(log.operationTime),
    title: "记录入职操作",
    description: "入职信息已更新",
    tagType: "info"
  }
  if (type === "UPDATE") {
    const labels = operationFieldLabels(operationChangedKeys(log))
    if (!labels.length) return null
    return { ...base, title: "更新入职资料", description: `修改了${labels.join("、")}`, tagType: "primary" }
  }
  if (type === "CREATE" || type === "IMPORT_CREATE") {
    return {
      ...base,
      title: type === "IMPORT_CREATE" ? "从导入数据创建入职单" : "创建入职单",
      description: "进入“待补资料”阶段",
      tagType: "primary"
    }
  }
  if (type === "STATE_CHANGE" || type === "CONFIRM") {
    const fromLabel = statusMeta(fromStatus).label
    const toLabel = statusMeta(toStatus || (type === "CONFIRM" ? "CONFIRMED" : undefined)).label
    return {
      ...base,
      title: transitionTitle(fromStatus, toStatus || (type === "CONFIRM" ? "CONFIRMED" : undefined)),
      description: `状态由“${fromLabel}”变为“${toLabel}”`,
      tagType: toStatus === "CANCELLED" || toStatus === "DRAFT" ? "warning" : "success"
    }
  }
  return base
}

const MASK_GLYPH_PATTERN = /[*＊•●○◯◎◉◌◍◦∙]/u

export function isOnboardingMaskPlaceholder(value) {
  if (typeof value !== "string") return false
  return MASK_GLYPH_PATTERN.test(value)
}

export function onboardingEditableKeys() {
  return EDIT_STEPS.reduce((keys, step) => keys.concat(step.fields.map(field => field.key)), [])
}

export function buildOnboardingUpdatePayload(detail, model, dirtySensitiveFields, sensitiveValues) {
  const payload = { version: detail && detail.version }
  const errors = {}
  const sensitive = new Set(Object.keys(SENSITIVE_ONBOARDING_FIELDS))
  onboardingEditableKeys().forEach(key => {
    if (sensitive.has(key)) return
    const before = detail ? detail[key] : undefined
    const after = model ? model[key] : undefined
    if (before !== after) payload[key] = after
  })
  Object.keys(SENSITIVE_ONBOARDING_FIELDS).forEach(key => {
    if (!dirtySensitiveFields || !dirtySensitiveFields[key]) return
    const value = sensitiveValues ? sensitiveValues[key] : undefined
    if (isOnboardingMaskPlaceholder(value)) errors[key] = "不能提交脱敏占位符，请输入完整的新值或撤销修改。"
    else payload[key] = value
  })
  return Object.keys(errors).length ? { payload: null, errors } : { payload, errors: {} }
}

export function firstErrorLocation(fieldErrors) {
  const errors = fieldErrors && typeof fieldErrors === "object" ? fieldErrors : {}
  for (let stepIndex = 0; stepIndex < EDIT_STEPS.length; stepIndex += 1) {
    const field = EDIT_STEPS[stepIndex].fields.find(item => errors[item.key])
    if (field) return { stepIndex, fieldKey: field.key }
  }
  return null
}

export function eligibleBindCandidates(conflicts) {
  return (Array.isArray(conflicts) ? conflicts : []).filter(candidate =>
    Boolean(candidate && candidate.candidateUserId && candidate.eligibleForBind) &&
    Array.isArray(candidate.allowedDecisions) && candidate.allowedDecisions.includes("BIND_EXISTING")
  )
}

export function eligibleRehireCandidates(conflicts) {
  return (Array.isArray(conflicts) ? conflicts : []).filter(candidate =>
    Boolean(candidate && candidate.candidateUserId && candidate.eligibleForRehire) &&
    Array.isArray(candidate.allowedDecisions) && candidate.allowedDecisions.includes("REHIRE_EXISTING")
  )
}

export function preferredConflictSelection(detail, conflicts) {
  const eligible = eligibleBindCandidates(conflicts)
  const rehire = eligibleRehireCandidates(conflicts)
  const preferred = detail && detail.preferredConflictAction === "BIND_EXISTING"
    ? eligible.find(candidate => candidate.candidateUserId === detail.preferredBindUserId)
    : null
  if (preferred) return { conflictAction: "BIND_EXISTING", bindUserId: preferred.candidateUserId }
  if (!eligible.length && rehire.length === 1) return { conflictAction: "REHIRE_EXISTING", bindUserId: rehire[0].candidateUserId }
  return { conflictAction: "CREATE_NEW", bindUserId: null }
}

export function createIdempotencyKey() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID()
  const bytes = new Uint8Array(16)
  if (typeof crypto !== "undefined" && crypto.getRandomValues) crypto.getRandomValues(bytes)
  else for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, "0"))
  return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-${hex.slice(8, 10).join("")}-${hex.slice(10).join("")}`
}

export function onboardingErrorBody(error) {
  if (!error || typeof error !== "object") return {}
  if (error.response && error.response.data && typeof error.response.data === "object") return error.response.data
  if (error.data && typeof error.data === "object") return error.data
  return error
}

export function isOnboardingVersionConflict(error) {
  return onboardingErrorBody(error).errorCode === "ONBOARDING_VERSION_CONFLICT"
}

export function sanitizeConfirmResult(result) {
  if (!result || typeof result !== "object") return {}
  const safe = { ...result }
  delete safe.oneTimePassword
  delete safe.oneTimePasswordExpiresAt
  return safe
}
