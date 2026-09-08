const assert = require("assert")

const {
  buildMobileDetailSections,
  resolveTransferTotalAmount,
  resolveTransferTotalQuantity,
  transferApprovalSummaryParts
} = require("../src/views/mobile/feature/mobileFeatureDetailSectionsPolicy")

assert.strictEqual(
  resolveTransferTotalQuantity({
    details: [
      { quantity: "1.5" },
      { noticeQty: 2 },
      { quantity: "invalid" }
    ]
  }),
  3.5,
  "detail quantities should be summed without turning invalid values into zero-valued evidence"
)

assert.strictEqual(
  resolveTransferTotalQuantity({ totalQuantity: 0, details: [{ quantity: 9 }] }),
  0,
  "an explicit top-level zero quantity should win over detail rows"
)

assert.strictEqual(
  resolveTransferTotalAmount({
    details: [
      { quantity: 2, costPrice: 3.25 },
      { lineAmount: 5 }
    ]
  }),
  11.5,
  "transfer amount should combine derived and explicit line amounts"
)

assert.deepStrictEqual(
  transferApprovalSummaryParts({
    approvalSummary: {
      summaryText: "当前待区域经理审批",
      currentCandidateDisplayNames: ["甲", "乙", "丙"],
      currentCandidateCount: 3
    }
  }),
  ["当前待区域经理审批", "候选：甲、乙等3人"],
  "approval summaries should remain compact while preserving the candidate count"
)

assert.deepStrictEqual(
  buildMobileDetailSections("transfer", {
    unifiedApprovalLoadError: true
  }),
  [{
    title: "统一审批轨迹",
    rows: [{
      title: "审批轨迹加载失败",
      meta: "主单详情已保留，请稍后重试",
      extra: "",
      value: "加载失败"
    }]
  }],
  "approval loading failures should not hide the main business detail"
)

const hiddenCostSections = buildMobileDetailSections("purchase", {
  details: [{
    productName: "明前龙井",
    productCode: "TEA-001",
    quantity: 2,
    unit: "盒",
    costPrice: 128
  }]
})
assert.strictEqual(hiddenCostSections[0].rows[0].extra, "")

const visibleCostSections = buildMobileDetailSections("purchase", {
  details: [{
    productName: "明前龙井",
    productCode: "TEA-001",
    quantity: 2,
    unit: "盒",
    costPrice: 128
  }]
}, {
  permissions: ["inv:cost:view"]
})
assert.strictEqual(
  visibleCostSections[0].rows[0].extra,
  "参考成本价 ¥128.00 · 小计 ¥256.00",
  "cost-sensitive line metadata should require the dedicated permission"
)

const replenishmentSections = buildMobileDetailSections("replenishment", {
  details: [{
    productName: "明前龙井",
    productCode: "TEA-001",
    quantity: 2,
    unit: "盒",
    costPrice: 128
  }]
})
assert.strictEqual(
  replenishmentSections[0].rows[0].extra,
  "参考成本价 ¥128.00 · 小计 ¥256.00",
  "replenishment detail cards should always show the submitted reference cost snapshot"
)

const stockCheckSections = buildMobileDetailSections("stockCheck", {
  approvalTrack: [{
    roundNo: 2,
    status: "invalidated",
    submittedBy: "盘点员",
    tasks: [{
      approverName: "主管",
      status: "invalidated",
      selfApproved: "0"
    }],
    detailSnapshot: JSON.stringify([{
      productName: "大红袍",
      bookQty: 8,
      actualQty: 7
    }]),
    invalidDetailSnapshot: "not-json"
  }]
})
assert.ok(
  stockCheckSections[0].rows[0].extra.includes("提交冻结：大红袍 账面8 实盘7"),
  "stock-check history should expose parseable frozen evidence and tolerate invalid optional snapshots"
)

console.log("mobile feature detail sections policy tests passed")
