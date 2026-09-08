const STATUS_LABELS = {
  sales: {
    draft: "草稿",
    submitted: "待发货",
    noticed: "已通知",
    delivered: "已出库",
    cancelled: "已取消"
  },
  purchase: {
    draft: "草稿",
    submitted: "待入库",
    received: "已入库",
    cancelled: "已取消"
  },
  product: {
    "0": "正常",
    "1": "停用"
  },
  oe: {
    "0": "正常",
    "1": "停用"
  },
  gift: {
    "0": "正常",
    "1": "停用"
  },
  customer: {
    "0": "正常",
    "1": "停用"
  },
  supplier: {
    "0": "合作中",
    "1": "暂停",
    "2": "终止"
  },
  category: {
    "0": "正常",
    "1": "停用"
  },
  stockCheck: {
    draft: "草稿",
    checking: "待确认",
    pending_approval: "待审批",
    returned: "已退回（可修改）",
    rejected: "已驳回（可修改）",
    invalidated: "库存已变化（需重盘）",
    completed: "已完成",
    cancelled: "已取消"
  },
  salesReturn: {
    draft: "草稿",
    submitted: "待确认",
    confirmed: "已确认",
    returned: "已退货",
    cancelled: "已取消"
  },
  purchaseReturn: {
    draft: "草稿",
    submitted: "待确认",
    returned: "已退货",
    confirmed: "已退货",
    cancelled: "已取消"
  },
  outbound: {
    pending: "待发货",
    submitted: "待发货",
    delivering: "发货中",
    completed: "已发货",
    cancelled: "已取消"
  },
  transfer: {
    draft: "草稿",
    submitted: "待审批",
    reserved: "待出库",
    approved: "待出库",
    partial_delivered: "部分出库",
    outbound: "待收货",
    delivered: "待收货",
    partial_received: "部分收货",
    received: "已完成",
    completed: "已完成",
    rejected: "已驳回",
    closed: "已关闭",
    discrepancy: "异常",
    cancelled: "已取消"
  },
  transferApproval: {
    submitted: "待审批"
  },
  fixedAssetRepair: {
    draft: "草稿",
    pending_confirm: "历史待确认（只读）",
    submitted: "已上报",
    rejected: "已驳回",
    cancelled: "已取消"
  },
  oaPurchase: {
    draft: "草稿",
    submitting: "提交中",
    pending: "审批中",
    approved: "已通过",
    rejected: "已驳回",
    returned: "已退回",
    withdrawn: "已撤回",
    terminated: "已终止",
    cancelled: "已关闭"
  },
  attendance: {
    normal: "正常",
    pending_checkout: "未签退",
    late: "迟到",
    early: "早退",
    late_and_early: "迟到+早退",
    absent: "缺勤",
    leave: "请假"
  },
  salary: {
    draft: "草稿",
    confirmed: "已确认"
  },
  notice: {
    "1": "通知",
    "2": "公告"
  },
  profile: {
    "0": "可用",
    "1": "停用"
  },
  systemCommon: {
    "0": "正常",
    "1": "停用"
  },
  logStatus: {
    "0": "成功",
    "1": "失败"
  },
  operStatus: {
    "0": "成功",
    "1": "失败"
  },
  jobStatus: {
    "0": "正常",
    "1": "暂停"
  }
}

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== ""
}

function toNumber(value) {
  if (!hasValue(value)) return null
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

function statusLabel(status, labels) {
  if (!hasValue(status)) return "-"
  const key = String(status)
  return labels && labels[key] ? labels[key] : "未知状态"
}

function stockStatusLabel(value) {
  const numberValue = toNumber(value)
  if (numberValue === null) return "-"
  if (numberValue <= 0) return "缺货"
  if (numberValue <= 10) return "低库存"
  return "正常"
}

function movementTypeLabel(type) {
  const labels = {
    purchase_in: "采购入库",
    purchase_return_out: "采购退货出库",
    sales_out: "销售出库",
    sales_return_in: "销售退货入库",
    transfer_in: "调拨入库",
    transfer_out: "调拨出库",
    stock_check_profit: "盘盈入库",
    stock_check_loss: "盘亏出库",
    adjustment: "库存调整",
    outbound_out: "出库",
    purchase_return: "采购退货",
    sales_return: "销售退货",
    check: "库存盘点"
  }
  return labels[type] || (hasValue(type) ? "其他库存变动" : "")
}

function businessTypeLabel(type) {
  const labels = {
    0: "其它",
    1: "新增",
    2: "修改",
    3: "删除",
    4: "授权",
    5: "导出",
    6: "导入",
    7: "强退",
    8: "生成代码",
    9: "清空数据"
  }
  return labels[String(type)] || (hasValue(type) ? "其他操作" : "")
}

function deptTypeLabel(type) {
  const labels = {
    STORE: "门店",
    WAREHOUSE: "仓库",
    DEPT: "部门",
    GROUP: "集团",
    COMPANY: "公司"
  }
  return labels[type] || (hasValue(type) ? "其他组织" : "")
}

function configTypeLabel(type) {
  const labels = {
    Y: "系统内置",
    N: "自定义"
  }
  return labels[type] || "其他参数"
}

function menuTypeLabel(type, isFrame) {
  if (isFrame === "0") return "外链"
  const labels = {
    M: "目录",
    C: "菜单",
    F: "按钮"
  }
  return labels[type] || (hasValue(type) ? "其他菜单类型" : "")
}

function transferTypeLabel(type) {
  const labels = {
    warehouse: "门店要货",
    store_return: "门店返仓",
    cross_store: "异店调货",
    store: "门店调货",
    oe: "OE 补货"
  }
  return labels[type] || (hasValue(type) ? "其他调拨类型" : "")
}

function transferSourceConfirmLabel(status) {
  return {
    NOT_REQUIRED: "无需确认",
    NOT_STARTED: "尚未开始",
    PENDING: "待调出店确认",
    CONFIRMED: "已全量确认",
    PARTIAL: "已部分确认",
    REJECTED: "无法调出",
    RESELECT_REQUIRED: "待重选调出店"
  }[String(status || "").toUpperCase()] || ""
}

function exceptionTypeLabel(type) {
  const labels = {
    advance_future_months: "透支未来额度",
    special_extra: "特殊追加额度"
  }
  return labels[type] || (hasValue(type) ? "其他额度例外" : "")
}

function dataScopeLabel(value) {
  const labels = {
    "1": "全部数据",
    "2": "自定义范围",
    "3": "本部门",
    "4": "本部门及以下",
    "5": "仅本人"
  }
  const key = String(value || "")
  return labels[key] || (key ? "未知数据范围" : "")
}

function unifiedApprovalStatusLabel(status) {
  const value = String(status || "").toUpperCase()
  const labels = {
    RUNNING: "审批中", WAITING: "等待中", COMPLETING: "完成回调中", RETURNING: "退回回调中",
    REJECTING: "拒绝回调中", WITHDRAWING: "撤回回调中", TERMINATING: "终止回调中",
    PENDING: "待处理", PROCESSING: "处理中", NONE: "无需回调", APPROVED: "已通过",
    RETURNED: "已退回", REJECTED: "已拒绝", WITHDRAWN: "已撤回", TERMINATED: "已终止",
    INVALIDATED: "已失效", SUCCESS: "成功", SUCCEEDED: "成功", RETRY: "待重试", FAILED: "失败",
    DEAD: "死信", PASSED: "通过", ERROR: "错误", WARNING: "警告", APPROVE: "同意",
    RETURN: "退回", REJECT: "拒绝", START: "发起", WITHDRAW: "撤回", TERMINATE: "终止",
    REASSIGN: "改派", REASSIGNED: "已改派", SKIP: "跳过", SKIPPED: "已跳过",
    CANCELLED: "已取消", CALLBACK: "业务回调", CALLBACK_REPLAY: "回调重试"
  }
  return labels[value] || (value ? "未知状态" : "-")
}

function approvalNodeStateLabel(state) {
  const labels = {
    approved: "已通过",
    current: "当前节点",
    waiting: "等待中",
    rejected: "已驳回",
    terminated: "已终止",
    auto_approved: "自动通过",
    partial: "数据不完整"
  }
  return labels[state] || "待确认"
}

function approvalRoundStateLabel(state) {
  const labels = {
    in_progress: "审批中",
    approved: "已通过",
    rejected: "已驳回",
    cancelled: "已终止",
    partial: "数据不完整"
  }
  return labels[state] || "无人工节点"
}

function shipmentStatusLabel(status) {
  const labels = {
    pending_receive: "待收货",
    received: "已收货",
    abnormal: "异常"
  }
  return labels[status] || (hasValue(status) ? "未知发货状态" : "")
}

module.exports = {
  STATUS_LABELS,
  approvalNodeStateLabel,
  approvalRoundStateLabel,
  businessTypeLabel,
  configTypeLabel,
  dataScopeLabel,
  deptTypeLabel,
  exceptionTypeLabel,
  menuTypeLabel,
  movementTypeLabel,
  shipmentStatusLabel,
  statusLabel,
  stockStatusLabel,
  transferSourceConfirmLabel,
  transferTypeLabel,
  unifiedApprovalStatusLabel
}
