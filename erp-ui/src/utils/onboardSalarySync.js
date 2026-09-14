import { previewOnboardSignSalary, archiveOnboardSignSalary, getOnboardSignSalaryStatus } from '@/api/oa/signTask'

function businessToday() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date())
  const part = key => parts.find(value => value.type === key).value
  return `${part('year')}-${part('month')}-${part('day')}`
}
function requestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `salary_${Date.now()}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`
}

// The generation button owns this state. Retrying retains the original payroll command;
// a lost response is recovered before another write. No extra HR form is required.
export async function syncOnboardSalaryBeforeGenerate(targets, state) {
  const intentKey = JSON.stringify(targets.map(row => [String(row.batchId), String(row.rowId), String(row.version)]).sort((a, b) => a.join(':').localeCompare(b.join(':'))))
  if (state.intentKey && state.intentKey !== intentKey && state.attempts) {
    // A changed selection is a new operation. Resolve the old response only;
    // never write salaries for employees that are no longer selected.
    for (const old of state.attempts.filter(attempt => !attempt.confirmed)) {
      const result = (await getOnboardSignSalaryStatus(old.request)).data
      if (!['CONFIRMED', 'NOT_FOUND'].includes(result.status)) throw new Error('上次同步结果暂未返回，请重试')
    }
    state.attempts = null
  }
  state.intentKey = intentKey
  if (!state.attempts) {
    const groups = new Map()
    targets.forEach(row => {
      const key = String(row.batchId)
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key).push(row)
    })
    const attempts = []
    for (const [batchId, rows] of groups) {
      for (let offset = 0; offset < rows.length; offset += 100) {
        const preview = await previewOnboardSignSalary(batchId, rows.slice(offset, offset + 100))
        const pending = (preview.data || []).filter(row => !row.confirmed)
        if (!pending.length) continue
        attempts.push({ confirmed: false, request: { batchId,
          rows: pending.map(row => ({ rowId: row.rowId, version: row.version, expectedSourceId: row.expectedSourceId, expectedProfileHash: row.expectedProfileHash })),
          requestId: requestId(), effectiveDate: businessToday(), reason: '生成入职合同时同步Excel工资', confirmed: true } })
      }
    }
    state.attempts = attempts
  }
  for (const attempt of state.attempts) {
    if (attempt.confirmed) continue
    let result = (await getOnboardSignSalaryStatus(attempt.request)).data
    if (result.status === 'NOT_FOUND') {
      try { result = (await archiveOnboardSignSalary(attempt.request)).data }
      catch (error) {
        const recovered = (await getOnboardSignSalaryStatus(attempt.request)).data
        if (recovered.status !== 'CONFIRMED') throw error
        result = recovered
      }
    }
    if (result.status !== 'CONFIRMED') throw new Error('工资同步结果暂未返回，请点击生成重试')
    attempt.confirmed = true
  }
}
