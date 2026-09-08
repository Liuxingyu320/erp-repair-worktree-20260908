const assert = require("assert")

const {
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
} = require("../src/views/mobile/feature/mobileFeatureStatusPolicy")

assert.strictEqual(statusLabel("submitted", STATUS_LABELS.sales), "待发货")
assert.strictEqual(statusLabel("FUTURE_STATUS", STATUS_LABELS.sales), "未知状态")
assert.strictEqual(statusLabel("", STATUS_LABELS.sales), "-")
assert.strictEqual(statusLabel(0, STATUS_LABELS.product), "正常")

assert.strictEqual(stockStatusLabel(null), "-")
assert.strictEqual(stockStatusLabel("invalid"), "-")
assert.strictEqual(stockStatusLabel(0), "缺货")
assert.strictEqual(stockStatusLabel(10), "低库存")
assert.strictEqual(stockStatusLabel(10.01), "正常")

assert.strictEqual(movementTypeLabel("purchase_in"), "采购入库")
assert.strictEqual(movementTypeLabel("future_type"), "其他库存变动")
assert.strictEqual(movementTypeLabel(""), "")
assert.strictEqual(businessTypeLabel(7), "强退")
assert.strictEqual(businessTypeLabel(99), "其他操作")
assert.strictEqual(deptTypeLabel("WAREHOUSE"), "仓库")
assert.strictEqual(deptTypeLabel("REGION"), "其他组织")
assert.strictEqual(configTypeLabel("Y"), "系统内置")
assert.strictEqual(configTypeLabel(""), "其他参数")
assert.strictEqual(menuTypeLabel("C", "1"), "菜单")
assert.strictEqual(menuTypeLabel("C", "0"), "外链")
assert.strictEqual(menuTypeLabel("", "1"), "")
assert.strictEqual(transferTypeLabel("cross_store"), "异店调货")
assert.strictEqual(transferTypeLabel("future_type"), "其他调拨类型")
assert.strictEqual(transferSourceConfirmLabel("partial"), "已部分确认")
assert.strictEqual(transferSourceConfirmLabel("future_status"), "")
assert.strictEqual(exceptionTypeLabel("special_extra"), "特殊追加额度")
assert.strictEqual(exceptionTypeLabel("future_type"), "其他额度例外")
assert.strictEqual(dataScopeLabel("4"), "本部门及以下")
assert.strictEqual(dataScopeLabel("9"), "未知数据范围")
assert.strictEqual(dataScopeLabel(""), "")

assert.strictEqual(unifiedApprovalStatusLabel("callback_replay"), "回调重试")
assert.strictEqual(unifiedApprovalStatusLabel("future_status"), "未知状态")
assert.strictEqual(unifiedApprovalStatusLabel(""), "-")
assert.strictEqual(approvalNodeStateLabel("auto_approved"), "自动通过")
assert.strictEqual(approvalNodeStateLabel("future_state"), "待确认")
assert.strictEqual(approvalRoundStateLabel("cancelled"), "已终止")
assert.strictEqual(approvalRoundStateLabel("future_state"), "无人工节点")
assert.strictEqual(shipmentStatusLabel("pending_receive"), "待收货")
assert.strictEqual(shipmentStatusLabel("future_status"), "未知发货状态")
assert.strictEqual(shipmentStatusLabel(""), "")

console.log("mobile feature status policy tests passed")
