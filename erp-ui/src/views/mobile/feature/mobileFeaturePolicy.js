const featureDefaults = {
  title: "手机工作台",
  heading: "移动处理",
  subtitle: "当前模块正在接入手机网页端工作流",
  icon: "cube",
  tone: "blue",
  listTitle: "待处理",
  actions: [
    { label: "新建", icon: "plus" },
    { label: "查询", icon: "search" }
  ]
}

function applyContextualMobileCopy(feature, selectedDeptType) {
  const contextCopy = feature && feature.contextCopy
  const copy = contextCopy && selectedDeptType ? contextCopy[selectedDeptType] : null
  return copy ? Object.assign({}, feature, copy) : feature
}

function applyContextualActionLabels(actions, contextActionLabels, selectedDeptType) {
  return (actions || []).map(action => {
    if (!action || !action.contextualLabel) return action
    const labelGroup = contextActionLabels && contextActionLabels[action.contextualLabel]
    const label = labelGroup && selectedDeptType ? labelGroup[selectedDeptType] : ""
    return label ? Object.assign({}, action, { label }) : action
  })
}

const FEATURE_ACTION_FALLBACKS = {
  oaPurchase: [
    { label: "全部申请", icon: "search", query: {} },
    { label: "草稿", icon: "document", query: { status: "draft" } },
    { label: "审批中", icon: "check", query: { status: "pending" } },
    { label: "已通过", icon: "check", query: { status: "approved" } },
    { label: "已退回", icon: "return", query: { status: "returned" } },
    { label: "已拒绝", icon: "close", query: { status: "rejected" } }
  ],
  salary: [
    { label: "计算本月", icon: "trend", actionId: "calculateSalary" },
    { label: "全部工资", icon: "search", query: { salaryMonthScope: "all" } }
  ]
}

const MOBILE_FORM_CREATE_PERMISSIONS = {
  sales: ["inv:sales:add"],
  purchase: ["inv:purchase:add"],
  salesReturn: ["inv:salesReturn:add"],
  purchaseReturn: ["inv:purchaseReturn:add"],
  stockCheck: ["inv:stockCheck:add"],
  replenishment: ["inv:transfer:add"],
  transfer: ["inv:transfer:add"],
  customer: ["inv:customerCard:add"],
  oaPurchase: ["oa:purchase:add"],
  fixedAssetRepair: ["oa:fixedAsset:repair:add"]
}

const MOBILE_FORM_EDIT_PERMISSIONS = {
  sales: ["inv:sales:add"],
  purchase: ["inv:purchase:add"],
  salesReturn: ["inv:salesReturn:add"],
  purchaseReturn: ["inv:purchaseReturn:add"],
  replenishment: ["inv:transfer:add", "inv:transfer:edit"],
  transfer: ["inv:transfer:add", "inv:transfer:edit"],
  customer: ["inv:customerCard:edit"],
  oaPurchase: ["oa:purchase:add"]
}

const MOBILE_FORM_SUBMIT_PERMISSIONS = {
  sales: {
    save: ["inv:sales:add"],
    submit: ["inv:sales:submit"]
  },
  purchase: {
    save: ["inv:purchase:add"],
    submit: ["inv:purchase:submit"]
  },
  salesReturn: {
    save: ["inv:salesReturn:add"],
    submit: ["inv:salesReturn:submit"]
  },
  purchaseReturn: {
    save: ["inv:purchaseReturn:add"],
    submit: ["inv:purchaseReturn:submit"]
  },
  stockCheck: {
    save: ["inv:stockCheck:add"],
    submit: ["inv:stockCheck:submit"]
  },
  replenishment: {
    save: ["inv:transfer:add", "inv:transfer:edit"],
    submit: ["inv:transfer:submit"]
  },
  transfer: {
    save: ["inv:transfer:add", "inv:transfer:edit"],
    submit: ["inv:transfer:submit"]
  },
  customer: {
    save: ["inv:customerCard:add", "inv:customerCard:edit"]
  },
  oaPurchase: {
    save: ["oa:purchase:add"],
    submit: ["oa:purchase:add"]
  },
  fixedAssetRepair: {
    save: ["oa:fixedAsset:repair:add"],
    submit: ["oa:fixedAsset:repair:add"]
  }
}

const MOBILE_ACTION_PERMISSIONS = {
  submitSales: ["inv:sales:submit"],
  createDeliveryNotice: ["inv:deliveryNotice:add"],
  cancelSales: ["inv:sales:remove"],
  submitPurchase: ["inv:purchase:submit"],
  deletePurchaseDraft: ["inv:purchase:remove"],
  qualityCheckPurchase: ["inv:purchase:qc"],
  receivePurchaseAll: ["inv:purchase:receive"],
  cancelPurchase: ["inv:purchase:remove"],
  submitSalesReturn: ["inv:salesReturn:submit"],
  confirmSalesReturn: ["inv:salesReturn:confirm"],
  cancelSalesReturn: ["inv:salesReturn:remove"],
  submitPurchaseReturn: ["inv:purchaseReturn:submit"],
  confirmPurchaseReturn: ["inv:purchaseReturn:confirm"],
  cancelPurchaseReturn: ["inv:purchaseReturn:remove"],
  deliverDeliveryNoticeAll: ["inv:deliveryNotice:deliver"],
  cancelDeliveryNotice: ["inv:deliveryNotice:remove"],
  saveStockCheckInput: ["inv:stockCheck:submit"],
  submitStockCheck: ["inv:stockCheck:submit"],
  deleteStockCheckDraft: ["inv:stockCheck:remove"],
  approveStockCheck: ["inv:stockCheck:approve"],
  returnStockCheck: ["inv:stockCheck:approve"],
  rejectStockCheck: ["inv:stockCheck:approve"],
  restartStockCheck: ["inv:stockCheck:submit"],
  withdrawStockCheck: ["inv:stockCheck:submit"],
  cancelStockCheck: ["inv:stockCheck:remove"],
  submitReplenishment: ["inv:transfer:submit"],
  deleteReplenishmentDraft: ["inv:transfer:remove"],
  submitTransferDraft: ["inv:transfer:submit"],
  deleteTransferDraft: ["inv:transfer:remove"],
  approveTransfer: ["inv:transfer:approve"],
  returnTransfer: ["inv:transfer:approve"],
  rejectTransfer: ["inv:transfer:approve"],
  confirmTransferSource: ["inv:transfer:deliver"],
  deliverTransferAll: ["inv:transfer:deliver"],
  receiveTransferShipment: ["inv:transfer:receive"],
  handleTransferDiscrepancy: ["inv:transfer:discrepancy:handle"],
  withdrawTransfer: ["inv:transfer:submit"],
  cancelTransfer: ["inv:transfer:remove"],
  addCustomerServiceRecord: ["inv:customerCard:record:add"],
  previewCustomerPhoto: ["inv:customerCard:query"],
  submitOaPurchase: ["oa:purchase:add"],
  calculateSalary: ["oa:salary:calculate"],
  runMonitorJob: ["monitor:job:changeStatus"],
  pauseMonitorJob: ["monitor:job:changeStatus"],
  resumeMonitorJob: ["monitor:job:changeStatus"],
  forceLogoutOnline: ["monitor:online:forceLogout"]
}

module.exports = {
  FEATURE_ACTION_FALLBACKS,
  MOBILE_ACTION_PERMISSIONS,
  MOBILE_FORM_CREATE_PERMISSIONS,
  MOBILE_FORM_EDIT_PERMISSIONS,
  MOBILE_FORM_SUBMIT_PERMISSIONS,
  applyContextualActionLabels,
  applyContextualMobileCopy,
  featureDefaults
}
