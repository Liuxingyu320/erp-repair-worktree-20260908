const assert = require("assert")

const {
  buildMobileDetailFields,
  buildMobileReviewFields,
  isNoticeRead,
  resolveTransferApprovalNodeName,
  transferBusinessTypeLabel
} = require("../src/views/mobile/feature/mobileFeatureDetailFieldsPolicy")

const stockRow = {
  productCode: "TEA-001",
  productName: "明前龙井",
  unit: "盒",
  currentQuantity: 6,
  availableQuantity: 5,
  costPrice: 128,
  totalCost: 768
}
const stockItem = { title: "明前龙井", detail: "可用 5 盒" }

assert.ok(
  !buildMobileDetailFields("stock", stockRow, stockItem).some(field => field.label.includes("成本")),
  "stock cost fields should stay hidden without inv:cost:view"
)
assert.deepStrictEqual(
  buildMobileDetailFields("stock", stockRow, stockItem, {
    permissions: ["inv:cost:view"]
  }).filter(field => field.label.includes("成本")),
  [
    { label: "参考成本价", value: "¥128.00" },
    { label: "库存总成本", value: "¥768.00" }
  ],
  "the dedicated permission should reveal both reference and aggregate stock cost"
)

assert.deepStrictEqual(
  buildMobileDetailFields("notice", {
    noticeType: "1",
    createBy: "admin",
    createTime: "2026-07-31 10:00:00",
    isRead: "1",
    noticeContent: "<p>系统<strong>维护</strong></p>"
  }, { detail: "系统维护" }),
  [
    { label: "类型", value: "通知" },
    { label: "发布人", value: "admin" },
    { label: "发布时间", value: "2026-07-31 10:00:00" },
    { label: "阅读状态", value: "已读" },
    { label: "内容", value: "系统 维护" }
  ],
  "notice details should remain plain text and preserve the read-state contract"
)

assert.deepStrictEqual(
  buildMobileDetailFields("futureFeature", {}, { detail: "兼容摘要" }),
  [{ label: "摘要", value: "兼容摘要" }],
  "unknown features should retain their card summary"
)

assert.strictEqual(
  transferBusinessTypeLabel({
    sourceBusinessType: "sales_delivery",
    transferType: "cross_store"
  }),
  "跨店销售在途"
)
assert.strictEqual(isNoticeRead({ isRead: true }), true)
assert.strictEqual(isNoticeRead({ isRead: "0" }), false)

const approvalRow = {
  fromDeptName: "中央仓",
  toDeptName: "湖滨店",
  details: [{ quantity: 2, costPrice: 10 }, { noticeQty: 3, costPrice: 4 }],
  approvalTrack: {
    rounds: [{
      nodes: [{
        state: "current",
        nodeName: "区域经理"
      }]
    }]
  }
}
assert.strictEqual(resolveTransferApprovalNodeName(approvalRow), "区域经理")
assert.deepStrictEqual(
  buildMobileReviewFields("transferApproval", approvalRow),
  [
    { label: "调出", value: "中央仓" },
    { label: "调入", value: "湖滨店" },
    { label: "申请数量", value: "5" },
    { label: "参考总价", value: "¥32.00" },
    { label: "审批节点", value: "区域经理" }
  ],
  "transfer review fields should show aggregate quantity, reference total, and active approval-node rules"
)

console.log("mobile feature detail fields policy tests passed")
