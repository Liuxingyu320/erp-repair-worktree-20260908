function displayValue(value, fallback = "全部") {
  return value === undefined || value === null || value === "" ? fallback : value
}

export function buildExportConfirmMessage(options = {}) {
  const moduleName = displayValue(options.moduleName, "当前模块")
  const rangeLabel = displayValue(options.rangeLabel || options.scopeLabel, "当前查询结果")
  const filterLabel = displayValue(options.filterLabel, "按页面筛选条件")
  const sensitiveFields = Array.isArray(options.sensitiveFields) ? options.sensitiveFields.filter(Boolean) : []
  const lines = [
    `确认导出${moduleName}？`,
    `导出范围：${rangeLabel}`,
    `筛选条件：${filterLabel}`
  ]
  if (sensitiveFields.length) {
    lines.push(`敏感字段：${sensitiveFields.join("、")}`)
  }
  lines.push("请确认导出文件只发送给有权限查看该数据的人员。")
  return lines.join("\n")
}

export function confirmExportAction(vm, options = {}) {
  const message = buildExportConfirmMessage(options)
  if (vm && vm.$modal && vm.$modal.confirm) {
    return vm.$modal.confirm(message)
  }
  if (vm && vm.$confirm) {
    return vm.$confirm(message, "导出确认", {
      confirmButtonText: "确认导出",
      cancelButtonText: "取消",
      type: "warning"
    })
  }
  return Promise.resolve()
}

export function confirmTemplateDownload(vm, options = {}) {
  const moduleName = displayValue(options.moduleName, "导入模板")
  const message = [
    `确认下载${moduleName}？`,
    "该模板仅用于录入导入数据，请勿直接当作业务数据导出文件流转。"
  ].join("\n")
  if (vm && vm.$modal && vm.$modal.confirm) {
    return vm.$modal.confirm(message)
  }
  if (vm && vm.$confirm) {
    return vm.$confirm(message, "模板下载确认", {
      confirmButtonText: "确认下载",
      cancelButtonText: "取消",
      type: "warning"
    })
  }
  return Promise.resolve()
}
