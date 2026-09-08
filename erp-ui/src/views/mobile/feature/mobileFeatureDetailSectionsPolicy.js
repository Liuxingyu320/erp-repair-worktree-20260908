const {
  approvalNodeStateLabel,
  approvalRoundStateLabel,
  deptTypeLabel,
  shipmentStatusLabel,
  unifiedApprovalStatusLabel
} = require("./mobileFeatureStatusPolicy")
const {
  canViewCost,
  firstText,
  firstValue,
  formatDateTimeText,
  formatMoneyText,
  formatQuantity,
  formatSignedQuantity,
  hasValue,
  joinDetail,
  safeText,
  toNumber,
  unitText
} = require("./mobileFeatureValuePolicy")

function buildMobileDetailSections(featureKey, row, options) {
  const sections = []
  const detailRows = row && Array.isArray(row.details) ? row.details : []
  const productRows = row && Array.isArray(row.products) ? row.products : []
  const shipmentRows = row && Array.isArray(row.shipments) ? row.shipments : []
  const salaryItemRows = row && Array.isArray(row.items) ? row.items : []
  const shopScopeRows = row && Array.isArray(row.shopScopeRows) ? row.shopScopeRows : []
  const stockCheckApprovalTrack = row && Array.isArray(row.approvalTrack) ? row.approvalTrack : []
  const customerServiceRows = row && Array.isArray(row.serviceRecords) ? row.serviceRecords : []
  const hasUnifiedApproval = Boolean(row && (row.unifiedApprovalDetail || row.unifiedApprovalLoadError))

  if (["stockCheck", "replenishment", "transfer", "transferApproval", "transferRecords"].indexOf(featureKey) > -1 && hasUnifiedApproval) {
    sections.push.apply(sections, buildUnifiedApprovalSections(row))
  }

  if (!hasUnifiedApproval && ["replenishment", "transfer", "transferApproval", "transferRecords"].indexOf(featureKey) > -1) {
    sections.push.apply(sections, buildTransferApprovalSections(row && row.approvalTrack))
  }

  if (featureKey === "customer" && customerServiceRows.length) {
    sections.push({
      title: "历史服务记录",
      rows: customerServiceRows.map(mapCustomerServiceSectionRow)
    })
  }

  if (detailRows.length) {
    sections.push({
      title: resolveDetailSectionTitle(featureKey),
      rows: detailRows.map(function(detail) {
        return mapDetailSectionRow(detail, featureKey, options)
      })
    })
  }

  if (productRows.length) {
    sections.push({
      title: "供货商品",
      rows: productRows.map(mapProductSectionRow)
    })
  }

  if (shipmentRows.length) {
    sections.push({
      title: "发货批次",
      rows: shipmentRows.map(mapShipmentSectionRow)
    })
  }

  if (salaryItemRows.length) {
    sections.push({
      title: "薪资档位",
      rows: salaryItemRows.map(mapSalaryItemSectionRow)
    })
  }

  if (shopScopeRows.length) {
    sections.push({
      title: "授权门店/仓库",
      rows: shopScopeRows.map(mapShopScopeSectionRow)
    })
  }

  if (!hasUnifiedApproval && featureKey === "stockCheck" && stockCheckApprovalTrack.length) {
    sections.push({
      title: "审批轨迹",
      rows: stockCheckApprovalTrack.map(mapStockCheckApprovalSectionRow)
    })
  }

  return sections
}

function buildUnifiedApprovalSections(row) {
  if (row && row.unifiedApprovalLoadError) {
    return [{
      title: "统一审批轨迹",
      rows: [{
        title: "审批轨迹加载失败",
        meta: "主单详情已保留，请稍后重试",
        extra: "",
        value: "加载失败"
      }]
    }]
  }
  const detail = row && row.unifiedApprovalDetail
  if (!detail || typeof detail !== "object") return []
  const instance = detail.instance || {}
  const tasks = Array.isArray(detail.tasks) ? detail.tasks : []
  const candidates = Array.isArray(detail.candidates) ? detail.candidates : []
  const taskRows = tasks.map(function(task) {
    const source = task || {}
    const taskCandidates = Array.isArray(source.candidates)
      ? source.candidates
      : candidates.filter(function(candidate) {
        return candidate && String(candidate.taskId) === String(source.taskId)
      })
    const candidateNames = taskCandidates.map(function(candidate) {
      return firstText(candidate, ["candidateName", "displayName", "userName", "nickName", "candidateUserName"])
    }).filter(Boolean)
    const fallbackCandidates = Array.isArray(source.candidateUserNames)
      ? source.candidateUserNames.filter(Boolean)
      : safeText(source.candidateUserNames || source.candidateNames, "")
    const operator = firstText(source, ["operatorName", "assigneeName"])
    const status = String(source.taskStatus || source.status || "").toUpperCase()
    return {
      title: String(source.nodeOrder || "-") + ". " + safeText(source.nodeName, "审批节点"),
      meta: operator
        ? "实际处理人：" + operator
        : "候选审批人：" + (candidateNames.length ? candidateNames.join("、") : fallbackCandidates || "待配置"),
      extra: firstText(source, ["completedTime", "completeTime"]),
      value: unifiedApprovalStatusLabel(status),
      current: status === "PENDING"
    }
  })
  const sections = [{
    title: "统一审批轨迹 · " + unifiedApprovalStatusLabel(instance.status),
    rows: taskRows.length ? taskRows : [{ title: "暂无审批节点记录", meta: "", extra: "", value: unifiedApprovalStatusLabel(instance.status) }]
  }]
  const actions = Array.isArray(detail.actions) ? detail.actions : []
  if (actions.length) {
    sections.push({
      title: "统一审批操作记录",
      rows: actions.map(function(action) {
        return {
          title: firstText(action, ["operatorName"]) || "系统",
          meta: firstText(action, ["actionReason", "reason"]) || "无备注",
          extra: firstText(action, ["createTime"]),
          value: unifiedApprovalStatusLabel(action.actionType || action.action)
        }
      })
    })
  }
  return sections
}

function transferApprovalSummaryParts(row) {
  const summary = row && row.approvalSummary
  if (!summary || typeof summary !== "object") return []
  const names = Array.isArray(summary.currentCandidateDisplayNames)
    ? summary.currentCandidateDisplayNames.filter(Boolean)
    : []
  const visibleNames = names.slice(0, 2)
  const candidateCount = Number(summary.currentCandidateCount || names.length)
  const candidates = visibleNames.length
    ? "候选：" + visibleNames.join("、") + (candidateCount > visibleNames.length ? "等" + candidateCount + "人" : "")
    : ""
  return [safeText(summary.summaryText, ""), candidates]
}

function buildTransferApprovalSections(approvalTrack) {
  if (!approvalTrack || typeof approvalTrack !== "object") return []
  if (approvalTrack.loadError) {
    return [{
      title: "审批进度",
      rows: [{
        title: "审批进度加载失败",
        meta: "主单详情已保留，请稍后重试",
        extra: "",
        value: "加载失败"
      }]
    }]
  }

  const rounds = Array.isArray(approvalTrack.rounds)
    ? approvalTrack.rounds.slice().sort((a, b) => Number(b.roundNo || 0) - Number(a.roundNo || 0))
    : []
  return rounds.map(function(round, index) {
    const nodes = Array.isArray(round.nodes) ? round.nodes : []
    return {
      title: index === 0 ? "审批进度" : "历史审批 · 第 " + round.roundNo + " 轮",
      rows: nodes.length
        ? nodes.map(mapTransferApprovalSectionRow)
        : [{ title: "本轮无人工审批节点", meta: "", extra: "", value: approvalRoundStateLabel(round.status) }]
    }
  })
}

function mapTransferApprovalSectionRow(node) {
  const candidateNames = Array.isArray(node.candidateDisplayNames)
    ? node.candidateDisplayNames.filter(Boolean)
    : []
  const actualApprover = safeText(node.actualApproverDisplayName, "")
  return {
    title: String(node.nodeOrder || "-") + ". " + safeText(node.nodeName, "审批节点"),
    meta: actualApprover
      ? "实际审批人：" + actualApprover + formatHandledTime(node.handledAt)
      : "候选审批人：" + (candidateNames.length ? candidateNames.join("、") : "待配置"),
    extra: joinDetail([
      safeText(node.approvalModeText, ""),
      node.comment ? "意见：" + node.comment : ""
    ]),
    value: approvalNodeStateLabel(node.state),
    current: node.state === "current"
  }
}

function formatHandledTime(handledAt) {
  const text = formatDateTimeText(handledAt)
  return text ? " · " + text : ""
}

function resolveDetailSectionTitle(featureKey) {
  const titles = {
    stockCheck: "盘点明细",
    outbound: "发货明细",
    replenishment: "补货明细",
    transfer: "调拨明细",
    transferApproval: "调拨明细",
    transferRecords: "调拨明细"
  }
  return titles[featureKey] || "商品明细"
}

function mapDetailSectionRow(row, featureKey, options) {
  const productName = firstText(row, ["productName", "goodsName", "name"])
  const productCode = firstText(row, ["productCode", "skuCode", "code"])
  const unit = firstText(row, ["unit"])
  const quantity = firstValue(row, ["quantity", "noticeQty", "checkQuantity", "bookQuantity", "bookQty"])
  const actualQuantity = firstValue(row, ["actualQuantity", "actualQty"])
  const deliveredQuantity = firstValue(row, ["deliveredQuantity", "deliveredQty"])
  const receivedQuantity = firstValue(row, ["receivedQuantity", "receivedQty"])
  const remainingQuantity = firstValue(row, ["remainingQuantity", "remainingQty"])
  const diffQuantity = firstValue(row, ["diffQuantity", "differenceQuantity", "diffQty"])
  const costPrice = firstValue(row, ["costPrice", "referenceCostPrice"])
  const amount = resolveDetailAmount(row, quantity)
  const extraParts = []
  const costVisible = canViewCost(options) ||
    ["replenishment", "transfer", "transferApproval", "transferRecords"].indexOf(featureKey) > -1
  const amountIsCostSensitive = ["purchase", "purchaseReturn", "replenishment", "transfer", "transferRecords", "stockCheck"].indexOf(featureKey) > -1 ||
    hasValue(costPrice)

  if (hasValue(actualQuantity)) extraParts.push("实盘 " + formatQuantity(actualQuantity) + unitText(unit))
  if (hasValue(deliveredQuantity)) extraParts.push("已发 " + formatQuantity(deliveredQuantity) + unitText(unit))
  if (hasValue(receivedQuantity)) extraParts.push("已收 " + formatQuantity(receivedQuantity) + unitText(unit))
  if (hasValue(remainingQuantity)) extraParts.push("剩余 " + formatQuantity(remainingQuantity) + unitText(unit))
  if (hasValue(diffQuantity)) extraParts.push("差异 " + formatSignedQuantity(diffQuantity) + unitText(unit))
  if (costVisible && hasValue(costPrice)) extraParts.push("参考成本价 " + formatMoneyText(costPrice))
  if (hasValue(amount) && (!amountIsCostSensitive || costVisible)) extraParts.push("小计 " + formatMoneyText(amount))

  return {
    title: safeText(productName, "商品明细"),
    meta: joinDetail([productCode, firstText(row, ["spec", "model"])]),
    value: hasValue(quantity) ? formatQuantity(quantity) + unitText(unit) : "-",
    extra: extraParts.join(" · ")
  }
}

function mapCustomerServiceSectionRow(row) {
  const partySize = firstValue(row, ["partySize"])
  const amount = firstValue(row, ["consumptionAmount"])
  return {
    title: safeText(firstText(row, ["teaServed"]), "到店服务"),
    meta: joinDetail([
      formatDateTimeText(firstText(row, ["serviceDate"])),
      firstText(row, ["serviceUserName", "createBy"])
    ]),
    value: hasValue(amount) ? formatMoneyText(amount) : (hasValue(partySize) ? String(partySize) + " 人" : "-"),
    extra: joinDetail([
      hasValue(partySize) ? String(partySize) + " 人" : "",
      firstText(row, ["serviceNote"]),
      firstText(row, ["cautionSnapshot"])
    ])
  }
}

function mapStockCheckApprovalSectionRow(instance) {
  const task = instance && Array.isArray(instance.tasks) && instance.tasks.length
    ? instance.tasks[instance.tasks.length - 1]
    : {}
  const approvalLabels = {
    running: "审批中",
    approved: "已通过",
    rejected: "已驳回",
    invalidated: "库存已变化",
    cancelled: "已取消"
  }
  const selfApproval = String(task.selfApproved || "0") === "1" ? "自审" : ""
  const detailSnapshotItems = parseStockCheckApprovalSnapshot(
    instance && instance.detailSnapshotItems,
    instance && instance.detailSnapshot
  )
  const invalidDetailSnapshotItems = parseStockCheckApprovalSnapshot(
    instance && instance.invalidDetailSnapshotItems,
    instance && instance.invalidDetailSnapshot
  )
  const adjustmentResultItems = parseStockCheckApprovalSnapshot(
    instance && instance.adjustmentResultItems,
    instance && instance.adjustmentResultSnapshot
  )
  return {
    title: "第 " + (instance.roundNo || "-") + " 轮 · " + (approvalLabels[String(instance.status || "").toLowerCase()] || "未知状态"),
    meta: joinDetail([
      instance.submittedBy ? "提交 " + instance.submittedBy : "",
      task.approverName ? "审批 " + task.approverName : ""
    ]),
    value: selfApproval || (task.status ? approvalLabels[String(task.status).toLowerCase()] || "未知状态" : "-"),
    extra: joinDetail([
      selfApproval,
      task.candidateUserNames ? "候选 " + task.candidateUserNames : "",
      task.approvalComment ? "意见 " + task.approvalComment : "",
      summarizeStockCheckEvidence("提交冻结", detailSnapshotItems, function(item) {
        return stockCheckEvidenceName(item) + " 账面" + formatQuantity(firstValue(item, ["bookQty", "bookQuantity"])) +
          " 实盘" + formatQuantity(firstValue(item, ["actualQty", "actualQuantity"]))
      }),
      summarizeStockCheckEvidence("失效证据", invalidDetailSnapshotItems, function(item) {
        return stockCheckEvidenceName(item) + " " + formatQuantity(firstValue(item, ["bookQuantity", "bookQty"])) +
          "→" + formatQuantity(firstValue(item, ["currentQuantity"]))
      }),
      summarizeStockCheckEvidence("调整结果", adjustmentResultItems, function(item) {
        return stockCheckEvidenceName(item) + " " + formatQuantity(firstValue(item, ["beforeQuantity"])) +
          "→" + formatQuantity(firstValue(item, ["afterQuantity"]))
      })
    ])
  }
}

function parseStockCheckApprovalSnapshot(items, snapshot) {
  if (Array.isArray(items)) return items
  if (!snapshot || typeof snapshot !== "string") return []
  try {
    const parsed = JSON.parse(snapshot)
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    return []
  }
}

function summarizeStockCheckEvidence(label, rows, formatter) {
  if (!rows.length) return ""
  return label + "：" + rows.map(formatter).join("；")
}

function stockCheckEvidenceName(item) {
  return firstText(item, ["productName", "productCode"]) || "商品" + safeText(firstValue(item, ["productId", "detailId"]), "-")
}

function resolveTransferTotalAmount(row) {
  const totalAmount = firstValue(row, ["totalAmount", "amount"])
  if (hasValue(totalAmount)) return totalAmount
  const rows = row && Array.isArray(row.details) ? row.details : []
  if (!rows.length) return null

  let hasAmount = false
  const total = rows.reduce(function(sum, detail) {
    const amount = toNumber(resolveDetailAmount(detail, firstValue(detail, ["quantity", "noticeQty", "checkQuantity", "bookQuantity"])))
    if (amount === null) return sum
    hasAmount = true
    return sum + amount
  }, 0)

  return hasAmount ? Number(total.toFixed(2)) : null
}

function resolveTransferTotalQuantity(row) {
  const topLevelQuantity = toNumber(firstValue(row, [
    "totalQuantity",
    "quantity",
    "applyQuantity",
    "requestedQuantity"
  ]))
  if (topLevelQuantity !== null) return topLevelQuantity

  const rows = row && Array.isArray(row.details) ? row.details : []
  let hasQuantity = false
  const total = rows.reduce(function(sum, detail) {
    const quantity = toNumber(firstValue(detail, [
      "quantity",
      "applyQuantity",
      "requestedQuantity",
      "noticeQty"
    ]))
    if (quantity === null) return sum
    hasQuantity = true
    return sum + quantity
  }, 0)

  return hasQuantity ? Number(total.toFixed(2)) : null
}

function resolveDetailAmount(row, quantity) {
  const amount = firstValue(row, ["amount", "lineAmount"])
  if (hasValue(amount)) return amount
  const quantityValue = toNumber(quantity)
  const costPrice = toNumber(firstValue(row, ["costPrice", "referenceCostPrice"]))
  if (quantityValue === null || costPrice === null) return null
  return Number((quantityValue * costPrice).toFixed(2))
}

function mapProductSectionRow(row) {
  const price = firstValue(row, ["purchasePrice", "costPrice", "salePrice500g", "salesPrice"])
  return {
    title: safeText(firstText(row, ["productName", "goodsName", "name"]), "供货商品"),
    meta: joinDetail([firstText(row, ["productCode", "skuCode", "code"]), firstText(row, ["categoryName"])]),
    value: formatMoneyText(price) || "-",
    extra: joinDetail([firstText(row, ["spec", "model"]), firstText(row, ["unit"])])
  }
}

function mapShipmentSectionRow(row) {
  return {
    title: safeText(firstText(row, ["shipmentNo", "batchNo", "code"]), "发货批次"),
    meta: joinDetail([shipmentStatusLabel(firstText(row, ["status"])), formatDateTimeText(firstText(row, ["shippedTime", "deliveredTime"]))]),
    value: firstText(row, ["shippedBy", "deliveredBy"]) || "-",
    extra: joinDetail([firstText(row, ["receivedBy"]), formatDateTimeText(firstText(row, ["receivedTime"]))])
  }
}

function mapSalaryItemSectionRow(row) {
  return {
    title: safeText(firstText(row, ["itemName", "salaryItemName", "name"]), "薪资档位"),
    meta: joinDetail([firstText(row, ["itemType", "type"]), firstText(row, ["roleName"])]),
    value: formatMoneyText(firstValue(row, ["amount", "salaryAmount", "baseSalary"])) || "-",
    extra: joinDetail([firstText(row, ["conditionText", "condition"]), firstText(row, ["remark"])])
  }
}

function mapShopScopeSectionRow(row) {
  return {
    title: safeText(firstText(row, ["deptName"]), "授权组织"),
    meta: joinDetail([deptTypeLabel(firstText(row, ["deptType"])), firstText(row, ["deptFullPath"])]),
    value: firstText(row, ["leader"]) || "-",
    extra: joinDetail([firstText(row, ["phone"]), firstText(row, ["email"])])
  }
}

module.exports = {
  buildMobileDetailSections,
  resolveTransferTotalAmount,
  resolveTransferTotalQuantity,
  transferApprovalSummaryParts
}
