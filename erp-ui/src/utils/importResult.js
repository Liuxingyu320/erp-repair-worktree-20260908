function formatCount(value) {
  const count = Number(value)
  return Number.isFinite(count) && count >= 0 ? count : 0
}

function formatError(item) {
  if (item == null) return ''
  if (typeof item !== 'object') return String(item)

  const row = item.row != null ? item.row : item.rowNumber
  const message = item.message || item.reason || item.error || ''
  if (row != null && message) return `第 ${row} 行：${message}`
  if (message) return String(message)

  try {
    return JSON.stringify(item)
  } catch (error) {
    return String(item)
  }
}

export function formatImportResult(response) {
  const payload = response && typeof response === 'object' ? response : {}
  const result = payload.data && typeof payload.data === 'object' ? payload.data : null

  if (result && (result.successCount != null || result.createdCount != null || result.failureCount != null)) {
    const successCount = result.committed === false
      ? 0
      : formatCount(result.successCount != null
        ? result.successCount
        : formatCount(result.createdCount) + formatCount(result.updatedCount))
    const lines = [
      `导入完成：成功 ${successCount} 条，失败 ${formatCount(result.failureCount)} 条`
    ]
    const errors = Array.isArray(result.errors) ? result.errors : result.failures
    if (Array.isArray(errors)) {
      lines.push(...errors.map(formatError).filter(Boolean))
    }
    return lines.join('\n')
  }

  return String(payload.msg || '导入完成').replace(/<br\s*\/?>/gi, '\n')
}

export function plainImportLines(message) {
  return String(message || "")
    .replace(/<br\s*\/?\s*>/gi, "\n")
    .replace(/<[^>]*>/g, "")
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
}

export function parseLegacyImportResult(message) {
  const lines = plainImportLines(message)
  const success = lines.join(" ").match(/成功(?:导入)?\s*(\d+)\s*条/)
  const failures = lines.map(text => {
    const match = text.match(/第\s*(\d+)\s*行[：:]?\s*(.*)/)
    return match ? { line: Number(match[1]), reason: match[2] || "导入失败" } : null
  }).filter(Boolean)
  return { successCount: success ? Number(success[1]) : 0, failureCount: failures.length, failures, lines }
}

export function parseImportResult(response) {
  const payload = response && typeof response === "object" ? response : {}
  const result = payload.data && typeof payload.data === "object" ? payload.data : null
  if (!result || (result.createdCount == null && result.updatedCount == null && result.failureCount == null)) {
    return parseLegacyImportResult(typeof response === "string" ? response : payload.msg)
  }
  const failures = (Array.isArray(result.failures) ? result.failures : []).map(item => ({
    line: Number(item.rowNumber != null ? item.rowNumber : item.row) || 0,
    userName: item.userName == null ? "" : String(item.userName),
    reason: item.message || item.reason || "导入失败"
  }))
  const committed = result.committed !== false
  const temporaryCredentials = committed && Array.isArray(result.temporaryCredentials)
    ? result.temporaryCredentials.map(item => ({
      userId: item.userId,
      userName: item.userName == null ? "" : String(item.userName),
      temporaryPassword: item.temporaryPassword == null ? "" : String(item.temporaryPassword),
      expiresAt: item.expiresAt == null ? "" : String(item.expiresAt)
    }))
    : []
  return {
    committed,
    createdCount: committed ? formatCount(result.createdCount) : 0,
    updatedCount: committed ? formatCount(result.updatedCount) : 0,
    successCount: committed ? formatCount(result.createdCount) + formatCount(result.updatedCount) : 0,
    failureCount: formatCount(result.failureCount != null ? result.failureCount : failures.length),
    failures,
    temporaryCredentials,
    lines: []
  }
}

export function neutralizeCsvCell(value) {
  const text = String(value == null ? "" : value)
  return /^[=+\-@\t\r]/.test(text.trimStart()) ? `'${text}` : text
}

export function safeCsvCell(value) {
  return `"${neutralizeCsvCell(value).replace(/"/g, '""')}"`
}

export function failureCsv(failures) {
  return ["行号,账号,原因", ...(failures || []).map(item =>
    `${Number(item.line) || ""},${safeCsvCell(item.userName)},${safeCsvCell(item.reason)}`
  )].join("\r\n")
}

export function credentialCsv(credentials) {
  return ["用户编号,登录账号,临时密码,失效时间", ...(credentials || []).map(item =>
    `${Number(item.userId) || ""},${safeCsvCell(item.userName)},${safeCsvCell(item.temporaryPassword)},${safeCsvCell(item.expiresAt)}`
  )].join("\r\n")
}
