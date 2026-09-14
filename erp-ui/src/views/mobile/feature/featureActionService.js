import { getSalesDetail, saveSales, submitSales, cancelSales } from "@/api/inventory/sales"
import { getPurchaseDetail, getPurchaseReceiveContext, submitPurchaseDraft, savePurchase as saveInventoryPurchase, submitPurchase, qualityCheckPurchase, listPendingReceiptBatches, qualityCheckPurchaseBatch, cancelPurchase, deleteDraftPurchase } from "@/api/inventory/purchase"
import { createDeliveryNotice, getDeliveryNotice, deliverDeliveryNotice, cancelDeliveryNotice } from "@/api/inventory/deliveryNotice"
import { getSalesReturn, submitSalesReturnDraft, saveSalesReturn, submitSalesReturn, confirmSalesReturn, cancelSalesReturn } from "@/api/inventory/salesReturn"
import { getPurchaseReturn, submitPurchaseReturnDraft, savePurchaseReturn, submitPurchaseReturn, confirmPurchaseReturn, cancelPurchaseReturn } from "@/api/inventory/purchaseReturn"
import {
  createStockCheck,
  getStockCheck,
  inputStockCheck,
  submitStockCheck,
  restartStockCheck,
  approveStockCheck,
  rejectStockCheck,
  cancelStockCheck,
  withdrawStockCheck,
  deleteStockCheck
} from "@/api/inventory/stockCheck"
import {
  getTransferDetail,
  saveTransfer,
  submitTransfer,
  approveTransfer,
  confirmTransferSource,
  deliverTransfer,
  receiveShipment,
  resolveTransferDiscrepancy,
  cancelTransfer,
  withdrawTransfer,
  deleteTransferDraft
} from "@/api/inventory/transfer"
import { getPurchaseDetail as getOaPurchaseDetail, savePurchase as saveOaPurchaseApi, submitPurchase as submitOaPurchaseApi } from "@/api/oa/purchase"
import { precheckFixedAssetRepair, submitFixedAssetRepair } from "@/api/oa/fixedAsset"
import { calculateSalary } from "@/api/oa/salary"
import { markNoticeRead, markNoticeReadAll } from "@/api/system/notice"
import { runJob, changeJobStatus } from "@/api/monitor/job"
import { forceLogout } from "@/api/monitor/online"
import { createCustomerServiceCard, updateCustomerServiceCard, addCustomerServiceRecord } from "@/api/inventory/customer"
import { approveApprovalTask, rejectApprovalTask, returnApprovalTask } from "@/api/approval/task"

const { createMobileActionRuntime } = require("./featureActionRuntime")

const { getPurchaseReceiveRecovery } = require("@/utils/purchaseReceiveRecovery")

const runtime = createMobileActionRuntime({
  purchaseReceiveRecovery: getPurchaseReceiveRecovery(),
  getSalesDetail,
  saveSales,
  submitSales,
  cancelSales,
  getPurchaseDetail,
  getPurchaseReceiveContext,
  submitPurchaseDraft,
  saveInventoryPurchase,
  submitPurchase,
  qualityCheckPurchase,
  listPendingReceiptBatches,
  qualityCheckPurchaseBatch,
  cancelPurchase,
  deleteDraftPurchase,
  createDeliveryNotice,
  getDeliveryNotice,
  deliverDeliveryNotice,
  cancelDeliveryNotice,
  getSalesReturn,
  submitSalesReturnDraft,
  saveSalesReturn,
  submitSalesReturn,
  confirmSalesReturn,
  cancelSalesReturn,
  getPurchaseReturn,
  submitPurchaseReturnDraft,
  savePurchaseReturn,
  submitPurchaseReturn,
  confirmPurchaseReturn,
  cancelPurchaseReturn,
  createStockCheck,
  getStockCheck,
  inputStockCheck,
  submitStockCheck,
  restartStockCheck,
  approveStockCheck,
  rejectStockCheck,
  cancelStockCheck,
  withdrawStockCheck,
  deleteStockCheck,
  getTransferDetail,
  saveTransfer,
  submitTransfer,
  approveTransfer,
  confirmTransferSource,
  deliverTransfer,
  receiveShipment,
  resolveTransferDiscrepancy,
  cancelTransfer,
  withdrawTransfer,
  deleteTransferDraft,
  getOaPurchaseDetail,
  saveOaPurchaseApi,
  submitOaPurchaseApi,
  precheckFixedAssetRepair,
  submitFixedAssetRepair,
  calculateSalary,
  markNoticeRead,
  markNoticeReadAll,
  runJob,
  changeJobStatus,
  forceLogout,
  createCustomerServiceCard,
  updateCustomerServiceCard,
  addCustomerServiceRecord,
  approveApprovalTask,
  rejectApprovalTask,
  returnApprovalTask
})

export function runMobileFeatureAction(featureKey, actionId, item, options = {}) {
  return runtime.runMobileFeatureAction(featureKey, actionId, item, options)
}

export function saveMobileFeatureForm(featureKey, data, options) {
  return runtime.saveMobileFeatureForm(featureKey, data, options)
}
