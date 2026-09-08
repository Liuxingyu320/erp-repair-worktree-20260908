const MAX_SIGN_TASK_EXPORT_ROWS = 500

function signTaskExportBlockReason(total) {
  const count = Number(total)
  if (!Number.isFinite(count) || count <= 0) {
    return '当前筛选条件下没有可导出的签约任务'
  }
  if (count > MAX_SIGN_TASK_EXPORT_ROWS) {
    return `当前筛选结果共 ${count} 条，单次最多导出 ${MAX_SIGN_TASK_EXPORT_ROWS} 条，请缩小筛选条件后分批导出`
  }
  return ''
}

module.exports = {
  MAX_SIGN_TASK_EXPORT_ROWS,
  signTaskExportBlockReason
}
