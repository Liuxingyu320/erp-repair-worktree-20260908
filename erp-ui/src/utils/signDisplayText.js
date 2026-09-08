const BUSINESS_CODE_LABELS = Object.freeze({
  CONFIG_HR_MISSING: '合同签约经办人配置缺失',
  VALIDATION_FAILED: '合同资料校验失败',
  SEND_FAILED: '合同发送失败',
  PLAN_NOT_FOUND: '未找到适用的签约方案',
  PLAN_CONFLICT: '签约方案范围冲突',
  DRAFT_DATA_MISSING: '签约草稿资料不完整',
  TEMPLATE_NOT_FOUND: '未找到可用合同模板',
  TEMPLATE_FILE_INVALID: '合同模板文件不完整',
  TEMPLATE_FILE_MISSING: '合同模板文件不存在',
  TEMPLATE_HASH_MISMATCH: '合同模板文件校验不一致'
})

function signTaskNumberLabel(task) {
  if (task && task.taskId !== undefined && task.taskId !== null && String(task.taskId).trim()) {
    return `合同任务${String(task.taskId).trim()}`
  }
  return '合同任务'
}

function signBusinessNumberLabel(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  const text = String(value).trim()
    .replace(/^SP[-_]*/i, '签约-')
    .replace(/^ST[-_]*/i, '任务-')
  return /[A-Za-z]/.test(text) ? '系统业务编号' : text
}

function signVersionLabel(value, emptyLabel = '-') {
  if (value === undefined || value === null || String(value).trim() === '') return emptyLabel
  const text = String(value).trim()
    .replace(/^SP[-_]*/i, '签约-')
    .replace(/draft/ig, '草稿')
    .replace(/final/ig, '最终')
    .replace(/version/ig, '版本')
    .replace(/(^|[-_])v(\d+)/ig, '$1第$2版')
  return /[A-Za-z]/.test(text) ? '合同版本' : text
}

function signBusinessText(value, fallback = '合同任务处理失败，请刷新后重试') {
  if (value === undefined || value === null || String(value).trim() === '') return fallback
  let text = String(value).trim()
  Object.keys(BUSINESS_CODE_LABELS).forEach(code => {
    text = text.split(code).join(BUSINESS_CODE_LABELS[code])
  })
  text = text
    .replace(/Base64/ig, '图片编码')
    .replace(/SHA[-_]?256/ig, '文件校验算法')
    .replace(/PDF/ig, '文件')
    .replace(/PNG/ig, '图片')
    .replace(/DOCX/ig, '文档')
    .replace(/\bhash\b/ig, '校验值')
    .replace(/\bHR\b/ig, '合同经办人')
    .replace(/\bID\b/ig, '编号')
    .replace(/\bSystem\b/ig, '系统')
    .replace(/\baction(?:Id|Version)?\b/ig, '业务动作')
  return /[A-Za-z]/.test(text) ? fallback : text
}

module.exports = {
  BUSINESS_CODE_LABELS,
  signBusinessNumberLabel,
  signBusinessText,
  signTaskNumberLabel,
  signVersionLabel
}
