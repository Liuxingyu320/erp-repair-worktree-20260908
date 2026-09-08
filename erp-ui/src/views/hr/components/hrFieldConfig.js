export const HR_TASKS = [
  { key: "all", label: "全部员工", countKey: "total", type: "primary" },
  { key: "complete", label: "业务必填完整", countKey: "complete", type: "success" },
  { key: "incomplete", label: "业务必填待补", countKey: "incomplete", type: "warning" },
  { key: "accountRisk", label: "账号待配置", countKey: "accountRisk", type: "danger" },
  { key: "average", label: "平均必填完成率", countKey: "average", type: "info", suffix: "%" }
]

export const DEFAULT_LIST_FIELDS = [
  "employeeName",
  "employeeNo",
  "phoneNumberMasked",
  "departmentName",
  "positionName",
  "employeeStatus",
  "employeeCategory",
  "profileCompletionPercent",
  "accountConfigurationStatus",
  "remark",
  "riskTags"
]

export const OPTIONAL_LIST_FIELDS = [
  { key: "departmentSupervisor", label: "部门主管" },
  { key: "directSupervisor", label: "直属主管" },
  { key: "contractStartDate", label: "合同起始日" },
  { key: "contractEndDate", label: "合同到期日" },
  { key: "contractType", label: "合同类型" },
  { key: "socialType", label: "社保类型" },
  { key: "socialSecurityLocation", label: "社保缴纳地" },
  { key: "housingFundLocation", label: "公积金缴纳地" },
  { key: "legalEntity", label: "法人单位" },
  { key: "recruitmentChannel", label: "招聘渠道" },
  { key: "leaveDate", label: "离职时间" }
]

export const SENSITIVE_FIELDS = [
  "salaryTotal", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary",
  "phoneNumber",
  "idNumber",
  "bankAccount",
  "registeredResidence",
  "currentAddress",
  "emergencyContactPhone",
  "officePhone"
]

export const READ_ONLY_DERIVED_FIELDS = [
  "salaryTotal", "baseSalary", "postSalary", "fieldAllowance", "performanceSalary",
  "companyName", "deptLevel1Name", "deptLevel2Name", "deptLevel3Name",
  "storeName", "positionName", "positionNames", "positionNo", "postNames", "departmentSupervisor", "workYears",
  "companyYears", "contractTerm"
]

export const COMPLETENESS_FIELDS = [
  ["employeeNo", "工号"],
  ["phoneNumber", "手机号"],
  ["idType", "证件类型"],
  ["idNumber", "证件号码"],
  ["entryDate", "入职日期"],
  ["contractType", "合同类型"],
  ["socialType", "社保类型"],
  ["contractStartDate", "合同起始"],
  ["contractEndDate", "合同到期"],
  ["emergencyContact", "紧急联系人"],
  ["emergencyContactPhone", "紧急联系人电话"],
  ["bankName", "开户银行"],
  ["bankAccount", "银行卡号"]
]

export const EDIT_REQUIRED_FIELDS = [
  { key: "employeeName", label: "姓名" },
  { key: "phoneNumber", label: "手机号" },
  { key: "employeeNo", label: "工号" },
  { key: "employeeStatus", label: "员工状态" },
  { key: "entryDate", label: "入职时间" }
]

export const DETAIL_GROUPS = [
  {
    title: "基础信息",
    fields: [
      ["employeeNo", "工号"],
      ["employeeName", "姓名"],
      ["phoneNumber", "手机号"],
      ["email", "邮箱"],
      ["sex", "性别"],
      ["birthDate", "出生日期"],
      ["ethnicity", "民族"],
      ["maritalStatus", "婚姻状况"],
      ["nationality", "国籍"],
      ["foreignNationalFlag", "是否外籍"],
      ["healthStatus", "健康状况"],
      ["politicalStatus", "政治面貌"],
      ["remark", "备注"]
    ]
  },
  {
    title: "组织岗位",
    fields: [
      ["positionNo", "岗位工号"],
      ["companyName", "所属公司"],
      ["deptLevel1Name", "一级部门"],
      ["deptLevel2Name", "二级部门"],
      ["deptLevel3Name", "三级部门"],
      ["storeName", "四级门店"],
      ["deptId", "当前部门"],
      ["postIds", "岗位"],
      ["jobGrade", "职级"],
      ["departmentSupervisor", "部门主管"],
      ["directSupervisorUserId", "直属主管"],
      ["legalEntity", "法人单位"]
    ]
  },
  {
    title: "身份户籍",
    fields: [
      ["idType", "证件类型"],
      ["idNumber", "证件号码"],
      ["bloodType", "血型"],
      ["registeredResidence", "户口所在地"],
      ["currentAddress", "现居住地址"]
    ]
  },
  {
    title: "教育信息",
    fields: [
      ["firstEducation", "第一学历"],
      ["firstDegree", "第一学位"],
      ["firstGraduationDate", "毕业时间"],
      ["firstGraduationSchool", "第一学历毕业学校"],
      ["firstMajor", "第一学历专业"],
      ["highestEducation", "最高学历"],
      ["highestDegree", "最高学位"],
      ["highestGraduationDate", "最高学历毕业时间"],
      ["highestGraduationSchool", "最高学历毕业学校"],
      ["highestMajor", "最高学历专业"]
    ]
  },
  {
    title: "用工合同",
    fields: [
      ["employeeStatus", "员工状态"],
      ["employeeCategory", "人员类别"],
      ["recruitmentChannel", "招聘渠道"],
      ["workStartDate", "参加工作时间"],
      ["workYears", "工龄"],
      ["entryDate", "入职时间"],
      ["probationPeriod", "试用期"],
      ["plannedRegularizationDate", "计划转正日期"],
      ["actualRegularizationDate", "实际转正日期"],
      ["companyYears", "司龄"],
      ["currentPositionStartDate", "本岗位任职日期"],
      ["contractStartDate", "合同起始日"],
      ["contractEndDate", "合同到期日"],
      ["contractType", "合同类型"],
      ["contractTerm", "合同期限"],
      ["renewalCount", "续签次数"],
      ["workLocation", "工作所在地"],
      ["workCityLevel", "城市级别"],
      ["attendanceMethod", "考勤方式"],
      ["leaveDate", "离职时间"]
    ]
  },
  {
    title: "社保公积金",
    fields: [
      ["householdType", "户口性质"],
      ["socialType", "社保类型"],
      ["socialSecurityLocation", "社保缴纳地"],
      ["housingFundLocation", "公积金缴纳地"]
    ]
  },
  {
    title: "联系人与银行",
    fields: [
      ["officePhone", "办公电话"],
      ["emergencyContact", "紧急联系人"],
      ["emergencyContactRelation", "与紧急联系人关系"],
      ["emergencyContactPhone", "紧急联系人电话"],
      ["bankName", "开户银行"],
      ["bankAccount", "银行卡号"]
    ]
  },
  {
    title: "入职合同工资",
    fields: [
      ["salaryTotal", "合同综合工资"],
      ["baseSalary", "底薪"],
      ["postSalary", "综合岗位津贴"],
      ["fieldAllowance", "综合驻外补贴"],
      ["performanceSalary", "月度绩效津贴"]
    ]
  },
  {
    title: "数据记录",
    fields: [
      ["accountCreateBy", "账号创建人"],
      ["accountCreateTime", "账号创建时间"],
      ["accountUpdateBy", "账号更新人"],
      ["accountUpdateTime", "账号更新时间"],
      ["profileCreateBy", "档案创建人"],
      ["profileCreateTime", "档案创建时间"],
      ["profileUpdateBy", "档案更新人"],
      ["profileUpdateTime", "档案更新时间"]
    ]
  }
]

const FIELD_ALIASES = {
  positionName: "postIds",
  positionNames: "postIds",
  postNames: "postIds",
  departmentName: "deptId",
  directSupervisor: "directSupervisorUserId"
}

const DATE_ONLY_FIELDS = new Set([
  "birthDate", "firstGraduationDate", "highestGraduationDate", "workStartDate", "entryDate",
  "plannedRegularizationDate", "actualRegularizationDate", "currentPositionStartDate",
  "contractStartDate", "contractEndDate", "leaveDate"
])

const DATE_TIME_FIELDS = new Set([
  "accountCreateTime", "accountUpdateTime", "profileCreateTime", "profileUpdateTime"
])

const DISPLAY_VALUE_MAPS = {
  sex: { "0": "男", "1": "女", "2": "未知" },
  employeeCategory: {
    FULL_TIME: "全职",
    PART_TIME: "兼职",
    INTERN: "实习生",
    LABOR_DISPATCH: "劳务派遣",
    OUTSOURCED: "外包人员"
  },
  foreignNationalFlag: { "0": "否", "1": "是" },
  status: { "0": "正常", "1": "停用" }
}

const FIELD_META = DETAIL_GROUPS.reduce((result, group, groupIndex) => {
  group.fields.forEach(field => {
    result[field[0]] = { label: field[1], title: group.title, groupIndex }
  })
  return result
}, {})

const WORKFLOW_MISSING_SOURCES = {
  employeeNo: "通过入职确认或工号生成规则处理",
  employeeStatus: "通过员工状态流程处理"
}

const DERIVED_MISSING_SOURCES = {
  companyName: "通过组织岗位配置或系统派生处理",
  deptLevel1Name: "通过组织岗位配置或系统派生处理",
  deptLevel2Name: "通过组织岗位配置或系统派生处理",
  deptLevel3Name: "通过组织岗位配置或系统派生处理",
  storeName: "通过组织岗位配置或系统派生处理",
  positionName: "通过组织岗位配置或系统派生处理",
  positionNames: "通过组织岗位配置或系统派生处理",
  postNames: "通过组织岗位配置或系统派生处理",
  departmentSupervisor: "通过组织岗位配置或系统派生处理",
  workYears: "通过基础日期或系统派生处理",
  companyYears: "通过基础日期或系统派生处理",
  contractTerm: "通过合同日期或系统派生处理"
}

function canonicalFieldKey(key) {
  return FIELD_ALIASES[key] || key
}

function businessDateParts(value) {
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) return value
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(date).reduce((result, part) => {
    if (part.type !== "literal") result[part.type] = part.value
    return result
  }, {})
  return `${parts.year}-${parts.month}-${parts.day}`
}

const IMPORT_METADATA_MARKER = /(?:Excel导入|外部(?:UserID|用户ID)|源工作表|导入批次|导入行(?:号)?|导入文件|文件哈希|导入哈希|SHA-?256)/i
const IMPORT_METADATA_SEGMENT = /^(?:Excel导入|外部(?:UserID|用户ID)|来源|源工作表|工作表|导入批次|导入行(?:号)?|导入文件(?:名)?|文件哈希|导入哈希|SHA-?256|员工类型|入职(?:日期|时间)?)\s*(?::|：|$)/i

export function minimizeHrBusinessRemark(value) {
  if (value === undefined || value === null) return ""
  const source = String(value).trim()
  if (!source || !IMPORT_METADATA_MARKER.test(source)) return source
  return source
    .split(/[;；\r\n]+/)
    .map(segment => segment.trim())
    .filter(segment => segment && !IMPORT_METADATA_SEGMENT.test(segment))
    .join("；")
}

export function profileFieldLabel(key) {
  const meta = FIELD_META[canonicalFieldKey(key)]
  return meta ? meta.label : "其他资料"
}

export function missingProfileLabels(row) {
  const keys = Array.isArray(row && row.missingRequiredFields)
    ? row.missingRequiredFields
    : (Array.isArray(row && row.missingProfileFields) ? row.missingProfileFields : [])
  const labels = keys.map(profileFieldLabel)
  return { visible: labels.slice(0, 3), remaining: Math.max(0, labels.length - 3) }
}

export function resolveMissingProfileFields(keys) {
  const result = { actionable: [], workflow: [], derived: [], unknown: [] }
  ;(Array.isArray(keys) ? keys : []).forEach(rawKey => {
    const key = canonicalFieldKey(rawKey)
    const resolutionKey = WORKFLOW_MISSING_SOURCES[rawKey] || DERIVED_MISSING_SOURCES[rawKey] ? rawKey : key
    if (WORKFLOW_MISSING_SOURCES[resolutionKey]) result.workflow.push({ key: resolutionKey, source: WORKFLOW_MISSING_SOURCES[resolutionKey] })
    else if (DERIVED_MISSING_SOURCES[resolutionKey]) result.derived.push({ key: resolutionKey, source: DERIVED_MISSING_SOURCES[resolutionKey] })
    else if (FIELD_META[key]) result.actionable.push(key)
    else result.unknown.push(rawKey)
  })
  return result
}

export function formatProfileDisplayValue(key, value) {
  if (value === undefined || value === null || value === "") return "未填写"
  const canonicalKey = canonicalFieldKey(key)
  if (canonicalKey === "remark") return minimizeHrBusinessRemark(value) || "未填写"
  if (DATE_ONLY_FIELDS.has(canonicalKey)) return businessDateParts(value) || String(value)
  if (DATE_TIME_FIELDS.has(canonicalKey)) {
    const date = value instanceof Date ? value : new Date(value)
    if (!Number.isNaN(date.getTime())) {
      return new Intl.DateTimeFormat("zh-CN", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false
      }).format(date).replace(/\//g, "-")
    }
  }
  const mapped = DISPLAY_VALUE_MAPS[canonicalKey]
  if (mapped && mapped[String(value)] !== undefined) return mapped[String(value)]
  if (Array.isArray(value)) {
    const readable = value.filter(item => typeof item === "string" && item.trim())
    return readable.length ? readable.join("、") : canonicalKey === "postIds" ? "岗位名称未配置" : "未填写"
  }
  if (typeof value === "object") return "未填写"
  return String(value)
}

export function groupMissingProfileFields(keys) {
  if (!Array.isArray(keys)) return []
  const derived = new Set(READ_ONLY_DERIVED_FIELDS)
  const grouped = new Map()
  keys.forEach(key => {
    const canonicalKey = canonicalFieldKey(key)
    const meta = FIELD_META[canonicalKey] || { label: "其他资料", title: "其他资料", groupIndex: DETAIL_GROUPS.length }
    if (!grouped.has(meta.title)) {
      grouped.set(meta.title, {
        title: meta.title,
        groupIndex: meta.groupIndex,
        actionableFields: [],
        derivedFields: []
      })
    }
    const bucket = derived.has(key) || derived.has(canonicalKey) ? "derivedFields" : "actionableFields"
    const labels = grouped.get(meta.title)[bucket]
    if (!labels.some(item => item.label === meta.label)) labels.push({ label: meta.label })
  })
  return Array.from(grouped.values())
    .sort((left, right) => left.groupIndex - right.groupIndex)
    .map(({ groupIndex, ...group }) => group)
}
