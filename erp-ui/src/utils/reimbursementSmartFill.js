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

// Capture values separately from row references: references identify local rows,
// but only the frozen payload determines the server's sortNo mapping.
function cloneReimbursementDraft(value) {
  return JSON.parse(JSON.stringify(value || {}))
}

const EXPENSE_FIELDS = ['expenseType', 'expenseDate', 'merchantName', 'description', 'claimedAmount', 'sourceInvoiceId']
function expenseSnapshot(row) {
  const value = row || {}
  return JSON.stringify(EXPENSE_FIELDS.map(key => key === 'claimedAmount'
    ? (amount(value[key]) == null ? null : Number(amount(value[key]).toFixed(2)))
    : text(value[key])))
}
function expenseListSnapshot(rows) {
  return JSON.stringify((rows || []).map(expenseSnapshot))
}
function captureReimbursementSave(form) {
  const payload = cloneReimbursementDraft(form)
  const rows = (form.items || []).map((source, index) => ({ source,
    before: cloneReimbursementDraft(source), sortNo: index + 1 }))
  payload.items = rows.map(row => ({ ...row.before, sortNo: row.sortNo }))
  return { payload, rows }
}
function mergeReimbursementSavedDraft(latest, remote, capture) {
  const payload = capture && capture.payload
  if (!payload || !remote || remote.reimbursementId == null || remote.rowVersion == null ||
      (payload.reimbursementId != null && String(payload.reimbursementId) !== String(remote.reimbursementId))) {
    throw new Error('无法确认保存记录身份，请核对最新记录')
  }
  const serverRows = remote.items || []
  if (serverRows.length !== capture.rows.length) throw new Error('保存后的费用数量不一致，请核对最新记录')
  const mapped = new Map(), ids = new Set()
  for (const row of capture.rows) {
    const candidates = serverRows.filter(value => Number(value.sortNo) === row.sortNo)
    const saved = candidates.length === 1 && candidates[0]
    if (!saved || saved.itemId == null || ids.has(String(saved.itemId)) || expenseSnapshot(saved) !== expenseSnapshot(row.before)) {
      throw new Error('无法确认保存后的费用对应关系，请核对最新记录')
    }
    ids.add(String(saved.itemId))
    if (saved.sourceInvoiceId != null && !(remote.invoices || []).some(invoice =>
      String(invoice.invoiceId) === String(saved.sourceInvoiceId) && String(invoice.itemId) === String(saved.itemId))) {
      throw new Error('保存后的发票与费用关联不一致，请核对最新记录')
    }
    mapped.set(row, saved)
  }
  const used = new Set()
  const items = (latest.items || []).map(current => {
    const matches = capture.rows.filter(row => row.source === current ||
      (row.before.itemId != null && current.itemId != null && String(row.before.itemId) === String(current.itemId)))
    if (matches.length > 1 || (matches.length && used.has(matches[0]))) throw new Error('本地费用身份重复，请核对最新记录')
    if (!matches.length) {
      if (current.itemId != null) throw new Error('本地费用来源无法确认，请核对最新记录')
      return cloneReimbursementDraft(current)
    }
    const row = matches[0], saved = mapped.get(row)
    used.add(row)
    const merged = { ...saved }
    for (const key of EXPENSE_FIELDS) {
      if (JSON.stringify(current[key]) !== JSON.stringify(row.before[key])) merged[key] = current[key]
    }
    return merged
  })
  return { ...remote, title: latest.title, purpose: latest.purpose, items }
}

// Refreshes may follow uploads/recognition without rebuilding expenses. A
// changed remote expense set must not grant a dirty old draft a new version.
function mergeReimbursementReadDraft(latest, remote, baseline, preserve) {
  if (!preserve) return remote
  if (!baseline || expenseListSnapshot(baseline.items) !== expenseListSnapshot(remote.items)) {
    throw new Error('最新记录的费用已变化，本地草稿已保留，请先核对')
  }
  if (text(baseline.title) !== text(remote.title) || text(baseline.purpose) !== text(remote.purpose)) {
    throw new Error('最新记录的标题或事由已变化，本地草稿已保留，请先核对')
  }
  const mapped = new Map()
  for (const before of baseline.items || []) {
    let matches = (remote.items || []).filter(row => before.itemId != null && String(row.itemId) === String(before.itemId))
    if (!matches.length && before.sourceInvoiceId != null) matches = (remote.items || []).filter(row => String(row.sourceInvoiceId) === String(before.sourceInvoiceId))
    if (!matches.length && before.sortNo != null) matches = (remote.items || []).filter(row => Number(row.sortNo) === Number(before.sortNo))
    if (matches.length !== 1 || expenseSnapshot(matches[0]) !== expenseSnapshot(before)) throw new Error('最新费用身份无法对应，请先核对')
    mapped.set(String(before.itemId), matches[0])
  }
  const items = (latest.items || []).map(current => {
    if (current.itemId == null) return current
    const saved = mapped.get(String(current.itemId))
    if (!saved) throw new Error('本地费用身份无法对应，请先核对')
    return { ...current, itemId: saved.itemId, sortNo: saved.sortNo, reimbursementId: saved.reimbursementId }
  })
  return { ...remote, title: latest.title, purpose: latest.purpose, items }
}

function isReimbursementVersionConflict(error) {
  return /已变化|版本|VERSION|CONFLICT/i.test(String(error && (error.businessCode || error.code) || '') + ' ' + String(error && error.message || ''))
}
function reimbursementDeleteMatches(baseline, remote, invoice) {
  if (!baseline || !remote || String(baseline.reimbursementId) !== String(remote.reimbursementId) ||
      Number(remote.rowVersion) !== Number(baseline.rowVersion) + 1 ||
      (remote.invoices || []).some(row => String(row.invoiceId) === String(invoice.invoiceId))) return false
  const expectedItems = (baseline.items || []).filter(row => invoice.itemId == null || String(row.itemId) !== String(invoice.itemId))
  const expectedInvoices = (baseline.invoices || []).filter(row => String(row.invoiceId) !== String(invoice.invoiceId))
  return text(baseline.title) === text(remote.title) && text(baseline.purpose) === text(remote.purpose) &&
    expenseListSnapshot(expectedItems) === expenseListSnapshot(remote.items) &&
    JSON.stringify(expectedItems.map(row => String(row.itemId))) === JSON.stringify((remote.items || []).map(row => String(row.itemId))) &&
    JSON.stringify(expectedInvoices.map(row => String(row.invoiceId))) === JSON.stringify((remote.invoices || []).map(row => String(row.invoiceId)))
}

// 删除结果只合并发票及其确定已删除的持久明细，不覆盖其他本地草稿字段。
function mergeReimbursementInvoiceDeletion(latest, remote, invoice) {
  if (!remote || String(remote.reimbursementId) !== String(latest.reimbursementId)) {
    throw new Error('报销记录已切换，请重新打开后核对删除结果')
  }
  const invoices = Array.isArray(remote.invoices) ? remote.invoices : []
  const deleted = !invoices.some(row => String(row.invoiceId) === String(invoice.invoiceId))
  const linkedItemRemoved = deleted && invoice.itemId != null &&
    !(remote.items || []).some(row => row.itemId != null && String(row.itemId) === String(invoice.itemId))
  return {
    ...latest,
    rowVersion: remote.rowVersion,
    invoices,
    items: (latest.items || []).filter(row => !linkedItemRemoved || row.itemId == null ||
      String(row.itemId) !== String(invoice.itemId)).map(row => deleted &&
        row.sourceInvoiceId != null && String(row.sourceInvoiceId) === String(invoice.invoiceId)
        ? { ...row, sourceInvoiceId: null } : row)
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
  cloneReimbursementDraft,
  captureReimbursementSave,
  mergeReimbursementSavedDraft,
  mergeReimbursementReadDraft,
  isReimbursementVersionConflict,
  reimbursementDeleteMatches,
  applyInvoiceToForm,
  ensureDraftForInvoiceUpload,
  hasRecognitionContent,
  mergeReimbursementInvoiceDeletion,
  inferExpenseType,
  isBlankExpenseItem,
  recoverReimbursementDeleteFailure,
  reimbursementFormSnapshot,
  reimbursementReadiness
}
