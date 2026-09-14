const assert = require("assert")

const {
  createMobileActionRuntime,
  getFixedAssetPrecheckFromError
} = require("../src/views/mobile/feature/featureActionRuntime")

const { createPurchaseReceiveRecovery } = require("../src/utils/purchaseReceiveRecovery")
const { memoryStore } = require("./purchaseReceiveRecovery.test")

function createStubRuntime(responses) {
  const calls = []
  let purchaseReceiveRecovery
  const api = new Proxy({}, {
    get(target, name) {
      if (name === "purchaseReceiveRecovery") return purchaseReceiveRecovery
      return function stubbedApi() {
        const args = Array.prototype.slice.call(arguments)
        calls.push({ name: String(name), args })
        const response = responses && responses[name]
        if (name === "getPurchaseReceiveContext" && response && response.data) return { ...response, data: { orderId: args[0], ...response.data } }
        if (name === "receivePurchase" && !response) return { data: { requestId: args[2], purchaseOrderId: args[0], warehouseId: args[1].warehouseId,
          receiptBatchId: 101, batchNo: "RECEIPT-101", receivedQuantity: args[1].items.reduce((n, item) => n + item.receiveQuantity, 0) } }
        return typeof response === "function" ? response.apply(null, args) : (response || { ok: true })
      }
    }
  })

  purchaseReceiveRecovery = createPurchaseReceiveRecovery({ storage: memoryStore(), context: () => ({ actor: "7", dept: "8" }),
    createId: () => "receive:test-runtime", transport: (orderId, payload, requestId, scope) => api.receivePurchase(Number(orderId), payload, requestId, scope) })
  return {
    calls,
    runtime: createMobileActionRuntime(api)
  }
}

function findCall(calls, name) {
  return calls.find(call => call.name === name)
}

;(async () => {
  {
    const { runtime, calls } = createStubRuntime()
    await runtime.saveMobileFeatureForm("oaPurchase", {
      title: "门店物料采购",
      submitAction: "submit",
      details: [{ productId: 1, quantity: 2 }]
    })
    assert.deepStrictEqual(
      findCall(calls, "submitOaPurchaseApi").args,
      [{ title: "门店物料采购", details: [{ productId: 1, quantity: 2 }] }],
      "OA purchase submit should call the submit API without leaking submitAction"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      precheckFixedAssetRepair: {
        data: { allowed: true, quotaUsageAmount: 20, availableQuotaAmount: 100 }
      }
    })
    const payload = { oeItemId: 1001, repairQuantity: 1, faultDescription: "破损" }
    await runtime.saveMobileFeatureForm("fixedAssetRepair", payload)
    assert.deepStrictEqual(
      calls.map(call => call.name),
      ["precheckFixedAssetRepair", "submitFixedAssetRepair"],
      "mobile fixed-asset repair should precheck quota before submitting"
    )
    assert.deepStrictEqual(findCall(calls, "submitFixedAssetRepair").args, [payload])
  }

  {
    const precheck = {
      allowed: false,
      errorCode: "FIXED_ASSET_QUOTA_EXCEEDED",
      message: "当前可用额度不足，不能上报；请按同款购买参考自行购买且无需上报",
      oeItemName: "盖碗",
      purchaseReferenceUrl: "https://shop.example.com/item/1001",
      purchaseReferenceNote: "白瓷100ml",
      purchaseReferenceReady: true
    }
    const { runtime, calls } = createStubRuntime({
      precheckFixedAssetRepair: { data: precheck }
    })
    let failure = null
    try {
      await runtime.saveMobileFeatureForm("fixedAssetRepair", {
        oeItemId: 1001,
        repairQuantity: 2,
        faultDescription: "破损"
      })
    } catch (error) {
      failure = error
    }
    assert.ok(failure, "over-quota mobile fixed-asset repair should be rejected")
    assert.strictEqual(failure.code, "FIXED_ASSET_QUOTA_EXCEEDED")
    assert.deepStrictEqual(failure.precheck, precheck)
    assert.strictEqual(
      calls.some(call => call.name === "submitFixedAssetRepair"),
      false,
      "over-quota precheck must prevent the report write request"
    )
  }

  {
    const precheck = { allowed: false, errorCode: "FIXED_ASSET_QUOTA_EXCEEDED", oeItemName: "茶杯" }
    assert.deepStrictEqual(
      getFixedAssetPrecheckFromError({ response: { data: { precheck } } }),
      precheck,
      "race-time backend quota errors should preserve the latest precheck payload"
    )
  }

  {
    const { runtime, calls } = createStubRuntime()
    await runtime.saveMobileFeatureForm("replenishment", {
      fromDeptId: 101,
      toDeptId: 202,
      transferType: "warehouse",
      submitAction: "submit",
      details: [{ productId: 5, quantity: 2 }]
    })
    assert.deepStrictEqual(
      findCall(calls, "submitTransfer").args,
      [{ fromDeptId: 101, toDeptId: 202, transferType: "warehouse", details: [{ productId: 5, quantity: 2 }] }],
      "mobile replenishment submit should create a transfer request instead of remaining a low-stock list"
    )
  }

  {
    const detailResponse = {
      data: {
        transferId: 50,
        transferType: "cross_store",
        sourceConfirmStatus: "PENDING",
        details: [
          { detailId: 501, productName: "茶叶", quantity: 7 },
          { detailId: 502, productName: "礼盒", quantity: 3 }
        ]
      }
    }
    const { runtime, calls } = createStubRuntime({
      getTransferDetail: detailResponse
    })

    await runtime.runMobileFeatureAction(
      "transfer",
      "confirmTransferSource",
      { _raw: { transferId: 50, status: "approved" } },
      {
        actionPayload: {
          items: [
            { detailId: 501, quantity: 5 },
            { detailId: 502, quantity: 3 }
          ],
          comment: "茶叶库存只够5件"
        }
      }
    )

    assert.deepStrictEqual(
      findCall(calls, "confirmTransferSource").args,
      [50, {
        items: [
          { detailId: 501, confirmedQuantity: 5 },
          { detailId: 502, confirmedQuantity: 3 }
        ],
        remark: "茶叶库存只够5件"
      }],
      "mobile source confirmation should preserve every line and the partial-confirmation reason"
    )

    await assert.rejects(
      runtime.runMobileFeatureAction(
        "transfer",
        "confirmTransferSource",
        { _raw: { transferId: 50, status: "approved" } },
        {
          actionPayload: {
            items: [
              { detailId: 501, quantity: 5 },
              { detailId: 502, quantity: 3 }
            ]
          }
        }
      ),
      /请填写说明/,
      "mobile source confirmation must require a reason whenever any quantity is returned"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getTransferDetail: {
        data: {
          transferId: 72,
          fromDeptId: 101,
          toDeptId: 202,
          transferType: "warehouse",
          details: [{ detailId: 1, productId: 5, quantity: 2 }]
        }
      }
    })
    await runtime.runMobileFeatureAction(
      "replenishment",
      "submitReplenishment",
      { _raw: { transferId: 72, status: "draft" } }
    )
    assert.deepStrictEqual(
      findCall(calls, "getTransferDetail").args,
      [72],
      "mobile replenishment draft submit should load the full transfer detail before submitting"
    )
    assert.deepStrictEqual(
      findCall(calls, "submitTransfer").args,
      [{
        transferId: 72,
        fromDeptId: 101,
        toDeptId: 202,
        transferType: "warehouse",
        details: [{ detailId: 1, productId: 5, quantity: 2 }]
      }],
      "mobile replenishment draft submit should call the desktop transfer submit API with full details"
    )
  }

  {
    const { runtime, calls } = createStubRuntime()
    await runtime.runMobileFeatureAction(
      "replenishment",
      "deleteReplenishmentDraft",
      { _raw: { transferId: 73, status: "draft" } }
    )
    assert.deepStrictEqual(
      findCall(calls, "deleteTransferDraft").args,
      [73],
      "mobile replenishment draft deletion should call the dedicated physical-delete API"
    )
  }

  {
    const { runtime, calls } = createStubRuntime()
    await runtime.runMobileFeatureAction(
      "purchase",
      "deletePurchaseDraft",
      { _raw: { orderId: 74, status: "draft" } }
    )
    assert.deepStrictEqual(
      findCall(calls, "deleteDraftPurchase").args,
      [74],
      "mobile purchase draft deletion should call the dedicated physical-delete API"
    )
  }

  {
    const { runtime, calls } = createStubRuntime()
    await runtime.runMobileFeatureAction(
      "stockCheck",
      "deleteStockCheckDraft",
      { _raw: { checkId: 75, status: "draft" } }
    )
    assert.deepStrictEqual(
      findCall(calls, "deleteStockCheck").args,
      [75],
      "mobile stock-check draft deletion should call the dedicated physical-delete API"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getTransferDetail: {
        data: {
          transferId: 76,
          fromDeptId: 101,
          toDeptId: 202,
          transferType: "warehouse",
          details: [{ detailId: 1, itemType: "product", itemId: 5, quantity: 2 }]
        }
      }
    })
    await runtime.runMobileFeatureAction(
      "transfer",
      "submitTransferDraft",
      { _raw: { transferId: 76, status: "draft" } }
    )
    await runtime.runMobileFeatureAction(
      "transfer",
      "deleteTransferDraft",
      { _raw: { transferId: 77, status: "draft" } }
    )
    assert.deepStrictEqual(findCall(calls, "getTransferDetail").args, [76])
    assert.deepStrictEqual(
      findCall(calls, "submitTransfer").args,
      [{
        transferId: 76,
        fromDeptId: 101,
        toDeptId: 202,
        transferType: "warehouse",
        details: [{ detailId: 1, itemType: "product", itemId: 5, quantity: 2 }]
      }],
      "mobile transfer draft submit should reload and submit the complete transfer"
    )
    assert.deepStrictEqual(
      calls.filter(call => call.name === "deleteTransferDraft").map(call => call.args),
      [[77]],
      "mobile transfer draft deletion should call the dedicated physical-delete API"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getPurchaseReceiveContext: {
        data: {
          warehouseId: 8,
          details: [
            { detailId: 1, quantity: 5, receivedQuantity: 1 },
            { detailId: 2, quantity: 2, receivedQuantity: 2 }
          ]
        }
      }
    })
    await runtime.runMobileFeatureAction(
      "purchase",
      "qualityCheckPurchase",
      { _raw: { orderId: 21, status: "arrived" } },
      { actionPayload: { qcResult: "rejected", comment: "外箱破损" } }
    )
    await runtime.runMobileFeatureAction(
      "purchase",
      "receivePurchaseAll",
      { _raw: { orderId: 21, status: "arrived" } },
      { receiveScope: { actor: "7", dept: "8" }, receiveObservedRequestId: null,
        actionPayload: {
          arrivedTime: "2026-07-29T10:30",
          supplierBatchNo: "SUP-20260729",
          deliveryNoteNo: "DN-20260729",
          remark: "外箱完好",
          items: [{ detailId: 1, quantity: 3 }]
        }
      }
    )
    assert.deepStrictEqual(
      findCall(calls, "qualityCheckPurchase").args,
      [21, { qcResult: "rejected", qcRemark: "外箱破损" }],
      "purchase QC should preserve mobile QC result and remark"
    )
    assert.deepStrictEqual(
      findCall(calls, "receivePurchase").args,
      [21, {
        warehouseId: "8",
        arrivedTime: "2026-07-29 10:30:00",
        items: [{ detailId: 1, receiveQuantity: 3 }],
        supplierBatchNo: "SUP-20260729",
        deliveryNoteNo: "DN-20260729",
        remark: "外箱完好"
      }, "receive:test-runtime", { actor: "7", dept: "8" }],
      "purchase receive should satisfy the backend DTO and preserve user-entered metadata and partial quantities"
    )
  }

  {
    const { runtime, calls } = createStubRuntime()
    const qualityItems = [{
      batchDetailId: 301,
      inspectedQuantity: 5,
      acceptedQuantity: 4,
      rejectedQuantity: 1,
      concessionQuantity: 0,
      defectReason: "外包装破损"
    }]
    await runtime.runMobileFeatureAction(
      "purchase",
      "qualityCheckPurchase",
      { _raw: { orderId: 22, status: "submitted" } },
      { actionPayload: { receiptBatchId: 201, qualityItems } }
    )
    assert.deepStrictEqual(
      findCall(calls, "qualityCheckPurchaseBatch").args,
      [22, { receiptBatchId: 201, items: qualityItems }],
      "mobile purchase QC should submit the selected receipt batch and line quantities"
    )
    assert.strictEqual(calls.some(call => call.name === "qualityCheckPurchase"), false)
  }

  {
    const { runtime, calls } = createStubRuntime()
    await assert.rejects(
      () => runtime.runMobileFeatureAction(
        "purchase",
        "qualityCheckPurchase",
        { _raw: { orderId: 23, status: "submitted" } },
        { actionPayload: {} }
      ),
      /请选择质检结果/,
      "legacy mobile QC must never default to passed"
    )
    assert.strictEqual(calls.some(call => call.name === "qualityCheckPurchase"), false)
  }

  {
    const { runtime, calls } = createStubRuntime({
      getPurchaseReceiveContext: {
        data: {
          warehouseId: 8,
          details: [
            { detailId: 1, quantity: 5, receivedQuantity: 1 }
          ]
        }
      }
    })
    let error = null
    try {
      await runtime.runMobileFeatureAction(
        "purchase",
        "receivePurchaseAll",
        { _raw: { orderId: 21, status: "arrived" } },
        { receiveScope: { actor: "7", dept: "8" }, receiveObservedRequestId: null, actionPayload: {} }
      )
    } catch (caught) {
      error = caught
    }
    assert.ok(error && /没有可收货明细/.test(error.message), "purchase receive should reject empty mobile item rows")
    assert.strictEqual(
      calls.some(call => call.name === "receivePurchase"),
      false,
      "purchase receive should not call the backend when the mobile dialog submitted no item rows"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getPurchaseReceiveContext: {
        data: {
          warehouseId: 8,
          details: [
            { detailId: 1, quantity: 5, receivedQuantity: 1 }
          ]
        }
      }
    })
    await assert.rejects(
      () => runtime.runMobileFeatureAction(
        "purchase",
        "receivePurchaseAll",
        { _raw: { orderId: 21, status: "arrived" } },
        { receiveScope: { actor: "7", dept: "8" }, receiveObservedRequestId: null, actionPayload: { items: [{ detailId: 1, quantity: 2 }] } }
      ),
      /请选择实际到货时间/,
      "mobile purchase receive must reject a request that omits the backend-required arrivedTime"
    )
    assert.strictEqual(
      calls.some(call => call.name === "receivePurchase"),
      false,
      "a purchase receive request without arrivedTime must never reach the backend"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getPurchaseReceiveContext: {
        data: {
          warehouseId: 8,
          details: [
            { detailId: 1, quantity: 5, receivedQuantity: 1 }
          ]
        }
      }
    })
    await runtime.runMobileFeatureAction(
      "purchase",
      "receivePurchaseAll",
      { _raw: { orderId: 21, status: "arrived" } },
      { receiveScope: { actor: "7", dept: "8" }, receiveObservedRequestId: null, actionPayload: { arrivedTime: "2026-07-29 11:20:30", allRemaining: true } }
    )
    assert.deepStrictEqual(
      findCall(calls, "receivePurchase").args,
      [21, {
        warehouseId: "8",
        arrivedTime: "2026-07-29 11:20:30",
        items: [{ detailId: 1, receiveQuantity: 4 }]
      }, "receive:test-runtime", { actor: "7", dept: "8" }],
      "purchase receive should still support an explicit all-remaining mobile confirmation"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getDeliveryNotice: {
        data: {
          warehouseId: 9,
          details: [
            { detailId: 31, warehouseId: 9, noticeQty: 6, deliveredQty: 2 }
          ]
        }
      }
    })
    await runtime.runMobileFeatureAction(
      "outbound",
      "deliverDeliveryNoticeAll",
      { _raw: { noticeId: 31, status: "pending" } },
      { actionPayload: { items: [{ detailId: 31, quantity: 4 }] } }
    )
    assert.deepStrictEqual(
      findCall(calls, "deliverDeliveryNotice").args,
      [31, { warehouseId: 9, items: [{ detailId: 31, deliverQuantity: 4 }] }],
      "outbound delivery should call delivery API with mobile quantity rows"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getDeliveryNotice: {
        data: {
          details: [
            { detailId: 41, warehouseId: 9, noticeQty: 6, deliveredQty: 2 },
            { detailId: 42, warehouseId: 10, noticeQty: 5, deliveredQty: 0 }
          ]
        }
      }
    })
    await runtime.runMobileFeatureAction(
      "outbound",
      "deliverDeliveryNoticeAll",
      { _raw: { noticeId: 32, status: "pending" } },
      {
        selectedDeptId: 9,
        actionPayload: {
          allRemaining: true,
          items: [
            { detailId: 41, quantity: 4 },
            { detailId: 42, quantity: 5 }
          ]
        }
      }
    )
    assert.deepStrictEqual(
      findCall(calls, "deliverDeliveryNotice").args,
      [32, { warehouseId: 9, items: [{ detailId: 41, deliverQuantity: 4 }] }],
      "multi-warehouse notices should submit only rows assigned to the selected warehouse"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getStockCheck: {
        data: {
          details: [
            { detailId: 41, productId: 100, bookQuantity: 10 }
          ]
        }
      }
    })
    await runtime.runMobileFeatureAction(
      "stockCheck",
      "saveStockCheckInput",
      { _raw: { checkId: 41, status: "draft" } },
      { actionPayload: { items: [{ detailId: 41, actualQuantity: 8 }] } }
    )
    await runtime.runMobileFeatureAction(
      "stockCheck",
      "submitStockCheck",
      { _raw: { checkId: 41, status: "rejected" } },
      { actionPayload: { items: [{ detailId: 41, actualQuantity: 8 }] } }
    )
    await runtime.runMobileFeatureAction(
      "stockCheck",
      "approveStockCheck",
      { _raw: { checkId: 41, approvalInstanceId: 77, status: "pending_approval" } },
      { actionPayload: { comment: "复核无误" } }
    )
    await runtime.runMobileFeatureAction(
      "stockCheck",
      "rejectStockCheck",
      { _raw: { checkId: 41, approvalInstanceId: 78, status: "pending_approval" } },
      { actionPayload: { comment: "差异说明不完整" } }
    )
    await runtime.runMobileFeatureAction(
      "stockCheck",
      "restartStockCheck",
      { _raw: { checkId: 41, status: "invalidated" } }
    )
    assert.deepStrictEqual(
      calls.filter(call => call.name === "inputStockCheck").map(call => call.args),
      [
        [41, { details: [{ detailId: 41, actualQty: 8 }] }],
        [41, { details: [{ detailId: 41, actualQty: 8 }] }]
      ],
      "stock check save and submit should persist the backend stock-check object shape"
    )
    assert.deepStrictEqual(
      findCall(calls, "submitStockCheck").args,
      [41],
      "stock check submit should call the approval-aware submit API"
    )
    assert.deepStrictEqual(
      findCall(calls, "approveStockCheck").args,
      [41, { instanceId: 77, comment: "复核无误" }],
      "stock check approval should carry the active instance"
    )
    assert.deepStrictEqual(
      findCall(calls, "rejectStockCheck").args,
      [41, { instanceId: 78, comment: "差异说明不完整" }],
      "stock check rejection should carry its reason and active instance"
    )
    assert.deepStrictEqual(
      findCall(calls, "restartStockCheck").args,
      [41],
      "invalidated stock checks should call restart"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getTransferDetail: {
        data: {
          details: [
            { detailId: 51, quantity: 7, deliveredQuantity: 2 }
          ],
          shipments: [
            {
              shipmentId: 501,
              status: "pending_receive",
              details: [{ transferDetailId: 51, shippedQuantity: 5, receivedQuantity: 1 }]
            },
            {
              shipmentId: 502,
              status: "pending_receive",
              details: [{ transferDetailId: 52, shippedQuantity: 3, receivedQuantity: 0 }]
            }
          ]
        }
      }
    })
    await runtime.runMobileFeatureAction(
      "transfer",
      "approveTransfer",
      { _raw: { transferId: 51, currentTaskId: "TF-51", status: "submitted" } },
      { actionPayload: { comment: "同意调拨" } }
    )
    await runtime.runMobileFeatureAction(
      "transfer",
      "deliverTransferAll",
      { _raw: { transferId: 51, status: "approved" } },
      { actionPayload: { items: [{ detailId: 51, quantity: 5 }] } }
    )
    await runtime.runMobileFeatureAction(
      "transfer",
      "receiveTransferShipment",
      { _raw: { transferId: 51, status: "delivered" } },
      {
        actionPayload: {
          shipmentId: 502,
          items: [{
            transferDetailId: 52,
            quantity: 2,
            discrepancyNote: "本批短少1件"
          }]
        }
      }
    )
    assert.deepStrictEqual(
      findCall(calls, "approveTransfer").args,
      [{ transferId: 51, taskId: "TF-51", action: "approve", comment: "同意调拨" }],
      "transfer approval should include task id and comment"
    )
    assert.deepStrictEqual(
      findCall(calls, "deliverTransfer").args,
      [51, { items: [{ detailId: 51, deliverQuantity: 5 }] }],
      "transfer delivery should submit selected remaining quantities"
    )
    assert.deepStrictEqual(
      findCall(calls, "receiveShipment").args,
      [502, {
        items: [{
          detailId: 52,
          receiveQuantity: 2,
          discrepancyNote: "本批短少1件"
        }]
      }],
      "transfer receiving should use the selected shipment batch and preserve discrepancy evidence"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getTransferDetail: {
        data: {
          transferId: 61,
          status: "discrepancy",
          discrepancies: [
            {
              discrepancyId: 701,
              discrepancyNo: "CY-701",
              status: "OPEN",
              version: 4
            }
          ]
        }
      }
    })
    await runtime.runMobileFeatureAction(
      "transfer",
      "handleTransferDiscrepancy",
      { _raw: { transferId: 61, status: "discrepancy" } },
      {
        actionPayload: {
          discrepancyId: 701,
          requestId: "ITD:701:runtime-test",
          responsibleParty: "LOGISTICS",
          comment: "运输途中外箱破损，退回来源仓",
          items: [{
            detailId: 711,
            category: "REJECTED",
            decision: "RETURN_SOURCE",
            quantity: 1,
            note: "外箱破损",
            attachmentRefs: "node-701"
          }]
        }
      }
    )
    assert.deepStrictEqual(
      findCall(calls, "getTransferDetail").args,
      [61],
      "mobile discrepancy handling must reload the transfer before mutating it"
    )
    assert.deepStrictEqual(
      findCall(calls, "resolveTransferDiscrepancy").args,
      [701, {
        requestId: "ITD:701:runtime-test",
        version: 4,
        responsibleParty: "LOGISTICS",
        note: "运输途中外箱破损，退回来源仓",
        items: [{
          detailId: 711,
          category: "REJECTED",
          decision: "RETURN_SOURCE",
          quantity: 1,
          note: "外箱破损",
          attachmentRefs: "node-701"
        }]
      }],
      "mobile discrepancy handling must submit the freshly loaded version and explicit business decision"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getTransferDetail: {
        data: {
          transferId: 62,
          status: "discrepancy",
          discrepancies: [{ discrepancyId: 702, status: "RESOLVED", version: 5 }]
        }
      }
    })
    await assert.rejects(
      () => runtime.runMobileFeatureAction(
        "transfer",
        "handleTransferDiscrepancy",
        { _raw: { transferId: 62, status: "discrepancy" } },
        {
          actionPayload: {
            discrepancyId: 702,
            requestId: "ITD:702:stale-test",
            responsibleParty: "SOURCE",
            note: "安排补发",
            items: [{
              detailId: 712,
              category: "SHORTAGE",
              decision: "RESHIP",
              quantity: 1
            }]
          }
        }
      ),
      /差异单已被处理/,
      "a stale mobile discrepancy todo must not mutate a resolved discrepancy"
    )
    assert.strictEqual(
      calls.some(call => call.name === "resolveTransferDiscrepancy"),
      false,
      "resolved discrepancies must never reach the write API"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      getTransferDetail: {
        data: {
          details: [
            { detailId: 51, quantity: 7, deliveredQuantity: 2 }
          ]
        }
      }
    })
    let error = null
    try {
      await runtime.runMobileFeatureAction(
        "transfer",
        "deliverTransferAll",
        { _raw: { transferId: 51, status: "approved" } },
        { actionPayload: {} }
      )
    } catch (caught) {
      error = caught
    }
    assert.ok(error && /没有可出库明细/.test(error.message), "transfer delivery should reject empty mobile item rows")
    assert.strictEqual(
      calls.some(call => call.name === "deliverTransfer"),
      false,
      "transfer delivery should not call the backend when the mobile dialog submitted no item rows"
    )
  }

  {
    const { runtime, calls } = createStubRuntime()
    await runtime.runMobileFeatureAction(
      "replenishment",
      "approveTransfer",
      { _raw: { transferId: 60, approvalEngine: "LEGACY", status: "submitted" } },
      { actionPayload: { comment: "同意补货" } }
    )
    assert.deepStrictEqual(
      findCall(calls, "approveTransfer").args,
      [{ transferId: 60, taskId: undefined, action: "approve", comment: "同意补货" }],
      "replenishment detail approval should reuse the transfer approval API and let the backend resolve the current candidate task"
    )
  }

  {
    const { runtime, calls } = createStubRuntime()
    await runtime.runMobileFeatureAction(
      "transferApproval",
      "approveTransfer",
      { _raw: { transferId: 61, taskId: 601, status: "submitted" } },
      { actionPayload: { comment: "同意" } }
    )
    assert.deepStrictEqual(
      findCall(calls, "approveTransfer").args,
      [{ transferId: 61, taskId: 601, action: "approve", comment: "同意" }],
      "transfer approval center approval should call transfer approval API with the task id"
    )
  }

  {
    const { runtime, calls } = createStubRuntime({
      listNoticeTop: {
        data: [
          { noticeId: 5, isRead: true },
          { noticeId: 4, isRead: true }
        ]
      },
      markNoticeReadAll: { unreadCount: 0, msg: "操作成功" }
    })

    const response = await runtime.runMobileFeatureAction("notice", "markAllNoticeRead")

    assert.strictEqual(
      calls.some(call => call.name === "listNoticeTop"),
      false,
      "mobile mark-all should not use the top-five read state to decide whether older notices exist"
    )
    assert.deepStrictEqual(
      calls.filter(call => call.name === "markNoticeReadAll").map(call => call.args),
      [[]],
      "mobile mark-all should call the database-level API exactly once without notice ids"
    )
    assert.strictEqual(response.unreadCount, 0, "mobile mark-all should preserve the authoritative unread count")
    assert.strictEqual(response.msg, "已全部标记为已读", "zero remaining notices should use the existing all-read success text")
  }

  {
    const { runtime, calls } = createStubRuntime({
      listNoticeTop: { data: [] },
      markNoticeReadAll: { unreadCount: 3, msg: "操作成功" }
    })

    const response = await runtime.runMobileFeatureAction("notice", "markAllNoticeRead")

    assert.strictEqual(
      calls.some(call => call.name === "listNoticeTop"),
      false,
      "an empty top list must not suppress the global mark-all request"
    )
    assert.deepStrictEqual(findCall(calls, "markNoticeReadAll").args, [])
    assert.ok(
      response.msg.includes("3") && !response.msg.includes("全部标记"),
      "a non-zero authoritative unread count must not claim that every notice was marked read"
    )
  }

  {
    const failure = new Error("全部已读请求失败")
    const { runtime, calls } = createStubRuntime({
      markNoticeReadAll: () => Promise.reject(failure)
    })
    let caught = null

    try {
      await runtime.runMobileFeatureAction("notice", "markAllNoticeRead")
    } catch (error) {
      caught = error
    }

    assert.strictEqual(caught, failure, "mobile mark-all should keep backend failures rejected")
    assert.strictEqual(calls.filter(call => call.name === "markNoticeReadAll").length, 1)
    assert.strictEqual(
      calls.some(call => call.name === "listNoticeTop"),
      false,
      "a failed mark-all request should not be replaced with a local top-list success"
    )
  }

  {
    const { runtime, calls } = createStubRuntime()
    await assert.rejects(
      runtime.runMobileFeatureAction("attendance", "checkInAttendance"),
      /unsupported mobile action/,
      "legacy generic attendance actions must fail closed"
    )
    assert.strictEqual(calls.length, 0, "a retired attendance action must not call any legacy API")
  }
})().then(() => console.log("mobile feature action runtime regression passed")).catch(error => {
  console.error(error)
  process.exit(1)
})
