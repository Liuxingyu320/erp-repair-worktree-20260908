'use strict'

const READY_RECOGNITION_STATUSES = new Set([
  'succeeded',
  'partial',
  'corrected'
])

function text(value) {
  return String(value == null ? '' : value).trim()
}

function amount(value) {
  if (value === undefined || value === null || value === '') return null
  const normalized = Number(String(value).replace(/,/g, ''))
  return Number.isFinite(normalized) ? normalized : null
}

function isBlankExpenseItem(item) {
  const source = item || {}
  return !text(source.expenseType) &&
    !text(source.merchantName) &&
    !text(source.description) &&
    !text(source.sourceInvoiceId)
}

function inferExpenseType(invoice) {
  const source = invoice || {}
  const haystack = [
    source.serviceType,
    source.commoditySummary,
    source.invoiceType,
    source.sellerName
  ].map(text).join(' ')
  const rules = [
    [/酒店|宾馆|住宿|客房/, '住宿费'],
    [/餐饮|饭店|餐厅|食品|外卖|餐费/, '餐饮费'],
    [/铁路|火车|航空|机票|出租|网约|公交|地铁|停车|通行|汽油|燃油|交通/, '交通费'],
    [/差旅|出差/, '差旅费'],
    [/办公|文具|耗材|打印|复印/, '办公费'],
    [/招待|宴请/, '招待费'],
    [/通讯|通信|话费|宽带|流量/, '通讯费']
  ]
  const match = rules.find(([pattern]) => pattern.test(haystack))
  return match ? match[1] : ''
}

function hasRecognitionContent(invoice) {
  const source = invoice || {}
  return Boolean(
    text(source.invoiceDate) ||
    text(source.sellerName) ||
    text(source.commoditySummary) ||
    text(source.serviceType) ||
    text(source.invoiceType) ||
    amount(source.invoiceTotalAmount) != null
  )
}

function applyInvoiceToForm(form, invoice, options = {}) {
  const targetForm = form && typeof form === 'object' ? form : {}
  const source = invoice || {}
  if (!hasRecognitionContent(source)) {
    return { applied: false, itemIndex: -1, reason: 'no_recognition_content' }
  }
  if (!Array.isArray(targetForm.items)) targetForm.items = []

  const sourceInvoiceId = source.invoiceId == null
    ? null : source.invoiceId
  let itemIndex = Number(options.targetIndex)
  const hasTarget = options.targetIndex !== undefined &&
    Number.isInteger(itemIndex) &&
    itemIndex >= 0 &&
    itemIndex < targetForm.items.length
  if (!hasTarget) itemIndex = -1
  if (!hasTarget && sourceInvoiceId != null) {
    itemIndex = targetForm.items.findIndex(item =>
      item && item.sourceInvoiceId != null &&
      String(item.sourceInvoiceId) === String(sourceInvoiceId)
    )
  }
  if (!hasTarget && itemIndex < 0) {
    itemIndex = targetForm.items.findIndex(isBlankExpenseItem)
  }
  if (itemIndex < 0) {
    if (targetForm.items.length >= 100) {
      return { applied: false, itemIndex: -1, reason: 'item_limit' }
    }
    targetForm.items.push({
      expenseType: '',
      expenseDate: '',
      merchantName: '',
      description: '',
      claimedAmount: undefined,
      sourceInvoiceId: null
    })
    itemIndex = targetForm.items.length - 1
  }

  const target = targetForm.items[itemIndex] || {}
  const inferredType = inferExpenseType(source)
  const invoiceAmount = amount(source.invoiceTotalAmount)
  const description = text(source.commoditySummary) ||
    text(source.serviceType) ||
    text(source.invoiceType) ||
    '发票报销'

  if (text(source.invoiceDate)) {
    target.expenseDate = text(source.invoiceDate)
  }
  if (text(source.sellerName)) target.merchantName = text(source.sellerName)
  if (invoiceAmount != null) target.claimedAmount = invoiceAmount
  if (description) target.description = description.slice(0, 300)
  if (inferredType) target.expenseType = inferredType
  if (sourceInvoiceId != null) target.sourceInvoiceId = sourceInvoiceId
  targetForm.items.splice(itemIndex, 1, target)

  if (!text(targetForm.title)) {
    const titlePrefix = text(source.sellerName) || inferredType || '费用'
    targetForm.title = `${titlePrefix}报销`.slice(0, 120)
  }
  if (!text(targetForm.purpose)) targetForm.purpose = '发票费用报销'

  return {
    applied: true,
    itemIndex,
    inferredType,
    incomplete: !text(target.expenseType) ||
      !text(target.expenseDate) ||
      !text(target.description) ||
      !(amount(target.claimedAmount) > 0)
  }
}

function reimbursementReadiness(form) {
  const source = form && typeof form === 'object' ? form : {}
  const items = Array.isArray(source.items) ? source.items : []
  const invoices = Array.isArray(source.invoices) ? source.invoices : []
  const basicReady = Boolean(text(source.title) && text(source.purpose))
  const itemIssues = []
  items.forEach((item, index) => {
    const missing = []
    if (!text(item && item.expenseType)) missing.push('费用类型')
    if (!text(item && item.expenseDate)) missing.push('日期')
    if (!text(item && item.description)) missing.push('说明')
    if (!(amount(item && item.claimedAmount) > 0)) missing.push('金额')
    if (missing.length) {
      itemIssues.push(`明细 ${index + 1} 缺少${missing.join('、')}`)
    }
  })
  if (!items.length) itemIssues.push('至少添加一条费用明细')

  const recognitionIssues = invoices
    .filter(invoice => !READY_RECOGNITION_STATUSES.has(
      text(invoice && invoice.recognitionStatus).toLowerCase()
    ))
    .map(invoice =>
      `发票“${text(invoice.originalName) || '未命名文件'}”需要完成识别或核对`
    )
  const blockingIssues = []
  if (!text(source.title)) blockingIssues.push('填写报销标题')
  if (!text(source.purpose)) blockingIssues.push('填写报销事由')
  blockingIssues.push(...itemIssues)
  if (!invoices.length) blockingIssues.push('至少上传一张发票')
  blockingIssues.push(...recognitionIssues)

  const claimTotal = items.reduce((total, item) => {
    const value = amount(item && item.claimedAmount)
    return total + (value == null ? 0 : value)
  }, 0)
  const invoiceTotal = invoices.reduce((total, invoice) => {
    const value = amount(invoice && invoice.invoiceTotalAmount)
    return total + (value == null ? 0 : value)
  }, 0)
  const warnings = []
  const duplicateCount = invoices.filter(invoice =>
    text(invoice && invoice.duplicateStatus).toLowerCase() === 'warning'
  ).length
  if (duplicateCount) warnings.push(`${duplicateCount} 张发票疑似重复，请重点核对`)
  const recognizedAmountCount = invoices.filter(invoice =>
    amount(invoice && invoice.invoiceTotalAmount) != null
  ).length
  const difference = Math.round((claimTotal - invoiceTotal) * 100) / 100
  if (recognizedAmountCount && Math.abs(difference) >= 0.01) {
    warnings.push(`明细与已识别发票合计相差 ¥${Math.abs(difference).toFixed(2)}`)
  }
  const partialCount = invoices.filter(invoice =>
    text(invoice && invoice.recognitionStatus).toLowerCase() === 'partial'
  ).length
  if (partialCount) warnings.push(`${partialCount} 张发票为部分识别，建议提交前核对`)
  const missingDateCount = invoices.filter(invoice =>
    !text(invoice && invoice.invoiceDate)
  ).length
  if (missingDateCount) warnings.push(`${missingDateCount} 张发票未识别开票日期，请确认费用日期`)

  return {
    ready: blockingIssues.length === 0,
    blockingIssues,
    warnings,
    checks: [
      {
        key: 'basic',
        ready: basicReady,
        label: basicReady ? '标题和事由已完整' : '补充标题和事由'
      },
      {
        key: 'items',
        ready: itemIssues.length === 0,
        label: itemIssues.length
          ? `${itemIssues.length} 条明细待补充`
          : `${items.length} 条费用明细已完整`
      },
      {
        key: 'invoices',
        ready: invoices.length > 0,
        label: invoices.length ? `已上传 ${invoices.length} 张发票` : '上传至少一张发票'
      },
      {
        key: 'recognition',
        ready: invoices.length > 0 && recognitionIssues.length === 0,
        label: !invoices.length
          ? '上传后自动检查识别状态'
          : recognitionIssues.length
          ? `${recognitionIssues.length} 张发票待处理`
          : '发票识别状态可提交'
      }
    ],
    claimTotal,
    invoiceTotal,
    difference
  }
}

function reimbursementFormSnapshot(form) {
  const source = form || {}
  return JSON.stringify({
    reimbursementId: source.reimbursementId || null,
    rowVersion: source.rowVersion == null ? null : source.rowVersion,
    title: text(source.title),
    purpose: text(source.purpose),
    items: (Array.isArray(source.items) ? source.items : []).map(item => ({
      expenseType: text(item && item.expenseType),
      expenseDate: text(item && item.expenseDate),
      merchantName: text(item && item.merchantName),
      description: text(item && item.description),
      claimedAmount: item && item.claimedAmount == null
        ? '' : text(item.claimedAmount),
      sourceInvoiceId: item && item.sourceInvoiceId == null
        ? null : text(item.sourceInvoiceId)
    })),
    invoices: (Array.isArray(source.invoices) ? source.invoices : []).map(invoice => ({
      invoiceId: invoice && invoice.invoiceId || null,
      recognitionStatus: text(invoice && invoice.recognitionStatus),
      invoiceNumber: text(invoice && invoice.invoiceNumber),
      invoiceDate: text(invoice && invoice.invoiceDate),
      sellerName: text(invoice && invoice.sellerName),
      invoiceTotalAmount: invoice && invoice.invoiceTotalAmount == null
        ? '' : text(invoice.invoiceTotalAmount),
      duplicateStatus: text(invoice && invoice.duplicateStatus)
    }))
  })
}

function ensureDraftForInvoiceUpload(form, persistDraft) {
  if (form && form.reimbursementId != null) {
    return Promise.resolve(form)
  }
  try {
    return Promise.resolve(persistDraft(form))
  } catch (error) {
    return Promise.reject(error)
  }
}

function recoverReimbursementDeleteFailure(error, showError, refresh) {
  if (typeof showError === 'function') showError(error)
  if (typeof refresh !== 'function') return Promise.resolve()
  try {
    return Promise.resolve(refresh()).catch(() => undefined)
  } catch (ignored) {
    return Promise.resolve()
  }
}

module.exports = {
  READY_RECOGNITION_STATUSES,
  applyInvoiceToForm,
  ensureDraftForInvoiceUpload,
  hasRecognitionContent,
  inferExpenseType,
  isBlankExpenseItem,
  recoverReimbursementDeleteFailure,
  reimbursementFormSnapshot,
  reimbursementReadiness
}
