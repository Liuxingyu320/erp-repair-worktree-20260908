const SIGN_DICTIONARY_LABELS = Object.freeze({
  LABOR_CONTRACT: '劳动合同',
  SERVICE_CONTRACT: '劳务合同',
  INTERNSHIP_AGREEMENT: '实习协议',
  OUTSOURCING_CONTRACT: '外包合同',
  SOCIAL_INSURED: 'B',
  SOCIAL_UNINSURED: 'A',
  NO_SOCIAL: 'A',
  DISPATCHED: '劳务派遣',
  PENDING_CONFIRMATION: '待确认',
  STUDENT_INTERN: '在校实习生',
  RETIRED_REHIRE: '退休返聘',
  OTHER: '其他',
  COMMERCIAL_ACCIDENT: '商业意外保险',
  EMPLOYER_LIABILITY: '雇主责任险',
  VOLUNTARY_EXPECTED: '正常主动离职',
  VOLUNTARY_UNEXPECTED: '突发主动离职',
  TERMINATION: '协商解除',
  DISCIPLINARY_TERMINATION: '违纪解除',
  DISPUTED_TERMINATION: '争议解除',
  COMPLETED: '已完成',
  PENDING: '待完成',
  NOT_APPLICABLE: '不适用',
  REQUIRED: '需要执行',
  LOW: '低风险',
  NORMAL: '常规',
  MEDIUM: '关注',
  HIGH: '高风险',
  REVIEW_REQUIRED: '需重点处理',
  A: '甲版',
  B: '乙版'
})

const SIGN_PACKAGE_STATUS_LABELS = Object.freeze({
  draft: '草稿',
  pending_sign: '待签署',
  part_viewed: '已查看',
  pending_company: '待选公司和印章',
  pending_final_confirm: '待确认文件',
  signed: '已完成',
  refused: '已拒签',
  expired: '已过期',
  voided: '已撤回',
  failed: '失败'
})

const SIGN_TASK_STATUS_LABELS = Object.freeze({
  NEW: '新任务',
  VALIDATING: '校验中',
  NEEDS_DATA: '待补资料',
  DRAFT_CREATED: '草稿已生成',
  WAITING_HR_CONFIRM: '历史待确认',
  READY_TO_SEND: '待发送',
  SENDING: '发送中',
  FAILED: '发送失败',
  PENDING_SIGN: '待员工签署',
  VIEWED: '已查看未签',
  PENDING_COMPANY: '待选公司和印章',
  PENDING_FINAL_CONFIRM: '待确认文件',
  SIGNED: '已完成',
  REFUSED: '员工拒签',
  EXPIRED: '已逾期',
  CANCELLED: '已取消',
  NO_ACTION: '无需处理'
})

const ONBOARD_CONTRACT_STATE_LABELS = Object.freeze({
  NOT_FOUND: '员工资料不可用',
  STATE_CONFLICT: '状态冲突待核验',
  NOT_INITIATED: '未发起',
  NO_RECORD: '未发起',
  NOT_ELIGIBLE: '当前不适用',
  EXISTING_RECORD: '已有合同记录',
  PROCESSING: '任务处理中',
  NEEDS_DATA: '资料不完整',
  PLAN_NOT_MATCHED: '未匹配方案',
  DRAFT: '草稿未发送',
  DRAFT_CREATED: '草稿未发送',
  WAITING_HR_CONFIRM: '待HR确认',
  READY_TO_SEND: '草稿待发送',
  SENDING: '发送中',
  SEND_FAILED: '发送失败',
  FAILED: '发送失败',
  SENT: '签署中',
  PENDING_SIGN: '等待员工签名',
  VIEWED: '员工已查看未签',
  PENDING_COMPANY: '等待公司盖章',
  PENDING_FINAL_CONFIRM: '等待确认文件',
  SIGNED: '已签署',
  REFUSED: '员工拒签',
  EXPIRED: '合同已过期',
  CANCELLED: '已取消',
  REVIEW_REQUIRED: '待人工核验',
  OFFLINE_SIGNED_PENDING_ARCHIVE: '已线下签署待归档',
  THIRD_PARTY_PENDING_SYNC: '第三方合同待同步',
  UNKNOWN: '状态暂不可用'
})

const ONBOARD_CONTRACT_ACTION_LABELS = Object.freeze({
  INITIATE_ONBOARD: '首次发起',
  FIRST_ISSUE: '首次发起',
  COMPLETE_PROFILE: '补齐签约资料',
  WAIT_PROCESSING: '等待任务处理',
  CONFIRM_TASK: 'HR确认任务',
  SEND_EXISTING: '发送已有草稿',
  RETRY_TASK: '重试失败任务',
  RETRY_FAILED: '重试失败任务',
  REMIND_EMPLOYEE: '提醒员工签署',
  WAIT_COMPANY: '选择公司并盖章',
  REMIND_FINAL_CONFIRM: '提醒最终确认',
  RESOLVE_REFUSAL: '处理员工拒签',
  RESOLVE_EXPIRY: '处理合同过期',
  ALREADY_SIGNED: '查看已签合同',
  MANUAL_REVIEW: '人工核验',
  REVIEW_CANCELLED: '检查已取消任务'
})

const SIGN_PACKAGE_EVENT_LABELS = Object.freeze({
  PACKAGE_CREATED: '创建签约包',
  DOCUMENT_GENERATED: '生成签约文件',
  PACKAGE_SENT: '发送签约包',
  DOCUMENT_READ_CONFIRMED: '确认阅读',
  PACKAGE_SIGN_REQUESTED: '提交首次签名',
  EMPLOYEE_INITIAL_SIGNED: '完成首次签名',
  FINAL_CONTRACT_GENERATED: '生成最终合同',
  FINAL_CONTRACT_CONFIRMED: '确认最终合同',
  PACKAGE_SIGNED: '完成签署',
  PACKAGE_REFUSED: '员工拒签',
  PACKAGE_EXPIRED: '签约过期',
  PACKAGE_VOIDED: '撤回签约包',
  PACKAGE_EXCEPTION_ACTION: '异常处置'
})

const SIGN_PACKAGE_EVENT_MESSAGES = Object.freeze({
  PACKAGE_CREATED: '已创建签约包草稿',
  DOCUMENT_GENERATED: '已生成签约文件',
  PACKAGE_SENT: '已发送给员工',
  DOCUMENT_READ_CONFIRMED: '员工已确认阅读',
  PACKAGE_SIGN_REQUESTED: '员工已提交签署',
  EMPLOYEE_INITIAL_SIGNED: '员工已完成首次手写签名',
  FINAL_CONTRACT_GENERATED: '已补充公司与印章并生成最终合同',
  FINAL_CONTRACT_CONFIRMED: '员工已确认最终合同',
  PACKAGE_SIGNED: '签约包已完成签署',
  PACKAGE_REFUSED: '员工已拒绝本次签约',
  PACKAGE_EXPIRED: '签约包已超过签署期限',
  PACKAGE_VOIDED: '签约包已撤回',
  PACKAGE_EXCEPTION_ACTION: '已记录签约异常处置'
})

const SIGN_TASK_REASON_LABELS = Object.freeze({
  MANUAL_SIGN_EXCEL_IMPORT: '签约名单导入',
  MANUAL_ONBOARD_INITIATION: '人工发起入职签约',
  EVENT_RECEIVED: '已接收业务事件',
  EVENT_REDELIVERED: '业务事件重新投递',
  DRAFT_CREATED: '签约草稿已生成',
  DRAFT_READY: '签约草稿已就绪',
  REVIEW_NOT_REQUIRED: '当前流程无需审核',
  SEND_REQUESTED: '已发起发送',
  MANUAL_SEND: '人工发送',
  BATCH_SEND: '批量发送',
  BATCH_SEND_RETRY: '批量发送重试',
  PACKAGE_CREATED: '签约包已创建',
  PACKAGE_PREPARED: '签约包已就绪',
  DOCUMENT_GENERATED: '签约文件已生成',
  PACKAGE_SENT: '签约包已发送',
  EMPLOYEE_VIEWED: '员工已查看',
  EMPLOYEE_INITIAL_SIGNED: '员工已完成首次签名',
  FINAL_CONTRACT_GENERATED: '最终合同已生成',
  FINAL_CONTRACT_CONFIRMED: '员工已确认最终合同',
  PACKAGE_VOIDED: '签约包已撤回',
  CONFIRMATION_STALE: '原确认已失效',
  VALIDATION_FAILED: '资料校验失败',
  SEND_FAILED: '签约包发送失败',
  NEEDS_DATA: '签约资料不完整',
  NO_ACTION: '无需签约处理',
  PLAN_NOT_FOUND: '未匹配签约方案',
  PLAN_CONFLICT: '签约方案冲突',
  PLAN_RULE_INVALID: '签约方案规则无效',
  PLAN_TEMPLATE_SNAPSHOT_MISSING: '签约模板快照缺失',
  PLAN_TEMPLATE_INVALID: '签约模板无效',
  DRAFT_DATA_MISSING: '签约草稿资料缺失',
  DECISION_INVALID: '签约决策无效',
  EMPLOYEE_CONTACTED: '已与员工沟通',
  BUSINESS_CANCELLED: '业务确认不再签约',
  OTHER_CLOSED: '其他关闭原因',
  DOCUMENT_CORRECTED: '文档或资料已修正',
  BUSINESS_UPDATED: '业务条件已变更',
  EMPLOYEE_REQUESTED: '员工申请',
  OTHER_REISSUE: '其他重新签约原因',
  BUSINESS_APPROVED: '业务已批准',
  OTHER_EXTENSION: '其他延期原因',
  CONTRACT_CONTENT_DISPUTE: '合同内容与沟通不一致',
  PERSONAL_INFORMATION_ERROR: '个人或岗位信息有误',
  COMPANY_INFORMATION_ERROR: '公司或印章信息有误',
  SIGNING_NOT_INTENDED: '员工不同意本次签约',
  SIGN_DEADLINE_ELAPSED: '已超过签署截止时间',
  OTHER: '其他原因'
})

export function signDictionaryLabel(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  const text = String(value).trim()
  const code = text.toUpperCase()
  if (SIGN_DICTIONARY_LABELS[code]) return SIGN_DICTIONARY_LABELS[code]
  const salaryVersion = text.match(/^(\d{4})[-_ ]*V(\d+)$/i)
  if (salaryVersion) return `${salaryVersion[1]}年第${salaryVersion[2]}版`
  const postLevel = text.match(/^P(\d+)$/i)
  if (postLevel) return `第${postLevel[1]}级`
  return /[A-Za-z]/.test(text) ? '未登记中文名称' : text
}

export function signPackageStatusLabel(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  return SIGN_PACKAGE_STATUS_LABELS[String(value).trim().toLowerCase()] || '未知状态'
}

export function signTaskStatusLabel(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  return SIGN_TASK_STATUS_LABELS[String(value).trim().toUpperCase()] || '未知状态'
}

export function onboardContractStateLabel(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  return ONBOARD_CONTRACT_STATE_LABELS[String(value).trim().toUpperCase()] || '未知合同状态'
}

export function onboardContractActionLabel(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  return ONBOARD_CONTRACT_ACTION_LABELS[String(value).trim().toUpperCase()] || '人工检查'
}

export function signPackageEventLabel(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  return SIGN_PACKAGE_EVENT_LABELS[String(value).trim().toUpperCase()] || '签约状态更新'
}

export function signPackageEventMessage(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  return SIGN_PACKAGE_EVENT_MESSAGES[String(value).trim().toUpperCase()] || '签约状态已更新'
}

export function signTaskReasonLabel(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  const text = String(value).trim()
  const label = SIGN_TASK_REASON_LABELS[text.toUpperCase()]
  if (label) return label
  return /[A-Za-z]/.test(text) ? '业务原因已记录' : text
}

export {
  ONBOARD_CONTRACT_ACTION_LABELS,
  ONBOARD_CONTRACT_STATE_LABELS,
  SIGN_DICTIONARY_LABELS,
  SIGN_PACKAGE_EVENT_LABELS,
  SIGN_PACKAGE_EVENT_MESSAGES,
  SIGN_PACKAGE_STATUS_LABELS,
  SIGN_TASK_REASON_LABELS,
  SIGN_TASK_STATUS_LABELS
}
