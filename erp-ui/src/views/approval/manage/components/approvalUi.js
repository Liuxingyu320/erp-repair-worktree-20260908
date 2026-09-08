export function unwrapData(response) {
  let value = response
  if (value && value.data !== undefined) value = value.data
  if (value && value.data !== undefined && !Array.isArray(value.rows) && !Array.isArray(value.list)) value = value.data
  return value === undefined || value === null ? {} : value
}

export function unwrapRows(response) {
  const value = unwrapData(response)
  if (Array.isArray(value)) return { rows: value, total: value.length }
  const rows = Array.isArray(value.rows) ? value.rows : Array.isArray(value.list) ? value.list : []
  const total = Number(value.total)
  return { rows, total: Number.isFinite(total) ? total : rows.length }
}

export function formatApprovalLoadError(error, fallback = '审批服务暂不可用，请稍后重试') {
  const response = error && error.response && typeof error.response === 'object' ? error.response : {}
  const data = response.data && typeof response.data === 'object' ? response.data : {}
  const headers = response.headers && typeof response.headers === 'object' ? response.headers : {}
  const responseStatus = Number(response.status)
  const errorCode = Number(error && error.code)
  const rawStatus = Number.isFinite(responseStatus) && responseStatus >= 400
    ? responseStatus
    : Number.isFinite(errorCode) && errorCode >= 400
      ? errorCode
      : response.status
  const status = Number(rawStatus)
  const header = name => headers[name] || (typeof headers.get === 'function' ? headers.get(name) : '')
  const requestId = data.requestId || data.traceId || header('x-request-id') || header('x-trace-id') || ''
  const rawMessage = error && error.message ? String(error.message).trim() : ''
  const message = rawMessage && rawMessage !== '系统未知错误，请反馈给管理员'
    ? rawMessage
    : fallback
  const diagnostic = [
    Number.isFinite(status) && status > 0 ? `状态码：${status}` : '',
    requestId ? `请求标识：${String(requestId).trim()}` : ''
  ].filter(Boolean).join(' · ')
  return {
    message,
    status: Number.isFinite(status) && status > 0 ? status : '',
    requestId: String(requestId || '').trim(),
    diagnostic
  }
}

export function entityId(entity, keys) {
  const source = entity || {}
  const candidates = Array.isArray(keys) ? keys : [keys]
  for (const key of candidates) {
    if (source[key] !== undefined && source[key] !== null && String(source[key]).trim()) return source[key]
  }
  return undefined
}

export function toArray(value) {
  return Array.isArray(value) ? value : []
}

export function statusType(status) {
  const value = String(status || '').toUpperCase()
  if (['ACTIVE', 'PUBLISHED', 'APPROVED', 'SUCCESS', 'SUCCEEDED', 'PASSED', 'DONE'].includes(value)) return 'success'
  if (['DRAFT', 'RUNNING', 'WAITING', 'PENDING', 'PROCESSING', 'RETRY', 'COMPLETING', 'RETURNING', 'REJECTING', 'WITHDRAWING', 'TERMINATING'].includes(value)) return 'warning'
  if (['FAILED', 'DEAD', 'REJECTED', 'TERMINATED', 'INVALIDATED', 'ERROR'].includes(value)) return 'danger'
  return 'info'
}

export function statusLabel(status) {
  const labels = {
    ACTIVE: '启用', DISABLED: '停用', DRAFT: '草稿', PUBLISHED: '已发布',
    RUNNING: '审批中', WAITING: '等待中', COMPLETING: '完成回调中', RETURNING: '退回回调中', REJECTING: '拒绝回调中',
    WITHDRAWING: '撤回回调中', TERMINATING: '终止回调中',
    APPROVED: '已通过', RETURNED: '已退回', REJECTED: '已拒绝', WITHDRAWN: '已撤回',
    TERMINATED: '已终止', INVALIDATED: '已失效', PENDING: '待处理', PROCESSING: '处理中',
    NONE: '无需回调', SUCCESS: '成功', SUCCEEDED: '成功', RETRY: '待重试', FAILED: '失败',
    DEAD: '死信', PASSED: '通过', ERROR: '错误', WARNING: '警告',
    START: '发起', APPROVE: '同意', RETURN: '退回', REJECT: '拒绝', WITHDRAW: '撤回',
    TERMINATE: '终止', REASSIGN: '改派', REASSIGNED: '已改派', SKIP: '跳过', SKIPPED: '已跳过',
    CANCELLED: '已取消', CALLBACK: '业务回调', CALLBACK_REPLAY: '回调重试',
    COMPLETE: '完成', DONE: '已完成', CREATE: '创建', UPDATE: '更新', SUBMIT: '提交',
    INVALIDATE: '失效', REPLAY: '重试'
  }
  const key = String(status || '').toUpperCase()
  return labels[key] || (key ? '未知状态' : '-')
}

export function businessCodeLabel(code) {
  const labels = {
    OA_PURCHASE: '采购审批',
    OA_REIMBURSEMENT: '报销审批',
    INV_TRANSFER: '调拨审批',
    INV_STOCK_CHECK: '盘点审批',
    HR_HEALTH_CERTIFICATE: '健康证审批'
  }
  return labels[String(code || '').toUpperCase()] || (code ? '其他审批业务' : '-')
}

export function businessSubtypeLabel(value) {
  const key = String(value || '').trim().toLowerCase()
  const labels = {
    all: '全部子类型',
    warehouse: '门店要货',
    store_return: '门店返仓',
    cross_store: '门店调货',
    store: '门店调货',
    oe: 'OE 补货',
    expense: '费用报销'
  }
  return labels[key] || (key ? '其他子类型' : '全部子类型')
}

export function validationIssueLabel(value) {
  const labels = {
    RULE_SAME_PRECISION_CONFLICT: '同精度规则冲突',
    RULE_OVERLAPPING_AREA_CONFLICT: '区域规则范围重叠',
    CALLBACK_ADAPTER_MISSING: '业务回调未配置',
    CONDITION_ORDER_INVALID: '条件顺序无效',
    CONDITION_INVALID: '条件配置无效',
    CONDITION_NEVER_MATCHES: '条件永远无法命中',
    NODE_EMPTY: '审批节点为空',
    NODE_ORDER_INVALID: '节点顺序无效',
    NODE_CODE_DUPLICATE: '节点编码重复',
    APPROVAL_MODE_INVALID: '多人审批方式无效',
    REQUIRED_COUNT_INVALID: '必需通过人数无效',
    MISSING_POLICY_INVALID: '缺人处理策略无效',
    SELF_POLICY_INVALID: '自审处理策略无效',
    NODE_ACTION_POLICY_INVALID: '节点操作策略无效',
    CANDIDATE_RESOLVER_MISSING: '审批人解析策略未配置',
    TRANSFER_TOPOLOGY_FIXED: '调拨审批节点结构已自动修正',
    TRANSFER_NODE_FIXED: '调拨审批节点已自动修正',
    TRANSFER_MISSING_POLICY_FIXED: '调拨缺人策略已自动修正',
    TRANSFER_SELF_POLICY_FIXED: '调拨自审策略已自动修正',
    TRANSFER_APPROVAL_MODE_FIXED: '调拨多人审批方式已自动修正',
    SCOPE_WITHOUT_ACTIVE_ORG: '适用范围内没有有效组织',
    CANDIDATE_COVERAGE_MISSING: '审批人覆盖缺失',
    CANDIDATE_COVERAGE_WARNING: '审批人覆盖存在风险'
  }
  const key = String(value || '').toUpperCase()
  return labels[key] || (key ? '其他配置问题' : '-')
}
