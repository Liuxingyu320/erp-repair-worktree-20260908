const {
  getMobileActionRow,
  getMobileActionTargetId
} = require("./featureActions")

const {
  buildEditableQuantityItems,
  filterDeliveryNoticeRowsByWarehouse,
  buildTransferReceiptItems,
  buildTransferApprovalPayload,
  buildStockCheckInputPayload,
  selectPendingShipment
} = require("./mobileActionPayloads")
const { buildCustomerServiceRecordPayload } = require("./mobileCustomerServiceRecord")

function createMobileActionRuntime(api) {
  const deps = api || {}

  function call(name) {
    const fn = deps[name]
    if (typeof fn !== "function") {
      return Promise.reject(new Error("missing mobile action api: " + name))
    }
    return fn.apply(null, Array.prototype.slice.call(arguments, 1))
  }

  function runMobileFeatureAction(featureKey, actionId, item, options) {
    if (featureKey === "sales") return runSalesAction(actionId, item)
    if (featureKey === "purchase") return runPurchaseAction(actionId, item, options)
    if (featureKey === "salesReturn") return runSalesReturnAction(actionId, item)
    if (featureKey === "purchaseReturn") return runPurchaseReturnAction(actionId, item)
    if (featureKey === "outbound") return runOutboundAction(actionId, item, options)
    if (featureKey === "stockCheck") return runStockCheckAction(actionId, item, options)
    if (featureKey === "transfer" || featureKey === "transferApproval") return runTransferAction(featureKey, actionId, item, options)
    if (featureKey === "replenishment") return runTransferAction("replenishment", actionId, item, options)
    if (featureKey === "oaPurchase") return runOaPurchaseAction(actionId, item)
    if (featureKey === "salary") return runSalaryAction(actionId, options)
    if (featureKey === "notice") return runNoticeAction(actionId, item)
    if (featureKey === "monitorJob") return runMonitorJobAction(actionId, item)
    if (featureKey === "monitorOnline") return runMonitorOnlineAction(actionId, item)
    if (featureKey === "customer") return runCustomerAction(actionId, item, options)
    return Promise.reject(new Error("unsupported mobile action"))
  }

  function saveMobileFeatureForm(featureKey, data, options) {
    const payload = Object.assign({}, data || {})
    const submitAction = payload.submitAction
    delete payload.submitAction

    if (featureKey === "sales") {
      return submitAction === "submit" ? call("submitSales", payload) : call("saveSales", payload)
    }
    if (featureKey === "purchase") {
      return submitAction === "submit" ? call("submitPurchase", payload) : call("saveInventoryPurchase", payload)
    }
    if (featureKey === "salesReturn") {
      return submitAction === "submit" ? call("submitSalesReturn", payload) : call("saveSalesReturn", payload)
    }
    if (featureKey === "purchaseReturn") {
      return submitAction === "submit" ? call("submitPurchaseReturn", payload) : call("savePurchaseReturn", payload)
    }
    if (featureKey === "stockCheck") return call("createStockCheck", payload)
    if (featureKey === "transfer" || featureKey === "replenishment") {
      return submitAction === "submit" ? call("submitTransfer", payload) : call("saveTransfer", payload)
    }
    if (featureKey === "oaPurchase") {
      return submitAction === "submit" ? call("submitOaPurchaseApi", payload) : call("saveOaPurchaseApi", payload)
    }
    if (featureKey === "fixedAssetRepair") {
      return Promise.resolve(call("precheckFixedAssetRepair", payload)).then(response => {
        if (options && typeof options.isCurrent === "function" && !options.isCurrent()) {
          throw new Error("表单已变化，请重新检查后提交")
        }
        const precheck = unwrapResponseData(response)
        if (!precheck || typeof precheck.allowed !== "boolean") {
          throw new Error("未取得有效的报修预检结果，请重试")
        }
        if (precheck.allowed === false) {
          throw createFixedAssetQuotaError(precheck)
        }
        return call("submitFixedAssetRepair", payload)
      })
    }
    if (featureKey === "customer") {
      const customerId = payload.customerId
      delete payload.customerId
      return customerId
        ? call("updateCustomerServiceCard", customerId, payload)
        : call("createCustomerServiceCard", payload)
    }
    return Promise.reject(new Error("unsupported mobile form"))
  }

  function runSalesAction(actionId, item) {
    const orderId = requireTargetId("sales", item)
    if (actionId === "submitSales") {
      return Promise.resolve(call("getSalesDetail", orderId)).then(res => call("submitSales", (res && res.data) || {}))
    }
    if (actionId === "createDeliveryNotice") return call("createDeliveryNotice", orderId, getMobileActionRow(item).version)
    if (actionId === "cancelSales") return call("cancelSales", orderId, getMobileActionRow(item).version)
    return Promise.reject(new Error("unsupported sales action"))
  }

  function runPurchaseAction(actionId, item, options) {
    const orderId = requireTargetId("purchase", item)
    if (actionId === "submitPurchase") {
      return call("submitPurchaseDraft", orderId, getMobileActionRow(item).version)
    }
    if (actionId === "deletePurchaseDraft") return call("deleteDraftPurchase", orderId)
    if (actionId === "qualityCheckPurchase") {
      const actionPayload = resolveActionPayload(options)
      if (actionPayload.receiptBatchId && Array.isArray(actionPayload.qualityItems)) {
        return call("qualityCheckPurchaseBatch", orderId, {
          receiptBatchId: actionPayload.receiptBatchId,
          items: actionPayload.qualityItems
        })
      }
      if (!actionPayload.qcResult) {
        return Promise.reject(new Error("请选择质检结果"))
      }
      return call("qualityCheckPurchase", orderId, {
        qcResult: actionPayload.qcResult,
        qcRemark: actionPayload.qcRemark || actionPayload.comment || ""
      })
    }
    if (actionId === "receivePurchaseAll") {
      const recovery = deps.purchaseReceiveRecovery
      if (!recovery) return Promise.reject(new Error("收货恢复服务不可用，未发送请求"))
      const frozen = JSON.parse(JSON.stringify(resolveActionPayload(options)))
      const scope = options && options.receiveScope
      if (!scope) return Promise.reject(new Error("请重新打开收货窗口确认账号和组织"))
      return recovery.run({
        orderId, scope, isCurrent: options.isCurrent,
        observedRequestId: options.receiveObservedRequestId,
        recoveryOnly: options.receiveRecoveryOnly === true,
        requestId: options.receiveRequestId,
        buildPayload: () => Promise.resolve(call("getPurchaseReceiveContext", orderId)).then(res => {
          const detail = (res && res.data) || {}
          if (String(detail.orderId) !== String(orderId)) throw new Error("采购详情身份无法确认")
          const items = buildEditableQuantityItems(detail.details || [], {
            totalKeys: ["quantity"], doneKeys: ["receivedQuantity"], quantityKey: "receiveQuantity",
            inputItems: resolveQuantityInputItems(frozen), allowAllRemaining: shouldUseAllRemaining(frozen)
          })
          if (items.length === 0) throw new Error("没有可收货明细")
          const arrivedTime = normalizeBackendDateTime(frozen.arrivedTime)
          if (!arrivedTime) throw new Error("请选择实际到货时间")
          const payload = { warehouseId: frozen.warehouseId || scope.dept, arrivedTime, items }
          copyOptionalTextFields(payload, frozen, ["supplierBatchNo", "deliveryNoteNo", "remark"])
          return payload
        })
      })
    }
    if (actionId === "cancelPurchase") return call("cancelPurchase", orderId)
    return Promise.reject(new Error("unsupported purchase action"))
  }

  function runSalesReturnAction(actionId, item) {
    const returnId = requireTargetId("salesReturn", item)
    if (actionId === "submitSalesReturn") {
      return call("submitSalesReturnDraft", returnId, getMobileActionRow(item).version)
    }
    if (actionId === "confirmSalesReturn") return call("confirmSalesReturn", returnId)
    if (actionId === "cancelSalesReturn") return call("cancelSalesReturn", returnId)
    return Promise.reject(new Error("unsupported sales return action"))
  }

  function runPurchaseReturnAction(actionId, item) {
    const returnId = requireTargetId("purchaseReturn", item)
    if (actionId === "submitPurchaseReturn") {
      return call("submitPurchaseReturnDraft", returnId, getMobileActionRow(item).version)
    }
    if (actionId === "confirmPurchaseReturn") return call("confirmPurchaseReturn", returnId)
    if (actionId === "cancelPurchaseReturn") return call("cancelPurchaseReturn", returnId)
    return Promise.reject(new Error("unsupported purchase return action"))
  }

  function runOutboundAction(actionId, item, options) {
    const noticeId = requireTargetId("outbound", item)
    if (actionId === "deliverDeliveryNoticeAll") {
      return Promise.resolve(call("getDeliveryNotice", noticeId)).then(res => {
        const detail = (res && res.data) || {}
        const actionPayload = resolveActionPayload(options)
        const warehouseId = (options && options.selectedDeptId) ||
          actionPayload.warehouseId ||
          detail.warehouseId
        const warehouseDetails = filterDeliveryNoticeRowsByWarehouse(detail.details || [], warehouseId)
        const inputItems = resolveQuantityInputItems(actionPayload)
        const items = buildEditableQuantityItems(warehouseDetails, {
          totalKeys: ["noticeQty"],
          doneKeys: ["deliveredQty"],
          quantityKey: "deliverQuantity",
          inputItems,
          allowAllRemaining: shouldUseAllRemaining(actionPayload)
        })
        if (items.length === 0) throw new Error("当前仓库没有可发货明细")
        return call("deliverDeliveryNotice", noticeId, {
          warehouseId,
          items
        })
      })
    }
    if (actionId === "cancelDeliveryNotice") return call("cancelDeliveryNotice", noticeId)
    return Promise.reject(new Error("unsupported outbound action"))
  }

  function runStockCheckAction(actionId, item, options) {
    const checkId = requireTargetId("stockCheck", item)
    const actionPayload = resolveActionPayload(options)
    const row = getMobileActionRow(item) || {}
    if (actionId === "saveStockCheckInput" || actionId === "submitStockCheck") {
      return Promise.resolve(call("getStockCheck", checkId)).then(res => {
        const detail = (res && res.data) || {}
        const items = buildStockCheckInputPayload(detail.details || [], actionPayload.items || actionPayload.details)
        if (items.length === 0) throw new Error("没有可录入的实盘明细")
        return Promise.resolve(call("inputStockCheck", checkId, { details: items })).then(() => {
          return actionId === "submitStockCheck" ? call("submitStockCheck", checkId) : undefined
        })
      })
    }
    if (actionId === "approveStockCheck" || actionId === "returnStockCheck" || actionId === "rejectStockCheck") {
      const approvalContext = resolveApprovalContext(row, options, actionPayload)
      if (approvalContext.native) {
        return runUnifiedApprovalTask("INV_STOCK_CHECK", actionId, approvalContext.taskId, actionPayload)
      }
      if (actionId === "returnStockCheck") {
        return Promise.reject(new Error("旧审批流程不支持退回动作"))
      }
      const instanceId = actionPayload.instanceId || firstValue(row, ["approvalInstanceId", "instanceId"])
      if (instanceId === undefined || instanceId === null || String(instanceId).trim() === "") {
        return Promise.reject(new Error("缺少当前审批实例"))
      }
      const payload = { instanceId, comment: actionPayload.comment || "" }
      return actionId === "approveStockCheck"
        ? call("approveStockCheck", checkId, payload)
        : call("rejectStockCheck", checkId, payload)
    }
    if (actionId === "restartStockCheck") return call("restartStockCheck", checkId)
    if (actionId === "deleteStockCheckDraft") return call("deleteStockCheck", checkId)
    if (actionId === "withdrawStockCheck") {
      return Promise.resolve(call("withdrawStockCheck", checkId)).then(response =>
        Object.assign({}, response || {}, { msg: "撤回请求已提交" })
      )
    }
    if (actionId === "cancelStockCheck") return call("cancelStockCheck", checkId)
    return Promise.reject(new Error("unsupported stock check action"))
  }

  function runTransferAction(featureKey, actionId, item, options) {
    const transferId = requireTargetId(featureKey, item)
    const actionPayload = resolveActionPayload(options)
    const row = getMobileActionRow(item) || {}
    if (actionId === "deleteReplenishmentDraft" || actionId === "deleteTransferDraft") {
      return call("deleteTransferDraft", transferId)
    }
    if (actionId === "submitReplenishment" || actionId === "submitTransferDraft") {
      return Promise.resolve(call("getTransferDetail", transferId)).then(res => call("submitTransfer", (res && res.data) || {}))
    }
    if (actionId === "approveTransfer" || actionId === "returnTransfer" || actionId === "rejectTransfer") {
      const approvalContext = resolveApprovalContext(row, options, actionPayload)
      if (approvalContext.native) {
        return runUnifiedApprovalTask("INV_TRANSFER", actionId, approvalContext.taskId, actionPayload)
      }
      if (actionId === "returnTransfer") {
        return Promise.reject(new Error("旧审批流程不支持退回动作"))
      }
    }
    if (actionId === "approveTransfer") {
      return call("approveTransfer", buildTransferApprovalPayload(transferId, {
        taskId: actionPayload.taskId || firstValue(row, ["currentTaskId", "taskId"]),
        action: "approve",
        comment: actionPayload.comment
      }))
    }
    if (actionId === "rejectTransfer") {
      return call("approveTransfer", buildTransferApprovalPayload(transferId, {
        taskId: actionPayload.taskId || firstValue(row, ["currentTaskId", "taskId"]),
        action: "reject",
        comment: actionPayload.comment
      }))
    }
    if (actionId === "confirmTransferSource") {
      return Promise.resolve(call("getTransferDetail", transferId)).then(res => {
        const detail = (res && res.data) || {}
        const detailRows = Array.isArray(detail.details) ? detail.details : []
        const inputItems = resolveQuantityInputItems(actionPayload) || []
        if (!detailRows.length || inputItems.length !== detailRows.length) {
          throw new Error("请逐行确认全部调拨明细")
        }
        let hasRemainder = false
        const items = detailRows.map(row => {
          const detailId = firstValue(row, ["detailId", "transferDetailId"])
          const input = inputItems.find(candidate =>
            String(firstValue(candidate, ["detailId", "transferDetailId"])) === String(detailId)
          )
          const requested = Number(row.quantity || 0)
          const confirmed = Number(input && (input.confirmedQuantity !== undefined
            ? input.confirmedQuantity : input.quantity))
          if (!Number.isFinite(confirmed) || confirmed < 0 || confirmed > requested) {
            throw new Error((row.itemName || row.productName || "物料") + "：确认数量必须在0和申请数量之间")
          }
          if (confirmed < requested) hasRemainder = true
          return { detailId, confirmedQuantity: confirmed }
        })
        const remark = String(actionPayload.comment || actionPayload.remark || "").trim()
        if (hasRemainder && !remark) {
          throw new Error("有部分或全部无法调出时，请填写说明")
        }
        return call("confirmTransferSource", transferId, { items, remark })
      })
    }
    if (actionId === "deliverTransferAll") {
      return Promise.resolve(call("getTransferDetail", transferId)).then(res => {
        const detail = (res && res.data) || {}
        const inputItems = resolveQuantityInputItems(actionPayload)
        const items = buildEditableQuantityItems(detail.details || [], {
          totalKeys: ["quantity"],
          doneKeys: ["deliveredQuantity"],
          quantityKey: "deliverQuantity",
          inputItems,
          allowAllRemaining: shouldUseAllRemaining(actionPayload)
        })
        if (items.length === 0) throw new Error("没有可出库明细")
        return call("deliverTransfer", transferId, { items })
      })
    }
    if (actionId === "receiveTransferShipment") {
      return Promise.resolve(call("getTransferDetail", transferId)).then(res => {
        const detail = (res && res.data) || {}
        const shipment = selectPendingShipment(detail, actionPayload)
        const inputItems = resolveQuantityInputItems(actionPayload)
        const items = buildTransferReceiptItems(shipment.details || [], inputItems, {
          allowAllRemaining: shouldUseAllRemaining(actionPayload)
        })
        if (items.length === 0) throw new Error("没有可收货明细")
        return call("receiveShipment", shipment.shipmentId, { items })
      })
    }
    if (actionId === "handleTransferDiscrepancy") {
      const discrepancyId = actionPayload.discrepancyId
      const requestId = String(actionPayload.requestId || "").trim()
      const responsibleParty = String(actionPayload.responsibleParty || "UNCONFIRMED").trim().toUpperCase()
      const note = String(actionPayload.note || actionPayload.comment || "").trim()
      const items = Array.isArray(actionPayload.items) ? actionPayload.items.map(item => ({
        detailId: item && item.detailId,
        category: String(item && item.category || "").trim().toUpperCase(),
        decision: String(item && item.decision || "").trim().toUpperCase(),
        quantity: Number(item && item.quantity),
        note: String(item && item.note || "").trim(),
        attachmentRefs: String(item && item.attachmentRefs || "").trim()
      })) : []
      if (discrepancyId === undefined || discrepancyId === null || String(discrepancyId).trim() === "") {
        return Promise.reject(new Error("请选择待处理差异单"))
      }
      if (!requestId) {
        return Promise.reject(new Error("差异处置 requestId 不能为空"))
      }
      if (!items.length) {
        return Promise.reject(new Error("请逐项填写差异类别处置"))
      }
      if (items.some(item => !item.detailId || ["SHORTAGE", "REJECTED", "DAMAGED"].indexOf(item.category) === -1 ||
        ["RESHIP", "RETURN_SOURCE", "ACCEPT_ACTUAL", "PENDING_QC", "WRITE_OFF"].indexOf(item.decision) === -1 ||
        !Number.isFinite(item.quantity) || item.quantity <= 0)) {
        return Promise.reject(new Error("差异明细、类别、决定和数量必须完整"))
      }
      if (["SOURCE", "TARGET", "LOGISTICS", "UNCONFIRMED"].indexOf(responsibleParty) === -1) {
        return Promise.reject(new Error("请选择有效责任方"))
      }
      if (!note) return Promise.reject(new Error("请填写差异处理说明"))

      return Promise.resolve(call("getTransferDetail", transferId)).then(res => {
        const detail = (res && res.data) || {}
        const discrepancies = Array.isArray(detail.discrepancies) ? detail.discrepancies : []
        const discrepancy = discrepancies.find(candidate =>
          candidate &&
          String(candidate.discrepancyId) === String(discrepancyId) &&
          String(candidate.status || "").toUpperCase() !== "RESOLVED"
        )
        if (!discrepancy) throw new Error("差异单已被处理，请刷新后重试")
        if (discrepancy.version === undefined || discrepancy.version === null) {
          throw new Error("差异单版本缺失，请刷新后重试")
        }
        return call("resolveTransferDiscrepancy", discrepancy.discrepancyId, {
          requestId,
          version: discrepancy.version,
          responsibleParty,
          note,
          items
        })
      })
    }
    if (actionId === "withdrawTransfer") {
      return Promise.resolve(call("withdrawTransfer", transferId)).then(response =>
        Object.assign(response || {}, { msg: "撤回请求已提交" })
      )
    }
    if (actionId === "cancelTransfer") return call("cancelTransfer", transferId)
    return Promise.reject(new Error("unsupported transfer action"))
  }

  function runOaPurchaseAction(actionId, item) {
    const purchaseId = requireTargetId("oaPurchase", item)
    if (actionId === "submitOaPurchase") {
      return Promise.resolve(call("getOaPurchaseDetail", purchaseId)).then(res => call("submitOaPurchaseApi", (res && res.data) || {}))
    }
    return Promise.reject(new Error("unsupported OA purchase action"))
  }

  function runSalaryAction(actionId, options) {
    if (actionId === "calculateSalary") return call("calculateSalary", { salaryMonth: resolveSalaryMonth(options) })
    return Promise.reject(new Error("unsupported salary action"))
  }

  function runNoticeAction(actionId, item) {
    if (actionId === "markNoticeRead") return call("markNoticeRead", requireTargetId("notice", item))
    if (actionId === "markAllNoticeRead") {
      return Promise.resolve(call("markNoticeReadAll")).then(res => {
        const unreadCount = res && res.unreadCount !== undefined ? Number(res.unreadCount) : NaN
        if (!Number.isFinite(unreadCount) || unreadCount < 0) return res
        return Object.assign({}, res, {
          msg: unreadCount === 0
            ? "已全部标记为已读"
            : "操作完成，仍有 " + unreadCount + " 条未读通知"
        })
      })
    }
    return Promise.reject(new Error("unsupported notice action"))
  }

  function runMonitorJobAction(actionId, item) {
    const jobId = requireTargetId("monitorJob", item)
    const row = getMobileActionRow(item) || {}
    if (actionId === "runMonitorJob") {
      const jobGroup = firstValue(row, ["jobGroup"])
      if (jobGroup === undefined) throw new Error("缺少任务组")
      return call("runJob", jobId, jobGroup)
    }
    if (actionId === "pauseMonitorJob") return call("changeJobStatus", jobId, "1")
    if (actionId === "resumeMonitorJob") return call("changeJobStatus", jobId, "0")
    return Promise.reject(new Error("unsupported monitor job action"))
  }

  function runMonitorOnlineAction(actionId, item) {
    if (actionId === "forceLogoutOnline") return call("forceLogout", requireTargetId("monitorOnline", item))
    return Promise.reject(new Error("unsupported monitor online action"))
  }

  function runCustomerAction(actionId, item, options) {
    const customerId = requireTargetId("customer", item)
    if (actionId === "addCustomerServiceRecord") {
      return call("addCustomerServiceRecord", customerId,
        buildCustomerServiceRecordPayload(resolveActionPayload(options)))
    }
    return Promise.reject(new Error("unsupported customer action"))
  }

  function runUnifiedApprovalTask(businessCode, actionId, taskId, actionPayload) {
    if (taskId === undefined || taskId === null || String(taskId).trim() === "") {
      return Promise.reject(new Error("缺少统一审批任务，请从工作台重新进入"))
    }
    const action = /return/i.test(actionId) ? "return" : /reject/i.test(actionId) ? "reject" : "approve"
    const payload = {
      requestId: businessCode + ":" + taskId + ":" + action + ":" + Date.now(),
      reason: String(actionPayload && actionPayload.comment || "").trim()
    }
    if (action === "approve") return call("approveApprovalTask", taskId, payload)
    if (action === "return") return call("returnApprovalTask", taskId, payload)
    return call("rejectApprovalTask", taskId, payload)
  }

  return {
    runMobileFeatureAction,
    saveMobileFeatureForm
  }
}

function resolveActionPayload(options) {
  const source = options || {}
  return source.actionPayload || source.payload || {}
}

function unwrapResponseData(response) {
  if (response && Object.prototype.hasOwnProperty.call(response, "data")) return response.data
  return response
}

function createFixedAssetQuotaError(precheck) {
  const error = new Error(precheck && precheck.message
    ? precheck.message
    : "当前可用额度不足，不能上报")
  error.code = (precheck && precheck.errorCode) || "FIXED_ASSET_QUOTA_EXCEEDED"
  error.precheck = precheck || null
  return error
}

function getFixedAssetPrecheckFromError(error) {
  if (!error) return null
  if (error.precheck && typeof error.precheck === "object") return error.precheck
  const responseData = error.response && error.response.data
  if (responseData && responseData.precheck && typeof responseData.precheck === "object") {
    return responseData.precheck
  }
  const errorData = error.data
  if (errorData && errorData.precheck && typeof errorData.precheck === "object") {
    return errorData.precheck
  }
  return null
}

function resolveApprovalContext(row, options, actionPayload) {
  const source = options || {}
  const payload = actionPayload || {}
  const taskId = firstValue(payload, ["approvalTaskId"]) ||
    firstValue(source, ["approvalTaskId"]) ||
    firstValue(row, ["approvalTaskId"])
  const engine = String(firstValue(row, ["approvalEngine"]) || "").toUpperCase()
  return {
    taskId,
    native: engine === "NATIVE" || (taskId !== undefined && taskId !== null && String(taskId).trim() !== "")
  }
}

function resolveQuantityInputItems(actionPayload) {
  const source = actionPayload || {}
  if (Array.isArray(source.items)) return source.items
  if (Array.isArray(source.inputItems)) return source.inputItems
  return undefined
}

function shouldUseAllRemaining(actionPayload) {
  const source = actionPayload || {}
  return source.allRemaining === true &&
    !Array.isArray(source.items) &&
    !Array.isArray(source.inputItems)
}

function normalizeBackendDateTime(value) {
  const normalized = String(value || "").trim().replace("T", " ")
  return normalized.length === 16 ? normalized + ":00" : normalized
}

function copyOptionalTextFields(target, source, keys) {
  ;(keys || []).forEach(key => {
    const value = source && source[key]
    if (value === undefined || value === null) return
    const normalized = String(value).trim()
    if (normalized) target[key] = normalized
  })
}

function requireTargetId(featureKey, item) {
  const targetId = getMobileActionTargetId(featureKey, item)
  if (targetId === undefined || targetId === null || String(targetId).trim() === "") {
    throw new Error("缺少单据ID")
  }
  return targetId
}

function resolveSalaryMonth(options) {
  const source = options || {}
  const query = source.query || {}
  const month = source.salaryMonth || query.salaryMonth
  if (month !== undefined && month !== null && String(month).trim() !== "") {
    return String(month).trim()
  }

  const now = new Date()
  return now.getFullYear() + "-" + String(now.getMonth() + 1).padStart(2, "0")
}

function firstValue(source, keys) {
  if (!source || typeof source !== "object") return undefined
  for (let i = 0; i < keys.length; i += 1) {
    const value = source[keys[i]]
    if (value !== undefined && value !== null && String(value).trim() !== "") return value
  }
  return undefined
}

module.exports = {
  createMobileActionRuntime,
  getFixedAssetPrecheckFromError
}
