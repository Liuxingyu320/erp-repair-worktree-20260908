'use strict'

const SAFE_REIMBURSEMENT_STATUSES = new Set([
  'succeeded',
  'partial',
  'corrected'
])
const TECHNICAL_ERROR_PATTERN =
  /(?:sql(?:syntax|state)?|exception|stack\s*trace|select\s+.+\s+from|insert\s+into|update\s+\w+\s+set|delete\s+from|java\.|org\.spring|com\.erp|\/users\/|\/var\/|\.java:\d+|\bat\s+\w+[.$])/i

function parseMoneyCents(value) {
  if (value === undefined || value === null || value === '') return null
  const normalized = String(value).trim().replace(/,/g, '')
  if (!/^\d+(?:\.\d{0,2})?$/.test(normalized)) return null
  const [whole, fraction = ''] = normalized.split('.')
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
  return Number.isSafeInteger(cents) ? cents : null
}

function formatMoneyCents(value) {
  const cents = Number(value)
  if (!Number.isSafeInteger(cents) || cents < 0) return '¥0.00'
  const whole = Math.floor(cents / 100)
  const fraction = String(cents % 100).padStart(2, '0')
  return `¥${whole}.${fraction}`
}

function formatMoney(value) {
  const cents = parseMoneyCents(value)
  return formatMoneyCents(cents == null ? 0 : cents)
}

function normalizeMoneyInput(value) {
  const cents = parseMoneyCents(value)
  if (cents == null) return ''
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`
}

function sumMoneyCents(values) {
  return (Array.isArray(values) ? values : []).reduce((total, value) => {
    const cents = parseMoneyCents(value)
    return total + (cents == null ? 0 : cents)
  }, 0)
}

function normalizeFormForSnapshot(form) {
  const source = form && typeof form === 'object' ? form : {}
  return {
    reimbursementId: source.reimbursementId == null
      ? null
      : String(source.reimbursementId),
    title: String(source.title || '').trim(),
    purpose: String(source.purpose || '').trim(),
    items: (Array.isArray(source.items) ? source.items : []).map(item => ({
      expenseType: String(item && item.expenseType || '').trim(),
      expenseDate: String(item && item.expenseDate || '').trim(),
      merchantName: String(item && item.merchantName || '').trim(),
      description: String(item && item.description || '').trim(),
      claimedAmount: normalizeMoneyInput(item && item.claimedAmount),
      sourceInvoiceId: item && item.sourceInvoiceId == null
        ? null : String(item.sourceInvoiceId)
    })),
    invoices: (Array.isArray(source.invoices) ? source.invoices : []).map(invoice => ({
      invoiceId: invoice && invoice.invoiceId == null
        ? null
        : String(invoice.invoiceId),
      invoiceNumber: String(invoice && invoice.invoiceNumber || '').trim(),
      invoiceDate: String(invoice && invoice.invoiceDate || '').trim(),
      sellerName: String(invoice && invoice.sellerName || '').trim(),
      sellerTaxNo: String(invoice && invoice.sellerTaxNo || '').trim(),
      invoiceTotalAmount: normalizeMoneyInput(invoice && invoice.invoiceTotalAmount),
      taxAmount: normalizeMoneyInput(invoice && invoice.taxAmount),
      commoditySummary: String(invoice && invoice.commoditySummary || '').trim()
    }))
  }
}

function serializeReimbursementForm(form) {
  return JSON.stringify(normalizeFormForSnapshot(form))
}

function validateReimbursementSubmission(form) {
  const source = form && typeof form === 'object' ? form : {}
  if (!String(source.title || '').trim()) return '请输入报销标题'
  if (!String(source.purpose || '').trim()) return '请输入报销事由'
  const items = Array.isArray(source.items) ? source.items : []
  if (!items.length) return '请至少填写一条费用明细'
  const invalidItem = items.some(item =>
    !item || !String(item.expenseType || '').trim() ||
    !String(item.expenseDate || '').trim() ||
    !String(item.description || '').trim() ||
    parseMoneyCents(item.claimedAmount) == null ||
    parseMoneyCents(item.claimedAmount) <= 0
  )
  if (invalidItem) return '请完整填写每条费用明细'
  const invoices = Array.isArray(source.invoices) ? source.invoices : []
  if (!invoices.length) return '请至少上传一个发票文件'
  if (invoices.some(invoice =>
    !SAFE_REIMBURSEMENT_STATUSES.has(
      String(invoice && invoice.recognitionStatus || '').toLowerCase()
    )
  )) {
    return '存在尚未识别或核对的发票，请先完成发票识别'
  }
  return ''
}

function reimbursementErrorMessage(error) {
  const response = error && error.response
  const responseData = response && response.data
  const status = Number(response && response.status) ||
    Number(responseData && responseData.code) ||
    Number(error && error.code) ||
    0
  const candidate = responseData && responseData.msg ||
    error && (error.msg || error.message)
  const message = String(candidate || '').replace(/\s+/g, ' ').trim()
  if (status === 401 || /(?:令牌|token).*(?:为空|无效|过期)|登录状态.*(?:失效|过期)/i.test(message)) {
    return '登录状态已失效，请重新登录'
  }
  if (status === 403) {
    return '当前账号无权执行此操作'
  }
  if (!message) return '操作失败，请稍后重试'
  if (status >= 500) {
    return '服务暂时不可用，请稍后重试'
  }
  if (message.length > 120 || TECHNICAL_ERROR_PATTERN.test(message)) {
    return '操作未完成，请稍后重试；如仍失败请联系管理员'
  }
  if (/network error|timeout|failed to fetch/i.test(message)) {
    return '网络连接不稳定，请检查网络后重试'
  }
  return message
}

function stableApprovalRequestId(taskId, action) {
  return `OA_REIMBURSEMENT:${String(taskId || '')}:${String(action || '')}:v1`
}

function mergeReimbursementRows(current, incoming) {
  const result = []
  const seen = new Set()
  ;(Array.isArray(current) ? current : [])
    .concat(Array.isArray(incoming) ? incoming : [])
    .forEach(row => {
      const key = row && row.reimbursementId == null
        ? ''
        : String(row.reimbursementId)
      if (!key || seen.has(key)) return
      seen.add(key)
      result.push(row)
    })
  return result
}

module.exports = {
  formatMoney,
  formatMoneyCents,
  mergeReimbursementRows,
  normalizeMoneyInput,
  parseMoneyCents,
  reimbursementErrorMessage,
  serializeReimbursementForm,
  stableApprovalRequestId,
  sumMoneyCents,
  validateReimbursementSubmission
}
