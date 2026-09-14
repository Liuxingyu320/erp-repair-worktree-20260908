const { buildTransferApprovalPayload } = require("../../../utils/transferApprovalPayload")

function buildEditableQuantityItems(rows, options) {
  const sourceRows = Array.isArray(rows) ? rows : []
  const source = options || {}
  const inputItems = Array.isArray(source.inputItems) ? source.inputItems : []
  const idKeys = source.idKeys || ["detailId", "transferDetailId"]
  const quantityKey = source.quantityKey || "quantity"
  const totalKeys = source.totalKeys || ["quantity", "noticeQty", "shippedQuantity"]
  const doneKeys = source.doneKeys || ["receivedQuantity", "deliveredQuantity", "deliveredQty"]
  const remainingKeys = source.remainingKeys || ["remainingQuantity", "remainingQty"]
  const allowAllRemaining = source.allowAllRemaining === true

  return sourceRows.reduce((items, row) => {
    const detailId = firstValue(row, idKeys)
    if (detailId === undefined) return items

    const remaining = resolveRemainingQuantity(row, { totalKeys, doneKeys, remainingKeys })
    const inputItem = findInputItem(inputItems, detailId, idKeys)
    if (!inputItem && !allowAllRemaining) return items
    const requested = inputItem ? firstNumber(inputItem, ["quantity", quantityKey, "actualQuantity"]) : remaining

    if (requested === null || requested <= 0) return items
    if (remaining !== null && requested > remaining) {
      throw new Error("处理数量不能大于剩余数量")
    }

    const item = { detailId }
    item[quantityKey] = normalizeQuantity(requested)
    items.push(item)
    return items
  }, [])
}

function filterDeliveryNoticeRowsByWarehouse(rows, warehouseId) {
  const sourceRows = Array.isArray(rows) ? rows : []
  if (warehouseId === undefined || warehouseId === null || String(warehouseId).trim() === "") {
    return []
  }
  return sourceRows.filter(row => {
    const rowWarehouseId = firstValue(row, ["warehouseId"])
    return rowWarehouseId !== undefined && String(rowWarehouseId) === String(warehouseId)
  })
}

function selectPendingShipment(detail, options) {
  const shipments = Array.isArray(detail && detail.shipments) ? detail.shipments : []
  const pendingShipments = shipments.filter(item => String(item.status || "").trim() === "pending_receive")
  const shipmentId = options && options.shipmentId

  if (shipmentId !== undefined && shipmentId !== null && String(shipmentId).trim() !== "") {
    const selected = pendingShipments.find(item => String(item.shipmentId) === String(shipmentId))
    if (!selected) throw new Error("未找到指定待收货发货批次")
    return selected
  }

  if (!pendingShipments.length) {
    throw new Error("没有待收货发货批次")
  }
  return pendingShipments[0]
}

function buildStockCheckInputPayload(detailRows, inputRows) {
  const details = Array.isArray(detailRows) ? detailRows : []
  const inputs = Array.isArray(inputRows) ? inputRows : []

  return details.reduce((items, row) => {
    const detailId = firstValue(row, ["detailId", "checkDetailId"])
    const input = findInputItem(inputs, detailId, ["detailId", "checkDetailId"])
    if (!input) return items

    const actualQuantity = firstNumber(input, ["actualQuantity", "actualQty", "quantity"])
    if (actualQuantity === null || actualQuantity < 0) {
      throw new Error("实盘数量不能为空")
    }

    const item = {
      detailId,
      actualQty: normalizeQuantity(actualQuantity)
    }
    // Keep the token from the opened form. The runtime's fresh GET must not bless stale input.
    if (input.snapshotVersion != null) item.snapshotVersion = input.snapshotVersion
    const hasRecount = Object.prototype.hasOwnProperty.call(input, "recountQty") ||
      Object.prototype.hasOwnProperty.call(input, "recountQuantity")
    const unchangedActual = firstNumber(row, ["actualQuantity", "actualQty", "checkQuantity"]) === actualQuantity
    const recountSource = hasRecount ? input : (unchangedActual ? row : {})
    const rawRecount = firstValue(recountSource, ["recountQty", "recountQuantity"])
    if (rawRecount !== undefined && rawRecount !== null && rawRecount !== "") {
      const recount = Number(rawRecount)
      if (!Number.isFinite(recount) || recount < 0) throw new Error("复盘数量不能小于0")
      item.recountQty = normalizeQuantity(recount)
    } else if (hasRecount) {
      item.recountQty = null
    }
    items.push(item)
    return items
  }, [])
}

function buildTransferReceiptItems(detailRows, inputRows, options) {
  const details = Array.isArray(detailRows) ? detailRows : []
  const inputs = Array.isArray(inputRows) ? inputRows : []
  const allowAllRemaining = options && options.allowAllRemaining === true

  return details.reduce((items, row) => {
    const detailId = firstValue(row, ["transferDetailId", "detailId"])
    if (detailId === undefined) return items
    const remaining = resolveRemainingQuantity(row, {
      totalKeys: ["shippedQuantity"],
      doneKeys: ["receivedQuantity"],
      remainingKeys: ["remainingQuantity", "remainingQty"]
    })
    if (remaining === null || remaining <= 0) return items

    const input = findInputItem(inputs, detailId, ["transferDetailId", "detailId"])
    if (!input && !allowAllRemaining) {
      throw new Error("调拨收货明细不完整")
    }
    const accepted = input
      ? firstNumber(input, ["receiveQuantity", "quantity"])
      : remaining
    const rejectedValue = firstValue(input, ["rejectedQuantity"])
    const damagedValue = firstValue(input, ["damagedQuantity"])
    const rejected = rejectedValue === undefined ? 0 : Number(rejectedValue)
    const damaged = damagedValue === undefined ? 0 : Number(damagedValue)
    if (accepted === null || !Number.isFinite(rejected) || !Number.isFinite(damaged) ||
      accepted < 0 || rejected < 0 || damaged < 0) {
      throw new Error("调拨收货分类数量必须是非负数")
    }

    const classified = accepted + rejected + damaged
    if (classified > remaining + 0.000001) {
      throw new Error("调拨收货分类数量不能大于待收数量")
    }
    const note = normalizeComment(input && input.discrepancyNote)
    const attachmentRefs = normalizeComment(input && input.attachmentRefs)
    const hasDifference = classified < remaining - 0.000001 || rejected > 0 || damaged > 0
    if (hasDifference && !note && !attachmentRefs) {
      throw new Error("存在短少、拒收或残损时，请填写差异说明")
    }

    const item = {
      detailId,
      receiveQuantity: normalizeQuantity(accepted)
    }
    if (rejected > 0) item.rejectedQuantity = normalizeQuantity(rejected)
    if (damaged > 0) item.damagedQuantity = normalizeQuantity(damaged)
    if (note) item.discrepancyNote = note
    if (attachmentRefs) item.attachmentRefs = attachmentRefs
    items.push(item)
    return items
  }, [])
}

function resolveStockCheckInputQuantity(row) {
  const value = firstValue(row, ["actualQuantity", "actualQty", "checkQuantity"])
  if (value === undefined) return ""
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? normalizeQuantity(numberValue) : ""
}

function resolveRemainingQuantity(row, options) {
  const remainingValue = firstNumber(row, options.remainingKeys)
  if (remainingValue !== null) return Math.max(remainingValue, 0)

  const total = firstNumber(row, options.totalKeys)
  const done = firstNumber(row, options.doneKeys) || 0
  if (total === null) return null
  return Math.max(total - done, 0)
}

function findInputItem(inputItems, detailId, idKeys) {
  if (detailId === undefined) return null
  return inputItems.find(item => String(firstValue(item, idKeys)) === String(detailId)) || null
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

function normalizeComment(comment) {
  return comment === undefined || comment === null ? "" : String(comment).trim()
}

module.exports = {
  buildEditableQuantityItems,
  filterDeliveryNoticeRowsByWarehouse,
  buildTransferReceiptItems,
  buildTransferApprovalPayload,
  buildStockCheckInputPayload,
  resolveStockCheckInputQuantity,
  selectPendingShipment
}
