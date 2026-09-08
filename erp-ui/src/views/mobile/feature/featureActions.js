const {
  canReceivePurchase,
  canQualityCheckPurchase,
  canCancelPurchase
} = require("../../inventory/purchase/purchaseActionRules")

const ACTION_DEFINITIONS = {
  submitSales: {
    label: "提交销售",
    tone: "primary",
    confirmText: "确认提交该销售单？",
    successText: "提交成功"
  },
  editSales: {
    label: "编辑草稿",
    tone: "primary",
    behavior: "edit-form"
  },
  createDeliveryNotice: {
    label: "生成发货",
    tone: "primary",
    confirmText: "确认生成发货通知？",
    successText: "发货通知已生成"
  },
  cancelSales: {
    label: "取消销售",
    tone: "danger",
    confirmText: "确认取消该销售单？",
    successText: "已取消"
  },
  submitPurchase: {
    label: "提交采购",
    tone: "primary",
    confirmText: "确认提交该采购单？",
    successText: "提交成功"
  },
  editPurchase: {
    label: "编辑草稿",
    tone: "primary",
    behavior: "edit-form"
  },
  deletePurchaseDraft: {
    label: "删除草稿",
    tone: "danger",
    confirmText: "确认永久删除该采购草稿？删除后无法恢复。",
    successText: "草稿已删除"
  },
  qualityCheckPurchase: {
    label: "逐行质检",
    tone: "warning",
    confirmText: "请核对收货批次和每行数量，合格与让步数量才会增加库存。",
    successText: "质检已提交"
  },
  receivePurchaseAll: {
    label: "按数量收货",
    tone: "primary",
    confirmText: "将按下方数量入库并更新采购单状态，请核对仓库和明细后确认。",
    successText: "收货成功"
  },
  cancelPurchase: {
    label: "取消采购",
    tone: "danger",
    confirmText: "确认取消该采购单？",
    successText: "已取消"
  },
  submitSalesReturn: {
    label: "提交退货",
    tone: "primary",
    confirmText: "确认提交该退货单？",
    successText: "提交成功"
  },
  editSalesReturn: {
    label: "编辑草稿",
    tone: "primary",
    behavior: "edit-form"
  },
  confirmSalesReturn: {
    label: "确认销售退货入库",
    tone: "primary",
    confirmText: "确认销售退货入库？系统会增加对应门店库存并更新退货单状态。",
    successText: "退货成功"
  },
  cancelSalesReturn: {
    label: "取消退货",
    tone: "danger",
    confirmText: "确认取消该退货单？",
    successText: "已取消"
  },
  submitPurchaseReturn: {
    label: "提交退货",
    tone: "primary",
    confirmText: "确认提交该采购退货单？",
    successText: "提交成功"
  },
  editPurchaseReturn: {
    label: "编辑草稿",
    tone: "primary",
    behavior: "edit-form"
  },
  confirmPurchaseReturn: {
    label: "确认采购退货出库",
    tone: "primary",
    confirmText: "确认采购退货出库？系统会扣减对应仓库库存并更新退货单状态。",
    successText: "退货成功"
  },
  cancelPurchaseReturn: {
    label: "取消退货",
    tone: "danger",
    confirmText: "确认取消该采购退货单？",
    successText: "已取消"
  },
  deliverDeliveryNoticeAll: {
    label: "按数量发货",
    tone: "primary",
    confirmText: "将按下方数量从仓库出库并更新发货通知状态，请核对仓库和明细后确认。",
    successText: "发货成功"
  },
  cancelDeliveryNotice: {
    label: "取消发货",
    tone: "danger",
    confirmText: "确认取消该发货通知？",
    successText: "已取消"
  },
  saveStockCheckInput: {
    label: "保存实盘",
    tone: "neutral",
    confirmText: "保存本次实盘数量，稍后仍可继续修改。",
    successText: "实盘数据已保存"
  },
  submitStockCheck: {
    label: "提交盘点",
    tone: "primary",
    confirmText: "提交后，无差异盘点自动完成；有差异盘点进入运营总监审批。",
    successText: "盘点已提交"
  },
  deleteStockCheckDraft: {
    label: "删除草稿",
    tone: "danger",
    confirmText: "确认永久删除该盘点草稿？删除后无法恢复。",
    successText: "草稿已删除"
  },
  approveStockCheck: {
    label: "审批通过",
    tone: "primary",
    confirmText: "请核对差异明细后确认通过。通过后将立即调整库存。",
    acceptsComment: true,
    successText: "审批通过"
  },
  returnStockCheck: {
    label: "退回修改",
    tone: "warning",
    acceptsComment: true,
    requiresComment: true,
    commentPlaceholder: "请输入退回修改原因",
    confirmText: "退回后盘点人可修改实盘数据并重新提交。",
    successText: "已退回修改"
  },
  rejectStockCheck: {
    label: "审批驳回",
    tone: "danger",
    confirmText: "驳回后盘点人可修改实盘数据并重新提交。",
    acceptsComment: true,
    requiresComment: true,
    commentPlaceholder: "请输入驳回原因",
    successText: "已驳回，可修改后重新提交"
  },
  restartStockCheck: {
    label: "重新盘点",
    tone: "warning",
    confirmText: "重新盘点将刷新账面库存并清空已录入实盘数量。",
    successText: "库存快照已刷新"
  },
  cancelStockCheck: {
    label: "取消盘点",
    tone: "danger",
    confirmText: "确认取消该盘点单？",
    successText: "已取消"
  },
  withdrawStockCheck: {
    label: "撤回审批",
    tone: "warning",
    confirmText: "确认撤回盘点审批？系统将使用默认原因“申请人撤回盘点审批”，业务状态将在审批回调后恢复为草稿。",
    successText: "撤回请求已提交"
  },
  submitReplenishment: {
    label: "提交补货",
    tone: "primary",
    confirmText: "确认提交该补货申请？",
    successText: "提交成功"
  },
  editReplenishment: {
    label: "编辑草稿",
    tone: "primary",
    behavior: "edit-form"
  },
  deleteReplenishmentDraft: {
    label: "删除草稿",
    tone: "danger",
    confirmText: "确认永久删除该补货草稿？删除后无法恢复。",
    successText: "草稿已删除"
  },
  editTransfer: {
    label: "编辑草稿",
    tone: "primary",
    behavior: "edit-form"
  },
  submitTransferDraft: {
    label: "提交调拨",
    tone: "primary",
    confirmText: "确认提交该调拨单？",
    successText: "提交成功"
  },
  deleteTransferDraft: {
    label: "删除草稿",
    tone: "danger",
    confirmText: "确认永久删除该调拨草稿？删除后无法恢复。",
    successText: "草稿已删除"
  },
  approveTransfer: {
    label: "审批通过",
    tone: "primary",
    acceptsComment: true,
    commentPlaceholder: "审批意见（选填）",
    confirmText: "确认审批通过该调拨单？",
    successText: "审批通过"
  },
  returnTransfer: {
    label: "退回修改",
    tone: "warning",
    requiresComment: true,
    commentPlaceholder: "请输入退回修改原因",
    confirmText: "确认退回该调拨单修改？",
    successText: "已退回修改"
  },
  rejectTransfer: {
    label: "审批驳回",
    tone: "danger",
    requiresComment: true,
    commentPlaceholder: "请输入调拨驳回原因",
    confirmText: "确认驳回该调拨单？",
    successText: "审批驳回"
  },
  confirmTransferSource: {
    label: "调出店确认",
    tone: "primary",
    acceptsComment: true,
    commentPlaceholder: "有部分或全部无法调出时必填说明",
    confirmText: "请逐行确认本店实际可调数量；未确认余量会自动退回调入店，生成待重选调出店草稿。",
    successText: "调出店确认完成"
  },
  deliverTransferAll: {
    label: "按数量发货",
    tone: "primary",
    confirmText: "将按下方数量从当前调出组织库存发货，请核对调拨单和库存后确认。",
    successText: "发货成功"
  },
  receiveTransferShipment: {
    label: "确认调拨收货",
    tone: "primary",
    confirmText: "请将待收数量如实分为验收入库、拒收、残损和短少；有差异时系统会生成差异单。",
    successText: "收货成功"
  },
  handleTransferDiscrepancy: {
    label: "处理调拨差异",
    tone: "warning",
    acceptsComment: true,
    requiresComment: true,
    commentPlaceholder: "请填写差异处理依据和后续动作",
    confirmText: "差异处理会更新调拨状态，并可能触发补发、退回来源或结案，请逐项核对后确认。",
    successText: "差异已处理"
  },
  cancelTransfer: {
    label: "取消调拨",
    tone: "danger",
    confirmText: "确认取消该调拨单？",
    successText: "已取消"
  },
  withdrawTransfer: {
    label: "撤回审批",
    tone: "warning",
    confirmText: "确认撤回调拨审批？系统将使用默认原因“申请人撤回调拨审批”，业务状态将在审批回调后恢复为草稿。",
    successText: "撤回请求已提交"
  },
  submitOaPurchase: {
    label: "提交申请",
    tone: "primary",
    confirmText: "确认提交该采购申请？",
    successText: "提交成功"
  },
  editOaPurchase: {
    label: "编辑申请",
    tone: "primary",
    behavior: "edit-form"
  },
  calculateSalary: {
    label: "计算本月",
    tone: "primary",
    confirmText: "确认重新计算当前月份工资？",
    successText: "工资计算完成"
  },
  markNoticeRead: {
    label: "标为已读",
    tone: "primary",
    confirmText: "确认将该通知标记为已读？",
    successText: "已标记为已读"
  },
  markAllNoticeRead: {
    label: "全部已读",
    tone: "primary",
    confirmText: "确认将所有未读通知标记为已读？",
    successText: "已全部标记为已读"
  },
  runMonitorJob: {
    label: "执行一次",
    tone: "primary",
    confirmText: "确认立即执行该定时任务？",
    successText: "执行成功"
  },
  pauseMonitorJob: {
    label: "暂停任务",
    tone: "warning",
    confirmText: "确认暂停该定时任务？",
    successText: "暂停成功"
  },
  resumeMonitorJob: {
    label: "恢复任务",
    tone: "primary",
    confirmText: "确认恢复该定时任务？",
    successText: "恢复成功"
  },
  forceLogoutOnline: {
    label: "强退",
    tone: "danger",
    confirmText: "确认强退该在线用户？",
    successText: "强退成功"
  },
  editCustomer: {
    label: "编辑客户服务卡",
    tone: "primary",
    featureFlag: "customerServiceCard",
    behavior: "edit-form"
  },
  addCustomerServiceRecord: {
    label: "追加服务记录",
    tone: "primary",
    featureFlag: "customerServiceCard",
    behavior: "customer-record",
    successText: "服务记录已追加"
  },
  previewCustomerPhoto: {
    label: "查看客户照片",
    tone: "neutral",
    behavior: "preview-customer-photo"
  },
  deleteCustomer: {
    label: "删除客户",
    tone: "danger",
    confirmText: "确认删除该客户？",
    successText: "删除成功"
  },
  editSupplier: {
    label: "编辑供应商",
    tone: "primary",
    behavior: "edit-form"
  },
  deleteSupplier: {
    label: "删除供应商",
    tone: "danger",
    confirmText: "确认删除该供应商？",
    successText: "删除成功"
  },
  editProduct: {
    label: "编辑商品",
    tone: "primary",
    behavior: "edit-form"
  },
  deleteProduct: {
    label: "删除商品",
    tone: "danger",
    confirmText: "确认删除该商品？",
    successText: "删除成功"
  },
  editCategory: {
    label: "编辑分类",
    tone: "primary",
    behavior: "edit-form"
  },
  deleteCategory: {
    label: "删除分类",
    tone: "danger",
    confirmText: "确认删除该分类？",
    successText: "删除成功"
  },
  editSalaryScheme: {
    label: "编辑方案",
    tone: "primary",
    behavior: "edit-form"
  },
  deleteSalaryScheme: {
    label: "删除方案",
    tone: "danger",
    confirmText: "确认删除该薪资方案？",
    successText: "删除成功"
  },
  editTransferRule: {
    label: "编辑规则",
    tone: "primary",
    behavior: "edit-form"
  },
  deleteTransferRule: {
    label: "删除规则",
    tone: "danger",
    confirmText: "确认删除该调拨规则？",
    successText: "删除成功"
  }
}

const TARGET_ID_KEYS = {
  sales: ["orderId", "salesOrderId", "id"],
  purchase: ["orderId", "purchaseId", "id"],
  salesReturn: ["returnId", "salesReturnId", "id"],
  purchaseReturn: ["returnId", "purchaseReturnId", "id"],
  outbound: ["noticeId", "deliveryNoticeId", "id"],
  stockCheck: ["checkId", "stockCheckId", "id"],
  transfer: ["transferId", "id"],
  transferApproval: ["transferId", "id"],
  replenishment: ["transferId", "id"],
  fixedAssetRepair: ["repairId", "id"],
  oaPurchase: ["purchaseId", "id"],
  salary: ["salaryId", "id"],
  notice: ["noticeId", "id"],
  profile: ["userId", "id"],
  customer: ["customerId", "id"],
  supplier: ["supplierId", "id"],
  product: ["productId", "id"],
  category: ["categoryId", "id"],
  salaryScheme: ["schemeId", "id"],
  transferRules: ["ruleId", "id"],
  monitorJob: ["jobId", "id"],
  monitorOnline: ["tokenId", "id"]
}

const READ_ONLY_OR_UNAVAILABLE_FEATURES = {
  supplier: true,
  product: true,
  category: true,
  salaryScheme: true,
  transferRules: true
}

function getMobileFeatureActions(featureKey, item, options) {
  const row = getMobileActionRow(item)
  const status = getMobileActionStatus(item)
  const context = options || {}
  const actions = []

  if (!row) return actions
  if (READ_ONLY_OR_UNAVAILABLE_FEATURES[featureKey]) return actions
  if (featureKey !== "notice" && featureKey !== "monitorOnline" && !status) return actions

  if (featureKey === "sales") {
    if (status === "draft") actions.push(createAction("editSales"))
    if (status === "draft") actions.push(createAction("submitSales"))
    if (status === "submitted") actions.push(createAction("createDeliveryNotice"))
    if (status === "draft" || status === "submitted") actions.push(createAction("cancelSales"))
  }

  if (featureKey === "purchase") {
    const purchaseRow = Object.assign({}, row, { status })
    if (status === "draft") actions.push(createAction("editPurchase"))
    if (status === "draft") actions.push(createAction("submitPurchase"))
    if (status === "draft") actions.push(createAction("deletePurchaseDraft"))
    if (canQualityCheckPurchase(purchaseRow)) actions.push(createAction("qualityCheckPurchase"))
    if (canReceivePurchase(purchaseRow)) actions.push(createAction("receivePurchaseAll"))
    if (canCancelPurchase(purchaseRow)) actions.push(createAction("cancelPurchase"))
  }

  if (featureKey === "salesReturn") {
    if (status === "draft") actions.push(createAction("editSalesReturn"))
    if (status === "draft") actions.push(createAction("submitSalesReturn"))
    if (status === "submitted") actions.push(createAction("confirmSalesReturn"))
    if (status === "draft" || status === "submitted") actions.push(createAction("cancelSalesReturn"))
  }

  if (featureKey === "purchaseReturn") {
    if (status === "draft") actions.push(createAction("editPurchaseReturn"))
    if (status === "draft") actions.push(createAction("submitPurchaseReturn"))
    if (status === "submitted") actions.push(createAction("confirmPurchaseReturn"))
    if (status === "draft" || status === "submitted") actions.push(createAction("cancelPurchaseReturn"))
  }

  if (featureKey === "outbound") {
    if (status === "pending" || status === "delivering") {
      actions.push(createAction("deliverDeliveryNoticeAll"))
    }
    if (status === "pending") actions.push(createAction("cancelDeliveryNotice"))
  }

  if (featureKey === "stockCheck") {
    const canExecute = canActAsStockCheckCounter(row, context)
    if (["draft", "rejected", "returned"].indexOf(status) > -1 && canExecute) {
      actions.push(createAction("saveStockCheckInput"))
      actions.push(createAction("submitStockCheck"))
    }
    if (status === "draft") actions.push(createAction("deleteStockCheckDraft"))
    if (status === "pending_approval") {
      const nativeApproval = isNativeApprovalContext(row, context)
      if (!nativeApproval || hasUnifiedApprovalTask(row, context)) {
        actions.push(createAction("approveStockCheck"))
        if (nativeApproval) actions.push(createAction("returnStockCheck"))
        actions.push(createAction("rejectStockCheck"))
      }
      if (nativeApproval && isUnifiedApprovalApplicant(row, context)) {
        actions.push(createAction("withdrawStockCheck"))
      }
    }
    if (status === "invalidated" && canExecute) actions.push(createAction("restartStockCheck"))
    if (["draft", "rejected", "returned", "invalidated"].indexOf(status) > -1 ||
      (status === "pending_approval" && !isNativeApprovalContext(row, context))) {
      actions.push(createAction("cancelStockCheck"))
    }
  }

  if (featureKey === "replenishment") {
    if (status === "draft" && isStoreOrUnknownContext(context)) {
      actions.push(createAction("editReplenishment"))
      actions.push(createAction("submitReplenishment"))
      actions.push(createAction("deleteReplenishmentDraft"))
    }
    if (status === "submitted") {
      const nativeApproval = isNativeApprovalContext(row, context)
      if (!nativeApproval || hasUnifiedApprovalTask(row, context)) {
        actions.push(createAction("approveTransfer"))
        if (nativeApproval) actions.push(createAction("returnTransfer"))
        actions.push(createAction("rejectTransfer"))
      }
      if (nativeApproval && isUnifiedApprovalApplicant(row, context)) {
        actions.push(createAction("withdrawTransfer"))
      }
    }
    if (canReceiveTransfer(row, status, context)) {
      actions.push(createAction("receiveTransferShipment"))
    }
    if (canHandleTransferDiscrepancy(row, status, context)) {
      actions.push(createAction("handleTransferDiscrepancy"))
    }
  }

  if (featureKey === "transfer") {
    if (status === "draft" && canManageManualTransferDraft(row, context)) {
      actions.push(createAction(
        shouldEditTransferAsReplenishment(row, context) ? "editReplenishment" : "editTransfer"
      ))
      actions.push(createAction("submitTransferDraft"))
      actions.push(createAction("deleteTransferDraft"))
    }
    if (status === "submitted") {
      const nativeApproval = isNativeApprovalContext(row, context)
      if ((nativeApproval && hasUnifiedApprovalTask(row, context)) || (!nativeApproval && context.selectedDeptType === "WAREHOUSE")) {
        actions.push(createAction("approveTransfer"))
        if (nativeApproval) actions.push(createAction("returnTransfer"))
        actions.push(createAction("rejectTransfer"))
      }
      if (nativeApproval && isUnifiedApprovalApplicant(row, context)) {
        actions.push(createAction("withdrawTransfer"))
      }
    }
    if (canConfirmTransferSource(row, status, context)) {
      actions.push(createAction("confirmTransferSource"))
    }
    if (canDeliverTransfer(row, status, context)) {
      actions.push(createAction("deliverTransferAll"))
    }
    if (canReceiveTransfer(row, status, context)) {
      actions.push(createAction("receiveTransferShipment"))
    }
    if (canHandleTransferDiscrepancy(row, status, context)) {
      actions.push(createAction("handleTransferDiscrepancy"))
    }
    if ((status === "draft" || (status === "submitted" && !isNativeApprovalContext(row, context))) &&
      canManageManualTransferDraft(row, context)) {
      actions.push(createAction("cancelTransfer"))
    }
  }

  if (featureKey === "transferApproval") {
    if (status === "submitted") {
      const nativeApproval = isNativeApprovalContext(row, context)
      if (!nativeApproval || hasUnifiedApprovalTask(row, context)) {
        actions.push(createAction("approveTransfer"))
        if (nativeApproval) actions.push(createAction("returnTransfer"))
        actions.push(createAction("rejectTransfer"))
      }
      if (nativeApproval && isUnifiedApprovalApplicant(row, context)) {
        actions.push(createAction("withdrawTransfer"))
      }
    }
  }

  if (featureKey === "oaPurchase") {
    if (status === "draft" || status === "returned" || status === "withdrawn") actions.push(createAction("editOaPurchase"))
    if (status === "draft" || status === "returned" || status === "withdrawn") actions.push(createAction("submitOaPurchase"))
  }

  if (featureKey === "notice" && isNoticeUnread(row)) {
    actions.push(createAction("markNoticeRead"))
  }

  if (featureKey === "customer") {
    if (status === "0") actions.push(createAction("addCustomerServiceRecord"))
    if (status === "0") actions.push(createAction("editCustomer"))
    if (row.photoNodeId) actions.push(createAction("previewCustomerPhoto"))
  }

  if (featureKey === "monitorJob") {
    actions.push(createAction("runMonitorJob"))
    if (status === "0") actions.push(createAction("pauseMonitorJob"))
    if (status === "1") actions.push(createAction("resumeMonitorJob"))
  }

  if (featureKey === "monitorOnline") {
    if (!row.currentSession) actions.push(createAction("forceLogoutOnline"))
  }

  return actions.filter(() => getMobileActionTargetId(featureKey, item) !== undefined)
}

function createAction(id) {
  const definition = ACTION_DEFINITIONS[id] || {}
  return Object.assign({ id }, definition, {
    surface: resolveMobileActionSurface(id, definition)
  })
}

function resolveMobileActionSurface(id, definition) {
  if (definition && (definition.surface === "primary" || definition.surface === "secondary")) {
    return definition.surface
  }
  if (/^(cancel|delete)/.test(id) || (definition && definition.behavior === "preview-customer-photo")) {
    return "secondary"
  }
  return "primary"
}

function partitionMobileDetailActions(actions, maxPrimary) {
  const limitValue = Number(maxPrimary)
  const limit = Number.isFinite(limitValue) && limitValue >= 0 ? Math.floor(limitValue) : 2
  const groups = { primary: [], secondary: [] }

  ;(Array.isArray(actions) ? actions : []).forEach(action => {
    if (!action) return
    if (action.surface === "secondary" || groups.primary.length >= limit) {
      groups.secondary.push(action)
      return
    }
    groups.primary.push(action)
  })

  return groups
}

function getMobileActionRow(item) {
  if (!item) return null
  return item._raw || item.raw || item
}

function getMobileActionStatus(item) {
  const row = getMobileActionRow(item)
  return normalizeText(item && (item._statusKey || item.statusKey || item.sourceStatus)) ||
    normalizeText(row && row.status)
}

function getMobileActionTargetId(featureKey, item) {
  const row = getMobileActionRow(item)
  const keys = TARGET_ID_KEYS[featureKey] || ["id"]
  return firstValue(row, keys)
}

function isStoreOrUnknownContext(context) {
  const deptType = normalizeText(context && context.selectedDeptType).toUpperCase()
  return !deptType || deptType === "STORE"
}

function canActAsStockCheckCounter(row, context) {
  const currentUserId = context && context.userId
  if (currentUserId === undefined || currentUserId === null || String(currentUserId).trim() === "") {
    return true
  }
  if (row && row.counterUserId !== undefined && row.counterUserId !== null &&
    String(row.counterUserId) === String(currentUserId)) {
    return true
  }
  const permissions = context && Array.isArray(context.permissions) ? context.permissions : []
  return permissions.indexOf("*:*:*") > -1 || permissions.indexOf("inv:stockCheck:edit") > -1
}

function hasUnifiedApprovalTask(row, context) {
  const taskId = normalizeText(context && context.approvalTaskId) || normalizeText(row && row.approvalTaskId)
  if (!taskId || row && row.unifiedApprovalLoadError) return false
  const detail = row && row.unifiedApprovalDetail
  if (!detail || typeof detail !== "object") return true
  const tasks = Array.isArray(detail.tasks) ? detail.tasks : []
  const task = tasks.find(item => item && String(item.taskId || item.id) === String(taskId))
  const instance = detail.instance || {}
  return !!task && normalizeText(task.taskStatus || task.status).toUpperCase() === "PENDING" &&
    normalizeText(instance.status).toUpperCase() === "RUNNING"
}

function isNativeApprovalContext(row, context) {
  return normalizeText(row && row.approvalEngine).toUpperCase() === "NATIVE" ||
    !!normalizeText(context && context.approvalTaskId) || !!normalizeText(row && row.approvalTaskId)
}

function isUnifiedApprovalApplicant(row, context) {
  const currentUserId = normalizeText(context && context.userId)
  if (!currentUserId) return false
  const detail = row && row.unifiedApprovalDetail
  const instance = detail && detail.instance || {}
  const applicantUserId = firstValue(instance, ["applicantUserId", "applicantId"])
  if (applicantUserId !== undefined) {
    return String(applicantUserId) === currentUserId
  }
  const submittedUserId = firstValue(row, ["submittedUserId", "applicantUserId"])
  return submittedUserId !== undefined && String(submittedUserId) === currentUserId
}

function shouldEditTransferAsReplenishment(row, context) {
  const deptType = normalizeText(context && context.selectedDeptType).toUpperCase()
  if (!deptType) return true
  const transferType = normalizeText(row && row.transferType) || "warehouse"
  return transferType === "warehouse" &&
    deptType === "STORE" &&
    hasSelectedDept(context) &&
    isSelectedDept(row, context, ["toWarehouseId", "toDeptId"])
}

function canManageManualTransferDraft(row, context) {
  if (normalizeText(context && context.selectedDeptType).toUpperCase() !== "STORE" ||
    !hasSelectedDept(context)) {
    return false
  }
  const transferType = normalizeText(row && row.transferType)
  const directionKeys = transferType === "store_return"
    ? ["fromWarehouseId", "fromDeptId"]
    : ["toWarehouseId", "toDeptId"]
  return isSelectedDept(row, context, directionKeys)
}

function canDeliverTransfer(row, status, context) {
  if (["approved", "reserved", "partial_delivered"].indexOf(status) === -1) return false
  if (requiresTransferSourceConfirmation(row) &&
    ["CONFIRMED", "PARTIAL"].indexOf(normalizeText(row && row.sourceConfirmStatus).toUpperCase()) === -1) {
    return false
  }
  if (hasSelectedDept(context)) {
    return isSelectedDept(row, context, ["fromWarehouseId", "fromDeptId"])
  }
  return normalizeText(context && context.selectedDeptType).toUpperCase() === "WAREHOUSE"
}

function canConfirmTransferSource(row, status, context) {
  if (status !== "approved" || !requiresTransferSourceConfirmation(row)) return false
  const sourceStatus = normalizeText(row && row.sourceConfirmStatus).toUpperCase() || "PENDING"
  if (["NOT_STARTED", "PENDING"].indexOf(sourceStatus) === -1) return false
  return hasSelectedDept(context) &&
    normalizeText(context && context.selectedDeptType).toUpperCase() === "STORE" &&
    isSelectedDept(row, context, ["fromWarehouseId", "fromDeptId"])
}

function requiresTransferSourceConfirmation(row) {
  return normalizeText(row && row.transferType) === "cross_store" &&
    normalizeText(row && row.sourceBusinessType) !== "sales_delivery"
}

function canReceiveTransfer(row, status, context) {
  if (["partial_delivered", "delivered", "partial_received"].indexOf(status) === -1) return false
  if (hasSelectedDept(context)) {
    return isSelectedDept(row, context, ["toWarehouseId", "toDeptId"])
  }
  return normalizeText(context && context.selectedDeptType).toUpperCase() === "STORE"
}

function canHandleTransferDiscrepancy(row, status, context) {
  return status === "discrepancy" &&
    hasSelectedDept(context) &&
    isSelectedDept(row, context, ["toWarehouseId", "toDeptId"])
}

function hasSelectedDept(context) {
  const selectedDeptId = context && context.selectedDeptId
  return selectedDeptId !== undefined && selectedDeptId !== null && String(selectedDeptId).trim() !== ""
}

function isSelectedDept(row, context, keys) {
  const selectedDeptId = String(context.selectedDeptId)
  return keys.some(key => {
    const value = row && row[key]
    return value !== undefined && value !== null && String(value) === selectedDeptId
  })
}

function buildAllRemainingItems(rows, options) {
  const sourceRows = Array.isArray(rows) ? rows : []
  const source = options || {}
  const totalKeys = source.totalKeys || ["quantity", "noticeQty", "shippedQuantity"]
  const doneKeys = source.doneKeys || ["receivedQuantity", "deliveredQuantity", "deliveredQty"]
  const remainingKeys = source.remainingKeys || ["remainingQuantity", "remainingQty"]
  const idKeys = source.idKeys || ["detailId", "transferDetailId"]
  const quantityKey = source.quantityKey || "quantity"

  return sourceRows.reduce((items, row) => {
    const remainingValue = firstNumber(row, remainingKeys)
    const total = firstNumber(row, totalKeys)
    const done = firstNumber(row, doneKeys) || 0
    const remaining = remainingValue !== null ? remainingValue : Math.max((total || 0) - done, 0)
    const detailId = firstValue(row, idKeys)

    if (detailId !== undefined && remaining > 0) {
      const item = { detailId }
      item[quantityKey] = normalizeQuantity(remaining)
      items.push(item)
    }

    return items
  }, [])
}

function firstValue(source, keys) {
  if (!source || typeof source !== "object") return undefined
  for (let i = 0; i < keys.length; i += 1) {
    const value = source[keys[i]]
    if (value !== undefined && value !== null && String(value).trim() !== "") return value
  }
  return undefined
}

function firstNumber(source, keys) {
  const value = firstValue(source, keys)
  if (value === undefined) return null
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function normalizeQuantity(value) {
  const numberValue = Number(value)
  if (!Number.isFinite(numberValue)) return 0
  return Number(numberValue.toFixed(2))
}

function normalizeText(value) {
  if (value === undefined || value === null) return ""
  return String(value).trim()
}

function isNoticeUnread(row) {
  if (!row || !Object.prototype.hasOwnProperty.call(row, "isRead")) return true
  const value = row.isRead
  return !(value === true || value === "true" || value === 1 || value === "1")
}

module.exports = {
  getMobileFeatureActions,
  getMobileActionRow,
  getMobileActionStatus,
  getMobileActionTargetId,
  buildAllRemainingItems,
  partitionMobileDetailActions
}
